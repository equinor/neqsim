package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

/**
 * Physical sign conventions, reference independence and snapshot behavior of the conservative fluid energy ledger.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TwoFluidConservativeEnergyReportTest {
  /** Negative caloric-reference energies and signed outflow must still close the first law. */
  @Test
  void signedOpenDomainLedgerAllowsNegativeCaloricReferenceEnergy() {
    TwoFluidConservativeEnergyReport report = new TwoFluidConservativeEnergyReport(0.2, -1000.0, -1023.0, -30.0, -3.0,
        10.0, 1.0e-10, 2.0e-11);
    assertEquals(-23.0, report.getStoredEnergyChangeJ(), 0.0);
    assertEquals(0.0, report.getResidualJ(), 0.0);
    assertTrue(report.isWithinTolerance(0.0, 0.0));
    assertEquals(1.0e-10, report.getMaxRelativeVolumeResidual(), 0.0);
    assertEquals(2.0e-11, report.getMaxRelativeThermodynamicEnergyResidual(), 0.0);
  }

  /** Boundary-resolved transfers must preserve their signs and reproduce the legacy net ledger. */
  @Test
  void boundaryResolvedAdvectionProvidesExactNetworkRoutingTerms() {
    TwoFluidConservativeEnergyReport report = new TwoFluidConservativeEnergyReport(0.2, 100.0, 107.0, 25.0, 18.0, 0.0,
        0.0, 0.0, 0.0);
    assertEquals(25.0, report.getInletAdvectedEnergyJ(), 0.0);
    assertEquals(18.0, report.getOutletAdvectedEnergyJ(), 0.0);
    assertEquals(7.0, report.getNetAdvectedEnergyJ(), 0.0);
    assertEquals(0.0, report.getResidualJ(), 0.0);
  }

  /** Phase rows retain countercurrent energy transport while reproducing aggregate routing terms. */
  @Test
  void phaseResolvedAdvectionPreservesOppositeSignedTransfers() {
    TwoFluidConservativeEnergyReport report = new TwoFluidConservativeEnergyReport(0.2, 100.0, 107.0, 25.0, 18.0,
        new double[] {30.0, -5.0, 0.0}, new double[] {22.0, -4.0, 0.0}, 0.0, 0.0, 0.0, 0.0);
    assertEquals(30.0, report.getInletAdvectedEnergyJ(TwoFluidConservativeEnergyReport.Phase.GAS), 0.0);
    assertEquals(-5.0, report.getInletAdvectedEnergyJ(TwoFluidConservativeEnergyReport.Phase.OIL), 0.0);
    assertEquals(22.0, report.getOutletAdvectedEnergyJ(TwoFluidConservativeEnergyReport.Phase.GAS), 0.0);
    assertEquals(-4.0, report.getOutletAdvectedEnergyJ(TwoFluidConservativeEnergyReport.Phase.OIL), 0.0);
    assertEquals(25.0, report.getInletPhaseAdvectedEnergyJ()[0] + report.getInletPhaseAdvectedEnergyJ()[1]
        + report.getInletPhaseAdvectedEnergyJ()[2], 0.0);
    assertEquals(18.0, report.getOutletPhaseAdvectedEnergyJ()[0] + report.getOutletPhaseAdvectedEnergyJ()[1]
        + report.getOutletPhaseAdvectedEnergyJ()[2], 0.0);
    double[] defensive = report.getOutletPhaseAdvectedEnergyJ();
    defensive[0] = 0.0;
    assertEquals(22.0, report.getOutletPhaseAdvectedEnergyJ()[0], 0.0);
  }

  /** A large arbitrary caloric energy reference must not hide an incorrect heat input. */
  @Test
  void closedCellResidualDoesNotUseAbsoluteStoredEnergyAsItsScale() {
    TwoFluidConservativeEnergyReport ordinary = new TwoFluidConservativeEnergyReport(1.0, -1000.0, -989.0, 0.0, 0.0,
        10.0, 0.0, 0.0);
    TwoFluidConservativeEnergyReport shifted = new TwoFluidConservativeEnergyReport(1.0, -1.0e12, -1.0e12 + 11.0, 0.0,
        0.0, 10.0, 0.0, 0.0);
    assertEquals(1.0, ordinary.getResidualJ(), 0.0);
    assertEquals(ordinary.getRelativeResidual(), shifted.getRelativeResidual(), 0.0);
    assertFalse(shifted.isWithinTolerance(0.1, 0.01));
    assertTrue(shifted.isWithinTolerance(1.0, 0.0));
  }

  /** A closed adiabatic invariant has a finite zero residual even with no energy transfers. */
  @Test
  void zeroChangeAndZeroTransferHaveZeroRelativeResidual() {
    TwoFluidConservativeEnergyReport report = new TwoFluidConservativeEnergyReport(0.0, 50.0, 50.0, 0.0, 0.0, 0.0, 0.0,
        0.0);
    assertEquals(0.0, report.getRelativeResidual(), 0.0);
    assertEquals(0.0, report.getElapsedTimeSeconds(), 0.0);
  }

  /** Accepted report values survive the same serialization used by pipe transactions. */
  @Test
  void serializationPreservesTheAcceptedLedgerAndRecoveryDiagnostics() {
    TwoFluidConservativeEnergyReport original = new TwoFluidConservativeEnergyReport(0.5, -10.0, 2.0, 5.0, 3.0, 4.0,
        1.0e-9, 2.0e-9);
    TwoFluidConservativeEnergyReport restored = SerializationUtils.clone(original);
    assertEquals(original.getInitialEnergyJ(), restored.getInitialEnergyJ(), 0.0);
    assertEquals(original.getFinalEnergyJ(), restored.getFinalEnergyJ(), 0.0);
    assertEquals(original.getInletAdvectedEnergyJ(), restored.getInletAdvectedEnergyJ(), 0.0);
    assertEquals(original.getOutletAdvectedEnergyJ(), restored.getOutletAdvectedEnergyJ(), 0.0);
    assertEquals(original.getNetAdvectedEnergyJ(), restored.getNetAdvectedEnergyJ(), 0.0);
    assertEquals(original.getGravitationalWorkJ(), restored.getGravitationalWorkJ(), 0.0);
    assertEquals(original.getWallHeatToFluidJ(), restored.getWallHeatToFluidJ(), 0.0);
    assertEquals(original.getMaxRelativeVolumeResidual(), restored.getMaxRelativeVolumeResidual(), 0.0);
    assertEquals(original.getMaxRelativeThermodynamicEnergyResidual(),
        restored.getMaxRelativeThermodynamicEnergyResidual(), 0.0);
    assertEquals(0.0, restored.getResidualJ(), 0.0);
  }

  /** Invalid diagnostic values and tolerances must fail instead of silently accepting an interval. */
  @Test
  void rejectsNonfiniteOrNegativeDiagnosticValuesAndTolerances() {
    assertThrows(IllegalArgumentException.class,
        () -> new TwoFluidConservativeEnergyReport(Double.NaN, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> new TwoFluidConservativeEnergyReport(1.0, 0.0, Double.POSITIVE_INFINITY, 0.0, 0.0, 0.0, 0.0, 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> new TwoFluidConservativeEnergyReport(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, -1.0e-9, 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> new TwoFluidConservativeEnergyReport(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> new TwoFluidConservativeEnergyReport(1.0, 0.0, 0.0, 2.0, 0.0,
        new double[] {1.0, 0.0, 0.0}, new double[3], 0.0, 0.0, 0.0, 0.0));
    TwoFluidConservativeEnergyReport report = new TwoFluidConservativeEnergyReport(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
        0.0);
    assertThrows(IllegalArgumentException.class, () -> report.isWithinTolerance(-1.0, 0.0));
    assertThrows(IllegalArgumentException.class, () -> report.isWithinTolerance(0.0, Double.NaN));
  }
}
