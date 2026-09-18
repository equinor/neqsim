package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable sulfur and hydrogen material-balance receipt for a hydrotreating screening case.
 *
 * <p>The calculation is deliberately limited to stoichiometric accounting. It does not predict
 * reaction kinetics, catalyst performance, operating conditions, heat duty, recycle requirements,
 * liquid yield, or product compliance.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingSulfurBalance implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Molecular mass of hydrogen in kilograms per mole. */
  public static final double HYDROGEN_MOLAR_MASS_KG_PER_MOL = 0.00201588;

  /** Molecular mass of hydrogen sulfide in kilograms per mole. */
  public static final double HYDROGEN_SULFIDE_MOLAR_MASS_KG_PER_MOL = 0.034081;

  /** Molecular mass of sulfur derived consistently from hydrogen sulfide and hydrogen. */
  public static final double SULFUR_MOLAR_MASS_KG_PER_MOL =
      HYDROGEN_SULFIDE_MOLAR_MASS_KG_PER_MOL - HYDROGEN_MOLAR_MASS_KG_PER_MOL;

  private final double feedMassKg;
  private final double feedSulfurMassFraction;
  private final double targetProductSulfurMassFraction;
  private final double hydrogenMolesPerSulfurMole;
  private final double initialSulfurMassKg;
  private final double sulfurRemovedMassKg;
  private final double remainingSulfurMassKg;
  private final double hydrogenConsumedMassKg;
  private final double hydrogenSulfideProducedMassKg;
  private final double hydrogenRetainedInLiquidMassKg;
  private final double productMassKg;
  private final double achievedProductSulfurMassFraction;
  private final double totalMassBalanceResidualKg;
  private final double sulfurBalanceResidualKg;

  private RefineryHydrotreatingSulfurBalance(
      double feedMassKg,
      double feedSulfurMassFraction,
      double targetProductSulfurMassFraction,
      double hydrogenMolesPerSulfurMole,
      double initialSulfurMassKg,
      double sulfurRemovedMassKg,
      double remainingSulfurMassKg,
      double hydrogenConsumedMassKg,
      double hydrogenSulfideProducedMassKg,
      double hydrogenRetainedInLiquidMassKg,
      double productMassKg,
      double achievedProductSulfurMassFraction,
      double totalMassBalanceResidualKg,
      double sulfurBalanceResidualKg) {
    this.feedMassKg = feedMassKg;
    this.feedSulfurMassFraction = feedSulfurMassFraction;
    this.targetProductSulfurMassFraction = targetProductSulfurMassFraction;
    this.hydrogenMolesPerSulfurMole = hydrogenMolesPerSulfurMole;
    this.initialSulfurMassKg = initialSulfurMassKg;
    this.sulfurRemovedMassKg = sulfurRemovedMassKg;
    this.remainingSulfurMassKg = remainingSulfurMassKg;
    this.hydrogenConsumedMassKg = hydrogenConsumedMassKg;
    this.hydrogenSulfideProducedMassKg = hydrogenSulfideProducedMassKg;
    this.hydrogenRetainedInLiquidMassKg = hydrogenRetainedInLiquidMassKg;
    this.productMassKg = productMassKg;
    this.achievedProductSulfurMassFraction = achievedProductSulfurMassFraction;
    this.totalMassBalanceResidualKg = totalMassBalanceResidualKg;
    this.sulfurBalanceResidualKg = sulfurBalanceResidualKg;
  }

  /**
   * Calculate a sulfur and hydrogen material-balance receipt.
   *
   * @param feedMassKg feed mass in kilograms
   * @param feedSulfurMassFraction sulfur mass fraction in the feed
   * @param targetProductSulfurMassFraction requested sulfur mass fraction on the calculated liquid
   *        product mass
   * @param hydrogenMolesPerSulfurMole explicit hydrogen consumption assumption in moles of H2 per
   *        mole of sulfur removed; must be at least one
   * @return immutable balance receipt
   */
  public static RefineryHydrotreatingSulfurBalance calculate(
      double feedMassKg,
      double feedSulfurMassFraction,
      double targetProductSulfurMassFraction,
      double hydrogenMolesPerSulfurMole) {
    requireFinitePositive("feedMassKg", feedMassKg);
    requireFraction("feedSulfurMassFraction", feedSulfurMassFraction);
    requireFraction("targetProductSulfurMassFraction", targetProductSulfurMassFraction);
    if (targetProductSulfurMassFraction > feedSulfurMassFraction) {
      throw new IllegalArgumentException(
          "targetProductSulfurMassFraction must not exceed feedSulfurMassFraction");
    }
    if (!Double.isFinite(hydrogenMolesPerSulfurMole)
        || hydrogenMolesPerSulfurMole < 1.0) {
      throw new IllegalArgumentException(
          "hydrogenMolesPerSulfurMole must be finite and at least one");
    }

    double initialSulfurMassKg = feedMassKg * feedSulfurMassFraction;
    double liquidMassChangePerSulfurMass =
        (hydrogenMolesPerSulfurMole * HYDROGEN_MOLAR_MASS_KG_PER_MOL
                - HYDROGEN_SULFIDE_MOLAR_MASS_KG_PER_MOL)
            / SULFUR_MOLAR_MASS_KG_PER_MOL;
    double denominator =
        1.0 + targetProductSulfurMassFraction * liquidMassChangePerSulfurMass;
    if (!Double.isFinite(denominator) || denominator <= 0.0) {
      throw new IllegalArgumentException("inputs do not define a positive material balance");
    }

    double sulfurRemovedMassKg =
        (initialSulfurMassKg - targetProductSulfurMassFraction * feedMassKg) / denominator;
    double sulfurMolesRemoved = sulfurRemovedMassKg / SULFUR_MOLAR_MASS_KG_PER_MOL;
    double hydrogenConsumedMassKg =
        sulfurMolesRemoved
            * hydrogenMolesPerSulfurMole
            * HYDROGEN_MOLAR_MASS_KG_PER_MOL;
    double hydrogenSulfideProducedMassKg =
        sulfurMolesRemoved * HYDROGEN_SULFIDE_MOLAR_MASS_KG_PER_MOL;
    double hydrogenRetainedInLiquidMassKg =
        sulfurMolesRemoved
            * (hydrogenMolesPerSulfurMole - 1.0)
            * HYDROGEN_MOLAR_MASS_KG_PER_MOL;
    double remainingSulfurMassKg = initialSulfurMassKg - sulfurRemovedMassKg;
    double productMassKg =
        feedMassKg + hydrogenConsumedMassKg - hydrogenSulfideProducedMassKg;
    double achievedProductSulfurMassFraction = remainingSulfurMassKg / productMassKg;
    double totalMassBalanceResidualKg =
        feedMassKg
            + hydrogenConsumedMassKg
            - productMassKg
            - hydrogenSulfideProducedMassKg;
    double sulfurBalanceResidualKg =
        initialSulfurMassKg - remainingSulfurMassKg - sulfurRemovedMassKg;

    double toleranceKg = 1.0e-12 * Math.max(1.0, feedMassKg);
    if (!Double.isFinite(productMassKg)
        || productMassKg <= 0.0
        || sulfurRemovedMassKg < -toleranceKg
        || sulfurRemovedMassKg > initialSulfurMassKg + toleranceKg
        || Math.abs(totalMassBalanceResidualKg) > toleranceKg
        || Math.abs(sulfurBalanceResidualKg) > toleranceKg) {
      throw new IllegalArgumentException("inputs do not define a closed material balance");
    }

    return new RefineryHydrotreatingSulfurBalance(
        feedMassKg,
        feedSulfurMassFraction,
        targetProductSulfurMassFraction,
        hydrogenMolesPerSulfurMole,
        initialSulfurMassKg,
        sulfurRemovedMassKg,
        remainingSulfurMassKg,
        hydrogenConsumedMassKg,
        hydrogenSulfideProducedMassKg,
        hydrogenRetainedInLiquidMassKg,
        productMassKg,
        achievedProductSulfurMassFraction,
        totalMassBalanceResidualKg,
        sulfurBalanceResidualKg);
  }

  /**
   * Calculate a receipt using the bulk sulfur reconstructed by an oil assay.
   *
   * <p>The assay is read but is not mutated.
   *
   * @param feedMassKg feed mass in kilograms
   * @param assay oil assay providing the bulk sulfur mass fraction
   * @param targetProductSulfurMassFraction requested sulfur mass fraction on the calculated liquid
   *        product mass
   * @param hydrogenMolesPerSulfurMole explicit hydrogen consumption assumption in moles of H2 per
   *        mole of sulfur removed
   * @return immutable balance receipt
   */
  public static RefineryHydrotreatingSulfurBalance calculateForAssay(
      double feedMassKg,
      OilAssayCharacterisation assay,
      double targetProductSulfurMassFraction,
      double hydrogenMolesPerSulfurMole) {
    Objects.requireNonNull(assay, "assay");
    return calculate(
        feedMassKg,
        assay.getBulkSulfurMassFraction(),
        targetProductSulfurMassFraction,
        hydrogenMolesPerSulfurMole);
  }

  private static void requireFinitePositive(String name, double value) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  private static void requireFraction(String name, double value) {
    if (!Double.isFinite(value) || value < 0.0 || value >= 1.0) {
      throw new IllegalArgumentException(name + " must be finite and in [0, 1)");
    }
  }

  /**
   * Get the feed mass.
   *
   * @return feed mass in kilograms
   */
  public double getFeedMassKg() {
    return feedMassKg;
  }

  /**
   * Get the feed sulfur mass fraction.
   *
   * @return feed sulfur mass fraction
   */
  public double getFeedSulfurMassFraction() {
    return feedSulfurMassFraction;
  }

  /**
   * Get the requested product sulfur mass fraction.
   *
   * @return target product sulfur mass fraction
   */
  public double getTargetProductSulfurMassFraction() {
    return targetProductSulfurMassFraction;
  }

  /**
   * Get the explicit hydrogen consumption assumption.
   *
   * @return moles of H2 consumed per mole of sulfur removed
   */
  public double getHydrogenMolesPerSulfurMole() {
    return hydrogenMolesPerSulfurMole;
  }

  /**
   * Get the initial sulfur mass.
   *
   * @return initial sulfur mass in kilograms
   */
  public double getInitialSulfurMassKg() {
    return initialSulfurMassKg;
  }

  /**
   * Get the sulfur removed from the liquid product.
   *
   * @return removed sulfur mass in kilograms
   */
  public double getSulfurRemovedMassKg() {
    return sulfurRemovedMassKg;
  }

  /**
   * Get the sulfur remaining in the liquid product.
   *
   * @return remaining sulfur mass in kilograms
   */
  public double getRemainingSulfurMassKg() {
    return remainingSulfurMassKg;
  }

  /**
   * Get the hydrogen consumed.
   *
   * @return hydrogen consumed in kilograms
   */
  public double getHydrogenConsumedMassKg() {
    return hydrogenConsumedMassKg;
  }

  /**
   * Get the hydrogen sulfide produced.
   *
   * @return hydrogen sulfide produced in kilograms
   */
  public double getHydrogenSulfideProducedMassKg() {
    return hydrogenSulfideProducedMassKg;
  }

  /**
   * Get hydrogen retained in the liquid after sulfur removal.
   *
   * @return retained hydrogen mass in kilograms
   */
  public double getHydrogenRetainedInLiquidMassKg() {
    return hydrogenRetainedInLiquidMassKg;
  }

  /**
   * Get the calculated liquid product mass.
   *
   * @return product mass in kilograms
   */
  public double getProductMassKg() {
    return productMassKg;
  }

  /**
   * Get the achieved sulfur mass fraction on the calculated product mass.
   *
   * @return achieved product sulfur mass fraction
   */
  public double getAchievedProductSulfurMassFraction() {
    return achievedProductSulfurMassFraction;
  }

  /**
   * Get the total mass balance residual.
   *
   * @return total mass residual in kilograms
   */
  public double getTotalMassBalanceResidualKg() {
    return totalMassBalanceResidualKg;
  }

  /**
   * Get the sulfur balance residual.
   *
   * @return sulfur mass residual in kilograms
   */
  public double getSulfurBalanceResidualKg() {
    return sulfurBalanceResidualKg;
  }
}
