package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Entropy closure and independent cold TP-root regressions for issue #3751. */
class PSFlashEntropyClosureTest {
  private SystemInterface fluid(String impurity, double temperature) {
    SystemInterface fluid = new SystemPrEos(temperature, 150.0);
    fluid.addComponent("CO2", impurity.isEmpty() ? 1.0 : 0.98);
    if (!impurity.isEmpty()) {
      fluid.addComponent(impurity, 0.02);
    }
    fluid.setMixingRule(2);
    fluid.useVolumeCorrection(false);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    return fluid;
  }

  @ParameterizedTest
  @ValueSource(strings = { "nitrogen", "hydrogen" })
  void mixturesCloseEntropyFromFreshAndContinuationStarts(String impurity) {
    SystemInterface inlet = fluid(impurity, 298.15);
    double target = inlet.getEntropy("J/kgK");
    SystemInterface continuation = inlet.clone();
    for (double pressure : new double[] { 70.0, 50.0, 40.0, 20.0, 10.0 }) {
      SystemInterface reference = tpRoot(inlet, pressure, target);
      for (SystemInterface state : new SystemInterface[] { inlet.clone(), continuation }) {
        state.setPressure(pressure);
        new ThermodynamicOperations(state).PSflash(target, "J/kgK");
        assertState(inlet, state, pressure, target);
        assertEquals(reference.getTemperature(), state.getTemperature(), 1.0e-4);
        assertEquals(reference.getNumberOfPhases(), state.getNumberOfPhases());
        assertEquals(reference.getDensity(), state.getDensity(), 0.01);
      }
    }
  }

  @Test
  void pureCo2ClosesEntropyOnBothSidesOfPhaseEntry() {
    for (double temperature : new double[] { 283.15, 298.15, 308.15 }) {
      SystemInterface inlet = fluid("", temperature);
      double target = inlet.getEntropy("J/kgK");
      SystemInterface continuation = inlet.clone();
      for (double pressure : new double[] { 70.0, 50.0, 46.5, 40.0, 20.0, 10.0 }) {
        for (SystemInterface state : new SystemInterface[] { inlet.clone(), continuation }) {
          state.setPressure(pressure);
          new ThermodynamicOperations(state).PSflash(target, "J/kgK");
          assertState(inlet, state, pressure, target);
          if (state.getNumberOfPhases() == 1) {
            assertEquals(tpRoot(inlet, pressure, target).getTemperature(), state.getTemperature(), 1.0e-4);
          } else {
            assertEquals(state.getPhase(0).getComponent(0).getFugacityCoefficient(),
                state.getPhase(1).getComponent(0).getFugacityCoefficient(), 1.0e-6);
          }
        }
      }
    }
  }

  @Test
  void entropyUnitsAndSystemAmountDoNotChangeTheSolution() {
    for (double amount : new double[] { 1.0e-8, 1.0, 1.0e6 }) {
      SystemInterface inlet = fluid("nitrogen", 298.15);
      inlet.setTotalNumberOfMoles(amount);
      new ThermodynamicOperations(inlet).TPflash();
      inlet.init(3);
      for (String unit : new String[] { "J/K", "J/molK", "J/kgK", "kJ/kgK" }) {
        SystemInterface state = inlet.clone();
        state.setPressure(50.0);
        new ThermodynamicOperations(state).PSflash(inlet.getEntropy(unit), unit);
        state.init(3);
        assertEquals(281.2568234268, state.getTemperature(), 1.0e-4);
        assertEquals(inlet.getEntropy("J/kgK"), state.getEntropy("J/kgK"), 1.0e-3);
      }
    }
  }

  @Test
  void largeTemperatureCorrectionsWorkInBothDirections() {
    SystemInterface reference = fluid("nitrogen", 298.15);
    reference.setPressure(50.0);
    reference.setTemperature(330.0);
    new ThermodynamicOperations(reference).TPflash();
    reference.init(3);
    for (double initialTemperature : new double[] { 230.0, 500.0 }) {
      SystemInterface state = reference.clone();
      state.setTemperature(initialTemperature);
      new ThermodynamicOperations(state).PSflash(reference.getEntropy());
      assertEquals(330.0, state.getTemperature(), 1.0e-5);
    }
  }

  @Test
  void invalidEntropyIsRejectedForPureFluidsAndMixtures() {
    for (String impurity : new String[] { "", "nitrogen" }) {
      for (double entropy : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
        SystemInterface state = fluid(impurity, 298.15);
        assertThrows(IllegalArgumentException.class, () -> new ThermodynamicOperations(state).PSflash(entropy));
      }
    }
  }

  @Test
  void iterationLimitThrowsAndRestoresWarmStartSetting() {
    boolean previousWarm = ThermodynamicModelSettings.isUseWarmStartKValues();
    try {
      for (boolean warm : new boolean[] { false, true }) {
        ThermodynamicModelSettings.setUseWarmStartKValues(warm);
        SystemInterface state = fluid("nitrogen", 298.15);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> new ThermodynamicOperations(state).PSflash(1.0e30));
        assertTrue(failure.getMessage().contains("PSflash"));
        assertTrue(failure.getMessage().contains("residual="));
        assertEquals(warm, ThermodynamicModelSettings.isUseWarmStartKValues());
      }
    } finally {
      ThermodynamicModelSettings.setUseWarmStartKValues(previousWarm);
    }
  }

  private void assertState(SystemInterface inlet, SystemInterface state, double pressure, double target) {
    state.init(3);
    assertEquals(target, state.getEntropy("J/kgK"), 1.0e-3);
    assertEquals(pressure, state.getPressure(), 1.0e-10);
    assertTrue(Double.isFinite(state.getTemperature()) && state.getTemperature() > 0.0);
    assertEquals(inlet.getTotalNumberOfMoles(), state.getTotalNumberOfMoles(), 1.0e-12);
    double betaSum = 0.0;
    for (int phase = 0; phase < state.getNumberOfPhases(); phase++) {
      double beta = state.getBeta(phase);
      assertTrue(Double.isFinite(beta) && beta >= 0.0 && beta <= 1.0);
      betaSum += beta;
      double sum = 0.0;
      for (int component = 0; component < state.getNumberOfComponents(); component++) {
        double fraction = state.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(fraction) && fraction >= 0.0 && fraction <= 1.0);
        sum += fraction;
      }
      assertEquals(1.0, sum, 1.0e-8);
    }
    assertEquals(1.0, betaSum, 1.0e-10);
    for (int component = 0; component < state.getNumberOfComponents(); component++) {
      double amount = 0.0;
      for (int phase = 0; phase < state.getNumberOfPhases(); phase++) {
        amount += state.getPhase(phase).getComponent(component).getNumberOfMolesInPhase();
      }
      assertEquals(inlet.getComponent(component).getNumberOfmoles(), amount, 1.0e-8);
    }
  }

  /** Independent bisection using a new fluid and a cold TP flash at every trial. */
  private SystemInterface tpRoot(SystemInterface inlet, double pressure, double target) {
    double low = 180.0;
    double high = 350.0;
    SystemInterface trial = null;
    boolean previousWarm = neqsim.thermo.ThermodynamicModelSettings.isUseWarmStartKValues();
    try {
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(false);
      for (int iteration = 0; iteration < 70; iteration++) {
        trial = inlet.clone();
        trial.setPressure(pressure);
        trial.setTemperature(0.5 * (low + high));
        new ThermodynamicOperations(trial).TPflash();
        trial.init(3);
        double residual = trial.getEntropy("J/kgK") - target;
        if (Math.abs(residual) < 1.0e-6) {
          return trial;
        }
        if (residual < 0.0) {
          low = trial.getTemperature();
        } else {
          high = trial.getTemperature();
        }
      }
      throw new AssertionError("Independent TP root did not close entropy");
    } finally {
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(previousWarm);
    }
  }
}
