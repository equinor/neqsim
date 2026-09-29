package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests fixed-screening-price case deltas for coupled hydrotreating economics. */
class RefineryHydrotreatingSulfurNitrogenEconomicsCaseDeltaReceiptTest {
  private static final double NON_HYDROGEN_MOLAR_MASS_KG_PER_MOL = 0.0280134;
  private static final double METHANE_CARBON_MASS_FRACTION = 12.011 / 16.043;
  private static final double METHANE_HYDROGEN_MASS_FRACTION = 4.032 / 16.043;

  @Test
  void attributesPublicBigHillThroughputCaseDelta() {
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baseline = screening(
        intensity(1000.0, 1.0, 100.0), 600.0, 100.0, 450.0);
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt candidate = screening(
        intensity(2000.0, 2.0, 100.0), 600.0, 100.0, 450.0);

    RefineryHydrotreatingSulfurNitrogenEconomicsCaseDeltaReceipt receipt = RefineryHydrotreatingSulfurNitrogenEconomicsCaseDeltaReceipt
        .calculate(baseline, candidate);

    assertSame(baseline, receipt.getBaselineReceipt());
    assertSame(candidate, receipt.getCandidateReceipt());
    assertEquals(1.0, receipt.getFeedMassFlowDeltaTonnesPerHour(), 1.0e-12);
    assertEquals(0.9954894479786512, receipt.getLiquidProductMassFlowDeltaTonnesPerHour(), 1.0e-12);
    assertEquals(0.00761202393313295, receipt.getExportGasMassFlowDeltaTonnesPerHour(), 1.0e-12);
    assertEquals(597.2936687871908, receipt.getLiquidProductFlowMarginContributionPerHour(), 1.0e-9);
    assertEquals(0.761202393313295, receipt.getExportGasFlowMarginContributionPerHour(), 1.0e-12);
    assertEquals(-450.0, receipt.getFeedFlowMarginContributionPerHour(), 1.0e-12);
    assertEquals(-54.13873899696584, receipt.getOperatingCostMarginContributionPerHour(), 1.0e-9);
    assertEquals(598.054871180504, receipt.getTotalProductValueDeltaPerHour(), 1.0e-9);
    assertEquals(504.13873899696586, receipt.getTotalVariableCostDeltaPerHour(), 1.0e-9);
    assertEquals(93.9161321835382, receipt.getScreeningMarginDeltaPerHour(), 1.0e-9);
    assertEquals(93.9161321835382, receipt.getAttributedMarginDeltaPerHour(), 1.0e-9);
    assertEquals(0.0, receipt.getProductValueDeltaClosureResidualPerHour(), 1.0e-9);
    assertEquals(0.0, receipt.getVariableCostDeltaClosureResidualPerHour(), 1.0e-9);
    assertEquals(0.0, receipt.getScreeningMarginDeltaClosureResidualPerHour(), 1.0e-9);
    assertEquals(0.0, receipt.getCaseAttributionClosureResidualPerHour(), 1.0e-9);
  }

  @Test
  void equivalentIndependentCasesHaveZeroDelta() {
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baseline = screening(
        intensity(1000.0, 1.0, 100.0), 600.0, 100.0, 450.0);
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt candidate = screening(
        intensity(1000.0, 1.0, 100.0), 600.0, 100.0, 450.0);

    RefineryHydrotreatingSulfurNitrogenEconomicsCaseDeltaReceipt receipt = RefineryHydrotreatingSulfurNitrogenEconomicsCaseDeltaReceipt
        .calculate(baseline, candidate);

    assertEquals(0.0, receipt.getAttributedMarginDeltaPerHour(), 1.0e-12);
    assertEquals(0.0, receipt.getScreeningMarginDeltaPerHour(), 1.0e-12);
    assertEquals(0.0, receipt.getCaseAttributionClosureResidualPerHour(), 1.0e-12);
  }

  @Test
  void lowerThroughputRetainsSignedMarginDelta() {
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baseline = screening(
        intensity(1000.0, 1.0, 100.0), 600.0, 100.0, 450.0);
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt candidate = screening(
        intensity(500.0, 0.5, 100.0), 600.0, 100.0, 450.0);

    RefineryHydrotreatingSulfurNitrogenEconomicsCaseDeltaReceipt receipt = RefineryHydrotreatingSulfurNitrogenEconomicsCaseDeltaReceipt
        .calculate(baseline, candidate);

    assertTrue(receipt.getScreeningMarginDeltaPerHour() < 0.0);
    assertEquals(receipt.getScreeningMarginDeltaPerHour(), receipt.getAttributedMarginDeltaPerHour(), 1.0e-9);
  }

  @Test
  void rejectsChangedCallerScreeningPrices() {
    RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt baselineIntensity = intensity(1000.0, 1.0, 100.0);
    RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt candidateIntensity = intensity(2000.0, 2.0, 100.0);
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baseline = screening(baselineIntensity, 600.0,
        100.0, 450.0);

    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenEconomicsCaseDeltaReceipt.calculate(baseline,
            screening(candidateIntensity, 601.0, 100.0, 450.0)));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenEconomicsCaseDeltaReceipt.calculate(baseline,
            screening(candidateIntensity, 600.0, 101.0, 450.0)));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenEconomicsCaseDeltaReceipt.calculate(baseline,
            screening(candidateIntensity, 600.0, 100.0, 451.0)));
  }

  @Test
  void rejectsMissingCase() {
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baseline = screening(
        intensity(1000.0, 1.0, 100.0), 600.0, 100.0, 450.0);

    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingSulfurNitrogenEconomicsCaseDeltaReceipt.calculate(null, baseline));
    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingSulfurNitrogenEconomicsCaseDeltaReceipt.calculate(baseline, null));
  }

  private static RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt screening(
      RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt intensity, double liquidProductPricePerTonne,
      double exportGasPricePerTonne, double feedCostPerTonne) {
    return RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt.calculate(intensity,
        liquidProductPricePerTonne, exportGasPricePerTonne, feedCostPerTonne);
  }

  private static RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt intensity(double feedMassFlowKgPerHour,
      double sensibleHeatingDutyMegaWatt, double carbonPricePerTonneCo2e) {
    RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt net = RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt
        .calculate(hydrogenReceipt(feedMassFlowKgPerHour),
            heatCredit(feedMassFlowKgPerHour, sensibleHeatingDutyMegaWatt), carbonPricePerTonneCo2e);
    return RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt.calculate(net);
  }

  private static RefineryHydrotreatingSulfurNitrogenOperatingReceipt hydrogenReceipt(double feedMassFlowKgPerHour) {
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supply = RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance
        .calculate(material(), 1.5, 0.90, NON_HYDROGEN_MOLAR_MASS_KG_PER_MOL);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance recycle = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance
        .calculate(supply, 0.90, 0.10, 0.20, 0.50, 0.05);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance throughput = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance
        .calculate(recycle, feedMassFlowKgPerHour);
    RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance utility = RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance
        .calculate(throughput, 120.0, 3.0);
    RefineryHydrotreatingSulfurNitrogenHydrogenEmissionsBalance emissions = RefineryHydrotreatingSulfurNitrogenHydrogenEmissionsBalance
        .calculate(utility, 10.0, 100.0);
    return RefineryHydrotreatingSulfurNitrogenOperatingReceipt.calculate(emissions);
  }

  private static RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance heatCredit(
      double feedMassFlowKgPerHour, double sensibleHeatingDutyMegaWatt) {
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supply = RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance
        .calculate(material(), 1.5, 0.90, NON_HYDROGEN_MOLAR_MASS_KG_PER_MOL);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance recycle = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance
        .calculate(supply, 0.90, 0.10, 0.20, 0.50, 0.05);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance throughput = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance
        .calculate(recycle, feedMassFlowKgPerHour);
    RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt distribution = RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt
        .calculate(throughput);
    RefineryHydrotreatingSulfurNitrogenThermalDutyBalance thermal = RefineryHydrotreatingSulfurNitrogenThermalDutyBalance
        .calculate(distribution, 100.0, 50.0, sensibleHeatingDutyMegaWatt);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance utility = RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance
        .calculate(thermal, 0.85, 50.0, 0.40, 3.0);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance combustion = RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance
        .calculate(utility, METHANE_CARBON_MASS_FRACTION, METHANE_HYDROGEN_MASS_FRACTION, 0.0, 0.0, 0.0, 0.2095,
            0.15);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance stack = RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance
        .calculate(combustion, 473.15, 298.15, 34.0);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance recovery = RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance
        .calculate(stack, 0.60);
    return RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance.calculate(recovery, 0.75);
  }

  private static RefineryHydrotreatingSulfurNitrogenBalance material() {
    return RefineryHydrotreatingSulfurNitrogenBalance.calculate(1000.0, 0.0040867518, 0.001095129, 15.0e-6,
        10.0e-6, 2.0, 4.0);
  }
}
