package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests net refinery operating receipts after fired-heater heat recovery. */
class RefineryHydrotreatingSulfurNitrogenNetOperatingReceiptTest {
  private static final double NON_HYDROGEN_MOLAR_MASS_KG_PER_MOL = 0.0280134;
  private static final double METHANE_CARBON_MASS_FRACTION = 12.011 / 16.043;
  private static final double METHANE_HYDROGEN_MASS_FRACTION = 4.032 / 16.043;

  @Test
  void qualifiesPublicBigHillNetOperatingReceipt() {
    RefineryHydrotreatingSulfurNitrogenOperatingReceipt hydrogen = hydrogenReceipt(1000.0);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance heat = heatCredit(1000.0, 1.0);
    RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt receipt = RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt
        .calculate(hydrogen, heat, 100.0);

    assertSame(hydrogen, receipt.getHydrogenOperatingReceipt());
    assertSame(heat, receipt.getHeatRecoveryCredit());
    assertEquals(100.0, receipt.getCarbonPricePerTonneCo2Equivalent(), 0.0);
    assertEquals(0.9774263516064652, receipt.getNetFuelEnergyMWhPerTonneFeed(), 1.0e-12);
    assertEquals(1.0180634422423367, receipt.getTotalExternalEnergyMWhPerTonneFeed(), 1.0e-12);
    assertEquals(223.31521913699645, receipt.getTotalEmissionsKgCo2EquivalentPerHour(), 1.0e-9);
    assertEquals(223.31521913699645, receipt.getTotalEmissionsKgCo2EquivalentPerTonneFeed(), 1.0e-9);
    assertEquals(28.149878926266194, receipt.getNetFuelCostPerHour(), 1.0e-12);
    assertEquals(22.331521913699646, receipt.getCombinedCarbonCostPerHour(), 1.0e-9);
    assertEquals(54.13873899696584, receipt.getTotalOperatingCostPerHour(), 1.0e-9);
    assertEquals(54.13873899696584, receipt.getTotalOperatingCostPerTonneFeed(), 1.0e-9);
    assertEquals(0.0, receipt.getEnergyClosureResidualMWhPerTonneFeed(), 1.0e-15);
    assertEquals(0.0, receipt.getEmissionsClosureResidualKgCo2EquivalentPerHour(), 1.0e-15);
    assertEquals(0.0, receipt.getCostClosureResidualPerHour(), 1.0e-15);
  }

  @Test
  void scalesRatesAndPreservesIntensities() {
    RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt base = netReceipt(1000.0, 1.0);
    RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt doubled = netReceipt(2000.0, 2.0);

    assertEquals(2.0 * base.getTotalEmissionsKgCo2EquivalentPerHour(),
        doubled.getTotalEmissionsKgCo2EquivalentPerHour(), 1.0e-9);
    assertEquals(2.0 * base.getTotalOperatingCostPerHour(), doubled.getTotalOperatingCostPerHour(), 1.0e-9);
    assertEquals(base.getTotalExternalEnergyMWhPerTonneFeed(), doubled.getTotalExternalEnergyMWhPerTonneFeed(),
        1.0e-12);
    assertEquals(base.getTotalEmissionsKgCo2EquivalentPerTonneFeed(),
        doubled.getTotalEmissionsKgCo2EquivalentPerTonneFeed(), 1.0e-9);
    assertEquals(base.getTotalOperatingCostPerTonneFeed(), doubled.getTotalOperatingCostPerTonneFeed(), 1.0e-9);
  }

  @Test
  void zeroCarbonPriceRetainsEnergyFuelCostAndEmissions() {
    RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt receipt = RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt
        .calculate(hydrogenReceipt(1000.0), heatCredit(1000.0, 1.0), 0.0);

    assertEquals(0.0, receipt.getCombinedCarbonCostPerHour(), 0.0);
    assertEquals(31.807217083266196, receipt.getTotalOperatingCostPerHour(), 1.0e-9);
    assertEquals(223.31521913699645, receipt.getTotalEmissionsKgCo2EquivalentPerHour(), 1.0e-9);
    assertEquals(1.0180634422423367, receipt.getTotalExternalEnergyMWhPerTonneFeed(), 1.0e-12);
  }

  @Test
  void rejectsInvalidOrInconsistentInputs() {
    RefineryHydrotreatingSulfurNitrogenOperatingReceipt hydrogen = hydrogenReceipt(1000.0);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance heat = heatCredit(1000.0, 1.0);
    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt.calculate(null, heat, 100.0));
    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt.calculate(hydrogen, null, 100.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt.calculate(hydrogen, heat, -1.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt.calculate(hydrogen, heat, Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt
        .calculate(hydrogen, heatCredit(2000.0, 2.0), 100.0));
  }

  private static RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt netReceipt(double feedMassFlowKgPerHour,
      double sensibleHeatingDutyMegaWatt) {
    return RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt.calculate(hydrogenReceipt(feedMassFlowKgPerHour),
        heatCredit(feedMassFlowKgPerHour, sensibleHeatingDutyMegaWatt), 100.0);
  }

  private static RefineryHydrotreatingSulfurNitrogenOperatingReceipt hydrogenReceipt(double feedMassFlowKgPerHour) {
    RefineryHydrotreatingSulfurNitrogenBalance material = material();
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supply = RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance
        .calculate(material, 1.5, 0.90, NON_HYDROGEN_MOLAR_MASS_KG_PER_MOL);
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
