package neqsim.process.decisionsupport;

import java.io.Serializable;
import java.time.Instant;

/**
 * Immutable audit record linking an operator query to its engineering recommendation.
 *
 * <p>
 * Every recommendation produced by the {@link DecisionSupportEngine} generates an audit record for
 * traceability and reproducibility. Records contain the full query and recommendation JSON, the
 * model state hash, and timing information.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class AuditRecord implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String auditId;
  private final Instant timestamp;
  private final String queryJson;
  private final String recommendationJson;
  private final String modelStateHash;
  private final long simulationDurationMs;
  private final String neqsimVersion;
  private final String workflowId;

  /**
   * Creates an audit record.
   *
   * @param auditId unique audit identifier
   * @param timestamp when the recommendation was produced
   * @param queryJson the full OperatorQuery as JSON
   * @param recommendationJson the full EngineeringRecommendation as JSON
   * @param modelStateHash hash of the ProcessSystem state used
   * @param simulationDurationMs time taken for the simulation in milliseconds
   * @param neqsimVersion the NeqSim version string
   * @param workflowId the workflow that produced the recommendation
   */
  public AuditRecord(String auditId, Instant timestamp, String queryJson, String recommendationJson,
      String modelStateHash, long simulationDurationMs, String neqsimVersion, String workflowId) {
    this.auditId = auditId;
    this.timestamp = timestamp;
    this.queryJson = queryJson;
    this.recommendationJson = recommendationJson;
    this.modelStateHash = modelStateHash;
    this.simulationDurationMs = simulationDurationMs;
    this.neqsimVersion = neqsimVersion;
    this.workflowId = workflowId;
  }

  /**
   * Gets the unique audit identifier.
   *
   * @return the audit ID
   */
  public String getAuditId() {
    return auditId;
  }

  /**
   * Gets the timestamp.
   *
   * @return the timestamp
   */
  public Instant getTimestamp() {
    return timestamp;
  }

  /**
   * Gets the full query as JSON.
   *
   * @return the query JSON
   */
  public String getQueryJson() {
    return queryJson;
  }

  /**
   * Gets the full recommendation as JSON.
   *
   * @return the recommendation JSON
   */
  public String getRecommendationJson() {
    return recommendationJson;
  }

  /**
   * Gets the model state hash.
   *
   * @return the hash string
   */
  public String getModelStateHash() {
    return modelStateHash;
  }

  /**
   * Gets the simulation duration in milliseconds.
   *
   * @return the duration in ms
   */
  public long getSimulationDurationMs() {
    return simulationDurationMs;
  }

  /**
   * Gets the NeqSim version.
   *
   * @return the version string
   */
  public String getNeqsimVersion() {
    return neqsimVersion;
  }

  /**
   * Gets the workflow ID.
   *
   * @return the workflow ID
   */
  public String getWorkflowId() {
    return workflowId;
  }

  @Override
  public String toString() {
    return "AuditRecord{auditId='" + auditId + "', workflow='" + workflowId + "', duration="
        + simulationDurationMs + "ms}";
  }
}
