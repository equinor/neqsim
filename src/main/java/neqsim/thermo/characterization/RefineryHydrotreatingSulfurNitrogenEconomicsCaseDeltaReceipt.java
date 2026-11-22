package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable fixed-screening-price case-delta receipt for qualified hydrotreating economics.
 *
 * <p>
 * The receipt compares two qualified upstream cases under identical caller-owned liquid-product price, export-gas
 * price, and feed cost. It attributes the screening-margin change to external liquid-product flow, export-gas flow,
 * feed flow, and the qualified aggregate operating-cost change. It does not infer causality inside the upstream
 * operating-cost receipt or embed prices, forecasts, optimization, or investment semantics.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingSulfurNitrogenEconomicsCaseDeltaReceipt implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double KILOGRAMS_PER_TONNE = 1000.0;
  private static final double CLOSURE_TOLERANCE = 1.0e-9;

  private final RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baselineReceipt;
  private final RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt candidateReceipt;
  private final double feedMassFlowDeltaTonnesPerHour;
  private final double liquidProductMassFlowDeltaTonnesPerHour;
  private final double exportGasMassFlowDeltaTonnesPerHour;
  private final double liquidProductFlowMarginContributionPerHour;
  private final double exportGasFlowMarginContributionPerHour;
  private final double feedFlowMarginContributionPerHour;
  private final double operatingCostMarginContributionPerHour;
  private final double attributedMarginDeltaPerHour;
  private final double totalProductValueDeltaPerHour;
  private final double totalVariableCostDeltaPerHour;
  private final double screeningMarginDeltaPerHour;
  private final double productValueDeltaClosureResidualPerHour;
  private final double variableCostDeltaClosureResidualPerHour;
  private final double screeningMarginDeltaClosureResidualPerHour;
  private final double caseAttributionClosureResidualPerHour;

  private RefineryHydrotreatingSulfurNitrogenEconomicsCaseDeltaReceipt(
      RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baselineReceipt,
      RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt candidateReceipt,
      double feedMassFlowDeltaTonnesPerHour, double liquidProductMassFlowDeltaTonnesPerHour,
      double exportGasMassFlowDeltaTonnesPerHour, double liquidProductFlowMarginContributionPerHour,
      double exportGasFlowMarginContributionPerHour, double feedFlowMarginContributionPerHour,
      double operatingCostMarginContributionPerHour, double attributedMarginDeltaPerHour,
      double totalProductValueDeltaPerHour, double totalVariableCostDeltaPerHour, double screeningMarginDeltaPerHour,
      double productValueDeltaClosureResidualPerHour, double variableCostDeltaClosureResidualPerHour,
      double screeningMarginDeltaClosureResidualPerHour, double caseAttributionClosureResidualPerHour) {
    this.baselineReceipt = baselineReceipt;
    this.candidateReceipt = candidateReceipt;
    this.feedMassFlowDeltaTonnesPerHour = feedMassFlowDeltaTonnesPerHour;
    this.liquidProductMassFlowDeltaTonnesPerHour = liquidProductMassFlowDeltaTonnesPerHour;
    this.exportGasMassFlowDeltaTonnesPerHour = exportGasMassFlowDeltaTonnesPerHour;
    this.liquidProductFlowMarginContributionPerHour = liquidProductFlowMarginContributionPerHour;
    this.exportGasFlowMarginContributionPerHour = exportGasFlowMarginContributionPerHour;
    this.feedFlowMarginContributionPerHour = feedFlowMarginContributionPerHour;
    this.operatingCostMarginContributionPerHour = operatingCostMarginContributionPerHour;
    this.attributedMarginDeltaPerHour = attributedMarginDeltaPerHour;
    this.totalProductValueDeltaPerHour = totalProductValueDeltaPerHour;
    this.totalVariableCostDeltaPerHour = totalVariableCostDeltaPerHour;
    this.screeningMarginDeltaPerHour = screeningMarginDeltaPerHour;
    this.productValueDeltaClosureResidualPerHour = productValueDeltaClosureResidualPerHour;
    this.variableCostDeltaClosureResidualPerHour = variableCostDeltaClosureResidualPerHour;
    this.screeningMarginDeltaClosureResidualPerHour = screeningMarginDeltaClosureResidualPerHour;
    this.caseAttributionClosureResidualPerHour = caseAttributionClosureResidualPerHour;
  }

  /**
   * Compare two qualified upstream cases under one caller-owned screening-price basis.
   *
   * @param baselineReceipt baseline qualified screening-economics receipt
   * @param candidateReceipt candidate qualified screening-economics receipt
   * @return immutable fixed-screening-price case-delta receipt
   * @throws NullPointerException if either receipt is missing
   * @throws IllegalArgumentException if caller-owned prices differ or the delta does not close
   */
  public static RefineryHydrotreatingSulfurNitrogenEconomicsCaseDeltaReceipt calculate(
      RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baselineReceipt,
      RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt candidateReceipt) {
    Objects.requireNonNull(baselineReceipt, "baselineReceipt");
    Objects.requireNonNull(candidateReceipt, "candidateReceipt");
    requireSameScreeningPrices(baselineReceipt, candidateReceipt);

    RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt baselineIntensity = baselineReceipt
        .getNetProductIntensityReceipt();
    RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt candidateIntensity = candidateReceipt
        .getNetProductIntensityReceipt();
    RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt baselineDistribution = baselineIntensity
        .getProductDistributionReceipt();
    RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt candidateDistribution = candidateIntensity
        .getProductDistributionReceipt();

    double feedDelta = (candidateIntensity.getFeedMassFlowKgPerHour() - baselineIntensity.getFeedMassFlowKgPerHour())
        / KILOGRAMS_PER_TONNE;
    double liquidProductDelta = (candidateIntensity.getLiquidProductMassFlowKgPerHour()
        - baselineIntensity.getLiquidProductMassFlowKgPerHour()) / KILOGRAMS_PER_TONNE;
    double exportGasDelta = (candidateDistribution.getThroughputBalance().getExportGasMassFlowKgPerHour()
        - baselineDistribution.getThroughputBalance().getExportGasMassFlowKgPerHour()) / KILOGRAMS_PER_TONNE;
    double liquidProductContribution = liquidProductDelta * baselineReceipt.getLiquidProductPricePerTonne();
    double exportGasContribution = exportGasDelta * baselineReceipt.getExportGasPricePerTonne();
    double feedContribution = -feedDelta * baselineReceipt.getFeedCostPerTonne();
    double operatingCostDelta = candidateReceipt.getOperatingCostPerHour() - baselineReceipt.getOperatingCostPerHour();
    double operatingCostContribution = -operatingCostDelta;
    double attributedMarginDelta = liquidProductContribution + exportGasContribution + feedContribution
        + operatingCostContribution;

    double totalProductValueDelta = candidateReceipt.getTotalProductValuePerHour()
        - baselineReceipt.getTotalProductValuePerHour();
    double totalVariableCostDelta = candidateReceipt.getTotalVariableCostPerHour()
        - baselineReceipt.getTotalVariableCostPerHour();
    double screeningMarginDelta = candidateReceipt.getScreeningMarginPerHour()
        - baselineReceipt.getScreeningMarginPerHour();
    double feedCostPerHourDelta = candidateReceipt.getFeedCostPerHour() - baselineReceipt.getFeedCostPerHour();
    double liquidProductValueDelta = candidateReceipt.getLiquidProductValuePerHour()
        - baselineReceipt.getLiquidProductValuePerHour();
    double exportGasValueDelta = candidateReceipt.getExportGasValuePerHour()
        - baselineReceipt.getExportGasValuePerHour();
    double productValueResidual = totalProductValueDelta - liquidProductValueDelta - exportGasValueDelta;
    double variableCostResidual = totalVariableCostDelta - feedCostPerHourDelta - operatingCostDelta;
    double screeningMarginResidual = screeningMarginDelta - totalProductValueDelta + totalVariableCostDelta;
    double attributionResidual = screeningMarginDelta - attributedMarginDelta;

    if (!allFinite(feedDelta, liquidProductDelta, exportGasDelta, liquidProductContribution, exportGasContribution,
        feedContribution, operatingCostDelta, operatingCostContribution, attributedMarginDelta, totalProductValueDelta,
        totalVariableCostDelta, screeningMarginDelta, productValueResidual, variableCostResidual,
        screeningMarginResidual, attributionResidual) || Math.abs(productValueResidual) > CLOSURE_TOLERANCE
        || Math.abs(variableCostResidual) > CLOSURE_TOLERANCE || Math.abs(screeningMarginResidual) > CLOSURE_TOLERANCE
        || Math.abs(attributionResidual) > CLOSURE_TOLERANCE) {
      throw new IllegalArgumentException("cases do not define a closed finite economics case-delta receipt");
    }

    return new RefineryHydrotreatingSulfurNitrogenEconomicsCaseDeltaReceipt(baselineReceipt, candidateReceipt,
        feedDelta, liquidProductDelta, exportGasDelta, liquidProductContribution, exportGasContribution,
        feedContribution, operatingCostContribution, attributedMarginDelta, totalProductValueDelta,
        totalVariableCostDelta, screeningMarginDelta, productValueResidual, variableCostResidual,
        screeningMarginResidual, attributionResidual);
  }

  private static void requireSameScreeningPrices(
      RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baselineReceipt,
      RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt candidateReceipt) {
    if (baselineReceipt.getLiquidProductPricePerTonne() != candidateReceipt.getLiquidProductPricePerTonne()
        || baselineReceipt.getExportGasPricePerTonne() != candidateReceipt.getExportGasPricePerTonne()
        || baselineReceipt.getFeedCostPerTonne() != candidateReceipt.getFeedCostPerTonne()) {
      throw new IllegalArgumentException("baseline and candidate must use identical caller-owned screening prices");
    }
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

  /** @return candidate-minus-baseline feed rate in t/h */
  public double getFeedMassFlowDeltaTonnesPerHour() {
    return feedMassFlowDeltaTonnesPerHour;
  }

  /** @return candidate-minus-baseline external liquid-product rate in t/h */
  public double getLiquidProductMassFlowDeltaTonnesPerHour() {
    return liquidProductMassFlowDeltaTonnesPerHour;
  }

  /** @return candidate-minus-baseline export-gas rate in t/h */
  public double getExportGasMassFlowDeltaTonnesPerHour() {
    return exportGasMassFlowDeltaTonnesPerHour;
  }

  /** @return liquid-product-flow contribution to margin change in currency units/h */
  public double getLiquidProductFlowMarginContributionPerHour() {
    return liquidProductFlowMarginContributionPerHour;
  }

  /** @return export-gas-flow contribution to margin change in currency units/h */
  public double getExportGasFlowMarginContributionPerHour() {
    return exportGasFlowMarginContributionPerHour;
  }

  /** @return feed-flow contribution to margin change in currency units/h */
  public double getFeedFlowMarginContributionPerHour() {
    return feedFlowMarginContributionPerHour;
  }

  /** @return aggregate qualified operating-cost contribution to margin change in currency units/h */
  public double getOperatingCostMarginContributionPerHour() {
    return operatingCostMarginContributionPerHour;
  }

  /** @return sum of the four attributed case contributions in currency units/h */
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

  /** @return fixed-price case-attribution closure residual in currency units/h */
  public double getCaseAttributionClosureResidualPerHour() {
    return caseAttributionClosureResidualPerHour;
  }
}
