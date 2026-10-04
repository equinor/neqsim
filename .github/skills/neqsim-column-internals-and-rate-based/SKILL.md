---
name: neqsim-column-internals-and-rate-based
description: "Guides agents in choosing equilibrium-stage versus rate-based packed columns, selecting solvers, sizing trays/packing, and checking hydraulic limits. USE WHEN: a user asks about tray or packing selection, column diameter, flooding, weeping, pressure drop, HETP, mass-transfer rates, or solver choice. Anchors: DistillationColumn, ShortcutDistillationColumn, PackedColumn, RateBasedPackedColumn, ColumnInternalsDesigner, TrayHydraulicsCalculator, and PackingHydraulicsCalculator."
last_verified: "2026-10-03"
---

# Column Internals and Rate-Based Contactors

## When to use this

Use this skill to select and inspect NeqSim's distillation/contacting model class, evaluate tray or packing hydraulics, or decide whether the task needs equilibrium-stage VLE or explicit rate-based mass transfer. This complements `neqsim-distillation-design`: that skill remains the detailed reference for ordinary `DistillationColumn` setup, specification combinations, solver troubleshooting, convergence gates, feed placement and Fenske/Underwood/Gilliland checks. Do not duplicate its full workflow; use this skill for model-class boundaries and internals/hydraulic APIs.

## Class map

| Class | Package | What it does | Verified API surface |
|---|---|---|---|
| `DistillationColumn` | `process.equipment.distillation` | Equilibrium-stage MESH tray column; also supports explicit side draws and liquid pumparound circuits. | `DistillationColumn(String, int, boolean hasReboiler, boolean hasCondenser)`, `addFeedStream(StreamInterface, int)`, `setSideDrawFraction(int, SideDrawPhase, double)`, `getSideDrawStream(int, SideDrawPhase)`, `addSideDrawFlowSpecification(int, SideDrawPhase, double, String)`, `addLiquidPumparound(String, int drawTray, int returnTray, double drawFraction, double temperatureDropK)`, `getPumparounds()`. |
| `ShortcutDistillationColumn` | `process.equipment.distillation` | Fenske–Underwood–Gilliland design estimate for a key-component separation; not a rigorous tray solve. | `ShortcutDistillationColumn(String, StreamInterface)`, `setLightKey(String)`, `setHeavyKey(String)`, `setLightKeyRecoveryDistillate(double)`, `setHeavyKeyRecoveryBottoms(double)`, `setRefluxRatioMultiplier(double)`, `run()`, `getMinimumNumberOfStages()`, `getMinimumRefluxRatio()`, `getActualNumberOfStages()`, `isSolved()`. |
| `PackedColumn` | `process.equipment.distillation` | Equilibrium-stage `DistillationColumn` with packing-derived hydraulic and stage-equivalence results after the VLE solve. | Constructor with gas feed or `(String, double, String, boolean, boolean)`; `setPackedHeight`, `setPackingType`, `setStructuredPacking`, `setDesignFloodFraction`, `getHETP()`, `getTheoreticalStages()`, `getNumberOfTrays()`, `getPercentFlood()`, `getPackingPressureDrop()`, `isHydraulicsCalculated()`. |
| `RateBasedPackedColumn` | `process.equipment.distillation` | Counter-current segmented packing model with interphase component transfer and heat-transfer options; distinct from equilibrium trays. | `RateBasedPackedColumn(String, StreamInterface, StreamInterface)`, `setColumnDiameter`, `setPackedHeight`, `setNumberOfSegments`, `setPackingType`, `setTransferComponents(String...)`, `setMaxIterations`, `setConvergenceTolerance`, `getComponentTransferTotals()`, `getSegmentResults()`, `solved()`. |
| `DistillationColumnMatrixSolver`, `NaphtaliSandholmSolver` | `process.equipment.distillation` | Specialized component-balance matrix warm-start and simultaneous MESH solver. | `DistillationColumnMatrixSolver(DistillationColumn)`, `setMaxIterations`, `setTolerance`, `setDampingFactor`; `NaphtaliSandholmSolver(DistillationColumn)`, `getLastIterations()`, `getLastMassBalanceError()`. |
| `ColumnSpecification` | `process.equipment.distillation` | Typed target specification for column product/location quantities. | Constructors: `(SpecificationType, ProductLocation, double)`, `(SpecificationType, ProductLocation, double, String componentName)`, and `(SpecificationType, ProductLocation, double, String componentName, String targetUnit)`; getters: `getType()`, `getLocation()`, `getTargetValue()`, `getComponentName()`. |
| `ScrubColumn` | `process.equipment.distillation` | Specialized equilibrium column with heavy-key overhead and bottoms-temperature limits. | `ScrubColumn(String, int, boolean, boolean)`, `setHeavyKeyComponent`, `setMaxHeavyKeyInOverhead`, `setMinimumBottomsTemperature(double, String)`. |
| `SimpleTray`, `ReactiveTray`, `VLSolidTray` | `process.equipment.distillation` | Stage mixer/flash implementation, reactive-flash-capable tray, and vapor-liquid-solid tray variant. | Constructors `SimpleTray(String)`, `ReactiveTray(String)`, `VLSolidTray(String)`; `SimpleTray.setUseReactiveFlash(boolean)`, `setHeatInput(double)`, `getGasOutStream()`. |
| `ColumnInternalsDesigner` | `process.equipment.distillation.internals` | Evaluates every tray or a packed bed, finds controlling hydraulics, and sizes/grades internals from a solved column. | `ColumnInternalsDesigner(DistillationColumn)`, `setInternalsType(String)`, `setTraySpacing(double)`, `setDesignFloodFraction(double)`, `setPackingPreset(String)`, `setPackedHeight(double)`, `calculate()`, `getRequiredDiameter()`, `isDesignOk()`, `getMaxPercentFlood()`, `getTotalPressureDrop()`. |
| `TrayHydraulicsCalculator` | `process.equipment.distillation.internals` | Standalone tray flood, weeping, entrainment, downcomer, pressure-drop, efficiency and diameter checks. | `setTrayType(String)`, `setColumnDiameter(double)`, `setTraySpacing(double)`, `setVaporMassFlow(double)`, `setLiquidMassFlow(double)`, `setVaporDensity(double)`, `setLiquidDensity(double)`, `calculate()`, `getPercentFlood()`, `isWeepingOk()`, `isDowncommerBackupOk()`, `getTotalTrayPressureDrop()`. |
| `PackingHydraulicsCalculator` | `process.equipment.distillation.internals` | Standalone packing pressure-drop, capacity, wetting/mass-transfer, HETP and diameter checks. | `setPackingPreset(String)`, `setStructuredPackingPreset(String)`, `setVaporMassFlow(double)`, `setLiquidMassFlow(double)`, `setVaporDensity(double)`, `setLiquidDensity(double)`, `setDesignFloodFraction(double)`, `calculate()`, `sizeColumnDiameter()`, `getPercentFlood()`, `getPressureDropPerMeter()`, `getHETP()`, `getNumberOfTheoreticalStages()`. |
| `PackingSpecification`, `PackingSpecificationLibrary` | `process.equipment.distillation.internals` | Stores packing category/material/geometry; resolves registered presets and aliases. | `PackingSpecificationLibrary.get(String)`, `getOrDefault(String)`, `register(...)`; specification getters include `getSpecificSurfaceArea()`, `getVoidFraction()`, `getPackingFactor()`, and `getCriticalSurfaceTension()`. |
| `PackingFoulingModel`, `GravityDrainageMargin` | `process.equipment.distillation.internals` | Adjusts packing geometry for deposit/void loss and screens liquid drainage head against gas pressure drop. | `PackingFoulingModel.fromVoidLossFraction(...)`, `fromDepositThickness(...)`, `getFouledVoidFraction()`, `getPressureDropRatio()`; `GravityDrainageMargin(double, double, double)`, `canDrain()`, `getMarginPressure()`, `getMarginRatio()`. |
| `DistillationColumnMechanicalDesign` | `process.mechanicaldesign.distillation` | Mechanical-design envelope for column geometry, internals, flooding, duties, material and estimated weights/cost. | Constructor accepts `ProcessEquipmentInterface`; `readDesignSpecifications()`, `calcDesign()`, `getColumnDiameter()`, `getColumnHeight()`, `getTotalPressureDrop()`, `setContactorInternalsType(String)`, `getContactorCapacityResult()`. |
| `ProductBoilingPointDistribution` | `process.equipment.distillation` | Discrete normal-boiling-point diagnostic from a stream, not a test curve. | `from(StreamInterface)`, `getBoilingPointTemperaturesKelvin()`, `getCumulativeMoleFractions()`, `getMeanNormalBoilingPointKelvin()`. |

## Build pattern

Use `RateBasedPackedColumn` only when component transfer rates across packed height matter and both inlet streams are defined. This test-backed example reports positive CO2 transfer for gas-to-liquid absorption. For equilibrium-stage distillation or a packed equilibrium column, use `DistillationColumn` or `PackedColumn` and the setup/convergence guide in `neqsim-distillation-design`.

```java
import neqsim.process.equipment.distillation.RateBasedPackedColumn;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface gasFluid = new SystemSrkEos(313.15, 50.0);
gasFluid.addComponent("methane", 0.90);
gasFluid.addComponent("CO2", 0.10);
gasFluid.setMixingRule("classic");
Stream gasIn = new Stream("gas in", gasFluid);
gasIn.setFlowRate(1000.0, "kg/hr");
gasIn.run();
gasIn.getThermoSystem().initProperties();

SystemInterface liquidFluid = new SystemSrkEos(303.15, 50.0);
liquidFluid.addComponent("water", 1.0);
liquidFluid.addComponent("CO2", 0.0);
liquidFluid.setMixingRule("classic");
Stream liquidIn = new Stream("lean liquid", liquidFluid);
liquidIn.setFlowRate(2000.0, "kg/hr");
liquidIn.run();
liquidIn.getThermoSystem().initProperties();

RateBasedPackedColumn column = new RateBasedPackedColumn("rate-based contactor", gasIn, liquidIn);
column.setColumnDiameter(1.0);
column.setPackedHeight(6.0);
column.setNumberOfSegments(4);
column.setMaxIterations(20);
column.setPackingType("Pall-Ring-50");
column.setTransferComponents("CO2");
column.setMassTransferCorrectionFactor(3.0);
column.setConvergenceTolerance(1.0e-9);
column.run();

double co2TransferMolPerSec = column.getComponentTransferTotals().get("CO2");
double residualMolPerSec = column.getLastConvergenceResidual();
```

Equivalent Python/JVM access pattern:

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
RateBasedPackedColumn = jneqsim.process.equipment.distillation.RateBasedPackedColumn

gas_fluid = SystemSrkEos(313.15, 50.0)
gas_fluid.addComponent("methane", 0.90)
gas_fluid.addComponent("CO2", 0.10)
gas_fluid.setMixingRule("classic")
gas_in = Stream("gas in", gas_fluid)
gas_in.setFlowRate(1000.0, "kg/hr")
gas_in.run()
gas_in.getThermoSystem().initProperties()

liquid_fluid = SystemSrkEos(303.15, 50.0)
liquid_fluid.addComponent("water", 1.0)
liquid_fluid.addComponent("CO2", 0.0)
liquid_fluid.setMixingRule("classic")
liquid_in = Stream("lean liquid", liquid_fluid)
liquid_in.setFlowRate(2000.0, "kg/hr")
liquid_in.run()
liquid_in.getThermoSystem().initProperties()

column = RateBasedPackedColumn("rate-based contactor", gas_in, liquid_in)
column.setColumnDiameter(1.0)
column.setPackedHeight(6.0)
column.setNumberOfSegments(4)
column.setMaxIterations(20)
column.setPackingType("Pall-Ring-50")
column.setTransferComponents("CO2")
column.setMassTransferCorrectionFactor(3.0)
column.setConvergenceTolerance(1.0e-9)
column.run()

co2_transfer_mol_per_sec = column.getComponentTransferTotals().get("CO2")
residual_mol_per_sec = column.getLastConvergenceResidual()
```

## Result extraction

| Result | API | Units / interpretation |
|---|---|---|
| Rate-based status | `solved()`, `getLastConvergenceResidual()`, `getLastIterationCount()` | Boolean, mol/s outlet/profile residual, iteration count. Require solved and residual no larger than configured tolerance. |
| Component transfer | `getComponentTransferTotals()` | Map of component to mol/s; positive means gas-to-liquid absorption, negative means liquid-to-gas stripping. |
| Rate-based profile | `getSegmentResults()` and each `SegmentResult` | Ordered bottom to top. `getGasMolarFlow()` / `getLiquidMolarFlow()` are mol/s; `getWettedArea()` is m2/m3; `getKGa()` / `getKLa()` are 1/s; `getInterfaceTemperatureK()` is K; `getHeatTransferRateW()` is W, positive gas-to-liquid; `getPressureDropPerMeter()` is Pa/m; `getPercentFlood()` is percent. |
| Packed equilibrium column | `PackedColumn.getHETP()`, `getTheoreticalStages()`, `getNumberOfTrays()` | HETP in m; packing-derived equivalent theoretical stages versus actual VLE stage topology. These are not interchangeable. |
| Packing hydraulics | `getPercentFlood()`, `getFloodingVelocity()`, `getPressureDropPerMeter()`, `getTotalPressureDrop()` | Flooding velocity m/s; pressure drop Pa/m and Pa in calculator; `PackedColumn.getPackingPressureDrop("mbar")` accepts Pa/mbar/bar. |
| Tray hydraulics | `getPercentFlood()`, `isWeepingOk()`, `getEntrainment()`, `getDowncommerBackup()`, `getTotalTrayPressureDrop()` | Flooding percent; Boolean checks; entrainment fraction mol/mol; backup height m of clear liquid; total pressure drop Pa. |
| Column-wide sizing | `ColumnInternalsDesigner.getRequiredDiameter()`, `getControllingTrayIndex()`, `getMaxPercentFlood()`, `getTotalPressureDrop()` | Diameter m; controlling tray index; percent flood; total pressure drop Pa. |
| Mechanical design | `getColumnDiameter()`, `getColumnHeight()`, `getTrayPressureDrop()`, `getTotalPressureDrop()`, `getReboilerDuty()`, `getCondenserDuty()` | Diameter/height m; tray pressure drop mbar/tray; total column pressure drop bar; duties kW. |
| Product boiling distribution | `ProductBoilingPointDistribution` getters | Discrete normal boiling-point support in K and cumulative mole fractions; not continuous ASTM D86/D2887 data. |

For a tray column, use `ColumnInternalsDesigner` after a converged column run. For `PackedColumn`, check `isHydraulicsCalculated()` and `isHydraulicsOk()` before trusting its packing metrics; read `getLastHydraulicsError()` if the latest hydraulic calculation failed.

## Gotchas

- **Model-class boundary:** `PackedColumn` subclasses the equilibrium `DistillationColumn`. Its `getTheoreticalStages()` is a post-solve hydraulic height/HETP equivalent; `getNumberOfTrays()` is the stage topology actually solved. Neither is a segmented rate-based mass-transfer result.
- **Rate-based is not a general distillation column:** `RateBasedPackedColumn` is counter-current and segmented, with configured transfer species and mass/heat transfer correlations. It requires gas and liquid inlet streams. It is not a general multicomponent crude tower with a condenser, reboiler, side products, tray equilibrium, or an automatic mass-transfer coefficient fitted to vendor data.
- **Convergence is a result gate:** do not consume outlet composition merely because `run()` returned. Require `solved()`, finite `getLastConvergenceResidual() <= getConvergenceTolerance()`, and component balances. An exhausted fixed-point run throws `IllegalStateException`; tests verify it does not publish the previous profile as a solved new run.
- **Unqualified simultaneous solver path:** the source test for simultaneous-segment enthalpy conservation is disabled as tracked issue 4094. Keep the default fixed-point segment solver for the tested path unless the alternative is independently validated; do not claim its energy closure based on convergence alone.
- **Hydraulic inputs are SI:** standalone calculators take mass flows in kg/s, density kg/m3, viscosity Pa s, surface tension N/m, diffusivity m2/s, geometric dimensions m except tray hole diameter in mm. Their setters are not NeqSim unit-converting setters.
- **Do not trust default/zero metrics:** run `calculate()` on the standalone calculators. For rate-based equipment, `validateSetup()` reports missing gas/liquid feeds, missing fluid systems, nonpositive diameter, negative height, missing segments, or missing packing with specific remediation. For `ColumnInternalsDesigner`, a missing column logs an error and returns; it does not throw. Check design-result validity and freshness.
- **Pressure/temperature conventions:** thermodynamic systems use K and bara unless an overload explicitly names a unit. `DistillationColumn.addLiquidPumparound(...)` takes its temperature drop in K and tray indices are bottom-up; verify tray numbering before mapping a P&ID or reference table.
- **Solver and tear interactions:** the available `DistillationColumn.SolverType` values include `DIRECT_SUBSTITUTION`, `DAMPED_SUBSTITUTION`, `INSIDE_OUT`, `MATRIX_INSIDE_OUT`, `WEGSTEIN`, `SUM_RATES`, `NEWTON`, `NAPHTALI_SANDHOLM`, `MESH_RESIDUAL`, and `AUTO`. `NEWTON` is a tray-temperature accelerator, not a full MESH Newton solve. Active pumparounds require outer return-stream coupling; the column logs and uses `MESH_RESIDUAL` instead of `NAPHTALI_SANDHOLM` when pumparounds are configured.
- **Pumparounds versus side products:** a pumparound draw is an internal recycle, not an external product. Generic `DistillationColumn` side-draw fractions and flow specs are supported, but `SarirAtmosphericSideStripperContactScreen` is only a source-bounded contact screen, not a multistage side stripper.
- **No inferential compliance:** passing a local flood fraction or pressure-drop check is not a vendor guarantee, API/ASME/NORSOK compliance finding, mechanical rating, or validated mass-transfer design.

## Validation / benchmarks

- Use a shortcut Fenske–Underwood–Gilliland result as an independent first estimate of minimum stages/reflux and actual stage demand only for a key-component, approximately constant-relative-volatility split. Then validate with a rigorous equilibrium column; do not compare shortcut results to rate-based packed transfer as though they shared a model basis.
- Preliminary design checks commonly target about 70–85% of flooding for tray operation; packing often uses a lower design fraction such as 65–75%. These are screening bands, not mandatory limits. Check top, bottom, and controlling internal stages rather than only one average condition.
- Inspect tray weeping, entrainment, downcomer backup and pressure drop together. For packing, inspect flooding, wetting, pressure drop per metre, HETP and bed height together; an acceptable flood percentage does not prove adequate separation.
- Use rough HETP ranges only to challenge order of magnitude: random packing often about 0.3–0.6 m, structured packing about 0.2–0.5 m; tray spacing/efficiency depends on service. The calculator tests use broad physical sanity bounds, not a universal performance guarantee. Replace generic values with vendor/test data for final design.
- For rate-based results, check signed component transfer against inlet/outlet material closure, direction of transfer, monotonic trend with packing height under fixed conditions, segment profile finiteness, and energy residual when heat transfer is active. The source tests use relative component-balance tolerances from `1e-5` to `1e-6` for their synthetic cases.
- For `PackedColumn`, confirm the hydraulic result belongs to the latest run and compare its `getTheoreticalStages()` to packed height/HETP, not to the stage count solved by the VLE model. For mechanical design, record the chosen internals preset, capacity factor, diameter override, pressure-drop basis, and vendor source.
- `ProductBoilingPointDistribution` is a discrete mole-basis diagnostic. Use `Standard_ASTM_D86` or its qualified D86/TBP conversion where applicable; no D2887 implementation is present in this package.

## Related skills

- `neqsim-distillation-design` for equilibrium-column setup, solver selection, specification combinations, convergence and conventional design heuristics.
- `neqsim-process-modeling` for flowsheet wiring, stream basis, process assembly and process-level validation.
- `neqsim-api-patterns` for EOS, mixing rules, property initialization and units.
- `neqsim-dynamic-simulation` for transient/control behavior; steady-state internals results do not qualify dynamic performance.
- `neqsim-benchmark-reference-data` for independent benchmarks, tolerance policy and source provenance.
- `neqsim-professional-reporting` for uncertainty, assumptions and design limitations in deliverables.