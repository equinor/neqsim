package neqsim.process.equipment.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.EnergyNetworkReport;

class CoupledProcessEnergyResultTest {

  @Test
  void testConstructorDefensivelyCopiesValidatedInputs() {
    List<CoupledProcessEnergyResult.IterationResult> history = new ArrayList<CoupledProcessEnergyResult.IterationResult>();
    history.add(
        new CoupledProcessEnergyResult.IterationResult(1, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, false));
    List<EnergyNetworkReport> reports = new ArrayList<EnergyNetworkReport>();

    CoupledProcessEnergyResult result = new CoupledProcessEnergyResult(false,
        CoupledProcessEnergyResult.TerminationReason.MAXIMUM_ITERATIONS, 1, Double.POSITIVE_INFINITY,
        Double.POSITIVE_INFINITY, history, reports);
    history.clear();
    reports.clear();

    assertEquals(1, result.getIterationHistory().size());
    assertThrows(UnsupportedOperationException.class, () -> result.getIterationHistory().clear());
    assertThrows(UnsupportedOperationException.class, () -> result.getEnergyReports().clear());
  }

  @Test
  void testInvalidResultInvariantsAreRejected() {
    List<CoupledProcessEnergyResult.IterationResult> oneIteration = Collections
        .singletonList(new CoupledProcessEnergyResult.IterationResult(1, 0.0, 0.0, true));
    List<EnergyNetworkReport> noReports = Collections.emptyList();

    assertThrows(IllegalArgumentException.class,
        () -> new CoupledProcessEnergyResult(true, null, 1, 0.0, 0.0, oneIteration, noReports));
    assertThrows(IllegalArgumentException.class,
        () -> new CoupledProcessEnergyResult(false, CoupledProcessEnergyResult.TerminationReason.MAXIMUM_ITERATIONS, -1,
            0.0, 0.0, Collections.<CoupledProcessEnergyResult.IterationResult>emptyList(), noReports));
    assertThrows(IllegalArgumentException.class, () -> new CoupledProcessEnergyResult(false,
        CoupledProcessEnergyResult.TerminationReason.MAXIMUM_ITERATIONS, 1, Double.NaN, 0.0, oneIteration, noReports));
    assertThrows(IllegalArgumentException.class, () -> new CoupledProcessEnergyResult(false,
        CoupledProcessEnergyResult.TerminationReason.MAXIMUM_ITERATIONS, 2, 0.0, 0.0, oneIteration, noReports));
    assertThrows(IllegalArgumentException.class, () -> new CoupledProcessEnergyResult(true,
        CoupledProcessEnergyResult.TerminationReason.MAXIMUM_ITERATIONS, 1, 0.0, 0.0, oneIteration, noReports));
    assertThrows(IllegalArgumentException.class,
        () -> new CoupledProcessEnergyResult.IterationResult(0, 0.0, 0.0, false));
  }
}
