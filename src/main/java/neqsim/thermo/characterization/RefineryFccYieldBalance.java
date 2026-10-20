package neqsim.thermo.characterization;

import java.io.Serializable;

/**
 * Immutable screening-level fluid catalytic cracking product-yield balance.
 *
 * <p>
 * The calculation converts a caller-selected fraction of a hydrocarbon feed and allocates the converted feed among dry
 * gas, liquefied petroleum gas, gasoline, light cycle oil, and coke. It is an algebraic scenario receipt, not a
 * kinetic, riser, catalyst, deactivation, fractionation, or product-property model.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryFccYieldBalance implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double FRACTION_TOLERANCE = 1.0e-12;

  private final double feedMassFlowKgPerHour;
  private final double conversionFraction;
  private final double dryGasMassFractionOfConvertedFeed;
  private final double liquefiedPetroleumGasMassFractionOfConvertedFeed;
  private final double gasolineMassFractionOfConvertedFeed;
  private final double lightCycleOilMassFractionOfConvertedFeed;
  private final double cokeMassFractionOfConvertedFeed;
  private final double convertedFeedMassFlowKgPerHour;
  private final double unconvertedOilMassFlowKgPerHour;
  private final double dryGasMassFlowKgPerHour;
  private final double liquefiedPetroleumGasMassFlowKgPerHour;
  private final double gasolineMassFlowKgPerHour;
  private final double lightCycleOilMassFlowKgPerHour;
  private final double cokeMassFlowKgPerHour;
  private final double totalProductMassFlowKgPerHour;
  private final double feedConversionBalanceResidualKgPerHour;
  private final double convertedProductMassBalanceResidualKgPerHour;
  private final double overallMassBalanceResidualKgPerHour;

  private RefineryFccYieldBalance(double feedMassFlowKgPerHour, double conversionFraction,
      double dryGasMassFractionOfConvertedFeed, double liquefiedPetroleumGasMassFractionOfConvertedFeed,
      double gasolineMassFractionOfConvertedFeed, double lightCycleOilMassFractionOfConvertedFeed,
      double cokeMassFractionOfConvertedFeed, double convertedFeedMassFlowKgPerHour,
      double unconvertedOilMassFlowKgPerHour, double dryGasMassFlowKgPerHour,
      double liquefiedPetroleumGasMassFlowKgPerHour, double gasolineMassFlowKgPerHour,
      double lightCycleOilMassFlowKgPerHour, double cokeMassFlowKgPerHour, double totalProductMassFlowKgPerHour,
      double feedConversionBalanceResidualKgPerHour, double convertedProductMassBalanceResidualKgPerHour,
      double overallMassBalanceResidualKgPerHour) {
    this.feedMassFlowKgPerHour = feedMassFlowKgPerHour;
    this.conversionFraction = conversionFraction;
    this.dryGasMassFractionOfConvertedFeed = dryGasMassFractionOfConvertedFeed;
    this.liquefiedPetroleumGasMassFractionOfConvertedFeed = liquefiedPetroleumGasMassFractionOfConvertedFeed;
    this.gasolineMassFractionOfConvertedFeed = gasolineMassFractionOfConvertedFeed;
    this.lightCycleOilMassFractionOfConvertedFeed = lightCycleOilMassFractionOfConvertedFeed;
    this.cokeMassFractionOfConvertedFeed = cokeMassFractionOfConvertedFeed;
    this.convertedFeedMassFlowKgPerHour = convertedFeedMassFlowKgPerHour;
    this.unconvertedOilMassFlowKgPerHour = unconvertedOilMassFlowKgPerHour;
    this.dryGasMassFlowKgPerHour = dryGasMassFlowKgPerHour;
    this.liquefiedPetroleumGasMassFlowKgPerHour = liquefiedPetroleumGasMassFlowKgPerHour;
    this.gasolineMassFlowKgPerHour = gasolineMassFlowKgPerHour;
    this.lightCycleOilMassFlowKgPerHour = lightCycleOilMassFlowKgPerHour;
    this.cokeMassFlowKgPerHour = cokeMassFlowKgPerHour;
    this.totalProductMassFlowKgPerHour = totalProductMassFlowKgPerHour;
    this.feedConversionBalanceResidualKgPerHour = feedConversionBalanceResidualKgPerHour;
    this.convertedProductMassBalanceResidualKgPerHour = convertedProductMassBalanceResidualKgPerHour;
    this.overallMassBalanceResidualKgPerHour = overallMassBalanceResidualKgPerHour;
  }

  /**
   * Calculate a five-lump FCC converted-product balance plus unconverted oil.
   *
   * @param feedMassFlowKgPerHour hydrocarbon feed mass flow in kg/h
   * @param conversionFraction fraction of hydrocarbon feed converted, in [0, 1]
   * @param dryGasMassFractionOfConvertedFeed dry-gas fraction of converted feed
   * @param liquefiedPetroleumGasMassFractionOfConvertedFeed LPG fraction of converted feed
   * @param gasolineMassFractionOfConvertedFeed gasoline fraction of converted feed
   * @param lightCycleOilMassFractionOfConvertedFeed light-cycle-oil fraction of converted feed
   * @param cokeMassFractionOfConvertedFeed coke fraction of converted feed
   * @return immutable FCC yield receipt
   */
  public static RefineryFccYieldBalance calculate(double feedMassFlowKgPerHour, double conversionFraction,
      double dryGasMassFractionOfConvertedFeed, double liquefiedPetroleumGasMassFractionOfConvertedFeed,
      double gasolineMassFractionOfConvertedFeed, double lightCycleOilMassFractionOfConvertedFeed,
      double cokeMassFractionOfConvertedFeed) {
    if (!Double.isFinite(feedMassFlowKgPerHour) || feedMassFlowKgPerHour <= 0.0) {
      throw new IllegalArgumentException("feedMassFlowKgPerHour must be finite and positive");
    }
    if (!Double.isFinite(conversionFraction) || conversionFraction < 0.0 || conversionFraction > 1.0) {
      throw new IllegalArgumentException("conversionFraction must be finite and in [0, 1]");
    }
    if (!allFiniteNonNegative(dryGasMassFractionOfConvertedFeed, liquefiedPetroleumGasMassFractionOfConvertedFeed,
        gasolineMassFractionOfConvertedFeed, lightCycleOilMassFractionOfConvertedFeed,
        cokeMassFractionOfConvertedFeed)) {
      throw new IllegalArgumentException("converted-feed product fractions must be finite and non-negative");
    }
    double fractionSum = dryGasMassFractionOfConvertedFeed + liquefiedPetroleumGasMassFractionOfConvertedFeed
        + gasolineMassFractionOfConvertedFeed + lightCycleOilMassFractionOfConvertedFeed
        + cokeMassFractionOfConvertedFeed;
    if (Math.abs(fractionSum - 1.0) > FRACTION_TOLERANCE) {
      throw new IllegalArgumentException("converted-feed product fractions must sum to one");
    }

    double convertedFeedMassFlowKgPerHour = feedMassFlowKgPerHour * conversionFraction;
    double unconvertedOilMassFlowKgPerHour = feedMassFlowKgPerHour - convertedFeedMassFlowKgPerHour;
    double dryGasMassFlowKgPerHour = convertedFeedMassFlowKgPerHour * dryGasMassFractionOfConvertedFeed;
    double liquefiedPetroleumGasMassFlowKgPerHour = convertedFeedMassFlowKgPerHour
        * liquefiedPetroleumGasMassFractionOfConvertedFeed;
    double gasolineMassFlowKgPerHour = convertedFeedMassFlowKgPerHour * gasolineMassFractionOfConvertedFeed;
    double lightCycleOilMassFlowKgPerHour = convertedFeedMassFlowKgPerHour * lightCycleOilMassFractionOfConvertedFeed;
    double cokeMassFlowKgPerHour = convertedFeedMassFlowKgPerHour * cokeMassFractionOfConvertedFeed;
    double convertedProductsMassFlowKgPerHour = dryGasMassFlowKgPerHour + liquefiedPetroleumGasMassFlowKgPerHour
        + gasolineMassFlowKgPerHour + lightCycleOilMassFlowKgPerHour + cokeMassFlowKgPerHour;
    double totalProductMassFlowKgPerHour = unconvertedOilMassFlowKgPerHour + convertedProductsMassFlowKgPerHour;
    double feedConversionBalanceResidualKgPerHour = feedMassFlowKgPerHour - convertedFeedMassFlowKgPerHour
        - unconvertedOilMassFlowKgPerHour;
    double convertedProductMassBalanceResidualKgPerHour = convertedFeedMassFlowKgPerHour
        - convertedProductsMassFlowKgPerHour;
    double overallMassBalanceResidualKgPerHour = feedMassFlowKgPerHour - totalProductMassFlowKgPerHour;

    return new RefineryFccYieldBalance(feedMassFlowKgPerHour, conversionFraction, dryGasMassFractionOfConvertedFeed,
        liquefiedPetroleumGasMassFractionOfConvertedFeed, gasolineMassFractionOfConvertedFeed,
        lightCycleOilMassFractionOfConvertedFeed, cokeMassFractionOfConvertedFeed, convertedFeedMassFlowKgPerHour,
        unconvertedOilMassFlowKgPerHour, dryGasMassFlowKgPerHour, liquefiedPetroleumGasMassFlowKgPerHour,
        gasolineMassFlowKgPerHour, lightCycleOilMassFlowKgPerHour, cokeMassFlowKgPerHour, totalProductMassFlowKgPerHour,
        feedConversionBalanceResidualKgPerHour, convertedProductMassBalanceResidualKgPerHour,
        overallMassBalanceResidualKgPerHour);
  }

  /**
   * Test that all supplied values are finite and non-negative.
   *
   * @param values values to inspect
   * @return true when every value is finite and non-negative
   */
  private static boolean allFiniteNonNegative(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value) || value < 0.0) {
        return false;
      }
    }
    return true;
  }

  /** @return hydrocarbon feed mass flow in kg/h */
  public double getFeedMassFlowKgPerHour() {
    return feedMassFlowKgPerHour;
  }

  /** @return converted fraction of hydrocarbon feed */
  public double getConversionFraction() {
    return conversionFraction;
  }

  /** @return dry-gas fraction of converted feed */
  public double getDryGasMassFractionOfConvertedFeed() {
    return dryGasMassFractionOfConvertedFeed;
  }

  /** @return LPG fraction of converted feed */
  public double getLiquefiedPetroleumGasMassFractionOfConvertedFeed() {
    return liquefiedPetroleumGasMassFractionOfConvertedFeed;
  }

  /** @return gasoline fraction of converted feed */
  public double getGasolineMassFractionOfConvertedFeed() {
    return gasolineMassFractionOfConvertedFeed;
  }

  /** @return light-cycle-oil fraction of converted feed */
  public double getLightCycleOilMassFractionOfConvertedFeed() {
    return lightCycleOilMassFractionOfConvertedFeed;
  }

  /** @return coke fraction of converted feed */
  public double getCokeMassFractionOfConvertedFeed() {
    return cokeMassFractionOfConvertedFeed;
  }

  /** @return converted feed mass flow in kg/h */
  public double getConvertedFeedMassFlowKgPerHour() {
    return convertedFeedMassFlowKgPerHour;
  }

  /** @return unconverted oil mass flow in kg/h */
  public double getUnconvertedOilMassFlowKgPerHour() {
    return unconvertedOilMassFlowKgPerHour;
  }

  /** @return dry-gas mass flow in kg/h */
  public double getDryGasMassFlowKgPerHour() {
    return dryGasMassFlowKgPerHour;
  }

  /** @return LPG mass flow in kg/h */
  public double getLiquefiedPetroleumGasMassFlowKgPerHour() {
    return liquefiedPetroleumGasMassFlowKgPerHour;
  }

  /** @return gasoline mass flow in kg/h */
  public double getGasolineMassFlowKgPerHour() {
    return gasolineMassFlowKgPerHour;
  }

  /** @return light-cycle-oil mass flow in kg/h */
  public double getLightCycleOilMassFlowKgPerHour() {
    return lightCycleOilMassFlowKgPerHour;
  }

  /** @return coke mass flow in kg/h */
  public double getCokeMassFlowKgPerHour() {
    return cokeMassFlowKgPerHour;
  }

  /** @return total represented product mass flow in kg/h */
  public double getTotalProductMassFlowKgPerHour() {
    return totalProductMassFlowKgPerHour;
  }

  /** @return feed-minus-converted-minus-unconverted residual in kg/h */
  public double getFeedConversionBalanceResidualKgPerHour() {
    return feedConversionBalanceResidualKgPerHour;
  }

  /** @return converted-feed-minus-converted-products residual in kg/h */
  public double getConvertedProductMassBalanceResidualKgPerHour() {
    return convertedProductMassBalanceResidualKgPerHour;
  }

  /** @return feed-minus-all-products residual in kg/h */
  public double getOverallMassBalanceResidualKgPerHour() {
    return overallMassBalanceResidualKgPerHour;
  }
}
