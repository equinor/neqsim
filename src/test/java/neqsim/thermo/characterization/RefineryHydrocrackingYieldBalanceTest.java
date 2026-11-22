package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests the screening hydrocracking product-yield mass balance. */
public class RefineryHydrocrackingYieldBalanceTest {
  @Test
  public void conversionHydrogenAndThreeProductLumpsCloseMass() {
    RefineryHydrocrackingYieldBalance receipt = RefineryHydrocrackingYieldBalance.calculate(1000.0, 0.80, 20.0, 0.10,
        0.30, 0.60);

    assertEquals(1000.0, receipt.getFeedMassFlowKgPerHour(), 0.0);
    assertEquals(0.80, receipt.getConversionFraction(), 0.0);
    assertEquals(20.0, receipt.getHydrogenConsumptionKgPerTonneFeed(), 0.0);
    assertEquals(800.0, receipt.getConvertedFeedMassFlowKgPerHour(), 1.0e-12);
    assertEquals(200.0, receipt.getUnconvertedOilMassFlowKgPerHour(), 1.0e-12);
    assertEquals(20.0, receipt.getHydrogenConsumptionMassFlowKgPerHour(), 1.0e-12);
    assertEquals(820.0, receipt.getConvertedProductMassFlowKgPerHour(), 1.0e-12);
    assertEquals(82.0, receipt.getLightGasMassFlowKgPerHour(), 1.0e-12);
    assertEquals(246.0, receipt.getNaphthaMassFlowKgPerHour(), 1.0e-12);
    assertEquals(492.0, receipt.getMiddleDistillateMassFlowKgPerHour(), 1.0e-12);
    assertEquals(1020.0, receipt.getTotalProductMassFlowKgPerHour(), 1.0e-12);
    assertEquals(0.0, receipt.getFeedConversionBalanceResidualKgPerHour(), 1.0e-12);
    assertEquals(0.0, receipt.getConvertedProductMassBalanceResidualKgPerHour(), 1.0e-12);
    assertEquals(0.0, receipt.getOverallMassBalanceResidualKgPerHour(), 1.0e-12);
  }

  @Test
  public void zeroAndCompleteConversionPreserveEndpoints() {
    RefineryHydrocrackingYieldBalance zero = RefineryHydrocrackingYieldBalance.calculate(1000.0, 0.0, 0.0, 0.10, 0.30,
        0.60);
    RefineryHydrocrackingYieldBalance complete = RefineryHydrocrackingYieldBalance.calculate(1000.0, 1.0, 20.0, 0.10,
        0.30, 0.60);

    assertEquals(1000.0, zero.getUnconvertedOilMassFlowKgPerHour(), 0.0);
    assertEquals(0.0, zero.getConvertedProductMassFlowKgPerHour(), 0.0);
    assertEquals(0.0, complete.getUnconvertedOilMassFlowKgPerHour(), 0.0);
    assertEquals(1020.0, complete.getConvertedProductMassFlowKgPerHour(), 1.0e-12);
  }

  @Test
  public void invalidInputsFailClosed() {
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrocrackingYieldBalance.calculate(0.0, 0.8, 20.0, 0.1, 0.3, 0.6));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrocrackingYieldBalance.calculate(1000.0, -0.01, 20.0, 0.1, 0.3, 0.6));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrocrackingYieldBalance.calculate(1000.0, 1.01, 20.0, 0.1, 0.3, 0.6));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrocrackingYieldBalance.calculate(1000.0, 0.8, -1.0, 0.1, 0.3, 0.6));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrocrackingYieldBalance.calculate(1000.0, 0.8, 20.0, 0.1, 0.3, 0.5));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrocrackingYieldBalance.calculate(1000.0, 0.0, 1.0, 0.1, 0.3, 0.6));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrocrackingYieldBalance.calculate(1000.0, Double.NaN, 20.0, 0.1, 0.3, 0.6));
  }
}
