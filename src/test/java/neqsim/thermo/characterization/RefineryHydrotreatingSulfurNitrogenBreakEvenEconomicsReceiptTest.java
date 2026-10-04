package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests break-even and price-sensitivity receipts for qualified coupled hydrotreating screening economics. */
class RefineryHydrotreatingSulfurNitrogenBreakEvenEconomicsReceiptTest {
  private static final double NON_HYDROGEN_MOLAR_MASS_KG_PER_MOL = 0.0280134;
  private static final double METHANE_CARBON_MASS_FRACTION = 12.011 / 16.043;
  private static final double METHANE_HYDROGEN_MASS_FRACTION = 4.032 / 16.043;

  @Test
  void qualifiesPublicBigHillBreakEvenEconomics() {
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt screening = screening(1000.0, 1.0, 100.0, 600.0, 100.0,
        450.0);
    RefineryHydrotreatingSulfurNitrogenBreakEvenEconomicsReceipt receipt = RefineryHydrotreatingSulfurNitrogenBreakEvenEconomicsReceipt
        .calculate(screening);

    assertSame(screening, receipt.getScreeningEconomicsReceipt());
    assertEquals(1.0, receipt.getFeedMassFlowTonnesPerHour(), 1.0e-12);
    assertEquals(0.9954894479786512, receipt.getLiquidProductMassFlowTonnesPerHour(), 1.0e-12);
    assertEquals(0.00761202393313295, receipt.getExportGasMassFlowTonnesPerHour(), 1.0e-12);
    assertEquals(505.6583348279225, receipt.getBreakEvenLiquidProductPricePerTonne(), 1.0e-9);
    assertEquals(-12237.86611921223, receipt.getBreakEvenExportGasPricePerTonne(), 1.0e-9);
    assertEquals(543.9161321835383, receipt.getBreakEvenFeedCostPerTonne(), 1.0e-9);
    assertEquals(-94.34166517207751, receipt.getLiquidProductPriceDeltaToBreakEvenPerTonne(), 1.0e-9);
    assertEquals(-12337.86611921223, receipt.getExportGasPriceDeltaToBreakEvenPerTonne(), 1.0e-9);
    assertEquals(93.91613218353825, receipt.getFeedCostDeltaToBreakEvenPerTonne(), 1.0e-9);
    assertEquals(0.9954894479786512, receipt.getLiquidProductMarginSensitivityTonnesPerHour(), 1.0e-12);
    assertEquals(0.00761202393313295, receipt.getExportGasMarginSensitivityTonnesPerHour(), 1.0e-12);
    assertEquals(-1.0, receipt.getFeedCostMarginSensitivityTonnesPerHour(), 1.0e-12);
    assertEquals(0.0, receipt.getLiquidProductBreakEvenClosureResidualPerHour(), 1.0e-12);
    assertEquals(0.0, receipt.getExportGasBreakEvenClosureResidualPerHour(), 1.0e-12);
    assertEquals(0.0, receipt.getFeedCostBreakEvenClosureResidualPerHour(), 1.0e-12);
  }

  @Test
  void signedExportGasBreakEvenRemainsAuditable() {
    RefineryHydrotreatingSulfurNitrogenBreakEvenEconomicsReceipt receipt = RefineryHydrotreatingSulfurNitrogenBreakEvenEconomicsReceipt
        .calculate(screening(1000.0, 1.0, 100.0, 600.0, 100.0, 450.0));

    assertTrue(receipt.getBreakEvenExportGasPricePerTonne() < 0.0);
    assertEquals(0.0, receipt.getExportGasBreakEvenClosureResidualPerHour(), 1.0e-12);
  }

  @Test
  void throughputScalingPreservesBreakEvenPricesAndScalesSensitivities() {
    RefineryHydrotreatingSulfurNitrogenBreakEvenEconomicsReceipt base = RefineryHydrotreatingSulfurNitrogenBreakEvenEconomicsReceipt
        .calculate(screening(1000.0, 1.0, 100.0, 600.0, 100.0, 450.0));
    RefineryHydrotreatingSulfurNitrogenBreakEvenEconomicsReceipt doubled = RefineryHydrotreatingSulfurNitrogenBreakEvenEconomicsReceipt
        .calculate(screening(2000.0, 2.0, 100.0, 600.0, 100.0, 450.0));

    assertEquals(base.getBreakEvenLiquidProductPricePerTonne(), doubled.getBreakEvenLiquidProductPricePerTonne(),
        1.0e-9);
    assertEquals(base.getBreakEvenExportGasPricePerTonne(), doubled.getBreakEvenExportGasPricePerTonne(), 1.0e-9);
    assertEquals(base.getBreakEvenFeedCostPerTonne(), doubled.getBreakEvenFeedCostPerTonne(), 1.0e-9);
    assertEquals(2.0 * base.getLiquidProductMarginSensitivityTonnesPerHour(),
        doubled.getLiquidProductMarginSensitivityTonnesPerHour(), 1.0e-12);
    assertEquals(2.0 * base.getExportGasMarginSensitivityTonnesPerHour(),
        doubled.getExportGasMarginSensitivityTonnesPerHour(), 1.0e-12);
    assertEquals(2.0 * base.getFeedCostMarginSensitivityTonnesPerHour(),
        doubled.getFeedCostMarginSensitivityTonnesPerHour(), 1.0e-12);
  }

  @Test
  void rejectsMissingUpstreamReceipt() {
    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingSulfurNitrogenBreakEvenEconomicsReceipt.calculate(null));
  }

  private static RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt screening(double feedMassFlowKgPerHour,
      double sensibleHeatingDutyMegaWatt, double carbonPricePerTonneCo2e, double liquidProductPricePerTonne,
      double exportGasPricePerTonne, double feedCostPerTonne) {
    RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt net = RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt
        .calculate(hydrogenReceipt(feedMassFlowKgPerHour),
            heatCredit(feedMassFlowKgPerHour, sensibleHeatingDutyMegaWatt), carbonPricePerTonneCo2e);
    RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt intensity = RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt
        .calculate(net);
    return RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt.calculate(intensity, liquidProductPricePerTonne,
        exportGasPricePerTonne, feedCostPerTonne);
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
        .calculate(utility, METHANE_CARBON_MASS_FRACTION, METHANE_HYDROGEN_MASS_FRACTION, 0.0, 0.0, 0.0, 0.2095, 0.15);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance stack = RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance
        .calculate(combustion, 473.15, 298.15, 34.0);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance recovery = RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance
        .calculate(stack, 0.60);
    return RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance.calculate(recovery, 0.75);
  }

  private static RefineryHydrotreatingSulfurNitrogenBalance material() {
    return RefineryHydrotreatingSulfurNitrogenBalance.calculate(1000.0, 0.0040867518, 0.001095129, 15.0e-6, 10.0e-6,
        2.0, 4.0);
  }
}
