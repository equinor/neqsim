package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests caller-owned fired-heater stack heat-recovery receipts. */
class RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalanceTest {
  private static final double NON_HYDROGEN_MOLAR_MASS_KG_PER_MOL = 0.0280134;
  private static final double METHANE_CARBON_MASS_FRACTION = 12.011 / 16.043;
  private static final double METHANE_HYDROGEN_MASS_FRACTION = 4.032 / 16.043;

  @Test
  void qualifiesPublicBigHillCallerScenario() {
    RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance stack = publicStackLoss(1000.0, 1.0);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance recovery =
        RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance.calculate(stack, 0.60);

    assertSame(stack, recovery.getStackLossBalance());
    assertEquals(0.60, recovery.getCallerSpecifiedRecoveryFraction(), 0.0);
    assertEquals(0.05467952332599066, recovery.getRecoveredHeatMegaWatt(), 1.0e-14);
    assertEquals(0.03645301555066044, recovery.getRemainingStackSensibleLossMegaWatt(), 1.0e-14);
    assertEquals(0.06271840959864103, recovery.getResidualFurnaceLossMegaWatt(), 1.0e-14);
    assertEquals(0.09917142514930147, recovery.getPostRecoveryFurnaceLossMegaWatt(), 1.0e-14);
    assertEquals(0.05331087380469287, recovery.getRecoveredHeatFractionOfFuelChemicalPower(), 1.0e-14);
    assertEquals(0.09668912619530722,
        recovery.getPostRecoveryFurnaceLossFractionOfFuelChemicalPower(), 1.0e-14);
    assertEquals(0.0, recovery.getHeatRecoveryClosureResidualMegaWatt(), 1.0e-15);
  }

  @Test
  void rateScalingPreservesFractions() {
    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance base =
        RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance
            .calculate(publicStackLoss(1000.0, 1.0), 0.60);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance doubled =
        RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance
            .calculate(publicStackLoss(2000.0, 2.0), 0.60);

    assertEquals(2.0 * base.getRecoveredHeatMegaWatt(), doubled.getRecoveredHeatMegaWatt(), 1.0e-13);
    assertEquals(base.getRecoveredHeatFractionOfFuelChemicalPower(),
        doubled.getRecoveredHeatFractionOfFuelChemicalPower(), 1.0e-14);
    assertEquals(base.getPostRecoveryFurnaceLossFractionOfFuelChemicalPower(),
        doubled.getPostRecoveryFurnaceLossFractionOfFuelChemicalPower(), 1.0e-14);
  }

  @Test
  void recoveryBoundsCloseWithoutChangingResidualFurnaceLoss() {
    RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance stack = publicStackLoss(1000.0, 1.0);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance none =
        RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance.calculate(stack, 0.0);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance all =
        RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance.calculate(stack, 1.0);

    assertEquals(0.0, none.getRecoveredHeatMegaWatt(), 0.0);
    assertEquals(stack.getStackSensibleLossMegaWatt(), none.getRemainingStackSensibleLossMegaWatt(), 0.0);
    assertEquals(stack.getStackSensibleLossMegaWatt(), all.getRecoveredHeatMegaWatt(), 0.0);
    assertEquals(0.0, all.getRemainingStackSensibleLossMegaWatt(), 0.0);
    assertEquals(stack.getResidualFurnaceLossMegaWatt(), all.getPostRecoveryFurnaceLossMegaWatt(), 0.0);
  }

  @Test
  void coolingCaseAndInvalidInputsFailClosed() {
    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance cooling =
        RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance
            .calculate(publicStackLoss(1000.0, 0.01), 0.60);
    assertEquals(0.0, cooling.getRecoveredHeatMegaWatt(), 0.0);
    assertEquals(0.0, cooling.getPostRecoveryFurnaceLossMegaWatt(), 0.0);

    RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance stack = publicStackLoss(1000.0, 1.0);
    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance.calculate(null, 0.60));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance.calculate(stack, -0.01));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance.calculate(stack, 1.01));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance.calculate(stack, Double.NaN));
  }

  private static RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance publicStackLoss(
      double feedMassFlowKgPerHour, double sensibleHeatingDutyMegaWatt) {
    RefineryHydrotreatingSulfurNitrogenBalance material = RefineryHydrotreatingSulfurNitrogenBalance.calculate(1000.0,
        0.0040867518, 0.001095129, 15.0e-6, 10.0e-6, 2.0, 4.0);
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supply =
        RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance.calculate(material, 1.5, 0.90,
            NON_HYDROGEN_MOLAR_MASS_KG_PER_MOL);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance recycle =
        RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance.calculate(supply, 0.90, 0.10, 0.20, 0.50, 0.05);
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance throughput =
        RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance.calculate(recycle,
            feedMassFlowKgPerHour);
    RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt distribution =
        RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt.calculate(throughput);
    RefineryHydrotreatingSulfurNitrogenThermalDutyBalance thermal =
        RefineryHydrotreatingSulfurNitrogenThermalDutyBalance.calculate(distribution, 100.0, 50.0,
            sensibleHeatingDutyMegaWatt);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance utility =
        RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance.calculate(thermal, 0.85, 50.0, 0.40, 3.0);
    RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance combustion =
        RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance.calculate(utility,
            METHANE_CARBON_MASS_FRACTION, METHANE_HYDROGEN_MASS_FRACTION, 0.0, 0.0, 0.0, 0.2095, 0.15);
    return RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance.calculate(combustion, 473.15, 298.15, 34.0);
  }
}
