package neqsim.process.equipment.capacity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.expander.ExpanderInterface;
import neqsim.process.equipment.expander.TurboExpanderCompressor;

/**
 * Capacity strategy for expander equipment.
 *
 * <p>
 * This strategy evaluates expander capacity based on multiple constraints including:
 * </p>
 * <ul>
 * <li>Power generation (compared to design power)</li>
 * <li>Speed limits (minimum and maximum)</li>
 * <li>Pressure ratio limits</li>
 * <li>Flow rate limits</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ExpanderCapacityStrategy implements EquipmentCapacityStrategy {

  /** Default maximum power ratio (fraction of design). */
  public static final double DEFAULT_MAX_POWER_RATIO = 1.1;

  /** Default minimum speed ratio. */
  public static final double DEFAULT_MIN_SPEED_RATIO = 0.7;

  /** Default maximum speed ratio. */
  public static final double DEFAULT_MAX_SPEED_RATIO = 1.05;

  /** Default design speed in RPM. */
  private static final double DEFAULT_DESIGN_SPEED = 3000.0;

  /** Default max pressure ratio for turboexpanders. */
  private static final double DEFAULT_MAX_PRESSURE_RATIO = 10.0;

  private double maxPowerRatio = DEFAULT_MAX_POWER_RATIO;
  private double minSpeedRatio = DEFAULT_MIN_SPEED_RATIO;
  private double maxSpeedRatio = DEFAULT_MAX_SPEED_RATIO;

  /**
   * Default constructor.
   */
  public ExpanderCapacityStrategy() {}

  /**
   * Constructor with custom constraints.
   *
   * @param maxPowerRatio maximum power ratio (e.g., 1.1 for 110% of design)
   * @param minSpeedRatio minimum speed ratio
   * @param maxSpeedRatio maximum speed ratio
   */
  public ExpanderCapacityStrategy(double maxPowerRatio, double minSpeedRatio,
      double maxSpeedRatio) {
    this.maxPowerRatio = maxPowerRatio;
    this.minSpeedRatio = minSpeedRatio;
    this.maxSpeedRatio = maxSpeedRatio;
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof Expander || equipment instanceof ExpanderInterface
        || equipment instanceof TurboExpanderCompressor;
  }

  /** {@inheritDoc} */
  @Override
  public int getPriority() {
    return 10;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "ExpanderCapacityStrategy";
  }

  /** {@inheritDoc} */
  @Override
  public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
    return Expander.class;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateCapacity(ProcessEquipmentInterface equipment) {
    if (!(equipment instanceof Expander)) {
      return 0.0;
    }

    Expander expander = (Expander) equipment;

    // Check if expander implements CapacityConstrainedEquipment
    if (expander instanceof CapacityConstrainedEquipment) {
      return ((CapacityConstrainedEquipment) expander).getMaxUtilization();
    }

    // Use power generation as primary capacity indicator
    double power = Math.abs(expander.getPower("kW"));
    double maxPower = expander.getCapacityMax();

    if (maxPower > 0 && power > 0) {
      return power / maxPower;
    }

    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateMaxCapacity(ProcessEquipmentInterface equipment) {
    if (!(equipment instanceof Expander)) {
      return 0.0;
    }

    Expander expander = (Expander) equipment;
    return expander.getCapacityMax();
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = new HashMap<>();

    if (!(equipment instanceof Expander)) {
      return constraints;
    }

    Expander expander = (Expander) equipment;

    // If expander already implements CapacityConstrainedEquipment, use its constraints
    if (expander instanceof CapacityConstrainedEquipment) {
      return ((CapacityConstrainedEquipment) expander).getCapacityConstraints();
    }

    // Power constraint
    double maxPower = expander.getCapacityMax();
    if (maxPower > 0) {
      CapacityConstraint powerConstraint =
          new CapacityConstraint("power", "kW", CapacityConstraint.ConstraintType.SOFT)
              .setDesignValue(maxPower).setMaxValue(maxPower * maxPowerRatio)
              .setDescription("Power generation")
              .setValueSupplier(() -> Math.abs(expander.getPower("kW")));
      constraints.put("power", powerConstraint);
    }

    // Speed constraint (if available)
    double maxSpeed = expander.getMaximumSpeed();
    double minSpeed = expander.getMinimumSpeed();
    double designSpeed = maxSpeed > 0 ? maxSpeed : DEFAULT_DESIGN_SPEED;
    if (designSpeed > 0) {
      CapacityConstraint speedConstraint =
          new CapacityConstraint("speed", "RPM", CapacityConstraint.ConstraintType.HARD)
              .setDesignValue(designSpeed).setMaxValue(designSpeed * maxSpeedRatio)
              .setMinValue(minSpeed > 0 ? minSpeed : designSpeed * minSpeedRatio)
              .setDescription("Expander speed").setValueSupplier(() -> expander.getSpeed());
      constraints.put("speed", speedConstraint);
    }

    // Pressure ratio constraint
    if (expander.getInletStream() != null && expander.getOutletStream() != null) {
      CapacityConstraint pressureRatioConstraint =
          new CapacityConstraint("pressureRatio", "ratio", CapacityConstraint.ConstraintType.SOFT)
              .setDesignValue(DEFAULT_MAX_PRESSURE_RATIO * 0.8)
              .setMaxValue(DEFAULT_MAX_PRESSURE_RATIO)
              .setDescription("Pressure ratio (inlet/outlet)").setValueSupplier(() -> {
                double inletP = expander.getInletStream().getPressure("bara");
                double outletP = expander.getOutletStream().getPressure("bara");
                return outletP > 0 ? inletP / outletP : 0.0;
              });
      constraints.put("pressureRatio", pressureRatioConstraint);
    }

    // Flow rate constraint
    if (expander.getInletStream() != null) {
      double maxFlow = maxPower > 0 ? maxPower * 100 : 100000;
      CapacityConstraint flowConstraint =
          new CapacityConstraint("flowRate", "kg/hr", CapacityConstraint.ConstraintType.SOFT)
              .setDesignValue(maxFlow * 0.9).setMaxValue(maxFlow).setDescription("Mass flow rate")
              .setValueSupplier(() -> expander.getInletStream().getFlowRate("kg/hr"));
      constraints.put("flowRate", flowConstraint);
    }

    return constraints;
  }

  /** {@inheritDoc} */
  @Override
  public List<CapacityConstraint> getViolations(ProcessEquipmentInterface equipment) {
    List<CapacityConstraint> violations = new ArrayList<>();
    Map<String, CapacityConstraint> constraints = getConstraints(equipment);

    for (CapacityConstraint c : constraints.values()) {
      if (c.isViolated()) {
        violations.add(c);
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

    for (CapacityConstraint c : constraints.values()) {
      double util = c.getUtilization();
      if (!Double.isNaN(util) && util > maxUtil) {
        maxUtil = util;
        bottleneck = c;
      }
    }

    return bottleneck;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isWithinHardLimits(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = getConstraints(equipment);
    for (CapacityConstraint c : constraints.values()) {
      if (c.getType() == CapacityConstraint.ConstraintType.HARD && c.isViolated()) {
        return false;
      }
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isWithinSoftLimits(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = getConstraints(equipment);
    for (CapacityConstraint c : constraints.values()) {
      if (c.getUtilization() > 1.0) {
        return false;
      }
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public double getAvailableMargin(ProcessEquipmentInterface equipment) {
    CapacityConstraint bottleneck = getBottleneckConstraint(equipment);
    if (bottleneck == null) {
      return 1.0;
    }
    double util = bottleneck.getUtilization();
    return Math.max(0.0, 1.0 - util);
  }

  /**
   * Sets the maximum power ratio.
   *
   * @param maxPowerRatio the maximum power ratio
   */
  public void setMaxPowerRatio(double maxPowerRatio) {
    this.maxPowerRatio = maxPowerRatio;
  }

  /**
   * Sets the minimum speed ratio.
   *
   * @param minSpeedRatio the minimum speed ratio
   */
  public void setMinSpeedRatio(double minSpeedRatio) {
    this.minSpeedRatio = minSpeedRatio;
  }

  /**
   * Sets the maximum speed ratio.
   *
   * @param maxSpeedRatio the maximum speed ratio
   */
  public void setMaxSpeedRatio(double maxSpeedRatio) {
    this.maxSpeedRatio = maxSpeedRatio;
  }
}
