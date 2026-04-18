---
name: neqsim-physics-explanations
description: "Physics explanations for common NeqSim results. USE WHEN: users ask 'why' about simulation results, when explaining unexpected behavior, or when adding educational context to notebooks and reports. Maps common engineering phenomena to plain-language explanations."
last_verified: "2026-07-04"
---

# NeqSim Physics Explanations

Reference explanations for common thermodynamic and process phenomena.
Use these to add educational context to simulation results.

## Thermodynamic Phenomena

### Joule-Thomson Effect
**What happens:** Gas cools when throttled (expanded through a valve) without doing work.
**Why:** At moderate pressures, intermolecular attraction dominates — expanding gas
molecules must overcome attractive forces, consuming internal energy, which lowers
temperature. At very high pressures (above the inversion temperature), repulsive
forces dominate and the gas heats up instead.
**Typical values:** Natural gas JT coefficient: 0.3-0.7 K/bar. CO2: 0.5-1.2 K/bar (higher because stronger intermolecular forces). Hydrogen: negative (heats on expansion at ambient T).
**Engineering significance:** JT cooling across choke valves can cause hydrate formation,
wax deposition, or auto-refrigeration below material design temperature limits (MDMT).

### Retrograde Condensation
**What happens:** A gas mixture forms liquid when pressure decreases (opposite of
what you'd expect — normally, lowering pressure should vaporize liquid).
**Why:** For mixtures between the cricondenbar and the cricondentherm on the phase
envelope, the dew point curve has a retrograde region where the mixture enters the
two-phase zone upon depressurization. This occurs because heavier components become
less soluble in the lighter gas phase at intermediate pressures.
**Engineering significance:** Rich gas/condensate pipelines may form liquid slugs during
depressurization. Separator design must account for liquid dropout at pipeline conditions.

### Hydrate Formation
**What happens:** Water molecules form cage-like crystalline structures around small
gas molecules (methane, ethane, propane, CO2, H2S) at high pressure and low temperature.
**Why:** At high pressure, gas molecules are forced close to water molecules. Below
a certain temperature (the hydrate equilibrium temperature), the water cages become
thermodynamically stable. The structure type depends on the guest molecule size:
sI (methane, CO2), sII (propane, i-butane), sH (large molecules with help gas).
**Prevention:** (1) Keep temperature above hydrate equilibrium + margin (typically 3-6 C),
(2) Inject thermodynamic inhibitors (MEG, methanol) to shift equilibrium,
(3) Inject low-dosage hydrate inhibitors (kinetic inhibitors, anti-agglomerants),
(4) Insulate pipelines, (5) Remove free water (dehydration).

### Phase Envelope
**What happens:** The phase envelope shows the boundary between single-phase and
two-phase regions on a P-T diagram.
**Key points:**
- **Cricondenbar:** Maximum pressure at which two phases can coexist — above this, always single phase regardless of temperature
- **Cricondentherm:** Maximum temperature at which two phases can coexist
- **Critical point:** Where liquid and gas become indistinguishable (same density, same properties)
- **Quality lines:** Iso-liquid-fraction curves inside the two-phase region

### Equation of State Selection
**Why SRK vs PR vs CPA matters:**
- **SRK (Soave-Redlich-Kwong):** Good for gas systems, light hydrocarbons. Simple, fast. Underestimates liquid density by 5-15%.
- **PR (Peng-Robinson):** Better liquid density than SRK (within 5-10%). Preferred for oil systems and reservoir fluids.
- **CPA (Cubic Plus Association):** Extends SRK with hydrogen bonding terms. Required for water, MEG, methanol, glycols. Without CPA, water solubility in hydrocarbons is wrong by orders of magnitude.
- **GERG-2008:** Multi-parameter reference EOS for natural gas. Highest accuracy (0.1% for density) but limited to gas-phase natural gas components. Used for custody transfer and fiscal metering.

## Process Equipment Phenomena

### Compressor Power and Efficiency
**What the numbers mean:**
- **Isentropic efficiency (70-85%):** Ratio of ideal (reversible, adiabatic) work to actual work. Higher = better compressor. New machines ~82-85%, aged ~70-78%.
- **Polytropic efficiency (75-88%):** More meaningful for multi-stage comparison because it's independent of gas properties. Always higher than isentropic for the same machine.
- **Discharge temperature:** Rises with pressure ratio and decreases with efficiency. High discharge T limits pressure ratio per stage (typically max 150-180 C for reciprocating, 200-230 C for centrifugal).
- **Surge:** Centrifugal compressors have a minimum flow below which they oscillate violently. Anti-surge systems recycle gas to maintain minimum flow.

### Separator Performance
**What determines separation quality:**
- **Residence time:** Gas bubbles need time to rise out of liquid; liquid droplets need time to fall out of gas. Typical: 3-5 minutes for oil/gas, 10-20 minutes for three-phase with emulsion.
- **Gas velocity:** Must stay below entrainment velocity or liquid droplets carry over to gas outlet. Souders-Brown equation: $V_{max} = K \sqrt{(\rho_L - \rho_G) / \rho_G}$
- **Liquid level:** Controls where gas/liquid interface sits. Instrumented with level transmitters and controlled via outlet valve.
- **Temperature effect:** Higher T reduces liquid density and viscosity, improving gas/liquid separation but increasing vapor pressure (more gas).

### Heat Exchanger Duty
**What the duty number means:**
- **Duty (W or kW):** Rate of heat transfer. $Q = \dot{m} \cdot C_p \cdot \Delta T$ for single phase, more complex with phase change.
- **LMTD:** Log-mean temperature difference — the driving force for heat transfer. Higher LMTD = smaller (cheaper) exchanger.
- **UA value:** Overall heat transfer coefficient times area. The exchanger's "size" in thermal terms.
- **Approach temperature:** Minimum temperature difference between hot and cold streams. Practical minimum: 3-10 C for liquid/liquid, 20-50 C for gas/gas.

### Pipeline Pressure Drop
**What causes it:**
- **Friction:** Wall shear stress removes energy from the flow. Dominates in long horizontal pipelines. Increases with velocity squared.
- **Elevation (hydrostatic):** $\Delta P_{hydrostatic} = \rho g \Delta h$. Dominates in risers and deep wells. For gas, much less than for liquid.
- **Acceleration:** Usually negligible except in two-phase flow where phase velocities change along the pipe.
- **Two-phase effects:** Liquid holdup, slip ratio, flow pattern transitions all increase pressure drop relative to single-phase. Beggs & Brill correlation captures these.

## Common "Why" Questions

### "Why is my density zero?"
You forgot to call `fluid.initProperties()` after the flash. The flash calculates
phase compositions and fractions, but transport/physical properties (density, viscosity,
thermal conductivity) require a separate initialization step.

### "Why does my water content look wrong with SRK?"
SRK (and PR) cannot model hydrogen bonding between water and hydrocarbons. They predict
water solubility in gas that is either too high or too low depending on conditions.
Use CPA (`SystemSrkCPAstatoil`) with mixing rule `10` for any system containing water.

### "Why is my compressor power negative?"
The outlet pressure is set lower than the inlet pressure. A compressor increases
pressure — use `ThrottlingValve` or `Expander` for pressure reduction.

### "Why doesn't my separator produce two phases?"
The inlet fluid may be entirely in one phase at the separator conditions. Run a
standalone TPflash on the feed fluid at separator P and T to verify. If only one
phase exists, separation isn't physically possible at those conditions.

### "Why is the Joule-Thomson coefficient so different for CO2 vs methane?"
CO2 has stronger intermolecular forces (quadrupole moment) than methane, so expanding
CO2 requires more energy to overcome molecular attraction, producing a larger
temperature drop per bar of pressure reduction.

### "Why does my hydrate temperature change when I add MEG?"
MEG (mono-ethylene glycol) is a thermodynamic hydrate inhibitor. It lowers the
activity of water, making it harder for water molecules to form the cage structures
around gas molecules. Colligative effect — more inhibitor = lower hydrate T.
Typical shift: 1-3 C per 10 wt% MEG in the aqueous phase.

### "Why is retrograde condensation a problem in pipelines?"
Liquid slugs in gas pipelines cause: (1) increased pressure drop, (2) equipment
damage from slug impact on bends and separators, (3) unstable flow delivery to
receiving facilities, (4) potential hydrate formation in the liquid phase.

### "Why do I need volume translation on my EOS?"
Standard SRK and PR systematically underpredict liquid density (5-15% error).
Volume translation adds a constant shift to the molar volume prediction without
changing the phase equilibrium (VLE). Use `SystemSrkEosvolcor` or `SystemPrEosvolcor`
when accurate liquid density is important (pipeline sizing, tank volumes).

### "Why does my three-phase flash not find the water phase?"
Call `fluid.setMultiPhaseCheck(true)` before the flash. Without this,
the solver only checks for gas/liquid equilibrium and may miss a second liquid
phase (aqueous). This is especially important for CPA systems with water.
