package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests to verify correctness of the gamma function implementation in WhitsonGammaModel.
 * 
 * Known exact values of Gamma function: - Gamma(1) = 1 - Gamma(2) = 1 - Gamma(3) = 2 - Gamma(0.5) =
 * sqrt(pi) = 1.7724538509 - Gamma(1.5) = sqrt(pi)/2 = 0.8862269255 - Gamma(2.5) = 3*sqrt(pi)/4 =
 * 1.3293403882
 */
public class GammaFunctionVerificationTest {

  private static final double SQRT_PI = Math.sqrt(Math.PI);
  private static final double TOLERANCE = 0.01; // 1% tolerance

  private PlusFractionModel model;
  private PlusFractionModel.WhitsonGammaModel gammaModel;

  @BeforeEach
  void setUp() {
    SystemSrkEos system = new SystemSrkEos(298.0, 10.0);
    system.addComponent("methane", 1.0);
    system.addPlusFraction("C7+", 1.0, 200.0, 0.8);
    model = new PlusFractionModel(system);
    gammaModel = model.new WhitsonGammaModel();
  }

  @Test
  void testGammaAtIntegerValues() {
    // Test Gamma(1) = 1
    double gamma1 = gammaModel.gamma(1.0);
    System.out.println("Gamma(1) = " + gamma1 + " (expected: 1.0)");
    assertEquals(1.0, gamma1, TOLERANCE, "Gamma(1) should be 1.0");

    // Test Gamma(2) = 1 (since Gamma(n) = (n-1)!)
    double gamma2 = gammaModel.gamma(2.0);
    System.out.println("Gamma(2) = " + gamma2 + " (expected: 1.0)");
    assertEquals(1.0, gamma2, TOLERANCE, "Gamma(2) should be 1.0");

    // Test Gamma(3) = 2! = 2
    double gamma3 = gammaModel.gamma(3.0);
    System.out.println("Gamma(3) = " + gamma3 + " (expected: 2.0)");
    assertEquals(2.0, gamma3, TOLERANCE * 2.0, "Gamma(3) should be 2.0");

    // Test Gamma(4) = 3! = 6
    double gamma4 = gammaModel.gamma(4.0);
    System.out.println("Gamma(4) = " + gamma4 + " (expected: 6.0)");
    assertEquals(6.0, gamma4, TOLERANCE * 6.0, "Gamma(4) should be 6.0");
  }

  @Test
  void testGammaAtHalfIntegerValues() {
    // Test Gamma(0.5) = sqrt(pi) = 1.7724538509
    double gamma05 = gammaModel.gamma(0.5);
    System.out.println("Gamma(0.5) = " + gamma05 + " (expected: " + SQRT_PI + ")");
    assertEquals(SQRT_PI, gamma05, TOLERANCE * SQRT_PI, "Gamma(0.5) should be sqrt(pi)");

    // Test Gamma(1.5) = sqrt(pi)/2 = 0.8862269255
    double expected15 = SQRT_PI / 2.0;
    double gamma15 = gammaModel.gamma(1.5);
    System.out.println("Gamma(1.5) = " + gamma15 + " (expected: " + expected15 + ")");
    assertEquals(expected15, gamma15, TOLERANCE * expected15, "Gamma(1.5) should be sqrt(pi)/2");

    // Test Gamma(2.5) = 3*sqrt(pi)/4 = 1.3293403882
    double expected25 = 3.0 * SQRT_PI / 4.0;
    double gamma25 = gammaModel.gamma(2.5);
    System.out.println("Gamma(2.5) = " + gamma25 + " (expected: " + expected25 + ")");
    assertEquals(expected25, gamma25, TOLERANCE * expected25, "Gamma(2.5) should be 3*sqrt(pi)/4");
  }

  @Test
  void testGammaRecurrenceRelation() {
    // Test the fundamental property: Gamma(x+1) = x * Gamma(x)
    double[] testValues = {1.0, 1.5, 2.0, 2.5, 3.0, 0.5};

    for (double x : testValues) {
      double gammaX = gammaModel.gamma(x);
      double gammaXplus1 = gammaModel.gamma(x + 1.0);
      double expected = x * gammaX;

      System.out.println(
          "Gamma(" + (x + 1.0) + ") = " + gammaXplus1 + ", x*Gamma(" + x + ") = " + expected);

      // The recurrence should hold: Gamma(x+1) = x * Gamma(x)
      assertEquals(expected, gammaXplus1, TOLERANCE * Math.abs(expected),
          "Recurrence relation Gamma(x+1) = x*Gamma(x) should hold for x = " + x);
    }
  }

  @Test
  void testTypicalCharacterizationAlphaValues() {
    // Test gamma function at typical alpha values used in characterization (0.5 to 3.0)

    // Test typical alpha = 1.0 (exponential distribution)
    double gamma10 = gammaModel.gamma(1.0);
    System.out.println("Gamma(1.0) = " + gamma10);
    assertTrue(gamma10 > 0, "Gamma function should be positive");
    assertEquals(1.0, gamma10, TOLERANCE, "Gamma(1) should be 1.0");

    // Test typical alpha = 0.5
    double gamma05 = gammaModel.gamma(0.5);
    System.out.println("Gamma(0.5) = " + gamma05);
    assertTrue(gamma05 > 1.7 && gamma05 < 1.8,
        "Gamma(0.5) should be approximately sqrt(pi) = 1.77, got: " + gamma05);

    // Test typical alpha = 2.0
    double gamma20 = gammaModel.gamma(2.0);
    System.out.println("Gamma(2.0) = " + gamma20);
    assertEquals(1.0, gamma20, TOLERANCE, "Gamma(2) should be 1.0");
  }
}
