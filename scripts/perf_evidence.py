"""Produce measured metrics and a k6 HTML report from raw CI results."""
import csv
import html
import json
import math
import os
from pathlib import Path

root = Path('reports/perf')
def k6(name):
    metrics = json.loads((root / f'k6-{name}.json').read_text())['metrics']
    return dict(tool=f'k6 {name}', orders=metrics['order_requests']['count'], requests_per_second=metrics['order_requests']['rate'],
                p95_ms=metrics['order_latency']['p(95)'], error_rate_percent=100 * metrics['order_errors']['value'],
                checks_percent=100 * metrics['checks']['value'])

rows = list(csv.DictReader((root / 'jmeter.jtl').open()))
orders = [row for row in rows if row['label'] == 'Place order']
assert len(orders) > 0, 'No JMeter orders measured'
start = min(int(row['timeStamp']) for row in orders)
end = max(int(row['timeStamp']) + int(row['elapsed']) for row in orders)
latencies = sorted(int(row['elapsed']) for row in orders)
jmeter = dict(tool='JMeter', orders=len(orders), requests_per_second=len(orders) / ((end - start) / 1000),
              p95_ms=latencies[math.ceil(0.95 * len(latencies)) - 1],
              error_rate_percent=100 * sum(row['success'] != 'true' for row in orders) / len(orders),
              all_samples=len(rows), all_sample_error_rate_percent=100 * sum(row['success'] != 'true' for row in rows) / len(rows))
calibration = json.loads((root / 'calibration.json').read_text())
result = dict(sha=os.environ.get('GITHUB_SHA'), run_url=f"https://github.com/{os.environ.get('GITHUB_REPOSITORY')}/actions/runs/{os.environ.get('GITHUB_RUN_ID')}",
              calibration=calibration, measurements=[k6('baseline'), k6('validation'), jmeter])
(root / 'measurements.json').write_text(json.dumps(result, indent=2) + '\n')
table = '| Tool | Order POST requests/s | Order POST p95 ms | Order POST error % | Orders |\n|---|---:|---:|---:|---:|\n'
for row in result['measurements']:
    table += f"| {row['tool']} | {row['requests_per_second']:.2f} | {row['p95_ms']:.2f} | {row['error_rate_percent']:.3f} | {row['orders']:.0f} |\n"
print(table)
print(f"Measured k6 latency budget: {calibration['budget_ms']} ms")
if os.environ.get('GITHUB_STEP_SUMMARY'):
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as output:
        output.write(table + f"\nMeasured k6 latency budget: {calibration['budget_ms']} ms.\n")
(root / 'k6-report.html').write_text('<!doctype html><html lang="en"><meta charset="utf-8"><title>k6 CI measurements</title><style>body{font:16px system-ui;max-width:1000px;margin:40px auto;background:#f5f7fa;color:#172b4d}pre{padding:24px;background:white;overflow:auto}a{color:#0055aa}</style><h1>k6 CI measurements</h1><p>Raw summary exports and calibration are included with this report.</p><pre>' + html.escape(table) + '</pre><pre>' + html.escape(json.dumps(result, indent=2)) + '</pre></html>')
assert jmeter['all_sample_error_rate_percent'] < 1, 'JMeter errors exceeded 1 percent'
validation = result['measurements'][1]
assert validation['error_rate_percent'] < 1 and validation['checks_percent'] > 99, 'k6 error or read checks failed'
assert validation['p95_ms'] < calibration['budget_ms'], 'k6 exceeded the measured latency budget'
