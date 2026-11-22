package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Independent bounds and energy checks for stiff local fluid-wall heat exchange.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TwoFluidPipeStiffWallThermalTest {
  /**
   * The adiabatic two-capacity problem has an independent exponential relaxation solution.
   */
  @Test
  void timeRefinementApproachesAnalyticalAdiabaticRelaxation() {
    double fluidCapacity = 1000.0;
    double wallCapacity = 100.0;
    double conductance = 100.0;
    double equilibrium = (fluidCapacity * 320.0 + wallCapacity * 280.0) / (fluidCapacity + wallCapacity);
    double exactFluid = equilibrium
        + (320.0 - equilibrium) * Math.exp(-conductance * (1.0 / fluidCapacity + 1.0 / wallCapacity));
    double previousError = Double.POSITIVE_INFINITY;
    for (int steps : new int[] {10, 20, 40, 80}) {
      double[] temperatures = {320.0, 280.0};
      for (int step = 0; step < steps; step++) {
        temperatures = TwoFluidPipe.advanceSimpleWallThermalState(temperatures[0], temperatures[1], 280.0,
            fluidCapacity, wallCapacity, conductance, 0.0, 0.0, 1.0 / steps);
      }
      assertEquals(fluidCapacity * 320.0 + wallCapacity * 280.0,
          fluidCapacity * temperatures[0] + wallCapacity * temperatures[1], 1.0e-6);
      double error = Math.abs(temperatures[0] - exactFluid);
      assertTrue(error < 0.55 * previousError, "Time refinement must converge to the analytical solution");
      previousError = error;
    }
    assertTrue(previousError < 0.01);
  }

  /**
   * A wall without heat capacity reduces to the declared series resistance rather than a fixed temperature.
   */
  @Test
  void masslessWallRecoversSeriesResistanceAndSourceEnergy() {
    double[] temperatures = TwoFluidPipe.advanceSimpleWallThermalState(320.0, 1000.0, 280.0, 100.0, 0.0, 1000.0, 10.0,
        20.0, 5.0);
    double ambientHeat = 5.0 * 10.0 * (temperatures[1] - 280.0);
    assertEquals(5.0 * 20.0 - ambientHeat, 100.0 * (temperatures[0] - 320.0), 1.0e-8);
    assertEquals(1000.0 * (temperatures[0] - temperatures[1]), 10.0 * (temperatures[1] - 280.0), 1.0e-8);
  }

  /**
   * Both configured resistance splits retain their quasi-steady temperature and ambient heat loss.
   */
  @Test
  void steadyWallAndFluidRemainFixedForEitherResistanceSplit() {
    for (double film : new double[] {10.0, 1000.0}) {
      double outer = 1.0 / (1.0 / 5.0 - 1.0 / film);
      double wall = (film * 320.0 + outer * 280.0) / (film + outer);
      double[] temperatures = TwoFluidPipe.advanceSimpleWallThermalState(320.0, wall, 280.0, 1000.0, 1.0, film, outer,
          5.0 * (320.0 - 280.0), 1000.0);
      assertEquals(320.0, temperatures[0], 1.0e-10);
      assertEquals(wall, temperatures[1], 1.0e-10);
    }
  }

  /**
   * A thin wall must cool without thermal oscillations at an otherwise admissible IMEX step.
   *
   * @throws Exception if the isolated thermal update cannot be invoked
   */
  @Test
  void thinWallCooldownStaysBoundedAndConservesEnergy() throws Exception {
    TwoFluidPipe pipe = gasCellPipe();
    pipe.setHeatTransferCoefficient(5.0);
    pipe.setWallProperties(0.0001, 7800.0, 500.0);
    pipe.setSurfaceTemperature(280.0, "K");
    setField(pipe, "wallTemperatureProfile", new double[] {300.0});
    TwoFluidSection cell = ((TwoFluidSection[]) getField(pipe, "sections"))[0];
    Method update = TwoFluidPipe.class.getDeclaredMethod("updateTransientTemperature", double.class, double[][].class,
        double[].class);
    update.setAccessible(true);

    for (int step = 0; step < 20; step++) {
      Object balance = update.invoke(pipe, 6.25, new double[2][3], new double[1]);
      double wall = pipe.getWallTemperatureProfile()[0];
      assertTrue(wall >= 280.0 && wall <= 300.0, "Unforced wall temperature escaped its physical bounds: " + wall);
      assertTrue(cell.getTemperature() >= 280.0 && cell.getTemperature() <= 300.0,
          "Unforced fluid temperature escaped its physical bounds: " + cell.getTemperature());
      double residual = (Double) getField(balance, "fluidEnergyChangeJ")
          + (Double) getField(balance, "wallEnergyChangeJ") + (Double) getField(balance, "ambientHeatLossJ");
      assertEquals(0.0, residual, 1.0e-5, "Fluid-wall exchange must cancel in the combined energy balance");
    }
    assertTrue(cell.getTemperature() < 300.0);
  }

  /**
   * Build one flowing gas cell without coupling the local thermal check to a hydraulic solver.
   *
   * @return pipe containing the prescribed cell
   * @throws Exception if the private cell array cannot be installed
   */
  private static TwoFluidPipe gasCellPipe() throws Exception {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 60.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream inlet = new Stream("thermal-inlet", fluid);
    inlet.setFlowRate(60.0, "kg/sec");
    inlet.run();
    TwoFluidPipe pipe = new TwoFluidPipe("stiff-wall", inlet);
    pipe.setDiameter(0.4);
    pipe.setNumberOfSections(1);
    pipe.setEnableJouleThomson(false);
    TwoFluidSection cell = new TwoFluidSection(0.0, 125.0, 0.4, 0.0);
    cell.setTemperature(300.0);
    cell.setPressure(6.0e6);
    cell.setGasHoldup(1.0);
    cell.setLiquidHoldup(0.0);
    cell.setOilHoldup(0.0);
    cell.setWaterHoldup(0.0);
    cell.setGasDensity(50.0);
    cell.setGasViscosity(1.0e-5);
    cell.setGasVelocity(10.0);
    cell.updateConservativeVariables();
    setField(pipe, "sections", new TwoFluidSection[] {cell});
    return pipe;
  }

  /**
   * Read isolated internal state for a conservation check.
   *
   * @param object object owning the field
   * @param name field name
   * @return field value
   * @throws Exception if reflection fails
   */
  private static Object getField(Object object, String name) throws Exception {
    Field field = object.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(object);
  }

  /**
   * Install an independently prescribed thermal state.
   *
   * @param object object owning the field
   * @param name field name
   * @param value field value
   * @throws Exception if reflection fails
   */
  private static void setField(Object object, String name, Object value) throws Exception {
    Field field = object.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(object, value);
  }
}
