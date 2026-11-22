package neqsim.pvtsimulation.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.util.CvdMaterialBalance.FinalLiquidAnalysis;
import neqsim.pvtsimulation.util.CvdMaterialBalance.LiquidState;
import neqsim.pvtsimulation.util.CvdMaterialBalance.Observation;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Published primary numerical accounting, analytical limits and immutable evidence handoff qualification.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class CvdMaterialBalanceTest {
  private static final Logger logger = LogManager.getLogger(CvdMaterialBalanceTest.class);
  private static final double PSI_PA = 6894.757293168;

  /**
   * Builds an independently hand-calculated two-component, two-withdrawal experiment.
   *
   * @param analysis optional final analysis
   * @return immutable complete accounting
   */
  private CvdMaterialBalance analytic(FinalLiquidAnalysis analysis) {
    return new CvdMaterialBalance("sample-v1", "synthetic CVD complete experiment", "public analytic inputs", 350,
        new String[] {"A", "B"},
        new Observation[] {new Observation("sat", 100e5, 1, 0, 0, new double[] {0.8, 0.2}),
            new Observation("step-1", 50e5, 1, 0.2, 0.2, new double[] {0.9, 0.1}),
            new Observation("step-2", 25e5, 1, 0.2, 0.4, new double[] {0.9, 0.1})},
        1e-12, 1e-8, analysis);
  }

  /**
   * Builds a single-component limit with one depletion row.
   *
   * @param liquidVolume liquid volume fraction
   * @param produced cumulative produced fraction
   * @return accounting report
   */
  private CvdMaterialBalance pure(double liquidVolume, double produced) {
    return new CvdMaterialBalance("sample", "pure analytic", "synthetic", 350, new String[] {"A"},
        new Observation[] {new Observation("sat", 100e5, 1, 0, 0, new double[] {1}),
            new Observation("step", 50e5, 1, liquidVolume, produced, new double[] {1})},
        1e-12, 1e-8, null);
  }

  /** Named withdrawals close against independently hand-computed retained/vapor/liquid inventories. */
  @Test
  void analyticBinaryConservesEveryComponentAcrossTheWholePath() {
    CvdMaterialBalance result = analytic(null);
    assertTrue(result.isCompatible());
    assertEquals(3, result.getObservationCount());
    assertArrayEquals(new String[] {"A", "B"}, result.getComponentNames());
    assertEquals(LiquidState.ABSENT, result.getStage(0).getLiquidState());
    assertThrows(IllegalStateException.class, () -> result.getStage(0).getLiquidMoleFractions());
    for (int k = 1; k <= 2; k++) {
      assertEquals(0.4, result.getStage(k).getLiquidMoles(), 1e-15);
      assertArrayEquals(new double[] {0.26, 0.14}, result.getStage(k).getLiquidComponentMoles(), 1e-15);
      assertArrayEquals(new double[] {0.65, 0.35}, result.getStage(k).getLiquidMoleFractions(), 1e-15);
      assertEquals(0.2, result.getStage(k).getStageWithdrawnMoles(), 1e-15);
      assertEquals(1 - 0.2 * k, result.getStage(k).getTotalRemainingMoles(), 1e-15);
      assertEquals(0.6 - 0.2 * k, result.getStage(k).getVaporMoles(), 1e-15);
      assertArrayEquals(new double[] {0.18 * k, 0.02 * k}, result.getStage(k).getProducedComponentMoles(), 1e-15);
      assertArrayEquals(new double[] {0.8 - 0.18 * k, 0.2 - 0.02 * k}, result.getStage(k).getRemainingComponentMoles(),
          1e-15);
    }
    JsonArray rows = JsonParser.parseString(result.toJson()).getAsJsonObject().getAsJsonArray("rows");
    for (int k = 0; k < rows.size(); k++) {
      JsonObject row = rows.get(k).getAsJsonObject();
      assertEquals(0, row.get("componentSumMinusLiquidMoles").getAsDouble(), 1e-15);
      for (int j = 0; j < 2; j++) {
        double initial = j == 0 ? 0.8 : 0.2;
        assertEquals(initial, row.getAsJsonArray("producedComponentMoles").get(j).getAsDouble()
            + row.getAsJsonArray("remainingComponentMoles").get(j).getAsDouble(), 1e-15);
      }
    }
    assertEquals(0.36, rows.get(2).getAsJsonObject().getAsJsonArray("producedComponentMoles").get(0).getAsDouble(),
        1e-15);
  }

  /** Changed vapor composition, component ordering and pressure scale preserve independent rational inventories. */
  @Test
  void ternaryInventoryReferenceSurvivesOrderAndPressureScaling() {
    int[][] orders = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
    double[][] composition = {{0.48, 0.33, 0.19}, {0.6, 0.3, 0.1}, {0.65, 0.3, 0.05}};
    // Independent prescribed accounting: initial = .2*y1 + .1*y2 + .3*y2 + .4*x2.
    double[] expectedFinalLiquid = {0.25, 0.375, 0.375};
    for (int[] order : orders) {
      for (double scale : new double[] {0.01, 1, 100}) {
        String[] names = new String[3];
        double[][] y = new double[3][3];
        for (int j = 0; j < 3; j++) {
          names[j] = "component-" + order[j];
          for (int k = 0; k < 3; k++) {
            y[k][j] = composition[k][order[j]];
          }
        }
        CvdMaterialBalance report = new CvdMaterialBalance("sample", "rational ternary path", "analytic", 350, names,
            new Observation[] {new Observation("sat", scale * 100e5, 1, 0, 0, y[0]),
                new Observation("step-1", scale * 100e5 * 5 / 9, 1, 0.1, 0.2, y[1]),
                new Observation("step-2", scale * 100e5 * 3 / 8, 1, 0.2, 0.3, y[2])},
            1e-12, 1e-8, null);
        assertTrue(report.isCompatible());
        assertEquals(0.4, report.getStage(2).getLiquidMoles(), 1e-15);
        double[] actual = report.getStage(2).getLiquidMoleFractions();
        for (int j = 0; j < 3; j++) {
          assertEquals(expectedFinalLiquid[order[j]], actual[j], 1e-15);
        }
      }
    }
  }

  /** Absent and numerically unresolved liquid never supplies an invented composition. */
  @Test
  void absentAndVanishingLiquidHaveExplicitLimits() {
    CvdMaterialBalance gasOnly = pure(0, 0.5);
    assertTrue(gasOnly.isCompatible());
    assertEquals(LiquidState.ABSENT, gasOnly.getStage(1).getLiquidState());
    assertThrows(IllegalStateException.class, () -> gasOnly.getStage(1).getLiquidMoleFractions());
    assertTrue(JsonParser.parseString(gasOnly.toJson()).getAsJsonObject().getAsJsonArray("rows").get(1)
        .getAsJsonObject().get("liquidMoleFractions").isJsonNull());
    CvdMaterialBalance vanishing = pure(0.2, 0.6 - 1e-10);
    assertFalse(vanishing.isCompatible());
    assertEquals(LiquidState.UNRESOLVED, vanishing.getStage(1).getLiquidState());
    assertThrows(IllegalStateException.class, () -> vanishing.getStage(1).getLiquidMoleFractions());
    assertEquals(LiquidState.RESOLVED, pure(0.2, 0.6 - 1e-6).getStage(1).getLiquidState());
    assertEquals(LiquidState.INCONSISTENT, pure(0, 0.2).getStage(1).getLiquidState());
    assertEquals(LiquidState.INCONSISTENT, pure(0, 0.6).getStage(1).getLiquidState());
  }

  /** A positive total liquid amount can still hide a negative component; retain the signed diagnostic. */
  @Test
  void negativeComponentsCannotPassByTotalClosureOrClipping() {
    CvdMaterialBalance result = new CvdMaterialBalance("sample", "bad composition", "synthetic", 350,
        new String[] {"A", "B"}, new Observation[] {new Observation("sat", 100e5, 1, 0, 0, new double[] {0.2, 0.8}),
            new Observation("step", 50e5, 1, 0.2, 0.2, new double[] {0.9, 0.1})},
        1e-12, 1e-8, null);
    assertFalse(result.isCompatible());
    assertEquals(0.4, result.getStage(1).getLiquidMoles(), 1e-15);
    assertEquals(-0.34, result.getStage(1).getLiquidComponentMoles()[0], 1e-15);
    assertEquals(LiquidState.INCONSISTENT, result.getStage(1).getLiquidState());
    assertThrows(IllegalStateException.class, result::requireCompatible);
    assertThrows(IllegalStateException.class, () -> result.getStage(1).getLiquidMoleFractions());
    CvdMaterialBalance trace = new CvdMaterialBalance("sample", "negative trace", "synthetic", 350,
        new String[] {"A", "B"}, new Observation[] {new Observation("sat", 100e5, 1, 0, 0, new double[] {0, 1}),
            new Observation("step", 50e5, 1, 0.2, 0.2, new double[] {1e-13, 1 - 1e-13})},
        1e-12, 1e-8, null);
    assertFalse(trace.isCompatible());
    assertEquals(LiquidState.INCONSISTENT, trace.getStage(1).getLiquidState());
    assertTrue(trace.getStage(1).getLiquidComponentMoles()[0] < 0);
    assertThrows(IllegalStateException.class, () -> trace.getStage(1).getLiquidMoleFractions());
  }

  /** Non-unit columns can cancel the liquid residual; independent produced/retained/vapor total checks must fail. */
  @Test
  void compositionBiasCannotCancelIntoPassingTotalAccounting() {
    double[] y = {0.5, 0.5 + 5e-11};
    CvdMaterialBalance result = new CvdMaterialBalance("sample", "cancellation limit", "analytic", 350,
        new String[] {"A", "B"},
        new Observation[] {new Observation("sat", 100, 1, 0, 0, y), new Observation("step", 50, 1, 0, 0.5, y)}, 1e-12,
        1e-8, null);
    assertFalse(result.isCompatible());
    JsonObject row = JsonParser.parseString(result.toJson()).getAsJsonObject().getAsJsonArray("rows").get(1)
        .getAsJsonObject();
    assertEquals(0, row.get("componentSumMinusLiquidMoles").getAsDouble(), 1e-15);
    for (String key : new String[] {"remainingComponentSumMinusTotalMoles", "producedComponentSumMinusWithdrawnMoles",
        "vaporComponentSumMinusVaporMoles"}) {
      assertTrue(Math.abs(row.get(key).getAsDouble()) > 1e-12);
    }
    assertEquals(y[1], row.getAsJsonArray("vaporMoleFractions").get(1).getAsDouble(), 0);
    assertThrows(IllegalStateException.class, result::requireCompatible);
  }

  /** Constructor, serialization, primitive-array access and JSON export preserve immutable experiment evidence. */
  @Test
  void inputsOutputsAndSerializationAreIsolated() {
    String[] names = {"A"};
    double[] y = {1};
    Observation[] observations = {new Observation("sat", 100, 1, 0, 0, y), new Observation("step", 50, 1, 0.2, 0.2, y)};
    CvdMaterialBalance report = new CvdMaterialBalance("sample", "experiment", "synthetic", 350, names, observations,
        1e-12, 1e-8, new FinalLiquidAnalysis("analysis", "analytic absolute bound", y, new double[] {0}));
    String before = report.toJson();
    names[0] = "changed";
    y[0] = 0;
    observations[1] = null;
    report.getStage(1).getLiquidComponentMoles()[0] = -9;
    report.getStage(1).getLiquidMoleFractions()[0] = -9;
    report.getComponentNames()[0] = "changed";
    report.getStage(1).getProducedComponentMoles()[0] = -9;
    report.getStage(1).getRemainingComponentMoles()[0] = -9;
    assertEquals(before, report.toJson());
    assertEquals(before, SerializationUtils.clone(report).toJson());
  }

  /** Bad bases, row order, dimensions and fractions cannot silently become a different experiment. */
  @Test
  void rejectsMalformedOrUnsupportedProtocols() {
    for (double invalid : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> new Observation("bad", invalid, 1, 0, 0, new double[] {1}));
      assertThrows(IllegalArgumentException.class, () -> new Observation("bad", 100, invalid, 0, 0, new double[] {1}));
    }
    for (double invalid : new double[] {-0.1, 1, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> new Observation("bad", 100, 1, invalid, 0, new double[] {1}));
      assertThrows(IllegalArgumentException.class, () -> new Observation("bad", 100, 1, 0, invalid, new double[] {1}));
    }
    assertThrows(IllegalArgumentException.class, () -> new Observation("bad", 100, 1, 0, 0, new double[] {0.7, 0.2}));
    assertThrows(IllegalArgumentException.class,
        () -> new FinalLiquidAnalysis("x", "y", new double[] {1}, new double[] {-1}));
    Observation reference = new Observation("sat", 100, 1, 0, 0, new double[] {1});
    Observation step = new Observation("step", 50, 1, 0.2, 0.2, new double[] {1});
    for (Observation[] bad : new Observation[][] {{reference}, {reference, null}, {step, reference},
        {reference, reference}, {step, new Observation("other", 25, 1, 0.2, 0.1, new double[] {1})},
        {reference, new Observation("step", 50, 1, 0.2, 0.2, new double[] {0.5, 0.5})}}) {
      assertThrows(IllegalArgumentException.class,
          () -> new CvdMaterialBalance("sample", "expt", "source", 350, new String[] {"A"}, bad, 1e-12, 1e-8, null));
    }
    assertThrows(IllegalArgumentException.class, () -> new CvdMaterialBalance("sample", "expt", "source", 350,
        new String[] {"A"}, new Observation[] {reference, step}, 1e-8, 1e-8, null));
    assertThrows(IllegalArgumentException.class,
        () -> new CvdMaterialBalance("sample", "expt", "source", 350, new String[] {"A"},
            new Observation[] {new Observation("sat", 100, Double.MAX_VALUE, 0, 0, new double[] {1}),
                new Observation("step", 50, Double.MIN_VALUE, 0.2, 0.2, new double[] {1})},
            1e-12, 1e-8, null));
  }

  /**
   * Opens a local attributed resource; numerical qualification never requires a remote source at test time.
   *
   * @param name file name
   * @return UTF-8 reader
   */
  private BufferedReader resource(String name) {
    return new BufferedReader(new InputStreamReader(
        getClass().getResourceAsStream("/neqsim/pvtsimulation/cvd-material-balance/" + name), StandardCharsets.UTF_8));
  }

  /**
   * Builds the complete primary-source NS-1 experiment with optional final experimental analysis.
   *
   * @param includeAnalysis whether to include the separate experimental final liquid and rounding-only bounds
   * @return immutable experiment
   * @throws Exception if resources cannot be read
   */
  private CvdMaterialBalance published(boolean includeAnalysis) throws Exception {
    List<Observation> observations = new ArrayList<Observation>();
    String[] names;
    try (BufferedReader reader = resource("ns1-table1.csv")) {
      String[] header = reader.readLine().split(",");
      names = Arrays.copyOfRange(header, 5, header.length);
      String line;
      while ((line = reader.readLine()) != null) {
        String[] fields = line.split(",");
        double[] y = new double[names.length];
        for (int j = 0; j < y.length; j++) {
          y[j] = Double.parseDouble(fields[j + 5]) / 100;
        }
        observations
            .add(new Observation(fields[0], Double.parseDouble(fields[1]) * PSI_PA, Double.parseDouble(fields[2]),
                Double.parseDouble(fields[4]) / 100, Double.parseDouble(fields[3]) / 100, y));
      }
    }
    FinalLiquidAnalysis analysis = null;
    if (includeAnalysis) {
      JsonArray expected;
      try (BufferedReader reader = resource("reference-results.json")) {
        expected = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("rows").get(5).getAsJsonObject()
            .getAsJsonArray("components");
      }
      double[] measured = new double[names.length];
      double[] bounds = new double[names.length];
      try (BufferedReader reader = resource("ns1-final-liquid.csv")) {
        reader.readLine();
        for (int j = 0; j < names.length; j++) {
          String[] source = reader.readLine().split(",");
          JsonObject component = expected.get(j).getAsJsonObject();
          assertEquals(names[j], source[0]);
          assertEquals(0, new BigDecimal(source[1]).movePointLeft(2)
              .compareTo(new BigDecimal(component.get("publishedCalculated").getAsString())));
          assertEquals(0, new BigDecimal(source[2]).movePointLeft(2)
              .compareTo(new BigDecimal(component.get("experimental").getAsString())));
          double nominal = component.get("liquidMoleFraction").getAsDouble();
          JsonArray bin = component.getAsJsonArray("printedRoundingInterval");
          measured[j] = Double.parseDouble(source[2]) / 100;
          bounds[j] = Math.max(nominal - bin.get(0).getAsDouble(), bin.get(1).getAsDouble() - nominal) + 0.00005;
        }
        assertTrue(reader.readLine() == null, "Complete final analysis, without ignored source rows");
      }
      analysis = new FinalLiquidAnalysis("Whitson-Torp Table1 experimental final liquid at 714.7 psia",
          "Conservative independent printed-rounding box envelope plus experimental half-digit; NOT measurement sigma",
          measured, bounds);
    }
    return new CvdMaterialBalance("NS-1 published recombined separator sample", "Whitson-Torp 1983 Table1 p614",
        "DOI 10.2118/10067-PA; public author PDF SHA256 99c7245e552e1b57389294c78f9433f48d1e71545db1aca15e8cb04acc2f1b91; smoothed/adjusted vapor analyses; numerical facts transcribed",
        (280.0 - 32) * 5 / 9 + 273.15, names, observations.toArray(new Observation[0]), 1e-12, 1e-8, analysis);
  }

  /**
   * All 66 liquid compositions and 198 named inventories agree with independent 50-digit reference arithmetic.
   *
   * @throws Exception if the local primary/reference fixture cannot be read
   */
  @Test
  void primaryTableReproducesPublishedCalculationButRetainsExperimentalDiscrepancy() throws Exception {
    CvdMaterialBalance report = published(false);
    assertTrue(report.isCompatible());
    JsonArray expected;
    try (BufferedReader reader = resource("reference-results.json")) {
      expected = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("rows");
    }
    JsonArray actual = JsonParser.parseString(report.toJson()).getAsJsonObject().getAsJsonArray("rows");
    double maxPublishedError = 0;
    for (int k = 1; k <= 6; k++) {
      JsonObject row = expected.get(k - 1).getAsJsonObject();
      JsonObject calculated = actual.get(k).getAsJsonObject();
      assertEquals(row.get("liquidMoles").getAsDouble(), report.getStage(k).getLiquidMoles(), 2e-15);
      double[] x = report.getStage(k).getLiquidMoleFractions();
      for (int j = 0; j < 11; j++) {
        JsonObject component = row.getAsJsonArray("components").get(j).getAsJsonObject();
        assertEquals(component.get("liquidMoleFraction").getAsDouble(), x[j], 2e-14);
        for (String[] key : new String[][] {{"producedMoles", "producedComponentMoles"},
            {"remainingMoles", "remainingComponentMoles"}, {"liquidMoles", "liquidComponentMoles"}}) {
          assertEquals(component.get(key[0]).getAsDouble(), calculated.getAsJsonArray(key[1]).get(j).getAsDouble(),
              2e-15);
        }
        if (k == 6) {
          double error = Math.abs(component.get("publishedCalculated").getAsDouble() - x[j]);
          assertTrue(error <= 0.000005, "Frozen half-last-digit published calculation tolerance");
          maxPublishedError = Math.max(maxPublishedError, error);
        }
      }
    }
    CvdMaterialBalance measured = published(true);
    assertFalse(measured.isCompatible());
    assertThrows(IllegalStateException.class, measured::requireCompatible);
    JsonObject comparison = JsonParser.parseString(measured.toJson()).getAsJsonObject()
        .getAsJsonObject("finalLiquidAnalysis");
    JsonArray residuals = comparison.getAsJsonArray("measuredMinusReconstructed");
    JsonArray bounds = comparison.getAsJsonArray("absoluteMoleFractionBounds");
    List<Integer> failed = new ArrayList<Integer>();
    for (int j = 0; j < 11; j++) {
      if (Math.abs(residuals.get(j).getAsDouble()) > bounds.get(j).getAsDouble()) {
        failed.add(j);
      }
    }
    assertEquals(Arrays.asList(2, 10), failed);
    logger.info(
        "NS-1: 66 liquid compositions/198 component inventories; max published calculation error {} mol%; experimental CH4 residual {} mol%, C7+ {} mol%",
        100 * maxPublishedError, 100 * residuals.get(2).getAsDouble(), 100 * residuals.get(10).getAsDouble());
  }

  /** The complete documented example attaches immutable evidence; failed/wrong-sample reports cannot attach. */
  @Test
  void documentedCheckpointExamplePreservesBothModelsAndEvidence() {
    CvdMaterialBalance evidence = analytic(new FinalLiquidAnalysis("independent analytic final liquid",
        "exact rational reference with 1e-12 absolute fraction numerical bound", new double[] {0.65, 0.35},
        new double[] {1e-12, 1e-12}));
    SystemInterface original = new SystemSrkEos(350, 100);
    original.addComponent("methane", 0.8);
    original.addComponent("n-heptane", 0.2);
    original.setMixingRule("classic");
    original.init(0);
    CalibratedPvtModel model = new CalibratedPvtModel(original, original.clone(), "sample-v1", "original-v1",
        "tuned-v1", "synthetic laboratory protocol", new String[] {"independent-calibration-CCE"}, null, evidence);
    CalibratedPvtModel restored = SerializationUtils.clone(model);
    assertEquals(JsonParser.parseString(evidence.toJson()),
        JsonParser.parseString(restored.getProvenance()).getAsJsonObject().get("cvdMaterialBalance"));
    assertThrows(IllegalArgumentException.class, () -> new CalibratedPvtModel(original, original, "wrong-sample", "a",
        "b", "c", new String[] {"cal"}, null, evidence));
    CvdMaterialBalance failed = analytic(new FinalLiquidAnalysis("bad analysis", "frozen exact comparison",
        new double[] {0.7, 0.3}, new double[] {1e-12, 1e-12}));
    assertThrows(IllegalStateException.class, () -> new CalibratedPvtModel(original, original, "sample-v1", "a", "b",
        "c", new String[] {"cal"}, null, failed));
    assertFalse(JsonParser.parseString(
        new CalibratedPvtModel(original, original, "sample-v1", "a", "b", "c", new String[] {"cal"}).getProvenance())
        .getAsJsonObject().has("cvdMaterialBalance"));
  }

  /** Measures only opt-in accounting and JSON costs; elapsed-time thresholds are not CI assertions. */
  @Test
  void reportsWarmOptInRuntime() {
    for (int i = 0; i < 3000; i++) {
      assertTrue(analytic(null).isCompatible());
    }
    for (int trial = 0; trial < 3; trial++) {
      long start = System.nanoTime();
      int accepted = 0;
      for (int i = 0; i < 10000; i++) {
        accepted += analytic(null).isCompatible() ? 1 : 0;
      }
      double ns = (System.nanoTime() - start) / 10000.0;
      CvdMaterialBalance report = analytic(null);
      start = System.nanoTime();
      int chars = 0;
      for (int i = 0; i < 100; i++) {
        chars += report.toJson().length();
      }
      assertEquals(10000, accepted);
      assertTrue(chars > 0);
      logger.info("CVD accounting timing trial {}: construction/check {} ns/op, JSON {} ns/op", trial, ns,
          (System.nanoTime() - start) / 100.0);
    }
  }
}
