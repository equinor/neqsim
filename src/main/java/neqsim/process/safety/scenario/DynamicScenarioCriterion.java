package neqsim.process.safety.scenario;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.processmodel.ProcessSystem;

/** Measurable safe-state or response-time criterion for a dynamic process scenario. */
public final class DynamicScenarioCriterion implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Extracts a scalar observation from the isolated process model. */
  public interface Extractor extends Serializable {
    double extract(ProcessSystem process);
  }

  private final String id;
  private final String name;
  private final String unit;
  private final Extractor extractor;
  private final Double lowerLimit;
  private final Double upperLimit;
  private final double deadlineSeconds;
  private final boolean required;

  private DynamicScenarioCriterion(Builder builder) {
    id = requireText(builder.id, "id");
    name = requireText(builder.name, "name");
    unit = requireText(builder.unit, "unit");
    extractor = builder.extractor;
    lowerLimit = builder.lowerLimit;
    upperLimit = builder.upperLimit;
    deadlineSeconds = builder.deadlineSeconds;
    required = builder.required;
  }

  public static Builder builder(String id, String name, String unit, Extractor extractor) {
    return new Builder(id, name, unit, extractor);
  }

  double extract(ProcessSystem process) {
    double value = extractor.extract(process);
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

  public double getDeadlineSeconds() {
    return deadlineSeconds;
  }

  public boolean isRequired() {
    return required;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("name", name);
    result.put("unit", unit);
    result.put("lowerLimit", lowerLimit);
    result.put("upperLimit", upperLimit);
    result.put("deadlineSeconds", Double.valueOf(deadlineSeconds));
    result.put("required", Boolean.valueOf(required));
    return result;
  }

  /** Builder for a dynamic response criterion. */
  public static final class Builder {
    private final String id;
    private final String name;
    private final String unit;
    private final Extractor extractor;
    private Double lowerLimit;
    private Double upperLimit;
    private double deadlineSeconds;
    private boolean required = true;

    private Builder(String id, String name, String unit, Extractor extractor) {
      if (extractor == null) {
        throw new IllegalArgumentException("extractor must not be null");
      }
      this.id = id;
      this.name = name;
      this.unit = unit;
      this.extractor = extractor;
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

    public DynamicScenarioCriterion build() {
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
