package neqsim.process.engineering.design;

import java.io.Serializable;

/** A scalar acceptance condition evaluated against the current engineering design state. */
public final class EngineeringDesignConstraint implements Serializable {
  private static final long serialVersionUID = 1000L;

  public enum Comparison {
    MAXIMUM, MINIMUM
  }

  private final String id;
  private final String description;
  private final String valueKey;
  private final double limit;
  private final String unit;
  private final Comparison comparison;

  public EngineeringDesignConstraint(String id, String description, String valueKey, double limit, String unit,
      Comparison comparison) {
    this.id = id;
    this.description = description;
    this.valueKey = valueKey;
    this.limit = limit;
    this.unit = unit;
    this.comparison = comparison;
  }

  public EngineeringConstraintResult evaluate(EngineeringDesignState state) {
    double actual = state.requireValue(valueKey);
    boolean satisfied = comparison == Comparison.MAXIMUM ? actual <= limit : actual >= limit;
    return new EngineeringConstraintResult(id, description, satisfied, actual, limit, unit, comparison.name());
  }
}
