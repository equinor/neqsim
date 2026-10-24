package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Checks explicit swelling temperatures and reconfiguration of their unit basis.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class SwellingTestTemperatureTest {
  /** Checks that the one-argument Kelvin setter overrides an earlier Celsius unit. */
  @Test
  void kelvinSetterResetsPreviouslyConfiguredCelsiusUnit() {
    SwellingTest reference = experiment();
    reference.setTemperature(353.15, "K");
    reference.runCalc();
    SwellingTest reconfigured = experiment();
    reconfigured.setTemperature(80.0, "C");
    reconfigured.setTemperature(353.15);
    reconfigured.runCalc();
    assertEquals(353.15, reconfigured.getThermoSystem().getTemperature(), 1.0e-12);
    assertArrayEquals(reference.getPressures(), reconfigured.getPressures(), 1.0e-8);
    assertArrayEquals(reference.getRelativeOilVolume(), reconfigured.getRelativeOilVolume(), 1.0e-8);
  }

  /** Checks explicit NaN differs from an omitted temperature and rejects invalid absolute temperatures. */
  @Test
  void invalidExplicitTemperaturesAreRejectedOnRun() {
    for (double temperature : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0,
        -1.0}) {
      SwellingTest simulation = experiment();
      simulation.setTemperature(temperature);
      assertThrows(IllegalArgumentException.class, simulation::runCalc);
    }
    SwellingTest celsius = experiment();
    celsius.setTemperature(-273.15, "C");
    assertThrows(IllegalArgumentException.class, celsius::runCalc);
    SwellingTest notFiniteCelsius = experiment();
    notFiniteCelsius.setTemperature(Double.NaN, "C");
    assertThrows(IllegalArgumentException.class, notFiniteCelsius::runCalc);
    SwellingTest invalidUnit = experiment();
    invalidUnit.setTemperature(353.15, "invalid");
    assertThrows(IllegalArgumentException.class, invalidUnit::runCalc);
  }

  /**
   * Build a synthetic unshifted binary SRK swelling experiment.
   *
   * @return experiment with a zero, ten and twenty mole-percent cumulative schedule
   */
  private SwellingTest experiment() {
    SystemInterface oil = new SystemSrkEos(298.15, 50.0);
    oil.addComponent("methane", 0.2);
    oil.addComponent("nC10", 0.8);
    oil.setMixingRule(1);
    oil.useVolumeCorrection(false);
    SystemInterface gas = new SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule(1);
    SwellingTest simulation = new SwellingTest(oil);
    simulation.setInjectionGas(gas);
    simulation.setCummulativeMolePercentGasInjected(new double[] {0.0, 10.0, 20.0});
    return simulation;
  }
}
