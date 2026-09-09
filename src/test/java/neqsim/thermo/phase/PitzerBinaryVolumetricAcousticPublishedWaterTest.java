package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * Published-water validation for the acoustic-to-isothermal compressibility conversion.
 *
 * <p>
 * Source: El Hawary and Meier, International Journal of Thermophysics 44, 180 (2023), doi:10.1007/s10765-023-03276-1,
 * CC BY 4.0. Sound speed is evaluated from their Table 3 correlation. Density and heat-capacity values, including the
 * symmetric finite-difference stencils, are from their Table 5.
 * </p>
 */
class PitzerBinaryVolumetricAcousticPublishedWaterTest extends neqsim.NeqSimTest {
  private static final double TEMPERATURE_K = 303.15;
  private static final double PRESSURE_MPA = 50.0;
  private static final double DENSITY_KG_PER_M3 = 1016.787;
  private static final double SPECIFIC_HEAT_CAPACITY_J_PER_KG_K = 4064.08;
  private static final double SPECIFIC_ISOCHORIC_HEAT_CAPACITY_J_PER_KG_K = 3976.52;

  // Table 5 symmetric temperature stencil at 50 MPa.
  private static final double DENSITY_AT_293_15_K_KG_PER_M3 = 1019.924;
  private static final double DENSITY_AT_313_15_K_KG_PER_M3 = 1013.017;

  // Table 5 symmetric pressure stencil at 303.15 K.
  private static final double DENSITY_AT_45_MPA_KG_PER_M3 = 1014.763;
  private static final double DENSITY_AT_55_MPA_KG_PER_M3 = 1018.791;

  private static final double MAXIMUM_RELATIVE_DIFFERENCE = 1.0e-3;

  @Test
  void agreesWithPublishedHeatCapacityAndDensityReconstructions() {
    double soundSpeedMPerS = publishedSoundSpeedMPerS(TEMPERATURE_K, PRESSURE_MPA);
    double thermalExpansionPerK = -(DENSITY_AT_313_15_K_KG_PER_M3 - DENSITY_AT_293_15_K_KG_PER_M3)
        / (20.0 * DENSITY_KG_PER_M3);

    double isentropicCompressibility = PitzerBinaryVolumetricAcousticConversion
        .calculateIsentropicCompressibility(DENSITY_KG_PER_M3, soundSpeedMPerS);
    double convertedIsothermalCompressibility = PitzerBinaryVolumetricAcousticConversion
        .calculateIsothermalCompressibility(TEMPERATURE_K, DENSITY_KG_PER_M3, soundSpeedMPerS, thermalExpansionPerK,
            SPECIFIC_HEAT_CAPACITY_J_PER_KG_K);

    double heatCapacityIdentity = isentropicCompressibility * SPECIFIC_HEAT_CAPACITY_J_PER_KG_K
        / SPECIFIC_ISOCHORIC_HEAT_CAPACITY_J_PER_KG_K;
    double pressureDerivative = (DENSITY_AT_55_MPA_KG_PER_M3 - DENSITY_AT_45_MPA_KG_PER_M3)
        / (10.0e6 * DENSITY_KG_PER_M3);

    assertEquals(1592.980718909979, soundSpeedMPerS, 1.0e-9);
    assertEquals(3.9603194920002513e-10, convertedIsothermalCompressibility, 1.0e-23);
    assertRelativeAgreement(heatCapacityIdentity, convertedIsothermalCompressibility);
    assertRelativeAgreement(pressureDerivative, convertedIsothermalCompressibility);
  }

  private static void assertRelativeAgreement(double expected, double actual) {
    assertEquals(expected, actual, Math.abs(expected) * MAXIMUM_RELATIVE_DIFFERENCE);
  }

  private static double publishedSoundSpeedMPerS(double temperatureK, double pressureMpa) {
    double criticalTemperatureK = 647.096;
    double criticalPressureMpa = 22.064;
    double reducedTemperature = temperatureK / criticalTemperatureK;
    double reducedPressure = pressureMpa / criticalPressureMpa;

    double[] coefficients = { 7.423615233e4, -1.571527759e5, 2.742740269e6, -3.599403339e6, -6.379367333e-4,
        2.357819770e5, 5.469994955e5, 2.225433179e-1, -2.304269454e5, 3.932184362e-7, -2.621801558e-6, 2.991452743e5 };
    double[] pressureExponents = { 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 2.0, 2.0, 3.0, 3.0, 3.0 };
    double[] temperatureExponents = { -5.0, -4.5, -0.5, 3.5, -19.0, 1.0, 7.0, -12.0, 8.0, -26.0, -24.0, 14.0 };

    double soundSpeedSquared = 0.0;
    for (int index = 0; index < coefficients.length; index++) {
      soundSpeedSquared += coefficients[index] * Math.pow(reducedPressure, pressureExponents[index])
          * Math.pow(reducedTemperature, temperatureExponents[index]);
    }
    return Math.sqrt(soundSpeedSquared);
  }
}
