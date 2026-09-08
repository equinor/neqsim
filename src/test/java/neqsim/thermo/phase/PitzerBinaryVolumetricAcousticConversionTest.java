package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** Tests acoustic-to-isothermal compressibility conversion for volumetric Pitzer evidence. */
class PitzerBinaryVolumetricAcousticConversionTest extends neqsim.NeqSimTest {
  private static final double TEMPERATURE_K = 298.15;
  private static final double DENSITY_KG_PER_M3 = 997.0;
  private static final double SOUND_SPEED_M_PER_S = 1497.0;
  private static final double THERMAL_EXPANSION_PER_K = 2.57e-4;
  private static final double SPECIFIC_HEAT_CAPACITY_J_PER_KG_K = 4181.0;

  @Test
  void convertsAcousticAndCaloricPropertiesInSiUnits() {
    double isentropic = PitzerBinaryVolumetricAcousticConversion.calculateIsentropicCompressibility(DENSITY_KG_PER_M3,
        SOUND_SPEED_M_PER_S);
    double isothermal = PitzerBinaryVolumetricAcousticConversion.calculateIsothermalCompressibility(TEMPERATURE_K,
        DENSITY_KG_PER_M3, SOUND_SPEED_M_PER_S, THERMAL_EXPANSION_PER_K, SPECIFIC_HEAT_CAPACITY_J_PER_KG_K);

    assertEquals(4.475702806553850e-10, isentropic, 1.0e-24);
    assertEquals(4.522944530170047e-10, isothermal, 1.0e-24);
    assertTrue(isothermal > isentropic);
  }

  @Test
  void zeroThermalExpansionMakesIsothermalAndIsentropicValuesEqual() {
    double isentropic = PitzerBinaryVolumetricAcousticConversion.calculateIsentropicCompressibility(DENSITY_KG_PER_M3,
        SOUND_SPEED_M_PER_S);
    double isothermal = PitzerBinaryVolumetricAcousticConversion.calculateIsothermalCompressibility(TEMPERATURE_K,
        DENSITY_KG_PER_M3, SOUND_SPEED_M_PER_S, 0.0, SPECIFIC_HEAT_CAPACITY_J_PER_KG_K);

    assertEquals(isentropic, isothermal, 0.0);
  }

  @Test
  void propagatesIndependentInputUncertaintiesThroughAnalyticSensitivities() {
    double[] standardUncertainties = { 0.02, 0.10, 0.20, 1.0e-6, 2.0 };
    double[][] covariance = diagonalCovariance(standardUncertainties);

    PitzerBinaryVolumetricAcousticConversion.ConversionResult result = PitzerBinaryVolumetricAcousticConversion
        .convertWithUncertainty(TEMPERATURE_K, DENSITY_KG_PER_M3, SOUND_SPEED_M_PER_S, THERMAL_EXPANSION_PER_K,
            SPECIFIC_HEAT_CAPACITY_J_PER_KG_K, covariance);

    double isentropic = 1.0 / (DENSITY_KG_PER_M3 * SOUND_SPEED_M_PER_S * SOUND_SPEED_M_PER_S);
    double thermalCorrection = TEMPERATURE_K * THERMAL_EXPANSION_PER_K * THERMAL_EXPANSION_PER_K
        / (DENSITY_KG_PER_M3 * SPECIFIC_HEAT_CAPACITY_J_PER_KG_K);
    double isothermal = isentropic + thermalCorrection;
    double[] expectedJacobian = {
        THERMAL_EXPANSION_PER_K * THERMAL_EXPANSION_PER_K / (DENSITY_KG_PER_M3 * SPECIFIC_HEAT_CAPACITY_J_PER_KG_K),
        -isothermal / DENSITY_KG_PER_M3, -2.0 * isentropic / SOUND_SPEED_M_PER_S,
        2.0 * TEMPERATURE_K * THERMAL_EXPANSION_PER_K / (DENSITY_KG_PER_M3 * SPECIFIC_HEAT_CAPACITY_J_PER_KG_K),
        -thermalCorrection / SPECIFIC_HEAT_CAPACITY_J_PER_KG_K };
    double expectedVariance = 0.0;
    for (int index = 0; index < PitzerBinaryVolumetricAcousticConversion.INPUT_COUNT; index++) {
      expectedVariance += expectedJacobian[index] * expectedJacobian[index] * covariance[index][index];
      assertEquals(expectedJacobian[index], result.getSensitivityJacobian()[index],
          Math.abs(expectedJacobian[index]) * 1.0e-14 + 1.0e-30);
    }

    assertEquals(isentropic, result.getIsentropicCompressibilityPaInverse(), 1.0e-24);
    assertEquals(thermalCorrection, result.getThermalCorrectionPaInverse(), 1.0e-24);
    assertEquals(isothermal, result.getIsothermalCompressibilityPaInverse(), 1.0e-24);
    assertEquals(Math.sqrt(expectedVariance), result.getStandardUncertaintyPaInverse(), 1.0e-24);
  }

  @Test
  void retainsCrossCovarianceTerms() {
    double[][] covariance = diagonalCovariance(new double[] { 0.0, 0.10, 0.20, 0.0, 0.0 });
    covariance[PitzerBinaryVolumetricAcousticConversion.DENSITY_INDEX][PitzerBinaryVolumetricAcousticConversion.SOUND_SPEED_INDEX] = 0.01;
    covariance[PitzerBinaryVolumetricAcousticConversion.SOUND_SPEED_INDEX][PitzerBinaryVolumetricAcousticConversion.DENSITY_INDEX] = 0.01;

    PitzerBinaryVolumetricAcousticConversion.ConversionResult result = PitzerBinaryVolumetricAcousticConversion
        .convertWithUncertainty(TEMPERATURE_K, DENSITY_KG_PER_M3, SOUND_SPEED_M_PER_S, THERMAL_EXPANSION_PER_K,
            SPECIFIC_HEAT_CAPACITY_J_PER_KG_K, covariance);
    double[] jacobian = result.getSensitivityJacobian();
    double expectedVariance = jacobian[PitzerBinaryVolumetricAcousticConversion.DENSITY_INDEX]
        * jacobian[PitzerBinaryVolumetricAcousticConversion.DENSITY_INDEX] * 0.01
        + jacobian[PitzerBinaryVolumetricAcousticConversion.SOUND_SPEED_INDEX]
            * jacobian[PitzerBinaryVolumetricAcousticConversion.SOUND_SPEED_INDEX] * 0.04
        + 2.0 * jacobian[PitzerBinaryVolumetricAcousticConversion.DENSITY_INDEX]
            * jacobian[PitzerBinaryVolumetricAcousticConversion.SOUND_SPEED_INDEX] * 0.01;

    assertEquals(Math.sqrt(expectedVariance), result.getStandardUncertaintyPaInverse(), 1.0e-24);
  }

  @Test
  void rejectsNonPhysicalInputsAndMalformedCovariance() {
    assertThrows(IllegalArgumentException.class,
        () -> PitzerBinaryVolumetricAcousticConversion.calculateIsentropicCompressibility(0.0, SOUND_SPEED_M_PER_S));
    assertThrows(IllegalArgumentException.class, () -> PitzerBinaryVolumetricAcousticConversion
        .calculateIsentropicCompressibility(Double.MAX_VALUE, Double.MAX_VALUE));
    assertThrows(IllegalArgumentException.class,
        () -> PitzerBinaryVolumetricAcousticConversion.calculateIsothermalCompressibility(TEMPERATURE_K,
            DENSITY_KG_PER_M3, SOUND_SPEED_M_PER_S, Double.NaN, SPECIFIC_HEAT_CAPACITY_J_PER_KG_K));
    assertThrows(IllegalArgumentException.class,
        () -> convert(new double[PitzerBinaryVolumetricAcousticConversion.INPUT_COUNT
            - 1][PitzerBinaryVolumetricAcousticConversion.INPUT_COUNT - 1]));

    double[][] asymmetric = diagonalCovariance(new double[] { 1.0, 1.0, 1.0, 1.0, 1.0 });
    asymmetric[0][1] = 0.1;
    assertThrows(IllegalArgumentException.class, () -> convert(asymmetric));

    double[][] indefinite = diagonalCovariance(new double[] { 1.0, 1.0, 1.0, 1.0, 1.0 });
    indefinite[0][1] = 2.0;
    indefinite[1][0] = 2.0;
    assertThrows(IllegalArgumentException.class, () -> convert(indefinite));

    double[][] zeroVarianceCorrelation = diagonalCovariance(new double[] { 0.0, 1.0, 1.0, 1.0, 1.0 });
    zeroVarianceCorrelation[0][1] = 0.1;
    zeroVarianceCorrelation[1][0] = 0.1;
    assertThrows(IllegalArgumentException.class, () -> convert(zeroVarianceCorrelation));
  }

  @Test
  void returnsDefensiveJacobianCopies() {
    PitzerBinaryVolumetricAcousticConversion.ConversionResult result = convert(
        new double[PitzerBinaryVolumetricAcousticConversion.INPUT_COUNT][PitzerBinaryVolumetricAcousticConversion.INPUT_COUNT]);

    double[] first = result.getSensitivityJacobian();
    double original = first[0];
    first[0] = Double.NaN;

    assertEquals(original, result.getSensitivityJacobian()[0], 0.0);
    assertNotSame(first, result.getSensitivityJacobian());
  }

  private static PitzerBinaryVolumetricAcousticConversion.ConversionResult convert(double[][] covariance) {
    return PitzerBinaryVolumetricAcousticConversion.convertWithUncertainty(TEMPERATURE_K, DENSITY_KG_PER_M3,
        SOUND_SPEED_M_PER_S, THERMAL_EXPANSION_PER_K, SPECIFIC_HEAT_CAPACITY_J_PER_KG_K, covariance);
  }

  private static double[][] diagonalCovariance(double[] standardUncertainties) {
    double[][] covariance = new double[standardUncertainties.length][standardUncertainties.length];
    for (int index = 0; index < standardUncertainties.length; index++) {
      covariance[index][index] = standardUncertainties[index] * standardUncertainties[index];
    }
    return covariance;
  }
}
