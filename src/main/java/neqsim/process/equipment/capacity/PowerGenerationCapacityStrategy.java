package neqsim.process.equipment.capacity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.powergeneration.CombinedCycleSystem;
import neqsim.process.equipment.powergeneration.GasTurbine;
import neqsim.process.equipment.powergeneration.HRSG;
import neqsim.process.equipment.powergeneration.SteamTurbine;

/**
 * Capacity strategy for power generation equipment.
 *
 * <p>
 * This strategy evaluates capacity for gas turbines, steam turbines, HRSG, and combined cycle
 * systems based on constraints including:
 * </p>
 * <ul>
 * <li>Power output vs rated power</li>
 * <li>Efficiency vs design efficiency</li>
 * <li>Heat transfer vs design capacity (HRSG)</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class PowerGenerationCapacityStrategy implements EquipmentCapacityStrategy {

  /** Default rated power in kW. */
  public static final double DEFAULT_RATED_POWER_KW = 50000.0;

  /** Default maximum exhaust temperature in Celsius. */
  public static final double DEFAULT_MAX_EXHAUST_TEMP_C = 600.0;

  private double ratedPowerKW = DEFAULT_RATED_POWER_KW;

  /**
   * Default constructor.
   */
  public PowerGenerationCapacityStrategy() {}

  /**
   * Constructor with custom rated power.
   *
   * @param ratedPowerKW rated power in kW
   */
  public PowerGenerationCapacityStrategy(double ratedPowerKW) {
    this.ratedPowerKW = ratedPowerKW;
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof GasTurbine || equipment instanceof SteamTurbine
        || equipment instanceof HRSG || equipment instanceof CombinedCycleSystem;
  }

  /** {@inheritDoc} */
  @Override
  public int getPriority() {
    return 10;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "PowerGenerationCapacityStrategy";
  }

  /** {@inheritDoc} */
  @Override
  public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
    return GasTurbine.class;
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
    if (equipment instanceof CombinedCycleSystem) {
      return ((CombinedCycleSystem) equipment).getTotalPower("kW");
    }
    return ratedPowerKW;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = new HashMap<String, CapacityConstraint>();

    if (equipment instanceof CombinedCycleSystem) {
      addCombinedCycleConstraints(constraints, (CombinedCycleSystem) equipment);
    } else if (equipment instanceof GasTurbine) {
      addGasTurbineConstraints(constraints, (GasTurbine) equipment);
    } else if (equipment instanceof SteamTurbine) {
      addSteamTurbineConstraints(constraints, (SteamTurbine) equipment);
    } else if (equipment instanceof HRSG) {
      addHRSGConstraints(constraints, (HRSG) equipment);
    }

    return constraints;
  }

  /**
   * Adds constraints for gas turbine.
   *
   * @param constraints map to add constraints to
   * @param gt the gas turbine
   */
  private void addGasTurbineConstraints(Map<String, CapacityConstraint> constraints,
      GasTurbine gt) {
    CapacityConstraint powerConstraint = new CapacityConstraint("power")
        .setDesignValue(ratedPowerKW).setMaxValue(ratedPowerKW * 1.05).setUnit("kW")
        .setSeverity(CapacityConstraint.ConstraintSeverity.HARD).setWarningThreshold(0.9)
        .setDescription("Gas turbine power output vs rated")
        .setValueSupplier(() -> Math.abs(gt.getPower()));
    constraints.put("power", powerConstraint);
  }

  /**
   * Adds constraints for steam turbine.
   *
   * @param constraints map to add constraints to
   * @param st the steam turbine
   */
  private void addSteamTurbineConstraints(Map<String, CapacityConstraint> constraints,
      SteamTurbine st) {
    CapacityConstraint powerConstraint = new CapacityConstraint("power")
        .setDesignValue(ratedPowerKW).setMaxValue(ratedPowerKW * 1.05).setUnit("kW")
        .setSeverity(CapacityConstraint.ConstraintSeverity.HARD).setWarningThreshold(0.9)
        .setDescription("Steam turbine power output vs rated")
        .setValueSupplier(() -> Math.abs(st.getPower("kW")));
    constraints.put("power", powerConstraint);
  }

  /**
   * Adds constraints for HRSG.
   *
   * @param constraints map to add constraints to
   * @param hrsg the HRSG
   */
  private void addHRSGConstraints(Map<String, CapacityConstraint> constraints, HRSG hrsg) {
    CapacityConstraint heatConstraint = new CapacityConstraint("heatTransferred")
        .setDesignValue(ratedPowerKW).setMaxValue(ratedPowerKW * 1.1).setUnit("kW")
        .setSeverity(CapacityConstraint.ConstraintSeverity.SOFT).setWarningThreshold(0.9)
        .setDescription("HRSG heat transfer vs design")
        .setValueSupplier(() -> Math.abs(hrsg.getHeatTransferred("kW")));
    constraints.put("heatTransferred", heatConstraint);
  }

  /**
   * Adds constraints for combined cycle system.
   *
   * @param constraints map to add constraints to
   * @param ccs the combined cycle system
   */
  private void addCombinedCycleConstraints(Map<String, CapacityConstraint> constraints,
      CombinedCycleSystem ccs) {
    CapacityConstraint powerConstraint = new CapacityConstraint("totalPower")
        .setDesignValue(ratedPowerKW).setMaxValue(ratedPowerKW * 1.05).setUnit("kW")
        .setSeverity(CapacityConstraint.ConstraintSeverity.HARD).setWarningThreshold(0.9)
        .setDescription("Combined cycle total power output vs rated")
        .setValueSupplier(() -> Math.abs(ccs.getTotalPower("kW")));
    constraints.put("totalPower", powerConstraint);

    // Efficiency constraint
    CapacityConstraint effConstraint =
        new CapacityConstraint("efficiency").setDesignValue(0.60).setMaxValue(1.0).setMinValue(0.20)
            .setUnit("fraction").setSeverity(CapacityConstraint.ConstraintSeverity.ADVISORY)
            .setWarningThreshold(0.9).setDescription("Overall thermal efficiency")
            .setValueSupplier(() -> ccs.getOverallEfficiency());
    constraints.put("efficiency", effConstraint);
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
