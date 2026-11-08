package neqsim.process.safety.scenario;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Measurable safe-state, sampled-bound or response-time criterion for a dynamic scenario.
 *
 * @author ESOL
 * @version 1.0
 */
public final class DynamicScenarioCriterion implements Serializable {
  private static final long serialVersionUID = 1000L;

  /**
   * Defines whether a limit must be reached once or maintained over a sampled window.
   *
   * @author ESOL
   * @version 1.0
   */
  public enum EvaluationMode {
    /** At least one observation must satisfy the limits by the deadline. */
    REACH_BY_DEADLINE,
    /** Every observation in the closed event-relative window must satisfy the limits. */
    MAINTAIN_BOUNDS
  }

  /** Extracts a scalar observation from the isolated process model. */
  public interface Extractor extends Serializable {
    double extract(ProcessSystem process);
  }

  /** Extracts a scalar observation from an isolated multi-area process model. */
  public interface ProcessModelExtractor extends Serializable {
    double extract(ProcessModel model);
  }

  private final String id;
  private final String name;
  private final String unit;
  private final String extractorDefinitionId;
  private final Extractor extractor;
  private final ProcessModelExtractor processModelExtractor;
  private final DynamicSafetyScenario.ExecutionTarget executionTarget;
  private final Double lowerLimit;
  private final Double upperLimit;
  private final double deadlineSeconds;
  private final boolean required;
  private final EvaluationMode evaluationMode;

  /**
   * Creates a criterion from the validated builder state.
   *
   * @param builder validated definition
   */
  private DynamicScenarioCriterion(Builder builder) {
    id = requireText(builder.id, "id");
    name = requireText(builder.name, "name");
    unit = requireText(builder.unit, "unit");
    extractorDefinitionId = requireText(builder.extractorDefinitionId, "extractorDefinitionId");
    extractor = builder.extractor;
    processModelExtractor = builder.processModelExtractor;
    executionTarget = builder.executionTarget;
    lowerLimit = builder.lowerLimit;
    upperLimit = builder.upperLimit;
    deadlineSeconds = builder.deadlineSeconds;
    required = builder.required;
    evaluationMode = builder.evaluationMode;
  }

  public static Builder builder(String id, String name, String unit, Extractor extractor) {
    return new Builder(id, name, unit, "UNSPECIFIED", extractor);
  }

  /**
   * Creates a criterion builder with an explicit, revision-controlled observation definition.
   *
   * @param id criterion identifier
   * @param name criterion name
   * @param unit observation unit
   * @param extractorDefinitionId stable identifier for the observation implementation and revision
   * @param extractor scalar process observation
   * @return criterion builder
   */
  public static Builder builder(String id, String name, String unit, String extractorDefinitionId,
      Extractor extractor) {
    return new Builder(id, name, unit, extractorDefinitionId, extractor);
  }

  /**
   * Creates a multi-area criterion builder.
   *
   * @param id criterion identifier
   * @param name criterion name
   * @param unit observation unit
   * @param extractor scalar model observation
   * @return criterion builder
   */
  public static Builder processModelBuilder(String id, String name, String unit, ProcessModelExtractor extractor) {
    return new Builder(id, name, unit, "UNSPECIFIED", extractor);
  }

  /**
   * Creates a multi-area criterion builder with an explicit observation definition identity.
   *
   * @param id criterion identifier
   * @param name criterion name
   * @param unit observation unit
   * @param extractorDefinitionId stable identifier for the observation implementation and revision
   * @param extractor scalar model observation
   * @return criterion builder
   */
  public static Builder processModelBuilder(String id, String name, String unit, String extractorDefinitionId,
      ProcessModelExtractor extractor) {
    return new Builder(id, name, unit, extractorDefinitionId, extractor);
  }

  double extract(ProcessSystem process) {
    if (getExecutionTarget() != DynamicSafetyScenario.ExecutionTarget.PROCESS_SYSTEM) {
      throw new IllegalStateException("Criterion " + id + " requires a ProcessModel");
    }
    double value = extractor.extract(process);
    if (!Double.isFinite(value)) {
      throw new IllegalStateException("Criterion " + id + " returned a non-finite value");
    }
    return value;
  }

  double extract(ProcessModel model) {
    if (getExecutionTarget() != DynamicSafetyScenario.ExecutionTarget.PROCESS_MODEL) {
      throw new IllegalStateException("Criterion " + id + " requires a ProcessSystem");
    }
    double value = processModelExtractor.extract(model);
    if (!Double.isFinite(value)) {
      throw new IllegalStateException("Criterion " + id + " returned a non-finite value");
    }
    return value;
  }

  boolean isSatisfied(double value) {
    return (lowerLimit == null || value >= lowerLimit.doubleValue())
        && (upperLimit == null || value <= upperLimit.doubleValue());
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getUnit() {
    return unit;
  }

  /** @return stable identifier for the observation implementation and revision */
  public String getExtractorDefinitionId() {
    return extractorDefinitionId;
  }

  /** @return process container required by this observation */
  public DynamicSafetyScenario.ExecutionTarget getExecutionTarget() {
    return executionTarget == null ? DynamicSafetyScenario.ExecutionTarget.PROCESS_SYSTEM : executionTarget;
  }

  public double getDeadlineSeconds() {
    return deadlineSeconds;
  }

  public boolean isRequired() {
    return required;
  }

  /**
   * Returns the evaluation policy, retaining reach semantics for older serialized definitions.
   *
   * @return configured evaluation mode
   */
  public EvaluationMode getEvaluationMode() {
    return evaluationMode == null ? EvaluationMode.REACH_BY_DEADLINE : evaluationMode;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("name", name);
    result.put("unit", unit);
    result.put("extractorDefinitionId", extractorDefinitionId);
    result.put("executionTarget", getExecutionTarget().name());
    result.put("lowerLimit", lowerLimit);
    result.put("upperLimit", upperLimit);
    result.put("deadlineSeconds", Double.valueOf(deadlineSeconds));
    result.put("required", Boolean.valueOf(required));
    result.put("evaluationMode", getEvaluationMode().name());
    return result;
  }

  /**
   * Builder for a dynamic response criterion.
   *
   * @author ESOL
   * @version 1.0
   */
  public static final class Builder {
    private final String id;
    private final String name;
    private final String unit;
    private final String extractorDefinitionId;
    private final Extractor extractor;
    private final ProcessModelExtractor processModelExtractor;
    private final DynamicSafetyScenario.ExecutionTarget executionTarget;
    private Double lowerLimit;
    private Double upperLimit;
    private double deadlineSeconds;
    private boolean required = true;
    private EvaluationMode evaluationMode = EvaluationMode.REACH_BY_DEADLINE;

    private Builder(String id, String name, String unit, String extractorDefinitionId, Extractor extractor) {
      if (extractor == null) {
        throw new IllegalArgumentException("extractor must not be null");
      }
      this.id = id;
      this.name = name;
      this.unit = unit;
      this.extractorDefinitionId = requireText(extractorDefinitionId, "extractorDefinitionId");
      this.extractor = extractor;
      this.processModelExtractor = null;
      this.executionTarget = DynamicSafetyScenario.ExecutionTarget.PROCESS_SYSTEM;
    }

    private Builder(String id, String name, String unit, String extractorDefinitionId,
        ProcessModelExtractor extractor) {
      if (extractor == null) {
        throw new IllegalArgumentException("extractor must not be null");
      }
      this.id = id;
      this.name = name;
      this.unit = unit;
      this.extractorDefinitionId = requireText(extractorDefinitionId, "extractorDefinitionId");
      this.extractor = null;
      this.processModelExtractor = extractor;
      this.executionTarget = DynamicSafetyScenario.ExecutionTarget.PROCESS_MODEL;
    }

    public Builder acceptanceRange(Double lower, Double upper) {
      if (lower != null && !Double.isFinite(lower.doubleValue())) {
        throw new IllegalArgumentException("lower limit must be finite");
      }
      if (upper != null && !Double.isFinite(upper.doubleValue())) {
        throw new IllegalArgumentException("upper limit must be finite");
      }
      if (lower != null && upper != null && lower.doubleValue() > upper.doubleValue()) {
        throw new IllegalArgumentException("lower limit must not exceed upper limit");
      }
      lowerLimit = lower;
      upperLimit = upper;
      return this;
    }

    public Builder deadlineSeconds(double value) {
      if (!Double.isFinite(value) || value < 0.0) {
        throw new IllegalArgumentException("deadlineSeconds must be finite and non-negative");
      }
      deadlineSeconds = value;
      return this;
    }

    public Builder required(boolean value) {
      required = value;
      return this;
    }

    /**
     * Selects reach-by-deadline or sampled bounds over the closed interval from zero to the deadline.
     *
     * <p>
     * MAINTAIN_BOUNDS requires a positive deadline and observations at both boundaries. It does not interpolate missing
     * boundaries or certify behaviour between physical samples. Use the duration after the initiating event as the
     * deadline to assess the full response horizon.
     * </p>
     *
     * @param value explicit observation policy
     * @return this builder
     * @throws IllegalArgumentException if the mode is null
     */
    public Builder evaluationMode(EvaluationMode value) {
      if (value == null) {
        throw new IllegalArgumentException("evaluationMode must not be null");
      }
      evaluationMode = value;
      return this;
    }

    /**
     * Builds a bounded criterion with an explicit observation policy.
     *
     * @return validated criterion
     * @throws IllegalStateException if bounds are absent or a maintained window is not positive
     */
    public DynamicScenarioCriterion build() {
      if (evaluationMode == EvaluationMode.MAINTAIN_BOUNDS && deadlineSeconds <= 0.0) {
        throw new IllegalStateException("MAINTAIN_BOUNDS requires a positive observation-window deadline");
      }
      if (lowerLimit == null && upperLimit == null) {
        throw new IllegalStateException("at least one acceptance limit is required");
      }
      return new DynamicScenarioCriterion(this);
    }
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
