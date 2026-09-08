package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Immutable feasible mass-fraction envelope for a quality-constrained binary refinery blend.
 *
 * <p>
 * The envelope combines the existing ideal-additive-volume specific-gravity rule, linear
 * sulfur/nitrogen bookkeeping, and common-temperature Refutas kinematic-viscosity rule. It does
 * not mutate an assay or thermodynamic system and does not introduce an independent property
 * correlation.
 * </p>
 */
public final class RefineryBinaryBlendEnvelope implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double FRACTION_ROUNDOFF_TOLERANCE = 1.0e-14;

  private final double[] sourceSpecificGravities;
  private final double[] sourceSulfurMassFractions;
  private final double[] sourceNitrogenMassFractions;
  private final double[] sourceKinematicViscositiesCSt;
  private final double temperatureCelsius;
  private final double minimumFirstSourceMassFraction;
  private final double maximumFirstSourceMassFraction;

  private RefineryBinaryBlendEnvelope(double[] sourceSpecificGravities,
      double[] sourceSulfurMassFractions, double[] sourceNitrogenMassFractions,
      double[] sourceKinematicViscositiesCSt, double temperatureCelsius,
      double minimumApiGravity, double maximumApiGravity, double maximumSulfurMassFraction,
      double maximumNitrogenMassFraction, double minimumKinematicViscosityCSt,
      double maximumKinematicViscosityCSt) {
    requireBinaryArray(sourceSpecificGravities, "specific gravities");
    requireBinaryArray(sourceSulfurMassFractions, "sulfur mass fractions");
    requireBinaryArray(sourceNitrogenMassFractions, "nitrogen mass fractions");
    requireBinaryArray(sourceKinematicViscositiesCSt, "kinematic viscosities");
    requireFiniteOrderedBounds(minimumApiGravity, maximumApiGravity, "API-gravity");
    requireMassFraction(maximumSulfurMassFraction, "Maximum sulfur mass fraction");
    requireMassFraction(maximumNitrogenMassFraction, "Maximum nitrogen mass fraction");
    if (!Double.isFinite(temperatureCelsius)) {
      throw new IllegalArgumentException("Common viscosity temperature must be finite");
    }
    if (!Double.isFinite(minimumKinematicViscosityCSt)
        || !Double.isFinite(maximumKinematicViscosityCSt)
        || !(minimumKinematicViscosityCSt > 0.2)
        || maximumKinematicViscosityCSt < minimumKinematicViscosityCSt) {
      throw new IllegalArgumentException(
          "Kinematic-viscosity bounds must be finite, ordered, and greater than 0.2 cSt");
    }

    // Reuse the qualified property implementations as the authoritative input-domain checks.
    double[] equalMasses = {1.0, 1.0};
    RefineryAssayBlend.fromBulkProperties(equalMasses, sourceSpecificGravities,
        sourceSulfurMassFractions, sourceNitrogenMassFractions);
    RefineryViscosityBlend.fromMassBasis(equalMasses, sourceKinematicViscositiesCSt,
        temperatureCelsius);

    this.sourceSpecificGravities = Arrays.copyOf(sourceSpecificGravities, 2);
    this.sourceSulfurMassFractions = Arrays.copyOf(sourceSulfurMassFractions, 2);
    this.sourceNitrogenMassFractions = Arrays.copyOf(sourceNitrogenMassFractions, 2);
    this.sourceKinematicViscositiesCSt = Arrays.copyOf(sourceKinematicViscositiesCSt, 2);
    this.temperatureCelsius = temperatureCelsius;

    double[] interval = {0.0, 1.0};
    double firstApiGravity = calculateApiGravity(sourceSpecificGravities[0]);
    double secondApiGravity = calculateApiGravity(sourceSpecificGravities[1]);
    intersectLinearConstraint(interval, firstApiGravity, secondApiGravity, minimumApiGravity,
        maximumApiGravity, "API-gravity");
    intersectLinearConstraint(interval, sourceSulfurMassFractions[0],
        sourceSulfurMassFractions[1], 0.0, maximumSulfurMassFraction, "sulfur");
    intersectLinearConstraint(interval, sourceNitrogenMassFractions[0],
        sourceNitrogenMassFractions[1], 0.0, maximumNitrogenMassFraction, "nitrogen");

    double firstBlendNumber = RefineryViscosityBlend
        .calculateViscosityBlendingNumber(sourceKinematicViscositiesCSt[0]);
    double secondBlendNumber = RefineryViscosityBlend
        .calculateViscosityBlendingNumber(sourceKinematicViscositiesCSt[1]);
    double minimumBlendNumber = RefineryViscosityBlend
        .calculateViscosityBlendingNumber(minimumKinematicViscosityCSt);
    double maximumBlendNumber = RefineryViscosityBlend
        .calculateViscosityBlendingNumber(maximumKinematicViscosityCSt);
    intersectLinearConstraint(interval, firstBlendNumber, secondBlendNumber,
        minimumBlendNumber, maximumBlendNumber, "kinematic viscosity");

    minimumFirstSourceMassFraction = interval[0];
    maximumFirstSourceMassFraction = interval[1];
  }

  /**
   * Build the feasible binary mass-fraction interval for explicit product-quality limits.
   *
   * @param sourceSpecificGravities two source specific gravities
   * @param sourceSulfurMassFractions two total-sulfur mass fractions on a 0-1 basis
   * @param sourceNitrogenMassFractions two total-nitrogen mass fractions on a 0-1 basis
   * @param sourceKinematicViscositiesCSt two source viscosities in cSt at the common temperature
   * @param temperatureCelsius common source-viscosity temperature in degrees Celsius
   * @param minimumApiGravity inclusive minimum blend API gravity
   * @param maximumApiGravity inclusive maximum blend API gravity
   * @param maximumSulfurMassFraction inclusive maximum blend sulfur mass fraction
   * @param maximumNitrogenMassFraction inclusive maximum blend nitrogen mass fraction
   * @param minimumKinematicViscosityCSt inclusive minimum blend viscosity in cSt
   * @param maximumKinematicViscosityCSt inclusive maximum blend viscosity in cSt
   * @return immutable feasible binary envelope
   * @throws IllegalArgumentException for invalid sources, bounds, or an empty feasible interval
   */
  public static RefineryBinaryBlendEnvelope fromQualityConstraints(
      double[] sourceSpecificGravities, double[] sourceSulfurMassFractions,
      double[] sourceNitrogenMassFractions, double[] sourceKinematicViscositiesCSt,
      double temperatureCelsius, double minimumApiGravity, double maximumApiGravity,
      double maximumSulfurMassFraction, double maximumNitrogenMassFraction,
      double minimumKinematicViscosityCSt, double maximumKinematicViscosityCSt) {
    return new RefineryBinaryBlendEnvelope(sourceSpecificGravities,
        sourceSulfurMassFractions, sourceNitrogenMassFractions,
        sourceKinematicViscositiesCSt, temperatureCelsius, minimumApiGravity,
        maximumApiGravity, maximumSulfurMassFraction, maximumNitrogenMassFraction,
        minimumKinematicViscosityCSt, maximumKinematicViscosityCSt);
  }

  /** @return inclusive minimum first-source mass fraction */
  public double getMinimumFirstSourceMassFraction() {
    return minimumFirstSourceMassFraction;
  }

  /** @return inclusive maximum first-source mass fraction */
  public double getMaximumFirstSourceMassFraction() {
    return maximumFirstSourceMassFraction;
  }

  /** @return common viscosity temperature in degrees Celsius */
  public double getTemperatureCelsius() {
    return temperatureCelsius;
  }

  /** @return defensive copy of the two source specific gravities */
  public double[] getSourceSpecificGravities() {
    return Arrays.copyOf(sourceSpecificGravities, 2);
  }

  /** @return defensive copy of the two source sulfur mass fractions */
  public double[] getSourceSulfurMassFractions() {
    return Arrays.copyOf(sourceSulfurMassFractions, 2);
  }

  /** @return defensive copy of the two source nitrogen mass fractions */
  public double[] getSourceNitrogenMassFractions() {
    return Arrays.copyOf(sourceNitrogenMassFractions, 2);
  }

  /** @return defensive copy of the two source viscosities in cSt */
  public double[] getSourceKinematicViscositiesCSt() {
    return Arrays.copyOf(sourceKinematicViscositiesCSt, 2);
  }

  /**
   * Evaluate one caller-selected point inside the feasible interval.
   *
   * @param firstSourceMassFraction first-source mass fraction
   * @return immutable combined property plan without cost metadata
   */
  public Plan evaluateAtFirstSourceMassFraction(double firstSourceMassFraction) {
    return createPlan(firstSourceMassFraction, Double.NaN, Double.NaN, false);
  }

  /**
   * Select the unique minimum-cost endpoint of the feasible interval.
   *
   * <p>
   * Costs may use any common currency per common mass unit. Equal costs fail closed when the
   * feasible interval contains more than one point because the optimum is then non-unique.
   * </p>
   *
   * @param firstSourceCostPerMass non-negative first-source unit cost
   * @param secondSourceCostPerMass non-negative second-source unit cost
   * @return immutable minimum-cost combined property plan
   */
  public Plan planMinimumCost(double firstSourceCostPerMass,
      double secondSourceCostPerMass) {
    requireNonNegativeFinite(firstSourceCostPerMass, "First-source cost");
    requireNonNegativeFinite(secondSourceCostPerMass, "Second-source cost");
    double firstMassFraction;
    if (firstSourceCostPerMass < secondSourceCostPerMass) {
      firstMassFraction = maximumFirstSourceMassFraction;
    } else if (firstSourceCostPerMass > secondSourceCostPerMass) {
      firstMassFraction = minimumFirstSourceMassFraction;
    } else if (minimumFirstSourceMassFraction == maximumFirstSourceMassFraction) {
      firstMassFraction = minimumFirstSourceMassFraction;
    } else {
      throw new IllegalArgumentException(
          "Equal source costs do not define a unique minimum-cost blend inside this envelope");
    }
    return createPlan(firstMassFraction, firstSourceCostPerMass, secondSourceCostPerMass, true);
  }

  private Plan createPlan(double firstSourceMassFraction, double firstSourceCostPerMass,
      double secondSourceCostPerMass, boolean costAvailable) {
    if (!Double.isFinite(firstSourceMassFraction)
        || firstSourceMassFraction < minimumFirstSourceMassFraction
        || firstSourceMassFraction > maximumFirstSourceMassFraction) {
      throw new IllegalArgumentException("First-source mass fraction must lie inside the feasible interval");
    }
    double[] masses = {firstSourceMassFraction, 1.0 - firstSourceMassFraction};
    RefineryAssayBlend assayBlend = RefineryAssayBlend.fromBulkProperties(masses,
        sourceSpecificGravities, sourceSulfurMassFractions, sourceNitrogenMassFractions);
    RefineryViscosityBlend viscosityBlend = RefineryViscosityBlend.fromMassBasis(masses,
        sourceKinematicViscositiesCSt, temperatureCelsius);
    double unitCost = costAvailable
        ? firstSourceMassFraction * firstSourceCostPerMass
            + (1.0 - firstSourceMassFraction) * secondSourceCostPerMass
        : Double.NaN;
    return new Plan(firstSourceMassFraction, assayBlend, viscosityBlend, costAvailable, unitCost);
  }

  private static void intersectLinearConstraint(double[] interval, double firstValue,
      double secondValue, double minimumValue, double maximumValue, String propertyName) {
    double slope = firstValue - secondValue;
    if (slope == 0.0) {
      if (firstValue < minimumValue || firstValue > maximumValue) {
        throw new IllegalArgumentException("No feasible binary blend satisfies the " + propertyName + " constraint");
      }
      return;
    }

    double firstIntersection = (minimumValue - secondValue) / slope;
    double secondIntersection = (maximumValue - secondValue) / slope;
    double propertyMinimumFraction = Math.min(firstIntersection, secondIntersection);
    double propertyMaximumFraction = Math.max(firstIntersection, secondIntersection);
    double updatedMinimum = Math.max(interval[0], propertyMinimumFraction);
    double updatedMaximum = Math.min(interval[1], propertyMaximumFraction);
    if (updatedMinimum > updatedMaximum) {
      if (updatedMinimum - updatedMaximum <= FRACTION_ROUNDOFF_TOLERANCE) {
        double sharedBoundary = Math.max(0.0,
            Math.min(1.0, 0.5 * (updatedMinimum + updatedMaximum)));
        interval[0] = sharedBoundary;
        interval[1] = sharedBoundary;
        return;
      }
      throw new IllegalArgumentException(
          "No feasible binary blend satisfies the " + propertyName + " constraint");
    }
    interval[0] = Math.max(0.0, updatedMinimum);
    interval[1] = Math.min(1.0, updatedMaximum);
  }

  private static double calculateApiGravity(double specificGravity) {
    return 141.5 / specificGravity - 131.5;
  }

  private static void requireBinaryArray(double[] values, String propertyName) {
    if (values == null || values.length != 2) {
      throw new IllegalArgumentException("Exactly two source " + propertyName + " are required");
    }
  }

  private static void requireFiniteOrderedBounds(double minimumValue, double maximumValue,
      String propertyName) {
    if (!Double.isFinite(minimumValue) || !Double.isFinite(maximumValue)
        || maximumValue < minimumValue) {
      throw new IllegalArgumentException(propertyName + " bounds must be finite and ordered");
    }
  }

  private static void requireMassFraction(double value, String propertyName) {
    if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(propertyName + " must be finite and between 0 and 1");
    }
  }

  private static void requireNonNegativeFinite(double value, String propertyName) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(propertyName + " must be finite and non-negative");
    }
  }

  /** Immutable evaluated blend inside a {@link RefineryBinaryBlendEnvelope}. */
  public static final class Plan implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double firstSourceMassFraction;
    private final RefineryAssayBlend assayBlend;
    private final RefineryViscosityBlend viscosityBlend;
    private final boolean unitCostAvailable;
    private final double unitCostPerMass;

    private Plan(double firstSourceMassFraction, RefineryAssayBlend assayBlend,
        RefineryViscosityBlend viscosityBlend, boolean unitCostAvailable,
        double unitCostPerMass) {
      this.firstSourceMassFraction = firstSourceMassFraction;
      this.assayBlend = assayBlend;
      this.viscosityBlend = viscosityBlend;
      this.unitCostAvailable = unitCostAvailable;
      this.unitCostPerMass = unitCostPerMass;
    }

    /** @return first-source mass fraction */
    public double getFirstSourceMassFraction() {
      return firstSourceMassFraction;
    }

    /** @return second-source mass fraction */
    public double getSecondSourceMassFraction() {
      return 1.0 - firstSourceMassFraction;
    }

    /** @return immutable ideal-volume and linear-quality blend result */
    public RefineryAssayBlend getAssayBlend() {
      return assayBlend;
    }

    /** @return immutable Refutas viscosity blend result */
    public RefineryViscosityBlend getViscosityBlend() {
      return viscosityBlend;
    }

    /** @return whether minimum-cost metadata is available */
    public boolean hasUnitCost() {
      return unitCostAvailable;
    }

    /**
     * @return blend cost in the same currency per mass unit supplied to the planner
     * @throws IllegalStateException when this plan was evaluated without costs
     */
    public double getUnitCostPerMass() {
      if (!unitCostAvailable) {
        throw new IllegalStateException("Unit cost is unavailable for an uncosted blend evaluation");
      }
      return unitCostPerMass;
    }
  }
}
