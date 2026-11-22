package neqsim.process.equipment.capacity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.reservoir.WellFlow;

/**
 * Capacity strategy for well flow equipment.
 *
 * <p>
 * This strategy contributes the well production index utilization constraint.
 * </p>
 *
 * <p>
 * The subsurface deliverability limits (drawdown and minimum flowing bottom-hole pressure) are owned by the
 * {@link WellFlow} equipment itself, not by this strategy: set them with
 * {@link WellFlow#setMaxDrawdown(double, String)} and {@link WellFlow#setMinBottomHolePressure(double, String)} and
 * enable them with {@link WellFlow#useWellConstraints()}. The well then publishes its own {@code "well drawdown"} and
 * {@code "min BHP"} capacity constraints.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class WellFlowCapacityStrategy implements EquipmentCapacityStrategy {

  /** Default maximum well production index in Sm3/d/bar. */
  public static final double DEFAULT_MAX_PI = 100.0;

  /**
   * Default maximum drawdown pressure in bar.
   *
   * @deprecated the drawdown limit belongs to the well; use {@link WellFlow#setMaxDrawdown(double, String)}.
   */
  @Deprecated
  public static final double DEFAULT_MAX_DRAWDOWN_BAR = 200.0;

  private double maxPI = DEFAULT_MAX_PI;

  /**
   * Default constructor.
   */
  public WellFlowCapacityStrategy() {
  }

  /**
   * Constructor with a custom production-index limit.
   *
   * @param maxPI maximum well production index in Sm3/d/bar
   * @param maxDrawdownBar ignored; the drawdown limit is owned by the well, so set it with
   * {@link WellFlow#setMaxDrawdown(double, String)} instead. Supplying it here has no effect and never had.
   * @deprecated use {@link #WellFlowCapacityStrategy(double)} and set the drawdown limit on the {@link WellFlow}.
   */
  @Deprecated
  public WellFlowCapacityStrategy(double maxPI, double maxDrawdownBar) {
    this.maxPI = maxPI;
  }

  /**
   * Constructor with a custom production-index limit.
   *
   * @param maxPI maximum well production index in Sm3/d/bar
   */
  public WellFlowCapacityStrategy(double maxPI) {
    this.maxPI = maxPI;
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof WellFlow;
  }

  /** {@inheritDoc} */
  @Override
  public int getPriority() {
    return 10;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "WellFlowCapacityStrategy";
  }

  /** {@inheritDoc} */
  @Override
  public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
    return WellFlow.class;
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
    if (equipment instanceof WellFlow) {
      return ((WellFlow) equipment).getWellProductionIndex();
    }
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = new LinkedHashMap<String, CapacityConstraint>();

    if (equipment instanceof WellFlow) {
      addWellFlowConstraints(constraints, (WellFlow) equipment);
    }

    return constraints;
  }

  /**
   * Adds constraints for well flow.
   *
   * @param constraints map to add constraints to
   * @param well the well flow equipment
   */
  private void addWellFlowConstraints(Map<String, CapacityConstraint> constraints, WellFlow well) {
    // Well production index constraint
    double pi = well.getWellProductionIndex();
    if (pi > 0) {
      CapacityConstraint piConstraint = new CapacityConstraint("productionIndex").setDesignValue(maxPI)
          .setMaxValue(maxPI * 1.2).setUnit("Sm3/d/bar").setSeverity(CapacityConstraint.ConstraintSeverity.SOFT)
          .setWarningThreshold(0.9).setDescription("Well production index vs expected")
          .setValueSupplier(() -> well.getWellProductionIndex());
      constraints.put("productionIndex", piConstraint);
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
