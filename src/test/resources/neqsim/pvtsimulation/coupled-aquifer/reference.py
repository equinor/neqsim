"""Independent explicit-SRK coupled water-support ODE; public synthetic inputs."""
import json
import math

R = 8.3144621  # J/(mol K)
TC = 190.56  # K
PC = 45.99e5  # Pa
W = .0115
T = 400.  # K
P = 200.  # bara
V = 1000.  # reservoir m3
DT = 10000.  # seconds
c = 2 ** (1 / 3) - 1
b = c / 3 * R * TC / PC
m = .48 + 1.574 * W - .176 * W * W
a = R * R * TC * TC / (9 * c * PC) * (1 + m * (1 - math.sqrt(T / TC))) ** 2


def molar_pressure(volume):
    """Return SRK pressure in bara from molar volume in m3/mol."""
    return (R * T / (volume - b) - a / (volume * (volume + b))) / 1e5


lo = b * (1 + 1e-12)
hi = R * T / (P * 1e5) + 10 * b
for _ in range(120):
    mid = (lo + hi) / 2
    if molar_pressure(mid) > P:
        lo = mid
    else:
        hi = mid
n0 = V / ((lo + hi) / 2)
assert abs(molar_pressure(V / n0) - P) < 1e-10


def pressure(amount, water):
    """Return explicit tank pressure for tracked methane and segregated water."""
    return molar_pressure((V - water) / amount)


def run(capacity_ratio, productivity_ratio, fraction, steps):
    """Integrate in dimensionless time, retaining all common 64-mesh endpoints."""
    h = 1 / steps
    water = 0.
    output = []

    def derivative(time, water):
        if capacity_ratio == 0 or productivity_ratio == 0:
            return 0.
        aquifer_pressure = P if capacity_ratio < 0 else P - water / (capacity_ratio * V / P)
        return productivity_ratio * V / P * (aquifer_pressure - pressure(n0 * (1 - fraction * time), water))

    for index in range(steps):
        time = index * h
        k1 = derivative(time, water)
        k2 = derivative(time + h / 2, water + h * k1 / 2)
        k3 = derivative(time + h / 2, water + h * k2 / 2)
        k4 = derivative(time + h, water + h * k3)
        water += h * (k1 + 2 * k2 + 2 * k3 + k4) / 6
        if (index + 1) % (steps // 64) == 0:
            output.append([pressure(n0 * (1 - fraction * (index + 1) * h), water), water])
    return output


cases = []
ratios = [(capacity, productivity) for capacity in [.25, 1., -1.] for productivity in [.1, 1.]]
for capacity, productivity in ratios + [(0., 1.), (1., 0.)]:
    for fraction in [.2, .5]:
        coarse = run(capacity, productivity, fraction, 16384)
        fine = run(capacity, productivity, fraction, 32768)
        assert max(abs(left[column] - right[column]) for left, right in zip(coarse, fine)
                   for column in range(2)) < 1e-8
        cases.append({'capacityRatio': capacity, 'productivityRatio': productivity,
                      'producedFraction': fraction, 'rows': fine})
print(json.dumps({'n0': n0, 'a': a, 'b': b, 'cases': cases}, indent=2))
