package neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** Tests conservative n-1 implicit finite-volume species transport. */
class ConservativeSpeciesTransportTest {
  @Test
  void closesEveryComponentWithoutNormalizationOrClipping() {
    String[] names = { "methane", "nitrogen", "carbon dioxide" };
    double[][] oldMassFraction = { { 0.80, 0.75 }, { 0.15, 0.20 }, { 0.05, 0.05 } };
    double[] inletMassFraction = { 0.70, 0.20, 0.10 };
    double[] oldCellMassKg = { 100.0, 100.0 };
    double[] newCellMassKg = { 105.0, 100.0 };
    double[] faceMassFlowKgPerSecond = { 1.0, 0.5, 0.5 };

    OnePhaseSpeciesConservationReport report = ConservativeSpeciesTransport.solve(names, oldMassFraction,
        inletMassFraction, oldCellMassKg, newCellMassKg, faceMassFlowKgPerSecond, 10.0);

    assertEquals(OnePhaseSpeciesConservationReport.ConservationReason.CONVERGED, report.getReason(),
        report.getMessage());
    assertTrue(report.getMaximumRelativeInventoryResidual() < 1.0e-14, report.getMessage());
    assertEquals(0.0, report.getMaximumMassFractionSumError(), 0.0);
    assertTrue(report.getMinimumMassFraction() >= 0.0);
    assertTrue(report.getMaximumMassFraction() <= 1.0);

    double[][] profile = report.getMassFractionProfile();
    assertEquals((80.0 + 10.0 * 1.0 * 0.70) / (105.0 + 10.0 * 0.5), profile[0][0], 1.0e-15);
    assertEquals((75.0 + 10.0 * 0.5 * profile[0][0]) / (100.0 + 10.0 * 0.5), profile[0][1], 1.0e-15);
  }

  @Test
  void reproducesImplicitUpwindStepRecurrence() {
    String[] names = { "carrier", "tracer" };
    double[][] oldMassFraction = { { 1.0, 1.0, 1.0, 1.0 }, { 0.0, 0.0, 0.0, 0.0 } };
    double[] inletMassFraction = { 0.0, 1.0 };
    double[] cellMassKg = { 10.0, 10.0, 10.0, 10.0 };
    double[] faceMassFlowKgPerSecond = { 1.0, 1.0, 1.0, 1.0, 1.0 };

    OnePhaseSpeciesConservationReport report = ConservativeSpeciesTransport.solve(names, oldMassFraction,
        inletMassFraction, cellMassKg, cellMassKg, faceMassFlowKgPerSecond, 5.0);

    assertTrue(report.isConverged(), report.getMessage());
    double[] expectedTracer = { 1.0 / 3.0, 1.0 / 9.0, 1.0 / 27.0, 1.0 / 81.0 };
    assertArrayEquals(expectedTracer, report.getMassFractionProfile()[1], 1.0e-15);
    assertTrue(report.getMaximumRelativeInventoryResidual() < 1.0e-14);
  }

  @Test
  void rejectsInvalidClosureAndReversedFlowWithoutRepairingInputs() {
    assertFalse(OnePhaseSpeciesConservationReport.ConservationReason.COUPLING_NOT_CONVERGED.isConverged());
    String[] names = { "carrier", "tracer" };
    double[][] invalidOld = { { 0.8 }, { 0.3 } };
    OnePhaseSpeciesConservationReport invalid = ConservativeSpeciesTransport.solve(names, invalidOld,
        new double[] { 0.8, 0.2 }, new double[] { 10.0 }, new double[] { 10.0 }, new double[] { 1.0, 1.0 }, 1.0);

    assertEquals(OnePhaseSpeciesConservationReport.ConservationReason.INVALID_STATE, invalid.getReason());
    assertFalse(invalid.isConverged());
    assertTrue(invalid.getMessage().contains("sum to one without normalization"));

    double[][] validOld = { { 0.8 }, { 0.2 } };
    OnePhaseSpeciesConservationReport reversed = ConservativeSpeciesTransport.solve(names, validOld,
        new double[] { 0.8, 0.2 }, new double[] { 10.0 }, new double[] { 10.0 }, new double[] { 1.0, -1.0 }, 1.0);
    assertEquals(OnePhaseSpeciesConservationReport.ConservationReason.UNSUPPORTED_FLOW, reversed.getReason());
    assertTrue(reversed.getMessage().contains("zero and reversed flow"));
  }

  @Test
  void reportDefensivelyCopiesProfiles() {
    OnePhaseSpeciesConservationReport report = ConservativeSpeciesTransport.solve(new String[] { "carrier", "tracer" },
        new double[][] { { 0.8 }, { 0.2 } }, new double[] { 0.8, 0.2 }, new double[] { 10.0 }, new double[] { 10.0 },
        new double[] { 1.0, 1.0 }, 1.0);
    double[][] first = report.getMassFractionProfile();
    first[0][0] = -1.0;
    assertEquals(0.8, report.getMassFractionProfile()[0][0], 1.0e-15);
    assertFalse(OnePhaseSpeciesConservationReport.notRun().toJson().contains("NaN"));
  }
}
