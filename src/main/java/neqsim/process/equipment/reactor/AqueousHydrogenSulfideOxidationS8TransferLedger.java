package neqsim.process.equipment.reactor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Maintains an immutable, append-only accounting ledger for mass-based S8 transfer batches.
 *
 * <p>
 * This class rejects duplicate batch identifiers and downstream idempotency keys across batches,
 * requires one product-identity basis, and closes the cumulative sulfur mass budget. Persisting the
 * serializable result lets a caller retain this bounded duplicate-consumption evidence across a
 * process restart. The class does not provide distributed locking or mutate a stream, flash,
 * deposition, filter, wall, process, transient, or pipeline model.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8TransferLedger {
  private static final int MAXIMUM_IDENTIFIER_LENGTH = 256;

  private AqueousHydrogenSulfideOxidationS8TransferLedger() {
  }

  /**
   * Create one immutable ledger from ordered S8 transfer batches.
   *
   * @param batches non-empty ordered transfer batches
   * @param ledgerIdentifier caller-supplied ledger identifier
   * @return immutable cumulative ledger result
   */
  public static Result create(List<AqueousHydrogenSulfideOxidationS8TransferBatch.Result> batches,
      String ledgerIdentifier) {
    String validatedLedgerIdentifier = requireIdentifier(ledgerIdentifier, "Ledger identifier");
    if (batches == null || batches.isEmpty()) {
      throw new IllegalArgumentException("At least one S8 transfer batch is required");
    }

    List<AqueousHydrogenSulfideOxidationS8TransferBatch.Result> copy = new ArrayList<AqueousHydrogenSulfideOxidationS8TransferBatch.Result>(
        batches.size());
    Set<String> batchIdentifiers = new HashSet<String>();
    Set<String> idempotencyKeys = new HashSet<String>();
    String productIdentityBasisIdentifier = null;
    int transferCount = 0;
    double totalSourceMassKg = 0.0;
    double totalTransferredMassKg = 0.0;
    double totalUnallocatedMassKg = 0.0;

    for (AqueousHydrogenSulfideOxidationS8TransferBatch.Result batch : batches) {
      if (batch == null) {
        throw new IllegalArgumentException("S8 transfer batch cannot be null");
      }
      validateBatch(batch);
      if (!batchIdentifiers.add(batch.getBatchIdentifier())) {
        throw new IllegalArgumentException("Duplicate S8 transfer batch identifier");
      }
      if (productIdentityBasisIdentifier == null) {
        productIdentityBasisIdentifier = batch.getProductIdentityBasisIdentifier();
      } else if (!productIdentityBasisIdentifier.equals(batch.getProductIdentityBasisIdentifier())) {
        throw new IllegalArgumentException("All S8 transfer batches must use one product-identity basis");
      }

      for (AqueousHydrogenSulfideOxidationS8Transfer.Result transfer : batch.getTransfers()) {
        if (!idempotencyKeys.add(transfer.getDownstreamIdempotencyKey())) {
          throw new IllegalArgumentException("Duplicate downstream idempotency key across S8 transfer batches");
        }
      }
      transferCount = finiteCountSum(transferCount, batch.getTransferCount());
      totalSourceMassKg = finiteSum(totalSourceMassKg, batch.getTotalSourceSulfurEquivalentMassKg(),
          "Ledger source sulfur-equivalent mass");
      totalTransferredMassKg = finiteSum(totalTransferredMassKg, batch.getTotalTransferredS8MassKg(),
          "Ledger transferred S8 mass");
      totalUnallocatedMassKg = finiteSum(totalUnallocatedMassKg,
          batch.getTotalUnallocatedSulfurEquivalentMassKg(), "Ledger unallocated sulfur-equivalent mass");
      copy.add(batch);
    }

    double accountedMassKg = finiteSum(totalTransferredMassKg, totalUnallocatedMassKg,
        "Ledger accounted sulfur mass");
    double closureResidualKg = totalSourceMassKg - accountedMassKg;
    requireFinite(closureResidualKg, "Ledger mass closure residual");
    double closureScale = Math.max(Math.abs(totalSourceMassKg), Math.abs(accountedMassKg));
    double closureTolerance = 8.0 * Math.ulp(closureScale);
    if (Math.abs(closureResidualKg) > closureTolerance) {
      throw new IllegalArgumentException("Ledger sulfur mass budget does not close");
    }

    return new Result(validatedLedgerIdentifier, productIdentityBasisIdentifier,
        Collections.unmodifiableList(copy), Collections.unmodifiableSet(new HashSet<String>(idempotencyKeys)),
        transferCount, totalSourceMassKg, totalTransferredMassKg, totalUnallocatedMassKg, closureResidualKg);
  }

  /**
   * Append one batch to an existing ledger without mutating the prior result.
   *
   * <p>
   * A deserialized ledger can be supplied here. Rebuilding the complete ledger rechecks every
   * recorded batch and idempotency key before the append succeeds.
   * </p>
   *
   * @param ledger existing immutable ledger
   * @param batch next transfer batch
   * @return new immutable ledger containing the prior batches followed by the new batch
   */
  public static Result append(Result ledger, AqueousHydrogenSulfideOxidationS8TransferBatch.Result batch) {
    if (ledger == null) {
      throw new IllegalArgumentException("Existing S8 transfer ledger cannot be null");
    }
    if (batch == null) {
      throw new IllegalArgumentException("S8 transfer batch cannot be null");
    }
    List<AqueousHydrogenSulfideOxidationS8TransferBatch.Result> combined = new ArrayList<AqueousHydrogenSulfideOxidationS8TransferBatch.Result>(
        ledger.getBatches());
    combined.add(batch);
    return create(combined, ledger.getLedgerIdentifier());
  }

  private static void validateBatch(AqueousHydrogenSulfideOxidationS8TransferBatch.Result batch) {
    requireIdentifier(batch.getBatchIdentifier(), "Batch identifier");
    requireIdentifier(batch.getProductIdentityBasisIdentifier(), "Product-identity basis identifier");
    if (!AqueousHydrogenSulfideOxidationS8Transfer.S8_COMPONENT_NAME.equals(batch.getComponentName())) {
      throw new IllegalArgumentException("Transfer batch does not identify the NeqSim S8 component");
    }
    if (batch.getTransfers() == null || batch.getTransfers().isEmpty()
        || batch.getTransferCount() != batch.getTransfers().size()) {
      throw new IllegalArgumentException("Transfer batch receipt count is inconsistent");
    }
    requireNonNegativeFinite(batch.getTotalSourceSulfurEquivalentMassKg(),
        "Batch source sulfur-equivalent mass");
    requireNonNegativeFinite(batch.getTotalTransferredS8MassKg(), "Batch transferred S8 mass");
    requireNonNegativeFinite(batch.getTotalUnallocatedSulfurEquivalentMassKg(),
        "Batch unallocated sulfur-equivalent mass");
    requireFinite(batch.getMassClosureResidualKg(), "Batch mass closure residual");

    double residual = batch.getTotalSourceSulfurEquivalentMassKg()
        - finiteSum(batch.getTotalTransferredS8MassKg(), batch.getTotalUnallocatedSulfurEquivalentMassKg(),
            "Batch accounted sulfur mass");
    if (Double.doubleToLongBits(residual) != Double.doubleToLongBits(batch.getMassClosureResidualKg())) {
      throw new IllegalArgumentException("Transfer batch closure evidence is inconsistent");
    }
    for (AqueousHydrogenSulfideOxidationS8Transfer.Result transfer : batch.getTransfers()) {
      if (transfer == null) {
        throw new IllegalArgumentException("S8 transfer receipt cannot be null");
      }
      if (!batch.getProductIdentityBasisIdentifier().equals(transfer.getProductIdentityBasisIdentifier())) {
        throw new IllegalArgumentException("Transfer receipt product-identity basis is inconsistent with its batch");
      }
      requireIdentifier(transfer.getDownstreamIdempotencyKey(), "Downstream idempotency key");
    }
  }

  private static int finiteCountSum(int left, int right) {
    if (right < 0 || left > Integer.MAX_VALUE - right) {
      throw new IllegalArgumentException("Ledger transfer count is not representable");
    }
    return left + right;
  }

  private static String requireIdentifier(String identifier, String name) {
    if (identifier == null || identifier.isEmpty() || !identifier.equals(identifier.trim())
        || identifier.length() > MAXIMUM_IDENTIFIER_LENGTH) {
      throw new IllegalArgumentException(name + " must be non-blank, trimmed, and no longer than 256 characters");
    }
    return identifier;
  }

  private static double finiteSum(double left, double right, String name) {
    double sum = left + right;
    requireFinite(sum, name);
    return sum;
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

  /** Immutable cumulative accounting evidence for ordered S8 transfer batches. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String ledgerIdentifier;
    private final String productIdentityBasisIdentifier;
    private final List<AqueousHydrogenSulfideOxidationS8TransferBatch.Result> batches;
    private final Set<String> downstreamIdempotencyKeys;
    private final int transferCount;
    private final double totalSourceSulfurEquivalentMassKg;
    private final double totalTransferredS8MassKg;
    private final double totalUnallocatedSulfurEquivalentMassKg;
    private final double massClosureResidualKg;

    private Result(String ledgerIdentifier, String productIdentityBasisIdentifier,
        List<AqueousHydrogenSulfideOxidationS8TransferBatch.Result> batches,
        Set<String> downstreamIdempotencyKeys, int transferCount, double totalSourceSulfurEquivalentMassKg,
        double totalTransferredS8MassKg, double totalUnallocatedSulfurEquivalentMassKg,
        double massClosureResidualKg) {
      this.ledgerIdentifier = ledgerIdentifier;
      this.productIdentityBasisIdentifier = productIdentityBasisIdentifier;
      this.batches = batches;
      this.downstreamIdempotencyKeys = downstreamIdempotencyKeys;
      this.transferCount = transferCount;
      this.totalSourceSulfurEquivalentMassKg = totalSourceSulfurEquivalentMassKg;
      this.totalTransferredS8MassKg = totalTransferredS8MassKg;
      this.totalUnallocatedSulfurEquivalentMassKg = totalUnallocatedSulfurEquivalentMassKg;
      this.massClosureResidualKg = massClosureResidualKg;
    }

    /** @return caller-supplied ledger identifier. */
    public String getLedgerIdentifier() {
      return ledgerIdentifier;
    }

    /** @return existing NeqSim component name represented by every transfer. */
    public String getComponentName() {
      return AqueousHydrogenSulfideOxidationS8Transfer.S8_COMPONENT_NAME;
    }

    /** @return shared caller-supplied S8 product-identity basis. */
    public String getProductIdentityBasisIdentifier() {
      return productIdentityBasisIdentifier;
    }

    /** @return ordered, unmodifiable transfer batches. */
    public List<AqueousHydrogenSulfideOxidationS8TransferBatch.Result> getBatches() {
      return batches;
    }

    /** @return unmodifiable set of consumed downstream idempotency keys. */
    public Set<String> getDownstreamIdempotencyKeys() {
      return downstreamIdempotencyKeys;
    }

    /** @return true if the ledger already contains the supplied downstream idempotency key. */
    public boolean containsDownstreamIdempotencyKey(String key) {
      return downstreamIdempotencyKeys.contains(key);
    }

    /** @return number of transfer batches. */
    public int getBatchCount() {
      return batches.size();
    }

    /** @return total number of transfer receipts. */
    public int getTransferCount() {
      return transferCount;
    }

    /** @return cumulative source sulfur-equivalent mass [kg S-equivalent]. */
    public double getTotalSourceSulfurEquivalentMassKg() {
      return totalSourceSulfurEquivalentMassKg;
    }

    /** @return cumulative mass represented as the downstream S8 component [kg]. */
    public double getTotalTransferredS8MassKg() {
      return totalTransferredS8MassKg;
    }

    /** @return cumulative unallocated sulfur-equivalent mass [kg S-equivalent]. */
    public double getTotalUnallocatedSulfurEquivalentMassKg() {
      return totalUnallocatedSulfurEquivalentMassKg;
    }

    /** @return cumulative mass-basis closure residual [kg]. */
    public double getMassClosureResidualKg() {
      return massClosureResidualKg;
    }
  }
}
