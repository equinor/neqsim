package neqsim.process.equipment.pipeline.twophasepipe.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.validation.MohmmedSlugFlowBenchmark.ComparisonRow;
import neqsim.process.equipment.pipeline.twophasepipe.validation.MohmmedSlugFlowBenchmark.Point;

/** Dataset fidelity and comparator verification only; these tests do not validate a flow solver. */
class MohmmedSlugFlowBenchmarkTest {
  private List<Point> readMeasurements() throws Exception {
    String resource = "/neqsim/process/equipment/pipeline/twophasepipe/validation/"
        + "mohmmed_2018_slug_measurements.csv";
    try (InputStream input = getClass().getResourceAsStream(resource)) {
      assertNotNull(input);
      return MohmmedSlugFlowBenchmark.readCsv(new InputStreamReader(input, StandardCharsets.UTF_8));
    }
  }

  @Test
  void retainsAllOriginalNumericObservationsAndTheirProvenance() throws Exception {
    List<Point> points = readMeasurements();
    assertEquals(75, points.size());
    assertEquals(15, points.stream().filter(p -> "translation_velocity".equals(p.quantity)).count());
    assertEquals(30, points.stream().filter(p -> "length_over_diameter".equals(p.quantity)).count());
    assertEquals(30, points.stream().filter(p -> "frequency".equals(p.quantity)).count());
    for (Point point : points) {
      assertEquals(0.074, point.diameter, 0.0);
      assertEquals(297.15, point.temperature, 0.0);
      assertEquals(101300.0, point.pressure, 0.0);
      assertTrue(point.flags.contains("NO_POINT_UNCERTAINTY"));
      assertTrue(point.sourceFile.endsWith(".xlsx"));
      assertTrue(point.sourceSheet.startsWith("xl/worksheets/"));
    }
    assertThrows(UnsupportedOperationException.class, () -> points.clear());
  }

  @Test
  void independentlyPinsMeasuredSpeedAndLengthCellsWithoutCorrectingSourceAmbiguity() throws Exception {
    Map<String, Point> points = new HashMap<String, Point>();
    for (Point point : readMeasurements()) {
      points.put(point.id, point);
    }
    Point speed = points.get("mmc2_sheet1_F4");
    assertEquals(0.698, speed.superficialGasVelocity, 1e-15);
    assertEquals(0.7, speed.superficialLiquidVelocity, 0.0);
    assertEquals(2.23, speed.measuredValue, 0.0);
    assertEquals("F4", speed.sourceValueCell);
    assertTrue(Double.isNaN(speed.stationMinimumDiameters));
    assertEquals(2.41, points.get("mmc2_sheet1_F5").measuredValue, 0.0);
    assertEquals(3.58, points.get("mmc2_sheet1_F6").measuredValue, 0.0);
    Point upstream = points.get("mmc4_sheet1_D5");
    assertEquals(4.1216216216216219, upstream.measuredValue, 0.0);
    assertEquals(54.0, upstream.stationMinimumDiameters, 0.0);
    assertEquals(58.0, upstream.stationMaximumDiameters, 0.0);
    Point downstream = points.get("mmc4_sheet2_D6");
    assertEquals(9.9054054054054053, downstream.measuredValue, 0.0);
    assertEquals(81.0, downstream.stationMinimumDiameters, 0.0);
    assertEquals(81.0, downstream.stationMaximumDiameters, 0.0);
  }

  @Test
  void missingAndInvalidPredictionsCannotDisappearFromAnOtherwisePassingComparison() throws Exception {
    List<Point> points = readMeasurements();
    // Deliberately constructed comparator input; this is not a model prediction or validation result.
    Map<String, Double> predictions = new HashMap<String, Double>();
    for (Point point : points) {
      predictions.put(point.id, point.measuredValue);
    }
    predictions.remove(points.get(0).id);
    predictions.put(points.get(1).id, Double.NaN);
    predictions.put(points.get(2).id, -1.0);
    List<ComparisonRow> rows = MohmmedSlugFlowBenchmark.compare(points, predictions);
    assertEquals(75, rows.size());
    assertEquals(3, rows.stream().filter(row -> !row.withinEngineeringTolerance).count());
    for (int index = 0; index < 3; index++) {
      assertEquals(Double.POSITIVE_INFINITY, rows.get(index).absoluteRelativeError);
    }
    predictions.put("wrong_source_cell", 1.0);
    assertThrows(IllegalArgumentException.class, () -> MohmmedSlugFlowBenchmark.compare(points, predictions));
    assertThrows(UnsupportedOperationException.class, () -> rows.clear());
  }

  @Test
  void translationToleranceIsFixedAndReportedAsEngineeringError() throws Exception {
    Point speed = readMeasurements().get(0);
    assertEquals(0.20, speed.getRelativeTolerance(), 0.0);
    List<ComparisonRow> rows = MohmmedSlugFlowBenchmark.compare(Collections.singletonList(speed),
        Collections.singletonMap(speed.id, speed.measuredValue * 1.21));
    assertFalse(rows.get(0).withinEngineeringTolerance);
    assertEquals(0.21, rows.get(0).absoluteRelativeError, 1e-14);
  }
}
