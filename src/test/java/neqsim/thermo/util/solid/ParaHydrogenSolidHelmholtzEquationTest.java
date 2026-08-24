package neqsim.thermo.util.solid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** Tests the thesis solid para-hydrogen Helmholtz equation. */
class ParaHydrogenSolidHelmholtzEquationTest {

  /** Verify the normalized third-order Debye function from thesis Equation 2.30. */
  @Test
  void testNormalizedThirdOrderDebyeFunction() {
    assertEquals(0.2248051880259382, ParaHydrogenSolidHelmholtzEquation.debyeFunction3(1.0), 1.0e-12);
    assertEquals(1.0 / 3.0, ParaHydrogenSolidHelmholtzEquation.debyeFunction3(1.0e-8), 1.0e-8);
  }

  /** Verify the safeguarded logarithmic-volume solver inverts the Helmholtz pressure. */
  @Test
  void testPressureInversion() {
    ParaHydrogenSolidHelmholtzEquation equation = new ParaHydrogenSolidHelmholtzEquation();
    double[][] states = { { 4.2, 0.07042 }, { 20.0, 1000.0 }, { 80.0, 10000.0 } };

    for (double[] state : states) {
      SolidHelmholtzState result = equation.evaluate(state[0], state[1]);
      double calculatedPressure = equation.calculatePressure(state[0], result.getMolarVolume());
      assertEquals(state[1] * 1.0e5, calculatedPressure, Math.max(1.0e-3, state[1] * 1.0e5 * 1.0e-9));
      assertTrue(result.getMolarVolume() > 5.0e-6,
          "Unexpected compressed root at T=" + state[0] + " K: " + result.getMolarVolume());
      assertTrue(result.getMolarVolume() < 30.0e-6,
          "Unexpected expanded root at T=" + state[0] + " K: " + result.getMolarVolume());
      assertTrue(Double.isFinite(result.getHeatCapacityCv()));
      assertTrue(Double.isFinite(result.getHeatCapacityCp()));
    }
  }

  private static final double VINET_REFERENCE_VOLUME = 23.14e-6;
  private static final double VINET_REFERENCE_BULK_MODULUS = 180.5e6;

  /** Verify the canonical Vinet pressure and bulk-modulus reference invariants. */
  @Test
  void testCanonicalVinetReferenceState() {
    ParaHydrogenSolidHelmholtzEquation equation = new ParaHydrogenSolidHelmholtzEquation();
    double volumeStep = VINET_REFERENCE_VOLUME * 1.0e-6;

    double referencePressure = equation.calculateVinetPressure(VINET_REFERENCE_VOLUME);
    double pressureDerivative = (equation.calculateVinetPressure(VINET_REFERENCE_VOLUME + volumeStep)
        - equation.calculateVinetPressure(VINET_REFERENCE_VOLUME - volumeStep)) / (2.0 * volumeStep);
    double calculatedBulkModulus = -VINET_REFERENCE_VOLUME * pressureDerivative;

    assertEquals(0.0, referencePressure, 1.0e-7);
    assertEquals(VINET_REFERENCE_BULK_MODULUS, calculatedBulkModulus, VINET_REFERENCE_BULK_MODULUS * 1.0e-7);
  }

  /** Verify pressure, entropy, stability, and heat-capacity identities by finite differences. */
  @Test
  void testIndependentHelmholtzDerivativeIdentities() {
    ParaHydrogenSolidHelmholtzEquation equation = new ParaHydrogenSolidHelmholtzEquation();
    double temperature = 20.0;
    double pressure = 1000.0;
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
        Math.abs(pressureFromEnergy) * 1.0e-7);
    assertEquals(state.getEntropy(), entropyFromEnergy, Math.max(1.0e-7, Math.abs(entropyFromEnergy) * 1.0e-7));
    assertTrue(-pressureVolumeDerivative > 0.0, "The Helmholtz volume curvature must be positive.");
    assertEquals(expectedHeatCapacityDifference, state.getHeatCapacityCp() - state.getHeatCapacityCv(),
        Math.max(1.0e-8, Math.abs(expectedHeatCapacityDifference) * 1.0e-6));
  }
}