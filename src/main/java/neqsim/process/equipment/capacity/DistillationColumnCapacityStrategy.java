package neqsim.process.equipment.capacity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.distillation.DistillationColumn;

/**
 * Capacity strategy for distillation column equipment.
 *
 * <p>
 * This strategy evaluates distillation column capacity based on multiple constraints including:
 * </p>
 * <ul>
 * <li>Flooding factor (vapor/liquid loading)</li>
 * <li>Weir loading (liquid flow per unit weir length)</li>
 * <li>Reboiler duty utilization</li>
 * <li>Condenser duty utilization</li>
 * <li>Tray pressure drop</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class DistillationColumnCapacityStrategy implements EquipmentCapacityStrategy {

  /** Default maximum flooding factor. */
  public static final double DEFAULT_MAX_FLOODING_FACTOR = 0.85;

  /** Default maximum weir loading (m3/hr per m of weir). */
  public static final double DEFAULT_MAX_WEIR_LOADING = 90.0;

  /** Default maximum tray pressure drop (mbar/tray). */
  public static final double DEFAULT_MAX_TRAY_PRESSURE_DROP = 10.0;

  private double maxFloodingFactor = DEFAULT_MAX_FLOODING_FACTOR;
  private double maxWeirLoading = DEFAULT_MAX_WEIR_LOADING;
  private double maxTrayPressureDrop = DEFAULT_MAX_TRAY_PRESSURE_DROP;

  /**
   * Default constructor.
   */
  public DistillationColumnCapacityStrategy() {}

  /**
   * Constructor with custom constraints.
   *
   * @param maxFloodingFactor maximum flooding factor (0-1)
   * @param maxWeirLoading maximum weir loading (m3/hr per m)
   * @param maxTrayPressureDrop maximum tray pressure drop (mbar/tray)
   */
  public DistillationColumnCapacityStrategy(double maxFloodingFactor, double maxWeirLoading,
      double maxTrayPressureDrop) {
    this.maxFloodingFactor = maxFloodingFactor;
    this.maxWeirLoading = maxWeirLoading;
    this.maxTrayPressureDrop = maxTrayPressureDrop;
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof DistillationColumn;
  }

  /** {@inheritDoc} */
  @Override
  public int getPriority() {
    return 10;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "DistillationColumnCapacityStrategy";
  }

  /** {@inheritDoc} */
  @Override
  public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
    return DistillationColumn.class;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateCapacity(ProcessEquipmentInterface equipment) {
    if (!(equipment instanceof DistillationColumn)) {
      return 0.0;
    }
    DistillationColumn column = (DistillationColumn) equipment;

    // If equipment implements CapacityConstrainedEquipment, use its constraints
    if (column instanceof CapacityConstrainedEquipment) {
      return ((CapacityConstrainedEquipment) column).getMaxUtilization();
    }

    // Fall back to basic evaluation using reboiler/condenser duties
    return evaluateBasicCapacity(column);
  }

  /**
   * Evaluates basic capacity when column doesn't have constraint interface.
   *
   * @param column the distillation column to evaluate
   * @return utilization factor (0-1+)
   */
  private double evaluateBasicCapacity(DistillationColumn column) {
    double maxUtilization = 0.0;

    // Use reboiler duty as a proxy for column loading
    if (column.getReboiler() != null) {
      double reboilerDuty = column.getReboiler().getDuty();
      if (reboilerDuty > 0) {
        // Estimate utilization based on typical design margins
        maxUtilization = 0.7; // Assume 70% of design capacity without detailed calculations
      }
    }

    // Use condenser duty
    if (column.getCondenser() != null) {
      double condenserDuty = column.getCondenser().getDuty();
      if (condenserDuty < 0) { // Condenser duty is typically negative
        maxUtilization = Math.max(maxUtilization, 0.7);
      }
    }

    return maxUtilization;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateMaxCapacity(ProcessEquipmentInterface equipment) {
    if (!(equipment instanceof DistillationColumn)) {
      return 0.0;
    }
    // Return flooding factor as the main capacity indicator
    return maxFloodingFactor;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = new HashMap<>();
    if (!(equipment instanceof DistillationColumn)) {
      return constraints;
    }
    DistillationColumn column = (DistillationColumn) equipment;

    // If column implements CapacityConstrainedEquipment, use its constraints
    if (column instanceof CapacityConstrainedEquipment) {
      return ((CapacityConstrainedEquipment) column).getCapacityConstraints();
    }

    // Flooding factor constraint
    CapacityConstraint floodConstraint = new CapacityConstraint("floodingFactor")
        .setDesignValue(maxFloodingFactor * 0.8).setMaxValue(maxFloodingFactor).setUnit("-")
        .setSeverity(CapacityConstraint.ConstraintSeverity.HARD)
        .setDescription("Tray flooding factor");
    constraints.put("floodingFactor", floodConstraint);

    // Weir loading constraint
    CapacityConstraint weirConstraint = new CapacityConstraint("weirLoading")
        .setDesignValue(maxWeirLoading * 0.7).setMaxValue(maxWeirLoading).setUnit("m3/hr/m")
        .setSeverity(CapacityConstraint.ConstraintSeverity.SOFT)
        .setDescription("Liquid weir loading");
    constraints.put("weirLoading", weirConstraint);

    // Tray pressure drop constraint
    CapacityConstraint dpConstraint = new CapacityConstraint("trayPressureDrop")
        .setDesignValue(maxTrayPressureDrop * 0.7).setMaxValue(maxTrayPressureDrop)
        .setUnit("mbar/tray").setSeverity(CapacityConstraint.ConstraintSeverity.SOFT)
        .setDescription("Tray pressure drop");
    constraints.put("trayPressureDrop", dpConstraint);

    // Reboiler duty constraint
    if (column.getReboiler() != null) {
      CapacityConstraint reboilerConstraint = new CapacityConstraint("reboilerDuty").setUnit("kW")
          .setSeverity(CapacityConstraint.ConstraintSeverity.SOFT)
          .setDescription("Reboiler heat duty")
          .setValueSupplier(() -> column.getReboiler().getDuty());
      constraints.put("reboilerDuty", reboilerConstraint);
    }

    // Condenser duty constraint
    if (column.getCondenser() != null) {
      CapacityConstraint condenserConstraint = new CapacityConstraint("condenserDuty").setUnit("kW")
          .setSeverity(CapacityConstraint.ConstraintSeverity.SOFT)
          .setDescription("Condenser heat duty")
          .setValueSupplier(() -> column.getCondenser().getDuty());
      constraints.put("condenserDuty", condenserConstraint);
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

  /**
   * Sets the maximum flooding factor.
   *
   * @param maxFloodingFactor maximum flooding factor (0-1)
   */
  public void setMaxFloodingFactor(double maxFloodingFactor) {
    this.maxFloodingFactor = maxFloodingFactor;
  }

  /**
   * Gets the maximum flooding factor.
   *
   * @return maximum flooding factor (0-1)
   */
  public double getMaxFloodingFactor() {
    return maxFloodingFactor;
  }

  /**
   * Sets the maximum weir loading.
   *
   * @param maxWeirLoading maximum weir loading (m3/hr per m)
   */
  public void setMaxWeirLoading(double maxWeirLoading) {
    this.maxWeirLoading = maxWeirLoading;
  }

  /**
   * Gets the maximum weir loading.
   *
   * @return maximum weir loading (m3/hr per m)
   */
  public double getMaxWeirLoading() {
    return maxWeirLoading;
  }

  /**
   * Sets the maximum tray pressure drop.
   *
   * @param maxTrayPressureDrop maximum tray pressure drop (mbar/tray)
   */
  public void setMaxTrayPressureDrop(double maxTrayPressureDrop) {
    this.maxTrayPressureDrop = maxTrayPressureDrop;
  }

  /**
   * Gets the maximum tray pressure drop.
   *
   * @return maximum tray pressure drop (mbar/tray)
   */
  public double getMaxTrayPressureDrop() {
    return maxTrayPressureDrop;
  }
}
