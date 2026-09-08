package neqsim.process.util.optimizer;

import java.io.Serializable;

/**
 * Immutable observation for one participant in a shared plant-resource constraint.
 *
 * <p>
 * Values retain the participant's declared source unit and basis. Conversion to the shared target is performed only by
 * {@link PlantSharedResourceEvidence} using the conversion registered in {@link PlantConstraintParticipant}. Missing
 * observations are represented by status and {@link Double#NaN}; they are never converted to zero. An explicit zero is
 * accepted only as a finite {@link Status#AVAILABLE} observation or as {@link Status#OUT_OF_SERVICE} evidence.
 * </p>
 */
public final class PlantSharedResourceParticipantSample
    implements Serializable, Comparable<PlantSharedResourceParticipantSample> {
  private static final long serialVersionUID = 1L;

  /** Participant observation status. */
  public enum Status {
    /** A finite observation is available. */
    AVAILABLE,
    /** The participant is explicitly unavailable and contributes verified zero load. */
    OUT_OF_SERVICE,
    /** The expected participant or its observation is missing. */
    MISSING,
    /** The observation belongs to an older process state. */
    STALE,
    /** The observed value is non-finite. */
    NON_FINITE_VALUE,
    /** The observation is outside its declared validity range. */
    OUTSIDE_VALIDITY,
    /** The underlying process calculation did not converge. */
    INCOMPLETE_CONVERGENCE,
    /** Participant identity, unit, basis, availability, or provenance is inconsistent. */
    METADATA_MISMATCH,
    /** Observation failed with an exception. */
    EXCEPTION
  }

  private final String sourceId;
  private final String calculationId;
  private final Status status;
  private final double value;
  private final String unit;
  private final String basis;
  private final String provenance;
  private final String diagnostic;
  private final boolean confidenceSet;
  private final double confidence;
  private final boolean validityRangeSet;
  private final double validityMinimum;
  private final double validityMaximum;

  private PlantSharedResourceParticipantSample(Builder builder) {
    sourceId = PlantConstraintScope.requireText(builder.sourceId, "Shared-resource participant identity");
    calculationId = PlantConstraintScope.requireText(builder.calculationId, "Calculation id");
    value = builder.value;
    unit = PlantConstraintScope.safeText(builder.unit);
    basis = PlantConstraintScope.safeText(builder.basis);
    provenance = PlantConstraintScope.safeText(builder.provenance);
    diagnostic = PlantConstraintScope.safeText(builder.diagnostic);
    confidenceSet = builder.confidenceSet;
    confidence = validateConfidence(builder.confidenceSet, builder.confidence);
    validityRangeSet = builder.validityRangeSet;
    validityMinimum = builder.validityMinimum;
    validityMaximum = builder.validityMaximum;
    validateValidityRange();
    status = resolveStatus(builder.status);
  }

  /** Starts a callback-free builder suitable for Java and JPype callers. */
  public static Builder builder(String sourceId, String calculationId) {
    return new Builder(sourceId, calculationId);
  }

  private static double validateConfidence(boolean set, double value) {
    if (!set) {
      return Double.NaN;
    }
    if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException("Participant confidence must be finite and in [0, 1]");
    }
    return value;
  }

  private void validateValidityRange() {
    if (validityRangeSet && (!Double.isFinite(validityMinimum) || !Double.isFinite(validityMaximum)
        || validityMinimum > validityMaximum)) {
      throw new IllegalArgumentException("Participant validity range must be finite and ordered");
    }
  }

  private Status resolveStatus(Status requested) {
    Status resolved = requested == null ? Status.MISSING : requested;
    if (resolved == Status.AVAILABLE || resolved == Status.OUT_OF_SERVICE) {
      if (!Double.isFinite(value)) {
        return Status.NON_FINITE_VALUE;
      }
      if (resolved == Status.OUT_OF_SERVICE && value != 0.0) {
        return Status.METADATA_MISMATCH;
      }
      if (validityRangeSet && (value < validityMinimum || value > validityMaximum)) {
        return Status.OUTSIDE_VALIDITY;
      }
    }
    return resolved;
  }

  /** @return stable caller-owned participant identity */
  public String getSourceId() {
    return sourceId;
  }

  /** @return exact process calculation identity */
  public String getCalculationId() {
    return calculationId;
  }

  /** @return observation status */
  public Status getStatus() {
    return status;
  }

  /** @return observed source value, or NaN when unavailable */
  public double getValue() {
    return value;
  }

  /** @return source engineering unit */
  public String getUnit() {
    return unit;
  }

  /** @return source measurement or reference basis */
  public String getBasis() {
    return basis;
  }

  /** @return runtime evidence provenance */
  public String getProvenance() {
    return provenance;
  }

  /** @return explanatory diagnostic, or empty */
  public String getDiagnostic() {
    return diagnostic;
  }

  /** @return whether confidence was explicitly supplied */
  public boolean hasConfidence() {
    return confidenceSet;
  }

  /** @return confidence in [0, 1], or NaN when not supplied */
  public double getConfidence() {
    return confidence;
  }

  /** @return whether a source-value validity range was supplied */
  public boolean hasValidityRange() {
    return validityRangeSet;
  }

  /** @return inclusive source-value validity minimum, or NaN */
  public double getValidityMinimum() {
    return validityRangeSet ? validityMinimum : Double.NaN;
  }

  /** @return inclusive source-value validity maximum, or NaN */
  public double getValidityMaximum() {
    return validityRangeSet ? validityMaximum : Double.NaN;
  }

  /** @return true for a finite available or verified out-of-service observation */
  public boolean isUsable() {
    return status == Status.AVAILABLE || status == Status.OUT_OF_SERVICE;
  }

  /** {@inheritDoc} */
  @Override
  public int compareTo(PlantSharedResourceParticipantSample other) {
    return sourceId.compareTo(other.sourceId);
  }

  /** Callback-free participant builder. */
  public static final class Builder {
    private final String sourceId;
    private final String calculationId;
    private Status status = Status.AVAILABLE;
    private double value = Double.NaN;
    private String unit = "";
    private String basis = "";
    private String provenance = "";
    private String diagnostic = "";
    private boolean confidenceSet;
    private double confidence = Double.NaN;
    private boolean validityRangeSet;
    private double validityMinimum = Double.NaN;
    private double validityMaximum = Double.NaN;

    private Builder(String sourceId, String calculationId) {
      this.sourceId = sourceId;
      this.calculationId = calculationId;
    }

    /** Sets participant status. */
    public Builder status(Status value) {
      status = value;
      return this;
    }

    /** Sets the observed value in the declared source unit and basis. */
    public Builder value(double observedValue) {
      value = observedValue;
      return this;
    }

    /** Sets the source engineering unit. */
    public Builder unit(String value) {
      unit = value;
      return this;
    }

    /** Sets the source measurement or reference basis. */
    public Builder basis(String value) {
      basis = value;
      return this;
    }

    /** Sets runtime evidence provenance. */
    public Builder provenance(String value) {
      provenance = value;
      return this;
    }

    /** Sets an explanatory diagnostic. */
    public Builder diagnostic(String value) {
      diagnostic = value;
      return this;
    }

    /** Sets evidence confidence in [0, 1]. */
    public Builder confidence(double value) {
      confidence = value;
      confidenceSet = true;
      return this;
    }

    /** Sets an inclusive source-value validity range. */
    public Builder validityRange(double minimum, double maximum) {
      validityMinimum = minimum;
      validityMaximum = maximum;
      validityRangeSet = true;
      return this;
    }

    /** @return validated immutable participant observation */
    public PlantSharedResourceParticipantSample build() {
      return new PlantSharedResourceParticipantSample(this);
    }
  }
}
