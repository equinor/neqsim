package neqsim.process.engineering.design;

import java.io.Serializable;

/** Execution controls for the closed engineering design loop. */
public final class EngineeringDesignLoopOptions implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final int maximumIterations;
  private final int caseParallelism;
  private final boolean requireAllConstraints;
  private final double processValueRelativeTolerance;
  private final boolean requireStableProcessValues;

  private EngineeringDesignLoopOptions(Builder builder) {
    if (builder.maximumIterations < 1) {
      throw new IllegalArgumentException("maximumIterations must be positive");
    }
    if (builder.caseParallelism < 1) {
      throw new IllegalArgumentException("caseParallelism must be positive");
    }
    maximumIterations = builder.maximumIterations;
    caseParallelism = builder.caseParallelism;
    requireAllConstraints = builder.requireAllConstraints;
    if (!Double.isFinite(builder.processValueRelativeTolerance) || builder.processValueRelativeTolerance < 0.0) {
      throw new IllegalArgumentException("processValueRelativeTolerance must be finite and non-negative");
    }
    processValueRelativeTolerance = builder.processValueRelativeTolerance;
    requireStableProcessValues = builder.requireStableProcessValues;
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

  public boolean isAllConstraintsRequired() {
    return requireAllConstraints;
  }

  public double getProcessValueRelativeTolerance() {
    return processValueRelativeTolerance;
  }

  public boolean isStableProcessValuesRequired() {
    return requireStableProcessValues;
  }

  public static final class Builder {
    private int maximumIterations = 12;
    private int caseParallelism = 1;
    private boolean requireAllConstraints = true;
    private double processValueRelativeTolerance = 1.0e-4;
    private boolean requireStableProcessValues = true;

    public Builder maximumIterations(int value) {
      maximumIterations = value;
      return this;
    }

    public Builder caseParallelism(int value) {
      caseParallelism = value;
      return this;
    }

    public Builder requireAllConstraints(boolean value) {
      requireAllConstraints = value;
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

    public EngineeringDesignLoopOptions build() {
      return new EngineeringDesignLoopOptions(this);
    }
  }
}
