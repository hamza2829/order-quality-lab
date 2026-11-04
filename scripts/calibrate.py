import json
import math
from pathlib import Path

root = Path('reports/perf')
data = json.loads((root / 'k6-baseline.json').read_text())
p95 = data['metrics']['order_latency']['p(95)']
# Round twice the measured p95 upward to 10 ms. The 50 ms floor avoids timer noise.
budget = max(50, math.ceil(p95 * 2 / 10) * 10)
(root / 'budget.env').write_text(f'LATENCY_BUDGET_MS={budget}\n')
(root / 'calibration.json').write_text(json.dumps(dict(baseline_p95_ms=p95, budget_ms=budget, rule='max(50, ceil(2 * baseline_p95_ms / 10) * 10)'), indent=2) + '\n')
print(f'Measured baseline p95 {p95:.3f} ms; validation budget {budget} ms')
