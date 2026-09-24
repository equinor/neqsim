package neqsim.process.equipment.reactor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Previews an ordered batch of verified S8 additions on detached stream clones.
 *
 * <p>
 * Every request owns an independent snapshot of its caller-provided stream. Batch evaluation delegates to
 * {@link AqueousHydrogenSulfideOxidationS8StreamApplicationPreview}, preserves source order, requires unique target and
 * application identities, and closes the aggregate S8 increment. No caller stream is mutated and no candidate stream is
 * run or flashed.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview {
  private AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview() {
  }

  /**
   * Preview all requests as one fail-closed ordered batch.
   *
   * @param requests ordered target-scoped application requests
   * @return immutable aggregate evidence and defensive candidate-stream previews
   * @throws IllegalArgumentException if the batch, identities, any request, or aggregate closure is invalid
   */
  public static Result applyToClones(List<Request> requests) {
    if (requests == null || requests.isEmpty()) {
      throw new IllegalArgumentException("Application-preview batch cannot be null or empty");
    }

    List<Request> orderedRequests = new ArrayList<Request>(requests.size());
    Set<String> targetIdentifiers = new HashSet<String>();
    Set<String> applicationKeys = new HashSet<String>();
    for (Request request : requests) {
      if (request == null) {
        throw new IllegalArgumentException("Application-preview batch cannot contain null requests");
      }
      if (!targetIdentifiers.add(request.observedTargetStateIdentifier)) {
        throw new IllegalArgumentException("Application-preview batch target identifiers must be unique");
      }
      if (!applicationKeys.add(request.observedApplicationIdempotencyKey)) {
        throw new IllegalArgumentException("Application-preview batch idempotency keys must be unique");
      }
      orderedRequests.add(request);
    }

    List<AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.Result> previews = new ArrayList<AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.Result>(
        orderedRequests.size());
    double plannedIncrementMol = 0.0;
    double observedIncrementMol = 0.0;
    double entryComparisonToleranceMol = 0.0;
    int strictAppendCount = 0;
    int unchangedCount = 0;
    for (Request request : orderedRequests) {
      AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.Result preview = AqueousHydrogenSulfideOxidationS8StreamApplicationPreview
          .applyToClone(request.plan, request.observedTargetStateIdentifier, request.observedApplicationIdempotencyKey,
              request.priorSnapshot);
      AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result receipt = preview.getApplicationReceipt();
      plannedIncrementMol += request.plan.getTransferredS8AmountMol();
      observedIncrementMol += receipt.getObservedS8IncrementMol();
      entryComparisonToleranceMol += 8.0 * Math.max(Math.ulp(Math.abs(request.plan.getTransferredS8AmountMol())),
          Math.max(Math.ulp(Math.abs(receipt.getObservedS8IncrementMol())),
              Math.max(Math.ulp(Math.abs(receipt.getPriorS8AmountMol())),
                  Math.ulp(Math.abs(receipt.getCandidateS8AmountMol())))));
      if (receipt.isStrictAppend()) {
        strictAppendCount++;
      }
      if (receipt.isUnchanged()) {
        unchangedCount++;
      }
      previews.add(preview);
    }

    requireNonNegativeFinite(plannedIncrementMol, "Aggregate planned S8 increment");
    requireNonNegativeFinite(observedIncrementMol, "Aggregate observed S8 increment");
    double closureResidualMol = plannedIncrementMol - observedIncrementMol;
    requireFinite(closureResidualMol, "Aggregate S8 closure residual");
    requireNonNegativeFinite(entryComparisonToleranceMol, "Aggregate entry-comparison tolerance");
    double summationToleranceMol = 8.0 * previews.size()
        * Math.max(Math.ulp(Math.abs(plannedIncrementMol)), Math.ulp(Math.abs(observedIncrementMol)));
    double closureToleranceMol = entryComparisonToleranceMol + summationToleranceMol;
    requireNonNegativeFinite(closureToleranceMol, "Aggregate S8 closure tolerance");
    if (Math.abs(closureResidualMol) > closureToleranceMol) {
      throw new IllegalArgumentException("Application-preview batch does not close on S8 increment");
    }

    return new Result(previews, strictAppendCount, unchangedCount, plannedIncrementMol, observedIncrementMol,
        closureResidualMol, closureToleranceMol);
  }

  private static StreamInterface independentClone(StreamInterface source, String name) {
    if (source == null) {
      throw new IllegalArgumentException(name + " cannot be null");
    }
    SystemInterface sourceFluid = source.getFluid();
    if (sourceFluid == null) {
      throw new IllegalArgumentException(name + " fluid cannot be null");
    }
    StreamInterface copy = source.clone();
    if (copy == null || copy == source) {
      throw new IllegalArgumentException(name + " clone must be an independent object");
    }
    SystemInterface copyFluid = copy.getFluid();
    if (copyFluid == null || copyFluid == sourceFluid) {
      throw new IllegalArgumentException(name + " clone fluid must be an independent object");
    }
    return copy;
  }

  private static void requireNonNegativeFinite(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
  }

  private static void requireFinite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
  }

  /** Immutable input for one target-scoped stream preview. */
  public static final class Request implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan;
    private final String observedTargetStateIdentifier;
    private final String observedApplicationIdempotencyKey;
    private final StreamInterface priorSnapshot;

    private Request(AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan,
        String observedTargetStateIdentifier, String observedApplicationIdempotencyKey, StreamInterface priorStream) {
      if (plan == null) {
        throw new IllegalArgumentException("Component-addition plan cannot be null");
      }
      if (observedTargetStateIdentifier == null || observedApplicationIdempotencyKey == null) {
        throw new IllegalArgumentException("Observed target and application identities cannot be null");
      }
      this.plan = plan;
      this.observedTargetStateIdentifier = observedTargetStateIdentifier;
      this.observedApplicationIdempotencyKey = observedApplicationIdempotencyKey;
      this.priorSnapshot = independentClone(priorStream, "Prior stream");
    }

    /**
     * Create one request with an independent snapshot of the caller-owned stream.
     *
     * @param plan verified target-scoped component-addition plan
     * @param observedTargetStateIdentifier observed identifier for the prior stream state
     * @param observedApplicationIdempotencyKey observed application idempotency key
     * @param priorStream caller-owned prior stream
     * @return immutable request
     */
    public static Request create(AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan,
        String observedTargetStateIdentifier, String observedApplicationIdempotencyKey, StreamInterface priorStream) {
      return new Request(plan, observedTargetStateIdentifier, observedApplicationIdempotencyKey, priorStream);
    }

    /** @return verified target-scoped component-addition plan. */
    public AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result getPlan() {
      return plan;
    }

    /** @return observed stable target-state identifier. */
    public String getObservedTargetStateIdentifier() {
      return observedTargetStateIdentifier;
    }

    /** @return observed application idempotency key. */
    public String getObservedApplicationIdempotencyKey() {
      return observedApplicationIdempotencyKey;
    }

    /** @return defensive clone of the captured prior stream and fluid. */
    public StreamInterface getPriorStream() {
      return independentClone(priorSnapshot, "Stored prior stream");
    }
  }

  /** Immutable ordered batch evidence. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final List<AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.Result> previews;
    private final int strictAppendCount;
    private final int unchangedCount;
    private final double plannedS8IncrementMol;
    private final double observedS8IncrementMol;
    private final double closureResidualMol;
    private final double closureToleranceMol;

    private Result(List<AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.Result> previews,
        int strictAppendCount, int unchangedCount, double plannedS8IncrementMol, double observedS8IncrementMol,
        double closureResidualMol, double closureToleranceMol) {
      this.previews = Collections
          .unmodifiableList(new ArrayList<AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.Result>(previews));
      this.strictAppendCount = strictAppendCount;
      this.unchangedCount = unchangedCount;
      this.plannedS8IncrementMol = plannedS8IncrementMol;
      this.observedS8IncrementMol = observedS8IncrementMol;
      this.closureResidualMol = closureResidualMol;
      this.closureToleranceMol = closureToleranceMol;
    }

    /** @return ordered immutable per-stream previews. */
    public List<AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.Result> getPreviews() {
      return Collections
          .unmodifiableList(new ArrayList<AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.Result>(previews));
    }

    /** @return number of strict-append entries. */
    public int getStrictAppendCount() {
      return strictAppendCount;
    }

    /** @return number of unchanged entries. */
    public int getUnchangedCount() {
      return unchangedCount;
    }

    /** @return sum of verified planned S8 increments [mol]. */
    public double getPlannedS8IncrementMol() {
      return plannedS8IncrementMol;
    }

    /** @return sum of observed candidate-minus-prior S8 increments [mol]. */
    public double getObservedS8IncrementMol() {
      return observedS8IncrementMol;
    }

    /** @return planned minus observed aggregate S8 increment [mol]. */
    public double getClosureResidualMol() {
      return closureResidualMol;
    }

    /** @return ULP-scaled aggregate closure tolerance [mol]. */
    public double getClosureToleranceMol() {
      return closureToleranceMol;
    }
  }
}
