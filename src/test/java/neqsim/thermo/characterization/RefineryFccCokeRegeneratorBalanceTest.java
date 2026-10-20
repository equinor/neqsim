package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests for {@link RefineryFccCokeRegeneratorBalance}. */
class RefineryFccCokeRegeneratorBalanceTest {
  private static final double TOLERANCE = 1.0e-9;

  @Test
  void calculatesCokeCarbonAirAndDryFlueGasBalance() {
    RefineryFccYieldBalance yield = RefineryFccYieldBalance.calculate(1000.0, 0.75, 0.05, 0.15, 0.50, 0.20, 0.10);
    RefineryFccCokeRegeneratorBalance result = RefineryFccCokeRegeneratorBalance.calculate(yield, 0.90, 0.96, 0.90,
        0.2095, 0.10);

    assertEquals(67.5, result.getCokeCarbonMassFlowKgPerHour(), TOLERANCE);
    assertEquals(7.5, result.getNonCarbonCokeMassFlowKgPerHour(), TOLERANCE);
    assertEquals(64.8, result.getBurnedCarbonMassFlowKgPerHour(), TOLERANCE);
    assertEquals(2.7, result.getRemainingCarbonMassFlowKgPerHour(), TOLERANCE);
    assertEquals(4.855549080009991, result.getCarbonDioxideMolarFlowKmolPerHour(), TOLERANCE);
    assertEquals(0.5395054533344435, result.getCarbonMonoxideMolarFlowKmolPerHour(), TOLERANCE);
    assertEquals(5.125301806677213, result.getStoichiometricOxygenDemandKmolPerHour(), TOLERANCE);
    assertEquals(5.637831987344935, result.getSuppliedOxygenKmolPerHour(), TOLERANCE);
    assertEquals(26.910892540882742, result.getDryAirMolarFlowKmolPerHour(), TOLERANCE);
    assertEquals(21.273060553537807, result.getNitrogenMolarFlowKmolPerHour(), TOLERANCE);
    assertEquals(0.512530180667722, result.getExcessOxygenMolarFlowKmolPerHour(), TOLERANCE);
    assertEquals(841.1329941949512, result.getDryFlueGasMassFlowKgPerHour(), TOLERANCE);
    assertEquals(0.0, result.getCarbonBalanceResidualKgPerHour(), TOLERANCE);
    assertEquals(0.0, result.getOxygenBalanceResidualKmolPerHour(), TOLERANCE);
    assertEquals(0.0, result.getDryGasMolarBalanceResidualKmolPerHour(), TOLERANCE);
  }

  @Test
  void acceptsZeroCokeAndZeroBurnCases() {
    RefineryFccYieldBalance noCoke = RefineryFccYieldBalance.calculate(100.0, 0.75, 0.10, 0.20, 0.50, 0.20, 0.0);
    RefineryFccCokeRegeneratorBalance noCokeResult = RefineryFccCokeRegeneratorBalance.calculate(noCoke, 0.90, 0.96,
        0.90, 0.2095, 0.10);
    RefineryFccYieldBalance withCoke = RefineryFccYieldBalance.calculate(100.0, 0.75, 0.05, 0.15, 0.50, 0.20, 0.10);
    RefineryFccCokeRegeneratorBalance zeroBurnResult = RefineryFccCokeRegeneratorBalance.calculate(withCoke, 0.90, 0.0,
        0.90, 0.2095, 0.10);

    assertEquals(0.0, noCokeResult.getDryAirMolarFlowKmolPerHour(), TOLERANCE);
    assertEquals(0.0, noCokeResult.getDryFlueGasMassFlowKgPerHour(), TOLERANCE);
    assertEquals(6.75, zeroBurnResult.getRemainingCarbonMassFlowKgPerHour(), TOLERANCE);
    assertEquals(0.0, zeroBurnResult.getDryAirMolarFlowKmolPerHour(), TOLERANCE);
  }

  @Test
  void rejectsInvalidRegeneratorAssumptions() {
    RefineryFccYieldBalance yield = RefineryFccYieldBalance.calculate(1000.0, 0.75, 0.05, 0.15, 0.50, 0.20, 0.10);

    assertThrows(NullPointerException.class,
        () -> RefineryFccCokeRegeneratorBalance.calculate(null, 0.90, 0.96, 0.90, 0.2095, 0.10));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryFccCokeRegeneratorBalance.calculate(yield, 1.01, 0.96, 0.90, 0.2095, 0.10));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryFccCokeRegeneratorBalance.calculate(yield, 0.90, 0.96, 0.90, 0.0, 0.10));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryFccCokeRegeneratorBalance.calculate(yield, 0.90, 0.96, 0.90, 0.2095, -0.01));
  }
}
