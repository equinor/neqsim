"""Method qualification registry and threat catalogue for the flow-assurance study report.

Qualification vocabulary follows ``docs/process/twofluidpipe-evidence-matrix.md``: *implemented* says only that a code path
exists; *numerically verified* means an executable analytic/conservation check passes; *experimentally qualified* needs a
reproducible measured-data comparison with declared gates. Nothing here promotes a method beyond its recorded evidence.
"""
import os
import re

EVIDENCE_MATRIX_REL = os.path.join("docs", "process", "twofluidpipe-evidence-matrix.md")

# status values of a threat in the applicability matrix
THREAT_STATUSES = ("calculated", "not_applicable", "open", "unsupported")

METHODS = [
    {"id": "M-HYD-BB", "name": "Steady multiphase hydraulics - Beggs and Brill (NeqSim PipeBeggsAndBrills)",
     "implemented": "yes", "numerically_verified": "partly (project analytic checks: hydrostatic head, single-phase friction, heat loss)",
     "experimentally_qualified": "no general claim in this toolkit",
     "use": "steady-state pressure drop, holdup, flow-regime label and mixture velocity along a route",
     "not_for": "transient or resolved slugging, severe/terrain slugging, pig tracking, restart sweep-out, long-duration liquid-rich transients",
     "limits": "empirical correlation derived from small-diameter laboratory data; accuracy for large-diameter, long, wet-gas subsea lines is not established here; "
               "SRK equation of state with classic mixing rule (no association) is used for the hydraulic fluid"},
    {"id": "M-HYD-CPA", "name": "Hydrate equilibrium temperature - SRK-CPA with hydrate check (ThermodynamicOperations.hydrateFormationTemperature)",
     "implemented": "yes", "numerically_verified": "partly (grid interpolation check; methane benchmark against secondary literature points)",
     "experimentally_qualified": "no (secondary-source methane check only; no study-fluid or inhibitor measurements)",
     "use": "equilibrium (thermodynamic) hydrate temperature at a given pressure and aqueous inhibitor mass fraction",
     "not_for": "hydrate formation or plugging kinetics, time to plug, hydrate dissociation rates, salt/electrolyte effects (not modelled)",
     "limits": "evaluated for a free aqueous phase on a pressure grid and interpolated in ln(p); C7+ pseudo-components are excluded because they can stall the solver"},
    {"id": "M-THERM-U", "name": "Layered overall heat-transfer coefficient (cylindrical conduction + external film or buried shape factor)",
     "implemented": "yes", "numerically_verified": "yes (closed-form construction; SPECIFIED_U inner-area basis checked against the NTU solution)",
     "experimentally_qualified": "no",
     "use": "effective U-value of each segment referenced to the inner area for the steady thermal solution and cooldown",
     "not_for": "field-joint, bulkhead or end-effect heat loss, wet-insulation degradation, buried-pipe transients",
     "limits": "internal film neglected (conservative); constant material properties; external film coefficient is an input"},
    {"id": "M-COOL-LUMP", "name": "Lumped exponential cooldown after shut-in (fluid + steel wall)",
     "implemented": "yes", "numerically_verified": "yes (RK4 integration against the closed form)",
     "experimentally_qualified": "no",
     "use": "time for the retained fluid to cool from the steady temperature to a stated criterion temperature (for example the hydrate equilibrium temperature)",
     "not_for": "plug formation time, natural convection, phase redistribution, latent heat, pressure decay, restart warm-up",
     "limits": "insulation thermal mass excluded (conservative); fluid properties frozen at the steady state; no axial conduction"},
    {"id": "M-INHIB-BAL", "name": "Inhibitor mass balance (lean stream, aqueous mass fraction)",
     "implemented": "yes", "numerically_verified": "yes (unit-tested mass balance)", "experimentally_qualified": "not applicable",
     "use": "aqueous inhibitor mass fraction and lean injection rate from produced water rate and lean purity",
     "not_for": "inhibitor losses to gas and condensate, dilution by condensed water, regeneration",
     "limits": "all inhibitor assumed to stay in the aqueous phase"},
    {"id": "M-TWOFLUID", "name": "Transient two-fluid pipe model (NeqSim TwoFluidPipe)",
     "implemented": "yes (API exists)", "numerically_verified": "see the evidence matrix rows", "experimentally_qualified": "NOT QUALIFIED for the transient slug, pig and severe-slugging uses (see the evidence matrix)",
     "use": "not used for any result in this report",
     "not_for": "any qualified transient result; its existence is not qualification",
     "limits": "failed and not-qualified rows of the evidence matrix are preserved unchanged in the report appendix"},
]

THREATS = [
    ("T-HYD-STEADY", "Steady pressure, temperature, phase rates, velocity and holdup profiles", "calculated", ["M-HYD-BB", "M-THERM-U"]),
    ("T-CAP", "Capacity, required source pressure (backpressure) and turndown envelope", "calculated", ["M-HYD-BB"]),
    ("T-HYDRATE", "Hydrate equilibrium margin, inhibited and uninhibited, inhibitor injection balance", "calculated", ["M-HYD-CPA", "M-INHIB-BAL"]),
    ("T-THERMAL", "Heat loss, arrival temperature and insulation alternatives", "calculated", ["M-THERM-U", "M-HYD-BB"]),
    ("T-COOLDOWN", "Cooldown time to the hydrate equilibrium temperature after shut-in", "calculated", ["M-COOL-LUMP"]),
    ("T-EROSION-VEL", "Mixture velocity against the project erosional-velocity limit", "calculated", ["M-HYD-BB"]),
    ("T-STARTUP", "Startup, restart, ramp-up, depressurisation, liquid accumulation and sweep-out", "unsupported", []),
    ("T-PIGGING", "Pigging and slug-receiving requirements", "unsupported", []),
    ("T-SEVERE-SLUG", "Severe slugging and terrain-induced slugging", "unsupported", []),
    ("T-WAX", "Wax appearance temperature and deposition", "open", []),
    ("T-ASPHALTENE", "Asphaltene stability", "open", []),
    ("T-SCALE", "Mineral scale and brine compatibility", "open", []),
    ("T-CORROSION", "CO2 corrosion (NORSOK M-506 basis) and inspected metal loss", "open", []),
    ("T-SAND", "Sand production and erosion", "open", []),
    ("T-SURGE", "Acoustic surge and water/liquid hammer", "open", []),
    ("T-INJ-PHASE", "Injection-fluid phase behaviour (inhibitor partitioning, freezing, losses)", "open", []),
]

DEFAULT_OPEN_REASONS = {
    "T-STARTUP": "no qualified transient solver is available in this toolkit for restart/ramp-up/sweep-out; an external-simulator hand-off package is written",
    "T-PIGGING": "pig tracking and slug receiving are not qualified in NeqSim (see the TwoFluidPipe evidence matrix); an external-simulator hand-off package is written",
    "T-SEVERE-SLUG": "Beggs and Brill does not resolve slugging and the TwoFluidPipe severe-slugging rows are not qualified; reported as open, never as safe",
    "T-WAX": "no wax appearance temperature or wax characterisation was supplied",
    "T-ASPHALTENE": "no asphaltene stability data was supplied and no applicability judgement was recorded in the basis",
    "T-SCALE": "no brine composition was supplied",
    "T-CORROSION": "NORSOK M-506 needs water chemistry (pH, bicarbonate, glycol) and wall shear that were not supplied; the standard text was not read",
    "T-SAND": "no sand production statement was recorded in the basis",
    "T-SURGE": "no valve-closure or pump-trip events were defined in the basis",
    "T-INJ-PHASE": "inhibitor losses to the hydrocarbon and gas phases and MEG freezing/phase split were not calculated",
}


def parse_evidence_matrix(repo_root):
    """Read the TwoFluidPipe capability rows (5 columns) from the evidence matrix; returns ``(rows, source_note)``.

    Rows keep their original wording. When the file is missing the result is ``([], 'unavailable ...')`` and the report
    states that the qualification matrix could not be read - it is never replaced by an invented summary.
    """
    path = os.path.join(repo_root, EVIDENCE_MATRIX_REL)
    if not os.path.isfile(path):
        return [], "unavailable: %s not found" % EVIDENCE_MATRIX_REL
    rows = []
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            if not line.startswith("|"):
                continue
            cells = [c.strip() for c in re.split(r"(?<!\\)\|", line.strip().strip("|"))]
            if len(cells) != 5 or set(cells[0]) <= set("-: ") or cells[0].startswith("Capability"):
                continue
            rows.append({"capability": cells[0], "implemented": cells[1], "numerically_verified": cells[2],
                         "experimentally_qualified": cells[3], "evidence": cells[4]})
    return rows, "read from %s" % EVIDENCE_MATRIX_REL
