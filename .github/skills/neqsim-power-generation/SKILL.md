---
name: neqsim-power-generation
description: "Power generation patterns for NeqSim. USE WHEN: modeling gas turbines, steam turbines, HRSG, combined cycle systems, waste heat recovery, or calculating fuel gas consumption and thermal efficiency. Covers GasTurbine, SteamTurbine, HRSG, CombinedCycleSystem classes and heat integration with PinchAnalysis."
last_verified: "2026-07-04"
---

# Power Generation with NeqSim

Guide for modeling power generation equipment — gas turbines, steam turbines,
heat recovery steam generators (HRSG), and combined cycle systems.

## When to Use This Skill

- Gas turbine modeling (power output, fuel consumption, exhaust conditions)
- Steam turbine expansion and power generation
- Heat recovery steam generator (HRSG) design
- Combined cycle system integration (GT + HRSG + ST)
- Waste heat recovery from process streams
- Fuel gas consumption and CO2 emissions from drivers
- Thermal efficiency calculations
- Heat integration / pinch analysis for process plants

## Key NeqSim Classes

| Class | Package | Purpose |
|-------|---------|---------|
| `GasTurbine` | `process.equipment.powergeneration` | Gas turbine with compressor, combustor, expander |
| `SteamTurbine` | `process.equipment.powergeneration` | Steam expansion turbine |
| `HRSG` | `process.equipment.powergeneration` | Heat recovery steam generator |
| `CombinedCycleSystem` | `process.equipment.powergeneration` | GT + HRSG + ST integrated system |
| `PinchAnalysis` | `process.equipment.heatexchanger.heatintegration` | Pinch analysis for heat integration |
| `HeatStream` | `process.equipment.heatexchanger.heatintegration` | Hot/cold stream for pinch analysis |

## 1. Gas Turbine

```java
// Create fuel gas and air streams
SystemInterface fuelGas = new SystemSrkEos(273.15 + 25, 30.0);
fuelGas.addComponent("methane", 0.90);
fuelGas.addComponent("ethane", 0.06);
fuelGas.addComponent("propane", 0.02);
fuelGas.addComponent("nitrogen", 0.02);
fuelGas.setMixingRule("classic");

Stream fuelStream = new Stream("Fuel Gas", fuelGas);
fuelStream.setFlowRate(5000.0, "kg/hr");

// Air stream (simplified composition)
SystemInterface air = new SystemSrkEos(273.15 + 15, 1.01325);
air.addComponent("nitrogen", 0.79);
air.addComponent("oxygen", 0.21);
air.setMixingRule("classic");

Stream airStream = new Stream("Combustion Air", air);
airStream.setFlowRate(100000.0, "kg/hr");

// Gas turbine
GasTurbine gt = new GasTurbine("GT-001", fuelStream, airStream);
gt.setIsentropicEfficiency(0.88);
gt.setCompressorPressureRatio(18.0);
gt.run();

double power_MW = gt.getPower("MW");
double thermalEff = gt.getThermalEfficiency();
double exhaustT = gt.getExhaustStream().getTemperature() - 273.15;
double fuelRate = gt.getFuelConsumption("kg/hr");
```

## 2. Steam Turbine

```java
// Steam at high pressure and temperature
SystemInterface steam = new SystemSrkEos(273.15 + 540, 100.0);
steam.addComponent("water", 1.0);
steam.setMixingRule("classic");

Stream steamFeed = new Stream("HP Steam", steam);
steamFeed.setFlowRate(50000.0, "kg/hr");

SteamTurbine st = new SteamTurbine("ST-001", steamFeed);
st.setOutletPressure(0.1);  // Condenser pressure in bara
st.setIsentropicEfficiency(0.85);
st.run();

double stPower = st.getPower("MW");
double outletT = st.getOutletStream().getTemperature() - 273.15;
```

## 3. Heat Recovery Steam Generator (HRSG)

```java
// Recover heat from gas turbine exhaust
HRSG hrsg = new HRSG("HRSG-001", gt.getExhaustStream(), waterFeedStream);
hrsg.run();

Stream steamOut = hrsg.getSteamOutStream();
double steamT = steamOut.getTemperature() - 273.15;
double steamP = steamOut.getPressure();
double stackT = hrsg.getStackTemperature() - 273.15;
```

## 4. Combined Cycle System

```java
// Integrated GT + HRSG + ST
CombinedCycleSystem ccgt = new CombinedCycleSystem("CCGT");
ccgt.setGasTurbine(gt);
ccgt.setHRSG(hrsg);
ccgt.setSteamTurbine(st);
ccgt.run();

double totalPower = ccgt.getTotalPower("MW");
double combinedEfficiency = ccgt.getCombinedEfficiency();
// Typical: 55-62% for modern CCGT
```

## 5. Heat Integration (Pinch Analysis)

```java
// Identify minimum utility requirements for a process
PinchAnalysis pinch = new PinchAnalysis("Plant Heat Integration");

// Add hot streams (need cooling)
pinch.addHotStream(new HeatStream("Reactor effluent", 250.0, 60.0, 5000.0));
pinch.addHotStream(new HeatStream("Column overhead", 120.0, 40.0, 3000.0));
pinch.addHotStream(new HeatStream("Product cooler", 80.0, 30.0, 1500.0));

// Add cold streams (need heating)
pinch.addColdStream(new HeatStream("Feed preheater", 25.0, 180.0, 4500.0));
pinch.addColdStream(new HeatStream("Reboiler", 150.0, 160.0, 2000.0));

// Set minimum approach temperature
pinch.setMinApproachTemperature(10.0);  // degrees C
pinch.run();

double minHotUtility = pinch.getMinHotUtility();   // kW
double minColdUtility = pinch.getMinColdUtility();  // kW
double pinchTemp = pinch.getPinchTemperature();     // °C
```

## 6. Emissions from Power Generation

```java
// Fuel gas composition determines CO2 emissions
// CH4 + 2O2 -> CO2 + 2H2O
// C2H6 + 3.5O2 -> 2CO2 + 3H2O
// C3H8 + 5O2 -> 3CO2 + 4H2O

// Approximate: 2.75 kg CO2 per kg natural gas (varies with composition)
double fuelRate_kg_hr = gt.getFuelConsumption("kg/hr");
double co2Factor = 2.75;  // kg CO2 / kg fuel (adjust for actual composition)
double co2_tonnes_yr = fuelRate_kg_hr * co2Factor * 8760 / 1e6;
```

## 7. Right-Sizing & Dispatch (gasturbine sub-package)

For late-life or turndown studies where the question is *"which turbines, how
many, and at what load?"* — use the catalog-driven classes under
`neqsim.process.equipment.powergeneration.gasturbine`. These integrate directly
with `ProcessSystem` and link to `Compressor.getPower()` via the
`PowerDemandConsumer` interface.

| Class | Purpose |
|-------|---------|
| `GasTurbineCatalog` | Loads the bundled `gas_turbine_catalog.csv` (14 aero + industrial models: LM2500, LM2500PLUS_G4, LM6000PF/PG, RB211_6562, Trent 60, SGT-700/750, Centaur 50, Taurus 60/70, Mars 100, Titan 130/250) |
| `GasTurbineSpec` | Immutable rating point (rated MW, ISO heat rate, exhaust flow/T, NOx, mass) |
| `GasTurbinePerformanceMap` | Part-load + ambient correction (aero vs industrial polynomials, min-load fraction) |
| `GasTurbineDegradation` | Recoverable + non-recoverable fouling vs fired hours, water-wash and overhaul reset |
| `GasTurbineEmissions` | Full-carbon-balance CO2, NOx, methane slip from fuel composition |
| `CO2TaxSchedule` | Loads `co2_tax_norway.csv` (2020–2040 NOK/tonne, CO2 tax + EU ETS), linear interpolation |
| `GasTurbineUnit` | `TwoPortEquipment` — runs inside a `ProcessSystem`, accepts fuel `Stream`, aggregates `Compressor` shaft load via `addPowerConsumer` |
| `TurbineDispatchOptimizer` | Picks the cheapest feasible on/off combination (brute-force ≤8 units, merit-order above) with N+1 reserve |
| `LateLifeRetrofitStudy` | Year-by-year NPV / CO2-avoided / payback for baseline vs retrofit fleet over a declining demand profile |

### Catalog & site-corrected available power

```java
GasTurbineSpec spec = GasTurbineCatalog.get("LM2500");
GasTurbineUnit gt = new GasTurbineUnit("GT-A", fuelStream, spec);
gt.setAmbientTemperatureK(273.15 + 30.0);  // hot day derate
gt.setDemandedPower(15.0e6);                // 15 MW shaft
gt.run(UUID.randomUUID());
double avail_MW = gt.getAvailablePowerW() / 1.0e6;
double load     = gt.getLoadFraction();
double co2_tph  = gt.getCO2EmissionKgPerS() * 3.6;
```

### Linking turbine shaft to compressor demand

Each `GasTurbineUnit` aggregates the live shaft demand from any number of
`Compressor` objects in the same flowsheet — the dispatcher reads it on every
`run()`:

```java
ProcessSystem plant = new ProcessSystem();
plant.add(exportCompressor);     // existing Compressor
plant.add(injectionCompressor);
GasTurbineUnit gt = new GasTurbineUnit("GT-A", fuelStream,
        GasTurbineCatalog.get("LM2500"));
gt.addPowerConsumer(exportCompressor);
gt.addPowerConsumer(injectionCompressor);
plant.add(gt);
plant.run();   // gt sums Compressor.getPower() automatically
```

### Fleet dispatch with N+1 redundancy

```java
List<GasTurbineUnit> fleet = Arrays.asList(gt1, gt2, gt3);  // each is a GasTurbineUnit
TurbineDispatchOptimizer disp = new TurbineDispatchOptimizer(
        /*fuelPriceNOKPerKg*/ 4.5,
        /*co2CostNOKPerTonne*/ 1500.0);
disp.setRequireNplusOne(true);
TurbineDispatchOptimizer.DispatchResult r = disp.dispatch(fleet, 18.0e6);
if (r.feasible) {
    System.out.println(r.summary());   // running units, load, NOK/hr
}
```

### Retrofit NPV vs baseline

```java
double[] demandMW = new double[15];
for (int i = 0; i < 15; i++) demandMW[i] = Math.max(8.0, 20.0 - i * 0.8);

LateLifeRetrofitStudy study = new LateLifeRetrofitStudy(
        baselineFleet,    // e.g. 2x LM6000PF
        retrofitFleet,    // e.g. 2x SGT-700
        demandMW,
        /*startYear*/ 2026,
        CO2TaxSchedule.loadDefault(),
        /*fuelPriceNOKPerKg*/ 4.5);
study.setRetrofitCapexMNOK(800.0);
study.setDiscountRate(0.08);
study.setAnnualOperatingHours(8000);
LateLifeRetrofitStudy.RetrofitResult res = study.run();
System.out.println("NPV (MNOK):       " + res.npvMNOK);
System.out.println("CO2 avoided (t):  " + res.totalCO2AvoidedTonne);
System.out.println("Payback (yr):     " + res.simplePaybackYear);
```

### When to choose this sub-package vs the legacy `GasTurbine`

| Use legacy `GasTurbine` | Use `GasTurbineUnit` + catalog |
|-------------------------|--------------------------------|
| You model the GT thermodynamically (compressor + combustor + expander) and care about exhaust composition into an HRSG | You are doing **right-sizing, dispatch, retrofit, or fleet-level CO2/NPV** studies and want vendor rating points, part-load + ambient correction, degradation, and N+1 |
| Combined-cycle integration where exhaust gas feeds an `HRSG` | Late-life turndown, replacing oversized turbines, CO2-tax sensitivity |

## Applicable Standards

| Standard | Scope |
|----------|-------|
| ISO 2314 | Gas turbine acceptance tests |
| IEC 60034 | Rotating electrical machines |
| API 616 | Gas turbines for petroleum industry |
| API 611/612 | Steam turbines |
| ASME PTC 22 | Gas turbine power performance |
| ASME PTC 6 | Steam turbine performance |

## Common Pitfalls

| Pitfall | Solution |
|---------|----------|
| Unrealistic efficiency (>45% simple cycle) | Typical: 30-40% simple cycle, 55-62% combined cycle |
| Missing air stream for gas turbine | GT requires both fuel and combustion air |
| Steam below saturation in turbine outlet | Check for wet steam — may need superheat or extraction |
| Pinch analysis with wrong units | HeatStream uses °C for temperature, kW for duty |
