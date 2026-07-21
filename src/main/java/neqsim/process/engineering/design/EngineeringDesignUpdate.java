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

  /** Defines how proposals from more than one module may govern a shared design variable. */
  public enum ConflictResolution {
    /** More than one module proposing the key is a configuration error. */
    REQUIRE_UNIQUE,
    /** Select the largest proposed value after confirming identical units and rules. */
    GOVERNING_MAXIMUM,
    /** Select the smallest proposed value after confirming identical units and rules. */
    GOVERNING_MINIMUM
  }

  private final String key;
  private final double requiredValue;
  private final String unit;
  private final String governingCaseId;
  private final double relativeTolerance;
  private final double[] candidates;
  private final DesignCandidate[] designCandidates;
  private final Applier applier;
  private final ConflictResolution conflictResolution;

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
    if (builder.designCandidates == null) {
      candidates = builder.candidates == null ? new double[0] : builder.candidates.clone();
      Arrays.sort(candidates);
      designCandidates = new DesignCandidate[0];
    } else {
      designCandidates = builder.designCandidates.clone();
      Arrays.sort(designCandidates);
      candidates = new double[designCandidates.length];
      for (int index = 0; index < designCandidates.length; index++) {
        if (!unit.equals(designCandidates[index].getUnit())) {
          throw new IllegalArgumentException("Candidate " + designCandidates[index].getId() + " uses "
              + designCandidates[index].getUnit() + " but update " + key + " uses " + unit);
        }
        candidates[index] = designCandidates[index].getValue();
      }
    }
    applier = builder.applier;
    conflictResolution = builder.conflictResolution;
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

  public double getRequiredValue() {
    return requiredValue;
  }

  public double getRelativeTolerance() {
    return relativeTolerance;
  }

  /** @return declared shared-variable conflict resolution */
  public ConflictResolution getConflictResolution() {
    return conflictResolution;
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

  String selectedCandidateId() {
    if (candidates.length == 0) {
      return "CONTINUOUS";
    }
    double selected = selectedValue();
    for (DesignCandidate candidate : designCandidates) {
      if (Double.compare(candidate.getValue(), selected) == 0) {
        return candidate.getId();
      }
    }
    return key + "@" + Double.toString(selected) + unit;
  }

  /** Returns the governed candidate records, synthesizing stable records for legacy numeric candidates. */
  public DesignCandidate[] getCandidates() {
    if (designCandidates.length > 0) {
      return designCandidates.clone();
    }
    DesignCandidate[] result = new DesignCandidate[candidates.length];
    for (int index = 0; index < candidates.length; index++) {
      result[index] = new DesignCandidate(key + "@" + Double.toString(candidates[index]) + unit, candidates[index],
          unit);
    }
    return result;
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
    private DesignCandidate[] designCandidates;
    private Applier applier;
    private ConflictResolution conflictResolution = ConflictResolution.REQUIRE_UNIQUE;

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
      designCandidates = null;
      return this;
    }

    /** Configures identified physical candidates such as schedule sizes or vendor ratings. */
    public Builder candidates(DesignCandidate... values) {
      designCandidates = values == null ? null : values.clone();
      candidates = null;
      return this;
    }

    public Builder applier(Applier value) {
      applier = value;
      return this;
    }

    /**
     * Declare how this update may be combined with proposals for the same key from other modules.
     *
     * @param value explicit governing rule
     * @return this builder
     */
    public Builder conflictResolution(ConflictResolution value) {
      if (value == null) {
        throw new IllegalArgumentException("conflictResolution must not be null");
      }
      conflictResolution = value;
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
