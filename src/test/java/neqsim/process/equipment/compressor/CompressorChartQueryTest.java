package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for formerly constant public map queries.
 *
 * @author NeqSim
 * @version 1.0
 */
class CompressorChartQueryTest {
  /**
   * Loads a synthetic digitized map with meaningful flow endpoints.
   *
   * @param chart backend to exercise
   */
  private void load(CompressorChart chart) {
    chart.setCurves(new double[] {300, 5, 10, 18}, new double[] {9000}, new double[][] {{100, 200, 300}},
        new double[][] {{100, 80, 50}}, new double[][] {{70, 80, 70}});
  }

  /** Checks both polynomial and alternative interpolation backends. */
  @Test
  void limitsAndEfficiencyAreEvaluated() {
    for (CompressorChart chart : new CompressorChart[] {new CompressorChart(),
        new CompressorChartAlternativeMapLookup()}) {
      load(chart);
      assertTrue(chart.checkSurge2(99, 9000));
      assertFalse(chart.checkSurge2(100, 9000));
      assertTrue(chart.checkStoneWall(301, 9000));
      assertFalse(chart.checkStoneWall(300, 9000));
      assertEquals(chart.getPolytropicEfficiency(200, 9000), chart.polytropicEfficiency(200, 9000), 1e-8);
      assertTrue(chart.polytropicEfficiency(200, 9000) < 100);
      chart.addSurgeCurve(new double[] {100}, new double[] {100});
      assertTrue(chart.checkSurge1(99, 100));
      assertFalse(chart.checkSurge1(100, 100));
      assertThrows(IllegalArgumentException.class, () -> chart.getFlowRangeStatus(Double.NaN, 9000));
    }
  }

  /** Checks missing map evidence is not silently a negative limit test. */
  @Test
  void unresolvedQueriesFailExplicitly() {
    CompressorChart empty = new CompressorChart();
    assertThrows(IllegalStateException.class, () -> empty.checkSurge1(100, 50));
    assertThrows(IllegalStateException.class, () -> empty.checkSurge2(100, 9000));
    assertThrows(IllegalStateException.class, () -> empty.checkStoneWall(100, 9000));
    assertThrows(IllegalArgumentException.class, () -> empty.checkSurge2(100, Double.NaN));
  }

  /** Checks the lowercase stonewall alias dispatches to the MW interpolated boundary. */
  @Test
  void molecularWeightBoundsUseBothMaps() {
    CompressorChartMWInterpolation chart = new CompressorChartMWInterpolation();
    chart.addMapAtMW(18, new double[] {9000}, new double[][] {{100, 200, 300}}, new double[][] {{100, 80, 50}},
        new double[][] {{70, 80, 70}});
    chart.addMapAtMW(22, new double[] {9000}, new double[][] {{200, 400, 600}}, new double[][] {{100, 80, 50}},
        new double[][] {{70, 80, 70}});
    chart.setOperatingMW(20);
    assertEquals(450, chart.getStonewallFlowAtSpeed(9000), 1e-8);
    assertTrue(chart.checkStoneWall(451, 9000));
    assertFalse(chart.checkStoneWall(450, 9000));
    assertTrue(chart.checkSurge2(149, 9000));
  }
}
