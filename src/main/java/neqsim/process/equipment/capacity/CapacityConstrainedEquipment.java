package neqsim.process.equipment.capacity;

import java.util.Map;

/**
 * Interface for process equipment that has capacity constraints.
 *
 * <p>
 * This interface provides a standardized way to query capacity limits and utilization for any
 * process equipment. Equipment implementing this interface can participate in automated bottleneck
 * detection and optimization routines.
 * </p>
 *
 * <p>
 * Example equipment types that should implement this interface:
 * </p>
 * <ul>
 * <li>Separators - gas load factor, liquid residence time</li>
 * <li>Compressors - speed, power, surge margin, discharge temperature</li>
 * <li>Pumps - NPSH margin, flow rate, power</li>
 * <li>Heat Exchangers - duty, approach temperature, pressure drop</li>
 * <li>Valves - Cv utilization, pressure drop</li>
 * <li>Pipes - velocity, erosional velocity ratio</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public interface CapacityConstrainedEquipment {

  /**
   * Checks if capacity analysis is enabled for this equipment.
   *
   * <p>
   * When disabled, this equipment is excluded from bottleneck detection, capacity utilization
   * summaries, and optimization routines. The equipment still tracks its constraints but doesn't
   * contribute to system-level analysis.
   * </p>
   *
   * <p>
   * Note: The actual field is stored in {@code ProcessEquipmentBaseClass} and is enabled by default
   * for all equipment. Equipment classes should not override this method as it delegates to the
   * base class implementation.
   * </p>
   *
   * @return true if capacity analysis is enabled (default is true)
   */
  boolean isCapacityAnalysisEnabled();

  /**
   * Enables or disables capacity analysis for this equipment.
   *
   * <p>
   * When disabled, this equipment is excluded from:
   * <ul>
   * <li>System bottleneck detection ({@code ProcessSystem.findBottleneck()})</li>
   * <li>Capacity utilization summaries ({@code ProcessSystem.getCapacityUtilizationSummary()})</li>
   * <li>Equipment near capacity lists ({@code ProcessSystem.getEquipmentNearCapacityLimit()})</li>
   * <li>Optimization constraint checking</li>
   * </ul>
   * The equipment still calculates and tracks its constraints internally.
   * </p>
   *
   * <p>
   * Note: The actual field is stored in {@code ProcessEquipmentBaseClass} and is enabled by default
   * for all equipment.
   * </p>
   *
   * @param enabled true to include in capacity analysis, false to exclude
   */
  void setCapacityAnalysisEnabled(boolean enabled);

  /**
   * Gets all capacity constraints defined for this equipment.
   *
   * <p>
   * The map keys are constraint names (e.g., "speed", "gasLoadFactor") and values are the
   * corresponding CapacityConstraint objects containing design values, current values, and
   * utilization calculations.
   * </p>
   *
   * @return unmodifiable map of constraint name to CapacityConstraint
   */
  Map<String, CapacityConstraint> getCapacityConstraints();

  /**
   * Gets the constraint with the highest utilization (the bottleneck).
   *
   * <p>
   * This method identifies which constraint is closest to or exceeding its design limit. Use this
   * to determine what is limiting equipment capacity.
   * </p>
   *
   * @return the bottleneck constraint, or null if no constraints are defined
   */
  CapacityConstraint getBottleneckConstraint();

  /**
   * Checks if any capacity constraint is violated (exceeds 100% utilization).
   *
   * <p>
   * A violated constraint means the equipment is operating beyond its design capacity. For HARD
   * constraints, this may indicate equipment trip or failure. For SOFT constraints, this indicates
   * reduced efficiency or accelerated wear.
   * </p>
   *
   * @return true if any constraint utilization exceeds 1.0 (100%)
   */
  boolean isCapacityExceeded();

  /**
   * Checks if any HARD constraint limit is exceeded.
   *
   * <p>
   * HARD limits represent absolute equipment limits (e.g., maximum speed) that cannot be exceeded
   * without equipment trip or damage. This is more severe than general capacity exceedance.
   * </p>
   *
   * @return true if any HARD constraint's max value is exceeded
   */
  boolean isHardLimitExceeded();

  /**
   * Gets the maximum utilization across all constraints.
   *
   * <p>
   * This gives a single number representing how close the equipment is to its limiting constraint.
   * Values above 1.0 indicate the equipment is over capacity.
   * </p>
   *
   * @return maximum utilization as fraction (1.0 = 100% of design capacity)
   */
  double getMaxUtilization();

  /**
   * Gets the maximum utilization as a percentage.
   *
   * @return maximum utilization as percentage (100.0 = 100% of design capacity)
   */
  default double getMaxUtilizationPercent() {
    return getMaxUtilization() * 100.0;
  }

  /**
   * Gets the available margin (headroom) on the most limiting constraint.
   *
   * <p>
   * Returns the remaining capacity before the bottleneck constraint reaches 100%. Negative values
   * indicate the equipment is already over capacity.
   * </p>
   *
   * @return available margin as fraction (0.2 = 20% headroom remaining)
   */
  default double getAvailableMargin() {
    return 1.0 - getMaxUtilization();
  }

  /**
   * Gets the available margin as a percentage.
   *
   * @return available margin as percentage
   */
  default double getAvailableMarginPercent() {
    return getAvailableMargin() * 100.0;
  }

  /**
   * Checks if any constraint is near its limit (above warning threshold).
   *
   * <p>
   * The warning threshold is typically set at 90% of design capacity to provide early warning
   * before constraints are violated. Only enabled constraints are checked.
   * </p>
   *
   * @return true if any enabled constraint is above its warning threshold
   */
  default boolean isNearCapacityLimit() {
    Map<String, CapacityConstraint> constraints = getCapacityConstraints();
    if (constraints == null) {
      return false;
    }
    for (CapacityConstraint constraint : constraints.values()) {
      if (constraint.isEnabled() && constraint.isNearLimit()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gets a summary of all constraint utilizations.
   *
   * <p>
   * Returns a map of constraint names to their current utilization percentages. Only enabled
   * constraints are included. Useful for reporting and visualization.
   * </p>
   *
   * @return map of constraint name to utilization percentage
   */
  default Map<String, Double> getUtilizationSummary() {
    Map<String, CapacityConstraint> constraints = getCapacityConstraints();
    java.util.Map<String, Double> summary = new java.util.LinkedHashMap<String, Double>();
    if (constraints != null) {
      for (Map.Entry<String, CapacityConstraint> entry : constraints.entrySet()) {
        if (entry.getValue().isEnabled()) {
          summary.put(entry.getKey(), entry.getValue().getUtilizationPercent());
        }
      }
    }
    return summary;
  }

  /**
   * Adds a new capacity constraint to this equipment.
   *
   * <p>
   * This allows dynamic addition of constraints at runtime. Constraints can be added during
   * equipment configuration or based on operating conditions.
   * </p>
   *
   * @param constraint the constraint to add
   */
  void addCapacityConstraint(CapacityConstraint constraint);

  /**
   * Removes a capacity constraint by name.
   *
   * @param constraintName the name of the constraint to remove
   * @return true if the constraint was found and removed
   */
  boolean removeCapacityConstraint(String constraintName);

  /**
   * Clears all capacity constraints from this equipment.
   */
  void clearCapacityConstraints();
}
