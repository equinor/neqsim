"""Gas-freeing (inert purge + air ventilation) model for the Njord B cargo tanks.

Physical model
--------------
* Tank gas space = N-stage well-mixed tanks in series (N=1: perfectly mixed jet-driven tank; N>1: increasingly
  plug-flow like). Constant total gas inventory (constant T and P), ideal-gas mixture.
* Inlet: clean inert gas line from the IGG (10 000 Nm3/h, O2 1-5 vol%, CO2 ~14 %, N2 balance, water saturated)
  during the purge phase; fresh air (IGG in fresh-air mode, 10 000 Nm3/h) during the ventilation phase.
* Initial gas: inert gas with a hydrocarbon fraction y_HC0 whose composition is the equilibrium vapour above the
  residual crude (heel) at the tank gas temperature (NeqSim PR78 / Uleberg EOS, tables from 20_build_tables.py).
* Residual crude/heel liquid evaporates into the first stage with a gas-side conductance kA:
      e_i = kA/(R T) * (Psat_eff_i * x_i - p_i)
  and weathers (light ends depleted) as it evaporates.
* Phase 1 (purge) ends when the HC fraction of the vent gas is <= 2 vol % (IMO/IGG guideline, IOM 8.10);
  phase 2 (air) ends when O2 >= 20.8 vol % and HC <= 1 % LEL (0.014 vol %), or after max_air_volumes tank volumes.
"""
import json
import os

import numpy as np
from scipy.interpolate import RegularGridInterpolator

HERE = os.path.dirname(os.path.abspath(__file__))
TABLES = json.load(open(os.path.join(HERE, "data", "psat_tables.json"), encoding="utf-8"))
NAMES = TABLES["names"]
HC = [n for n in NAMES if n not in ("N2", "CO2")]  # hydrocarbon components tracked in the gas
MW_HC = {  # kg/kmol (EOS sheet)
    "C1": 16.04, "C2": 30.07, "C3": 44.1, "iC4": 58.12, "nC4": 58.12, "iC5": 72.15, "nC5": 72.15, "C6": 86.18,
    "C7P": 96.0, "C8P": 107.0, "C9P": 121.0, "C10P": 158.37, "C15P": 237.97, "C21P": 340.67, "C31P": 515.96,
    "C51P": 815.11, "C7A": 96.0, "C8A": 107.0, "C9A": 121.0, "C10A": 159.18, "C15A": 239.14, "C21A": 343.88,
    "C31A": 527.49, "C51A": 836.63,
}
SPECIES = ["O2", "CO2", "N2", "H2O"] + HC
MW = np.array([32.0, 44.01, 28.01, 18.015] + [MW_HC[n] for n in HC])
IDX = {s: i for i, s in enumerate(SPECIES)}
I_C1 = IDX["C1"]
I_C2 = IDX["C2"]
HC_IDX = np.array([IDX[n] for n in HC])
NMVOC_IDX = np.array([IDX[n] for n in HC if n != "C1"])
C3P_IDX = np.array([IDX[n] for n in HC if n not in ("C1", "C2")])
R_BAR = 0.0831446  # m3 bar / (kmol K)
NM3_PER_KMOL = 22.414  # 0 C, 1.01325 bara


def psat_h2o_bar(T_C):
    """Water vapour pressure (bara), Magnus-type correlation (valid 0-50 C)."""
    return 6.1094e-3 * np.exp(17.625 * T_C / (T_C + 243.04))


class Tables:
    """Bilinear interpolation of heel composition and Psat_eff over (P_w, T_gas) for a crude basis."""

    def __init__(self, basis):
        e = TABLES["bases"][basis]
        self.pw = np.array(TABLES["pw_grid"])
        self.T = np.array(TABLES["T_grid"])
        nP, nT = len(self.pw), len(self.T)
        self.x0 = np.zeros((nP, len(HC)))
        self.ps = np.zeros((nP, nT, len(HC)))
        for ip, pw in enumerate(self.pw):
            ent = e["pw"][str(float(pw))]
            self.x0[ip] = [ent["x0"][n] for n in HC]
            for it, T in enumerate(self.T):
                self.ps[ip, it] = [ent["psat_eff"][str(float(T))][n] for n in HC]
        self._ifx = RegularGridInterpolator((self.pw,), self.x0)
        self._ifp = RegularGridInterpolator((self.pw, self.T), self.ps)
        self.tvp40 = e["tvp_C"]["40"]

    def heel(self, pw, T):
        pw = float(np.clip(pw, self.pw[0], self.pw[-1]))
        T = float(np.clip(T, self.T[0], self.T[-1]))
        x0 = self._ifx([pw])[0]
        x0 = x0 / x0.sum()
        ps = self._ifp([[pw, T]])[0]
        return x0, ps


_TABLE_CACHE = {}


def get_tables(basis):
    if basis not in _TABLE_CACHE:
        _TABLE_CACHE[basis] = Tables(basis)
    return _TABLE_CACHE[basis]


DEFAULTS = dict(
    V_gross=16262.0,       # m3 gas volume of the emptied tank
    P=1.06,                # bara tank pressure
    T_gas=20.0,            # C tank gas temperature
    y_hc0=0.10,            # mole fraction HC in the tank gas at start of purge
    pw=0.95,               # weathering pressure of the heel liquid (bara)
    V_heel=10.0,           # m3 residual crude
    kA=1000.0,             # m3/h gas-side evaporation conductance
    n_stages=1,            # mixing stages
    Q_nm3h=10000.0,        # IGG capacity, Nm3/h
    o2_ig=0.03,            # IG O2 (dry), mole fraction
    co2_ig=0.14,           # IG CO2 (dry) at 3 % O2
    T_ig_out=15.0,         # C IG cooling tower outlet (water saturated)
    T_air=8.0, rh_air=0.85,
    basis="base",
    purge_hc_stop=0.02,
    o2_target=0.208,
    lel_stop=1.4e-4,       # 1 % LEL of ~1.4 vol %
    max_air_volumes=14.0,
    max_purge_volumes=25.0,
    dtheta=0.01,           # step in tank volumes
    rho_heel=810.0, mw_heel=177.0,
)


def ig_composition(p):
    h = psat_h2o_bar(p["T_ig_out"]) / p["P"]
    o2 = p["o2_ig"]
    co2 = 14.0 * (20.9 - 100 * o2) / (20.9 - 3.0) / 100.0  # IGG: ~14 vol % CO2 at 3 % O2, scaled with O2 (combustion balance)
    n2 = 1.0 - o2 - co2
    y = np.zeros(len(SPECIES))
    y[IDX["O2"]], y[IDX["CO2"]], y[IDX["N2"]] = o2 * (1 - h), co2 * (1 - h), n2 * (1 - h)
    y[IDX["H2O"]] = h
    return y


def air_composition(p):
    h = p["rh_air"] * psat_h2o_bar(p["T_air"]) / p["P"]
    y = np.zeros(len(SPECIES))
    y[IDX["O2"]], y[IDX["CO2"]] = 0.2095 * (1 - h), 0.0004 * (1 - h)
    y[IDX["N2"]] = (1.0 - 0.2095 - 0.0004) * (1 - h)  # N2 + Ar lumped as N2
    y[IDX["H2O"]] = h
    return y


def simulate(**kw):
    """Run one gas-freeing simulation; returns dict with time series (per output step) and totals."""
    p = dict(DEFAULTS)
    p.update(kw)
    tab = get_tables(p["basis"])
    T_K = 273.15 + p["T_gas"]
    N_gas = p["P"] * p["V_gross"] / (R_BAR * T_K)  # kmol
    Q = p["Q_nm3h"] / NM3_PER_KMOL  # kmol/h
    tau = N_gas / Q  # h per tank volume
    nst = int(p["n_stages"])
    Ns = N_gas / nst

    x0, ps = tab.heel(p["pw"], p["T_gas"])
    # saturation limit of the initial HC fraction
    p_sat_tot = float(np.dot(ps, x0))
    y_sat = p_sat_tot / p["P"]
    y_hc0 = min(p["y_hc0"], 0.9 * y_sat)
    # initial HC composition: equilibrium vapour above the heel
    y_eq = ps * x0
    y_eq = y_eq / y_eq.sum()
    n0 = np.zeros((nst, len(SPECIES)))
    ig = ig_composition(p)
    base = ig.copy()
    base[HC_IDX] = 0.0
    base = base / base.sum()
    comp0 = (1.0 - y_hc0) * base
    comp0[HC_IDX] = y_hc0 * y_eq
    n0[:] = Ns * comp0
    m_heel = np.zeros(len(HC))
    heel_kmol = p["V_heel"] * p["rho_heel"] / p["mw_heel"]
    m_heel[:] = heel_kmol * x0
    m_heel0 = m_heel.copy()
    air = air_composition(p)

    dth = p["dtheta"]
    dt = dth * tau  # h
    n = n0.copy()
    t = 0.0
    phase = 1
    rec_t, rec_phase, rec_y, rec_cum = [], [], [], []
    cum = np.zeros(len(SPECIES))
    cum_by_phase = {1: np.zeros(len(SPECIES)), 2: np.zeros(len(SPECIES))}
    mol_by_phase = {1: 0.0, 2: 0.0}
    t_switch = None
    theta_phase_start = 0.0
    theta = 0.0
    stop = False
    evap_total = np.zeros(len(HC))
    step = 0
    tot_in = np.zeros(len(SPECIES))
    while not stop:
        inflow_comp = ig if phase == 1 else air
        # evaporation in stage 1
        y1 = n[0] / Ns
        e = np.zeros(len(HC))
        if p["V_heel"] > 0 and m_heel.sum() > 1e-12:
            xl = m_heel / m_heel.sum()
            pp = y1[HC_IDX] * p["P"]
            e = p["kA"] / (R_BAR * T_K) * np.maximum(ps * xl - pp, 0.0)  # kmol/h
            e = np.minimum(e * dt, m_heel) / dt
        E = e.sum()
        flows_in = Q * inflow_comp
        # stage 1
        F_in = Q
        F_out = F_in + E
        yin = inflow_comp
        dn = np.zeros_like(n)
        dn[0] = F_in * yin - F_out * (n[0] / Ns)
        dn[0, HC_IDX] += e
        for s in range(1, nst):
            dn[s] = F_out * (n[s - 1] / Ns) - F_out * (n[s] / Ns)
        n_old = n
        n = n + dn * dt
        n = np.maximum(n, 0.0)
        m_heel = np.maximum(m_heel - e * dt, 0.0)
        evap_total += e * dt
        out_flow = F_out * (n_old[-1] / Ns)  # kmol/h by species (consistent with the explicit Euler step)
        out_flow_tot = F_out
        cum += out_flow * dt
        cum_by_phase[phase] += out_flow * dt
        mol_by_phase[phase] += out_flow_tot * dt
        tot_in += flows_in * dt
        t += dt
        theta += dth
        yo = n[-1] / Ns
        if step % 5 == 0:
            rec_t.append(t)
            rec_phase.append(phase)
            rec_y.append(yo.copy())
            rec_cum.append(cum.copy())
        step += 1
        y_hc = yo[HC_IDX].sum()
        if phase == 1:
            if y_hc <= p["purge_hc_stop"] or (theta - theta_phase_start) >= p["max_purge_volumes"]:
                phase = 2
                t_switch = t
                theta_phase_start = theta
        else:
            if (yo[IDX["O2"]] >= p["o2_target"] and y_hc <= p["lel_stop"]) or (theta - theta_phase_start) >= p["max_air_volumes"]:
                stop = True
    rec_y = np.array(rec_y)
    rec_cum = np.array(rec_cum)
    res = dict(
        params=p, t_h=np.array(rec_t), phase=np.array(rec_phase), y=rec_y, cum_kmol=rec_cum, tau_h=tau, N_gas_kmol=N_gas,
        t_switch_h=t_switch, t_end_h=t, y_hc0_used=y_hc0, y_sat=y_sat, y_eq=y_eq,
        cum_by_phase=cum_by_phase, mol_by_phase=mol_by_phase, evap_kmol=evap_total, heel_kmol0=heel_kmol,
        tot_in_kmol=tot_in, n_final=n.sum(axis=0), n_initial=n0.sum(axis=0), heel_left=m_heel, heel0=m_heel0,
    )
    return res


def summarize(res):
    """Volume-averaged compositions, peak values and cumulative masses of one run."""
    p = res["params"]
    out = {}
    for label, cum in (("purge", res["cum_by_phase"][1]), ("air", res["cum_by_phase"][2]),
                       ("total", res["cum_by_phase"][1] + res["cum_by_phase"][2])):
        tot = cum.sum()
        if tot <= 0:
            continue
        mol = cum
        out[label] = {
            "vent_kmol": float(tot),
            "CH4_mol_pct": 100 * float(mol[I_C1] / tot),
            "ethane_mol_pct": 100 * float(mol[I_C2] / tot),
            "nmVOC_mol_pct": 100 * float(mol[NMVOC_IDX].sum() / tot),
            "nmVOC_C3plus_mol_pct": 100 * float(mol[C3P_IDX].sum() / tot),
            "HC_mol_pct": 100 * float(mol[HC_IDX].sum() / tot),
            "O2_mol_pct": 100 * float(mol[IDX["O2"]] / tot),
            "CO2_mol_pct": 100 * float(mol[IDX["CO2"]] / tot),
            "N2_mol_pct": 100 * float(mol[IDX["N2"]] / tot),
            "H2O_mol_pct": 100 * float(mol[IDX["H2O"]] / tot),
            "CH4_kg": float(mol[I_C1] * MW[I_C1]),
            "ethane_kg": float(mol[I_C2] * MW[I_C2]),
            "nmVOC_kg": float((mol[NMVOC_IDX] * MW[NMVOC_IDX]).sum()),
            "nmVOC_C3plus_kg": float((mol[C3P_IDX] * MW[C3P_IDX]).sum()),
        }
    y = res["y"]
    hc = y[:, HC_IDX].sum(axis=1)
    out["peak"] = {
        "CH4_mol_pct": 100 * float(y[:, I_C1].max()),
        "nmVOC_mol_pct": 100 * float(y[:, NMVOC_IDX].sum(axis=1).max()),
        "HC_mol_pct": 100 * float(hc.max()),
    }
    out["t_purge_h"] = res["t_switch_h"]
    out["t_total_h"] = res["t_end_h"]
    out["tau_h"] = res["tau_h"]
    out["y_hc0_used_pct"] = 100 * res["y_hc0_used"]
    out["y_sat_pct"] = 100 * res["y_sat"]
    return out
