package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for CompressorChartGenerator, verifying that generated curves work correctly with
 * different chart types including "interpolate and extrapolate".
 *
 * @author AGAS
 * @version 1.0
 */
public class CompressorChartGeneratorTest {
  private Compressor compressor;
  private Stream inletStream;

  /**
   * Set up a compressor with inlet stream for testing.
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
   * Test that generated single-speed chart works with "interpolate and extrapolate" chart type.
   */
  @Test
  public void testSingleSpeedChartWithInterpolateAndExtrapolate() {
    // Set chart type before generating
    compressor.setCompressorChartType("interpolate and extrapolate");

    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    generator.setChartType("interpolate and extrapolate");

    CompressorChartInterface chart = generator.generateCompressorChart("normal curves");

    assertNotNull(chart, "Chart should not be null");
    assertTrue(chart instanceof CompressorChartAlternativeMapLookupExtrapolate,
        "Chart should be CompressorChartAlternativeMapLookupExtrapolate for interpolate and extrapolate");

    // Set chart and run compressor
    compressor.setCompressorChart(chart);
    compressor.run();

    // Verify compressor runs without errors
    assertTrue(compressor.getOutletPressure() > compressor.getInletStream().getPressure("bara"),
        "Outlet pressure should be higher than inlet pressure");
  }

  /**
   * Test that generated multi-speed chart works with "interpolate and extrapolate" chart type.
   */
  @Test
  public void testMultiSpeedChartWithInterpolateAndExtrapolate() {
    compressor.setCompressorChartType("interpolate and extrapolate");

    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    generator.setChartType("interpolate and extrapolate");

    CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 5);

    assertNotNull(chart, "Chart should not be null");
    assertTrue(chart instanceof CompressorChartAlternativeMapLookupExtrapolate,
        "Chart should be CompressorChartAlternativeMapLookupExtrapolate for interpolate and extrapolate");

    // Verify chart data
    double[] speeds = chart.getSpeeds();
    assertNotNull(speeds, "Speeds should not be null");
    assertEquals(5, speeds.length, "Should have 5 speed curves");

    double[][] flows = chart.getFlows();
    double[][] heads = chart.getHeads();
    double[][] efficiencies = chart.getPolytropicEfficiencies();

    assertNotNull(flows, "Flows should not be null");
    assertNotNull(heads, "Heads should not be null");
    assertNotNull(efficiencies, "Efficiencies should not be null");
    assertEquals(5, flows.length, "Should have 5 flow arrays");

    // Set chart and run compressor
    compressor.setCompressorChart(chart);
    compressor.run();

    // Verify compressor runs without errors
    assertTrue(compressor.getPolytropicFluidHead() > 0, "Polytropic head should be positive");
  }

  /**
   * Test that template-based chart generation works with "interpolate and extrapolate".
   */
  @Test
  public void testTemplateChartWithInterpolateAndExtrapolate() {
    compressor.setCompressorChartType("interpolate and extrapolate");

    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    generator.setChartType("interpolate and extrapolate");

    CompressorChartInterface chart = generator.generateFromTemplate("CENTRIFUGAL_STANDARD", 5);

    assertNotNull(chart, "Template chart should not be null");
    assertTrue(chart instanceof CompressorChartAlternativeMapLookupExtrapolate,
        "Template chart should be CompressorChartAlternativeMapLookupExtrapolate");

    // Set chart and run compressor
    compressor.setCompressorChart(chart);
    compressor.run();

    // Verify it works
    assertTrue(compressor.getOutletPressure() > 0, "Outlet pressure should be positive");
  }

  /**
   * Test that surge and stonewall curves are generated for single-speed charts.
   */
  @Test
  public void testSingleSpeedSurgeAndStonewallCurves() {
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    generator.setChartType("interpolate and extrapolate");

    CompressorChartInterface chart = generator.generateCompressorChart("normal curves");

    assertNotNull(chart, "Chart should not be null");

    // Set chart and run
    compressor.setCompressorChart(chart);
    compressor.run();

    // Check surge and stonewall - they should be created for single-speed
    double distToSurge = compressor.getDistanceToSurge();
    assertFalse(Double.isNaN(distToSurge), "Distance to surge should not be NaN");
  }

  /**
   * Test that surge and stonewall curves are generated for multi-speed charts.
   */
  @Test
  public void testMultiSpeedSurgeAndStonewallCurves() {
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    generator.setChartType("interpolate and extrapolate");

    CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 5);

    assertNotNull(chart, "Chart should not be null");

    // Verify chart has surge and stonewall data
    double[] speeds = chart.getSpeeds();
    assertTrue(speeds.length >= 1, "Should have at least 1 speed curve");

    // Set chart and run
    compressor.setCompressorChart(chart);
    compressor.run();

    // Check surge and stonewall
    double distToSurge = compressor.getDistanceToSurge();
    assertFalse(Double.isNaN(distToSurge), "Distance to surge should not be NaN for multi-speed");
  }

  /**
   * Test that chart can be used with "interpolate" chart type (without extrapolate).
   */
  @Test
  public void testChartWithInterpolateOnly() {
    compressor.setCompressorChartType("interpolate");

    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    generator.setChartType("interpolate");

    CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 3);

    assertNotNull(chart, "Chart should not be null");
    assertTrue(chart instanceof CompressorChartAlternativeMapLookup,
        "Chart should be CompressorChartAlternativeMapLookup for interpolate");

    compressor.setCompressorChart(chart);
    compressor.run();

    assertTrue(compressor.getPolytropicFluidHead() > 0, "Polytropic head should be positive");
  }

  /**
   * Test that chart can be used with "simple" / "fan law" chart type.
   */
  @Test
  public void testChartWithSimpleType() {
    compressor.setCompressorChartType("simple");

    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    generator.setChartType("simple");

    CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 3);

    assertNotNull(chart, "Chart should not be null");
    assertTrue(chart instanceof CompressorChart, "Chart should be CompressorChart for simple type");

    compressor.setCompressorChart(chart);
    compressor.run();

    assertTrue(compressor.getPolytropicFluidHead() > 0, "Polytropic head should be positive");
  }

  /**
   * Test that getter methods return valid curve data for Python plotting.
   */
  @Test
  public void testGetterMethodsForPlotting() {
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    generator.setChartType("interpolate and extrapolate");

    CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 5);

    // Test all getter methods
    double[] speeds = chart.getSpeeds();
    double[][] flows = chart.getFlows();
    double[][] heads = chart.getHeads();
    double[][] effs = chart.getPolytropicEfficiencies();

    // Verify all arrays are non-null and have matching dimensions
    assertNotNull(speeds, "Speeds array should not be null");
    assertNotNull(flows, "Flows array should not be null");
    assertNotNull(heads, "Heads array should not be null");
    assertNotNull(effs, "Efficiencies array should not be null");

    assertEquals(5, speeds.length, "Should have 5 speeds");
    assertEquals(5, flows.length, "Should have 5 flow curves");
    assertEquals(5, heads.length, "Should have 5 head curves");
    assertEquals(5, effs.length, "Should have 5 efficiency curves");

    // Verify each curve has data points
    for (int i = 0; i < speeds.length; i++) {
      assertTrue(speeds[i] > 0, "Speed " + i + " should be positive");
      assertTrue(flows[i].length > 0, "Flow curve " + i + " should have data points");
      assertTrue(heads[i].length > 0, "Head curve " + i + " should have data points");
      assertTrue(effs[i].length > 0, "Efficiency curve " + i + " should have data points");

      // Verify dimensions match within each speed
      assertEquals(flows[i].length, heads[i].length,
          "Flow and head curve " + i + " should have same number of points");
      assertEquals(flows[i].length, effs[i].length,
          "Flow and efficiency curve " + i + " should have same number of points");
    }
  }

  /**
   * Test that chart conditions are returned correctly.
   */
  @Test
  public void testChartConditionsGetter() {
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 3);

    double[] conditions = chart.getChartConditions();
    assertNotNull(conditions, "Chart conditions should not be null");
    assertTrue(conditions.length > 0, "Chart conditions should have at least one value");

    // First condition is typically molar mass
    assertTrue(conditions[0] > 0, "Molar mass condition should be positive");
  }

  /**
   * Test the convenience method on Compressor class for generating charts.
   */
  @Test
  public void testCompressorGenerateChartMethod() {
    compressor.setCompressorChartType("interpolate and extrapolate");
    compressor.run();

    // Test no-arg version
    compressor.generateCompressorChart();
    assertNotNull(compressor.getCompressorChart(), "Chart should not be null after generation");
    assertTrue(compressor.getCompressorChart().isUseCompressorChart(),
        "Chart should be active after generation");

    // Test with number of speeds
    compressor.generateCompressorChart(5);
    double[] speeds = compressor.getCompressorChart().getSpeeds();
    assertNotNull(speeds, "Speeds should not be null");
    assertEquals(5, speeds.length, "Should have 5 speed curves");

    // Test with generation option
    compressor.generateCompressorChart("normal curves");
    assertNotNull(compressor.getCompressorChart().getSpeeds(), "Speeds should not be null");

    // Test with generation option and number of speeds
    compressor.generateCompressorChart("mid range", 3);
    assertEquals(3, compressor.getCompressorChart().getSpeeds().length,
        "Should have 3 speed curves");
  }

  /**
   * Test the template-based generation method on Compressor class.
   */
  @Test
  public void testCompressorGenerateFromTemplate() {
    compressor.setCompressorChartType("interpolate and extrapolate");
    compressor.run();

    compressor.generateCompressorChartFromTemplate("CENTRIFUGAL_STANDARD", 5);

    CompressorChartInterface chart = compressor.getCompressorChart();
    assertNotNull(chart, "Chart should not be null");
    assertTrue(chart instanceof CompressorChartAlternativeMapLookupExtrapolate,
        "Should be correct chart type");

    double[] speeds = chart.getSpeeds();
    assertEquals(5, speeds.length, "Should have 5 speed curves");

    // Verify compressor runs with the generated chart
    compressor.run();
    assertTrue(compressor.getPolytropicFluidHead() > 0, "Head should be positive");
  }

  /**
   * Test generating chart with specific speed values.
   */
  @Test
  public void testCompressorGenerateWithSpecificSpeeds() {
    compressor.setCompressorChartType("interpolate and extrapolate");
    compressor.run();

    double[] speeds = {8000, 9000, 10000, 11000, 12000};
    compressor.generateCompressorChart("normal curves", speeds);

    CompressorChartInterface chart = compressor.getCompressorChart();
    double[] chartSpeeds = chart.getSpeeds();

    assertNotNull(chartSpeeds, "Speeds should not be null");
    assertEquals(5, chartSpeeds.length, "Should have 5 speed curves");
    assertEquals(8000.0, chartSpeeds[0], 0.1, "First speed should be 8000");
    assertEquals(12000.0, chartSpeeds[4], 0.1, "Last speed should be 12000");
  }

  /**
   * Test getCompressorChartType method.
   */
  @Test
  public void testGetCompressorChartType() {
    compressor.setCompressorChartType("simple");
    assertEquals("simple", compressor.getCompressorChartType());

    compressor.setCompressorChartType("interpolate");
    assertEquals("interpolate", compressor.getCompressorChartType());

    compressor.setCompressorChartType("interpolate and extrapolate");
    assertEquals("interpolate and extrapolate", compressor.getCompressorChartType());
  }

  /**
   * Test that power curves are generated correctly.
   *
   * <p>
   * Power is calculated as: P = mass_flow * head / efficiency. This is critical for driver
   * selection and energy analysis.
   * </p>
   */
  @Test
  public void testPowerCurvesGeneration() {
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    generator.setChartType("interpolate and extrapolate");

    CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 3);

    // Get power curves
    double[][] powers = chart.getPowers();

    assertNotNull(powers, "Power curves should not be null");
    assertEquals(3, powers.length, "Should have 3 power curves (one per speed)");

    // Verify power curves have correct dimensions
    double[][] flows = chart.getFlows();
    for (int i = 0; i < powers.length; i++) {
      assertEquals(flows[i].length, powers[i].length,
          "Power curve " + i + " should have same number of points as flow curve");

      // Verify all power values are positive
      for (int j = 0; j < powers[i].length; j++) {
        assertTrue(powers[i][j] > 0, "Power at speed " + i + " point " + j + " should be positive");
      }
    }

    // Verify power increases with speed at design point (middle point)
    int designPointIdx = flows[0].length / 2;
    for (int i = 1; i < powers.length; i++) {
      assertTrue(powers[i][designPointIdx] > powers[i - 1][designPointIdx],
          "Power should increase with speed at design point");
    }
  }

  /**
   * Test that pressure ratio curves are generated correctly.
   *
   * <p>
   * Pressure ratio is calculated from polytropic head using thermodynamic relations. Useful for
   * process design and control system configuration.
   * </p>
   */
  @Test
  public void testPressureRatioCurvesGeneration() {
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    generator.setChartType("interpolate and extrapolate");

    CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 3);

    // Get pressure ratio curves
    double[][] pressureRatios = chart.getPressureRatios();

    assertNotNull(pressureRatios, "Pressure ratio curves should not be null");
    assertEquals(3, pressureRatios.length, "Should have 3 pressure ratio curves (one per speed)");

    // Verify pressure ratio curves have correct dimensions
    double[][] flows = chart.getFlows();
    for (int i = 0; i < pressureRatios.length; i++) {
      assertEquals(flows[i].length, pressureRatios[i].length,
          "Pressure ratio curve " + i + " should have same number of points as flow curve");

      // Verify all pressure ratios are >= 1.0
      for (int j = 0; j < pressureRatios[i].length; j++) {
        assertTrue(pressureRatios[i][j] >= 1.0,
            "Pressure ratio at speed " + i + " point " + j + " should be >= 1.0");
      }
    }

    // Verify pressure ratio increases with head (surge has highest head, stonewall has lowest)
    // At constant speed, pressure ratio should decrease from surge to stonewall
    for (int i = 0; i < pressureRatios.length; i++) {
      int surgeIdx = 0;
      int stonewallIdx = pressureRatios[i].length - 1;
      assertTrue(pressureRatios[i][surgeIdx] > pressureRatios[i][stonewallIdx],
          "Pressure ratio at surge should be higher than at stonewall for speed " + i);
    }
  }

  /**
   * Test that reference density and inlet pressure can be set for power calculations.
   */
  @Test
  public void testReferenceDensityAndPressureSettings() {
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 3);

    // Test that reference values are set
    double density = chart.getReferenceDensity();
    double inletPressure = chart.getInletPressure();
    double polytropicExp = chart.getPolytropicExponent();

    assertTrue(!Double.isNaN(density), "Reference density should be set");
    assertTrue(!Double.isNaN(inletPressure), "Inlet pressure should be set");
    assertTrue(!Double.isNaN(polytropicExp), "Polytropic exponent should be set");

    assertTrue(density > 0, "Reference density should be positive");
    assertTrue(inletPressure > 0, "Inlet pressure should be positive");
    assertTrue(polytropicExp > 1.0 && polytropicExp < 2.0,
        "Polytropic exponent should be between 1 and 2 for typical gases");
  }

  /**
   * Test power and pressure ratio calculations consistency.
   *
   * <p>
   * Verifies that power follows P = mass_flow * head / efficiency and pressure ratio follows
   * polytropic relations.
   * </p>
   */
  @Test
  public void testPowerAndPressureRatioConsistency() {
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 3);

    double[][] flows = chart.getFlows();
    double[][] heads = chart.getHeads();
    double[][] effs = chart.getPolytropicEfficiencies();
    double[][] powers = chart.getPowers();
    double[][] pressureRatios = chart.getPressureRatios();
    double density = chart.getReferenceDensity();

    // Check consistency at design point
    int designPointIdx = flows[0].length / 2;
    for (int i = 0; i < flows.length; i++) {
      double flow = flows[i][designPointIdx]; // m3/hr
      double head = heads[i][designPointIdx]; // kJ/kg
      double eff = effs[i][designPointIdx] / 100.0; // fraction

      // Calculate expected power: P = (density * volumeFlow * head) / efficiency
      double massFlow = density * flow / 3600.0; // kg/s
      double expectedPower = massFlow * head / eff; // kW

      double actualPower = powers[i][designPointIdx];

      // Allow 1% tolerance for numerical differences
      assertEquals(expectedPower, actualPower, expectedPower * 0.01,
          "Power calculation should be consistent at speed " + i);
    }
  }

  /**
   * Test that discharge temperature curves are generated correctly.
   *
   * <p>
   * Discharge temperature is calculated from inlet temperature and pressure ratio using polytropic
   * relations. This is critical for downstream equipment design and material temperature limits.
   * </p>
   */
  @Test
  public void testDischargeTemperatureCurvesGeneration() {
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    generator.setChartType("interpolate and extrapolate");

    CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 3);

    // Get discharge temperature curves
    double[][] dischargeTemps = chart.getDischargeTemperatures();

    assertNotNull(dischargeTemps, "Discharge temperature curves should not be null");
    assertEquals(3, dischargeTemps.length,
        "Should have 3 discharge temperature curves (one per speed)");

    // Verify discharge temperature curves have correct dimensions
    double[][] flows = chart.getFlows();
    for (int i = 0; i < dischargeTemps.length; i++) {
      assertEquals(flows[i].length, dischargeTemps[i].length,
          "Discharge temp curve " + i + " should have same number of points as flow curve");

      // Verify all temperatures are above inlet temperature (compression heats the gas)
      double inletTemp = chart.getInletTemperature();
      for (int j = 0; j < dischargeTemps[i].length; j++) {
        assertTrue(dischargeTemps[i][j] >= inletTemp, "Discharge temp at speed " + i + " point " + j
            + " should be >= inlet temp (compression heats gas)");
      }
    }

    // Verify discharge temperature increases with head (pressure ratio)
    // At surge (high head) temperature should be higher than at stonewall (low head)
    for (int i = 0; i < dischargeTemps.length; i++) {
      int surgeIdx = 0;
      int stonewallIdx = dischargeTemps[i].length - 1;
      assertTrue(dischargeTemps[i][surgeIdx] > dischargeTemps[i][stonewallIdx],
          "Discharge temp at surge should be higher than at stonewall for speed " + i);
    }
  }

  /**
   * Test that inlet temperature and gamma can be set for discharge temperature calculations.
   */
  @Test
  public void testInletTemperatureAndGammaSettings() {
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 3);

    // Test that reference values are set
    double inletTemp = chart.getInletTemperature();
    double gamma = chart.getGamma();

    assertTrue(!Double.isNaN(inletTemp), "Inlet temperature should be set");
    assertTrue(!Double.isNaN(gamma), "Gamma should be set");

    assertTrue(inletTemp > 200 && inletTemp < 500,
        "Inlet temperature should be reasonable (200-500 K), got: " + inletTemp);
    assertTrue(gamma > 1.0 && gamma < 2.0,
        "Gamma should be between 1 and 2 for typical gases, got: " + gamma);
  }

  /**
   * Test discharge temperature calculation consistency with thermodynamic relations.
   */
  @Test
  public void testDischargeTemperatureConsistency() {
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 3);

    double[][] pressureRatios = chart.getPressureRatios();
    double[][] dischargeTemps = chart.getDischargeTemperatures();
    double[][] effs = chart.getPolytropicEfficiencies();
    double inletTemp = chart.getInletTemperature();
    double gamma = chart.getGamma();

    // At design point, verify T2/T1 ~ PR^((gamma-1)/(gamma*eta))
    int designPointIdx = pressureRatios[0].length / 2;
    for (int i = 0; i < pressureRatios.length; i++) {
      double pr = pressureRatios[i][designPointIdx];
      double t2 = dischargeTemps[i][designPointIdx];
      double eta = effs[i][designPointIdx] / 100.0;

      // Expected: T2 = T1 * PR^((gamma-1)/(gamma*eta))
      double isentropicExp = (gamma - 1.0) / gamma;
      double polytropicExp = isentropicExp / eta;
      double expectedT2 = inletTemp * Math.pow(pr, polytropicExp);

      // Allow 5% tolerance for numerical differences
      assertEquals(expectedT2, t2, expectedT2 * 0.05,
          "Discharge temperature should follow polytropic relation at speed " + i);
    }
  }
}
