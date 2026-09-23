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
 * Verifies an ordered batch of externally applied S8 additions without mutating any stream.
 *
 * <p>
 * Every request captures independent before and after stream snapshots. Verification delegates to
 * {@link AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt}, preserves source order, requires unique target
 * and application identities, and closes both the aggregate S8 increment and the aggregate total-mole increment.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt {
  private static final double COMPARISON_ULPS = 8.0;

  private AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt() {
  }

  /**
   * Verify all externally applied requests as one fail-closed ordered batch.
   *
   * @param requests ordered target-scoped before/after application requests
   * @return immutable aggregate and per-entry verification evidence
   * @throws IllegalArgumentException if the batch, identities, inventories, or closure is invalid
   */
  public static Result verify(List<Request> requests) {
    if (requests == null || requests.isEmpty()) {
      throw new IllegalArgumentException("Application-receipt batch cannot be null or empty");
    }

    List<Request> orderedRequests = new ArrayList<Request>(requests.size());
    Set<String> targetIdentifiers = new HashSet<String>();
    Set<String> applicationKeys = new HashSet<String>();
    for (Request request : requests) {
      if (request == null) {
        throw new IllegalArgumentException("Application-receipt batch cannot contain null requests");
      }
      if (!targetIdentifiers.add(request.observedTargetStateIdentifier)) {
        throw new IllegalArgumentException("Application-receipt batch target identifiers must be unique");
      }
      if (!applicationKeys.add(request.observedApplicationIdempotencyKey)) {
        throw new IllegalArgumentException("Application-receipt batch idempotency keys must be unique");
      }
      orderedRequests.add(request);
    }

    List<AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result> receipts = new ArrayList<AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result>(
        orderedRequests.size());
    double plannedS8IncrementMol = 0.0;
    double observedS8IncrementMol = 0.0;
    double observedTotalIncrementMol = 0.0;
    double s8EntryToleranceMol = 0.0;
    double totalEntryToleranceMol = 0.0;
    double maximumNonS8InventoryResidualMol = 0.0;
    int preservedNonS8ComponentCount = 0;
    int strictAppendCount = 0;
    int unchangedCount = 0;
    for (Request request : orderedRequests) {
      AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result receipt = AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt
          .verify(request.plan, request.observedTargetStateIdentifier, request.observedApplicationIdempotencyKey,
              request.priorSnapshot.getFluid(), request.candidateSnapshot.getFluid());
      plannedS8IncrementMol += receipt.getPlannedS8IncrementMol();
      observedS8IncrementMol += receipt.getObservedS8IncrementMol();
      observedTotalIncrementMol += receipt.getObservedTotalIncrementMol();
      s8EntryToleranceMol += comparisonTolerance(receipt.getPlannedS8IncrementMol(),
          receipt.getObservedS8IncrementMol(), receipt.getPriorS8AmountMol(), receipt.getCandidateS8AmountMol());
      totalEntryToleranceMol += comparisonTolerance(receipt.getObservedS8IncrementMol(),
          receipt.getObservedTotalIncrementMol(), receipt.getPriorTotalAmountMol(),
          receipt.getCandidateTotalAmountMol());
      maximumNonS8InventoryResidualMol = Math.max(maximumNonS8InventoryResidualMol,
          receipt.getMaximumNonS8InventoryResidualMol());
      preservedNonS8ComponentCount += receipt.getPreservedNonS8ComponentCount();
      if (receipt.isStrictAppend()) {
        strictAppendCount++;
      }
      if (receipt.isUnchanged()) {
        unchangedCount++;
      }
      receipts.add(receipt);
    }

    requireNonNegativeFinite(plannedS8IncrementMol, "Aggregate planned S8 increment");
    requireNonNegativeFinite(observedS8IncrementMol, "Aggregate observed S8 increment");
    requireNonNegativeFinite(observedTotalIncrementMol, "Aggregate observed total-mole increment");
    requireNonNegativeFinite(s8EntryToleranceMol, "Aggregate S8 entry tolerance");
    requireNonNegativeFinite(totalEntryToleranceMol, "Aggregate total-mole entry tolerance");
    requireNonNegativeFinite(maximumNonS8InventoryResidualMol, "Maximum non-S8 inventory residual");

    double s8ClosureResidualMol = plannedS8IncrementMol - observedS8IncrementMol;
    double totalAmountClosureResidualMol = observedTotalIncrementMol - observedS8IncrementMol;
    requireFinite(s8ClosureResidualMol, "Aggregate S8 closure residual");
    requireFinite(totalAmountClosureResidualMol, "Aggregate total-mole closure residual");
    double s8ClosureToleranceMol = s8EntryToleranceMol
        + summationTolerance(receipts.size(), plannedS8IncrementMol, observedS8IncrementMol);
    double totalAmountClosureToleranceMol = totalEntryToleranceMol
        + summationTolerance(receipts.size(), observedTotalIncrementMol, observedS8IncrementMol);
    requireNonNegativeFinite(s8ClosureToleranceMol, "Aggregate S8 closure tolerance");
    requireNonNegativeFinite(totalAmountClosureToleranceMol, "Aggregate total-mole closure tolerance");
    if (Math.abs(s8ClosureResidualMol) > s8ClosureToleranceMol) {
      throw new IllegalArgumentException("Application-receipt batch does not close on S8 increment");
    }
    if (Math.abs(totalAmountClosureResidualMol) > totalAmountClosureToleranceMol) {
      throw new IllegalArgumentException("Application-receipt batch total amount does not close on S8 increment");
    }

    return new Result(receipts, strictAppendCount, unchangedCount, preservedNonS8ComponentCount, plannedS8IncrementMol,
        observedS8IncrementMol, observedTotalIncrementMol, s8ClosureResidualMol, s8ClosureToleranceMol,
        totalAmountClosureResidualMol, totalAmountClosureToleranceMol, maximumNonS8InventoryResidualMol);
  }

  private static double comparisonTolerance(double expected, double observed, double priorAmount,
      double candidateAmount) {
    requireFinite(expected, "Expected comparison amount");
    requireFinite(observed, "Observed comparison amount");
    requireFinite(priorAmount, "Prior comparison amount");
    requireFinite(candidateAmount, "Candidate comparison amount");
    return COMPARISON_ULPS * Math.max(Math.ulp(Math.abs(expected)), Math.max(Math.ulp(Math.abs(observed)),
        Math.max(Math.ulp(Math.abs(priorAmount)), Math.ulp(Math.abs(candidateAmount)))));
  }

  private static double summationTolerance(int count, double first, double second) {
    return COMPARISON_ULPS * count * Math.max(Math.ulp(Math.abs(first)), Math.ulp(Math.abs(second)));
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

  /** Immutable input for one externally applied target-scoped stream update. */
  public static final class Request implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan;
    private final String observedTargetStateIdentifier;
    private final String observedApplicationIdempotencyKey;
    private final StreamInterface priorSnapshot;
    private final StreamInterface candidateSnapshot;

    private Request(AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan,
        String observedTargetStateIdentifier, String observedApplicationIdempotencyKey, StreamInterface priorStream,
        StreamInterface candidateStream) {
      if (plan == null) {
        throw new IllegalArgumentException("Component-addition plan cannot be null");
      }
      if (observedTargetStateIdentifier == null || observedApplicationIdempotencyKey == null) {
        throw new IllegalArgumentException("Observed target and application identities cannot be null");
      }
      if (plan.requiresMutation() && (priorStream == candidateStream || (priorStream != null && candidateStream != null
          && priorStream.getFluid() == candidateStream.getFluid()))) {
        throw new IllegalArgumentException("Positive S8 application requires independent before and after streams");
      }
      this.plan = plan;
      this.observedTargetStateIdentifier = observedTargetStateIdentifier;
      this.observedApplicationIdempotencyKey = observedApplicationIdempotencyKey;
      this.priorSnapshot = independentClone(priorStream, "Prior stream");
      this.candidateSnapshot = independentClone(candidateStream, "Candidate stream");
    }

    /**
     * Create one request with independent snapshots of caller-owned before and after streams.
     *
     * @param plan verified target-scoped component-addition plan
     * @param observedTargetStateIdentifier observed target-state identifier
     * @param observedApplicationIdempotencyKey observed application idempotency key
     * @param priorStream caller-owned stream immediately before application
     * @param candidateStream caller-owned stream immediately after application
     * @return immutable request
     */
    public static Request create(AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan,
        String observedTargetStateIdentifier, String observedApplicationIdempotencyKey, StreamInterface priorStream,
        StreamInterface candidateStream) {
      return new Request(plan, observedTargetStateIdentifier, observedApplicationIdempotencyKey, priorStream,
          candidateStream);
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

    /** @return defensive clone of the captured candidate stream and fluid. */
    public StreamInterface getCandidateStream() {
      return independentClone(candidateSnapshot, "Stored candidate stream");
    }
  }

  /** Immutable ordered batch application evidence. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final List<AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result> receipts;
    private final int strictAppendCount;
    private final int unchangedCount;
    private final int preservedNonS8ComponentCount;
    private final double plannedS8IncrementMol;
    private final double observedS8IncrementMol;
    private final double observedTotalIncrementMol;
    private final double s8ClosureResidualMol;
    private final double s8ClosureToleranceMol;
    private final double totalAmountClosureResidualMol;
    private final double totalAmountClosureToleranceMol;
    private final double maximumNonS8InventoryResidualMol;

    private Result(List<AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result> receipts,
        int strictAppendCount, int unchangedCount, int preservedNonS8ComponentCount, double plannedS8IncrementMol,
        double observedS8IncrementMol, double observedTotalIncrementMol, double s8ClosureResidualMol,
        double s8ClosureToleranceMol, double totalAmountClosureResidualMol, double totalAmountClosureToleranceMol,
        double maximumNonS8InventoryResidualMol) {
      this.receipts = Collections.unmodifiableList(
          new ArrayList<AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result>(receipts));
      this.strictAppendCount = strictAppendCount;
      this.unchangedCount = unchangedCount;
      this.preservedNonS8ComponentCount = preservedNonS8ComponentCount;
      this.plannedS8IncrementMol = plannedS8IncrementMol;
      this.observedS8IncrementMol = observedS8IncrementMol;
      this.observedTotalIncrementMol = observedTotalIncrementMol;
      this.s8ClosureResidualMol = s8ClosureResidualMol;
      this.s8ClosureToleranceMol = s8ClosureToleranceMol;
      this.totalAmountClosureResidualMol = totalAmountClosureResidualMol;
      this.totalAmountClosureToleranceMol = totalAmountClosureToleranceMol;
      this.maximumNonS8InventoryResidualMol = maximumNonS8InventoryResidualMol;
    }

    /** @return fresh unmodifiable copy of ordered per-stream receipts. */
    public List<AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result> getReceipts() {
      return Collections.unmodifiableList(
          new ArrayList<AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result>(receipts));
    }

    /** @return number of strict-append entries. */
    public int getStrictAppendCount() {
      return strictAppendCount;
    }

    /** @return number of unchanged entries. */
    public int getUnchangedCount() {
      return unchangedCount;
    }

    /** @return total number of preserved non-S8 component identities across the batch. */
    public int getPreservedNonS8ComponentCount() {
      return preservedNonS8ComponentCount;
    }

    /** @return sum of planned S8 increments [mol]. */
    public double getPlannedS8IncrementMol() {
      return plannedS8IncrementMol;
    }

    /** @return sum of observed S8 increments [mol]. */
    public double getObservedS8IncrementMol() {
      return observedS8IncrementMol;
    }

    /** @return sum of observed total-mole increments [mol]. */
    public double getObservedTotalIncrementMol() {
      return observedTotalIncrementMol;
    }

    /** @return planned minus observed aggregate S8 increment [mol]. */
    public double getS8ClosureResidualMol() {
      return s8ClosureResidualMol;
    }

    /** @return ULP-scaled aggregate S8 closure tolerance [mol]. */
    public double getS8ClosureToleranceMol() {
      return s8ClosureToleranceMol;
    }

    /** @return observed total increment minus observed S8 increment [mol]. */
    public double getTotalAmountClosureResidualMol() {
      return totalAmountClosureResidualMol;
    }

    /** @return ULP-scaled aggregate total-amount closure tolerance [mol]. */
    public double getTotalAmountClosureToleranceMol() {
      return totalAmountClosureToleranceMol;
    }

    /** @return largest absolute non-S8 component inventory residual in any entry [mol]. */
    public double getMaximumNonS8InventoryResidualMol() {
      return maximumNonS8InventoryResidualMol;
    }
  }
}
