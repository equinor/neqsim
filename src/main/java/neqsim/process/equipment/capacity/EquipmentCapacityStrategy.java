package neqsim.process.equipment.capacity;

import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;

/**
 * Strategy interface for equipment-specific capacity evaluation.
 *
 * <p>
 * This interface enables a plugin-based architecture for evaluating capacity constraints across
 * different equipment types. Each equipment type can have its own strategy implementation that
 * understands the specific physics and constraints of that equipment.
 * </p>
 *
 * <p>
 * Strategy implementations should be registered with {@link EquipmentCapacityStrategyRegistry} for
 * automatic discovery during optimization.
 * </p>
 *
 * <p><strong>Example Implementation</strong></p>
 * 
 * <pre>
 * public class CompressorCapacityStrategy implements EquipmentCapacityStrategy {
 *   &#64;Override
 *   public boolean supports(ProcessEquipmentInterface equipment) {
 *     return equipment instanceof Compressor;
 *   }
 * 
 *   &#64;Override
 *   public double evaluateCapacity(ProcessEquipmentInterface equipment) {
 *     Compressor comp = (Compressor) equipment;
 *     return comp.getMaxUtilization();
 *   }
 * }
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see EquipmentCapacityStrategyRegistry
 * @see CapacityConstraint
 */
public interface EquipmentCapacityStrategy {

  /**
   * Checks if this strategy supports the given equipment.
   *
   * @param equipment the equipment to check
   * @return true if this strategy can evaluate the equipment
   */
  boolean supports(ProcessEquipmentInterface equipment);

  /**
   * Gets the priority of this strategy.
   *
   * <p>
   * When multiple strategies support the same equipment, the one with higher priority is used.
   * Default priority is 0. Use positive values for more specific strategies.
   * </p>
   *
   * @return the priority value (higher = more preferred)
   */
  default int getPriority() {
    return 0;
  }

  /**
   * Evaluates the current capacity utilization of the equipment.
   *
   * <p>
   * Returns a value between 0 and 1+ where:
   * <ul>
   * <li>0.0 = no load</li>
   * <li>1.0 = at design capacity</li>
   * <li>&gt;1.0 = over capacity</li>
   * </ul>
   *
   * @param equipment the equipment to evaluate
   * @return capacity utilization as a fraction
   */
  double evaluateCapacity(ProcessEquipmentInterface equipment);

  /**
   * Evaluates the maximum capacity of the equipment.
   *
   * <p>
   * Returns the maximum capacity in the equipment's natural units (e.g., flow rate, power, duty).
   * </p>
   *
   * @param equipment the equipment to evaluate
   * @return maximum capacity
   */
  double evaluateMaxCapacity(ProcessEquipmentInterface equipment);

  /**
   * Gets all capacity constraints for this equipment.
   *
   * <p>
   * Returns a map of constraint name to constraint object. The constraints include both the current
   * value and the design/limit values.
   * </p>
   *
   * @param equipment the equipment to get constraints for
   * @return map of constraint name to CapacityConstraint
   */
  Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment);

  /**
   * Gets the list of constraint violations for this equipment.
   *
   * <p>
   * Returns only the constraints that are currently violated (utilization &gt; 1.0 or outside
   * limits).
   * </p>
   *
   * @param equipment the equipment to check
   * @return list of violated constraints
   */
  List<CapacityConstraint> getViolations(ProcessEquipmentInterface equipment);

  /**
   * Gets the bottleneck constraint (highest utilization).
   *
   * @param equipment the equipment to evaluate
   * @return the constraint with highest utilization, or null if none
   */
  CapacityConstraint getBottleneckConstraint(ProcessEquipmentInterface equipment);

  /**
   * Checks if the equipment is operating within all hard limits.
   *
   * @param equipment the equipment to check
   * @return true if no hard limits are violated
   */
  boolean isWithinHardLimits(ProcessEquipmentInterface equipment);

  /**
   * Checks if the equipment is operating within all soft limits (design values).
   *
   * @param equipment the equipment to check
   * @return true if no soft limits are violated
   */
  boolean isWithinSoftLimits(ProcessEquipmentInterface equipment);

  /**
   * Gets the available margin before hitting the bottleneck constraint.
   *
   * @param equipment the equipment to evaluate
   * @return available margin as a fraction (0.2 = 20% headroom)
   */
  default double getAvailableMargin(ProcessEquipmentInterface equipment) {
    return 1.0 - evaluateCapacity(equipment);
  }

  /**
   * Gets a descriptive name for this strategy.
   *
   * @return strategy name
   */
  String getName();

  /**
   * Gets the equipment class this strategy handles.
   *
   * @return the equipment class
   */
  Class<? extends ProcessEquipmentInterface> getEquipmentClass();
}
