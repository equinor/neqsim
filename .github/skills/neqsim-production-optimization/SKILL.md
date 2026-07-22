---
name: neqsim-production-optimization
description: "Production optimization, bottleneck analysis, decline modeling, decline-curve history matching (Arps + Duong), reservoir material balance surveillance (OGIP/OOIP, drive indices, aquifer influx), and IOR/EOR screening with NeqSim. USE WHEN: optimizing production rates, identifying facility bottlenecks, forecasting production profiles, fitting decline curves to production history, estimating reserves from pressure/production data, analyzing gas lift allocation, evaluating IOR/EOR options, or running multi-scenario production comparisons."
last_verified: "2026-07-11"
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

## Decline Curve Fitting & History Matching (`DeclineCurveAnalysis`)

`neqsim.pvtsimulation.util.DeclineCurveAnalysis` is a static, unit-agnostic
Arps **+ Duong** decline toolkit. Besides the forward `rate(...)`,
`cumulativeProduction(...)`, `eur(...)` and `forecast(...)` methods it now
**least-squares fits** decline parameters to a measured rate-time history — the
inverse (surveillance) direction used to estimate remaining reserves and EUR
directly from production data. Times are in days; rates keep whatever consistent
unit you supply.

```java
import java.util.Map;
import neqsim.pvtsimulation.util.DeclineCurveAnalysis;

// t[] in days, q[] in bbl/d (or Sm3/d, MMscf/d — unit-agnostic)
Map<String, Double> fit = DeclineCurveAnalysis.fitArps(t, q);
double qi = fit.get("qi");        // initial rate
double di = fit.get("di");        // nominal decline (1/day)
double b = fit.get("b");          // Arps exponent (grid-searched 0..1)
double r2 = fit.get("rSquared");  // goodness of fit

// EUR to an economic-limit rate straight from the fit
double eur = DeclineCurveAnalysis.eurFromFit(fit, 50.0);   // rate-unit * days

// Windowed fit — exclude early transient / cleanup points (indices 0..2 here)
Map<String, Double> fitW = DeclineCurveAnalysis.fitArps(t, q, 3, t.length - 1);
```

The `fitArps` overloads grid-search the exponent `b` over `[0, 1]` (coarse then
fine), analytically solving `qi` and `di` for each candidate by linearising in
rate space (`ln q` vs `t` for `b≈0`; `q^-b` vs `t` otherwise). Use the windowed
overload to fit only the established boundary-dominated decline.

### Duong (2011) — tight / unconventional wells

For fracture-dominated tight-gas and shale wells the Arps model over-estimates
reserves. The **Duong (2011)** model is included:

```java
// Forward
double q = DeclineCurveAnalysis.rateDuong(q1, a, m, t);        // rate at time t
double gp = DeclineCurveAnalysis.cumulativeDuong(q1, a, m, t); // cumulative

// Fit q1, a, m to a rate history (log-log q/Gp vs t straight line)
Map<String, Double> duong = DeclineCurveAnalysis.fitDuong(t, q);
// keys: "q1", "a", "m", "rSquared"
```

**Model selection:** fit both `fitArps` and `fitDuong`, compare `rSquared`, and
prefer Duong when the well is in transient linear (fracture-dominated) flow.

---

## Reservoir Material Balance & Surveillance (inverse tank models)

`neqsim.pvtsimulation.reservoirproperties.materialbalance` regresses original
hydrocarbon in place, drive mechanism and aquifer support **directly from a
measured pressure-vs-cumulative-production history**. These "inverse" tank models
complement the forward depletion model (`SimpleReservoir`) — use them for
reserves surveillance and drive diagnosis. Pressures are in bara, temperatures in
Kelvin; cumulative volumes keep any consistent surface unit and the returned
in-place volume is in the same unit.

### Gas — P/Z line, Cole plot, Havlena-Odeh (`GasMaterialBalance`)

```java
import neqsim.pvtsimulation.reservoirproperties.materialbalance.GasMaterialBalance;

// P/Z straight line → OGIP (supply Z, or let it compute Z from Sutton + Hall-Yarborough)
GasMaterialBalance.Result r = GasMaterialBalance.fitVolumetric(pressure, z, gp);
double ogip = r.getOgip();          // x-intercept where p/Z = 0
double piZi = r.getPiOverZi();      // initial p/Z intercept (bara)
double r2 = r.getRSquared();

// Compute Z internally (Sutton pseudo-criticals + Hall-Yarborough)
GasMaterialBalance.Result r2fit = GasMaterialBalance.fitVolumetric(pressure, gp, tempK, gasGravity);

// Cole plot — aquifer diagnostic (flat = volumetric depletion, rising = water influx)
double[][] cole = GasMaterialBalance.colePlot(pressure, z, gp, tempK); // [0]=Gp, [1]=F/Eg

// Havlena-Odeh with a supplied cumulative water influx We
GasMaterialBalance.Result rHO = GasMaterialBalance.fitHavlenaOdeh(pressure, z, gp, we, tempK);
```

### Oil — Havlena-Odeh, gas-cap ratio, drive indices (`OilMaterialBalance`)

```java
import neqsim.pvtsimulation.reservoirproperties.materialbalance.OilMaterialBalance;

// Build the F / Eo / Eg / Efw terms from black-oil PVT, then regress:
OilMaterialBalance.Result dep = OilMaterialBalance.fitDepletionDrive(f, eo);        // OOIP (no gas cap / aquifer)
OilMaterialBalance.Result gc = OilMaterialBalance.fitGasCapDrive(f, eo, eg);        // OOIP + gas-cap ratio m
OilMaterialBalance.Result wd = OilMaterialBalance.fitWaterDrive(f, eo, we, bw);     // OOIP with known We

// Pirson fractional drive indices {DDI, SDI, WDI, EDI} (sum ≈ 1)
double[] di = OilMaterialBalance.driveIndices(n, m, eoTerm, egTerm, efwTerm, we, bw, fTerm);
```

### Aquifer influx — Van Everdingen-Hurst / Carter-Tracy (`VanEverdingenHurstAquifer`)

```java
import neqsim.pvtsimulation.reservoirproperties.materialbalance.VanEverdingenHurstAquifer;

double u = VanEverdingenHurstAquifer.aquiferConstant(porosity, ct, thickness, radius, angleDeg);
double[] we = VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(tD, deltaP, u, reD); // reservoir m3
String aqutab = VanEverdingenHurstAquifer.exportAqutab(tD, reD);  // ECLIPSE AQUTAB include table
```

The `We` array feeds the aquifer term of `GasMaterialBalance.fitHavlenaOdeh` /
`OilMaterialBalance.fitWaterDrive`. Use `reD = Double.POSITIVE_INFINITY` for an
infinite-acting aquifer or a finite `reD` for a bounded one.

**Workflow:** (1) diagnose drive with the Cole plot / drive indices, (2) if
water drive, build `We` with Carter-Tracy, (3) regress OGIP/OOIP with the
matching `fit*` method, (4) cross-check reserves against the `DeclineCurveAnalysis`
EUR.

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

### Choke + Lift-Gas Co-Optimization with Facility Constraints (strupe/øke lists)

When the decision is *which wells to choke back or open up* under **several** shared
facility ceilings at once (gas handling, produced-water/PWRI, and the lift-gas budget) —
the classic offshore "strupe/øke liste" — use the choke-and-gas-lift allocation stack in
`neqsim.process.fielddevelopment.integrated`. It co-optimizes choke opening **and**
lift-gas per well, honours discrete on/off locks (sand, lost comms, life extension) and
per-well gas ceilings, and emits an operator-ranked action list.

```java
// NIP-1: build a per-well GLPC anchored on a rigorous WellSystem nodal solve.
//        base oil rate comes from NeqSim IPR-VLP; the lift response is fitted to a
//        well-test peak (optimumLift, peakOil) or a fractional uplift.
GasLiftPerformanceCurve curve =
    GasLiftPerformanceCurve.fromWellSystem(wellSystem, 60000.0, peakOil, 150000.0);
// or: GasLiftPerformanceCurve.fromWellSystemUplift(wellSystem, 0.25, 50000.0, 150000.0);

// NIP-2: a chokeable, gas-lifted well with bounds and operational locks.
ChokeableGasLiftWell w = new ChokeableGasLiftWell("S-24", curve)
    .setMaxChokeFraction(0.60)          // "0-60%"
    .setCurrentChokeFraction(0.0)       // current strupe setting
    .setGor(750.0).setWaterCut(0.35)    // for facility roll-up
    .setGasHandlingLimit(750_000.0);    // per-well "mye gass" ceiling
// .setForcedShut(true, "sand production");  // hard lock

// NIP-3: co-optimize choke + lift under multiple facility constraints (never throws).
ChokeAndGasLiftAllocationOptimizer opt = new ChokeAndGasLiftAllocationOptimizer()
    .addWell(w) /* ...add all wells... */
    .setLiftGasBudget(totalLiftGasSm3d)
    .setGasHandlingLimit(maxGasSm3d)     // shared compressor limit
    .setWaterHandlingLimit(maxWaterSm3d) // shared produced-water/PWRI limit
    .setObjective(ChokeAndGasLiftAllocationOptimizer.Objective.OIL);
ChokeAndGasLiftAllocationOptimizer.AllocationResult r = opt.optimize();
String json = r.toJson();                // schema-versioned

// NIP-4: turn the optimum into the ranked strupe/øke recommendation list.
StrupeOkeReport report = StrupeOkeReport.build(Arrays.asList(w /* ... */), r);
System.out.println(report.toTable());    // per well: OPEN / CHOKE_BACK / SHUT / NO_CHANGE + uplift
```

Screening-grade: choke is a linear deliverability scale, the facility relief is a greedy
"choke back the least valuable barrels first" search. Use `LoopedPipeNetwork` for a
rigorous coupled network solve. Distinct from `GasLiftOptimizer` (lift gas + compression
only) and `ReservoirToMarketOptimizer` (choke + one throughput cap only).

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
| Choke + lift under gas/water/lift limits (strupe/øke list) | `ChokeAndGasLiftAllocationOptimizer` + `StrupeOkeReport` | Multi-constraint fleet, on/off locks |
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
