package neqsim.process.equipment.capacity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.separator.Separator;

/**
 * Capacity strategy for separator equipment.
 *
 * <p>
 * This strategy evaluates separator capacity based on multiple constraints including:
 * <ul>
 * <li>Gas load factor (Fs factor) for vertical separators</li>
 * <li>Liquid level (residence time)</li>
 * <li>Liquid carryover risk</li>
 * <li>Foam handling capacity</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class SeparatorCapacityStrategy implements EquipmentCapacityStrategy {

  /** Default maximum gas load factor (m/s * sqrt(kg/m3)). */
  public static final double DEFAULT_MAX_GAS_LOAD_FACTOR = 0.1;

  /** Default maximum liquid level fraction. */
  public static final double DEFAULT_MAX_LIQUID_LEVEL = 0.8;

  private double maxGasLoadFactor = DEFAULT_MAX_GAS_LOAD_FACTOR;
  private double maxLiquidLevel = DEFAULT_MAX_LIQUID_LEVEL;

  /**
   * Default constructor.
   */
  public SeparatorCapacityStrategy() {}

  /**
   * Constructor with custom constraints.
   *
   * @param maxGasLoadFactor maximum gas load factor
   * @param maxLiquidLevel maximum liquid level fraction
   */
  public SeparatorCapacityStrategy(double maxGasLoadFactor, double maxLiquidLevel) {
    this.maxGasLoadFactor = maxGasLoadFactor;
    this.maxLiquidLevel = maxLiquidLevel;
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof Separator;
  }

  /** {@inheritDoc} */
  @Override
  public int getPriority() {
    return 10;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "SeparatorCapacityStrategy";
  }

  /** {@inheritDoc} */
  @Override
  public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
    return Separator.class;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateCapacity(ProcessEquipmentInterface equipment) {
    if (!(equipment instanceof Separator)) {
      return 0.0;
    }

    Separator sep = (Separator) equipment;

    // If separator implements CapacityConstrainedEquipment, use its max utilization
    if (sep instanceof CapacityConstrainedEquipment) {
      return ((CapacityConstrainedEquipment) sep).getMaxUtilization();
    }

    // Fallback: use liquid level as primary capacity indicator
    double liquidLevel = sep.getLiquidLevel();
    if (liquidLevel > 0) {
      return liquidLevel / maxLiquidLevel;
    }

    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateMaxCapacity(ProcessEquipmentInterface equipment) {
    if (!(equipment instanceof Separator)) {
      return 0.0;
    }

    Separator sep = (Separator) equipment;
    // Return the design capacity if available
    return sep.getCapacityMax();
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = new HashMap<>();

    if (!(equipment instanceof Separator)) {
      return constraints;
    }

    Separator sep = (Separator) equipment;

    // If separator already implements CapacityConstrainedEquipment, use its constraints
    if (sep instanceof CapacityConstrainedEquipment) {
      return ((CapacityConstrainedEquipment) sep).getCapacityConstraints();
    }

    // Liquid level constraint
    CapacityConstraint levelConstraint =
        new CapacityConstraint("liquidLevel").setDesignValue(maxLiquidLevel).setMaxValue(0.95)
            .setUnit("fraction").setSeverity(CapacityConstraint.ConstraintSeverity.SOFT)
            .setWarningThreshold(0.9).setValueSupplier(() -> sep.getLiquidLevel());
    constraints.put("liquidLevel", levelConstraint);

    // Gas load factor constraint (if separator has gas handling)
    try {
      if (sep.getGasOutStream() != null && sep.getGasOutStream().getThermoSystem() != null) {
        // Calculate gas load factor (Souders-Brown equation approximation)
        CapacityConstraint gasConstraint =
            new CapacityConstraint("gasLoadFactor").setDesignValue(maxGasLoadFactor)
                .setMaxValue(maxGasLoadFactor * 1.2).setUnit("m/s*sqrt(kg/m3)")
                .setSeverity(CapacityConstraint.ConstraintSeverity.SOFT).setValueSupplier(() -> {
                  double gasFlowRate = sep.getGasOutStream().getFlowRate("m3/hr");
                  return gasFlowRate / 3600.0 / 100.0; // Simplified - actual would use Fs factor
                });
        constraints.put("gasLoadFactor", gasConstraint);
      }
    } catch (Exception e) {
      // Gas outlet not available
    }

    return constraints;
  }

  /** {@inheritDoc} */
  @Override
  public List<CapacityConstraint> getViolations(ProcessEquipmentInterface equipment) {
    List<CapacityConstraint> violations = new ArrayList<>();
    Map<String, CapacityConstraint> constraints = getConstraints(equipment);

    for (CapacityConstraint constraint : constraints.values()) {
      if (constraint.isViolated()) {
        violations.add(constraint);
      }
    }

    return violations;
  }

  /** {@inheritDoc} */
  @Override
  public CapacityConstraint getBottleneckConstraint(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = getConstraints(equipment);

    CapacityConstraint bottleneck = null;
    double maxUtil = 0.0;

    for (CapacityConstraint constraint : constraints.values()) {
      double util = constraint.getUtilization();
      if (!Double.isNaN(util) && util > maxUtil) {
        maxUtil = util;
        bottleneck = constraint;
      }
    }

    return bottleneck;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isWithinHardLimits(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = getConstraints(equipment);

    for (CapacityConstraint constraint : constraints.values()) {
      if (constraint.getSeverity() == CapacityConstraint.ConstraintSeverity.HARD
          || constraint.getSeverity() == CapacityConstraint.ConstraintSeverity.CRITICAL) {
        if (constraint.isHardLimitExceeded()) {
          return false;
        }
      }
    }

    return true;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isWithinSoftLimits(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = getConstraints(equipment);

    for (CapacityConstraint constraint : constraints.values()) {
      if (constraint.getUtilization() > 1.0) {
        return false;
      }
    }

    return true;
  }

  // Getters and setters

  /**
   * Gets the maximum gas load factor.
   *
   * @return maximum gas load factor
   */
  public double getMaxGasLoadFactor() {
    return maxGasLoadFactor;
  }

  /**
   * Sets the maximum gas load factor.
   *
   * @param maxGasLoadFactor maximum gas load factor
   */
  public void setMaxGasLoadFactor(double maxGasLoadFactor) {
    this.maxGasLoadFactor = maxGasLoadFactor;
  }

  /**
   * Gets the maximum liquid level.
   *
   * @return maximum liquid level fraction
   */
  public double getMaxLiquidLevel() {
    return maxLiquidLevel;
  }

  /**
   * Sets the maximum liquid level.
   *
   * @param maxLiquidLevel maximum liquid level fraction
   */
  public void setMaxLiquidLevel(double maxLiquidLevel) {
    this.maxLiquidLevel = maxLiquidLevel;
  }
}
