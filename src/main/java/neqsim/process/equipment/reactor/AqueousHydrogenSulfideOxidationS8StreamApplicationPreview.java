package neqsim.process.equipment.reactor;

import java.io.Serializable;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Applies a verified S8 component-addition plan to a detached deep clone of a stream.
 *
 * <p>
 * The caller-owned stream and fluid are never mutated. The candidate stream is not run, and no thermodynamic flash is
 * executed. Every candidate returned to a caller is another defensive deep clone.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8StreamApplicationPreview {
  private AqueousHydrogenSulfideOxidationS8StreamApplicationPreview() {
  }

  /**
   * Preview one target-scoped S8 component-addition plan on an independent stream clone.
   *
   * @param plan verified target-scoped component-addition plan
   * @param observedTargetStateIdentifier identifier observed for the caller-owned stream state
   * @param observedApplicationIdempotencyKey idempotency key observed for this preview
   * @param priorStream caller-owned stream before the proposed addition
   * @return defensive candidate stream snapshot and immutable application evidence
   * @throws IllegalArgumentException if input, clone independence, component application, or inventory verification
   * fails
   */
  public static Result applyToClone(AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan,
      String observedTargetStateIdentifier, String observedApplicationIdempotencyKey, StreamInterface priorStream) {
    if (priorStream == null) {
      throw new IllegalArgumentException("Prior stream cannot be null");
    }
    SystemInterface priorFluid = priorStream.getFluid();
    if (priorFluid == null) {
      throw new IllegalArgumentException("Prior stream fluid cannot be null");
    }

    StreamInterface candidateStream = independentClone(priorStream, "Prior stream");
    AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview.Result fluidPreview = AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview
        .applyToClone(plan, observedTargetStateIdentifier, observedApplicationIdempotencyKey, priorFluid);
    candidateStream.setFluid(fluidPreview.getCandidateTarget());

    SystemInterface candidateFluid = candidateStream.getFluid();
    if (candidateFluid == null) {
      throw new IllegalArgumentException("Candidate stream fluid cannot be null");
    }
    if (candidateFluid == priorFluid) {
      throw new IllegalArgumentException("Candidate stream fluid must not alias the prior fluid");
    }
    AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result receipt = AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt
        .verify(plan, observedTargetStateIdentifier, observedApplicationIdempotencyKey, priorFluid, candidateFluid);
    return new Result(receipt, candidateStream);
  }

  private static StreamInterface independentClone(StreamInterface source, String name) {
    StreamInterface copy = source.clone();
    if (copy == null) {
      throw new IllegalArgumentException(name + " clone cannot be null");
    }
    if (copy == source) {
      throw new IllegalArgumentException(name + " clone must be an independent object");
    }
    SystemInterface sourceFluid = source.getFluid();
    SystemInterface copyFluid = copy.getFluid();
    if (copyFluid == null) {
      throw new IllegalArgumentException(name + " clone fluid cannot be null");
    }
    if (copyFluid == sourceFluid) {
      throw new IllegalArgumentException(name + " clone fluid must be an independent object");
    }
    return copy;
  }

  /** Serializable preview containing immutable evidence and a defensive candidate stream. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result receipt;
    private final StreamInterface candidateSnapshot;

    private Result(AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result receipt,
        StreamInterface candidateStream) {
      this.receipt = receipt;
      this.candidateSnapshot = independentClone(candidateStream, "Candidate stream");
    }

    /** @return immutable evidence that the candidate stream inventory matches the plan. */
    public AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result getApplicationReceipt() {
      return receipt;
    }

    /**
     * Return an independent copy of the detached candidate stream.
     *
     * @return defensive stream and fluid clone containing the planned S8 addition
     * @throws IllegalArgumentException if the stored stream cannot produce an independent clone
     */
    public StreamInterface getCandidateStream() {
      return independentClone(candidateSnapshot, "Stored candidate stream");
    }
  }
}
