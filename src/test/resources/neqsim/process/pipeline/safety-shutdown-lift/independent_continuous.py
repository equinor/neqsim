"""Independent continuous ideal lift oracle, not the Java backward-Euler algorithm.

Synthetic parameters frozen in #4707 comment6523980161. SciPy DOP853 integrates
lift/velocity/jet volume, with Brent's method solving the monotone hydraulic
characteristic at each RHS evaluation. A terminal seat event selects the first
physical contact. Orifice area is extended as zero only in solver trial stages
below the terminal seat; no such trial state is accepted as physical evidence.
No vendor or asset data, calibration or pressure-floor substitution is used.
"""
import json
import math
from pathlib import Path
import platform
import scipy
from scipy.integrate import solve_ivp
from scipy.optimize import brentq

MASS = 0.2
DAMPING = 2.0
STIFFNESS = 2000.0
AREA = 1e-4
RHO = 1000.0
CD = 0.65
WIDTH = 0.05
RESISTANCE = 5e8
DRIVE = -1e5
INITIAL_LIFT = 0.005


def hydraulic(lift, velocity):
    """Bracket the signed pressure root without the production quadratic."""
    forcing = DRIVE - RESISTANCE * AREA * velocity
    capacity = CD * WIDTH * max(lift, 0.0) * math.sqrt(2.0 / RHO)

    def residual(pressure):
        return pressure + RESISTANCE * capacity * math.copysign(math.sqrt(abs(pressure)), pressure) - forcing

    if capacity == 0.0 or forcing == 0.0:
        pressure = forcing
    else:
        pressure = brentq(residual, min(0.0, forcing), max(0.0, forcing), xtol=1e-9, rtol=1e-14)
    jet = capacity * math.copysign(math.sqrt(abs(pressure)), pressure)
    return pressure, jet


def derivative(time, state):
    """Continuous mechanics and independent signed jet-volume inventory."""
    lift, velocity, _ = state
    pressure, jet = hydraulic(lift, velocity)
    acceleration = (AREA * pressure - STIFFNESS * lift - DAMPING * velocity) / MASS
    return [velocity, acceleration, jet]


def seat(time, state):
    """Terminal downward physical seat event; reject continuation beyond it."""
    return state[0]


seat.terminal = True
seat.direction = -1


def run(max_step):
    """Compute independent contact and through-volume data with explicit tolerances."""
    result = solve_ivp(derivative, (0.0, 0.2), [INITIAL_LIFT, 0.0, 0.0], method="DOP853",
                       rtol=1e-12, atol=1e-14, max_step=max_step, events=seat)
    assert result.success and len(result.t_events[0]) == 1
    time = float(result.t_events[0][0])
    lift, velocity, jet_volume = (float(value) for value in result.y_events[0][0])
    assert abs(lift) <= 1e-14 and velocity < 0.0 and jet_volume < 0.0
    swept = AREA * (0.0 - INITIAL_LIFT)
    return {"contactTime_s": time, "preImpactVelocity_m_s": velocity,
            "inelasticImpactEnergy_J": 0.5 * MASS * velocity * velocity,
            "jetVolume_m3": jet_volume, "sweptVolume_m3": swept,
            "totalVolume_m3": jet_volume + swept, "rhsEvaluations": result.nfev}


if __name__ == "__main__":
    first = run(2e-5)
    second = run(1e-5)
    assert abs(first["contactTime_s"] - second["contactTime_s"]) <= 1e-12
    assert abs(first["preImpactVelocity_m_s"] - second["preImpactVelocity_m_s"]) <= 1e-10
    assert abs(first["totalVolume_m3"] - second["totalVolume_m3"]) <= 1e-14
    output = {"study": "Safety Shutdown independent constant-characteristic ideal lift oracle",
              "method": "SciPy DOP853 + Brent signed pressure + terminal seat event",
              "python": platform.python_version(), "scipy": scipy.__version__,
              "rtol": 1e-12, "atol": 1e-14, "maxStep_s": 1e-5,
              "physicalInputs": {"mass_kg": MASS, "damping_N_s_m": DAMPING,
                                 "stiffness_N_m": STIFFNESS, "pressureArea_m2": AREA,
                                 "density_kg_m3": RHO, "dischargeCoefficient": CD,
                                 "orificeWidth_m": WIDTH, "resistance_Pa_s_m3": RESISTANCE,
                                 "drive_Pa": DRIVE, "initialLift_m": INITIAL_LIFT},
              "result": second, "independentRefinement": first,
              "experimentalAndAssetQualification": "NOT_EVALUATED"}
    Path(__file__).with_name("coupled-oracle.json").write_text(json.dumps(output, indent=2) + "\n")
