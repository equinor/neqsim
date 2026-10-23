package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;

/**
 * Regressions for phase-resolved film throughput and persistent wall thermal inventory.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TwoFluidPipeWallFilmStateTest {
  /**
   * A configuration refresh must not recreate the wall's stored energy.
   *
   * @throws Exception if the private thermal state cannot be installed
   */
  @Test
  void selectingEitherSplitPreservesExistingWallTemperatures() throws Exception {
    TwoFluidPipe pipe = new TwoFluidPipe("wall-state");
    double[] temperatures = {290.0, 295.0};
    Field wall = TwoFluidPipe.class.getDeclaredField("wallTemperatureProfile");
    wall.setAccessible(true);
    wall.set(pipe, temperatures);

    for (boolean enabled : new boolean[] {true, false, false, true}) {
      pipe.setPhysicalWallFilmSplit(enabled);
      assertEquals(enabled, pipe.isPhysicalWallFilmSplit());
      assertSame(temperatures, pipe.getWallTemperatureProfile(),
          "Changing resistance allocation must preserve the existing wall thermal inventory");
    }
  }

  /**
   * Opposite liquid phase momenta must not disguise finite throughput as stagnation.
   *
   * @throws Exception if the local film calculation cannot be invoked
   */
  @Test
  void oppositeOilWaterMomentaRetainTheirFilmThroughput() throws Exception {
    TwoFluidPipe pipe = new TwoFluidPipe("countercurrent-film");
    pipe.setDiameter(0.4);
    TwoFluidSection section = liquidSection();
    section.setLiquidVelocity(0.0);
    section.setOilVelocity(-1.25);
    section.setWaterVelocity(1.0);
    assertEquals(0.0, 0.5 * 800.0 * section.getOilVelocity() + 0.5 * 1000.0 * section.getWaterVelocity(), 0.0);
    double countercurrent = filmCoefficient(pipe, section);

    section.setLiquidVelocity(1000.0 / 900.0);
    section.setOilVelocity(1.25);
    section.setWaterVelocity(1.0);
    double cocurrent = filmCoefficient(pipe, section);
    assertTrue(cocurrent > pipe.getStagnantInnerHeatTransferCoefficient());
    assertEquals(cocurrent, countercurrent, 1.0e-12,
        "The sum of absolute phase mass fluxes is unchanged when one liquid reverses");
  }

  /**
   * The effective emulsion viscosity must be retained while unused phase properties are ignored.
   *
   * @throws Exception if the local film calculation cannot be invoked
   */
  @Test
  void filmUsesEffectiveLiquidViscosityAndIgnoresAbsentGas() throws Exception {
    TwoFluidPipe pipe = new TwoFluidPipe("phase-local-film");
    pipe.setDiameter(0.4);
    TwoFluidSection section = liquidSection();
    section.setLiquidVelocity(1000.0 / 900.0);
    section.setOilVelocity(1.25);
    section.setWaterVelocity(1.0);
    section.setOilViscosity(0.001);
    section.setWaterViscosity(0.002);
    section.setLiquidViscosity(0.012);
    double reynolds = 1000.0 * 0.4 / 0.012;
    double prandtl = 0.012 * 2100.0 / 0.12;
    double expected = 0.023 * Math.pow(reynolds, 0.8) * Math.pow(prandtl, 0.3) * 0.12 / 0.4;
    assertEquals(expected, filmCoefficient(pipe, section), 1.0e-12,
        "The film must retain the effective liquid viscosity from the oil-water closure");

    section.setGasDensity(Double.NaN);
    section.setGasViscosity(Double.NaN);
    section.setGasVelocity(Double.NaN);
    section.setLiquidVelocity(0.0);
    assertEquals(expected, filmCoefficient(pipe, section), 1.0e-12,
        "Unused gas properties and cancelling bulk liquid momentum must not alter the film");
  }

  /**
   * Make a liquid-full cell with equal oil and water volumes and independently known properties.
   *
   * @return cell before choosing its phase velocities
   */
  private static TwoFluidSection liquidSection() {
    TwoFluidSection section = new TwoFluidSection(0.0, 10.0, 0.4, 0.0);
    section.setGasHoldup(0.0);
    section.setLiquidHoldup(1.0);
    section.setOilHoldup(0.5);
    section.setWaterHoldup(0.5);
    section.setOilDensity(800.0);
    section.setWaterDensity(1000.0);
    section.setOilViscosity(0.001);
    section.setWaterViscosity(0.001);
    section.setLiquidDensity(900.0);
    section.setLiquidViscosity(0.001);
    return section;
  }

  /**
   * Evaluate the private local closure without requiring a hydraulic initialization.
   *
   * @param pipe pipe supplying the configured diameter and stagnant coefficient
   * @param section local phase state
   * @return fluid-side film coefficient in W/(m2 K)
   * @throws Exception if reflection or the closure invocation fails
   */
  private static double filmCoefficient(TwoFluidPipe pipe, TwoFluidSection section) throws Exception {
    Method method = TwoFluidPipe.class.getDeclaredMethod("sectionFilmCoefficient", TwoFluidSection.class);
    method.setAccessible(true);
    return (Double) method.invoke(pipe, section);
  }
}
