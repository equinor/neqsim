"""Fail-closed completeness and identity checks for the fixed pressure-moment matrix."""
import csv
import math

FIELDS = ['diameter_m', 'depth_m', 'liquid_m3', 'gas_m3', 'liquid_m2', 'gas_m2']


def read_native(path, inputs):
    """Read exactly the 222 ordered input identities and finite, physical phase outputs."""
    expected = [tuple(map(float, line.split(','))) for line in inputs.splitlines()]
    if len(expected) != 222 or len(set(expected)) != 222:
        raise ValueError('Expected matrix must contain exactly 222 unique sections')
    with path.open(newline='') as source:
        reader = csv.DictReader(source)
        if reader.fieldnames != FIELDS:
            raise ValueError('Native CSV schema mismatch')
        rows = list(reader)
    if len(rows) != len(expected):
        raise ValueError('Native matrix is incomplete or has extra rows')
    for index, (row, identity) in enumerate(zip(rows, expected)):
        if set(row) != set(FIELDS) or any(row[key] is None for key in FIELDS):
            raise ValueError('Malformed native row: ' + str(index))
        values = [float(row[key]) for key in FIELDS]
        if not all(math.isfinite(value) for value in values):
            raise ValueError('Nonfinite native row: ' + str(index))
        diameter, depth, liquid, gas, liquid_area, gas_area = values
        if (diameter, depth) != identity:
            raise ValueError('Native input identity/order mismatch: ' + str(index))
        if diameter <= 0 or not 0 <= depth <= diameter or liquid < 0 or gas > 0 or min(liquid_area, gas_area) < 0:
            raise ValueError('Invalid phase geometry: ' + str(index))
        if (depth == 0 and (liquid != 0 or liquid_area != 0)) or (depth == diameter and (gas != 0 or gas_area != 0)):
            raise ValueError('Absent phase carries a nonzero moment or area')
    return rows
