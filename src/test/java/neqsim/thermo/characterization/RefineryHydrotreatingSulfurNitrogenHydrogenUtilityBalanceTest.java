package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests coupled sulfur/nitrogen hydrogen utility receipts. */
class RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalanceTest {
  private static final double LHV_MJ_PER_KG = 120.0;
  private static final double COST_PER_KG = 3.0;
  private static final double NITROGEN_MOLAR_MASS_KG_PER_MOL = 0.0280134;

  @Test
  void qualifiesPublicBigHillCoupledUtilityReceipt() {
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance throughput = publicThroughput(1000.0);

    RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance utility = RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance
        .calculate(throughput, LHV_MJ_PER_KG, COST_PER_KG);

    assertSame(throughput, utility.getThroughputBalance());
    assertEquals(LHV_MJ_PER_KG, utility.getHydrogenLowerHeatingValueMegaJoulePerKg());
    assertEquals(COST_PER_KG, utility.getHydrogenCostPerKg());
    assertEquals(1.219112719, utility.getFreshHydrogenMassFlowKgPerHour(), 1.0e-9);
    assertEquals(1.136701836, utility.getConsumedHydrogenMassFlowKgPerHour(), 1.0e-9);
    assertEquals(0.082410883, utility.getExportHydrogenMassFlowKgPerHour(), 1.0e-9);
    assertEquals(0.040637091, utility.getFreshHydrogenChemicalPowerMegaWatt(), 1.0e-9);
    assertEquals(0.037890061, utility.getConsumedHydrogenChemicalPowerMegaWatt(), 1.0e-9);
    assertEquals(0.002747029, utility.getExportHydrogenChemicalPowerMegaWatt(), 1.0e-9);
    assertEquals(0.0, utility.getHydrogenEnergyBalanceResidualMegaWatt(), 1.0e-14);
    assertEquals(3.657338157, utility.getFreshHydrogenCostPerHour(), 1.0e-9);
    assertEquals(3.657338157, utility.getFreshHydrogenCostPerTonneFeed(), 1.0e-9);
    assertEquals(0.932400932, utility.getFreshHydrogenUtilizationFraction(), 1.0e-9);
  }

  @Test
  void scalesRatesAndPreservesNormalizedCost() {
    RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance base = RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance
        .calculate(publicThroughput(1000.0), LHV_MJ_PER_KG, COST_PER_KG);
    RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance doubled = RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance
        .calculate(publicThroughput(2000.0), LHV_MJ_PER_KG, COST_PER_KG);

    assertEquals(2.0 * base.getFreshHydrogenMassFlowKgPerHour(), doubled.getFreshHydrogenMassFlowKgPerHour(), 1.0e-12);
    assertEquals(2.0 * base.getFreshHydrogenChemicalPowerMegaWatt(), doubled.getFreshHydrogenChemicalPowerMegaWatt(),
        1.0e-12);
    assertEquals(2.0 * base.getFreshHydrogenCostPerHour(), doubled.getFreshHydrogenCostPerHour(), 1.0e-12);
    assertEquals(base.getFreshHydrogenCostPerTonneFeed(), doubled.getFreshHydrogenCostPerTonneFeed(), 1.0e-12);
  }

  @Test
  void keepsZeroRemovalUtilityAtZero() {
    RefineryHydrotreatingSulfurNitrogenBalance material = RefineryHydrotreatingSulfurNitrogenBalance.calculate(1000.0,
        0.0, 0.0, 0.0, 0.0, 2.0, 4.0);
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supply = RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance
        .calculate(material, 1.5, 0.90, NITROGEN_MOLAR_MASS_KG_PER_MOL);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance recycle = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance
        .calculate(supply, 0.90, 0.10, 0.20, 0.50, 0.05);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance throughput = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance
        .calculate(recycle, 1000.0);

    RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance utility = RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance
        .calculate(throughput, LHV_MJ_PER_KG, COST_PER_KG);

    assertEquals(0.0, utility.getFreshHydrogenMassFlowKgPerHour(), 0.0);
    assertEquals(0.0, utility.getConsumedHydrogenChemicalPowerMegaWatt(), 0.0);
    assertEquals(0.0, utility.getFreshHydrogenCostPerHour(), 0.0);
    assertEquals(0.0, utility.getFreshHydrogenCostPerTonneFeed(), 0.0);
    assertEquals(0.0, utility.getFreshHydrogenUtilizationFraction(), 0.0);
  }

  @Test
  void rejectsInvalidInputs() {
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance throughput = publicThroughput(1000.0);

    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance.calculate(null, LHV_MJ_PER_KG, COST_PER_KG));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance.calculate(throughput, 0.0, COST_PER_KG));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance.calculate(throughput, Double.NaN, COST_PER_KG));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance.calculate(throughput, LHV_MJ_PER_KG, -1.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance.calculate(throughput, LHV_MJ_PER_KG,
            Double.POSITIVE_INFINITY));
  }

  private static RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance publicThroughput(
      double feedMassFlowKgPerHour) {
    RefineryHydrotreatingSulfurNitrogenBalance material = RefineryHydrotreatingSulfurNitrogenBalance.calculate(1000.0,
        0.0040867518, 0.001095129, 15.0e-6, 10.0e-6, 2.0, 4.0);
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supply = RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance
        .calculate(material, 1.5, 0.90, NITROGEN_MOLAR_MASS_KG_PER_MOL);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance recycle = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance
        .calculate(supply, 0.90, 0.10, 0.20, 0.50, 0.05);
    return RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance.calculate(recycle,
        feedMassFlowKgPerHour);
  }
}
