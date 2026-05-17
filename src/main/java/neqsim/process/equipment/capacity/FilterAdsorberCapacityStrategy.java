package neqsim.process.equipment.capacity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.adsorber.SimpleAdsorber;
import neqsim.process.equipment.filter.Filter;

/**
 * Capacity strategy for filter and adsorber equipment.
 *
 * <p>
 * This strategy evaluates filter and adsorber capacity based on constraints including:
 * </p>
 * <ul>
 * <li>Pressure drop vs maximum allowable</li>
 * <li>Cv factor utilization</li>
 * <li>NTU vs design NTU (adsorbers)</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class FilterAdsorberCapacityStrategy implements EquipmentCapacityStrategy {

  /** Default maximum pressure drop in bar. */
  public static final double DEFAULT_MAX_DP_BAR = 5.0;

  private double maxDpBar = DEFAULT_MAX_DP_BAR;

  /**
   * Default constructor.
   */
  public FilterAdsorberCapacityStrategy() {}

  /**
   * Constructor with custom maximum pressure drop.
   *
   * @param maxDpBar maximum pressure drop in bar
   */
  public FilterAdsorberCapacityStrategy(double maxDpBar) {
    this.maxDpBar = maxDpBar;
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof Filter || equipment instanceof SimpleAdsorber;
  }

  /** {@inheritDoc} */
  @Override
  public int getPriority() {
    return 10;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "FilterAdsorberCapacityStrategy";
  }

  /** {@inheritDoc} */
  @Override
  public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
    return Filter.class;
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
    return maxDpBar;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = new HashMap<String, CapacityConstraint>();

    if (equipment instanceof Filter) {
      addFilterConstraints(constraints, (Filter) equipment);
    } else if (equipment instanceof SimpleAdsorber) {
      addAdsorberConstraints(constraints, (SimpleAdsorber) equipment);
    }

    return constraints;
  }

  /**
   * Adds constraints specific to filter equipment.
   *
   * @param constraints map to add constraints to
   * @param filter the filter
   */
  private void addFilterConstraints(Map<String, CapacityConstraint> constraints, Filter filter) {
    // Pressure drop constraint
    CapacityConstraint dpConstraint =
        new CapacityConstraint("pressureDrop").setDesignValue(maxDpBar).setMaxValue(maxDpBar * 1.2)
            .setUnit("bar").setSeverity(CapacityConstraint.ConstraintSeverity.SOFT)
            .setWarningThreshold(0.8).setDescription("Filter pressure drop vs maximum allowable")
            .setValueSupplier(() -> Math.abs(filter.getDeltaP()));
    constraints.put("pressureDrop", dpConstraint);
  }

  /**
   * Adds constraints specific to adsorber equipment.
   *
   * @param constraints map to add constraints to
   * @param adsorber the adsorber
   */
  private void addAdsorberConstraints(Map<String, CapacityConstraint> constraints,
      SimpleAdsorber adsorber) {
    // NTU constraint
    double ntu = adsorber.getNTU();
    if (ntu > 0) {
      CapacityConstraint ntuConstraint =
          new CapacityConstraint("NTU").setDesignValue(ntu).setMaxValue(ntu * 1.5).setUnit("")
              .setSeverity(CapacityConstraint.ConstraintSeverity.SOFT).setWarningThreshold(0.9)
              .setDescription("Number of transfer units vs design")
              .setValueSupplier(() -> adsorber.getNTU());
      constraints.put("NTU", ntuConstraint);
    }
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
