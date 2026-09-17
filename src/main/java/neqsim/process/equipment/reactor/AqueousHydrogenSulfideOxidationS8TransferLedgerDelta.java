package neqsim.process.equipment.reactor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reconciles two immutable S8 transfer-ledger states as an exact ordered-prefix transition.
 *
 * <p>
 * This class rebuilds both supplied ledgers through their existing validation contract, then proves that every batch
 * and transfer in the prior state is bitwise identical and in the same position in the candidate state. It reports only
 * accounting deltas. It does not mutate either ledger, append a batch, persist state, coordinate writers, or apply S8
 * to a process model.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8TransferLedgerDelta {
  private AqueousHydrogenSulfideOxidationS8TransferLedgerDelta() {
  }

  /**
   * Reconcile a prior ledger state with an unchanged or append-only candidate state.
   *
   * @param prior persisted prior ledger state
   * @param candidate candidate successor ledger state
   * @return immutable reconciliation receipt
   * @throws IllegalArgumentException if either state is invalid or the candidate is not an exact ordered extension of
   * the prior state
   */
  public static Result reconcile(AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior,
      AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate) {
    if (prior == null || candidate == null) {
      throw new IllegalArgumentException("Prior and candidate S8 transfer ledgers are required");
    }

    AqueousHydrogenSulfideOxidationS8TransferLedger.Result validatedPrior = AqueousHydrogenSulfideOxidationS8TransferLedger
        .create(prior.getBatches(), prior.getLedgerIdentifier());
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result validatedCandidate = AqueousHydrogenSulfideOxidationS8TransferLedger
        .create(candidate.getBatches(), candidate.getLedgerIdentifier());

    if (!validatedPrior.getLedgerIdentifier().equals(validatedCandidate.getLedgerIdentifier())) {
      throw new IllegalArgumentException("Ledger identifiers do not match");
    }
    if (!validatedPrior.getComponentName().equals(validatedCandidate.getComponentName())) {
      throw new IllegalArgumentException("Ledger component identities do not match");
    }
    if (!validatedPrior.getProductIdentityBasisIdentifier()
        .equals(validatedCandidate.getProductIdentityBasisIdentifier())) {
      throw new IllegalArgumentException("Ledger product-identity bases do not match");
    }
    if (validatedCandidate.getBatchCount() < validatedPrior.getBatchCount()) {
      throw new IllegalArgumentException("Candidate ledger truncates the prior state");
    }

    for (int index = 0; index < validatedPrior.getBatchCount(); index++) {
      if (!sameBatch(validatedPrior.getBatches().get(index), validatedCandidate.getBatches().get(index))) {
        throw new IllegalArgumentException("Candidate ledger does not preserve the exact prior batch prefix");
      }
    }

    List<AqueousHydrogenSulfideOxidationS8TransferBatch.Result> addedBatches = new ArrayList<AqueousHydrogenSulfideOxidationS8TransferBatch.Result>(
        validatedCandidate.getBatches().subList(validatedPrior.getBatchCount(), validatedCandidate.getBatchCount()));
    int addedTransferCount = validatedCandidate.getTransferCount() - validatedPrior.getTransferCount();
    double sourceMassDeltaKg = nonNegativeDifference(validatedCandidate.getTotalSourceSulfurEquivalentMassKg(),
        validatedPrior.getTotalSourceSulfurEquivalentMassKg(), "Source sulfur-equivalent mass delta");
    double transferredMassDeltaKg = nonNegativeDifference(validatedCandidate.getTotalTransferredS8MassKg(),
        validatedPrior.getTotalTransferredS8MassKg(), "Transferred S8 mass delta");
    double unallocatedMassDeltaKg = nonNegativeDifference(
        validatedCandidate.getTotalUnallocatedSulfurEquivalentMassKg(),
        validatedPrior.getTotalUnallocatedSulfurEquivalentMassKg(), "Unallocated sulfur-equivalent mass delta");
    double closureResidualDeltaKg = validatedCandidate.getMassClosureResidualKg()
        - validatedPrior.getMassClosureResidualKg();
    if (addedTransferCount < 0 || !Double.isFinite(closureResidualDeltaKg)) {
      throw new IllegalArgumentException("Ledger reconciliation delta is not representable");
    }

    return new Result(validatedPrior.getLedgerIdentifier(), validatedPrior.getProductIdentityBasisIdentifier(),
        Collections.unmodifiableList(addedBatches), addedTransferCount, sourceMassDeltaKg, transferredMassDeltaKg,
        unallocatedMassDeltaKg, closureResidualDeltaKg);
  }

  private static boolean sameBatch(AqueousHydrogenSulfideOxidationS8TransferBatch.Result left,
      AqueousHydrogenSulfideOxidationS8TransferBatch.Result right) {
    if (!left.getBatchIdentifier().equals(right.getBatchIdentifier())
        || !left.getComponentName().equals(right.getComponentName())
        || !left.getProductIdentityBasisIdentifier().equals(right.getProductIdentityBasisIdentifier())
        || left.getTransferCount() != right.getTransferCount()
        || !sameDouble(left.getTotalSourceSulfurEquivalentMassKg(), right.getTotalSourceSulfurEquivalentMassKg())
        || !sameDouble(left.getTotalTransferredS8MassKg(), right.getTotalTransferredS8MassKg())
        || !sameDouble(left.getTotalUnallocatedSulfurEquivalentMassKg(),
            right.getTotalUnallocatedSulfurEquivalentMassKg())
        || !sameDouble(left.getMassClosureResidualKg(), right.getMassClosureResidualKg())) {
      return false;
    }
    for (int index = 0; index < left.getTransferCount(); index++) {
      if (!sameTransfer(left.getTransfers().get(index), right.getTransfers().get(index))) {
        return false;
      }
    }
    return true;
  }

  private static boolean sameTransfer(AqueousHydrogenSulfideOxidationS8Transfer.Result left,
      AqueousHydrogenSulfideOxidationS8Transfer.Result right) {
    return left.getComponentName().equals(right.getComponentName())
        && left.getSourceSegmentIndex() == right.getSourceSegmentIndex()
        && sameDouble(left.getDurationHours(), right.getDurationHours())
        && sameDouble(left.getWaterInventoryKg(), right.getWaterInventoryKg())
        && left.getFitPath() == right.getFitPath()
        && left.getAllocationBasisIdentifier().equals(right.getAllocationBasisIdentifier())
        && left.getProductIdentityBasisIdentifier().equals(right.getProductIdentityBasisIdentifier())
        && left.getDownstreamIdempotencyKey().equals(right.getDownstreamIdempotencyKey())
        && sameDouble(left.getSourceSulfurEquivalentMassRateKgPerHour(),
            right.getSourceSulfurEquivalentMassRateKgPerHour())
        && sameDouble(left.getTransferredS8MassRateKgPerHour(), right.getTransferredS8MassRateKgPerHour())
        && sameDouble(left.getUnallocatedSulfurEquivalentMassRateKgPerHour(),
            right.getUnallocatedSulfurEquivalentMassRateKgPerHour())
        && sameDouble(left.getRateClosureResidualKgPerHour(), right.getRateClosureResidualKgPerHour())
        && sameDouble(left.getSourceSulfurEquivalentMassKg(), right.getSourceSulfurEquivalentMassKg())
        && sameDouble(left.getTransferredS8MassKg(), right.getTransferredS8MassKg())
        && sameDouble(left.getUnallocatedSulfurEquivalentMassKg(), right.getUnallocatedSulfurEquivalentMassKg())
        && sameDouble(left.getMassClosureResidualKg(), right.getMassClosureResidualKg());
  }

  private static boolean sameDouble(double left, double right) {
    return Double.doubleToLongBits(left) == Double.doubleToLongBits(right);
  }

  private static double nonNegativeDifference(double candidate, double prior, String name) {
    double difference = candidate - prior;
    if (!Double.isFinite(difference) || difference < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
    return difference;
  }

  /** Immutable accounting delta between an exact prior prefix and its candidate successor. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String ledgerIdentifier;
    private final String productIdentityBasisIdentifier;
    private final List<AqueousHydrogenSulfideOxidationS8TransferBatch.Result> addedBatches;
    private final int addedTransferCount;
    private final double sourceSulfurEquivalentMassDeltaKg;
    private final double transferredS8MassDeltaKg;
    private final double unallocatedSulfurEquivalentMassDeltaKg;
    private final double massClosureResidualDeltaKg;

    private Result(String ledgerIdentifier, String productIdentityBasisIdentifier,
        List<AqueousHydrogenSulfideOxidationS8TransferBatch.Result> addedBatches, int addedTransferCount,
        double sourceSulfurEquivalentMassDeltaKg, double transferredS8MassDeltaKg,
        double unallocatedSulfurEquivalentMassDeltaKg, double massClosureResidualDeltaKg) {
      this.ledgerIdentifier = ledgerIdentifier;
      this.productIdentityBasisIdentifier = productIdentityBasisIdentifier;
      this.addedBatches = Collections
          .unmodifiableList(new ArrayList<AqueousHydrogenSulfideOxidationS8TransferBatch.Result>(addedBatches));
      this.addedTransferCount = addedTransferCount;
      this.sourceSulfurEquivalentMassDeltaKg = sourceSulfurEquivalentMassDeltaKg;
      this.transferredS8MassDeltaKg = transferredS8MassDeltaKg;
      this.unallocatedSulfurEquivalentMassDeltaKg = unallocatedSulfurEquivalentMassDeltaKg;
      this.massClosureResidualDeltaKg = massClosureResidualDeltaKg;
    }

    /** @return reconciled caller-supplied ledger identifier. */
    public String getLedgerIdentifier() {
      return ledgerIdentifier;
    }

    /** @return reconciled caller-supplied S8 product-identity basis. */
    public String getProductIdentityBasisIdentifier() {
      return productIdentityBasisIdentifier;
    }

    /** @return existing NeqSim component name represented by the reconciled ledgers. */
    public String getComponentName() {
      return AqueousHydrogenSulfideOxidationS8Transfer.S8_COMPONENT_NAME;
    }

    /** @return ordered, unmodifiable batches added after the exact prior prefix. */
    public List<AqueousHydrogenSulfideOxidationS8TransferBatch.Result> getAddedBatches() {
      return addedBatches;
    }

    /** @return number of batches added after the exact prior prefix. */
    public int getAddedBatchCount() {
      return addedBatches.size();
    }

    /** @return number of transfer receipts added after the exact prior prefix. */
    public int getAddedTransferCount() {
      return addedTransferCount;
    }

    /** @return whether candidate and prior represent the same exact ledger state. */
    public boolean isUnchanged() {
      return addedBatches.isEmpty();
    }

    /** @return whether the candidate is a strict append-only extension of the prior state. */
    public boolean isStrictAppend() {
      return !addedBatches.isEmpty();
    }

    /** @return added source sulfur-equivalent mass [kg S-equivalent]. */
    public double getSourceSulfurEquivalentMassDeltaKg() {
      return sourceSulfurEquivalentMassDeltaKg;
    }

    /** @return added mass represented as the downstream S8 component [kg]. */
    public double getTransferredS8MassDeltaKg() {
      return transferredS8MassDeltaKg;
    }

    /** @return added unallocated sulfur-equivalent mass [kg S-equivalent]. */
    public double getUnallocatedSulfurEquivalentMassDeltaKg() {
      return unallocatedSulfurEquivalentMassDeltaKg;
    }

    /** @return change in cumulative mass-closure residual [kg]. */
    public double getMassClosureResidualDeltaKg() {
      return massClosureResidualDeltaKg;
    }
  }
}
