package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos1978;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression for aqueous phase loss in the public wet rich gas example from issue #4659.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TPflashIssue4659WetRichGasTest {
  /**
   * Builds the synthetic PR78 fluid reported in the issue.
   *
   * @param temperature temperature in kelvin
   * @param pressure absolute pressure in bar
   * @return fluid with classic mixing rule and multiphase checking
   */
  private SystemInterface fluid(double temperature, double pressure) {
    SystemInterface fluid = new SystemPrEos1978(temperature, pressure);
    String[] names = {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane",
        "n-pentane", "n-hexane", "n-heptane", "n-octane", "n-nonane", "water"};
    double[] amounts = {0.001, 0.003, 0.17, 0.21, 0.35, 0.043, 0.126, 0.024, 0.029, 0.011, 0.008, 0.0026, 0.0004,
        0.021};
    for (int i = 0; i < names.length; i++) {
      fluid.addComponent(names[i], amounts[i]);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /** Verifies that a stable aqueous phase is solved rather than returned as an unequilibrated seed. */
  @Test
  void tpScanKeepsAqueousPhaseAndContinuousEntropy() {
    double previousEntropy = Double.NaN;
    double previousTemperature = Double.NaN;
    for (double temperature : new double[] {327.0, 327.2, 327.4, 327.42, 327.45, 327.5, 327.6, 328.0}) {
      SystemInterface state = fluid(temperature, 8.0);
      new ThermodynamicOperations(state).TPflash();
      state.init(3);
      assertEquals(3, state.getNumberOfPhases(), "T=" + temperature);
      assertTrue(state.hasPhaseType(PhaseType.AQUEOUS), "T=" + temperature);
      assertTrue(state.getPhase(PhaseType.AQUEOUS).getBeta() > 0.003, "T=" + temperature);
      if (Double.isFinite(previousEntropy)) {
        double slope = (state.getEntropy() - previousEntropy) / (temperature - previousTemperature);
        assertTrue(slope > 0.0 && slope < 1.0, "dS/dT=" + slope + " at T=" + temperature);
      }
      assertEquilibrium(state);
      previousEntropy = state.getEntropy();
      previousTemperature = temperature;
    }
  }

  /** Checks direct PS closure with the equilibrium inlet entropy, without relaxing PSFlash acceptance. */
  @Test
  void directPsFlashClosesEntropy() {
    SystemInterface state = fluid(285.0, 1.9);
    ThermodynamicOperations operations = new ThermodynamicOperations(state);
    operations.TPflash();
    state.init(3);
    double entropy = state.getEntropy();
    state.setPressure(8.0);
    operations.PSflash(entropy);
    state.init(3);
    assertEquals(entropy, state.getEntropy(), PSFlash.entropyTolerance(state, entropy));
    assertTrue(state.getTemperature() > 327.42 && state.getTemperature() < 328.0);
    assertTrue(state.doMultiPhaseCheck());
    assertEquilibrium(state);
  }

  /** Checks cold, reused and cloned states while heating through phase disappearance and cooling back. */
  @Test
  void reusedAndClonedStatesCrossTheDewRegion() {
    SystemInterface reused = fluid(327.0, 8.0);
    for (double temperature : new double[] {327.4, 327.45, 328.0, 330.0, 331.0, 331.5, 331.9, 332.0, 340.0, 360.0,
        332.0, 331.9, 331.5, 330.0, 328.0, 327.45, 327.4}) {
      SystemInterface reference = fluid(temperature, 8.0);
      new ThermodynamicOperations(reference).TPflash();
      reference.init(3);
      reused.setTemperature(temperature);
      new ThermodynamicOperations(reused).TPflash();
      reused.init(3);
      assertEquals(reference.getNumberOfPhases(), reused.getNumberOfPhases(), "T=" + temperature);
      assertEquals(reference.getEntropy(), reused.getEntropy(), 1.0e-7, "T=" + temperature);
      assertEquilibrium(reused);
      for (int phase = 0; phase < reference.getNumberOfPhases(); phase++) {
        PhaseType type = reference.getPhase(phase).getType();
        assertTrue(reused.hasPhaseType(type));
        assertEquals(reference.getBeta(phase), reused.getPhase(type).getBeta(), 1.0e-8);
      }
      reused = reused.clone();
    }
  }

  /** Checks the PR78 fugacity and analytic derivative identities on the recovered three-phase state. */
  @Test
  void recoveredStateRetainsThermodynamicConsistency() {
    SystemInterface state = fluid(327.45, 8.0);
    new ThermodynamicOperations(state).TPflash();
    state.init(3);
    ThermodynamicModelTest modelTest = new ThermodynamicModelTest(state);
    modelTest.setMaxError(1.0e-8);
    assertTrue(modelTest.checkFugacityCoefficients());
    assertTrue(modelTest.checkFugacityCoefficientsDn());
    assertTrue(modelTest.checkFugacityCoefficientsDn2());
    assertTrue(modelTest.checkFugacityCoefficientsDP());
    assertTrue(modelTest.checkFugacityCoefficientsDT());
    // Trace solutes in the tiny aqueous phase limit the finite-difference resolution of mole derivatives.
    modelTest.setMaxError(1.0e-5);
    assertTrue(modelTest.checkNumerically());
  }

  /** Exercises the originally failing 40-step polytropic compressor. */
  @Test
  void polytropicCompressorConverges() {
    Stream feed = new Stream("feed", fluid(285.0, 1.9));
    feed.setFlowRate(22000.0, "kg/hr");
    feed.run();
    Compressor compressor = new Compressor("compressor", feed);
    compressor.setOutletPressure(8.0, "bara");
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicEfficiency(0.67);
    compressor.setNumberOfCompressorCalcSteps(40);
    compressor.run();
    SystemInterface outlet = compressor.getOutletStream().getThermoSystem();
    assertEquals(8.0, outlet.getPressure(), 1.0e-10);
    assertTrue(outlet.getTemperature() > feed.getTemperature());
    assertTrue(Double.isFinite(compressor.getPower()) && compressor.getPower() > 0.0);
    assertEquilibrium(outlet);
  }

  /**
   * Checks normalization, component conservation and equal component fugacities.
   *
   * @param state calculated equilibrium state
   */
  private void assertEquilibrium(SystemInterface state) {
    double betaSum = 0.0;
    for (int phase = 0; phase < state.getNumberOfPhases(); phase++) {
      assertTrue(Double.isFinite(state.getBeta(phase)) && state.getBeta(phase) > 0.0);
      betaSum += state.getBeta(phase);
      double sum = 0.0;
      for (int component = 0; component < state.getNumberOfComponents(); component++) {
        double x = state.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(x) && x >= 0.0 && x <= 1.0);
        sum += x;
      }
      assertEquals(1.0, sum, 1.0e-10);
    }
    assertEquals(1.0, betaSum, 1.0e-10);
    for (int component = 0; component < state.getNumberOfComponents(); component++) {
      double recombined = 0.0;
      double reference = state.getPhase(0).getComponent(component).getx()
          * state.getPhase(0).getComponent(component).getFugacityCoefficient();
      for (int phase = 0; phase < state.getNumberOfPhases(); phase++) {
        double x = state.getPhase(phase).getComponent(component).getx();
        recombined += state.getBeta(phase) * x;
        double fugacity = x * state.getPhase(phase).getComponent(component).getFugacityCoefficient();
        assertEquals(0.0, Math.log(fugacity / reference), 1.0e-7, "phase/component=" + phase + "/" + component);
      }
      assertEquals(state.getPhase(0).getComponent(component).getz(), recombined, 1.0e-9);
    }
  }
}
