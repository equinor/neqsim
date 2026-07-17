package neqsim.process.engineering.designcase;

import java.io.Serializable;

/** Execution controls for deterministic multi-case simulation. */
public final class EngineeringCaseRunOptions implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final int parallelism;
  private final boolean requireConvergence;

  private EngineeringCaseRunOptions(Builder builder) {
    parallelism = builder.parallelism;
    requireConvergence = builder.requireConvergence;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static EngineeringCaseRunOptions sequential() {
    return builder().parallelism(1).build();
  }

  public int getParallelism() {
    return parallelism;
  }

  public boolean isConvergenceRequired() {
    return requireConvergence;
  }

  /** Builder for case execution options. */
  public static final class Builder {
    private int parallelism = 1;
    private boolean requireConvergence = true;

    public Builder parallelism(int value) {
      if (value < 1) {
        throw new IllegalArgumentException("parallelism must be positive");
      }
      parallelism = value;
      return this;
    }

    public Builder requireConvergence(boolean value) {
      requireConvergence = value;
      return this;
    }

    public EngineeringCaseRunOptions build() {
      return new EngineeringCaseRunOptions(this);
    }
  }
}
