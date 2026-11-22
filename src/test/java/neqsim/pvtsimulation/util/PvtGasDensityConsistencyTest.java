package neqsim.pvtsimulation.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.util.PvtGasDensityConsistency.Observation;
import neqsim.pvtsimulation.util.PvtGasDensityConsistency.Quantity;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Independent arithmetic, source transcription, limits and opt-in handoff qualification.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PvtGasDensityConsistencyTest {
  private static final Logger logger = LogManager.getLogger(PvtGasDensityConsistencyTest.class);
  private static final double PSI_PA = 6894.757293168;

  /**
   * Creates a point or bounded synthetic gas tuple with explicit SI units.
   *
   * @param density observed density in kg/m3
   * @param bound absolute density bound in kg/m3
   * @return observation
   */
  private Observation ideal(double density, double bound) {
    return new Observation("row-1", "CCE-1", "same equilibrium gas phase", "synthetic analytic fixture",
        new Quantity(831446.261815324, 0), new Quantity(100, 0), new Quantity(1, 0), new Quantity(0.02, 0),
        new Quantity(density, bound));
  }

  /**
   * Freezes a named sample's check set.
   *
   * @param sample sample identity
   * @param rows complete selected observation set
   * @return immutable report
   */
  private PvtGasDensityConsistency report(String sample, Observation... rows) {
    return new PvtGasDensityConsistency(sample, "explicit absolute bounds, not sigmas", "public synthetic data", rows);
  }

  /** Point, touching-interval, dilute and inconsistent limits do not use an EOS prediction. */
  @Test
  void analyticPointAndClosedIntervalLimits() {
    assertEquals(20, ideal(20, 0).getImpliedDensityKgPerM3(), 1e-13);
    assertTrue(ideal(20, 0).isCompatible());
    assertTrue(ideal(21, 1).isCompatible());
    assertFalse(ideal(21, 0.99).isCompatible());
    assertFalse(ideal(19, 0.99).isCompatible());
    Observation dilute = new Observation("dilute", "CCE", "gas", "analytic", new Quantity(1e-6, 0),
        new Quantity(100, 0), new Quantity(1, 0), new Quantity(0.02, 0), new Quantity(2.4054471008545208e-11, 1e-25));
    assertTrue(dilute.isCompatible());
    assertTrue(dilute.getImpliedLowerKgPerM3() > 0);
  }

  /** Positive-input monotonicity encloses all 16 independent decimal endpoint combinations. */
  @Test
  void intervalBoundsEncloseIndependentDecimalCorners() {
    Quantity[] q = {new Quantity(20e6, 1e5), new Quantity(350, 0.5), new Quantity(0.9, 0.01),
        new Quantity(0.025, 0.0001)};
    Observation observation = new Observation("corners", "CCE", "gas", "independent decimal corner matrix", q[0], q[1],
        q[2], q[3], new Quantity(200, 100));
    for (int mask = 0; mask < 16; mask++) {
      BigDecimal[] values = new BigDecimal[4];
      for (int i = 0; i < values.length; i++) {
        values[i] = BigDecimal.valueOf((mask & (1 << i)) == 0 ? q[i].getLower() : q[i].getUpper());
      }
      BigDecimal density = values[0].multiply(values[3])
          .divide(values[2].multiply(values[1]).multiply(new BigDecimal("8.31446261815324")), MathContext.DECIMAL128);
      assertTrue(density.doubleValue() >= observation.getImpliedLowerKgPerM3());
      assertTrue(density.doubleValue() <= observation.getImpliedUpperKgPerM3());
    }
  }

  /** Nonphysical values and unrepresentable arithmetic fail explicitly rather than creating a passing NaN. */
  @Test
  void rejectsMissingNonphysicalAndUnrepresentableInputs() {
    for (double value : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> new Quantity(value, 0));
    }
    for (double bound : new double[] {-1, 1, 2, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> new Quantity(1, bound));
    }
    assertThrows(IllegalArgumentException.class, () -> new Quantity(Double.MAX_VALUE, Double.MIN_VALUE));
    assertThrows(IllegalArgumentException.class, () -> new Observation("a", "b", "c", "d", null, new Quantity(1, 0),
        new Quantity(1, 0), new Quantity(1, 0), new Quantity(1, 0)));
    assertThrows(IllegalArgumentException.class,
        () -> new Observation("a", "b", "c", "d", new Quantity(Double.MAX_VALUE, 0), new Quantity(1, 0),
            new Quantity(1, 0), new Quantity(2, 0), new Quantity(1, 0)));
    assertThrows(IllegalArgumentException.class,
        () -> new Observation("a", "b", "c", "d", new Quantity(Double.MIN_VALUE, 0), new Quantity(1, 0),
            new Quantity(1, 0), new Quantity(0.1, 0), new Quantity(1, 0)));
    assertThrows(IllegalArgumentException.class, () -> report("sample"));
    assertThrows(IllegalArgumentException.class, () -> report("sample", ideal(20, 0), ideal(21, 0)));
    assertThrows(IllegalArgumentException.class, () -> report(" ", ideal(20, 0)));
    assertThrows(IllegalArgumentException.class, () -> report("sample", (Observation) null));
  }

  /** Failures retain raw values, source references and useful row identities after serialization. */
  @Test
  void immutableCompleteEvidenceRetainsFailures() {
    Observation[] rows = {ideal(21, 0)};
    PvtGasDensityConsistency evidence = report("sample", rows);
    rows[0] = ideal(20, 0);
    String json = evidence.toJson();
    PvtGasDensityConsistency restored = SerializationUtils.clone(evidence);
    assertEquals(json, restored.toJson());
    assertFalse(restored.isCompatible());
    assertTrue(assertThrows(IllegalStateException.class, restored::requireCompatible).getMessage().contains("row-1"));
    JsonObject exported = JsonParser.parseString(json).getAsJsonObject();
    JsonObject row = exported.getAsJsonArray("rows").get(0).getAsJsonObject();
    assertEquals(21, row.getAsJsonObject("densityKgPerM3").get("value").getAsDouble(), 0);
    assertEquals("CCE-1", row.get("experimentId").getAsString());
    row.addProperty("compatible", true);
    assertEquals(json, evidence.toJson());
  }

  /**
   * Reads a small UTF-8 test resource without an external download at test time.
   *
   * @param name resource file name
   * @return buffered resource reader
   */
  private BufferedReader resource(String name) {
    return new BufferedReader(new InputStreamReader(
        getClass().getResourceAsStream("/neqsim/pvtsimulation/gas-density/" + name), StandardCharsets.UTF_8));
  }

  /**
   * Printed source values (including 1300 psia) match independently executed 50-digit decimal evidence.
   *
   * @throws Exception if the checked-in source or reference fixture cannot be read
   */
  @Test
  void publicPrimaryDataPreservesOneIncompatibleRow() throws Exception {
    JsonArray reference;
    try (BufferedReader reader = resource("reference-results.json")) {
      reference = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("rows");
    }
    List<Observation> rows = new ArrayList<Observation>();
    int compatible = 0;
    try (BufferedReader reader = resource("duvernay-table18.csv")) {
      reader.readLine();
      String line;
      while ((line = reader.readLine()) != null) {
        String[] fields = line.split(",");
        Observation row = new Observation(fields[0], "2012-10037190-table18-CVD", "recombined sample vapor at 106 C",
            "report 2012-10037190 printed p37 table18 " + fields[0],
            new Quantity(Double.parseDouble(fields[1]) * PSI_PA, 0.5 * PSI_PA), new Quantity(379.15, 0.5),
            new Quantity(Double.parseDouble(fields[2]), 0.0005),
            new Quantity(Double.parseDouble(fields[3]) / 1000, 0.005 / 1000),
            new Quantity(Double.parseDouble(fields[4]) * 1000, 0.0005 * 1000));
        JsonObject expected = reference.get(rows.size()).getAsJsonObject();
        assertEquals(expected.get("impliedDensityKgPerM3").getAsDouble(), row.getImpliedDensityKgPerM3(), 1e-9);
        assertEquals(expected.get("impliedLowerKgPerM3").getAsDouble(), row.getImpliedLowerKgPerM3(), 1e-9);
        assertEquals(expected.get("impliedUpperKgPerM3").getAsDouble(), row.getImpliedUpperKgPerM3(), 1e-9);
        assertEquals(expected.get("compatible").getAsBoolean(), row.isCompatible());
        compatible += row.isCompatible() ? 1 : 0;
        rows.add(row);
      }
    }
    assertEquals(9, rows.size());
    assertEquals(8, compatible);
    assertEquals(75.27816287269633, rows.get(8).getImpliedDensityKgPerM3(), 1e-9);
    PvtGasDensityConsistency report = report("Duvernay recombined fluid", rows.toArray(new Observation[0]));
    assertFalse(report.isCompatible());
    assertEquals("Incompatible gas-density observations: Psat",
        assertThrows(IllegalStateException.class, report::requireCompatible).getMessage());
  }

  /** The executable guide example attaches compatible evidence; failed and wrong-sample evidence cannot attach. */
  @Test
  void documentedCheckpointHandoffFreezesEvidence() {
    SystemInterface original = new SystemSrkEos(300, 10);
    original.addComponent("methane", 1);
    original.setMixingRule("classic");
    original.init(0);
    PvtGasDensityConsistency evidence = report("synthetic sample", ideal(20, 0));
    CalibratedPvtModel model = new CalibratedPvtModel(original, original.clone(), "synthetic sample", "original v1",
        "tuned v1", "synthetic protocol", new String[] {"CCE-1"}, evidence);
    CalibratedPvtModel restored = SerializationUtils.clone(model);
    JsonObject provenance = JsonParser.parseString(restored.getProvenance()).getAsJsonObject();
    assertEquals(JsonParser.parseString(evidence.toJson()), provenance.get("gasDensityConsistency"));
    assertThrows(IllegalStateException.class, () -> new CalibratedPvtModel(original, original, "synthetic sample", "a",
        "b", "c", new String[] {"CCE-1"}, report("synthetic sample", ideal(21, 0))));
    assertThrows(IllegalArgumentException.class, () -> new CalibratedPvtModel(original, original, "different sample",
        "a", "b", "c", new String[] {"CCE-1"}, evidence));
    assertFalse(JsonParser
        .parseString(
            new CalibratedPvtModel(original, original, "sample", "a", "b", "c", new String[] {"CCE-1"}).getProvenance())
        .getAsJsonObject().has("gasDensityConsistency"));
  }

  /** Reports opt-in arithmetic/report costs; elapsed-time thresholds are deliberately not CI acceptance tests. */
  @Test
  void reportsOptInRuntimeWithoutAddingSimulationWork() {
    for (int i = 0; i < 5000; i++) {
      assertTrue(ideal(20, 0).isCompatible());
    }
    for (int trial = 0; trial < 3; trial++) {
      long start = System.nanoTime();
      int accepted = 0;
      for (int i = 0; i < 10000; i++) {
        accepted += ideal(20, 0).isCompatible() ? 1 : 0;
      }
      double arithmeticNs = (System.nanoTime() - start) / 10000.0;
      PvtGasDensityConsistency evidence = report("synthetic sample", ideal(20, 0));
      start = System.nanoTime();
      int chars = 0;
      for (int i = 0; i < 100; i++) {
        chars += evidence.toJson().length();
      }
      double jsonNs = (System.nanoTime() - start) / 100.0;
      assertEquals(10000, accepted);
      assertTrue(chars > 0);
      logger.info("Gas-density opt-in trial {}: {} ns/tuple, {} ns/report (1 row)", trial, arithmeticNs, jsonNs);
    }
  }
}
