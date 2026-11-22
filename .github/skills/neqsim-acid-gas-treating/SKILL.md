---
name: neqsim-acid-gas-treating
description: "Guides NeqSim agents through acid-gas and contaminant removal with SimpleAmineAbsorber, SimpleAmineRegenerator, SystemKentEisenberg, SystemDesmukhMather, RateBasedAbsorber, MembraneSeparator, and PressureSwingAdsorptionBed. USE WHEN: screening CO2/H2S sweetening, setting up amine absorption and regeneration, comparing reactive amine thermodynamics, estimating H2S scavenger demand, or comparing membrane, adsorption, and staged-column alternatives."
last_verified: "2026-10-03"
---

# Acid-Gas Treating with NeqSim

## When to use this

- Build a screening acid-gas absorber/regenerator using the `SimpleAmine*` equipment.
- Evaluate CO2/H2S phase equilibrium with Kent-Eisenberg or Desmukh-Mather systems.
- Compare packed-column transfer calculations, equilibrium-stage columns, and shortcut absorbers.
- Screen an H2S scavenger injection rate or a membrane/PSA component-removal alternative.
- Inspect an adsorption bed, PSA cascade, or cyclic adsorption controller.
- Verify component balances, acid-gas loading, H2 purity/recovery, and absorber/regenerator outputs.

The equipment names do not all imply the same fidelity. `SimpleAmineAbsorber` applies configured
removal efficiencies and transfers removed acid gas to a cloned solvent stream; it is not a reactive
amine equilibrium or mass-transfer solver. `SimpleAmineRegenerator` uses simplified loading and duty
balances. `RateBasedAbsorber` has Onda and Billet-Schultes transfer correlations, but requires
credible packing, fluid, and calibration inputs. `MembraneSeparator` and `PressureSwingAdsorptionBed`
are simplified separation models. Use `AbsorptionColumn`/`StrippingColumn` or reactive thermo systems
when their different physical basis fits the question, and state remaining validation gaps.

## Class map

| Class | Package | What it does | Verified key setters/getters |
|---|---|---|---|
| `SimpleAmineAbsorber` | `neqsim.process.equipment.absorber` | Efficiency-based CO2/H2S removal and solvent loading estimate | `setLeanAmineInStream(StreamInterface)`, `setCO2RemovalEfficiency(double)`, `getSweetGasOutStream()`, `getRichAmineLoading()` |
| `SimpleAmineRegenerator` | same | Simplified rich-solvent stripping and duty estimate | `setRichAmineInStream(StreamInterface)`, `setLeanLoadingTarget(double)`, `getLeanLoading()`, `getReboilerDutyKW()` |
| `AbsorptionColumn` | same | Tray/stage absorber based on `DistillationColumn` | `AbsorptionColumn(String,int)`, `addGasInStream(StreamInterface)`, `addSolventInStream(StreamInterface)`, `getGasLoadFactor()` |
| `StrippingColumn` | same | Staged stripping column | `StrippingColumn(String,int)`, `addStrippingGasStream(StreamInterface)`, `addRichLiquidStream(StreamInterface)`, `getOverheadGasStream()` |
| `RateBasedAbsorber` | same | Packed-column rate-transfer model | `setColumnDiameter(double)`, `setPackedHeight(double)`, `setMassTransferModel(MassTransferModel)`, `getHeightOfTransferUnit()` |
| `H2SScavenger` | same | Empirical scavenger performance/injection screen | `setScavengerType(ScavengerType)`, `setScavengerInjectionRate(double,String)`, `calculateRequiredInjectionRate()` |
| `SimpleTEGAbsorber` | same | Gas dehydration contactor | `addGasInStream(StreamInterface)`, `addSolventInStream(StreamInterface)`, `getGasOutStream()`, `getSolventOutStream()` |
| `WaterStripperColumn` | same | Water-stripping contactor/column | `addGasInStream(StreamInterface)`, `addSolventInStream(StreamInterface)`, `getGasOutStream()`, `getWaterDewPointTemperature()` |
| `SimpleAbsorber` | same | Shortcut absorber base with convergence diagnostics | `setNumberOfTheoreticalStages(double)`, `getLastRunExitReason()`, `getOutletStream(int)` |
| `SystemKentEisenberg` | `neqsim.thermo.system` | SRK gas/oil plus Kent-Eisenberg aqueous amine/electrolyte phase | `SystemKentEisenberg(double,double)`, `setMixingRule(4)`, `chemicalReactionInit()` |
| `SystemDesmukhMather` | same | SRK gas/oil plus Desmukh-Mather aqueous amine/electrolyte phase | `SystemDesmukhMather(double,double)`, `chemicalReactionInit()`, `createDatabase(true)` |
| `MembraneSeparator` | `neqsim.process.equipment.membrane` | Per-component permeate split or simplified permeability calculation | `setPermeateFraction(String,double)`, `setPermeability(String,double)`, permeate/retentate getters |
| `PressureSwingAdsorptionBed` | `neqsim.process.equipment.adsorber` | Equilibrium-NTU adsorption with H2 recovery target | `setSorbent(SorbentType)`, `setRecoveryTarget(double)`, `getH2Purity()`, `getH2Recovery()` |
| `PSACascade` | same | PSA cascade with configurable equalisation/recovery uplift | `setConfiguration(CascadeConfiguration)`, `setCycleTime(double)`, `getH2Purity()`, `getTailGasStream()` |
| `AdsorptionBed` | same | Isotherm/LDF adsorption bed with spatial and transient state | geometry/material setters, `getAverageLoading(int)`, `getBedUtilization(int)`, `validateSetup()` |
| `SimpleAdsorber` | same | Shortcut stage-efficiency absorber/adsorber base | `setAproachToEquilibrium(double)`, `setNumberOfStages(int)`, `getOutletStream(int)` |
| `AdsorptionCycleController` | same | Schedules adsorption-bed cycle phases | `AdsorptionCycleController(AdsorptionBed)`, `addStep(PhaseStep)`, `setAutoLoop(boolean)`, `getCurrentPhase()` |

The test suite did not demonstrate an amine setup using `SystemElectrolyteCPAstatoil`; do not
describe that system as the validated amine route based on these sources. The verified amine thermo
paths are `SystemKentEisenberg` and `SystemDesmukhMather`.

## Build pattern

This minimal case follows the amine absorber/regenerator test pattern. It runs the equipment
sequentially; it does not close a lean-solvent circulation loop.

```java
import neqsim.process.equipment.absorber.SimpleAmineAbsorber;
import neqsim.process.equipment.absorber.SimpleAmineRegenerator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface gasFluid = new SystemSrkEos(313.15, 2.0);
gasFluid.addComponent("methane", 0.90);
gasFluid.addComponent("CO2", 0.08);
gasFluid.addComponent("H2S", 0.02);
gasFluid.setMixingRule("classic");
Stream sourGas = new Stream("sour gas", gasFluid);
sourGas.setFlowRate(10000.0, "kg/hr");
sourGas.run();

SystemInterface amineFluid = new SystemSrkEos(318.15, 2.0);
amineFluid.addComponent("CO2", 0.009);
amineFluid.addComponent("H2S", 0.001);
amineFluid.addComponent("water", 0.80);
amineFluid.addComponent("MDEA", 0.19);
amineFluid.setMixingRule("classic");
Stream leanAmine = new Stream("lean amine", amineFluid);
leanAmine.setFlowRate(60000.0, "kg/hr");
leanAmine.run();

SimpleAmineAbsorber absorber = new SimpleAmineAbsorber("amine absorber", sourGas);
absorber.setLeanAmineInStream(leanAmine);
absorber.setAmineType("MDEA");
absorber.setCO2RemovalEfficiency(0.90);
absorber.setH2SRemovalEfficiency(0.99);
absorber.run();

SimpleAmineRegenerator regenerator = new SimpleAmineRegenerator("amine regenerator",
    absorber.getRichAmineOutStream());
regenerator.setAmineType("MDEA");
regenerator.setReboilerTemperatureC(120.0);
regenerator.setLeanLoadingTarget(0.01);
regenerator.setRegenerationEfficiency(0.95);
regenerator.run();
double reboilerDutyKW = regenerator.getReboilerDutyKW();
```

Equivalent Python class lookup pattern:

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
SimpleAmineAbsorber = jneqsim.process.equipment.absorber.SimpleAmineAbsorber
SimpleAmineRegenerator = jneqsim.process.equipment.absorber.SimpleAmineRegenerator

gas = SystemSrkEos(313.15, 2.0)
for component, amount in (("methane", 0.90), ("CO2", 0.08), ("H2S", 0.02)):
    gas.addComponent(component, amount)
gas.setMixingRule("classic")
sour_gas = Stream("sour gas", gas)
sour_gas.setFlowRate(10000.0, "kg/hr")
sour_gas.run()

solvent = SystemSrkEos(318.15, 2.0)
for component, amount in (("CO2", 0.009), ("H2S", 0.001), ("water", 0.80), ("MDEA", 0.19)):
    solvent.addComponent(component, amount)
solvent.setMixingRule("classic")
lean = Stream("lean amine", solvent)
lean.setFlowRate(60000.0, "kg/hr")
lean.run()

absorber = SimpleAmineAbsorber("amine absorber", sour_gas)
absorber.setLeanAmineInStream(lean)
absorber.setAmineType("MDEA")
absorber.setCO2RemovalEfficiency(0.90)
absorber.setH2SRemovalEfficiency(0.99)
absorber.run()
regenerator = SimpleAmineRegenerator("amine regenerator", absorber.getRichAmineOutStream())
regenerator.setAmineType("MDEA")
regenerator.setReboilerTemperatureC(120.0)
regenerator.setLeanLoadingTarget(0.01)
regenerator.setRegenerationEfficiency(0.95)
regenerator.run()
reboiler_duty_kw = regenerator.getReboilerDutyKW()
```

For a reactive thermodynamic flash, use the tested system-specific setup rather than the shortcut
absorber. Kent-Eisenberg test setup uses numeric mixing rule `4`; the reactive Desmukh-Mather test
calls `chemicalReactionInit()`, `createDatabase(true)`, then `setMixingRule("classic")` before TP flash.
`SystemElectrolyteCPAstatoil` examples in this test area concern electrolyte/CPA cases, not an
amine-sweetening validation case.

Membrane and PSA starting points:

```java
MembraneSeparator membrane = new MembraneSeparator("CO2 membrane", sourGas);
membrane.setPermeateFraction("CO2", 0.5);
membrane.setDefaultPermeateFraction(0.1);
membrane.run();

PressureSwingAdsorptionBed psa = new PressureSwingAdsorptionBed("H2 PSA", shiftedSyngas);
psa.setRecoveryTarget(0.85);
psa.run();
```

## Result extraction

| Result | Exact getter | Units / interpretation |
|---|---|---|
| Sweet gas / rich solvent | `getSweetGasOutStream()` / `getRichAmineOutStream()` | Stream objects; inspect per-component rates and compositions |
| Absorber rich loading | `getRichAmineLoading()` | mol acid gas/mol amine; loading calculation is simplified |
| Required solvent flow | `getRequiredCirculationRate()` | m3/h |
| Required packed height | `getRequiredPackingHeight()` | m |
| Design checks | `validateDesign()` | Map of check keys to `DesignCheck`; not an external code certification |
| Regenerator loading | `getRichLoading()` / `getLeanLoading()` | mol CO2/mol amine |
| Regenerator duty | `getReboilerDutyKW()` | kW; components also available by desorption/sensible/steam contribution |
| Specific duty | `getSpecificReboilerDutyMJperKgCO2()` | MJ/kg CO2 stripped |
| Rate-based transfer | `getOverallKGa()`, `getOverallKLa()` | 1/s; `getHeightOfTransferUnit()` is m and NTU is dimensionless |
| Scavenger | `getH2SRemovalEfficiency()`, `getH2SRemoved("kg/hr")` | Fraction and kg/h; injection getter accepts supported unit strings |
| Membrane | `getPermeateStream()`, `getRetentateStream()` | Streams; compare component molar rates and total feed/outlet flow |
| PSA | `getH2Purity()`, `getH2Recovery()` | Mole fraction and dimensionless fraction; tail-gas vector in mol/s |
| Adsorption bed | `getAverageLoading(int)`, `getBedUtilization(int)`, `getMassTransferZoneLength(int)` | Component-indexed bed outputs; check class docs for each output basis |

## Gotchas

- `SimpleAmineAbsorber` is efficiency-based bookkeeping. The configured amine type, weight percent,
  loadings, and circulation/packing calculations do not turn it into a reactive equilibrium model.
- The simple absorber adds captured CO2/H2S to the lean-solvent clone only if that component already
  exists in the solvent system. Include the acid-gas components (trace if needed) in the lean solvent
  or the absorber-side material balance will not include those captured moles. The absorber now logs
  a warning when removed CO2/H2S has no matching component in the lean solvent.
- `SimpleAmineRegenerator` reports CO2 loading and duty; it removes H2S separately. Do not read its
  CO2 loading as total acid-gas loading. Duty getters remain zero when no CO2 is stripped or no
  positive molar flow is present.
- For a real lean/rich recycle, use a pressure let-down, cooler, pump, and `Recycle`/converged
  flowsheet. Check recycle convergence and make a component-wise balance; a sequential example is
  not a closed-loop process model.
- Flash exceptions in the simple amine equipment are logged and caught. A populated output stream
  can therefore exist after a failed flash; check phase/composition results and finite properties.
- `RateBasedAbsorberTest` allows KGa, KLa, wetted area, and HTU to be zero. Treat zero, NaN, or
  non-finite transfer metrics as an unusable calculation, not a successful separation.
- `MembraneSeparator` defaults component permeate fractions to zero. Its permeability option uses
  `permeability * area * feed partial pressure`, capped at available component moles; it is a
  simplified split calculation, not a pressure-driven multi-stage membrane design.
- `PressureSwingAdsorptionBed` recovery target is enforced by venting excess product hydrogen to
  tail gas; it is not the result of simulating industrial cycle sequencing. Use `PSACascade` or
  `AdsorptionCycleController` for the corresponding simplified cycle abstractions.
- Use K and bara in thermo constructors. `SimpleAmineRegenerator.setReboilerTemperatureC(double)`
  takes Celsius; column geometry uses m; scavenger contact time uses seconds; PSA tail flows are
  mol/s. Use explicit stream flow units.
- Set the mixing rule before a flash. The tested `SystemSrkEos` workflows use `"classic"`; the
  Kent-Eisenberg test uses numeric `4`. The tested reactive Desmukh-Mather workflow order is
  `chemicalReactionInit()`, `createDatabase(true)`, then `setMixingRule("classic")`, TP flash, and
  `initProperties()` before transport-property reads. Numeric CPA rule `10` belongs to CPA model
  workflows; no amine validation test here uses `SystemElectrolyteCPAstatoil`.
- `AdsorptionBed.validateSetup()` reports required geometry/material errors with remediation, e.g.
  “Bed diameter must be positive” / “Set bed diameter: setBedDiameter(value)” and
  “Adsorbent material not specified” / “Set adsorbent material: setAdsorbentMaterial(name)”.
  Call it and fix errors before running the bed.
- `SimpleAbsorber` exposes the legacy spelling `setAproachToEquilibrium(double)`; the amine-specific
  class instead exposes `setApproachToEquilibrium(double)`.

## Validation / benchmarks

- Require sweet-gas acid-gas component fractions/rates below their sour-feed values and verify the
  captured material appears in rich solvent or the selected removal stream.
- For a regenerator, require rich loading at least lean loading, positive CO2 in acid-gas overhead,
  and positive finite duty when CO2 is stripped. Use the duty model as an estimate, not a vendor
  energy guarantee.
- Amine loading is mol acid gas/mol amine. Rich loading must exceed lean loading; numeric targets
  vary with solvent, circulation, pressure, temperature, and feed composition. Compare against
  measured PVT/plant data or a documented design basis. Reference: Kohl & Nielsen, *Gas Purification*.
- The PSA unit documents a typical industrial H2 recovery target range of 0.75-0.90 depending on
  equalisation steps. Its test uses a 0.85 target, checks purity above 0.85, recovery in 0.80-0.85,
  and total feed = product + tail gas within 1%. These are model regression checks, not proof of a
  specific vendor skid rating.
- For a membrane, verify total molar balance and the intended permeate/retentate enrichment; a
  set fraction is the assumed selectivity, not evidence of measured membrane performance.
- `AbsorptionColumnTest` checks convergence, component balance, and effect of reduced Murphree
  efficiency. `RateBasedAbsorberTest` only requires non-negative transfer metrics; add positive,
  finite-value checks and independent validation for engineering use.
- References for process context: GPSA Engineering Data Book, *Gas Purification* (Kohl & Nielsen),
  and the relevant solvent, packing, membrane, or adsorbent vendor data. No clause-level compliance
  claim is made here.

## Related skills

- `neqsim-process-modeling` — connect treating equipment, utilities, recycle, and downstream units.
- `neqsim-teg-dehydration-modeling` — validated TEG dehydration flowsheet and regeneration loop.
- `neqsim-hydrogen-production` — PSA purification and hydrogen-chain context.
- `neqsim-reaction-engineering` — reaction and equilibrium-model selection.
- `neqsim-api-patterns` — fluid setup, mixing rules, units, and property initialization.
- `neqsim-input-validation` — component, phase, and operating-condition screening.
- `neqsim-troubleshooting` — flash, column, and recycle diagnosis.
- `neqsim-standards-lookup` — standards identification and clause-to-method traceability.
- `neqsim-professional-reporting` — report assumptions, evidence, and limitations.
- `neqsim-sulfur-recovery` — route recovered Claus acid gas to sulfur recovery and tail-gas handling.