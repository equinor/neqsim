package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Independent slash-record and unit-contract checks against hand-authored OPM-compatible fixtures.
 *
 * <p>
 * Synthetic table pressures encode every axis coordinate. These are format regression tests, not a physical well model
 * or a claim of simulator execution. See optimizer/vfp/README.md for provenance.
 * </p>
 */
class EclipseVFPExporterContractTest {
  private static final double CUBIC_METRES_PER_STB = 0.158987294928;
  private static final double CUBIC_METRES_PER_MSCF = 28.316846592;
  private static final double PASCALS_PER_PSI = 6894.757293168;

  @TempDir
  Path tempDir;

  @Test
  void fullMetricTableMatchesIndependentFixtureAtEveryCoordinate() throws IOException {
    EclipseVFPExporter exporter = fullExporter();
    assertSameTable(parse(fixture("vfpprod_metric.inc")), parse(exporter.getVFPPRODString()));
  }

  @Test
  void fullFieldTableConvertsEveryDimensionalQuantity() throws IOException {
    EclipseVFPExporter exporter = fullExporter();
    exporter.setTableNumber(8);
    exporter.setUnitSystem("FIELD");
    exporter.setDatumDepth(304.8);
    exporter.setFlowRates(new double[] { 100 * CUBIC_METRES_PER_STB, 200 * CUBIC_METRES_PER_STB });
    exporter.setTHPs(new double[] { PASCALS_PER_PSI / 1000, PASCALS_PER_PSI / 500 });
    exporter.setGORs(new double[] { CUBIC_METRES_PER_MSCF / CUBIC_METRES_PER_STB,
        2 * CUBIC_METRES_PER_MSCF / CUBIC_METRES_PER_STB });
    exporter.setALQs(new double[] { 0, 10 * CUBIC_METRES_PER_MSCF });
    exporter.setBHPTable(pressures(2, 2, 2, 2, 2, PASCALS_PER_PSI / 100000));
    assertSameTable(parse(fixture("vfpprod_field.inc")), parse(exporter.getVFPPRODString()));
  }

  @Test
  void fieldGasRatesAndInverseCompositionRatiosUseMscf() {
    EclipseVFPExporter exporter = fullExporter();
    exporter.setUnitSystem("FIELD");
    exporter.setFlowRateType("GAS");
    exporter.setWaterCutType("WGR");
    exporter.setGORType("OGR");
    exporter.setFlowRates(new double[] { 1000, 2000 });
    exporter.setWaterCuts(new double[] { 0.01, 0.02 });
    exporter.setGORs(new double[] { 0.03, 0.06 });
    ParsedTable table = parse(exporter.getVFPPRODString());
    assertNear(35.31466672148859, table.axes[0][0]);
    assertNear(70.62933344297718, table.axes[0][1]);
    assertNear(1.7810760667903526, table.axes[2][0]);
    assertNear(3.5621521335807052, table.axes[2][1]);
    assertNear(5.343228200371057, table.axes[3][0]);
    assertNear(10.686456400742114, table.axes[3][1]);
    assertNear(17.657333360744295, table.axes[4][1]);
    assertNear(145.03773773020923, table.axes[1][0]);
    assertNear(14503.773773020924, table.rows.get(Arrays.asList(1, 1, 1, 1))[0]);
  }

  @Test
  void fieldOilAndLiquidFlowShareStockTankBarrelsAndDimensionlessWaterRatios() {
    for (String flowType : new String[] { "OIL", "LIQ" }) {
      for (String waterType : new String[] { "WCT", "WOR" }) {
        EclipseVFPExporter exporter = fullExporter();
        exporter.setUnitSystem("FIELD");
        exporter.setFlowRateType(flowType);
        exporter.setWaterCutType(waterType);
        exporter.setGORType("GLR");
        ParsedTable table = parse(exporter.getVFPPRODString());
        assertNear(628.9810770432105, table.axes[0][0]);
        assertNear(0.1, table.axes[2][0]);
        assertNear(0.4, table.axes[2][1]);
        assertNear(0.2807291666666667, table.axes[3][0]);
        assertNear(0.5614583333333334, table.axes[3][1]);
      }
    }
  }

  @Test
  void declaredRateAndPressureInputUnitsAreConvertedNumerically() {
    String[] rateUnits = { "Sm3/day", "Sm3/hr", "Sm3/s" };
    double[] rateInputs = { 86400, 3600, 1 };
    String[] pressureUnits = { "bara", "Pa", "psia" };
    double[] pressureInputs = { 10, 1000000, 145.03773773020923 };
    for (int r = 0; r < rateUnits.length; r++) {
      for (int p = 0; p < pressureUnits.length; p++) {
        EclipseVFPExporter exporter = new EclipseVFPExporter();
        exporter.setInputUnits(rateUnits[r], pressureUnits[p]);
        exporter.setFlowRates(new double[] { rateInputs[r] });
        exporter.setTHPs(new double[] { pressureInputs[p] });
        exporter.setBHPTable(new double[][][][][] { { { { { 2 * pressureInputs[p] } } } } });
        ParsedTable table = parse(exporter.getVFPPRODString());
        assertNear(86400, table.axes[0][0]);
        assertNear(10, table.axes[1][0]);
        assertNear(20, table.rows.get(Arrays.asList(1, 1, 1, 1))[0]);
      }
    }
  }

  @Test
  void omittedCompositionAndLiftAxesBecomeNeutralSingletons() {
    ParsedTable table = parse(neutralExporter().getVFPPRODString());
    assertEquals("", table.header.get(6));
    for (int axis = 2; axis < 5; axis++) {
      assertArrayEquals(new double[] { 0 }, table.axes[axis], 0.0);
    }
    assertEquals(2, table.rows.size());
    assertArrayEquals(new double[] { 1100, 1101 }, table.rows.get(Arrays.asList(2, 1, 1, 1)), 1e-8);
  }

  @Test
  void injectionTableMatchesIndependentIndexedFixture() throws IOException {
    EclipseVFPExporter exporter = neutralExporter();
    exporter.setTableNumber(9);
    exporter.setDatumDepth(2500);
    exporter.setFlowRateType("WAT");
    assertSameTable(parse(fixture("vfpinj_metric.inc")), parse(exporter.getVFPINJString()));
  }

  @Test
  void injectionFieldUnitsDistinguishGasFromWater() {
    for (String type : new String[] { "WAT", "GAS" }) {
      EclipseVFPExporter exporter = neutralExporter();
      exporter.setUnitSystem("FIELD");
      exporter.setFlowRateType(type);
      ParsedTable table = parse(exporter.getVFPINJString());
      assertNear("GAS".equals(type) ? 3.531466672148859 : 628.9810770432105, table.axes[0][0]);
      assertNear(145.03773773020923, table.axes[1][0]);
      assertNear(14503.773773020924, table.rows.get(Arrays.asList(1))[0]);
    }
  }

  @Test
  void invalidAxesAreRejectedInsteadOfRoundedSortedOrInvented() {
    List<Consumer<EclipseVFPExporter>> invalid = Arrays.asList(e -> e.setFlowRates(null),
        e -> e.setFlowRates(new double[0]), e -> e.setFlowRates(new double[] { 100, 100 }),
        e -> e.setFlowRates(new double[] { 200, 100 }), e -> e.setFlowRates(new double[] { -1, 100 }),
        e -> e.setFlowRates(new double[] { 100, Double.POSITIVE_INFINITY }), e -> e.setTHPs(null),
        e -> e.setTHPs(new double[] { 0, 20 }), e -> e.setTHPs(new double[] { 20, 10 }),
        e -> e.setTHPs(new double[] { 10, 10 }), e -> e.setTHPs(new double[] { 10, Double.NaN }),
        e -> e.setWaterCuts(new double[] { 0.1, 0.1 }), e -> e.setWaterCuts(new double[] { 0.1, 1.1 }),
        e -> e.setWaterCuts(new double[] { -0.1, 0.4 }), e -> e.setGORs(new double[] { 100, 50 }),
        e -> e.setGORs(new double[] { 50, 50 }), e -> e.setGORs(new double[] { 50, Double.NaN }),
        e -> e.setALQs(new double[] { 0, -1 }), e -> e.setALQs(new double[] { 0, 0 }),
        e -> e.setALQs(new double[] { 0, Double.POSITIVE_INFINITY }));
    for (Consumer<EclipseVFPExporter> mutation : invalid) {
      assertThrows(RuntimeException.class, () -> {
        EclipseVFPExporter exporter = fullExporter();
        mutation.accept(exporter);
        exporter.getVFPPRODString();
      });
    }
  }

  @Test
  void missingRaggedAndDimensionallyInconsistentPressureTablesAreRejected() {
    List<double[][][][][]> invalid = new ArrayList<>();
    invalid.add(null);
    invalid.add(new double[0][][][][]);
    invalid.add(pressures(1, 2, 2, 2, 2, 1));
    invalid.add(pressures(2, 1, 2, 2, 2, 1));
    invalid.add(pressures(2, 2, 1, 2, 2, 1));
    invalid.add(pressures(2, 2, 2, 1, 2, 1));
    invalid.add(pressures(2, 2, 2, 2, 1, 1));
    invalid.add(pressures(2, 2, 2, 2, 3, 1));
    double[][][][][] ragged = pressures(2, 2, 2, 2, 2, 1);
    ragged[1][1][1] = null;
    invalid.add(ragged);
    for (double[][][][][] data : invalid) {
      assertThrows(RuntimeException.class, () -> {
        EclipseVFPExporter exporter = fullExporter();
        exporter.setBHPTable(data);
        exporter.getVFPPRODString();
      });
    }
  }

  @Test
  void infeasibleOrNonfinitePressureCellsNeverBecomeValidBhp() {
    for (double invalid : new double[] { 0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
      assertThrows(RuntimeException.class, () -> {
        EclipseVFPExporter exporter = fullExporter();
        double[][][][][] data = pressures(2, 2, 2, 2, 2, 1);
        data[1][1][1][1][1] = invalid;
        exporter.setBHPTable(data);
        exporter.getVFPPRODString();
      });
    }
  }

  @Test
  void unsupportedMetadataAndInputUnitsFailExplicitly() {
    List<Consumer<EclipseVFPExporter>> invalid = Arrays.asList(e -> e.setTableNumber(0),
        e -> e.setDatumDepth(Double.NaN), e -> e.setUnitSystem("LAB"), e -> e.setFlowRateType("TM"),
        e -> e.setFlowRateType("WAT"), e -> e.setWaterCutType("WWR"), e -> e.setGORType("MMW"),
        e -> e.setALQType("COMP"), e -> e.setInputUnits("kg/hr", "bara"), e -> e.setInputUnits("Sm3/day", "barg"));
    for (Consumer<EclipseVFPExporter> mutation : invalid) {
      assertThrows(RuntimeException.class, () -> {
        EclipseVFPExporter exporter = fullExporter();
        mutation.accept(exporter);
        exporter.getVFPPRODString();
      });
    }
  }

  @Test
  void undeclaredLiftQuantityAndNonneutralInjectionDimensionsAreRejected() {
    assertThrows(RuntimeException.class, () -> {
      EclipseVFPExporter exporter = fullExporter();
      exporter.setALQType("");
      exporter.getVFPPRODString();
    });
    assertThrows(RuntimeException.class, () -> fullExporter().getVFPINJString());
    assertThrows(RuntimeException.class, () -> {
      EclipseVFPExporter exporter = neutralExporter();
      exporter.setWaterCuts(new double[] { 0.5 });
      exporter.getVFPINJString();
    });
  }

  @Test
  void invalidExportsPreserveExistingFilesAndDoNotCreateNewFiles() throws IOException {
    Path existing = tempDir.resolve("existing.inc");
    Path absent = tempDir.resolve("absent.inc");
    byte[] original = "preserve this existing deck\n".getBytes(StandardCharsets.UTF_8);
    Files.write(existing, original);
    EclipseVFPExporter exporter = fullExporter();
    exporter.setBHPTable(null);
    assertThrows(RuntimeException.class, () -> exporter.exportVFPPROD(existing.toString()));
    assertThrows(RuntimeException.class, () -> exporter.exportVFPPROD(absent.toString()));
    assertArrayEquals(original, Files.readAllBytes(existing));
    assertFalse(Files.exists(absent));
    assertThrows(RuntimeException.class, () -> fullExporter().exportVFPINJ(existing.toString()));
    assertArrayEquals(original, Files.readAllBytes(existing));
  }

  @Test
  void unsupportedFacilityAndCapacityMappingsFailBeforeWriting() throws IOException {
    Path file = tempDir.resolve("facility.inc");
    byte[] original = "existing facility data\n".getBytes(StandardCharsets.UTF_8);
    Files.write(file, original);
    EclipseVFPExporter exporter = fullExporter();
    assertThrows(UnsupportedOperationException.class, () -> exporter.exportVFPEXP(file.toString()));
    assertArrayEquals(original, Files.readAllBytes(file));
    Path absent = tempDir.resolve("unsupported.inc");
    assertThrows(UnsupportedOperationException.class, () -> exporter.exportVFPEXP(absent.toString()));
    assertFalse(Files.exists(absent));
    assertThrows(UnsupportedOperationException.class,
        () -> exporter.setLiftCurveData(new ProcessOptimizationEngine.LiftCurveData()));
  }

  @Test
  void decimalFormattingIsIndependentOfDefaultLocale() throws IOException {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.GERMANY);
      assertSameTable(parse(fixture("vfpprod_metric.inc")), parse(fullExporter().getVFPPRODString()));
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  void distinctCloseAxisValuesRemainDistinctInTheDeck() {
    EclipseVFPExporter exporter = fullExporter();
    exporter.setFlowRates(new double[] { 1.0000000001, 1.0000000002 });
    exporter.setTHPs(new double[] { 10.0000000001, 10.0000000002 });
    ParsedTable table = parse(exporter.getVFPPRODString());
    assertTrue(table.axes[0][1] > table.axes[0][0]);
    assertTrue(table.axes[1][1] > table.axes[1][0]);
  }

  @Test
  void independentReaderRejectsTruncatedDuplicateAndOutOfRangeRecords() throws IOException {
    String valid = fixture("vfpprod_metric.inc");
    assertThrows(IllegalArgumentException.class, () -> parse(valid.replace("2 2 2 2 1116 1117 /", "")));
    assertThrows(IllegalArgumentException.class,
        () -> parse(valid.replace("2 2 2 2 1116 1117 /", "1 1 1 1 1116 1117 /")));
    assertThrows(IllegalArgumentException.class,
        () -> parse(valid.replace("2 2 2 2 1116 1117 /", "2 2 2 3 1116 1117 /")));
    assertThrows(IllegalArgumentException.class, () -> parse(valid.replace("2 2 2 2 1116 1117 /", "2 2 2 2 1116 /")));
    assertThrows(IllegalArgumentException.class, () -> parse(valid.replace("'GRAT'", "'BHP'")));
    assertThrows(IllegalArgumentException.class, () -> parse(valid.replace("1117 /", "NaN /")));
  }

  private static EclipseVFPExporter fullExporter() {
    EclipseVFPExporter exporter = new EclipseVFPExporter(7);
    exporter.setDatumDepth(2500);
    exporter.setFlowRateType("LIQ");
    exporter.setWaterCutType("WCT");
    exporter.setGORType("GOR");
    exporter.setALQType("GRAT");
    exporter.setFlowRates(new double[] { 100, 200 });
    exporter.setTHPs(new double[] { 10, 20 });
    exporter.setWaterCuts(new double[] { 0.1, 0.4 });
    exporter.setGORs(new double[] { 50, 100 });
    exporter.setALQs(new double[] { 0, 500 });
    exporter.setBHPTable(pressures(2, 2, 2, 2, 2, 1));
    return exporter;
  }

  private static EclipseVFPExporter neutralExporter() {
    EclipseVFPExporter exporter = new EclipseVFPExporter();
    exporter.setFlowRates(new double[] { 100, 200 });
    exporter.setTHPs(new double[] { 10, 20 });
    exporter.setBHPTable(pressures(2, 2, 1, 1, 1, 1));
    return exporter;
  }

  private static double[][][][][] pressures(int nf, int nt, int nw, int ng, int na, double unitScale) {
    double[][][][][] data = new double[nf][nt][nw][ng][na];
    for (int f = 0; f < nf; f++) {
      for (int t = 0; t < nt; t++) {
        for (int w = 0; w < nw; w++) {
          for (int g = 0; g < ng; g++) {
            for (int a = 0; a < na; a++) {
              data[f][t][w][g][a] = (1000 + 100 * t + 10 * w + 4 * g + 2 * a + f) * unitScale;
            }
          }
        }
      }
    }
    return data;
  }

  private static String fixture(String name) throws IOException {
    try (InputStream input = EclipseVFPExporterContractTest.class.getResourceAsStream("/optimizer/vfp/" + name)) {
      assertNotNull(input, "Missing independent fixture " + name);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int count;
      while ((count = input.read(buffer)) != -1) {
        output.write(buffer, 0, count);
      }
      return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
  }

  private static void assertNear(double expected, double actual) {
    assertEquals(expected, actual, Math.max(1, Math.abs(expected)) * 1e-10);
  }

  private static void assertSameTable(ParsedTable expected, ParsedTable actual) {
    assertEquals(expected.keyword, actual.keyword);
    assertEquals(expected.header.size(), actual.header.size());
    for (int i = 0; i < expected.header.size(); i++) {
      if (i < 2) {
        assertNear(Double.parseDouble(expected.header.get(i)), Double.parseDouble(actual.header.get(i)));
      } else {
        assertEquals(expected.header.get(i), actual.header.get(i));
      }
    }
    assertEquals(expected.axes.length, actual.axes.length);
    for (int i = 0; i < expected.axes.length; i++) {
      assertArrayEquals(expected.axes[i], actual.axes[i], 1e-8);
    }
    assertEquals(expected.rows.keySet(), actual.rows.keySet());
    for (List<Integer> coordinate : expected.rows.keySet()) {
      assertArrayEquals(expected.rows.get(coordinate), actual.rows.get(coordinate), 1e-8);
    }
  }

  /** Independent reader for the explicit, uncompressed VFP subset emitted by NeqSim. */
  private static ParsedTable parse(String text) {
    StringBuilder uncommented = new StringBuilder();
    for (String line : text.split("\\r?\\n")) {
      int comment = line.indexOf("--");
      uncommented.append(comment < 0 ? line : line.substring(0, comment)).append('\n');
    }
    Matcher matcher = Pattern.compile("'([^']*)'|([^\\s/]+)|(/)").matcher(uncommented);
    List<List<String>> records = new ArrayList<>();
    List<String> record = new ArrayList<>();
    while (matcher.find()) {
      if (matcher.group(3) != null) {
        require(!record.isEmpty(), "Unexpected empty record");
        records.add(record);
        record = new ArrayList<>();
      } else {
        record.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
      }
    }
    require(record.isEmpty(), "Missing record terminator");
    require(!records.isEmpty() && !records.get(0).isEmpty(), "Missing keyword");
    String keyword = records.get(0).remove(0);
    boolean production = "VFPPROD".equals(keyword);
    require(production || "VFPINJ".equals(keyword), "Unsupported keyword");
    List<String> header = records.get(0);
    require(header.size() == (production ? 9 : 6), "Incorrect header field count");
    require(Integer.parseInt(header.get(0)) > 0, "Invalid table number");
    require(Double.isFinite(Double.parseDouble(header.get(1))), "Invalid datum depth");
    if (production) {
      require(Arrays.asList("OIL", "LIQ", "GAS").contains(header.get(2)), "Invalid flow type");
      require(Arrays.asList("WOR", "WCT", "WGR").contains(header.get(3)), "Invalid water definition");
      require(Arrays.asList("GOR", "GLR", "OGR").contains(header.get(4)), "Invalid gas definition");
      require("THP".equals(header.get(5)), "Invalid pressure definition");
      if ("1*".equals(header.get(6))) {
        header.set(6, "");
      }
      require(Arrays.asList("", "GRAT").contains(header.get(6)), "Invalid lift definition");
    } else {
      require(Arrays.asList("OIL", "WAT", "GAS").contains(header.get(2)), "Invalid injection flow type");
      require("THP".equals(header.get(3)), "Invalid pressure definition");
    }
    require(Arrays.asList("METRIC", "FIELD").contains(header.get(production ? 7 : 4)), "Invalid units");
    require("BHP".equals(header.get(production ? 8 : 5)), "Body is not BHP");
    int axisCount = production ? 5 : 2;
    require(records.size() > axisCount, "Missing axis records");
    double[][] axes = new double[axisCount][];
    int rowCount = 1;
    for (int axis = 0; axis < axisCount; axis++) {
      axes[axis] = numbers(records.get(axis + 1), 0);
      require(axes[axis].length > 0, "Empty axis");
      for (int i = 0; i < axes[axis].length; i++) {
        require(axes[axis][i] >= 0, "Negative axis value");
        require(axis != 1 || axes[axis][i] > 0, "Nonpositive THP");
        require(i == 0 || axes[axis][i] > axes[axis][i - 1], "Axis not strictly increasing");
      }
      if (axis > 0) {
        rowCount *= axes[axis].length;
      }
    }
    require(records.size() == 1 + axisCount + rowCount, "Incomplete table");
    Map<List<Integer>, double[]> rows = new LinkedHashMap<>();
    for (int i = axisCount + 1; i < records.size(); i++) {
      List<String> values = records.get(i);
      int indices = production ? 4 : 1;
      require(values.size() == indices + axes[0].length, "Incorrect pressure row width");
      List<Integer> coordinate = new ArrayList<>();
      for (int axis = 0; axis < indices; axis++) {
        int index = Integer.parseInt(values.get(axis));
        require(index >= 1 && index <= axes[axis + 1].length, "Index out of range");
        coordinate.add(index);
      }
      double[] pressures = numbers(values, indices);
      for (double pressure : pressures) {
        require(pressure > 0, "Invalid BHP");
      }
      require(!rows.containsKey(coordinate), "Duplicate coordinate");
      rows.put(coordinate, pressures);
    }
    return new ParsedTable(keyword, header, axes, rows);
  }

  private static double[] numbers(List<String> tokens, int start) {
    double[] values = new double[tokens.size() - start];
    for (int i = start; i < tokens.size(); i++) {
      values[i - start] = Double.parseDouble(tokens.get(i));
      require(Double.isFinite(values[i - start]), "Nonfinite value");
    }
    return values;
  }

  private static void require(boolean valid, String message) {
    if (!valid) {
      throw new IllegalArgumentException(message);
    }
  }

  private static final class ParsedTable {
    final String keyword;
    final List<String> header;
    final double[][] axes;
    final Map<List<Integer>, double[]> rows;

    ParsedTable(String keyword, List<String> header, double[][] axes, Map<List<Integer>, double[]> rows) {
      this.keyword = keyword;
      this.header = header;
      this.axes = axes;
      this.rows = rows;
    }
  }
}
