#!/usr/bin/env bash
set -euo pipefail
mkdir -p reports/perf
export ORDER_URL="${ORDER_URL:-http://localhost:8080}"
export INVENTORY_URL="${INVENTORY_URL:-http://localhost:8081}"
# Warm both JVMs before the baseline. These requests are outside the measured sample.
curl -fsS -X PUT "$INVENTORY_URL/api/stock/WARM" -H 'Content-Type: application/json' -d '{"available":1000}' > /dev/null
for i in $(seq 1 40); do
  curl -fsS "$ORDER_URL/api/orders" -H 'Content-Type: application/json' -d '{"lines":[{"sku":"WARM","quantity":1,"unitPrice":1.00,"currency":"EUR"}]}' > /dev/null
done
docker run --rm --network host --user "$(id -u):$(id -g)" -v "$PWD/perf:/scripts:ro" -v "$PWD/reports/perf:/results" -e ORDER_URL -e INVENTORY_URL grafana/k6:1.3.0 run --summary-export=/results/k6-baseline.json /scripts/orders.js
python3 scripts/calibrate.py
source reports/perf/budget.env
export LATENCY_BUDGET_MS
set +e
docker run --rm --network host --user "$(id -u):$(id -g)" -v "$PWD/perf:/scripts:ro" -v "$PWD/reports/perf:/results" -e ORDER_URL -e INVENTORY_URL -e LATENCY_BUDGET_MS grafana/k6:1.3.0 run --summary-export=/results/k6-validation.json /scripts/orders.js
k6_status=$?
set -e
# JMeter is downloaded only on the runner. Verify the archive against Apache's SHA512.
tools_dir="${RUNNER_TEMP:-/tmp}/order-quality-jmeter"
mkdir -p "$tools_dir"
curl -fsSL --retry 3 https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-5.6.3.tgz -o "$tools_dir/jmeter.tgz"
curl -fsSL --retry 3 https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-5.6.3.tgz.sha512 -o "$tools_dir/jmeter.sha512"
python3 - "$tools_dir" <<'PY'
import hashlib, pathlib, re, sys
p = pathlib.Path(sys.argv[1])
expected = re.search(r'\b[0-9a-fA-F]{128}\b', (p / 'jmeter.sha512').read_text()).group().lower()
assert hashlib.sha512((p / 'jmeter.tgz').read_bytes()).hexdigest() == expected, 'JMeter checksum mismatch'
PY
tar -xzf "$tools_dir/jmeter.tgz" -C "$tools_dir"
curl -fsS -X PUT "$INVENTORY_URL/api/stock/PERF" -H 'Content-Type: application/json' -d '{"available":1000000000}' > /dev/null
"$tools_dir/apache-jmeter-5.6.3/bin/jmeter" -n -t perf/orders.jmx -l reports/perf/jmeter.jtl -j reports/perf/jmeter.log -e -o reports/perf/jmeter-html -Jorder.host=localhost -Jorder.port=8080 -Jjmeter.save.saveservice.output_format=csv
python3 scripts/perf_evidence.py
exit "$k6_status"
