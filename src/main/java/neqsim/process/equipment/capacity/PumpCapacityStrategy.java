package neqsim.process.equipment.capacity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.pump.PumpInterface;

/**
 * Capacity strategy for pump equipment.
 *
 * <p>
 * This strategy evaluates pump capacity based on multiple constraints including:
 * <ul>
 * <li>NPSH margin (Net Positive Suction Head)</li>
 * <li>Power consumption</li>
 * <li>Speed limits</li>
 * <li>Flow rate limits</li>
 * <li>Head limits</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class PumpCapacityStrategy implements EquipmentCapacityStrategy {

  /** Default minimum NPSH margin (m). */
  public static final double DEFAULT_MIN_NPSH_MARGIN = 1.0;

  /** Default maximum power overload factor. */
  public static final double DEFAULT_MAX_POWER_FACTOR = 1.1;

  private double minNpshMargin = DEFAULT_MIN_NPSH_MARGIN;
  private double maxPowerFactor = DEFAULT_MAX_POWER_FACTOR;

  /**
   * Default constructor.
   */
  public PumpCapacityStrategy() {}

  /**
   * Constructor with custom constraints.
   *
   * @param minNpshMargin minimum NPSH margin in meters
   * @param maxPowerFactor maximum power factor (1.1 = 110% of rated)
   */
  public PumpCapacityStrategy(double minNpshMargin, double maxPowerFactor) {
    this.minNpshMargin = minNpshMargin;
    this.maxPowerFactor = maxPowerFactor;
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof Pump || equipment instanceof PumpInterface;
  }

  /** {@inheritDoc} */
  @Override
  public int getPriority() {
    return 10;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "PumpCapacityStrategy";
  }

  /** {@inheritDoc} */
  @Override
  public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
    return Pump.class;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateCapacity(ProcessEquipmentInterface equipment) {
    if (!(equipment instanceof Pump)) {
      return 0.0;
    }

    Pump pump = (Pump) equipment;

    // Check if pump implements CapacityConstrainedEquipment
    if (pump instanceof CapacityConstrainedEquipment) {
      return ((CapacityConstrainedEquipment) pump).getMaxUtilization();
    }

    // Use power utilization as primary capacity indicator
    double power = pump.getPower("kW");
    double maxPower = pump.getCapacityMax();

    if (maxPower > 0 && power > 0) {
      return power / maxPower;
    }

    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateMaxCapacity(ProcessEquipmentInterface equipment) {
    if (!(equipment instanceof Pump)) {
      return 0.0;
    }

    return ((Pump) equipment).getCapacityMax();
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = new HashMap<>();

    if (!(equipment instanceof Pump)) {
      return constraints;
    }

    Pump pump = (Pump) equipment;

    // Power constraint
    double maxPower = pump.getCapacityMax();

    if (maxPower > 0) {
      CapacityConstraint powerConstraint = new CapacityConstraint("power").setDesignValue(maxPower)
          .setMaxValue(maxPower * maxPowerFactor).setUnit("kW")
          .setSeverity(CapacityConstraint.ConstraintSeverity.HARD).setWarningThreshold(0.9)
          .setValueSupplier(() -> pump.getPower("kW"));
      constraints.put("power", powerConstraint);
    }

    // NPSH margin constraint
    double npshAvailable = pump.getNPSHAvailable();
    double npshRequired = pump.getNPSHRequired();
    double npshMargin = npshAvailable - npshRequired;
    if (npshAvailable > 0 && npshRequired > 0) {
      CapacityConstraint npshConstraint = new CapacityConstraint("npshMargin")
          .setDesignValue(minNpshMargin).setMinValue(minNpshMargin).setUnit("m")
          .setSeverity(CapacityConstraint.ConstraintSeverity.HARD)
          .setValueSupplier(() -> pump.getNPSHAvailable() - pump.getNPSHRequired());
      constraints.put("npshMargin", npshConstraint);
    }

    // Flow rate constraint (using minimum flow as lower limit)
    double minFlow = pump.getMinimumFlow();
    if (minFlow > 0 && pump.getInletStream() != null
        && pump.getInletStream().getThermoSystem() != null) {
      CapacityConstraint flowConstraint = new CapacityConstraint("flowRate").setMinValue(minFlow)
          .setUnit("m3/hr").setSeverity(CapacityConstraint.ConstraintSeverity.SOFT)
          .setValueSupplier(() -> pump.getInletStream().getFlowRate("m3/hr"));
      constraints.put("flowRate", flowConstraint);
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
   * Gets the minimum NPSH margin.
   *
   * @return minimum NPSH margin in meters
   */
  public double getMinNpshMargin() {
    return minNpshMargin;
  }

  /**
   * Sets the minimum NPSH margin.
   *
   * @param minNpshMargin minimum NPSH margin in meters
   */
  public void setMinNpshMargin(double minNpshMargin) {
    this.minNpshMargin = minNpshMargin;
  }

  /**
   * Gets the maximum power factor.
   *
   * @return maximum power factor
   */
  public double getMaxPowerFactor() {
    return maxPowerFactor;
  }

  /**
   * Sets the maximum power factor.
   *
   * @param maxPowerFactor maximum power factor
   */
  public void setMaxPowerFactor(double maxPowerFactor) {
    this.maxPowerFactor = maxPowerFactor;
  }
}
