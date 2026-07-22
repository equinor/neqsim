package neqsim.process.mechanicaldesign.thermowell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ThermowellDesignCalculator}.
 */
public class ThermowellDesignCalculatorTest {
  /**
   * A short, stiff thermowell in a moderate gas flow should screen as acceptable with a low frequency ratio.
   */
  @Test
  void testAcceptableShortThermowell() {
    ThermowellDesignCalculator calc = new ThermowellDesignCalculator();
    calc.setGeometry(0.10, 0.0226, 0.0226, 0.00665, 0.005);
    calc.setMaterial(7960.0, 1.93e11, 1.38e8, 4.83e7);
    calc.setProcessConditions(50.0, 15.0, 1.5e-5, 5.0e6);
    calc.setCorrectionFactors(0.99, 0.97, 1.0);
    calc.calcAll();

    assertTrue(calc.getReynoldsNumber() > 0.0, "Reynolds number should be positive");
    assertTrue(calc.getStrouhalNumber() >= 0.18 && calc.getStrouhalNumber() <= 0.22,
        "Strouhal number should be in the subcritical plateau");
    assertTrue(calc.getInstalledNaturalFrequency() < calc.getNaturalFrequency(),
        "Installed natural frequency should be reduced by correction factors");
    assertTrue(calc.getFrequencyRatio() < 0.8, "Short stiff well should be below the frequency limit");
    assertTrue(calc.isFrequencyCheckPassed(), "Frequency check should pass");
    assertNotNull(calc.toJson());
  }

  /**
   * A long slender thermowell in a high-velocity flow should have a higher frequency ratio than a short stiff one.
   */
  @Test
  void testLongThermowellHigherFrequencyRatio() {
    ThermowellDesignCalculator shortWell = new ThermowellDesignCalculator();
    shortWell.setGeometry(0.10, 0.0226, 0.0226, 0.00665, 0.005);
    shortWell.setMaterial(7960.0, 1.93e11, 1.38e8, 4.83e7);
    shortWell.setProcessConditions(80.0, 45.0, 1.4e-5, 8.0e6);
    shortWell.calcAll();

    ThermowellDesignCalculator longWell = new ThermowellDesignCalculator();
    longWell.setGeometry(0.40, 0.0180, 0.0180, 0.00665, 0.004);
    longWell.setMaterial(7960.0, 1.93e11, 1.38e8, 4.83e7);
    longWell.setProcessConditions(80.0, 45.0, 1.4e-5, 8.0e6);
    longWell.calcAll();

    assertTrue(longWell.getFrequencyRatio() > shortWell.getFrequencyRatio(),
        "Long slender well should have a higher frequency ratio");
  }

  /**
   * The hydrostatic limit and design-acceptable aggregation should be consistent.
   */
  @Test
  void testHydrostaticLimit() {
    ThermowellDesignCalculator calc = new ThermowellDesignCalculator();
    calc.setGeometry(0.10, 0.0226, 0.0226, 0.00665, 0.005);
    calc.setMaterial(7960.0, 1.93e11, 1.38e8, 4.83e7);
    calc.setProcessConditions(50.0, 15.0, 1.5e-5, 5.0e6);
    calc.calcAll();

    assertTrue(calc.getMaxAllowablePressure() > 0.0, "Max allowable pressure should be positive");
    boolean aggregate = calc.isFrequencyCheckPassed() && calc.isDynamicStressCheckPassed()
        && calc.isStaticStressCheckPassed() && calc.isHydrostaticCheckPassed();
    assertEquals(aggregate, calc.isDesignAcceptable(), "Aggregate acceptance should match individual checks");
  }

  /**
   * Lowering the frequency-ratio limit to the conservative TW-1974 basis should tighten the frequency check.
   */
  @Test
  void testConservativeFrequencyLimit() {
    ThermowellDesignCalculator calc = new ThermowellDesignCalculator();
    calc.setGeometry(0.20, 0.0200, 0.0200, 0.00665, 0.004);
    calc.setMaterial(7960.0, 1.93e11, 1.38e8, 4.83e7);
    calc.setProcessConditions(60.0, 30.0, 1.5e-5, 6.0e6);
    calc.setFrequencyRatioLimit(0.4);
    calc.calcAll();
    boolean strict = calc.isFrequencyCheckPassed();

    calc.setFrequencyRatioLimit(0.8);
    calc.calcAll();
    boolean relaxed = calc.isFrequencyCheckPassed();

    assertTrue(relaxed || !strict, "Relaxing the limit cannot make a passing case fail");
    assertFalse(strict && !relaxed, "A case passing the strict limit must also pass the relaxed limit");
  }
}
