package neqsim.process.equipment.capacity;

import neqsim.process.equipment.ProcessEquipmentInterface;

/**
 * Result class containing information about the bottleneck equipment and constraint.
 *
 * <p>
 * This class is returned by ProcessSystem.findBottleneck() and contains the equipment that is
 * limiting process capacity, along with the specific constraint that is most limiting.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class BottleneckResult {

  /** The equipment that is the bottleneck. */
  private final ProcessEquipmentInterface equipment;

  /** The most limiting constraint on the bottleneck equipment. */
  private final CapacityConstraint constraint;

  /** The utilization of the bottleneck constraint. */
  private final double utilization;

  /**
   * Creates a bottleneck result.
   *
   * @param equipment the bottleneck equipment (may be null if no constraints defined)
   * @param constraint the limiting constraint (may be null if no constraints defined)
   * @param utilization the utilization as fraction (1.0 = 100%)
   */
  public BottleneckResult(ProcessEquipmentInterface equipment, CapacityConstraint constraint,
      double utilization) {
    this.equipment = equipment;
    this.constraint = constraint;
    this.utilization = utilization;
  }

  /**
   * Creates an empty bottleneck result (no bottleneck found).
   *
   * @return an empty result
   */
  public static BottleneckResult empty() {
    return new BottleneckResult(null, null, 0.0);
  }

  /**
   * Checks if a bottleneck was found.
   *
   * @return true if equipment and constraint are not null
   */
  public boolean hasBottleneck() {
    return equipment != null && constraint != null;
  }

  /**
   * Gets the bottleneck equipment.
   *
   * @return the equipment, or null if no bottleneck
   */
  public ProcessEquipmentInterface getEquipment() {
    return equipment;
  }

  /**
   * Gets the name of the bottleneck equipment.
   *
   * @return the equipment name, or "None" if no bottleneck
   */
  public String getEquipmentName() {
    return equipment != null ? equipment.getName() : "None";
  }

  /**
   * Gets the limiting constraint.
   *
   * @return the constraint, or null if no bottleneck
   */
  public CapacityConstraint getConstraint() {
    return constraint;
  }

  /**
   * Gets the name of the limiting constraint.
   *
   * @return the constraint name, or "None" if no bottleneck
   */
  public String getConstraintName() {
    return constraint != null ? constraint.getName() : "None";
  }

  /**
   * Gets the utilization as a fraction.
   *
   * @return utilization (1.0 = 100%)
   */
  public double getUtilization() {
    return utilization;
  }

  /**
   * Gets the utilization as a percentage.
   *
   * @return utilization percentage (100.0 = 100%)
   */
  public double getUtilizationPercent() {
    return utilization * 100.0;
  }

  /**
   * Checks if the bottleneck constraint is exceeded.
   *
   * @return true if utilization is above 100%
   */
  public boolean isExceeded() {
    return utilization > 1.0;
  }

  /**
   * Checks if the bottleneck is near its limit (above warning threshold).
   *
   * @return true if constraint is near limit
   */
  public boolean isNearLimit() {
    return constraint != null && constraint.isNearLimit();
  }

  /**
   * Gets the available margin as a fraction.
   *
   * @return margin (0.2 = 20% headroom)
   */
  public double getMargin() {
    return 1.0 - utilization;
  }

  /**
   * Gets the available margin as a percentage.
   *
   * @return margin percentage
   */
  public double getMarginPercent() {
    return getMargin() * 100.0;
  }

  @Override
  public String toString() {
    if (!hasBottleneck()) {
      return "No bottleneck found (no capacity constraints defined)";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("Bottleneck: ").append(getEquipmentName());
    sb.append(" - ").append(getConstraintName());
    sb.append(String.format(" at %.1f%%", getUtilizationPercent()));
    if (isExceeded()) {
      sb.append(" [EXCEEDED]");
    } else if (isNearLimit()) {
      sb.append(" [NEAR LIMIT]");
    }
    return sb.toString();
  }
}
