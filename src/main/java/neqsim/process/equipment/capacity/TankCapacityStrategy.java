package neqsim.process.equipment.capacity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.tank.Tank;

/**
 * Capacity strategy for tank equipment.
 *
 * <p>
 * This strategy evaluates tank capacity based on multiple constraints including:
 * </p>
 * <ul>
 * <li>Liquid level (fill level and overflow risk)</li>
 * <li>Volume utilization</li>
 * <li>Vapor space requirements</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class TankCapacityStrategy implements EquipmentCapacityStrategy {

  /** Default maximum liquid level fraction. */
  public static final double DEFAULT_MAX_LIQUID_LEVEL = 0.85;

  /** Default minimum liquid level fraction. */
  public static final double DEFAULT_MIN_LIQUID_LEVEL = 0.10;

  private double maxLiquidLevel = DEFAULT_MAX_LIQUID_LEVEL;
  private double minLiquidLevel = DEFAULT_MIN_LIQUID_LEVEL;

  /**
   * Default constructor.
   */
  public TankCapacityStrategy() {}

  /**
   * Constructor with custom constraints.
   *
   * @param maxLiquidLevel maximum liquid level fraction (0-1)
   * @param minLiquidLevel minimum liquid level fraction (0-1)
   */
  public TankCapacityStrategy(double maxLiquidLevel, double minLiquidLevel) {
    this.maxLiquidLevel = maxLiquidLevel;
    this.minLiquidLevel = minLiquidLevel;
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof Tank;
  }

  /** {@inheritDoc} */
  @Override
  public int getPriority() {
    return 10;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "TankCapacityStrategy";
  }

  /** {@inheritDoc} */
  @Override
  public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
    return Tank.class;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateCapacity(ProcessEquipmentInterface equipment) {
    if (!(equipment instanceof Tank)) {
      return 0.0;
    }
    Tank tank = (Tank) equipment;

    // If equipment implements CapacityConstrainedEquipment, use its constraints
    if (tank instanceof CapacityConstrainedEquipment) {
      return ((CapacityConstrainedEquipment) tank).getMaxUtilization();
    }

    // Fall back to basic evaluation
    return evaluateBasicCapacity(tank);
  }

  /**
   * Evaluates basic capacity when tank doesn't have constraint interface.
   *
   * @param tank the tank to evaluate
   * @return utilization factor (0-1+)
   */
  private double evaluateBasicCapacity(Tank tank) {
    double maxUtilization = 0.0;

    // Check liquid level if available
    double liquidLevel = tank.getLiquidLevel();
    if (liquidLevel > 0 && maxLiquidLevel > 0) {
      double levelUtilization = liquidLevel / maxLiquidLevel;
      maxUtilization = Math.max(maxUtilization, levelUtilization);
    }

    return maxUtilization;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateMaxCapacity(ProcessEquipmentInterface equipment) {
    if (!(equipment instanceof Tank)) {
      return 0.0;
    }
    // Return 1.0 (100% fill) as the max capacity
    return 1.0;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = new HashMap<>();
    if (!(equipment instanceof Tank)) {
      return constraints;
    }
    Tank tank = (Tank) equipment;

    // If tank implements CapacityConstrainedEquipment, use its constraints
    if (tank instanceof CapacityConstrainedEquipment) {
      return ((CapacityConstrainedEquipment) tank).getCapacityConstraints();
    }

    // Liquid level constraint
    CapacityConstraint levelConstraint =
        new CapacityConstraint("liquidLevel").setDesignValue(maxLiquidLevel * 0.7)
            .setMaxValue(maxLiquidLevel).setMinValue(minLiquidLevel).setUnit("fraction")
            .setSeverity(CapacityConstraint.ConstraintSeverity.HARD).setWarningThreshold(0.9)
            .setDescription("Liquid level fraction").setValueSupplier(tank::getLiquidLevel);
    constraints.put("liquidLevel", levelConstraint);

    // Volume utilization constraint
    CapacityConstraint volConstraint =
        new CapacityConstraint("volumeUtilization").setDesignValue(0.7).setMaxValue(0.85)
            .setUnit("fraction").setSeverity(CapacityConstraint.ConstraintSeverity.SOFT)
            .setDescription("Tank volume utilization");
    constraints.put("volumeUtilization", volConstraint);

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

  /**
   * Sets the maximum liquid level.
   *
   * @param maxLiquidLevel maximum liquid level fraction
   */
  public void setMaxLiquidLevel(double maxLiquidLevel) {
    this.maxLiquidLevel = maxLiquidLevel;
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
   * Sets the minimum liquid level.
   *
   * @param minLiquidLevel minimum liquid level fraction
   */
  public void setMinLiquidLevel(double minLiquidLevel) {
    this.minLiquidLevel = minLiquidLevel;
  }

  /**
   * Gets the minimum liquid level.
   *
   * @return minimum liquid level fraction
   */
  public double getMinLiquidLevel() {
    return minLiquidLevel;
  }
}
