package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

class TwoFluidInletPressureBoundaryTest {
  @Test
  void flowInletRemovesTheSpuriousStationaryAlternatingPressureMode() {
    TwoFluidSection[] cells = new TwoFluidSection[8];
    for (int index = 0; index < cells.length; index++) {
      cells[index] = section(0.1, 0.4, 0.4);
      cells[index].setLength(5.0);
      cells[index].setGasHoldup(1.0);
      cells[index].setLiquidHoldup(0.0);
      cells[index].setOilHoldup(0.0);
      cells[index].setWaterHoldup(0.0);
      cells[index].setPressure(5.0e6 + (index % 2 == 0 ? 1000.0 : -1000.0));
      cells[index].setGasDensity(40.0 * cells[index].getPressure() / 5.0e6);
      cells[index].updateConservativeVariables();
    }
    TwoFluidSection feed = cells[0].clone();
    feed.setPressure(5.0e6);
    TwoFluidConservationEquations equations = equations();
    equations.setOutletBoundaryPressure(5.0e6);
    equations.setInletBoundaryState(feed);
    assertRest(equations.calcRHS(cells, 5.0));
    equations.setInletPhaseFlowBoundaryState(feed);
    double[][] rhs = equations.calcRHS(cells, 5.0);
    assertEquals(cells[0].getArea() * 1000.0 / 5.0, rhs[0][3], 1.0e-9);
    rhs[0][3] = 0.0;
    assertRest(rhs);
  }

  @Test
  void prescribedPhaseAdvectionUsesTrialPressureInBothTractionAndHoldupSource() {
    TwoFluidSection cell = section(0.1, 0.4, 0.4);
    TwoFluidSection feed = section(0.12, 0.6, 0.1);
    feed.setPressure(1.4e5);
    feed.setGasVelocity(3.0);
    feed.setOilVelocity(0.55);
    feed.setWaterVelocity(-0.45);
    feed.updateConservativeVariables();
    double[] expectedMass = { feed.getGasMassPerLength() * feed.getGasVelocity(),
        feed.getOilMassPerLength() * feed.getOilVelocity(), feed.getWaterMassPerLength() * feed.getWaterVelocity() };
    double[] velocity = { feed.getGasVelocity(), feed.getOilVelocity(), feed.getWaterVelocity() };
    TwoFluidConservationEquations equations = equations();
    equations.setClosedBoundaries(false, true);
    equations.setInletPhaseFlowBoundaryState(feed);
    // Mutating the caller's feed cannot alter the prescribed advection.
    feed.setGasVelocity(100.0);
    for (double pressure : new double[] { 2.0e5, 3.1e5, 2.0e5 }) {
      cell.setPressure(pressure);
      double[][] rhs = equations.calcRHS(new TwoFluidSection[] { cell.clone() }, cell.getLength());
      assertArrayEquals(expectedMass, equations.getLastMassBalanceRate().getInletMassFlowKgPerSecond(), 1.0e-12);
      for (int phase = 0; phase < 3; phase++) {
        assertEquals(expectedMass[phase] / cell.getLength(), rhs[0][phase], 1.0e-12);
        // At rest the pressure source must exactly cancel pressure traction, even across boundary area/holdup jumps.
        assertEquals(expectedMass[phase] * velocity[phase] / cell.getLength(), rhs[0][phase + 3], 1.0e-9);
      }
    }
    assertEquals(1.4e5, feed.getPressure(), 0.0);
  }

  @Test
  void legacyStateBoundaryStillPrescribesPressureAndBothSettersRestoreCellBoundary() {
    TwoFluidSection cell = section(0.1, 0.4, 0.4);
    TwoFluidSection feed = cell.clone();
    feed.setPressure(1.4e5);
    TwoFluidConservationEquations equations = equations();
    equations.setInletPhaseFlowBoundaryState(feed);
    assertRest(equations.calcRHS(new TwoFluidSection[] { cell.clone() }, cell.getLength()));
    equations.setInletBoundaryState(feed);
    double[][] forced = equations.calcRHS(new TwoFluidSection[] { cell.clone() }, cell.getLength());
    double force = 0.0;
    for (int phase = 0; phase < 3; phase++) {
      force += forced[0][phase + 3] * cell.getLength();
    }
    assertEquals((feed.getPressure() - cell.getPressure()) * cell.getArea(), force, 1.0e-9);
    equations.setInletBoundaryState(null);
    assertRest(equations.calcRHS(new TwoFluidSection[] { cell.clone() }, cell.getLength()));
    equations.setInletPhaseFlowBoundaryState(feed);
    equations.setInletPhaseFlowBoundaryState(null);
    assertRest(equations.calcRHS(new TwoFluidSection[] { cell.clone() }, cell.getLength()));
  }

  @Test
  void closedFlowBoundaryAndSerializationRetainPressureBalanceWithoutAdvection() throws Exception {
    TwoFluidSection cell = section(0.1, 0.4, 0.4);
    TwoFluidSection feed = section(0.12, 0.6, 0.1);
    feed.setPressure(1.4e5);
    feed.setGasVelocity(3.0);
    feed.setLiquidVelocity(0.5);
    feed.updateConservativeVariables();
    TwoFluidConservationEquations equations = equations();
    equations.setInletPhaseFlowBoundaryState(feed);
    equations.setClosedBoundaries(true, true);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(equations);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      equations = (TwoFluidConservationEquations) input.readObject();
    }
    assertTrue(equations.isConsistentPhasePressureEnabled());
    assertRest(equations.calcRHS(new TwoFluidSection[] { cell.clone() }, cell.getLength()));
    assertArrayEquals(new double[3], equations.getLastMassBalanceRate().getInletMassFlowKgPerSecond(), 0.0);
  }

  private static void assertRest(double[][] rhs) {
    for (double[] row : rhs) {
      assertArrayEquals(new double[7], row, 1.0e-9);
    }
  }

  private static TwoFluidConservationEquations equations() {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeEnergyEquation(false);
    equations.setConsistentPhasePressureEnabled(true);
    return equations;
  }

  private static TwoFluidSection section(double diameter, double gasHoldup, double waterHoldup) {
    TwoFluidSection cell = new TwoFluidSection(5.0, 10.0, diameter, 0.0);
    cell.setGasDensity(40.0);
    cell.setOilDensity(700.0);
    cell.setWaterDensity(1000.0);
    cell.setGasViscosity(1.2e-5);
    cell.setOilViscosity(1.0e-3);
    cell.setWaterViscosity(1.0e-3);
    cell.setGasHoldup(gasHoldup);
    cell.setLiquidHoldup(1.0 - gasHoldup);
    cell.setWaterCut(waterHoldup / (1.0 - gasHoldup));
    cell.setGasSoundSpeed(300.0);
    cell.setLiquidSoundSpeed(1200.0);
    cell.setPressure(2.0e5);
    cell.updateConservativeVariables();
    return cell;
  }
}
