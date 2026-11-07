"""Independent continuous well-mixed gas balance and explicit refinement reference.

Run with selected Python: continuous_reference.py [decimal-precision].
Shares only the independent SRK functions/constants with reference.py, never NeqSim.
Inputs, analytical equations and frozen bounds are in REFINEMENT_SCOPE.md.
"""
from decimal import Decimal as D
import json
from reference import K, pressure, volume

TEMPERATURE = D(400)
TANK_VOLUME = D('1017.3')
INITIAL_X = [D('.9'), D('.1')]
INITIAL_N = TANK_VOLUME / volume(TEMPERATURE, INITIAL_X, D(200), K)
INITIAL = [q * INITIAL_N for q in INITIAL_X]


def endpoint(n, produced):
    """Return inventories, pressure, and actual surface gas volume when defined."""
    total = sum(n)
    row = {'retainedMoles': n, 'producedMoles': produced,
           'pressureBara': pressure(TEMPERATURE, [q / total for q in n],
                                    TANK_VOLUME / total, K)}
    if sum(produced):
        row['surfaceGasVolumeM3'] = sum(produced) * volume(
            D('288.15'), [q / sum(produced) for q in produced], D('1.01325'), K)
    return row


def continuous(n, production, injection):
    """Integrate the well-mixed component ODE analytically over a full interval."""
    total = sum(n)
    added = sum(injection)
    final_total = total + added - production
    if not added:
        final = [q * final_total / total for q in n]
    else:
        factor = (-added / total).exp() if added == production else (
            -added / (added - production) * (final_total / total).ln()).exp()
        final = [final_total * (injection[i] / added
                 + (n[i] / total - injection[i] / added) * factor) for i in range(2)]
    removed = [n[i] + injection[i] - final[i] for i in range(2)]
    # Zero-production is defined exactly rather than emitting roundoff offgas.
    if not production:
        final = [n[i] + injection[i] for i in range(2)]
        removed = [D(0), D(0)]
    return endpoint(final, removed)


def explicit(n, production, injection, substeps):
    """Apply independently implemented removal-before-addition component balances."""
    n = n.copy()
    removed = [D(0), D(0)]
    for _ in range(substeps):
        total = sum(n)
        dq = [production / substeps * q / total for q in n]
        n = [n[i] - dq[i] + injection[i] / substeps for i in range(2)]
        removed = [removed[i] + dq[i] for i in range(2)]
    return endpoint(n, removed)


def generate():
    """Return five complete limiting/refinement scenarios, with every row retained."""
    cases = [
        ('alternating', [(100000, [0, 20000]), (100000, [10000, 0]),
                         (100000, [0, 15000]), (100000, [5000, 5000])]),
        ('constant inventory', [(100000, [0, 100000])]),
        ('withdrawal only', [(100000, [0, 0])]),
        ('injection only', [(0, [0, 100000])]),
        ('no flow', [(0, [0, 0])]),
    ]
    output = []
    for name, schedule in cases:
        schedule = [(D(p), [D(q) for q in inj]) for p, inj in schedule]
        n = INITIAL.copy()
        limit = []
        for production, injection in schedule:
            row = continuous(n, production, injection)
            limit.append(row)
            n = row['retainedMoles']
        refinements = []
        for substeps in ([1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024]
                         if name in ["alternating", "constant inventory"] else [1, 2, 4, 8, 16, 32, 64]):
            n = INITIAL.copy()
            rows = []
            for production, injection in schedule:
                row = explicit(n, production, injection, substeps)
                rows.append(row)
                n = row['retainedMoles']
            error = max(abs(row['retainedMoles'][i] - exact['retainedMoles'][i])
                        for row, exact in zip(rows, limit) for i in range(2))
            refinements.append({'substeps': substeps, 'rows': rows,
                                'maxContinuousComponentErrorMoles': error})
        output.append({'name': name, 'schedule': [
            {'producedMoles': p, 'injectedMoles': inj} for p, inj in schedule],
            'continuous': limit, 'refinements': refinements})
    return {'components': ['methane', 'ethane'], 'initialVolumeM3': TANK_VOLUME,
            'initialMoles': INITIAL, 'temperatureK': TEMPERATURE, 'cases': output}


if __name__ == '__main__':
    print(json.dumps(generate(), default=lambda q: float(q), indent=2, allow_nan=False))
