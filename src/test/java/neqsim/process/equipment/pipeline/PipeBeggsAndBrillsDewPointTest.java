package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Pressure continuity regressions for separator gas at its dew point (issue #4654).
 *
 * @author NeqSim
 * @version 1.0
 */
class PipeBeggsAndBrillsDewPointTest {
  private static final Logger logger = LogManager.getLogger(PipeBeggsAndBrillsDewPointTest.class);

  /**
   * Creates the saturated separator gas from the reported public composition.
   *
   * @return separator gas at 48.5 degrees Celsius and 59.9 bara
   */
  private SystemInterface saturatedGas() {
    SystemInterface fluid = new SystemSrkEos(321.65, 60.0);
    String[] names = {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "n-pentane",
        "n-hexane", "n-heptane", "n-octane", "n-nonane", "nC10", "water"};
    double[] amounts = {0.01, 0.01, 0.90, 0.04, 0.015, 0.004, 0.004, 0.003, 0.003, 0.003, 0.003, 0.002, 0.002, 0.002};
    for (int i = 0; i < names.length; i++) {
      fluid.addComponent(names[i], amounts[i]);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(465000.0, "kg/hr");
    feed.setTemperature(48.5, "C");
    feed.setPressure(59.9, "bara");
    feed.run();
    ThreePhaseSeparator separator = new ThreePhaseSeparator("separator");
    separator.addStream(feed);
    separator.run();
    return separator.getGasOutStream().getFluid().clone();
  }

  /**
   * Microkelvin inlet changes must not cause a finite jump when condensate first appears.
   */
  @Test
  void outletPressureIsContinuousAtDewPoint() {
    SystemInterface gas = saturatedGas();
    for (boolean isothermal : new boolean[] {false, true}) {
      for (int increments : new int[] {1, 5, 20}) {
        checkPressureContinuity(gas, isothermal, increments);
      }
    }
  }

  /**
   * Checks the phase-appearance crossing for a thermal mode and spatial resolution.
   *
   * @param gas separator gas used to make independent inlet streams
   * @param isothermal whether to keep the temperature constant
   * @param increments number of spatial increments
   */
  private void checkPressureContinuity(SystemInterface gas, boolean isothermal, int increments) {
    double minimum = Double.POSITIVE_INFINITY;
    double maximum = Double.NEGATIVE_INFINITY;
    for (double offset : new double[] {-1e-6, -1e-7, -Math.ulp(321.65), 0.0, Math.ulp(321.65), 1e-7, 1e-6}) {
      Stream inlet = new Stream("inlet", gas.clone());
      inlet.setTemperature(321.65 + offset, "K");
      inlet.run();
      PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("pipe", inlet);
      pipe.setLength(32.73548);
      pipe.setDiameter(0.46676);
      pipe.setElevation(0.0);
      pipe.setPipeWallRoughness(4.6e-5);
      pipe.setRunIsothermal(isothermal);
      pipe.setNumberOfIncrements(increments);
      pipe.run();
      double pressure = pipe.getOutletStream().getPressure("bara");
      logger.info("isothermal={}, increments={}, dT={} K, outlet={} bara", isothermal, increments, offset, pressure);
      assertTrue(Double.isFinite(pressure) && pressure > 59.0 && pressure < 59.9);
      assertEquals(inlet.getFlowRate("kg/hr"), pipe.getOutletStream().getFlowRate("kg/hr"),
          1e-9 * inlet.getFlowRate("kg/hr"));
      for (double holdup : pipe.getLiquidHoldupProfile()) {
        assertTrue(Double.isFinite(holdup) && holdup >= 0.0 && holdup <= 1.0);
      }
      pipe.run();
      assertEquals(pressure, pipe.getOutletStream().getPressure("bara"), 1e-10, "Repeated pipe execution");
      minimum = Math.min(minimum, pressure);
      maximum = Math.max(maximum, pressure);
    }
    assertTrue(maximum - minimum < 1e-5, "Microkelvin outlet pressure spread: " + (maximum - minimum)
        + " bar, isothermal=" + isothermal + ", increments=" + increments);
    logger.info("isothermal={}, increments={}, pressure spread={} bar", isothermal, increments, maximum - minimum);
  }

  /**
   * A post-solve aqueous trial must reach equilibrium instead of leaking its arbitrary initial phase fraction.
   */
  @Test
  void reflashingSaturatedGasSolvesAqueousSeed() {
    SystemInterface gas = saturatedGas();
    gas.setTemperature(321.65 - 1e-6);
    new ThermodynamicOperations(gas).TPflash();
    new ThermodynamicOperations(gas).TPflash();
    gas.init(3);
    if (gas.hasPhaseType("aqueous")) {
      assertTrue(gas.getBeta(gas.getPhaseNumberOfPhase("aqueous")) < 1e-8,
          "Aqueous seed must be solved, not returned at its initial 1e-5 fraction");
    }
    for (int component = 0; component < gas.getNumberOfComponents(); component++) {
      double overall = gas.getComponent(component).getz();
      double balance = 0.0;
      double referenceLogFugacity = Math.log(gas.getPhase(0).getComponent(component).getx())
          + gas.getPhase(0).getComponent(component).getLogFugacityCoefficient();
      for (int phase = 0; phase < gas.getNumberOfPhases(); phase++) {
        balance += gas.getBeta(phase) * gas.getPhase(phase).getComponent(component).getx();
        double logFugacity = Math.log(gas.getPhase(phase).getComponent(component).getx())
            + gas.getPhase(phase).getComponent(component).getLogFugacityCoefficient();
        assertEquals(referenceLogFugacity, logFugacity, 1e-6, "Phase fugacity equality");
      }
      assertEquals(overall, balance, 1e-9, "Component mole balance");
    }
    for (int phase = 0; phase < gas.getNumberOfPhases(); phase++) {
      // The absolute symmetry identity has inverse-mole units. Normalize each
      // phase to avoid amplifying roundoff in the vanishing phase inventory.
      SystemInterface phaseFluid = gas.phaseToSystem(phase);
      phaseFluid.setTotalFlowRate(1.0, "mol/sec");
      phaseFluid.init(3);
      ThermodynamicModelTest checker = new ThermodynamicModelTest(phaseFluid);
      checker.setMaxError(1e-6);
      assertTrue(checker.checkFugacityCoefficients());
      assertTrue(checker.checkFugacityCoefficientsDP());
      assertTrue(checker.checkFugacityCoefficientsDT());
      assertTrue(checker.checkFugacityCoefficientsDn());
      assertTrue(checker.checkFugacityCoefficientsDn2());
      assertTrue(checker.checkNumerically(), "Analytic fugacity derivatives match refined finite differences");
    }
  }

  /**
   * The trace-liquid extension tends to the dry-gas multiplier without removing liquid holdup.
   */
  @Test
  void frictionHasContinuousDryGasLimit() {
    assertEquals(0.0, PipeBeggsAndBrills.frictionSlipExponent(0.0, 0.0));
    assertEquals(0.0, PipeBeggsAndBrills.frictionSlipExponent(1.0, 1.0), 0.0);
    for (int power = -16; power <= -6; power++) {
      double fraction = Math.pow(10.0, power);
      double holdup = 1.065 * Math.pow(fraction, 0.5824) / Math.pow(50.0, 0.0609);
      double exponent = PipeBeggsAndBrills.frictionSlipExponent(fraction, holdup);
      assertTrue(Double.isFinite(exponent) && exponent >= 0.0);
      if (power <= -10) {
        assertEquals(1.0, Math.exp(exponent), 1e-7, "Dry-gas friction limit at liquid fraction " + fraction);
      }
    }
  }

  /**
   * Both published exponent branches remain unchanged above the trace range, with a smooth upper join.
   */
  @Test
  void retainsPublishedWetFlowFriction() {
    for (double fraction : new double[] {1e-6, 1e-5, 0.01, 0.1, 0.5}) {
      for (double y : new double[] {0.5, 1.0, 1.1, 1.21, 2.0}) {
        double holdup = Math.sqrt(fraction / y);
        double logY = Math.log(y);
        double expected = y > 1.0 && y < 1.2 ? Math.log(2.2 * y - 1.2)
            : logY / (-0.0523 + 3.182 * logY - 0.8725 * Math.pow(logY, 2.0) + 0.01853 * Math.pow(logY, 4.0));
        assertEquals(expected, PipeBeggsAndBrills.frictionSlipExponent(fraction, holdup), 1e-12);
      }
    }
    double fraction = 1e-6;
    double holdup = 1.065 * Math.pow(fraction, 0.5824) / Math.pow(50.0, 0.0609);
    double center = PipeBeggsAndBrills.frictionSlipExponent(fraction, holdup);
    double step = 1e-11;
    double left = PipeBeggsAndBrills.frictionSlipExponent(fraction - step, holdup);
    double right = PipeBeggsAndBrills.frictionSlipExponent(fraction + step, holdup);
    assertEquals(center - left, right - center, 1e-8, "Smooth join at one ppm liquid by volume");
  }
}
