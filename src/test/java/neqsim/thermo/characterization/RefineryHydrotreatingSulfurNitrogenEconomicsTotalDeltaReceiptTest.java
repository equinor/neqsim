package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests symmetric physical-case and screening-scenario economics attribution. */
class RefineryHydrotreatingSulfurNitrogenEconomicsTotalDeltaReceiptTest {
  private static final double NON_HYDROGEN_MOLAR_MASS_KG_PER_MOL = 0.0280134;
  private static final double METHANE_CARBON_MASS_FRACTION = 12.011 / 16.043;
  private static final double METHANE_HYDROGEN_MASS_FRACTION = 4.032 / 16.043;

  @Test
  void attributesPublicBigHillPhysicalAndScenarioDeltaSymmetrically() {
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baseline =
        screening(intensity(1000.0, 1.0, 100.0), 600.0, 100.0, 450.0);
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt candidate =
        screening(intensity(2000.0, 2.0, 100.0), 625.0, 80.0, 460.0);

    RefineryHydrotreatingSulfurNitrogenEconomicsTotalDeltaReceipt receipt =
        RefineryHydrotreatingSulfurNitrogenEconomicsTotalDeltaReceipt.calculate(
            baseline, candidate);

    assertSame(baseline, receipt.getBaselineReceipt());
    assertSame(candidate, receipt.getCandidateReceipt());
    assertEquals(1.0, receipt.getFeedMassFlowDeltaTonnesPerHour(), 1.0e-12);
    assertEquals(0.9954894479786512,
        receipt.getLiquidProductMassFlowDeltaTonnesPerHour(), 1.0e-12);
    assertEquals(0.00761202393313295,
        receipt.getExportGasMassFlowDeltaTonnesPerHour(), 1.0e-12);
    assertEquals(25.0, receipt.getLiquidProductPriceDeltaPerTonne(), 1.0e-12);
    assertEquals(-20.0, receipt.getExportGasPriceDeltaPerTonne(), 1.0e-12);
    assertEquals(10.0, receipt.getFeedCostDeltaPerTonne(), 1.0e-12);
    assertEquals(609.7372868869239,
        receipt.getLiquidProductFlowMarginContributionPerHour(), 1.0e-9);
    assertEquals(37.33085429919942,
        receipt.getLiquidProductPriceMarginContributionPerHour(), 1.0e-9);
    assertEquals(0.6850821539819655,
        receipt.getExportGasFlowMarginContributionPerHour(), 1.0e-12);
    assertEquals(-0.22836071799398852,
        receipt.getExportGasPriceMarginContributionPerHour(), 1.0e-12);
    assertEquals(-455.0, receipt.getFeedFlowMarginContributionPerHour(), 1.0e-12);
    assertEquals(-15.0, receipt.getFeedCostMarginContributionPerHour(), 1.0e-12);
    assertEquals(-54.13873899696584,
        receipt.getOperatingCostMarginContributionPerHour(), 1.0e-9);
    assertEquals(647.5248626221114, receipt.getTotalProductValueDeltaPerHour(), 1.0e-9);
    assertEquals(524.1387389969657, receipt.getTotalVariableCostDeltaPerHour(), 1.0e-9);
    assertEquals(123.38612362514567, receipt.getScreeningMarginDeltaPerHour(), 1.0e-9);
    assertEquals(123.38612362514567, receipt.getAttributedMarginDeltaPerHour(), 1.0e-9);
    assertClosures(receipt);
  }

  @Test
  void reversingComparisonNegatesEveryContribution() {
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baseline =
        screening(intensity(1000.0, 1.0, 100.0), 600.0, 100.0, 450.0);
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt candidate =
        screening(intensity(2000.0, 2.0, 100.0), 625.0, 80.0, 460.0);
    RefineryHydrotreatingSulfurNitrogenEconomicsTotalDeltaReceipt forward =
        RefineryHydrotreatingSulfurNitrogenEconomicsTotalDeltaReceipt.calculate(
            baseline, candidate);
    RefineryHydrotreatingSulfurNitrogenEconomicsTotalDeltaReceipt reverse =
        RefineryHydrotreatingSulfurNitrogenEconomicsTotalDeltaReceipt.calculate(
            candidate, baseline);

    assertEquals(-forward.getLiquidProductFlowMarginContributionPerHour(),
        reverse.getLiquidProductFlowMarginContributionPerHour(), 1.0e-9);
    assertEquals(-forward.getLiquidProductPriceMarginContributionPerHour(),
        reverse.getLiquidProductPriceMarginContributionPerHour(), 1.0e-9);
    assertEquals(-forward.getExportGasFlowMarginContributionPerHour(),
        reverse.getExportGasFlowMarginContributionPerHour(), 1.0e-12);
    assertEquals(-forward.getExportGasPriceMarginContributionPerHour(),
        reverse.getExportGasPriceMarginContributionPerHour(), 1.0e-12);
    assertEquals(-forward.getFeedFlowMarginContributionPerHour(),
        reverse.getFeedFlowMarginContributionPerHour(), 1.0e-12);
    assertEquals(-forward.getFeedCostMarginContributionPerHour(),
        reverse.getFeedCostMarginContributionPerHour(), 1.0e-12);
    assertEquals(-forward.getOperatingCostMarginContributionPerHour(),
        reverse.getOperatingCostMarginContributionPerHour(), 1.0e-9);
    assertEquals(-forward.getAttributedMarginDeltaPerHour(),
        reverse.getAttributedMarginDeltaPerHour(), 1.0e-9);
    assertClosures(reverse);
  }

  @Test
  void pureScenarioDeltaHasNoPhysicalContribution() {
    RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt physical =
        intensity(1000.0, 1.0, 100.0);
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baseline =
        screening(physical, 600.0, 100.0, 450.0);
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt candidate =
        screening(physical, 625.0, 80.0, 460.0);

    RefineryHydrotreatingSulfurNitrogenEconomicsTotalDeltaReceipt receipt =
        RefineryHydrotreatingSulfurNitrogenEconomicsTotalDeltaReceipt.calculate(
            baseline, candidate);

    assertEquals(0.0, receipt.getLiquidProductFlowMarginContributionPerHour(), 1.0e-12);
    assertEquals(0.0, receipt.getExportGasFlowMarginContributionPerHour(), 1.0e-12);
    assertEquals(0.0, receipt.getFeedFlowMarginContributionPerHour(), 1.0e-12);
    assertEquals(0.0, receipt.getOperatingCostMarginContributionPerHour(), 1.0e-12);
    assertEquals(14.73499572080362, receipt.getAttributedMarginDeltaPerHour(), 1.0e-9);
    assertClosures(receipt);
  }

  @Test
  void pureCaseDeltaHasNoPriceContribution() {
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baseline =
        screening(intensity(1000.0, 1.0, 100.0), 600.0, 100.0, 450.0);
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt candidate =
        screening(intensity(2000.0, 2.0, 100.0), 600.0, 100.0, 450.0);

    RefineryHydrotreatingSulfurNitrogenEconomicsTotalDeltaReceipt receipt =
        RefineryHydrotreatingSulfurNitrogenEconomicsTotalDeltaReceipt.calculate(
            baseline, candidate);

    assertEquals(0.0, receipt.getLiquidProductPriceMarginContributionPerHour(), 1.0e-12);
    assertEquals(0.0, receipt.getExportGasPriceMarginContributionPerHour(), 1.0e-12);
    assertEquals(0.0, receipt.getFeedCostMarginContributionPerHour(), 1.0e-12);
    assertEquals(93.9161321835382, receipt.getAttributedMarginDeltaPerHour(), 1.0e-9);
    assertClosures(receipt);
  }

  @Test
  void equivalentIndependentCasesHaveZeroDelta() {
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baseline =
        screening(intensity(1000.0, 1.0, 100.0), 600.0, 100.0, 450.0);
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt candidate =
        screening(intensity(1000.0, 1.0, 100.0), 600.0, 100.0, 450.0);

    RefineryHydrotreatingSulfurNitrogenEconomicsTotalDeltaReceipt receipt =
        RefineryHydrotreatingSulfurNitrogenEconomicsTotalDeltaReceipt.calculate(
            baseline, candidate);

    assertEquals(0.0, receipt.getAttributedMarginDeltaPerHour(), 1.0e-12);
    assertEquals(0.0, receipt.getScreeningMarginDeltaPerHour(), 1.0e-12);
    assertClosures(receipt);
  }

  @Test
  void rejectsMissingCase() {
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baseline =
        screening(intensity(1000.0, 1.0, 100.0), 600.0, 100.0, 450.0);

    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingSulfurNitrogenEconomicsTotalDeltaReceipt
            .calculate(null, baseline));
    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingSulfurNitrogenEconomicsTotalDeltaReceipt
            .calculate(baseline, null));
  }

  private static void assertClosures(
      RefineryHydrotreatingSulfurNitrogenEconomicsTotalDeltaReceipt receipt) {
    assertEquals(0.0, receipt.getProductValueDeltaClosureResidualPerHour(), 1.0e-9);
    assertEquals(0.0, receipt.getVariableCostDeltaClosureResidualPerHour(), 1.0e-9);
    assertEquals(0.0, receipt.getScreeningMarginDeltaClosureResidualPerHour(), 1.0e-9);
    assertEquals(0.0, receipt.getTotalAttributionClosureResidualPerHour(), 1.0e-9);
  }

  private static RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt screening(
      RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt intensity,
      double liquidProductPricePerTonne, double exportGasPricePerTonne,
      double feedCostPerTonne) {
    return RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt.calculate(
        intensity, liquidProductPricePerTonne, exportGasPricePerTonne, feedCostPerTonne);
  }

  private static RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt intensity(
      double feedMassFlowKgPerHour, double sensibleHeatingDutyMegaWatt,
      double carbonPricePerTonneCo2e) {
    RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt net =
        RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt.calculate(
            hydrogenReceipt(feedMassFlowKgPerHour),
            heatCredit(feedMassFlowKgPerHour, sensibleHeatingDutyMegaWatt),
            carbonPricePerTonneCo2e);
    return RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt.calculate(net);
  }

  private static RefineryHydrotreatingSulfurNitrogenOperatingReceipt hydrogenReceipt(
      double feedMassFlowKgPerHour) {
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supply =
        RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance.calculate(
            material(), 1.5, 0.90, NON_HYDROGEN_MOLAR_MASS_KG_PER_MOL);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance recycle =
        RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance.calculate(
            supply, 0.90, 0.10, 0.20, 0.50, 0.05);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance throughput =
        RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance.calculate(
            recycle, feedMassFlowKgPerHour);
    RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance utility =
        RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance.calculate(
            throughput, 120.0, 3.0);
    RefineryHydrotreatingSulfurNitrogenHydrogenEmissionsBalance emissions =
        RefineryHydrotreatingSulfurNitrogenHydrogenEmissionsBalance.calculate(
            utility, 10.0, 100.0);
    return RefineryHydrotreatingSulfurNitrogenOperatingReceipt.calculate(emissions);
  }

  private static RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance
      heatCredit(double feedMassFlowKgPerHour, double sensibleHeatingDutyMegaWatt) {
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supply =
        RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance.calculate(
            material(), 1.5, 0.90, NON_HYDROGEN_MOLAR_MASS_KG_PER_MOL);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance recycle =
        RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance.calculate(
            supply, 0.90, 0.10, 0.20, 0.50, 0.05);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance throughput =
        RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance.calculate(
            recycle, feedMassFlowKgPerHour);
    RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt distribution =
        RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt.calculate(throughput);
    RefineryHydrotreatingSulfurNitrogenThermalDutyBalance thermal =
        RefineryHydrotreatingSulfurNitrogenThermalDutyBalance.calculate(
            distribution, 100.0, 50.0, sensibleHeatingDutyMegaWatt);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance utility =
        RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance.calculate(
            thermal, 0.85, 50.0, 0.40, 3.0);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance combustion =
        RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance.calculate(
            utility, METHANE_CARBON_MASS_FRACTION, METHANE_HYDROGEN_MASS_FRACTION,
            0.0, 0.0, 0.0, 0.2095, 0.15);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance stack =
        RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance.calculate(
            combustion, 473.15, 298.15, 34.0);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance recovery =
        RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance.calculate(
            stack, 0.60);
    return RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance
        .calculate(recovery, 0.75);
  }

  private static RefineryHydrotreatingSulfurNitrogenBalance material() {
    return RefineryHydrotreatingSulfurNitrogenBalance.calculate(
        1000.0, 0.0040867518, 0.001095129, 15.0e-6, 10.0e-6, 2.0, 4.0);
  }
}
