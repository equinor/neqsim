"""Lumped cooldown after shut-in (pure Python, no JVM).

Model: a pipe section of fixed content cools exponentially towards the ambient temperature,

    T(t) = T_amb + (T_0 - T_amb) * exp(-t / tau),   tau = C' / (U_i * 2 * pi * r_i)

with ``C'`` the heat capacity per metre of the retained fluid plus the steel wall (insulation thermal mass is
excluded: conservative, it shortens the time). ``U_i`` is referenced to the INNER area. Natural convection inside the
fluid, latent heat of phase change, hydrate heat of formation, fluid redistribution and axial conduction are not
modelled; the result is a time to reach a temperature, not a time to plug.
"""
import math

from fa_units import FlowAssuranceError


def tau_s(rho_fluid_kg_m3, cp_fluid_j_kgk, id_m, steel_mass_kg_m, steel_cp_j_kgk, u_inner_w_m2k):
    """Cooldown time constant (s) of one metre of pipe."""
    for name, v in (("fluid density", rho_fluid_kg_m3), ("fluid cp", cp_fluid_j_kgk), ("inner diameter", id_m),
                    ("steel mass", steel_mass_kg_m), ("steel cp", steel_cp_j_kgk), ("U-value", u_inner_w_m2k)):
        if not (isinstance(v, (int, float)) and math.isfinite(v) and v > 0.0):
            raise FlowAssuranceError("cooldown: %s must be positive and finite, got %r" % (name, v))
    c_per_m = rho_fluid_kg_m3 * cp_fluid_j_kgk * math.pi * id_m ** 2 / 4.0 + steel_mass_kg_m * steel_cp_j_kgk
    return c_per_m / (u_inner_w_m2k * math.pi * id_m)


def time_to_temperature_s(t0_c, t_amb_c, t_crit_c, tau):
    """Time (s) for T to fall from ``t0_c`` to ``t_crit_c``.

    Returns 0.0 when the start is already at or below the criterion and ``math.inf`` when the criterion is at or below the
    ambient temperature (it is never reached).
    """
    if t0_c <= t_crit_c:
        return 0.0
    if t_crit_c <= t_amb_c:
        return math.inf
    return tau * math.log((t0_c - t_amb_c) / (t_crit_c - t_amb_c))


def temperature_at(t0_c, t_amb_c, tau, time_s):
    """Temperature (degC) after ``time_s`` seconds of cooldown."""
    return t_amb_c + (t0_c - t_amb_c) * math.exp(-time_s / tau)


def rk4_time_to_temperature_s(t0_c, t_amb_c, t_crit_c, tau, dt_s=60.0, t_max_s=1.0e8):
    """Independent numerical check: integrate dT/dt = -(T - T_amb)/tau with RK4 until ``t_crit_c`` is crossed."""
    if t0_c <= t_crit_c:
        return 0.0
    if t_crit_c <= t_amb_c:
        return math.inf
    def f(temp):
        return -(temp - t_amb_c) / tau
    t, temp = 0.0, t0_c
    while t < t_max_s:
        k1 = f(temp)
        k2 = f(temp + 0.5 * dt_s * k1)
        k3 = f(temp + 0.5 * dt_s * k2)
        k4 = f(temp + dt_s * k3)
        nxt = temp + dt_s * (k1 + 2.0 * k2 + 2.0 * k3 + k4) / 6.0
        if nxt <= t_crit_c:
            return t + dt_s * (temp - t_crit_c) / (temp - nxt)
        temp, t = nxt, t + dt_s
    return math.inf
