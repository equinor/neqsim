---
name: neqsim-advanced-control-mpc-and-virtual-sensing
description: "Guides agents through ProcessLinkedMPC, ProcessLinearizer, ModelPredictiveController, VirtualFlowMeter, SoftSensor, and DataReconciliationEngine. USE WHEN: identifying a process model for MPC, exporting state-space or industrial controller models, estimating unmetered flow or properties, checking controller structures, detecting steady state, reconciling noisy measurements, or screening controller performance."
last_verified: "2026-10-03"
---

# Advanced Control, MPC, and Virtual Sensing

## When to use this

Use this skill when NeqSim's existing dynamic/process model needs a controller-facing model, bounded receding-horizon control, a physics-based soft measurement, or steady-state data reconciliation.

- `ProcessLinkedMPC` links configured manipulated, controlled, disturbance, and state variables to one existing `ProcessSystem`; it identifies local dynamics and computes/apply moves.
- `ProcessLinearizer`, `StepResponseGenerator`, `StateSpaceExporter`, `IndustrialMPCExporter`, and `SubrModlExporter` create model-identification and integration artifacts.
- `ModelPredictiveController` is a controller-device implementation with a first-order prediction model, constraints, feed-quality prediction, and optional moving-horizon estimation. It is not a DCS emulator.
- `VirtualFlowMeter` and `SoftSensor` estimate quantities/properties from a stream's fluid and supplied operating conditions. They are screening/model estimates, not custody-transfer instruments.
- `SteadyStateDetector` and `DataReconciliationEngine` screen input data and reconcile measured values against declared linear balance constraints.

Keep each layer distinct: dynamic simulation models process response (`neqsim-dynamic-simulation`); controllability/operability maps feasible envelopes (`neqsim-controllability-operability`); calibration and plant-data workflows select and qualify measurements (`neqsim-model-calibration-and-data-reconciliation`, `neqsim-plant-data`). Compressor minimum-speed/recycle coordination belongs to `neqsim-compressor-antisurge-recycle`.

## Class map

| Class or family | Package | What it does | Key methods verified |
|---|---|---|---|
| `ProcessLinkedMPC` | `neqsim.process.mpc` | Binds MVs/CVs/DVs/SVRs to a `ProcessSystem`, identifies its model and performs repeated control calculations. | Constructor `(String, ProcessSystem)`; `addMV`, `addCV`, `addCVZone`, `addDV`, `addSVR`, `identifyModel`, `calculate`, `applyMoves`, `getLastMoves`, `getLinearizationResult`, `exportModel` |
| `ProcessLinearizer`, variable classes | `neqsim.process.mpc` | Computes local finite-difference gains for configured manipulated/controlled/disturbance variables. | `ProcessLinearizer(ProcessSystem)`, `addMV`, `addCV`, `addDV`, `linearize`, `setDefaultPerturbationSize` |
| `StepResponseGenerator`, `StepResponse`, `NonlinearPredictor`, derivative calculator | `neqsim.process.mpc` | Builds step-response models, finite-difference process responses, and optional nonlinear prediction inputs. | `StepResponse` constructor and response getters are documented in `MPCIntegrationTest`; verify a generator method before use |
| State-space and industrial exporters | `neqsim.process.mpc` | Exports local linear model to discrete state-space, Industrial MPC, SubrModl, soft-sensor, and controller data-exchange formats. | `StateSpaceExporter(LinearizationResult)`, `toDiscreteStateSpace`, `getA`, `getB`, `getC`, `getD`; `createIndustrialExporter`, `createSubrModlExporter` |
| `ModelPredictiveController` | `neqsim.process.controllerdevice` | Standalone first-order, receding-horizon controller device with linear quality constraints and feed disturbance handling. | Constructors `()`, `(String)`; `setPredictionHorizon`, `setControllerSetPoint`, `setTransmitter`, `getResponse` |
| Controller benchmarks and metrics/events | `neqsim.process.controllerdevice` | Records controller benchmark scenarios, performance metrics, and events for comparative assessment. | APIs vary by object; inspect source before assuming metric units or benchmark acceptance |
| Logic/sequence/transfer-function blocks | `neqsim.process.controllerdevice` | Provides logic blocks, sequential function charts, and transfer-function structures. | Use their public interfaces and transition/update APIs from source; these are not a plant DCS runtime |
| Cascade, feed-forward, override, ratio, split-range, minimum-speed recycle | `neqsim.process.controllerdevice.structure` | Composes controller outputs for common multiloop, selector, and recycle-coordination patterns. | Minimum-speed integration is detailed in `neqsim-compressor-antisurge-recycle` |
| `VirtualFlowMeter`, `VFMResult`, `UncertaintyBounds` | `neqsim.process.measurementdevice.vfm` | Estimates gas/oil/water rates and simple uncertainty bounds from stream fluid, pressure drop, choke opening, and calibration factor. | `VirtualFlowMeter(String, StreamInterface)`, `calculateFlowRates`, `setMeasurementUncertainties`; `getOilFlowRate`, `getGasFlowRate`, `getWaterFlowRate`, `getQuality`, uncertainty getters |
| `SoftSensor` | `neqsim.process.measurementdevice.vfm` | Estimates supported fluid properties by flashing a cloned stream fluid at supplied P/T. | `SoftSensor(String, StreamInterface, PropertyType)`, `setInput`, `setInputs`, `estimate` |
| `SteadyStateDetector`, `SteadyStateVariable` | `neqsim.process.util.reconciliation` | Tests incoming variable histories with a sliding-window steady-state detector before reconciliation/calibration. | `SteadyStateDetector(int)`, `addVariable`, `updateVariable`, `evaluate` |
| `DataReconciliationEngine`, `ReconciliationVariable`, `ReconciliationResult` | `neqsim.process.util.reconciliation` | Applies weighted least squares to measurement values under caller-defined linear equality balances; reports statistical flags. | `addVariable`, `addConstraint(double[])`, `setGrossErrorThreshold`, `reconcile`; `isConverged`, `getErrorMessage`, `getVariables`, `getChiSquareStatistic`, `getGrossErrors` |

## Build pattern

The example starts from a converged separator/valve process. The local linear model and MPC operate around this configured point; they do not replace transient simulation or a validated control-system design.

```java
import neqsim.process.mpc.ProcessLinkedMPC;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.8);
fluid.addComponent("ethane", 0.1);
fluid.addComponent("propane", 0.05);
fluid.addComponent("n-pentane", 0.05);
fluid.setMixingRule("classic");
Stream feed = new Stream("feed", fluid);
feed.setFlowRate(100.0, "kg/hr");
feed.setTemperature(25.0, "C");
feed.setPressure(50.0, "bara");
ThrottlingValve valve = new ThrottlingValve("inlet_valve", feed);
valve.setOutletPressure(30.0);
Separator separator = new Separator("separator", valve.getOutletStream());
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(valve);
process.add(separator);
process.run();

ProcessLinkedMPC mpc = new ProcessLinkedMPC("separator-pressure", process);
mpc.addMV("inlet_valve", "opening", 0.0, 1.0, 0.1);
mpc.addCV("separator", "pressure", 30.0);
mpc.setConstraint("separator", "pressure", 20.0, 40.0);
mpc.identifyModel(60.0);
mpc.setPredictionHorizon(20);
mpc.setControlHorizon(5);
double[] moves = mpc.calculate();
mpc.applyMoves();
```

Python exposes the same Java class lookup pattern. Use the shared NeqSim Python environment, and check whether the installed bridge requires Java overload disambiguation.

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
ThrottlingValve = jneqsim.process.equipment.valve.ThrottlingValve
Separator = jneqsim.process.equipment.separator.Separator
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
ProcessLinkedMPC = jneqsim.process.mpc.ProcessLinkedMPC

fluid = SystemSrkEos(298.15, 50.0)
fluid.addComponent("methane", 0.8)
fluid.addComponent("ethane", 0.1)
fluid.addComponent("propane", 0.05)
fluid.addComponent("n-pentane", 0.05)
fluid.setMixingRule("classic")
feed = Stream("feed", fluid)
feed.setFlowRate(100.0, "kg/hr")
feed.setTemperature(25.0, "C")
feed.setPressure(50.0, "bara")
valve = ThrottlingValve("inlet_valve", feed)
valve.setOutletPressure(30.0)
separator = Separator("separator", valve.getOutletStream())
process = ProcessSystem()
process.add(feed)
process.add(valve)
process.add(separator)
process.run()

mpc = ProcessLinkedMPC("separator-pressure", process)
mpc.addMV("inlet_valve", "opening", 0.0, 1.0, 0.1)
mpc.addCV("separator", "pressure", 30.0)
mpc.setConstraint("separator", "pressure", 20.0, 40.0)
mpc.identifyModel(60.0)
mpc.setPredictionHorizon(20)
mpc.setControlHorizon(5)
moves = mpc.calculate()
mpc.applyMoves()
```

## Result extraction

| Output | Exact accessor | Units / meaning |
|---|---|---|
| Local gain model | `getLinearizationResult()`, then `getGainMatrix()`, `getGain(cvName, mvName)`, `getMvOperatingPoint()`, `getCvOperatingPoint()` | Gain units are output-unit per input-unit; keep named variable units consistent |
| Control demand | `calculate()` or `getLastMoves()` | `double[]`, one move per configured MV in declaration order; values follow the configured variable bounds/units |
| Process mutation | `applyMoves()` | Applies the last calculated move set to the linked process equipment |
| State-space export | `exportModel().toDiscreteStateSpace(sampleTime)`; model `getSampleTime`, `getA/B/C/D` | Discrete sample time is seconds; matrix states/inputs/outputs inherit model variable definitions |
| VFM phase rates | `VFMResult.getOilFlowRate()`, `getGasFlowRate()`, `getWaterFlowRate()`, `getTotalLiquidFlowRate()` | `Sm3/d` |
| VFM quality/uncertainty | `getQuality()`, `getOilUncertainty()`, `getGasUncertainty()`, `getWaterUncertainty()` | `HIGH`, `NORMAL`, `LOW`, `EXTRAPOLATED`, `INVALID`; uncertainty record carries unit and bounds |
| Soft sensor | `estimate()` | Default unit depends on `PropertyType`: e.g. density `kg/m3`, viscosity `cP`, pressure `bara`, GOR `Sm3/Sm3` |
| Reconciliation | `isConverged`, `getErrorMessage`, `getVariables`, `getConstraintResidualsBefore/After`, `getChiSquareStatistic`, `isGlobalTestPassed`, `getGrossErrors` | Residual arrays follow constraint insertion order; variable values preserve declared input units |

## Gotchas

- Identify the MPC only after the process has a valid solved operating point. `calculate()` before `identifyModel()` throws `IllegalStateException`; `applyMoves()` before a calculation also throws.
- `ProcessLinearizer` perturbs a local model; results can be misleading at hard bounds, discontinuities, phase transitions, or a non-converged base point. Re-check nonlinear behavior over the intended operating envelope.
- MVs/CVs use the property access and unit behavior of the bound equipment variables. Use explicit physical bounds, rate limits, and compatible engineering units; don't assume a universal percent, bara, or temperature conversion.
- `ModelPredictiveController` uses an internal first-order model and analytical constrained move calculation. It does not emulate a DCS, PLC, SIS, communications stack, alarm rationalization, or vendor controller execution environment.
- State-space, Industrial MPC, and SubrModl exporters produce files/configuration for integration; they do not deploy, authenticate, or validate a target controller.
- `VirtualFlowMeter` implements a simplified differential-pressure multiplier (`sqrt(abs(dP))`) with a calibration factor, not a standards-based meter sizing/flow equation. Its output is an estimate; quality `HIGH` is not a custody-transfer approval.
- VFM returns `INVALID` when stream/fluid is absent or a flash fails. Phase-absent rates remain zero; distinguish a physical zero from an unavailable phase/model result.
- `SoftSensor.estimate()` returns `NaN` when stream/fluid is unavailable, the flash fails, or a selected phase property cannot be evaluated. Test `Double.isFinite` and preserve the output unit.
- `DataReconciliationEngine` requires more variables than constraints. Constraints are caller-defined linear rows with coefficients ordered exactly as variables were added; the engine does not derive a process flowsheet balance automatically.
- Reconciliation failure returns a result with `isConverged()==false` and a message such as `No variables added`, `No constraints added`, or `Need more variables ...`; never report default values as reconciled measurements.
- `SteadyStateDetector`'s R-statistic is a data-window screen. A steady-state verdict does not prove tag independence, sensor health, process stability outside the selected window, or calibration validity.

## Validation / benchmarks

- Start from a converged steady state; test local gain signs and magnitudes against independent small positive/negative perturbations and the intended process direction.
- Check response bounds, move limits, control horizon, prediction horizon, sample time, quality constraints, and model-validity region before using moves in a study.
- Compare controller performance with `ControlsBenchmarkSuite` and report `ControllerPerformanceMetrics`; retain event history and failed scenarios. Benchmark completion is not DCS or functional-safety qualification.
- Validate exported matrices by reconstructing the operating-point response and checking dimensions, named-variable ordering, and units. Test imports in the actual target platform before claiming interoperability.
- For VFM, compare against independent well tests across multiple operating points; report calibration range, uncertainty basis, and EXTRAPOLATED/INVALID cases.
- Run steady-state detection on selected, quality-screened signals; reconcile only after the feed/product balance equations, sigmas, degrees of freedom, and gross-error flags have been reviewed.
- For plant-data calibration and historian comparison, follow `neqsim-model-calibration-and-data-reconciliation` and `neqsim-plant-data` rather than folding data acquisition into this controller layer.

## Related skills

- `neqsim-dynamic-simulation`
- `neqsim-compressor-antisurge-recycle`
- `neqsim-model-calibration-and-data-reconciliation`
- `neqsim-controllability-operability`
- `neqsim-optimization-and-doe`
- `neqsim-plant-data`
- `neqsim-pid-process-operations`
- `neqsim-process-safety`