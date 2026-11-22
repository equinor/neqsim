package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
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
    return buildFluid(false);
  }

  /**
   * Builds a flashed fluid with optional produced water.
   *
   * @param includeWater whether to include a separate aqueous phase
   * @return the fluid with initialized caloric and transport properties
   */
  private SystemInterface buildFluid(boolean includeWater) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 60.0, 60.0);
    fluid.addComponent("methane", 0.45);
    fluid.addComponent("n-pentane", 0.25);
    fluid.addComponent("n-heptane", 0.30);
    if (includeWater) {
      fluid.addComponent("water", 0.6);
      fluid.setMultiPhaseCheck(true);
    }
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

  /**
   * Builds a calibrated riser with changing cell lengths and inclinations.
   *
   * @param multiplier steady liquid-holdup calibration
   * @return the steady pipe
   */
  private TwoFluidPipe nonuniformRiser(double multiplier) {
    return nonuniformRiser(multiplier, false);
  }

  /**
   * Builds a nonuniform riser with an optional separate aqueous phase.
   *
   * @param multiplier steady liquid-holdup calibration
   * @param includeWater whether to add produced water
   * @return the steady pipe
   */
  private TwoFluidPipe nonuniformRiser(double multiplier, boolean includeWater) {
    SystemInterface fluid = buildFluid(includeWater);
    Stream feed = new Stream("feed", fluid);
    feed.run();
    TwoFluidPipe pipe = new TwoFluidPipe("nonuniform riser", feed);
    double[] lengths = {30.0, 70.0, 100.0, 200.0, 150.0, 150.0, 70.0, 30.0};
    double[] faces = {0.0, 3.0, 24.0, 84.0, 244.0, 379.0, 514.0, 577.0, 600.0};
    pipe.setLength(LENGTH);
    pipe.setDiameter(0.15);
    pipe.setNumberOfSections(lengths.length);
    pipe.setSectionLengths(lengths);
    pipe.setCellFaceElevationProfile(faces);
    pipe.setIncludeEnergyEquation(false);
    pipe.setLiquidHoldupMultiplier(multiplier);
    pipe.setOutletPressure(30.0, "bara");
    pipe.run();
    return pipe;
  }

  /**
   * A nonuniform face-based riser must keep the steady pressure placement at the transient handoff.
   */
  @Test
  void testNonuniformRiserPressureHandoff() {
    for (double multiplier : new double[] {1.0, 0.53}) {
      TwoFluidPipe pipe = nonuniformRiser(multiplier);
      Assertions.assertTrue(pipe.isSteadyStateConverged());
      double[] steady = pipe.getPressureProfile();
      pipe.runTransient(1.0e-6, null);
      double[] transientPressure = pipe.getPressureProfile();
      for (int cell = 0; cell < steady.length; cell++) {
        Assertions.assertEquals(steady[cell], transientPressure[cell], 1000.0,
            "pressure handoff at cell " + cell + " with holdup multiplier " + multiplier);
      }
    }

  }

  /**
   * Consistency calibration must use the same prescribed outlet-face traction as every transient stage.
   */
  @Test
  void testCalibrationUsesPrescribedOutletTraction() {
    TwoFluidPipe pipe = nonuniformRiser(0.53);
    TwoFluidSection[] before = pipe.getSectionSnapshots();
    pipe.runTransient(0.001, null);
    TwoFluidSection[] after = pipe.getSectionSnapshots();
    int last = before.length - 1;
    Assertions.assertEquals(before[last].getGasVelocity(), after[last].getGasVelocity(), 1.0e-4,
        "unchanged outlet traction must not seed gas acceleration");
    Assertions.assertEquals(before[last].getOilVelocity(), after[last].getOilVelocity(), 1.0e-4,
        "unchanged outlet traction must not seed oil acceleration");
  }

  /**
   * A hydraulic flash must refresh caloric properties before publishing conserved energy.
   */
  @Test
  void testSteadyPhaseEnthalpiesMatchLocalFlash() {
    for (boolean includeWater : new boolean[] {false, true}) {
      TwoFluidPipe pipe = nonuniformRiser(1.0, includeWater);
      for (TwoFluidSection section : pipe.getSectionSnapshots()) {
        SystemInterface local = pipe.getInletStream().getFluid().clone();
        local.setPressure(section.getPressure() / 1.0e5, "bara");
        local.setTemperature(section.getTemperature(), "K");
        new ThermodynamicOperations(local).TPflash();
        local.initProperties();
        if (section.getGasMassPerLength() > 0.0) {
          Assertions.assertEquals(local.getPhase("gas").getEnthalpy("J/kg"), section.getGasEnthalpy(), 1.0,
              "gas enthalpy must belong to the reported local P/T");
        }
        if (section.getOilMassPerLength() > 0.0) {
          Assertions.assertEquals(local.getPhase("oil").getEnthalpy("J/kg"), section.getOilEnthalpy(), 1.0,
              "oil enthalpy must belong to the reported local P/T");
        }
        if (section.getWaterMassPerLength() > 0.0) {
          Assertions.assertEquals(local.getPhase("aqueous").getEnthalpy("J/kg"), section.getWaterEnthalpy(), 1.0,
              "aqueous enthalpy must belong to the reported local P/T");
        }
      }
    }
  }
}
