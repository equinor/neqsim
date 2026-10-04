package neqsim.thermo.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.util.EquilibriumSoundSpeed.Result;
import neqsim.thermo.util.EquilibriumSoundSpeed.State;
import neqsim.thermo.util.EquilibriumSoundSpeed.Status;
import neqsim.thermo.util.EquilibriumSoundSpeed.Stencil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Engineering and API regression coverage for issue #3752. */
class EquilibriumSoundSpeedTest extends neqsim.NeqSimTest {
  private static final Logger logger = LogManager.getLogger(EquilibriumSoundSpeedTest.class);

  private SystemInterface feed(String impurity) {
    SystemInterface fluid = new SystemPrEos(298.15, 150.0);
    fluid.addComponent("CO2", impurity == null ? 1.0 : 0.98);
    if (impurity != null) {
      fluid.addComponent(impurity, 0.02);
    }
    fluid.setMixingRule(2);
    fluid.useVolumeCorrection(false);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    return fluid;
  }

  private void check(Result result) {
    assertTrue(result.isConverged(), result.getStatus() + ": " + result.getMessage());
    assertTrue(Double.isFinite(result.getSoundSpeed()) && result.getSoundSpeed() > 0.0);
    assertTrue(result.getRelativeStepError() <= 0.002);
    assertTrue(result.getFlashEvaluations() >= 5);
    for (State state : result.getSamples()) {
      assertEquals(0.0, state.getEntropyResidual(), 1.0e-7);
      assertEquals(0.0, state.getComponentResidual(), 1.0e-8);
      assertEquals(0.0, state.getFugacityResidual(), 1.0e-6);
      double phaseSum = 0.0;
      for (double beta : state.getPhaseFractions()) {
        assertTrue(beta >= 0.0 && beta <= 1.0);
        phaseSum += beta;
      }
      assertEquals(1.0, phaseSum, 1.0e-8);
    }
    logger.info("Equilibrium acoustic result: c={} m/s, stencil={}, step={} Pa, error={}, flashes={}",
        result.getSoundSpeed(), result.getStencil(), result.getPressureStepPa(), result.getRelativeStepError(),
        result.getFlashEvaluations());
  }

  @Test
  void singlePhaseAnalyticalAndIdealGasLimits() {
    SystemInterface dense = feed(null);
    Result result = dense.calculateEquilibriumSoundSpeed();
    check(result);
    assertEquals(456.23, result.getSoundSpeed(), 0.05);
    assertEquals(dense.getPhase(0).getSoundSpeed(), result.getSoundSpeed(), 0.01);
    assertEquals(Stencil.CENTRAL, result.getStencil());
    assertFalse(result.isPhaseBoundaryEncountered());

    SystemInterface gas = new SystemPrEos(350.0, 0.001);
    gas.addComponent("nitrogen", 1.0);
    gas.setMixingRule(2);
    gas.useVolumeCorrection(false);
    new ThermodynamicOperations(gas).TPflash();
    gas.init(3);
    Result dilute = gas.calculateEquilibriumSoundSpeed();
    check(dilute);
    double ideal = Math.sqrt(gas.getCp() / gas.getCv() * 8.314462618 * 350.0 / gas.getMolarMass());
    assertEquals(ideal, dilute.getSoundSpeed(), ideal * 2.0e-4);
  }

  @Test
  void pureCo2TwoPhaseDerivativeDiffersFromLegacyAverage() {
    SystemInterface fluid = feed(null);
    double entropy = fluid.getEntropy("J/kgK");
    fluid.setPressure(40.0);
    new ThermodynamicOperations(fluid).PSflash(entropy, "J/kgK");
    fluid.init(3);
    assertEquals(2, fluid.getNumberOfPhases());
    Result result = fluid.calculateEquilibriumSoundSpeed();
    check(result);
    assertEquals(57.08, result.getSoundSpeed(), 0.03);
    assertEquals(408.76, fluid.getSoundSpeed(), 0.05);
    assertTrue(result.getSoundSpeed() < fluid.getSoundSpeed() / 5.0);
    double lowerDensity = nativePureDensity(feed(null), 39.99, entropy);
    double upperDensity = nativePureDensity(feed(null), 40.01, entropy);
    assertEquals(Math.sqrt(2000.0 / (upperDensity - lowerDensity)), result.getSoundSpeed(), 0.01);
    assertEquals(2, result.getSamples()[0].getPhaseTypes().length);
  }

  private double nativePureDensity(SystemInterface fluid, double pressure, double entropy) {
    fluid.setPressure(pressure);
    new ThermodynamicOperations(fluid).PSflash(entropy, "J/kgK");
    fluid.init(3);
    assertEquals(entropy, fluid.getEntropy("J/kgK"), 1.0e-6);
    return fluid.getMass("kg") / fluid.getVolume("m3");
  }

  @Test
  void mixtureIsentropesUseCheckedRootsAndConserveComponents() {
    for (String impurity : new String[] {"nitrogen", "hydrogen"}) {
      SystemInterface original = feed(impurity);
      double target = original.getEntropy("J/kgK");
      for (double pressure : new double[] {50.0, 40.0, 20.0}) {
        SystemInterface fluid = referenceTpRoot(original, pressure, target);
        assertEquals(2, fluid.getNumberOfPhases());
        if (impurity.equals("nitrogen") && pressure == 50.0) {
          assertEquals(281.2568234268, fluid.getTemperature(), 1.0e-7);
          assertEquals(642.5310058, fluid.getMass("kg") / fluid.getVolume("m3"), 1.0e-4);
        }
        Result result = fluid.calculateEquilibriumSoundSpeed();
        check(result);
        assertEquals(target, result.getSpecificEntropy(), 1.0e-7);
        double low = referenceTpRoot(original, pressure - 0.01, target).getDensity();
        double high = referenceTpRoot(original, pressure + 0.01, target).getDensity();
        double reference = Math.sqrt(2000.0 / (high - low));
        assertEquals(reference, result.getSoundSpeed(), reference * 5.0e-4);
      }
    }
  }

  // Independent wide temperature bracket, with fresh source clones at each reference point.
  private SystemInterface referenceTpRoot(SystemInterface original, double pressure, double entropy) {
    double lower = 200.0;
    double upper = 350.0;
    SystemInterface trial = null;
    for (int i = 0; i < 60; i++) {
      trial = original.clone();
      trial.setPressure(pressure);
      trial.setTemperature((lower + upper) / 2.0);
      new ThermodynamicOperations(trial).TPflash();
      trial.init(3);
      double error = trial.getEntropy("J/kgK") - entropy;
      if (Math.abs(error) < 1.0e-9) {
        break;
      }
      if (error > 0.0) {
        upper = trial.getTemperature();
      } else {
        lower = trial.getTemperature();
      }
    }
    assertEquals(entropy, trial.getEntropy("J/kgK"), 1.0e-7);
    return trial;
  }

  @Test
  void stepSensitivityInventoryScalingAndPhysicalDensityCorrection() {
    SystemInterface fluid = referenceTpRoot(feed("nitrogen"), 40.0, feed("nitrogen").getEntropy("J/kgK"));
    Result baseline = fluid.calculateEquilibriumSoundSpeed();
    check(baseline);
    for (double step : new double[] {0.0002, 0.002, 0.01}) {
      Result result = EquilibriumSoundSpeed.calculate(fluid, step);
      check(result);
      assertEquals(baseline.getSoundSpeed(), result.getSoundSpeed(), baseline.getSoundSpeed() * 0.002);
    }
    fluid.setTotalNumberOfMoles(1000.0);
    fluid.init(3);
    Result scaled = fluid.calculateEquilibriumSoundSpeed();
    check(scaled);
    assertEquals(baseline.getSoundSpeed(), scaled.getSoundSpeed(), 0.01);
    fluid.useVolumeCorrection(true);
    Result corrected = fluid.calculateEquilibriumSoundSpeed();
    check(corrected);
    assertEquals(scaled.getSoundSpeed(), corrected.getSoundSpeed(), 0.01);
    assertEquals(fluid.getMass("kg") / fluid.getVolume("m3"), corrected.getSamples()[0].getDensity(), 1.0e-5);
  }

  @Test
  void phaseEntryReportsOneSidedStencil() {
    SystemInterface fluid = feed(null);
    fluid.setPressure(46.5);
    fluid.setTemperature(284.3849043789973);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    assertEquals(1, fluid.getNumberOfPhases());
    Result result = fluid.calculateEquilibriumSoundSpeed(0.05);
    check(result);
    assertTrue(result.isPhaseBoundaryEncountered());
    assertEquals(Stencil.FORWARD, result.getStencil());
    for (State state : result.getSamples()) {
      assertTrue(state.getPressurePa() >= result.getSamples()[0].getPressurePa());
      assertEquals(1, state.getPhaseTypes().length);
    }
  }

  @Test
  void callerStateWarmStartAndResultImmutabilityArePreserved() throws Exception {
    SystemInterface fluid = feed(null);
    double entropy = fluid.getEntropy("J/kgK");
    fluid.setPressure(40.0);
    new ThermodynamicOperations(fluid).PSflash(entropy, "J/kgK");
    fluid.init(3);
    double temperature = fluid.getTemperature();
    double beta = fluid.getBeta(0);
    double volume = fluid.getVolume();
    double legacy = fluid.getSoundSpeed();
    boolean previous = ThermodynamicModelSettings.isUseWarmStartKValues();
    try {
      ThermodynamicModelSettings.setUseWarmStartKValues(true);
      Result first = fluid.calculateEquilibriumSoundSpeed();
      check(first);
      assertTrue(ThermodynamicModelSettings.isUseWarmStartKValues());
      Result second = fluid.calculateEquilibriumSoundSpeed();
      assertEquals(first.getSoundSpeed(), second.getSoundSpeed(), 1.0e-10);
      assertEquals(temperature, fluid.getTemperature(), 0.0);
      assertEquals(40.0, fluid.getPressure(), 0.0);
      assertEquals(beta, fluid.getBeta(0), 0.0);
      assertEquals(volume, fluid.getVolume(), 0.0);
      assertEquals(legacy, fluid.getSoundSpeed(), 0.0);
      assertEquals(entropy, fluid.getEntropy("J/kgK"), 1.0e-8);
      double[] fractions = first.getSamples()[0].getPhaseFractions();
      double[] expected = fractions.clone();
      fractions[0] = -1.0;
      first.getSamples()[0] = null;
      assertArrayEquals(expected, first.getSamples()[0].getPhaseFractions());
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
        output.writeObject(first);
      }
      try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
        Result copy = (Result) input.readObject();
        assertEquals(first.getSoundSpeed(), copy.getSoundSpeed(), 0.0);
        assertEquals(first.getStencil(), copy.getStencil());
      }
    } finally {
      ThermodynamicModelSettings.setUseWarmStartKValues(previous);
    }
  }

  @Test
  void invalidAndUnsupportedInputsDoNotYieldAcousticSpeeds() {
    assertThrows(IllegalArgumentException.class, () -> EquilibriumSoundSpeed.calculate(null));
    assertThrows(IllegalArgumentException.class, () -> new SystemPrEos().calculateEquilibriumSoundSpeed());
    SystemInterface fluid = feed(null);
    for (double step : new double[] {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 0.1}) {
      assertThrows(IllegalArgumentException.class, () -> fluid.calculateEquilibriumSoundSpeed(step));
    }
    fluid.setHydrateCheck(true);
    Result unsupported = fluid.calculateEquilibriumSoundSpeed();
    assertEquals(Status.UNSUPPORTED_STATE, unsupported.getStatus());
    assertFalse(unsupported.isConverged());
    assertTrue(Double.isNaN(unsupported.getSoundSpeed()));
    assertEquals(0, unsupported.getFlashEvaluations());
  }

  @Test
  void failedEntropyAndNonpositiveDerivativesHaveExplicitStatuses() {
    SystemInterface invalidEntropy = new SystemPrEos(350.0, 100.0) {
      private static final long serialVersionUID = 1L;

      @Override
      public double getEntropy(String unit) {
        return Double.NaN;
      }
    };
    invalidEntropy.addComponent("nitrogen", 1.0);
    invalidEntropy.setMixingRule(2);
    new ThermodynamicOperations(invalidEntropy).TPflash();
    Result failed = invalidEntropy.calculateEquilibriumSoundSpeed();
    assertEquals(Status.FLASH_FAILED, failed.getStatus());
    assertTrue(Double.isNaN(failed.getSoundSpeed()));
    assertTrue(failed.getMessage().contains("entropy"));

    // Controlled constant-density model verifies that zero compressibility is never accepted.
    SystemInterface constantDensity = new SystemPrEos(350.0, 100.0) {
      private static final long serialVersionUID = 1L;

      @Override
      public double getVolume(String unit) {
        return 0.001;
      }
    };
    constantDensity.addComponent("nitrogen", 1.0);
    constantDensity.setMixingRule(2);
    new ThermodynamicOperations(constantDensity).TPflash();
    constantDensity.init(3);
    Result derivative = constantDensity.calculateEquilibriumSoundSpeed();
    assertEquals(Status.DERIVATIVE_NOT_CONVERGED, derivative.getStatus());
    assertFalse(derivative.isConverged());
    assertTrue(Double.isNaN(derivative.getSoundSpeed()));
  }

  @Test
  void documentationExample() {
    SystemInterface fluid = new SystemPrEos(298.15, 150.0);
    fluid.addComponent("CO2", 1.0);
    fluid.setMixingRule(2);
    fluid.useVolumeCorrection(false);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    double entropy = fluid.getEntropy("J/kgK");
    fluid.setPressure(40.0);
    new ThermodynamicOperations(fluid).PSflash(entropy, "J/kgK");
    fluid.init(3);
    if (Math.abs(fluid.getEntropy("J/kgK") - entropy) > 1.0e-7) {
      throw new IllegalStateException("Decompression path did not preserve entropy");
    }
    EquilibriumSoundSpeed.Result result = fluid.calculateEquilibriumSoundSpeed();
    if (!result.isConverged()) {
      throw new IllegalStateException(result.getStatus() + ": " + result.getMessage());
    }
    double equilibriumSpeed = result.getSoundSpeed();
    double legacyAverage = fluid.getSoundSpeed();
    assertEquals(57.08, equilibriumSpeed, 0.03);
    assertEquals(408.76, legacyAverage, 0.05);
  }
}
