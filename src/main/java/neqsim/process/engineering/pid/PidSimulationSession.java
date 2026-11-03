package neqsim.process.engineering.pid;

import java.util.Collection;
import neqsim.process.dynamics.DynamicCapabilityReport;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.designcase.EngineeringCaseRunOptions;
import neqsim.process.engineering.designcase.EngineeringCaseRunReport;
import neqsim.process.engineering.designcase.EngineeringCaseRunner;
import neqsim.process.engineering.designcase.EngineeringCaseSet;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.scenario.DynamicSafetyScenario;
import neqsim.process.safety.scenario.DynamicSafetyScenarioResult;
import neqsim.process.safety.scenario.DynamicSafetyScenarioRunner;

/**
 * Runs explicit P&amp;ID scopes against isolated copies of their source simulation.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class PidSimulationSession {
  private final EngineeringProject project;
  private final PidDesignModel model;
  private final PidSimulationBindings bindings;

  /**
   * Connects a P&amp;ID revision to its source simulation and binding evidence.
   *
   * @param project governed project
   * @param model P&amp;ID model
   * @param bindings explicit simulation bindings
   */
  public PidSimulationSession(EngineeringProject project, PidDesignModel model, PidSimulationBindings bindings) {
    if (bindings == null) {
      throw new IllegalArgumentException("bindings must not be null");
    }
    bindings.assertCurrent(project, model);
    this.project = project;
    this.model = model;
    this.bindings = bindings;
  }

  /** @return explicit P&amp;ID-to-simulation binding register */
  public PidSimulationBindings getBindings() {
    return bindings;
  }

  /**
   * Runs steady state on a copy with all currently bound elements as the declared study scope.
   *
   * @return converged isolated process copy
   */
  public ProcessSystem runSteadyState() {
    bindings.assertCurrent(project, model);
    bindings.requireBindings(bindings.getBoundPidTags());
    ProcessSystem working = project.getEngineeringProcessSystem().copy();
    working.run();
    if (!working.solved()) {
      throw new IllegalStateException("P&ID steady-state study did not converge");
    }
    return working;
  }

  /**
   * Executes controlled steady-state design cases using the existing independent-copy runner.
   *
   * @param cases controlled cases and metrics
   * @param options execution options
   * @param pidTags explicitly exercised P&amp;ID tags
   * @return case results and governing envelopes
   */
  public EngineeringCaseRunReport runSteadyStateCases(EngineeringCaseSet cases, EngineeringCaseRunOptions options,
      Collection<String> pidTags) {
    bindings.assertCurrent(project, model);
    bindings.requireBindings(pidTags);
    return EngineeringCaseRunner.run(project.getEngineeringProcessSystem(), cases, options);
  }

  /**
   * Runs an explicitly configured dynamic test with strict capability preflight on its configured process copy.
   *
   * <p>
   * Factories and criterion extractors should resolve their P&amp;ID tags through this session's binding register using
   * the isolated process supplied by the existing scenario runner. Unbound objects outside the declared test scope
   * remain visible in the register and are never counted as tested. The scenario must declare the actual simulation
   * names for all exercised P&amp;ID tags so its retained equipment-tag evidence maps back through the saved register.
   * Event-exact execution retains the runner's end-of-step logic scan: for scan-executed actions, an
   * initiating-event-to-safe-state deadline includes the declared control scan as well as actuator stroke time.
   * </p>
   *
   * @param scenario controlled initiating event, logic and measurable acceptance criteria
   * @param pidTags explicitly exercised P&amp;ID tags
   * @return dynamic response evidence, including failed preflight or initialization
   */
  public DynamicSafetyScenarioResult runDynamicScenario(DynamicSafetyScenario scenario, Collection<String> pidTags) {
    bindings.assertCurrent(project, model);
    bindings.requireBindings(pidTags);
    if (scenario == null) {
      throw new IllegalArgumentException("scenario must not be null");
    }
    for (String tag : pidTags) {
      String target = bindings.getSimulationName(tag);
      if (!scenario.getEquipmentTags().contains(target)) {
        throw new IllegalArgumentException(
            "Scenario evidence must declare simulation target " + target + " for exercised P&ID tag " + tag);
      }
    }
    return DynamicSafetyScenarioRunner.runStrict(project.getEngineeringProcessSystem(), scenario);
  }

  /**
   * Reports the current base-model dynamic configuration without promoting it to tested readiness.
   *
   * @return independent dynamic capability and activation audit
   */
  public DynamicCapabilityReport getDynamicCapabilityReport() {
    bindings.assertCurrent(project, model);
    return DynamicCapabilityReport.from(project.getEngineeringProcessSystem());
  }
}
