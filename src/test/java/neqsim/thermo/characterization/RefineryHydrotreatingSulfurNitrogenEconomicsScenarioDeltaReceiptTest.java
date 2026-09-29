package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests same-physical-case price and feed-cost attribution for coupled hydrotreating screening economics. */
class RefineryHydrotreatingSulfurNitrogenEconomicsScenarioDeltaReceiptTest {
  private static final double NON_HYDROGEN_MOLAR_MASS_KG_PER_MOL = 0.0280134;
  private static final double METHANE_CARBON_MASS_FRACTION = 12.011 / 16.043;
  private static final double METHANE_HYDROGEN_MASS_FRACTION = 4.032 / 16.043;

  @Test
  void attributesPublicBigHillScenarioDelta() {
    RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt intensity = intensity(1000.0, 1.0, 100.0);
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baseline =
        screening(intensity, 600.0, 100.0, 450.0);
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt candidate =
        screening(intensity, 625.0, 80.0, 460.0);

    RefineryHydrotreatingSulfurNitrogenEconomicsScenarioDeltaReceipt receipt =
        RefineryHydrotreatingSulfurNitrogenEconomicsScenarioDeltaReceipt.calculate(baseline, candidate);

    assertSame(baseline, receipt.getBaselineReceipt());
    assertSame(candidate, receipt.getCandidateReceipt());
    assertEquals(25.0, receipt.getLiquidProductPriceDeltaPerTonne(), 1.0e-12);
    assertEquals(-20.0, receipt.getExportGasPriceDeltaPerTonne(), 1.0e-12);
    assertEquals(10.0, receipt.getFeedCostDeltaPerTonne(), 1.0e-12);
    assertEquals(24.88723619946628, receipt.getLiquidProductMarginContributionPerHour(), 1.0e-9);
    assertEquals(-0.152240478662659, receipt.getExportGasMarginContributionPerHour(), 1.0e-9);
    assertEquals(-10.0, receipt.getFeedCostMarginContributionPerHour(), 1.0e-12);
    assertEquals(24.73499572080362, receipt.getTotalProductValueDeltaPerHour(), 1.0e-9);
    assertEquals(10.0, receipt.getTotalVariableCostDeltaPerHour(), 1.0e-9);
    assertEquals(14.73499572080362, receipt.getScreeningMarginDeltaPerHour(), 1.0e-9);
    assertEquals(14.73499572080362, receipt.getAttributedMarginDeltaPerHour(), 1.0e-9);
    assertEquals(0.0, receipt.getProductValueDeltaClosureResidualPerHour(), 1.0e-9);
    assertEquals(0.0, receipt.getVariableCostDeltaClosureResidualPerHour(), 1.0e-9);
    assertEquals(0.0, receipt.getScreeningMarginDeltaClosureResidualPerHour(), 1.0e-9);
    assertEquals(0.0, receipt.getPriceAttributionClosureResidualPerHour(), 1.0e-9);
  }

  @Test
  void unchangedScenarioHasZeroDelta() {
    RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt intensity = intensity(1000.0, 1.0, 100.0);
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baseline =
        screening(intensity, 600.0, 100.0, 450.0);
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt candidate =
        screening(intensity, 600.0, 100.0, 450.0);

    RefineryHydrotreatingSulfurNitrogenEconomicsScenarioDeltaReceipt receipt =
        RefineryHydrotreatingSulfurNitrogenEconomicsScenarioDeltaReceipt.calculate(baseline, candidate);

    assertEquals(0.0, receipt.getAttributedMarginDeltaPerHour(), 1.0e-12);
    assertEquals(0.0, receipt.getScreeningMarginDeltaPerHour(), 1.0e-12);
    assertEquals(0.0, receipt.getPriceAttributionClosureResidualPerHour(), 1.0e-12);
  }

  @Test
  void adverseCallerScenarioRetainsSignedMarginDelta() {
    RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt intensity = intensity(1000.0, 1.0, 100.0);
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baseline =
        screening(intensity, 600.0, 100.0, 450.0);
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt candidate =
        screening(intensity, 550.0, 50.0, 500.0);

    RefineryHydrotreatingSulfurNitrogenEconomicsScenarioDeltaReceipt receipt =
        RefineryHydrotreatingSulfurNitrogenEconomicsScenarioDeltaReceipt.calculate(baseline, candidate);

    assertTrue(receipt.getScreeningMarginDeltaPerHour() < 0.0);
    assertEquals(receipt.getScreeningMarginDeltaPerHour(), receipt.getAttributedMarginDeltaPerHour(), 1.0e-9);
  }

  @Test
  void throughputScalingScalesContributions() {
    RefineryHydrotreatingSulfurNitrogenEconomicsScenarioDeltaReceipt base = scenarioDelta(1000.0, 1.0);
    RefineryHydrotreatingSulfurNitrogenEconomicsScenarioDeltaReceipt doubled = scenarioDelta(2000.0, 2.0);

    assertEquals(base.getLiquidProductPriceDeltaPerTonne(), doubled.getLiquidProductPriceDeltaPerTonne(), 1.0e-12);
    assertEquals(base.getExportGasPriceDeltaPerTonne(), doubled.getExportGasPriceDeltaPerTonne(), 1.0e-12);
    assertEquals(base.getFeedCostDeltaPerTonne(), doubled.getFeedCostDeltaPerTonne(), 1.0e-12);
    assertEquals(2.0 * base.getAttributedMarginDeltaPerHour(), doubled.getAttributedMarginDeltaPerHour(), 1.0e-9);
    assertEquals(2.0 * base.getScreeningMarginDeltaPerHour(), doubled.getScreeningMarginDeltaPerHour(), 1.0e-9);
  }

  @Test
  void rejectsDifferentPhysicalEvidence() {
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baseline =
        screening(intensity(1000.0, 1.0, 100.0), 600.0, 100.0, 450.0);
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt candidate =
        screening(intensity(1000.0, 1.0, 100.0), 625.0, 80.0, 460.0);

    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenEconomicsScenarioDeltaReceipt.calculate(baseline, candidate));
  }

  @Test
  void rejectsMissingScenario() {
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt baseline =
        screening(intensity(1000.0, 1.0, 100.0), 600.0, 100.0, 450.0);

    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingSulfurNitrogenEconomicsScenarioDeltaReceipt.calculate(null, baseline));
    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingSulfurNitrogenEconomicsScenarioDeltaReceipt.calculate(baseline, null));
  }

  private static RefineryHydrotreatingSulfurNitrogenEconomicsScenarioDeltaReceipt scenarioDelta(
      double feedMassFlowKgPerHour, double sensibleHeatingDutyMegaWatt) {
    RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt intensity =
        intensity(feedMassFlowKgPerHour, sensibleHeatingDutyMegaWatt, 100.0);
    return RefineryHydrotreatingSulfurNitrogenEconomicsScenarioDeltaReceipt.calculate(
        screening(intensity, 600.0, 100.0, 450.0), screening(intensity, 625.0, 80.0, 460.0));
  }

  private static RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt screening(
      RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt intensity,
      double liquidProductPricePerTonne, double exportGasPricePerTonne, double feedCostPerTonne) {
    return RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt.calculate(intensity,
        liquidProductPricePerTonne, exportGasPricePerTonne, feedCostPerTonne);
  }

  private static RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt intensity(
      double feedMassFlowKgPerHour, double sensibleHeatingDutyMegaWatt, double carbonPricePerTonneCo2e) {
    RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt net =
        RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt.calculate(
            hydrogenReceipt(feedMassFlowKgPerHour),
            heatCredit(feedMassFlowKgPerHour, sensibleHeatingDutyMegaWatt), carbonPricePerTonneCo2e);
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
        RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance.calculate(throughput, 120.0, 3.0);
    RefineryHydrotreatingSulfurNitrogenHydrogenEmissionsBalance emissions =
        RefineryHydrotreatingSulfurNitrogenHydrogenEmissionsBalance.calculate(utility, 10.0, 100.0);
    return RefineryHydrotreatingSulfurNitrogenOperatingReceipt.calculate(emissions);
  }

  private static RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance heatCredit(
      double feedMassFlowKgPerHour, double sensibleHeatingDutyMegaWatt) {
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
            utility, METHANE_CARBON_MASS_FRACTION, METHANE_HYDROGEN_MASS_FRACTION, 0.0, 0.0, 0.0, 0.2095, 0.15);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance stack =
        RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance.calculate(
            combustion, 473.15, 298.15, 34.0);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance recovery =
        RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance.calculate(stack, 0.60);
    return RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance.calculate(recovery, 0.75);
  }

  private static RefineryHydrotreatingSulfurNitrogenBalance material() {
    return RefineryHydrotreatingSulfurNitrogenBalance.calculate(
        1000.0, 0.0040867518, 0.001095129, 15.0e-6, 10.0e-6, 2.0, 4.0);
  }
}
