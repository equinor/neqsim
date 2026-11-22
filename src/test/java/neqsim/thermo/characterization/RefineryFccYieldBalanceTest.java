package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests for {@link RefineryFccYieldBalance}. */
class RefineryFccYieldBalanceTest {
  private static final double TOLERANCE = 1.0e-10;

  @Test
  void calculatesFiveLumpYieldAndUnconvertedOilBalance() {
    RefineryFccYieldBalance result = RefineryFccYieldBalance.calculate(1000.0, 0.75, 0.05, 0.15, 0.50, 0.20, 0.10);

    assertEquals(750.0, result.getConvertedFeedMassFlowKgPerHour(), TOLERANCE);
    assertEquals(250.0, result.getUnconvertedOilMassFlowKgPerHour(), TOLERANCE);
    assertEquals(37.5, result.getDryGasMassFlowKgPerHour(), TOLERANCE);
    assertEquals(112.5, result.getLiquefiedPetroleumGasMassFlowKgPerHour(), TOLERANCE);
    assertEquals(375.0, result.getGasolineMassFlowKgPerHour(), TOLERANCE);
    assertEquals(150.0, result.getLightCycleOilMassFlowKgPerHour(), TOLERANCE);
    assertEquals(75.0, result.getCokeMassFlowKgPerHour(), TOLERANCE);
    assertEquals(1000.0, result.getTotalProductMassFlowKgPerHour(), TOLERANCE);
    assertEquals(0.0, result.getFeedConversionBalanceResidualKgPerHour(), TOLERANCE);
    assertEquals(0.0, result.getConvertedProductMassBalanceResidualKgPerHour(), TOLERANCE);
    assertEquals(0.0, result.getOverallMassBalanceResidualKgPerHour(), TOLERANCE);
  }

  @Test
  void acceptsLimitingConversionCases() {
    RefineryFccYieldBalance zeroConversion = RefineryFccYieldBalance.calculate(100.0, 0.0, 0.05, 0.15, 0.50, 0.20,
        0.10);
    RefineryFccYieldBalance fullConversion = RefineryFccYieldBalance.calculate(100.0, 1.0, 0.05, 0.15, 0.50, 0.20,
        0.10);

    assertEquals(100.0, zeroConversion.getUnconvertedOilMassFlowKgPerHour(), TOLERANCE);
    assertEquals(0.0, zeroConversion.getCokeMassFlowKgPerHour(), TOLERANCE);
    assertEquals(0.0, fullConversion.getUnconvertedOilMassFlowKgPerHour(), TOLERANCE);
    assertEquals(100.0, fullConversion.getConvertedFeedMassFlowKgPerHour(), TOLERANCE);
  }

  @Test
  void rejectsInvalidInputsAndNonClosingYieldFractions() {
    assertThrows(IllegalArgumentException.class,
        () -> RefineryFccYieldBalance.calculate(0.0, 0.75, 0.05, 0.15, 0.50, 0.20, 0.10));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryFccYieldBalance.calculate(1000.0, 1.01, 0.05, 0.15, 0.50, 0.20, 0.10));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryFccYieldBalance.calculate(1000.0, 0.75, 0.05, 0.15, 0.50, 0.20, 0.09));
  }
}
