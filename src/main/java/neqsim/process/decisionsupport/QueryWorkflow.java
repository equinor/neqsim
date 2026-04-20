package neqsim.process.decisionsupport;

import neqsim.process.processmodel.ProcessSystem;

/**
 * Interface for pluggable decision support workflows.
 *
 * <p>
 * Each workflow handles a specific type of operator query (rate change feasibility, gas quality
 * impact, derate options, etc.). The {@link DecisionSupportEngine} dispatches queries to the
 * appropriate registered workflow.
 * </p>
 *
 * <p>
 * Implementations must be stateless — all state is passed via parameters. The engine creates a
 * fresh clone of the ProcessSystem for each invocation.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public interface QueryWorkflow {

  /**
   * Checks whether this workflow can handle the given query.
   *
   * @param query the operator query
   * @return true if this workflow can handle the query
   */
  boolean canHandle(OperatorQuery query);

  /**
   * Executes the workflow against a process model and returns a recommendation.
   *
   * <p>
   * The provided ProcessSystem is a disposable copy — workflows may modify it freely. The operating
   * specification provides the plant-specific limits to check against.
   * </p>
   *
   * @param process a cloned ProcessSystem (may be modified)
   * @param query the operator query to evaluate
   * @param spec the operating specifications and limits to check against
   * @return an engineering recommendation
   */
  EngineeringRecommendation execute(ProcessSystem process, OperatorQuery query,
      OperatingSpecification spec);

  /**
   * Gets the unique workflow identifier.
   *
   * @return the workflow ID
   */
  String getWorkflowId();

  /**
   * Gets a human-readable description of this workflow.
   *
   * @return the description
   */
  String getDescription();
}
