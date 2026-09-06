package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class TwoFluidVariableAreaPressureRegressionTest {
  @Test
  void constantPressureAtRestIsBalancedAcrossChangingAreaInEveryPhase() {
    for (double gasHoldup : new double[] { 1.0, 0.4, 0.0 }) {
      TwoFluidSection[] cells = { cell(0.5, 1.0, 0.1, gasHoldup), cell(2.0, 2.0, 0.2, gasHoldup),
          cell(4.5, 3.0, 0.15, gasHoldup) };
      TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
      equations.setEnableInterfacialPressure(true);

      double[][] rhs = equations.calcRHS(cells, 1.0);

      for (int index = 0; index < cells.length; index++) {
        for (int phase = 0; phase < 3; phase++) {
          assertEquals(0.0, rhs[index][phase], 1.0e-12);
          assertEquals(0.0, rhs[index][phase + 3], 1.0e-9,
              "Constant pressure cannot accelerate cell " + index + ", phase " + phase + ", alphaGas=" + gasHoldup);
        }
      }
    }
  }

  private static TwoFluidSection cell(double position, double length, double diameter, double gasHoldup) {
    TwoFluidSection cell = new TwoFluidSection(position, length, diameter, 0.0);
    cell.setGasDensity(10.0);
    cell.setOilDensity(800.0);
    cell.setWaterDensity(1000.0);
    cell.setLiquidDensity(900.0);
    cell.setGasViscosity(1.0e-5);
    cell.setOilViscosity(1.0e-3);
    cell.setWaterViscosity(1.0e-3);
    cell.setLiquidViscosity(1.0e-3);
    cell.setGasHoldup(gasHoldup);
    cell.setLiquidHoldup(1.0 - gasHoldup);
    cell.setWaterCut(0.5);
    cell.setGasVelocity(0.0);
    cell.setLiquidVelocity(0.0);
    cell.setGasSoundSpeed(300.0);
    cell.setLiquidSoundSpeed(1200.0);
    cell.setPressure(2.0e5);
    cell.updateConservativeVariables();
    return cell;
  }
}
