package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Independent-unit references for the Twu temperature and pressure perturbations.
 *
 * <p>
 * Reference values were evaluated in degrees Rankine and psia using the Twu equations in the whitsonPVT
 * critical-properties manual, then converted to kelvin and bar. The tolerance allows rounding of the Kelvin-form
 * coefficients used by NeqSim. These are correlation checks, not experimental validation.
 * </p>
 *
 * @see <a href="https://manual.pvt.whitson.com/methods/c7p_characterization/critical_properties_models/">Twu
 * equations</a>
 */
class TwuCriticalPropertiesReferenceTest {
  @ParameterizedTest
  @CsvSource({ "400.0, 0.74, 580.6598280134665, 26.740842734338823",
      "550.0, 0.85, 740.1062912506554, 18.54968847037336",
      "745.2801420983992, 0.94575, 921.5443732930067, 12.051520005768454" })
  void matchesRankineReference(double boilingPoint, double density, double criticalTemperature,
      double criticalPressure) {
    TBPModelInterface model = new TBPfractionModel().getModel("Twu");
    model.setBoilingPoint(boilingPoint);
    // An explicit boiling point makes the reference independent of the MW-to-Tb correlation.
    assertEquals(criticalTemperature, model.calcTC(200.0, density), 0.002);
    assertEquals(criticalPressure, model.calcPC(200.0, density), 0.002);
    assertTrue(model.calcTC(200.0, density) > boilingPoint);
    assertTrue(Double.isFinite(model.calcAcentricFactor(200.0, density)));
    assertTrue(model.calcAcentricFactor(200.0, density) > 0.0);
  }
}
