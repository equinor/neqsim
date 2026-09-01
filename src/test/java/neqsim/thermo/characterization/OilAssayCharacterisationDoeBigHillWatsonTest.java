package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Public DOE refinery-assay qualification of per-cut UOP/Watson characterization factors. */
public class OilAssayCharacterisationDoeBigHillWatsonTest {
  private static final double[] LOWER_BOUNDARY_F = { 375.0, 530.0, 650.0, 850.0 };
  private static final double[] UPPER_BOUNDARY_F = { 530.0, 650.0, 850.0, 1050.0 };
  private static final double[] SPECIFIC_GRAVITY = { 0.8297, 0.8604, 0.9039, 0.9336 };
  private static final double[] DOE_WATSON_FACTOR = { 11.7, 11.8, 11.8, 12.0 };

  @Test
  public void doeBoundedCutsReproducePublishedWatsonFactors() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.clearCuts();

    double maximumAbsoluteError = 0.0;
    for (int i = 0; i < DOE_WATSON_FACTOR.length; i++) {
      AssayCut cut = new AssayCut("DOE_BH_2021_" + (i + 1)).withMassFraction(0.25)
          .withSpecificGravity(SPECIFIC_GRAVITY[i])
          .withBoilingRangeCelsius(fahrenheitToCelsius(LOWER_BOUNDARY_F[i]), fahrenheitToCelsius(UPPER_BOUNDARY_F[i]));
      characterisation.addCut(cut);

      double absoluteError = Math.abs(cut.getWatsonCharacterizationFactor() - DOE_WATSON_FACTOR[i]);
      maximumAbsoluteError = Math.max(maximumAbsoluteError, absoluteError);
      assertEquals(DOE_WATSON_FACTOR[i], cut.getWatsonCharacterizationFactor(), 0.05,
          "DOE one-decimal UOP K factor should agree with the bounded-cut midpoint calculation");
    }

    assertEquals(0.012132370527373482, maximumAbsoluteError, 1.0e-12);
    assertEquals(0, system.getNumberOfComponents(), "Property queries must not mutate the thermodynamic system");
  }

  @Test
  public void temperatureAndDensityInputViewsRemainEquivalent() {
    double midpointFahrenheit = 0.5 * (375.0 + 530.0);
    double midpointCelsius = fahrenheitToCelsius(midpointFahrenheit);
    double midpointKelvin = midpointCelsius + 273.15;
    double specificGravity = 0.8297;
    double exactApiGravity = 141.5 / specificGravity - 131.5;

    AssayCut kelvinCut = new AssayCut("Kelvin").withMassFraction(1.0).withSpecificGravity(specificGravity)
        .withAverageBoilingPointKelvin(midpointKelvin);
    AssayCut celsiusCut = new AssayCut("Celsius").withMassFraction(1.0).withSpecificGravity(specificGravity)
        .withAverageBoilingPointCelsius(midpointCelsius);
    AssayCut fahrenheitCut = new AssayCut("Fahrenheit").withMassFraction(1.0).withApiGravity(exactApiGravity)
        .withAverageBoilingPointFahrenheit(midpointFahrenheit);
    AssayCut rangeCut = new AssayCut("Range").withMassFraction(1.0).withSpecificGravity(specificGravity)
        .withBoilingRangeCelsius(fahrenheitToCelsius(375.0), fahrenheitToCelsius(530.0));

    double expected = kelvinCut.getWatsonCharacterizationFactor();
    assertEquals(expected, celsiusCut.getWatsonCharacterizationFactor(), 1.0e-12);
    assertEquals(expected, fahrenheitCut.getWatsonCharacterizationFactor(), 1.0e-12);
    assertEquals(expected, rangeCut.getWatsonCharacterizationFactor(), 1.0e-12);
    assertTrue(Double.isFinite(expected));
    assertTrue(expected > 0.0);
  }

  @Test
  public void missingInputsFailClosed() {
    AssayCut missingDensity = new AssayCut("MissingDensity").withMassFraction(1.0)
        .withAverageBoilingPointFahrenheit(452.5);
    AssayCut missingBoilingPoint = new AssayCut("MissingBoilingPoint").withMassFraction(1.0)
        .withSpecificGravity(0.8297);

    assertThrows(IllegalStateException.class, missingDensity::getWatsonCharacterizationFactor);
    assertThrows(IllegalStateException.class, missingBoilingPoint::getWatsonCharacterizationFactor);
  }

  private static double fahrenheitToCelsius(double fahrenheit) {
    return (fahrenheit - 32.0) * 5.0 / 9.0;
  }
}
