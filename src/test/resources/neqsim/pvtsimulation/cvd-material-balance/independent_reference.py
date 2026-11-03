"""Offline 50-digit CVD accounting and conservative printed-rounding screen.

No NeqSim, EOS, fitting, normalization or measured-accuracy acceptance. See README.
Run from any directory; output is deterministic and invariant under Python -O.
"""
import csv
from decimal import Decimal as D, localcontext, ROUND_FLOOR, ROUND_CEILING
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOURCE_SHA256 = '99c7245e552e1b57389294c78f9433f48d1e71545db1aca15e8cb04acc2f1b91'


def require(condition, message):
    """Fail even under optimized Python."""
    if not condition:
        raise ValueError(message)


def box(value, half):
    """Exact decimal input bin."""
    return value - half, value + half


def sub(a, b):
    """Outward-rounded interval difference."""
    with localcontext() as c:
        c.prec = 50
        c.rounding = ROUND_FLOOR
        lo = a[0] - b[1]
        c.rounding = ROUND_CEILING
        hi = a[1] - b[0]
    return lo, hi


def mul(a, b):
    """Outward-rounded interval product, including signed endpoints."""
    with localcontext() as c:
        c.prec = 50
        c.rounding = ROUND_FLOOR
        lo = min(x * y for x in a for y in b)
        c.rounding = ROUND_CEILING
        hi = max(x * y for x in a for y in b)
    return lo, hi


def div(a, b):
    """Outward-rounded interval quotient with strictly positive divisor."""
    require(b[0] > 0, 'Unresolved interval liquid/divisor')
    with localcontext() as c:
        c.prec = 50
        c.rounding = ROUND_FLOOR
        lo = min(x / y for x in a for y in b)
        c.rounding = ROUND_CEILING
        hi = max(x / y for x in a for y in b)
    return lo, hi


def evaluate():
    """Read attributed source facts and independently evaluate all six stages."""
    with (ROOT / 'ns1-table1.csv').open(newline='') as stream:
        reader = csv.DictReader(stream)
        names = reader.fieldnames[5:]
        rows = list(reader)
    with (ROOT / 'ns1-final-liquid.csv').open(newline='') as stream:
        final = list(csv.DictReader(stream))
    require(len(rows) == 7 and len(names) == 11, 'Incomplete source table')
    require([r['component'] for r in final] == names, 'Final-analysis component order')
    p = [D(r['pressure_psia']) for r in rows]
    z = [D(r['gas_z']) for r in rows]
    n = [D(r['cumulative_produced_percent']) / 100 for r in rows]
    sl = [D(r['liquid_volume_percent']) / 100 for r in rows]
    y = [[D(r[name]) / 100 for name in names] for r in rows]
    require(all(sum(v) == 1 for v in y), 'Source compositions do not sum to one')
    require(n[0] == sl[0] == 0, 'Saturated reference must have exact protocol zeros')
    require(all(p[k] < p[k-1] and n[k] >= n[k-1] for k in range(1, 7)), 'Invalid path')
    # Frozen source rounding bins, not measurement standard deviations.
    pb = [box(v, D('.05')) for v in p]
    zb = [box(v, D('.0005')) for v in z]
    nb = [(D(0), D(0))] + [box(v, D('.000005')) for v in n[1:]]
    yb = [[box(v, D('.00005')) for v in a] for a in y]
    produced = [D(0)] * 11
    produced_box = [(D(0), D(0)) for _ in names]
    result = []
    for k in range(1, 7):
        withdrawal = n[k] - n[k-1]
        withdrawal_box = sub(nb[k], nb[k-1])
        gas = (p[k] / p[0]) * (z[0] / z[k]) * (1-sl[k])
        liquid = 1 - n[k] - gas
        gas_box = mul(mul(div(pb[k], pb[0]), div(zb[0], zb[k])),
                      sub((D(1), D(1)), box(sl[k], D('.0005'))))
        liquid_box = sub(sub((D(1), D(1)), nb[k]), gas_box)
        require(liquid > 0 and liquid_box[0] > 0, 'Unresolved liquid amount')
        components = []
        for j, name in enumerate(names):
            produced[j] += withdrawal * y[k][j]
            # Subtract negative product interval to accumulate without an add primitive.
            produced_box[j] = sub(produced_box[j], mul((-withdrawal_box[1], -withdrawal_box[0]), yb[k][j]))
            remaining = y[0][j] - produced[j]
            liquid_component = remaining - gas * y[k][j]
            x = liquid_component / liquid
            numerator_box = sub(sub(yb[0][j], produced_box[j]), mul(gas_box, yb[k][j]))
            x_box = div(numerator_box, liquid_box)
            components.append(dict(component=name, producedMoles=str(produced[j]),
                                   remainingMoles=str(remaining), liquidMoles=str(liquid_component),
                                   liquidMoleFraction=str(x),
                                   printedRoundingInterval=[str(v) for v in x_box]))
            if k == 6:
                calculated = D(final[j]['published_calculated_mol_percent']) / 100
                measured = D(final[j]['experimental_mol_percent']) / 100
                require(abs(x-calculated) <= D('.000005'), 'Published calculation exceeds frozen half-digit')
                components[-1].update(publishedCalculated=str(calculated), experimental=str(measured),
                                      measuredMinusReconstructed=str(measured-x),
                                      experimentalOverlapsPrintedRounding=(x_box[0] <= measured+D('.00005')
                                                                          and x_box[1] >= measured-D('.00005')))
        result.append(dict(rowId=rows[k]['row_id'], totalMoles=str(1-n[k]), vaporMoles=str(gas),
                           liquidMoles=str(liquid), components=components))
    failed = [c['component'] for c in result[-1]['components'] if not c['experimentalOverlapsPrintedRounding']]
    require(failed == ['C1', 'C7+'], 'Changed experimental rounding-only screen')
    return dict(sourcePdfSha256=SOURCE_SHA256, precisionDigits=50, basisInitialMoles=1,
                interpretation='arithmetic benchmark; printed bins are not measurement uncertainty',
                temperatureK=str((D(280)-32)*5/9+D('273.15')), rows=result,
                experimentalComponentsOutsideRoundingOnlyScreen=failed)


if __name__ == '__main__':
    with localcontext() as context:
        context.prec = 50
        print(json.dumps(evaluate(), indent=2, allow_nan=False))
