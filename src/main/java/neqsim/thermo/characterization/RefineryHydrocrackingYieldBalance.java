package neqsim.thermo.characterization;

import java.io.Serializable;

/**
 * Immutable screening-level hydrocracking product-yield and hydrogen-consumption balance.
 *
 * <p>
 * The calculation converts a caller-selected fraction of a hydrocarbon feed, adds a caller-selected hydrogen
 * consumption, and allocates the converted outlet among light gas, naphtha, and middle-distillate lumps. It is an
 * algebraic scenario receipt, not a kinetic, reactor, catalyst, phase-equilibrium, or product-property model.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrocrackingYieldBalance implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double FRACTION_TOLERANCE = 1.0e-12;

  private final double feedMassFlowKgPerHour;
  private final double conversionFraction;
  private final double hydrogenConsumptionKgPerTonneFeed;
  private final double lightGasMassFractionOfConvertedProduct;
  private final double naphthaMassFractionOfConvertedProduct;
  private final double middleDistillateMassFractionOfConvertedProduct;
  private final double convertedFeedMassFlowKgPerHour;
  private final double unconvertedOilMassFlowKgPerHour;
  private final double hydrogenConsumptionMassFlowKgPerHour;
  private final double convertedProductMassFlowKgPerHour;
  private final double lightGasMassFlowKgPerHour;
  private final double naphthaMassFlowKgPerHour;
  private final double middleDistillateMassFlowKgPerHour;
  private final double totalProductMassFlowKgPerHour;
  private final double feedConversionBalanceResidualKgPerHour;
  private final double convertedProductMassBalanceResidualKgPerHour;
  private final double overallMassBalanceResidualKgPerHour;

  private RefineryHydrocrackingYieldBalance(double feedMassFlowKgPerHour, double conversionFraction,
      double hydrogenConsumptionKgPerTonneFeed, double lightGasMassFractionOfConvertedProduct,
      double naphthaMassFractionOfConvertedProduct, double middleDistillateMassFractionOfConvertedProduct,
      double convertedFeedMassFlowKgPerHour, double unconvertedOilMassFlowKgPerHour,
      double hydrogenConsumptionMassFlowKgPerHour, double convertedProductMassFlowKgPerHour,
      double lightGasMassFlowKgPerHour, double naphthaMassFlowKgPerHour, double middleDistillateMassFlowKgPerHour,
      double totalProductMassFlowKgPerHour, double feedConversionBalanceResidualKgPerHour,
      double convertedProductMassBalanceResidualKgPerHour, double overallMassBalanceResidualKgPerHour) {
    this.feedMassFlowKgPerHour = feedMassFlowKgPerHour;
    this.conversionFraction = conversionFraction;
    this.hydrogenConsumptionKgPerTonneFeed = hydrogenConsumptionKgPerTonneFeed;
    this.lightGasMassFractionOfConvertedProduct = lightGasMassFractionOfConvertedProduct;
    this.naphthaMassFractionOfConvertedProduct = naphthaMassFractionOfConvertedProduct;
    this.middleDistillateMassFractionOfConvertedProduct = middleDistillateMassFractionOfConvertedProduct;
    this.convertedFeedMassFlowKgPerHour = convertedFeedMassFlowKgPerHour;
    this.unconvertedOilMassFlowKgPerHour = unconvertedOilMassFlowKgPerHour;
    this.hydrogenConsumptionMassFlowKgPerHour = hydrogenConsumptionMassFlowKgPerHour;
    this.convertedProductMassFlowKgPerHour = convertedProductMassFlowKgPerHour;
    this.lightGasMassFlowKgPerHour = lightGasMassFlowKgPerHour;
    this.naphthaMassFlowKgPerHour = naphthaMassFlowKgPerHour;
    this.middleDistillateMassFlowKgPerHour = middleDistillateMassFlowKgPerHour;
    this.totalProductMassFlowKgPerHour = totalProductMassFlowKgPerHour;
    this.feedConversionBalanceResidualKgPerHour = feedConversionBalanceResidualKgPerHour;
    this.convertedProductMassBalanceResidualKgPerHour = convertedProductMassBalanceResidualKgPerHour;
    this.overallMassBalanceResidualKgPerHour = overallMassBalanceResidualKgPerHour;
  }

  /**
   * Calculate a three-product-lump hydrocracking yield balance.
   *
   * @param feedMassFlowKgPerHour hydrocarbon feed mass flow in kg/h
   * @param conversionFraction fraction of hydrocarbon feed converted, in [0, 1]
   * @param hydrogenConsumptionKgPerTonneFeed consumed hydrogen in kg/t hydrocarbon feed
   * @param lightGasMassFractionOfConvertedProduct light-gas fraction of converted feed plus consumed hydrogen
   * @param naphthaMassFractionOfConvertedProduct naphtha fraction of converted feed plus consumed hydrogen
   * @param middleDistillateMassFractionOfConvertedProduct middle-distillate fraction of converted feed plus consumed
   * hydrogen
   * @return immutable hydrocracking yield receipt
   */
  public static RefineryHydrocrackingYieldBalance calculate(double feedMassFlowKgPerHour, double conversionFraction,
      double hydrogenConsumptionKgPerTonneFeed, double lightGasMassFractionOfConvertedProduct,
      double naphthaMassFractionOfConvertedProduct, double middleDistillateMassFractionOfConvertedProduct) {
    if (!Double.isFinite(feedMassFlowKgPerHour) || feedMassFlowKgPerHour <= 0.0) {
      throw new IllegalArgumentException("feedMassFlowKgPerHour must be finite and positive");
    }
    if (!Double.isFinite(conversionFraction) || conversionFraction < 0.0 || conversionFraction > 1.0) {
      throw new IllegalArgumentException("conversionFraction must be finite and in [0, 1]");
    }
    if (!Double.isFinite(hydrogenConsumptionKgPerTonneFeed) || hydrogenConsumptionKgPerTonneFeed < 0.0) {
      throw new IllegalArgumentException("hydrogenConsumptionKgPerTonneFeed must be finite and non-negative");
    }
    if (!allFiniteNonNegative(lightGasMassFractionOfConvertedProduct, naphthaMassFractionOfConvertedProduct,
        middleDistillateMassFractionOfConvertedProduct)) {
      throw new IllegalArgumentException("converted-product mass fractions must be finite and non-negative");
    }
    double fractionSum = lightGasMassFractionOfConvertedProduct + naphthaMassFractionOfConvertedProduct
        + middleDistillateMassFractionOfConvertedProduct;
    if (Math.abs(fractionSum - 1.0) > FRACTION_TOLERANCE) {
      throw new IllegalArgumentException("converted-product mass fractions must sum to one");
    }
    if (conversionFraction == 0.0 && hydrogenConsumptionKgPerTonneFeed > 0.0) {
      throw new IllegalArgumentException("hydrogen consumption must be zero when conversion is zero");
    }

    double convertedFeedMassFlowKgPerHour = feedMassFlowKgPerHour * conversionFraction;
    double unconvertedOilMassFlowKgPerHour = feedMassFlowKgPerHour - convertedFeedMassFlowKgPerHour;
    double hydrogenConsumptionMassFlowKgPerHour = feedMassFlowKgPerHour * hydrogenConsumptionKgPerTonneFeed / 1000.0;
    double convertedProductMassFlowKgPerHour = convertedFeedMassFlowKgPerHour + hydrogenConsumptionMassFlowKgPerHour;
    double lightGasMassFlowKgPerHour = convertedProductMassFlowKgPerHour * lightGasMassFractionOfConvertedProduct;
    double naphthaMassFlowKgPerHour = convertedProductMassFlowKgPerHour * naphthaMassFractionOfConvertedProduct;
    double middleDistillateMassFlowKgPerHour = convertedProductMassFlowKgPerHour
        * middleDistillateMassFractionOfConvertedProduct;
    double totalProductMassFlowKgPerHour = unconvertedOilMassFlowKgPerHour + lightGasMassFlowKgPerHour
        + naphthaMassFlowKgPerHour + middleDistillateMassFlowKgPerHour;
    double feedConversionBalanceResidualKgPerHour = feedMassFlowKgPerHour - convertedFeedMassFlowKgPerHour
        - unconvertedOilMassFlowKgPerHour;
    double convertedProductMassBalanceResidualKgPerHour = convertedProductMassFlowKgPerHour - lightGasMassFlowKgPerHour
        - naphthaMassFlowKgPerHour - middleDistillateMassFlowKgPerHour;
    double overallMassBalanceResidualKgPerHour = feedMassFlowKgPerHour + hydrogenConsumptionMassFlowKgPerHour
        - totalProductMassFlowKgPerHour;

    double tolerance = 1.0e-12 * Math.max(1.0, totalProductMassFlowKgPerHour);
    if (!allFiniteNonNegative(convertedFeedMassFlowKgPerHour, unconvertedOilMassFlowKgPerHour,
        hydrogenConsumptionMassFlowKgPerHour, convertedProductMassFlowKgPerHour, lightGasMassFlowKgPerHour,
        naphthaMassFlowKgPerHour, middleDistillateMassFlowKgPerHour, totalProductMassFlowKgPerHour)
        || Math.abs(feedConversionBalanceResidualKgPerHour) > tolerance
        || Math.abs(convertedProductMassBalanceResidualKgPerHour) > tolerance
        || Math.abs(overallMassBalanceResidualKgPerHour) > tolerance) {
      throw new IllegalArgumentException("inputs do not define a closed hydrocracking yield balance");
    }

    return new RefineryHydrocrackingYieldBalance(feedMassFlowKgPerHour, conversionFraction,
        hydrogenConsumptionKgPerTonneFeed, lightGasMassFractionOfConvertedProduct,
        naphthaMassFractionOfConvertedProduct, middleDistillateMassFractionOfConvertedProduct,
        convertedFeedMassFlowKgPerHour, unconvertedOilMassFlowKgPerHour, hydrogenConsumptionMassFlowKgPerHour,
        convertedProductMassFlowKgPerHour, lightGasMassFlowKgPerHour, naphthaMassFlowKgPerHour,
        middleDistillateMassFlowKgPerHour, totalProductMassFlowKgPerHour, feedConversionBalanceResidualKgPerHour,
        convertedProductMassBalanceResidualKgPerHour, overallMassBalanceResidualKgPerHour);
  }

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

  /** @return caller-owned hydrocarbon conversion fraction */
  public double getConversionFraction() {
    return conversionFraction;
  }

  /** @return consumed hydrogen in kg/t hydrocarbon feed */
  public double getHydrogenConsumptionKgPerTonneFeed() {
    return hydrogenConsumptionKgPerTonneFeed;
  }

  /** @return light-gas fraction of converted feed plus consumed hydrogen */
  public double getLightGasMassFractionOfConvertedProduct() {
    return lightGasMassFractionOfConvertedProduct;
  }

  /** @return naphtha fraction of converted feed plus consumed hydrogen */
  public double getNaphthaMassFractionOfConvertedProduct() {
    return naphthaMassFractionOfConvertedProduct;
  }

  /** @return middle-distillate fraction of converted feed plus consumed hydrogen */
  public double getMiddleDistillateMassFractionOfConvertedProduct() {
    return middleDistillateMassFractionOfConvertedProduct;
  }

  /** @return converted hydrocarbon feed in kg/h */
  public double getConvertedFeedMassFlowKgPerHour() {
    return convertedFeedMassFlowKgPerHour;
  }

  /** @return unconverted hydrocarbon product in kg/h */
  public double getUnconvertedOilMassFlowKgPerHour() {
    return unconvertedOilMassFlowKgPerHour;
  }

  /** @return consumed hydrogen mass flow in kg/h */
  public double getHydrogenConsumptionMassFlowKgPerHour() {
    return hydrogenConsumptionMassFlowKgPerHour;
  }

  /** @return converted feed plus consumed hydrogen in kg/h */
  public double getConvertedProductMassFlowKgPerHour() {
    return convertedProductMassFlowKgPerHour;
  }

  /** @return light-gas product in kg/h */
  public double getLightGasMassFlowKgPerHour() {
    return lightGasMassFlowKgPerHour;
  }

  /** @return naphtha product in kg/h */
  public double getNaphthaMassFlowKgPerHour() {
    return naphthaMassFlowKgPerHour;
  }

  /** @return middle-distillate product in kg/h */
  public double getMiddleDistillateMassFlowKgPerHour() {
    return middleDistillateMassFlowKgPerHour;
  }

  /** @return all represented hydrocracking products in kg/h */
  public double getTotalProductMassFlowKgPerHour() {
    return totalProductMassFlowKgPerHour;
  }

  /** @return feed-minus-converted-minus-unconverted residual in kg/h */
  public double getFeedConversionBalanceResidualKgPerHour() {
    return feedConversionBalanceResidualKgPerHour;
  }

  /** @return converted-feed-plus-hydrogen-minus-lumped-products residual in kg/h */
  public double getConvertedProductMassBalanceResidualKgPerHour() {
    return convertedProductMassBalanceResidualKgPerHour;
  }

  /** @return feed-plus-hydrogen-minus-all-products residual in kg/h */
  public double getOverallMassBalanceResidualKgPerHour() {
    return overallMassBalanceResidualKgPerHour;
  }
}
