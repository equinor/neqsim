"""Standalone column-study script.

Mirrors the column_study.ipynb notebook but runs as a plain Python script:
    - Builds a 24-component SRK fluid with UniSim Tc/Pc/omega overrides
    - Creates main feed + top reflux streams
    - Builds and runs the 20VE105_205 stabilizer column (10 trays + reboiler,
      no internal condenser — external reflux comes in as the top feed)
    - Prints convergence diagnostics
    - Compares NeqSim overhead/bottoms with UniSim reference values
    - Prints overall mass balance and energy balance
    - Plots the temperature profile and internal vapour/liquid flows
      (saves to column_study_profile.png and also shows it)

Run:
    python examples/notebooks/column_study.py
or
    python examples/notebooks/column_study.py --no-show       # don't open a window
    python examples/notebooks/column_study.py --out figs/col.png

Tray numbering used in this script is the UniSim TOP-DOWN convention:
    tray 1  = top tray  (just below external condenser)
    tray 10 = bottom tray (just above reboiler)
    reboiler is below tray 10 with dP = 0 to tray 10

NeqSim internally uses BOTTOM-UP stage numbering, so we convert via:
    NeqSim stage = (n_trays + 1) - UniSim_tray   for trays
    NeqSim stage 0 = reboiler
"""

from __future__ import annotations

import argparse
import os
import sys
import time
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


# ---------------------------------------------------------------------------
# Repo bootstrap — use the LOCAL neqsim build, not the pip-installed package
# ---------------------------------------------------------------------------
def find_repo_root() -> Path:
    env = os.environ.get("NEQSIM_PROJECT_ROOT")
    if env and (Path(env) / "pom.xml").exists():
        return Path(env)
    here = Path(__file__).resolve()
    for candidate in [here.parent] + list(here.parents):
        if (candidate / "pom.xml").exists() and (candidate / "devtools" / "neqsim_dev_setup.py").exists():
            return candidate
    for guess in [Path("/workspaces/neqsim")]:
        if (guess / "pom.xml").exists():
            return guess
    raise FileNotFoundError(
        "Could not locate NeqSim repo root; set NEQSIM_PROJECT_ROOT.")


REPO_ROOT = find_repo_root()
sys.path.insert(0, str(REPO_ROOT / "devtools"))

from neqsim_dev_setup import neqsim_init, neqsim_classes  # noqa: E402
import jpype  # noqa: E402

ns = neqsim_init(project_root=REPO_ROOT)
ns = neqsim_classes(ns)

SystemSrkEos = ns.SystemSrkEos
Stream = ns.Stream
DistillationColumn = ns.DistillationColumn
SolverType = jpype.JClass(
    "neqsim.process.equipment.distillation.DistillationColumn$SolverType")

# EOS systems available for the sweep. Names match Java class basenames.
_EOS_CLASSES = {
    "SRK":            jpype.JClass("neqsim.thermo.system.SystemSrkEos"),
    "SRK-Peneloux":   jpype.JClass("neqsim.thermo.system.SystemSrkPenelouxEos"),
    "SRK-TwuCoon":    jpype.JClass("neqsim.thermo.system.SystemSrkTwuCoonStatoilEos"),
    "SRK-MathCop":    jpype.JClass("neqsim.thermo.system.SystemSrkMathiasCopeman"),
    "PR":             jpype.JClass("neqsim.thermo.system.SystemPrEos"),
    "PR-1978":        jpype.JClass("neqsim.thermo.system.SystemPrEos1978"),
    "PR-Danesh":      jpype.JClass("neqsim.thermo.system.SystemPrDanesh"),
    "PR-LeeKesler":   jpype.JClass("neqsim.thermo.system.SystemPrLeeKeslerEos"),
    "PR-MathCop":     jpype.JClass("neqsim.thermo.system.SystemPrMathiasCopeman"),
}

# TBP characterization models recognised by Characterise.setTBPModel(name).
_TBP_MODELS = [
    "PedersenSRK",
    "PedersenSRKHeavyOil",
    "PedersenPR",
    "PedersenPR2",
    "PedersenPRHeavyOil",
    "RiaziDaubert",
    "Lee-Kesler",
    "Twu",
    "Cavett",
    "Standing",
]


# ---------------------------------------------------------------------------
# UniSim reference data
# ---------------------------------------------------------------------------
MANUAL_FEED = {
    "name": "20VD008 liq",
    "temperature_C": 77.0000001251743,
    "pressure_bara": 4.2 + 1.01325,   # 4.2 barg
    "mass_flow_kg_hr": 99381.1038920480,
    "molar_composition": {
        "H2S":      0.000000000000000,
        "H2O":      1.26975950126355e-03,
        "Nitrogen": 3.88734329545213e-06,
        "CO2":      2.03669541112211e-03,
        "Methane":  8.35885649596034e-03,
        "Ethane":   0.030312967680537,
        "Propane":  9.83075308994837e-02,
        "i-Butane": 4.09665694460258e-02,
        "n-Butane": 0.114510205790434,
        "i-Pentane": 0.060313250815548,
        "n-Pentane": 7.73190146573562e-02,
        "C6*":      0.104982256950121,
        "C7*":      0.139005591552077,
        "C8*":      0.127908100975965,
        "C9*":      6.20910685541127e-02,
        "C10-C12*": 6.65500502353172e-02,
        "C13-C14*": 0.020235118084271,
        "C15-C16*": 1.25829097167123e-02,
        "C17-C19*": 0.011709984585876,
        "C20-C22*": 7.11881671769593e-03,
        "C23-C25*": 4.57627195654846e-03,
        "C26-C30*": 4.50555256022543e-03,
        "C31-C38*": 3.25896678227350e-03,
        "C39-C80*": 2.07657328777843e-03,
    },
}

MANUAL_TOP_FEED = {
    "name": "top_stage_reflux",
    "temperature_C": 32.14,
    "pressure_bara": 3.7 + 1.01325,
    "mass_flow_kg_hr": 7658.93041734027,
    "molar_composition": {
        "H2S":      0.000000000000000,
        "H2O":      4.35105155095748e-04,
        "Nitrogen": 7.63046322451461e-07,
        "CO2":      7.26662709595144e-04,
        "Methane":  2.18297869906758e-03,
        "Ethane":   1.65679049317917e-02,
        "Propane":  0.121425832401003,
        "i-Butane": 9.52769636340267e-02,
        "n-Butane": 0.306895179064482,
        "i-Pentane": 0.160387157274294,
        "n-Pentane": 0.192815999863345,
        "C6*":      7.08381536172843e-02,
        "C7*":      2.29352443485453e-02,
        "C8*":      7.52373948573269e-03,
        "C9*":      1.57073918334608e-03,
        "C10-C12*": 3.72237726886924e-04,
        "C13-C14*": 3.00684238117462e-05,
        "C15-C16*": 1.06371993923885e-05,
        "C17-C19*": 4.12319121029786e-06,
        "C20-C22*": 4.63134876824379e-07,
        "C23-C25*": 4.37268256091263e-08,
        "C26-C30*": 3.14276050239541e-09,
        "C31-C38*": 4.02814694571035e-11,
        "C39-C80*": 2.31802265756544e-14,
    },
}

# Column inputs — UniSim TOP-DOWN tray numbering
MANUAL_COLUMN = {
    "n_trays": 10,
    "main_feed_tray_topdown": 5,
    "top_feed_tray_topdown": 1,
    "top_pressure_bara": 5.01325,
    "bottom_pressure_bara": 5.06325,
    "reboiler_temperature_C": 137.309085069090,
    "has_reboiler": True,
    "has_condenser": False,
    "tray_efficiency_default": 0.9,
    "tray_efficiency_override": {},   # no overrides — all trays at default 0.9
    "reboiler_efficiency": 1.0,
    # Reboiler specification: "T" (current) fixes T_bot and lets V float;
    # "boilup" fixes V/B (molar) and lets T_bot float. Use "boilup" to
    # diagnose whether the +128 % boilup error is driven by reboiler-side
    # enthalpy / Cp (anchoring V should then collapse rms_dT).
    "reboiler_spec_mode": "T",
    # UniSim V/B (molar). V_reb_mass = 33,265.7 kg/hr; bottoms = 745.78 kmol/hr.
    # MW of V_reb at 137 C / 5 bara is ~70-78 kg/kmol -> V_reb ≈ 427-475 kmol/hr.
    # Adjust once the exact UniSim molar value is known.
    "boilup_ratio_molar": 0.595,
}

UNISIM_TARGET = {
    "overhead": {
        "temperature_C": 55.63690596810403,
        "pressure_bara": 5.01325,
        "mass_flow_kg_hr": 23745.275834065487,
        "molar_flow_kgmol_hr": 437.40427826052155,
        "vapour_fraction": 0.9976570112003801,
    },
    "bottoms": {
        "temperature_C": 136.84988049673456,
        "pressure_bara": 5.06325,
        "mass_flow_kg_hr": 83561.38485823716,
        "molar_flow_kgmol_hr": 745.782511183358,
        "vapour_fraction": 0.0,
    },
}

# Per-tray UniSim profile (top-down, tray 1 = top, tray 10 = bottom).
# T in C, P in barg.
UNISIM_TRAY_PROFILE = {
    "T_C_topdown": [
        55.0352092263182,
        60.4943624688327,
        65.1027206540858,
        70.7739536320417,
        82.2452891411628,
        88.1350583791952,
        93.3124258992463,
        98.4324503710853,
        104.962736678313,
        115.150145109534,
    ],
    "P_barg_topdown": [
        4.00000000000000,
        4.00555555555556,
        4.01111111111111,
        4.01666666666667,
        4.02222222222222,
        4.02777777777778,
        4.03333333333333,
        4.03888888888889,
        4.04444444444444,
        4.05000000000000,
    ],
    # V_above_kg_hr[k] = vapour leaving UniSim tray (k+1) upward, k=0..9
    "V_kg_hr_topdown": [
        23518.5710198693,
        24066.4442843555,
        23724.5933507742,
        23144.1391770657,
        21848.2779462332,
        19203.8952560119,
        23127.9858647393,
        26590.3729304016,
        29754.5804551019,
        33265.6710691705,
    ],
    # L_below_kg_hr[k] = liquid leaving UniSim tray (k+1) downward, k=0..9
    "L_kg_hr_topdown": [
        8206.80370595773,
        7864.95274824513,
        7284.49857453664,
        5988.63734370410,
        102725.358545531,
        106649.449154258,
        110111.836219921,
        113276.043744621,
        116787.134358689,
        120415.322285159,
    ],
}

_NEQSIM_NAME = {
    "H2S": "H2S", "H2O": "water", "Nitrogen": "nitrogen", "CO2": "CO2",
    "Methane": "methane", "Ethane": "ethane", "Propane": "propane",
    "i-Butane": "i-butane", "n-Butane": "n-butane",
    "i-Pentane": "i-pentane", "n-Pentane": "n-pentane",
}

_PSEUDO_COMPONENTS = [
    ("C6*",      0.08617800140380859, 0.6626640014648439),
    ("C7*",      0.0909560012817383,  0.740698486328125),
    ("C8*",      0.103429000854492,   0.769004028320313),
    ("C9*",      0.117186996459961,   0.789065673828125),
    ("C10-C12*", 0.145809005737305,   0.8048148193359379),
    ("C13-C14*", 0.181330001831055,   0.825066711425781),
    ("C15-C16*", 0.21227799987793,    0.8377041015625),
    ("C17-C19*", 0.248141998291016,   0.849904113769531),
    ("C20-C22*", 0.289217010498047,   0.863837097167969),
    ("C23-C25*", 0.330338989257813,   0.8755130004882811),
    ("C26-C30*", 0.384696990966797,   0.8886063232421879),
    ("C31-C38*", 0.471157989501953,   0.9061005249023439),
    ("C39-C80*", 0.6624600219726561,  0.936200378417969),
]

_COMPONENT_ORDER = list(_NEQSIM_NAME.keys()) + \
    [p[0] for p in _PSEUDO_COMPONENTS]

# Toggle: if False, use NeqSim's own Tc/Pc/omega correlations for the TBP cuts
# (only MW and density are passed via addTBPfraction). Set True to overwrite
# with values harvested from the UniSim Hypo Group Controls table.
APPLY_UNISIM_OVERRIDES = False

# Toggle: Peneloux volume correction. NeqSim's Pedersen c-shift correlation
# returns NEGATIVE values for the heaviest pseudo-cuts (C31+, C39+), which
# biases liquid fugacity and K-values. Set False to disable Peneloux for a
# cleaner SRK-vs-UniSim comparison.
USE_VOLUME_CORRECTION = True

# Toggle: zero out all binary interaction parameters (kij). UniSim Hypos
# default to kij=0 for HC-HC pairs; NeqSim auto-fills some kij from a
# correlation. Set True to force kij=0 across the board (HC-HC and HC-light).
ZERO_OUT_KIJ = False

_UNISIM_OVERRIDES = {
    # name_PC:      (Tc_C,                Pc_barg,              omega)
    "C6*_PC":       (234.249993896484,    28.6699995117187,     0.296000003814697),
    "C7*_PC":       (254.701013183594,    33.5599995117188,     0.453299999237061),
    "C8*_PC":       (278.279992675781,    30.2799995117188,     0.489300012588501),
    "C9*_PC":       (300.410974121094,    27.1099995117188,     0.528299987316132),
    "C10-C12*_PC":  (337.982995605469,    21.7799995117188,     0.609300017356873),
    "C13-C14*_PC":  (377.610986328125,    18.2699995117188,     0.701900005340576),
    "C15-C16*_PC":  (408.168969726563,    16.3399995117188,     0.779600024223328),
    "C17-C19*_PC":  (440.560998535156,    14.8599995117188,     0.865100026130676),
    "C20-C22*_PC":  (474.975976562500,    13.7999995117188,     0.955699980258942),
    "C23-C25*_PC":  (506.904992675781,    13.0699995117188,     1.03820002079010),
    "C26-C30*_PC":  (546.941003417969,    12.4099995117188,     1.13440001010895),
    "C31-C38*_PC":  (606.256005859375,    11.7799995117188,     1.25170004367828),
    "C39-C80*_PC":  (734.040979003906,    11.2199995117188,     1.28810000419617),
}


def build_base_fluid(eos_name: str = "SRK",
                     tbp_model: str = "PedersenSRK",
                     verbose: bool = True):
    """Build the 24-component base fluid using the chosen EOS class and TBP
    characterization model. Composition is set later via setMolarComposition
    on a clone, so only Tc/Pc/omega/MW characterization changes here."""
    EosCls = _EOS_CLASSES[eos_name]
    fluid = EosCls(273.15 + 15.0, 1.01325)
    # Select Tc/Pc/omega correlation for TBP cuts BEFORE adding them
    try:
        fluid.getCharacterization().setTBPModel(tbp_model)
    except Exception as e:
        if verbose:
            print(f"  (warning: could not set TBP model '{tbp_model}': {e})")
    for name, neqsim_name in _NEQSIM_NAME.items():
        fluid.addComponent(neqsim_name, 1e-10)
    for name, mw, dens in _PSEUDO_COMPONENTS:
        fluid.addTBPfraction(name, 1e-10, mw, dens)
    fluid.setMixingRule("classic")
    fluid.setMultiPhaseCheck(True)
    # SRK + Peneloux volume correction (controlled by USE_VOLUME_CORRECTION).
    fluid.useVolumeCorrection(bool(USE_VOLUME_CORRECTION))
    fluid.init(0)

    if ZERO_OUT_KIJ:
        try:
            n_comp = int(fluid.getNumberOfComponents())
            for ph in range(int(fluid.getNumberOfPhases())):
                mr = fluid.getPhase(ph).getMixingRule()
                for i in range(n_comp):
                    for j in range(n_comp):
                        if i != j:
                            try:
                                mr.setBinaryInteractionParameter(i, j, 0.0)
                            except Exception:
                                pass
            if verbose:
                print("  (kij forced to 0 for all pairs)")
        except Exception as e:
            if verbose:
                print(f"  (warning: could not zero kij: {e})")

    # Diagnostic dump: what did NeqSim actually compute for each component?
    if verbose:
        phase0 = fluid.getPhase(0)
        print()
        print("Component characterization (NeqSim, before any UniSim override):")
        print(f"  {'name':<15} {'Tc[K]':>9} {'Pc[bara]':>9} "
              f"{'omega':>7} {'MW':>8} {'c_pen':>11}")
        print("  " + "-" * 70)
        for i in range(int(phase0.getNumberOfComponents())):
            c = phase0.getComponent(i)
            try:
                cpen = float(c.getVolumeCorrection())
            except Exception:
                cpen = float("nan")
            print(f"  {str(c.getComponentName()):<15} "
                  f"{float(c.getTC()):>9.2f} "
                  f"{float(c.getPC()):>9.3f} "
                  f"{float(c.getAcentricFactor()):>7.4f} "
                  f"{float(c.getMolarMass())*1000:>8.2f} "
                  f"{cpen:>+11.4e}")

    n_overridden = 0
    if APPLY_UNISIM_OVERRIDES:
        nphases = int(fluid.getNumberOfPhases())
        for ph in range(nphases):
            phase = fluid.getPhase(ph)
            for i in range(int(phase.getNumberOfComponents())):
                comp = phase.getComponent(i)
                cname = str(comp.getComponentName())
                if cname in _UNISIM_OVERRIDES:
                    tc_c, pc_barg, omega = _UNISIM_OVERRIDES[cname]
                    comp.setTC(tc_c + 273.15)
                    comp.setPC(pc_barg + 1.01325)
                    comp.setAcentricFactor(omega)
                    if ph == 0:
                        n_overridden += 1
        fluid.init(0)
        if verbose:
            print(f"Base fluid built: EOS={eos_name}, TBP={tbp_model}, "
                  f"{int(fluid.getNumberOfComponents())} components, "
                  f"{n_overridden} pseudo-component Tc/Pc/omega overridden from UniSim.")
    else:
        if verbose:
            print(f"Base fluid built: EOS={eos_name}, TBP={tbp_model}, "
                  f"{int(fluid.getNumberOfComponents())} components "
                  f"(native Tc/Pc/omega from TBP model, overrides DISABLED).")
    return fluid


def make_stream(base_fluid, name, feed_dict, flow_rate, flow_unit):
    fluid = base_fluid.clone()
    z = [max(float(feed_dict["molar_composition"].get(c, 0.0)), 1e-100)
         for c in _COMPONENT_ORDER]
    z_sum = sum(z)
    z = [v / z_sum for v in z]
    fluid.setMolarComposition(z)
    fluid.setTemperature(feed_dict["temperature_C"], "C")
    p_bara = feed_dict.get("pressure_bara",
                           feed_dict.get("pressure_barg", 0.0) + 1.01325)
    fluid.setPressure(p_bara, "bara")
    fluid.init(0)
    s = Stream(name, fluid)
    s.setTemperature(feed_dict["temperature_C"], "C")
    s.setPressure(fluid.getPressure(), "bara")
    s.setFlowRate(flow_rate, flow_unit)
    s.run()
    return s


# ---------------------------------------------------------------------------
# Column build
# ---------------------------------------------------------------------------
def unisim_tray_to_ns_stage(unisim_tray: int, n_trays: int) -> int:
    """UniSim top-down tray (1..n_trays) -> NeqSim bottom-up stage (1..n_trays)."""
    return (n_trays + 1) - int(unisim_tray)


def build_column(feed_stream, top_feed_stream):
    n_trays = int(MANUAL_COLUMN["n_trays"])
    col = DistillationColumn(
        "20VE105_205_standalone",
        n_trays,
        bool(MANUAL_COLUMN["has_reboiler"]),
        bool(MANUAL_COLUMN["has_condenser"]),
    )

    main_feed_ns = unisim_tray_to_ns_stage(
        MANUAL_COLUMN["main_feed_tray_topdown"], n_trays)
    top_feed_ns = unisim_tray_to_ns_stage(
        MANUAL_COLUMN["top_feed_tray_topdown"],  n_trays)
    col.addFeedStream(feed_stream,     main_feed_ns)
    col.addFeedStream(top_feed_stream, top_feed_ns)

    P_top = float(MANUAL_COLUMN["top_pressure_bara"])
    P_bot = float(MANUAL_COLUMN["bottom_pressure_bara"])
    col.setTopPressure(P_top)
    # NeqSim's DistillationColumn divides ΔP across n_trays intervals (it
    # counts the reboiler as a stage in the internal interpolation). UniSim
    # divides ΔP across (n_trays - 1) tray-to-tray intervals. Compensate the
    # bottom pressure so the per-tray P matches UniSim exactly. The reboiler
    # then ends up one extra dp-step above the bottom tray, which is fine.
    if n_trays >= 2:
        P_bot_eff = P_top + (P_bot - P_top) * (n_trays / (n_trays - 1.0))
    else:
        P_bot_eff = P_bot
    col.setBottomPressure(P_bot_eff)

    spec_mode = str(MANUAL_COLUMN.get("reboiler_spec_mode", "T")).lower()
    if spec_mode == "boilup":
        v_over_b = float(MANUAL_COLUMN["boilup_ratio_molar"])
        # Seed the reboiler with the UniSim T_bot so the NS initial estimate
        # is in the right neighbourhood, then override with V/B for the spec.
        col.getReboiler().setOutTemperature(
            273.15 + MANUAL_COLUMN["reboiler_temperature_C"])
        col.setReboilerBoilupRatio(v_over_b)
        print(f"[COL] reboiler spec: V/B (molar) = {v_over_b:.4f}"
              f"  (init T_bot seed = {MANUAL_COLUMN['reboiler_temperature_C']:.2f} C)")
    else:
        col.getReboiler().setOutTemperature(
            273.15 + MANUAL_COLUMN["reboiler_temperature_C"])
        print(f"[COL] reboiler spec: T_bot = "
              f"{MANUAL_COLUMN['reboiler_temperature_C']:.4f} C")

    # NOTE: per-tray pressures are set by DistillationColumn during run()
    # from setTopPressure / setBottomPressure (with the compensation above),
    # so no manual tray.setPressure loop is needed.

    # Murphree efficiencies (UniSim top-down)
    col.setMurphreeEfficiency(float(MANUAL_COLUMN["tray_efficiency_default"]))
    col.setMurphreeEfficiency(0, float(MANUAL_COLUMN["reboiler_efficiency"]))
    for k_topdown, eta in MANUAL_COLUMN["tray_efficiency_override"].items():
        ns_stage = unisim_tray_to_ns_stage(k_topdown, n_trays)
        col.setMurphreeEfficiency(ns_stage, float(eta))

    col.setSolverType(SolverType.valueOf("NAPHTALI_SANDHOLM"))
    col.setMaxNumberOfIterations(300)
    return col, main_feed_ns, top_feed_ns


# ---------------------------------------------------------------------------
# Reporting
# ---------------------------------------------------------------------------
def print_header(title):
    print()
    print("=" * 72)
    print(f" {title}")
    print("=" * 72)


def print_stage_setup(col):
    n_trays = int(MANUAL_COLUMN["n_trays"])
    print(f'{"NS stage":>8} {"UniSim":>8} {"role":<12} {"P (barg)":>10} {"eta":>8}')
    print("-" * 52)
    for j in range(n_trays + 1):
        if j == 0:
            role = "reboiler"
            unisim_label = "reb"
            try:
                P_j = float(col.getReboiler().getPressure())
            except Exception:
                P_j = float(MANUAL_COLUMN["bottom_pressure_bara"])
        else:
            unisim_tray = (n_trays + 1) - j
            suffix = "  (top)" if unisim_tray == 1 else (
                "  (bot)" if unisim_tray == n_trays else "")
            role = f"tray {unisim_tray}{suffix}"
            unisim_label = str(unisim_tray)
            P_j = float("nan")
            tray = col.getTray(j)
            # Try thermosystem first (authoritative after run), then tray getters
            try:
                P_j = float(tray.getThermoSystem().getPressure())
            except Exception:
                pass
            if not np.isfinite(P_j) or P_j <= 0.0:
                try:
                    P_j = float(tray.getPressure())
                except Exception:
                    pass
        try:
            eta_j = float(col.getMurphreeEfficiency(j))
        except Exception:
            eta_j = float("nan")
        print(
            f"{j:>8} {unisim_label:>8} {role:<12} {(P_j - 1.01325):>10.4f} {eta_j:>8.4f}")


def print_convergence(col, feed_stream, top_feed_stream):
    iters = int(col.getLastIterationCount())
    temp_res = float(col.getLastTemperatureResidual())
    mass_res = float(col.getLastMassResidual())
    energy_res = float(col.getLastEnergyResidual())
    converged = bool(col.solved())
    mb_err = float(col.getMassBalance("kg/hr"))

    feed_main = float(feed_stream.getFlowRate("kg/hr"))
    feed_top = float(top_feed_stream.getFlowRate("kg/hr"))
    feed_tot = feed_main + feed_top
    mb_rel = 100.0 * mb_err / feed_tot if feed_tot else float("nan")

    print(f"  Iterations      : {iters}")
    print(f"  Converged       : {converged}")
    print(f"  Temp residual   : {temp_res:.3e}  K")
    print(f"  Mass residual   : {mass_res:.3e}  (relative, tray-level)")
    print(f"  Energy residual : {energy_res:.3e}")
    print()
    print(f"  Main feed       : {feed_main:12.4f} kg/hr")
    print(f"  Top feed (refl) : {feed_top:12.4f} kg/hr")
    print(f"  Total IN        : {feed_tot:12.4f} kg/hr")
    print(f"  col.getMassBalance() = {mb_err:+.4f} kg/hr  ({mb_rel:+.4f} %)")
    if not converged:
        print("  WARNING: column did not converge.")


def compare_with_unisim(col):
    oh = col.getGasOutStream()
    btm = col.getLiquidOutStream()
    rows = []
    for key, stream in (("overhead", oh), ("bottoms", btm)):
        u = UNISIM_TARGET[key]
        n_T = float(stream.getTemperature("C"))
        n_P = float(stream.getPressure("bara"))
        n_kg = float(stream.getFlowRate("kg/hr"))
        n_mol = float(stream.getFlowRate("kmol/hr"))
        n_bet = float(stream.getFluid().getBeta())
        rows.append({
            "Stream": key,
            "UniSim T (C)": u["temperature_C"],
            "NeqSim T (C)": n_T,
            "T Dev (C)": n_T - u["temperature_C"],
            "UniSim P (barg)": u["pressure_bara"] - 1.01325,
            "NeqSim P (barg)": n_P - 1.01325,
            "UniSim F (kg/hr)": u["mass_flow_kg_hr"],
            "NeqSim F (kg/hr)": n_kg,
            "F Dev (%)": 100.0 * (n_kg - u["mass_flow_kg_hr"]) / u["mass_flow_kg_hr"],
            "UniSim mol (kmol/hr)": u["molar_flow_kgmol_hr"],
            "NeqSim mol (kmol/hr)": n_mol,
            "mol Dev (%)": 100.0 * (n_mol - u["molar_flow_kgmol_hr"]) / u["molar_flow_kgmol_hr"],
            "UniSim vapfrac": u["vapour_fraction"],
            "NeqSim vapfrac": n_bet,
        })
    df = pd.DataFrame(rows).round(4)
    with pd.option_context("display.max_columns", None,
                           "display.width", 220):
        print(df.to_string(index=False))
    return oh, btm


def print_mass_balance(feed_stream, top_feed_stream, oh, btm):
    feeds_kg = float(feed_stream.getFlowRate("kg/hr")) + \
        float(top_feed_stream.getFlowRate("kg/hr"))
    feeds_kmol = float(feed_stream.getFlowRate("kmol/hr")) + \
        float(top_feed_stream.getFlowRate("kmol/hr"))
    out_kg = float(oh.getFlowRate("kg/hr")) + float(btm.getFlowRate("kg/hr"))
    out_kmol = float(oh.getFlowRate("kmol/hr")) + \
        float(btm.getFlowRate("kmol/hr"))
    err_kg = out_kg - feeds_kg
    err_kmol = out_kmol - feeds_kmol
    print(f"  IN  : {feeds_kg:12.3f} kg/hr   ({feeds_kmol:9.3f} kmol/hr)")
    print(f"  OUT : {out_kg:12.3f} kg/hr   ({out_kmol:9.3f} kmol/hr)")
    err_pct = 100.0 * err_kg / feeds_kg if feeds_kg else float("nan")
    mol_pct = 100.0 * err_kmol / feeds_kmol if feeds_kmol else float("nan")
    print(f"  ΔM  : {err_kg:+12.3e} kg/hr   ({err_pct:+8.4f} %)")
    print(f"  Δn  : {err_kmol:+12.3e} kmol/hr ({mol_pct:+8.4f} %)")
    status = "PASS" if abs(err_pct) < 0.5 else "WARN" if abs(
        err_pct) < 2.0 else "FAIL"
    print(f"  Status: {status}  (PASS<0.5%, WARN<2%, FAIL>=2%)")


def print_energy_balance(feed_stream, top_feed_stream, oh, btm, col):
    def H(stream):
        return float(stream.getThermoSystem().getEnthalpy())
    H_main = H(feed_stream)
    H_top = H(top_feed_stream)
    H_oh = H(oh)
    H_btm = H(btm)
    Q_overall = (H_oh + H_btm) - (H_main + H_top)

    reb = col.getReboiler()
    try:
        Q_stored = float(reb.getDuty())
    except Exception:
        Q_stored = float("nan")

    print(f"  H(main feed)              = {H_main/1e6:+12.4f} MW")
    print(f"  H(top feed / ext reflux)  = {H_top / 1e6:+12.4f} MW")
    print(f"  H(overhead)               = {H_oh / 1e6:+12.4f} MW")
    print(f"  H(bottoms)                = {H_btm/1e6:+12.4f} MW")
    print("  -----")
    print(
        f"  Q_reboiler (overall EB)   = {Q_overall/1e6:+12.4f} MW   <-- authoritative")
    print(
        f"  Q_reboiler (stored field) = {Q_stored / 1e6:+12.4f} MW   (set by Reboiler.run(); STALE after NS/IO solve)")
    print("  Note: overall EB is closed by construction on the feed/product streams")
    print("        and is the correct duty. Stage-level checks via tray internal")
    print("        streams are unreliable post-NS-solve and have been removed.")


# ---------------------------------------------------------------------------
# Profile plot
# ---------------------------------------------------------------------------
def plot_profile(col, out_path: Path, show: bool):
    n_stages = int(col.getNumberOfTrays())
    stages = list(range(n_stages))
    T_profile = []
    P_profile = []
    V_kmolhr = []
    L_kmolhr = []
    V_kghr = []
    L_kghr = []
    for j in stages:
        tr = col.getTray(j)
        try:
            T_profile.append(float(tr.getTemperature() - 273.15))
        except Exception:
            T_profile.append(
                float(tr.getThermoSystem().getTemperature() - 273.15))
        try:
            P_profile.append(float(tr.getPressure()))
        except Exception:
            P_profile.append(float("nan"))
        try:
            V_kmolhr.append(float(tr.getGasOutStream().getFlowRate("kmol/hr")))
            V_kghr.append(float(tr.getGasOutStream().getFlowRate("kg/hr")))
        except Exception:
            V_kmolhr.append(float("nan"))
            V_kghr.append(float("nan"))
        try:
            L_kmolhr.append(
                float(tr.getLiquidOutStream().getFlowRate("kmol/hr")))
            L_kghr.append(
                float(tr.getLiquidOutStream().getFlowRate("kg/hr")))
        except Exception:
            L_kmolhr.append(float("nan"))
            L_kghr.append(float("nan"))

    T_top_ref = UNISIM_TARGET["overhead"]["temperature_C"]
    T_bot_ref = UNISIM_TARGET["bottoms"]["temperature_C"]
    n_trays = int(MANUAL_COLUMN["n_trays"])

    # UniSim per-tray profile (top-down). Map to NS stage (bottom-up).
    u_T_topdown = UNISIM_TRAY_PROFILE["T_C_topdown"]
    u_P_topdown = UNISIM_TRAY_PROFILE["P_barg_topdown"]
    u_V_topdown = UNISIM_TRAY_PROFILE["V_kg_hr_topdown"]
    u_L_topdown = UNISIM_TRAY_PROFILE["L_kg_hr_topdown"]
    # NS stage for UniSim tray k (top-down, k=1..n_trays) is (n_trays + 1) - k.
    tray_ns_stages = [(n_trays + 1) - k for k in range(1, n_trays + 1)]

    print()
    print(f'{"NS":>4} {"UniSim":>7} {"Type":<10} {"T (C)":>9} {"P (barg)":>9} '
          f'{"V (kg/hr)":>12} {"L (kg/hr)":>12}')
    print("-" * 70)
    # Show trays only (UniSim tray 1..n_trays, top-down). Reboiler excluded.
    for k in range(1, n_trays + 1):
        j = (n_trays + 1) - k
        stype = "tray (top)" if k == 1 else (
            "tray (bot)" if k == n_trays else "tray")
        print(f"{j:>4} {k:>7} {stype:<10} {T_profile[j]:>9.2f} {(P_profile[j] - 1.01325):>9.3f} "
              f"{V_kghr[j]:>12.1f} {L_kghr[j]:>12.1f}")
    print("-" * 70)
    print(
        f"UniSim ref: T_top = {T_top_ref:.2f} C, T_bottom = {T_bot_ref:.2f} C")
    print(f"NeqSim    : T_top = {T_profile[-1]:.2f} C, T_bottom = {T_profile[0]:.2f} C  "
          f"(\u0394T_top = {T_profile[-1] - T_top_ref:+.2f}, \u0394T_bot = {T_profile[0] - T_bot_ref:+.2f})")

    # ---- Per-tray comparison: T & P ----
    print()
    print("Per-tray comparison vs UniSim   T (C) and P (barg)")
    print(f'{"UniSim":>6} {"NS":>4} '
          f'{"T_uni":>9} {"T_neq":>9} {"dT":>8}   '
          f'{"P_uni":>9} {"P_neq":>9} {"dP":>9}')
    print("-" * 78)
    for k_idx, j in enumerate(tray_ns_stages):
        k = k_idx + 1
        t_uni = u_T_topdown[k_idx]
        p_uni = u_P_topdown[k_idx]
        t_neq = T_profile[j]
        p_neq = P_profile[j] - 1.01325
        print(f"{k:>6} {j:>4} "
              f"{t_uni:>9.2f} {t_neq:>9.2f} {(t_neq - t_uni):>+8.2f}   "
              f"{p_uni:>9.4f} {p_neq:>9.4f} {(p_neq - p_uni):>+9.4f}")
    t_devs = [T_profile[tray_ns_stages[i]] - u_T_topdown[i]
              for i in range(n_trays)]
    p_devs = [(P_profile[tray_ns_stages[i]] - 1.01325) - u_P_topdown[i]
              for i in range(n_trays)]
    print("-" * 78)
    print(f"  T: max |dT| = {max(abs(d) for d in t_devs):.2f} C,    "
          f"RMS = {(sum(d*d for d in t_devs)/n_trays)**0.5:.2f} C")
    print(f"  P: max |dP| = {max(abs(d) for d in p_devs):.4f} barg, "
          f"RMS = {(sum(d*d for d in p_devs)/n_trays)**0.5:.4f} barg")

    # ---- Per-tray comparison: V_above and L_below (kg/hr) ----
    print()
    print("Per-tray comparison vs UniSim   V_above and L_below (kg/hr)")
    print(f'{"UniSim":>6} {"NS":>4} '
          f'{"V_uni":>11} {"V_neq":>11} {"dV":>10} {"dV%":>7}   '
          f'{"L_uni":>11} {"L_neq":>11} {"dL":>10} {"dL%":>7}')
    print("-" * 105)
    for k_idx, j in enumerate(tray_ns_stages):
        k = k_idx + 1
        v_uni = u_V_topdown[k_idx]
        l_uni = u_L_topdown[k_idx]
        v_neq = V_kghr[j]
        l_neq = L_kghr[j]
        dv = v_neq - v_uni
        dl = l_neq - l_uni
        dv_pct = 100.0 * dv / v_uni if v_uni else float("nan")
        dl_pct = 100.0 * dl / l_uni if l_uni else float("nan")
        print(f"{k:>6} {j:>4} "
              f"{v_uni:>11.1f} {v_neq:>11.1f} {dv:>+10.1f} {dv_pct:>+7.2f}   "
              f"{l_uni:>11.1f} {l_neq:>11.1f} {dl:>+10.1f} {dl_pct:>+7.2f}")
    v_devs = [V_kghr[tray_ns_stages[i]] - u_V_topdown[i]
              for i in range(n_trays)]
    l_devs = [L_kghr[tray_ns_stages[i]] - u_L_topdown[i]
              for i in range(n_trays)]
    print("-" * 105)
    print(f"  V: max |dV| = {max(abs(d) for d in v_devs):.1f} kg/hr, "
          f"RMS = {(sum(d*d for d in v_devs)/n_trays)**0.5:.1f} kg/hr")
    print(f"  L: max |dL| = {max(abs(d) for d in l_devs):.1f} kg/hr, "
          f"RMS = {(sum(d*d for d in l_devs)/n_trays)**0.5:.1f} kg/hr")

    # ---- Composition of liquid going TO reboiler (NS stage 1 -> NS stage 0) ----
    # That is the liquid stream leaving NS stage 1 (= UniSim tray 10, bottom tray).
    UNISIM_L_TO_REB = [
        0.000000000000000, 7.89140667091304e-09, 1.98416870370679e-15,
        6.41604479553517e-09, 7.83663932370674e-10, 3.20041197483774e-06,
        1.50484796362763e-03, 1.38476431118582e-02, 8.86518093740134e-02,
        9.80483637866382e-02, 0.128834805903354,   0.158840733855768,
        0.183292972976880,   0.144527475277435,   6.31688228639087e-02,
        0.061224844717427,   1.79355133750074e-02, 0.011047850332099,
        1.02470297182957e-02, 6.22336876362977e-03, 3.99978924084143e-03,
        3.93777685242656e-03, 2.84826062399445e-03, 1.81487575970335e-03,
    ]
    try:
        l_to_reb = col.getTray(1).getLiquidOutStream().getFluid().clone()
        comp_names = [c.getComponentName() for c in l_to_reb.getPhase(0).getcomponentArray()
                      if c is not None]
        # use overall mole fractions of the stream
        z_neq = [float(l_to_reb.getMolarComposition()[i])
                 for i in range(l_to_reb.getNumberOfComponents())]
    except Exception as e:
        print(f"  (skip composition diag: {e})")
        z_neq = []
        comp_names = []

    if z_neq and len(z_neq) == len(UNISIM_L_TO_REB):
        print()
        print("Liquid composition entering reboiler (NS stage 1 L-out  = UniSim tray 10 L-down)")
        print(f"{'Component':<14} {'z_uni':>14} {'z_neq':>14} {'dz':>13} {'dz%':>9}")
        print("-" * 70)
        max_abs = 0.0
        max_name = ""
        rms = 0.0
        for nm, zu, zn in zip(comp_names, UNISIM_L_TO_REB, z_neq):
            dz = zn - zu
            pct = (100.0 * dz / zu) if zu > 1e-12 else float("nan")
            print(f"{nm:<14} {zu:>14.6e} {zn:>14.6e} {dz:>+13.3e} {pct:>+9.2f}")
            if abs(dz) > max_abs:
                max_abs = abs(dz)
                max_name = nm
            rms += dz * dz
        rms = (rms / len(z_neq)) ** 0.5
        print("-" * 70)
        print(f"  max |dz| = {max_abs:.3e} on {max_name},   RMS = {rms:.3e}")
        print(f"  sum z_uni = {sum(UNISIM_L_TO_REB):.6f}, "
              f"sum z_neq = {sum(z_neq):.6f}")
    elif z_neq:
        print(
            f"  (comp-count mismatch: NeqSim {len(z_neq)} vs UniSim {len(UNISIM_L_TO_REB)})")

    fig, (ax1, axP, ax2) = plt.subplots(1, 3, figsize=(16, 6))

    # Tray-only NeqSim values for overlay against UniSim per-tray profile
    tray_T_neq = [T_profile[j] for j in tray_ns_stages]
    tray_P_neq = [P_profile[j] - 1.01325 for j in tray_ns_stages]  # barg
    tray_V_neq = [V_kghr[j] for j in tray_ns_stages]
    tray_L_neq = [L_kghr[j] for j in tray_ns_stages]

    # Trays only — exclude NS stage 0 (reboiler) from the plot
    tray_stages_plot = list(tray_ns_stages)  # NS stages 1..n_trays
    tray_V_plot = [V_kghr[j] for j in tray_stages_plot]
    tray_L_plot = [L_kghr[j] for j in tray_stages_plot]

    ax1.plot(tray_T_neq, tray_stages_plot, "o-", color="tab:red",
             lw=2, label="NeqSim (trays)")
    ax1.plot(u_T_topdown, tray_ns_stages, "s--", color="tab:blue",
             lw=1.5, label="UniSim tray profile")
    ax1.plot([T_top_ref], [tray_ns_stages[0]], "D",
             color="black", ms=10, mfc="none", mew=2,
             label="UniSim product T (top)")
    ax1.set_xlabel("Temperature (C)")
    ax1.set_ylabel("NeqSim stage (1 = bottom tray, top = top tray)")
    ax1.set_title("Temperature Profile (trays only)")
    ax1.grid(True, alpha=0.3)
    ax1.legend(loc="best")

    # ---- Pressure profile (trays only) ----
    axP.plot(tray_P_neq, tray_stages_plot, "o-", color="tab:purple",
             lw=2, label="NeqSim (trays)")
    axP.plot(u_P_topdown, tray_ns_stages, "s--", color="tab:blue",
             lw=1.5, label="UniSim tray profile")
    axP.set_xlabel("Pressure (barg)")
    axP.set_ylabel("NeqSim stage")
    axP.set_title("Pressure Profile (trays only)")
    axP.grid(True, alpha=0.3)
    axP.legend(loc="best")

    ax2.plot(tray_V_plot, tray_stages_plot, "o-", color="tab:blue",
             lw=2, label="NeqSim V up")
    ax2.plot(tray_L_plot, tray_stages_plot, "s-", color="tab:green",
             lw=2, label="NeqSim L down")
    ax2.plot(u_V_topdown, tray_ns_stages, "o--", color="tab:cyan",
             lw=1.5, mfc="none", label="UniSim V above")
    ax2.plot(u_L_topdown, tray_ns_stages, "s--", color="tab:olive",
             lw=1.5, mfc="none", label="UniSim L below")
    ax2.set_xlabel("Mass flow (kg/hr)")
    ax2.set_ylabel("NeqSim stage")
    ax2.set_title("Internal Vapour & Liquid Flows")
    ax2.grid(True, alpha=0.3)
    ax2.legend(loc="best")

    fig.suptitle(f"20VE105_205 — {n_trays} trays + reboiler, "
                 f"reboiler T fixed at {MANUAL_COLUMN['reboiler_temperature_C']:.1f} C",
                 fontsize=11)
    plt.tight_layout()
    plt.savefig(out_path, dpi=150, bbox_inches="tight")
    print(f"\nProfile plot saved to: {out_path}")
    if show:
        plt.show()
    else:
        plt.close(fig)


# ---------------------------------------------------------------------------
# Product composition comparison (overhead vapour & reboiler bottoms)
# ---------------------------------------------------------------------------
# UniSim reference compositions, ordered by _COMPONENT_ORDER.
UNISIM_OVERHEAD_Y = [
    0.000000000000000,
    3.19796132823814e-03,
    9.63386867406638e-06,
    5.13963863661524e-03,
    2.08673046289698e-02,
    7.81174205117154e-02,
    0.271230393891182,
    0.115713755615714,
    0.297909989948415,
    9.00911329325428e-02,
    9.61033015613255e-02,
    1.84614651141807e-02,
    2.81092967615207e-03,
    3.20694560495289e-04,
    2.52983420638179e-05,
    1.06411896168960e-06,
    1.40305581489532e-08,
    1.12042053486034e-09,
    1.07044443295051e-10,
    6.19140307821347e-12,
    5.05147530563770e-13,
    3.52393911315598e-14,
    4.64971208495140e-16,
    2.96933178317266e-19,
]

UNISIM_BOTTOMS_X = [
    0.000000000000000,
    5.97296226077841e-10,
    3.17857672248332e-16,
    1.39007943417817e-09,
    1.87118646694352e-10,
    1.02466903151263e-06,
    4.19127244860309e-04,
    4.08920192379211e-03,
    2.58407920409853e-02,
    4.68959023678474e-02,
    6.63036983580072e-02,
    0.130370647853579,
    0.164428154380332,
    0.164989150155456,
    8.81824614784974e-02,
    0.115061036398155,
    4.24208204213709e-02,
    3.04128244434246e-02,
    0.032607963058484,
    2.27312171013093e-02,
    1.64675592412304e-02,
    1.86027516552455e-02,
    1.61618032720863e-02,
    1.40138617618106e-02,
]


def _stream_composition(stream):
    """Return (names, z) for a stream, ordered as the stream's own components."""
    fluid = stream.getFluid().clone()
    n = int(fluid.getNumberOfComponents())
    phase0 = fluid.getPhase(0)
    names = [str(phase0.getComponent(i).getComponentName()) for i in range(n)]
    z = [float(fluid.getMolarComposition()[i]) for i in range(n)]
    return names, z


def compare_product_compositions(col, out_path: Path, show: bool):
    """Compare NeqSim overhead vapour and reboiler bottoms compositions
    against the UniSim reference. Prints a numerical table and saves a
    nice bar-chart figure."""
    oh = col.getGasOutStream()
    btm = col.getLiquidOutStream()

    names_v, y_neq = _stream_composition(oh)
    names_l, x_neq = _stream_composition(btm)

    if names_v != names_l:
        print("  (warning: vapour/liquid component orders differ; using vapour order)")
    comp_names = names_v
    n = len(comp_names)

    # Align UniSim arrays to the stream order (they are already in _COMPONENT_ORDER).
    if n == len(UNISIM_OVERHEAD_Y):
        y_uni = list(UNISIM_OVERHEAD_Y)
        x_uni = list(UNISIM_BOTTOMS_X)
    else:
        print(f"  (warning: component count mismatch — NeqSim {n} vs UniSim "
              f"{len(UNISIM_OVERHEAD_Y)}; aborting composition comparison)")
        return

    # --- Numerical table ----------------------------------------------------
    print(f"{'Component':<12} "
          f"{'y_uni':>12} {'y_neq':>12} {'dy':>11}   "
          f"{'x_uni':>12} {'x_neq':>12} {'dx':>11}")
    print("-" * 84)
    rms_y = 0.0
    rms_x = 0.0
    max_dy = 0.0
    max_dx = 0.0
    for nm, yu, yn, xu, xn in zip(comp_names, y_uni, y_neq, x_uni, x_neq):
        dy = yn - yu
        dx = xn - xu
        rms_y += dy * dy
        rms_x += dx * dx
        max_dy = max(max_dy, abs(dy))
        max_dx = max(max_dx, abs(dx))
        print(f"{nm:<12} "
              f"{yu:>12.4e} {yn:>12.4e} {dy:>+11.3e}   "
              f"{xu:>12.4e} {xn:>12.4e} {dx:>+11.3e}")
    rms_y = (rms_y / n) ** 0.5
    rms_x = (rms_x / n) ** 0.5
    print("-" * 84)
    print(f"  vapour : max |dy| = {max_dy:.3e}, RMS = {rms_y:.3e}, "
          f"sum y_uni = {sum(y_uni):.6f}, sum y_neq = {sum(y_neq):.6f}")
    print(f"  liquid : max |dx| = {max_dx:.3e}, RMS = {rms_x:.3e}, "
          f"sum x_uni = {sum(x_uni):.6f}, sum x_neq = {sum(x_neq):.6f}")

    # --- Bar chart ----------------------------------------------------------
    x_idx = np.arange(n)
    width = 0.38

    fig, (ax_v, ax_l, ax_log) = plt.subplots(
        3, 1, figsize=(15, 12), constrained_layout=True
    )

    # Overhead vapour (linear)
    ax_v.bar(x_idx - width / 2, y_uni, width, color="tab:blue",
             edgecolor="black", linewidth=0.5, label="UniSim")
    ax_v.bar(x_idx + width / 2, y_neq, width, color="tab:red",
             edgecolor="black", linewidth=0.5, label="NeqSim")
    ax_v.set_xticks(x_idx)
    ax_v.set_xticklabels(comp_names, rotation=60, ha="right")
    ax_v.set_ylabel("Mole fraction")
    ax_v.set_title("Overhead vapour composition (y) — linear scale")
    ax_v.grid(True, axis="y", alpha=0.3)
    ax_v.set_axisbelow(True)
    ax_v.legend(loc="upper right")
    for xi, yu, yn in zip(x_idx, y_uni, y_neq):
        peak = max(yu, yn)
        if peak >= 0.05:
            ax_v.text(xi, peak + 0.008,
                      f"u {yu*100:.1f}%\nn {yn*100:.1f}%",
                      ha="center", va="bottom", fontsize=7)

    # Bottoms liquid (linear)
    ax_l.bar(x_idx - width / 2, x_uni, width, color="tab:blue",
             edgecolor="black", linewidth=0.5, label="UniSim")
    ax_l.bar(x_idx + width / 2, x_neq, width, color="tab:red",
             edgecolor="black", linewidth=0.5, label="NeqSim")
    ax_l.set_xticks(x_idx)
    ax_l.set_xticklabels(comp_names, rotation=60, ha="right")
    ax_l.set_ylabel("Mole fraction")
    ax_l.set_title("Reboiler bottoms composition (x) — linear scale")
    ax_l.grid(True, axis="y", alpha=0.3)
    ax_l.set_axisbelow(True)
    ax_l.legend(loc="upper right")
    for xi, xu, xn in zip(x_idx, x_uni, x_neq):
        peak = max(xu, xn)
        if peak >= 0.05:
            ax_l.text(xi, peak + 0.005,
                      f"u {xu*100:.1f}%\nn {xn*100:.1f}%",
                      ha="center", va="bottom", fontsize=7)

    # Combined log-scale view (vapour and liquid side-by-side per component)
    floor = 1e-20
    width_log = 0.2

    def _safe(arr):
        return np.where(np.asarray(arr) > floor, arr, floor)

    ax_log.bar(x_idx - 1.5 * width_log, _safe(y_uni), width_log,
               color="tab:blue", edgecolor="black", linewidth=0.4,
               label="y UniSim")
    ax_log.bar(x_idx - 0.5 * width_log, _safe(y_neq), width_log,
               color="tab:red", edgecolor="black", linewidth=0.4,
               label="y NeqSim")
    ax_log.bar(x_idx + 0.5 * width_log, _safe(x_uni), width_log,
               color="tab:cyan", edgecolor="black", linewidth=0.4,
               label="x UniSim")
    ax_log.bar(x_idx + 1.5 * width_log, _safe(x_neq), width_log,
               color="tab:orange", edgecolor="black", linewidth=0.4,
               label="x NeqSim")
    ax_log.set_yscale("log")
    ax_log.set_ylim(1e-18, 2.0)
    ax_log.set_xticks(x_idx)
    ax_log.set_xticklabels(comp_names, rotation=60, ha="right")
    ax_log.set_ylabel("Mole fraction (log scale)")
    ax_log.set_title("UniSim vs NeqSim — vapour & liquid, log scale")
    ax_log.grid(True, axis="y", which="both", alpha=0.3)
    ax_log.set_axisbelow(True)
    ax_log.legend(loc="lower center", ncol=4, framealpha=0.95)

    fig.suptitle("20VE105_205 — UniSim vs NeqSim product compositions",
                 fontsize=13, fontweight="bold")

    out_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(out_path, dpi=150, bbox_inches="tight")
    print(f"\nProduct composition plot saved to: {out_path}")
    if show:
        plt.show()
    else:
        plt.close(fig)


# ---------------------------------------------------------------------------
# Sweep over fluid description models (EOS x TBP characterization)
# ---------------------------------------------------------------------------
def _run_one_case(eos_name, tbp_model):
    """Build fluid+column for a given (EOS, TBP model) pair, run it, and
    return a metrics dict. Returns None if the run blows up."""
    try:
        base_fluid = build_base_fluid(eos_name=eos_name,
                                      tbp_model=tbp_model, verbose=False)
        feed_stream = make_stream(base_fluid, "manual_column_feed",
                                  MANUAL_FEED, MANUAL_FEED["mass_flow_kg_hr"],
                                  "kg/hr")
        top_feed_stream = make_stream(base_fluid, "top_stage_reflux",
                                      MANUAL_TOP_FEED,
                                      MANUAL_TOP_FEED["mass_flow_kg_hr"],
                                      "kg/hr")
        col, _mfns, _tfns = build_column(feed_stream, top_feed_stream)
        col.run()
    except Exception as e:
        return {"error": str(e)[:60]}

    n_trays = int(MANUAL_COLUMN["n_trays"])
    tray_ns = [(n_trays + 1) - k for k in range(1, n_trays + 1)]

    T_neq, V_neq, L_neq = [], [], []
    for j in tray_ns:
        tr = col.getTray(j)
        try:
            T_neq.append(float(tr.getTemperature() - 273.15))
        except Exception:
            T_neq.append(
                float(tr.getThermoSystem().getTemperature() - 273.15))
        try:
            V_neq.append(float(tr.getGasOutStream().getFlowRate("kg/hr")))
        except Exception:
            V_neq.append(float("nan"))
        try:
            L_neq.append(float(tr.getLiquidOutStream().getFlowRate("kg/hr")))
        except Exception:
            L_neq.append(float("nan"))

    u_T = UNISIM_TRAY_PROFILE["T_C_topdown"]
    u_V = UNISIM_TRAY_PROFILE["V_kg_hr_topdown"]
    u_L = UNISIM_TRAY_PROFILE["L_kg_hr_topdown"]

    dT = [T_neq[i] - u_T[i] for i in range(n_trays)]
    dV = [V_neq[i] - u_V[i] for i in range(n_trays)]
    dL = [L_neq[i] - u_L[i] for i in range(n_trays)]

    def rms(x):
        return (sum(v * v for v in x) / len(x)) ** 0.5

    oh = col.getGasOutStream()
    btm = col.getLiquidOutStream()
    T_top_neq = float(oh.getTemperature("C"))
    T_bot_neq = float(btm.getTemperature("C"))
    V_reb = V_neq[-1]   # vapour from bottom tray = reboiler boilup
    V_reb_uni = u_V[-1]
    return {
        "T_top": T_top_neq,
        "T_bot": T_bot_neq,
        "V_reb_kg_hr": V_reb,
        "V_reb_dev_pct": 100.0 * (V_reb - V_reb_uni) / V_reb_uni,
        "max_dT": max(abs(d) for d in dT),
        "rms_dT": rms(dT),
        "max_dV_pct": max(abs(100.0 * dV[i] / u_V[i]) for i in range(n_trays)),
        "rms_dV": rms(dV),
        "rms_dL": rms(dL),
        "converged": bool(col.solved()),
        "iters": int(col.getLastIterationCount()),
    }


def run_sweep():
    # Pair each TBP model with its "matching" EOS family. PR-flavoured models
    # are tested with a PR EOS; SRK-flavoured with SRK; generic ones with both.
    cases = [
        ("SRK",          "PedersenSRK"),
        ("SRK",          "PedersenSRKHeavyOil"),
        ("SRK",          "RiaziDaubert"),
        ("SRK",          "Lee-Kesler"),
        ("SRK",          "Twu"),
        ("SRK",          "Cavett"),
        ("SRK",          "Standing"),
        ("SRK-Peneloux", "PedersenSRK"),
        ("SRK-TwuCoon",  "PedersenSRK"),
        ("SRK-TwuCoon",  "Twu"),
        ("SRK-MathCop",  "PedersenSRK"),
        ("PR",           "PedersenPR"),
        ("PR",           "PedersenPR2"),
        ("PR",           "PedersenPRHeavyOil"),
        ("PR",           "Lee-Kesler"),
        ("PR",           "Twu"),
        ("PR-1978",      "PedersenPR"),
        ("PR-Danesh",    "PedersenPR"),
        ("PR-LeeKesler", "PedersenPR"),
        ("PR-MathCop",   "PedersenPR"),
    ]

    V_reb_uni = UNISIM_TRAY_PROFILE["V_kg_hr_topdown"][-1]
    T_top_uni = UNISIM_TARGET["overhead"]["temperature_C"]
    T_bot_uni = UNISIM_TARGET["bottoms"]["temperature_C"]

    print_header(
        "Sweep over (EOS x TBP characterization) — composition UNCHANGED")
    print(f"UniSim targets: T_top={T_top_uni:.2f} C, T_bot={T_bot_uni:.2f} C, "
          f"V_reboiler={V_reb_uni:.0f} kg/hr")
    print()
    hdr = (f"{'EOS':<14} {'TBP model':<22} "
           f"{'T_top':>7} {'T_bot':>7} "
           f"{'V_reb':>10} {'dV_reb%':>9} "
           f"{'maxdT':>7} {'rmsdT':>7} "
           f"{'rmsdV':>9} {'rmsdL':>10} "
           f"{'iters':>6} {'conv':>5}")
    print(hdr)
    print("-" * len(hdr))

    rows = []
    t0 = time.time()
    for eos, tbp in cases:
        sys.stdout.write(f"  running {eos:<14} {tbp:<22}\r")
        sys.stdout.flush()
        m = _run_one_case(eos, tbp)
        if m is None or "error" in m:
            err = (m or {}).get("error", "exception")
            print(f"{eos:<14} {tbp:<22} ERROR: {err}")
            continue
        rows.append((eos, tbp, m))
        print(f"{eos:<14} {tbp:<22} "
              f"{m['T_top']:>7.2f} {m['T_bot']:>7.2f} "
              f"{m['V_reb_kg_hr']:>10.0f} {m['V_reb_dev_pct']:>+9.2f} "
              f"{m['max_dT']:>7.2f} {m['rms_dT']:>7.2f} "
              f"{m['rms_dV']:>9.0f} {m['rms_dL']:>10.0f} "
              f"{m['iters']:>6} {str(m['converged']):>5}")
    print("-" * len(hdr))
    print(f"Sweep finished in {time.time() - t0:.1f} s, "
          f"{len(rows)} successful cases.")

    if not rows:
        return

    # Composite score: weight reboiler boilup deviation heavily (it's the
    # dominant symptom), plus tray T+V RMS.
    def score(m):
        return (abs(m["V_reb_dev_pct"]) +
                0.5 * m["rms_dT"] +
                0.0003 * m["rms_dV"])

    ranked = sorted(rows, key=lambda r: score(r[2]))
    print()
    print("Ranking (lower is closer to UniSim;  "
          "score = |dV_reb%| + 0.5*rmsdT + 3e-4*rmsdV):")
    print(f"{'rank':>4}  {'EOS':<14} {'TBP':<22} {'score':>8} "
          f"{'dV_reb%':>9} {'rmsdT':>7}")
    print("-" * 68)
    for i, (eos, tbp, m) in enumerate(ranked, start=1):
        print(f"{i:>4}  {eos:<14} {tbp:<22} {score(m):>8.2f} "
              f"{m['V_reb_dev_pct']:>+9.2f} {m['rms_dT']:>7.2f}")

    best = ranked[0]
    print()
    print(f"BEST: EOS = {best[0]}, TBP model = {best[1]}")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path,
                        default=Path(__file__).resolve().parent /
                        "column_study_profile.png",
                        help="Output PNG path for the profile plot.")
    parser.add_argument("--out-comp", type=Path,
                        default=Path(__file__).resolve().parent /
                        "column_study_compositions.png",
                        help="Output PNG path for the product composition bar chart.")
    parser.add_argument("--no-show", action="store_true",
                        help="Don't open the plot window (still saves PNG).")
    parser.add_argument("--sweep", action="store_true",
                        help="Sweep EOS x TBP characterization models "
                             "and rank by closeness to UniSim. Skips the "
                             "single-case profile/plot output.")
    parser.add_argument("--eos", type=str, default="SRK",
                        help=f"EOS for single run (one of {list(_EOS_CLASSES)})")
    parser.add_argument("--tbp", type=str, default="PedersenSRK",
                        help=f"TBP model for single run (one of {_TBP_MODELS})")
    args = parser.parse_args()

    if args.sweep:
        run_sweep()
        return

    print_header("Build fluid and streams")
    base_fluid = build_base_fluid(eos_name=args.eos, tbp_model=args.tbp)
    feed_stream = make_stream(base_fluid, "manual_column_feed", MANUAL_FEED,
                              MANUAL_FEED["mass_flow_kg_hr"], "kg/hr")
    top_feed_stream = make_stream(base_fluid, "top_stage_reflux", MANUAL_TOP_FEED,
                                  MANUAL_TOP_FEED["mass_flow_kg_hr"], "kg/hr")
    print(f"Main feed: {float(feed_stream.getFlowRate('kg/hr')):.1f} kg/hr  "
          f"({float(feed_stream.getFlowRate('kmol/hr')):.1f} kmol/hr)")
    print(f"Top feed : {float(top_feed_stream.getFlowRate('kg/hr')):.1f} kg/hr  "
          f"({float(top_feed_stream.getFlowRate('kmol/hr')):.1f} kmol/hr)")

    print_header("Build column")
    col, main_feed_ns, top_feed_ns = build_column(feed_stream, top_feed_stream)
    print(f"Main feed -> UniSim tray {MANUAL_COLUMN['main_feed_tray_topdown']} "
          f"(NeqSim stage {main_feed_ns})")
    print(f"Top feed  -> UniSim tray {MANUAL_COLUMN['top_feed_tray_topdown']} "
          f"(NeqSim stage {top_feed_ns})")

    print_header("Run column")
    t0 = time.time()
    col.run()
    print(f"Column run completed in {time.time() - t0:.2f} s")

    print_header("Stage setup (after run)")
    print_stage_setup(col)

    print_header("Convergence diagnostics")
    print_convergence(col, feed_stream, top_feed_stream)

    print_header("Comparison with UniSim reference")
    oh, btm = compare_with_unisim(col)

    print_header("Mass balance (overall)")
    print_mass_balance(feed_stream, top_feed_stream, oh, btm)

    print_header("Energy balance (overall)")
    print_energy_balance(feed_stream, top_feed_stream, oh, btm, col)

    print_header("Temperature & flow profile")
    plot_profile(col, args.out, show=not args.no_show)

    print_header("Product composition comparison (overhead y, bottoms x)")
    compare_product_compositions(col, args.out_comp, show=not args.no_show)


if __name__ == "__main__":
    main()
