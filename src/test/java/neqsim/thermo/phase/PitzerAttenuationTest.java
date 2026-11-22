package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

/** Independent integral references for the Pitzer attenuation and its scaled derivative. */
class PitzerAttenuationTest {
  @Test
  void agreesWithIntegralDefinitionsAcrossSmallArgumentBoundary() {
    for (double x : new double[] {0.0, 1.0e-15, 1.0e-12, 1.0e-8, 1.0e-4, 0.1, 0.499999, 0.5, 0.500001, 1.0, 2.0,
        10.0}) {
      double g = 0.0;
      double scaled = 0.0;
      int intervals = 8192;
      for (int i = 0; i <= intervals; i++) {
        double t = (double) i / intervals;
        double weight = i == 0 || i == intervals ? 1.0 : (i % 2 == 0 ? 2.0 : 4.0);
        double integrand = weight * t * Math.exp(-x * t);
        g += 2.0 * integrand;
        scaled -= x * t * integrand;
      }
      assertEquals(g / (3.0 * intervals), PitzerAttenuation.value(x), 3.0e-14, "g at " + x);
      assertEquals(scaled / (3.0 * intervals), PitzerAttenuation.scaledDerivative(x), 3.0e-14 * Math.min(1.0, x),
          "scaled derivative at " + x);
    }
  }

  @Test
  void analyticalLimitsAndInvalidArgumentsAreExplicit() {
    assertEquals(1.0, PitzerAttenuation.value(0.0), 0.0);
    assertEquals(0.0, PitzerAttenuation.scaledDerivative(0.0), 0.0);
    assertEquals(0.0, PitzerAttenuation.value(Double.MAX_VALUE), 0.0);
    assertEquals(0.0, PitzerAttenuation.scaledDerivative(Double.MAX_VALUE), 0.0);
    for (double x : new double[] {-1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> PitzerAttenuation.value(x));
      assertThrows(IllegalArgumentException.class, () -> PitzerAttenuation.scaledDerivative(x));
    }
  }
}
