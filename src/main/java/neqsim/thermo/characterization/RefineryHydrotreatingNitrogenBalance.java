package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable nitrogen, hydrogen, and ammonia material-balance receipt for a hydrotreating screen.
 *
 * <p>
 * The calculation is limited to stoichiometric accounting with an explicit hydrogen-use assumption. It does not
 * identify nitrogen species or predict reaction pathways, kinetics, catalyst performance, operating conditions, heat
 * duty, hydrocarbon yield, or compliance.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingNitrogenBalance implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Molecular mass of hydrogen in kilograms per mole. */
  public static final double HYDROGEN_MOLAR_MASS_KG_PER_MOL = 0.00201588;

  /** Molecular mass of ammonia in kilograms per mole from the NIST Chemistry WebBook. */
  public static final double AMMONIA_MOLAR_MASS_KG_PER_MOL = 0.0170305;

  /** Molecular mass of nitrogen derived consistently from ammonia and hydrogen. */
  public static final double NITROGEN_MOLAR_MASS_KG_PER_MOL = AMMONIA_MOLAR_MASS_KG_PER_MOL
      - 1.5 * HYDROGEN_MOLAR_MASS_KG_PER_MOL;

  private final double feedMassKg;
  private final double feedNitrogenMassFraction;
  private final double targetProductNitrogenMassFraction;
  private final double hydrogenMolesPerNitrogenMole;
  private final double initialNitrogenMassKg;
  private final double nitrogenRemovedMassKg;
  private final double remainingNitrogenMassKg;
  private final double hydrogenConsumedMassKg;
  private final double ammoniaProducedMassKg;
  private final double hydrogenRetainedInLiquidMassKg;
  private final double productMassKg;
  private final double achievedProductNitrogenMassFraction;
  private final double totalMassBalanceResidualKg;
  private final double nitrogenBalanceResidualKg;

  private RefineryHydrotreatingNitrogenBalance(double feedMassKg, double feedNitrogenMassFraction,
      double targetProductNitrogenMassFraction, double hydrogenMolesPerNitrogenMole, double initialNitrogenMassKg,
      double nitrogenRemovedMassKg, double remainingNitrogenMassKg, double hydrogenConsumedMassKg,
      double ammoniaProducedMassKg, double hydrogenRetainedInLiquidMassKg, double productMassKg,
      double achievedProductNitrogenMassFraction, double totalMassBalanceResidualKg, double nitrogenBalanceResidualKg) {
    this.feedMassKg = feedMassKg;
    this.feedNitrogenMassFraction = feedNitrogenMassFraction;
    this.targetProductNitrogenMassFraction = targetProductNitrogenMassFraction;
    this.hydrogenMolesPerNitrogenMole = hydrogenMolesPerNitrogenMole;
    this.initialNitrogenMassKg = initialNitrogenMassKg;
    this.nitrogenRemovedMassKg = nitrogenRemovedMassKg;
    this.remainingNitrogenMassKg = remainingNitrogenMassKg;
    this.hydrogenConsumedMassKg = hydrogenConsumedMassKg;
    this.ammoniaProducedMassKg = ammoniaProducedMassKg;
    this.hydrogenRetainedInLiquidMassKg = hydrogenRetainedInLiquidMassKg;
    this.productMassKg = productMassKg;
    this.achievedProductNitrogenMassFraction = achievedProductNitrogenMassFraction;
    this.totalMassBalanceResidualKg = totalMassBalanceResidualKg;
    this.nitrogenBalanceResidualKg = nitrogenBalanceResidualKg;
  }

  /**
   * Calculate a nitrogen, hydrogen, and ammonia material-balance receipt.
   *
   * @param feedMassKg feed mass in kilograms
   * @param feedNitrogenMassFraction total-nitrogen mass fraction in the feed
   * @param targetProductNitrogenMassFraction requested total-nitrogen mass fraction on the calculated liquid product
   * mass
   * @param hydrogenMolesPerNitrogenMole explicit hydrogen consumption assumption in moles of H2 per mole of nitrogen
   * removed; must be at least 1.5
   * @return immutable balance receipt
   */
  public static RefineryHydrotreatingNitrogenBalance calculate(double feedMassKg, double feedNitrogenMassFraction,
      double targetProductNitrogenMassFraction, double hydrogenMolesPerNitrogenMole) {
    requireFinitePositive("feedMassKg", feedMassKg);
    requireFraction("feedNitrogenMassFraction", feedNitrogenMassFraction);
    requireFraction("targetProductNitrogenMassFraction", targetProductNitrogenMassFraction);
    if (targetProductNitrogenMassFraction > feedNitrogenMassFraction) {
      throw new IllegalArgumentException("targetProductNitrogenMassFraction must not exceed feedNitrogenMassFraction");
    }
    if (!Double.isFinite(hydrogenMolesPerNitrogenMole) || hydrogenMolesPerNitrogenMole < 1.5) {
      throw new IllegalArgumentException("hydrogenMolesPerNitrogenMole must be finite and at least 1.5");
    }

    double initialNitrogenMassKg = feedMassKg * feedNitrogenMassFraction;
    double liquidMassChangePerNitrogenMass = (hydrogenMolesPerNitrogenMole * HYDROGEN_MOLAR_MASS_KG_PER_MOL
        - AMMONIA_MOLAR_MASS_KG_PER_MOL) / NITROGEN_MOLAR_MASS_KG_PER_MOL;
    double denominator = 1.0 + targetProductNitrogenMassFraction * liquidMassChangePerNitrogenMass;
    if (!Double.isFinite(denominator) || denominator <= 0.0) {
      throw new IllegalArgumentException("inputs do not define a positive material balance");
    }

    double nitrogenRemovedMassKg = (initialNitrogenMassKg - targetProductNitrogenMassFraction * feedMassKg)
        / denominator;
    double nitrogenMolesRemoved = nitrogenRemovedMassKg / NITROGEN_MOLAR_MASS_KG_PER_MOL;
    double hydrogenConsumedMassKg = nitrogenMolesRemoved * hydrogenMolesPerNitrogenMole
        * HYDROGEN_MOLAR_MASS_KG_PER_MOL;
    double ammoniaProducedMassKg = nitrogenMolesRemoved * AMMONIA_MOLAR_MASS_KG_PER_MOL;
    double hydrogenRetainedInLiquidMassKg = nitrogenMolesRemoved * (hydrogenMolesPerNitrogenMole - 1.5)
        * HYDROGEN_MOLAR_MASS_KG_PER_MOL;
    double remainingNitrogenMassKg = initialNitrogenMassKg - nitrogenRemovedMassKg;
    double productMassKg = feedMassKg + hydrogenConsumedMassKg - ammoniaProducedMassKg;
    double achievedProductNitrogenMassFraction = remainingNitrogenMassKg / productMassKg;
    double totalMassBalanceResidualKg = feedMassKg + hydrogenConsumedMassKg - productMassKg - ammoniaProducedMassKg;
    double nitrogenBalanceResidualKg = initialNitrogenMassKg - remainingNitrogenMassKg - nitrogenRemovedMassKg;

    double toleranceKg = 1.0e-12 * Math.max(1.0, feedMassKg);
    if (!Double.isFinite(productMassKg) || productMassKg <= 0.0 || nitrogenRemovedMassKg < -toleranceKg
        || nitrogenRemovedMassKg > initialNitrogenMassKg + toleranceKg
        || Math.abs(totalMassBalanceResidualKg) > toleranceKg || Math.abs(nitrogenBalanceResidualKg) > toleranceKg) {
      throw new IllegalArgumentException("inputs do not define a closed material balance");
    }

    return new RefineryHydrotreatingNitrogenBalance(feedMassKg, feedNitrogenMassFraction,
        targetProductNitrogenMassFraction, hydrogenMolesPerNitrogenMole, initialNitrogenMassKg, nitrogenRemovedMassKg,
        remainingNitrogenMassKg, hydrogenConsumedMassKg, ammoniaProducedMassKg, hydrogenRetainedInLiquidMassKg,
        productMassKg, achievedProductNitrogenMassFraction, totalMassBalanceResidualKg, nitrogenBalanceResidualKg);
  }

  /**
   * Calculate a receipt using bulk nitrogen reconstructed by an oil assay.
   *
   * <p>
   * The assay is read but is not mutated.
   *
   * @param feedMassKg feed mass in kilograms
   * @param assay oil assay providing the bulk nitrogen mass fraction
   * @param targetProductNitrogenMassFraction requested nitrogen mass fraction on the calculated liquid product mass
   * @param hydrogenMolesPerNitrogenMole explicit hydrogen consumption assumption in moles of H2 per mole of nitrogen
   * removed
   * @return immutable balance receipt
   */
  public static RefineryHydrotreatingNitrogenBalance calculateForAssay(double feedMassKg,
      OilAssayCharacterisation assay, double targetProductNitrogenMassFraction, double hydrogenMolesPerNitrogenMole) {
    Objects.requireNonNull(assay, "assay");
    return calculate(feedMassKg, assay.getBulkNitrogenMassFraction(), targetProductNitrogenMassFraction,
        hydrogenMolesPerNitrogenMole);
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

  /** @return feed mass in kilograms */
  public double getFeedMassKg() {
    return feedMassKg;
  }

  /** @return feed total-nitrogen mass fraction */
  public double getFeedNitrogenMassFraction() {
    return feedNitrogenMassFraction;
  }

  /** @return target product total-nitrogen mass fraction */
  public double getTargetProductNitrogenMassFraction() {
    return targetProductNitrogenMassFraction;
  }

  /** @return explicit moles of H2 consumed per mole of nitrogen removed */
  public double getHydrogenMolesPerNitrogenMole() {
    return hydrogenMolesPerNitrogenMole;
  }

  /** @return initial nitrogen mass in kilograms */
  public double getInitialNitrogenMassKg() {
    return initialNitrogenMassKg;
  }

  /** @return nitrogen removed from the liquid product in kilograms */
  public double getNitrogenRemovedMassKg() {
    return nitrogenRemovedMassKg;
  }

  /** @return nitrogen remaining in the liquid product in kilograms */
  public double getRemainingNitrogenMassKg() {
    return remainingNitrogenMassKg;
  }

  /** @return hydrogen consumed in kilograms */
  public double getHydrogenConsumedMassKg() {
    return hydrogenConsumedMassKg;
  }

  /** @return ammonia produced in kilograms */
  public double getAmmoniaProducedMassKg() {
    return ammoniaProducedMassKg;
  }

  /** @return hydrogen retained in the liquid bookkeeping balance in kilograms */
  public double getHydrogenRetainedInLiquidMassKg() {
    return hydrogenRetainedInLiquidMassKg;
  }

  /** @return calculated liquid product mass in kilograms */
  public double getProductMassKg() {
    return productMassKg;
  }

  /** @return achieved total-nitrogen mass fraction on calculated product mass */
  public double getAchievedProductNitrogenMassFraction() {
    return achievedProductNitrogenMassFraction;
  }

  /** @return total material-balance residual in kilograms */
  public double getTotalMassBalanceResidualKg() {
    return totalMassBalanceResidualKg;
  }

  /** @return nitrogen material-balance residual in kilograms */
  public double getNitrogenBalanceResidualKg() {
    return nitrogenBalanceResidualKg;
  }
}
