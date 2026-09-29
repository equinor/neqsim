package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable break-even and first-order price-sensitivity receipt for qualified hydrotreating screening economics.
 *
 * <p>
 * Each break-even value changes one caller-owned price or cost at a time while holding the other qualified scenario
 * terms fixed. The results are algebraic screening values, not market forecasts, optimization results, or investment
 * recommendations.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingSulfurNitrogenBreakEvenEconomicsReceipt implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double KILOGRAMS_PER_TONNE = 1000.0;
  private static final double CLOSURE_TOLERANCE = 1.0e-9;

  private final RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt screeningEconomicsReceipt;
  private final double feedMassFlowTonnesPerHour;
  private final double liquidProductMassFlowTonnesPerHour;
  private final double exportGasMassFlowTonnesPerHour;
  private final double breakEvenLiquidProductPricePerTonne;
  private final double breakEvenExportGasPricePerTonne;
  private final double breakEvenFeedCostPerTonne;
  private final double liquidProductPriceDeltaToBreakEvenPerTonne;
  private final double exportGasPriceDeltaToBreakEvenPerTonne;
  private final double feedCostDeltaToBreakEvenPerTonne;
  private final double liquidProductMarginSensitivityTonnesPerHour;
  private final double exportGasMarginSensitivityTonnesPerHour;
  private final double feedCostMarginSensitivityTonnesPerHour;
  private final double liquidProductBreakEvenClosureResidualPerHour;
  private final double exportGasBreakEvenClosureResidualPerHour;
  private final double feedCostBreakEvenClosureResidualPerHour;

  private RefineryHydrotreatingSulfurNitrogenBreakEvenEconomicsReceipt(
      RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt screeningEconomicsReceipt,
      double feedMassFlowTonnesPerHour, double liquidProductMassFlowTonnesPerHour,
      double exportGasMassFlowTonnesPerHour, double breakEvenLiquidProductPricePerTonne,
      double breakEvenExportGasPricePerTonne, double breakEvenFeedCostPerTonne,
      double liquidProductPriceDeltaToBreakEvenPerTonne, double exportGasPriceDeltaToBreakEvenPerTonne,
      double feedCostDeltaToBreakEvenPerTonne, double liquidProductMarginSensitivityTonnesPerHour,
      double exportGasMarginSensitivityTonnesPerHour, double feedCostMarginSensitivityTonnesPerHour,
      double liquidProductBreakEvenClosureResidualPerHour, double exportGasBreakEvenClosureResidualPerHour,
      double feedCostBreakEvenClosureResidualPerHour) {
    this.screeningEconomicsReceipt = screeningEconomicsReceipt;
    this.feedMassFlowTonnesPerHour = feedMassFlowTonnesPerHour;
    this.liquidProductMassFlowTonnesPerHour = liquidProductMassFlowTonnesPerHour;
    this.exportGasMassFlowTonnesPerHour = exportGasMassFlowTonnesPerHour;
    this.breakEvenLiquidProductPricePerTonne = breakEvenLiquidProductPricePerTonne;
    this.breakEvenExportGasPricePerTonne = breakEvenExportGasPricePerTonne;
    this.breakEvenFeedCostPerTonne = breakEvenFeedCostPerTonne;
    this.liquidProductPriceDeltaToBreakEvenPerTonne = liquidProductPriceDeltaToBreakEvenPerTonne;
    this.exportGasPriceDeltaToBreakEvenPerTonne = exportGasPriceDeltaToBreakEvenPerTonne;
    this.feedCostDeltaToBreakEvenPerTonne = feedCostDeltaToBreakEvenPerTonne;
    this.liquidProductMarginSensitivityTonnesPerHour = liquidProductMarginSensitivityTonnesPerHour;
    this.exportGasMarginSensitivityTonnesPerHour = exportGasMarginSensitivityTonnesPerHour;
    this.feedCostMarginSensitivityTonnesPerHour = feedCostMarginSensitivityTonnesPerHour;
    this.liquidProductBreakEvenClosureResidualPerHour = liquidProductBreakEvenClosureResidualPerHour;
    this.exportGasBreakEvenClosureResidualPerHour = exportGasBreakEvenClosureResidualPerHour;
    this.feedCostBreakEvenClosureResidualPerHour = feedCostBreakEvenClosureResidualPerHour;
  }

  /**
   * Calculate single-variable break-even prices and exact linear margin sensitivities.
   *
   * @param screeningEconomicsReceipt qualified caller-priced screening-economics receipt
   * @return immutable break-even and price-sensitivity receipt
   */
  public static RefineryHydrotreatingSulfurNitrogenBreakEvenEconomicsReceipt calculate(
      RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt screeningEconomicsReceipt) {
    Objects.requireNonNull(screeningEconomicsReceipt, "screeningEconomicsReceipt");

    RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt intensity = screeningEconomicsReceipt
        .getNetProductIntensityReceipt();
    RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt distribution = intensity
        .getProductDistributionReceipt();
    double feedRate = intensity.getFeedMassFlowKgPerHour() / KILOGRAMS_PER_TONNE;
    double liquidProductRate = intensity.getLiquidProductMassFlowKgPerHour() / KILOGRAMS_PER_TONNE;
    double exportGasRate = distribution.getThroughputBalance().getExportGasMassFlowKgPerHour()
        / KILOGRAMS_PER_TONNE;
    if (!allFinitePositive(feedRate, liquidProductRate, exportGasRate)) {
      throw new IllegalArgumentException("qualified feed, liquid-product, and export-gas rates must be positive");
    }

    double breakEvenLiquidProductPrice = (screeningEconomicsReceipt.getTotalVariableCostPerHour()
        - screeningEconomicsReceipt.getExportGasValuePerHour()) / liquidProductRate;
    double breakEvenExportGasPrice = (screeningEconomicsReceipt.getTotalVariableCostPerHour()
        - screeningEconomicsReceipt.getLiquidProductValuePerHour()) / exportGasRate;
    double breakEvenFeedCost = (screeningEconomicsReceipt.getTotalProductValuePerHour()
        - screeningEconomicsReceipt.getOperatingCostPerHour()) / feedRate;
    double liquidProductDelta = breakEvenLiquidProductPrice
        - screeningEconomicsReceipt.getLiquidProductPricePerTonne();
    double exportGasDelta = breakEvenExportGasPrice - screeningEconomicsReceipt.getExportGasPricePerTonne();
    double feedCostDelta = breakEvenFeedCost - screeningEconomicsReceipt.getFeedCostPerTonne();
    double liquidProductSensitivity = liquidProductRate;
    double exportGasSensitivity = exportGasRate;
    double feedCostSensitivity = -feedRate;
    double margin = screeningEconomicsReceipt.getScreeningMarginPerHour();
    double liquidProductResidual = margin + liquidProductSensitivity * liquidProductDelta;
    double exportGasResidual = margin + exportGasSensitivity * exportGasDelta;
    double feedCostResidual = margin + feedCostSensitivity * feedCostDelta;

    if (!allFinite(breakEvenLiquidProductPrice, breakEvenExportGasPrice, breakEvenFeedCost, liquidProductDelta,
        exportGasDelta, feedCostDelta, liquidProductSensitivity, exportGasSensitivity, feedCostSensitivity,
        liquidProductResidual, exportGasResidual, feedCostResidual)
        || Math.abs(liquidProductResidual) > CLOSURE_TOLERANCE
        || Math.abs(exportGasResidual) > CLOSURE_TOLERANCE
        || Math.abs(feedCostResidual) > CLOSURE_TOLERANCE) {
      throw new IllegalArgumentException("inputs do not define a closed finite break-even economics receipt");
    }

    return new RefineryHydrotreatingSulfurNitrogenBreakEvenEconomicsReceipt(screeningEconomicsReceipt, feedRate,
        liquidProductRate, exportGasRate, breakEvenLiquidProductPrice, breakEvenExportGasPrice, breakEvenFeedCost,
        liquidProductDelta, exportGasDelta, feedCostDelta, liquidProductSensitivity, exportGasSensitivity,
        feedCostSensitivity, liquidProductResidual, exportGasResidual, feedCostResidual);
  }

  /**
   * Check that every supplied value is finite and strictly positive.
   *
   * @param values values to check
   * @return true when every value is finite and strictly positive
   */
  private static boolean allFinitePositive(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value) || value <= 0.0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Check that every supplied value is finite.
   *
   * @param values values to check
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

  /** @return qualified upstream screening-economics receipt */
  public RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt getScreeningEconomicsReceipt() {
    return screeningEconomicsReceipt;
  }

  /** @return qualified feed mass flow in t/h */
  public double getFeedMassFlowTonnesPerHour() {
    return feedMassFlowTonnesPerHour;
  }

  /** @return qualified external liquid-product mass flow in t/h */
  public double getLiquidProductMassFlowTonnesPerHour() {
    return liquidProductMassFlowTonnesPerHour;
  }

  /** @return qualified export-gas mass flow in t/h */
  public double getExportGasMassFlowTonnesPerHour() {
    return exportGasMassFlowTonnesPerHour;
  }

  /** @return liquid-product price that yields zero margin while other terms remain fixed */
  public double getBreakEvenLiquidProductPricePerTonne() {
    return breakEvenLiquidProductPricePerTonne;
  }

  /** @return export-gas price that yields zero margin while other terms remain fixed */
  public double getBreakEvenExportGasPricePerTonne() {
    return breakEvenExportGasPricePerTonne;
  }

  /** @return feed cost that yields zero margin while other terms remain fixed */
  public double getBreakEvenFeedCostPerTonne() {
    return breakEvenFeedCostPerTonne;
  }

  /** @return break-even liquid-product price minus the caller-owned price in currency units/t */
  public double getLiquidProductPriceDeltaToBreakEvenPerTonne() {
    return liquidProductPriceDeltaToBreakEvenPerTonne;
  }

  /** @return break-even export-gas price minus the caller-owned price in currency units/t */
  public double getExportGasPriceDeltaToBreakEvenPerTonne() {
    return exportGasPriceDeltaToBreakEvenPerTonne;
  }

  /** @return break-even feed cost minus the caller-owned feed cost in currency units/t */
  public double getFeedCostDeltaToBreakEvenPerTonne() {
    return feedCostDeltaToBreakEvenPerTonne;
  }

  /** @return margin response to liquid-product price in t/h */
  public double getLiquidProductMarginSensitivityTonnesPerHour() {
    return liquidProductMarginSensitivityTonnesPerHour;
  }

  /** @return margin response to export-gas price in t/h */
  public double getExportGasMarginSensitivityTonnesPerHour() {
    return exportGasMarginSensitivityTonnesPerHour;
  }

  /** @return margin response to feed cost in negative t/h */
  public double getFeedCostMarginSensitivityTonnesPerHour() {
    return feedCostMarginSensitivityTonnesPerHour;
  }

  /** @return reconstructed zero-margin residual for the liquid-product price axis in currency units/h */
  public double getLiquidProductBreakEvenClosureResidualPerHour() {
    return liquidProductBreakEvenClosureResidualPerHour;
  }

  /** @return reconstructed zero-margin residual for the export-gas price axis in currency units/h */
  public double getExportGasBreakEvenClosureResidualPerHour() {
    return exportGasBreakEvenClosureResidualPerHour;
  }

  /** @return reconstructed zero-margin residual for the feed-cost axis in currency units/h */
  public double getFeedCostBreakEvenClosureResidualPerHour() {
    return feedCostBreakEvenClosureResidualPerHour;
  }
}
