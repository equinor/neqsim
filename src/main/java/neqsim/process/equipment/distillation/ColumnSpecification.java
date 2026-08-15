package neqsim.process.equipment.distillation;

import java.io.Serializable;

/**
 * Represents a column specification (degree of freedom) for a distillation column.
 *
 * <p>
 * In a distillation column with a condenser and reboiler, there are two degrees of freedom that must be satisfied. Each
 * specification constrains one degree of freedom. Common specification types include:
 * </p>
 * <ul>
 * <li>Product purity (mole fraction of a component in distillate or bottoms)</li>
 * <li>Reflux ratio (L/D for condenser or V/B boilup ratio for reboiler)</li>
 * <li>Component recovery (fraction of a component recovered in a product)</li>
 * <li>Product flow rate (total flow rate of distillate or bottoms)</li>
 * <li>Condenser or reboiler duty</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * // Specify 95% methane purity in the top product
 * ColumnSpecification topSpec = new ColumnSpecification(ColumnSpecification.SpecificationType.PRODUCT_PURITY,
 *     ColumnSpecification.ProductLocation.TOP, 0.95, "methane");
 *
 * // Specify boilup ratio of 2.0 at the reboiler
 * ColumnSpecification bottomSpec = new ColumnSpecification(ColumnSpecification.SpecificationType.REFLUX_RATIO,
 *     ColumnSpecification.ProductLocation.BOTTOM, 2.0);
 *
 * column.setTopSpecification(topSpec);
 * column.setBottomSpecification(bottomSpec);
 * </pre>
 *
 * <p>
 * On a column without an external side draw, top and bottom product-flow targets are linked by total material balance,
 * and top and bottom recoveries of the same component are linked by component balance. Use one member of either pair
 * with an independent purity, recovery, duty, or reflux specification. The column validator and run preflight reject
 * dependent pairs before solver iteration.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class ColumnSpecification implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Default tolerance for the secant/bisection solver. */
  private static final double DEFAULT_TOLERANCE = 1.0e-4;

  /** Default maximum iterations for the outer adjustment loop. */
  private static final int DEFAULT_MAX_ITERATIONS = 20;

  /**
   * Types of column specifications.
   */
  public enum SpecificationType {
    /**
     * Mole fraction of a named component in the specified product stream. Requires {@code componentName} to be set.
     */
    PRODUCT_PURITY,

    /**
     * Reflux ratio (L/D) at the condenser or boilup ratio (V/B) at the reboiler.
     */
    REFLUX_RATIO,

    /**
     * Fraction of a named component's feed that is recovered in the specified product. Requires {@code componentName}
     * to be set. Value is between 0 and 1.
     */
    COMPONENT_RECOVERY,

    /**
     * Total molar flow rate of the product stream in mol/hr.
     */
    PRODUCT_FLOW_RATE,

    /**
     * Duty of the condenser or reboiler in watts.
     */
    DUTY
  }

  /**
   * Indicates which end of the column this specification refers to.
   */
  public enum ProductLocation {
    /** Top (distillate/overhead) product or condenser. */
    TOP,
    /** Bottom (bottoms) product or reboiler. */
    BOTTOM
  }

  private final SpecificationType type;
  private final ProductLocation location;
  private final double targetValue;
  private final String componentName;
  /** Unit used to interpret a product-flow target; null only in legacy serialized objects. */
  private final String targetUnit;
  private double tolerance = DEFAULT_TOLERANCE;
  private int maxIterations = DEFAULT_MAX_ITERATIONS;

  /**
   * Creates a column specification that does not require a component name.
   *
   * @param type the specification type (REFLUX_RATIO, PRODUCT_FLOW_RATE, or DUTY)
   * @param location which end of the column this applies to
   * @param targetValue the target value for this specification
   */
  public ColumnSpecification(SpecificationType type, ProductLocation location, double targetValue) {
    this(type, location, targetValue, null);
  }

  /**
   * Creates a column specification with a component name.
   *
   * @param type the specification type
   * @param location which end of the column this applies to
   * @param targetValue the target value for this specification
   * @param componentName the name of the component (required for PRODUCT_PURITY and COMPONENT_RECOVERY)
   */
  public ColumnSpecification(SpecificationType type, ProductLocation location, double targetValue,
      String componentName) {
    this(type, location, targetValue, componentName, defaultTargetUnit(type));
  }

  /**
   * Creates a column specification with an explicit target unit.
   *
   * <p>
   * The explicit unit is currently used by {@link SpecificationType#PRODUCT_FLOW_RATE}; other specification types keep
   * their established dimensionless or watt targets. Existing constructors remain source compatible and use mol/hr for
   * product-flow specifications.
   * </p>
   *
   * @param type the specification type
   * @param location which end of the column this applies to
   * @param targetValue the target value in {@code targetUnit}
   * @param componentName component name for component-based specifications, otherwise {@code null}
   * @param targetUnit unit for a product-flow target
   */
  public ColumnSpecification(SpecificationType type, ProductLocation location, double targetValue, String componentName,
      String targetUnit) {
    this.type = type;
    this.location = location;
    this.targetValue = targetValue;
    this.componentName = componentName;
    this.targetUnit = type == SpecificationType.PRODUCT_FLOW_RATE && targetUnit != null ? targetUnit.trim()
        : defaultTargetUnit(type);
    validate();
  }

  /**
   * Return the backward-compatible target unit for a specification type.
   *
   * @param type specification type
   * @return mol/hr for product flow, W for duty, or an empty string for dimensionless targets
   */
  private static String defaultTargetUnit(SpecificationType type) {
    if (type == SpecificationType.PRODUCT_FLOW_RATE) {
      return "mol/hr";
    }
    if (type == SpecificationType.DUTY) {
      return "W";
    }
    return "";
  }

  /**
   * Validates that the specification is internally consistent.
   *
   * @throws IllegalArgumentException if the specification is invalid
   */
  private void validate() {
    if (type == null) {
      throw new IllegalArgumentException("Specification type cannot be null");
    }
    if (location == null) {
      throw new IllegalArgumentException("Product location cannot be null");
    }
    if (!Double.isFinite(targetValue)) {
      throw new IllegalArgumentException("Specification target value must be finite");
    }
    if (type == SpecificationType.PRODUCT_PURITY || type == SpecificationType.COMPONENT_RECOVERY) {
      if (componentName == null || componentName.trim().isEmpty()) {
        throw new IllegalArgumentException(type + " specification requires a component name");
      }
    }
    if (type == SpecificationType.PRODUCT_PURITY) {
      if (targetValue < 0.0 || targetValue > 1.0) {
        throw new IllegalArgumentException("Product purity must be between 0 and 1, got: " + targetValue);
      }
    }
    if (type == SpecificationType.COMPONENT_RECOVERY) {
      if (targetValue < 0.0 || targetValue > 1.0) {
        throw new IllegalArgumentException("Component recovery must be between 0 and 1, got: " + targetValue);
      }
    }
    if (type == SpecificationType.REFLUX_RATIO) {
      if (targetValue < 0.0) {
        throw new IllegalArgumentException("Reflux/boilup ratio must be non-negative, got: " + targetValue);
      }
    }
    if (type == SpecificationType.PRODUCT_FLOW_RATE) {
      if (targetValue <= 0.0) {
        throw new IllegalArgumentException("Product flow rate must be positive, got: " + targetValue);
      }
      if (targetUnit == null || targetUnit.trim().isEmpty()) {
        throw new IllegalArgumentException("Product flow rate requires a non-empty flow unit");
      }
    }
  }

  /**
   * Returns the specification type.
   *
   * @return the specification type
   */
  public SpecificationType getType() {
    return type;
  }

  /**
   * Returns which end of the column this specification refers to.
   *
   * @return the product location
   */
  public ProductLocation getLocation() {
    return location;
  }

  /**
   * Returns the target value for this specification.
   *
   * @return the target value
   */
  public double getTargetValue() {
    return targetValue;
  }

  /**
   * Returns the component name (may be null for non-component specifications).
   *
   * @return the component name, or null
   */
  public String getComponentName() {
    return componentName;
  }

  /**
   * Returns the unit used to interpret the target value.
   *
   * <p>
   * Product-flow specifications created through the legacy constructors, including deserialized historical objects, use
   * mol/hr. Duty specifications use W and fraction/ratio targets are dimensionless.
   * </p>
   *
   * @return target unit, or an empty string for dimensionless specifications
   */
  public String getTargetUnit() {
    return targetUnit == null ? defaultTargetUnit(type) : targetUnit;
  }

  /**
   * Returns the convergence tolerance for the outer adjustment loop.
   *
   * @return the tolerance
   */
  public double getTolerance() {
    return tolerance;
  }

  /**
   * Sets the convergence tolerance for the outer adjustment loop.
   *
   * @param tolerance the tolerance (must be positive)
   */
  public void setTolerance(double tolerance) {
    if (!Double.isFinite(tolerance) || tolerance <= 0.0) {
      throw new IllegalArgumentException("Tolerance must be positive, got: " + tolerance);
    }
    this.tolerance = tolerance;
  }

  /**
   * Returns the maximum number of iterations for the outer adjustment loop.
   *
   * @return the maximum iterations
   */
  public int getMaxIterations() {
    return maxIterations;
  }

  /**
   * Sets the maximum number of iterations for the outer adjustment loop.
   *
   * @param maxIterations the maximum iterations (must be positive)
   */
  public void setMaxIterations(int maxIterations) {
    if (maxIterations <= 0) {
      throw new IllegalArgumentException("Max iterations must be positive, got: " + maxIterations);
    }
    this.maxIterations = maxIterations;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("ColumnSpecification[type=").append(type);
    sb.append(", location=").append(location);
    sb.append(", target=").append(targetValue);
    if (!getTargetUnit().isEmpty()) {
      sb.append(" ").append(getTargetUnit());
    }
    if (componentName != null) {
      sb.append(", component=").append(componentName);
    }
    sb.append("]");
    return sb.toString();
  }
}
