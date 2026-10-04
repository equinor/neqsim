---
name: neqsim-engineering-design-package
description: "Guides agents through NeqSim EngineeringProject, EngineeringDesignLoop, PidDesignSynthesizer, DexpiEngineeringExporter, EngineeringDeliverableCompiler, and EngineeringApprovalLedger. USE WHEN: building a governed engineering design basis, iterating equipment design modules over cases, synthesizing and checking P&IDs, exporting DEXPI or CFIHOS handovers, validating package numerics, assessing change impact, or recording review decisions."
last_verified: "2026-10-03"
---

# Engineering Design Package

## When to use this

Use this skill when the job is to coordinate existing NeqSim process engineering into a traceable design package. It covers engineering design cases and modules, generated P&ID proposals, engineering graph views, export/round-trip checks, numerical-health evidence, revision impact, and approval history.

Run stages in this order:

1. Build or verify a solved `ProcessSystem`, identify the controlled project revision, design basis, evidence, cases, limits, and data gaps.
2. Run the case envelope and the `EngineeringDesignLoop` with only the typed design modules needed for the question.
3. Synthesize P&ID proposals from the approved proposal profile; check references, coverage, and evidence completeness.
4. Export native DEXPI and compatibility artifacts; separately qualify internal structural round-trip and any named external CAE import/export.
5. Compile the coordinated package; inspect schema/topology and numerical-health results rather than treating generated files as proof of fitness.
6. Diff the controlled revisions, propagate impact actions, then build the approval ledger with revision-aware invalidation.

For process topology construction, operating actions, and DEXPI context, use `neqsim-pid-process-operations` and `neqsim-process-modeling`. This skill adds the typed design loop, package coordination, qualification, change, and approval APIs; it does not replace those workflows.

## Class map

| Class or family | Package | What it does | Key methods verified |
|---|---|---|---|
| `EngineeringProject`, `EngineeringDesignBasis`, `NorsokOffshoreEngineeringBuilder` | `neqsim.process.engineering` | Binds the runnable process, requirements, standards, evidence, and revision into a governed project. | `from`, `build`, `setRevision`, `validate`, `getEngineeringProcessSystem` |
| `EngineeringDesignLoop`, `EngineeringDesignLoopOptions`, `EngineeringDesignLoopResult` | `neqsim.process.engineering.design` | Executes case runs and module updates until design/process values and constraints converge or a fail reason occurs. | `run`, `builder`, `maximumIterations`, `isConverged`, `getTerminationReason`, `getIterations`, `getDesignedProcess` |
| Variables, constraints, dependency graph, convergence report, candidates | `neqsim.process.engineering.design` | Carries unit-bearing design values, candidate choices, dependency order, conflict decisions, and per-iteration convergence evidence. | `getKey`, `getUnit`, `getSelectedValue`, `getConvergenceReport`, `getOrderedModuleIds` |
| Fifteen typed design modules: separator, line/network, valve, compressor/pump, exchanger, instrument, inventory, materials, mechanical, safety, relief, rated capacity | `neqsim.process.engineering.design.modules` | Evaluate typed discipline design updates and constraints against governing case metrics. | `EngineeringDesignModule.evaluate`; `SeparatorProcessDesignModule` constructor; `RatedCapacityDesignModule` constructor |
| P&ID basis/model/synthesis, rule catalogs, tag allocator | `neqsim.process.engineering.pid` | Generates deterministic control/safeguarding/instrument proposals and stable tags from process equipment and a proposal basis. | `PidDesignSynthesizer.synthesize`, `PidDesignModel.getElements`, `getElementsByType`, `PidTagAllocator.allocate`, `reserve` |
| P&ID completeness, HAZOP handoff, and materialization/export | `neqsim.process.engineering.pid` | Checks proposal references/coverage and writes design model, completeness, HAZOP, and DEXPI sidecars. | `PidCompletenessValidator.validate`, `PidEngineeringPackageExporter.export` |
| Canonical engineering graph, calculation DAG, graph diff, diagram/stream/balance tables and graphical projections | `neqsim.process.engineering.model` | Represents process, calculations, provenance, diagram tables and revision differences independently of XML rendering. | `EngineeringGraphDiff.getImpactedNodeIds`; graph API calls are deliberately kept behind the compiler in the sample |
| DEXPI engineering export/materialization/validation and round-trip qualifier | `neqsim.process.engineering.dexpi` | Writes native DEXPI 2.0 plus Proteus/pyDEXPI compatibility and engineering sidecars; performs internal structural checks. | `DexpiEngineeringExporter.export`, `refreshPackageManifest`, `EngineeringDexpiRoundTripQualifier.qualify` |
| CFIHOS 2.0 staging handover | `neqsim.process.engineering.handover` | Maps canonical graph nodes/properties/documents to a project-controlled reference-data mapping and emits gap findings. | `Cfihos20HandoverExporter.export`; result: `getManifestFile`, `getAssessmentFile`, `getUnmappedFile`, `getReport` |
| Package/schema/topology validation | `neqsim.process.engineering.validation` | Validates generated engineering artifacts and topology against the schema catalog. | Validation is invoked by the compiler/export gates; do not infer a successful external import from schema validation |
| Numerical health | `neqsim.process.engineering.numerics` | Reports process convergence, mass closure, supplied energy closure, equation residuals, and supplied Jacobian health. | `analyze`, `addEnergyClosure`, `addEquationResidual`, `sensitivityJacobian` |
| Compiler, discipline packages, automation plan, approval ledger | `neqsim.process.engineering.deliverables` | Writes coordinated package artifacts, reports, and revision-aware approval state. | `EngineeringDeliverableCompiler.compile`; result path getters; `EngineeringApprovalLedger.build` |
| Generalized impact and lifecycle journal | `neqsim.process.engineering.impact`; `neqsim.process.processmodel.lifecycle.event` | Propagates graph change events to recalculate, regenerate, revalidate, review, or reapprove affected objects; journals model changes. | `GeneralizedImpactAnalyzer.analyze`; event-subject analysis and changed-node analysis |
| Production vertical slice and preflight | `neqsim.process.engineering.verticalslice` | Runs controlled inlet/compression/export scenarios with explicit policy and qualification gates. | `ProductionVerticalSliceSimulator.runStrictAndCompile`; run `ProductionVerticalSlicePreflight` first |
| Typed calculation kernel families | `neqsim.process.engineering.calculation` | Kernel families cover DNV pipeline/pipe-soil/global buckling, API pump, ISO metering, NORSOK corrosion, equipment/mechanical, valve/instrument, relief, safety, and materials calculations. | Select the specific typed kernel and inspect its assessment/context contract before use |
| Production qualification and safety lifecycle families | `neqsim.process.engineering.production`; `neqsim.process.engineering.safety.lifecycle` | Method applicability/benchmark qualification, production-readiness and external-evidence gates; HAZOP/LOPA/SRS workflow and change revalidation. | `EngineeringMethodQualificationRegistry`; `HazopLopaSrsWorkflow`; `SafetyStudyRevalidationPlanner` |
| Process topology graph, DEXPI process exchange, lifecycle state | `neqsim.process.processmodel.graph`; `neqsim.process.processmodel.dexpi`; `neqsim.process.processmodel.lifecycle` | Provides process-level graph/XML and lifecycle foundations beneath the engineering-project layer. | Use `Dexpi20XmlWriter` for native process exchange; use the engineering exporter for governed packages |

## Build pattern

The short example executes a case envelope, records a discrete rated-capacity decision, builds a project from the returned process copy, and compiles its package. A real project should replace the illustrative feed metric and capacity with equipment-specific metrics, evidence, and design modules.

```java
import java.nio.file.Paths;
import java.util.Arrays;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.engineering.design.EngineeringDesignLoop;
import neqsim.process.engineering.design.EngineeringDesignLoopOptions;
import neqsim.process.engineering.design.EngineeringDesignModule;
import neqsim.process.engineering.design.EngineeringDesignLoopResult;
import neqsim.process.engineering.design.modules.RatedCapacityDesignModule;
import neqsim.process.engineering.designcase.EngineeringCaseSet;
import neqsim.process.engineering.designcase.EngineeringDesignCase;
import neqsim.process.engineering.designcase.EngineeringMetric;
import neqsim.process.engineering.deliverables.EngineeringDeliverableCompiler;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.1);
fluid.setMixingRule("classic");
Stream feed = new Stream("FEED", fluid);
feed.setFlowRate(1000.0, "kg/hr");
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.run();

EngineeringMetric feedFlow = new EngineeringMetric("FEED.flow", "FEED", "Feed flow", "kg/hr",
    EngineeringMetric.GoverningDirection.MAXIMUM, new EngineeringMetric.Extractor() {
      private static final long serialVersionUID = 1L;
      public double extract(ProcessSystem solved) {
        return ((Stream) solved.getUnit("FEED")).getFlowRate("kg/hr");
      }
    });
EngineeringDesignCase normal = new EngineeringDesignCase("normal", "Normal",
    EngineeringDesignCase.Type.NORMAL, new EngineeringDesignCase.Configurator() {
      private static final long serialVersionUID = 1L;
      public void configure(ProcessSystem solved) { }
    });
EngineeringCaseSet cases = new EngineeringCaseSet("basis").addCase(normal).addMetric(feedFlow);
EngineeringDesignModule rating = new RatedCapacityDesignModule("FEED", "FEED.flow", "ratedCapacity", "kg/hr",
    0.10, new double[] {1200.0, 1500.0});
EngineeringDesignLoopResult design = EngineeringDesignLoop.run(process, cases,
  Arrays.<EngineeringDesignModule>asList(rating),
    EngineeringDesignLoopOptions.builder().maximumIterations(6).build());
if (!design.isConverged()) {
  throw new IllegalStateException(design.getTerminationReason());
}
EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Example design", design.getDesignedProcess())
    .projectId("EXAMPLE-A").build().setRevision("A");
EngineeringDeliverableCompiler.compile(project, Paths.get("engineering-package"));
```

Python uses the same Java classes through the installed `neqsim` bridge. Adapt the case metric and output directory to the actual project. This equivalent minimal project-build/export path intentionally leaves case-loop interface proxies to Python-specific integration code.

```python
from neqsim import jneqsim
from java.nio.file import Paths

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
ProjectBuilder = jneqsim.process.engineering.NorsokOffshoreEngineeringBuilder
Compiler = jneqsim.process.engineering.deliverables.EngineeringDeliverableCompiler

fluid = SystemSrkEos(298.15, 50.0)
fluid.addComponent("methane", 0.9)
fluid.addComponent("ethane", 0.1)
fluid.setMixingRule("classic")
feed = Stream("FEED", fluid)
feed.setFlowRate(1000.0, "kg/hr")
process = ProcessSystem()
process.add(feed)
process.run()
project = getattr(ProjectBuilder, "from")("Example design", process).projectId("EXAMPLE-A").build()
project.setRevision("A")
compiled = Compiler.compile(project, Paths.get("engineering-package"))
print(compiled.getEngineeringGraphFile())
```

In Python/JayDeBeApi variants, Java static method names may be exposed with underscore escaping (for example `from_`); verify the bridge's actual method exposure. Do not silently skip the Java case loop or claim it ran when the Python proxy is not configured.

## Result extraction

| Result | Exact accessor | Units or interpretation |
|---|---|---|
| Loop verdict | `isConverged()`, `getTerminationReason()` | Boolean plus controlled termination code; false is not a usable design convergence |
| Designed process/state | `getDesignedProcess()`, `getState()` | Isolated `ProcessSystem` copy and unit-bearing design state; the base process is not mutated |
| Iteration evidence | `getIterations()`; iteration `getConvergenceReport()` and `getCaseReport()` | Case completion, applied updates, constraints and convergence evidence |
| Selected design | `EngineeringDesignVariable.getSelectedValue()`, `getUnit()`, `getSelectedCandidateId()`, `getGoverningCaseId()` | Value with the update's declared unit and provenance |
| Compiled package | `CompilationResult.getEngineeringGraphFile()`, `getEngineeringCalculationDagFile()`, `getNumericalHealthFile()`, `getValidationReportFile()`, `getEngineeringApprovalLedgerFile()` | Files under the output directory; `getDexpiResult()` exposes XML, interoperability and package-manifest paths |
| DEXPI export | `ExportResult.getDexpi20File()`, `getDexpiFile()`, `getPyDexpiFile()`, `getValidationFile()`, `getInteroperabilityReportFile()` | Native DEXPI 2.0, Proteus graphical P&ID, pyDEXPI-compatible file, and distinct validation reports |
| CFIHOS handover | `Result.getManifestFile()`, `getAssessmentFile()`, `getUnmappedFile()`, `getReport()` | Staging artifacts and explicit mapping/readiness gaps, not target-system acceptance |
| Approval ledger | `EngineeringApprovalLedger.build(project, graph, revisionDiff)` | Effective discipline state; impacted subjects become `REVALIDATION_REQUIRED` |

## Gotchas

- A converged loop is only numerical/design-loop convergence. `EngineeringDesignLoopResult.toMap()` explicitly reports `fitnessForConstruction=false` and `engineeringApprovalRequired=true`.
- The default loop requires a complete case envelope and stable process values. Missing/non-finite metrics can end in `MAXIMUM_ITERATIONS_REACHED`; accepted case limits are a separate option.
- Dependency cycles/missing module dependencies throw `EngineeringDesignDependencyException`. Conflicting same-key proposals fail closed unless units and conflict-resolution policies agree; `REQUIRE_UNIQUE` accepts only equivalent proposals.
- Case and design quantities are not dimensionally converted by the loop. Give every `EngineeringDesignUpdate` one consistent declared unit; candidates with a different unit are rejected.
- P&ID elements are proposals. Completeness validation intentionally adds discipline approval and HAZOP/LOPA review findings; generated trip setpoints, voting, SIL, and valve failure action are not final decisions.
- `DexpiEngineeringExporter` blocks projects with validation errors. Native DEXPI schema/semantic checks, internal identity/reference checks, pyDEXPI import, and a named commercial CAE round-trip are separate gates.
- `Cfihos20HandoverExporter` emits deterministic staging CSVs, JSON assessment, and gaps. Exact RDL mapping approval, Principal transformation, target validation, contractual completeness, and information acceptance remain external.
- Numerical health distinguishes `NOT_AVAILABLE` and `NOT_APPLICABLE`; missing closure evidence is not zero. Add energy closure/residual/Jacobian evidence explicitly where required.
- `EngineeringApprovalLedger` checks subject identity and record supersession; a graph revision impact changes effective state to revalidation required rather than carrying approval forward.
- Do not call the proposal profile “NORSOK compliant.” The builder adds requirements and references; it does not perform the safety lifecycle or issue approval.

## Validation / benchmarks

- Require `design.isConverged()`, inspect its termination code and every iteration/case envelope, and confirm the base process is unchanged where isolation matters.
- Inspect the compiled `engineering-package-validation.json`, topology findings, numerical-health report, unresolved actions, production-readiness assessment, and qualification plan. A clean schema is necessary, not sufficient.
- Run independent, exact-method-version benchmark evidence through the qualification workflow before claiming a qualified calculation. Reference-facility and synthetic examples are software tests, not independent engineering validation.
- For DEXPI, distinguish bundled schema/semantic validation, internal structural reimport, pyDEXPI validation, and named CAE product/version import-export evidence. Keep every report and diff.
- For a changed model, capture a controlled graph revision, calculate `EngineeringGraphDiff`, run impact analysis, regenerate affected artifacts, and rebuild the approval ledger. Preserve unresolved nodes and cycles.
- For safety decisions, hand over to `neqsim-process-safety`; HAZOP/LOPA/SRS and accountable discipline approvals are not substituted by P&ID proposal generation.

## Related skills

- `neqsim-pid-process-operations`
- `neqsim-process-modeling`
- `neqsim-process-safety`
- `neqsim-dynamic-simulation`
- `neqsim-standards-lookup`
- `neqsim-professional-reporting`
- `neqsim-relief-flare-network`
- `neqsim-equipment-cost-estimation`