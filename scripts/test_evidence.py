"""Read executed JUnit XML and JaCoCo XML. Never infer counts from source files."""
import json
import os
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

kind = sys.argv[1]
counts = {name: dict(tests=0, failures=0, errors=0, skipped=0) for name in ("unit", "integration", "contract", "e2e")}
for path in Path('.').glob('*/target/*-reports/TEST-*.xml'):
    suite = ET.parse(path).getroot()
    name = suite.attrib['name']
    group = 'e2e' if name.endswith('E2ETest') else 'contract' if name.endswith('ContractTest') else 'integration' if name.endswith('IT') else 'unit'
    for metric in counts[group]:
        counts[group][metric] += int(suite.attrib.get(metric, 0))

coverage = []
for path in Path('.').glob('*/target/site/jacoco/jacoco.xml'):
    for package in ET.parse(path).getroot().findall('package'):
        if package.attrib['name'].endswith(('/domain', '/application')):
            counter = next(c for c in package.findall('counter') if c.attrib['type'] == 'LINE')
            covered, missed = int(counter.attrib['covered']), int(counter.attrib['missed'])
            coverage.append(dict(package=package.attrib['name'], covered=covered, missed=missed, percent=round(100 * covered / (covered + missed), 2)))

result = dict(sha=os.environ.get('GITHUB_SHA'), run_url=f"https://github.com/{os.environ.get('GITHUB_REPOSITORY')}/actions/runs/{os.environ.get('GITHUB_RUN_ID')}", counts=counts, coverage=coverage)
Path('reports').mkdir(exist_ok=True)
Path(f'reports/{kind}.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result, indent=2))
required = {'unit': ['unit'], 'integration': ['integration', 'contract'], 'e2e': ['e2e']}[kind]
for group in required:
    stats = counts[group]
    assert stats['tests'] > 0, f'No {group} tests executed'
    assert stats['failures'] + stats['errors'] + stats['skipped'] == 0, f'{group} suite did not fully pass'
if kind == 'unit':
    assert len(coverage) == 4, 'Expected domain and application packages in both services'
    assert all(c['percent'] >= 80 for c in coverage), 'Coverage below 80 percent'
