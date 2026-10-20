package neqsim.thermo.characterization;

import java.io.Serializable;

/**
 * Immutable aggregate receipt for a multi-cut catalytic-reforming feed screen.
 *
 * <p>
 * Each cut delegates to {@link RefineryCatalyticReformingStoichiometricBalance}. The aggregate therefore preserves the
 * same-carbon-number naphthene dehydrogenation and paraffin dehydrocyclization boundary while exposing feed, reformate,
 * aromatic, hydrogen, and closure totals for a carbon-number distribution.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryCatalyticReformingFeedBalance implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final int[] carbonNumbers;
  private final RefineryCatalyticReformingStoichiometricBalance[] cutBalances;
  private final double naphtheneFeedFlowKmolPerHour;
  private final double paraffinFeedFlowKmolPerHour;
  private final double unconvertedNaphtheneFlowKmolPerHour;
  private final double unconvertedParaffinFlowKmolPerHour;
  private final double aromaticProductFlowKmolPerHour;
  private final double hydrogenProductFlowKmolPerHour;
  private final double feedMassFlowKgPerHour;
  private final double aromaticProductMassFlowKgPerHour;
  private final double hydrogenProductMassFlowKgPerHour;
  private final double reformateMassFlowKgPerHour;
  private final double totalProductMassFlowKgPerHour;
  private final double reformateMassYieldFraction;
  private final double aromaticMassFractionOfReformate;
  private final double aromaticProductKgPerTonneFeed;
  private final double hydrogenProductKgPerTonneFeed;
  private final double massBalanceResidualKgPerHour;
  private final double carbonAtomBalanceResidualKmolPerHour;
  private final double hydrogenAtomBalanceResidualKmolPerHour;

  private RefineryCatalyticReformingFeedBalance(int[] carbonNumbers,
      RefineryCatalyticReformingStoichiometricBalance[] cutBalances, double naphtheneFeedFlowKmolPerHour,
      double paraffinFeedFlowKmolPerHour, double unconvertedNaphtheneFlowKmolPerHour,
      double unconvertedParaffinFlowKmolPerHour, double aromaticProductFlowKmolPerHour,
      double hydrogenProductFlowKmolPerHour, double feedMassFlowKgPerHour, double aromaticProductMassFlowKgPerHour,
      double hydrogenProductMassFlowKgPerHour, double reformateMassFlowKgPerHour, double totalProductMassFlowKgPerHour,
      double reformateMassYieldFraction, double aromaticMassFractionOfReformate, double aromaticProductKgPerTonneFeed,
      double hydrogenProductKgPerTonneFeed, double massBalanceResidualKgPerHour,
      double carbonAtomBalanceResidualKmolPerHour, double hydrogenAtomBalanceResidualKmolPerHour) {
    this.carbonNumbers = carbonNumbers.clone();
    this.cutBalances = cutBalances.clone();
    this.naphtheneFeedFlowKmolPerHour = naphtheneFeedFlowKmolPerHour;
    this.paraffinFeedFlowKmolPerHour = paraffinFeedFlowKmolPerHour;
    this.unconvertedNaphtheneFlowKmolPerHour = unconvertedNaphtheneFlowKmolPerHour;
    this.unconvertedParaffinFlowKmolPerHour = unconvertedParaffinFlowKmolPerHour;
    this.aromaticProductFlowKmolPerHour = aromaticProductFlowKmolPerHour;
    this.hydrogenProductFlowKmolPerHour = hydrogenProductFlowKmolPerHour;
    this.feedMassFlowKgPerHour = feedMassFlowKgPerHour;
    this.aromaticProductMassFlowKgPerHour = aromaticProductMassFlowKgPerHour;
    this.hydrogenProductMassFlowKgPerHour = hydrogenProductMassFlowKgPerHour;
    this.reformateMassFlowKgPerHour = reformateMassFlowKgPerHour;
    this.totalProductMassFlowKgPerHour = totalProductMassFlowKgPerHour;
    this.reformateMassYieldFraction = reformateMassYieldFraction;
    this.aromaticMassFractionOfReformate = aromaticMassFractionOfReformate;
    this.aromaticProductKgPerTonneFeed = aromaticProductKgPerTonneFeed;
    this.hydrogenProductKgPerTonneFeed = hydrogenProductKgPerTonneFeed;
    this.massBalanceResidualKgPerHour = massBalanceResidualKgPerHour;
    this.carbonAtomBalanceResidualKmolPerHour = carbonAtomBalanceResidualKmolPerHour;
    this.hydrogenAtomBalanceResidualKmolPerHour = hydrogenAtomBalanceResidualKmolPerHour;
  }

  /**
   * Calculate a multi-cut catalytic-reforming stoichiometric receipt.
   *
   * <p>
   * Arrays share one index and are retained in caller order. Carbon numbers must be unique and at least six. Every row
   * must contain a positive naphthene-plus-paraffin flow. Conversion values are caller-owned scenario assumptions, not
   * yield predictions.
   *
   * @param carbonNumbers carbon number for each feed cut
   * @param naphtheneFeedFlowsKmolPerHour naphthene feed flows in kmol/h
   * @param paraffinFeedFlowsKmolPerHour paraffin feed flows in kmol/h
   * @param naphtheneConversionFractions naphthene conversion fractions in [0, 1]
   * @param paraffinConversionFractions paraffin conversion fractions in [0, 1]
   * @return immutable multi-cut material-balance receipt
   */
  public static RefineryCatalyticReformingFeedBalance calculate(int[] carbonNumbers,
      double[] naphtheneFeedFlowsKmolPerHour, double[] paraffinFeedFlowsKmolPerHour,
      double[] naphtheneConversionFractions, double[] paraffinConversionFractions) {
    requireInputs(carbonNumbers, naphtheneFeedFlowsKmolPerHour, paraffinFeedFlowsKmolPerHour,
        naphtheneConversionFractions, paraffinConversionFractions);

    RefineryCatalyticReformingStoichiometricBalance[] cutBalances = new RefineryCatalyticReformingStoichiometricBalance[carbonNumbers.length];
    double naphtheneFeedFlowKmolPerHour = 0.0;
    double paraffinFeedFlowKmolPerHour = 0.0;
    double unconvertedNaphtheneFlowKmolPerHour = 0.0;
    double unconvertedParaffinFlowKmolPerHour = 0.0;
    double aromaticProductFlowKmolPerHour = 0.0;
    double hydrogenProductFlowKmolPerHour = 0.0;
    double feedMassFlowKgPerHour = 0.0;
    double aromaticProductMassFlowKgPerHour = 0.0;
    double hydrogenProductMassFlowKgPerHour = 0.0;
    double totalProductMassFlowKgPerHour = 0.0;
    double carbonAtomBalanceResidualKmolPerHour = 0.0;
    double hydrogenAtomBalanceResidualKmolPerHour = 0.0;

    for (int index = 0; index < carbonNumbers.length; index++) {
      RefineryCatalyticReformingStoichiometricBalance cutBalance = RefineryCatalyticReformingStoichiometricBalance
          .calculate(carbonNumbers[index], naphtheneFeedFlowsKmolPerHour[index], paraffinFeedFlowsKmolPerHour[index],
              naphtheneConversionFractions[index], paraffinConversionFractions[index]);
      cutBalances[index] = cutBalance;
      naphtheneFeedFlowKmolPerHour += cutBalance.getNaphtheneFeedFlowKmolPerHour();
      paraffinFeedFlowKmolPerHour += cutBalance.getParaffinFeedFlowKmolPerHour();
      unconvertedNaphtheneFlowKmolPerHour += cutBalance.getUnconvertedNaphtheneFlowKmolPerHour();
      unconvertedParaffinFlowKmolPerHour += cutBalance.getUnconvertedParaffinFlowKmolPerHour();
      aromaticProductFlowKmolPerHour += cutBalance.getAromaticProductFlowKmolPerHour();
      hydrogenProductFlowKmolPerHour += cutBalance.getHydrogenProductFlowKmolPerHour();
      feedMassFlowKgPerHour += cutBalance.getFeedMassFlowKgPerHour();
      aromaticProductMassFlowKgPerHour += cutBalance.getAromaticProductMassFlowKgPerHour();
      hydrogenProductMassFlowKgPerHour += cutBalance.getHydrogenProductMassFlowKgPerHour();
      totalProductMassFlowKgPerHour += cutBalance.getTotalProductMassFlowKgPerHour();
      carbonAtomBalanceResidualKmolPerHour += cutBalance.getCarbonAtomBalanceResidualKmolPerHour();
      hydrogenAtomBalanceResidualKmolPerHour += cutBalance.getHydrogenAtomBalanceResidualKmolPerHour();
    }

    double massBalanceResidualKgPerHour = feedMassFlowKgPerHour - totalProductMassFlowKgPerHour;
    double reformateMassFlowKgPerHour = totalProductMassFlowKgPerHour - hydrogenProductMassFlowKgPerHour;
    double massTolerance = 1.0e-12 * Math.max(1.0, feedMassFlowKgPerHour);
    double atomTolerance = 1.0e-12 * Math.max(1.0, naphtheneFeedFlowKmolPerHour + paraffinFeedFlowKmolPerHour);
    if (!Double.isFinite(feedMassFlowKgPerHour) || !Double.isFinite(totalProductMassFlowKgPerHour)
        || !Double.isFinite(reformateMassFlowKgPerHour) || feedMassFlowKgPerHour <= 0.0
        || reformateMassFlowKgPerHour <= 0.0 || Math.abs(massBalanceResidualKgPerHour) > massTolerance
        || Math.abs(carbonAtomBalanceResidualKmolPerHour) > atomTolerance
        || Math.abs(hydrogenAtomBalanceResidualKmolPerHour) > atomTolerance) {
      throw new IllegalArgumentException("cuts do not define a closed aggregate balance");
    }

    double tonnesFeedPerHour = feedMassFlowKgPerHour / 1000.0;
    double reformateMassYieldFraction = reformateMassFlowKgPerHour / feedMassFlowKgPerHour;
    double aromaticMassFractionOfReformate = aromaticProductMassFlowKgPerHour / reformateMassFlowKgPerHour;
    double aromaticProductKgPerTonneFeed = aromaticProductMassFlowKgPerHour / tonnesFeedPerHour;
    double hydrogenProductKgPerTonneFeed = hydrogenProductMassFlowKgPerHour / tonnesFeedPerHour;

    return new RefineryCatalyticReformingFeedBalance(carbonNumbers, cutBalances, naphtheneFeedFlowKmolPerHour,
        paraffinFeedFlowKmolPerHour, unconvertedNaphtheneFlowKmolPerHour, unconvertedParaffinFlowKmolPerHour,
        aromaticProductFlowKmolPerHour, hydrogenProductFlowKmolPerHour, feedMassFlowKgPerHour,
        aromaticProductMassFlowKgPerHour, hydrogenProductMassFlowKgPerHour, reformateMassFlowKgPerHour,
        totalProductMassFlowKgPerHour, reformateMassYieldFraction, aromaticMassFractionOfReformate,
        aromaticProductKgPerTonneFeed, hydrogenProductKgPerTonneFeed, massBalanceResidualKgPerHour,
        carbonAtomBalanceResidualKmolPerHour, hydrogenAtomBalanceResidualKmolPerHour);
  }

  /**
   * Validate input arrays and unique carbon-number rows.
   *
   * @param carbonNumbers carbon-number array
   * @param naphtheneFeedFlows naphthene-flow array
   * @param paraffinFeedFlows paraffin-flow array
   * @param naphtheneConversions naphthene-conversion array
   * @param paraffinConversions paraffin-conversion array
   */
  private static void requireInputs(int[] carbonNumbers, double[] naphtheneFeedFlows, double[] paraffinFeedFlows,
      double[] naphtheneConversions, double[] paraffinConversions) {
    if (carbonNumbers == null || naphtheneFeedFlows == null || paraffinFeedFlows == null || naphtheneConversions == null
        || paraffinConversions == null) {
      throw new IllegalArgumentException("input arrays must not be null");
    }
    int length = carbonNumbers.length;
    if (length == 0 || naphtheneFeedFlows.length != length || paraffinFeedFlows.length != length
        || naphtheneConversions.length != length || paraffinConversions.length != length) {
      throw new IllegalArgumentException("input arrays must have the same positive length");
    }
    for (int index = 0; index < length; index++) {
      for (int prior = 0; prior < index; prior++) {
        if (carbonNumbers[index] == carbonNumbers[prior]) {
          throw new IllegalArgumentException("carbonNumbers must be unique");
        }
      }
    }
  }

  /** @return carbon numbers in caller order as a defensive copy */
  public int[] getCarbonNumbers() {
    return carbonNumbers.clone();
  }

  /** @return immutable per-cut receipts as a defensive array copy */
  public RefineryCatalyticReformingStoichiometricBalance[] getCutBalances() {
    return cutBalances.clone();
  }

  /** @return total naphthene feed flow in kmol/h */
  public double getNaphtheneFeedFlowKmolPerHour() {
    return naphtheneFeedFlowKmolPerHour;
  }

  /** @return total paraffin feed flow in kmol/h */
  public double getParaffinFeedFlowKmolPerHour() {
    return paraffinFeedFlowKmolPerHour;
  }

  /** @return total unconverted naphthene flow in kmol/h */
  public double getUnconvertedNaphtheneFlowKmolPerHour() {
    return unconvertedNaphtheneFlowKmolPerHour;
  }

  /** @return total unconverted paraffin flow in kmol/h */
  public double getUnconvertedParaffinFlowKmolPerHour() {
    return unconvertedParaffinFlowKmolPerHour;
  }

  /** @return total aromatic product flow in kmol/h */
  public double getAromaticProductFlowKmolPerHour() {
    return aromaticProductFlowKmolPerHour;
  }

  /** @return total hydrogen product flow in kmol/h */
  public double getHydrogenProductFlowKmolPerHour() {
    return hydrogenProductFlowKmolPerHour;
  }

  /** @return total feed mass flow in kg/h */
  public double getFeedMassFlowKgPerHour() {
    return feedMassFlowKgPerHour;
  }

  /** @return total aromatic product mass flow in kg/h */
  public double getAromaticProductMassFlowKgPerHour() {
    return aromaticProductMassFlowKgPerHour;
  }

  /** @return total hydrogen product mass flow in kg/h */
  public double getHydrogenProductMassFlowKgPerHour() {
    return hydrogenProductMassFlowKgPerHour;
  }

  /** @return idealized liquid hydrocarbon reformate mass flow in kg/h */
  public double getReformateMassFlowKgPerHour() {
    return reformateMassFlowKgPerHour;
  }

  /** @return total product mass flow, including hydrogen, in kg/h */
  public double getTotalProductMassFlowKgPerHour() {
    return totalProductMassFlowKgPerHour;
  }

  /** @return reformate mass divided by feed mass */
  public double getReformateMassYieldFraction() {
    return reformateMassYieldFraction;
  }

  /** @return aromatic product mass divided by idealized reformate mass */
  public double getAromaticMassFractionOfReformate() {
    return aromaticMassFractionOfReformate;
  }

  /** @return aromatic product in kg per tonne of feed */
  public double getAromaticProductKgPerTonneFeed() {
    return aromaticProductKgPerTonneFeed;
  }

  /** @return hydrogen product in kg per tonne of feed */
  public double getHydrogenProductKgPerTonneFeed() {
    return hydrogenProductKgPerTonneFeed;
  }

  /** @return total mass-balance residual in kg/h */
  public double getMassBalanceResidualKgPerHour() {
    return massBalanceResidualKgPerHour;
  }

  /** @return carbon-atom balance residual in kmol-atoms/h */
  public double getCarbonAtomBalanceResidualKmolPerHour() {
    return carbonAtomBalanceResidualKmolPerHour;
  }

  /** @return hydrogen-atom balance residual in kmol-atoms/h */
  public double getHydrogenAtomBalanceResidualKmolPerHour() {
    return hydrogenAtomBalanceResidualKmolPerHour;
  }
}
