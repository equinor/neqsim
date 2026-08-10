package neqsim.process.dynamics;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Creates calculation identifiers with explicit transient physical-step semantics.
 *
 * <p>
 * A non-null identifier passed to {@code ProcessSystem.runTransient(dt, id)} identifies one physical timestep. The same
 * physical-step identifier may be reused for repeated algebraic/nonlinear evaluations inside that timestep so mutable
 * clocks and controllers remain idempotent. The next physical timestep must use a different identifier.
 * </p>
 *
 * <p>
 * Evaluation identifiers are separate diagnostic identities for nonlinear/refinement/substep work. They must not
 * replace the physical-step identifier when invoking equipment or controllers whose idempotency is keyed to the
 * physical step.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class TransientStepIdentifier {
  private static final String NAMESPACE = "neqsim-transient-step-v1";

  private TransientStepIdentifier() {
  }

  /**
   * Creates a random identifier for one physical timestep.
   *
   * @return new physical-step identifier
   */
  public static UUID randomPhysicalStep() {
    return UUID.randomUUID();
  }

  /**
   * Creates a reproducible identifier for one physical timestep in a named run or scenario.
   *
   * <p>
   * This is useful for deterministic safety scenarios, regression tests and OTS replay. The same scope and step index
   * always produce the same UUID, while adjacent physical steps produce different UUIDs.
   * </p>
   *
   * @param scope stable run, scenario or replay identity
   * @param physicalStepIndex zero-based physical-step index
   * @return deterministic physical-step identifier
   */
  public static UUID deterministicPhysicalStep(String scope, long physicalStepIndex) {
    validateScope(scope);
    validateIndex("physicalStepIndex", physicalStepIndex);
    return nameUuid("physical", scope, physicalStepIndex);
  }

  /**
   * Creates a reproducible diagnostic identity for one refinement/nonlinear evaluation within a physical timestep.
   *
   * <p>
   * This identity is for diagnostics, residual histories and solver bookkeeping. Continue passing the parent
   * {@code physicalStepIdentifier} to equipment/controller {@code runTransient} calls so one physical step advances
   * mutable state exactly once.
   * </p>
   *
   * @param physicalStepIdentifier parent physical-step identifier
   * @param evaluationIndex zero-based evaluation/refinement index
   * @return deterministic evaluation identifier
   */
  public static UUID deterministicEvaluation(UUID physicalStepIdentifier, long evaluationIndex) {
    if (physicalStepIdentifier == null) {
      throw new IllegalArgumentException("physicalStepIdentifier must not be null");
    }
    validateIndex("evaluationIndex", evaluationIndex);
    return nameUuid("evaluation", physicalStepIdentifier.toString(), evaluationIndex);
  }

  private static UUID nameUuid(String kind, String scope, long index) {
    String value = NAMESPACE + ":" + kind.length() + ":" + kind + ":" + scope.length() + ":" + scope + ":" + index;
    return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
  }

  private static void validateScope(String scope) {
    if (scope == null || scope.trim().isEmpty()) {
      throw new IllegalArgumentException("scope must not be null or empty");
    }
  }

  private static void validateIndex(String name, long index) {
    if (index < 0L) {
      throw new IllegalArgumentException(name + " must be >= 0: " + index);
    }
  }
}
