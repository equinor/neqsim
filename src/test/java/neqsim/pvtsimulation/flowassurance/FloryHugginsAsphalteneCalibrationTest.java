package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the data calibration, temperature dependence and solver robustness of {@link FloryHugginsAsphalteneModel}.
 *
 * @author ESOL
 * @version 1.0
 */
class FloryHugginsAsphalteneCalibrationTest {
  /** Live oil with a bubble point of about 135 bara at 373.15 K. */
  private SystemInterface fluid;

  /**
   * Builds the live oil used by all tests.
   */
  @BeforeEach
  void setUp() {
    fluid = new SystemSrkEos(373.15, 500.0);
    fluid.addComponent("methane", 0.40);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-heptane", 0.32);
    fluid.addComponent("nC10", 0.20);
    fluid.setMixingRule("classic");
  }

  /**
   * Creates a model with 3 wt% asphaltene.
   *
   * @return configured model
   */
  private FloryHugginsAsphalteneModel newModel() {
    FloryHugginsAsphalteneModel model = new FloryHugginsAsphalteneModel(fluid, 373.15);
    model.setAsphalteneWeightFraction(0.03);
    return model;
  }

  /**
   * The solubility limit is the smallest root of the Flory-Huggins equation and satisfies it.
   */
  @Test
  void dissolvedFractionSatisfiesSolubilityEquation() {
    FloryHugginsAsphalteneModel model = newModel();
    double liquidMolarVolume = 150.0;
    double phi = model.calculateMaxDissolvedFraction(14.0, liquidMolarVolume, 373.15);
    double chi = model.calculateChiParameter(14.0, 373.15);
    double ratio = model.getAsphaltMolarVolume() / liquidMolarVolume;
    double residual = Math.log(phi) + (1.0 - ratio) * (1.0 - phi) + chi * (1.0 - phi) * (1.0 - phi);
    assertTrue(phi > 0.0 && phi < 0.01, "expected a dilute solubility limit but got " + phi);
    assertEquals(0.0, residual, 1.0e-8);
  }

  /**
   * Without a solubility parameter mismatch and with equal molar volumes the asphaltene is completely miscible.
   */
  @Test
  void miscibleSystemReturnsUnity() {
    FloryHugginsAsphalteneModel model = newModel();
    double phi = model.calculateMaxDissolvedFraction(model.getAsphalteneSolubilityParameter(),
        model.getAsphaltMolarVolume(), 373.15);
    assertEquals(1.0, phi, 1.0e-12);
  }

  /**
   * Repeated SARA configuration must not accumulate the solubility parameter correction.
   */
  @Test
  void saraConfigurationIsIdempotent() {
    FloryHugginsAsphalteneModel model = newModel();
    model.configureFromSARA(0.50, 0.25, 0.20, 0.03);
    double first = model.getAsphalteneSolubilityParameter();
    model.configureFromSARA(0.50, 0.25, 0.20, 0.03);
    assertEquals(first, model.getAsphalteneSolubilityParameter(), 1.0e-12);
    assertEquals(21.0 * 0.97, first, 1.0e-12);
  }

  /**
   * A one-point calibration reproduces the measured upper onset pressure and gives a lower onset below it.
   */
  @Test
  void calibrationReproducesMeasuredOnsetPressure() {
    FloryHugginsAsphalteneModel model = newModel();
    assertFalse(model.isCalibratedToOnsetData());
    double delta = model.calibrateToOnsetPressure(373.15, 300.0);
    assertTrue(model.isCalibratedToOnsetData());
    assertTrue(delta > 18.0 && delta < 22.0, "calibrated solubility parameter out of range: " + delta);
    assertEquals(300.0, model.calculateOnsetPressure(373.15, 500.0, 1.0), 0.05);

    double lower = model.calculateLowerOnsetPressure(373.15, 500.0, 1.0);
    assertTrue(lower > 1.0 && lower < 135.0, "lower onset should be below the bubble point but was " + lower);
    assertTrue(model.calculatePrecipitatedFraction(200.0, 373.15) > 0.0);
    assertEquals(0.0, model.calculatePrecipitatedFraction(450.0, 373.15), 1.0e-12);
  }

  /**
   * Onset pressures generated with known parameters are recovered by the multi-temperature regression.
   */
  @Test
  void fitRecoversTemperatureDependentSolubilityParameter() {
    FloryHugginsAsphalteneModel truth = newModel();
    truth.setAsphalteneSolubilityParameter(19.6);
    truth.setSolubilityParameterTemperatureDependence(353.15, -0.012);
    double[] temperatures = {333.15, 353.15, 373.15};
    double[] onsets = new double[temperatures.length];
    for (int i = 0; i < temperatures.length; i++) {
      onsets[i] = truth.calculateOnsetPressure(temperatures[i], 500.0, 1.0);
      assertFalse(Double.isNaN(onsets[i]), "synthetic onset missing at " + temperatures[i]);
    }

    FloryHugginsAsphalteneModel fitted = newModel();
    FloryHugginsAsphalteneModel.OnsetFitResult result = fitted.fitToOnsetData(temperatures, onsets);
    assertEquals(353.15, result.getReferenceTemperature(), 1.0e-9);
    assertEquals(19.6, result.getSolubilityParameter(), 0.01);
    assertEquals(-0.012, result.getTemperatureCoefficient(), 5.0e-4);
    assertTrue(result.getMaximumAbsoluteDeviation() < 2.0,
        "maximum deviation " + result.getMaximumAbsoluteDeviation() + " bar");
    assertEquals(19.6 - 0.012 * 20.0, fitted.getAsphalteneSolubilityParameter(373.15), 0.02);
  }

  /**
   * Invalid calibration input is rejected.
   */
  @Test
  void invalidCalibrationInputIsRejected() {
    final FloryHugginsAsphalteneModel model = newModel();
    assertThrows(IllegalArgumentException.class, () -> model.fitToOnsetData(new double[] {373.15}, new double[0]));
    assertThrows(IllegalArgumentException.class, () -> model.setOnsetDetectionThreshold(-1.0));
    model.setAsphalteneWeightFraction(0.0005);
    assertThrows(IllegalStateException.class, () -> model.calibrateToOnsetPressure(373.15, 300.0));
  }
}
