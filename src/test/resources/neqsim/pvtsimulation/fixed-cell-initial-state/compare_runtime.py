"""Compare alternating forks of the unchanged explicit-cell API (no Git/network).

Compile the base sources and FixedCellRuntimeProbe separately first. The main
classpath must contain the candidate classes, resources and resolved dependencies.
This narrow control does not measure legacy CVD or general simulation throughput.
"""
import argparse
import json
import math
import os
from pathlib import Path
import platform
import statistics
import subprocess


def main():
    """Preserve raw warmed timings and identical-output checks for six pairs."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath', required=True)
    parser.add_argument('--baseline-classes', required=True)
    parser.add_argument('--probe-classes', required=True)
    parser.add_argument('--base-revision', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--warmup-trials', type=int, default=10)
    parser.add_argument('--trials-per-sample', type=int, default=10)
    args = parser.parse_args()
    output = Path(args.output)
    forks = output.parent / 'runtime-forks'
    forks.mkdir(parents=True, exist_ok=True)
    results = []
    for pair in range(6):
        order = ['baseline', 'candidate'] if pair % 2 == 0 else ['candidate', 'baseline']
        record = dict(order=order)
        for name in order:
            classpath = args.probe_classes + os.pathsep + args.classpath
            if name == 'baseline':
                classpath = args.baseline_classes + os.pathsep + classpath
            path = forks / ('%02d-%s.json' % (pair, name))
            subprocess.run(['java', '-Xms128m', '-Xmx512m', '-cp', classpath,
                            'FixedCellRuntimeProbe', str(path.resolve()), str(args.warmup_trials),
                            str(args.trials_per_sample)], check=True)
            record[name] = json.loads(path.read_text(encoding='utf-8'))
        if not math.isclose(record['baseline']['checksumVolumeM3'], record['candidate']['checksumVolumeM3'], rel_tol=1e-10, abs_tol=0):
            raise RuntimeError('Unchanged-family outputs differ')
        record['candidateToBaselineMedianRatio'] = (
            statistics.median(record['candidate']['seconds'])
            / statistics.median(record['baseline']['seconds']))
        results.append(record)
    ratios = [r['candidateToBaselineMedianRatio'] for r in results]
    report = dict(baseRevision=args.base_revision, operatingSystem=platform.system(),
                  architecture=platform.machine(), jvmOptions=['-Xms128m', '-Xmx512m'],
                  pairs=results, pairMedianRatioRange=[min(ratios), max(ratios)],
                  geometricMeanMedianRatio=math.exp(statistics.mean(math.log(x) for x in ratios)),
                  interpretation='Narrow warmed unchanged-family control; no general speedup claim')
    output.write_text(json.dumps(report, indent=2, allow_nan=False)+'\n', encoding='utf-8')
    print(json.dumps({k: report[k] for k in ['pairMedianRatioRange', 'geometricMeanMedianRatio']}))


if __name__ == '__main__':
    main()
