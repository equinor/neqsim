package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests integrated coupled sulfur/nitrogen operating receipts. */
class RefineryHydrotreatingSulfurNitrogenOperatingReceiptTest {
  private static final double LHV_MJ_PER_KG = 120.0;
  private static final double HYDROGEN_COST_PER_KG = 3.0;
  private static final double EMISSION_FACTOR_KG_CO2E_PER_KG_H2 = 10.0;
  private static final double CARBON_PRICE_PER_TONNE_CO2E = 100.0;

  @Test
  void qualifiesPublicBigHillOperatingReceipt() {
    RefineryHydrotreatingSulfurNitrogenHydrogenEmissionsBalance emissions = publicEmissions(1000.0);

    RefineryHydrotreatingSulfurNitrogenOperatingReceipt receipt = RefineryHydrotreatingSulfurNitrogenOperatingReceipt
        .calculate(emissions);

    assertSame(emissions, receipt.getEmissionsBalance());
    assertEquals(1000.0, receipt.getFeedMassFlowKgPerHour(), 0.0);
    assertEquals(1.219112719, receipt.getFreshHydrogenMassFlowKgPerHour(), 1.0e-9);
    assertEquals(0.040637091, receipt.getFreshHydrogenEnergyMWhPerTonneFeed(), 1.0e-9);
    assertEquals(12.19112719, receipt.getHydrogenSupplyEmissionsKgCo2EquivalentPerHour(), 1.0e-8);
    assertEquals(12.19112719, receipt.getHydrogenSupplyEmissionsKgCo2EquivalentPerTonneFeed(), 1.0e-8);
    assertEquals(3.657338157, receipt.getHydrogenPurchaseCostPerHour(), 1.0e-9);
    assertEquals(1.219112719, receipt.getCarbonCostPerHour(), 1.0e-9);
    assertEquals(4.876450876, receipt.getTotalScenarioCostPerHour(), 1.0e-9);
    assertEquals(3.657338157, receipt.getHydrogenPurchaseCostPerTonneFeed(), 1.0e-9);
    assertEquals(1.219112719, receipt.getCarbonCostPerTonneFeed(), 1.0e-9);
    assertEquals(4.876450876, receipt.getTotalScenarioCostPerTonneFeed(), 1.0e-9);
    assertEquals(0.0, receipt.getTotalScenarioCostClosureResidualPerHour(), 1.0e-15);
  }

  @Test
  void scalesRatesAndPreservesNormalizedResults() {
    RefineryHydrotreatingSulfurNitrogenOperatingReceipt base = RefineryHydrotreatingSulfurNitrogenOperatingReceipt
        .calculate(publicEmissions(1000.0));
    RefineryHydrotreatingSulfurNitrogenOperatingReceipt doubled = RefineryHydrotreatingSulfurNitrogenOperatingReceipt
        .calculate(publicEmissions(2000.0));

    assertEquals(2.0 * base.getFreshHydrogenMassFlowKgPerHour(), doubled.getFreshHydrogenMassFlowKgPerHour(), 1.0e-12);
    assertEquals(2.0 * base.getHydrogenSupplyEmissionsKgCo2EquivalentPerHour(),
        doubled.getHydrogenSupplyEmissionsKgCo2EquivalentPerHour(), 1.0e-12);
    assertEquals(2.0 * base.getTotalScenarioCostPerHour(), doubled.getTotalScenarioCostPerHour(), 1.0e-12);
    assertEquals(base.getFreshHydrogenEnergyMWhPerTonneFeed(), doubled.getFreshHydrogenEnergyMWhPerTonneFeed(),
        1.0e-12);
    assertEquals(base.getHydrogenSupplyEmissionsKgCo2EquivalentPerTonneFeed(),
        doubled.getHydrogenSupplyEmissionsKgCo2EquivalentPerTonneFeed(), 1.0e-12);
    assertEquals(base.getTotalScenarioCostPerTonneFeed(), doubled.getTotalScenarioCostPerTonneFeed(), 1.0e-12);
  }

  @Test
  void zeroScenarioInputsKeepPhysicalEnergyButZeroCostAndEmissions() {
    RefineryHydrotreatingSulfurNitrogenOperatingReceipt receipt = RefineryHydrotreatingSulfurNitrogenOperatingReceipt
        .calculate(publicEmissions(1000.0, 0.0, 0.0, 0.0));

    assertEquals(0.0, receipt.getHydrogenSupplyEmissionsKgCo2EquivalentPerHour(), 0.0);
    assertEquals(0.0, receipt.getHydrogenPurchaseCostPerHour(), 0.0);
    assertEquals(0.0, receipt.getCarbonCostPerHour(), 0.0);
    assertEquals(0.0, receipt.getTotalScenarioCostPerHour(), 0.0);
    assertEquals(0.040637091, receipt.getFreshHydrogenEnergyMWhPerTonneFeed(), 1.0e-9);
  }

  @Test
  void rejectsNullInput() {
    assertThrows(NullPointerException.class, () -> RefineryHydrotreatingSulfurNitrogenOperatingReceipt.calculate(null));
  }

  private static RefineryHydrotreatingSulfurNitrogenHydrogenEmissionsBalance publicEmissions(
      double feedMassFlowKgPerHour) {
    return publicEmissions(feedMassFlowKgPerHour, HYDROGEN_COST_PER_KG, EMISSION_FACTOR_KG_CO2E_PER_KG_H2,
        CARBON_PRICE_PER_TONNE_CO2E);
  }

  private static RefineryHydrotreatingSulfurNitrogenHydrogenEmissionsBalance publicEmissions(
      double feedMassFlowKgPerHour, double hydrogenCostPerKg, double emissionFactorKgCo2ePerKgH2,
      double carbonPricePerTonneCo2e) {
    RefineryHydrotreatingSulfurNitrogenBalance material = RefineryHydrotreatingSulfurNitrogenBalance.calculate(1000.0,
        0.0040867518, 0.001095129, 15.0e-6, 10.0e-6, 2.0, 4.0);
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supply = RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance
        .calculate(material, 1.5, 0.90, 0.0280134);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance recycle = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance
        .calculate(supply, 0.90, 0.10, 0.20, 0.50, 0.05);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance throughput = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance
        .calculate(recycle, feedMassFlowKgPerHour);
    RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance utility = RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance
        .calculate(throughput, LHV_MJ_PER_KG, hydrogenCostPerKg);
    return RefineryHydrotreatingSulfurNitrogenHydrogenEmissionsBalance.calculate(utility, emissionFactorKgCo2ePerKgH2,
        carbonPricePerTonneCo2e);
  }
}
