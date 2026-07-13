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
| `GasTurbineVendorPerformance` | `process.equipment.powergeneration` | Vendor ISO-rated GT driver — fuel + CO2 matched to a shaft/electric load demand |
| `SteamTurbine` | `process.equipment.powergeneration` | Steam expansion turbine |
| `HRSG` | `process.equipment.powergeneration` | Heat recovery steam generator |
| `CombinedCycleSystem` | `process.equipment.powergeneration` | GT + HRSG + ST integrated system |
| `PinchAnalysis` | `process.equipment.heatexchanger.heatintegration` | Pinch analysis for heat integration |
| `HeatStream` | `process.equipment.heatexchanger.heatintegration` | Hot/cold stream for pinch analysis |

## Vendor-performance GT driver — matched fuel & power (PREFERRED for drivers)

For a real mechanical-drive or generator gas turbine (e.g. a GE LM2500 driving a
compressor or an AC generator), the built-in simple-cycle `GasTurbine` is a rough
model and is unreliable for net power / efficiency. Use
`GasTurbineVendorPerformance` instead: it takes a **load demand** and returns the
fuel rate and CO2 **matched to that load** using the fuel gas's rigorous ISO 6976
LCV and stoichiometric carbon.

```java
GasTurbineVendorPerformance gt = new GasTurbineVendorPerformance("GT driver", fuelStream);
gt.setVendorRating(22.4, "MW", 0.37);        // ISO base power, base LHV efficiency
gt.setAmbientDerating(15.0, 0.007);          // design ambient C, power lapse /degC
gt.setSiteAmbientTemperature(15.0);
gt.setPartLoadHeatRateCoefficient(0.15);     // heat-rate rise at part load
gt.setLoadDemand(compressor.getPower("kW"), "kW");  // <-- the process power need
gt.run(UUID.randomUUID());
double fuel = gt.getFuelFlowRate("kg/hr");
double co2  = gt.getCO2EmissionRate("tonne/day");
double loadFrac = gt.getLoadFraction();      // vs site-rated MAX (bottleneck basis)
double spareKW  = gt.getSiteRatedPower("kW") - compressor.getPower("kW");
```

**Matching rule (do this, never hardcode fuel/power):**
- **Mechanical-drive GT** load demand = the *simulated* compressor shaft power
  (`compressor.getPower("kW")`), so fuel and CO2 track the process automatically.
- **Fuel gas** is a slip-stream of the real process gas (e.g. treated export gas).
  For a closed mass balance, physically tap it with a `Splitter`
  (`setFlowRates([-1.0, fuelKgHr], "kg/hr")`) so sales gas = gas − fuel.
- **Platform electric LOAD** is an *operational* quantity — resolve it by source
  priority, do NOT hardcode: (1) measured historian tag (tagreader/Seeq
  main-switchboard active power) — the correct primary source; (2) STID
  generator installed rating × documented load factor *if* STID exposes a
  structured rating; (3) a clearly-flagged fallback. STID's structured
  `rating`/`load` fields are frequently empty (they live in the datasheet PDF),
  so the historian is the right source for the actual load — record the chosen
  `source` in `results.json` so the value is never an unsourced magic number.
- **Max limits / bottlenecks** for production optimization: report per-GT
  `getSiteRatedPower`, `getLoadFraction`, spare power, and N+1 firm capacity for
  the generators; the highest-loaded unit is the power bottleneck.

## 1. Gas Turbine

```java
// Fuel gas stream — the combustion air is generated internally by the GasTurbine.
SystemInterface fuelGas = new SystemSrkEos(273.15 + 25, 30.0);
fuelGas.addComponent("methane", 0.90);
fuelGas.addComponent("ethane", 0.06);
fuelGas.addComponent("propane", 0.02);
fuelGas.addComponent("nitrogen", 0.02);
fuelGas.setMixingRule("classic");

Stream fuelStream = new Stream("Fuel Gas", fuelGas);
fuelStream.setFlowRate(5000.0, "kg/hr");
fuelStream.run();

// Simplified Brayton-cycle gas turbine: internal air compressor + combustor + expander + cooler.
GasTurbine gt = new GasTurbine("GT-001", fuelStream);
gt.combustionpressure = 18.0;      // firing / combustion pressure [bara]
gt.setExcessAirFactor(2.5);        // excess air over stoichiometric (caps firing temperature)
gt.run();

double power_MW = gt.getPower("MW");           // net shaft power (expander - air compressor)
double rejectHeat_W = gt.getHeat();           // heat rejected by the exhaust cooler [W]
double idealAFR = gt.calcIdealAirFuelRatio(); // stoichiometric air/fuel mass ratio
```

> The legacy `GasTurbine` is a simplified thermodynamic Brayton model. For
> vendor-rated power, part-load + ambient correction, degradation, emissions,
> and dispatch use `GasTurbineUnit` + `GasTurbineCatalog` (see section 7).

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
// Recover heat from a hot turbine-exhaust gas stream. The HRSG takes a single
// hot-gas inlet stream; steam conditions are set on the unit.
HRSG hrsg = new HRSG("HRSG-001", exhaustGasStream);
hrsg.setSteamPressure(40.0);            // bara
hrsg.setSteamTemperature(400.0, "C");
hrsg.setFeedWaterTemperature(105.0, "C");
hrsg.setApproachTemperature(15.0);
hrsg.run();

double duty_W = hrsg.getHeatTransferred();
double steamFlow = hrsg.getSteamFlowRate("kg/hr");
double stackT = hrsg.getGasOutletTemperature() - 273.15;  // °C
```

## 4. Combined Cycle System

```java
// Integrated GT + HRSG + ST. The sub-units are built internally from the fuel
// gas stream — configure conditions through the setters below.
CombinedCycleSystem ccgt = new CombinedCycleSystem("CCGT", fuelStream);
ccgt.setCombustionPressure(18.0);
ccgt.setSteamPressure(40.0);
ccgt.setSteamTemperature(400.0, "C");
ccgt.setGasTurbineEfficiency(0.38);
ccgt.setSteamTurbineEfficiency(0.85);
ccgt.run();

double totalPower = ccgt.getTotalPower("MW");
double gtPower_W = ccgt.getGasTurbinePower();
double stPower_W = ccgt.getSteamTurbinePower();
double combinedEfficiency = ccgt.getOverallEfficiency();
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
double fuelRate_kg_hr = fuelStream.getFlowRate("kg/hr");
double co2Factor = 2.75;  // kg CO2 / kg fuel (adjust for actual composition)
double co2_tonnes_yr = fuelRate_kg_hr * co2Factor * 8760 / 1e6;

// For a rigorous full-carbon-balance CO2, NOx, and methane-slip estimate use the
// catalog-driven GasTurbineUnit (section 7): gt.getCO2EmissionKgPerHr().
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

## Flue-gas composition, NOx, SO2 and adiabatic flame temperature — `CombustionCalculator`

For the **exhaust (flue-gas) composition and pollutant rates** of any turbine, burner, or fired
heater, use `neqsim.process.util.combustion.CombustionCalculator`. It deliberately splits the two
physically different families of exhaust species — **do not use a single Gibbs reactor for the whole
problem:**

- **Major species (N2, O2, CO2, H2O, Ar) and SO2 — stoichiometric.** These are combustion-complete
  (not equilibrium-limited at the exhaust), so an exact element balance of full combustion with
  excess air is used: all fuel C -> CO2, all fuel H -> H2O, all fuel S (e.g. H2S) -> SO2.
- **NOx and CO — kinetically frozen.** Thermal (Zeldovich) NO and CO quench in the exhaust. A Gibbs
  reactor **over-predicts NO at flame temperature and returns ~0 at stack temperature**, so it is the
  wrong tool. They come from public EMEP/EEA-style **emission factors** (g per GJ fuel LHV), which
  the caller replaces with a vendor guarantee or CEMS value.
- **Adiabatic flame temperature** is a rigorous NeqSim energy balance (bisection on the product
  mixture enthalpy so the released LHV heats the products from 298 K) — excess air lowers it via
  inert dilution.

```java
SystemInterface fuel = new SystemSrkEos(288.15, 20.0);
fuel.addComponent("methane", 0.95);
fuel.addComponent("ethane", 0.03);
fuel.addComponent("H2S", 0.0002);
fuel.setMixingRule("classic");

CombustionCalculator.CombustionResult r = new CombustionCalculator(fuel)
    .setFuelFlowRate(7896.0)         // kg/hr (total combusted fuel)
    .setExcessAirRatio(3.2)          // GT ~3-3.5 -> ~14-15 vol% exhaust O2; burner ~1.05-1.2
    .setBurnerType(CombustionCalculator.BurnerType.GAS_TURBINE_DLE) // sets typical NOx/CO for the technology
    .setNoxFactorGPerGJ(130.0)       // optional: override with a vendor/CEMS value AFTER setBurnerType
    .setCoFactorGPerGJ(30.0)
    .setAssumedFuelH2sPpmv(5.0)      // used only if the fuel carries no sulphur
    .calculate();

r.getFlueMoleFraction("CO2");         // exhaust CO2 mole fraction
r.exhaustO2VolPercent;                // ~14 vol% for a GT
r.pollutantPpmv.get("NOx");           // NOx ppmv (emission-factor basis)
r.getMassRateKgPerHr("SO2");          // SO2 kg/hr (fuel-sulphur stoichiometric)
r.adiabaticFlameTemperatureK;         // rigorous NeqSim energy balance
String json = r.toJson();
```

**Burner technology (`BurnerType`)** — NOx and CO are combustion-technology-dependent, so the
calculator ships a small burner database that sets typical factors: `CONVENTIONAL` (130/30 g/GJ),
`LOW_NOX` (60/40), `ULTRA_LOW_NOX` (30/50), `GAS_TURBINE_CONVENTIONAL` (300/40),
`GAS_TURBINE_DLE` (90/30), `GAS_TURBINE_WET` (130/60). A low-NOx technology trades lower NOx for
somewhat higher CO. These are screening defaults on a natural-gas basis — always override with a
vendor guarantee or CEMS value when available. Accuracy also depends on fuel composition (H2 raises
flame temperature and NOx), excess air / O2, combustion-air preheat, load, humidity / water-steam
injection and the reference-O2 reporting basis (3% for heaters, 15% for GT); the stoichiometric
majors and SO2 are exact, only the emission-factor NOx/CO carry this technology sensitivity.

`stoichAirFuelMassRatio` / `airFuelMassRatio`, `fuelLhvKJperKg`, `fuelEnergyGJperHr` and the full
`massRateKgPerHr` map (NOx key is `NOx_as_NO2`) are also returned. This pairs with
`GasTurbineVendorPerformance` / `GasTurbine` (which give the fuel rate) and with the CO2 accounting
above. The same `setBurnerType(...)` / emission-factor NOx/CO is available on the `FurnaceBurner`
unit operation via `setUseEmissionFactorPollutants(true)` or `setBurnerType(...)`.

### Stack-emission reporting (dry basis, reference O2, mg/Nm3, Nm3/hr, t/yr)

For a **regulatory stack-emission report** (EU IED / EN 14792 NOx, EN 15058 CO, EN 14791 SO2) the
concentration must be **dry, at a reference O2, in mg/Nm3** — not wet ppmv. The calculator does this
chain correctly:

- `setReferenceO2VolPercent(3.0)` for fired heaters/boilers, `15.0` for gas turbines. The result
  then carries `pollutantPpmvAtReferenceO2` and `pollutantMgPerNm3AtReferenceO2` (the value directly
  comparable with a permit limit). The reference-O2 correction `C_ref = C*(20.9-O2ref)/(20.9-O2meas)`
  is applied on the **dry** concentration at the **dry** exhaust O2 (`exhaustO2VolPercentDry`) — the
  20.9 % basis is dry air, so mixing wet ppmv with it (a common mistake) is wrong.
- `setNormalTemperatureC(0.0)` (EU "Normal", 273.15 K; use 15/25 for other bases) drives the mg/Nm3
  conversion and the normalized flue-gas flow `flueGasNm3PerHrWet` / `flueGasNm3PerHrDry`. Sanity
  check: **1 ppmv NOx-as-NO2 ~ 2.05 mg/Nm3, CO ~ 1.25, SO2 ~ 2.86** at 0 degC.
- `setAnnualOperatingHours(8000)` rolls the mass rates up to `massRateTonnesPerYear` (permit / annual
  report). `pollutantPpmv` is wet, `pollutantPpmvDry` is dry — always compare limits on the dry / ref-O2
  outputs.

### SO3 / acid dew point, other pollutants, NOx routes, actual stack conditions

The extended stack-emission physics (all optional, 0 / off by default so clean-gas results are unchanged):

- **SO3 & acid dew point** — `setSo3FractionOfSox(0.03)` splits fuel-sulphur oxides into SO2 and SO3
  (typically 1–5 %). The result then reports `SO3`, the `acidDewPointC` (Verhoff-Banchero sulfuric-acid dew
  point) and always the `waterDewPointC`. The acid dew point (often 120–150 °C) is well above the water dew
  point and sets the cold-end / stack minimum metal temperature to avoid corrosion.
- **Other pollutants** — `setPmFactorGPerGJ`, `setCh4SlipFactorGPerGJ`, `setVocFactorGPerGJ`,
  `setN2oFactorGPerGJ` add particulates, methane slip, non-methane VOC and N2O (relevant for liquid/dual-fuel,
  lean-premix GT, and reciprocating gas engines). Each appears in `massRateKgPerHr`, `massRateTonnesPerYear`,
  and (for the gaseous species) the ppmv / mg/Nm3 maps.
- **NOx route breakdown** — the base NOx factor is the thermal (Zeldovich) route; add the additive
  `setPromptNoxFactorGPerGJ` (Fenimore) and `setFuelNoxFactorGPerGJ` (fuel-bound N). The result carries
  `noxThermalKgPerHr` / `noxPromptKgPerHr` / `noxFuelKgPerHr` and the total in `massRateKgPerHr["NOx_as_NO2"]`.
- **Actual stack conditions (dispersion/plume)** — `setStackGasTemperatureC(150.0)` (the measured or
  heat-recovery-outlet temperature, NOT the adiabatic flame temperature) gives the actual volumetric flow
  `stackActualM3PerHr` (Am³/hr); adding `setStackDiameterM(1.5)` gives the stack-exit `stackVelocityMPerS`.
  `setStackPressureBara` sets the basis for the actual flow and the dew-point partial pressures.

### Field calibration and fuel-composition / turndown studies

- **Field-calibrate** the emission factor to a CEMS/stack test point:
  `calibrateNoxFromMeasuredPpmv(measuredPpmv, measuredExhaustO2VolPercent)` (and the CO analogue)
  set a multiplicative factor so the model reproduces the measurement and scales to other loads on a
  fuel-energy basis. This anchors magnitude before predicting other conditions.
- **Fuel-composition effect** (e.g. ethane/propane ratio) is captured rigorously through stoichiometry,
  AFR, LHV and flame temperature. Optional `enableThermalNoxScaling(refFlameTempK)` scales NOx with the
  Zeldovich temperature dependence (Ta ~ 38000 K) — **directionally correct** (hotter flame -> more NOx).
- **Fixed-air / minimum-fuel turndown**: `setAirFlowRate(kgPerHr)` puts the calculator in **air-driven
  mode** — lambda floats from the fixed air, the fuel rate and the fuel composition, so you can lower the
  fuel rate and watch lambda rise (leaner), exhaust O2 rise and NOx fall. The result flags `airDriven`
  and `subStoichiometric` (lambda < 1, air-starved — outside the valid screening range). **Caveat:**
  `enableThermalCoScaling(...)` must **not** be used to predict CO over an excess-air (lambda) sweep — CO
  vs lambda is U-shaped (O2-driven), and the flame-T scaling predicts the wrong sign there; field-calibrate
  CO against measured CO-vs-O2 for load/turndown studies.

**Physics basis / limits (state-of-the-art alignment):** stoichiometry + excess air (Turns; GPSA;
API 560/537), Zeldovich thermal NOx (Ta ~ 38000 K; Lefebvre), EMEP/EEA + EPA AP-42 emission factors,
EPA Method 19 / EN 14792 reference-O2 correction, Verhoff-Banchero acid dew point. Prompt/fuel-N NOx and
N2O/PM/CH4/VOC are available as optional emission factors; the SO3 split and acid/water dew points and the
actual stack conditions are modelled. The **one** remaining physics limit is high-temperature dissociation in
the adiabatic flame temperature (like a no-dissociation H&MB simulator it slightly over-predicts near
stoichiometric). For rigorous flame chemistry use Cantera/CHEMKIN with a validated mechanism (GRI-Mech 3.0) —
this class is the fast, auditable engineering-screening model consistent with how process simulators estimate
emissions.

### Combined-cycle retrofit (HRSG + SteamTurbine on top of the fleet)

To evaluate a bottoming cycle on top of a `GasTurbineUnit` retrofit fleet, feed
the synthesised exhaust into the legacy `HRSG` + `SteamTurbine`. Use the
per-unit exhaust mass flow and temperature from `GasTurbineSpec` (corrected by
`GasTurbinePerformanceMap` if needed) to build an exhaust `Stream`, then size
the bottoming cycle and add its power output to the retrofit `LateLifeRetrofitStudy`
result as an annual energy credit (extra MWh × fuel-equivalent × CO2 factor).
The 20-year rightsizing notebook (see below) demonstrates this pattern in
section 10.

### Reservoir + pipeline-driven shaft demand

Instead of a synthetic linear decline, drive the compressor shaft load from a
physics-based reservoir + flow line model. A simple gas-law material balance on
the reservoir gives `(P_res, T_res)` each year; a `PipeBeggsAndBrills` riser
plus flow line gives arrival pressure at the platform; the export compressor
suction pressure then sets shaft demand which the `GasTurbineUnit` /
`TurbineDispatchOptimizer` resolves. This couples the late-life study to real
field decline (recovery factor, reservoir size uncertainty) and is the right
pattern when CO2 tax and turbine count must be evaluated against an uncertain
production profile rather than a stipulated demand curve. Section 11 of the
20-year rightsizing notebook implements this end-to-end.

### Worked example

See [`examples/notebooks/gas_turbine_rightsizing_20yr.ipynb`](https://github.com/equinor/neqsim/blob/master/examples/notebooks/gas_turbine_rightsizing_20yr.ipynb)
for a complete 20-year late-life right-sizing study: catalog + site correction,
degradation, dispatch with N+1, `LateLifeRetrofitStudy` with `CO2TaxSchedule`,
HRSG + steam-turbine combined-cycle extension, and reservoir-driven demand.

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
