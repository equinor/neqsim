package neqsim.process.equipment.capacity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.subsea.SubseaTree;
import neqsim.process.equipment.subsea.SubseaWell;

/**
 * Capacity strategy for subsea equipment.
 *
 * <p>
 * This strategy evaluates subsea well and tree capacity based on constraints including:
 * </p>
 * <ul>
 * <li>Wellhead pressure vs design pressure</li>
 * <li>Flow rate vs maximum well deliverability</li>
 * <li>Choke opening (SubseaTree)</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class SubseaEquipmentCapacityStrategy implements EquipmentCapacityStrategy {

  /** Default maximum wellhead pressure in bara. */
  public static final double DEFAULT_MAX_WHP_BARA = 500.0;

  private double maxWhpBara = DEFAULT_MAX_WHP_BARA;

  /**
   * Default constructor.
   */
  public SubseaEquipmentCapacityStrategy() {}

  /**
   * Constructor with custom maximum wellhead pressure.
   *
   * @param maxWhpBara maximum wellhead pressure in bara
   */
  public SubseaEquipmentCapacityStrategy(double maxWhpBara) {
    this.maxWhpBara = maxWhpBara;
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof SubseaWell || equipment instanceof SubseaTree;
  }

  /** {@inheritDoc} */
  @Override
  public int getPriority() {
    return 10;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "SubseaEquipmentCapacityStrategy";
  }

  /** {@inheritDoc} */
  @Override
  public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
    return SubseaWell.class;
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
    if (equipment instanceof SubseaWell) {
      return ((SubseaWell) equipment).getMaxWellheadPressure();
    }
    if (equipment instanceof SubseaTree) {
      return ((SubseaTree) equipment).getDesignPressure();
    }
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
    Map<String, CapacityConstraint> constraints = new HashMap<String, CapacityConstraint>();

    if (equipment instanceof SubseaWell) {
      addWellConstraints(constraints, (SubseaWell) equipment);
    } else if (equipment instanceof SubseaTree) {
      addTreeConstraints(constraints, (SubseaTree) equipment);
    }

    return constraints;
  }

  /**
   * Adds constraints for subsea well.
   *
   * @param constraints map to add constraints to
   * @param well the subsea well
   */
  private void addWellConstraints(Map<String, CapacityConstraint> constraints, SubseaWell well) {
    double maxWhp = well.getMaxWellheadPressure();
    if (maxWhp > 0) {
      CapacityConstraint whpConstraint = new CapacityConstraint("wellheadPressure")
          .setDesignValue(maxWhp).setMaxValue(maxWhp * 1.1).setUnit("bara")
          .setSeverity(CapacityConstraint.ConstraintSeverity.HARD).setWarningThreshold(0.9)
          .setDescription("Wellhead pressure vs maximum design");
      constraints.put("wellheadPressure", whpConstraint);
    }
  }

  /**
   * Adds constraints for subsea tree.
   *
   * @param constraints map to add constraints to
   * @param tree the subsea tree
   */
  private void addTreeConstraints(Map<String, CapacityConstraint> constraints, SubseaTree tree) {
    double designP = tree.getDesignPressure();
    if (designP > 0) {
      CapacityConstraint pressureConstraint = new CapacityConstraint("designPressure")
          .setDesignValue(designP).setMaxValue(designP * 1.1).setUnit("bara")
          .setSeverity(CapacityConstraint.ConstraintSeverity.HARD).setWarningThreshold(0.9)
          .setDescription("Operating pressure vs tree design pressure");
      constraints.put("designPressure", pressureConstraint);
    }

    // Choke opening constraint (1.0 = fully open = at capacity)
    CapacityConstraint chokeConstraint = new CapacityConstraint("chokeOpening").setDesignValue(0.8)
        .setMaxValue(1.0).setMinValue(0.0).setUnit("fraction")
        .setSeverity(CapacityConstraint.ConstraintSeverity.SOFT).setWarningThreshold(0.9)
        .setDescription("Choke opening fraction").setValueSupplier(() -> tree.getChokeOpening());
    constraints.put("chokeOpening", chokeConstraint);
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
