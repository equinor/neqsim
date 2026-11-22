package neqsim.pvtsimulation.reservoirproperties.materialbalance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Independent mathematical references and numerical limits for the infinite radial influence kernel.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class RadialInfiniteInfluenceTest {
  /** Infinite outer-radius convention. */
  private static final double INFINITE = Double.POSITIVE_INFINITY;

  /**
   * Screens every representable shortcut boundary without allowing roundoff-sized pressure decreases.
   *
   * @throws Exception if the frozen common input vector cannot be read
   */
  @Test
  void resolvedTailIsMonotoneAtAllShortcutBoundaries() throws Exception {
    String path = "/neqsim/pvtsimulation/infinite-radial/optimization64/common-times.txt";
    try (InputStream stream = getClass().getResourceAsStream(path)) {
      assertNotNull(stream);
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        double previousTime = 0.0;
        double previousPressure = 0.0;
        int count = 0;
        String line;
        while ((line = reader.readLine()) != null) {
          double time = Double.parseDouble(line);
          double pressure = VanEverdingenHurstAquifer.dimensionlessPressure(time, INFINITE);
          double derivative = VanEverdingenHurstAquifer.dimensionlessPressureDerivative(time, INFINITE);
          assertTrue(Double.isFinite(time) && time > previousTime);
          assertTrue(Double.isFinite(pressure) && pressure >= previousPressure,
              "Positive-tail pressure must not decrease at " + time);
          assertTrue(Double.isFinite(derivative) && derivative > 0.0);
          previousTime = time;
          previousPressure = pressure;
          count++;
        }
        assertEquals(51216, count);
      }
    }
  }

  /**
   * Checks every separately inverted pressure and derivative, including off-grid times.
   *
   * @throws Exception if the retained independent reference cannot be read
   */
  @Test
  void independentLaplacePressureAndDerivative() throws Exception {
    try (InputStream stream = getClass().getResourceAsStream("/neqsim/pvtsimulation/infinite-radial/reference.json")) {
      assertNotNull(stream);
      JsonObject data = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
      assertEquals(0, data.getAsJsonArray("failures").size());
      assertEquals(20, data.getAsJsonArray("rows").size());
      for (JsonElement element : data.getAsJsonArray("rows")) {
        JsonObject row = element.getAsJsonObject();
        double time = row.get("time").getAsDouble();
        double pressure = row.getAsJsonObject("pressure").get("digits35").getAsDouble();
        double derivative = row.getAsJsonObject("derivative").get("digits35").getAsDouble();
        assertEquals(pressure, VanEverdingenHurstAquifer.dimensionlessPressure(time, INFINITE), 1e-7 + 1e-6 * pressure,
            "Independent pressure at " + time);
        assertEquals(derivative, VanEverdingenHurstAquifer.dimensionlessPressureDerivative(time, INFINITE),
            1e-6 + 1e-5 * derivative, "Independent derivative at " + time);
      }
    }
  }

  /** Protects both old branch junctions and consistency of the analytic derivative. */
  @Test
  void pressureAndDerivativeAreContinuousAtFormerJunctions() {
    for (double time : new double[] {0.01, 200.0, Math.exp(80.0), 50.0 * Math.exp(80.0),
        (26.0 / 64.0) * (26.0 / 64.0)}) {
      double before = VanEverdingenHurstAquifer.dimensionlessPressure(Math.nextDown(time), INFINITE);
      double at = VanEverdingenHurstAquifer.dimensionlessPressure(time, INFINITE);
      double after = VanEverdingenHurstAquifer.dimensionlessPressure(Math.nextUp(time), INFINITE);
      assertTrue(before <= at && at <= after, "Pressure cannot jump downward at " + time);
      assertEquals(before, after, 1e-12 * Math.max(1.0, at));
      double derivative = VanEverdingenHurstAquifer.dimensionlessPressureDerivative(time, INFINITE);
      assertTrue(derivative > 0.0);
      assertEquals(derivative, VanEverdingenHurstAquifer.dimensionlessPressureDerivative(Math.nextUp(time), INFINITE),
          1e-12 * derivative);
    }
    for (double time : new double[] {1e-8, 1e-4, 0.1, 1.0, 100.0, 1e4, 1e10}) {
      double step = time * 1e-4;
      double difference = (VanEverdingenHurstAquifer.dimensionlessPressure(time + step, INFINITE)
          - VanEverdingenHurstAquifer.dimensionlessPressure(time - step, INFINITE)) / (2.0 * step);
      double analytic = VanEverdingenHurstAquifer.dimensionlessPressureDerivative(time, INFINITE);
      assertEquals(difference, analytic, 1e-6 + 1e-5 * analytic);
    }
  }

  /** Exercises representable time endpoints with scaled, nonvacuous asymptotic checks. */
  @Test
  void positiveTimeRangeRetainsBothDiffusionLimits() {
    double tiny = Double.MIN_VALUE;
    double root = Math.sqrt(tiny);
    assertEquals(2.0 / Math.sqrt(Math.PI), VanEverdingenHurstAquifer.dimensionlessPressure(tiny, INFINITE) / root,
        1e-12);
    assertEquals(1.0 / Math.sqrt(Math.PI),
        VanEverdingenHurstAquifer.dimensionlessPressureDerivative(tiny, INFINITE) * root, 1e-12);
    double huge = Double.MAX_VALUE;
    assertEquals(0.5, huge * VanEverdingenHurstAquifer.dimensionlessPressureDerivative(huge, INFINITE), 1e-12);
    assertEquals(Math.log(2.0) - 0.5 * 0.5772156649015328606,
        VanEverdingenHurstAquifer.dimensionlessPressure(huge, INFINITE) - 0.5 * Math.log(huge), 1e-7);
    double previous = 0.0;
    for (int i = 0; i <= 2000; i++) {
      double time = Math.exp(-740.0 + 1400.0 * i / 2000.0);
      double pressure = VanEverdingenHurstAquifer.dimensionlessPressure(time, INFINITE);
      double derivative = VanEverdingenHurstAquifer.dimensionlessPressureDerivative(time, INFINITE);
      assertTrue(Double.isFinite(pressure) && pressure > previous, "Positive monotone pressure at " + time);
      assertTrue(Double.isFinite(derivative) && derivative > 0.0, "Positive derivative at " + time);
      previous = pressure;
    }
  }

  /** Preserves zero-time bookkeeping and sentinel radii while rejecting invalid time evidence. */
  @Test
  void conventionsAndInvalidArgumentsAreExplicit() {
    assertEquals(0.0, VanEverdingenHurstAquifer.dimensionlessPressure(0.0, INFINITE));
    assertEquals(0.0, VanEverdingenHurstAquifer.dimensionlessPressureDerivative(0.0, INFINITE));
    for (double radius : new double[] {0.0, -1.0, Double.NEGATIVE_INFINITY}) {
      assertEquals(VanEverdingenHurstAquifer.dimensionlessPressure(1.0, INFINITE),
          VanEverdingenHurstAquifer.dimensionlessPressure(1.0, radius));
      assertEquals(VanEverdingenHurstAquifer.dimensionlessPressureDerivative(1.0, INFINITE),
          VanEverdingenHurstAquifer.dimensionlessPressureDerivative(1.0, radius));
    }
    for (double time : new double[] {-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class,
          () -> VanEverdingenHurstAquifer.dimensionlessPressure(time, INFINITE));
      assertThrows(IllegalArgumentException.class,
          () -> VanEverdingenHurstAquifer.dimensionlessPressureDerivative(time, INFINITE));
    }
    assertThrows(IllegalArgumentException.class,
        () -> VanEverdingenHurstAquifer.dimensionlessPressure(1.0, Double.NaN));
  }

  /** Qualifies derivative selection only, without admitting the legacy bounded transient policy. */
  @Test
  void boundedMaximumPolicyRemainsExplicitlySeparate() {
    double time = 1e-4;
    double expected = 2.0 * time / 99.0 + Math.log(10.0) - 0.75;
    assertEquals(expected, VanEverdingenHurstAquifer.dimensionlessPressure(time, 10.0), 1e-15);
    assertEquals(2.0 / 99.0, VanEverdingenHurstAquifer.dimensionlessPressureDerivative(time, 10.0), 1e-15);
  }
}
