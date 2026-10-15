package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations.TransactionalEvaluation;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import org.junit.jupiter.api.Test;

/** Conservative and transactional contracts for directional phase-face transport. */
class DirectionalPhaseFaceFluxTest {
  @Test
  void directionalSplitKeepsSignedMassMomentumAndDonorRelations() {
    DirectionalPhaseFaceFlux forward = new DirectionalPhaseFaceFlux(0.4, 1.2, 7.0, 0.2, 0.3, 0.6, -1.5);
    assertEquals(0.4, forward.getForwardMassFlowKgPerSecond(), 0.0);
    assertEquals(0.0, forward.getReverseMassFlowKgPerSecond(), 0.0);
    assertEquals(0.4, forward.getSignedMassFlowKgPerSecond(), 0.0);
    assertEquals(1.2, forward.getAdvectiveMomentumN(), 0.0);
    assertEquals(0.0, forward.getForwardDonorResidualN(), 1.0e-15);

    DirectionalPhaseFaceFlux reverse = new DirectionalPhaseFaceFlux(-0.3, 1.5, 8.0, 0.2, 0.3, 0.6, -1.5);
    assertEquals(0.0, reverse.getForwardMassFlowKgPerSecond(), 0.0);
    assertEquals(0.3, reverse.getReverseMassFlowKgPerSecond(), 0.0);
    assertEquals(-0.3, reverse.getSignedMassFlowKgPerSecond(), 0.0);
    assertEquals(1.5, reverse.getAdvectiveMomentumN(), 0.0);
    assertEquals(0.0, reverse.getReverseDonorResidualN(), 1.0e-15);
  }

  @Test
  void absentDonorUsesExactZeroMomentumRowWithoutInventoryFloor() {
    DirectionalPhaseFaceFlux absent = new DirectionalPhaseFaceFlux(0.0, 0.0, 3.0, 0.0, 1.0, 0.0, -2.0);
    assertEquals(0.0, absent.getForwardDonorResidualN(), 0.0);
    assertEquals(0.0, absent.getLeftDonorMassKgPerM(), 0.0);
  }

  @Test
  void operatorAndAdapterCarryTheSameImmutableDirectionalLedger() throws Exception {
    TwoFluidSection left = section(0.5, 2.0, -1.0);
    TwoFluidSection right = section(1.5, 2.0, -1.0);
    TwoFluidSection[] cells = {left, right};
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeEnergyEquation(false);
    equations.setIncludeMassTransfer(false);
    equations.setEnableWaterOilSlip(true);
    equations.setAllowOutletPhaseBackflow(true);

    TransactionalEvaluation direct = equations.evaluateTransactional(cloneSections(cells), 1.0);
    DirectionalPhaseFaceFlux[][] directFaces = direct.getDirectionalPhaseFaceFluxes();
    assertEquals(1, directFaces.length);
    assertEquals(3, directFaces[0].length);
    assertEquals(direct.getPhaseMassFaceFluxes()[1][1], directFaces[0][1].getSignedMassFlowKgPerSecond(), 1.0e-12);
    assertEquals(direct.getPhaseMassFaceFluxes()[1][2], directFaces[0][2].getSignedMassFlowKgPerSecond(), 1.0e-12);
    assertEquals(0.0, directFaces[0][1].getForwardDonorResidualN(), 1.0e-12);
    assertEquals(0.0, directFaces[0][2].getReverseDonorResidualN(), 1.0e-12);

    TwoFluidUnsplitModelAdapter adapter = new TwoFluidUnsplitModelAdapter(equations, cells, 1.0,
        (cell, state, pressure, time) -> new double[] {10.0, 800.0, 1000.0});
    double[][] state = states(cells);
    double[] pressure = {left.getPressure(), right.getPressure()};
    UnsplitTransientSolver.Evaluation adapted = adapter.evaluate(state, pressure, state, pressure, 0.0, Double.NaN,
        false);
    DirectionalPhaseFaceFlux[][] adaptedFaces = adapted.getDirectionalPhaseFaceFluxes();
    assertEquals(directFaces[0][1].getSignedMassFlowKgPerSecond(), adaptedFaces[0][1].getSignedMassFlowKgPerSecond(),
        1.0e-12);
    assertNotSame(adaptedFaces, adapted.getDirectionalPhaseFaceFluxes());
    assertEquals(left.getLength(), adapted.getCellLengthsM()[0], 0.0);
    assertEquals(right.getLength(), adapted.getCellLengthsM()[1], 0.0);
    assertEquals(direct.getPhaseMassSourcesPerLength()[0][0], adapted.getPhaseMassSourcesPerLength()[0][0], 0.0);
    assertNotSame(adapted.getCellLengthsM(), adapted.getCellLengthsM());
    assertNotSame(adapted.getPhaseMassSourcesPerLength(), adapted.getPhaseMassSourcesPerLength());

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(adaptedFaces[0][1]);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      DirectionalPhaseFaceFlux restored = (DirectionalPhaseFaceFlux) input.readObject();
      assertEquals(adaptedFaces[0][1].getSignedMassFlowKgPerSecond(), restored.getSignedMassFlowKgPerSecond(), 0.0);
      assertEquals(adaptedFaces[0][1].getPressureTractionN(), restored.getPressureTractionN(), 0.0);
    }
  }

  private static TwoFluidSection section(double position, double oilVelocity, double waterVelocity) {
    TwoFluidSection section = new TwoFluidSection(position, 1.0, 0.1, 0.0);
    section.setGasDensity(10.0);
    section.setOilDensity(800.0);
    section.setWaterDensity(1000.0);
    section.setLiquidDensity(900.0);
    section.setGasHoldup(0.4);
    section.setLiquidHoldup(0.6);
    section.setOilHoldup(0.3);
    section.setWaterHoldup(0.3);
    section.setWaterCut(0.5);
    section.setGasVelocity(0.5);
    section.setOilVelocity(oilVelocity);
    section.setWaterVelocity(waterVelocity);
    section.setPressure(2.0e5);
    section.setGasSoundSpeed(300.0);
    section.setLiquidSoundSpeed(1200.0);
    section.setGasEnthalpy(1.0e5);
    section.setLiquidEnthalpy(1.0e5);
    section.updateConservativeVariables();
    section.extractPrimitiveVariables();
    return section;
  }

  private static TwoFluidSection[] cloneSections(TwoFluidSection[] sections) {
    TwoFluidSection[] result = new TwoFluidSection[sections.length];
    for (int cell = 0; cell < sections.length; cell++) {
      result[cell] = sections[cell].clone();
    }
    return result;
  }

  private static double[][] states(TwoFluidSection[] sections) {
    double[][] result = new double[sections.length][];
    for (int cell = 0; cell < sections.length; cell++) {
      result[cell] = sections[cell].getStateVector();
    }
    return result;
  }
}
