package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests caller-priced variable-cost screening receipts for qualified coupled hydrotreating cases. */
class RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceiptTest {
  private static final double NON_HYDROGEN_MOLAR_MASS_KG_PER_MOL = 0.0280134;
  private static final double METHANE_CARBON_MASS_FRACTION = 12.011 / 16.043;
  private static final double METHANE_HYDROGEN_MASS_FRACTION = 4.032 / 16.043;

  @Test
  void qualifiesPublicBigHillCallerPricedScreeningEconomics() {
    RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt intensity = intensity(1000.0, 1.0, 100.0);
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt receipt = RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt
        .calculate(intensity, 600.0, 100.0, 450.0);

    assertSame(intensity, receipt.getNetProductIntensityReceipt());
    assertEquals(600.0, receipt.getLiquidProductPricePerTonne(), 0.0);
    assertEquals(100.0, receipt.getExportGasPricePerTonne(), 0.0);
    assertEquals(450.0, receipt.getFeedCostPerTonne(), 0.0);
    assertEquals(597.2936687871908, receipt.getLiquidProductValuePerHour(), 1.0e-9);
    assertEquals(0.761202393313295, receipt.getExportGasValuePerHour(), 1.0e-12);
    assertEquals(598.054871180504, receipt.getTotalProductValuePerHour(), 1.0e-9);
    assertEquals(450.0, receipt.getFeedCostPerHour(), 1.0e-12);
    assertEquals(54.13873899696584, receipt.getOperatingCostPerHour(), 1.0e-9);
    assertEquals(504.13873899696586, receipt.getTotalVariableCostPerHour(), 1.0e-9);
    assertEquals(93.9161321835382, receipt.getScreeningMarginPerHour(), 1.0e-9);
    assertEquals(600.7646513932006, receipt.getTotalProductValuePerTonneLiquidProduct(), 1.0e-9);
    assertEquals(506.42298622112304, receipt.getTotalVariableCostPerTonneLiquidProduct(), 1.0e-9);
    assertEquals(94.34166517207751, receipt.getScreeningMarginPerTonneLiquidProduct(), 1.0e-9);
    assertEquals(0.0, receipt.getProductValueClosureResidualPerHour(), 1.0e-12);
    assertEquals(0.0, receipt.getVariableCostClosureResidualPerHour(), 1.0e-12);
    assertEquals(0.0, receipt.getScreeningMarginClosureResidualPerHour(), 1.0e-12);
  }

  @Test
  void throughputScalingPreservesNormalizedEconomics() {
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt base = RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt
        .calculate(intensity(1000.0, 1.0, 100.0), 600.0, 100.0, 450.0);
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt doubled = RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt
        .calculate(intensity(2000.0, 2.0, 100.0), 600.0, 100.0, 450.0);

    assertEquals(2.0 * base.getTotalProductValuePerHour(), doubled.getTotalProductValuePerHour(), 1.0e-9);
    assertEquals(2.0 * base.getTotalVariableCostPerHour(), doubled.getTotalVariableCostPerHour(), 1.0e-9);
    assertEquals(2.0 * base.getScreeningMarginPerHour(), doubled.getScreeningMarginPerHour(), 1.0e-9);
    assertEquals(base.getTotalProductValuePerTonneFeed(), doubled.getTotalProductValuePerTonneFeed(), 1.0e-9);
    assertEquals(base.getScreeningMarginPerTonneLiquidProduct(),
        doubled.getScreeningMarginPerTonneLiquidProduct(), 1.0e-9);
  }

  @Test
  void zeroProductPricesProduceFiniteNegativeScreeningMargin() {
    RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt receipt = RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt
        .calculate(intensity(1000.0, 1.0, 100.0), 0.0, 0.0, 450.0);

    assertEquals(0.0, receipt.getTotalProductValuePerHour(), 0.0);
    assertEquals(-504.13873899696586, receipt.getScreeningMarginPerHour(), 1.0e-9);
    assertEquals(-504.13873899696586, receipt.getScreeningMarginPerTonneFeed(), 1.0e-9);
    assertEquals(0.0, receipt.getScreeningMarginClosureResidualPerHour(), 1.0e-12);
  }

  @Test
  void rejectsMissingOrInvalidCallerInputs() {
    RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt intensity = intensity(1000.0, 1.0, 100.0);

    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt.calculate(null, 600.0, 100.0, 450.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt.calculate(intensity, -1.0, 100.0, 450.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt.calculate(intensity, 600.0,
            Double.NaN, 450.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt.calculate(intensity, 600.0, 100.0,
            Double.POSITIVE_INFINITY));
  }

  private static RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt intensity(
      double feedMassFlowKgPerHour, double sensibleHeatingDutyMegaWatt, double carbonPricePerTonneCo2e) {
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
