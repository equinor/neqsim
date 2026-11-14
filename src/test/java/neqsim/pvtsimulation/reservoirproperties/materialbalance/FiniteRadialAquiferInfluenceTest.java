package neqsim.pvtsimulation.reservoirproperties.materialbalance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Independent diffusion and recurrence checks for a sealed radial annulus. */
class FiniteRadialAquiferInfluenceTest {
  /** Resource location for independently generated references. */
  private static final String REFERENCES = "/neqsim/pvtsimulation/finite-radial/";

  @Test
  void matchesIndependentInverseLaplaceResponses() throws IOException {
    JsonObject fixture;
    try (BufferedReader input = fixture("laplace_reference.json")) {
      fixture = JsonParser.parseReader(input).getAsJsonObject();
    }
    assertEquals(36, fixture.getAsJsonArray("rows").size());
    assertEquals(0, fixture.getAsJsonArray("failures").size());
    int parentFailures = 0;
    for (JsonElement entry : fixture.getAsJsonArray("rows")) {
      JsonObject row = entry.getAsJsonObject();
      double radius = row.get("radius").getAsDouble();
      double time = row.get("time").getAsDouble();
      double pressure = row.get("pressure").getAsDouble();
      double slope = row.get("derivative").getAsDouble();
      FiniteRadialAquiferInfluence annulus = new FiniteRadialAquiferInfluence(radius);
      String point = "R=" + radius + " t=" + time;
      assertEquals(pressure, annulus.dimensionlessPressure(time), tolerance(pressure), point);
      assertEquals(slope, annulus.dimensionlessPressureDerivative(time), tolerance(slope), point);
      if (Math.abs(pressure - VanEverdingenHurstAquifer.dimensionlessPressure(time, radius)) > tolerance(pressure)
          || Math.abs(slope - VanEverdingenHurstAquifer.dimensionlessPressureDerivative(time, radius)) > tolerance(
              slope)) {
        parentFailures++;
      }
    }
    assertTrue(parentFailures > 0, "The independent matrix must distinguish the legacy finite approximation");
    for (JsonElement entry : fixture.getAsJsonArray("lateLimits")) {
      JsonObject limit = entry.getAsJsonObject();
      FiniteRadialAquiferInfluence annulus = new FiniteRadialAquiferInfluence(limit.get("radius").getAsDouble());
      double slope = limit.get("lateSlope").getAsDouble();
      double intercept = limit.get("lateIntercept").getAsDouble();
      assertEquals(slope, annulus.dimensionlessPressureDerivative(10000.0), 2.0e-11);
      assertEquals(intercept, annulus.dimensionlessPressure(10000.0) - slope * 10000.0, 2.0e-11);
    }
  }

  @Test
  void matchesIndependentConservativeFiniteVolumes() throws IOException {
    try (BufferedReader input = fixture("fv_reference.csv")) {
      assertEquals("radius,time,response", input.readLine());
      int cases = 0;
      String line;
      while ((line = input.readLine()) != null) {
        String[] row = line.split(",");
        double expected = Double.parseDouble(row[2]);
        double actual = new FiniteRadialAquiferInfluence(Double.parseDouble(row[0]))
            .dimensionlessPressure(Double.parseDouble(row[1]));
        assertEquals(expected, actual, 1.0e-7 + 1.0e-3 * expected, line);
        cases++;
      }
      assertEquals(12, cases);
    }
  }

  @Test
  void preservesPressureAndSlopeTrendsAcrossRadiiAndTheJunction() {
    for (int ri = 0; ri <= 64; ri++) {
      double radius = 2.0 + ri / 8.0;
      FiniteRadialAquiferInfluence annulus = new FiniteRadialAquiferInfluence(radius);
      checkJunction(annulus, radius);
      double previousPressure = 0.0;
      double previousSlope = Double.POSITIVE_INFINITY;
      for (double time = 1.0e-8; time <= 1.0e4; time *= 1.125) {
        double[] response = annulus.evaluate(time);
        assertTrue(response[0] > previousPressure);
        assertTrue(response[1] <= previousSlope && response[1] > 0.0);
        assertTrue(response[2] > 0.0);
        previousPressure = response[0];
        previousSlope = response[1];
      }
    }
  }

  @Test
  void checksModeRootsNearTheBesselEvaluationTransition() {
    // Independent Bessel determinant roots where an inner or outer argument crosses 12.
    double[] transitions = {2.0485236012510892, 2.1116263964381616, 2.310466835351046, 2.5723808077592727,
        4.872645109967445};
    for (double center : transitions) {
      for (double radius : new double[] {center - 1.0e-8, center, center + 1.0e-8}) {
        checkJunction(new FiniteRadialAquiferInfluence(radius), radius);
      }
    }
  }

  /** Checks adjacent-double numerical agreement at the short-time handoff. */
  private static void checkJunction(FiniteRadialAquiferInfluence annulus, double radius) {
    double width = radius - 1.0;
    double junction = width * width / 64.0;
    double[] before = annulus.evaluate(junction);
    double[] after = annulus.evaluate(Math.nextUp(junction));
    for (int i = 0; i < 2; i++) {
      assertEquals(before[i], after[i], 2.0e-12 + 2.0e-11 * before[i], "junction at radius " + radius);
    }
  }

  @Test
  void matchesDiscreteRecurrenceAndApproachesClosedStorage() {
    double[] time = {0.0, 0.01, 0.4, 1.0, 10.0, 100.0, 10000.0};
    double[] drop = {0.0, 4.0, 7.0, 9.0, 2.0, 2.0, 2.0};
    double storage = 12.5;
    FiniteRadialAquiferInfluence annulus = new FiniteRadialAquiferInfluence(3.5);
    double[] influx = VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(time, drop, storage, annulus);
    double expected = 0.0;
    for (int i = 1; i < time.length; i++) {
      double pressure = annulus.dimensionlessPressure(time[i]);
      double slope = annulus.dimensionlessPressureDerivative(time[i]);
      expected += (time[i] - time[i - 1]) * (storage * drop[i] - expected * slope) / (pressure - time[i - 1] * slope);
      assertEquals(expected, influx[i], 2.0e-11 * Math.max(1.0, expected));
    }
    double capacity = 0.5 * (3.5 * 3.5 - 1.0);
    double[] fixedDrop = {4.0, 4.0, 4.0, 4.0};
    double[] extremeTime = {0.0, 1.0e15, Math.nextUp(1.0e15), Double.MAX_VALUE};
    double[] saturated = VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(extremeTime, fixedDrop, storage,
        annulus);
    for (int i = 1; i < saturated.length; i++) {
      assertEquals(storage * fixedDrop[i] * capacity, saturated[i], 2.0e-11);
    }
    assertArrayEquals(new double[time.length],
        VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(time, drop, 0.0, annulus));
    assertArrayEquals(new double[3], VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(
        new double[] {0.0, 0.01, 100.0}, new double[] {0.0, -4.0, -4.0}, storage, annulus));
  }

  @Test
  void validatesEndpointsAndRunsDocumentedExample() {
    for (double radius : new double[] {Math.nextDown(2.0), Math.nextUp(10.0), 0.0, Double.NaN,
        Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> new FiniteRadialAquiferInfluence(radius));
    }
    FiniteRadialAquiferInfluence annulus = new FiniteRadialAquiferInfluence(3.5);
    for (double time : new double[] {-1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> annulus.dimensionlessPressure(time));
    }
    assertEquals(0.0, annulus.dimensionlessPressure(0.0));
    assertEquals(0.0, annulus.dimensionlessPressureDerivative(0.0));
    assertTrue(annulus.dimensionlessPressure(Double.MIN_VALUE) > 0.0);
    assertTrue(Double.isFinite(annulus.dimensionlessPressureDerivative(Double.MIN_VALUE)));
    assertTrue(Double.isFinite(annulus.dimensionlessPressure(Double.MAX_VALUE)));
    assertThrows(IllegalArgumentException.class,
        () -> VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(new double[] {0.0}, new double[] {0.0}, 1.0, null));
    assertThrows(IllegalArgumentException.class,
        () -> VanEverdingenHurstAquifer.influenceTable(new double[] {0.0}, null));
    assertThrows(IllegalArgumentException.class, () -> VanEverdingenHurstAquifer.exportAqutab(new double[0], annulus));

    double[] times = {0.01, 0.1, 1.0, 10.0, 100.0};
    double[] pressureDrops = {0.0, 1.0, 2.0, 3.0, 4.0};
    double aquiferConstant = 12.5;
    double[] pressures = VanEverdingenHurstAquifer.influenceTable(times, annulus);
    double[] cumulativeInflux = VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(times, pressureDrops,
        aquiferConstant, annulus);
    String aqutab = VanEverdingenHurstAquifer.exportAqutab(times, annulus);
    assertEquals(annulus.dimensionlessPressure(1.0), pressures[2]);
    assertTrue(cumulativeInflux[4] > 0.0);
    assertTrue(aqutab.startsWith("AQUTAB\n"));
    assertTrue(aqutab.contains("0.010000"));
    assertTrue(aqutab.endsWith("/\n"));
  }

  /** Opens one pinned reference resource as UTF-8. */
  private static BufferedReader fixture(String filename) {
    return new BufferedReader(new InputStreamReader(
        FiniteRadialAquiferInfluenceTest.class.getResourceAsStream(REFERENCES + filename), StandardCharsets.UTF_8));
  }

  /** Returns the absolute pressure or derivative error budget. */
  private static double tolerance(double expected) {
    return 2.0e-11 + 5.0e-10 * Math.abs(expected);
  }
}
