---
name: neqsim-flow-assurance
description: "Flow assurance analysis patterns for NeqSim. USE WHEN: predicting hydrate formation, wax appearance, asphaltene stability, CO2/H2S corrosion, pipeline hydraulics, water/liquid hammer screening, slug flow, thermal analysis, or chemical inhibitor dosing. Covers all flow assurance threats with NeqSim code patterns and industry standards."
last_verified: "2026-07-04"
---

# Flow Assurance Analysis with NeqSim

Consolidated guide for all flow assurance threats — hydrate, wax, asphaltene, corrosion,
hydraulics, water/liquid hammer screening, slugging, and thermal management — with
NeqSim code patterns.

## When to Use This Skill

- Hydrate formation temperature/pressure prediction
- Hydrate inhibitor dosing (MEG, methanol, ethanol)
- Wax appearance temperature (WAT) and wax deposition risk
- Asphaltene stability screening (de Boer, CII)
- CO2 and H2S corrosion rate estimation
- Pipeline pressure drop and temperature profile
- Water hammer/liquid hammer screening for fast valve closure, pump trip, or check-valve slam
- Multiphase flow pattern prediction (slug, annular, stratified)
- Thermal insulation sizing for subsea pipelines
- Arrival temperature and cooldown calculations

## Applicable Standards

| Domain | Standards | Key Requirements |
|--------|-----------|-----------------|
| Pipeline design | DNV-ST-F101, NORSOK L-001, ASME B31.4/B31.8 | Wall thickness, design factors, corrosion allowance |
| Corrosion | NORSOK M-001, DNV-RP-F112, ISO 21457 | Material selection, CO2/H2S corrosion rates |
| Subsea pipelines | DNV-RP-F109, NORSOK U-001 | On-bottom stability, span assessment |
| Hydrate management | DNV-RP-F116 | Hydrate prevention and remediation |
| GRP piping | ISO 14692 | Non-metallic pipe design |
| Pipeline integrity | DNV-RP-F116, API 1160 | Integrity management |

For fast acoustic transients, also load `neqsim-water-hammer`. Use
`WaterHammerStudy` or MCP `runWaterHammer` with STID route geometry, tagreader
event windows, and valve/pump event schedules; use this flow-assurance skill for
the broader operating-envelope and mitigation context.

## 1. Hydrate Analysis

### Hydrate Formation Temperature

```java
// CPA EOS required for accurate water-hydrocarbon modeling
SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 10, 100.0);
fluid.addComponent("methane", 0.80);
fluid.addComponent("ethane", 0.05);
fluid.addComponent("propane", 0.03);
fluid.addComponent("CO2", 0.02);
fluid.addComponent("water", 0.10);
fluid.setMixingRule(10);  // CPA mixing rule
fluid.setMultiPhaseCheck(true);
fluid.setHydrateCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.hydrateFormationTemperature();
double hydrateT_C = fluid.getTemperature() - 273.15;
```

### Hydrate Equilibrium Curve (Multiple Pressures)

```java
// Calculate hydrate T at several pressures for the full curve
double[] pressures = {20, 40, 60, 80, 100, 120, 150, 200};
double[] hydrateTemps = new double[pressures.length];

for (int i = 0; i < pressures.length; i++) {
    SystemInterface testFluid = fluid.clone();
    testFluid.setPressure(pressures[i]);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testFluid);
    testOps.hydrateFormationTemperature();
    hydrateTemps[i] = testFluid.getTemperature() - 273.15;
}
```

### Hydrate Inhibitor Dosing (MEG)

```java
// Add MEG to suppress hydrate formation temperature
SystemInterface inhibitedFluid = new SystemSrkCPAstatoil(273.15 + 4, 100.0);
inhibitedFluid.addComponent("methane", 0.80);
inhibitedFluid.addComponent("water", 0.15);
inhibitedFluid.addComponent("MEG", 0.05);  // 25 wt% MEG in water phase
inhibitedFluid.setMixingRule(10);
inhibitedFluid.setMultiPhaseCheck(true);
inhibitedFluid.setHydrateCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(inhibitedFluid);
ops.hydrateFormationTemperature();
double inhibitedHydrateT = inhibitedFluid.getTemperature() - 273.15;
// Compare with uninhibited to get subcooling margin
```

### MEG Concentration Sweep

```java
// Find required MEG concentration for target subcooling
double[] megWtPct = {0, 10, 20, 30, 40, 50};
for (double wt : megWtPct) {
    // Create fluid with appropriate MEG/water ratio
    double waterFrac = 0.20 * (1.0 - wt / 100.0);
    double megFrac = 0.20 * (wt / 100.0);
    SystemInterface testFluid = new SystemSrkCPAstatoil(273.15, 100.0);
    testFluid.addComponent("methane", 0.80);
    testFluid.addComponent("water", waterFrac);
    testFluid.addComponent("MEG", megFrac);
    testFluid.setMixingRule(10);
    testFluid.setHydrateCheck(true);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testFluid);
    testOps.hydrateFormationTemperature();
    // Record hydrate T vs MEG concentration
}
```

## 2. Wax Analysis

### Wax Appearance Temperature (WAT)

```java
// Oil system with C7+ fractions for wax prediction
SystemInterface oil = new SystemSrkEos(273.15 + 60, 50.0);
oil.addComponent("methane", 0.30);
oil.addComponent("ethane", 0.10);
oil.addTBPfraction("C7", 0.10, 92.0 / 1000, 0.727);
oil.addTBPfraction("C10", 0.15, 134.0 / 1000, 0.78);
oil.addTBPfraction("C15", 0.15, 206.0 / 1000, 0.83);
oil.addPlusFraction("C20", 0.20, 350.0 / 1000, 0.88);
oil.getCharacterization().setWaxModel(true);
oil.getCharacterization().characterisePlusFraction();
oil.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(oil);
ops.calcWAT();
double wat_C = oil.getTemperature() - 273.15;
```

### Wax Fraction vs Temperature (PVT Simulation)

```java
import neqsim.pvtsimulation.simulation.WaxFractionSim;

WaxFractionSim waxSim = new WaxFractionSim(oil);
waxSim.setTemperatures(new double[]{333.15, 313.15, 293.15, 273.15});
waxSim.run();
double[] waxFractions = waxSim.getWaxFraction();
```

## 3. Asphaltene Stability

### de Boer Screening

```java
// Assess asphaltene precipitation risk
// Key parameters: reservoir pressure, bubble point, density difference
// Risk increases when operating pressure approaches bubble point
// High-risk zone: ΔP > 200 bar above bubble point for light oils

// Use CPA for asphaltene modeling
SystemInterface aspFluid = new SystemSrkCPAstatoil(273.15 + 90, 300.0);
// Add components including heavy asphaltenic fractions
aspFluid.setMixingRule(10);

ThermodynamicOperations ops = new ThermodynamicOperations(aspFluid);
ops.TPflash();
aspFluid.initProperties();

// Check if asphaltene phase is stable
// Compare upper/lower asphaltene onset pressures vs operating P
```

## 4. Pipeline Hydraulics

### Simple Adiabatic Pipe

```java
AdiabaticPipe pipe = new AdiabaticPipe("Export Pipeline", feedStream);
pipe.setLength(50000.0);       // 50 km in meters
pipe.setDiameter(0.508);       // 20 inch in meters
pipe.setInletElevation(0.0);
pipe.setOutletElevation(-350.0);  // negative = downhill (subsea)
pipe.run();

double outletP = pipe.getOutletStream().getPressure();  // bara
double outletT = pipe.getOutletStream().getTemperature() - 273.15;  // C
double dP = feedStream.getPressure() - outletP;  // pressure drop
```

### Beggs and Brill Multiphase Correlation

```java
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("Subsea Flowline", feedStream);
pipe.setPipeWallRoughness(5e-5);  // meters
pipe.setLength(50000.0);          // meters
pipe.setAngle(0.0);               // horizontal
pipe.setDiameter(0.254);          // 10 inch

// For subsea with heat loss
pipe.setOuterTemperature(277.15);  // 4°C seawater
pipe.run();

// Get flow regime, liquid holdup, pressure profile
double outP = pipe.getOutletStream().getPressure();
double outT = pipe.getOutletStream().getTemperature() - 273.15;
```

### Pipeline with Formation Temperature Gradient (Wells / Risers)

```java
PipeBeggsAndBrills wellbore = new PipeBeggsAndBrills("Production Well", feedStream);
wellbore.setLength(3000.0);
wellbore.setElevation(-3000.0);  // vertical well
wellbore.setDiameter(0.1571);    // 6-5/8 inch
wellbore.setPipeWallRoughness(5e-5);

// Formation temperature: 90°C at bottom, gradient of -0.03°C/m going up
wellbore.setFormationTemperatureGradient(4.0, -0.03, "C");
wellbore.run();
```

### Pipeline Sizing (Iterative)

```java
// Iterate over diameters to find optimal size
double[] diameters = {0.1524, 0.2032, 0.254, 0.3048, 0.3556, 0.4064, 0.508};
// 6", 8", 10", 12", 14", 16", 20"

for (double d : diameters) {
    Stream testFeed = new Stream("feed", feedFluid.clone());
    testFeed.setFlowRate(flowRate, "kg/hr");
    testFeed.run();

    PipeBeggsAndBrills testPipe = new PipeBeggsAndBrills("test", testFeed);
    testPipe.setLength(pipeLength);
    testPipe.setDiameter(d);
    testPipe.setPipeWallRoughness(5e-5);
    testPipe.run();

    double dP = testFeed.getPressure() - testPipe.getOutletStream().getPressure();
    double velocity = testPipe.getSuperficialVelocity();
    // Check: erosional velocity < API RP 14E limit, dP within allowable
}
```

## 5. CO2 / H2S Corrosion Assessment

### CO2 Partial Pressure Based Screening

```java
// After flash calculation
fluid.initProperties();
double pCO2 = fluid.getPhase("gas").getComponent("CO2").getx()
              * fluid.getPressure();  // CO2 partial pressure in bara

// NORSOK M-001 / DNV-RP-F112 screening:
// pCO2 < 0.02 bar  → low risk (carbon steel OK)
// 0.02 < pCO2 < 0.2 → moderate (corrosion allowance or inhibitor)
// pCO2 > 0.2        → high risk (CRA or heavy inhibition)
```

### Temperature and pH Effects

```java
// Corrosion rate increases with temperature up to ~80°C
// then decreases due to protective FeCO3 film
// Lower pH (more acidic) → higher corrosion rate
// Water cut affects wetting: >30% water cut → higher risk
```

### Network-Level Corrosion (LoopedPipeNetwork)

For production gathering networks, `LoopedPipeNetwork` has inline corrosion
models that compute rates per element during network solution:

```java
// de Waard-Milliams (default) or NORSOK M-506
net.setCorrosiveGas("trunk", 0.035, 0.002);  // CO2 mol%, H2S mol%
net.setCorrosionModel("trunk", "NORSOK");     // "DEWAARD" or "NORSOK"
net.setMinAllowableWallLife(20.0);            // years

net.run();
Map<String, double[]> corr = net.calculateCorrosion();
// Per element: [0] = rate (mm/yr), [1] = pCO2 (bar), [2] = wall life (yr)
List<String> violations = net.getCorrosionViolations();
```

Models: de Waard-Milliams (log10(Vcorr) = 5.8 - 1710/T + 0.67*log10(pCO2))
and NORSOK M-506 (Vcorr = Kt * fCO2^0.62 * (S/19)^0.146).

### Network-Level Sand Erosion (DNV RP O501)

```java
net.setSandRate("W1", 3.0);             // kg/hr
net.setMaxAllowableSandRate(10.0);
net.setMaxAllowableErosionRate(5.0);    // mm/yr

net.run();
Map<String, double[]> sand = net.calculateSandTransport();
// Per element: [0] = rate, [1] = concentration, [2] = erosion, [3] = deposition
List<String> violations = net.getSandViolations();
```

Erosion per DNV RP O501: E = K * Csand * v^2.6 * dp^0.2.
Deposition flagged when v < 1 m/s.

## 6. Thermal Analysis

### Cooldown Calculation

```java
// Simple cooldown time estimate for insulated pipeline
// Use Newton's cooling law: T(t) = Tsea + (Tin - Tsea) * exp(-t/tau)
// where tau = m * Cp / (U * A)
// U = overall heat transfer coefficient (W/m2K)
// A = pipe surface area per unit length (m2/m)
// m = fluid mass per unit length (kg/m)
// Cp = fluid heat capacity (J/kgK)
```

### Arrival Temperature Check

```java
// Critical checks:
// 1. Arrival T > hydrate formation T + margin (typically 5°C subcooling)
// 2. Arrival T > WAT (if waxy crude) + margin
// 3. Arrival T > pour point (for restart)
// If not met: increase insulation, add DEH, reduce flow, or inject inhibitor
```

## 7. Flow Assurance Decision Matrix

| Threat | Detection | NeqSim Method | Mitigation |
|--------|-----------|---------------|------------|
| Hydrate | `hydrateFormationTemperature()` | CPA EOS + hydrate check | MEG, methanol, insulation, DEH |
| Wax | `calcWAT()`, `WaxFractionSim` | Wax characterization | Pigging, inhibitor, insulation |
| Asphaltene | de Boer screening | CPA flash at multiple P | Inhibitor, avoid P drop |
| Corrosion (CO2) | CO2 partial pressure | Standard flash | CRA, inhibitor, pH stabilization |
| Slugging | Beggs & Brill flow regime | `PipeBeggsAndBrills` | Slug catcher, topside choking |
| Scale | Ion activity product | Electrolyte-CPA | Scale inhibitor, pH control |

## 8. CO2 Injection Well Analysis

For CCS/injection well safety analysis, use the dedicated analyzer:

```java
CO2InjectionWellAnalyzer analyzer = new CO2InjectionWellAnalyzer("InjWell-1");
analyzer.setFluid(co2Fluid);
analyzer.setWellGeometry(measuredDepth, innerDiameter, roughness);
analyzer.setOperatingConditions(inletP_bara, inletT_C, massFlowRate_kg_h);
analyzer.setFormationTemperature(surfaceT_C, bottomholeT_C);
analyzer.addTrackedComponent("hydrogen", maxMolFracLimit);
analyzer.runFullAnalysis();
boolean safe = analyzer.isSafeToOperate();
```

### Impurity Monitoring

```java
ImpurityMonitor monitor = new ImpurityMonitor("H2-Mon", stream);
monitor.addTrackedComponent("hydrogen", 0.10);  // 10 mol% limit
monitor.addTrackedComponent("H2S", 0.001);      // 0.1% limit
double enrichment = monitor.getEnrichmentFactor("hydrogen");
boolean exceeds = monitor.exceedsLimit("hydrogen");
```

## 9. Common Pitfalls

| Pitfall | Solution |
|---------|----------|
| Hydrate T too low (no water in fluid) | Add water component to fluid |
| Using SRK instead of CPA for water systems | Use `SystemSrkCPAstatoil` with mixing rule `10` |
| Pipeline output T = input T (adiabatic) | Set `outerTemperature` for heat loss |
| Zero viscosity from pipeline calculation | Call `fluid.initProperties()` after flash |
| Wax prediction fails (no heavy fractions) | Add C7+ TBP fractions with wax model enabled |
| MEG not reducing hydrate T | Check MEG is partitioning to aqueous phase |
