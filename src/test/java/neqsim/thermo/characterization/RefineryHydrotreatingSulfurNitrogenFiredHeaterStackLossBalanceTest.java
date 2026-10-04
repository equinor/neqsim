package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests coupled sulfur/nitrogen fired-heater stack sensible-loss receipts. */
class RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalanceTest {
  private static final double NON_HYDROGEN_MOLAR_MASS_KG_PER_MOL = 0.0280134;
  private static final double METHANE_CARBON_MASS_FRACTION = 12.011 / 16.043;
  private static final double METHANE_HYDROGEN_MASS_FRACTION = 4.032 / 16.043;

  @Test
  void qualifiesPublicBigHillCallerScenario() {
    RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance combustion = publicCombustion(1000.0, 1.0);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance stack = RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance
        .calculate(combustion, 473.15, 298.15, 34.0);

    assertSame(combustion, stack.getCombustionBalance());
    assertEquals(175.0, stack.getTemperatureDifferenceKelvin(), 0.0);
    assertEquals(0.0911325388766511, stack.getStackSensibleLossMegaWatt(), 1.0e-14);
    assertEquals(0.06271840959864103, stack.getResidualFurnaceLossMegaWatt(), 1.0e-14);
    assertEquals(0.08885145634115478, stack.getStackLossFractionOfFuelChemicalPower(), 1.0e-14);
    assertEquals(0.5923430422743649, stack.getStackLossFractionOfFurnaceLoss(), 1.0e-14);
    assertEquals(0.0611485436588453, stack.getResidualLossFractionOfFuelChemicalPower(), 1.0e-14);
    assertEquals(0.0, stack.getFurnaceLossClosureResidualMegaWatt(), 1.0e-15);
  }

  @Test
  void callerRateScalingPreservesLossFractions() {
    RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance base = RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance
        .calculate(publicCombustion(1000.0, 1.0), 473.15, 298.15, 34.0);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance doubled = RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance
        .calculate(publicCombustion(2000.0, 2.0), 473.15, 298.15, 34.0);

    assertEquals(2.0 * base.getStackSensibleLossMegaWatt(), doubled.getStackSensibleLossMegaWatt(), 1.0e-13);
    assertEquals(base.getStackLossFractionOfFuelChemicalPower(), doubled.getStackLossFractionOfFuelChemicalPower(),
        1.0e-14);
    assertEquals(base.getStackLossFractionOfFurnaceLoss(), doubled.getStackLossFractionOfFurnaceLoss(), 1.0e-14);
  }

  @Test
  void coolingCaseProducesZeroStackLoss() {
    RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance stack = RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance
        .calculate(publicCombustion(1000.0, 0.01), 473.15, 298.15, 34.0);

    assertEquals(0.0, stack.getStackSensibleLossMegaWatt(), 0.0);
    assertEquals(0.0, stack.getResidualFurnaceLossMegaWatt(), 0.0);
    assertEquals(0.0, stack.getStackLossFractionOfFuelChemicalPower(), 0.0);
    assertEquals(0.0, stack.getFurnaceLossClosureResidualMegaWatt(), 0.0);
  }

  @Test
  void invalidInputsFailClosed() {
    RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance combustion = publicCombustion(1000.0, 1.0);

    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance.calculate(null, 473.15, 298.15, 34.0));
    assertThrows(IllegalArgumentException.class, () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance
        .calculate(combustion, 290.0, 298.15, 34.0));
    assertThrows(IllegalArgumentException.class, () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance
        .calculate(combustion, Double.NaN, 298.15, 34.0));
    assertThrows(IllegalArgumentException.class, () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance
        .calculate(combustion, 473.15, 298.15, 0.0));
    assertThrows(IllegalArgumentException.class, () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance
        .calculate(combustion, 1273.15, 298.15, 100.0));
  }

  private static RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance publicCombustion(
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
    return RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance.calculate(utility,
        METHANE_CARBON_MASS_FRACTION, METHANE_HYDROGEN_MASS_FRACTION, 0.0, 0.0, 0.0, 0.2095, 0.15);
  }
}
