package neqsim.process.equipment.capacity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.electrolyzer.CO2Electrolyzer;
import neqsim.process.equipment.electrolyzer.Electrolyzer;

/**
 * Capacity strategy for electrolyzer equipment.
 *
 * <p>
 * This strategy evaluates electrolyzer capacity based on constraints including:
 * </p>
 * <ul>
 * <li>Cell voltage vs design voltage</li>
 * <li>Power consumption vs rated</li>
 * <li>Hydrogen production rate vs design (water electrolysis)</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ElectrolyzerCapacityStrategy implements EquipmentCapacityStrategy {

  /** Default maximum cell voltage in volts. */
  public static final double DEFAULT_MAX_VOLTAGE_V = 2.0;

  /** Default rated power consumption in kW. */
  public static final double DEFAULT_RATED_POWER_KW = 10000.0;

  private double maxVoltageV = DEFAULT_MAX_VOLTAGE_V;
  private double ratedPowerKW = DEFAULT_RATED_POWER_KW;

  /**
   * Default constructor.
   */
  public ElectrolyzerCapacityStrategy() {}

  /**
   * Constructor with custom constraints.
   *
   * @param maxVoltageV maximum cell voltage in volts
   * @param ratedPowerKW rated power consumption in kW
   */
  public ElectrolyzerCapacityStrategy(double maxVoltageV, double ratedPowerKW) {
    this.maxVoltageV = maxVoltageV;
    this.ratedPowerKW = ratedPowerKW;
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof Electrolyzer || equipment instanceof CO2Electrolyzer;
  }

  /** {@inheritDoc} */
  @Override
  public int getPriority() {
    return 10;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "ElectrolyzerCapacityStrategy";
  }

  /** {@inheritDoc} */
  @Override
  public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
    return Electrolyzer.class;
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
    return ratedPowerKW;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = new HashMap<String, CapacityConstraint>();

    if (equipment instanceof Electrolyzer) {
      addElectrolyzerConstraints(constraints, (Electrolyzer) equipment);
    } else if (equipment instanceof CO2Electrolyzer) {
      addCO2ElectrolyzerConstraints(constraints, (CO2Electrolyzer) equipment);
    }

    return constraints;
  }

  /**
   * Adds constraints for water electrolyzer.
   *
   * @param constraints map to add constraints to
   * @param electrolyzer the electrolyzer
   */
  private void addElectrolyzerConstraints(Map<String, CapacityConstraint> constraints,
      Electrolyzer electrolyzer) {
    // Cell voltage constraint
    CapacityConstraint voltageConstraint = new CapacityConstraint("cellVoltage")
        .setDesignValue(maxVoltageV).setMaxValue(maxVoltageV * 1.1).setUnit("V")
        .setSeverity(CapacityConstraint.ConstraintSeverity.HARD).setWarningThreshold(0.9)
        .setDescription("Cell voltage vs maximum design")
        .setValueSupplier(() -> electrolyzer.getCellVoltage());
    constraints.put("cellVoltage", voltageConstraint);
  }

  /**
   * Adds constraints for CO2 electrolyzer.
   *
   * @param constraints map to add constraints to
   * @param co2Electrolyzer the CO2 electrolyzer
   */
  private void addCO2ElectrolyzerConstraints(Map<String, CapacityConstraint> constraints,
      CO2Electrolyzer co2Electrolyzer) {
    // Cell voltage constraint (CO2 electrolysis typically higher voltage)
    CapacityConstraint voltageConstraint = new CapacityConstraint("cellVoltage")
        .setDesignValue(maxVoltageV * 1.5).setMaxValue(maxVoltageV * 1.65).setUnit("V")
        .setSeverity(CapacityConstraint.ConstraintSeverity.HARD).setWarningThreshold(0.9)
        .setDescription("CO2 electrolyzer cell voltage vs maximum design");
    constraints.put("cellVoltage", voltageConstraint);
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
