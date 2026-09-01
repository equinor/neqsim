---
name: neqsim-self-heating-ignition
version: "1.0.0"
description: "Self-heating, spontaneous ignition and thermal-criticality screening for reactive porous media — lagging fires from combustible liquid absorbed into porous thermal insulation, Frank-Kamenetskii and Semenov criticality, critical layer thickness, critical surface temperature, induction time to ignition, and Arrhenius parameter fitting from hot-storage (basket) tests per EN 15188 / ASTM E2021. USE WHEN: a task involves a flammable liquid leak or spill onto insulation or lagging, oil-soaked lagging, spontaneous combustion, self-ignition, smouldering, a fire with no obvious ignition source, insulation-material selection driven by absorbency, or fitting oxidation kinetics from oven/basket test data. Anchors on neqsim.process.safety.selfheating."
last_verified: "2026-08-07"
requires:
  java_packages: [neqsim.process.safety.selfheating, neqsim.process.safety.reaction]
---

# NeqSim Self-Heating and Spontaneous Ignition Skill

Screening for **low-temperature self-heating leading to spontaneous ignition** —
the mechanism behind *lagging fires*, where a combustible liquid soaks into porous
thermal insulation and ignites with no external ignition source.

## When this skill applies

Reach for this skill when you see any of these signals:

- A fire or smoke event on insulated pipework, vessels or valves with **no
  identified ignition source**.
- A leak of a combustible liquid — glycol (MEG/DEG/TEG), heat-transfer oil,
  lubricating oil, hydraulic fluid, amine, diesel — onto or into **porous
  insulation** (mineral wool, glass wool, calcium silicate, perlite).
- Questions phrased as *"can this liquid self-ignite?"*, *"is this insulation
  safe?"*, *"how thick can the lagging be?"*, *"why did it catch fire hours
  after the leak stopped?"*
- A request to interpret **oven / basket / hot-storage test** results, or to
  extrapolate small-scale test data to plant scale.

## The one thing to get right

**Self-heating is a size-dependent problem, not a temperature-only problem.**

Heat is generated throughout the volume (scales with $r^3$) but lost through the
surface (scales with $r^2$). So the *same material at the same temperature* can be
perfectly stable as a thin film and ignite as a thick layer.

This is why the lumped adiabatic screening in
`neqsim.process.safety.reaction.RunawayReactionAnalyzer` **cannot answer this
question** — it assumes a well-stirred mass with no spatial conduction, so it has
no concept of a critical thickness or a critical surface temperature. Do not
substitute it. Use `neqsim.process.safety.selfheating`.

Equally, **do not use a Gibbs/equilibrium calculation to decide whether something
self-ignites.** `GibbsReactor` will report that any hydrocarbon is fully oxidised
to CO₂ and H₂O at 20 °C, because that is thermodynamically true and kinetically
irrelevant. Equilibrium gives you the *fuel* ($\Delta H$); only kinetics give you
the *hazard*.

## Model selection

| Situation | Model | Class |
|---|---|---|
| Liquid absorbed in insulation, internal gradients matter (Bi ≫ 1) | Frank-Kamenetskii | `PorousMediaSelfHeatingAnalyzer` |
| Drained pool, thin film, small sample; uniform temperature (Bi ≪ 1) | Semenov | `SemenovSelfHeatingAnalyzer` |
| *When* will it ignite, not just *whether* | Transient 1-D conduction + Arrhenius source | `SelfHeatingInductionSolver` |
| Fitting E and P from oven test data | Linearised criticality regression | `BasketTestRegression` |

## Governing relations

Frank-Kamenetskii criticality parameter:

$$
\delta = \frac{E\,P\,r^{2}}{\lambda\,R\,T_a^{2}}\exp\!\left(-\frac{E}{R\,T_a}\right)
$$

with $P = A\,Q\,\rho$ the volumetric heat-release pre-exponential factor [W/m³],
$E$ the activation energy [J/mol], $r$ the characteristic half-dimension [m],
$\lambda$ the effective conductivity of the *wetted* medium [W/(m·K)] and $T_a$
the boundary temperature [K]. A steady state exists only while
$\delta \le \delta_{crit}$, where $\delta_{crit}$ depends only on shape:
slab 0.878, infinite cylinder 2.00, sphere 3.32, cube 2.52, equicylinder 2.76.

Semenov criticality parameter, $\psi_{crit} = 1/e$:

$$
\psi = \frac{E\,V\,P}{h\,S\,R\,T_a^{2}}\exp\!\left(-\frac{E}{R\,T_a}\right)
$$

## Standard workflow

### 1. Get the kinetics — they are measured, never calculated

$E$ and $P$ cannot come from thermodynamics. Sources, in order of preference:

1. Basket / hot-storage test on the actual soaked material → `BasketTestRegression`.
2. Published data for the same liquid/substrate pair.
3. A screening estimate, **clearly flagged as an assumption** in `results.json`.

```java
BasketTestRegression fit = new BasketTestRegression();
fit.setEffectiveThermalConductivity(0.09);       // wetted insulation, not dry
fit.addPoint(SelfHeatingGeometry.CUBE, 25.0, "mm", 168.0, "C");
fit.addPoint(SelfHeatingGeometry.CUBE, 50.0, "mm", 149.0, "C");
fit.addPoint(SelfHeatingGeometry.CUBE, 100.0, "mm", 132.0, "C");
BasketTestRegressionResult k = fit.regress();
// k.getActivationEnergyJPerMol(), k.getVolumetricPreFactorWPerM3(), k.getRSquared()
```

Require at least three basket sizes and check `getRSquared()` before trusting the fit.

### 2. Screen the plant-scale geometry

```java
PorousMediaSelfHeatingResult r = k
    .createAnalyzer(SelfHeatingGeometry.SLAB, 50.0, "mm", 180.0, "C")
    .analyze();

r.getVerdict();                 // SUBCRITICAL / MARGINAL / SELF_IGNITION
r.getCriticalTemperatureK();    // hottest safe surface for this thickness
r.getCriticalDimensionM();      // thickest safe layer at this temperature
```

For lagging on a hot line, use the convenience configuration, which applies a
conservative bounding assumption (slab half-dimension = full insulation thickness,
boundary temperature = pipe wall temperature) and records that assumption in
`getWarnings()`:

```java
new PorousMediaSelfHeatingAnalyzer()
    .setEffectiveThermalConductivity(0.09)
    .setActivationEnergy(110.0, "kJ/mol")
    .setVolumetricHeatReleasePreFactor(5.0e13)
    .forPipeInsulation(50.0, "mm", 180.0, "C")
    .analyze();
```

### 3. If supercritical, get the induction time

```java
SelfHeatingInductionResult t = new SelfHeatingInductionSolver()
    .setGeometry(SelfHeatingGeometry.SLAB)
    .setCharacteristicDimension(50.0, "mm")
    .setEffectiveThermalConductivity(0.09)
    .setBulkProperties(150.0, 1200.0)      // bulk density, specific heat
    .setActivationEnergy(110.0, "kJ/mol")
    .setVolumetricHeatReleasePreFactor(5.0e13)
    .setBoundaryTemperature(180.0, "C")
    .setMaxTime(30.0, "day")
    .solve();

t.isIgnited();
t.getInductionTimeHours();     // typically hours to days — not seconds
```

### 4. Hand off the consequence

A predicted ignition is a **source term**, not the end of the study. Continue to
`neqsim-consequence-analysis` (pool/jet fire radiation) and
`neqsim-relief-flare-network` if the fire exposes pressurised equipment.

## Input guidance and common mistakes

| Input | Guidance | Common mistake |
|---|---|---|
| $\lambda$ effective conductivity | Use the **wetted** composite value; absorbed liquid displaces pore air and raises it well above the dry rating | Using the dry insulation datasheet value — non-conservative |
| $r$ characteristic dimension | Must match `SelfHeatingGeometry.getDimensionDescription()` (half-thickness for a slab, radius for cylinder/sphere) | Passing full thickness where half-thickness is expected, or vice versa |
| $P$ pre-factor | $P = A\,Q\,\rho$ where $\rho$ is **reactive liquid mass per bulk volume**, not the insulation density | Using insulation bulk density instead of liquid loading |
| $T_a$ boundary temperature | The hot process surface, not ambient air, for lagging on a hot line | Using ambient air temperature — badly non-conservative |
| Geometry for transient solver | Only SLAB / INFINITE_CYLINDER / SPHERE are one-dimensional | Passing CUBE to `SelfHeatingInductionSolver` (rejected) |

## Interpreting the verdict

- `SUBCRITICAL` — a stable steady state exists. Still report the margin; the
  steady self-heating excess is only a few kelvin, so there is **no temperature
  warning before runaway**.
- `MARGINAL` — within 30 % of criticality. Small changes in liquid loading,
  thickness or surface temperature tip it over. Recommend confirmatory testing.
- `SELF_IGNITION` — no steady state exists. Report the induction time and treat
  as a credible fire scenario.

## Reporting

Populate `results.json` with:

- `key_results`: `delta`, `delta_crit`, `critical_temperature_C`,
  `critical_thickness_mm`, `induction_time_hours`, `verdict`
- `assumptions`: kinetic-parameter provenance (measured vs assumed), effective
  conductivity basis, and the pipe-insulation bounding assumption if used
- `standards_applied`: EN 15188, ASTM E2021 for the test method
- Carry every entry of `getWarnings()` into the assumptions/gaps register

## Mitigation hierarchy (for recommendations)

1. **Eliminate absorbency** — closed-cell or non-absorbing insulation, so there is
   no porous matrix to hold the liquid and trap the heat. This removes the
   mechanism rather than managing it, and is normally the correct recommendation.
2. **Prevent wetting** — eliminate the leak; fit liquid-tight cladding and
   drainage that sheds leakage clear of the lagging.
3. **Reduce thickness** below the critical dimension, or lower the surface
   temperature below the critical temperature.
4. **Detect and inspect** — remove and replace contaminated insulation promptly;
   note that detection is weak as a sole barrier because the pre-runaway
   temperature signature is only a few kelvin.

## Related skills

- `neqsim-process-safety` — HAZOP guideword *Other than → self-ignition*, LOPA, risk matrix
- `neqsim-consequence-analysis` — fire radiation once ignition is predicted
- `neqsim-reaction-engineering` — `KineticReaction` if a full reactor model of the oxidation is needed
- `neqsim-hazid-fmea-eta-fta` — placing the scenario in an event tree
- `neqsim-standards-lookup` — EN 15188, ASTM E2021, insulation standards
