package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable coupled sulfur, nitrogen, hydrogen, hydrogen-sulfide, and ammonia balance receipt.
 *
 * <p>
 * The receipt solves sulfur and nitrogen targets on one calculated liquid-product mass. It is
 * limited to stoichiometric bookkeeping and does not predict reaction pathways, kinetics, catalyst
 * performance, operating conditions, heat duty, hydrocarbon yield, or compliance.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingSulfurNitrogenBalance implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final double feedMassKg;
  private final double feedSulfurMassFraction;
  private final double feedNitrogenMassFraction;
  private final double targetProductSulfurMassFraction;
  private final double targetProductNitrogenMassFraction;
  private final double hydrogenMolesPerSulfurMole;
  private final double hydrogenMolesPerNitrogenMole;
  private final double initialSulfurMassKg;
  private final double initialNitrogenMassKg;
  private final double sulfurRemovedMassKg;
  private final double nitrogenRemovedMassKg;
  private final double remainingSulfurMassKg;
  private final double remainingNitrogenMassKg;
  private final double sulfurHydrogenConsumedMassKg;
  private final double nitrogenHydrogenConsumedMassKg;
  private final double totalHydrogenConsumedMassKg;
  private final double hydrogenSulfideProducedMassKg;
  private final double ammoniaProducedMassKg;
  private final double hydrogenRetainedInLiquidMassKg;
  private final double productMassKg;
  private final double achievedProductSulfurMassFraction;
  private final double achievedProductNitrogenMassFraction;
  private final double totalMassBalanceResidualKg;
  private final double sulfurBalanceResidualKg;
  private final double nitrogenBalanceResidualKg;

  private RefineryHydrotreatingSulfurNitrogenBalance(double feedMassKg, double feedSulfurMassFraction,
      double feedNitrogenMassFraction, double targetProductSulfurMassFraction,
      double targetProductNitrogenMassFraction, double hydrogenMolesPerSulfurMole,
      double hydrogenMolesPerNitrogenMole, double initialSulfurMassKg, double initialNitrogenMassKg,
      double sulfurRemovedMassKg, double nitrogenRemovedMassKg, double remainingSulfurMassKg,
      double remainingNitrogenMassKg, double sulfurHydrogenConsumedMassKg,
      double nitrogenHydrogenConsumedMassKg, double totalHydrogenConsumedMassKg,
      double hydrogenSulfideProducedMassKg, double ammoniaProducedMassKg,
      double hydrogenRetainedInLiquidMassKg, double productMassKg,
      double achievedProductSulfurMassFraction, double achievedProductNitrogenMassFraction,
      double totalMassBalanceResidualKg, double sulfurBalanceResidualKg,
      double nitrogenBalanceResidualKg) {
    this.feedMassKg = feedMassKg;
    this.feedSulfurMassFraction = feedSulfurMassFraction;
    this.feedNitrogenMassFraction = feedNitrogenMassFraction;
    this.targetProductSulfurMassFraction = targetProductSulfurMassFraction;
    this.targetProductNitrogenMassFraction = targetProductNitrogenMassFraction;
    this.hydrogenMolesPerSulfurMole = hydrogenMolesPerSulfurMole;
    this.hydrogenMolesPerNitrogenMole = hydrogenMolesPerNitrogenMole;
    this.initialSulfurMassKg = initialSulfurMassKg;
    this.initialNitrogenMassKg = initialNitrogenMassKg;
    this.sulfurRemovedMassKg = sulfurRemovedMassKg;
    this.nitrogenRemovedMassKg = nitrogenRemovedMassKg;
    this.remainingSulfurMassKg = remainingSulfurMassKg;
    this.remainingNitrogenMassKg = remainingNitrogenMassKg;
    this.sulfurHydrogenConsumedMassKg = sulfurHydrogenConsumedMassKg;
    this.nitrogenHydrogenConsumedMassKg = nitrogenHydrogenConsumedMassKg;
    this.totalHydrogenConsumedMassKg = totalHydrogenConsumedMassKg;
    this.hydrogenSulfideProducedMassKg = hydrogenSulfideProducedMassKg;
    this.ammoniaProducedMassKg = ammoniaProducedMassKg;
    this.hydrogenRetainedInLiquidMassKg = hydrogenRetainedInLiquidMassKg;
    this.productMassKg = productMassKg;
    this.achievedProductSulfurMassFraction = achievedProductSulfurMassFraction;
    this.achievedProductNitrogenMassFraction = achievedProductNitrogenMassFraction;
    this.totalMassBalanceResidualKg = totalMassBalanceResidualKg;
    this.sulfurBalanceResidualKg = sulfurBalanceResidualKg;
    this.nitrogenBalanceResidualKg = nitrogenBalanceResidualKg;
  }

  /**
   * Calculate a coupled sulfur and nitrogen hydrotreating material-balance receipt.
   *
   * @param feedMassKg feed mass in kilograms
   * @param feedSulfurMassFraction sulfur mass fraction in the feed
   * @param feedNitrogenMassFraction total-nitrogen mass fraction in the feed
   * @param targetProductSulfurMassFraction requested sulfur mass fraction on product mass
   * @param targetProductNitrogenMassFraction requested nitrogen mass fraction on product mass
   * @param hydrogenMolesPerSulfurMole moles of H2 consumed per mole of sulfur removed; at least one
   * @param hydrogenMolesPerNitrogenMole moles of H2 consumed per mole of nitrogen removed; at least
   *        1.5
   * @return immutable coupled balance receipt
   */
  public static RefineryHydrotreatingSulfurNitrogenBalance calculate(double feedMassKg,
      double feedSulfurMassFraction, double feedNitrogenMassFraction,
      double targetProductSulfurMassFraction, double targetProductNitrogenMassFraction,
      double hydrogenMolesPerSulfurMole, double hydrogenMolesPerNitrogenMole) {
    requireFinitePositive("feedMassKg", feedMassKg);
    requireFraction("feedSulfurMassFraction", feedSulfurMassFraction);
    requireFraction("feedNitrogenMassFraction", feedNitrogenMassFraction);
    requireFraction("targetProductSulfurMassFraction", targetProductSulfurMassFraction);
    requireFraction("targetProductNitrogenMassFraction", targetProductNitrogenMassFraction);
    if (feedSulfurMassFraction + feedNitrogenMassFraction >= 1.0) {
      throw new IllegalArgumentException("combined feed sulfur and nitrogen fractions must be below one");
    }
    if (targetProductSulfurMassFraction + targetProductNitrogenMassFraction >= 1.0) {
      throw new IllegalArgumentException("combined product sulfur and nitrogen fractions must be below one");
    }
    if (targetProductSulfurMassFraction > feedSulfurMassFraction) {
      throw new IllegalArgumentException("targetProductSulfurMassFraction must not exceed feedSulfurMassFraction");
    }
    if (targetProductNitrogenMassFraction > feedNitrogenMassFraction) {
      throw new IllegalArgumentException("targetProductNitrogenMassFraction must not exceed feedNitrogenMassFraction");
    }
    if (!Double.isFinite(hydrogenMolesPerSulfurMole) || hydrogenMolesPerSulfurMole < 1.0) {
      throw new IllegalArgumentException("hydrogenMolesPerSulfurMole must be finite and at least one");
    }
    if (!Double.isFinite(hydrogenMolesPerNitrogenMole) || hydrogenMolesPerNitrogenMole < 1.5) {
      throw new IllegalArgumentException("hydrogenMolesPerNitrogenMole must be finite and at least 1.5");
    }

    double sulfurMassChangePerRemovedMass =
        (hydrogenMolesPerSulfurMole * RefineryHydrotreatingSulfurBalance.HYDROGEN_MOLAR_MASS_KG_PER_MOL
            - RefineryHydrotreatingSulfurBalance.HYDROGEN_SULFIDE_MOLAR_MASS_KG_PER_MOL)
            / RefineryHydrotreatingSulfurBalance.SULFUR_MOLAR_MASS_KG_PER_MOL;
    double nitrogenMassChangePerRemovedMass =
        (hydrogenMolesPerNitrogenMole * RefineryHydrotreatingNitrogenBalance.HYDROGEN_MOLAR_MASS_KG_PER_MOL
            - RefineryHydrotreatingNitrogenBalance.AMMONIA_MOLAR_MASS_KG_PER_MOL)
            / RefineryHydrotreatingNitrogenBalance.NITROGEN_MOLAR_MASS_KG_PER_MOL;
    double denominator = 1.0 + sulfurMassChangePerRemovedMass * targetProductSulfurMassFraction
        + nitrogenMassChangePerRemovedMass * targetProductNitrogenMassFraction;
    double numerator = feedMassKg * (1.0 + sulfurMassChangePerRemovedMass * feedSulfurMassFraction
        + nitrogenMassChangePerRemovedMass * feedNitrogenMassFraction);
    if (!Double.isFinite(denominator) || denominator <= 0.0 || !Double.isFinite(numerator)
        || numerator <= 0.0) {
      throw new IllegalArgumentException("inputs do not define a positive coupled material balance");
    }

    double productMassKg = numerator / denominator;
    double initialSulfurMassKg = feedMassKg * feedSulfurMassFraction;
    double initialNitrogenMassKg = feedMassKg * feedNitrogenMassFraction;
    double sulfurRemovedMassKg = initialSulfurMassKg - targetProductSulfurMassFraction * productMassKg;
    double nitrogenRemovedMassKg = initialNitrogenMassKg - targetProductNitrogenMassFraction * productMassKg;
    double sulfurMolesRemoved =
        sulfurRemovedMassKg / RefineryHydrotreatingSulfurBalance.SULFUR_MOLAR_MASS_KG_PER_MOL;
    double nitrogenMolesRemoved =
        nitrogenRemovedMassKg / RefineryHydrotreatingNitrogenBalance.NITROGEN_MOLAR_MASS_KG_PER_MOL;
    double sulfurHydrogenConsumedMassKg = sulfurMolesRemoved * hydrogenMolesPerSulfurMole
        * RefineryHydrotreatingSulfurBalance.HYDROGEN_MOLAR_MASS_KG_PER_MOL;
    double nitrogenHydrogenConsumedMassKg = nitrogenMolesRemoved * hydrogenMolesPerNitrogenMole
        * RefineryHydrotreatingNitrogenBalance.HYDROGEN_MOLAR_MASS_KG_PER_MOL;
    double totalHydrogenConsumedMassKg =
        sulfurHydrogenConsumedMassKg + nitrogenHydrogenConsumedMassKg;
    double hydrogenSulfideProducedMassKg = sulfurMolesRemoved
        * RefineryHydrotreatingSulfurBalance.HYDROGEN_SULFIDE_MOLAR_MASS_KG_PER_MOL;
    double ammoniaProducedMassKg =
        nitrogenMolesRemoved * RefineryHydrotreatingNitrogenBalance.AMMONIA_MOLAR_MASS_KG_PER_MOL;
    double hydrogenRetainedInLiquidMassKg =
        sulfurMolesRemoved * (hydrogenMolesPerSulfurMole - 1.0)
            * RefineryHydrotreatingSulfurBalance.HYDROGEN_MOLAR_MASS_KG_PER_MOL
            + nitrogenMolesRemoved * (hydrogenMolesPerNitrogenMole - 1.5)
                * RefineryHydrotreatingNitrogenBalance.HYDROGEN_MOLAR_MASS_KG_PER_MOL;
    double remainingSulfurMassKg = initialSulfurMassKg - sulfurRemovedMassKg;
    double remainingNitrogenMassKg = initialNitrogenMassKg - nitrogenRemovedMassKg;
    double achievedProductSulfurMassFraction = remainingSulfurMassKg / productMassKg;
    double achievedProductNitrogenMassFraction = remainingNitrogenMassKg / productMassKg;
    double totalMassBalanceResidualKg = feedMassKg + totalHydrogenConsumedMassKg - productMassKg
        - hydrogenSulfideProducedMassKg - ammoniaProducedMassKg;
    double sulfurBalanceResidualKg =
        initialSulfurMassKg - remainingSulfurMassKg - sulfurRemovedMassKg;
    double nitrogenBalanceResidualKg =
        initialNitrogenMassKg - remainingNitrogenMassKg - nitrogenRemovedMassKg;

    double toleranceKg = 1.0e-12 * Math.max(1.0, feedMassKg + totalHydrogenConsumedMassKg);
    if (!Double.isFinite(productMassKg) || productMassKg <= 0.0 || sulfurRemovedMassKg < -toleranceKg
        || sulfurRemovedMassKg > initialSulfurMassKg + toleranceKg
        || nitrogenRemovedMassKg < -toleranceKg
        || nitrogenRemovedMassKg > initialNitrogenMassKg + toleranceKg
        || Math.abs(totalMassBalanceResidualKg) > toleranceKg
        || Math.abs(sulfurBalanceResidualKg) > toleranceKg
        || Math.abs(nitrogenBalanceResidualKg) > toleranceKg) {
      throw new IllegalArgumentException("inputs do not define a closed coupled material balance");
    }

    return new RefineryHydrotreatingSulfurNitrogenBalance(feedMassKg, feedSulfurMassFraction,
        feedNitrogenMassFraction, targetProductSulfurMassFraction, targetProductNitrogenMassFraction,
        hydrogenMolesPerSulfurMole, hydrogenMolesPerNitrogenMole, initialSulfurMassKg,
        initialNitrogenMassKg, sulfurRemovedMassKg, nitrogenRemovedMassKg, remainingSulfurMassKg,
        remainingNitrogenMassKg, sulfurHydrogenConsumedMassKg, nitrogenHydrogenConsumedMassKg,
        totalHydrogenConsumedMassKg, hydrogenSulfideProducedMassKg, ammoniaProducedMassKg,
        hydrogenRetainedInLiquidMassKg, productMassKg, achievedProductSulfurMassFraction,
        achievedProductNitrogenMassFraction, totalMassBalanceResidualKg, sulfurBalanceResidualKg,
        nitrogenBalanceResidualKg);
  }

  /**
   * Calculate a coupled receipt using bulk sulfur and nitrogen reconstructed by an oil assay.
   *
   * @param feedMassKg feed mass in kilograms
   * @param assay oil assay providing bulk sulfur and nitrogen mass fractions
   * @param targetProductSulfurMassFraction requested sulfur mass fraction on product mass
   * @param targetProductNitrogenMassFraction requested nitrogen mass fraction on product mass
   * @param hydrogenMolesPerSulfurMole moles of H2 consumed per mole of sulfur removed
   * @param hydrogenMolesPerNitrogenMole moles of H2 consumed per mole of nitrogen removed
   * @return immutable coupled balance receipt
   */
  public static RefineryHydrotreatingSulfurNitrogenBalance calculateForAssay(double feedMassKg,
      OilAssayCharacterisation assay, double targetProductSulfurMassFraction,
      double targetProductNitrogenMassFraction, double hydrogenMolesPerSulfurMole,
      double hydrogenMolesPerNitrogenMole) {
    Objects.requireNonNull(assay, "assay");
    return calculate(feedMassKg, assay.getBulkSulfurMassFraction(),
        assay.getBulkNitrogenMassFraction(), targetProductSulfurMassFraction,
        targetProductNitrogenMassFraction, hydrogenMolesPerSulfurMole,
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

  /** @return feed sulfur mass fraction */
  public double getFeedSulfurMassFraction() {
    return feedSulfurMassFraction;
  }

  /** @return feed total-nitrogen mass fraction */
  public double getFeedNitrogenMassFraction() {
    return feedNitrogenMassFraction;
  }

  /** @return target product sulfur mass fraction */
  public double getTargetProductSulfurMassFraction() {
    return targetProductSulfurMassFraction;
  }

  /** @return target product total-nitrogen mass fraction */
  public double getTargetProductNitrogenMassFraction() {
    return targetProductNitrogenMassFraction;
  }

  /** @return explicit moles of H2 consumed per mole of sulfur removed */
  public double getHydrogenMolesPerSulfurMole() {
    return hydrogenMolesPerSulfurMole;
  }

  /** @return explicit moles of H2 consumed per mole of nitrogen removed */
  public double getHydrogenMolesPerNitrogenMole() {
    return hydrogenMolesPerNitrogenMole;
  }

  /** @return initial sulfur mass in kilograms */
  public double getInitialSulfurMassKg() {
    return initialSulfurMassKg;
  }

  /** @return initial nitrogen mass in kilograms */
  public double getInitialNitrogenMassKg() {
    return initialNitrogenMassKg;
  }

  /** @return sulfur removed in kilograms */
  public double getSulfurRemovedMassKg() {
    return sulfurRemovedMassKg;
  }

  /** @return nitrogen removed in kilograms */
  public double getNitrogenRemovedMassKg() {
    return nitrogenRemovedMassKg;
  }

  /** @return sulfur remaining in the liquid product in kilograms */
  public double getRemainingSulfurMassKg() {
    return remainingSulfurMassKg;
  }

  /** @return nitrogen remaining in the liquid product in kilograms */
  public double getRemainingNitrogenMassKg() {
    return remainingNitrogenMassKg;
  }

  /** @return H2 consumed by sulfur removal in kilograms */
  public double getSulfurHydrogenConsumedMassKg() {
    return sulfurHydrogenConsumedMassKg;
  }

  /** @return H2 consumed by nitrogen removal in kilograms */
  public double getNitrogenHydrogenConsumedMassKg() {
    return nitrogenHydrogenConsumedMassKg;
  }

  /** @return total H2 consumed in kilograms */
  public double getTotalHydrogenConsumedMassKg() {
    return totalHydrogenConsumedMassKg;
  }

  /** @return H2S produced in kilograms */
  public double getHydrogenSulfideProducedMassKg() {
    return hydrogenSulfideProducedMassKg;
  }

  /** @return NH3 produced in kilograms */
  public double getAmmoniaProducedMassKg() {
    return ammoniaProducedMassKg;
  }

  /** @return total hydrogen retained in the liquid bookkeeping balance in kilograms */
  public double getHydrogenRetainedInLiquidMassKg() {
    return hydrogenRetainedInLiquidMassKg;
  }

  /** @return calculated liquid product mass in kilograms */
  public double getProductMassKg() {
    return productMassKg;
  }

  /** @return achieved product sulfur mass fraction */
  public double getAchievedProductSulfurMassFraction() {
    return achievedProductSulfurMassFraction;
  }

  /** @return achieved product total-nitrogen mass fraction */
  public double getAchievedProductNitrogenMassFraction() {
    return achievedProductNitrogenMassFraction;
  }

  /** @return total material-balance residual in kilograms */
  public double getTotalMassBalanceResidualKg() {
    return totalMassBalanceResidualKg;
  }

  /** @return sulfur material-balance residual in kilograms */
  public double getSulfurBalanceResidualKg() {
    return sulfurBalanceResidualKg;
  }

  /** @return nitrogen material-balance residual in kilograms */
  public double getNitrogenBalanceResidualKg() {
    return nitrogenBalanceResidualKg;
  }
}
