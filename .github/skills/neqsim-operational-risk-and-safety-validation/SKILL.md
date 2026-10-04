---
name: neqsim-operational-risk-and-safety-validation
description: "This skill guides agents through NeqSim operational risk simulation and evidence-based safety review. USE WHEN: generating failure scenarios, running transient ESD tests, reviewing open drains or process safety systems, checking P-002/STS0131 gates, or estimating dynamic, portfolio, condition-based, or real-time risk. Anchors: OperationalRiskSimulator, DynamicSafetyScenarioRunner, EmergencyShutdownTestRunner, OpenDrainReviewEngine, ProcessSafetySystemReviewEngine."
last_verified: "2026-10-03"
---

# Operational Risk and Safety Validation

## When to use this

Use this skill for risk simulations and structured evidence reviews that are not the core HAZOP/LOPA/SIL, relief, fire-rupture or consequence calculations. Use the routing table below to avoid repeating existing skill workflows.

## Class map

| Class | Package | What it does | Verified API |
|---|---|---|---|
| `OperationalRiskSimulator`, `OperationalRiskResult` | `neqsim.process.safety.risk` | Monte Carlo equipment failure/repair, production-loss and availability simulation over a process model | `setFeedStreamName(String)`, `setProductStreamName(String)`, `setRandomSeed(long)`, `addEquipmentReliability(String,double,double)`, `runSimulation(int,double)` |
| `DynamicRiskSimulator` | `neqsim.process.safety.risk.dynamic` | Operational-risk simulation with ramp-up/shutdown and transient-loss settings | Constructor `(ProcessSystem)`, `setTimestepHours(double)`, `setSimulateTransients(boolean)`; inherits risk simulation API |
| `PortfolioRiskAnalyzer` | `neqsim.process.safety.risk.portfolio` | Asset/portfolio and common-cause production-risk analysis | `addAsset(String,String,double)`, `runRiskSimulation(int)`, `getLastResult()` |
| `ConditionBasedReliability` | `neqsim.process.safety.risk.condition` | Updates reliability from condition indicators and health contributions | `addIndicator(ConditionIndicator)`, `getProbabilityOfFailure(double)` |
| `PhysicsBasedRiskMonitor`, `RealTimeRiskAssessment` | `neqsim.process.safety.risk.realtime` | Equipment/system condition and live risk assessment records | `getOverallRiskScore()`, `getBottleneckEquipment()`, `addKRI(String,double)` |
| `RiskModel`, `RiskMatrix` | `neqsim.process.safety.risk` | Risk-model and matrix primitives | See `neqsim-process-safety` for HAZOP/LOPA/SIL and matrix workflow |
| `AutomaticScenarioGenerator`, `ProcessSafetyScenario` | `neqsim.process.safety.scenario`, `neqsim.process.safety` | Generates equipment failure deviations and reusable process-safety scenarios | `addFailureModes(FailureMode...)`, `generateSingleFailures()`, `generateCombinations(int)` |
| `DynamicSafetyScenarioRunner`, `DynamicSafetyScenario`, `DynamicScenarioCriterion` | `neqsim.process.safety.scenario` | Applies an event and protection logic to a copied process transient; evaluates criteria | `run(ProcessSystem,DynamicSafetyScenario)`, `isPassed()`, `getFirstSatisfiedSeconds()` |
| `EmergencyShutdownTestRunner`, `EmergencyShutdownTestPlan`, `EmergencyShutdownTestResult` | `neqsim.process.safety.esd` | Runs configured ESD logic sequences and records field/tag samples and criterion verdicts | `run(ProcessSystem,EmergencyShutdownTestPlan,ProcessLogic...)`, `getVerdict()`, `getWarnings()`, `getTimeSeries()` |
| `OpenDrainReviewEngine`, `OpenDrainReviewInput`, `OpenDrainReviewItem` | `neqsim.process.safety.opendrain` | Deterministic evidence review of open-drain capacity, segregation, seals, backflow and related design evidence | `evaluate(OpenDrainReviewInput)`, `evaluateItem(OpenDrainReviewItem,double)` |
| `ProcessSafetySystemReviewEngine`, `S001SecondaryPressureProtectionCriteria` | `neqsim.process.safety.processsafetysystem` | Reviews normalized Clause 10 process-safety-system evidence, including secondary pressure protection | `evaluate(ProcessSafetySystemReviewInput)`, criteria `setMaximumEventPressureBara(...)`, `setDemandFrequencyPerYear(...)` |
| `NorsokP002ComplianceChecker`, `Sts0131Gate`, `StandardsDesignReview` | `neqsim.process.safety.compliance` | Point checks against supplied process design values and a narrow technical-safety gate | `checkFlareLineMach(...)`, `checkBlowdownRhoV2(...)`, `getFindings()`, `addPsvSizingMargin(...)`, `isAcceptable()` |
| `SafetySystemPerformanceAnalyzer`, `PerformanceStandard`, `TR2237Templates` | `neqsim.process.safety.barrier` | Analyzes demand cases, measurement devices, SIFs and barrier performance evidence | `addDemandCase(SafetySystemDemand)`, `addMeasurementDevices(ProcessSystem)` |
| `FireHeatLoadCalculator`, `VesselRuptureCalculator`, `TransientWallHeatTransfer` | `neqsim.process.util.fire` | Fire heat-load and wall/rupture calculations | Fire load/rupture workflows are routed to the existing fire and rupture skills below |

### Routing table

| Question | Class or workflow | Existing skill to load |
|---|---|---|
| What does the 5x5 risk score mean, or how do I run a HAZOP/LOPA/SIL/ETA/FTA? | `RiskMatrix`, `RiskModel`, SIS/ETA/FTA classes | `neqsim-process-safety`, `neqsim-hazid-fmea-eta-fta` |
| How long does detection, logic and valve closure take? | `EsdResponseTimeSimulator` | `neqsim-process-safety` |
| Does an ESD sequence reach the safe state under transient process behavior? | `EmergencyShutdownTestRunner` | This skill; then `neqsim-dynamic-simulation` |
| Which equipment failure modes should be turned into scenarios? | `AutomaticScenarioGenerator` | This skill; HAZOP worksheet framing in `neqsim-process-safety` |
| Does a modeled protection logic sequence meet a time criterion after an initiating event? | `DynamicSafetyScenarioRunner` | This skill; `neqsim-dynamic-simulation` |
| Is the open-drain evidence/capacity basis complete? | `OpenDrainReviewEngine` | This skill; `neqsim-process-safety` for wider safety case |
| Is process safety system evidence complete, including secondary pressure protection? | `ProcessSafetySystemReviewEngine` | This skill; `neqsim-process-safety` for barrier/LOPA/SIL context |
| Does a supplied Mach, rho-v-squared, vent velocity or carryover point pass its configured P-002 limit? | `NorsokP002ComplianceChecker` | This skill; `neqsim-relief-flare-network` for relief/flare sizing |
| Does a narrow set of depressurization, PSV, MDMT, trapped-liquid or SIL checks pass the STS0131 gate? | `Sts0131Gate` | This skill; `neqsim-depressurization-mdmt`, `neqsim-trapped-liquid-fire-rupture`, or `neqsim-process-safety` for the calculation |
| What is the release rate/source term or downstream consequence? | `ReleaseFlowModel`, then fire/dispersion model | `neqsim-release-source-terms`, `neqsim-consequence-analysis` |
| How do I model fire load, vessel failure or trapped liquid? | Fire utilities and rupture model | `neqsim-relief-flare-network`, `neqsim-trapped-liquid-fire-rupture` |

P-002 and STS0131 checkers are scoped gates, not full standards conformity reviews. `NorsokP002ComplianceChecker` explicitly evaluates only values supplied point by point. Confirm governing edition, limit and project basis before use.

## Build pattern

This minimal Monte Carlo example uses a separator/compressor/cooler process like the source regression test. Failure rates are per year; MTTR is hours. Run with a fixed seed for reproducibility.

```java
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.risk.OperationalRiskResult;
import neqsim.process.safety.risk.OperationalRiskSimulator;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface fluid = new SystemSrkEos(280.0, 50.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic");
ProcessSystem process = new ProcessSystem();
Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(5000.0, "kg/hr");
feed.setTemperature(25.0, "C");
feed.setPressure(50.0, "bara");
process.add(feed);
Separator separator = new Separator("Inlet Separator", feed);
process.add(separator);
Compressor compressor = new Compressor("Export Compressor", separator.getGasOutStream());
compressor.setOutletPressure(100.0, "bara");
process.add(compressor);
Cooler cooler = new Cooler("Export Cooler", compressor.getOutletStream());
cooler.setOutTemperature(35.0, "C");
process.add(cooler);
Stream export = new Stream("Export Gas", cooler.getOutletStream());
process.add(export);
process.run();
OperationalRiskResult result = new OperationalRiskSimulator(process)
    .setFeedStreamName("Feed").setProductStreamName("Export Gas").setRandomSeed(12345L)
    .addEquipmentReliability("Export Compressor", 0.05, 24.0).runSimulation(1000, 365.0);
double p50Kg = result.getP50Production();
double meanAvailabilityPercent = result.getMeanAvailability();
```

Equivalent Python class lookup:

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator
Compressor = jneqsim.process.equipment.compressor.Compressor
Cooler = jneqsim.process.equipment.heatexchanger.Cooler
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
OperationalRiskSimulator = jneqsim.process.safety.risk.OperationalRiskSimulator
fluid = SystemSrkEos(280.0, 50.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.setMixingRule("classic")
process = ProcessSystem()
feed = Stream("Feed", fluid)
feed.setFlowRate(5000.0, "kg/hr")
process.add(feed)
separator = Separator("Inlet Separator", feed)
process.add(separator)
compressor = Compressor("Export Compressor", separator.getGasOutStream())
compressor.setOutletPressure(100.0, "bara")
process.add(compressor)
cooler = Cooler("Export Cooler", compressor.getOutletStream())
cooler.setOutTemperature(35.0, "C")
process.add(cooler)
export = Stream("Export Gas", cooler.getOutletStream())
process.add(export)
process.run()
simulator = OperationalRiskSimulator(process)
simulator.setFeedStreamName("Feed")
simulator.setProductStreamName("Export Gas")
simulator.setRandomSeed(12345)
simulator.addEquipmentReliability("Export Compressor", 0.05, 24.0)
result = simulator.runSimulation(1000, 365.0)
p50_kg = result.getP50Production()
availability_percent = result.getMeanAvailability()
```

## Result extraction

| Result | Unit / interpretation |
|---|---|
| `OperationalRiskResult.getP10Production()`, `getP50Production()`, `getP90Production()` | kg produced over the configured horizon; source defines P10 as optimistic and P90 as conservative |
| `getMeanProduction()`, `getMinProduction()`, `getMaxProduction()` | kg over the horizon |
| `getMeanAvailability()`, `getMinAvailability()`, `getMaxAvailability()` | Percent, not a 0-1 fraction |
| `getMeanDowntimeHours()`, `getMaxDowntimeHours()` | Hours over Monte Carlo iterations |
| `DynamicSafetyScenarioResult.isPassed()` / `getFirstSatisfiedSeconds()` | Criterion verdict and first relative time satisfied; null when never satisfied |
| `EmergencyShutdownTestResult.getVerdict()`, `getErrors()`, `getWarnings()`, `getTimeSeries()` | Structured transient evidence and test outcome |
| `OpenDrainReviewReport.getOverallVerdict()` | Evidence-review verdict such as PASS / PASS_WITH_WARNINGS / FAIL |
| `NorsokP002ComplianceChecker.getFindings()` / `isCompliant()` | Supplied point checks only; an empty finding list is not evidence of full compliance |
| `Sts0131Gate.getFindings()` / `isAcceptable()` | Narrow aggregate gate result for recorded checks |

## Gotchas

- `EmergencyShutdownTestRunner.run(...)` operates on the supplied `ProcessSystem`; protect or copy it yourself if the base case must remain unchanged. `DynamicSafetyScenarioRunner.run(...)` explicitly calls `baseProcess.copy()`.
- The ESD runner does not emulate a certified SIS logic solver. Its conclusions cover only the NeqSim logic/equipment and test plan supplied.
- Scenario generation identifies modeled equipment and creates candidate failures. It does not establish initiating-event frequency, independence, safeguards, or credible combinations for a site.
- `OperationalRiskSimulator` uses the caller's failure-rate/MTTR inputs and process impact model. It is not a reliability database; seed the random generator and document data provenance.
- The simulator calls `processSystem.run()` for its baseline. Avoid concurrent use or assume the passed process remains in precisely its prior mutable state.
- Risk-result availability getters are percentages; `ConditionBasedReliability` health contribution is a different metric and must not be substituted directly for equipment availability.
- Open-drain and process-safety-system engines assess normalized evidence. Missing fields can produce warnings or fail findings; they do not retrieve STID or validate drawings themselves.
- P-002 defaults and STS0131 gates are not a complete, edition-controlled conformity assessment. Verify each limit and evidence requirement against the purchased/project-approved source.
- `FireHeatLoadCalculator`, `VesselRuptureCalculator`, `TransientWallHeatTransfer`, relief/flare and dispersion workflows remain in the related fire/consequence skills.

## Validation / benchmarks

Regression tests include `OperationalRiskSimulatorTest`, `DynamicSafetyScenarioRunnerTest`, `EmergencyShutdownTestRunnerTest`, `OpenDrainReviewEngineTest`, `ProcessSafetySystemReviewEngineTest`, `NorsokP002ComplianceCheckerTest`, and `Sts0131GateTest`. Use test cases to verify the API behavior, then validate assumptions with independent site data and reviewed safety requirements. For dynamic studies, retain initialization/convergence errors, time series, criterion thresholds, trigger time and first-satisfied time. For evidence reviews, retain input provenance and every finding.

Verified MCP runner classes include `OpenDrainReviewRunner`, `NorsokS001Clause10ReviewRunner`, and `SafetySystemPerformanceRunner` under `neqsim.mcp.runners`. The checks above do not expose a direct runner for the Java `OperationalRiskSimulator`, `EmergencyShutdownTestRunner`, or `DynamicSafetyScenarioRunner`; do not imply those calculations are available through MCP.

## Related skills

- `neqsim-process-safety` — HAZOP, barrier registers, LOPA/SIL, ESD response-time budget and safety case
- `neqsim-hazid-fmea-eta-fta` — HAZID, FMEA, event tree and fault tree
- `neqsim-relief-flare-network` — API 520/521 relief and flare calculations
- `neqsim-trapped-liquid-fire-rupture` — fire rupture analysis
- `neqsim-consequence-analysis` — dispersion and fire consequences
- `neqsim-depressurization-mdmt` — blowdown and MDMT
- `neqsim-dynamic-simulation` — transient process and controller modeling