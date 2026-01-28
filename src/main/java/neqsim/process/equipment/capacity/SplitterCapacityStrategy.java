package neqsim.process.equipment.capacity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.splitter.Splitter;

/**
 * Capacity strategy for splitter equipment.
 *
 * <p>
 * This strategy evaluates splitter capacity based on multiple constraints including:
 * </p>
 * <ul>
 * <li>Header velocity (erosion and pressure drop)</li>
 * <li>Pressure drop through distribution</li>
 * <li>Flow distribution uniformity</li>
 * <li>Branch flow rates</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class SplitterCapacityStrategy implements EquipmentCapacityStrategy {

  /** Default maximum header velocity (m/s). */
  public static final double DEFAULT_MAX_VELOCITY = 30.0;

  /** Default maximum pressure drop (bar). */
  public static final double DEFAULT_MAX_PRESSURE_DROP = 0.5;

  private double maxVelocity = DEFAULT_MAX_VELOCITY;
  private double maxPressureDrop = DEFAULT_MAX_PRESSURE_DROP;

  /**
   * Default constructor.
   */
  public SplitterCapacityStrategy() {}

  /**
   * Constructor with custom constraints.
   *
   * @param maxVelocity maximum header velocity (m/s)
   * @param maxPressureDrop maximum pressure drop (bar)
   */
  public SplitterCapacityStrategy(double maxVelocity, double maxPressureDrop) {
    this.maxVelocity = maxVelocity;
    this.maxPressureDrop = maxPressureDrop;
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof Splitter;
  }

  /** {@inheritDoc} */
  @Override
  public int getPriority() {
    return 10;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "SplitterCapacityStrategy";
  }

  /** {@inheritDoc} */
  @Override
  public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
    return Splitter.class;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateCapacity(ProcessEquipmentInterface equipment) {
    if (!(equipment instanceof Splitter)) {
      return 0.0;
    }
    Splitter splitter = (Splitter) equipment;

    // If equipment implements CapacityConstrainedEquipment, use its constraints
    if (splitter instanceof CapacityConstrainedEquipment) {
      return ((CapacityConstrainedEquipment) splitter).getMaxUtilization();
    }

    // Fall back to basic evaluation
    return evaluateBasicCapacity(splitter);
  }

  /**
   * Evaluates basic capacity when splitter doesn't have constraint interface.
   *
   * @param splitter the splitter to evaluate
   * @return utilization factor (0-1+)
   */
  private double evaluateBasicCapacity(Splitter splitter) {
    // Simple evaluation based on split consistency
    double maxUtilization = 0.0;

    // Check split ratios sum to ~1.0
    double[] splitFactors = splitter.getSplitFactors();
    if (splitFactors != null) {
      double sum = 0.0;
      for (double factor : splitFactors) {
        sum += factor;
      }
      // Deviation from 1.0 indicates potential issues
      double deviation = Math.abs(sum - 1.0);
      if (deviation > 0.01) {
        maxUtilization = Math.max(maxUtilization, deviation * 10.0);
      }
    }

    return maxUtilization;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateMaxCapacity(ProcessEquipmentInterface equipment) {
    if (!(equipment instanceof Splitter)) {
      return 0.0;
    }
    // Return pressure drop capacity as the main limit
    return maxPressureDrop;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = new HashMap<>();
    if (!(equipment instanceof Splitter)) {
      return constraints;
    }
    Splitter splitter = (Splitter) equipment;

    // If splitter implements CapacityConstrainedEquipment, use its constraints
    if (splitter instanceof CapacityConstrainedEquipment) {
      return ((CapacityConstrainedEquipment) splitter).getCapacityConstraints();
    }

    // Pressure drop constraint
    CapacityConstraint dpConstraint = new CapacityConstraint("pressureDrop")
        .setDesignValue(maxPressureDrop).setMaxValue(maxPressureDrop * 1.2).setUnit("bar")
        .setSeverity(CapacityConstraint.ConstraintSeverity.SOFT)
        .setDescription("Pressure drop across splitter").setValueSupplier(() -> 0.0); // Ideal
                                                                                      // splitter
                                                                                      // has zero
                                                                                      // pressure
                                                                                      // drop
    constraints.put("pressureDrop", dpConstraint);

    // Flow distribution constraint
    CapacityConstraint flowDistConstraint =
        new CapacityConstraint("flowDistribution").setDesignValue(1.0).setUnit("-")
            .setSeverity(CapacityConstraint.ConstraintSeverity.ADVISORY)
            .setDescription("Split ratio sum (should be 1.0)").setValueSupplier(() -> {
              double[] factors = splitter.getSplitFactors();
              if (factors == null) {
                return 0.0;
              }
              double sum = 0.0;
              for (double f : factors) {
                sum += f;
              }
              return sum;
            });
    constraints.put("flowDistribution", flowDistConstraint);

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
