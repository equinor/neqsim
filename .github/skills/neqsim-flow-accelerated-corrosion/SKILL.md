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
