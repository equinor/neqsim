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
    EngineeringSimulationResult simulation = ProcessToEngineeringSimulator.run(project, autoConfigurationPolicy,
        caseParallelism);
    InletCompressionExportSliceQualification.Result qualification = InletCompressionExportSliceQualification
        .qualify(project, simulation, qualificationPolicy);
    project.recordVerticalSliceQualification(qualification);
    return new Result(simulation, qualification, null);
  }

  /** Runs the complete simulator and emits the coordinated package from the same qualified project state. */
  public static Result runAndCompile(EngineeringProject project,
      EngineeringAutoConfigurationPolicy autoConfigurationPolicy,
      InletCompressionExportSlicePolicy qualificationPolicy, int caseParallelism, Path outputDirectory,
      EngineeringGraph baselineGraph) throws IOException {
    Result run = run(project, autoConfigurationPolicy, qualificationPolicy, caseParallelism);
    EngineeringDeliverableCompiler.CompilationResult compilation = EngineeringDeliverableCompiler.compile(project,
        outputDirectory, baselineGraph);
    return new Result(run.simulation, run.qualification, compilation);
  }

  /** Immutable simulation, qualification and optional package-compilation result. */
  public static final class Result {
    private final EngineeringSimulationResult simulation;
    private final InletCompressionExportSliceQualification.Result qualification;
    private final EngineeringDeliverableCompiler.CompilationResult compilation;

    Result(EngineeringSimulationResult simulation, InletCompressionExportSliceQualification.Result qualification,
        EngineeringDeliverableCompiler.CompilationResult compilation) {
      this.simulation = simulation;
      this.qualification = qualification;
      this.compilation = compilation;
    }

    public EngineeringSimulationResult getSimulation() {
      return simulation;
    }

    public InletCompressionExportSliceQualification.Result getQualification() {
      return qualification;
    }

    public EngineeringDeliverableCompiler.CompilationResult getCompilation() {
      return compilation;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
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
