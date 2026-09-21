package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RefineryHydrotreatingHydrogenEmissionsBalanceTest {
  private static final double LHV_MJ_PER_KG = 120.0;
  private static final double HYDROGEN_COST_PER_KG = 3.0;
  private static final double EMISSION_FACTOR_KG_CO2E_PER_KG_H2 = 10.0;
  private static final double CARBON_PRICE_PER_TONNE_CO2E = 100.0;

  @Test
  void qualifiesPublicBigHillEmissionsReceipt() {
    RefineryHydrotreatingHydrogenUtilityBalance utility = publicUtility(1000.0);

    RefineryHydrotreatingHydrogenEmissionsBalance emissions =
        RefineryHydrotreatingHydrogenEmissionsBalance.calculate(
            utility, EMISSION_FACTOR_KG_CO2E_PER_KG_H2, CARBON_PRICE_PER_TONNE_CO2E);

    assertSame(utility, emissions.getUtilityBalance());
    assertEquals(
        EMISSION_FACTOR_KG_CO2E_PER_KG_H2,
        emissions.getHydrogenSupplyEmissionFactorKgCo2EquivalentPerKgHydrogen());
    assertEquals(
        CARBON_PRICE_PER_TONNE_CO2E, emissions.getCarbonPricePerTonneCo2Equivalent());
    assertEquals(
        5.49093756, emissions.getHydrogenSupplyEmissionsKgCo2EquivalentPerHour(), 1.0e-8);
    assertEquals(
        5.49093756,
        emissions.getHydrogenSupplyEmissionsKgCo2EquivalentPerTonneFeed(),
        1.0e-8);
    assertEquals(0.549093756, emissions.getCarbonCostPerHour(), 1.0e-9);
    assertEquals(0.549093756, emissions.getCarbonCostPerTonneFeed(), 1.0e-9);
    assertEquals(
        utility.getFreshHydrogenMassFlowKgPerHour() * EMISSION_FACTOR_KG_CO2E_PER_KG_H2,
        emissions.getHydrogenSupplyEmissionsKgCo2EquivalentPerHour(),
        0.0);
  }

  @Test
  void scalesRatesAndPreservesNormalizedIntensities() {
    RefineryHydrotreatingHydrogenEmissionsBalance base =
        RefineryHydrotreatingHydrogenEmissionsBalance.calculate(
            publicUtility(1000.0),
            EMISSION_FACTOR_KG_CO2E_PER_KG_H2,
            CARBON_PRICE_PER_TONNE_CO2E);
    RefineryHydrotreatingHydrogenEmissionsBalance doubled =
        RefineryHydrotreatingHydrogenEmissionsBalance.calculate(
            publicUtility(2000.0),
            EMISSION_FACTOR_KG_CO2E_PER_KG_H2,
            CARBON_PRICE_PER_TONNE_CO2E);

    assertEquals(
        2.0 * base.getHydrogenSupplyEmissionsKgCo2EquivalentPerHour(),
        doubled.getHydrogenSupplyEmissionsKgCo2EquivalentPerHour(),
        1.0e-12);
    assertEquals(
        base.getHydrogenSupplyEmissionsKgCo2EquivalentPerTonneFeed(),
        doubled.getHydrogenSupplyEmissionsKgCo2EquivalentPerTonneFeed(),
        1.0e-12);
    assertEquals(
        2.0 * base.getCarbonCostPerHour(), doubled.getCarbonCostPerHour(), 1.0e-12);
    assertEquals(
        base.getCarbonCostPerTonneFeed(), doubled.getCarbonCostPerTonneFeed(), 1.0e-12);
  }

  @Test
  void zeroScenarioInputsProduceZeroReceipt() {
    RefineryHydrotreatingHydrogenEmissionsBalance zero =
        RefineryHydrotreatingHydrogenEmissionsBalance.calculate(
            publicUtility(1000.0), 0.0, 0.0);

    assertEquals(0.0, zero.getHydrogenSupplyEmissionsKgCo2EquivalentPerHour(), 0.0);
    assertEquals(
        0.0, zero.getHydrogenSupplyEmissionsKgCo2EquivalentPerTonneFeed(), 0.0);
    assertEquals(0.0, zero.getCarbonCostPerHour(), 0.0);
    assertEquals(0.0, zero.getCarbonCostPerTonneFeed(), 0.0);
  }

  @Test
  void rejectsInvalidInputs() {
    RefineryHydrotreatingHydrogenUtilityBalance utility = publicUtility(1000.0);

    assertThrows(
        NullPointerException.class,
        () ->
            RefineryHydrotreatingHydrogenEmissionsBalance.calculate(
                null, EMISSION_FACTOR_KG_CO2E_PER_KG_H2, CARBON_PRICE_PER_TONNE_CO2E));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            RefineryHydrotreatingHydrogenEmissionsBalance.calculate(
                utility, -1.0, CARBON_PRICE_PER_TONNE_CO2E));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            RefineryHydrotreatingHydrogenEmissionsBalance.calculate(
                utility, Double.NaN, CARBON_PRICE_PER_TONNE_CO2E));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            RefineryHydrotreatingHydrogenEmissionsBalance.calculate(
                utility, EMISSION_FACTOR_KG_CO2E_PER_KG_H2, -1.0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            RefineryHydrotreatingHydrogenEmissionsBalance.calculate(
                utility, EMISSION_FACTOR_KG_CO2E_PER_KG_H2, Double.POSITIVE_INFINITY));
  }

  private static RefineryHydrotreatingHydrogenUtilityBalance publicUtility(
      double feedMassFlowKgPerHour) {
    RefineryHydrotreatingSulfurBalance sulfur =
        RefineryHydrotreatingSulfurBalance.calculate(1000.0, 0.0040867518, 15.0e-6, 2.0);
    RefineryHydrotreatingHydrogenSupplyBalance supply =
        RefineryHydrotreatingHydrogenSupplyBalance.calculate(sulfur, 1.5, 0.90, 0.0280134);
    RefineryHydrotreatingHydrogenRecycleBalance recycle =
        RefineryHydrotreatingHydrogenRecycleBalance.calculate(
            supply, 0.90, 0.10, 0.50, 0.05);
    RefineryHydrotreatingThroughputBalance throughput =
        RefineryHydrotreatingThroughputBalance.calculate(recycle, feedMassFlowKgPerHour);
    return RefineryHydrotreatingHydrogenUtilityBalance.calculate(
        throughput, LHV_MJ_PER_KG, HYDROGEN_COST_PER_KG);
  }
}
