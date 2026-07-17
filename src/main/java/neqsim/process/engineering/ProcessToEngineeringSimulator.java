package neqsim.process.engineering;

import neqsim.process.engineering.designcase.EngineeringCaseRunOptions;

/** Public entry point for iterative process-to-engineering simulation. */
public final class ProcessToEngineeringSimulator {
  private ProcessToEngineeringSimulator() {
  }

  public static EngineeringSimulationResult run(EngineeringProject project) {
    return run(project, 1);
  }

  public static EngineeringSimulationResult run(EngineeringProject project, int caseParallelism) {
    if (project == null) {
      throw new IllegalArgumentException("project must not be null");
    }
    if (project.getExecutableDesignCases().isEmpty()) {
      throw new IllegalStateException("At least one executable engineering design case is required");
    }
    if (project.getEngineeringDesignModules().isEmpty()) {
      throw new IllegalStateException("At least one engineering design module is required");
    }
    return EngineeringSimulationRunner.run(project,
        EngineeringCaseRunOptions.builder().parallelism(caseParallelism).requireConvergence(true).build());
  }
}
