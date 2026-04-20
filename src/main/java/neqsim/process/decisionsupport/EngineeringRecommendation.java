package neqsim.process.decisionsupport;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.google.gson.Gson;

/**
 * Structured, auditable engineering recommendation produced by the decision support engine.
 *
 * <p>
 * Contains a verdict (feasible/not feasible), detailed findings, constraint check results,
 * assumptions, limitations, and simulation provenance. Designed for both machine consumption
 * ({@link #toJson()}) and human-readable control room display ({@link #toHumanReadable()}).
 * </p>
 *
 * <pre>
 * EngineeringRecommendation rec =
 *     EngineeringRecommendation.builder().verdict(Verdict.FEASIBLE_WITH_WARNINGS)
 *         .summary("Rate increase to 150 t/hr is possible but compressor K-100 "
 *             + "will operate at 92% of surge limit.")
 *         .addFinding(new Finding("Compressor K-100 surge margin", Severity.WARNING,
 *             "K-100.surgeMargin", 0.08, 0.10, "fraction"))
 *         .confidence(0.85).build();
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class EngineeringRecommendation implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Gson GSON = GsonFactory.instance();

  private final String auditId;
  private final Verdict verdict;
  private final String summary;
  private final List<Finding> findings;
  private final List<ConstraintCheckResult> constraintChecks;
  private final Map<String, String> operatingEnvelopeStatus;
  private final List<DerateOption> derateOptions;
  private final List<String> assumptions;
  private final List<String> limitations;
  private final double confidence;
  private final String modelVersion;
  private final String queryId;
  private final Instant timestamp;

  /**
   * Overall verdict of the engineering recommendation.
   */
  public enum Verdict {
    /** The proposed operation is feasible within all constraints. */
    FEASIBLE,

    /** The proposed operation is feasible but some soft limits may be approached. */
    FEASIBLE_WITH_WARNINGS,

    /** The proposed operation is not feasible — one or more hard constraints are violated. */
    NOT_FEASIBLE,

    /** Insufficient data or model limitations prevent a definitive answer. */
    REQUIRES_FURTHER_ANALYSIS
  }

  /**
   * Severity level for findings.
   */
  public enum Severity {
    /** Informational only. */
    INFO,

    /** Warning — soft limit approached or minor concern. */
    WARNING,

    /** Error — hard constraint violated or significant issue. */
    ERROR,

    /** Critical — safety-related constraint violated. */
    CRITICAL
  }

  /**
   * Private constructor — use {@link Builder} to create instances.
   *
   * @param builder the builder with configured values
   */
  private EngineeringRecommendation(Builder builder) {
    this.auditId = builder.auditId;
    this.verdict = builder.verdict;
    this.summary = builder.summary;
    this.findings = new ArrayList<>(builder.findings);
    this.constraintChecks = new ArrayList<>(builder.constraintChecks);
    this.operatingEnvelopeStatus = new HashMap<>(builder.operatingEnvelopeStatus);
    this.derateOptions = new ArrayList<>(builder.derateOptions);
    this.assumptions = new ArrayList<>(builder.assumptions);
    this.limitations = new ArrayList<>(builder.limitations);
    this.confidence = builder.confidence;
    this.modelVersion = builder.modelVersion;
    this.queryId = builder.queryId;
    this.timestamp = builder.timestamp;
  }

  /**
   * Creates a new builder.
   *
   * @return a new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Serializes this recommendation to JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return GSON.toJson(this);
  }

  /**
   * Produces a short, human-readable summary suitable for control room display.
   *
   * @return formatted text recommendation
   */
  public String toHumanReadable() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== ENGINEERING RECOMMENDATION ===\n");
    sb.append("Verdict: ").append(verdict).append("\n");
    sb.append("Confidence: ").append(String.format("%.0f%%", confidence * 100)).append("\n\n");
    sb.append("Summary:\n").append(summary).append("\n");

    if (!findings.isEmpty()) {
      sb.append("\nFindings:\n");
      for (Finding f : findings) {
        sb.append("  [").append(f.severity).append("] ").append(f.description);
        if (f.variable != null) {
          sb.append(" (").append(f.variable).append(": ").append(String.format("%.4f", f.value))
              .append(" ").append(f.unit).append(", limit: ").append(String.format("%.4f", f.limit))
              .append(")");
        }
        sb.append("\n");
      }
    }

    if (!constraintChecks.isEmpty()) {
      sb.append("\nConstraint Checks:\n");
      for (ConstraintCheckResult cc : constraintChecks) {
        sb.append("  ").append(cc.status == ConstraintStatus.PASS ? "PASS" : cc.status.name())
            .append(" ").append(cc.constraintName);
        if (cc.marginPercent >= 0) {
          sb.append(" (margin: ").append(String.format("%.1f%%", cc.marginPercent)).append(")");
        }
        sb.append("\n");
      }
    }

    if (!derateOptions.isEmpty()) {
      sb.append("\nDerate Options (safest first):\n");
      for (int i = 0; i < derateOptions.size(); i++) {
        DerateOption opt = derateOptions.get(i);
        sb.append("  ").append(i + 1).append(". Rate=").append(String.format("%.1f", opt.flowRate))
            .append(" ").append(opt.flowRateUnit).append(" — margin=")
            .append(String.format("%.1f%%", opt.safetyMarginPercent)).append(" (")
            .append(opt.riskLevel).append(")\n");
      }
    }

    if (!assumptions.isEmpty()) {
      sb.append("\nAssumptions:\n");
      for (String a : assumptions) {
        sb.append("  - ").append(a).append("\n");
      }
    }

    if (!limitations.isEmpty()) {
      sb.append("\nLimitations:\n");
      for (String l : limitations) {
        sb.append("  - ").append(l).append("\n");
      }
    }

    sb.append("\nAudit ID: ").append(auditId).append("\n");
    return sb.toString();
  }

  // ── Getters ──

  /**
   * Gets the unique audit identifier for traceability.
   *
   * @return the audit ID
   */
  public String getAuditId() {
    return auditId;
  }

  /**
   * Gets the overall verdict.
   *
   * @return the verdict
   */
  public Verdict getVerdict() {
    return verdict;
  }

  /**
   * Gets the natural-language summary.
   *
   * @return the summary
   */
  public String getSummary() {
    return summary;
  }

  /**
   * Gets the detailed findings.
   *
   * @return unmodifiable list of findings
   */
  public List<Finding> getFindings() {
    return java.util.Collections.unmodifiableList(findings);
  }

  /**
   * Gets the constraint check results.
   *
   * @return unmodifiable list of constraint checks
   */
  public List<ConstraintCheckResult> getConstraintChecks() {
    return java.util.Collections.unmodifiableList(constraintChecks);
  }

  /**
   * Gets the operating envelope status per equipment.
   *
   * @return unmodifiable map of equipment name to status description
   */
  public Map<String, String> getOperatingEnvelopeStatus() {
    return java.util.Collections.unmodifiableMap(operatingEnvelopeStatus);
  }

  /**
   * Gets the derate options (sorted safest first).
   *
   * @return unmodifiable list of derate options
   */
  public List<DerateOption> getDerateOptions() {
    return java.util.Collections.unmodifiableList(derateOptions);
  }

  /**
   * Gets the assumptions made.
   *
   * @return unmodifiable list of assumptions
   */
  public List<String> getAssumptions() {
    return java.util.Collections.unmodifiableList(assumptions);
  }

  /**
   * Gets the model limitations and caveats.
   *
   * @return unmodifiable list of limitations
   */
  public List<String> getLimitations() {
    return java.util.Collections.unmodifiableList(limitations);
  }

  /**
   * Gets the overall confidence score (0-1).
   *
   * @return the confidence
   */
  public double getConfidence() {
    return confidence;
  }

  /**
   * Gets the model version string.
   *
   * @return the model version
   */
  public String getModelVersion() {
    return modelVersion;
  }

  /**
   * Gets the query ID this recommendation responds to.
   *
   * @return the query ID
   */
  public String getQueryId() {
    return queryId;
  }

  /**
   * Gets the timestamp when this recommendation was created.
   *
   * @return the timestamp
   */
  public Instant getTimestamp() {
    return timestamp;
  }

  // ── Inner model classes ──

  /**
   * A single finding from the engineering analysis.
   */
  public static class Finding implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String description;
    private final Severity severity;
    private final String variable;
    private final double value;
    private final double limit;
    private final String unit;

    /**
     * Creates a finding with full details.
     *
     * @param description human-readable finding text
     * @param severity severity level
     * @param variable the affected variable address
     * @param value the current/predicted value
     * @param limit the constraint limit
     * @param unit the engineering unit
     */
    public Finding(String description, Severity severity, String variable, double value,
        double limit, String unit) {
      this.description = description;
      this.severity = severity;
      this.variable = variable;
      this.value = value;
      this.limit = limit;
      this.unit = unit;
    }

    /**
     * Creates a finding without a specific variable reference.
     *
     * @param description human-readable finding text
     * @param severity severity level
     */
    public Finding(String description, Severity severity) {
      this(description, severity, null, 0.0, 0.0, "");
    }

    /**
     * Gets the finding description.
     *
     * @return the description
     */
    public String getDescription() {
      return description;
    }

    /**
     * Gets the finding severity.
     *
     * @return the severity
     */
    public Severity getSeverity() {
      return severity;
    }

    /**
     * Gets the affected variable address.
     *
     * @return the variable, or null if not specific to a variable
     */
    public String getVariable() {
      return variable;
    }

    /**
     * Gets the current/predicted value.
     *
     * @return the value
     */
    public double getValue() {
      return value;
    }

    /**
     * Gets the constraint limit.
     *
     * @return the limit
     */
    public double getLimit() {
      return limit;
    }

    /**
     * Gets the engineering unit.
     *
     * @return the unit
     */
    public String getUnit() {
      return unit;
    }
  }

  /**
   * Result of checking a single constraint.
   */
  public static class ConstraintCheckResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String constraintName;
    private final ConstraintStatus status;
    private final double marginPercent;
    private final double currentValue;
    private final double limitValue;
    private final String unit;

    /**
     * Creates a constraint check result.
     *
     * @param constraintName the constraint name
     * @param status the status (PASS/WARN/FAIL)
     * @param marginPercent margin to limit as percentage (positive = within limit)
     * @param currentValue the current value
     * @param limitValue the limit value
     * @param unit the engineering unit
     */
    public ConstraintCheckResult(String constraintName, ConstraintStatus status,
        double marginPercent, double currentValue, double limitValue, String unit) {
      this.constraintName = constraintName;
      this.status = status;
      this.marginPercent = marginPercent;
      this.currentValue = currentValue;
      this.limitValue = limitValue;
      this.unit = unit;
    }

    /**
     * Gets the constraint name.
     *
     * @return the constraint name
     */
    public String getConstraintName() {
      return constraintName;
    }

    /**
     * Gets the constraint check status.
     *
     * @return the status
     */
    public ConstraintStatus getStatus() {
      return status;
    }

    /**
     * Gets the margin to limit as percentage.
     *
     * @return the margin percentage (positive = within limit)
     */
    public double getMarginPercent() {
      return marginPercent;
    }

    /**
     * Gets the current value.
     *
     * @return the current value
     */
    public double getCurrentValue() {
      return currentValue;
    }

    /**
     * Gets the limit value.
     *
     * @return the limit value
     */
    public double getLimitValue() {
      return limitValue;
    }

    /**
     * Gets the engineering unit.
     *
     * @return the unit
     */
    public String getUnit() {
      return unit;
    }
  }

  /**
   * Status of a constraint check.
   */
  public enum ConstraintStatus {
    /** Constraint satisfied with adequate margin. */
    PASS,

    /** Constraint satisfied but margin is low. */
    WARN,

    /** Constraint violated. */
    FAIL
  }

  /**
   * A derate option with its safety characteristics.
   */
  public static class DerateOption implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double flowRate;
    private final String flowRateUnit;
    private final double safetyMarginPercent;
    private final String riskLevel;
    private final String limitingEquipment;
    private final Map<String, ConstraintStatus> constraintStatuses;

    /**
     * Creates a derate option.
     *
     * @param flowRate the derated flow rate
     * @param flowRateUnit the flow rate unit
     * @param safetyMarginPercent overall safety margin percentage
     * @param riskLevel risk characterization (e.g., "Low", "Medium", "High")
     * @param limitingEquipment the equipment that limits further increase
     * @param constraintStatuses map of constraint name to status at this rate
     */
    public DerateOption(double flowRate, String flowRateUnit, double safetyMarginPercent,
        String riskLevel, String limitingEquipment,
        Map<String, ConstraintStatus> constraintStatuses) {
      this.flowRate = flowRate;
      this.flowRateUnit = flowRateUnit;
      this.safetyMarginPercent = safetyMarginPercent;
      this.riskLevel = riskLevel;
      this.limitingEquipment = limitingEquipment;
      this.constraintStatuses = new HashMap<>(constraintStatuses);
    }

    /**
     * Gets the derated flow rate.
     *
     * @return the flow rate
     */
    public double getFlowRate() {
      return flowRate;
    }

    /**
     * Gets the flow rate unit.
     *
     * @return the flow rate unit
     */
    public String getFlowRateUnit() {
      return flowRateUnit;
    }

    /**
     * Gets the overall safety margin percentage.
     *
     * @return the safety margin percentage
     */
    public double getSafetyMarginPercent() {
      return safetyMarginPercent;
    }

    /**
     * Gets the risk level characterization.
     *
     * @return the risk level
     */
    public String getRiskLevel() {
      return riskLevel;
    }

    /**
     * Gets the equipment that limits further rate increase.
     *
     * @return the limiting equipment name
     */
    public String getLimitingEquipment() {
      return limitingEquipment;
    }

    /**
     * Gets the constraint status map at this rate.
     *
     * @return unmodifiable map of constraint name to status
     */
    public Map<String, ConstraintStatus> getConstraintStatuses() {
      return java.util.Collections.unmodifiableMap(constraintStatuses);
    }
  }

  /**
   * Builder for constructing {@link EngineeringRecommendation} instances.
   */
  public static class Builder {
    private String auditId = UUID.randomUUID().toString();
    private Verdict verdict = Verdict.REQUIRES_FURTHER_ANALYSIS;
    private String summary = "";
    private final List<Finding> findings = new ArrayList<>();
    private final List<ConstraintCheckResult> constraintChecks = new ArrayList<>();
    private final Map<String, String> operatingEnvelopeStatus = new HashMap<>();
    private final List<DerateOption> derateOptions = new ArrayList<>();
    private final List<String> assumptions = new ArrayList<>();
    private final List<String> limitations = new ArrayList<>();
    private double confidence = 0.0;
    private String modelVersion = "";
    private String queryId = "";
    private Instant timestamp = Instant.now();

    /**
     * Creates a new Builder with default values.
     */
    Builder() {}

    /**
     * Sets the verdict.
     *
     * @param verdict the overall verdict
     * @return this builder
     */
    public Builder verdict(Verdict verdict) {
      this.verdict = verdict;
      return this;
    }

    /**
     * Sets the summary text.
     *
     * @param summary the natural-language summary
     * @return this builder
     */
    public Builder summary(String summary) {
      this.summary = summary;
      return this;
    }

    /**
     * Adds a finding.
     *
     * @param finding the finding to add
     * @return this builder
     */
    public Builder addFinding(Finding finding) {
      this.findings.add(finding);
      return this;
    }

    /**
     * Adds a constraint check result.
     *
     * @param result the constraint check result
     * @return this builder
     */
    public Builder addConstraintCheck(ConstraintCheckResult result) {
      this.constraintChecks.add(result);
      return this;
    }

    /**
     * Adds an operating envelope status entry.
     *
     * @param equipmentName the equipment name
     * @param status the status description
     * @return this builder
     */
    public Builder addOperatingEnvelopeStatus(String equipmentName, String status) {
      this.operatingEnvelopeStatus.put(equipmentName, status);
      return this;
    }

    /**
     * Adds a derate option.
     *
     * @param option the derate option
     * @return this builder
     */
    public Builder addDerateOption(DerateOption option) {
      this.derateOptions.add(option);
      return this;
    }

    /**
     * Adds an assumption.
     *
     * @param assumption the assumption text
     * @return this builder
     */
    public Builder addAssumption(String assumption) {
      this.assumptions.add(assumption);
      return this;
    }

    /**
     * Adds a limitation.
     *
     * @param limitation the limitation text
     * @return this builder
     */
    public Builder addLimitation(String limitation) {
      this.limitations.add(limitation);
      return this;
    }

    /**
     * Sets the confidence score.
     *
     * @param confidence confidence between 0.0 and 1.0
     * @return this builder
     */
    public Builder confidence(double confidence) {
      this.confidence = Math.max(0.0, Math.min(1.0, confidence));
      return this;
    }

    /**
     * Sets the model version.
     *
     * @param modelVersion the model version string
     * @return this builder
     */
    public Builder modelVersion(String modelVersion) {
      this.modelVersion = modelVersion;
      return this;
    }

    /**
     * Sets the query ID this recommendation responds to.
     *
     * @param queryId the query ID
     * @return this builder
     */
    public Builder queryId(String queryId) {
      this.queryId = queryId;
      return this;
    }

    /**
     * Sets the audit ID (defaults to a random UUID).
     *
     * @param auditId the audit ID
     * @return this builder
     */
    public Builder auditId(String auditId) {
      this.auditId = auditId;
      return this;
    }

    /**
     * Sets the timestamp (defaults to now).
     *
     * @param timestamp the timestamp
     * @return this builder
     */
    public Builder timestamp(Instant timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    /**
     * Builds the EngineeringRecommendation.
     *
     * @return the constructed recommendation
     */
    public EngineeringRecommendation build() {
      return new EngineeringRecommendation(this);
    }
  }
}
