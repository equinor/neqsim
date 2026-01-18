package neqsim.process.equipment.capacity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.ejector.Ejector;

/**
 * Capacity strategy for ejector equipment.
 *
 * <p>
 * This strategy evaluates ejector capacity based on multiple constraints including:
 * </p>
 * <ul>
 * <li>Entrainment ratio (suction/motive flow)</li>
 * <li>Motive flow rate limits</li>
 * <li>Suction pressure limits</li>
 * <li>Discharge pressure limits</li>
 * <li>Isentropic efficiency</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class EjectorCapacityStrategy implements EquipmentCapacityStrategy {

  /** Default maximum entrainment ratio. */
  public static final double DEFAULT_MAX_ENTRAINMENT_RATIO = 3.0;

  /** Default minimum suction pressure (bara). */
  public static final double DEFAULT_MIN_SUCTION_PRESSURE = 0.5;

  /** Default maximum motive flow rate (kg/hr). */
  public static final double DEFAULT_MAX_MOTIVE_FLOW = 100000.0;

  private double maxEntrainmentRatio = DEFAULT_MAX_ENTRAINMENT_RATIO;
  private double minSuctionPressure = DEFAULT_MIN_SUCTION_PRESSURE;
  private double maxMotiveFlowRate = DEFAULT_MAX_MOTIVE_FLOW;

  /**
   * Default constructor.
   */
  public EjectorCapacityStrategy() {}

  /**
   * Constructor with custom constraints.
   *
   * @param maxEntrainmentRatio maximum entrainment ratio
   * @param minSuctionPressure minimum suction pressure in bara
   * @param maxMotiveFlowRate maximum motive flow rate in kg/hr
   */
  public EjectorCapacityStrategy(double maxEntrainmentRatio, double minSuctionPressure,
      double maxMotiveFlowRate) {
    this.maxEntrainmentRatio = maxEntrainmentRatio;
    this.minSuctionPressure = minSuctionPressure;
    this.maxMotiveFlowRate = maxMotiveFlowRate;
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof Ejector;
  }

  /** {@inheritDoc} */
  @Override
  public int getPriority() {
    return 10;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "EjectorCapacityStrategy";
  }

  /** {@inheritDoc} */
  @Override
  public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
    return Ejector.class;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateCapacity(ProcessEquipmentInterface equipment) {
    if (!(equipment instanceof Ejector)) {
      return 0.0;
    }

    Ejector ejector = (Ejector) equipment;

    // Check if ejector implements CapacityConstrainedEquipment
    if (ejector instanceof CapacityConstrainedEquipment) {
      return ((CapacityConstrainedEquipment) ejector).getMaxUtilization();
    }

    // Calculate entrainment ratio as primary capacity indicator
    double entrainmentRatio = ejector.getEntrainmentRatio();
    if (entrainmentRatio > 0 && maxEntrainmentRatio > 0) {
      return entrainmentRatio / maxEntrainmentRatio;
    }

    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateMaxCapacity(ProcessEquipmentInterface equipment) {
    if (!(equipment instanceof Ejector)) {
      return 0.0;
    }

    // For ejectors, max capacity is based on motive stream capability
    Ejector ejector = (Ejector) equipment;
    if (ejector.getMotiveStream() != null) {
      return ejector.getMotiveStream().getFlowRate("kg/hr") * maxEntrainmentRatio;
    }
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = new HashMap<>();

    if (!(equipment instanceof Ejector)) {
      return constraints;
    }

    Ejector ejector = (Ejector) equipment;

    // If ejector already implements CapacityConstrainedEquipment, use its constraints
    if (ejector instanceof CapacityConstrainedEquipment) {
      return ((CapacityConstrainedEquipment) ejector).getCapacityConstraints();
    }

    // Entrainment ratio constraint
    CapacityConstraint entrainmentConstraint =
        new CapacityConstraint("entrainmentRatio", "ratio", CapacityConstraint.ConstraintType.SOFT)
            .setDesignValue(maxEntrainmentRatio * 0.9).setMaxValue(maxEntrainmentRatio)
            .setDescription("Suction to motive mass flow ratio")
            .setValueSupplier(() -> ejector.getEntrainmentRatio());
    constraints.put("entrainmentRatio", entrainmentConstraint);

    // Efficiency constraint (informational)
    CapacityConstraint efficiencyConstraint =
        new CapacityConstraint("efficiency", "fraction", CapacityConstraint.ConstraintType.SOFT)
            .setDesignValue(0.75).setMaxValue(1.0).setDescription("Isentropic efficiency")
            .setValueSupplier(() -> ejector.getEfficiencyIsentropic());
    constraints.put("efficiency", efficiencyConstraint);

    // Suction pressure constraint (minimum)
    if (ejector.getSuctionStream() != null) {
      CapacityConstraint suctionPressureConstraint =
          new CapacityConstraint("suctionPressure", "bara", CapacityConstraint.ConstraintType.HARD)
              .setMinValue(minSuctionPressure).setDesignValue(minSuctionPressure * 2)
              .setMaxValue(Double.MAX_VALUE).setDescription("Minimum suction pressure")
              .setValueSupplier(() -> ejector.getSuctionStream().getPressure("bara"));
      constraints.put("suctionPressure", suctionPressureConstraint);
    }

    // Motive flow rate constraint
    if (ejector.getMotiveStream() != null) {
      CapacityConstraint motiveFlowConstraint =
          new CapacityConstraint("motiveFlowRate", "kg/hr", CapacityConstraint.ConstraintType.SOFT)
              .setDesignValue(maxMotiveFlowRate * 0.9).setMaxValue(maxMotiveFlowRate)
              .setDescription("Motive stream flow rate")
              .setValueSupplier(() -> ejector.getMotiveStream().getFlowRate("kg/hr"));
      constraints.put("motiveFlowRate", motiveFlowConstraint);
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
   * Sets the maximum entrainment ratio.
   *
   * @param maxEntrainmentRatio the maximum entrainment ratio
   */
  public void setMaxEntrainmentRatio(double maxEntrainmentRatio) {
    this.maxEntrainmentRatio = maxEntrainmentRatio;
  }

  /**
   * Sets the minimum suction pressure.
   *
   * @param minSuctionPressure the minimum suction pressure in bara
   */
  public void setMinSuctionPressure(double minSuctionPressure) {
    this.minSuctionPressure = minSuctionPressure;
  }

  /**
   * Sets the maximum motive flow rate.
   *
   * @param maxMotiveFlowRate the maximum motive flow rate in kg/hr
   */
  public void setMaxMotiveFlowRate(double maxMotiveFlowRate) {
    this.maxMotiveFlowRate = maxMotiveFlowRate;
  }
}
