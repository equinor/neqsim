package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests coupled sulfur/nitrogen fired-heater combustion receipts. */
class RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalanceTest {
  private static final double NON_HYDROGEN_MOLAR_MASS_KG_PER_MOL = 0.0280134;
  private static final double METHANE_CARBON_MASS_FRACTION = 12.011 / 16.043;
  private static final double METHANE_HYDROGEN_MASS_FRACTION = 4.032 / 16.043;

  @Test
  void qualifiesPublicBigHillMethaneElementalScenario() {
    RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance utility = publicUtility(1000.0, 1.0);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance combustion = RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance
        .calculate(utility, METHANE_CARBON_MASS_FRACTION, METHANE_HYDROGEN_MASS_FRACTION, 0.0, 0.0, 0.0, 0.2095, 0.15);

    assertSame(utility, combustion.getUtilityBalance());
    assertEquals(9.206314937124004, combustion.getStoichiometricOxygenKmolPerHour(), 1.0e-12);
    assertEquals(50.53585765008404, combustion.getDryAirKmolPerHour(), 1.0e-10);
    assertEquals(1457.8911687253815, combustion.getDryAirMassFlowKgPerHour(), 1.0e-9);
    assertEquals(4.603157468562002, combustion.getCarbonDioxideKmolPerHour(), 1.0e-12);
    assertEquals(9.206314937124004, combustion.getWaterKmolPerHour(), 1.0e-12);
    assertEquals(45.93270018152204, combustion.getDryFlueGasKmolPerHour(), 1.0e-10);
    assertEquals(55.13901511864604, combustion.getWetFlueGasKmolPerHour(), 1.0e-10);
    assertEquals(0.10021525950729489, combustion.getDryCarbonDioxideMoleFraction(), 1.0e-14);
    assertEquals(0.030064577852188464, combustion.getDryOxygenMoleFraction(), 1.0e-14);
    assertEquals(202.58035703394515, combustion.getDirectCarbonDioxideKgPerHour(), 1.0e-10);
    assertEquals(202.58035703394515, combustion.getDirectCarbonDioxideKgPerTonneFeed(), 1.0e-10);
    assertEquals(1531.7396239935217, combustion.getFlueGasMassFlowKgPerHour(), 1.0e-9);
    assertEquals(0.0, combustion.getMassClosureResidualKgPerHour(), 1.0e-10);
  }

  @Test
  void chsonFuelClosesAndTracksSulfurAndNitrogen() {
    RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance combustion = RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance
        .calculate(publicUtility(1000.0, 1.0), 0.70, 0.10, 0.05, 0.10, 0.05, 0.2095, 0.10);

    assertTrue(combustion.getSulfurDioxideKmolPerHour() > 0.0);
    assertTrue(combustion.getNitrogenKmolPerHour() > 0.0);
    assertTrue(combustion.getDirectCarbonDioxideKgPerHour() > 0.0);
    assertEquals(0.0, combustion.getMassClosureResidualKgPerHour(), 1.0e-9);
  }

  @Test
  void callerRateScalingPreservesNormalizedCarbonDioxide() {
    RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance base = RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance
        .calculate(publicUtility(1000.0, 1.0), METHANE_CARBON_MASS_FRACTION, METHANE_HYDROGEN_MASS_FRACTION, 0.0, 0.0,
            0.0, 0.2095, 0.15);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance doubled = RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance
        .calculate(publicUtility(2000.0, 2.0), METHANE_CARBON_MASS_FRACTION, METHANE_HYDROGEN_MASS_FRACTION, 0.0, 0.0,
            0.0, 0.2095, 0.15);

    assertEquals(2.0 * base.getDirectCarbonDioxideKgPerHour(), doubled.getDirectCarbonDioxideKgPerHour(), 1.0e-10);
    assertEquals(base.getDirectCarbonDioxideKgPerTonneFeed(), doubled.getDirectCarbonDioxideKgPerTonneFeed(), 1.0e-12);
    assertEquals(base.getDryOxygenMoleFraction(), doubled.getDryOxygenMoleFraction(), 1.0e-14);
  }

  @Test
  void coolingCaseProducesZeroCombustionFlow() {
    RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance combustion = RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance
        .calculate(publicUtility(1000.0, 0.01), METHANE_CARBON_MASS_FRACTION, METHANE_HYDROGEN_MASS_FRACTION, 0.0, 0.0,
            0.0, 0.2095, 0.15);

    assertEquals(0.0, combustion.getDryAirKmolPerHour(), 0.0);
    assertEquals(0.0, combustion.getWetFlueGasKmolPerHour(), 0.0);
    assertEquals(0.0, combustion.getDirectCarbonDioxideKgPerHour(), 0.0);
    assertEquals(0.0, combustion.getMassClosureResidualKgPerHour(), 0.0);
  }

  @Test
  void invalidInputsFailClosed() {
    RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance utility = publicUtility(1000.0, 1.0);

    assertThrows(NullPointerException.class, () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance
        .calculate(null, 0.75, 0.25, 0.0, 0.0, 0.0, 0.2095, 0.15));
    assertThrows(IllegalArgumentException.class, () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance
        .calculate(utility, 0.70, 0.20, 0.0, 0.0, 0.0, 0.2095, 0.15));
    assertThrows(IllegalArgumentException.class, () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance
        .calculate(utility, Double.NaN, 0.25, 0.0, 0.0, 0.75, 0.2095, 0.15));
    assertThrows(IllegalArgumentException.class, () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance
        .calculate(utility, 0.75, 0.25, 0.0, 0.0, 0.0, 0.0, 0.15));
    assertThrows(IllegalArgumentException.class, () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance
        .calculate(utility, 0.75, 0.25, 0.0, 0.0, 0.0, 0.2095, -0.01));
    assertThrows(IllegalArgumentException.class, () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance
        .calculate(utility, 0.0, 0.0, 0.0, 1.0, 0.0, 0.2095, 0.15));
  }

  private static RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance publicUtility(
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
    return RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance.calculate(thermal, 0.85, 50.0, 0.40, 3.0);
  }
}
