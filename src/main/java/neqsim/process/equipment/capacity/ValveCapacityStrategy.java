package neqsim.process.equipment.capacity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.valve.ValveInterface;

/**
 * Capacity strategy for valve equipment.
 *
 * <p>
 * This strategy evaluates valve capacity based on multiple constraints including:
 * <ul>
 * <li>Valve opening percentage (Cv utilization)</li>
 * <li>Pressure drop across valve</li>
 * <li>Choked flow conditions</li>
 * <li>Control authority</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ValveCapacityStrategy implements EquipmentCapacityStrategy {

  /** Default maximum valve opening for good control (%). */
  public static final double DEFAULT_MAX_OPENING = 80.0;

  /** Default minimum valve opening for good control (%). */
  public static final double DEFAULT_MIN_OPENING = 20.0;

  private double maxOpening = DEFAULT_MAX_OPENING;
  private double minOpening = DEFAULT_MIN_OPENING;

  /**
   * Default constructor.
   */
  public ValveCapacityStrategy() {}

  /**
   * Constructor with custom opening limits.
   *
   * @param minOpening minimum valve opening percentage
   * @param maxOpening maximum valve opening percentage
   */
  public ValveCapacityStrategy(double minOpening, double maxOpening) {
    this.minOpening = minOpening;
    this.maxOpening = maxOpening;
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof ThrottlingValve || equipment instanceof ValveInterface;
  }

  /** {@inheritDoc} */
  @Override
  public int getPriority() {
    return 10;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "ValveCapacityStrategy";
  }

  /** {@inheritDoc} */
  @Override
  public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
    return ThrottlingValve.class;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateCapacity(ProcessEquipmentInterface equipment) {
    if (!(equipment instanceof ThrottlingValve)) {
      return 0.0;
    }

    ThrottlingValve valve = (ThrottlingValve) equipment;

    // Use valve opening percentage as capacity indicator
    double opening = valve.getPercentValveOpening();
    if (opening > 0 && maxOpening > 0) {
      return opening / maxOpening;
    }

    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateMaxCapacity(ProcessEquipmentInterface equipment) {
    if (!(equipment instanceof ThrottlingValve)) {
      return 0.0;
    }

    ThrottlingValve valve = (ThrottlingValve) equipment;
    return valve.getMaximumValveOpening();
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = new HashMap<>();

    if (!(equipment instanceof ThrottlingValve)) {
      return constraints;
    }

    ThrottlingValve valve = (ThrottlingValve) equipment;

    // Valve opening constraint
    double maxAllowedOpening = valve.getMaximumValveOpening();
    if (maxAllowedOpening <= 0) {
      maxAllowedOpening = 100.0;
    }

    final double finalMaxOpening = maxAllowedOpening;
    CapacityConstraint openingConstraint = new CapacityConstraint("valveOpening")
        .setDesignValue(maxOpening).setMaxValue(finalMaxOpening).setMinValue(minOpening)
        .setUnit("%").setSeverity(CapacityConstraint.ConstraintSeverity.SOFT)
        .setWarningThreshold(0.9).setValueSupplier(() -> valve.getPercentValveOpening());
    constraints.put("valveOpening", openingConstraint);

    // Pressure drop constraint (control authority)
    if (valve.getInletStream() != null && valve.getOutletStream() != null) {
      // Check for excessive pressure drop (>50% of inlet is typically too much)
      CapacityConstraint dpConstraint =
          new CapacityConstraint("pressureDropRatio").setDesignValue(0.3) // 30% DP ratio as design
              .setMaxValue(0.5) // 50% as max before choked flow concerns
              .setUnit("ratio").setSeverity(CapacityConstraint.ConstraintSeverity.SOFT)
              .setValueSupplier(() -> {
                double inletP = valve.getInletStream().getPressure("bara");
                double outletP = valve.getOutletStream().getPressure("bara");
                double dp = inletP - outletP;
                return (dp > 0 && inletP > 0) ? dp / inletP : 0.0;
              });
      constraints.put("pressureDropRatio", dpConstraint);
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
      if (constraint.isHardLimitExceeded()) {
        return false;
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
   * Gets the maximum valve opening.
   *
   * @return maximum opening percentage
   */
  public double getMaxOpening() {
    return maxOpening;
  }

  /**
   * Sets the maximum valve opening.
   *
   * @param maxOpening maximum opening percentage
   */
  public void setMaxOpening(double maxOpening) {
    this.maxOpening = maxOpening;
  }

  /**
   * Gets the minimum valve opening.
   *
   * @return minimum opening percentage
   */
  public double getMinOpening() {
    return minOpening;
  }

  /**
   * Sets the minimum valve opening.
   *
   * @param minOpening minimum opening percentage
   */
  public void setMinOpening(double minOpening) {
    this.minOpening = minOpening;
  }
}
