package neqsim.process.engineering;

import neqsim.process.engineering.designcase.EngineeringCaseRunOptions;
import neqsim.process.engineering.production.EngineeringAutoConfigurationPolicy;
import neqsim.process.engineering.production.EngineeringAutoConfigurator;
import neqsim.process.engineering.production.EngineeringProductionReadinessBasis;

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

  /** Configures explicit discipline modules from project policy and then runs the closed design loop. */
  public static EngineeringSimulationResult run(EngineeringProject project, EngineeringAutoConfigurationPolicy policy) {
    return run(project, policy, 1);
  }

  /** Configures explicit discipline modules from project policy and runs cases with controlled parallelism. */
  public static EngineeringSimulationResult run(EngineeringProject project, EngineeringAutoConfigurationPolicy policy,
      int caseParallelism) {
    if (project == null || policy == null) {
      throw new IllegalArgumentException("project and policy must not be null");
    }
    EngineeringAutoConfigurator.Result configuration = EngineeringAutoConfigurator.configure(project, policy);
    EngineeringProductionReadinessBasis basis = project.getProductionReadinessBasis();
    if (basis == null) {
      basis = new EngineeringProductionReadinessBasis();
      project.setProductionReadinessBasis(basis);
    }
    basis.autoConfigurationResult(configuration);
    return run(project, caseParallelism);
  }
}
