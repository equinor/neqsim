package neqsim.process.equipment.reactor;

import java.io.Serializable;

/**
 * Selects one explicit fit path from an elemental-sulfur allocation for mass-based transfer to NeqSim's existing S8
 * component path.
 *
 * <p>
 * This class records a caller-supplied product-identity basis and downstream idempotency key. It does not qualify the
 * S8 representation, convert sulfur mass to S8 moles, mutate a stream, or run a flash, deposition, filter, wall,
 * corrosion, process, transient, or pipeline model.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8Transfer {
  /** Existing NeqSim component name used for an explicitly selected S8 representation. */
  public static final String S8_COMPONENT_NAME = "S8";

  private static final int MAXIMUM_IDENTIFIER_LENGTH = 256;

  private AqueousHydrogenSulfideOxidationS8Transfer() {
  }

  /** Published fit-scatter path selected explicitly by the caller. */
  public enum FitPath {
    /** Lower-rate Millero fit path. */
    LOWER_RATE,
    /** Nominal Millero fit path. */
    NOMINAL,
    /** Upper-rate Millero fit path. */
    UPPER_RATE
  }

  /**
   * Create a mass-based transfer receipt for one explicitly selected fit path.
   *
   * @param allocation immutable elemental-sulfur allocation receipt
   * @param fitPath explicit lower-rate, nominal, or upper-rate path
   * @param productIdentityBasisIdentifier caller's basis for representing allocated elemental sulfur as the NeqSim S8
   * component
   * @param downstreamIdempotencyKey key that a downstream ledger must use to prevent repeat consumption
   * @return immutable mass-based S8 transfer receipt
   */
  public static Result create(AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result allocation,
      FitPath fitPath, String productIdentityBasisIdentifier, String downstreamIdempotencyKey) {
    if (allocation == null) {
      throw new IllegalArgumentException("Elemental-sulfur allocation cannot be null");
    }
    if (fitPath == null) {
      throw new IllegalArgumentException("Fit path cannot be null");
    }
    String productBasis = requireIdentifier(productIdentityBasisIdentifier, "Product-identity basis identifier");
    String idempotencyKey = requireIdentifier(downstreamIdempotencyKey, "Downstream idempotency key");

    AqueousHydrogenSulfideOxidationElementalSulfurAllocation.PathResult selectedPath = selectPath(allocation, fitPath);
    validatePath(selectedPath);

    return new Result(allocation.getSourceSegmentIndex(), allocation.getDurationHours(),
        allocation.getWaterInventoryKg(), fitPath, allocation.getAllocationBasisIdentifier(), productBasis,
        idempotencyKey, selectedPath.getSourceSulfurEquivalentMassRateKgPerHour(),
        selectedPath.getAllocatedElementalSulfurMassRateKgPerHour(),
        selectedPath.getUnallocatedSulfurEquivalentMassRateKgPerHour(), selectedPath.getRateClosureResidualKgPerHour(),
        selectedPath.getSourceSulfurEquivalentMassKg(), selectedPath.getAllocatedElementalSulfurMassKg(),
        selectedPath.getUnallocatedSulfurEquivalentMassKg(), selectedPath.getMassClosureResidualKg());
  }

  private static AqueousHydrogenSulfideOxidationElementalSulfurAllocation.PathResult selectPath(
      AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result allocation, FitPath fitPath) {
    switch (fitPath) {
    case LOWER_RATE:
      return allocation.getLowerRate();
    case NOMINAL:
      return allocation.getNominal();
    case UPPER_RATE:
      return allocation.getUpperRate();
    default:
      throw new IllegalArgumentException("Unsupported fit path");
    }
  }

  private static String requireIdentifier(String identifier, String name) {
    if (identifier == null || identifier.isEmpty() || !identifier.equals(identifier.trim())
        || identifier.length() > MAXIMUM_IDENTIFIER_LENGTH) {
      throw new IllegalArgumentException(name + " must be non-blank, trimmed, and no longer than 256 characters");
    }
    return identifier;
  }

  private static void validatePath(AqueousHydrogenSulfideOxidationElementalSulfurAllocation.PathResult path) {
    requireNonNegativeFinite(path.getSourceSulfurEquivalentMassRateKgPerHour(), "Source sulfur-equivalent mass rate");
    requireNonNegativeFinite(path.getAllocatedElementalSulfurMassRateKgPerHour(),
        "Allocated elemental-sulfur mass rate");
    requireNonNegativeFinite(path.getUnallocatedSulfurEquivalentMassRateKgPerHour(),
        "Unallocated sulfur-equivalent mass rate");
    requireNonNegativeFinite(path.getSourceSulfurEquivalentMassKg(), "Source sulfur-equivalent mass");
    requireNonNegativeFinite(path.getAllocatedElementalSulfurMassKg(), "Allocated elemental-sulfur mass");
    requireNonNegativeFinite(path.getUnallocatedSulfurEquivalentMassKg(), "Unallocated sulfur-equivalent mass");
    requireFinite(path.getRateClosureResidualKgPerHour(), "Rate closure residual");
    requireFinite(path.getMassClosureResidualKg(), "Mass closure residual");

    if (path.getAllocatedElementalSulfurMassRateKgPerHour() > path.getSourceSulfurEquivalentMassRateKgPerHour()
        || path.getUnallocatedSulfurEquivalentMassRateKgPerHour() > path.getSourceSulfurEquivalentMassRateKgPerHour()
        || path.getAllocatedElementalSulfurMassKg() > path.getSourceSulfurEquivalentMassKg()
        || path.getUnallocatedSulfurEquivalentMassKg() > path.getSourceSulfurEquivalentMassKg()) {
      throw new IllegalArgumentException("Selected allocation exceeds its source sulfur budget");
    }

    double rateResidual = path.getSourceSulfurEquivalentMassRateKgPerHour()
        - (path.getAllocatedElementalSulfurMassRateKgPerHour()
            + path.getUnallocatedSulfurEquivalentMassRateKgPerHour());
    double massResidual = path.getSourceSulfurEquivalentMassKg()
        - (path.getAllocatedElementalSulfurMassKg() + path.getUnallocatedSulfurEquivalentMassKg());
    if (Double.doubleToLongBits(rateResidual) != Double.doubleToLongBits(path.getRateClosureResidualKgPerHour())
        || Double.doubleToLongBits(massResidual) != Double.doubleToLongBits(path.getMassClosureResidualKg())) {
      throw new IllegalArgumentException("Selected allocation closure evidence is inconsistent");
    }
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

  /** Immutable mass-based transfer receipt for one fit path and one source segment. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final int sourceSegmentIndex;
    private final double durationHours;
    private final double waterInventoryKg;
    private final FitPath fitPath;
    private final String allocationBasisIdentifier;
    private final String productIdentityBasisIdentifier;
    private final String downstreamIdempotencyKey;
    private final double sourceSulfurEquivalentMassRateKgPerHour;
    private final double transferredS8MassRateKgPerHour;
    private final double unallocatedSulfurEquivalentMassRateKgPerHour;
    private final double rateClosureResidualKgPerHour;
    private final double sourceSulfurEquivalentMassKg;
    private final double transferredS8MassKg;
    private final double unallocatedSulfurEquivalentMassKg;
    private final double massClosureResidualKg;

    private Result(int sourceSegmentIndex, double durationHours, double waterInventoryKg, FitPath fitPath,
        String allocationBasisIdentifier, String productIdentityBasisIdentifier, String downstreamIdempotencyKey,
        double sourceSulfurEquivalentMassRateKgPerHour, double transferredS8MassRateKgPerHour,
        double unallocatedSulfurEquivalentMassRateKgPerHour, double rateClosureResidualKgPerHour,
        double sourceSulfurEquivalentMassKg, double transferredS8MassKg, double unallocatedSulfurEquivalentMassKg,
        double massClosureResidualKg) {
      this.sourceSegmentIndex = sourceSegmentIndex;
      this.durationHours = durationHours;
      this.waterInventoryKg = waterInventoryKg;
      this.fitPath = fitPath;
      this.allocationBasisIdentifier = allocationBasisIdentifier;
      this.productIdentityBasisIdentifier = productIdentityBasisIdentifier;
      this.downstreamIdempotencyKey = downstreamIdempotencyKey;
      this.sourceSulfurEquivalentMassRateKgPerHour = sourceSulfurEquivalentMassRateKgPerHour;
      this.transferredS8MassRateKgPerHour = transferredS8MassRateKgPerHour;
      this.unallocatedSulfurEquivalentMassRateKgPerHour = unallocatedSulfurEquivalentMassRateKgPerHour;
      this.rateClosureResidualKgPerHour = rateClosureResidualKgPerHour;
      this.sourceSulfurEquivalentMassKg = sourceSulfurEquivalentMassKg;
      this.transferredS8MassKg = transferredS8MassKg;
      this.unallocatedSulfurEquivalentMassKg = unallocatedSulfurEquivalentMassKg;
      this.massClosureResidualKg = massClosureResidualKg;
    }

    /** @return existing NeqSim component name selected for downstream representation. */
    public String getComponentName() {
      return S8_COMPONENT_NAME;
    }

    /** @return source-order segment index. */
    public int getSourceSegmentIndex() {
      return sourceSegmentIndex;
    }

    /** @return source segment duration [h]. */
    public double getDurationHours() {
      return durationHours;
    }

    /** @return explicit liquid-water inventory used by the source projection [kg]. */
    public double getWaterInventoryKg() {
      return waterInventoryKg;
    }

    /** @return explicitly selected fit path. */
    public FitPath getFitPath() {
      return fitPath;
    }

    /** @return caller-supplied elemental-sulfur allocation-basis identifier. */
    public String getAllocationBasisIdentifier() {
      return allocationBasisIdentifier;
    }

    /** @return caller-supplied basis for selecting the S8 representation. */
    public String getProductIdentityBasisIdentifier() {
      return productIdentityBasisIdentifier;
    }

    /** @return caller-supplied downstream idempotency key. */
    public String getDownstreamIdempotencyKey() {
      return downstreamIdempotencyKey;
    }

    /** @return selected source sulfur-equivalent mass rate [kg S-equivalent/h]. */
    public double getSourceSulfurEquivalentMassRateKgPerHour() {
      return sourceSulfurEquivalentMassRateKgPerHour;
    }

    /** @return selected mass rate represented as the downstream S8 component [kg/h]. */
    public double getTransferredS8MassRateKgPerHour() {
      return transferredS8MassRateKgPerHour;
    }

    /** @return selected unallocated sulfur-equivalent mass rate [kg S-equivalent/h]. */
    public double getUnallocatedSulfurEquivalentMassRateKgPerHour() {
      return unallocatedSulfurEquivalentMassRateKgPerHour;
    }

    /** @return selected rate-basis closure residual [kg/h]. */
    public double getRateClosureResidualKgPerHour() {
      return rateClosureResidualKgPerHour;
    }

    /** @return selected source sulfur-equivalent mass [kg S-equivalent]. */
    public double getSourceSulfurEquivalentMassKg() {
      return sourceSulfurEquivalentMassKg;
    }

    /** @return selected mass represented as the downstream S8 component [kg]. */
    public double getTransferredS8MassKg() {
      return transferredS8MassKg;
    }

    /** @return selected unallocated sulfur-equivalent mass [kg S-equivalent]. */
    public double getUnallocatedSulfurEquivalentMassKg() {
      return unallocatedSulfurEquivalentMassKg;
    }

    /** @return selected mass-basis closure residual [kg]. */
    public double getMassClosureResidualKg() {
      return massClosureResidualKg;
    }
  }
}
