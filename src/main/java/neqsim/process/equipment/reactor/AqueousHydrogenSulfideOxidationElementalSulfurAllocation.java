package neqsim.process.equipment.reactor;

import java.io.Serializable;

/**
 * Creates an explicit, non-mutating elemental-sulfur allocation scenario from one qualified aqueous H2S/O2
 * sulfur-equivalent segment budget.
 *
 * <p>
 * The caller supplies the allocation fraction and a basis identifier. This class does not provide a default fraction,
 * qualify the caller's basis, infer a product yield, create S8, or mutate a process model. The unallocated
 * sulfur-equivalent remainder is retained so downstream accounting can avoid applying the same sulfur twice.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationElementalSulfurAllocation {
  private static final int MAXIMUM_BASIS_IDENTIFIER_LENGTH = 256;

  private AqueousHydrogenSulfideOxidationElementalSulfurAllocation() {
  }

  /**
   * Allocate an explicit fraction of one segment's sulfur-equivalent loss to an elemental-sulfur scenario.
   *
   * @param source qualified dimensional water-inventory projection for one segment
   * @param elementalSulfurAllocationFraction caller-supplied allocation fraction [fraction]
   * @param allocationBasisIdentifier non-blank identifier for the caller's allocation basis
   * @return immutable allocation receipt for the lower, nominal, and upper fit-scatter paths
   */
  public static Result allocate(AqueousHydrogenSulfideOxidationWaterInventoryProjection.Result source,
      double elementalSulfurAllocationFraction, String allocationBasisIdentifier) {
    if (source == null) {
      throw new IllegalArgumentException("Source sulfur-equivalent budget cannot be null");
    }
    if (!Double.isFinite(elementalSulfurAllocationFraction) || elementalSulfurAllocationFraction < 0.0
        || elementalSulfurAllocationFraction > 1.0) {
      throw new IllegalArgumentException("Elemental-sulfur allocation fraction must be finite and in [0, 1]");
    }
    String basisIdentifier = requireBasisIdentifier(allocationBasisIdentifier);

    PathResult lowerRate = allocatePath(source.getLowerRateMeanSulfurEquivalentMassRateKgPerHour(),
        source.getLowerRateReactedSulfurEquivalentMassKg(), elementalSulfurAllocationFraction, "Lower-rate");
    PathResult nominal = allocatePath(source.getNominalMeanSulfurEquivalentMassRateKgPerHour(),
        source.getNominalReactedSulfurEquivalentMassKg(), elementalSulfurAllocationFraction, "Nominal");
    PathResult upperRate = allocatePath(source.getUpperRateMeanSulfurEquivalentMassRateKgPerHour(),
        source.getUpperRateReactedSulfurEquivalentMassKg(), elementalSulfurAllocationFraction, "Upper-rate");

    return new Result(source.getSegmentIndex(), source.getDurationHours(), source.getWaterInventoryKg(),
        elementalSulfurAllocationFraction, basisIdentifier, lowerRate, nominal, upperRate);
  }

  private static String requireBasisIdentifier(String identifier) {
    if (identifier == null || identifier.isEmpty() || !identifier.equals(identifier.trim())
        || identifier.length() > MAXIMUM_BASIS_IDENTIFIER_LENGTH) {
      throw new IllegalArgumentException(
          "Allocation basis identifier must be non-blank, trimmed, and no longer than 256 characters");
    }
    return identifier;
  }

  private static PathResult allocatePath(double sourceMassRateKgPerHour, double sourceMassKg, double allocationFraction,
      String pathName) {
    requireNonNegativeFinite(sourceMassRateKgPerHour, pathName + " source sulfur-equivalent mass rate");
    requireNonNegativeFinite(sourceMassKg, pathName + " source sulfur-equivalent mass");

    double allocatedMassRateKgPerHour = finiteProduct(sourceMassRateKgPerHour, allocationFraction,
        pathName + " allocated elemental-sulfur mass rate");
    double allocatedMassKg = finiteProduct(sourceMassKg, allocationFraction,
        pathName + " allocated elemental-sulfur mass");
    double unallocatedMassRateKgPerHour = finiteDifference(sourceMassRateKgPerHour, allocatedMassRateKgPerHour,
        pathName + " unallocated sulfur-equivalent mass rate");
    double unallocatedMassKg = finiteDifference(sourceMassKg, allocatedMassKg,
        pathName + " unallocated sulfur-equivalent mass");

    double rateClosureResidualKgPerHour = sourceMassRateKgPerHour
        - (allocatedMassRateKgPerHour + unallocatedMassRateKgPerHour);
    double massClosureResidualKg = sourceMassKg - (allocatedMassKg + unallocatedMassKg);
    requireFinite(rateClosureResidualKgPerHour, pathName + " allocation rate closure residual");
    requireFinite(massClosureResidualKg, pathName + " allocation mass closure residual");

    return new PathResult(sourceMassRateKgPerHour, allocatedMassRateKgPerHour, unallocatedMassRateKgPerHour,
        rateClosureResidualKgPerHour, sourceMassKg, allocatedMassKg, unallocatedMassKg, massClosureResidualKg);
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

  private static double finiteProduct(double first, double second, String name) {
    double product = first * second;
    if (!Double.isFinite(product) || product < 0.0 || (first > 0.0 && second > 0.0 && product == 0.0)) {
      throw new IllegalArgumentException(name + " is not finite and representable");
    }
    return product;
  }

  private static double finiteDifference(double minuend, double subtrahend, String name) {
    double difference = minuend - subtrahend;
    if (!Double.isFinite(difference) || difference < 0.0) {
      throw new IllegalArgumentException(name + " is not finite and non-negative");
    }
    return difference;
  }

  /** Immutable allocation receipt for one qualified source segment. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final int sourceSegmentIndex;
    private final double durationHours;
    private final double waterInventoryKg;
    private final double elementalSulfurAllocationFraction;
    private final String allocationBasisIdentifier;
    private final PathResult lowerRate;
    private final PathResult nominal;
    private final PathResult upperRate;

    private Result(int sourceSegmentIndex, double durationHours, double waterInventoryKg,
        double elementalSulfurAllocationFraction, String allocationBasisIdentifier, PathResult lowerRate,
        PathResult nominal, PathResult upperRate) {
      this.sourceSegmentIndex = sourceSegmentIndex;
      this.durationHours = durationHours;
      this.waterInventoryKg = waterInventoryKg;
      this.elementalSulfurAllocationFraction = elementalSulfurAllocationFraction;
      this.allocationBasisIdentifier = allocationBasisIdentifier;
      this.lowerRate = lowerRate;
      this.nominal = nominal;
      this.upperRate = upperRate;
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

    /** @return caller-supplied elemental-sulfur allocation fraction [fraction]. */
    public double getElementalSulfurAllocationFraction() {
      return elementalSulfurAllocationFraction;
    }

    /**
     * Return the caller's allocation-basis identifier.
     *
     * <p>
     * Presence of this identifier records provenance but does not establish that the allocation is scientifically
     * qualified.
     * </p>
     *
     * @return allocation-basis identifier
     */
    public String getAllocationBasisIdentifier() {
      return allocationBasisIdentifier;
    }

    /** @return lower-rate fit-path allocation receipt. */
    public PathResult getLowerRate() {
      return lowerRate;
    }

    /** @return nominal fit-path allocation receipt. */
    public PathResult getNominal() {
      return nominal;
    }

    /** @return upper-rate fit-path allocation receipt. */
    public PathResult getUpperRate() {
      return upperRate;
    }
  }

  /** Immutable source, allocated, remainder, and closure evidence for one fit path. */
  public static final class PathResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double sourceSulfurEquivalentMassRateKgPerHour;
    private final double allocatedElementalSulfurMassRateKgPerHour;
    private final double unallocatedSulfurEquivalentMassRateKgPerHour;
    private final double rateClosureResidualKgPerHour;
    private final double sourceSulfurEquivalentMassKg;
    private final double allocatedElementalSulfurMassKg;
    private final double unallocatedSulfurEquivalentMassKg;
    private final double massClosureResidualKg;

    private PathResult(double sourceSulfurEquivalentMassRateKgPerHour, double allocatedElementalSulfurMassRateKgPerHour,
        double unallocatedSulfurEquivalentMassRateKgPerHour, double rateClosureResidualKgPerHour,
        double sourceSulfurEquivalentMassKg, double allocatedElementalSulfurMassKg,
        double unallocatedSulfurEquivalentMassKg, double massClosureResidualKg) {
      this.sourceSulfurEquivalentMassRateKgPerHour = sourceSulfurEquivalentMassRateKgPerHour;
      this.allocatedElementalSulfurMassRateKgPerHour = allocatedElementalSulfurMassRateKgPerHour;
      this.unallocatedSulfurEquivalentMassRateKgPerHour = unallocatedSulfurEquivalentMassRateKgPerHour;
      this.rateClosureResidualKgPerHour = rateClosureResidualKgPerHour;
      this.sourceSulfurEquivalentMassKg = sourceSulfurEquivalentMassKg;
      this.allocatedElementalSulfurMassKg = allocatedElementalSulfurMassKg;
      this.unallocatedSulfurEquivalentMassKg = unallocatedSulfurEquivalentMassKg;
      this.massClosureResidualKg = massClosureResidualKg;
    }

    /** @return source sulfur-equivalent mean loss rate [kg S-equivalent/h]. */
    public double getSourceSulfurEquivalentMassRateKgPerHour() {
      return sourceSulfurEquivalentMassRateKgPerHour;
    }

    /** @return caller-allocated elemental-sulfur mean rate [kg S/h]. */
    public double getAllocatedElementalSulfurMassRateKgPerHour() {
      return allocatedElementalSulfurMassRateKgPerHour;
    }

    /** @return unallocated sulfur-equivalent mean rate [kg S-equivalent/h]. */
    public double getUnallocatedSulfurEquivalentMassRateKgPerHour() {
      return unallocatedSulfurEquivalentMassRateKgPerHour;
    }

    /** @return rate-basis allocation closure residual [kg/h]. */
    public double getRateClosureResidualKgPerHour() {
      return rateClosureResidualKgPerHour;
    }

    /** @return source reacted sulfur-equivalent mass [kg S-equivalent]. */
    public double getSourceSulfurEquivalentMassKg() {
      return sourceSulfurEquivalentMassKg;
    }

    /** @return caller-allocated elemental-sulfur mass [kg S]. */
    public double getAllocatedElementalSulfurMassKg() {
      return allocatedElementalSulfurMassKg;
    }

    /** @return unallocated sulfur-equivalent mass [kg S-equivalent]. */
    public double getUnallocatedSulfurEquivalentMassKg() {
      return unallocatedSulfurEquivalentMassKg;
    }

    /** @return mass-basis allocation closure residual [kg]. */
    public double getMassClosureResidualKg() {
      return massClosureResidualKg;
    }
  }
}
