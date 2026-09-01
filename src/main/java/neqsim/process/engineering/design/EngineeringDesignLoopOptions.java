package neqsim.process.engineering.design;

import java.io.Serializable;
import neqsim.process.engineering.designcase.EngineeringCaseRunOptions;

/** Execution controls for the closed engineering design loop. */
public final class EngineeringDesignLoopOptions implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final int maximumIterations;
  private final int caseParallelism;
  private final EngineeringCaseRunOptions caseRunOptions;
  private final boolean requireAllConstraints;
  private final boolean requireCompleteCaseEnvelope;
  private final boolean requireAcceptedCaseEnvelope;
  private final double processValueRelativeTolerance;
  private final boolean requireStableProcessValues;
  private final boolean detectDiscreteOscillation;

  private EngineeringDesignLoopOptions(Builder builder) {
    if (builder.maximumIterations < 1) {
      throw new IllegalArgumentException("maximumIterations must be positive");
    }
    if (builder.caseParallelism < 1) {
      throw new IllegalArgumentException("caseParallelism must be positive");
    }
    maximumIterations = builder.maximumIterations;
    caseParallelism = builder.caseParallelism;
    caseRunOptions = builder.caseRunOptions == null
        ? EngineeringCaseRunOptions.builder().parallelism(builder.caseParallelism).build()
        : builder.caseRunOptions;
    requireAllConstraints = builder.requireAllConstraints;
    requireCompleteCaseEnvelope = builder.requireCompleteCaseEnvelope;
    requireAcceptedCaseEnvelope = builder.requireAcceptedCaseEnvelope;
    if (!Double.isFinite(builder.processValueRelativeTolerance) || builder.processValueRelativeTolerance < 0.0) {
      throw new IllegalArgumentException("processValueRelativeTolerance must be finite and non-negative");
    }
    processValueRelativeTolerance = builder.processValueRelativeTolerance;
    requireStableProcessValues = builder.requireStableProcessValues;
    detectDiscreteOscillation = builder.detectDiscreteOscillation;
  }

  public static Builder builder() {
    return new Builder();
  }

  public int getMaximumIterations() {
    return maximumIterations;
  }

  public int getCaseParallelism() {
    return caseParallelism;
  }

  /**
   * Get all execution controls used for every design-loop case run.
   *
   * @return immutable case-run options
   */
  public EngineeringCaseRunOptions getCaseRunOptions() {
    return caseRunOptions;
  }

  public boolean isAllConstraintsRequired() {
    return requireAllConstraints;
  }

  /**
   * Check whether a complete governing case envelope is required for convergence.
   *
   * @return {@code true} when missing metrics, failed cases, or skipped cases block convergence
   */
  public boolean isCompleteCaseEnvelopeRequired() {
    return requireCompleteCaseEnvelope;
  }

  /**
   * Check whether every configured metric limit must also be accepted for convergence.
   *
   * @return {@code true} when case-envelope acceptance is required
   */
  public boolean isAcceptedCaseEnvelopeRequired() {
    return requireAcceptedCaseEnvelope;
  }

  public double getProcessValueRelativeTolerance() {
    return processValueRelativeTolerance;
  }

  public boolean isStableProcessValuesRequired() {
    return requireStableProcessValues;
  }

  public boolean isDiscreteOscillationDetectionEnabled() {
    return detectDiscreteOscillation;
  }

  public static final class Builder {
    private int maximumIterations = 12;
    private int caseParallelism = 1;
    private EngineeringCaseRunOptions caseRunOptions;
    private boolean requireAllConstraints = true;
    private boolean requireCompleteCaseEnvelope = true;
    private boolean requireAcceptedCaseEnvelope;
    private double processValueRelativeTolerance = 1.0e-4;
    private boolean requireStableProcessValues = true;
    private boolean detectDiscreteOscillation = true;

    public Builder maximumIterations(int value) {
      maximumIterations = value;
      return this;
    }

    public Builder caseParallelism(int value) {
      caseParallelism = value;
      caseRunOptions = null;
      return this;
    }

    /**
     * Configure complete case execution behavior, including numerical-health and failure policy.
     *
     * @param value immutable case-run options
     * @return this builder
     */
    public Builder caseRunOptions(EngineeringCaseRunOptions value) {
      if (value == null) {
        throw new IllegalArgumentException("caseRunOptions must not be null");
      }
      caseRunOptions = value;
      caseParallelism = value.getParallelism();
      return this;
    }

    public Builder requireAllConstraints(boolean value) {
      requireAllConstraints = value;
      return this;
    }

    /**
     * Configure whether incomplete case envelopes block convergence.
     *
     * @param value completeness requirement
     * @return this builder
     */
    public Builder requireCompleteCaseEnvelope(boolean value) {
      requireCompleteCaseEnvelope = value;
      return this;
    }

    /**
     * Configure whether assessed metric limits must be accepted for convergence.
     *
     * @param value acceptance requirement
     * @return this builder
     */
    public Builder requireAcceptedCaseEnvelope(boolean value) {
      requireAcceptedCaseEnvelope = value;
      return this;
    }

    public Builder processValueRelativeTolerance(double value) {
      processValueRelativeTolerance = value;
      return this;
    }

    public Builder requireStableProcessValues(boolean value) {
      requireStableProcessValues = value;
      return this;
    }

    public Builder detectDiscreteOscillation(boolean value) {
      detectDiscreteOscillation = value;
      return this;
    }

    public EngineeringDesignLoopOptions build() {
      return new EngineeringDesignLoopOptions(this);
    }
  }
}
