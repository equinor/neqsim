"""Independent review control: five-point versus frozen three-point prior cell quadrature."""
import copy
import importlib.util
import json
import math
from pathlib import Path

here = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('reference', here / 'reference.py')
reference = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reference)
data = json.loads((here / 'reference.json').read_text())
case = next(c for c in data['cases'] if c['name'] == 'conflicting-prior-9')
nodes = [0., -.5384693101056831, .5384693101056831, -.9061798459386640, .9061798459386640]
weights = [.5688888888888889, .4786286704993665, .4786286704993665, .2369268850561891, .2369268850561891]
masses = []
for index in range(81):
    i, j = divmod(index, 9)
    vlo, vhi = 800 + i * 400 / 9, 800 + (i + 1) * 400 / 9
    jlo, jhi = .0001 + j * .0008 / 9, .0001 + (j + 1) * .0008 / 9
    terms = []
    for nv, wv in zip(nodes, weights):
        volume = (vlo + vhi + nv * (vhi - vlo)) / 2
        z = (volume - 1130) / 70
        for nj, wj in zip(nodes, weights):
            productivity = (jlo + jhi + nj * (jhi - jlo)) / 2
            y = (productivity - .0008) / .00015
            terms.append(wv * wj * math.exp(-.5 * (z * z + y * y - 1.2 * z * y) / .64))
    masses.append(math.fsum(terms) * (vhi - vlo) * (jhi - jlo) / 4)
candidates = copy.deepcopy(case['candidates'])
for candidate, mass in zip(candidates, masses):
    candidate['mass'] = mass
new = reference.summarize(candidates, case['rows'], case['observations'], case['sigma'])
old = case['expected']
print(json.dumps({
    'case': case['name'],
    'control': 'five-point tensor Gauss versus frozen three-point cell masses; unchanged predictions',
    'maxNormalizedPriorAbsDifference': max(abs(a - b) for a, b in zip(new['prior'], old['prior'])),
    'maxNormalizedPriorRelativeDifference': max(abs(a - b) / b for a, b in zip(new['prior'], old['prior'])),
    'maxPosteriorMassAbsDifference': max(abs(a - b) for a, b in zip(new['weights'], old['weights'])),
    'volumeMeanM3': [old['means'][0], new['means'][0]],
    'productivityMeanM3PerSecondPerBar': [old['means'][4], new['means'][4]],
    'volumeProductivityCorrelation': [old['correlations'][0][4], new['correlations'][0][4]],
    'priorStandardizedVolumeShift': [old['shifts'][0], new['shifts'][0]],
    'effectiveCount': [old['effectiveCount'], new['effectiveCount']],
    'logEvidence': [old['logEvidence'], new['logEvidence']],
    'sameParameterQuantiles': old['parameterQuantiles'] == new['parameterQuantiles'],
    'samePressureQuantiles': old['pressureQuantiles'] == new['pressureQuantiles'],
    'claim': 'prior quadrature sensitivity; not continuous posterior convergence',
}, indent=2))
