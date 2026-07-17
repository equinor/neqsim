package neqsim.process.engineering.design;

import java.io.Serializable;
import java.util.Arrays;
import neqsim.process.processmodel.ProcessSystem;

/** Proposed scalar design update, optionally applied to the iterative process copy. */
public final class EngineeringDesignUpdate implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Applies one selected scalar design value to a process model. */
  public interface Applier extends Serializable {
    void apply(ProcessSystem process, double value);
  }

  private final String key;
  private final double requiredValue;
  private final String unit;
  private final String governingCaseId;
  private final double relativeTolerance;
  private final double[] candidates;
  private final Applier applier;

  private EngineeringDesignUpdate(Builder builder) {
    key = requireText(builder.key, "key");
    if (!Double.isFinite(builder.requiredValue)) {
      throw new IllegalArgumentException("requiredValue must be finite");
    }
    requiredValue = builder.requiredValue;
    unit = requireText(builder.unit, "unit");
    governingCaseId = builder.governingCaseId == null ? "" : builder.governingCaseId.trim();
    if (!Double.isFinite(builder.relativeTolerance) || builder.relativeTolerance < 0.0) {
      throw new IllegalArgumentException("relativeTolerance must be finite and non-negative");
    }
    relativeTolerance = builder.relativeTolerance;
    candidates = builder.candidates == null ? new double[0] : builder.candidates.clone();
    Arrays.sort(candidates);
    applier = builder.applier;
  }

  public static Builder builder(String key, double requiredValue, String unit) {
    return new Builder(key, requiredValue, unit);
  }

  public String getKey() {
    return key;
  }

  public String getUnit() {
    return unit;
  }

  public String getGoverningCaseId() {
    return governingCaseId;
  }

  public double getRelativeTolerance() {
    return relativeTolerance;
  }

  public double selectedValue() {
    if (candidates.length == 0) {
      return requiredValue;
    }
    for (double candidate : candidates) {
      if (candidate >= requiredValue) {
        return candidate;
      }
    }
    throw new IllegalStateException("No configured candidate satisfies " + key + " >= " + requiredValue + " " + unit);
  }

  void apply(ProcessSystem process, double selectedValue) {
    if (applier != null) {
      applier.apply(process, selectedValue);
    }
  }

  public static final class Builder {
    private final String key;
    private final double requiredValue;
    private final String unit;
    private String governingCaseId = "";
    private double relativeTolerance = 1.0e-3;
    private double[] candidates;
    private Applier applier;

    private Builder(String key, double requiredValue, String unit) {
      this.key = key;
      this.requiredValue = requiredValue;
      this.unit = unit;
    }

    public Builder governingCaseId(String value) {
      governingCaseId = value;
      return this;
    }

    public Builder relativeTolerance(double value) {
      relativeTolerance = value;
      return this;
    }

    public Builder candidates(double... values) {
      candidates = values == null ? null : values.clone();
      return this;
    }

    public Builder applier(Applier value) {
      applier = value;
      return this;
    }

    public EngineeringDesignUpdate build() {
      return new EngineeringDesignUpdate(this);
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }
}
