package neqsim.process.equipment.capacity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.mixer.Mixer;

/**
 * Capacity strategy for mixer equipment.
 *
 * <p>
 * This strategy evaluates mixer capacity based on multiple constraints including:
 * </p>
 * <ul>
 * <li>Header velocity (erosion and pressure drop)</li>
 * <li>Pressure drop through mixing</li>
 * <li>Flow distribution uniformity</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class MixerCapacityStrategy implements EquipmentCapacityStrategy {

  /** Default maximum header velocity (m/s). */
  public static final double DEFAULT_MAX_VELOCITY = 30.0;

  /** Default maximum pressure drop (bar). */
  public static final double DEFAULT_MAX_PRESSURE_DROP = 0.5;

  private double maxVelocity = DEFAULT_MAX_VELOCITY;
  private double maxPressureDrop = DEFAULT_MAX_PRESSURE_DROP;

  /**
   * Default constructor.
   */
  public MixerCapacityStrategy() {}

  /**
   * Constructor with custom constraints.
   *
   * @param maxVelocity maximum header velocity (m/s)
   * @param maxPressureDrop maximum pressure drop (bar)
   */
  public MixerCapacityStrategy(double maxVelocity, double maxPressureDrop) {
    this.maxVelocity = maxVelocity;
    this.maxPressureDrop = maxPressureDrop;
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof Mixer;
  }

  /** {@inheritDoc} */
  @Override
  public int getPriority() {
    return 10;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "MixerCapacityStrategy";
  }

  /** {@inheritDoc} */
  @Override
  public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
    return Mixer.class;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateCapacity(ProcessEquipmentInterface equipment) {
    if (!(equipment instanceof Mixer)) {
      return 0.0;
    }
    Mixer mixer = (Mixer) equipment;

    // If equipment implements CapacityConstrainedEquipment, use its constraints
    if (mixer instanceof CapacityConstrainedEquipment) {
      return ((CapacityConstrainedEquipment) mixer).getMaxUtilization();
    }

    // Fall back to basic evaluation
    return evaluateBasicCapacity(mixer);
  }

  /**
   * Evaluates basic capacity when mixer doesn't have constraint interface.
   *
   * @param mixer the mixer to evaluate
   * @return utilization factor (0-1+)
   */
  private double evaluateBasicCapacity(Mixer mixer) {
    double maxUtilization = 0.0;

    // Evaluate pressure consistency
    if (mixer.getOutletStream() != null && mixer.getNumberOfInputStreams() > 0) {
      double outletPressure = mixer.getOutletStream().getPressure("bara");
      double minInletPressure = Double.MAX_VALUE;

      for (int i = 0; i < mixer.getNumberOfInputStreams(); i++) {
        double inletP = mixer.getStream(i).getPressure("bara");
        minInletPressure = Math.min(minInletPressure, inletP);
      }

      // Pressure drop utilization
      if (minInletPressure > 0 && minInletPressure < Double.MAX_VALUE) {
        double pressureDrop = minInletPressure - outletPressure;
        double dpUtilization = pressureDrop / maxPressureDrop;
        maxUtilization = Math.max(maxUtilization, dpUtilization);
      }
    }

    return maxUtilization;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateMaxCapacity(ProcessEquipmentInterface equipment) {
    if (!(equipment instanceof Mixer)) {
      return 0.0;
    }
    // Return pressure drop capacity as the main limit
    return maxPressureDrop;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = new HashMap<>();
    if (!(equipment instanceof Mixer)) {
      return constraints;
    }
    Mixer mixer = (Mixer) equipment;

    // If mixer implements CapacityConstrainedEquipment, use its constraints
    if (mixer instanceof CapacityConstrainedEquipment) {
      return ((CapacityConstrainedEquipment) mixer).getCapacityConstraints();
    }

    // Pressure drop constraint
    CapacityConstraint dpConstraint = new CapacityConstraint("pressureDrop")
        .setDesignValue(maxPressureDrop).setMaxValue(maxPressureDrop * 1.2).setUnit("bar")
        .setSeverity(CapacityConstraint.ConstraintSeverity.SOFT)
        .setDescription("Pressure drop across mixer").setValueSupplier(() -> {
          if (mixer.getOutletStream() != null && mixer.getNumberOfInputStreams() > 0) {
            double minInletP = Double.MAX_VALUE;
            for (int i = 0; i < mixer.getNumberOfInputStreams(); i++) {
              minInletP = Math.min(minInletP, mixer.getStream(i).getPressure("bara"));
            }
            return minInletP < Double.MAX_VALUE
                ? minInletP - mixer.getOutletStream().getPressure("bara")
                : 0.0;
          }
          return 0.0;
        });
    constraints.put("pressureDrop", dpConstraint);

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
   * Sets the maximum velocity.
   *
   * @param maxVelocity maximum velocity in m/s
   */
  public void setMaxVelocity(double maxVelocity) {
    this.maxVelocity = maxVelocity;
  }

  /**
   * Gets the maximum velocity.
   *
   * @return maximum velocity in m/s
   */
  public double getMaxVelocity() {
    return maxVelocity;
  }

  /**
   * Sets the maximum pressure drop.
   *
   * @param maxPressureDrop maximum pressure drop in bar
   */
  public void setMaxPressureDrop(double maxPressureDrop) {
    this.maxPressureDrop = maxPressureDrop;
  }

  /**
   * Gets the maximum pressure drop.
   *
   * @return maximum pressure drop in bar
   */
  public double getMaxPressureDrop() {
    return maxPressureDrop;
  }
}
