---
name: neqsim-production-optimization
description: "Production optimization, bottleneck analysis, decline modeling, and IOR/EOR screening with NeqSim. USE WHEN: optimizing production rates, identifying facility bottlenecks, forecasting production profiles, analyzing gas lift allocation, evaluating IOR/EOR options, or running multi-scenario production comparisons."
last_verified: "2026-07-04"
---

# NeqSim Production Optimization Skill

Reference for production forecasting, optimization, bottleneck analysis, and
improved recovery using NeqSim's field development and process simulation tools.

---

## Production Profile Modeling

### Decline Curve Types

| Model | Formula | Use Case |
|-------|---------|----------|
| Exponential | $q(t) = q_i e^{-Dt}$ | Gas wells, constant decline |
| Hyperbolic | $q(t) = q_i (1 + bDt)^{-1/b}$ | Oil wells, b = 0.3-0.8 |
| Harmonic | $q(t) = q_i / (1 + Dt)$ | Hyperbolic with b = 1 |
| Plateau + exponential | Build-up → plateau → decline | Constrained by facility |

### Production Profile Generation

```java
ProductionProfile profile = new ProductionProfile();

// Method 1: Arps decline
profile.setDeclineModel(ProductionProfile.DeclineModel.EXPONENTIAL);
profile.setPeakRate(25000.0);         // boe/d
profile.setDeclineRate(0.15);         // 15% per year (Di)
profile.setPlateauDuration(3);        // years at peak
profile.setProjectLife(25);           // total years
double[] rates = profile.generateProfile();

// Method 2: Hyperbolic with b-factor
profile.setDeclineModel(ProductionProfile.DeclineModel.HYPERBOLIC);
profile.setHyperbolicB(0.5);

// Method 3: Resource-constrained
ProductionProfileGenerator gen = new ProductionProfileGenerator();
gen.setResourceVolume(100.0);         // MMboe recoverable
gen.setRecoveryFactor(0.55);
gen.setPeakRate(25000.0);
gen.setBuildUpYears(2);
gen.setPlateauYears(5);
gen.generate();
```

### Multi-Well Drill Schedule

```java
FieldProductionScheduler scheduler = new FieldProductionScheduler();
scheduler.setNumberOfWells(8);
scheduler.setDrillingInterval(6);      // months between wells
scheduler.setFirstOil(2027);
scheduler.setWellProductivity(4000.0); // initial boe/d/well
scheduler.setDeclineRate(0.12);        // 12%/year per well
scheduler.setFacilityCapacity(28000.0);// boe/d plateau constraint

double[][] schedule = scheduler.generateSchedule(25);
// schedule[year][0] = oil rate, [1] = gas rate, [2] = water rate
```

---

## Facility Bottleneck Analysis

### Identifying Constraints

```java
BottleneckAnalyzer analyzer = new BottleneckAnalyzer(processSystem);
analyzer.setReservoirDecline(reservoirModel);
analyzer.setRateRange(10000, 60000, 5000);  // min, max, step (boe/d)

Map<String, Double> bottlenecks = analyzer.findBottlenecks();
// Returns: {"HP Separator": 35000, "Gas Compressor": 28000,
//           "Export Pump": 40000, "Water Treatment": 45000}
// Bottleneck = Gas Compressor at 28,000 boe/d
```

### Facility Capacity Over Time

```java
FacilityCapacity capacity = new FacilityCapacity();
capacity.setEquipment("HP Separator", 35000.0);    // boe/d
capacity.setEquipment("Gas Compressor", 28000.0);
capacity.setEquipment("Export Pump", 40000.0);
capacity.setEquipment("Water Treatment", 45000.0);

// Apply to production profile
double[] constrainedProfile = capacity.constrain(unconstrained);
String bottleneck = capacity.getActiveBottleneck(year);
```

---

## Production Allocation

### Multi-Field to Single Facility

```java
ProductionAllocator allocator = new ProductionAllocator();
allocator.addField("Field A", fieldAProfile, 0.60);  // priority weight
allocator.addField("Field B", fieldBProfile, 0.30);
allocator.addField("Field C", fieldCProfile, 0.10);
allocator.setFacilityCapacity(50000.0);               // boe/d

Map<String, double[]> allocation = allocator.allocate();
// Returns constrained profiles per field
```

---

## Network Optimization

### Multi-Well Gathering System

```java
NetworkSolver network = new NetworkSolver("Production Network");
network.addWell(well1, 3.0);    // well, flowline length (km)
network.addWell(well2, 5.5);
network.addWell(well3, 8.0);
network.addWell(well4, 4.2);

// Mode 1: Fixed manifold pressure → find well rates
network.setSolutionMode(SolutionMode.FIXED_MANIFOLD_PRESSURE);
network.setManifoldPressure(60.0);  // bara
NetworkResult result = network.solve();

for (String wellName : result.getWellNames()) {
    double rate = result.getWellRate(wellName);     // Sm3/d
    double whp = result.getWellheadPressure(wellName); // bara
}

// Mode 2: Fixed total rate → find required manifold pressure
network.setSolutionMode(SolutionMode.FIXED_TOTAL_RATE);
network.setTargetTotalRate(50000.0);  // Sm3/d
result = network.solve();
double requiredManifoldP = result.getManifoldPressure();
```

### LoopedPipeNetwork (Advanced)

Full NR-GGA production network solver with IPR, chokes, tubing VLP, Beggs-Brill
multiphase, compressors, artificial lift, water/sand/corrosion/emissions:

```java
LoopedPipeNetwork net = new LoopedPipeNetwork("Gathering");
net.setFluidTemplate(gas);
net.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
net.setMaxIterations(500);
net.setTolerance(500.0);

// Wells with IPR
net.addSourceNode("R1", 230.0, 0.0);
net.addJunctionNode("MF1");
net.addWellIPR("R1", "MF1", "W1", 5e-13, true);

// Artificial lift
net.setGasLift("W1", 500.0);             // kg/hr
net.setESP("W2", 80.0, 0.55);            // kW, efficiency

// Water, sand, corrosion tracking
net.setWaterCut("W1", 0.15);
net.setSandRate("W1", 3.0);              // kg/hr
net.setCorrosiveGas("trunk", 0.035, 0.002);  // CO2, H2S mol frac
net.setCorrosionModel("trunk", "NORSOK");     // NORSOK M-506

// GHG emissions
net.setCO2EmissionFactor(2.75);
net.setMethaneSlipFactor(0.02);

net.run();

// Post-run analysis
Map<String, double[]> sand = net.calculateSandTransport();
Map<String, double[]> corr = net.calculateCorrosion();
Map<String, double[]> em = net.calculateEmissions();
double annualCO2 = net.getAnnualCO2EmissionsTonnes();
```

See [production_well_networks.md](docs/process/equipment/production_well_networks.md)
for full API documentation of all features.

---

## Gas Lift Optimization

### Single Well

```java
GasLiftCalculator glCalc = new GasLiftCalculator();
glCalc.setWellDepth(3000.0);          // m
glCalc.setReservoirPressure(250.0);   // bara
glCalc.setProductionRate(5000.0);     // boe/d
glCalc.setGLR(500.0);                // Sm3/Sm3
glCalc.setInjectionPressure(150.0);   // bara

double optimalGLR = glCalc.calculateOptimalGLR();
double gasRate = glCalc.calculateInjectionRate();  // Sm3/d
```

### Multi-Well Allocation

```java
GasLiftOptimizer optimizer = new GasLiftOptimizer();
optimizer.addWell("P1", well1, glCalc1);
optimizer.addWell("P2", well2, glCalc2);
optimizer.addWell("P3", well3, glCalc3);
optimizer.setTotalGasAvailable(500000.0);  // Sm3/d field gas supply

Map<String, Double> allocation = optimizer.optimize();
// Returns optimal gas injection rate per well
double totalOilGain = optimizer.getTotalOilGain();
```

---

## Scenario Analysis

### Multi-Scenario Comparison

```java
ScenarioAnalyzer scenarios = new ScenarioAnalyzer();

// Base case
scenarios.addScenario("Base", baseEngine);

// High oil price
CashFlowEngine highPrice = baseEngine.clone();
highPrice.setOilPrice(90.0);
scenarios.addScenario("High Price", highPrice);

// Low recovery
CashFlowEngine lowRecovery = baseEngine.clone();
lowRecovery.setRecoveryFactor(0.45);
scenarios.addScenario("Low Recovery", lowRecovery);

// Accelerated drilling
CashFlowEngine accelerated = baseEngine.clone();
accelerated.setDrillingInterval(3);  // 3 months vs 6
scenarios.addScenario("Accelerated", accelerated);

Map<String, CashFlowResult> results = scenarios.runAll();
scenarios.generateComparisonTable();
```

---

## IOR/EOR Screening Considerations

### Water Injection (Voidage Replacement)

```java
InjectionStrategy waterInj = InjectionStrategy.waterInjection(1.0);  // VRR = 1.0
InjectionResult result = waterInj.calculateInjection(
    reservoir, oilRate, gasRate, waterRate
);
double requiredRate = result.waterInjectionRate;   // Sm3/d
double achievedVRR = result.achievedVRR;
```

### Gas Injection

```java
InjectionStrategy gasInj = InjectionStrategy.gasInjection(0.8);  // VRR = 0.8
// May also use produced gas reinjection
```

### Key IOR/EOR Methods (Screening Criteria)

| Method | Viscosity Limit | Depth Limit | API Gravity | Recovery Boost |
|--------|----------------|-------------|-------------|----------------|
| Water flood | < 150 cP | Any | > 15° | 5-30% OOIP |
| WAG | < 10 cP | > 1500 m | 25-50° | 5-15% OOIP |
| Polymer | 10-150 cP | < 3000 m | > 15° | 5-15% OOIP |
| Steam (SAGD) | > 200 cP | < 1500 m | 7-20° | 20-50% OOIP |
| CO2 flooding | < 12 cP | > 600 m | > 25° | 8-20% OOIP |
| Surfactant | < 35 cP | < 3000 m | > 20° | 5-15% OOIP |

---

## Emissions Tracking

```java
EmissionsTracker emissions = new EmissionsTracker();
emissions.setProcessSystem(processSystem);
emissions.setFuelType("natural_gas");
emissions.setProductionRate(25000.0);  // boe/d

double co2Tonnes = emissions.calculateAnnualCO2();
double intensity = emissions.getCO2Intensity();  // kgCO2/boe
```

### Detailed Emissions

```java
DetailedEmissionsCalculator detailedCalc = new DetailedEmissionsCalculator();
detailedCalc.setGasTurbinePower(25.0);    // MW
detailedCalc.setFlareRate(5000.0);        // Sm3/d
detailedCalc.setFugitiveRate(0.001);      // fraction of throughput

Map<String, Double> breakdown = detailedCalc.calculate();
// {"turbine_CO2": ..., "flare_CO2": ..., "fugitive_CH4": ..., "total_CO2e": ...}
```

---

## Energy Efficiency

```java
EnergyEfficiencyCalculator efficiency = new EnergyEfficiencyCalculator();
efficiency.setProcessSystem(processSystem);
efficiency.setExportRate(25000.0);  // boe/d

double specificPower = efficiency.getSpecificPower();  // kW/boe
double energyEfficiency = efficiency.getEfficiency();  // %
Map<String, Double> consumers = efficiency.getPowerBreakdown();
// {"Gas Compression": 15.2, "Water Injection": 8.5, "Utilities": 3.1}
```

---

## Late Life & Decommissioning

### Late Life Options

| Strategy | NeqSim Support | Key Considerations |
|----------|---------------|-------------------|
| Infill drilling | New `WellSystem` + network rebalance | Marginal well economics |
| Water shut-off | Adjust `WellSystem` water cut | Intervention cost vs benefit |
| Gas lift optimization | `GasLiftOptimizer` | Declining reservoir pressure |
| Tie-back satellite | `TiebackAnalyzer` | Host capacity utilization |
| EOR (CO2, polymer) | `InjectionStrategy` + EOS | Fluid compatibility |
| Cessation of production | `DecommissioningEstimator` | Regulatory requirements |

### Decommissioning

```java
DecommissioningEstimator decom = new DecommissioningEstimator();
decom.setNumberOfWells(6);
decom.setWellAbandonment(true);       // P&A wells
decom.setSubseaRemoval(true);         // Remove subsea equipment
decom.setPlatformRemoval(false);      // Subsea tieback — no platform
decom.setPipelineDecommissioning(true);
decom.setWaterDepth(350.0);
decom.setRegion("Norway");

double abex = decom.estimate();       // MUSD
Map<String, Double> breakdown = decom.getBreakdown();
```

---

## Common Optimization Pitfalls

| Pitfall | Impact | Prevention |
|---------|--------|------------|
| Optimizing wells independently | Sub-optimal network, back-pressure effects | Always use `NetworkSolver` for coupled optimization |
| Ignoring facility constraints | Unrealistic production profile | Apply `FacilityCapacity` constraints to profile |
| Static gas lift allocation | Missed oil as reservoir depletes | Re-optimize gas lift periodically as BHP declines |
| Ignoring water cut increase | Overstated revenue, understated OPEX | Model watercut trajectory, include water treatment costs |
| Over-producing from best wells | Premature water/gas coning | Balanced withdrawal per reservoir zone |
| Ignoring backpressure coupling | Wrong wellhead pressures | Network solver captures well-to-well interactions |
