"""Paired query-only diagnostic using saved exact baseline classes, never a CI wall-clock assertion."""
import argparse
import json
from pathlib import Path
import statistics
import subprocess
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--baseline-classes', type=Path, required=True)
parser.add_argument('--driver-classes', type=Path, required=True)
parser.add_argument('--classpath-file', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[6]
args.output.mkdir(parents=True, exist_ok=True)
separator = ';' if sys.platform == 'win32' else ':'
common = [str(args.driver_classes.resolve()), str(root / 'target/classes'), args.classpath_file.read_text().strip()]
results = []
for block in range(5):
    for position, label in enumerate(['baseline', 'candidate', 'candidate', 'baseline']):
        paths = ([str(args.baseline_classes.resolve())] if label == 'baseline' else []) + common
        prefix = args.output / f'{block}-{position}-{label}'
        command = ['java', '-cp', separator.join(paths), 'QuantileEndpointQualification',
                   str(prefix.with_suffix('.endpoints.json')), str(prefix.with_suffix('.timing.json'))]
        process = subprocess.run(command, capture_output=True, text=True)
        expected_exit = 1 if label == 'baseline' else 0
        if process.returncode != expected_exit:
            raise RuntimeError(f'{label} unexpected exit {process.returncode}: {process.stderr}')
        endpoints = json.loads(prefix.with_suffix('.endpoints.json').read_text())
        failures = sum(not row['pass'] for row in endpoints)
        if failures != (8 if label == 'baseline' else 0):
            raise RuntimeError(f'{label} endpoint control changed: {failures}')
        samples = json.loads(prefix.with_suffix('.timing.json').read_text())
        results.append({'block': block, 'position': position, 'label': label,
                        'command': command, 'exit': process.returncode, 'endpointFailures': failures,
                        'samples': samples,
                        'medianSecondsPerQuery': statistics.median(s['seconds'] / s['queries'] for s in samples
                                                                 if not s['warmup'])})
summary = {label: statistics.median(r['medianSecondsPerQuery'] for r in results if r['label'] == label)
           for label in ['baseline', 'candidate']}
summary['claim'] = 'five ABBA blocks; interior pressure query only; no simulation-speed or CI timing claim'
(args.output / 'timings.json').write_text(json.dumps({'summary': summary, 'runs': results}, indent=2) + '\n')
print(json.dumps(summary))
