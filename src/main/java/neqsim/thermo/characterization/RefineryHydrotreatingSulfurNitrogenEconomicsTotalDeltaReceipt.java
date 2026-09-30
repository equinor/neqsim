package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable symmetric total-delta receipt for qualified hydrotreating screening economics.
 *
 * <p>
 * The receipt compares arbitrary qualified baseline and candidate screening cases that may differ
 * in both physical flows and caller-owned prices. Bilinear value and feed-cost changes are split
 * symmetrically with midpoint identities, while the qualified operating-cost change remains one
 * aggregate contribution. The allocation is exact bookkeeping, not a causal model.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingSulfurNitrogenEconomicsTotalDeltaReceipt
    implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double KILOGRAMS_PER_TONNE = 1000.0;
  private static final double CLOSURE_TOLERANCE = 1.0e-9;

  private final RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baselineReceipt;
  private final RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt candidateReceipt;
  private final double feedMassFlowDeltaTonnesPerHour;
  private final double liquidProductMassFlowDeltaTonnesPerHour;
  private final double exportGasMassFlowDeltaTonnesPerHour;
  private final double liquidProductPriceDeltaPerTonne;
  private final double exportGasPriceDeltaPerTonne;
  private final double feedCostDeltaPerTonne;
  private final double liquidProductFlowMarginContributionPerHour;
  private final double liquidProductPriceMarginContributionPerHour;
  private final double exportGasFlowMarginContributionPerHour;
  private final double exportGasPriceMarginContributionPerHour;
  private final double feedFlowMarginContributionPerHour;
  private final double feedCostMarginContributionPerHour;
  private final double operatingCostMarginContributionPerHour;
  private final double attributedMarginDeltaPerHour;
  private final double totalProductValueDeltaPerHour;
  private final double totalVariableCostDeltaPerHour;
  private final double screeningMarginDeltaPerHour;
  private final double productValueDeltaClosureResidualPerHour;
  private final double variableCostDeltaClosureResidualPerHour;
  private final double screeningMarginDeltaClosureResidualPerHour;
  private final double totalAttributionClosureResidualPerHour;

  private RefineryHydrotreatingSulfurNitrogenEconomicsTotalDeltaReceipt(
      RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baselineReceipt,
      RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt candidateReceipt,
      double feedMassFlowDeltaTonnesPerHour,
      double liquidProductMassFlowDeltaTonnesPerHour,
      double exportGasMassFlowDeltaTonnesPerHour, double liquidProductPriceDeltaPerTonne,
      double exportGasPriceDeltaPerTonne, double feedCostDeltaPerTonne,
      double liquidProductFlowMarginContributionPerHour,
      double liquidProductPriceMarginContributionPerHour,
      double exportGasFlowMarginContributionPerHour,
      double exportGasPriceMarginContributionPerHour, double feedFlowMarginContributionPerHour,
      double feedCostMarginContributionPerHour, double operatingCostMarginContributionPerHour,
      double attributedMarginDeltaPerHour, double totalProductValueDeltaPerHour,
      double totalVariableCostDeltaPerHour, double screeningMarginDeltaPerHour,
      double productValueDeltaClosureResidualPerHour,
      double variableCostDeltaClosureResidualPerHour,
      double screeningMarginDeltaClosureResidualPerHour,
      double totalAttributionClosureResidualPerHour) {
    this.baselineReceipt = baselineReceipt;
    this.candidateReceipt = candidateReceipt;
    this.feedMassFlowDeltaTonnesPerHour = feedMassFlowDeltaTonnesPerHour;
    this.liquidProductMassFlowDeltaTonnesPerHour = liquidProductMassFlowDeltaTonnesPerHour;
    this.exportGasMassFlowDeltaTonnesPerHour = exportGasMassFlowDeltaTonnesPerHour;
    this.liquidProductPriceDeltaPerTonne = liquidProductPriceDeltaPerTonne;
    this.exportGasPriceDeltaPerTonne = exportGasPriceDeltaPerTonne;
    this.feedCostDeltaPerTonne = feedCostDeltaPerTonne;
    this.liquidProductFlowMarginContributionPerHour =
        liquidProductFlowMarginContributionPerHour;
    this.liquidProductPriceMarginContributionPerHour =
        liquidProductPriceMarginContributionPerHour;
    this.exportGasFlowMarginContributionPerHour = exportGasFlowMarginContributionPerHour;
    this.exportGasPriceMarginContributionPerHour = exportGasPriceMarginContributionPerHour;
    this.feedFlowMarginContributionPerHour = feedFlowMarginContributionPerHour;
    this.feedCostMarginContributionPerHour = feedCostMarginContributionPerHour;
    this.operatingCostMarginContributionPerHour = operatingCostMarginContributionPerHour;
    this.attributedMarginDeltaPerHour = attributedMarginDeltaPerHour;
    this.totalProductValueDeltaPerHour = totalProductValueDeltaPerHour;
    this.totalVariableCostDeltaPerHour = totalVariableCostDeltaPerHour;
    this.screeningMarginDeltaPerHour = screeningMarginDeltaPerHour;
    this.productValueDeltaClosureResidualPerHour =
        productValueDeltaClosureResidualPerHour;
    this.variableCostDeltaClosureResidualPerHour =
        variableCostDeltaClosureResidualPerHour;
    this.screeningMarginDeltaClosureResidualPerHour =
        screeningMarginDeltaClosureResidualPerHour;
    this.totalAttributionClosureResidualPerHour = totalAttributionClosureResidualPerHour;
  }

  /**
   * Compare arbitrary qualified physical cases and caller-owned screening scenarios.
   *
   * @param baselineReceipt baseline qualified screening-economics receipt
   * @param candidateReceipt candidate qualified screening-economics receipt
   * @return immutable symmetric total economics-delta receipt
   * @throws NullPointerException if either receipt is missing
   * @throws IllegalArgumentException if the derived attribution is non-finite or does not close
   */
  public static RefineryHydrotreatingSulfurNitrogenEconomicsTotalDeltaReceipt calculate(
      RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baselineReceipt,
      RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt candidateReceipt) {
    Objects.requireNonNull(baselineReceipt, "baselineReceipt");
    Objects.requireNonNull(candidateReceipt, "candidateReceipt");

    RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt baselineIntensity =
        baselineReceipt.getNetProductIntensityReceipt();
    RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt candidateIntensity =
        candidateReceipt.getNetProductIntensityReceipt();
    RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt baselineDistribution =
        baselineIntensity.getProductDistributionReceipt();
    RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt candidateDistribution =
        candidateIntensity.getProductDistributionReceipt();

    double baselineFeed = baselineIntensity.getFeedMassFlowKgPerHour() / KILOGRAMS_PER_TONNE;
    double candidateFeed = candidateIntensity.getFeedMassFlowKgPerHour() / KILOGRAMS_PER_TONNE;
    double baselineLiquid =
        baselineIntensity.getLiquidProductMassFlowKgPerHour() / KILOGRAMS_PER_TONNE;
    double candidateLiquid =
        candidateIntensity.getLiquidProductMassFlowKgPerHour() / KILOGRAMS_PER_TONNE;
    double baselineGas = baselineDistribution.getThroughputBalance()
        .getExportGasMassFlowKgPerHour() / KILOGRAMS_PER_TONNE;
    double candidateGas = candidateDistribution.getThroughputBalance()
        .getExportGasMassFlowKgPerHour() / KILOGRAMS_PER_TONNE;

    double feedDelta = candidateFeed - baselineFeed;
    double liquidDelta = candidateLiquid - baselineLiquid;
    double gasDelta = candidateGas - baselineGas;
    double liquidPriceDelta = candidateReceipt.getLiquidProductPricePerTonne()
        - baselineReceipt.getLiquidProductPricePerTonne();
    double gasPriceDelta = candidateReceipt.getExportGasPricePerTonne()
        - baselineReceipt.getExportGasPricePerTonne();
    double feedCostDelta =
        candidateReceipt.getFeedCostPerTonne() - baselineReceipt.getFeedCostPerTonne();

    double liquidFlowContribution = liquidDelta * average(
        baselineReceipt.getLiquidProductPricePerTonne(),
        candidateReceipt.getLiquidProductPricePerTonne());
    double liquidPriceContribution = liquidPriceDelta * average(baselineLiquid, candidateLiquid);
    double gasFlowContribution = gasDelta * average(baselineReceipt.getExportGasPricePerTonne(),
        candidateReceipt.getExportGasPricePerTonne());
    double gasPriceContribution = gasPriceDelta * average(baselineGas, candidateGas);
    double feedFlowContribution = -feedDelta * average(baselineReceipt.getFeedCostPerTonne(),
        candidateReceipt.getFeedCostPerTonne());
    double feedCostContribution = -feedCostDelta * average(baselineFeed, candidateFeed);
    double operatingCostContribution =
        -(candidateReceipt.getOperatingCostPerHour() - baselineReceipt.getOperatingCostPerHour());
    double attributedMarginDelta = liquidFlowContribution + liquidPriceContribution
        + gasFlowContribution + gasPriceContribution + feedFlowContribution
        + feedCostContribution + operatingCostContribution;

    double totalProductValueDelta = candidateReceipt.getTotalProductValuePerHour()
        - baselineReceipt.getTotalProductValuePerHour();
    double totalVariableCostDelta = candidateReceipt.getTotalVariableCostPerHour()
        - baselineReceipt.getTotalVariableCostPerHour();
    double screeningMarginDelta = candidateReceipt.getScreeningMarginPerHour()
        - baselineReceipt.getScreeningMarginPerHour();
    double productValueResidual = totalProductValueDelta - liquidFlowContribution
        - liquidPriceContribution - gasFlowContribution - gasPriceContribution;
    double variableCostResidual = totalVariableCostDelta + feedFlowContribution
        + feedCostContribution + operatingCostContribution;
    double screeningMarginResidual =
        screeningMarginDelta - totalProductValueDelta + totalVariableCostDelta;
    double attributionResidual = screeningMarginDelta - attributedMarginDelta;

    if (!allFinite(feedDelta, liquidDelta, gasDelta, liquidPriceDelta, gasPriceDelta,
        feedCostDelta, liquidFlowContribution, liquidPriceContribution, gasFlowContribution,
        gasPriceContribution, feedFlowContribution, feedCostContribution,
        operatingCostContribution, attributedMarginDelta, totalProductValueDelta,
        totalVariableCostDelta, screeningMarginDelta, productValueResidual,
        variableCostResidual, screeningMarginResidual, attributionResidual)
        || Math.abs(productValueResidual) > CLOSURE_TOLERANCE
        || Math.abs(variableCostResidual) > CLOSURE_TOLERANCE
        || Math.abs(screeningMarginResidual) > CLOSURE_TOLERANCE
        || Math.abs(attributionResidual) > CLOSURE_TOLERANCE) {
      throw new IllegalArgumentException(
          "inputs do not define a closed finite symmetric total economics delta");
    }

    return new RefineryHydrotreatingSulfurNitrogenEconomicsTotalDeltaReceipt(
        baselineReceipt, candidateReceipt, feedDelta, liquidDelta, gasDelta, liquidPriceDelta,
        gasPriceDelta, feedCostDelta, liquidFlowContribution, liquidPriceContribution,
        gasFlowContribution, gasPriceContribution, feedFlowContribution, feedCostContribution,
        operatingCostContribution, attributedMarginDelta, totalProductValueDelta,
        totalVariableCostDelta, screeningMarginDelta, productValueResidual,
        variableCostResidual, screeningMarginResidual, attributionResidual);
  }

  private static double average(double first, double second) {
    return first / 2.0 + second / 2.0;
  }

  private static boolean allFinite(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value)) {
        return false;
      }
    }
    return true;
  }

  /** @return baseline qualified screening-economics receipt */
  public RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt getBaselineReceipt() {
    return baselineReceipt;
  }

  /** @return candidate qualified screening-economics receipt */
  public RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt getCandidateReceipt() {
    return candidateReceipt;
  }

  /** @return candidate-minus-baseline feed flow in t/h */
  public double getFeedMassFlowDeltaTonnesPerHour() {
    return feedMassFlowDeltaTonnesPerHour;
  }

  /** @return candidate-minus-baseline liquid-product flow in t/h */
  public double getLiquidProductMassFlowDeltaTonnesPerHour() {
    return liquidProductMassFlowDeltaTonnesPerHour;
  }

  /** @return candidate-minus-baseline export-gas flow in t/h */
  public double getExportGasMassFlowDeltaTonnesPerHour() {
    return exportGasMassFlowDeltaTonnesPerHour;
  }

  /** @return candidate-minus-baseline liquid-product price in currency units/t */
  public double getLiquidProductPriceDeltaPerTonne() {
    return liquidProductPriceDeltaPerTonne;
  }

  /** @return candidate-minus-baseline export-gas price in currency units/t */
  public double getExportGasPriceDeltaPerTonne() {
    return exportGasPriceDeltaPerTonne;
  }

  /** @return candidate-minus-baseline feed cost in currency units/t */
  public double getFeedCostDeltaPerTonne() {
    return feedCostDeltaPerTonne;
  }

  /** @return symmetric liquid-product flow contribution in currency units/h */
  public double getLiquidProductFlowMarginContributionPerHour() {
    return liquidProductFlowMarginContributionPerHour;
  }

  /** @return symmetric liquid-product price contribution in currency units/h */
  public double getLiquidProductPriceMarginContributionPerHour() {
    return liquidProductPriceMarginContributionPerHour;
  }

  /** @return symmetric export-gas flow contribution in currency units/h */
  public double getExportGasFlowMarginContributionPerHour() {
    return exportGasFlowMarginContributionPerHour;
  }

  /** @return symmetric export-gas price contribution in currency units/h */
  public double getExportGasPriceMarginContributionPerHour() {
    return exportGasPriceMarginContributionPerHour;
  }

  /** @return symmetric feed-flow contribution in currency units/h */
  public double getFeedFlowMarginContributionPerHour() {
    return feedFlowMarginContributionPerHour;
  }

  /** @return symmetric feed-cost contribution in currency units/h */
  public double getFeedCostMarginContributionPerHour() {
    return feedCostMarginContributionPerHour;
  }

  /** @return aggregate qualified operating-cost contribution in currency units/h */
  public double getOperatingCostMarginContributionPerHour() {
    return operatingCostMarginContributionPerHour;
  }

  /** @return sum of all symmetric margin contributions in currency units/h */
  public double getAttributedMarginDeltaPerHour() {
    return attributedMarginDeltaPerHour;
  }

  /** @return candidate-minus-baseline product value in currency units/h */
  public double getTotalProductValueDeltaPerHour() {
    return totalProductValueDeltaPerHour;
  }

  /** @return candidate-minus-baseline variable cost in currency units/h */
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

  /** @return total symmetric-attribution closure residual in currency units/h */
  public double getTotalAttributionClosureResidualPerHour() {
    return totalAttributionClosureResidualPerHour;
  }
}
