package neqsim.process.engineering.verticalslice;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.EngineeringSimulationResult;
import neqsim.process.engineering.ProcessToEngineeringSimulator;
import neqsim.process.engineering.deliverables.EngineeringDeliverableCompiler;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.production.EngineeringAutoConfigurationPolicy;

/** Runs, qualifies and optionally compiles the first production process-to-engineering vertical slice. */
public final class ProductionVerticalSliceSimulator {
  private ProductionVerticalSliceSimulator() {
  }

  public static Result run(EngineeringProject project, EngineeringAutoConfigurationPolicy autoConfigurationPolicy,
      InletCompressionExportSlicePolicy qualificationPolicy, int caseParallelism) {
    ProductionVerticalSlicePreflight.Result preflight = ProductionVerticalSlicePreflight.assess(project,
        qualificationPolicy);
    ProductionVerticalSliceExecutionManifest manifest = ProductionVerticalSliceExecutionManifest.build(project,
        autoConfigurationPolicy, qualificationPolicy, preflight);
    project.recordVerticalSliceExecutionManifest(manifest);
    EngineeringSimulationResult simulation = ProcessToEngineeringSimulator.run(project, autoConfigurationPolicy,
        caseParallelism);
    InletCompressionExportSliceQualification.Result qualification = InletCompressionExportSliceQualification
        .qualify(project, simulation, qualificationPolicy);
    project.recordVerticalSliceQualification(qualification);
    return new Result(preflight, manifest, simulation, qualification, null);
  }

  /**
   * Runs only when controlled definitions pass preflight, preventing expensive partial or misleading calculations.
   */
  public static Result runStrict(EngineeringProject project, EngineeringAutoConfigurationPolicy autoConfigurationPolicy,
      InletCompressionExportSlicePolicy qualificationPolicy, int caseParallelism) {
    ProductionVerticalSlicePreflight.Result preflight = ProductionVerticalSlicePreflight.assess(project,
        qualificationPolicy);
    ProductionVerticalSliceExecutionManifest manifest = ProductionVerticalSliceExecutionManifest.build(project,
        autoConfigurationPolicy, qualificationPolicy, preflight);
    project.recordVerticalSliceExecutionManifest(manifest);
    if (!preflight.isReadyForSimulation()) {
      throw new IllegalStateException("Production vertical-slice preflight failed: " + preflight.getBlockers());
    }
    EngineeringSimulationResult simulation = ProcessToEngineeringSimulator.run(project, autoConfigurationPolicy,
        caseParallelism);
    InletCompressionExportSliceQualification.Result qualification = InletCompressionExportSliceQualification
        .qualify(project, simulation, qualificationPolicy);
    project.recordVerticalSliceQualification(qualification);
    return new Result(preflight, manifest, simulation, qualification, null);
  }

  /** Runs the complete simulator and emits the coordinated package from the same qualified project state. */
  public static Result runAndCompile(EngineeringProject project,
      EngineeringAutoConfigurationPolicy autoConfigurationPolicy, InletCompressionExportSlicePolicy qualificationPolicy,
      int caseParallelism, Path outputDirectory, EngineeringGraph baselineGraph) throws IOException {
    Result run = run(project, autoConfigurationPolicy, qualificationPolicy, caseParallelism);
    EngineeringDeliverableCompiler.CompilationResult compilation = EngineeringDeliverableCompiler.compile(project,
        outputDirectory, baselineGraph);
    return new Result(run.preflight, run.manifest, run.simulation, run.qualification, compilation);
  }

  /** Runs strict preflight, simulation, qualification and coordinated package compilation as one governed operation. */
  public static Result runStrictAndCompile(EngineeringProject project,
      EngineeringAutoConfigurationPolicy autoConfigurationPolicy, InletCompressionExportSlicePolicy qualificationPolicy,
      int caseParallelism, Path outputDirectory, EngineeringGraph baselineGraph) throws IOException {
    Result run = runStrict(project, autoConfigurationPolicy, qualificationPolicy, caseParallelism);
    EngineeringDeliverableCompiler.CompilationResult compilation = EngineeringDeliverableCompiler.compile(project,
        outputDirectory, baselineGraph);
    return new Result(run.preflight, run.manifest, run.simulation, run.qualification, compilation);
  }

  /** Immutable simulation, qualification and optional package-compilation result. */
  public static final class Result {
    private final ProductionVerticalSlicePreflight.Result preflight;
    private final ProductionVerticalSliceExecutionManifest manifest;
    private final EngineeringSimulationResult simulation;
    private final InletCompressionExportSliceQualification.Result qualification;
    private final EngineeringDeliverableCompiler.CompilationResult compilation;

    Result(ProductionVerticalSlicePreflight.Result preflight, ProductionVerticalSliceExecutionManifest manifest,
        EngineeringSimulationResult simulation, InletCompressionExportSliceQualification.Result qualification,
        EngineeringDeliverableCompiler.CompilationResult compilation) {
      this.preflight = preflight;
      this.manifest = manifest;
      this.simulation = simulation;
      this.qualification = qualification;
      this.compilation = compilation;
    }

    public EngineeringSimulationResult getSimulation() {
      return simulation;
    }

    public ProductionVerticalSlicePreflight.Result getPreflight() {
      return preflight;
    }

    public ProductionVerticalSliceExecutionManifest getManifest() {
      return manifest;
    }

    public InletCompressionExportSliceQualification.Result getQualification() {
      return qualification;
    }

    public EngineeringDeliverableCompiler.CompilationResult getCompilation() {
      return compilation;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("preflight", preflight.toMap());
      result.put("executionManifest", manifest.toMap());
      result.put("simulation", simulation.toMap());
      result.put("verticalSliceQualification", qualification.toMap());
      result.put("packageCompiled", Boolean.valueOf(compilation != null));
      result.put("qualifiedForControlledPilot", Boolean.valueOf(qualification.isQualifiedForControlledPilot()));
      result.put("fitnessForConstruction", Boolean.FALSE);
      result.put("engineeringApprovalRequired", Boolean.TRUE);
      return result;
    }
  }
}
