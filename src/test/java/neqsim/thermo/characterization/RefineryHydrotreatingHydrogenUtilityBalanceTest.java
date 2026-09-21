package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class RefineryHydrotreatingHydrogenUtilityBalanceTest {
  private static final double LHV_MJ_PER_KG = 120.0;
  private static final double COST_PER_KG = 3.0;

  @Test
  void qualifiesPublicBigHillUtilityReceipt() {
    RefineryHydrotreatingThroughputBalance throughput = publicThroughput(1000.0);

    RefineryHydrotreatingHydrogenUtilityBalance utility =
        RefineryHydrotreatingHydrogenUtilityBalance.calculate(
            throughput, LHV_MJ_PER_KG, COST_PER_KG);

    assertSame(throughput, utility.getThroughputBalance());
    assertEquals(LHV_MJ_PER_KG, utility.getHydrogenLowerHeatingValueMegaJoulePerKg());
    assertEquals(COST_PER_KG, utility.getHydrogenCostPerKg());
    assertEquals(0.549093756, utility.getFreshHydrogenMassFlowKgPerHour(), 1.0e-9);
    assertEquals(0.511975530, utility.getConsumedHydrogenMassFlowKgPerHour(), 1.0e-9);
    assertEquals(0.037118226, utility.getExportHydrogenMassFlowKgPerHour(), 1.0e-9);
    assertEquals(0.018303125, utility.getFreshHydrogenChemicalPowerMegaWatt(), 1.0e-9);
    assertEquals(0.017065851, utility.getConsumedHydrogenChemicalPowerMegaWatt(), 1.0e-9);
    assertEquals(0.001237274, utility.getExportHydrogenChemicalPowerMegaWatt(), 1.0e-9);
    assertEquals(0.0, utility.getHydrogenEnergyBalanceResidualMegaWatt(), 1.0e-14);
    assertEquals(1.647281268, utility.getFreshHydrogenCostPerHour(), 1.0e-9);
    assertEquals(1.647281268, utility.getFreshHydrogenCostPerTonneFeed(), 1.0e-9);
    assertEquals(0.932400932, utility.getFreshHydrogenUtilizationFraction(), 1.0e-9);
  }

  @Test
  void scalesRatesAndPreservesNormalizedCost() {
    RefineryHydrotreatingHydrogenUtilityBalance base =
        RefineryHydrotreatingHydrogenUtilityBalance.calculate(
            publicThroughput(1000.0), LHV_MJ_PER_KG, COST_PER_KG);
    RefineryHydrotreatingHydrogenUtilityBalance doubled =
        RefineryHydrotreatingHydrogenUtilityBalance.calculate(
            publicThroughput(2000.0), LHV_MJ_PER_KG, COST_PER_KG);

    assertEquals(
        2.0 * base.getFreshHydrogenMassFlowKgPerHour(),
        doubled.getFreshHydrogenMassFlowKgPerHour(),
        1.0e-12);
    assertEquals(
        2.0 * base.getFreshHydrogenChemicalPowerMegaWatt(),
        doubled.getFreshHydrogenChemicalPowerMegaWatt(),
        1.0e-12);
    assertEquals(
        2.0 * base.getFreshHydrogenCostPerHour(),
        doubled.getFreshHydrogenCostPerHour(),
        1.0e-12);
    assertEquals(
        base.getFreshHydrogenCostPerTonneFeed(),
        doubled.getFreshHydrogenCostPerTonneFeed(),
        1.0e-12);
  }

  @Test
  void keepsZeroRemovalUtilityAtZero() {
    RefineryHydrotreatingSulfurBalance sulfur =
        RefineryHydrotreatingSulfurBalance.calculate(1000.0, 0.0, 0.0, 2.0);
    RefineryHydrotreatingHydrogenSupplyBalance supply =
        RefineryHydrotreatingHydrogenSupplyBalance.calculate(
            sulfur, 1.5, 0.90, 0.0280134);
    RefineryHydrotreatingHydrogenRecycleBalance recycle =
        RefineryHydrotreatingHydrogenRecycleBalance.calculate(
            supply, 0.90, 0.10, 0.50, 0.05);
    RefineryHydrotreatingThroughputBalance throughput =
        RefineryHydrotreatingThroughputBalance.calculate(recycle, 1000.0);

    RefineryHydrotreatingHydrogenUtilityBalance utility =
        RefineryHydrotreatingHydrogenUtilityBalance.calculate(
            throughput, LHV_MJ_PER_KG, COST_PER_KG);

    assertEquals(0.0, utility.getFreshHydrogenMassFlowKgPerHour(), 0.0);
    assertEquals(0.0, utility.getConsumedHydrogenChemicalPowerMegaWatt(), 0.0);
    assertEquals(0.0, utility.getFreshHydrogenCostPerHour(), 0.0);
    assertEquals(0.0, utility.getFreshHydrogenCostPerTonneFeed(), 0.0);
    assertEquals(0.0, utility.getFreshHydrogenUtilizationFraction(), 0.0);
  }

  @Test
  void rejectsInvalidInputs() {
    RefineryHydrotreatingThroughputBalance throughput = publicThroughput(1000.0);

    assertThrows(
        NullPointerException.class,
        () ->
            RefineryHydrotreatingHydrogenUtilityBalance.calculate(
                null, LHV_MJ_PER_KG, COST_PER_KG));
    assertThrows(
        IllegalArgumentException.class,
        () -> RefineryHydrotreatingHydrogenUtilityBalance.calculate(throughput, 0.0, COST_PER_KG));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            RefineryHydrotreatingHydrogenUtilityBalance.calculate(
                throughput, Double.NaN, COST_PER_KG));
    assertThrows(
        IllegalArgumentException.class,
        () -> RefineryHydrotreatingHydrogenUtilityBalance.calculate(throughput, LHV_MJ_PER_KG, -1.0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            RefineryHydrotreatingHydrogenUtilityBalance.calculate(
                throughput, LHV_MJ_PER_KG, Double.POSITIVE_INFINITY));
  }

  private static RefineryHydrotreatingThroughputBalance publicThroughput(
      double feedMassFlowKgPerHour) {
    RefineryHydrotreatingSulfurBalance sulfur =
        RefineryHydrotreatingSulfurBalance.calculate(
            1000.0, 0.0040867518, 15.0e-6, 2.0);
    RefineryHydrotreatingHydrogenSupplyBalance supply =
        RefineryHydrotreatingHydrogenSupplyBalance.calculate(
            sulfur, 1.5, 0.90, 0.0280134);
    RefineryHydrotreatingHydrogenRecycleBalance recycle =
        RefineryHydrotreatingHydrogenRecycleBalance.calculate(
            supply, 0.90, 0.10, 0.50, 0.05);
    return RefineryHydrotreatingThroughputBalance.calculate(
        recycle, feedMassFlowKgPerHour);
  }
}
