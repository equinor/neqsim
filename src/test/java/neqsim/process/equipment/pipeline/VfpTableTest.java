package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.util.optimizer.EclipseVFPExporter;

/**
 * Hand-calculated interpolation, inversion and deck-contract regressions.
 *
 * @author ESOL
 * @version 1.0
 */
class VfpTableTest {
  @TempDir
  Path directory;

  /** @return a hand-checkable GAS/WGR/GOR deck, Sm3/day and bara */
  static String deck() {
    return "VFPPROD\n1 0 'GAS' 'WGR' 'GOR' 'THP' '' 'METRIC' 'BHP' /\n"
        + "1000000 2000000 /\n20 40 /\n0 /\n100 /\n0 /\n" + "1 1 1 1 60 80 /\n2 1 1 1 100 140 /\n";
  }

  /**
   * Tests the average of all four corners, plus file loading and defensive axes.
   *
   * @throws IOException if the temporary table cannot be written or read
   */
  @Test
  void handCalculatedMidpointAndFileLoading() throws IOException {
    VfpTable table = VfpTable.fromString(deck());
    assertEquals(95, table.inletPressure(1500000, 30, 0, 100, 0), 1e-9);
    assertEquals("GAS", table.getFlowRateType());
    assertEquals("GOR", table.getGasRatioType());
    assertArrayEquals(new double[] {1, 2}, table.getRateAxis("MSm3/day"));
    double[] axis = table.getThpAxis("bara");
    axis[0] = -1;
    assertEquals(20, table.getThpAxis("bara")[0]);
    Path path = directory.resolve("pipeline.inc");
    Files.write(path, deck().getBytes(StandardCharsets.UTF_8));
    assertEquals(95, VfpTable.fromFile(path.toString()).inletPressure(1500000, 30, 0, 100, 0), 1e-9);
  }

  /** Tests inversion at boundaries and interior points without iteration tolerance. */
  @Test
  void inversionRoundTripsAtAndBetweenAxisPoints() {
    VfpTable table = VfpTable.fromString(deck());
    for (double rate : new double[] {1000000, 1250000, 1500000, 2000000}) {
      for (double thp : new double[] {20, 25, 30, 40}) {
        double bhp = table.inletPressure(rate, thp, 0, 100, 0);
        assertEquals(thp, table.outletPressure(rate, bhp, 0, 100, 0), 1e-9);
      }
    }
  }

  /** Tests genuine four-axis multilinear interpolation against an analytical polynomial. */
  @Test
  void writerRoundTripAcrossAllInterpolatedAxes() {
    EclipseVFPExporter writer = new EclipseVFPExporter();
    writer.setFlowRateType("GAS");
    writer.setWaterCutType("WGR");
    writer.setGORType("OGR");
    writer.setFlowRates(new double[] {100, 200});
    writer.setTHPs(new double[] {10, 20});
    writer.setWaterCuts(new double[] {0, 1});
    writer.setGORs(new double[] {1, 2});
    double[][][][][] values = new double[2][2][2][2][1];
    for (int f = 0; f < 2; f++) {
      for (int t = 0; t < 2; t++) {
        for (int w = 0; w < 2; w++) {
          for (int g = 0; g < 2; g++) {
            values[f][t][w][g][0] = 5 + (f + 1) * (t + 1) * (w + 1) * (g + 1);
          }
        }
      }
    }
    writer.setBHPTable(values);
    VfpTable table = VfpTable.fromString(writer.getVFPPRODString());
    assertEquals(5 + 1.25 * 1.6 * 1.3 * 1.8, table.inletPressure(125, 16, 0.3, 1.8, 0), 1e-9);
    assertEquals(values[1][1][1][1][0], table.inletPressure(200, 20, 1, 2, 0));
  }

  /** Tests that explicit record indices, rather than row order, determine the table coordinates. */
  @Test
  void pressureRecordsCanBeReordered() {
    String reordered = deck().replace("1 1 1 1 60 80 /\n2 1 1 1 100 140 /", "2 1 1 1 100 140 /\n1 1 1 1 60 80 /");
    assertEquals(95, VfpTable.fromString(reordered).inletPressure(1500000, 30, 0, 100, 0), 1e-9);
  }

  /** Tests informative range errors, including singleton axes and non-finite input. */
  @Test
  void allCoordinatesRejectExtrapolation() {
    VfpTable table = VfpTable.fromString(deck());
    assertError("rate", () -> table.inletPressure(999999, 30, 0, 100, 0));
    assertError("THP", () -> table.inletPressure(1500000, 41, 0, 100, 0));
    assertError("WGR", () -> table.inletPressure(1500000, 30, 0.1, 100, 0));
    assertError("GOR", () -> table.inletPressure(1500000, 30, 0, 101, 0));
    assertError("ALQ", () -> table.inletPressure(1500000, 30, 0, 100, 1));
    assertError("rate", () -> table.inletPressure(Double.NaN, 30, 0, 100, 0));
    assertError("BHP", () -> table.outletPressure(1500000, 500, 0, 100, 0));
    assertError("unit", () -> table.getRateAxis("kg/hr"));
  }

  /** Tests unsupported definitions before they can be silently misinterpreted. */
  @Test
  void unsupportedDefinitionsAreNamed() {
    for (String[] pair : new String[][] {{"METRIC", "FIELD"}, {"GAS", "OIL"}, {"WGR", "GOR"}, {"GOR", "GLR"},
        {"THP", "BHP"}, {"BHP", "TEMP"}}) {
      assertError(pair[1], () -> VfpTable.fromString(deck().replace("'" + pair[0] + "'", "'" + pair[1] + "'")));
    }
    assertError("VFPINJ", () -> VfpTable.fromString("VFPINJ\n1 0 'GAS' 'THP' 'METRIC' 'BHP' /\n1 /\n10 /\n1 20 /\n"));
    assertError("ALQ", () -> VfpTable.fromString(deck().replace("''", "'GRAT'").replace("100 /\n0 /", "100 /\n0 1 /")
        .replace("2 1 1 1 100 140 /", "1 1 1 2 60 80 /\n2 1 1 1 100 140 /\n2 1 1 2 100 140 /")));
  }

  /** Tests structural integrity: missing, extra, duplicated, fractional or invalid records. */
  @Test
  void malformedDecksAreRejected() {
    assertError("Missing pressure records", () -> VfpTable.fromString(deck().replace("2 1 1 1 100 140 /", "")));
    assertError("Extra pressure records", () -> VfpTable.fromString(deck() + "1 1 1 1 60 80 /"));
    assertError("Duplicate", () -> VfpTable.fromString(deck().replace("2 1 1 1", "1 1 1 1")));
    assertError("THP", () -> VfpTable.fromString(deck().replace("2 1 1 1", "1.5 1 1 1")));
    assertError("THP", () -> VfpTable.fromString(deck().replace("2 1 1 1", "3 1 1 1")));
    assertError("rate", () -> VfpTable.fromString(deck().replace("1000000 2000000", "2000000 1000000")));
    assertError("rate", () -> VfpTable.fromString(deck().replace("1000000 2000000", "1000000 1000000")));
    assertError("BHP", () -> VfpTable.fromString(deck().replace("60 80", "0 80")));
    assertError("Non-finite", () -> VfpTable.fromString(deck().replace("60 80", "NaN 80")));
    assertError("Missing pressure records",
        () -> VfpTable.fromString(deck().trim().substring(0, deck().trim().length() - 1)));
  }

  /** Tests nonmonotone, flat and decreasing THP curves separately. */
  @Test
  void inversionRequiresAnUnambiguousCurve() {
    VfpTable flat = VfpTable.fromString(deck().replace("100 140", "60 80"));
    assertError("monotonic", () -> flat.outletPressure(1500000, 70, 0, 100, 0));
    VfpTable nonmonotone = VfpTable.fromString(
        deck().replace("20 40 /", "20 30 40 /").replace("2 1 1 1 100 140 /", "2 1 1 1 100 140 /\n3 1 1 1 80 90 /"));
    assertError("monotonic", () -> nonmonotone.outletPressure(1500000, 80, 0, 100, 0));
    VfpTable decreasing = VfpTable.fromString(deck().replace("60 80", "160 180"));
    assertEquals(30, decreasing.outletPressure(1500000, 145, 0, 100, 0), 1e-9);
  }

  /** Tests common explicit Eclipse repetition, D exponents, case and full-deck boundaries. */
  @Test
  void explicitNumericSyntaxAndFollowingKeywordAreSupported() {
    VfpTable table = VfpTable
        .fromString(deck().replace("VFPPROD", "vfpprod").replace("60 80", "1*6D1 8D1") + "\nEND\n");
    assertEquals(95, table.inletPressure(1500000, 30, 0, 100, 0), 1e-9);
    assertError("default token", () -> VfpTable.fromString(deck().replace("60 80", "2*")));
  }

  /**
   * Checks error type and actionable context.
   *
   * @param text required message fragment
   * @param action operation that must fail
   */
  static void assertError(String text, org.junit.jupiter.api.function.Executable action) {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class, action);
    assertTrue(error.getMessage().contains(text), error.getMessage());
  }
}
