import neqsim.thermo.phase.PitzerBinaryVolumetricAcousticConversion;
import neqsim.thermo.phase.PitzerBinaryVolumetricAcousticConversion.ConversionResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Converts acoustic and caloric measurements to isothermal compressibility with uncertainty.
 */
public final class AcousticCompressibilityConversionExample {
  private static final Logger logger =
      LogManager.getLogger(AcousticCompressibilityConversionExample.class);

  private AcousticCompressibilityConversionExample() {}

  /**
   * Runs a complete SI-unit conversion with independent input uncertainties.
   *
   * @param args unused command-line arguments
   */
  public static void main(String[] args) {
    double temperatureK = 298.15;
    double densityKgPerM3 = 997.0;
    double soundSpeedMPerS = 1497.0;
    double thermalExpansionPerK = 2.57e-4;
    double specificHeatCapacityJPerKgK = 4181.0;

    double[][] inputCovariance =
        new double[PitzerBinaryVolumetricAcousticConversion.INPUT_COUNT]
            [PitzerBinaryVolumetricAcousticConversion.INPUT_COUNT];
    inputCovariance[PitzerBinaryVolumetricAcousticConversion.TEMPERATURE_INDEX]
        [PitzerBinaryVolumetricAcousticConversion.TEMPERATURE_INDEX] = square(0.02);
    inputCovariance[PitzerBinaryVolumetricAcousticConversion.DENSITY_INDEX]
        [PitzerBinaryVolumetricAcousticConversion.DENSITY_INDEX] = square(0.10);
    inputCovariance[PitzerBinaryVolumetricAcousticConversion.SOUND_SPEED_INDEX]
        [PitzerBinaryVolumetricAcousticConversion.SOUND_SPEED_INDEX] = square(0.20);
    inputCovariance[PitzerBinaryVolumetricAcousticConversion.THERMAL_EXPANSION_INDEX]
        [PitzerBinaryVolumetricAcousticConversion.THERMAL_EXPANSION_INDEX] = square(1.0e-6);
    inputCovariance[PitzerBinaryVolumetricAcousticConversion.HEAT_CAPACITY_INDEX]
        [PitzerBinaryVolumetricAcousticConversion.HEAT_CAPACITY_INDEX] = square(2.0);

    ConversionResult result =
        PitzerBinaryVolumetricAcousticConversion.convertWithUncertainty(
            temperatureK,
            densityKgPerM3,
            soundSpeedMPerS,
            thermalExpansionPerK,
            specificHeatCapacityJPerKgK,
            inputCovariance);

    requirePhysicalResult(result);
    logger.info(
        "At {} K: kappaS={} 1/Pa, correction={} 1/Pa, kappaT={} +/- {} 1/Pa",
        temperatureK,
        result.getIsentropicCompressibilityPaInverse(),
        result.getThermalCorrectionPaInverse(),
        result.getIsothermalCompressibilityPaInverse(),
        result.getStandardUncertaintyPaInverse());
  }

  private static void requirePhysicalResult(ConversionResult result) {
    double isentropic = result.getIsentropicCompressibilityPaInverse();
    double correction = result.getThermalCorrectionPaInverse();
    double isothermal = result.getIsothermalCompressibilityPaInverse();
    double uncertainty = result.getStandardUncertaintyPaInverse();

    if (!Double.isFinite(isentropic)
        || !Double.isFinite(correction)
        || !Double.isFinite(isothermal)
        || !Double.isFinite(uncertainty)
        || isentropic <= 0.0
        || correction < 0.0
        || isothermal < isentropic
        || uncertainty < 0.0
        || result.getSensitivityJacobian().length
            != PitzerBinaryVolumetricAcousticConversion.INPUT_COUNT) {
      throw new IllegalStateException("Acoustic compressibility conversion returned an invalid result");
    }
  }

  private static double square(double value) {
    return value * value;
  }
}
