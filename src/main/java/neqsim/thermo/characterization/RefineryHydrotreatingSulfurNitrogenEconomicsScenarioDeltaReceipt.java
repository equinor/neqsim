package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable same-physical-case scenario-delta receipt for qualified hydrotreating screening economics.
 *
 * <p>
 * The receipt compares two caller-priced screening scenarios that share one qualified upstream physical receipt. It
 * attributes the screening-margin change to liquid-product price, export-gas price, and feed cost without embedding
 * prices, forecasts, optimization, or investment semantics.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingSulfurNitrogenEconomicsScenarioDeltaReceipt implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double KILOGRAMS_PER_TONNE = 1000.0;
  private static final double CLOSURE_TOLERANCE = 1.0e-9;

  private final RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baselineReceipt;
  private final RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt candidateReceipt;
  private final double liquidProductPriceDeltaPerTonne;
  private final double exportGasPriceDeltaPerTonne;
  private final double feedCostDeltaPerTonne;
  private final double liquidProductMarginContributionPerHour;
  private final double exportGasMarginContributionPerHour;
  private final double feedCostMarginContributionPerHour;
  private final double attributedMarginDeltaPerHour;
  private final double totalProductValueDeltaPerHour;
  private final double totalVariableCostDeltaPerHour;
  private final double screeningMarginDeltaPerHour;
  private final double productValueDeltaClosureResidualPerHour;
  private final double variableCostDeltaClosureResidualPerHour;
  private final double screeningMarginDeltaClosureResidualPerHour;
  private final double priceAttributionClosureResidualPerHour;

  /**
   * Construct an immutable same-physical-case scenario-delta receipt.
   *
   * @param baselineReceipt baseline caller-priced screening receipt
   * @param candidateReceipt candidate caller-priced screening receipt
   * @param liquidProductPriceDeltaPerTonne candidate-minus-baseline liquid-product price
   * @param exportGasPriceDeltaPerTonne candidate-minus-baseline export-gas price
   * @param feedCostDeltaPerTonne candidate-minus-baseline feed cost
   * @param liquidProductMarginContributionPerHour liquid-product contribution to margin change
   * @param exportGasMarginContributionPerHour export-gas contribution to margin change
   * @param feedCostMarginContributionPerHour feed-cost contribution to margin change
   * @param attributedMarginDeltaPerHour sum of the three attributed margin contributions
   * @param totalProductValueDeltaPerHour candidate-minus-baseline product value
   * @param totalVariableCostDeltaPerHour candidate-minus-baseline variable cost
   * @param screeningMarginDeltaPerHour candidate-minus-baseline screening margin
   * @param productValueDeltaClosureResidualPerHour product-value delta closure residual
   * @param variableCostDeltaClosureResidualPerHour variable-cost delta closure residual
   * @param screeningMarginDeltaClosureResidualPerHour screening-margin delta closure residual
   * @param priceAttributionClosureResidualPerHour price-attribution closure residual
   */
  private RefineryHydrotreatingSulfurNitrogenEconomicsScenarioDeltaReceipt(
      RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baselineReceipt,
      RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt candidateReceipt,
      double liquidProductPriceDeltaPerTonne, double exportGasPriceDeltaPerTonne,
      double feedCostDeltaPerTonne, double liquidProductMarginContributionPerHour,
      double exportGasMarginContributionPerHour, double feedCostMarginContributionPerHour,
      double attributedMarginDeltaPerHour, double totalProductValueDeltaPerHour,
      double totalVariableCostDeltaPerHour, double screeningMarginDeltaPerHour,
      double productValueDeltaClosureResidualPerHour, double variableCostDeltaClosureResidualPerHour,
      double screeningMarginDeltaClosureResidualPerHour, double priceAttributionClosureResidualPerHour) {
    this.baselineReceipt = baselineReceipt;
    this.candidateReceipt = candidateReceipt;
    this.liquidProductPriceDeltaPerTonne = liquidProductPriceDeltaPerTonne;
    this.exportGasPriceDeltaPerTonne = exportGasPriceDeltaPerTonne;
    this.feedCostDeltaPerTonne = feedCostDeltaPerTonne;
    this.liquidProductMarginContributionPerHour = liquidProductMarginContributionPerHour;
    this.exportGasMarginContributionPerHour = exportGasMarginContributionPerHour;
    this.feedCostMarginContributionPerHour = feedCostMarginContributionPerHour;
    this.attributedMarginDeltaPerHour = attributedMarginDeltaPerHour;
    this.totalProductValueDeltaPerHour = totalProductValueDeltaPerHour;
    this.totalVariableCostDeltaPerHour = totalVariableCostDeltaPerHour;
    this.screeningMarginDeltaPerHour = screeningMarginDeltaPerHour;
    this.productValueDeltaClosureResidualPerHour = productValueDeltaClosureResidualPerHour;
    this.variableCostDeltaClosureResidualPerHour = variableCostDeltaClosureResidualPerHour;
    this.screeningMarginDeltaClosureResidualPerHour = screeningMarginDeltaClosureResidualPerHour;
    this.priceAttributionClosureResidualPerHour = priceAttributionClosureResidualPerHour;
  }

  /**
   * Compare two caller-priced scenarios over the same qualified physical evidence.
   *
   * @param baselineReceipt baseline caller-priced screening receipt
   * @param candidateReceipt candidate caller-priced screening receipt
   * @return immutable scenario-delta and exact price-attribution receipt
   * @throws NullPointerException if either receipt is missing
   * @throws IllegalArgumentException if the receipts do not share the same qualified upstream receipt or fail closure
   */
  public static RefineryHydrotreatingSulfurNitrogenEconomicsScenarioDeltaReceipt calculate(
      RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baselineReceipt,
      RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt candidateReceipt) {
    Objects.requireNonNull(baselineReceipt, "baselineReceipt");
    Objects.requireNonNull(candidateReceipt, "candidateReceipt");
    if (baselineReceipt.getNetProductIntensityReceipt() != candidateReceipt.getNetProductIntensityReceipt()) {
      throw new IllegalArgumentException("baseline and candidate must share the same qualified upstream receipt");
    }

    RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt intensity = baselineReceipt
        .getNetProductIntensityReceipt();
    RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt distribution = intensity
        .getProductDistributionReceipt();
    double feedRate = intensity.getFeedMassFlowKgPerHour() / KILOGRAMS_PER_TONNE;
    double liquidProductRate = intensity.getLiquidProductMassFlowKgPerHour() / KILOGRAMS_PER_TONNE;
    double exportGasRate = distribution.getThroughputBalance().getExportGasMassFlowKgPerHour()
        / KILOGRAMS_PER_TONNE;

    double liquidProductPriceDelta =
        candidateReceipt.getLiquidProductPricePerTonne() - baselineReceipt.getLiquidProductPricePerTonne();
    double exportGasPriceDelta =
        candidateReceipt.getExportGasPricePerTonne() - baselineReceipt.getExportGasPricePerTonne();
    double feedCostDelta = candidateReceipt.getFeedCostPerTonne() - baselineReceipt.getFeedCostPerTonne();
    double liquidProductContribution = liquidProductRate * liquidProductPriceDelta;
    double exportGasContribution = exportGasRate * exportGasPriceDelta;
    double feedCostContribution = -feedRate * feedCostDelta;
    double attributedMarginDelta = liquidProductContribution + exportGasContribution + feedCostContribution;
    double totalProductValueDelta =
        candidateReceipt.getTotalProductValuePerHour() - baselineReceipt.getTotalProductValuePerHour();
    double totalVariableCostDelta =
        candidateReceipt.getTotalVariableCostPerHour() - baselineReceipt.getTotalVariableCostPerHour();
    double screeningMarginDelta =
        candidateReceipt.getScreeningMarginPerHour() - baselineReceipt.getScreeningMarginPerHour();
    double feedCostPerHourDelta = candidateReceipt.getFeedCostPerHour() - baselineReceipt.getFeedCostPerHour();
    double liquidProductValueDelta =
        candidateReceipt.getLiquidProductValuePerHour() - baselineReceipt.getLiquidProductValuePerHour();
    double exportGasValueDelta =
        candidateReceipt.getExportGasValuePerHour() - baselineReceipt.getExportGasValuePerHour();
    double productValueResidual = totalProductValueDelta - liquidProductValueDelta - exportGasValueDelta;
    double variableCostResidual = totalVariableCostDelta - feedCostPerHourDelta;
    double screeningMarginResidual = screeningMarginDelta - totalProductValueDelta + totalVariableCostDelta;
    double attributionResidual = screeningMarginDelta - attributedMarginDelta;

    if (!allFinite(feedRate, liquidProductRate, exportGasRate, liquidProductPriceDelta, exportGasPriceDelta,
        feedCostDelta, liquidProductContribution, exportGasContribution, feedCostContribution,
        attributedMarginDelta, totalProductValueDelta, totalVariableCostDelta, screeningMarginDelta,
        productValueResidual, variableCostResidual, screeningMarginResidual, attributionResidual)
        || Math.abs(productValueResidual) > CLOSURE_TOLERANCE
        || Math.abs(variableCostResidual) > CLOSURE_TOLERANCE
        || Math.abs(screeningMarginResidual) > CLOSURE_TOLERANCE
        || Math.abs(attributionResidual) > CLOSURE_TOLERANCE) {
      throw new IllegalArgumentException("scenarios do not define a closed finite economics delta receipt");
    }

    return new RefineryHydrotreatingSulfurNitrogenEconomicsScenarioDeltaReceipt(baselineReceipt, candidateReceipt,
        liquidProductPriceDelta, exportGasPriceDelta, feedCostDelta, liquidProductContribution,
        exportGasContribution, feedCostContribution, attributedMarginDelta, totalProductValueDelta,
        totalVariableCostDelta, screeningMarginDelta, productValueResidual, variableCostResidual,
        screeningMarginResidual, attributionResidual);
  }

  /**
   * Check that every value is finite.
   *
   * @param values values to inspect
   * @return true when every value is finite
   */
  private static boolean allFinite(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value)) {
        return false;
      }
    }
    return true;
  }

  /** @return baseline caller-priced screening receipt */
  public RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt getBaselineReceipt() {
    return baselineReceipt;
  }

  /** @return candidate caller-priced screening receipt */
  public RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt getCandidateReceipt() {
    return candidateReceipt;
  }

  /** @return candidate-minus-baseline liquid-product price in currency units/t product */
  public double getLiquidProductPriceDeltaPerTonne() {
    return liquidProductPriceDeltaPerTonne;
  }

  /** @return candidate-minus-baseline export-gas price in currency units/t gas */
  public double getExportGasPriceDeltaPerTonne() {
    return exportGasPriceDeltaPerTonne;
  }

  /** @return candidate-minus-baseline feed cost in currency units/t feed */
  public double getFeedCostDeltaPerTonne() {
    return feedCostDeltaPerTonne;
  }

  /** @return liquid-product price contribution to margin change in currency units/h */
  public double getLiquidProductMarginContributionPerHour() {
    return liquidProductMarginContributionPerHour;
  }

  /** @return export-gas price contribution to margin change in currency units/h */
  public double getExportGasMarginContributionPerHour() {
    return exportGasMarginContributionPerHour;
  }

  /** @return feed-cost contribution to margin change in currency units/h */
  public double getFeedCostMarginContributionPerHour() {
    return feedCostMarginContributionPerHour;
  }

  /** @return sum of attributed price and feed-cost contributions in currency units/h */
  public double getAttributedMarginDeltaPerHour() {
    return attributedMarginDeltaPerHour;
  }

  /** @return candidate-minus-baseline total product value in currency units/h */
  public double getTotalProductValueDeltaPerHour() {
    return totalProductValueDeltaPerHour;
  }

  /** @return candidate-minus-baseline total variable cost in currency units/h */
  public double getTotalVariableCostDeltaPerHour() {
    return totalVariableCostDeltaPerHour;
  }

  /** @return candidate-minus-baseline screening margin in currency units/h */
  public double getScreeningMarginDeltaPerHour() {
    return screeningMarginDeltaPerHour;
  }

  /** @return product-value delta closure residual in currency units/h */
  public double getProductValueDeltaClosureResidualPerHour() {
    return productValueDeltaClosureResidualPerHour;
  }

  /** @return variable-cost delta closure residual in currency units/h */
  public double getVariableCostDeltaClosureResidualPerHour() {
    return variableCostDeltaClosureResidualPerHour;
  }

  /** @return screening-margin delta closure residual in currency units/h */
  public double getScreeningMarginDeltaClosureResidualPerHour() {
    return screeningMarginDeltaClosureResidualPerHour;
  }

  /** @return price-attribution closure residual in currency units/h */
  public double getPriceAttributionClosureResidualPerHour() {
    return priceAttributionClosureResidualPerHour;
  }
}
