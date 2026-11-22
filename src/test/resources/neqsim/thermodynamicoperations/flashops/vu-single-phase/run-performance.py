"""Run one VU workload per JVM; provide identical classpaths except the first class directory."""
import argparse
import csv
import json
from pathlib import Path
import statistics
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--classpath', required=True)
parser.add_argument('--baseline', required=True)
parser.add_argument('--candidate', required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--warmup', type=int, default=30)
parser.add_argument('--measured', type=int, default=12)
parser.add_argument('--replicates', type=int, default=2)
args = parser.parse_args()
if args.output.exists():
    raise SystemExit('Refusing to append a new experiment to an existing output')
for repetition in range(args.replicates):
    versions = ['baseline', 'candidate'] if repetition % 2 == 0 else ['candidate', 'baseline']
    for eos in ['SRK', 'PR']:
        for state in ['gas', 'oil', 'aqueous', 'large']:
            for rule in ([2, 4] if repetition % 2 == 0 else [4, 2]):
                for version in versions:
                    prefix = getattr(args, version)
                    label = f'{version}_rep{repetition}'
                    command = ['java', '-Xms128m', '-Xmx1g', '-cp', prefix + ':' + args.classpath,
                               'neqsim.thermodynamicoperations.flashops.VUSinglePhasePerformanceBenchmark',
                               label, str(args.output), eos, str(rule), state,
                               str(args.warmup), str(args.measured)]
                    logfile = args.output.with_name(f'vu-{eos}-{state}-{rule}-{label}.log')
                    with logfile.open('w') as log:
                        result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT)
                    if result.returncode:
                        raise SystemExit(f'Benchmark failed: see {logfile}')
            print('completed', repetition, eos, state, flush=True)
rows = list(csv.DictReader(args.output.open()))
summary = []
for eos in ['SRK', 'PR']:
    for state in ['gas', 'oil', 'aqueous', 'large']:
        for rule in ['2', '4']:
            selected = [r for r in rows if (r['eos'], r['state'], r['rule']) == (eos, state, rule)]
            metrics = {}
            for version in ['baseline', 'candidate']:
                values = [r for r in selected if r['sample'].startswith(version)]
                metrics[version] = {
                    field: statistics.median(float(r[field]) for r in values)
                    for field in ['elapsed_ns', 'allocated_bytes', 'iterations', 'solves']}
                metrics[version]['worst'] = {
                    field: max(float(r[field]) for r in values)
                    for field in ['max_volume_error', 'max_energy_error', 'max_returned_enthalpy_drift',
                                  'max_pressure_error', 'max_balance', 'max_normalization']}
            ratios = []
            for repetition in range(args.replicates):
                medians = {v: statistics.median(float(r['elapsed_ns']) for r in selected
                           if r['sample'].startswith(f'{v}_rep{repetition}_'))
                           for v in ['baseline', 'candidate']}
                ratios.append(medians['candidate'] / medians['baseline'])
            summary.append(dict(eos=eos, state=state, rule=rule, metrics=metrics,
                                process_pair_time_ratios=ratios))
args.output.with_suffix('.summary.json').write_text(json.dumps(summary, indent=2) + '\n')
print('completed all independent JVMs', flush=True)
