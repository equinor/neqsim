package neqsim.thermo.util.solid;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemArgonSolidHelmholtzEos;

/** Tests the published solid-argon Helmholtz equation and reference calculations. */
class ArgonSolidHelmholtzEquationTest {

  /** Reproduce every property reported in Table 8 of Maltby et al. (2024). */
  @Test
  void testPublicationTable8SampleCalculation() {
    SystemArgonSolidHelmholtzEos system = new SystemArgonSolidHelmholtzEos(70.0, 10.0);
    system.init(3);
    SolidHelmholtzState state = system.getArgonSolidEquation().evaluate(70.0, 10.0);
    ArgonSolidHelmholtzEquation equation = system.getArgonSolidEquation();
    double molarVolume = state.getMolarVolume();
    double thermalExpansivity = equation.calculateThermalExpansivity(70.0, molarVolume);
    double gruneisen = equation.calculateThermalGruneisenCoefficient(70.0, molarVolume);
    double isothermalCompressibility = equation.calculateIsothermalCompressibility(70.0, molarVolume);
    double isentropicCompressibility = equation.calculateIsentropicCompressibility(70.0, molarVolume);

    // The parameter table is rounded; tolerances include its resulting low-pressure cancellation error.
    assertAll(() -> assertEquals(23.97e-6, molarVolume, 0.02e-6),
        () -> assertEquals(-9009.0, state.getHelmholtzEnergy(), 0.5),
        () -> assertEquals(30.35, state.getHeatCapacityCp(), 0.07),
        () -> assertEquals(22.93, state.getHeatCapacityCv(), 0.02),
        () -> assertEquals(-8985.0, state.getGibbsEnergy(), 0.5), () -> assertEquals(32.97, state.getEntropy(), 0.005),
        () -> assertEquals(1.684e-3, thermalExpansivity, 0.011e-3), () -> assertEquals(2.745, gruneisen, 0.001),
        () -> assertEquals(0.6412e-9, isothermalCompressibility, 0.004e-9),
        () -> assertEquals(0.4844e-9, isentropicCompressibility, 0.0022e-9));
  }

  /** Verify the logarithmic-volume solver from low pressure through the 16 GPa limit. */
  @Test
  void testPressureInversionAcrossPublishedRange() {
    ArgonSolidHelmholtzEquation equation = new ArgonSolidHelmholtzEquation();
    double[][] states = { { 20.0, 0.01 }, { 70.0, 10.0 }, { 300.0, 160000.0 } };

    for (double[] state : states) {
      SolidHelmholtzState result;
      try {
        result = equation.evaluate(state[0], state[1]);
      } catch (RuntimeException exception) {
        throw new AssertionError("Failed pressure inversion at T=" + state[0] + " K and p=" + state[1] + " bara.",
            exception);
      }
      double calculatedPressure = equation.calculatePressure(state[0], result.getMolarVolume());
      assertEquals(state[1] * 1.0e5, calculatedPressure, Math.max(1.0e-3, state[1] * 1.0e5 * 1.0e-9));
      assertTrue(result.getMolarVolume() > 5.0e-6);
      assertTrue(result.getMolarVolume() < 30.0e-6);
      assertTrue(result.getHeatCapacityCp() > result.getHeatCapacityCv());
      assertTrue(result.getHeatCapacityCv() > 0.0);
    }
  }

  /** Independently verify pressure, entropy, and heat-capacity derivative identities. */
  @Test
  void testIndependentHelmholtzDerivativeIdentities() {
    ArgonSolidHelmholtzEquation equation = new ArgonSolidHelmholtzEquation();
    double temperature = 70.0;
    double pressure = 10.0;
    SolidHelmholtzState state = equation.evaluate(temperature, pressure);
    double molarVolume = state.getMolarVolume();
    double volumeStep = molarVolume * 1.0e-5;
    double temperatureStep = temperature * 1.0e-5;

    double pressureFromEnergy = -(equation.calculateHelmholtzEnergy(temperature, molarVolume + volumeStep)
        - equation.calculateHelmholtzEnergy(temperature, molarVolume - volumeStep)) / (2.0 * volumeStep);
    double entropyFromEnergy = -(equation.calculateHelmholtzEnergy(temperature + temperatureStep, molarVolume)
        - equation.calculateHelmholtzEnergy(temperature - temperatureStep, molarVolume)) / (2.0 * temperatureStep);
    double pressureVolumeDerivative = (equation.calculatePressure(temperature, molarVolume + volumeStep)
        - equation.calculatePressure(temperature, molarVolume - volumeStep)) / (2.0 * volumeStep);
    double pressureTemperatureDerivative = (equation.calculatePressure(temperature + temperatureStep, molarVolume)
        - equation.calculatePressure(temperature - temperatureStep, molarVolume)) / (2.0 * temperatureStep);
    double expectedHeatCapacityDifference = temperature * pressureTemperatureDerivative * pressureTemperatureDerivative
        / -pressureVolumeDerivative;

    assertEquals(equation.calculatePressure(temperature, molarVolume), pressureFromEnergy,
        Math.abs(pressureFromEnergy) * 5.0e-7);
    assertEquals(state.getEntropy(), entropyFromEnergy, Math.max(1.0e-7, Math.abs(entropyFromEnergy) * 1.0e-7));
    assertTrue(-pressureVolumeDerivative > 0.0);
    assertEquals(expectedHeatCapacityDifference, state.getHeatCapacityCp() - state.getHeatCapacityCv(),
        Math.max(1.0e-8, Math.abs(expectedHeatCapacityDifference) * 1.0e-6));
  }
}
