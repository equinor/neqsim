"""Event model v2: offload (tank emptied under inert-gas blanket) -> standing (closed, empty) -> inert-gas purge to vent.

Phases (all with the same species vector: O2, CO2, N2, H2O + hydrocarbon components of the Njord crude EOS)
  0 offload : crude leaves, inert gas (IG) from the IGG replaces it; HC evaporates from the crude surface (conductance kA_s)
              into the growing ullage. Tank closed (no outflow).
  1 standing: closed empty tank at the measured gas temperature; heel/clingage exchanges HC with the gas (conductance kA_h,
              evaporation or condensation).
  2 purge   : IG at Q_nm3h through N mixing stages in series to the vent (measured IGG run window); heel keeps evaporating.

The HC fraction and composition at the start of the purge are therefore results of the model, not inputs.
"""
import numpy as np

import gasfree_model as G

SPECIES, HC, IDX, MW = G.SPECIES, G.HC, G.IDX, G.MW
HC_IDX, NMVOC_IDX, C3P_IDX = G.HC_IDX, G.NMVOC_IDX, G.C3P_IDX
I_C1, I_C2 = G.I_C1, G.I_C2
R = G.R_BAR
NM3 = G.NM3_PER_KMOL

DEF = dict(
    V_gross=16262.0, level0=0.90, t_off=11.0, T_c=27.0, t_stand=24.0, T_g=15.0, t_purge=24.0,
    P=1.06, Q_nm3h=8000.0, o2_ig=0.03, T_ig_out=10.0, n_stages=1, pw=1.0, basis="base",
    f_sat0=0.7, kA_s=10000.0, V_heel=20.0, kA_h=1000.0, rho_heel=810.0, mw_heel=177.0,
)


def _h2o(T_C, P):
    return float(G.psat_h2o_bar(T_C) / P)


def ig_vector(p):
    h = _h2o(p["T_ig_out"], p["P"])
    o2 = p["o2_ig"]
    co2 = 0.14 * (20.9 - 100 * o2) / (20.9 - 3.0)
    y = np.zeros(len(SPECIES))
    y[IDX["O2"]], y[IDX["CO2"]], y[IDX["N2"]] = o2 * (1 - h), co2 * (1 - h), (1 - o2 - co2) * (1 - h)
    y[IDX["H2O"]] = h
    return y


def simulate_event(**kw):
    p = dict(DEF)
    p.update(kw)
    tab = G.get_tables(p["basis"])
    P = p["P"]
    V = p["V_gross"]
    x0, ps_c = tab.heel(p["pw"], p["T_c"])
    _, ps_g = tab.heel(p["pw"], p["T_g"])
    ig = ig_vector(p)
    base = ig.copy()
    base[HC_IDX] = 0.0
    base /= base.sum()

    # ---------- phase 0: offload ----------
    Tc_K = 273.15 + p["T_c"]
    y_sat_c = float(np.dot(ps_c, x0)) / P
    y_u0 = min(p["f_sat0"] * y_sat_c, 0.95)
    y_eq_c = ps_c * x0
    y_eq_c = y_eq_c / y_eq_c.sum()
    V_u0 = (1.0 - p["level0"]) * V
    N_u0 = P * V_u0 / (R * Tc_K)
    comp = (1 - y_u0) * base
    comp[HC_IDX] = y_u0 * y_eq_c
    n = N_u0 * comp
    n_init = n.copy()
    ig_added = np.zeros(len(SPECIES))
    evap_off = np.zeros(len(HC))
    nsteps = 300
    dt = p["t_off"] / nsteps
    dN = P / (R * Tc_K) * (V * p["level0"] / p["t_off"]) * dt
    for _ in range(nsteps):
        N = n.sum()
        y = n / N
        pp = y[HC_IDX] * P
        e = p["kA_s"] / (R * Tc_K) * (ps_c * x0 - pp)  # kmol/h; crude is an infinite reservoir
        e = np.maximum(e, -n[HC_IDX] / dt * 0.5)
        E = float(e.sum() * dt)
        f_ig = max(dN - E, 0.0)
        n[HC_IDX] += e * dt
        n += f_ig * ig
        ig_added += f_ig * ig
        evap_off += e * dt
    y_end_off = n / n.sum()
    hc_end_off = float(y_end_off[HC_IDX].sum())

    # ---------- heel ----------
    heel_kmol = p["V_heel"] * p["rho_heel"] / p["mw_heel"]
    m = heel_kmol * x0
    Tg_K = 273.15 + p["T_g"]

    # ---------- phase 1: standing ----------
    dts = 0.25
    nst_steps = int(max(p["t_stand"], 0.0) / dts)
    evap_stand = np.zeros(len(HC))
    for _ in range(nst_steps):
        N = n.sum()
        y = n / N
        pp = y[HC_IDX] * P
        xl = m / m.sum() if m.sum() > 1e-12 else x0
        e = p["kA_h"] / (R * Tg_K) * (ps_g * xl - pp)
        e = np.maximum(e, -n[HC_IDX] / dts * 0.5)
        e = np.minimum(e, m / dts)
        n[HC_IDX] += e * dts
        evap_stand += e * dts
        m = np.maximum(m - e * dts, 0.0)
    y_start = n / n.sum()
    hc_start = float(y_start[HC_IDX].sum())
    hc_comp_start = y_start[HC_IDX] / y_start[HC_IDX].sum() if hc_start > 0 else y_eq_c

    # ---------- phase 2: purge ----------
    nst = int(p["n_stages"])
    N_gas = n.sum()
    Ns = N_gas / nst
    stages = np.tile(n / nst, (nst, 1))
    Q = p["Q_nm3h"] / NM3
    tau = N_gas / Q
    dt = min(0.004 * tau, 0.02)
    steps = int(round(p["t_purge"] / dt))
    dt = p["t_purge"] / steps
    cum = np.zeros(len(SPECIES))
    ig_purge = np.zeros(len(SPECIES))
    evap_purge = np.zeros(len(HC))
    rec_t, rec_y = [], []
    cum_rec = []
    for k in range(steps):
        y1 = stages[0] / Ns
        pp = y1[HC_IDX] * P
        xl = m / m.sum() if m.sum() > 1e-12 else x0
        e = p["kA_h"] / (R * Tg_K) * (ps_g * xl - pp)
        e = np.maximum(e, -stages[0][HC_IDX] / dt * 0.5)
        e = np.minimum(e, m / dt)
        E = float(e.sum())
        F_out = Q + E
        old = stages.copy()
        stages[0] += (Q * ig - F_out * old[0] / Ns) * dt
        stages[0][HC_IDX] += e * dt
        for s in range(1, nst):
            stages[s] += (F_out * old[s - 1] / Ns - F_out * old[s] / Ns) * dt
        stages = np.maximum(stages, 0.0)
        m = np.maximum(m - e * dt, 0.0)
        out = F_out * old[-1] / Ns
        cum += out * dt
        ig_purge += Q * ig * dt
        evap_purge += e * dt
        if k % 5 == 0:
            rec_t.append((k + 1) * dt)
            rec_y.append(stages[-1] / Ns)
            cum_rec.append(cum.copy())
    rec_y = np.array(rec_y)
    final_gas = stages.sum(axis=0)
    return dict(
        params=p, tau_h=tau, N_gas_kmol=N_gas, y_sat_c=y_sat_c, y_u0=y_u0, hc_end_offload=hc_end_off, hc_start_purge=hc_start,
        hc_comp_start=hc_comp_start, t=np.array(rec_t), y=rec_y, cum_kmol=cum, cum_rec=np.array(cum_rec), final_gas_kmol=final_gas,
        heel_left_kmol=float(m.sum()), heel_kmol0=float(heel_kmol),
        n_init=n_init, ig_off=ig_added, evap_off=evap_off, evap_stand=evap_stand, ig_purge=ig_purge, evap_purge=evap_purge,
        heel_m0=heel_kmol * x0, heel_m_end=m,
    )


def summarize_event(r):
    cum = r["cum_kmol"]
    tot = cum.sum()
    t = r["t"]
    y = r["y"]
    out = dict(
        vent_kmol=float(tot),
        CH4_mol_pct=100 * float(cum[I_C1] / tot), ethane_mol_pct=100 * float(cum[I_C2] / tot),
        nmVOC_mol_pct=100 * float(cum[NMVOC_IDX].sum() / tot), nmVOC_C3plus_mol_pct=100 * float(cum[C3P_IDX].sum() / tot),
        HC_mol_pct=100 * float(cum[HC_IDX].sum() / tot), O2_mol_pct=100 * float(cum[IDX["O2"]] / tot),
        CO2_mol_pct=100 * float(cum[IDX["CO2"]] / tot), N2_mol_pct=100 * float(cum[IDX["N2"]] / tot),
        H2O_mol_pct=100 * float(cum[IDX["H2O"]] / tot),
        CH4_kg=float(cum[I_C1] * MW[I_C1]), ethane_kg=float(cum[I_C2] * MW[I_C2]),
        nmVOC_kg=float((cum[NMVOC_IDX] * MW[NMVOC_IDX]).sum()), nmVOC_C3plus_kg=float((cum[C3P_IDX] * MW[C3P_IDX]).sum()),
        peak_CH4_mol_pct=100 * float(y[:, I_C1].max()), peak_nmVOC_mol_pct=100 * float(y[:, NMVOC_IDX].sum(axis=1).max()),
        peak_HC_mol_pct=100 * float(y[:, HC_IDX].sum(axis=1).max()),
        hc_start_purge_pct=100 * r["hc_start_purge"], hc_end_offload_pct=100 * r["hc_end_offload"],
        y_sat_c_pct=100 * r["y_sat_c"], tau_h=r["tau_h"],
        residual_gas_HC_kg=float((r["final_gas_kmol"][HC_IDX] * MW[HC_IDX]).sum()),
        residual_gas_CH4_kg=float(r["final_gas_kmol"][I_C1] * MW[I_C1]),
        residual_gas_nmVOC_kg=float((r["final_gas_kmol"][NMVOC_IDX] * MW[NMVOC_IDX]).sum()),
        residual_gas_HC_mol_pct=100 * float(r["final_gas_kmol"][HC_IDX].sum() / r["final_gas_kmol"].sum()),
        heel_left_pct=100 * r["heel_left_kmol"] / r["heel_kmol0"] if r["heel_kmol0"] > 0 else 0.0,
        IG_tank_volumes=float(r["params"]["Q_nm3h"] * r["params"]["t_purge"] / NM3 / r["N_gas_kmol"]),
        first3h_CH4_mol_pct=None, first3h_nmVOC_mol_pct=None,
    )
    # first 3 h flow-weighted composition
    ci = r["cum_rec"]
    i3 = int(np.searchsorted(t, 3.0))
    i3 = min(max(i3, 1), len(t) - 1)
    c3 = ci[i3]
    out["first3h_CH4_mol_pct"] = 100 * float(c3[I_C1] / c3.sum())
    out["first3h_nmVOC_mol_pct"] = 100 * float(c3[NMVOC_IDX].sum() / c3.sum())
    return out
