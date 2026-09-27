package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests caller-owned fired-heater recovered-heat fuel credits. */
class RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalanceTest {
  private static final double NON_HYDROGEN_MOLAR_MASS_KG_PER_MOL = 0.0280134;
  private static final double METHANE_CARBON_MASS_FRACTION = 12.011 / 16.043;
  private static final double METHANE_HYDROGEN_MASS_FRACTION = 4.032 / 16.043;

  @Test
  void qualifiesPublicBigHillCallerScenario() {
    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance recovery = publicRecovery(1000.0, 1.0);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance credit = RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance
        .calculate(recovery, 0.75);

    assertSame(recovery, credit.getHeatRecoveryBalance());
    assertEquals(0.75, credit.getCallerSpecifiedUtilizationFraction(), 0.0);
    assertEquals(0.041009642494492994, credit.getUtilizedRecoveredHeatMegaWatt(), 1.0e-14);
    assertEquals(0.013669880831497665, credit.getUnutilizedRecoveredHeatMegaWatt(), 1.0e-14);
    assertEquals(0.04824663822881529, credit.getAvoidedFuelChemicalPowerMegaWatt(), 1.0e-14);
    assertEquals(3.473757952474701, credit.getAvoidedFuelMassFlowKgPerHour(), 1.0e-12);
    assertEquals(1.3895031809898803, credit.getAvoidedFuelCostPerHour(), 1.0e-12);
    assertEquals(10.421273857424103, credit.getAvoidedFuelEmissionsKgCo2EquivalentPerHour(), 1.0e-12);
    assertEquals(70.37469731566549, credit.getNetFuelMassFlowKgPerHour(), 1.0e-12);
    assertEquals(28.149878926266194, credit.getNetFuelCostPerHour(), 1.0e-12);
    assertEquals(211.12409194699646, credit.getNetFuelEmissionsKgCo2EquivalentPerHour(), 1.0e-12);
    assertEquals(0.0, credit.getRecoveredHeatUtilizationClosureResidualMegaWatt(), 1.0e-15);
    assertEquals(0.0, credit.getFuelCreditClosureResidualMegaWatt(), 1.0e-15);
  }

  @Test
  void rateScalingPreservesNormalizedCredits() {
    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance base = RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance
        .calculate(publicRecovery(1000.0, 1.0), 0.75);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance doubled = RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance
        .calculate(publicRecovery(2000.0, 2.0), 0.75);

    assertEquals(2.0 * base.getAvoidedFuelMassFlowKgPerHour(), doubled.getAvoidedFuelMassFlowKgPerHour(), 1.0e-12);
    assertEquals(base.getAvoidedFuelMassKgPerTonneFeed(), doubled.getAvoidedFuelMassKgPerTonneFeed(), 1.0e-12);
    assertEquals(base.getAvoidedFuelCostPerTonneFeed(), doubled.getAvoidedFuelCostPerTonneFeed(), 1.0e-12);
    assertEquals(base.getAvoidedFuelEmissionsKgCo2EquivalentPerTonneFeed(),
        doubled.getAvoidedFuelEmissionsKgCo2EquivalentPerTonneFeed(), 1.0e-12);
  }

  @Test
  void utilizationBoundsAndCoolingCaseClose() {
    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance recovery = publicRecovery(1000.0, 1.0);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance none = RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance
        .calculate(recovery, 0.0);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance all = RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance
        .calculate(recovery, 1.0);

    assertEquals(0.0, none.getAvoidedFuelMassFlowKgPerHour(), 0.0);
    assertEquals(recovery.getRecoveredHeatMegaWatt(), none.getUnutilizedRecoveredHeatMegaWatt(), 0.0);
    assertEquals(0.0, all.getUnutilizedRecoveredHeatMegaWatt(), 0.0);
    assertEquals(recovery.getRecoveredHeatMegaWatt(), all.getUtilizedRecoveredHeatMegaWatt(), 0.0);

    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance cooling = RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance
        .calculate(publicRecovery(1000.0, 0.01), 0.75);
    assertEquals(0.0, cooling.getUtilizedRecoveredHeatMegaWatt(), 0.0);
    assertEquals(0.0, cooling.getAvoidedFuelMassFlowKgPerHour(), 0.0);
    assertEquals(0.0, cooling.getNetFuelMassFlowKgPerHour(), 0.0);
  }

  @Test
  void invalidInputsFailClosed() {
    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance recovery = publicRecovery(1000.0, 1.0);
    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance.calculate(null, 0.75));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance.calculate(recovery, -0.01));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance.calculate(recovery, 1.01));
    assertThrows(IllegalArgumentException.class, () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance
        .calculate(recovery, Double.NaN));
  }

  private static RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance publicRecovery(
      double feedMassFlowKgPerHour, double sensibleHeatingDutyMegaWatt) {
    RefineryHydrotreatingSulfurNitrogenBalance material = RefineryHydrotreatingSulfurNitrogenBalance.calculate(1000.0,
        0.0040867518, 0.001095129, 15.0e-6, 10.0e-6, 2.0, 4.0);
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supply = RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance
        .calculate(material, 1.5, 0.90, NON_HYDROGEN_MOLAR_MASS_KG_PER_MOL);
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
    return RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance.calculate(stack, 0.60);
  }
}
