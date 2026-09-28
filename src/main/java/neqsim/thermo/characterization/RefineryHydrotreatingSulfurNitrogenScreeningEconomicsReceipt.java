package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable caller-priced screening-economics receipt for a qualified coupled sulfur/nitrogen hydrotreating case.
 *
 * <p>
 * The receipt values qualified external liquid-product and export-gas mass flows and combines caller-owned feed cost
 * with the qualified net operating cost. It is deliberately a variable-cost screening calculation, not a product
 * quality, market forecast, lifecycle, capital, tax, financing, optimization, compliance, or investment model.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double KILOGRAMS_PER_TONNE = 1000.0;

  private final RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt netProductIntensityReceipt;
  private final double liquidProductPricePerTonne;
  private final double exportGasPricePerTonne;
  private final double feedCostPerTonne;
  private final double liquidProductValuePerHour;
  private final double exportGasValuePerHour;
  private final double totalProductValuePerHour;
  private final double feedCostPerHour;
  private final double operatingCostPerHour;
  private final double totalVariableCostPerHour;
  private final double screeningMarginPerHour;
  private final double totalProductValuePerTonneFeed;
  private final double totalVariableCostPerTonneFeed;
  private final double screeningMarginPerTonneFeed;
  private final double totalProductValuePerTonneLiquidProduct;
  private final double totalVariableCostPerTonneLiquidProduct;
  private final double screeningMarginPerTonneLiquidProduct;
  private final double productValueClosureResidualPerHour;
  private final double variableCostClosureResidualPerHour;
  private final double screeningMarginClosureResidualPerHour;

  private RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt(
      RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt netProductIntensityReceipt,
      double liquidProductPricePerTonne, double exportGasPricePerTonne, double feedCostPerTonne,
      double liquidProductValuePerHour, double exportGasValuePerHour, double totalProductValuePerHour,
      double feedCostPerHour, double operatingCostPerHour, double totalVariableCostPerHour,
      double screeningMarginPerHour, double totalProductValuePerTonneFeed, double totalVariableCostPerTonneFeed,
      double screeningMarginPerTonneFeed, double totalProductValuePerTonneLiquidProduct,
      double totalVariableCostPerTonneLiquidProduct, double screeningMarginPerTonneLiquidProduct,
      double productValueClosureResidualPerHour, double variableCostClosureResidualPerHour,
      double screeningMarginClosureResidualPerHour) {
    this.netProductIntensityReceipt = netProductIntensityReceipt;
    this.liquidProductPricePerTonne = liquidProductPricePerTonne;
    this.exportGasPricePerTonne = exportGasPricePerTonne;
    this.feedCostPerTonne = feedCostPerTonne;
    this.liquidProductValuePerHour = liquidProductValuePerHour;
    this.exportGasValuePerHour = exportGasValuePerHour;
    this.totalProductValuePerHour = totalProductValuePerHour;
    this.feedCostPerHour = feedCostPerHour;
    this.operatingCostPerHour = operatingCostPerHour;
    this.totalVariableCostPerHour = totalVariableCostPerHour;
    this.screeningMarginPerHour = screeningMarginPerHour;
    this.totalProductValuePerTonneFeed = totalProductValuePerTonneFeed;
    this.totalVariableCostPerTonneFeed = totalVariableCostPerTonneFeed;
    this.screeningMarginPerTonneFeed = screeningMarginPerTonneFeed;
    this.totalProductValuePerTonneLiquidProduct = totalProductValuePerTonneLiquidProduct;
    this.totalVariableCostPerTonneLiquidProduct = totalVariableCostPerTonneLiquidProduct;
    this.screeningMarginPerTonneLiquidProduct = screeningMarginPerTonneLiquidProduct;
    this.productValueClosureResidualPerHour = productValueClosureResidualPerHour;
    this.variableCostClosureResidualPerHour = variableCostClosureResidualPerHour;
    this.screeningMarginClosureResidualPerHour = screeningMarginClosureResidualPerHour;
  }

  /**
   * Calculate a caller-priced variable-cost screening receipt.
   *
   * @param netProductIntensityReceipt qualified upstream net product-intensity receipt
   * @param liquidProductPricePerTonne caller-owned liquid-product price in currency units/t product
   * @param exportGasPricePerTonne caller-owned export-gas price in currency units/t gas
   * @param feedCostPerTonne caller-owned liquid-feed cost in currency units/t feed
   * @return immutable screening-economics receipt
   */
  public static RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt calculate(
      RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt netProductIntensityReceipt,
      double liquidProductPricePerTonne, double exportGasPricePerTonne, double feedCostPerTonne) {
    Objects.requireNonNull(netProductIntensityReceipt, "netProductIntensityReceipt");
    if (!allFiniteNonNegative(liquidProductPricePerTonne, exportGasPricePerTonne, feedCostPerTonne)) {
      throw new IllegalArgumentException("caller-owned prices and feed cost must be finite and non-negative");
    }

    RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt distribution = netProductIntensityReceipt
        .getProductDistributionReceipt();
    double feedTonnesPerHour = netProductIntensityReceipt.getFeedMassFlowKgPerHour() / KILOGRAMS_PER_TONNE;
    double liquidProductTonnesPerHour = netProductIntensityReceipt.getLiquidProductMassFlowKgPerHour()
        / KILOGRAMS_PER_TONNE;
    double exportGasTonnesPerHour = distribution.getThroughputBalance().getExportGasMassFlowKgPerHour()
        / KILOGRAMS_PER_TONNE;
    double liquidProductValue = liquidProductTonnesPerHour * liquidProductPricePerTonne;
    double exportGasValue = exportGasTonnesPerHour * exportGasPricePerTonne;
    double totalProductValue = liquidProductValue + exportGasValue;
    double feedCost = feedTonnesPerHour * feedCostPerTonne;
    double operatingCost = netProductIntensityReceipt.getNetOperatingReceipt().getTotalOperatingCostPerHour();
    double totalVariableCost = feedCost + operatingCost;
    double screeningMargin = totalProductValue - totalVariableCost;
    double productValuePerTonneFeed = totalProductValue / feedTonnesPerHour;
    double variableCostPerTonneFeed = totalVariableCost / feedTonnesPerHour;
    double screeningMarginPerTonneFeed = screeningMargin / feedTonnesPerHour;
    double productValuePerTonneProduct = totalProductValue / liquidProductTonnesPerHour;
    double variableCostPerTonneProduct = totalVariableCost / liquidProductTonnesPerHour;
    double screeningMarginPerTonneProduct = screeningMargin / liquidProductTonnesPerHour;
    double productValueResidual = totalProductValue - liquidProductValue - exportGasValue;
    double variableCostResidual = totalVariableCost - feedCost - operatingCost;
    double screeningMarginResidual = screeningMargin - totalProductValue + totalVariableCost;

    if (!allFiniteNonNegative(feedTonnesPerHour, liquidProductTonnesPerHour, exportGasTonnesPerHour,
        liquidProductValue, exportGasValue, totalProductValue, feedCost, operatingCost, totalVariableCost,
        productValuePerTonneFeed, variableCostPerTonneFeed, productValuePerTonneProduct,
        variableCostPerTonneProduct)
        || !allFinite(screeningMargin, screeningMarginPerTonneFeed, screeningMarginPerTonneProduct)
        || Math.abs(productValueResidual) > 1.0e-9 || Math.abs(variableCostResidual) > 1.0e-9
        || Math.abs(screeningMarginResidual) > 1.0e-9) {
      throw new IllegalArgumentException("inputs do not define a closed finite screening-economics receipt");
    }

    return new RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt(netProductIntensityReceipt,
        liquidProductPricePerTonne, exportGasPricePerTonne, feedCostPerTonne, liquidProductValue, exportGasValue,
        totalProductValue, feedCost, operatingCost, totalVariableCost, screeningMargin, productValuePerTonneFeed,
        variableCostPerTonneFeed, screeningMarginPerTonneFeed, productValuePerTonneProduct,
        variableCostPerTonneProduct, screeningMarginPerTonneProduct, productValueResidual, variableCostResidual,
        screeningMarginResidual);
  }

  private static boolean allFiniteNonNegative(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value) || value < 0.0) {
        return false;
      }
    }
    return true;
  }

  private static boolean allFinite(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value)) {
        return false;
      }
    }
    return true;
  }

  /** @return qualified upstream net product-intensity receipt */
  public RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt getNetProductIntensityReceipt() {
    return netProductIntensityReceipt;
  }

  /** @return caller-owned liquid-product price in currency units/t product */
  public double getLiquidProductPricePerTonne() {
    return liquidProductPricePerTonne;
  }

  /** @return caller-owned export-gas price in currency units/t gas */
  public double getExportGasPricePerTonne() {
    return exportGasPricePerTonne;
  }

  /** @return caller-owned feed cost in currency units/t feed */
  public double getFeedCostPerTonne() {
    return feedCostPerTonne;
  }

  /** @return liquid-product value in currency units/h */
  public double getLiquidProductValuePerHour() {
    return liquidProductValuePerHour;
  }

  /** @return export-gas value in currency units/h */
  public double getExportGasValuePerHour() {
    return exportGasValuePerHour;
  }

  /** @return liquid-product plus export-gas value in currency units/h */
  public double getTotalProductValuePerHour() {
    return totalProductValuePerHour;
  }

  /** @return caller-owned feed cost in currency units/h */
  public double getFeedCostPerHour() {
    return feedCostPerHour;
  }

  /** @return qualified net operating cost in currency units/h */
  public double getOperatingCostPerHour() {
    return operatingCostPerHour;
  }

  /** @return feed plus qualified net operating cost in currency units/h */
  public double getTotalVariableCostPerHour() {
    return totalVariableCostPerHour;
  }

  /** @return total product value minus total variable cost in currency units/h */
  public double getScreeningMarginPerHour() {
    return screeningMarginPerHour;
  }

  /** @return total product value in currency units/t feed */
  public double getTotalProductValuePerTonneFeed() {
    return totalProductValuePerTonneFeed;
  }

  /** @return total variable cost in currency units/t feed */
  public double getTotalVariableCostPerTonneFeed() {
    return totalVariableCostPerTonneFeed;
  }

  /** @return screening margin in currency units/t feed */
  public double getScreeningMarginPerTonneFeed() {
    return screeningMarginPerTonneFeed;
  }

  /** @return total product value in currency units/t liquid product */
  public double getTotalProductValuePerTonneLiquidProduct() {
    return totalProductValuePerTonneLiquidProduct;
  }

  /** @return total variable cost in currency units/t liquid product */
  public double getTotalVariableCostPerTonneLiquidProduct() {
    return totalVariableCostPerTonneLiquidProduct;
  }

  /** @return screening margin in currency units/t liquid product */
  public double getScreeningMarginPerTonneLiquidProduct() {
    return screeningMarginPerTonneLiquidProduct;
  }

  /** @return total value minus liquid-product and export-gas value terms in currency units/h */
  public double getProductValueClosureResidualPerHour() {
    return productValueClosureResidualPerHour;
  }

  /** @return total variable cost minus feed and operating cost terms in currency units/h */
  public double getVariableCostClosureResidualPerHour() {
    return variableCostClosureResidualPerHour;
  }

  /** @return margin minus product value plus variable cost in currency units/h */
  public double getScreeningMarginClosureResidualPerHour() {
    return screeningMarginClosureResidualPerHour;
  }
}
