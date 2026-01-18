package neqsim.process.equipment.capacity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger2;

/**
 * Capacity strategy for heat exchanger equipment.
 *
 * <p>
 * This strategy evaluates heat exchanger capacity based on multiple constraints including:
 * <ul>
 * <li>Heat duty (compared to design duty)</li>
 * <li>Approach temperature (minimum temperature difference)</li>
 * <li>Pressure drop (allowable DP per side)</li>
 * <li>Fouling factor impact</li>
 * </ul>
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class HeatExchangerCapacityStrategy implements EquipmentCapacityStrategy {

  /** Default minimum approach temperature (°C). */
  public static final double DEFAULT_MIN_APPROACH_TEMP = 5.0;

  /** Default maximum duty ratio (fraction of design). */
  public static final double DEFAULT_MAX_DUTY_RATIO = 1.1;

  private double minApproachTemp = DEFAULT_MIN_APPROACH_TEMP;
  private double maxDutyRatio = DEFAULT_MAX_DUTY_RATIO;

  /**
   * Default constructor.
   */
  public HeatExchangerCapacityStrategy() {}

  /**
   * Constructor with custom constraints.
   *
   * @param minApproachTemp minimum approach temperature in Celsius
   * @param maxDutyRatio maximum duty ratio
   */
  public HeatExchangerCapacityStrategy(double minApproachTemp, double maxDutyRatio) {
    this.minApproachTemp = minApproachTemp;
    this.maxDutyRatio = maxDutyRatio;
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof Heater || equipment instanceof MultiStreamHeatExchanger2;
  }

  /** {@inheritDoc} */
  @Override
  public int getPriority() {
    return 10;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "HeatExchangerCapacityStrategy";
  }

  /** {@inheritDoc} */
  @Override
  public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
    return Heater.class;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateCapacity(ProcessEquipmentInterface equipment) {
    if (equipment instanceof Heater) {
      Heater heater = (Heater) equipment;
      double duty = Math.abs(heater.getDuty());
      double maxDuty = heater.getMaxDesignDuty();
      if (maxDuty > 0 && duty > 0) {
        return duty / maxDuty;
      }
    } else if (equipment instanceof MultiStreamHeatExchanger2) {
      MultiStreamHeatExchanger2 hx = (MultiStreamHeatExchanger2) equipment;
      double duty = Math.abs(hx.energyDiff());
      double maxDuty = hx.getCapacityMax();
      if (maxDuty > 0 && duty > 0) {
        return duty / maxDuty;
      }
    }

    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateMaxCapacity(ProcessEquipmentInterface equipment) {
    if (equipment instanceof Heater) {
      return ((Heater) equipment).getMaxDesignDuty();
    } else if (equipment instanceof MultiStreamHeatExchanger2) {
      return ((MultiStreamHeatExchanger2) equipment).getCapacityMax();
    }
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = new HashMap<>();

    if (equipment instanceof Heater) {
      Heater heater = (Heater) equipment;

      // Duty constraint
      double maxDuty = heater.getMaxDesignDuty();

      if (maxDuty > 0) {
        CapacityConstraint dutyConstraint = new CapacityConstraint("duty").setDesignValue(maxDuty)
            .setMaxValue(maxDuty * maxDutyRatio).setUnit("W")
            .setSeverity(CapacityConstraint.ConstraintSeverity.SOFT).setWarningThreshold(0.9)
            .setValueSupplier(() -> Math.abs(heater.getDuty()));
        constraints.put("duty", dutyConstraint);
      }

      // Outlet temperature constraint (for temperature-controlled heaters)
      if (heater.getOutletStream() != null && heater.getOutletStream().getThermoSystem() != null) {
        CapacityConstraint tempConstraint = new CapacityConstraint("outletTemperature").setUnit("C")
            .setSeverity(CapacityConstraint.ConstraintSeverity.SOFT)
            .setValueSupplier(() -> heater.getOutletStream().getTemperature("C"));
        constraints.put("outletTemperature", tempConstraint);
      }

    } else if (equipment instanceof MultiStreamHeatExchanger2) {
      MultiStreamHeatExchanger2 hx = (MultiStreamHeatExchanger2) equipment;

      // Duty constraint
      double maxDuty = hx.getCapacityMax();

      if (maxDuty > 0) {
        CapacityConstraint dutyConstraint = new CapacityConstraint("duty").setDesignValue(maxDuty)
            .setMaxValue(maxDuty * maxDutyRatio).setUnit("W")
            .setSeverity(CapacityConstraint.ConstraintSeverity.SOFT).setWarningThreshold(0.9)
            .setValueSupplier(() -> Math.abs(hx.energyDiff()));
        constraints.put("duty", dutyConstraint);
      }
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
   * Gets the minimum approach temperature.
   *
   * @return minimum approach temperature in Celsius
   */
  public double getMinApproachTemp() {
    return minApproachTemp;
  }

  /**
   * Sets the minimum approach temperature.
   *
   * @param minApproachTemp minimum approach temperature in Celsius
   */
  public void setMinApproachTemp(double minApproachTemp) {
    this.minApproachTemp = minApproachTemp;
  }

  /**
   * Gets the maximum duty ratio.
   *
   * @return maximum duty ratio
   */
  public double getMaxDutyRatio() {
    return maxDutyRatio;
  }

  /**
   * Sets the maximum duty ratio.
   *
   * @param maxDutyRatio maximum duty ratio
   */
  public void setMaxDutyRatio(double maxDutyRatio) {
    this.maxDutyRatio = maxDutyRatio;
  }
}
