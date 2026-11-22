"""Independent ideal lift/contact prerequisite for NeqSim Safety Shutdown #4707.

No NeqSim implementation or vendor parameters are represented by this diagnostic.
SI inputs; translating equal-area ideal transducer in parallel with signed orifice.
"""
import csv
import hashlib
import json
import math
from pathlib import Path
import sys
import time

import numpy as np
from scipy.integrate import quad
from scipy.linalg import expm
from scipy.optimize import brentq


GATES = {
    "normalized_power_energy": 1e-12,
    "refinement_ratio": 1.7,
    "inertia_limit_relative": 1e-7,
    "event_relative": 1e-10,
    "event_absolute_SI": 1e-12,
}


def require_close(actual, expected, relative=1e-10, absolute=1e-12):
    """Apply frozen mixed SI event/limit acceptance."""
    if abs(actual - expected) > absolute + relative * abs(expected):
        raise AssertionError((actual, expected))


def midpoint_step(y, v, dt, mass, damping, stiffness, force):
    """Implicit midpoint mechanics; pressure force held over this interval."""
    mean_v = (mass * v + 0.5 * dt * (force - stiffness * y)) / (
        mass + 0.5 * damping * dt + 0.25 * stiffness * dt * dt
    )
    return y + dt * mean_v, 2 * mean_v - v, mean_v


def exact_state(t, mass, damping, stiffness, force):
    """Independent augmented matrix exponential for initial rest under constant force."""
    operator = np.array([
        [0.0, 1.0, 0.0],
        [-stiffness / mass, -damping / mass, force / mass],
        [0.0, 0.0, 0.0],
    ])
    return (expm(operator * t) @ np.array([0.0, 0.0, 1.0]))[:2]


def energy(y, v, mass, stiffness, preload=0.0):
    """Kinetic plus spring/preload potential energy, J."""
    return 0.5 * mass * v * v + 0.5 * stiffness * y * y + preload * y


def run_free(dt, mass=0.2, damping=2.0, stiffness=2000.0, duration=0.2, backward=False):
    """Integrate mechanics and independently accumulate pressure work/damping loss."""
    area = 1e-4
    differential = 2e5
    force = area * differential
    count = int(round(duration / dt))
    y = v = work = loss = maximum = numerical_loss = 0.0
    for _ in range(count):
        old_energy = energy(y, v, mass, stiffness)
        if backward:
            v1 = (mass * v + dt * (force - stiffness * y)) / (mass + damping * dt + stiffness * dt * dt)
            y1 = y + dt * v1
            mean_v = v1
            interval_numerical = 0.5 * mass * (v1 - v) ** 2 + 0.5 * stiffness * (y1 - y) ** 2
        else:
            y1, v1, mean_v = midpoint_step(y, v, dt, mass, damping, stiffness, force)
            interval_numerical = 0.0
        interval_work = differential * area * (y1 - y)
        interval_loss = damping * dt * mean_v * mean_v
        residual = energy(y1, v1, mass, stiffness) - old_energy - interval_work + interval_loss + interval_numerical
        scale = max(1e-20, abs(old_energy), abs(interval_work), abs(interval_loss),
                    abs(energy(y1, v1, mass, stiffness)))
        maximum = max(maximum, abs(residual) / scale)
        assert interval_loss >= 0.0
        assert y1 >= -1e-12 and y1 < 0.05, "Free benchmark must not hide contact"
        work += interval_work
        loss += interval_loss
        numerical_loss += interval_numerical
        y, v = y1, v1
    assert maximum <= GATES["normalized_power_energy"], maximum
    reference_y, reference_v = exact_state(duration, mass, damping, stiffness, force)
    state_error = math.hypot((y - reference_y) / 0.01, (v - reference_v) / 1.0)
    return {
        "dt_s": dt, "mass_kg": mass, "damping_N_s_m": damping,
        "lift_m": y, "velocity_m_s": v, "exact_lift_m": float(reference_y),
        "exact_velocity_m_s": float(reference_v), "normalized_state_error": state_error,
        "hydraulic_mechanical_work_J": work, "damping_loss_J": loss,
        "final_mechanical_energy_J": energy(y, v, mass, stiffness),
        "numerical_energy_loss_J": numerical_loss,
        "max_normalized_step_energy_residual": maximum,
    }


def main(output):
    """Execute all frozen controls and retain every raw row plus explicit qualification limits."""
    started = time.perf_counter()
    output.mkdir(parents=True, exist_ok=True)
    refinement = [run_free(dt) for dt in (1e-3, 5e-4, 2.5e-4, 1.25e-4)]
    for first, second in zip(refinement, refinement[1:]):
        ratio = first["normalized_state_error"] / second["normalized_state_error"]
        second["previous_error_ratio"] = ratio
        assert ratio >= GATES["refinement_ratio"], ratio
    nearby = [run_free(2.5e-4, m, c, duration=0.1)
              for m in (0.05, 0.2, 0.8) for c in (0.0, 2.0, 10.0)]

    backward_refinement = [run_free(dt, backward=True) for dt in (1e-4, 5e-5, 2.5e-5, 1.25e-5)]
    for first, second in zip(backward_refinement, backward_refinement[1:]):
        ratio = first["normalized_state_error"] / second["normalized_state_error"]
        second["previous_error_ratio"] = ratio
        assert ratio >= GATES["refinement_ratio"], ratio
    stiffness_rows = []
    for scheme in ("implicit_midpoint", "backward_Euler"):
        y = v = 0.0
        m, c, k, f, dt = 1e-12, 2.0, 2000.0, 20.0, 1.25e-4
        for _ in range(1600):
            if scheme == "implicit_midpoint":
                y, v, _ = midpoint_step(y, v, dt, m, c, k, f)
            else:
                v = (m * v + dt * (f - k * y)) / (m + c * dt + k * dt * dt)
                y += dt * v
        expected_y = f / k * (1 - math.exp(-k * 0.2 / c))
        expected_v = f / c * math.exp(-k * 0.2 / c)
        y_error = abs(y - expected_y) / (f / k)
        v_error = abs(v - expected_v) / (f / c)
        status = "PASS" if max(y_error, v_error) <= 1e-7 else "FAIL"
        stiffness_rows.append({"scheme": scheme, "lift_m": y, "velocity_m_s": v,
                               "relative_lift_error": y_error, "normalized_velocity_error": v_error,
                               "massless_damped_gate": status})
    assert stiffness_rows[1]["massless_damped_gate"] == "PASS"

    # Two-port power split for a declared constant effective pressure area.
    area = 1e-4
    rows = []
    for lift in (0.0, 0.001, 0.005):
        for speed in (-0.2, 0.0, 0.2):
            for dp in (-2e5, -1.0, 0.0, 1.0, 2e5):
                jet = 0.65 * 0.05 * lift * math.copysign(math.sqrt(2 * abs(dp) / 1000), dp)
                displacement = area * speed
                flow = jet + displacement
                port_power = dp * flow
                orifice_loss = dp * jet
                mechanical_power = dp * area * speed
                residual = port_power - orifice_loss - mechanical_power
                normalized = abs(residual) / max(1.0, abs(port_power), orifice_loss, abs(mechanical_power))
                assert orifice_loss >= 0 and normalized <= 1e-12
                rows.append({"lift_m": lift, "speed_m_s": speed, "differential_Pa": dp,
                             "jet_flow_m3_s": jet, "displacement_flow_m3_s": displacement,
                             "total_port_flow_m3_s": flow, "orifice_loss_W": orifice_loss,
                             "mechanical_power_W": mechanical_power, "normalized_power_residual": normalized})

    # A locked orifice with a pressure-driven moving transducer is the decisive negative control.
    # No model fit is used: the exact force-step energy identity follows from virtual work.
    mass, damping, stiffness, force, duration = 0.2, 2.0, 2000.0, 20.0, 0.2
    y, v = exact_state(duration, mass, damping, stiffness, force)
    exact_loss = quad(lambda t: damping * exact_state(t, mass, damping, stiffness, force)[1] ** 2,
                      0.0, duration, epsabs=1e-13, epsrel=1e-13)[0]
    exact_energy = energy(y, v, mass, stiffness)
    exact_work = force * y
    require_close(exact_work, exact_energy + exact_loss)
    negative_control = {
        "missing_swept_flow_model": "FAIL",
        "pressure_work_reported_if_Qjet_only_J": 0.0,
        "required_pressure_work_J": float(exact_work),
        "mechanical_storage_J": float(exact_energy), "physical_damping_loss_J": exact_loss,
        "unaccounted_energy_J": float(exact_energy + exact_loss),
        "interpretation": "Ideal equal-area translating transducer only; not a defect claim against static CheckValve",
    }
    assert exact_work > 0.0

    # Dimensionally scaled limiting controls use the update formula, not an inaccurate finite-time static claim.
    small_mass = 1e-12
    ys, vs, _ = midpoint_step(0, 0, 0.01, small_mass, 0, 2000, 20)
    # At m -> 0, midpoint position is the static balance, not the endpoint (an undamped algebraic limit).
    static_relative = abs(0.5 * ys - 20 / 2000) / (20 / 2000)
    assert static_relative <= 1e-7
    large_mass = 1e9
    yl, vl, _ = midpoint_step(0, 0, 0.01, large_mass, 0, 2000, 20)
    inertial_y = 0.5 * 20 / large_mass * 0.01 ** 2
    inertia_relative = abs(yl - inertial_y) / inertial_y
    assert inertia_relative <= 1e-7
    limits = {"small_mass_kg": small_mass, "midpoint_static_relative_error": static_relative,
              "large_mass_kg": large_mass, "inertial_displacement_relative_error": inertia_relative,
              "qualification": "One-step scaled limits; no finite-time zero-inertia convergence claim"}

    # Constant-force free flight to each physical stop; exact event plus restitution energy, no clipping step.
    impacts = []
    for stop in ("seat", "upper"):
        direction = -1 if stop == "seat" else 1
        acceleration = direction * 5.0
        initial_v = direction * 0.05
        distance = direction * 0.005
        t_exact = (-initial_v + direction * math.sqrt(initial_v ** 2 + 2 * acceleration * distance)) / acceleration
        t_root = brentq(lambda t: initial_v * t + 0.5 * acceleration * t * t - distance,
                        0, 0.2, xtol=5e-15)
        require_close(t_root, t_exact)
        before = initial_v + acceleration * t_root
        for restitution in (0.0, 0.25, 1.0):
            after = -restitution * before
            contact_loss = 0.5 * mass * (before * before - after * after)
            expected_loss = 0.5 * mass * before * before * (1 - restitution ** 2)
            require_close(contact_loss, expected_loss)
            assert contact_loss >= -1e-12
            impacts.append({"stop": stop, "event_time_s": t_root, "analytic_time_s": t_exact,
                            "velocity_before_m_s": before, "velocity_after_m_s": after,
                            "restitution": restitution, "contact_loss_J": contact_loss})

    # Reverse jet and swept flow occur while the ideal disc is still open and closing.
    initial_lift, acceleration, dp = 0.005, -5.0, -1e4
    closure_time = math.sqrt(-2 * initial_lift / acceleration)
    jet_factor = -0.65 * 0.05 * math.sqrt(2 * abs(dp) / 1000)
    jet_volume = quad(lambda t: jet_factor * (initial_lift + 0.5 * acceleration * t * t),
                      0, closure_time, epsabs=1e-15)[0]
    swept_volume = area * (0 - initial_lift)
    expected_jet_volume = jet_factor * (2 / 3) * initial_lift * closure_time
    require_close(jet_volume, expected_jet_volume)
    assert jet_volume < 0 and swept_volume < 0
    reverse = {"closure_time_s": closure_time, "reverse_jet_volume_m3": jet_volume,
               "swept_volume_m3": swept_volume, "total_reverse_volume_m3": jet_volume + swept_volume,
               "disc_velocity_before_seating_m_s": acceleration * closure_time,
               "acoustic_pressure_loads": "NOT_EVALUATED"}

    results = {"study": "Safety Shutdown ideal lift/contact energy prerequisite",
               "gates": GATES, "benchmark_execution": "PASS", "candidate_method_qualification": "PARTIAL",
               "production_NeqSim_coupling": "NOT_EVALUATED", "experimental_validation": "NOT_EVALUATED",
               "full_pump_trip_slam_asset_qualification": "NOT_EVALUATED", "refinement": refinement,
               "nearby": nearby, "backward_Euler_refinement": backward_refinement,
               "massless_damped_stiffness": stiffness_rows,
               "negative_control": negative_control, "inertia_limits": limits,
               "impacts": impacts, "reverse_before_seat": reverse}
    (output / "results.json").write_text(json.dumps(results, indent=2) + "\n")
    with (output / "signed-power.csv").open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)
    digests = {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
               for p in sorted(output.iterdir()) if p.name in ("results.json", "signed-power.csv")}
    print(json.dumps({"results": digests, "elapsed_s": time.perf_counter() - started,
                      "signed_power_cases": len(rows), "refinement_cases": len(refinement),
                      "nearby_cases": len(nearby), "impact_cases": len(impacts)}))


if __name__ == "__main__":
    main(Path(sys.argv[1]))
