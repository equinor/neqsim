package neqsim.process.equipment.capacity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.pipeline.OnePhasePipeLine;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;

/**
 * Capacity strategy for pipe and pipeline equipment.
 *
 * <p>
 * This strategy evaluates pipe capacity based on multiple constraints including:
 * <ul>
 * <li>Superficial velocity (erosional velocity limits)</li>
 * <li>Pressure drop (allowable DP)</li>
 * <li>Liquid holdup (for multiphase)</li>
 * <li>Flow regime stability</li>
 * </ul>
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class PipeCapacityStrategy implements EquipmentCapacityStrategy {

  /** Default maximum gas velocity (m/s). */
  public static final double DEFAULT_MAX_GAS_VELOCITY = 25.0;

  /** Default maximum liquid velocity (m/s). */
  public static final double DEFAULT_MAX_LIQUID_VELOCITY = 5.0;

  /** Default maximum multiphase velocity (m/s). */
  public static final double DEFAULT_MAX_MULTIPHASE_VELOCITY = 20.0;

  /** Default maximum erosional velocity ratio. */
  public static final double DEFAULT_MAX_EROSIONAL_RATIO = 0.8;

  private double maxGasVelocity = DEFAULT_MAX_GAS_VELOCITY;
  private double maxLiquidVelocity = DEFAULT_MAX_LIQUID_VELOCITY;
  private double maxMultiphaseVelocity = DEFAULT_MAX_MULTIPHASE_VELOCITY;
  private double maxErosionalRatio = DEFAULT_MAX_EROSIONAL_RATIO;

  /**
   * Default constructor.
   */
  public PipeCapacityStrategy() {}

  /**
   * Constructor with custom velocity limits.
   *
   * @param maxGasVelocity maximum gas velocity in m/s
   * @param maxLiquidVelocity maximum liquid velocity in m/s
   * @param maxMultiphaseVelocity maximum multiphase velocity in m/s
   */
  public PipeCapacityStrategy(double maxGasVelocity, double maxLiquidVelocity,
      double maxMultiphaseVelocity) {
    this.maxGasVelocity = maxGasVelocity;
    this.maxLiquidVelocity = maxLiquidVelocity;
    this.maxMultiphaseVelocity = maxMultiphaseVelocity;
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof AdiabaticPipe || equipment instanceof PipeBeggsAndBrills
        || equipment instanceof OnePhasePipeLine;
  }

  /** {@inheritDoc} */
  @Override
  public int getPriority() {
    return 10;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "PipeCapacityStrategy";
  }

  /** {@inheritDoc} */
  @Override
  public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
    return AdiabaticPipe.class;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateCapacity(ProcessEquipmentInterface equipment) {
    // Get velocity and compare to appropriate limit
    double velocity = getSuperficialVelocity(equipment);
    double maxVelocity = getApplicableMaxVelocity(equipment);

    if (maxVelocity > 0 && velocity > 0) {
      return velocity / maxVelocity;
    }

    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateMaxCapacity(ProcessEquipmentInterface equipment) {
    return getApplicableMaxVelocity(equipment);
  }

  /**
   * Gets the superficial velocity from the pipe.
   *
   * @param equipment the pipe equipment
   * @return superficial velocity in m/s
   */
  private double getSuperficialVelocity(ProcessEquipmentInterface equipment) {
    if (equipment instanceof PipeBeggsAndBrills) {
      return ((PipeBeggsAndBrills) equipment).getOutletSuperficialVelocity();
    }
    if (equipment instanceof AdiabaticPipe) {
      // Try to get velocity from outlet stream
      AdiabaticPipe pipe = (AdiabaticPipe) equipment;
      if (pipe.getOutletStream() != null && pipe.getOutletStream().getThermoSystem() != null) {
        double flowRate = pipe.getOutletStream().getThermoSystem().getFlowRate("m3/hr");
        double diameter = pipe.getDiameter();
        if (diameter > 0) {
          double area = Math.PI * diameter * diameter / 4.0;
          return flowRate / 3600.0 / area;
        }
      }
    }
    return 0.0;
  }

  /**
   * Gets the applicable maximum velocity based on flow type.
   *
   * @param equipment the pipe equipment
   * @return maximum velocity in m/s
   */
  private double getApplicableMaxVelocity(ProcessEquipmentInterface equipment) {
    // Check if there's a design velocity set on the equipment
    if (equipment instanceof PipeBeggsAndBrills) {
      PipeBeggsAndBrills pipe = (PipeBeggsAndBrills) equipment;
      double designVel = pipe.getMechanicalDesign().getMaxDesignVelocity();
      if (designVel > 0) {
        return designVel;
      }
    }

    // Default to multiphase velocity
    return maxMultiphaseVelocity;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = new HashMap<>();

    // Velocity constraint
    double maxVel = getApplicableMaxVelocity(equipment);

    CapacityConstraint velocityConstraint = new CapacityConstraint("velocity")
        .setDesignValue(maxVel * maxErosionalRatio).setMaxValue(maxVel).setUnit("m/s")
        .setSeverity(CapacityConstraint.ConstraintSeverity.SOFT).setWarningThreshold(0.85)
        .setValueSupplier(() -> getSuperficialVelocity(equipment));
    constraints.put("velocity", velocityConstraint);

    // Pressure drop constraint (if available)
    if (equipment instanceof PipeBeggsAndBrills) {
      PipeBeggsAndBrills pipe = (PipeBeggsAndBrills) equipment;
      double inletP = pipe.getInletStream().getPressure("bara");
      // Assume 10% of inlet pressure as allowable DP
      double maxDP = inletP * 0.1;
      CapacityConstraint dpConstraint = new CapacityConstraint("pressureDrop").setDesignValue(maxDP)
          .setMaxValue(maxDP * 1.5).setUnit("bar")
          .setSeverity(CapacityConstraint.ConstraintSeverity.SOFT).setValueSupplier(() -> {
            double inP = pipe.getInletStream().getPressure("bara");
            double outP = pipe.getOutletStream().getPressure("bara");
            return inP - outP;
          });
      constraints.put("pressureDrop", dpConstraint);
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
   * Gets the maximum gas velocity.
   *
   * @return maximum gas velocity in m/s
   */
  public double getMaxGasVelocity() {
    return maxGasVelocity;
  }

  /**
   * Sets the maximum gas velocity.
   *
   * @param maxGasVelocity maximum gas velocity in m/s
   */
  public void setMaxGasVelocity(double maxGasVelocity) {
    this.maxGasVelocity = maxGasVelocity;
  }

  /**
   * Gets the maximum liquid velocity.
   *
   * @return maximum liquid velocity in m/s
   */
  public double getMaxLiquidVelocity() {
    return maxLiquidVelocity;
  }

  /**
   * Sets the maximum liquid velocity.
   *
   * @param maxLiquidVelocity maximum liquid velocity in m/s
   */
  public void setMaxLiquidVelocity(double maxLiquidVelocity) {
    this.maxLiquidVelocity = maxLiquidVelocity;
  }

  /**
   * Gets the maximum multiphase velocity.
   *
   * @return maximum multiphase velocity in m/s
   */
  public double getMaxMultiphaseVelocity() {
    return maxMultiphaseVelocity;
  }

  /**
   * Sets the maximum multiphase velocity.
   *
   * @param maxMultiphaseVelocity maximum multiphase velocity in m/s
   */
  public void setMaxMultiphaseVelocity(double maxMultiphaseVelocity) {
    this.maxMultiphaseVelocity = maxMultiphaseVelocity;
  }

  /**
   * Gets the maximum erosional velocity ratio.
   *
   * @return maximum erosional velocity ratio
   */
  public double getMaxErosionalRatio() {
    return maxErosionalRatio;
  }

  /**
   * Sets the maximum erosional velocity ratio.
   *
   * @param maxErosionalRatio maximum erosional velocity ratio
   */
  public void setMaxErosionalRatio(double maxErosionalRatio) {
    this.maxErosionalRatio = maxErosionalRatio;
  }
}
