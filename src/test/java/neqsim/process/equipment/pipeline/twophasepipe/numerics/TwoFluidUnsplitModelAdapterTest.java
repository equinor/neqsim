package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import org.junit.jupiter.api.Test;

class TwoFluidUnsplitModelAdapterTest {
  @Test
  void repeatedResidualProbesAreTransactionalAndUseTheOutletFacePressure() {
    TwoFluidSection[] accepted = { section(0.0), section(10.0) };
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeEnergyEquation(false);
    equations.setIncludeMassTransfer(false);

    equations.calcRHS(cloneSections(accepted), 10.0);
    TwoFluidConservationEquations.MassBalanceRate acceptedBalance = equations.getLastMassBalanceRate();
    double[][] acceptedFaces = equations.getLastPhaseMassFaceFluxes();
    boolean acceptedBackflow = equations.isOutletBackflowClamped();

    double[][] state = { accepted[0].getStateVector(), accepted[1].getStateVector() };
    double[][] acceptedState = { state[0].clone(), state[1].clone() };
    double[] pressure = { accepted[0].getPressure(), accepted[1].getPressure() };
    double[] area = { accepted[0].getArea(), accepted[1].getArea() };
    TwoFluidUnsplitModelAdapter adapter = new TwoFluidUnsplitModelAdapter(equations, accepted, 10.0,
        (cell, conservativeState, cellPressure, time) ->
            new double[] { 40.0 + 1.0e-6 * (cellPressure - 5.0e6), 700.0, 1000.0 });
    UnsplitTransientSolver solver = new UnsplitTransientSolver();

    double[] free = solver.residual(state, pressure, state, pressure, area, 0.05, 0.0, Double.NaN, false, adapter);
    double[] repeated = solver.residual(state, pressure, state, pressure, area, 0.05, 0.0, Double.NaN, false, adapter);
    double[] fixed = solver.residual(state, pressure, state, pressure, area, 0.05, 0.0, 4.0e6, true, adapter);

    assertArrayEquals(free, repeated, 0.0);
    assertNotEquals(free[10], fixed[10], "Outlet gas momentum must use the prescribed face pressure");
    assertNotEquals(free[11], fixed[11], "Outlet oil momentum must use the prescribed face pressure");
    assertSame(acceptedBalance, equations.getLastMassBalanceRate());
    assertMatrixEquals(acceptedFaces, equations.getLastPhaseMassFaceFluxes());
    if (acceptedBackflow) {
      assertSame(Boolean.TRUE, Boolean.valueOf(equations.isOutletBackflowClamped()));
    } else {
      assertSame(Boolean.FALSE, Boolean.valueOf(equations.isOutletBackflowClamped()));
    }
    assertArrayEquals(acceptedState[0], accepted[0].getStateVector(), 0.0);
    assertArrayEquals(acceptedState[1], accepted[1].getStateVector(), 0.0);
    assertArrayEquals(pressure, new double[] { accepted[0].getPressure(), accepted[1].getPressure() }, 0.0);
  }

  private static TwoFluidSection section(double position) {
    TwoFluidSection section = new TwoFluidSection(position, 10.0, 0.1, 0.0);
    section.setPressure(5.0e6);
    section.setTemperature(300.0);
    section.setGasDensity(40.0);
    section.setOilDensity(700.0);
    section.setWaterDensity(1000.0);
    section.setLiquidDensity(850.0);
    section.setGasViscosity(1.2e-5);
    section.setOilViscosity(1.0e-3);
    section.setWaterViscosity(1.0e-3);
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
    section.setGasVelocity(3.0);
    section.setLiquidVelocity(0.5);
    section.setOilVelocity(0.55);
    section.setWaterVelocity(0.45);
    section.updateConservativeVariables();
    section.updateDerivedQuantities();
    return section;
  }

  private static TwoFluidSection[] cloneSections(TwoFluidSection[] sections) {
    TwoFluidSection[] copy = new TwoFluidSection[sections.length];
    for (int cell = 0; cell < sections.length; cell++) {
      copy[cell] = sections[cell].clone();
    }
    return copy;
  }

  private static void assertMatrixEquals(double[][] expected, double[][] actual) {
    org.junit.jupiter.api.Assertions.assertEquals(expected.length, actual.length);
    for (int row = 0; row < expected.length; row++) {
      assertArrayEquals(expected[row], actual[row], 0.0);
    }
  }
}
