---
name: neqsim-utility-design
version: "1.0.0"
description: "Screening-level utility-system DESIGN with NeqSim Java classes and the MCP designUtilities tool — fired steam Boiler, Deaerator, vapour-compression RefrigerationCycle, on-site NitrogenSystem generator, and multi-pressure SteamNetwork header cascade, plus the UtilitySystemDesigner aggregator that harvests demands from a run ProcessSystem/ProcessModel. USE WHEN: a task needs to size a utility duty/flow/power, estimate utility CO2 and operating cost, build a steam header balance, or expose utility sizing through MCP. Pairs with neqsim-utilities-specification (which decides utility LEVELS) and neqsim-heat-integration (grand composite curve drives the duties)."
last_verified: "2026-06-27"
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

These are **screening-level** models for early design, not detailed mechanical design.

## Classes

All classes live in `neqsim.process.util.utilitydesign`, expose
`public static final String SCHEMA_VERSION = "1.0"`, and provide
`calculate()`, `toResultsMap()`, and `toJson()`. Each has a no-arg and a
`String name` constructor.

| Class | Models | Key inputs | Key outputs |
|-------|--------|-----------|-------------|
| `Boiler` | Fired steam boiler package | steam duties (kW), efficiency, fuel LHV | fuel mass, steam generation, feedwater/blowdown, fan power, CO₂, OPEX |
| `Deaerator` | Feedwater deaerator | feedwater flow, inlet T, pressure | deaerator T, heat duty, stripping steam, vent rate |
| `RefrigerationCycle` | Vapour-compression chiller (Carnot × efficiency) | duties (kW), evaporator/condenser T | COP, compressor power, condenser duty, CO₂, OPEX |
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
- These are screening models: no detailed mechanical, control, or transient design.
- Set the mixing rule and run the flowsheet **before** `UtilitySystemDesigner.fromProcessSystem`.

## Related

- [`neqsim-utilities-specification`](../neqsim-utilities-specification/SKILL.md) — choosing utility levels
- [`neqsim-heat-integration`](../neqsim-heat-integration/SKILL.md) — deriving duties from composite curves
- [`neqsim-power-generation`](../neqsim-power-generation/SKILL.md) — HRSG / steam-turbine network
