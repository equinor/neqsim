package neqsim.process.equipment.network;

import java.io.Serializable;

/**
 * Scaled residual and diagnostic for one optimization constraint.
 */
public class NetworkConstraintResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String name;
  private final boolean hard;
  private final double residual;
  private final double scale;
  private final double scaledResidual;
  private final boolean satisfied;
  private final boolean active;
  private final String unit;
  private final String message;
  private Double sensitivity;

  /**
   * Create a constraint result.
   *
   * @param name name
   * @param hard true for hard constraint
   * @param residual non-negative violation magnitude
   * @param scale positive scaling value
   * @param active true when binding or nearly binding
   * @param unit residual unit
   * @param message diagnostic
   */
  public NetworkConstraintResult(String name, boolean hard, double residual, double scale, boolean active, String unit,
      String message) {
    this.name = name;
    this.hard = hard;
    this.residual = Math.max(0.0, residual);
    this.scale = scale > 0.0 ? scale : 1.0;
    this.scaledResidual = this.residual / this.scale;
    this.satisfied = this.residual <= 0.0;
    this.active = active;
    this.unit = unit;
    this.message = message;
  }

  /** @return constraint name */
  public String getName() {
    return name;
  }

  /** @return true for hard constraint */
  public boolean isHard() {
    return hard;
  }

  /** @return violation magnitude */
  public double getResidual() {
    return residual;
  }

  /** @return residual scale */
  public double getScale() {
    return scale;
  }

  /** @return dimensionless scaled residual */
  public double getScaledResidual() {
    return scaledResidual;
  }

  /** @return true when no violation exists */
  public boolean isSatisfied() {
    return satisfied;
  }

  /** @return true when the constraint is binding or nearly binding */
  public boolean isActive() {
    return active;
  }

  /** @return unit */
  public String getUnit() {
    return unit;
  }

  /** @return diagnostic */
  public String getMessage() {
    return message;
  }

  /** @return optional finite-difference sensitivity */
  public Double getSensitivity() {
    return sensitivity;
  }

  /**
   * Set an available sensitivity or shadow-value estimate.
   *
   * @param value sensitivity
   */
  public void setSensitivity(Double value) {
    sensitivity = value;
  }
}
