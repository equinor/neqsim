package neqsim.thermo.characterization;

import java.io.Serializable;

/**
 * Immutable screening-level delayed-coking product-yield balance.
 *
 * <p>
 * The calculation converts a caller-selected fraction of a residue feed and allocates the converted feed among gas,
 * naphtha, light coker gas oil, heavy coker gas oil, and petroleum coke. It is an algebraic scenario receipt, not a
 * kinetic, furnace, coke-drum, cycle-time, fractionation, or product-property model.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryDelayedCokingYieldBalance implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double FRACTION_TOLERANCE = 1.0e-12;

  private final double feedMassFlowKgPerHour;
  private final double conversionFraction;
  private final double gasMassFractionOfConvertedFeed;
  private final double naphthaMassFractionOfConvertedFeed;
  private final double lightCokerGasOilMassFractionOfConvertedFeed;
  private final double heavyCokerGasOilMassFractionOfConvertedFeed;
  private final double cokeMassFractionOfConvertedFeed;
  private final double convertedFeedMassFlowKgPerHour;
  private final double unconvertedResidueMassFlowKgPerHour;
  private final double gasMassFlowKgPerHour;
  private final double naphthaMassFlowKgPerHour;
  private final double lightCokerGasOilMassFlowKgPerHour;
  private final double heavyCokerGasOilMassFlowKgPerHour;
  private final double cokeMassFlowKgPerHour;
  private final double totalProductMassFlowKgPerHour;
  private final double convertedProductMassBalanceResidualKgPerHour;
  private final double overallMassBalanceResidualKgPerHour;

  private RefineryDelayedCokingYieldBalance(double feedMassFlowKgPerHour, double conversionFraction,
      double gasMassFractionOfConvertedFeed, double naphthaMassFractionOfConvertedFeed,
      double lightCokerGasOilMassFractionOfConvertedFeed, double heavyCokerGasOilMassFractionOfConvertedFeed,
      double cokeMassFractionOfConvertedFeed, double convertedFeedMassFlowKgPerHour,
      double unconvertedResidueMassFlowKgPerHour, double gasMassFlowKgPerHour, double naphthaMassFlowKgPerHour,
      double lightCokerGasOilMassFlowKgPerHour, double heavyCokerGasOilMassFlowKgPerHour, double cokeMassFlowKgPerHour,
      double totalProductMassFlowKgPerHour, double convertedProductMassBalanceResidualKgPerHour,
      double overallMassBalanceResidualKgPerHour) {
    this.feedMassFlowKgPerHour = feedMassFlowKgPerHour;
    this.conversionFraction = conversionFraction;
    this.gasMassFractionOfConvertedFeed = gasMassFractionOfConvertedFeed;
    this.naphthaMassFractionOfConvertedFeed = naphthaMassFractionOfConvertedFeed;
    this.lightCokerGasOilMassFractionOfConvertedFeed = lightCokerGasOilMassFractionOfConvertedFeed;
    this.heavyCokerGasOilMassFractionOfConvertedFeed = heavyCokerGasOilMassFractionOfConvertedFeed;
    this.cokeMassFractionOfConvertedFeed = cokeMassFractionOfConvertedFeed;
    this.convertedFeedMassFlowKgPerHour = convertedFeedMassFlowKgPerHour;
    this.unconvertedResidueMassFlowKgPerHour = unconvertedResidueMassFlowKgPerHour;
    this.gasMassFlowKgPerHour = gasMassFlowKgPerHour;
    this.naphthaMassFlowKgPerHour = naphthaMassFlowKgPerHour;
    this.lightCokerGasOilMassFlowKgPerHour = lightCokerGasOilMassFlowKgPerHour;
    this.heavyCokerGasOilMassFlowKgPerHour = heavyCokerGasOilMassFlowKgPerHour;
    this.cokeMassFlowKgPerHour = cokeMassFlowKgPerHour;
    this.totalProductMassFlowKgPerHour = totalProductMassFlowKgPerHour;
    this.convertedProductMassBalanceResidualKgPerHour = convertedProductMassBalanceResidualKgPerHour;
    this.overallMassBalanceResidualKgPerHour = overallMassBalanceResidualKgPerHour;
  }

  /**
   * Calculate a five-lump delayed-coking converted-product balance plus unconverted residue.
   *
   * @param feedMassFlowKgPerHour residue feed mass flow in kg/h
   * @param conversionFraction fraction of residue feed converted, in [0, 1]
   * @param gasMassFractionOfConvertedFeed gas fraction of converted feed
   * @param naphthaMassFractionOfConvertedFeed naphtha fraction of converted feed
   * @param lightCokerGasOilMassFractionOfConvertedFeed light coker gas oil fraction
   * @param heavyCokerGasOilMassFractionOfConvertedFeed heavy coker gas oil fraction
   * @param cokeMassFractionOfConvertedFeed petroleum-coke fraction of converted feed
   * @return immutable delayed-coking yield receipt
   */
  public static RefineryDelayedCokingYieldBalance calculate(double feedMassFlowKgPerHour, double conversionFraction,
      double gasMassFractionOfConvertedFeed, double naphthaMassFractionOfConvertedFeed,
      double lightCokerGasOilMassFractionOfConvertedFeed, double heavyCokerGasOilMassFractionOfConvertedFeed,
      double cokeMassFractionOfConvertedFeed) {
    if (!Double.isFinite(feedMassFlowKgPerHour) || feedMassFlowKgPerHour <= 0.0) {
      throw new IllegalArgumentException("feedMassFlowKgPerHour must be finite and positive");
    }
    if (!Double.isFinite(conversionFraction) || conversionFraction < 0.0 || conversionFraction > 1.0) {
      throw new IllegalArgumentException("conversionFraction must be finite and in [0, 1]");
    }
    if (!allFiniteNonNegative(gasMassFractionOfConvertedFeed, naphthaMassFractionOfConvertedFeed,
        lightCokerGasOilMassFractionOfConvertedFeed, heavyCokerGasOilMassFractionOfConvertedFeed,
        cokeMassFractionOfConvertedFeed)) {
      throw new IllegalArgumentException("converted-feed product fractions must be finite and non-negative");
    }
    double fractionSum = gasMassFractionOfConvertedFeed + naphthaMassFractionOfConvertedFeed
        + lightCokerGasOilMassFractionOfConvertedFeed + heavyCokerGasOilMassFractionOfConvertedFeed
        + cokeMassFractionOfConvertedFeed;
    if (Math.abs(fractionSum - 1.0) > FRACTION_TOLERANCE) {
      throw new IllegalArgumentException("converted-feed product fractions must sum to one");
    }

    double convertedFeed = feedMassFlowKgPerHour * conversionFraction;
    double unconvertedResidue = feedMassFlowKgPerHour - convertedFeed;
    double gas = convertedFeed * gasMassFractionOfConvertedFeed;
    double naphtha = convertedFeed * naphthaMassFractionOfConvertedFeed;
    double lightCokerGasOil = convertedFeed * lightCokerGasOilMassFractionOfConvertedFeed;
    double heavyCokerGasOil = convertedFeed * heavyCokerGasOilMassFractionOfConvertedFeed;
    double coke = convertedFeed * cokeMassFractionOfConvertedFeed;
    double convertedProducts = gas + naphtha + lightCokerGasOil + heavyCokerGasOil + coke;
    double totalProducts = unconvertedResidue + convertedProducts;

    return new RefineryDelayedCokingYieldBalance(feedMassFlowKgPerHour, conversionFraction,
        gasMassFractionOfConvertedFeed, naphthaMassFractionOfConvertedFeed, lightCokerGasOilMassFractionOfConvertedFeed,
        heavyCokerGasOilMassFractionOfConvertedFeed, cokeMassFractionOfConvertedFeed, convertedFeed, unconvertedResidue,
        gas, naphtha, lightCokerGasOil, heavyCokerGasOil, coke, totalProducts, convertedFeed - convertedProducts,
        feedMassFlowKgPerHour - totalProducts);
  }

  private static boolean allFiniteNonNegative(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value) || value < 0.0) {
        return false;
      }
    }
    return true;
  }

  /** @return residue feed mass flow in kg/h */
  public double getFeedMassFlowKgPerHour() {
    return feedMassFlowKgPerHour;
  }

  /** @return converted fraction of residue feed */
  public double getConversionFraction() {
    return conversionFraction;
  }

  /** @return gas fraction of converted feed */
  public double getGasMassFractionOfConvertedFeed() {
    return gasMassFractionOfConvertedFeed;
  }

  /** @return naphtha fraction of converted feed */
  public double getNaphthaMassFractionOfConvertedFeed() {
    return naphthaMassFractionOfConvertedFeed;
  }

  /** @return light coker gas oil fraction of converted feed */
  public double getLightCokerGasOilMassFractionOfConvertedFeed() {
    return lightCokerGasOilMassFractionOfConvertedFeed;
  }

  /** @return heavy coker gas oil fraction of converted feed */
  public double getHeavyCokerGasOilMassFractionOfConvertedFeed() {
    return heavyCokerGasOilMassFractionOfConvertedFeed;
  }

  /** @return coke fraction of converted feed */
  public double getCokeMassFractionOfConvertedFeed() {
    return cokeMassFractionOfConvertedFeed;
  }

  /** @return converted feed mass flow in kg/h */
  public double getConvertedFeedMassFlowKgPerHour() {
    return convertedFeedMassFlowKgPerHour;
  }

  /** @return unconverted residue mass flow in kg/h */
  public double getUnconvertedResidueMassFlowKgPerHour() {
    return unconvertedResidueMassFlowKgPerHour;
  }

  /** @return gas mass flow in kg/h */
  public double getGasMassFlowKgPerHour() {
    return gasMassFlowKgPerHour;
  }

  /** @return naphtha mass flow in kg/h */
  public double getNaphthaMassFlowKgPerHour() {
    return naphthaMassFlowKgPerHour;
  }

  /** @return light coker gas oil mass flow in kg/h */
  public double getLightCokerGasOilMassFlowKgPerHour() {
    return lightCokerGasOilMassFlowKgPerHour;
  }

  /** @return heavy coker gas oil mass flow in kg/h */
  public double getHeavyCokerGasOilMassFlowKgPerHour() {
    return heavyCokerGasOilMassFlowKgPerHour;
  }

  /** @return coke mass flow in kg/h */
  public double getCokeMassFlowKgPerHour() {
    return cokeMassFlowKgPerHour;
  }

  /** @return total represented product mass flow in kg/h */
  public double getTotalProductMassFlowKgPerHour() {
    return totalProductMassFlowKgPerHour;
  }

  /** @return converted-feed-minus-products residual in kg/h */
  public double getConvertedProductMassBalanceResidualKgPerHour() {
    return convertedProductMassBalanceResidualKgPerHour;
  }

  /** @return feed-minus-all-products residual in kg/h */
  public double getOverallMassBalanceResidualKgPerHour() {
    return overallMassBalanceResidualKgPerHour;
  }
}
