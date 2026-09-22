package neqsim.process.equipment.reactor;

import java.io.Serializable;
import neqsim.thermo.system.SystemInterface;

/**
 * Applies a verified S8 component-addition plan to an independent thermodynamic-system clone.
 *
 * <p>
 * The caller-owned prior target is never mutated. The candidate is initialized for component
 * bookkeeping and verified through
 * {@link AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt}; no thermodynamic flash is
 * run. Every candidate returned to a caller is another defensive clone.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview {
  private AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview() {}

  /**
   * Preview one target-scoped S8 component-addition plan on an independent clone.
   *
   * @param plan verified target-scoped component-addition plan
   * @param observedTargetStateIdentifier identifier observed for the caller-owned prior target
   * @param observedApplicationIdempotencyKey idempotency key observed for this preview
   * @param priorTarget caller-owned target before the proposed addition
   * @return defensive candidate snapshot and immutable application evidence
   * @throws IllegalArgumentException if inputs, clone independence, component application, or
   *     inventory verification fail
   */
  public static Result applyToClone(
      AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan,
      String observedTargetStateIdentifier,
      String observedApplicationIdempotencyKey,
      SystemInterface priorTarget) {
    requireMatchingPlanIdentity(
        plan, observedTargetStateIdentifier, observedApplicationIdempotencyKey);
    if (priorTarget == null) {
      throw new IllegalArgumentException("Prior target system cannot be null");
    }

    SystemInterface candidateTarget = independentClone(priorTarget, "Prior target");
    if (plan.requiresMutation()) {
      candidateTarget.addComponent(plan.getComponentName(), plan.getTransferredS8AmountMol());
      candidateTarget.init(0);
    }

    AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result receipt =
        AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.verify(
            plan,
            observedTargetStateIdentifier,
            observedApplicationIdempotencyKey,
            priorTarget,
            candidateTarget);
    return new Result(receipt, candidateTarget);
  }

  private static void requireMatchingPlanIdentity(
      AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan,
      String observedTargetStateIdentifier,
      String observedApplicationIdempotencyKey) {
    if (plan == null) {
      throw new IllegalArgumentException("S8 component-addition plan cannot be null");
    }
    if (observedTargetStateIdentifier == null
        || !plan.getTargetStateIdentifier().equals(observedTargetStateIdentifier)) {
      throw new IllegalArgumentException(
          "Target-state identifier does not match the component-addition plan");
    }
    if (observedApplicationIdempotencyKey == null
        || !plan.getApplicationIdempotencyKey().equals(observedApplicationIdempotencyKey)) {
      throw new IllegalArgumentException(
          "Application idempotency key does not match the component-addition plan");
    }
  }

  private static SystemInterface independentClone(SystemInterface source, String name) {
    SystemInterface copy = source.clone();
    if (copy == null) {
      throw new IllegalArgumentException(name + " clone cannot be null");
    }
    if (copy == source) {
      throw new IllegalArgumentException(name + " clone must be an independent object");
    }
    return copy;
  }

  /** Serializable preview containing immutable evidence and a defensively cloned candidate. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result receipt;
    private final SystemInterface candidateSnapshot;

    private Result(
        AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result receipt,
        SystemInterface candidateTarget) {
      this.receipt = receipt;
      this.candidateSnapshot = independentClone(candidateTarget, "Candidate target");
    }

    /** @return immutable evidence that the candidate inventory matches the plan. */
    public AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result
        getApplicationReceipt() {
      return receipt;
    }

    /**
     * Return an independent copy of the previewed candidate.
     *
     * @return defensive thermodynamic-system clone containing the planned S8 addition
     * @throws IllegalArgumentException if the stored candidate cannot produce an independent clone
     */
    public SystemInterface getCandidateTarget() {
      return independentClone(candidateSnapshot, "Stored candidate target");
    }
  }
}
