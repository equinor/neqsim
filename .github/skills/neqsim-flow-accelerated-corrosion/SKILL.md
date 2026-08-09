---
name: neqsim-flow-accelerated-corrosion
version: "1.0.0"
description: "Flow-accelerated corrosion (FAC) and high-temperature pH in closed heating- and cooling-medium loops — magnetite film stability, mass-transfer-controlled wall thinning at bends and welds, wall shear stress, in-situ pH at operating temperature versus laboratory pH, alkaline margin above neutrality, and Cr-Mo material upgrade screening. USE WHEN: a task involves localised wall thinning or leaks at bends, elbows or circumferential welds in carbon-steel tubing; a closed glycol/water or water heating-medium, cooling-medium, boiler-feedwater or condensate loop; a WHRU, steam generator or economiser tube bundle; pH control of an amine-buffered loop; or any question of the form 'is the fluid alkaline enough at operating temperature?'. Anchors on neqsim.process.corrosion."
last_verified: "2026-08-07"
requires:
  java_packages: [neqsim.process.corrosion, neqsim.process.chemistry.rca]
---

# NeqSim Flow-Accelerated Corrosion and High-Temperature pH Skill

Screening for **flow-accelerated corrosion (FAC)** — dissolution of the protective
magnetite film under mass-transfer control — and for the **in-situ pH** that
governs it.

## When this skill applies

- Localised wall thinning or leaks at **bends, elbows or circumferential welds**
  in carbon-steel tube bundles, with intact surfaces nearby.
- Closed **heating- or cooling-medium loops** (glycol/water or water),
  boiler-feedwater, condensate, WHRU / economiser / steam-generator tubes.
- Operating temperature roughly **90–250 °C**, peaking near 150 °C.
- Questions about **pH control** of an amine-buffered loop, or whether measured
  chemistry is adequate.

## Two mistakes to avoid before anything else

**1. Do not judge a hot system by its laboratory pH.**

Laboratory pH is measured on a cooled sample, typically at 20–25 °C. Corrosion
happens at 150 °C. Two things move, by different amounts:

- The amine pK$_a$ falls, so the buffered fluid becomes less alkaline.
- **Neutrality itself falls**: neutral water is pH 7.00 at 25 °C but about
  **pH 5.85 at 150 °C**.

So the meaningful quantity is the **alkaline margin**, $\text{pH}(T) - \text{pH}_{neutral}(T)$,
not the raw pH. Use [`AmineBufferedPH`](../../../src/main/java/neqsim/process/corrosion/AmineBufferedPH.java).

**2. Do not confuse FAC with erosion-corrosion.** They occur at the same
locations and need *different* mitigation:

| | FAC | Erosion-corrosion |
|---|---|---|
| Mechanism | Electrochemical dissolution, mass-transfer limited | Mechanical removal by particles/cavitation |
| Surface | Smooth, wavy, scalloped | Directional grooves, impingement craters |
| Fix | Chemistry, temperature, Cr alloying | Remove particles / impingement |

`RootCauseAnalyser` now raises both as separate candidates.

**3. Do not read a CFD wall-shear map as a FAC map at a separated feature.**

FAC is mass-transfer controlled. `FlowAcceleratedCorrosion` gets there through
Berger-Hau, $Sh = 0.0165\,Re^{0.86}Sc^{0.33}$, with a Stokes-Einstein diffusivity —
for a hot glycol/water loop $Sc$ lands near **280**, so the concentration boundary
layer is roughly $Sc^{1/3}\approx 6.5$ times thinner than the momentum one.

CFD studies usually deliver wall shear, and the tempting conversion is
$k_m\propto u^{*}\propto\sqrt{\tau_w}$. **That is only valid where the boundary layer
stays attached** — a bend, a gentle taper. At a weld root protrusion, an orifice or
any sudden expansion the flow separates and reattaches, and **at reattachment
$\tau_w$ passes through zero while $k_m$ peaks**. A shear map there puts its minimum
close to where the metal loss is worst, so:

- a shear-derived geometry factor is defensible for `ELBOW_BEND`, **not** for
  `WELD_ROOT_PROTRUSION` or `DOWNSTREAM_ORIFICE`;
- a low-shear separated wake in a CFD figure must not be described as low-risk.

To compute a weld factor honestly you need a passive scalar at the real $Sc$ on a
wall-resolved mesh, validated by reproducing the Berger-Hau $Sh$ in a straight run
first — the mass-transfer analogue of checking the straight-pipe $\tau_w$. The
`cfd-coupling` skill solves momentum only today, so state the screening factor and
its basis rather than substituting a shear ratio.

## Workflow

### 1. Get fluid properties from the actual fluid

Do not use water tables — glycol changes density and viscosity substantially,
and viscosity roughly halves between 100 °C and 150 °C, which nearly doubles
the Reynolds number at constant velocity.

```java
SystemInterface f = new SystemSrkCPAstatoil(273.15 + 150.0, 19.6);
f.addComponent("TEG", 45.0, "kg/sec");
f.addComponent("water", 55.0, "kg/sec");
f.setMixingRule(10);
new ThermodynamicOperations(f).TPflash();
f.initProperties();
double rho = f.getDensity("kg/m3");
double mu = f.getViscosity("cP");
```

### 2. Convert laboratory pH to in-situ pH

```java
AmineBufferedPHResult ph = new AmineBufferedPH()
    .setAmine(BufferAmine.DEA)
    .setMeasuredPH(8.7, 20.0)        // lab value and lab temperature
    .setOperatingTemperature(150.0)
    .setGlycolMassFraction(0.45)     // records a warning; not corrected
    .calculate();

ph.getOperatingPH();          // in-situ pH at 150 C
ph.getMarginAtOperating();    // the number that matters
ph.getVerdict();              // ROBUST / ADEQUATE / MARGINAL / INSUFFICIENT
```

### 2b. Ask how much buffer is left, not just how much margin

A positive margin does not say whether it sits on a fresh buffer or on the remnant of
a nearly exhausted one, and those call for different actions. `calculateAlkalineReserve`
locates the titration end point — the laboratory pH at which the margin at the
operating temperature reaches zero — and reports how much of the usable reserve has
already been consumed. The amine concentration cancels out, so **no dosing record is
needed**: a fluid sample is enough.

```java
AmineBufferedPH calc = new AmineBufferedPH()
    .setAmine(BufferAmine.DEA)
    .setMeasuredPH(8.7, 20.0)
    .setOperatingTemperature(150.0)
    .setGlycolMassFraction(0.45);

// 310 mg/L total organic acids, formate/acetate average molar mass 52.03 g/mol
AlkalineReserveResult r = calc.calculateAlkalineReserve(310.0, 52.03);

r.getMeasuredPHAtZeroMargin();          // 8.33 - the real control floor, not pH 7
r.getFreeBaseFractionAsFound();         // 0.31
r.getReserveSpentFraction();            // 0.82 - four fifths already gone
r.getRemainingAcidCapacityMgPerL();     // 68 mg/L of further acid
r.getRemainingAcidCapacityMmolPerL();   // 1.31 mmol/L - also prices a CO2 ingress
r.getDerivedAmineInventoryMmolPerL();   // cross-check against the dosing record
```

Call `calculateAlkalineReserve()` with no arguments when no acid analysis exists; the
fractions still come out, only the capacity fields are NaN. Use
`AmineBufferedPH.freeBaseFraction(pH, pKa)` to draw the titration curve.

**Why this changes the recommendation.** A margin of +0.37 reads as a small but
positive number. Expressed as a reserve it is the span from pH 8.7 down to pH 8.33,
with 82 % already spent — so total organic acid belongs in the control limits, not in
a trend plot, and a single sub-9 pH excursion is most of what is left.

### 2c. Price a CO2 or O2 ingress against that reserve

Dissolved CO2 titrates the same buffer mole for mole at these pH values, and O2
titrates it indirectly by oxidising the glycol to organic acids. Both therefore
convert into the same mmol/L currency as the remaining capacity. Get the solubility
from a flash of the actual glycol/water rather than a water table, and ask for the
concentration unit directly:

```java
double hCO2 = fluid.calcHenrysConstant("CO2", "mol/m3/bar");   // == mmol/L/bar
double hO2 = fluid.calcHenrysConstant("oxygen", "mol/m3/bar");
double dissolvedCO2mmolPerL = hCO2 * partialPressureBar;
```

Supported units are `bar` (the default, `f_i / x_i` per mole fraction), `mol/m3/bar`,
`mmol/L/bar`, `mol/L/bar` and `mg/L/bar`. **One mol/m3 is one mmol/L** — dividing by
1000 between them is the most common error in this calculation, which is why both
spellings are accepted. The method needs exactly two phases and throws rather than
returning zero if that is not the case.

### 3. Rank the FAC contributors

```java
FlowAcceleratedCorrosionResult fac = new FlowAcceleratedCorrosion()
    .setFlow(2.66, 0.025)
    .setFluidProperties(rho, mu)
    .setTemperature(150.0)
    .setInSituPH(ph.getOperatingPH())     // in-situ, never the lab value
    .setGeometry(FacGeometry.WELD_AT_BEND)
    .setChromiumContent(0.02)             // carbon steel; P11 is 1.25
    .calculate();

fac.getDominantFactor();       // which lever actually controls the outcome
fac.getWallShearStressPa();
fac.ratioTo(otherCase);        // quantify a proposed change
```

The index is **for comparison only** — ratios between cases are meaningful, the
absolute value is not a wall-loss rate.

## Things worth checking that investigations routinely miss

| Check | Why |
|---|---|
| **Express a velocity exceedance as shear** | $\tau \propto v^{1.75}$, so a 3 % velocity overshoot is a ~13 % shear overshoot. A "3 %" framing makes a real exceedance look like noise |
| **Correlate damage with the hot end** | Re rises ~70 % from 100 → 150 °C at constant velocity, and 150 °C is the solubility peak. Damage should concentrate at the outlet end — a cheap, testable prediction |
| **Check the filter micron rating** | Magnetite fines are 1–10 µm. An 80 µm element removes essentially none of them. "Improve filtration" must carry a rating |
| **Check the boiling margin** | Compute the bubble point; if bulk boiling is impossible, that hypothesis is closed off |
| **Flow reduction is not free** | Cutting flow to reduce shear reduces recovered duty, and at fixed duty raises metal temperature *towards* the 150 °C peak. Quantify the trade-off rather than asserting it |
| **Ask what the loop actually ran at** | A design basis says what the flow *should* be in stand-by; only the historian says how long it was there. See below — this is usually the cheapest decisive evidence in the whole study |

### Measure the exposure instead of assuming it

A closed heating-medium loop is usually served by two or more identical units on
an N+1 duty/stand-by rota, and the stand-by one runs at a small fraction of design
flow. That makes the loop a natural experiment: same fluid, same pH history, same
material, different duty. If some units have leaked and others have not, the
historian settles whether duty is the difference.

Resolve the tag family from the plant's tag database (all three of flow, outlet
temperature and outlet pressure exist for each unit), then report, per unit:

- the **fraction of live-loop time below each design threshold** — the stand-by
  flows the data sheet specifies, and the min-flow protection levels;
- the **median flow as a fraction of design**;
- the **maximum outlet temperature** against the high-temperature protection
  setting and the data sheet film limit.

On one WHRU loop this turned "the unit that runs continuously has not leaked" from
an absence of maintenance records into a measured 4 % versus 40-46 % of time below
20 % of design flow. It also **removed** a competing hypothesis: cycle count did
not discriminate at all, which corroborated a separate fatigue screening.

See `enterprise-plant-data` for the unit-reconciliation, outage-exclusion and
cycle-aliasing traps that decide whether those percentages mean anything.

### Find out how the bend is actually built before modelling it

Damage reported "at circumferential welds adjacent to bends" is a construction
question first and a fluid-dynamics question second. Retrieve the bundle
description and the *Return bends* block of the equipment data sheet before
assuming anything:

| What to look for | Why it changes the analysis |
|---|---|
| **Bend angle** | A 180° return is the norm in serpentine coils, not a 90° elbow. Dean secondary flow needs roughly a quarter to a half turn to develop, so a 90° case releases the flow before the vortex pair is established and **understates** both the extrados enhancement and its downstream persistence. A geometry factor computed on 90° is a lower bound for a 180° return |
| **Formed bend or separate fitting** | If the data sheet says the return bends are *forged fittings* (e.g. ASTM A234 WPB) rather than formed tube, then a circumferential butt weld exists at each end of every bend **by construction**. `WELD_AT_BEND` stops being a screening assumption and becomes the documented geometry |
| **Fitting grade vs tube grade** | A106 Gr. B tube welded to an A234 WPB fitting is a dissimilar-heat joint. Neither grade specifies chromium, and FAC rate is controlled by residual Cr at the **hundredths of a percent** level, so the lower-Cr side wastes preferentially and produces sharp thinning at the weld rather than general loss. The material certificates carry the analysis — this is a document retrieval, not a study |
| **Bend radius** | Frequently absent from the data sheet. If no *R*/*D* is documented, say so; do not let an assumed long-radius default pass as a retrieved value |

**Provenance trap that produced this entry.** A CFD script carried
`bend_angle_deg = 90.0` and `BEND_RADIUS_RATIO = 1.5` under a block comment
reading "all from the task (data sheet geometry)". The bore genuinely was from the
data sheet; the bend was not, and the controlled documents said 180°. A comment
that covers a block of constants can be true for one line and false for the next —
**source each geometry constant individually**, and put the document number next to
it rather than at the top of the block.

**And the angle moves where you inspect.** Re-solving that case at the documented
180° changed more than the number. At *R*/*D* = 1.5, Re ≈ 1.7 × 10⁵:

| | 90° bend | 180° return |
|---|---|---|
| Peak wall shear ratio | 2.33 | 2.74 |
| Geometry factor √R | 1.53 | 1.66 |
| **Where the peak sits** | intrados, near the entry | **55° off the intrados, past mid-turn** |

Over a half turn the Dean vortex pair becomes fully developed and convects
high-momentum core fluid outward along the side walls, so the maximum migrates off
the intrados towards the quarter position and is delayed to beyond the mid-point of
the arc. Downstream over 15–25 D the pattern inverts — extrados ≈ 1.5, intrados
≈ 0.9 in the separated wake. **Inspecting the intrados entry, which is the right
answer for a 90° elbow, is the wrong answer for a 180° return.**

## Material upgrade

About 1 % Cr gives roughly an order-of-magnitude improvement. NORSOK M-001
specifies **ASTM A335 P11** for WHRU heat-exchanger tubes; API RP 571 and API RP
661 both point to low-alloy Cr-Mo steel for FAC-prone service. If plain carbon
steel is installed where a standard specifies P11, that is a compliance finding,
not just an observation — check it explicitly with `neqsim-standards-lookup`.

## Reporting

`results.json` should carry: `in_situ_pH`, `alkaline_margin`, `wall_shear_stress_Pa`,
`fac_index_ratio` for each case compared, `dominant_factor`, and every entry of
`getWarnings()` in the assumptions/gaps register. Declare the ideal-solution
basis of the pH shift and the comparison-only nature of the FAC index.

## Related skills

- `neqsim-flow-assurance` — CO2/H2S corrosion in production systems (a *different* mechanism; NORSOK M-506 does not apply to a CO2-free closed loop)
- `neqsim-root-cause-analysis` — ranking FAC against other candidates
- `neqsim-standards-lookup` — NORSOK M-001, API RP 571 §3.9, API RP 669, API RP 661
- `neqsim-self-heating-ignition` — the same Arrhenius framework applied to glycol oxidative degradation, which generates the organic acids that consume alkalinity
- `neqsim-plant-data` / `enterprise-plant-data` — measured stand-by exposure, the duty comparison between units, and the traps in quoting "% of design flow"
- `enterprise-maintenance-api` — leak distribution per tag and equipment-availability history (filter outages), which pairs with the historian evidence
