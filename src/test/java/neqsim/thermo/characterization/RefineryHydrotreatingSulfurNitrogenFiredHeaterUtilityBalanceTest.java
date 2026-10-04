package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests coupled sulfur/nitrogen fired-heater utility receipts. */
class RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalanceTest {
  private static final double NON_HYDROGEN_MOLAR_MASS_KG_PER_MOL = 0.0280134;

  @Test
  void qualifiesPublicBigHillCallerScenario() {
    RefineryHydrotreatingSulfurNitrogenThermalDutyBalance thermal = publicThermalDuty(1000.0, 1.0);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance heater = RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance
        .calculate(thermal, 0.85, 50.0, 0.40, 3.0);

    assertSame(thermal, heater.getThermalDutyBalance());
    assertEquals(0.8718220413599882, heater.getDeliveredHeatingDutyMegaWatt(), 1.0e-12);
    assertEquals(1.0256729898352803, heater.getFuelChemicalPowerMegaWatt(), 1.0e-12);
    assertEquals(0.15385094847529213, heater.getFurnaceLossMegaWatt(), 1.0e-12);
    assertEquals(73.84845526814019, heater.getFuelMassFlowKgPerHour(), 1.0e-10);
    assertEquals(73.84845526814019, heater.getFuelMassKgPerTonneFeed(), 1.0e-10);
    assertEquals(29.539382107256074, heater.getFuelCostPerHour(), 1.0e-10);
    assertEquals(29.539382107256074, heater.getFuelCostPerTonneFeed(), 1.0e-10);
    assertEquals(221.54536580442056, heater.getFuelEmissionsKgCo2EquivalentPerHour(), 1.0e-10);
    assertEquals(221.54536580442056, heater.getFuelEmissionsKgCo2EquivalentPerTonneFeed(), 1.0e-10);
    assertEquals(0.0, heater.getHeatingDeliveryResidualMegaWatt(), 1.0e-15);
  }

  @Test
  void callerRateScalingPreservesNormalizedResults() {
    RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance base = RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance
        .calculate(publicThermalDuty(1000.0, 1.0), 0.85, 50.0, 0.40, 3.0);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance doubled = RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance
        .calculate(publicThermalDuty(2000.0, 2.0), 0.85, 50.0, 0.40, 3.0);

    assertEquals(2.0 * base.getFuelMassFlowKgPerHour(), doubled.getFuelMassFlowKgPerHour(), 1.0e-10);
    assertEquals(base.getFuelMassKgPerTonneFeed(), doubled.getFuelMassKgPerTonneFeed(), 1.0e-12);
    assertEquals(base.getFuelCostPerTonneFeed(), doubled.getFuelCostPerTonneFeed(), 1.0e-12);
    assertEquals(base.getFuelEmissionsKgCo2EquivalentPerTonneFeed(),
        doubled.getFuelEmissionsKgCo2EquivalentPerTonneFeed(), 1.0e-12);
  }

  @Test
  void coolingCaseRequiresNoFiredHeaterFuel() {
    RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance heater = RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance
        .calculate(publicThermalDuty(1000.0, 0.01), 0.85, 50.0, 0.40, 3.0);

    assertEquals(0.0, heater.getDeliveredHeatingDutyMegaWatt(), 0.0);
    assertEquals(0.11817795864001186, heater.getCoolingDutyMegaWatt(), 1.0e-12);
    assertEquals(0.0, heater.getFuelMassFlowKgPerHour(), 0.0);
    assertEquals(0.0, heater.getFuelCostPerHour(), 0.0);
    assertEquals(0.0, heater.getFuelEmissionsKgCo2EquivalentPerHour(), 0.0);
    assertEquals(0.0, heater.getHeatingDeliveryResidualMegaWatt(), 0.0);
  }

  @Test
  void invalidInputsFailClosed() {
    RefineryHydrotreatingSulfurNitrogenThermalDutyBalance thermal = publicThermalDuty(1000.0, 1.0);

    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance.calculate(null, 0.85, 50.0, 0.40, 3.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance.calculate(thermal, 0.0, 50.0, 0.40, 3.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance.calculate(thermal, 1.01, 50.0, 0.40, 3.0));
    assertThrows(IllegalArgumentException.class, () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance
        .calculate(thermal, 0.85, Double.NaN, 0.40, 3.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance.calculate(thermal, 0.85, 50.0, -0.01, 3.0));
    assertThrows(IllegalArgumentException.class, () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance
        .calculate(thermal, 0.85, 50.0, 0.40, Double.POSITIVE_INFINITY));
  }

  private static RefineryHydrotreatingSulfurNitrogenThermalDutyBalance publicThermalDuty(double feedMassFlowKgPerHour,
      double sensibleHeatingDutyMegaWatt) {
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
    return RefineryHydrotreatingSulfurNitrogenThermalDutyBalance.calculate(distribution, 100.0, 50.0,
        sensibleHeatingDutyMegaWatt);
  }
}
