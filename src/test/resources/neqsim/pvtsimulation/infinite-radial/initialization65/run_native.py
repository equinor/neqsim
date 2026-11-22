"""Retain complete actual-Java8 ABBA runs and cold/fallback controls."""
import argparse
from datetime import datetime, timezone
import json
import gzip
import hashlib
from pathlib import Path
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', required=True)
    parser.add_argument('--before-classes', required=True)
    parser.add_argument('--after-classes', required=True)
    parser.add_argument('--dependencies', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent
    dependencies = args.dependencies.read_text().strip()
    executions = []
    for index, label in enumerate(['before', 'after', 'after', 'before']):
        classes = args.before_classes if label == 'before' else args.after_classes
        classpath = classes + ':' + dependencies
        jobs = [('Qualification', [str(root.parent / 'reference.json')], 'qualification')]
        for mode in ['1.0', '4.9E-324', 'concurrent']:
            jobs.append(('neqsim.pvtsimulation.reservoirproperties.materialbalance.Startup65',
                         [], 'startup-' + mode))
        for driver, inputs, kind in jobs:
            output = root / f'{index}-{label}-{kind}.json'
            command = [args.java, '-cp', classpath, driver]
            command += inputs + [str(output)] if inputs else [str(output), kind[8:]]
            started = datetime.now(timezone.utc).isoformat()
            clock = time.perf_counter()
            run = subprocess.run(command, check=False, capture_output=True, text=True)
            payload_sha = None
            if not run.returncode and kind == 'qualification':
                payload = output.read_bytes()
                payload_sha = hashlib.sha256(payload).hexdigest()
                compressed = output.with_suffix(output.suffix + '.gz')
                compressed.write_bytes(gzip.compress(payload, mtime=0))
                output.unlink()
                output = compressed
            executions.append({'command': command, 'startedUtc': started,
                               'elapsedSeconds': time.perf_counter() - clock,
                               'exit': run.returncode, 'stdout': run.stdout,
                               'stderr': run.stderr, 'label': label,
                               'kind': kind, 'output': output.name, 'uncompressedSha256': payload_sha})
            args.output.write_text(json.dumps(executions, indent=2) + '\n')
            if run.returncode:
                raise RuntimeError('Native run failed; complete result retained')


if __name__ == '__main__':
    main()
