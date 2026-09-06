package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/** Independent first-law checks for the total-energy conservative variable. */
class TwoFluidTotalEnergyContractTest {
  @Test
  void stationaryWallFrictionCannotCreateTotalEnergy() {
    TwoFluidSection cell = cell();
    cell.setGasWallShear(30.0);
    cell.setLiquidWallShear(50.0);
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    assertEquals(0.0, equations.calcSourceTerms(new TwoFluidSection[] { cell })[0][6], 1e-10);
  }

  @Test
  void gravityWorkUsesEachSignedPhaseMomentum() {
    TwoFluidSection cell = cell();
    cell.setInclination(0.3);
    double expected = -9.81 * Math.sin(0.3)
        * (cell.getGasMomentumPerLength() + cell.getOilMomentumPerLength() + cell.getWaterMomentumPerLength());
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    assertEquals(expected, equations.calcSourceTerms(new TwoFluidSection[] { cell })[0][6], 1e-10);
  }

  @Test
  void aColderWallRemovesEnergy() {
    TwoFluidSection cell = cell();
    cell.setTemperature(320.0);
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setEnableHeatTransfer(true);
    equations.setHeatTransferCoefficient(10.0);
    equations.setSurfaceTemperature(300.0);
    double heat = equations.calcSourceTerms(new TwoFluidSection[] { cell })[0][6];
    assertTrue(heat < 0.0);
    assertEquals(-10.0 * Math.PI * cell.getDiameter() * 20.0, heat, 1e-10);
  }

  @Test
  void everyUniformFaceCarriesTotalPhaseEnthalpy() throws Exception {
    TwoFluidSection cell = cell();
    double expected = cell.getGasMomentumPerLength() * (cell.getGasEnthalpy() + 0.5 * 9.0)
        + cell.getOilMomentumPerLength() * (cell.getLiquidEnthalpy() + 0.5 * 4.0)
        + cell.getWaterMomentumPerLength() * (cell.getLiquidEnthalpy() + 0.5);
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setAllowOutletPhaseBackflow(true);
    for (String name : new String[] { "calcInletFlux", "calcOutletFlux" }) {
      Method method = TwoFluidConservationEquations.class.getDeclaredMethod(name, TwoFluidSection.class);
      method.setAccessible(true);
      assertEquals(expected, ((double[]) method.invoke(equations, cell))[6], 1e-7);
    }
    Method internal = TwoFluidConservationEquations.class.getDeclaredMethod("calcInterfaceFluxes",
        TwoFluidSection[].class, double.class);
    internal.setAccessible(true);
    double[][] flux = (double[][]) internal.invoke(equations, new TwoFluidSection[] { cell, cell.clone() }, 1.0);
    assertEquals(expected, flux[0][6], 1e-7);
  }

  private static TwoFluidSection cell() {
    TwoFluidSection cell = new TwoFluidSection(0.5, 1.0, 0.1, 0.0);
    cell.setGasDensity(10.0);
    cell.setOilDensity(800.0);
    cell.setWaterDensity(1000.0);
    cell.setLiquidDensity(900.0);
    cell.setGasHoldup(0.4);
    cell.setLiquidHoldup(0.6);
    cell.setOilHoldup(0.3);
    cell.setWaterHoldup(0.3);
    cell.setWaterCut(0.5);
    cell.setGasVelocity(3.0);
    cell.setOilVelocity(2.0);
    cell.setWaterVelocity(-1.0);
    cell.setGasEnthalpy(1e5);
    cell.setLiquidEnthalpy(1e5);
    cell.setPressure(2e5);
    cell.setGasSoundSpeed(300.0);
    cell.setLiquidSoundSpeed(1200.0);
    cell.updateConservativeVariables();
    return cell;
  }
}
