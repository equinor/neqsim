---
name: neqsim-utility-design
version: "1.0.0"
description: "Utility-system design with NeqSim and MCP designUtilities: Boiler, Deaerator, NitrogenSystem, SteamNetwork, UtilitySystemDesigner, and RefrigerationCycle or RefrigerationSystem. USE WHEN sizing duties, flows or power, estimating utility CO2 and cost, building a steam header balance, or modeling refrigeration and heat pumps. Covers economizers, intercooling, cascades, process chillers and p-h/p-s/T-s diagrams. Levels: neqsim-utilities-specification. Duties: neqsim-heat-integration."
last_verified: "2026-11-26"
---

# NeqSim Utility Design Skill

Size and cost the core plant utilities with deterministic NeqSim models in
`neqsim.process.util.utilitydesign`. This skill covers the **calculation** side
(duties → flows → power → CO₂ → cost); use
[`neqsim-utilities-specification`](../neqsim-utilities-specification/SKILL.md) to
decide *which* utility levels to provide and
[`neqsim-heat-integration`](../neqsim-heat-integration/SKILL.md) to derive the duties.

## When to Use

- Sizing a fired steam boiler, deaerator, refrigeration package, or N₂ generator
- Building a multi-pressure steam header balance (HP→MP→LP letdown cascade)
- Aggregating heating/cooling/shaft-power/instrument-air demands from a flowsheet
- Estimating utility CO₂ emissions and annual operating cost for ESG/screening
- Exposing utility sizing through the MCP `designUtilities` tool

These are **screening-level** models for early design, not detailed mechanical design. The
exception is refrigeration: `RefrigerationSystem` (below) is a rigorous cycle simulation.

## Classes

All classes live in `neqsim.process.util.utilitydesign`, expose
`public static final String SCHEMA_VERSION = "1.0"`, and provide
`calculate()`, `toResultsMap()`, and `toJson()`. Each has a no-arg and a
`String name` constructor.

| Class | Models | Key inputs | Key outputs |
|-------|--------|-----------|-------------|
| `Boiler` | Fired steam boiler package | steam duties (kW), efficiency, fuel LHV | fuel mass, steam generation, feedwater/blowdown, fan power, CO₂, OPEX |
| `Deaerator` | Feedwater deaerator | feedwater flow, inlet T, pressure | deaerator T, heat duty, stripping steam, vent rate |
| `RefrigerationCycle` | Vapour-compression chiller, screening (Carnot × efficiency) | duties (kW), evaporator/condenser T | COP, compressor power, condenser duty, CO₂, OPEX |
| `NitrogenSystem` | On-site N₂ generation | demand (Nm³/h), purity, method | specific energy, power, air feed, CO₂, OPEX |
| `SteamNetwork` | Multi-pressure header cascade + embedded `Boiler`/`Deaerator` | levels, demands, local generation | per-header balance, boiler generation, totals |
| `UtilitySystemDesigner` | Plant-wide aggregator | run `ProcessSystem`/`ProcessModel` | total heating/cooling/shaft/air, CW + air-cooler split |

## Boiler

```java
import neqsim.process.util.utilitydesign.Boiler;

Boiler boiler = new Boiler("HP Boiler");
boiler.setBoilerEfficiency(0.85);
boiler.setFuelLowHeatingValueMJperKg(47.0);
boiler.addSteamDuty("Reboiler", 5000.0); // kW; negative duty is rejected
boiler.calculate();
double fuelKgh = boiler.getFuelMassDemandKgh();
double steamKgh = boiler.getSteamGenerationKgh();
double co2 = boiler.getCo2TonnePerYear();
String json = boiler.toJson();
```

## RefrigerationCycle

COP is `(T_evap/ΔT) × cycleEfficiency` (Carnot fraction). Condenser duty is the
evaporator duty plus compressor power.

```java
import neqsim.process.util.utilitydesign.RefrigerationCycle;

RefrigerationCycle cycle = new RefrigerationCycle("Propane Chiller");
cycle.setEvaporatorTempC(-35.0);
cycle.setCondenserTempC(35.0);
cycle.setCycleEfficiency(0.55);
cycle.addRefrigerationDuty("Gas Chilling", 3000.0); // kW
cycle.calculate();
double power = cycle.getCompressorPowerKW();
double cop = cycle.getCop();
```

## RefrigerationSystem (rigorous cycle, process unit)

Use `neqsim.process.equipment.refrigeration.RefrigerationSystem` instead of `RefrigerationCycle`
when the answer needs compressor power per stage, refrigerant flows, discharge temperatures,
suction volume flows, an economizer or inter-cooler, a cascade, or a refrigeration plant that
follows the process chillers. It is a `ProcessEquipmentInterface`: add it to the `ProcessSystem`.
Guide: [docs/process/equipment/refrigeration.md](../../../docs/process/equipment/refrigeration.md).

```java
import neqsim.process.equipment.refrigeration.RefrigerationLoad;
import neqsim.process.equipment.refrigeration.RefrigerationSystem;

RefrigerationSystem ref = new RefrigerationSystem("propane refrigeration");
ref.setRefrigerant("propane"); // or R717, CO2, propene, ethylene, R134a ...
ref.setCondenserCoolingMedium(20.0, 30.0, 8.0, "C"); // or setCondensingTemperature(40.0, "C")
ref.setSubcooling(3.0);
ref.setIsentropicEfficiency(0.78);
ref.addLevel("MP", 0.0, "C").setTemperatureFromLoads(5.0).addCooler(warmChiller);
ref.addLevel("LP", -30.0, "C").setTemperatureFromLoads(5.0).addCooler(coldChiller);
process.add(ref);
process.run();
double powerKW = ref.getCompressorPower("kW");
double cop = ref.getCOP();
double condenserKW = ref.getCondenserDuty("kW");
List<String> warnings = ref.getWarnings(); // loads colder than their level allows, wet suction
```

| Task | Call |
|------|------|
| Economizer stage at the geometric-mean pressure | `ref.addGeometricMeanEconomizer("economizer")` after the levels and condenser are set |
| Two-stage ammonia with open flash inter-cooler | `ref.setInterstageCooling(RefrigerationSystem.InterstageCooling.FLASH_INTERCOOLER)` |
| Side-load cycle without flash vessels | `ref.setEconomizerType(RefrigerationSystem.EconomizerType.NONE)` |
| Fixed duty | `level.addFixedDuty("name", 1000.0, "kW")` |
| Cascade (CO2 under ammonia or propane) | `warmLevel.addLoad(RefrigerationLoad.cascadeCondenser(coldSystem))` |
| Detailed compressor on a stage | `new Compressor("K", ref.createSuctionStream(stageIndex))`; stage 0 discharges to the condenser |
| Power to an energy stream or bus | `ref.connectEnergyStream(RefrigerationSystem.SHAFT_POWER_PORT, stream)` |
| Condenser onto cooling water | `ref.addCondenserTo(coolingWaterSystem, approachK)` |
| Calibrate the screening model | `cycle.setCycleEfficiency(ref.getCarnotFraction())` |

**Refrigerant model.** `setRefrigerant(name)` picks the equation of state; do not swap it without a
reason: `SystemAmmoniaEos` for ammonia, `SystemGERG2008Eos` for CO2, `SystemPrMathiasCopeman` for
the rest. Measured on 2026-11-26 against NIST saturation data and the Tillner-Roth and Baehr R134a
equation (saturation pressure / latent heat error): ammonia and CO2 below 0.1 %; propane, ethane and
the butanes within 0.5 % / 0.7 % (plain Peng-Robinson is +2.1 % for isobutane at its boiling point);
R134a within 0.3 % / 2.0 %; R12 within 0.5 %; propene +0.7 % and ethylene +1.5 % at the boiling
point (no Mathias-Copeman parameters, standard Peng-Robinson function). Another single-component
model goes in through `RefrigerantFluid.of(name, fluid)` and is rejected when its saturation states
are not in equilibrium, as GERG-2008 is for propane at 41 C.

**Ammonia with a cubic or CPA model.** The database ideal-gas heat capacity of ammonia was 16 to
42 % high until 2026-11-26, and its SRK and Peng-Robinson Mathias-Copeman parameters gave saturation
pressures 70 % low. Both are corrected; ammonia heat duties and discharge temperatures calculated
before that date with SRK, PR or CPA are not comparable.

**Carnot fraction.** Measured the same day at -35 C / 35 C and 0.75 isentropic efficiency:
single-stage propane 0.514, propene 0.518, R134a 0.520, ammonia 0.554; with a geometric-mean flash
economizer 0.611, 0.607, 0.617, 0.579. Use these, not a guess, for `RefrigerationCycle`.

## NitrogenSystem

Generation method sets the specific energy and air-to-product ratio; purity above
99% adds a small energy penalty.

```java
import neqsim.process.util.utilitydesign.NitrogenSystem;

NitrogenSystem n2 = new NitrogenSystem("N2 Generation");
n2.setNitrogenDemandNm3h(500.0);
n2.setPurityPercent(99.5);
n2.setGenerationMethod(NitrogenSystem.GenerationMethod.MEMBRANE); // or PSA, CRYOGENIC
n2.calculate();
double powerKW = n2.getPowerKW();
double airFeed = n2.getAirFeedNm3h();
```

## SteamNetwork

Headers are sorted descending by pressure; the cascade balances each header
top-down and the top header's make-up sets the boiler generation.

```java
import neqsim.process.util.utilitydesign.SteamNetwork;

SteamNetwork net = new SteamNetwork("Steam System");
net.addLevel("HP", 42.0, 253.0);   // name, pressure bara, saturation T °C
net.addLevel("LP", 4.5, 148.0);
net.addDemand("HP", 8000.0);        // kg/h; unknown header throws
net.addDemand("LP", 5000.0);
net.setLocalGeneration("LP", 2000.0);
net.calculate();
double boilerKgh = net.getBoilerGenerationKgh();
```

## UtilitySystemDesigner (flowsheet aggregator)

```java
import neqsim.process.util.utilitydesign.UtilitySystemDesigner;

processSystem.run();
UtilitySystemDesigner u = UtilitySystemDesigner.fromProcessSystem(processSystem).design();
double heating = u.getTotalHeatingDutyKW();
double cooling = u.getTotalCoolingDutyKW();
double cwFlow = u.getCoolingWaterFlowM3h();
// For a ProcessModel: UtilitySystemDesigner.fromProcessModel(plant)
```

## MCP `designUtilities` tool

The MCP server (`neqsim-mcp-server`) exposes `designUtilities` (Tier 2,
Engineering Advanced). Dispatch on `utilityType`:

```json
{ "utilityType": "boiler", "name": "HP Boiler", "boilerEfficiency": 0.85,
  "duties": [{ "name": "Reboiler", "dutyKW": 5000.0 }] }
```

| `utilityType` | Required-ish fields |
|---------------|---------------------|
| `boiler` | `duties` [{name,dutyKW}] or `dutyKW`, `boilerEfficiency`, `fuelLowHeatingValueMJperKg` |
| `deaerator` | `feedwaterFlowKgh`, `feedwaterInletTempC`, `operatingPressureBara` |
| `refrigeration` | `duties` or `dutyKW`, `evaporatorTempC`, `condenserTempC`, `cycleEfficiency` |
| `nitrogen` | `nitrogenDemandNm3h`, `purityPercent`, `generationMethod` (MEMBRANE\|PSA\|CRYOGENIC) |
| `steamNetwork` | `levels` [{name,pressureBara,saturationTempC}], `demands` [{level,demandKgh}], `localGeneration` |

The runner returns the class `toJson()` wrapped by the standard MCP response
envelope. Backend: `neqsim.mcp.runners.UtilityDesignRunner`.

## Agentic utility optimization

`UtilityCompressionOptimizer` (same package) drives a **real** two-stage
compression flowsheet (feed → `Compressor` → intercooler `Cooler` → `Compressor`)
through the agentic [`AgenticProcessOptimizer`](../neqsim-optimization-and-doe/SKILL.md)
to find the interstage pressure that minimizes total shaft power. It rediscovers
the analytic geometric-mean optimum `sqrt(pIn·pOut)` from the live model.

```java
import neqsim.process.util.utilitydesign.UtilityCompressionOptimizer;

UtilityCompressionOptimizer opt = new UtilityCompressionOptimizer("Instrument Air");
opt.setInletPressureBara(5.0);
opt.setDeliveryPressureBara(60.0);
opt.setSeed(42L).setMaxEvaluations(70);
opt.optimize();
double pMid = opt.getOptimumInterstagePressureBara(); // ≈ sqrt(5*60) = 17.3 bara
double power = opt.getMinTotalPowerKW();
String json = opt.toJson();
```

Single decision variable (interstage pressure), custom objective = summed stage
power, deterministic for a fixed seed, never throws. Use as the template for
wiring any utility flowsheet into a closed optimization loop.

## Gotchas

- All duties are **kW**; negative duties are rejected (boiler, refrigeration).
- `SteamNetwork.addDemand`/`setLocalGeneration` validate the header name exists.
- Refrigeration COP uses absolute temperatures — keep evaporator below condenser.
- `RefrigerationSystem` takes single-component refrigerants and subcritical condensing only: a
  mixture is rejected (mixed refrigerants: `neqsim-lng-liquefaction`), and CO2 must be cascaded.
- `RefrigerationSystem` is solved after the coolers it serves under graph execution. A flowsheet
  with `Recycle` or `Adjuster` units runs in insertion order: add it after those coolers.
- `UtilitySystemDesigner` counts a `RefrigerationSystem` as its condenser duty plus its compressor
  shaft load, and skips the coolers bound to it. Without one in the flowsheet, sub-ambient coolers
  are still booked as cooling water.
- Halocarbons: only R134a and R12 are in the component database.
- The `utilitydesign` classes are screening models: no detailed mechanical, control, or transient design.
- Set the mixing rule and run the flowsheet **before** `UtilitySystemDesigner.fromProcessSystem`.

## Related

- [`neqsim-utilities-specification`](../neqsim-utilities-specification/SKILL.md) — choosing utility levels
- [`neqsim-heat-integration`](../neqsim-heat-integration/SKILL.md) — deriving duties from composite curves
- [`neqsim-power-generation`](../neqsim-power-generation/SKILL.md) — HRSG / steam-turbine network

- [Utilities design report](../neqsim-utilities-design-report/SKILL.md) — reconcile consumers, simultaneous case loads, supply/distribution, utility-loss scenarios and report acceptance using the main process-report workflow.

## Reference cooling water and seawater

Load [neqsim-cooling-water](../neqsim-cooling-water/SKILL.md) for water-cooled
process equipment, seawater utilities, reference IAPWS properties, coolant flow
and pumping. Keep each exchanger side on its own fluid model and distinguish
fixed-salinity bulk utilities from electrolyte chemistry and salt mixing.

## Draw refrigeration and heat-pump diagrams

Run the flowsheet and check the stage flows, warnings, energy balance and absolute
pressures. Use [the plotting helper](scripts/refrigeration_diagrams.py) with a
solved `RefrigerationSystem` for p–h, p–s and T–s, or a solved `LNGHeatExchanger`
for T–Q. Both functions return Matplotlib figures. Use the examples and medium
inputs in [the refrigeration guide](../../../docs/process/equipment/refrigeration.md#plot-the-solved-process).

- Plot physical pressure in bara on log axes. Use mass-specific h and s and one EOS/reference throughout.
- Identify suction, discharge, condenser liquid, valve outlets, and active evaporators. Show each compression stage and its ideal isentropic endpoint. Preserve separate flash or side-load branches. Label lines between state points as schematic.
- For T–Q, use each composite's cumulative duty from the same cold end, keep the reported minimum internal approach and retain any temperature cross. Inspect the rendered axes and legend before exporting.
- For a heat pump specify both source and sink conditions, medium composition, pressure, temperature range and property basis. Report recovered heat, shaft and electrical power, and useful heat delivered per electrical input. Include only recovered condenser duty within the stated heating boundary.
