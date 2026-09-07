package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

/** Tests the parameter-neutral standard binary Pitzer volumetric equation. */
class PitzerBinaryVolumetricModelTest extends neqsim.NeqSimTest {
  private static final double TEMPERATURE_K = 298.15;
  private static final double PRESSURE_PA = 10.0e6;
  private static final double CACL2_MOLAR_MASS = 0.11098;

  @Test
  void returnsLimitingVolumeAtZeroMolality() {
    PitzerBinaryVolumetricModel model = calciumChlorideModel();
    PitzerBinaryVolumetricModel.StateParameters parameters = representativeParameters();

    assertEquals(17.6e-6, model.calculateApparentMolarVolume(0.0, 17.6e-6, parameters), 0.0);
    assertEquals(0.0, model.calculateIonicStrength(0.0), 0.0);
  }

  @Test
  void matchesHandCalculatedBinaryInteractionTerm() {
    PitzerBinaryVolumetricModel model = calciumChlorideModel();
    double beta0PressureDerivative = 2.0e-10;
    PitzerBinaryVolumetricModel.StateParameters parameters = new PitzerBinaryVolumetricModel.StateParameters(
        TEMPERATURE_K, PRESSURE_PA, 0.0, beta0PressureDerivative, 0.0, 0.0);

    double molality = 1.5;
    double limitingVolume = 17.6e-6;
    double expectedInteraction = 2.0 * 8.31446261815324 * TEMPERATURE_K * (2.0 * molality * beta0PressureDerivative);

    assertEquals(3.0 * molality, model.calculateIonicStrength(molality), 0.0);
    assertEquals(limitingVolume + expectedInteraction,
        model.calculateApparentMolarVolume(molality, limitingVolume, parameters), 1.0e-19);
  }

  @Test
  void evaluatesAttenuationWithoutDiluteCancellation() {
    assertEquals(1.0, PitzerBinaryVolumetricModel.attenuation(0.0), 0.0);
    assertEquals(1.0 - 2.0e-12 / 3.0, PitzerBinaryVolumetricModel.attenuation(1.0e-12), 1.0e-16);

    double x = 2.0;
    double expected = 2.0 * (1.0 - (1.0 + x) * Math.exp(-x)) / (x * x);
    assertEquals(expected, PitzerBinaryVolumetricModel.attenuation(x), 0.0);
  }

  @Test
  void referenceFormMatchesInfiniteDilutionForm() {
    PitzerBinaryVolumetricModel model = calciumChlorideModel();
    PitzerBinaryVolumetricModel.StateParameters parameters = representativeParameters();
    double limitingVolume = 17.6e-6;
    double referenceMolality = 1.0;
    double referenceVolume = model.calculateApparentMolarVolume(referenceMolality, limitingVolume, parameters);

    for (double molality : new double[] { 0.0, 0.25, 1.0, 3.0, 6.0 }) {
      double direct = model.calculateApparentMolarVolume(molality, limitingVolume, parameters);
      double relative = model.calculateApparentMolarVolumeFromReference(molality, referenceMolality, referenceVolume,
          parameters);
      assertEquals(direct, relative, 2.0e-19);
    }
  }

  @Test
  void densityConversionRoundTripsAndRecoversPureWaterLimit() {
    double molality = 3.0;
    double waterDensity = 997.0474;
    double apparentVolume = 28.0e-6;

    double solutionDensity = PitzerBinaryVolumetricModel.calculateDensity(molality, CACL2_MOLAR_MASS, waterDensity,
        apparentVolume);
    double recoveredVolume = PitzerBinaryVolumetricModel.calculateApparentMolarVolumeFromDensity(molality,
        CACL2_MOLAR_MASS, waterDensity, solutionDensity);

    assertEquals(apparentVolume, recoveredVolume, 2.0e-19);
    assertEquals(waterDensity,
        PitzerBinaryVolumetricModel.calculateDensity(0.0, CACL2_MOLAR_MASS, waterDensity, apparentVolume), 0.0);
  }

  @Test
  void rejectsInvalidFormulaStateAndConversions() {
    assertThrows(IllegalArgumentException.class, () -> new PitzerBinaryVolumetricModel(1, 2, 1, -1));
    assertThrows(IllegalArgumentException.class, () -> new PitzerBinaryVolumetricModel(0, 1, 1, -1));
    assertThrows(IllegalArgumentException.class,
        () -> new PitzerBinaryVolumetricModel.StateParameters(0.0, PRESSURE_PA, 0.0, 0.0, 0.0, 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> new PitzerBinaryVolumetricModel.StateParameters(TEMPERATURE_K, PRESSURE_PA, 0.0, Double.NaN, 0.0, 0.0));

    PitzerBinaryVolumetricModel model = calciumChlorideModel();
    PitzerBinaryVolumetricModel.StateParameters parameters = representativeParameters();
    assertThrows(IllegalArgumentException.class, () -> model.calculateApparentMolarVolume(-0.1, 17.6e-6, parameters));
    assertThrows(IllegalArgumentException.class,
        () -> model.calculateApparentMolarVolumeFromReference(1.0, 0.0, 25.0e-6, parameters));
    assertThrows(IllegalArgumentException.class, () -> model.calculateApparentMolarVolume(1.0, 17.6e-6, null));
    assertThrows(IllegalArgumentException.class, () -> model.calculateApparentMolarVolume(1.0, Double.MAX_VALUE,
        new PitzerBinaryVolumetricModel.StateParameters(TEMPERATURE_K, PRESSURE_PA, Double.MAX_VALUE, 0.0, 0.0, 0.0)));
    assertThrows(IllegalArgumentException.class, () -> PitzerBinaryVolumetricModel
        .calculateApparentMolarVolumeFromDensity(0.0, CACL2_MOLAR_MASS, 997.0, 1100.0));
    assertThrows(IllegalArgumentException.class,
        () -> PitzerBinaryVolumetricModel.calculateDensity(10.0, CACL2_MOLAR_MASS, 997.0, -2.0e-4));
    assertThrows(IllegalArgumentException.class, () -> PitzerBinaryVolumetricModel.attenuation(Double.NaN));
  }

  private static PitzerBinaryVolumetricModel calciumChlorideModel() {
    return new PitzerBinaryVolumetricModel(1, 2, 2, -1);
  }

  private static PitzerBinaryVolumetricModel.StateParameters representativeParameters() {
    return new PitzerBinaryVolumetricModel.StateParameters(TEMPERATURE_K, PRESSURE_PA, 1.0e-6, 2.0e-10, -1.0e-10,
        5.0e-11);
  }
}
