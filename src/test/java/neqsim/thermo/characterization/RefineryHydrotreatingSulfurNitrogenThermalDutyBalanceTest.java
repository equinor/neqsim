package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests coupled sulfur/nitrogen thermal-duty receipts. */
class RefineryHydrotreatingSulfurNitrogenThermalDutyBalanceTest {
  private static final double NON_HYDROGEN_MOLAR_MASS_KG_PER_MOL = 0.0280134;

  @Test
  void qualifiesPublicBigHillCallerScenario() {
    RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt distribution = publicDistribution(1000.0);
    RefineryHydrotreatingSulfurNitrogenThermalDutyBalance duty = RefineryHydrotreatingSulfurNitrogenThermalDutyBalance
        .calculate(distribution, 100.0, 50.0, 1.0);

    assertSame(distribution, duty.getProductDistributionReceipt());
    assertEquals(0.11310609606334222, duty.getSulfurReactionHeatReleaseMegaWatt(), 1.0e-12);
    assertEquals(0.015071862576669634, duty.getNitrogenReactionHeatReleaseMegaWatt(), 1.0e-12);
    assertEquals(0.12817795864001186, duty.getTotalReactionHeatReleaseMegaWatt(), 1.0e-12);
    assertEquals(0.12817795864001186, duty.getReactionHeatReleaseMegaWattHourPerTonneFeed(), 1.0e-12);
    assertEquals(1.0, duty.getSensibleHeatingMegaWattHourPerTonneFeed(), 1.0e-12);
    assertEquals(0.8718220413599882, duty.getNetExternalDutyMegaWatt(), 1.0e-12);
    assertEquals(0.8718220413599882, duty.getExternalHeatingDutyMegaWatt(), 1.0e-12);
    assertEquals(0.0, duty.getExternalCoolingDutyMegaWatt(), 0.0);
    assertEquals(0.0, duty.getDutyClosureResidualMegaWatt(), 1.0e-15);
  }

  @Test
  void callerRateScalingPreservesNormalizedDuties() {
    RefineryHydrotreatingSulfurNitrogenThermalDutyBalance base = RefineryHydrotreatingSulfurNitrogenThermalDutyBalance
        .calculate(publicDistribution(1000.0), 100.0, 50.0, 1.0);
    RefineryHydrotreatingSulfurNitrogenThermalDutyBalance doubled = RefineryHydrotreatingSulfurNitrogenThermalDutyBalance
        .calculate(publicDistribution(2000.0), 100.0, 50.0, 2.0);

    assertEquals(2.0 * base.getSulfurReactionHeatReleaseMegaWatt(), doubled.getSulfurReactionHeatReleaseMegaWatt(),
        1.0e-12);
    assertEquals(2.0 * base.getExternalHeatingDutyMegaWatt(), doubled.getExternalHeatingDutyMegaWatt(), 1.0e-12);
    assertEquals(base.getReactionHeatReleaseMegaWattHourPerTonneFeed(),
        doubled.getReactionHeatReleaseMegaWattHourPerTonneFeed(), 1.0e-15);
    assertEquals(base.getSensibleHeatingMegaWattHourPerTonneFeed(),
        doubled.getSensibleHeatingMegaWattHourPerTonneFeed(), 1.0e-15);
  }

  @Test
  void zeroReactionFactorsAndCoolingCaseClose() {
    RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt distribution = publicDistribution(1000.0);
    RefineryHydrotreatingSulfurNitrogenThermalDutyBalance zero = RefineryHydrotreatingSulfurNitrogenThermalDutyBalance
        .calculate(distribution, 0.0, 0.0, 1.0);
    RefineryHydrotreatingSulfurNitrogenThermalDutyBalance cooling = RefineryHydrotreatingSulfurNitrogenThermalDutyBalance
        .calculate(distribution, 100.0, 50.0, 0.01);

    assertEquals(0.0, zero.getTotalReactionHeatReleaseMegaWatt(), 0.0);
    assertEquals(1.0, zero.getExternalHeatingDutyMegaWatt(), 0.0);
    assertEquals(0.0, zero.getExternalCoolingDutyMegaWatt(), 0.0);
    assertEquals(0.0, cooling.getExternalHeatingDutyMegaWatt(), 0.0);
    assertEquals(0.11817795864001186, cooling.getExternalCoolingDutyMegaWatt(), 1.0e-12);
    assertEquals(0.0, cooling.getDutyClosureResidualMegaWatt(), 1.0e-15);
  }

  @Test
  void invalidInputsFailClosed() {
    RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt distribution = publicDistribution(1000.0);

    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingSulfurNitrogenThermalDutyBalance.calculate(null, 1.0, 1.0, 1.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenThermalDutyBalance.calculate(distribution, -1.0, 1.0, 1.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenThermalDutyBalance.calculate(distribution, 1.0, Double.NaN, 1.0));
    assertThrows(IllegalArgumentException.class, () -> RefineryHydrotreatingSulfurNitrogenThermalDutyBalance
        .calculate(distribution, 1.0, 1.0, Double.POSITIVE_INFINITY));
  }

  private static RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt publicDistribution(
      double feedMassFlowKgPerHour) {
    RefineryHydrotreatingSulfurNitrogenBalance material = RefineryHydrotreatingSulfurNitrogenBalance.calculate(1000.0,
        0.0040867518, 0.001095129, 15.0e-6, 10.0e-6, 2.0, 4.0);
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supply = RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance
        .calculate(material, 1.5, 0.90, NON_HYDROGEN_MOLAR_MASS_KG_PER_MOL);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance recycle = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance
        .calculate(supply, 0.90, 0.10, 0.20, 0.50, 0.05);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance throughput = RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance
        .calculate(recycle, feedMassFlowKgPerHour);
    return RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt.calculate(throughput);
  }
}
