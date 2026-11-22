package neqsim.process.corrosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests that carbon and low-alloy steels are not assessed against stainless-steel chloride SCC limits, and that the
 * oxygen derating applies to 304/316 grades.
 */
public class ChlorideSCCCarbonSteelTest {

  /**
   * Runs an assessment for one material and service.
   *
   * @param material material designation
   * @param temperatureC temperature in degrees Celsius
   * @param chlorideMgL chloride in mg/L
   * @param oxygen dissolved oxygen present
   * @return the evaluated assessment
   */
  private ChlorideSCCAssessment assess(String material, double temperatureC, double chlorideMgL, boolean oxygen) {
    ChlorideSCCAssessment assessment = new ChlorideSCCAssessment();
    assessment.setMaterialType(material);
    assessment.setTemperatureC(temperatureC);
    assessment.setChlorideConcentrationMgL(chlorideMgL);
    assessment.setOxygenPresent(oxygen);
    assessment.evaluate();
    return assessment;
  }

  /** Carbon and low-alloy steel designations are not susceptible to chloride SCC. */
  @Test
  void carbonAndLowAlloySteelsAreNotSusceptible() {
    for (String material : new String[] {"Carbon Steel", "CS", "API 5L X65", "A106 Gr. B", "Low alloy steel"}) {
      ChlorideSCCAssessment assessment = assess(material, 80.0, 50000.0, false);
      assertTrue(assessment.isSCCAcceptable(), material + " should be acceptable");
      assertEquals("Not susceptible", assessment.getRiskLevel(), material);
      assertTrue(Double.isNaN(assessment.getMaxAllowableTemperatureC()), material);
    }
  }

  /** Stainless grades are still assessed against their limits. */
  @Test
  void stainlessGradesAreStillAssessed() {
    assertFalse(assess("316L", 80.0, 20000.0, false).isSCCAcceptable());
    assertFalse(ChlorideSCCAssessment.isCarbonOrLowAlloySteel("22CR DUPLEX"));
    assertFalse(ChlorideSCCAssessment.isCarbonOrLowAlloySteel("13CR SUPERMARTENSITIC"));
    assertFalse(ChlorideSCCAssessment.isCarbonOrLowAlloySteel("316L STAINLESS"));
  }

  /** Dissolved oxygen lowers the 316L limit by 25 degrees C. */
  @Test
  void oxygenDeratesAusteniticLimit() {
    double withoutOxygen = assess("316L", 45.0, 500.0, false).getMaxAllowableTemperatureC();
    double withOxygen = assess("316L", 45.0, 500.0, true).getMaxAllowableTemperatureC();
    assertEquals(withoutOxygen - 25.0, withOxygen, 1.0e-9);
  }
}
