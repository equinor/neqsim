package neqsim.process.util.optimizer;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategy;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategyRegistry;
import neqsim.process.util.optimizer.InstalledEquipmentCapacityEvidence.ConstraintOrigin;

/**
 * Resolves equipment-local capacity identities with direct definitions taking precedence per name.
 *
 * <p>
 * A direct constraint replaces only the strategy constraint with the same name. Other strategy constraints remain
 * visible, including when the direct override is disabled or incomplete. This resolver does not sample value suppliers,
 * infer an installed rating, or retain a global cache. Returned entries reference live definitions and must be sampled
 * into immutable evidence before being used as an operating-point report.
 * </p>
 */
public final class EquipmentCapacityConstraintResolver {
  private EquipmentCapacityConstraintResolver() {
  }

  /**
   * Resolves all direct and selected-strategy constraints in deterministic name order.
   *
   * @param equipment equipment to inspect
   * @return immutable map whose values reference the selected live definitions
   * @throws IllegalArgumentException when equipment or a constraint identity is invalid
   */
  public static Map<String, ResolvedConstraint> resolve(ProcessEquipmentInterface equipment) {
    if (equipment == null) {
      throw new IllegalArgumentException("Equipment is required");
    }
    Map<String, CapacityConstraint> direct = equipment.getCapacityConstraints();
    EquipmentCapacityStrategy strategy = EquipmentCapacityStrategyRegistry.getInstance().findStrategy(equipment);
    Map<String, CapacityConstraint> generated = strategy == null ? null : strategy.getConstraints(equipment);
    return resolve(direct, generated);
  }

  /**
   * Resolves supplied discovery maps using the same direct-over-strategy identity policy.
   *
   * @param direct direct equipment definitions, or null
   * @param strategy strategy definitions, or null
   * @return immutable name-sorted map
   * @throws IllegalArgumentException when a map contains an invalid identity or definition
   */
  public static Map<String, ResolvedConstraint> resolve(Map<String, CapacityConstraint> direct,
      Map<String, CapacityConstraint> strategy) {
    Map<String, ResolvedConstraint> resolved = new TreeMap<String, ResolvedConstraint>();
    add(resolved, strategy, ConstraintOrigin.STRATEGY);
    add(resolved, direct, ConstraintOrigin.DIRECT);
    return Collections.unmodifiableMap(resolved);
  }

  private static void add(Map<String, ResolvedConstraint> target, Map<String, CapacityConstraint> source,
      ConstraintOrigin origin) {
    if (source == null) {
      return;
    }
    for (Map.Entry<String, CapacityConstraint> entry : source.entrySet()) {
      String name = PlantConstraintScope.requireText(entry.getKey(), "Constraint name");
      CapacityConstraint constraint = entry.getValue();
      if (constraint == null || !name.equals(constraint.getName()) || !name.equals(entry.getKey())) {
        throw new IllegalArgumentException("Constraint map identity does not match definition: " + name);
      }
      target.put(name, new ResolvedConstraint(constraint, origin));
    }
  }

  /** Selected live definition and the discovery path that supplied it. */
  public static final class ResolvedConstraint {
    private final CapacityConstraint constraint;
    private final ConstraintOrigin origin;

    private ResolvedConstraint(CapacityConstraint constraint, ConstraintOrigin origin) {
      this.constraint = constraint;
      this.origin = origin;
    }

    /** @return selected live capacity definition */
    public CapacityConstraint getConstraint() {
      return constraint;
    }

    /** @return direct or strategy discovery origin */
    public ConstraintOrigin getOrigin() {
      return origin;
    }
  }
}
