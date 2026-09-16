package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations.MassBalanceRate;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations.TransactionalEvaluation;
import org.junit.jupiter.api.Test;

/** Exact trial ledgers must never be confused with retained accepted diagnostics. */
class TwoFluidTransactionalEvaluationTest {
  @Test
  void capturesTheCurrentLedgerAndRestoresPreviouslyPublishedDiagnostics() {
    TwoFluidConservationEquations equations = equations();
    TwoFluidSection[] accepted = { section(0.0, 3.0), section(3.0, 7.0), section(10.0, 11.0) };
    equations.calcRHS(cloneSections(accepted), 7.0);
    MassBalanceRate savedBalance = equations.getLastMassBalanceRate();
    double[][] savedFaces = equations.getLastPhaseMassFaceFluxes();
    double[][] savedForces = equations.getLastMomentumSourceForcesPerLength();
    TwoFluidSection[] trial = cloneSections(accepted);
    trial[2].setGasVelocity(4.0);
    trial[2].setOilVelocity(-0.3);
    trial[2].setWaterVelocity(0.4);
    trial[2].updateConservativeVariables();

    TransactionalEvaluation evaluation = equations.evaluateTransactional(cloneSections(trial), 7.0);
    TransactionalEvaluation repeated = equations.evaluateTransactional(cloneSections(trial), 7.0);

    assertSame(savedBalance, equations.getLastMassBalanceRate());
    assertMatrixEquals(savedFaces, equations.getLastPhaseMassFaceFluxes());
    assertMatrixEquals(savedForces, equations.getLastMomentumSourceForcesPerLength());
    assertMatrixEquals(evaluation.getRates(), repeated.getRates());
    assertMatrixEquals(evaluation.getPhaseMassFaceFluxes(), repeated.getPhaseMassFaceFluxes());
    assertNotEquals(savedFaces[3][1], evaluation.getPhaseMassFaceFluxes()[3][1]);
    assertTrue(evaluation.getPhaseMassFaceFluxes()[3][1] < 0.0);
    assertFalse(evaluation.isOutletBackflowClamped());
    assertDomainBalance(trial, evaluation);
    assertEquals(6, evaluation.getMomentumSourceForcesPerLength()[0].length);

    double rate = evaluation.getRates()[0][0];
    double flux = evaluation.getPhaseMassFaceFluxes()[0][0];
    double force = evaluation.getMomentumSourceForcesPerLength()[0][0];
    evaluation.getRates()[0][0] = Double.NaN;
    evaluation.getPhaseMassFaceFluxes()[0][0] = Double.NaN;
    evaluation.getPhaseMassSourcesPerLength()[0][0] = Double.NaN;
    evaluation.getMomentumSourceForcesPerLength()[0][0] = Double.NaN;
    evaluation.getMassBalanceRate().getInletMassFlowKgPerSecond()[0] = Double.NaN;
    assertEquals(rate, evaluation.getRates()[0][0], 0.0);
    assertEquals(flux, evaluation.getPhaseMassFaceFluxes()[0][0], 0.0);
    assertEquals(force, evaluation.getMomentumSourceForcesPerLength()[0][0], 0.0);
    assertDomainBalance(trial, evaluation);
  }

  @Test
  void integratesPhaseSourcesUsingIndividualCellLengths() {
    TwoFluidConservationEquations equations = equations();
    equations.setIncludeMassTransfer(true);
    equations.setClosedBoundaries(true, true);
    TwoFluidSection[] trial = { section(0.0, 3.0), section(3.0, 7.0) };
    trial[0].setMassTransferRate(0.002);
    trial[1].setMassTransferRate(-0.001);

    TransactionalEvaluation evaluation = equations.evaluateTransactional(cloneSections(trial), 5.0);

    assertDomainBalance(trial, evaluation);
    double[] source = evaluation.getMassBalanceRate().getSourceMassFlowKgPerSecond();
    assertNotEquals(0.0, source[0]);
    assertEquals(0.0, source[0] + source[1] + source[2], 1.0e-14);
    for (int phase = 0; phase < 3; phase++) {
      double integrated = 0.0;
      for (int cell = 0; cell < trial.length; cell++) {
        integrated += trial[cell].getLength() * evaluation.getPhaseMassSourcesPerLength()[cell][phase];
      }
      assertEquals(integrated, source[phase], 1.0e-14);
    }
  }

  @Test
  void reportsTrialBackflowSeparatelyFromAnEarlierStickyClamp() {
    TwoFluidConservationEquations equations = equations();
    equations.setAllowOutletPhaseBackflow(false);
    TwoFluidSection reversed = section(0.0, 3.0);
    reversed.setOilVelocity(-0.25);
    reversed.updateConservativeVariables();
    equations.calcRHS(new TwoFluidSection[] { reversed.clone() }, 3.0);
    assertTrue(equations.isOutletBackflowClamped());

    TransactionalEvaluation forward = equations.evaluateTransactional(new TwoFluidSection[] { section(0.0, 3.0) }, 3.0);
    assertFalse(forward.isOutletBackflowClamped());
    assertTrue(equations.isOutletBackflowClamped(), "Previously published sticky diagnostic must be restored");

    equations.clearOutletBackflowClamped();
    TransactionalEvaluation clamped = equations.evaluateTransactional(new TwoFluidSection[] { reversed.clone() }, 3.0);
    assertTrue(clamped.isOutletBackflowClamped());
    assertFalse(equations.isOutletBackflowClamped(), "A trial must not publish its own sticky diagnostic");
  }

  @Test
  void restoresAllPublishedDiagnosticsAfterAnEvaluationThrows() {
    FailingEquations equations = new FailingEquations();
    equations.setIncludeEnergyEquation(false);
    equations.setIncludeMassTransfer(false);
    equations.setMomentumForceDiagnosticsEnabled(true);
    equations.calcRHS(new TwoFluidSection[] { section(0.0, 3.0) }, 3.0);
    MassBalanceRate savedBalance = equations.getLastMassBalanceRate();
    double[][] savedFaces = equations.getLastPhaseMassFaceFluxes();
    double[][] savedForces = equations.getLastMomentumSourceForcesPerLength();
    equations.fail = true;

    assertSame(equations.failure, assertThrows(IllegalStateException.class,
        () -> equations.evaluateTransactional(new TwoFluidSection[] { section(0.0, 7.0), section(7.0, 11.0) }, 9.0)));

    assertSame(savedBalance, equations.getLastMassBalanceRate());
    assertMatrixEquals(savedFaces, equations.getLastPhaseMassFaceFluxes());
    assertMatrixEquals(savedForces, equations.getLastMomentumSourceForcesPerLength());
    assertFalse(equations.isOutletBackflowClamped());
    equations.fail = false;
    equations.evaluateTransactional(new TwoFluidSection[] { section(0.0, 3.0) }, 3.0);
    assertSame(savedBalance, equations.getLastMassBalanceRate());
  }

  @Test
  void serializesExactLedgerWithoutAliasingLaterOperatorEvaluations() throws Exception {
    TwoFluidConservationEquations equations = equations();
    equations.setMomentumForceDiagnosticsEnabled(false);
    TransactionalEvaluation original = equations.evaluateTransactional(new TwoFluidSection[] { section(0.0, 3.0) },
        3.0);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(original);
    }
    TransactionalEvaluation restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (TransactionalEvaluation) input.readObject();
    }
    equations.calcRHS(new TwoFluidSection[] { section(0.0, 1.0), section(1.0, 2.0) }, 1.5);
    assertMatrixEquals(original.getRates(), restored.getRates());
    assertMatrixEquals(original.getPhaseMassFaceFluxes(), restored.getPhaseMassFaceFluxes());
    assertArrayEquals(original.getMassBalanceRate().getOutletMassFlowKgPerSecond(),
        restored.getMassBalanceRate().getOutletMassFlowKgPerSecond(), 0.0);
    assertEquals(0, restored.getMomentumSourceForcesPerLength().length);
  }

  private static void assertDomainBalance(TwoFluidSection[] sections, TransactionalEvaluation evaluation) {
    MassBalanceRate balance = evaluation.getMassBalanceRate();
    for (int phase = 0; phase < 3; phase++) {
      double accumulation = 0.0;
      for (int cell = 0; cell < sections.length; cell++) {
        accumulation += evaluation.getRates()[cell][phase] * sections[cell].getLength();
      }
      assertEquals(balance.getInletMassFlowKgPerSecond()[phase] - balance.getOutletMassFlowKgPerSecond()[phase]
          + balance.getSourceMassFlowKgPerSecond()[phase], accumulation, 1.0e-11);
    }
  }

  private static TwoFluidConservationEquations equations() {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeEnergyEquation(false);
    equations.setIncludeMassTransfer(false);
    equations.setAllowOutletPhaseBackflow(true);
    equations.setMomentumForceDiagnosticsEnabled(true);
    return equations;
  }

  private static TwoFluidSection section(double position, double length) {
    TwoFluidSection section = new TwoFluidSection(position, length, 0.1, 0.0);
    section.setPressure(5.0e6);
    section.setTemperature(300.0);
    section.setGasDensity(40.0);
    section.setOilDensity(700.0);
    section.setWaterDensity(1000.0);
    section.setLiquidDensity(850.0);
    section.setGasViscosity(1.2e-5);
    section.setLiquidViscosity(1.0e-3);
    section.setGasSoundSpeed(300.0);
    section.setLiquidSoundSpeed(1200.0);
    section.setSurfaceTension(0.02);
    section.setGasHoldup(0.6);
    section.setLiquidHoldup(0.4);
    section.setOilHoldup(0.2);
    section.setWaterHoldup(0.2);
    section.setWaterCut(0.5);
    section.setOilFractionInLiquid(0.5);
    section.setGasVelocity(2.0);
    section.setLiquidVelocity(0.5);
    section.setOilVelocity(0.6);
    section.setWaterVelocity(0.4);
    section.updateConservativeVariables();
    section.updateDerivedQuantities();
    return section;
  }

  private static TwoFluidSection[] cloneSections(TwoFluidSection[] sections) {
    TwoFluidSection[] result = new TwoFluidSection[sections.length];
    for (int cell = 0; cell < result.length; cell++) {
      result[cell] = sections[cell].clone();
    }
    return result;
  }

  private static void assertMatrixEquals(double[][] expected, double[][] actual) {
    assertEquals(expected.length, actual.length);
    for (int row = 0; row < expected.length; row++) {
      assertArrayEquals(expected[row], actual[row], 0.0);
    }
  }

  private static final class FailingEquations extends TwoFluidConservationEquations {
    private static final long serialVersionUID = 1L;
    private final IllegalStateException failure = new IllegalStateException("injected failure after diagnostic writes");
    private boolean fail;

    @Override
    public double[][] calcRHS(TwoFluidSection[] sections, double dx) {
      double[][] result = super.calcRHS(sections, dx);
      if (fail) {
        throw failure;
      }
      return result;
    }
  }
}
