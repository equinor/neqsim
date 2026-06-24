package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the structured operating-point accessors {@link Compressor#getOperatingPoint()} and
 * {@link Compressor#getOperatingPointJson()}.
 *
 * @author NeqSim
 * @version 1.0
 */
public class CompressorOperatingPointTest {
  private Compressor compressor;
  private Stream inletStream;

  /**
   * Builds a single-stage compressor running on a real gas fluid.
   */
  @BeforeEach
  public void setUp() {
    SystemInterface gas = new SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.05);
    gas.addComponent("propane", 0.03);
    gas.addComponent("n-butane", 0.02);
    gas.setMixingRule("classic");
    gas.setMultiPhaseCheck(false);

    inletStream = new Stream("inlet", gas);
    inletStream.setFlowRate(10000.0, "kg/hr");
    inletStream.setTemperature(25.0, "C");
    inletStream.setPressure(50.0, "bara");
    inletStream.run();

    compressor = new Compressor("test compressor");
    compressor.setInletStream(inletStream);
    compressor.setOutletPressure(100.0, "bara");
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicEfficiency(0.75);
    compressor.setSpeed(10000);
    compressor.run();
  }

  /**
   * Without a compressor chart the operating point should still be complete, report {@code chartActive=false},
   * {@code withinChart=true} and {@code limitingConstraint="no_chart"}.
   */
  @Test
  public void testOperatingPointWithoutChart() {
    Map<String, Object> point = compressor.getOperatingPoint();

    assertNotNull(point, "Operating point map should not be null");
    assertEquals("1.0", point.get("schemaVersion"));
    assertEquals("test compressor", point.get("name"));
    assertEquals(Boolean.FALSE, point.get("chartActive"), "No chart was set");
    assertEquals(Boolean.TRUE, point.get("withinChart"), "Without a chart there is no map limit to violate");
    assertEquals("no_chart", point.get("limitingConstraint"));

    double flow = ((Number) point.get("flow_m3hr")).doubleValue();
    double head = ((Number) point.get("head_kJkg")).doubleValue();
    double powerMW = ((Number) point.get("power_MW")).doubleValue();
    double powerkW = ((Number) point.get("power_kW")).doubleValue();
    assertTrue(flow > 0.0, "Inlet flow should be positive");
    assertTrue(head > 0.0, "Polytropic head should be positive");
    assertTrue(powerMW > 0.0, "Power should be positive");
    assertEquals(powerMW * 1000.0, powerkW, 1e-6, "MW and kW must be consistent");

    double outP = ((Number) point.get("outletPressure_bara")).doubleValue();
    assertEquals(100.0, outP, 1.0, "Outlet pressure should match the spec");
  }

  /**
   * The JSON variant should be parseable and carry the same keys as the map.
   */
  @Test
  public void testOperatingPointJsonIsValid() {
    String json = compressor.getOperatingPointJson();
    assertNotNull(json, "JSON should not be null");

    @SuppressWarnings("unchecked")
    Map<String, Object> parsed = new Gson().fromJson(json, Map.class);
    assertNotNull(parsed, "Parsed JSON should not be null");
    assertEquals("test compressor", parsed.get("name"));
    assertTrue(parsed.containsKey("distanceToSurge"));
    assertTrue(parsed.containsKey("distanceToStoneWall"));
    assertTrue(parsed.containsKey("limitingConstraint"));
  }

  /**
   * With a generated compressor chart the chart-related fields should be active, the surge distance should be finite,
   * and {@code withinChart} should be consistent with {@code limitingConstraint}.
   */
  @Test
  public void testOperatingPointWithChart() {
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    generator.setChartType("interpolate and extrapolate");
    CompressorChartInterface chart = generator.generateCompressorChart("normal curves");
    compressor.setCompressorChart(chart);
    compressor.run();

    Map<String, Object> point = compressor.getOperatingPoint();

    assertEquals(Boolean.TRUE, point.get("chartActive"), "Chart should be active");
    double distToSurge = ((Number) point.get("distanceToSurge")).doubleValue();
    assertFalse(Double.isNaN(distToSurge), "Distance to surge should not be NaN with a chart");

    boolean withinChart = (Boolean) point.get("withinChart");
    String constraint = (String) point.get("limitingConstraint");
    if (withinChart) {
      assertEquals("none", constraint, "withinChart should imply no limiting constraint");
    } else {
      assertTrue("surge".equals(constraint) || "stonewall".equals(constraint),
          "A constrained point must be surge or stonewall, was " + constraint);
    }
  }
}
