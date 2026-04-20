package neqsim.process.decisionsupport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Central engine for processing operator queries and producing auditable engineering
 * recommendations.
 *
 * <p>
 * The engine holds a reference to the base {@link ProcessSystem} (the validated steady-state
 * model), a set of registered {@link QueryWorkflow} implementations, and an
 * {@link OperatingSpecification} defining plant-specific limits. Each evaluation creates a fresh
 * clone of the process model, dispatches to the appropriate workflow, and logs the result via the
 * {@link AuditLogger}.
 * </p>
 *
 * <pre>
 * DecisionSupportEngine engine = new DecisionSupportEngine(process);
 * engine.setOperatingSpecification(spec);
 * engine.registerWorkflow(QueryType.RATE_CHANGE_FEASIBILITY, new RateChangeFeasibilityWorkflow());
 * EngineeringRecommendation rec = engine.evaluate(query);
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class DecisionSupportEngine {
  private static final Logger logger = LogManager.getLogger(DecisionSupportEngine.class);

  private final ProcessSystem baseProcess;
  private final Map<OperatorQuery.QueryType, QueryWorkflow> workflows;
  private OperatingSpecification operatingSpecification;
  private AuditLogger auditLogger;
  private String modelVersion;

  /**
   * Creates a decision support engine with the given base process model.
   *
   * @param baseProcess the validated steady-state process model (will be cloned for each query)
   */
  public DecisionSupportEngine(ProcessSystem baseProcess) {
    if (baseProcess == null) {
      throw new IllegalArgumentException("baseProcess must not be null");
    }
    this.baseProcess = baseProcess;
    this.workflows = new HashMap<>();
    this.operatingSpecification = new OperatingSpecification();
    this.auditLogger = new InMemoryAuditLogger();
    this.modelVersion = "unknown";
  }

  /**
   * Registers a workflow for a specific query type.
   *
   * @param queryType the query type this workflow handles
   * @param workflow the workflow implementation
   */
  public void registerWorkflow(OperatorQuery.QueryType queryType, QueryWorkflow workflow) {
    workflows.put(queryType, workflow);
    logger.info("Registered workflow '{}' for query type {}", workflow.getWorkflowId(), queryType);
  }

  /**
   * Evaluates an operator query and returns an auditable engineering recommendation.
   *
   * <p>
   * The evaluation: (1) clones the base process model, (2) dispatches to the appropriate workflow,
   * (3) creates an audit record, and (4) returns the recommendation.
   * </p>
   *
   * @param query the operator query to evaluate
   * @return the engineering recommendation
   */
  public EngineeringRecommendation evaluate(OperatorQuery query) {
    long startTime = System.currentTimeMillis();

    // Find workflow
    QueryWorkflow workflow = findWorkflow(query);
    if (workflow == null) {
      return buildUnsupportedQueryResponse(query);
    }

    // Clone the process model
    ProcessSystem workingCopy;
    try {
      workingCopy = baseProcess.copy();
    } catch (Exception e) {
      logger.error("Failed to clone process model: {}", e.getMessage());
      return buildErrorResponse(query, "Failed to clone process model: " + e.getMessage());
    }

    // Execute workflow
    EngineeringRecommendation recommendation;
    try {
      recommendation = workflow.execute(workingCopy, query, operatingSpecification);
    } catch (Exception e) {
      logger.error("Workflow '{}' failed: {}", workflow.getWorkflowId(), e.getMessage(), e);
      return buildErrorResponse(query,
          "Workflow '" + workflow.getWorkflowId() + "' failed: " + e.getMessage());
    }

    long duration = System.currentTimeMillis() - startTime;

    // Log audit record
    String modelHash = Integer.toHexString(baseProcess.hashCode());
    AuditRecord record = new AuditRecord(recommendation.getAuditId(), Instant.now(), query.toJson(),
        recommendation.toJson(), modelHash, duration, modelVersion, workflow.getWorkflowId());
    auditLogger.log(record);

    logger.info("Query {} evaluated by workflow '{}' in {}ms -> {}", query.getQueryId(),
        workflow.getWorkflowId(), duration, recommendation.getVerdict());

    return recommendation;
  }

  /**
   * Lists all registered workflow descriptions.
   *
   * @return list of workflow descriptions
   */
  public List<String> getAvailableWorkflows() {
    List<String> descriptions = new ArrayList<>();
    for (Map.Entry<OperatorQuery.QueryType, QueryWorkflow> entry : workflows.entrySet()) {
      descriptions.add(entry.getKey() + ": " + entry.getValue().getDescription());
    }
    return descriptions;
  }

  /**
   * Finds the appropriate workflow for a query.
   *
   * @param query the operator query
   * @return the workflow, or null if none found
   */
  private QueryWorkflow findWorkflow(OperatorQuery query) {
    // First try exact type match
    QueryWorkflow workflow = workflows.get(query.getQueryType());
    if (workflow != null) {
      return workflow;
    }

    // Then try canHandle() on all workflows
    for (QueryWorkflow candidate : workflows.values()) {
      if (candidate.canHandle(query)) {
        return candidate;
      }
    }

    return null;
  }

  /**
   * Builds a response for an unsupported query type.
   *
   * @param query the unsupported query
   * @return a recommendation indicating the query type is not supported
   */
  private EngineeringRecommendation buildUnsupportedQueryResponse(OperatorQuery query) {
    return EngineeringRecommendation.builder()
        .verdict(EngineeringRecommendation.Verdict.REQUIRES_FURTHER_ANALYSIS)
        .summary("No workflow registered for query type: " + query.getQueryType()
            + ". Available types: " + workflows.keySet())
        .queryId(query.getQueryId()).confidence(0.0).modelVersion(modelVersion).build();
  }

  /**
   * Builds an error response when a workflow fails.
   *
   * @param query the query that failed
   * @param errorMessage the error message
   * @return a recommendation describing the error
   */
  private EngineeringRecommendation buildErrorResponse(OperatorQuery query, String errorMessage) {
    return EngineeringRecommendation.builder()
        .verdict(EngineeringRecommendation.Verdict.REQUIRES_FURTHER_ANALYSIS)
        .summary("Error: " + errorMessage)
        .addFinding(new EngineeringRecommendation.Finding(errorMessage,
            EngineeringRecommendation.Severity.ERROR))
        .queryId(query.getQueryId()).confidence(0.0).modelVersion(modelVersion).build();
  }

  // ── Configuration setters/getters ──

  /**
   * Sets the operating specification with plant-specific limits.
   *
   * @param spec the operating specification
   */
  public void setOperatingSpecification(OperatingSpecification spec) {
    this.operatingSpecification = spec;
  }

  /**
   * Gets the operating specification.
   *
   * @return the operating specification
   */
  public OperatingSpecification getOperatingSpecification() {
    return operatingSpecification;
  }

  /**
   * Sets the audit logger.
   *
   * @param auditLogger the audit logger implementation
   */
  public void setAuditLogger(AuditLogger auditLogger) {
    this.auditLogger = auditLogger;
  }

  /**
   * Gets the audit logger.
   *
   * @return the audit logger
   */
  public AuditLogger getAuditLogger() {
    return auditLogger;
  }

  /**
   * Sets the model version string for audit trail provenance.
   *
   * @param modelVersion the model version
   */
  public void setModelVersion(String modelVersion) {
    this.modelVersion = modelVersion;
  }

  /**
   * Gets the model version.
   *
   * @return the model version
   */
  public String getModelVersion() {
    return modelVersion;
  }

  /**
   * Gets the base process system (read-only reference).
   *
   * @return the base process system
   */
  public ProcessSystem getBaseProcess() {
    return baseProcess;
  }
}
