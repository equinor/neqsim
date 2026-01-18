package neqsim.process.equipment.capacity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;

/**
 * Capacity strategy for compressor equipment.
 *
 * <p>
 * This strategy evaluates compressor capacity based on multiple constraints including:
 * <ul>
 * <li>Speed limits (min/max RPM)</li>
 * <li>Power limits (driver rated power)</li>
 * <li>Surge margin (minimum flow)</li>
 * <li>Stonewall margin (maximum flow)</li>
 * <li>Discharge temperature limits</li>
 * <li>Polytropic head limits</li>
 * </ul>
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CompressorCapacityStrategy implements EquipmentCapacityStrategy {

  /** Default minimum surge margin (10%). */
  public static final double DEFAULT_MIN_SURGE_MARGIN = 0.10;

  /** Default minimum stonewall margin (5%). */
  public static final double DEFAULT_MIN_STONEWALL_MARGIN = 0.05;

  /** Default maximum discharge temperature (200°C). */
  public static final double DEFAULT_MAX_DISCHARGE_TEMP = 200.0;

  private double minSurgeMargin = DEFAULT_MIN_SURGE_MARGIN;
  private double minStonewallMargin = DEFAULT_MIN_STONEWALL_MARGIN;
  private double maxDischargeTemp = DEFAULT_MAX_DISCHARGE_TEMP;

  /**
   * Default constructor.
   */
  public CompressorCapacityStrategy() {}

  /**
   * Constructor with custom constraints.
   *
   * @param minSurgeMargin minimum surge margin as fraction
   * @param minStonewallMargin minimum stonewall margin as fraction
   * @param maxDischargeTemp maximum discharge temperature in Celsius
   */
  public CompressorCapacityStrategy(double minSurgeMargin, double minStonewallMargin,
      double maxDischargeTemp) {
    this.minSurgeMargin = minSurgeMargin;
    this.minStonewallMargin = minStonewallMargin;
    this.maxDischargeTemp = maxDischargeTemp;
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof Compressor;
  }

  /** {@inheritDoc} */
  @Override
  public int getPriority() {
    return 10; // High priority for specific equipment
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "CompressorCapacityStrategy";
  }

  /** {@inheritDoc} */
  @Override
  public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
    return Compressor.class;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateCapacity(ProcessEquipmentInterface equipment) {
    if (!(equipment instanceof Compressor)) {
      return 0.0;
    }

    Compressor comp = (Compressor) equipment;

    // Check if compressor implements CapacityConstrainedEquipment
    if (comp instanceof CapacityConstrainedEquipment) {
      return ((CapacityConstrainedEquipment) comp).getMaxUtilization();
    }

    // Fallback: use power utilization
    double power = comp.getPower("kW");
    double maxPower = getMaxPower(comp);
    if (maxPower > 0 && power > 0) {
      return power / maxPower;
    }

    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateMaxCapacity(ProcessEquipmentInterface equipment) {
    if (!(equipment instanceof Compressor)) {
      return 0.0;
    }

    Compressor comp = (Compressor) equipment;
    return getMaxPower(comp);
  }

  /**
   * Gets the maximum power for a compressor.
   *
   * <p>
   * Tries to get max power from driver first, then from mechanical design.
   * </p>
   *
   * @param comp the compressor
   * @return maximum power in kW, or 0 if not available
   */
  private double getMaxPower(Compressor comp) {
    // Try to get from driver
    if (comp.getDriver() != null) {
      double maxPower = comp.getDriver().getMaxAvailablePower();
      if (maxPower > 0) {
        return maxPower;
      }
      maxPower = comp.getDriver().getRatedPower();
      if (maxPower > 0) {
        return maxPower;
      }
    }
    // Fallback to mechanical design
    if (comp.getMechanicalDesign() != null) {
      double maxPower = comp.getMechanicalDesign().maxDesignPower;
      if (maxPower > 0) {
        return maxPower;
      }
    }
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = new HashMap<>();

    if (!(equipment instanceof Compressor)) {
      return constraints;
    }

    Compressor comp = (Compressor) equipment;

    // If compressor already implements CapacityConstrainedEquipment, use its constraints
    if (comp instanceof CapacityConstrainedEquipment) {
      return ((CapacityConstrainedEquipment) comp).getCapacityConstraints();
    }

    // Otherwise, build constraints manually
    // Speed constraint
    double speed = comp.getSpeed();
    double maxSpeed = comp.getMaximumSpeed();
    double minSpeed = comp.getMinimumSpeed();
    if (maxSpeed > 0) {
      CapacityConstraint speedConstraint = StandardConstraintType.COMPRESSOR_SPEED
          .createConstraint().setDesignValue(maxSpeed).setMaxValue(maxSpeed * 1.05)
          .setMinValue(minSpeed).setValueSupplier(() -> comp.getSpeed());
      constraints.put("speed", speedConstraint);
    }

    // Power constraint
    double power = comp.getPower("kW");
    double maxPower = getMaxPower(comp);
    if (maxPower > 0) {
      CapacityConstraint powerConstraint =
          StandardConstraintType.COMPRESSOR_POWER.createConstraint().setDesignValue(maxPower)
              .setMaxValue(maxPower * 1.1).setValueSupplier(() -> comp.getPower("kW"));
      constraints.put("power", powerConstraint);
    }

    // Surge margin constraint
    double surgeMargin = comp.getDistanceToSurge();
    if (!Double.isNaN(surgeMargin) && !Double.isInfinite(surgeMargin)) {
      CapacityConstraint surgeConstraint =
          StandardConstraintType.COMPRESSOR_SURGE_MARGIN.createConstraint().setDesignValue(100.0)
              .setMinValue(minSurgeMargin * 100.0).setValueSupplier(() -> {
                double margin = comp.getDistanceToSurge();
                return margin > 0 ? 100.0 / (1.0 + margin) : 100.0;
              });
      constraints.put("surgeMargin", surgeConstraint);
    }

    // Stonewall margin constraint
    double stonewallMargin = comp.getDistanceToStoneWall();
    if (!Double.isNaN(stonewallMargin) && !Double.isInfinite(stonewallMargin)) {
      CapacityConstraint stonewallConstraint = StandardConstraintType.COMPRESSOR_STONEWALL_MARGIN
          .createConstraint().setDesignValue(100.0).setMinValue(minStonewallMargin * 100.0)
          .setValueSupplier(() -> {
            double margin = comp.getDistanceToStoneWall();
            return margin > 0 ? 100.0 / (1.0 + margin) : 100.0;
          });
      constraints.put("stonewallMargin", stonewallConstraint);
    }

    // Discharge temperature constraint
    if (comp.getOutletStream() != null && comp.getOutletStream().getThermoSystem() != null) {
      CapacityConstraint tempConstraint = new CapacityConstraint("dischargeTemperature")
          .setDesignValue(maxDischargeTemp).setMaxValue(maxDischargeTemp * 1.1).setUnit("C")
          .setSeverity(CapacityConstraint.ConstraintSeverity.HARD)
          .setValueSupplier(() -> comp.getOutletStream().getTemperature("C"));
      constraints.put("dischargeTemperature", tempConstraint);
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
    if (!(equipment instanceof Compressor)) {
      return true;
    }

    Compressor comp = (Compressor) equipment;

    // Check surge
    if (comp.isSurge()) {
      return false;
    }

    // Check stonewall
    if (comp.isStoneWall()) {
      return false;
    }

    // Check hard constraint violations
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
   * Gets the minimum surge margin.
   *
   * @return minimum surge margin as fraction
   */
  public double getMinSurgeMargin() {
    return minSurgeMargin;
  }

  /**
   * Sets the minimum surge margin.
   *
   * @param minSurgeMargin minimum surge margin as fraction
   */
  public void setMinSurgeMargin(double minSurgeMargin) {
    this.minSurgeMargin = minSurgeMargin;
  }

  /**
   * Gets the minimum stonewall margin.
   *
   * @return minimum stonewall margin as fraction
   */
  public double getMinStonewallMargin() {
    return minStonewallMargin;
  }

  /**
   * Sets the minimum stonewall margin.
   *
   * @param minStonewallMargin minimum stonewall margin as fraction
   */
  public void setMinStonewallMargin(double minStonewallMargin) {
    this.minStonewallMargin = minStonewallMargin;
  }

  /**
   * Gets the maximum discharge temperature.
   *
   * @return maximum discharge temperature in Celsius
   */
  public double getMaxDischargeTemp() {
    return maxDischargeTemp;
  }

  /**
   * Sets the maximum discharge temperature.
   *
   * @param maxDischargeTemp maximum discharge temperature in Celsius
   */
  public void setMaxDischargeTemp(double maxDischargeTemp) {
    this.maxDischargeTemp = maxDischargeTemp;
  }
}
