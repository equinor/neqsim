---
title: Engineering Utilities Reference
description: "Reference guide for NeqSim engineering utilities: FluidBuilder, HeatMaterialBalance, SensitivityAnalysis, MonteCarloSimulator, ConvergenceDiagnostics, HydrateRiskMapper, and EOSComparison."
---

# Engineering Utilities Reference

NeqSim includes a suite of engineering utilities designed to accelerate
thermodynamic and process simulation workflows. These utilities reduce
boilerplate, improve reproducibility, and enable AI-driven task solving.

## FluidBuilder — Fluent Fluid Creation

**Package:** `neqsim.thermo.system`

Create thermodynamic fluids with a fluent API or use preset industry compositions.

### Fluent API

```java
SystemInterface fluid = FluidBuilder.create(273.15 + 25.0, 60.0)
    .addComponent("methane", 0.85)
    .addComponent("ethane", 0.10)
    .addComponent("propane", 0.05)
    .withMixingRule("classic")
    .build();
```

### With EOS Selection

```java
SystemInterface fluid = FluidBuilder.create(273.15 + 80.0, 200.0)
    .withEOS(FluidBuilder.EOSType.PR)
    .addComponent("methane", 0.50)
    .addComponent("n-hexane", 0.50)
    .withMixingRule("classic")
    .withMultiPhaseCheck()
    .build();
```

### Oil Characterization

```java
SystemInterface oil = FluidBuilder.create(273.15 + 80.0, 250.0)
    .withEOS(FluidBuilder.EOSType.PR)
    .addComponent("methane", 0.30)
    .addComponent("ethane", 0.08)
    .addTBPFraction("C7", 0.06, 0.092, 0.727)
    .addTBPFraction("C8", 0.05, 0.104, 0.749)
    .addPlusFraction("C20", 0.23, 0.350, 0.88)
    .withLumpedComponents(6)
    .withMixingRule("classic")
    .build();
```

### Preset Fluids

| Method | Description | EOS |
|--------|-------------|-----|
| `leanNaturalGas(T, P)` | Dry gas, 85% CH4 | SRK |
| `richNaturalGas(T, P)` | Wet gas, 72% CH4 + C3+ | SRK |
| `typicalBlackOil(T, P)` | Black oil with C7-C20+ | PR |
| `gasCondensate(T, P)` | Condensate with C7-C15+ | SRK |
| `dryExportGas(T, P)` | Pipeline gas, 92% CH4 | SRK |
| `co2Rich(T, P)` | CCS stream, 95% CO2 | SRK-CPA |
| `acidGas(T, P)` | Sour gas with H2S + CO2 | SRK-CPA |

---

## HeatMaterialBalance — HMB Reports

**Package:** `neqsim.process.util.report`

Generate industry-standard Heat and Material Balance reports from any `ProcessSystem`.

```java
ProcessSystem process = ...;
process.run();

HeatMaterialBalance hmb = new HeatMaterialBalance(process);
String json = hmb.toJson();        // Full JSON report
String csv = hmb.streamTableToCSV(); // Stream table as CSV
```

### Customisation

```java
hmb.setTemperatureUnit("K")
   .setPressureUnit("barg")
   .setFlowUnit("kg/sec");
```

### Output Structure

The JSON contains:
- **streamTable** — all streams with T, P, flow, composition, density, enthalpy, entropy, vapour fraction
- **equipmentSummary** — compressor power, heater/cooler duty, heat exchanger UA

---

## SensitivityAnalysis — Parameter Sweeps

**Package:** `neqsim.process.util.optimizer`

Vary one parameter across a range while tracking multiple outputs.

```java
SensitivityAnalysis sa = new SensitivityAnalysis(process);

sa.setParameter("Outlet Pressure", 70.0, 150.0, 8,
    (proc, val) -> {
        Compressor c = (Compressor) proc.getUnit("Comp");
        c.setOutletPressure(val);
    });

sa.addOutput("Power (kW)", proc -> {
    Compressor c = (Compressor) proc.getUnit("Comp");
    return c.getPower("kW");
});

SensitivityAnalysis.SensitivityResult result = sa.run();
System.out.println(result.toJson());
```

Each evaluation runs on a fresh copy of the process to avoid state contamination.

---

## MonteCarloSimulator — Uncertainty Analysis

**Package:** `neqsim.process.util.optimizer`

Run N Monte Carlo iterations with triangular or uniform distributions.

```java
MonteCarloSimulator mc = new MonteCarloSimulator(process, 200);
mc.setSeed(42);

mc.addTriangularParameter("Gas Price", 0.8, 1.5, 2.5,
    (proc, val) -> { /* apply to process */ });

mc.addUniformParameter("CAPEX multiplier", 0.85, 1.4,
    (proc, val) -> { /* apply to process */ });

mc.setOutputExtractor("NPV (MNOK)", proc -> calculateNPV(proc));

MonteCarloSimulator.MonteCarloResult result = mc.run();
System.out.println("P10=" + result.getP10());
System.out.println("P50=" + result.getP50());
System.out.println("P90=" + result.getP90());
System.out.println("Mean=" + result.getMean());
System.out.println("P(NPV<0)=" + result.getProbabilityBelow(0));
System.out.println(result.toJson());
```

### Tornado Sensitivity

Tornado data is automatically computed by varying each parameter individually
between its low and high bounds. Access via `result.getTornado()`.

---

## ConvergenceDiagnostics — Troubleshooting

**Package:** `neqsim.process.equipment.util`

Diagnose convergence issues in processes with recycle loops and adjusters.

```java
ConvergenceDiagnostics diag = new ConvergenceDiagnostics(process);
ConvergenceDiagnostics.DiagnosticReport report = diag.analyze();

if (!report.isConverged()) {
    for (String suggestion : report.getSuggestions()) {
        System.out.println("  -> " + suggestion);
    }
}
System.out.println(report.toJson());
```

### What It Checks

- **Recycle units**: flow, temperature, pressure, composition errors vs tolerances
- **Adjuster units**: error vs tolerance
- Identifies the dominant error type (flow, temperature, composition, etc.)
- Generates actionable remediation suggestions

---

## HydrateRiskMapper — Pipeline Hydrate Assessment

**Package:** `neqsim.pvtsimulation.flowassurance`

Map hydrate formation risk along a pipeline P-T profile.

```java
HydrateRiskMapper mapper = new HydrateRiskMapper(fluid);

// Add pipeline profile (km, bara, °C)
mapper.addProfilePoint(0.0, 100.0, 60.0);
mapper.addProfilePoint(10.0, 95.0, 40.0);
mapper.addProfilePoint(50.0, 75.0, 4.0);

HydrateRiskMapper.RiskProfile profile = mapper.calculate();

for (HydrateRiskMapper.RiskPoint rp : profile.getPoints()) {
    System.out.printf("%.1f km: %s (subcooling=%.1f°C)%n",
        rp.distanceKm, rp.riskLevel, rp.subcoolingC);
}
System.out.println(profile.toJson());
```

### Risk Levels

Subcooling is defined as `T_actual - T_hydrate`. Positive values mean the
operating temperature is safely above the hydrate formation temperature.

| Level | Condition (default thresholds) | Meaning |
|-------|-------------------------------|---------|
| CRITICAL | T_actual < T_hydrate (margin < 0°C) | Inside hydrate zone — immediate action |
| HIGH | Margin 0–3°C above hydrate T | Close to hydrate curve — mitigation needed |
| MEDIUM | Margin 3–6°C above hydrate T | Moderate margin — monitor |
| LOW | Margin > 6°C above hydrate T | Safe |

Custom thresholds: `mapper.setRiskThresholds(3.0, 6.0)` (highRiskC, mediumRiskC)

---

## EOSComparison — Model Comparison

**Package:** `neqsim.integration`

Compare thermodynamic properties across multiple equations of state.

```java
EOSComparison comp = new EOSComparison();
comp.addComponent("methane", 0.85);
comp.addComponent("ethane", 0.10);
comp.addComponent("propane", 0.05);
comp.setConditions(273.15 + 25.0, 60.0);
comp.setEOSTypes(EOSComparison.EOSType.SRK,
    EOSComparison.EOSType.PR,
    EOSComparison.EOSType.GERG2008);

EOSComparison.ComparisonResult result = comp.compare();
System.out.println(result.toJson());

// Check property deviations
double densityDev = result.getMaxDeviation("density");
System.out.println("Max density deviation: " + densityDev + "%");
```

### Available Properties

density, Z, gasDensity, gasViscosity, gasZ, gasCp, oilDensity, oilViscosity, enthalpy, entropy

### Supported EOS Types

SRK, PR, SRK-CPA, GERG-2008
