package neqsim.process.corrosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for the FeCO3 supersaturation film feedback added to {@link NorsokM506CorrosionRate}.
 *
 * @author ESOL
 * @version 1.0
 */
public class NorsokM506FeCO3FilmTest {

  /**
   * Builds a low-temperature CO2 case where the temperature-only scale correction is inactive, so the FeCO3 film effect
   * is isolated.
   *
   * @return a configured (not yet calculated) corrosion model
   */
  private NorsokM506CorrosionRate lowTemperatureCase() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();
    model.setTemperatureCelsius(30.0);
    model.setTotalPressureBara(50.0);
    model.setCO2MoleFraction(0.05);
    model.setFlowVelocityMs(1.0);
    model.setPipeDiameterM(0.2);
    model.setActualPH(4.5);
    return model;
  }

  /**
   * Verifies that an unset FeCO3 ratio leaves the model behaviour unchanged (film factor = 1).
   */
  @Test
  void unsetRatioIsBackwardCompatible() {
    NorsokM506CorrosionRate model = lowTemperatureCase();
    model.calculate();
    assertEquals(1.0, model.calculateFeCO3FilmFactor(), 1.0e-9, "unset FeCO3 ratio should give a film factor of 1.0");
    assertEquals(-1.0, model.getFeCO3SaturationRatio(), 1.0e-9);
  }

  /**
   * Verifies that a supersaturated FeCO3 ratio reduces the corrosion rate relative to the undersaturated case.
   */
  @Test
  void supersaturationSuppressesCorrosion() {
    NorsokM506CorrosionRate baseline = lowTemperatureCase();
    baseline.calculate();
    double baseRate = baseline.getCorrectedCorrosionRate();

    NorsokM506CorrosionRate filmed = lowTemperatureCase();
    filmed.setFeCO3SaturationRatio(50.0); // strongly supersaturated -> protective siderite film
    filmed.calculate();
    double filmedRate = filmed.getCorrectedCorrosionRate();

    assertTrue(filmed.calculateFeCO3FilmFactor() < 1.0, "supersaturation should give a film factor below 1");
    assertTrue(filmedRate < baseRate,
        "supersaturated FeCO3 should reduce corrosion: " + filmedRate + " vs " + baseRate);
    assertTrue(filmedRate > 0.0, "corrosion rate should remain positive");
  }

  /**
   * Verifies that an undersaturated FeCO3 ratio gives no film credit.
   */
  @Test
  void undersaturationGivesNoFilmCredit() {
    NorsokM506CorrosionRate model = lowTemperatureCase();
    model.setFeCO3SaturationRatio(0.5); // undersaturated
    model.calculate();
    assertEquals(1.0, model.calculateFeCO3FilmFactor(), 1.0e-9, "undersaturated FeCO3 should give no film credit");
  }
}
