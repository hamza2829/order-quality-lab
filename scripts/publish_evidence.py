"""Publish only evidence from completed runs of the same tested commit."""
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

source = Path(sys.argv[1])
data = {kind: json.loads(next(source.rglob(name)).read_text()) for kind, name in
        [('unit', 'unit.json'), ('integration', 'integration.json'), ('e2e', 'e2e.json'), ('performance', 'measurements.json')]}
shas = {item['sha'] for item in data.values()}
assert len(shas) == 1 and None not in shas, 'Evidence must describe one tested commit'
sha = shas.pop()
for kind, group in [('unit', 'unit'), ('integration', 'integration'), ('integration', 'contract'), ('e2e', 'e2e')]:
    stats = data[kind]['counts'][group]
    assert stats['tests'] > 0 and stats['failures'] + stats['errors'] + stats['skipped'] == 0, f'{group} did not pass'

test_table = '| Test type | Executed | Failures | Skipped | CI evidence |\n|---|---:|---:|---:|---|\n'
for label, kind, group in [('Unit', 'unit', 'unit'), ('Integration', 'integration', 'integration'), ('Contract', 'integration', 'contract'), ('End to end', 'e2e', 'e2e')]:
    stats = data[kind]['counts'][group]
    test_table += f"| {label} | {stats['tests']} | {stats['failures'] + stats['errors']} | {stats['skipped']} | [Run]({data[kind]['run_url']}) |\n"
coverage = '| Package | Covered lines | Total lines | Line coverage |\n|---|---:|---:|---:|\n'
for item in data['unit']['coverage']:
    coverage += f"| `{item['package'].replace('/', '.')}` | {item['covered']} | {item['covered'] + item['missed']} | {item['percent']:.2f}% |\n"
tests = f'Executed against [{sha[:12]}](https://github.com/hamza2829/order-quality-lab/commit/{sha}). Parameterized invocations count as separate tests. Unit tests are counted once, even though integration CI also reruns them.\n\n' + test_table + '\nUnit-only JaCoCo coverage, with an 80 percent minimum per package:\n\n' + coverage

perf = data['performance']
perf_table = '| Tool | Order POST requests/s | Order POST p95 ms | Order POST error rate | Orders |\n|---|---:|---:|---:|---:|\n'
for row in perf['measurements']:
    perf_table += f"| {row['tool']} | {row['requests_per_second']:.2f} | {row['p95_ms']:.2f} | {row['error_rate_percent']:.3f}% | {row['orders']:.0f} |\n"
perf_text = f"Measured in [performance CI]({perf['run_url']}) on commit `{sha[:12]}`.\n\n" + perf_table
perf_text += f"\nBaseline p95 was {perf['calibration']['baseline_p95_ms']:.2f} ms. The derived k6 validation budget was {perf['calibration']['budget_ms']} ms. "
perf_text += f"JMeter's overall sample error rate, including reads, was {perf['measurements'][2]['all_sample_error_rate_percent']:.3f}%.\n"

readme = Path('README.md')
text = readme.read_text()
for marker, body in [('TEST_RESULTS', tests), ('PERF_RESULTS', perf_text)]:
    text = re.sub(f'<!-- {marker}_START -->.*?<!-- {marker}_END -->', f'<!-- {marker}_START -->\n{body}<!-- {marker}_END -->', text, flags=re.S)
upgrade = subprocess.check_output(['git', 'log', '--format=%H', '--grep=Upgrade springdoc', '-1'], text=True).strip()
assert upgrade, 'The separate upgrade commit is missing'
text = text.replace('The initial implementation uses springdoc 2.9.0. A separate small commit will move it to 2.9.1 after the initial checks. This is an example of a focused dependency upgrade with unchanged behavior. Its exact commit and verified checks will be recorded after execution.',
                    f'The initial implementation used springdoc 2.9.0. [A separate small commit](https://github.com/hamza2829/order-quality-lab/commit/{upgrade}) upgraded it to 2.9.1 after the baseline workflows passed. The workflows linked above passed after the upgrade. The upgrade and a small behavior-preserving refactoring are documented in [the upgrade example](docs/upgrade.md).')
readme.write_text(text)
performance = Path('docs/performance.md')
text = performance.read_text().replace('No numbers are claimed before a successful CI measurement. This page will be populated from the uploaded raw results and linked to the exact run and tested commit.', '<!-- MEASUREMENTS_START -->\n' + perf_text + '<!-- MEASUREMENTS_END -->')
text = re.sub(r'<!-- MEASUREMENTS_START -->.*?<!-- MEASUREMENTS_END -->', '<!-- MEASUREMENTS_START -->\n' + perf_text + '<!-- MEASUREMENTS_END -->', text, flags=re.S)
performance.write_text(text)
out = Path('docs/evidence')
out.mkdir(parents=True, exist_ok=True)
for kind, item in data.items():
    (out / f'{kind}.json').write_text(json.dumps(item, indent=2) + '\n')
print(tests + '\n' + perf_text)
