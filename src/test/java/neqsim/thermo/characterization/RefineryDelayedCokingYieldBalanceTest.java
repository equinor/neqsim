package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests for {@link RefineryDelayedCokingYieldBalance}. */
class RefineryDelayedCokingYieldBalanceTest {
  private static final double TOLERANCE = 1.0e-10;

  @Test
  void calculatesFiveLumpYieldAndUnconvertedResidueBalance() {
    RefineryDelayedCokingYieldBalance result = RefineryDelayedCokingYieldBalance.calculate(
        1000.0, 0.70, 0.08, 0.18, 0.32, 0.22, 0.20);

    assertEquals(700.0, result.getConvertedFeedMassFlowKgPerHour(), TOLERANCE);
    assertEquals(300.0, result.getUnconvertedResidueMassFlowKgPerHour(), TOLERANCE);
    assertEquals(56.0, result.getGasMassFlowKgPerHour(), TOLERANCE);
    assertEquals(126.0, result.getNaphthaMassFlowKgPerHour(), TOLERANCE);
    assertEquals(224.0, result.getLightCokerGasOilMassFlowKgPerHour(), TOLERANCE);
    assertEquals(154.0, result.getHeavyCokerGasOilMassFlowKgPerHour(), TOLERANCE);
    assertEquals(140.0, result.getCokeMassFlowKgPerHour(), TOLERANCE);
    assertEquals(1000.0, result.getTotalProductMassFlowKgPerHour(), TOLERANCE);
    assertEquals(0.0, result.getConvertedProductMassBalanceResidualKgPerHour(), TOLERANCE);
    assertEquals(0.0, result.getOverallMassBalanceResidualKgPerHour(), TOLERANCE);
  }

  @Test
  void rejectsInvalidInputsAndNonClosingFractions() {
    assertThrows(IllegalArgumentException.class,
        () -> RefineryDelayedCokingYieldBalance.calculate(0.0, 0.70, 0.08, 0.18, 0.32, 0.22, 0.20));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryDelayedCokingYieldBalance.calculate(1000.0, 1.01, 0.08, 0.18, 0.32, 0.22, 0.20));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryDelayedCokingYieldBalance.calculate(1000.0, 0.70, 0.08, 0.18, 0.32, 0.22, 0.19));
  }
}
