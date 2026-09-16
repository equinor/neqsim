package neqsim.process.equipment.reactor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Groups mass-based S8 transfer receipts into one immutable, duplicate-safe accounting batch.
 *
 * <p>
 * This class validates one shared product-identity basis, rejects duplicate downstream idempotency keys, and closes the
 * aggregate sulfur mass budget. It does not persist consumed keys across batches, add S8 to a stream, or run a flash,
 * deposition, filter, wall, corrosion, process, transient, or pipeline model.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8TransferBatch {
  private static final int MAXIMUM_IDENTIFIER_LENGTH = 256;

  private AqueousHydrogenSulfideOxidationS8TransferBatch() {
  }

  /**
   * Create one immutable accounting batch from ordered S8 transfer receipts.
   *
   * @param transfers non-empty ordered transfer receipts
   * @param batchIdentifier caller-supplied batch identifier
   * @return immutable batch result
   */
  public static Result create(List<AqueousHydrogenSulfideOxidationS8Transfer.Result> transfers,
      String batchIdentifier) {
    String validatedBatchIdentifier = requireIdentifier(batchIdentifier, "Batch identifier");
    if (transfers == null || transfers.isEmpty()) {
      throw new IllegalArgumentException("At least one S8 transfer receipt is required");
    }

    List<AqueousHydrogenSulfideOxidationS8Transfer.Result> copy = new ArrayList<AqueousHydrogenSulfideOxidationS8Transfer.Result>(
        transfers.size());
    Set<String> idempotencyKeys = new HashSet<String>();
    String productIdentityBasisIdentifier = null;
    double totalSourceMassKg = 0.0;
    double totalTransferredMassKg = 0.0;
    double totalUnallocatedMassKg = 0.0;

    for (AqueousHydrogenSulfideOxidationS8Transfer.Result transfer : transfers) {
      if (transfer == null) {
        throw new IllegalArgumentException("S8 transfer receipt cannot be null");
      }
      validateTransfer(transfer);
      if (productIdentityBasisIdentifier == null) {
        productIdentityBasisIdentifier = transfer.getProductIdentityBasisIdentifier();
      } else if (!productIdentityBasisIdentifier.equals(transfer.getProductIdentityBasisIdentifier())) {
        throw new IllegalArgumentException("All S8 transfer receipts must use one product-identity basis");
      }
      if (!idempotencyKeys.add(transfer.getDownstreamIdempotencyKey())) {
        throw new IllegalArgumentException("Duplicate downstream idempotency key");
      }

      totalSourceMassKg = finiteSum(totalSourceMassKg, transfer.getSourceSulfurEquivalentMassKg(),
          "Total source sulfur-equivalent mass");
      totalTransferredMassKg = finiteSum(totalTransferredMassKg, transfer.getTransferredS8MassKg(),
          "Total transferred S8 mass");
      totalUnallocatedMassKg = finiteSum(totalUnallocatedMassKg, transfer.getUnallocatedSulfurEquivalentMassKg(),
          "Total unallocated sulfur-equivalent mass");
      copy.add(transfer);
    }

    double accountedMassKg = finiteSum(totalTransferredMassKg, totalUnallocatedMassKg, "Total accounted sulfur mass");
    double closureResidualKg = totalSourceMassKg - accountedMassKg;
    requireFinite(closureResidualKg, "Aggregate mass closure residual");
    double closureScale = Math.max(Math.abs(totalSourceMassKg), Math.abs(accountedMassKg));
    double closureTolerance = 8.0 * Math.ulp(closureScale);
    if (Math.abs(closureResidualKg) > closureTolerance) {
      throw new IllegalArgumentException("Aggregate sulfur mass budget does not close");
    }

    return new Result(validatedBatchIdentifier, productIdentityBasisIdentifier, Collections.unmodifiableList(copy),
        totalSourceMassKg, totalTransferredMassKg, totalUnallocatedMassKg, closureResidualKg);
  }

  private static void validateTransfer(AqueousHydrogenSulfideOxidationS8Transfer.Result transfer) {
    if (!AqueousHydrogenSulfideOxidationS8Transfer.S8_COMPONENT_NAME.equals(transfer.getComponentName())) {
      throw new IllegalArgumentException("Transfer receipt does not identify the NeqSim S8 component");
    }
    requireIdentifier(transfer.getProductIdentityBasisIdentifier(), "Product-identity basis identifier");
    requireIdentifier(transfer.getDownstreamIdempotencyKey(), "Downstream idempotency key");
    requireNonNegativeFinite(transfer.getSourceSulfurEquivalentMassKg(), "Source sulfur-equivalent mass");
    requireNonNegativeFinite(transfer.getTransferredS8MassKg(), "Transferred S8 mass");
    requireNonNegativeFinite(transfer.getUnallocatedSulfurEquivalentMassKg(), "Unallocated sulfur-equivalent mass");
    requireFinite(transfer.getMassClosureResidualKg(), "Mass closure residual");

    double residual = transfer.getSourceSulfurEquivalentMassKg() - finiteSum(transfer.getTransferredS8MassKg(),
        transfer.getUnallocatedSulfurEquivalentMassKg(), "Receipt accounted sulfur mass");
    if (Double.doubleToLongBits(residual) != Double.doubleToLongBits(transfer.getMassClosureResidualKg())) {
      throw new IllegalArgumentException("Transfer receipt closure evidence is inconsistent");
    }
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

  /** Immutable, aggregate accounting evidence for one ordered transfer batch. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String batchIdentifier;
    private final String productIdentityBasisIdentifier;
    private final List<AqueousHydrogenSulfideOxidationS8Transfer.Result> transfers;
    private final double totalSourceSulfurEquivalentMassKg;
    private final double totalTransferredS8MassKg;
    private final double totalUnallocatedSulfurEquivalentMassKg;
    private final double massClosureResidualKg;

    private Result(String batchIdentifier, String productIdentityBasisIdentifier,
        List<AqueousHydrogenSulfideOxidationS8Transfer.Result> transfers, double totalSourceSulfurEquivalentMassKg,
        double totalTransferredS8MassKg, double totalUnallocatedSulfurEquivalentMassKg, double massClosureResidualKg) {
      this.batchIdentifier = batchIdentifier;
      this.productIdentityBasisIdentifier = productIdentityBasisIdentifier;
      this.transfers = transfers;
      this.totalSourceSulfurEquivalentMassKg = totalSourceSulfurEquivalentMassKg;
      this.totalTransferredS8MassKg = totalTransferredS8MassKg;
      this.totalUnallocatedSulfurEquivalentMassKg = totalUnallocatedSulfurEquivalentMassKg;
      this.massClosureResidualKg = massClosureResidualKg;
    }

    /** @return caller-supplied batch identifier. */
    public String getBatchIdentifier() {
      return batchIdentifier;
    }

    /** @return existing NeqSim component name represented by every transfer. */
    public String getComponentName() {
      return AqueousHydrogenSulfideOxidationS8Transfer.S8_COMPONENT_NAME;
    }

    /** @return shared caller-supplied S8 product-identity basis. */
    public String getProductIdentityBasisIdentifier() {
      return productIdentityBasisIdentifier;
    }

    /** @return ordered, unmodifiable transfer receipts. */
    public List<AqueousHydrogenSulfideOxidationS8Transfer.Result> getTransfers() {
      return transfers;
    }

    /** @return number of transfer receipts. */
    public int getTransferCount() {
      return transfers.size();
    }

    /** @return aggregate source sulfur-equivalent mass [kg S-equivalent]. */
    public double getTotalSourceSulfurEquivalentMassKg() {
      return totalSourceSulfurEquivalentMassKg;
    }

    /** @return aggregate mass represented as the downstream S8 component [kg]. */
    public double getTotalTransferredS8MassKg() {
      return totalTransferredS8MassKg;
    }

    /** @return aggregate unallocated sulfur-equivalent mass [kg S-equivalent]. */
    public double getTotalUnallocatedSulfurEquivalentMassKg() {
      return totalUnallocatedSulfurEquivalentMassKg;
    }

    /** @return aggregate mass-basis closure residual [kg]. */
    public double getMassClosureResidualKg() {
      return massClosureResidualKg;
    }
  }
}
