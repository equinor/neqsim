---
name: neqsim-ccs-hydrogen
description: "CO2 capture, transport, storage (CCS) and hydrogen systems patterns for NeqSim. USE WHEN: modeling CO2 pipelines, injection wells, impurity effects on phase behavior, CO2 dense phase transport, hydrogen blending, electrolysis, or any CCS/H2 value chain analysis. Covers CO2 phase behavior, impurity management, well integrity, and hydrogen systems."
last_verified: "2026-07-04"
---

# CCS and Hydrogen Systems with NeqSim

Guide for modeling carbon capture and storage (CCS) value chains and hydrogen
systems, including CO2 transport, injection wells, impurity effects, and H2 blending.

## When to Use This Skill

- CO2 pipeline design and phase behavior
- CO2 injection well analysis and safety
- Impurity effects on CO2 phase envelope (H2, N2, O2, H2S, CH4)
- Dense phase CO2 transport conditions
- CO2 dehydration requirements
- Hydrogen blending with natural gas
- Water electrolysis and green hydrogen
- Blue hydrogen (SMR/ATR + CCS)
- Hydrogen pipeline transport

## Applicable Standards

| Domain | Standards | Key Requirements |
|--------|-----------|-----------------|
| CO2 pipeline | DNV-RP-F104, ISO 27913 | CO2 pipeline design, impurity limits |
| CO2 storage | ISO 27914, EU CCS Directive | Storage site characterization |
| CO2 transport | ISO 27913 | Composition specs, phase management |
| CO2 quality | ISO 27916 | CO2 stream specification |
| Hydrogen pipeline | ASME B31.12 | H2 piping and pipelines |
| Hydrogen quality | ISO 14687 (fuel cell), EN 16726 (grid) | Purity requirements |

## 1. CO2 Phase Behavior

### CO2 Critical Point and Phase Envelope

CO2 critical point: 31.1°C, 73.8 bara. Most CO2 pipelines operate in dense phase
(above critical pressure) to avoid two-phase flow.

```java
// Pure CO2 phase behavior
SystemInterface co2 = new SystemSrkEos(273.15 + 25, 80.0);
co2.addComponent("CO2", 1.0);
co2.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(co2);
ops.calcPTphaseEnvelope();
// CO2 is in dense/supercritical phase at typical pipeline conditions (>80 bara, 4-40°C)
```

### Impurity Effects on CO2 Phase Envelope

Impurities widen the phase envelope and raise the cricondenbar, creating risk
of two-phase flow in pipelines designed for dense phase operation.

```java
// CO2 with typical impurities from post-combustion capture
SystemInterface co2Mix = new SystemSrkEos(273.15 + 10, 110.0);
co2Mix.addComponent("CO2", 0.95);
co2Mix.addComponent("nitrogen", 0.02);
co2Mix.addComponent("oxygen", 0.005);
co2Mix.addComponent("water", 0.005);
co2Mix.addComponent("H2S", 0.001);
co2Mix.addComponent("hydrogen", 0.005);
co2Mix.addComponent("methane", 0.014);
co2Mix.setMixingRule("classic");
co2Mix.setMultiPhaseCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(co2Mix);
ops.calcPTphaseEnvelope();
// Compare cricondenbar with pure CO2 — impurities raise it significantly
// N2, H2, O2 have the largest effect on raising cricondenbar
```

### Impurity Impact Ranking (on phase envelope)

| Impurity | Effect on Cricondenbar | Effect on Density | Corrosion Risk |
|----------|----------------------|-------------------|----------------|
| N2 | Large increase | Decrease | None |
| H2 | Large increase | Large decrease | Embrittlement |
| O2 | Moderate increase | Slight decrease | Oxidation |
| Ar | Moderate increase | Slight decrease | None |
| CH4 | Moderate increase | Decrease | None |
| H2S | Small increase | Slight increase | High (sour) |
| SO2 | Small effect | Slight increase | High (acid) |
| H2O | Minimal on vapor | — | Corrosion with CO2 |

## 2. CO2 Pipeline Design

### Dense Phase Transport

```java
// Typical CO2 pipeline: 110 bara inlet, 80 bara min, 4-40°C
Stream co2Feed = new Stream("CO2 Feed", co2Mix);
co2Feed.setFlowRate(1000000.0, "kg/hr");  // ~1 Mt/yr
co2Feed.setTemperature(25.0, "C");
co2Feed.setPressure(110.0, "bara");

PipeBeggsAndBrills pipeline = new PipeBeggsAndBrills("CO2 Pipeline", co2Feed);
pipeline.setLength(150000.0);     // 150 km
pipeline.setDiameter(0.508);       // 20 inch
pipeline.setPipeWallRoughness(5e-5);
pipeline.setOuterTemperature(277.15);  // 4°C seabed
pipeline.run();

double outP = pipeline.getOutletStream().getPressure();
double outT = pipeline.getOutletStream().getTemperature() - 273.15;
// Verify: outlet P > cricondenbar to stay in dense phase
```

### CO2 Dehydration Requirement

```java
// Water content must be below ~50 ppmv to prevent hydrate and corrosion
// Use CPA for accurate water in CO2 modeling
SystemInterface wetCO2 = new SystemSrkCPAstatoil(273.15 + 25, 110.0);
wetCO2.addComponent("CO2", 0.99);
wetCO2.addComponent("water", 0.01);
wetCO2.setMixingRule(10);
wetCO2.setMultiPhaseCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(wetCO2);
ops.TPflash();
wetCO2.initProperties();

// Check water content in CO2-rich phase
double waterInCO2 = wetCO2.getPhase("gas").getComponent("water").getx();
// Convert to ppmv and compare with spec (typically <50 ppmv)
```

## 3. CO2 Injection Well Analysis

### Full-Stack Well Analysis

```java
CO2InjectionWellAnalyzer analyzer = new CO2InjectionWellAnalyzer("InjWell-1");
analyzer.setFluid(co2Fluid);
analyzer.setWellGeometry(1300.0, 0.1571, 5e-5);  // depth, ID, roughness
analyzer.setOperatingConditions(90.0, 25.0, 150000.0);  // P, T, flow
analyzer.setFormationTemperature(4.0, 43.0);  // surface T, bottomhole T
analyzer.addTrackedComponent("hydrogen", 0.10);  // impurity limit
analyzer.runFullAnalysis();

boolean safe = analyzer.isSafeToOperate();
// Checks: phase transitions in wellbore, impurity enrichment, thermal stress
```

### Wellbore Temperature Profile

```java
PipeBeggsAndBrills wellbore = new PipeBeggsAndBrills("CO2 Injector", co2Feed);
wellbore.setLength(1300.0);
wellbore.setElevation(-1300.0);  // vertical injection well
wellbore.setDiameter(0.1571);     // 6-5/8 inch tubing
wellbore.setPipeWallRoughness(5e-5);
wellbore.setFormationTemperatureGradient(4.0, -0.03, "C");
wellbore.run();

double bhp = wellbore.getOutletStream().getPressure();
double bht = wellbore.getOutletStream().getTemperature() - 273.15;
```

### Impurity Enrichment Monitoring

During phase transitions in the wellbore, light impurities (H2, N2) concentrate
in the gas phase, potentially exceeding well material limits.

```java
ImpurityMonitor monitor = new ImpurityMonitor("H2-Monitor", stream);
monitor.addTrackedComponent("hydrogen", 0.10);   // 10 mol% limit
monitor.addTrackedComponent("H2S", 0.001);        // 0.1% limit
monitor.addTrackedComponent("oxygen", 0.005);     // 0.5% limit

double h2Enrichment = monitor.getEnrichmentFactor("hydrogen");
boolean h2Safe = !monitor.exceedsLimit("hydrogen");
```

### Shutdown Transient Analysis

```java
TransientWellbore wellbore = new TransientWellbore("Shutdown", co2Feed);
wellbore.setWellDepth(1300.0);
wellbore.setFormationTemperature(277.15, 316.15);  // surface, bottom (K)
wellbore.setShutdownCoolingRate(6.0);  // °C/hr cooling rate
wellbore.runShutdownSimulation(48.0, 1.0);  // 48 hours, 1 hr timestep

// Check for phase transition during cooldown
// Risk: CO2 may transition to two-phase, causing pressure surges
```

### CO2 Flow Corrections

```java
// Static utility for CO2-specific flow adjustments
boolean dense = CO2FlowCorrections.isDensePhase(system);
double holdupCorr = CO2FlowCorrections.getLiquidHoldupCorrectionFactor(system);
// Dense phase CO2 requires modified correlations for pressure drop
```

## 4. Hydrogen Systems

### Hydrogen Blending with Natural Gas

```java
// Evaluate H2 blending impact on existing gas network
SystemInterface blendedGas = new SystemSrkEos(273.15 + 15, 70.0);
blendedGas.addComponent("hydrogen", 0.10);   // 10% H2 blend
blendedGas.addComponent("methane", 0.81);
blendedGas.addComponent("ethane", 0.05);
blendedGas.addComponent("propane", 0.02);
blendedGas.addComponent("nitrogen", 0.02);
blendedGas.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(blendedGas);
ops.TPflash();
blendedGas.initProperties();

// Key impacts of H2 blending:
double density = blendedGas.getDensity("kg/m3");       // Decreases with H2
double gcv = blendedGas.getPhase("gas").getCp("J/kgK"); // Changes energy content
double z = blendedGas.getZ();                            // Compressibility changes

// For Wobbe index and calorific value:
Standard_ISO6976 iso = new Standard_ISO6976(blendedGas);
iso.calculate();
double wobbe = iso.getValue("SuperiorWobbeIndex");
// H2 reduces Wobbe index — check against pipeline spec limits
```

### Hydrogen Pipeline Transport

```java
// H2 has very low density — requires higher velocities or larger diameters
Stream h2Feed = new Stream("H2 Feed", h2Fluid);
h2Feed.setFlowRate(10000.0, "kg/hr");
h2Feed.setPressure(70.0, "bara");

PipeBeggsAndBrills h2Pipe = new PipeBeggsAndBrills("H2 Pipeline", h2Feed);
h2Pipe.setLength(100000.0);   // 100 km
h2Pipe.setDiameter(0.508);     // 20 inch
h2Pipe.setPipeWallRoughness(5e-5);
h2Pipe.run();

// H2 pressure drop is lower per unit mass but energy density is much lower
// Consider: material compatibility (H2 embrittlement), safety zones
```

### Blue Hydrogen (SMR + CCS)

```java
// Steam Methane Reforming produces H2 + CO2
// CH4 + H2O -> CO + 3H2 (reforming)
// CO + H2O -> CO2 + H2 (water-gas shift)

// Model with GibbsReactor for equilibrium
SystemInterface syngasFluid = new SystemSrkEos(273.15 + 850, 30.0);
syngasFluid.addComponent("methane", 0.25);
syngasFluid.addComponent("water", 0.75);
syngasFluid.setMixingRule("classic");

GibbsReactor reformer = new GibbsReactor("SMR", syngasFeed);
reformer.run();
// Outlet: H2, CO, CO2, H2O, unconverted CH4
```

## 5. CCS Value Chain Integration

### Capture → Transport → Storage Workflow

```java
// 1. Post-combustion capture outlet (after amine scrubbing)
SystemInterface capturedCO2 = new SystemSrkEos(273.15 + 40, 2.0);
capturedCO2.addComponent("CO2", 0.995);
capturedCO2.addComponent("nitrogen", 0.003);
capturedCO2.addComponent("water", 0.002);
capturedCO2.setMixingRule("classic");

// 2. Compression to pipeline pressure
Stream co2Stream = new Stream("Captured CO2", capturedCO2);
co2Stream.setFlowRate(500000.0, "kg/hr");

// Multi-stage compression with intercooling
Compressor comp1 = new Compressor("Stage 1", co2Stream);
comp1.setOutletPressure(5.0);
Cooler cooler1 = new Cooler("IC 1", comp1.getOutletStream());
cooler1.setOutTemperature(273.15 + 30);

Compressor comp2 = new Compressor("Stage 2", cooler1.getOutletStream());
comp2.setOutletPressure(20.0);
Cooler cooler2 = new Cooler("IC 2", comp2.getOutletStream());
cooler2.setOutTemperature(273.15 + 30);

Compressor comp3 = new Compressor("Stage 3", cooler2.getOutletStream());
comp3.setOutletPressure(80.0);
Cooler cooler3 = new Cooler("IC 3", comp3.getOutletStream());
cooler3.setOutTemperature(273.15 + 30);

// 3. Pump to pipeline pressure (above critical — dense phase)
// CO2 is liquid above ~65 bara at 30°C, pump is more efficient than compressor
Compressor pump = new Compressor("Pump", cooler3.getOutletStream());
pump.setOutletPressure(150.0);

// 4. Pipeline transport
PipeBeggsAndBrills pipeline = new PipeBeggsAndBrills("CO2 Export", pump.getOutletStream());
pipeline.setLength(200000.0);  // 200 km
pipeline.setDiameter(0.508);
pipeline.setOuterTemperature(277.15);

// 5. Injection well
// See CO2InjectionWellAnalyzer above

ProcessSystem ccsProcess = new ProcessSystem();
ccsProcess.add(co2Stream);
ccsProcess.add(comp1); ccsProcess.add(cooler1);
ccsProcess.add(comp2); ccsProcess.add(cooler2);
ccsProcess.add(comp3); ccsProcess.add(cooler3);
ccsProcess.add(pump);
ccsProcess.add(pipeline);
ccsProcess.run();

double totalPower = comp1.getPower("kW") + comp2.getPower("kW")
                  + comp3.getPower("kW") + pump.getPower("kW");
```

## 6. Common Pitfalls

| Pitfall | Solution |
|---------|----------|
| CO2 two-phase in pipeline | Ensure P > cricondenbar (account for impurities) |
| Using SRK for CO2+water | Use CPA (`SystemSrkCPAstatoil`) for accurate water solubility |
| Ignoring impurity effect on phase envelope | Always calculate phase envelope with impurities included |
| H2 density too high | Verify EOS handles low-density H2 correctly at high P |
| CO2 injection below fracture P | Check bottomhole P vs formation fracture gradient |
| Ignoring JT cooling in CO2 expansion | CO2 expands significantly — can cause solid CO2 below -56.6°C |
| Hydrogen embrittlement not flagged | Use ASME B31.12 for H2 service; flag H2 partial pressure > limits |
