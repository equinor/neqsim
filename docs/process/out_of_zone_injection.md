---
title: "Out-of-Zone Injection Modeling"
description: "Comprehensive guide to modeling out-of-zone injection risks in water, gas, and CO2 injection wells using NeqSim. Covers multi-zone injectivity, annular leakage, cement degradation, multi-compartment reservoirs, fracture containment, and injection conformance monitoring."
---

# Out-of-Zone Injection Modeling

Out-of-zone injection occurs when injected fluid (water, gas, or CO2) migrates outside the intended target reservoir interval. This can happen through:

- **Fracture propagation** beyond the target zone's stress barriers
- **Cement and casing failures** creating annular flow paths
- **Differential injectivity** in commingled injection into multiple zones
- **Pressure communication** between connected reservoir compartments

NeqSim provides a suite of interconnected classes for modeling these mechanisms, assessing risk, and monitoring injection conformance.

## Architecture Overview

| Class | Package | Purpose |
|-------|---------|---------|
| `WellFlow` (injection mode) | `process.equipment.reservoir` | Multi-zone injectivity with fracture containment |
| `InjectionWellModel` | `process.fielddevelopment.reservoir` | Multi-zone injection allocation with thermal stress |
| `AnnularLeakagePath` | `process.equipment.reservoir` | Behind-casing leakage (cubic law + Darcy) and MAASP per API RP 90 |
| `MultiCompartmentReservoir` | `process.equipment.reservoir` | Inter-zone pressure communication |
| `CementDegradationModel` | `process.equipment.reservoir` | Time-dependent cement permeability under CO2 |
| `InjectionConformanceMonitor` | `process.equipment.reservoir` | Hall plot analysis and conformance diagnosis |

## Multi-Zone Injection (WellFlow)

The `WellFlow` class supports injection mode via the `FlowMode.INJECTION` enum. Each reservoir layer can have a fracture pressure and barrier stress contrast for containment assessment.

### Setting Up an Injection Well

```java
// Create reservoir fluid
SystemInterface fluid = new SystemSrkEos(273.15 + 80.0, 250.0);
fluid.addComponent("water", 1.0);
fluid.setMixingRule("classic");

Stream injStream = new Stream("inj", fluid);
injStream.setFlowRate(5000.0, "kg/hr");
injStream.run();

// Create well in injection mode
WellFlow well = new WellFlow("INJ-1");
well.setInletStream(injStream);
well.setFlowMode(WellFlow.FlowMode.INJECTION);

// Add injection zones with injectivity and fracture data
well.addInjectionZone("Target Sand", injStream, 250.0, 1e-3, 350.0);
well.addInjectionZone("Thief Zone", injStream, 230.0, 2e-3, 300.0);
well.setTargetZone("Target Sand");
well.run();

// Assess results
double efficiency = well.getInjectionEfficiency();
double oozRate = well.getOutOfZoneRate("kg/hr");
```

### Fracture Containment Check

Each `ReservoirLayer` tracks fracture pressure and barrier stress contrast:

```java
WellFlow.ReservoirLayer layer = new WellFlow.ReservoirLayer("Zone A", stream, 250.0, 1e-3);
layer.setFracturePressure(350.0, "bara");
layer.setBarrierStressContrast(20.0, "bara");

// Check if BHP is safely below fracture propagation limit
boolean contained = layer.isFractureContained(360.0); // true if net pressure < barrier

// Get safety margin (positive = safe, negative = risk)
double margin = layer.getFractureContainmentMargin(340.0); // bar
```

The containment check uses: net pressure = BHP - fracture pressure. If net pressure < barrier stress contrast, fracture is contained within the zone.

## Multi-Zone Injection Model (InjectionWellModel)

The `InjectionWellModel` provides rate allocation across multiple zones based on their individual injectivity indices, plus thermal stress effects on effective fracture pressure.

### Zone Allocation

```java
InjectionWellModel model = new InjectionWellModel();
model.setMaxBHP(350.0, "bara");
model.setDrainageRadius(500.0);
model.setWellboreRadius(0.1);

// Define injection zones
InjectionWellModel.InjectionZone target = new InjectionWellModel.InjectionZone(
    "Target Sand", 2500.0, 250.0, 100.0, 30.0, 350.0);
target.skinFactor = 2.0;
target.isTargetZone = true;

InjectionWellModel.InjectionZone thief = new InjectionWellModel.InjectionZone(
    "Thief Zone", 2600.0, 230.0, 500.0, 10.0, 300.0);

model.addZone(target);
model.addZone(thief);

// Calculate allocation for target rate
InjectionWellModel.MultiZoneInjectionResult result =
    model.calculateMultiZone(10000.0);

System.out.println("Total rate: " + result.totalRate + " Sm3/day");
System.out.println("Injection efficiency: " + result.injectionEfficiency);
System.out.println("Out-of-zone rate: " + result.outOfZoneRate + " Sm3/day");
```

### Thermal Stress Effects

Cold-water injection reduces the effective fracture pressure due to thermal contraction of the near-wellbore rock:

$$
\Delta\sigma_{thermal} = \frac{\alpha \cdot E \cdot \Delta T}{1 - \nu}
$$

```java
// Injection at 30°C into 80°C reservoir
model.setThermalStressReduction(303.15, 353.15, 1.2e-5, 20.0);
model.setPoissonsRatio(0.25);

double thermalReduction = model.getThermalStressReduction(); // bar
double effectiveFracP = model.getEffectiveFracturePressure(); // bar
```

## Annular Leakage (AnnularLeakagePath)

Models fluid leakage behind casing through two mechanisms:

- **Channel flow (cubic law):** Leakage through a micro-annulus gap: $q = \frac{w \cdot \delta^3}{12 \mu} \cdot \frac{\Delta P}{L}$
- **Porous cement (Darcy flow):** Flow through degraded cement: $q = \frac{k \cdot A}{\mu \cdot L} \cdot \Delta P$

```java
AnnularLeakagePath leakage = new AnnularLeakagePath("cement leak");
leakage.setLeakageMechanism(AnnularLeakagePath.LeakageMechanism.COMBINED);
leakage.setPathGeometry(1500.0, 1600.0, 0.10, 0.001); // 100m path, 1mm gap
leakage.setCementPermeability(0.001, "mD");
leakage.setCementCrossSectionArea(0.01); // m2
leakage.setFluidViscosity(0.001); // Pa.s

leakage.calculate(350.0, 250.0); // source 350 bara, sink 250 bara

double channelRate = leakage.getChannelLeakageRate("m3/day");
double cementRate = leakage.getCementLeakageRate("m3/day");
double totalRate = leakage.getTotalLeakageRate("m3/day");
```

### MAASP Calculation (API RP 90)

The `AnnularLeakagePath` also calculates Maximum Allowable Annular Surface
Pressure (MAASP) per API RP 90. MAASP is the minimum of three independent
limits:

$$
\text{MAASP} = \min\left(\frac{P_{burst}}{SF_{burst}},\; \frac{P_{collapse}}{SF_{collapse}},\; P_{frac} - \rho g h\right)
$$

where:

- $P_{burst}$ = casing burst rating at the annulus surface
- $P_{collapse}$ = tubing collapse rating
- $P_{frac}$ = fracture pressure at the casing shoe
- $\rho g h$ = hydrostatic head of the annular fluid

```java
AnnularLeakagePath leakage = new AnnularLeakagePath("A-annulus");

// Set MAASP parameters
leakage.setMAASPParameters(
    620.0,    // casing burst rating (bara)
    480.0,    // tubing collapse rating (bara)
    2500.0,   // shoe depth (m)
    500.0,    // fracture pressure at shoe (bara)
    0.098     // annular fluid gradient (bar/m) — completion brine
);

// Optional: override API RP 90 default safety factors
leakage.setMAASPSafetyFactors(1.10, 1.00);

// Calculate
double maasp = leakage.calculateMAASP();
String limiting = leakage.getMAASPLimitingCriterion();
// Returns: "Fracture at shoe", "Casing burst", or "Tubing collapse"

// Check if monitored annulus pressure is safe
boolean exceeded = leakage.isAnnularPressureExceeded(300.0);
```

The limiting criterion tells the operator which constraint controls, guiding
the annular pressure management strategy.

## Multi-Compartment Reservoir

Tracks pressure evolution across connected reservoir compartments using material balance with inter-zone transmissibility:

$$
V_i \cdot c_{t,i} \cdot \frac{dP_i}{dt} = q_{inj,i} - q_{prod,i} + \sum_j T_{ij}(P_j - P_i)
$$

```java
MultiCompartmentReservoir reservoir = new MultiCompartmentReservoir("Field");

// Add compartments (name, fluid, poreVolume_m3, pressure_bara)
reservoir.addCompartment("Target Sand", targetFluid, 1.0e7, 250.0);
reservoir.addCompartment("Thief Zone", thiefFluid, 5.0e6, 230.0);
reservoir.addCompartment("Aquifer", aquiferFluid, 1.0e9, 260.0);

// Set compressibility and inter-zone transmissibility
reservoir.setCompressibility("Target Sand", 1e-4);
reservoir.setCompressibility("Thief Zone", 2e-4);
reservoir.setTransmissibility("Target Sand", "Thief Zone", 0.5);
reservoir.setTransmissibility("Target Sand", "Aquifer", 0.1);

// Add wells
reservoir.addInjectionRate("INJ-1", "Target Sand", 5000.0);
reservoir.addProductionRate("PROD-1", "Target Sand", 3000.0);

// Time-step for 365 days
for (int day = 0; day < 365; day++) {
    reservoir.runTimeStep(86400.0); // 1 day in seconds
    double pTarget = reservoir.getCompartmentPressure("Target Sand", "bara");
    double crossflow = reservoir.getInterZoneFlowRate(
        "Target Sand", "Thief Zone", "Sm3/day");
}
```

## Cement Degradation (CO2 Wells)

Critical for CCS projects: models time-dependent cement degradation under CO2 exposure.

The carbonation front advances as:

$$
d(t) = A \sqrt{D_{eff} \cdot t}
$$

where $D_{eff}$ depends on temperature (Arrhenius) and CO2 partial pressure.

```java
CementDegradationModel cement = new CementDegradationModel("Prod Casing Cement");
cement.setCementType(CementDegradationModel.CementType.PORTLAND);
cement.setInitialPermeability(0.001, "mD");
cement.setCementThickness(0.05, "m"); // 50 mm

// 10 bar CO2, 60°C
cement.setCO2Conditions(10.0, 273.15 + 60.0);

// Assess at 30 years
double depth30 = cement.getDegradationDepth(30.0, "mm");
double perm30 = cement.getPermeabilityAtTime(30.0, "mD");
boolean compromised = cement.isCementCompromised(30.0);
double timeToFull = cement.getTimeToFullCarbonation("years");
```

### Cement Types

| Type | Description | Carbonation Rate Factor |
|------|-------------|------------------------|
| `PORTLAND` | Standard Class G/H | 1.0 (baseline) |
| `SILICA_PORTLAND` | Portland + silica flour | 0.6 (more resistant) |
| `CO2_RESISTANT` | Calcium aluminate / geopolymer | 0.2 (most resistant) |

## Injection Conformance Monitoring

The `InjectionConformanceMonitor` uses Hall plot analysis and injection profile data to diagnose conformance issues.

### Hall Plot Analysis

The Hall plot cumulates (WHP $\times$ $\Delta t$) vs. cumulative injection. Slope changes indicate:

- **Decreasing slope:** Fracture growth or improved injectivity
- **Increasing slope:** Plugging, scaling, or skin increase
- **Sudden slope change:** Possible out-of-zone breakthrough

```java
InjectionConformanceMonitor monitor = new InjectionConformanceMonitor("INJ-1 Monitor");

// Record daily injection data (time_days, whp_bar, rate_m3/d)
for (int day = 0; day < 100; day++) {
    double whp = 250.0 + day * 0.1;  // slowly rising
    double rate = 5000.0;
    monitor.recordInjectionData(day, whp, rate);
}

// Analyze
monitor.calculateHallPlot();
double currentSlope = monitor.getCurrentHallSlope();
double initialSlope = monitor.getInitialHallSlope();

// Detect changes
boolean changed = monitor.detectSlopeChange(0.2); // 20% threshold
String diagnosis = monitor.getDiagnosis();
// Returns: NORMAL, FRACTURE_GROWTH, PLUGGING, or OUT_OF_ZONE_SUSPECTED
```

### Injection Profile Analysis

```java
// Add zone profile data from production logging
monitor.addZoneProfile("Target Sand", 2500.0, 0.6, true);   // 60% of injection
monitor.addZoneProfile("Thief Zone", 2600.0, 0.3, false);   // 30% thief zone
monitor.addZoneProfile("Unconfirmed", 2700.0, 0.1, false);  // 10% unknown

double efficiency = monitor.getInjectionEfficiency();    // 0.6
double oozFraction = monitor.getOutOfZoneFraction();     // 0.4
```

## Integrated Workflow

A typical out-of-zone injection risk assessment combines multiple classes:

```
                           ┌──────────────────────┐
                           │ InjectionWellModel    │
                           │ (zone allocation +    │
                           │  thermal stress)      │
                           └──────────┬───────────┘
                                      │
              ┌───────────────────────┼───────────────────────┐
              ▼                       ▼                       ▼
   ┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐
   │ WellFlow          │   │ AnnularLeakage   │   │ CementDegradation│
   │ (IPR + fracture   │   │ (cubic law +     │   │ (CO2 carbonation │
   │  containment)     │   │  Darcy flow)     │   │  over time)      │
   └────────┬─────────┘   └────────┬─────────┘   └────────┬─────────┘
            │                      │                       │
            ▼                      ▼                       ▼
   ┌──────────────────────────────────────────────────────────────┐
   │ MultiCompartmentReservoir                                    │
   │ (pressure response across connected zones)                   │
   └──────────────────────────────────────────────────────────────┘
            │
            ▼
   ┌──────────────────┐
   │ Conformance       │
   │ Monitor           │
   │ (Hall plot +      │
   │  diagnostics)     │
   └──────────────────┘
```

### Step-by-Step Assessment

1. **Define zones:** Create injection zones with injectivity, fracture pressure, and barrier stress
2. **Thermal stress:** Calculate effective fracture pressure under cold injection
3. **Zone allocation:** Run `InjectionWellModel.calculateMultiZone()` to get rates per zone
4. **Leakage paths:** Model annular leakage with `AnnularLeakagePath`
5. **Cement integrity:** Assess long-term cement condition with `CementDegradationModel`
6. **Pressure response:** Time-step `MultiCompartmentReservoir` to track inter-zone flow
7. **Surveillance:** Feed injection data into `InjectionConformanceMonitor` for diagnostics

## Related Documentation

- [CO2 Injection Well Analysis](co2_injection_well_analysis.md)
- [Well Mechanical Design](well_mechanical_design.md)
- [Reservoirs](equipment/reservoirs.md)
