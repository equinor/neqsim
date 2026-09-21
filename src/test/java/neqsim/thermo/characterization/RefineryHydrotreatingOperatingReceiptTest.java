package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RefineryHydrotreatingOperatingReceiptTest {
  private static final double LHV_MJ_PER_KG = 120.0;
  private static final double HYDROGEN_COST_PER_KG = 3.0;
  private static final double EMISSION_FACTOR_KG_CO2E_PER_KG_H2 = 10.0;
  private static final double CARBON_PRICE_PER_TONNE_CO2E = 100.0;

  @Test
  void qualifiesPublicBigHillOperatingReceipt() {
    RefineryHydrotreatingHydrogenEmissionsBalance emissions = publicEmissions(1000.0);

    RefineryHydrotreatingOperatingReceipt receipt =
        RefineryHydrotreatingOperatingReceipt.calculate(emissions);

    assertSame(emissions, receipt.getEmissionsBalance());
    assertEquals(1000.0, receipt.getFeedMassFlowKgPerHour(), 0.0);
    assertEquals(0.549093756, receipt.getFreshHydrogenMassFlowKgPerHour(), 1.0e-9);
    assertEquals(0.018303125, receipt.getFreshHydrogenEnergyMWhPerTonneFeed(), 1.0e-9);
    assertEquals(
        5.49093756, receipt.getHydrogenSupplyEmissionsKgCo2EquivalentPerHour(), 1.0e-8);
    assertEquals(
        5.49093756,
        receipt.getHydrogenSupplyEmissionsKgCo2EquivalentPerTonneFeed(),
        1.0e-8);
    assertEquals(1.647281268, receipt.getHydrogenPurchaseCostPerHour(), 1.0e-9);
    assertEquals(0.549093756, receipt.getCarbonCostPerHour(), 1.0e-9);
    assertEquals(2.196375024, receipt.getTotalScenarioCostPerHour(), 1.0e-9);
    assertEquals(1.647281268, receipt.getHydrogenPurchaseCostPerTonneFeed(), 1.0e-9);
    assertEquals(0.549093756, receipt.getCarbonCostPerTonneFeed(), 1.0e-9);
    assertEquals(2.196375024, receipt.getTotalScenarioCostPerTonneFeed(), 1.0e-9);
    assertEquals(0.0, receipt.getTotalScenarioCostClosureResidualPerHour(), 1.0e-15);
  }

  @Test
  void scalesRatesAndPreservesNormalizedResults() {
    RefineryHydrotreatingOperatingReceipt base =
        RefineryHydrotreatingOperatingReceipt.calculate(publicEmissions(1000.0));
    RefineryHydrotreatingOperatingReceipt doubled =
        RefineryHydrotreatingOperatingReceipt.calculate(publicEmissions(2000.0));

    assertEquals(
        2.0 * base.getFreshHydrogenMassFlowKgPerHour(),
        doubled.getFreshHydrogenMassFlowKgPerHour(),
        1.0e-12);
    assertEquals(
        2.0 * base.getHydrogenSupplyEmissionsKgCo2EquivalentPerHour(),
        doubled.getHydrogenSupplyEmissionsKgCo2EquivalentPerHour(),
        1.0e-12);
    assertEquals(
        2.0 * base.getTotalScenarioCostPerHour(),
        doubled.getTotalScenarioCostPerHour(),
        1.0e-12);
    assertEquals(
        base.getFreshHydrogenEnergyMWhPerTonneFeed(),
        doubled.getFreshHydrogenEnergyMWhPerTonneFeed(),
        1.0e-12);
    assertEquals(
        base.getHydrogenSupplyEmissionsKgCo2EquivalentPerTonneFeed(),
        doubled.getHydrogenSupplyEmissionsKgCo2EquivalentPerTonneFeed(),
        1.0e-12);
    assertEquals(
        base.getTotalScenarioCostPerTonneFeed(),
        doubled.getTotalScenarioCostPerTonneFeed(),
        1.0e-12);
  }

  @Test
  void zeroScenarioInputsKeepPhysicalEnergyButZeroCostAndEmissions() {
    RefineryHydrotreatingOperatingReceipt receipt =
        RefineryHydrotreatingOperatingReceipt.calculate(publicEmissions(1000.0, 0.0, 0.0, 0.0));

    assertEquals(0.0, receipt.getHydrogenSupplyEmissionsKgCo2EquivalentPerHour(), 0.0);
    assertEquals(0.0, receipt.getHydrogenPurchaseCostPerHour(), 0.0);
    assertEquals(0.0, receipt.getCarbonCostPerHour(), 0.0);
    assertEquals(0.0, receipt.getTotalScenarioCostPerHour(), 0.0);
    assertEquals(0.018303125, receipt.getFreshHydrogenEnergyMWhPerTonneFeed(), 1.0e-9);
  }

  @Test
  void rejectsNullInput() {
    assertThrows(
        NullPointerException.class, () -> RefineryHydrotreatingOperatingReceipt.calculate(null));
  }

  private static RefineryHydrotreatingHydrogenEmissionsBalance publicEmissions(
      double feedMassFlowKgPerHour) {
    return publicEmissions(
        feedMassFlowKgPerHour,
        HYDROGEN_COST_PER_KG,
        EMISSION_FACTOR_KG_CO2E_PER_KG_H2,
        CARBON_PRICE_PER_TONNE_CO2E);
  }

  private static RefineryHydrotreatingHydrogenEmissionsBalance publicEmissions(
      double feedMassFlowKgPerHour,
      double hydrogenCostPerKg,
      double emissionFactorKgCo2ePerKgH2,
      double carbonPricePerTonneCo2e) {
    RefineryHydrotreatingSulfurBalance sulfur =
        RefineryHydrotreatingSulfurBalance.calculate(1000.0, 0.0040867518, 15.0e-6, 2.0);
    RefineryHydrotreatingHydrogenSupplyBalance supply =
        RefineryHydrotreatingHydrogenSupplyBalance.calculate(sulfur, 1.5, 0.90, 0.0280134);
    RefineryHydrotreatingHydrogenRecycleBalance recycle =
        RefineryHydrotreatingHydrogenRecycleBalance.calculate(
            supply, 0.90, 0.10, 0.50, 0.05);
    RefineryHydrotreatingThroughputBalance throughput =
        RefineryHydrotreatingThroughputBalance.calculate(recycle, feedMassFlowKgPerHour);
    RefineryHydrotreatingHydrogenUtilityBalance utility =
        RefineryHydrotreatingHydrogenUtilityBalance.calculate(
            throughput, LHV_MJ_PER_KG, hydrogenCostPerKg);
    return RefineryHydrotreatingHydrogenEmissionsBalance.calculate(
        utility, emissionFactorKgCo2ePerKgH2, carbonPricePerTonneCo2e);
  }
}
