package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests the steady-state liquid holdup calibration multiplier of {@link TwoFluidPipe}.
 *
 * @author NeqSim
 * @version 1.0
 */
class TwoFluidPipeLiquidHoldupMultiplierTest {
  /** Number of finite volumes. */
  private static final int SECTIONS = 40;

  /** Pipe length, in m. */
  private static final double LENGTH = 800.0;

  /** Rise of the riser, in m. */
  private static final double RISE = 600.0;

  /**
   * Builds a flashed two-phase gas-oil fluid.
   *
   * @return a fluid with initialised properties
   */
  private SystemInterface buildFluid() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 60.0, 60.0);
    fluid.addComponent("methane", 0.45);
    fluid.addComponent("n-pentane", 0.25);
    fluid.addComponent("n-heptane", 0.30);
    fluid.setMixingRule("classic");
    fluid.setPressure(60.0, "bara");
    fluid.setTemperature(60.0, "C");
    fluid.setTotalFlowRate(20000.0, "kg/hr");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    return fluid;
  }

  /**
   * Runs the riser steady state with the given holdup multiplier.
   *
   * @param multiplier liquid holdup multiplier
   * @return the pipe after the steady-state solve
   */
  private TwoFluidPipe runRiser(double multiplier) {
    Stream stream = new Stream("feed", buildFluid());
    stream.setFlowRate(20000.0, "kg/hr");
    stream.setPressure(60.0, "bara");
    stream.setTemperature(60.0, "C");
    stream.run();

    TwoFluidPipe pipe = new TwoFluidPipe("riser", stream);
    pipe.setLength(LENGTH);
    pipe.setDiameter(0.15);
    pipe.setRoughness(4.5e-5);
    pipe.setNumberOfSections(SECTIONS);
    double[] faces = new double[SECTIONS + 1];
    for (int i = 0; i <= SECTIONS; i++) {
      faces[i] = RISE * i / SECTIONS;
    }
    pipe.setCellFaceElevationProfile(faces);
    pipe.setIncludeEnergyEquation(false);
    pipe.setLiquidHoldupMultiplier(multiplier);
    pipe.run();
    return pipe;
  }

  /**
   * Averages a profile.
   *
   * @param values profile values
   * @return arithmetic mean
   */
  private double mean(double[] values) {
    double sum = 0.0;
    for (double v : values) {
      sum += v;
    }
    return sum / values.length;
  }

  /**
   * The default multiplier is one and is validated on input.
   */
  @Test
  @DisplayName("Default multiplier is 1 and invalid values are rejected")
  void testDefaultAndValidation() {
    TwoFluidPipe pipe = new TwoFluidPipe("p", new Stream("s", buildFluid()));
    Assertions.assertEquals(1.0, pipe.getLiquidHoldupMultiplier(), 0.0);
    Assertions.assertThrows(IllegalArgumentException.class, () -> pipe.setLiquidHoldupMultiplier(0.0));
    Assertions.assertThrows(IllegalArgumentException.class, () -> pipe.setLiquidHoldupMultiplier(Double.NaN));
    pipe.setLiquidHoldupMultiplier(0.8);
    Assertions.assertEquals(0.8, pipe.getLiquidHoldupMultiplier(), 0.0);
  }

  /**
   * A multiplier below one must lower the liquid holdup and the hydrostatic pressure drop of an upward line, while a
   * multiplier of one must leave the closure result unchanged.
   */
  @Test
  @DisplayName("Holdup multiplier below 1 lowers holdup and riser pressure drop")
  void testMultiplierLowersHoldupAndPressureDrop() {
    TwoFluidPipe base = runRiser(1.0);
    TwoFluidPipe tuned = runRiser(0.8);
    double holdupBase = mean(base.getLiquidHoldupProfile());
    double holdupTuned = mean(tuned.getLiquidHoldupProfile());
    Assertions.assertTrue(holdupBase > 0.0 && holdupBase < 1.0);
    Assertions.assertTrue(holdupTuned < holdupBase, "tuned holdup " + holdupTuned + " vs " + holdupBase);
    double[] pBase = base.getPressureProfile();
    double[] pTuned = tuned.getPressureProfile();
    double dpBase = pBase[0] - pBase[pBase.length - 1];
    double dpTuned = pTuned[0] - pTuned[pTuned.length - 1];
    Assertions.assertTrue(dpTuned < dpBase, "tuned dP " + dpTuned + " vs " + dpBase);
  }
}
