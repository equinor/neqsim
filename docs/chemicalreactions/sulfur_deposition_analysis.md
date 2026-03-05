---
title: "Sulfur Deposition and Corrosion Analysis in NeqSim"
description: "Comprehensive guide to simulating elemental sulfur formation, S8 solubility, solid sulfur precipitation, and FeS corrosion in natural gas systems using NeqSim's GibbsReactor and SulfurDepositionAnalyser."
---

## Overview

NeqSim provides integrated tools for analysing sulfur behaviour in gas value chains — from wellhead to sales gas. This covers:

- **Chemical equilibrium**: H₂S + O₂ reactions producing S₈, SO₂, H₂O via Gibbs energy minimisation
- **Sulfur solubility**: S₈ dissolved in the gas phase at elevated T and P
- **Solid sulfur precipitation**: Desublimation of S₈ when gas cools below the sulfur dew point
- **Corrosion assessment**: FeS formation risk, NACE MR0175 sour severity, SO₂/H₂SO₄ corrosion

These capabilities are relevant for both **offshore** (subsea pipelines, subsea processing) and **onshore** (gas processing trains, Claus sulfur recovery units) operations.

## Key Reactions

| Reaction                     | Description                       | NeqSim Tool              |
| ---------------------------- | --------------------------------- | ------------------------ |
| 2 H₂S + O₂ → 2 H₂O + ¼ S₈    | Claus reaction (direct oxidation) | GibbsReactor             |
| 2 H₂S + 3 O₂ → 2 SO₂ + 2 H₂O | Full oxidation of H₂S             | GibbsReactor             |
| 3 H₂S + SO₂ → 4 S + 2 H₂O    | Claus tail gas reaction           | GibbsReactor             |
| S₈(gas) → S₈(solid)          | Desublimation / precipitation     | TPSolidflash             |
| Fe + H₂S → FeS + H₂          | Iron sulfide corrosion            | SulfurDepositionAnalyser |

## Components and Tools

### 1. GibbsReactor — Chemical Equilibrium

The `GibbsReactor` finds equilibrium compositions by minimising total Gibbs free energy subject to element balance constraints:

$$G = \sum_i n_i \left( G_i^0 + RT \ln \frac{f_i}{f_i^0} \right)$$

The solver uses Newton–Raphson iteration with Lagrange multipliers for element conservation (C, H, O, N, S, Ar). Species thermodynamic data (standard Gibbs energy of formation, heat capacity polynomials) are loaded from `GibbsReactDatabase.csv` and `DatabaseGibbsFreeEnergyCoeff.csv`.

**Key configuration:**

| Method                          | Description                       | Default    |
| ------------------------------- | --------------------------------- | ---------- |
| `setEnergyMode("isothermal")`   | Isothermal or adiabatic operation | ISOTHERMAL |
| `setMaxIterations(5000)`        | Maximum Newton–Raphson iterations | 5000       |
| `setConvergenceTolerance(1e-4)` | Convergence criterion             | 1e-6       |
| `setDampingComposition(0.01)`   | Damping factor for stability      | 0.01       |
| `setComponentAsInert(name)`     | Mark component as non-reactive    | —          |

**Java example:**
```java
SystemInterface fluid = new SystemSrkEos(273.15 + 400.0, 1.5);
fluid.addComponent("H2S", 2.0);
fluid.addComponent("oxygen", 1.0);
fluid.addComponent("water", 0.0);
fluid.addComponent("S8", 0.0);
fluid.addComponent("SO2", 0.0);
fluid.setMixingRule(2);

Stream feed = new Stream("feed", fluid);
feed.run();

GibbsReactor reactor = new GibbsReactor("Claus", feed);
reactor.setEnergyMode("isothermal");
reactor.setMaxIterations(5000);
reactor.setDampingComposition(0.01);
reactor.run();
// Outlet stream contains equilibrium composition
```

### 2. TPSolidflash — Sulfur Solubility and Precipitation

NeqSim models solid S₈ using a solid fugacity approach. The equilibrium condition is:

$$f_{\text{S8}}^{\text{solid}}(T, P) = f_{\text{S8}}^{\text{gas}}(T, P, y)$$

The solid fugacity uses a sublimation pressure correlation with Poynting correction for pressure. When gas cools below the **sulfur dew point**, S₈ desublimes as a solid phase.

**Java example:**
```java
SystemInterface gas = new SystemSrkEos(273.15 + 50.0, 150.0);
gas.addComponent("methane", 0.90);
gas.addComponent("H2S", 0.05);
gas.addComponent("S8", 1e-8);
gas.setMixingRule(2);
gas.setMultiPhaseCheck(true);
gas.setSolidPhaseCheck("S8");

ThermodynamicOperations ops = new ThermodynamicOperations(gas);
ops.TPSolidflash();

boolean solidPresent = gas.hasPhaseType("solid");
```

### 3. SulfurDepositionAnalyser — Integrated Unit Operation

The `SulfurDepositionAnalyser` is a process equipment unit that combines all sulfur analysis capabilities in a single `run()` call:

1. **Chemical equilibrium** — Gibbs reactor for H₂S + O₂ reactions
2. **Sulfur solubility** — TP-solid flash for S₈ in gas
3. **Temperature sweep** — Scans temperature range to find deposition onset
4. **Corrosion assessment** — FeS risk, NACE sour severity, SO₂ corrosion

**Java example:**
```java
SystemInterface gas = new SystemSrkEos(273.15 + 80.0, 150.0);
gas.addComponent("methane", 0.82);
gas.addComponent("ethane", 0.05);
gas.addComponent("propane", 0.02);
gas.addComponent("CO2", 0.03);
gas.addComponent("H2S", 0.05);
gas.addComponent("nitrogen", 0.02);
gas.addComponent("water", 0.005);
gas.addComponent("oxygen", 0.00005);
gas.addComponent("S8", 1e-8);
gas.addComponent("SO2", 0.0);
gas.setMixingRule(2);

Stream feed = new Stream("feed", gas);
feed.setFlowRate(100000, "kg/hr");
feed.run();

SulfurDepositionAnalyser analyser =
    new SulfurDepositionAnalyser("Sulfur Analyser", feed);
analyser.setTemperatureSweepRange(0, 200, 5);  // 0-200 C in 5 C steps
analyser.run();

// Results
double onsetT = analyser.getSulfurDepositionOnsetTemperature();   // °C
double solubility = analyser.getSulfurSolubilityMgSm3();          // mg/Sm³
boolean corrosionRisk = analyser.hasCorrosionRisk();
String jsonReport = analyser.getResultsAsJson();
```

**Key result accessors:**

| Method                                  | Returns                          |
| --------------------------------------- | -------------------------------- |
| `getSulfurSolubilityInGas()`            | S₈ mole fraction in gas at inlet |
| `getSulfurSolubilityMgSm3()`            | S₈ concentration in mg/Sm³       |
| `getSulfurDepositionOnsetTemperature()` | Onset temperature (°C) or NaN    |
| `isSolidSulfurPresent()`                | Whether solid S₈ exists at inlet |
| `hasCorrosionRisk()`                    | Overall corrosion risk flag      |
| `getEquilibriumComposition()`           | Map of component → mole fraction |
| `getTemperatureSweepResults()`          | List of T-sweep data points      |
| `getCorrosionAssessment()`              | Map of corrosion assessment data |
| `getResultsAsJson()`                    | Comprehensive JSON report        |

## Application Scenarios

### Offshore Subsea Pipeline

A sour gas pipeline from wellhead (120 °C, 200 bar) to shore (4 °C, 80 bar) experiences continuous cooling. Sulfur solubility decreases with temperature, leading to S₈ precipitation in mid-pipeline sections. Use the temperature sweep to identify the critical pipeline segment where solid deposition begins.

**Risk factors:**
- Subsea pipelines cool to seabed ambient (2–8 °C)
- Pressure drop reduces solubility further
- Solid sulfur accumulates in low-velocity zones and dead legs
- FeS scale forms on internal pipe surfaces

**Mitigation:**
- Pipeline insulation to maintain temperature above sulfur dew point
- Chemical sulfur solvents (carbon disulfide, mineral oils)
- Regular pigging campaigns
- Corrosion inhibitor injection

### Onshore Gas Processing

In gas processing plants, the highest sulfur deposition risk locations are:

1. **JT valve / turboexpander outlet** — Joule–Thomson cooling causes the largest temperature drop (can reach −25 °C)
2. **Cold separator internals** — Condensed phases collect solid sulfur
3. **Heat exchanger tubes** — Gradual cooling zones
4. **Gas metering stations** — Pressure reduction

Use the analyser at each process point to map risk levels.

### Claus Sulfur Recovery Unit (SRU)

The GibbsReactor directly simulates the Claus process equilibrium:
- **Thermal stage** (1000+ °C): H₂S + O₂ → S₂ + H₂O
- **Catalytic stages** (200–350 °C): 2 H₂S + SO₂ → 3 S + 2 H₂O

The O₂/H₂S ratio is critical — stoichiometric ratio of 0.5 maximises S₈ yield; excess O₂ produces SO₂ instead.

## Corrosion Assessment

The analyser evaluates corrosion risk based on industry standards:

### NACE MR0175 Sour Severity

| H₂S Partial Pressure | Classification |
| -------------------- | -------------- |
| < 0.3 kPa            | Non-sour       |
| 0.3 – 1.0 kPa        | Mild sour      |
| 1.0 – 10 kPa         | Moderate sour  |
| > 10 kPa             | Severe sour    |

### FeS Formation

Iron sulfide (FeS) forms whenever H₂S contacts carbon steel in the presence of water:
- $\Delta G_f^0 = -100.4$ kJ/mol (thermodynamically favourable at all temperatures)
- Corrosion rate accelerates above 60 °C
- FeS scale morphology determines whether it provides protection

### SO₂ / H₂SO₄ Corrosion

When SO₂ is present (from H₂S oxidation) and water condenses:
- SO₂ + H₂O → H₂SO₃ (sulfurous acid)
- Further oxidation produces H₂SO₄ (sulfuric acid)
- Requires corrosion-resistant alloys or inhibitor injection

## Database Entries

Sulfur species thermodynamic data is stored in:

| File                               | Species                                           | Data                                   |
| ---------------------------------- | ------------------------------------------------- | -------------------------------------- |
| `GibbsReactDatabase.csv`           | H₂S, S, S₂, S₈, SO₂, SO₃, H₂SO₄, FeS, Fe₂O₃, FeS₂ | Element counts, Cp, ΔHf, ΔGf, ΔSf      |
| `DatabaseGibbsFreeEnergyCoeff.csv` | S₈, SO₂, SO₃, H₂S, H₂SO₄                          | Polynomial coefficients for G(T), H(T) |
| `COMP.csv`                         | S₈, SO₂                                           | Component properties for EOS           |
| `COMPSALT.csv`                     | FeS                                               | Salt equilibrium data                  |

## Demo Notebook

A comprehensive Jupyter notebook demonstrating all capabilities is available at:

[examples/sulfurtask/SulfurDepositionAnalysis.ipynb](../../examples/sulfurtask/SulfurDepositionAnalysis.ipynb)

The notebook covers:
1. Gas composition setup with H₂S and S₈
2. Solid phase configuration and TP-solid flash
3. Sulfur solubility maps (T-P grids)
4. Sulfur saturation envelope / deposition zone
5. Gibbs reactor for Claus reactions
6. Temperature sweep of equilibrium products
7. O₂/H₂S ratio sensitivity
8. SulfurDepositionAnalyser with corrosion assessment
9. Offshore pipeline sulfur dropout simulation
10. Onshore gas processing risk mapping
11. H₂S concentration sensitivity analysis
12. JSON reporting

## Source Code

| File                                                        | Description                        |
| ----------------------------------------------------------- | ---------------------------------- |
| `neqsim.process.equipment.reactor.SulfurDepositionAnalyser` | Integrated analyser unit operation |
| `neqsim.process.equipment.reactor.GibbsReactor`             | Gibbs energy minimisation reactor  |
| `neqsim.process.equipment.reactor.GibbsReactorCO2`          | Specialised CO₂/acid gas reactor   |
| `neqsim.thermodynamicoperations.flashops.SolidFlash1`       | TP-solid flash implementation      |

## Related Documentation

- [Chemical Reactions Module](README.md)
- [Chemical Reaction Deep Review](CHEMICAL_REACTION_DEEP_REVIEW.md)
