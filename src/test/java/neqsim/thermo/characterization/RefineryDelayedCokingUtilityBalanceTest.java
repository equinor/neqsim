package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests for {@link RefineryDelayedCokingUtilityBalance}. */
class RefineryDelayedCokingUtilityBalanceTest {
  private static final double TOLERANCE = 1.0e-10;

  @Test
  void calculatesFeedBasisFuelAndSteamDemand() {
    RefineryDelayedCokingYieldBalance yield = RefineryDelayedCokingYieldBalance.calculate(
        1000.0, 0.70, 0.08, 0.18, 0.32, 0.22, 0.20);
    RefineryDelayedCokingUtilityBalance result = RefineryDelayedCokingUtilityBalance.calculate(
        yield, 2.0, 0.85, 45.0, 50.0);

    assertEquals(0.5555555555555556, result.getProcessDutyMegaWatt(), TOLERANCE);
    assertEquals(2352.9411764705883, result.getFuelEnergyInputMegaJoulePerHour(), TOLERANCE);
    assertEquals(52.28758169934641, result.getFuelMassFlowKgPerHour(), TOLERANCE);
    assertEquals(50.0, result.getSteamMassFlowKgPerHour(), TOLERANCE);
    assertEquals(0.0, result.getEnergyBalanceResidualMegaJoulePerHour(), TOLERANCE);
  }

  @Test
  void rejectsInvalidUtilityAssumptions() {
    RefineryDelayedCokingYieldBalance yield = RefineryDelayedCokingYieldBalance.calculate(
        1000.0, 0.70, 0.08, 0.18, 0.32, 0.22, 0.20);
    assertThrows(NullPointerException.class,
        () -> RefineryDelayedCokingUtilityBalance.calculate(null, 2.0, 0.85, 45.0, 50.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryDelayedCokingUtilityBalance.calculate(yield, 2.0, 0.0, 45.0, 50.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryDelayedCokingUtilityBalance.calculate(yield, 2.0, 0.85, 0.0, 50.0));
  }
}
