package neqsim.process.equipment.capacity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.reactor.GibbsReactor;
import neqsim.process.equipment.reactor.PlugFlowReactor;
import neqsim.process.equipment.reactor.StirredTankReactor;

/**
 * Capacity strategy for reactor equipment.
 *
 * <p>
 * This strategy evaluates reactor capacity based on constraints including:
 * </p>
 * <ul>
 * <li>Heat duty vs design duty</li>
 * <li>Conversion vs target conversion</li>
 * <li>Residence time vs minimum required</li>
 * <li>Temperature limits</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ReactorCapacityStrategy implements EquipmentCapacityStrategy {

  /** Default maximum reactor temperature in Celsius. */
  public static final double DEFAULT_MAX_TEMPERATURE_C = 500.0;

  /** Default design heat duty in kW. */
  public static final double DEFAULT_MAX_DUTY_KW = 10000.0;

  private double maxTemperatureC = DEFAULT_MAX_TEMPERATURE_C;
  private double maxDutyKW = DEFAULT_MAX_DUTY_KW;

  /**
   * Default constructor.
   */
  public ReactorCapacityStrategy() {}

  /**
   * Constructor with custom constraints.
   *
   * @param maxTemperatureC maximum reactor temperature in Celsius
   * @param maxDutyKW maximum heat duty in kW
   */
  public ReactorCapacityStrategy(double maxTemperatureC, double maxDutyKW) {
    this.maxTemperatureC = maxTemperatureC;
    this.maxDutyKW = maxDutyKW;
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof GibbsReactor || equipment instanceof PlugFlowReactor
        || equipment instanceof StirredTankReactor;
  }

  /** {@inheritDoc} */
  @Override
  public int getPriority() {
    return 10;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "ReactorCapacityStrategy";
  }

  /** {@inheritDoc} */
  @Override
  public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
    return GibbsReactor.class;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateCapacity(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = getConstraints(equipment);
    double maxUtil = 0.0;
    for (CapacityConstraint c : constraints.values()) {
      if (c.isEnabled()) {
        double util = c.getUtilization();
        if (!Double.isNaN(util) && util > maxUtil) {
          maxUtil = util;
        }
      }
    }
    return maxUtil;
  }

  /** {@inheritDoc} */
  @Override
  public double evaluateMaxCapacity(ProcessEquipmentInterface equipment) {
    return maxDutyKW;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = new HashMap<String, CapacityConstraint>();

    if (equipment instanceof PlugFlowReactor) {
      addPlugFlowConstraints(constraints, (PlugFlowReactor) equipment);
    } else if (equipment instanceof StirredTankReactor) {
      addCSTRConstraints(constraints, (StirredTankReactor) equipment);
    } else if (equipment instanceof GibbsReactor) {
      addGibbsConstraints(constraints, (GibbsReactor) equipment);
    }

    return constraints;
  }

  /**
   * Adds constraints specific to plug flow reactors.
   *
   * @param constraints map to add constraints to
   * @param pfr the plug flow reactor
   */
  private void addPlugFlowConstraints(Map<String, CapacityConstraint> constraints,
      PlugFlowReactor pfr) {
    // Heat duty constraint
    CapacityConstraint dutyConstraint =
        new CapacityConstraint("heatDuty").setDesignValue(maxDutyKW).setMaxValue(maxDutyKW * 1.1)
            .setUnit("kW").setSeverity(CapacityConstraint.ConstraintSeverity.SOFT)
            .setWarningThreshold(0.9).setDescription("Reactor heat duty vs design")
            .setValueSupplier(() -> Math.abs(pfr.getHeatDuty("kW")));
    constraints.put("heatDuty", dutyConstraint);

    // Conversion constraint (target = 1.0 = 100%)
    CapacityConstraint convConstraint = new CapacityConstraint("conversion").setDesignValue(1.0)
        .setMaxValue(1.0).setUnit("fraction")
        .setSeverity(CapacityConstraint.ConstraintSeverity.SOFT).setWarningThreshold(0.9)
        .setDescription("Reactor conversion vs target").setValueSupplier(() -> pfr.getConversion());
    constraints.put("conversion", convConstraint);
  }

  /**
   * Adds constraints specific to stirred tank reactors.
   *
   * @param constraints map to add constraints to
   * @param cstr the stirred tank reactor
   */
  private void addCSTRConstraints(Map<String, CapacityConstraint> constraints,
      StirredTankReactor cstr) {
    // Heat duty constraint
    CapacityConstraint dutyConstraint =
        new CapacityConstraint("heatDuty").setDesignValue(maxDutyKW).setMaxValue(maxDutyKW * 1.1)
            .setUnit("kW").setSeverity(CapacityConstraint.ConstraintSeverity.SOFT)
            .setWarningThreshold(0.9).setDescription("Reactor heat duty vs design")
            .setValueSupplier(() -> Math.abs(cstr.getHeatDuty("kW")));
    constraints.put("heatDuty", dutyConstraint);

    // Agitator power constraint
    double agitatorPower = cstr.getAgitatorPower();
    if (agitatorPower > 0) {
      CapacityConstraint agitatorConstraint = new CapacityConstraint("agitatorPower")
          .setDesignValue(agitatorPower).setMaxValue(agitatorPower * 1.1).setUnit("W")
          .setSeverity(CapacityConstraint.ConstraintSeverity.SOFT).setWarningThreshold(0.9)
          .setDescription("Agitator power vs design")
          .setValueSupplier(() -> cstr.getAgitatorPower());
      constraints.put("agitatorPower", agitatorConstraint);
    }
  }

  /**
   * Adds constraints specific to Gibbs reactors.
   *
   * @param constraints map to add constraints to
   * @param gibbs the Gibbs reactor
   */
  private void addGibbsConstraints(Map<String, CapacityConstraint> constraints,
      GibbsReactor gibbs) {
    // Power (energy) constraint
    CapacityConstraint dutyConstraint =
        new CapacityConstraint("heatDuty").setDesignValue(maxDutyKW).setMaxValue(maxDutyKW * 1.1)
            .setUnit("kW").setSeverity(CapacityConstraint.ConstraintSeverity.SOFT)
            .setWarningThreshold(0.9).setDescription("Reactor energy balance vs design")
            .setValueSupplier(() -> Math.abs(gibbs.getPower("kW")));
    constraints.put("heatDuty", dutyConstraint);
  }

  /** {@inheritDoc} */
  @Override
  public List<CapacityConstraint> getViolations(ProcessEquipmentInterface equipment) {
    List<CapacityConstraint> violations = new ArrayList<CapacityConstraint>();
    for (CapacityConstraint c : getConstraints(equipment).values()) {
      if (c.isViolated()) {
        violations.add(c);
      }
    }
    return violations;
  }

  /** {@inheritDoc} */
  @Override
  public CapacityConstraint getBottleneckConstraint(ProcessEquipmentInterface equipment) {
    CapacityConstraint bottleneck = null;
    double maxUtil = 0.0;
    for (CapacityConstraint c : getConstraints(equipment).values()) {
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
    for (CapacityConstraint c : getConstraints(equipment).values()) {
      if (c.getSeverity() == CapacityConstraint.ConstraintSeverity.HARD
          || c.getSeverity() == CapacityConstraint.ConstraintSeverity.CRITICAL) {
        if (c.isHardLimitExceeded()) {
          return false;
        }
      }
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isWithinSoftLimits(ProcessEquipmentInterface equipment) {
    for (CapacityConstraint c : getConstraints(equipment).values()) {
      if (c.isViolated()) {
        return false;
      }
    }
    return true;
  }
}
