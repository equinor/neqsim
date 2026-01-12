package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for CompressorChartMWInterpolation.
 *
 * @author NeqSim Development Team
 */
public class CompressorChartMWInterpolationTest {

  private CompressorChartMWInterpolation chart;
  private double[] chartConditions;

  // Test data for MW = 18 g/mol
  private double[] speed18 = {10000, 11000, 12000};
  private double[][] flow18 = {{3000, 3500, 4000, 4500, 5000}, {3300, 3800, 4300, 4800, 5300},
      {3600, 4100, 4600, 5100, 5600}};
  private double[][] head18 =
      {{120, 115, 108, 98, 85}, {138, 132, 124, 113, 98}, {158, 151, 142, 130, 113}};
  private double[][] polyEff18 = {{75, 78, 80, 78, 73}, {74, 77, 79, 77, 72}, {73, 76, 78, 76, 71}};

  // Test data for MW = 22 g/mol (lower head capacity due to heavier gas)
  private double[] speed22 = {10000, 11000, 12000};
  private double[][] flow22 = {{2800, 3300, 3800, 4300, 4800}, {3100, 3600, 4100, 4600, 5100},
      {3400, 3900, 4400, 4900, 5400}};
  private double[][] head22 =
      {{100, 96, 90, 82, 71}, {115, 110, 103, 94, 82}, {132, 126, 118, 108, 94}};
  private double[][] polyEff22 = {{73, 76, 78, 76, 71}, {72, 75, 77, 75, 70}, {71, 74, 76, 74, 69}};

  @BeforeEach
  void setUp() {
    chart = new CompressorChartMWInterpolation();
    chart.setHeadUnit("kJ/kg");

    chartConditions = new double[] {25.0, 50.0, 50.0, 20.0};

    // Add map at MW = 18 g/mol
    chart.addMapAtMW(18.0, chartConditions, speed18, flow18, head18, polyEff18);

    // Add map at MW = 22 g/mol
    chart.addMapAtMW(22.0, chartConditions, speed22, flow22, head22, polyEff22);
  }

  @Test
  void testAddMapAtMW() {
    assertEquals(2, chart.getNumberOfMaps(), "Should have 2 maps");
    assertEquals(18.0, chart.getMapMolecularWeights().get(0), 0.001);
    assertEquals(22.0, chart.getMapMolecularWeights().get(1), 0.001);
  }

  @Test
  void testGetChartAtMW() {
    CompressorChart chart18 = chart.getChartAtMW(18.0);
    assertNotNull(chart18, "Should find chart at MW=18");

    CompressorChart chart22 = chart.getChartAtMW(22.0);
    assertNotNull(chart22, "Should find chart at MW=22");

    CompressorChart chartNotFound = chart.getChartAtMW(25.0);
    assertNull(chartNotFound, "Should not find chart at MW=25");
  }

  @Test
  void testInterpolationAtExactMW() {
    // At exact MW = 18, should return values from the 18 g/mol map
    chart.setOperatingMW(18.0);
    double head = chart.getPolytropicHead(3500.0, 10000);
    double expectedHead = chart.getChartAtMW(18.0).getPolytropicHead(3500.0, 10000);
    assertEquals(expectedHead, head, 0.001, "At exact MW=18, should return MW=18 map values");

    // At exact MW = 22, should return values from the 22 g/mol map
    chart.setOperatingMW(22.0);
    head = chart.getPolytropicHead(3300.0, 10000);
    expectedHead = chart.getChartAtMW(22.0).getPolytropicHead(3300.0, 10000);
    assertEquals(expectedHead, head, 0.001, "At exact MW=22, should return MW=22 map values");
  }

  @Test
  void testInterpolationBetweenMaps() {
    // At MW = 20 (midpoint), should interpolate between maps
    chart.setOperatingMW(20.0);

    double flow = 3500.0;
    double speed = 10000.0;

    double head18val = chart.getChartAtMW(18.0).getPolytropicHead(flow, speed);
    double head22val = chart.getChartAtMW(22.0).getPolytropicHead(flow, speed);
    double expectedHead = (head18val + head22val) / 2.0; // 50% interpolation

    double actualHead = chart.getPolytropicHead(flow, speed);
    assertEquals(expectedHead, actualHead, 0.01,
        "At MW=20 (midpoint), head should be average of both maps");

    // Test efficiency interpolation
    double eff18val = chart.getChartAtMW(18.0).getPolytropicEfficiency(flow, speed);
    double eff22val = chart.getChartAtMW(22.0).getPolytropicEfficiency(flow, speed);
    double expectedEff = (eff18val + eff22val) / 2.0;

    double actualEff = chart.getPolytropicEfficiency(flow, speed);
    assertEquals(expectedEff, actualEff, 0.01,
        "At MW=20 (midpoint), efficiency should be average of both maps");
  }

  @Test
  void testInterpolationAt25Percent() {
    // At MW = 19 (25% from 18 to 22)
    chart.setOperatingMW(19.0);

    double flow = 3500.0;
    double speed = 10000.0;

    double head18val = chart.getChartAtMW(18.0).getPolytropicHead(flow, speed);
    double head22val = chart.getChartAtMW(22.0).getPolytropicHead(flow, speed);
    double expectedHead = head18val + 0.25 * (head22val - head18val);

    double actualHead = chart.getPolytropicHead(flow, speed);
    assertEquals(expectedHead, actualHead, 0.01, "At MW=19 (25%), head should be interpolated");
  }

  @Test
  void testBelowLowestMWUsesBoundaryMap() {
    // At MW = 16 (below 18), should use MW=18 map
    chart.setOperatingMW(16.0);

    double flow = 3500.0;
    double speed = 10000.0;

    double head18val = chart.getChartAtMW(18.0).getPolytropicHead(flow, speed);
    double actualHead = chart.getPolytropicHead(flow, speed);

    assertEquals(head18val, actualHead, 0.001,
        "Below lowest MW, should use lowest MW map (no extrapolation)");
  }

  @Test
  void testAboveHighestMWUsesBoundaryMap() {
    // At MW = 25 (above 22), should use MW=22 map
    chart.setOperatingMW(25.0);

    double flow = 3300.0;
    double speed = 10000.0;

    double head22val = chart.getChartAtMW(22.0).getPolytropicHead(flow, speed);
    double actualHead = chart.getPolytropicHead(flow, speed);

    assertEquals(head22val, actualHead, 0.001,
        "Above highest MW, should use highest MW map (no extrapolation)");
  }

  @Test
  void testDisableInterpolation() {
    chart.setOperatingMW(20.0);
    chart.setInterpolationEnabled(false);

    // When disabled, should use base chart (first added map)
    double flowVal = 3500.0;
    double speedVal = 10000.0;

    double actualHead = chart.getPolytropicHead(flowVal, speedVal);
    // Base chart behavior - uses polynomial fit from first map
    assertFalse(Double.isNaN(actualHead), "Should return valid head when interpolation disabled");
  }

  @Test
  void testWithSingleMap() {
    // Create chart with only one map
    CompressorChartMWInterpolation singleMapChart = new CompressorChartMWInterpolation();
    singleMapChart.setHeadUnit("kJ/kg");
    singleMapChart.addMapAtMW(20.0, chartConditions, speed18, flow18, head18, polyEff18);
    singleMapChart.setOperatingMW(22.0); // Different from map MW

    double head = singleMapChart.getPolytropicHead(3500.0, 10000);
    double expectedHead = singleMapChart.getChartAtMW(20.0).getPolytropicHead(3500.0, 10000);

    assertEquals(expectedHead, head, 0.001,
        "With single map, should use that map regardless of MW");
  }

  @Test
  void testThreeMapsInterpolation() {
    // Add a third map at MW = 20
    double[] speed20 = {10000, 11000, 12000};
    double[][] flow20 = {{2900, 3400, 3900, 4400, 4900}, {3200, 3700, 4200, 4700, 5200},
        {3500, 4000, 4500, 5000, 5500}};
    double[][] head20 =
        {{110, 105, 99, 90, 78}, {126, 121, 113, 103, 90}, {145, 138, 130, 119, 103}};
    double[][] polyEff20 = {{74, 77, 79, 77, 72}, {73, 76, 78, 76, 71}, {72, 75, 77, 75, 70}};

    chart.addMapAtMW(20.0, chartConditions, speed20, flow20, head20, polyEff20);

    assertEquals(3, chart.getNumberOfMaps(), "Should have 3 maps");
    assertEquals(18.0, chart.getMapMolecularWeights().get(0), 0.001);
    assertEquals(20.0, chart.getMapMolecularWeights().get(1), 0.001);
    assertEquals(22.0, chart.getMapMolecularWeights().get(2), 0.001);

    // Test interpolation between MW=18 and MW=20 (at MW=19)
    chart.setOperatingMW(19.0);
    double flow = 3400.0;
    double speed = 10000.0;

    double head18val = chart.getChartAtMW(18.0).getPolytropicHead(flow, speed);
    double head20val = chart.getChartAtMW(20.0).getPolytropicHead(flow, speed);
    double expectedHead = (head18val + head20val) / 2.0; // 50% between 18 and 20

    double actualHead = chart.getPolytropicHead(flow, speed);
    assertEquals(expectedHead, actualHead, 0.01, "Should interpolate between MW=18 and MW=20");

    // Test interpolation between MW=20 and MW=22 (at MW=21)
    chart.setOperatingMW(21.0);
    double head20val2 = chart.getChartAtMW(20.0).getPolytropicHead(flow, speed);
    double head22val = chart.getChartAtMW(22.0).getPolytropicHead(flow, speed);
    double expectedHead2 = (head20val2 + head22val) / 2.0;

    double actualHead2 = chart.getPolytropicHead(flow, speed);
    assertEquals(expectedHead2, actualHead2, 0.01, "Should interpolate between MW=20 and MW=22");
  }

  @Test
  void testGenerateSurgeAndStoneWallCurves() {
    chart.generateAllSurgeCurves();
    chart.generateAllStoneWallCurves();

    // Check that surge curves were generated for all maps
    for (Double mw : chart.getMapMolecularWeights()) {
      CompressorChart mapChart = chart.getChartAtMW(mw);
      assertTrue(mapChart.getSurgeCurve().isActive(), "Surge curve should be active for MW=" + mw);
    }
  }

  @Test
  void testIntegrationWithCompressor() {
    // Create fluid
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(4000.0, "Am3/hr");

    // Create process
    Stream stream = new Stream("inlet", fluid);
    stream.run();

    Compressor comp = new Compressor("K-100", stream);
    comp.setUsePolytropicCalc(true);

    // Set the MW interpolation chart
    comp.setCompressorChart(chart);
    chart.setOperatingMW(20.0);
    comp.setSpeed(10000);

    // Run compressor
    ProcessSystem process = new ProcessSystem();
    process.add(stream);
    process.add(comp);
    process.run();

    // Verify polytropic head was calculated using interpolation
    double polyHead = comp.getPolytropicHead("kJ/kg");
    assertTrue(polyHead > 0, "Polytropic head should be positive");
    assertTrue(Double.isFinite(polyHead), "Polytropic head should be finite");
  }

  @Test
  void testSetHeadUnitPropagates() {
    chart.setHeadUnit("meter");

    assertEquals("meter", chart.getHeadUnit());
    for (Double mw : chart.getMapMolecularWeights()) {
      assertEquals("meter", chart.getChartAtMW(mw).getHeadUnit(),
          "Head unit should propagate to all maps");
    }
  }

  @Test
  void testAutoGenerateSurgeCurves() {
    // Create new chart with auto-generate enabled
    CompressorChartMWInterpolation autoChart = new CompressorChartMWInterpolation();
    autoChart.setHeadUnit("kJ/kg");
    autoChart.setAutoGenerateSurgeCurves(true);
    autoChart.setAutoGenerateStoneWallCurves(true);

    // Add maps - surge and stone wall should be auto-generated
    autoChart.addMapAtMW(18.0, chartConditions, speed18, flow18, head18, polyEff18);
    autoChart.addMapAtMW(22.0, chartConditions, speed22, flow22, head22, polyEff22);

    // Verify surge curves were generated
    assertTrue(autoChart.getChartAtMW(18.0).getSurgeCurve().isActive(),
        "Surge curve should be auto-generated for MW=18");
    assertTrue(autoChart.getChartAtMW(22.0).getSurgeCurve().isActive(),
        "Surge curve should be auto-generated for MW=22");
  }

  @Test
  void testInterpolatedSurgeFlow() {
    // Generate surge curves for all maps
    chart.generateAllSurgeCurves();

    chart.setOperatingMW(20.0);

    // Get surge flow at a given head using interpolation
    double head = 100.0;
    double surgeFlow18 = chart.getChartAtMW(18.0).getSurgeCurve().getFlow(head);
    double surgeFlow22 = chart.getChartAtMW(22.0).getSurgeCurve().getFlow(head);
    double expectedSurgeFlow = (surgeFlow18 + surgeFlow22) / 2.0;

    double actualSurgeFlow = chart.getSurgeFlow(head);
    assertEquals(expectedSurgeFlow, actualSurgeFlow, 0.01,
        "Surge flow should be interpolated between MW maps");
  }

  @Test
  void testInterpolatedStoneWallFlow() {
    // Generate stone wall curves for all maps
    chart.generateAllStoneWallCurves();

    chart.setOperatingMW(20.0);

    // Get stone wall flow at a given head using interpolation
    double head = 100.0;
    double stoneFlow18 =
        ((SafeSplineStoneWallCurve) chart.getChartAtMW(18.0).getStoneWallCurve()).getFlow(head);
    double stoneFlow22 =
        ((SafeSplineStoneWallCurve) chart.getChartAtMW(22.0).getStoneWallCurve()).getFlow(head);
    double expectedStoneFlow = (stoneFlow18 + stoneFlow22) / 2.0;

    double actualStoneFlow = chart.getStoneWallFlow(head);
    assertEquals(expectedStoneFlow, actualStoneFlow, 0.01,
        "Stone wall flow should be interpolated between MW maps");
  }

  @Test
  void testIsSurgeInterpolated() {
    chart.generateAllSurgeCurves();
    chart.setOperatingMW(20.0);

    double head = 100.0;
    double surgeFlow = chart.getSurgeFlow(head);

    // Flow below surge should be in surge
    assertTrue(chart.isSurge(head, surgeFlow * 0.8), "Flow 20% below surge should be in surge");

    // Flow above surge should not be in surge
    assertFalse(chart.isSurge(head, surgeFlow * 1.2),
        "Flow 20% above surge should not be in surge");
  }

  @Test
  void testIsStoneWallInterpolated() {
    chart.generateAllStoneWallCurves();
    chart.setOperatingMW(20.0);

    double head = 100.0;
    double stoneWallFlow = chart.getStoneWallFlow(head);

    // Flow above stone wall should be choked
    assertTrue(chart.isStoneWall(head, stoneWallFlow * 1.2),
        "Flow 20% above stone wall should be choked");

    // Flow below stone wall should not be choked
    assertFalse(chart.isStoneWall(head, stoneWallFlow * 0.8),
        "Flow 20% below stone wall should not be choked");
  }

  @Test
  void testDistanceToSurgeInterpolated() {
    chart.generateAllSurgeCurves();
    chart.setOperatingMW(20.0);

    double head = 100.0;
    double surgeFlow = chart.getSurgeFlow(head);
    double operatingFlow = surgeFlow * 1.25; // 25% above surge

    double distance = chart.getDistanceToSurge(head, operatingFlow);
    assertEquals(0.25, distance, 0.01, "Distance to surge should be 25%");
  }

  @Test
  void testDistanceToStoneWallInterpolated() {
    chart.generateAllStoneWallCurves();
    chart.setOperatingMW(20.0);

    double head = 100.0;
    double stoneWallFlow = chart.getStoneWallFlow(head);
    double operatingFlow = stoneWallFlow * 0.8; // Stone wall is 25% above operating flow

    double distance = chart.getDistanceToStoneWall(head, operatingFlow);
    assertEquals(0.25, distance, 0.01, "Distance to stone wall should be 25%");
  }

  @Test
  void testSeparateFlowArraysForEfficiency() {
    // Create chart using separate flow arrays for head and efficiency
    CompressorChartMWInterpolation separateFlowChart = new CompressorChartMWInterpolation();
    separateFlowChart.setHeadUnit("kJ/kg");

    double[] speeds = {10000, 11000};
    double[][] flowHead = {{3000, 3500, 4000, 4500}, {3300, 3800, 4300, 4800}};
    double[][] heads = {{120, 115, 108, 98}, {138, 132, 124, 113}};
    // Efficiency measured at different flow points
    double[][] flowEff = {{3100, 3600, 4100}, {3400, 3900, 4400}};
    double[][] effs = {{76, 79, 77}, {75, 78, 76}};

    // Add map using separate flow arrays
    separateFlowChart.addMapAtMW(20.0, chartConditions, speeds, flowHead, heads, flowEff, effs);

    assertEquals(1, separateFlowChart.getNumberOfMaps());

    // Verify chart works
    separateFlowChart.setOperatingMW(20.0);
    double head = separateFlowChart.getPolytropicHead(3500.0, 10000);
    double eff = separateFlowChart.getPolytropicEfficiency(3500.0, 10000);

    assertTrue(head > 0, "Head should be positive");
    assertTrue(eff > 0 && eff <= 100, "Efficiency should be between 0 and 100");
  }

  @Test
  void testSingleSpeedCurveWithMW() {
    // Test single-speed curve with (MW, speed, flow, head, polyEff)
    CompressorChartMWInterpolation singleSpeedChart = new CompressorChartMWInterpolation();
    singleSpeedChart.setHeadUnit("kJ/kg");

    double speed = 10000;
    double[] flow = {3000, 3500, 4000, 4500, 5000};
    double[] head = {120, 115, 108, 98, 85};
    double[] eff = {75, 78, 80, 78, 73};

    // Add single-speed map at MW = 18
    singleSpeedChart.addMapAtMW(18.0, speed, flow, head, eff);

    // Add single-speed map at MW = 22
    double[] head22 = {100, 96, 90, 82, 71};
    double[] eff22 = {73, 76, 78, 76, 71};
    singleSpeedChart.addMapAtMW(22.0, speed, flow, head22, eff22);

    assertEquals(2, singleSpeedChart.getNumberOfMaps());

    // Test interpolation at MW = 20
    singleSpeedChart.setOperatingMW(20.0);
    double headAt20 = singleSpeedChart.getPolytropicHead(3500.0, speed);
    double effAt20 = singleSpeedChart.getPolytropicEfficiency(3500.0, speed);

    assertTrue(headAt20 > 0, "Head should be positive");
    assertTrue(effAt20 > 0 && effAt20 <= 100, "Efficiency should be between 0 and 100");

    // Head at MW=20 should be between MW=18 and MW=22 values
    double head18 = singleSpeedChart.getChartAtMW(18.0).getPolytropicHead(3500.0, speed);
    double headMW22 = singleSpeedChart.getChartAtMW(22.0).getPolytropicHead(3500.0, speed);
    assertTrue(headAt20 <= head18 && headAt20 >= headMW22,
        "Head at MW=20 should be between MW=18 and MW=22");
  }

  @Test
  void testSingleSpeedCurveWithSeparateFlowArrays() {
    // Test single-speed curve with (MW, speed, flow, head, flowEff, polyEff)
    CompressorChartMWInterpolation singleSpeedChart = new CompressorChartMWInterpolation();
    singleSpeedChart.setHeadUnit("kJ/kg");

    double speed = 10000;
    double[] flowHead = {3000, 3500, 4000, 4500, 5000};
    double[] head = {120, 115, 108, 98, 85};
    // Efficiency at different flow points
    double[] flowEff = {3100, 3600, 4100};
    double[] eff = {76, 79, 77};

    // Add map with separate flow arrays
    singleSpeedChart.addMapAtMW(20.0, speed, flowHead, head, flowEff, eff);

    assertEquals(1, singleSpeedChart.getNumberOfMaps());

    singleSpeedChart.setOperatingMW(20.0);
    double headVal = singleSpeedChart.getPolytropicHead(3500.0, speed);
    double effVal = singleSpeedChart.getPolytropicEfficiency(3500.0, speed);

    assertTrue(headVal > 0, "Head should be positive");
    assertTrue(effVal > 0 && effVal <= 100, "Efficiency should be between 0 and 100");
  }

  @Test
  void testMultiSpeedCurveWithoutChartConditions() {
    // Test multi-speed curve with (MW, speed[], flow[][], head[][], polyEff[][])
    CompressorChartMWInterpolation multiSpeedChart = new CompressorChartMWInterpolation();
    multiSpeedChart.setHeadUnit("kJ/kg");

    double[] speeds = {10000, 11000, 12000};

    // Add map at MW = 18 without chartConditions
    multiSpeedChart.addMapAtMW(18.0, speeds, flow18, head18, polyEff18);

    // Add map at MW = 22 without chartConditions
    multiSpeedChart.addMapAtMW(22.0, speeds, flow22, head22, polyEff22);

    assertEquals(2, multiSpeedChart.getNumberOfMaps());

    // Test interpolation at MW = 20
    multiSpeedChart.setOperatingMW(20.0);
    double headAt20 = multiSpeedChart.getPolytropicHead(3500.0, 10000);
    double effAt20 = multiSpeedChart.getPolytropicEfficiency(3500.0, 10000);

    assertTrue(headAt20 > 0, "Head should be positive");
    assertTrue(effAt20 > 0 && effAt20 <= 100, "Efficiency should be between 0 and 100");

    // Head at MW=20 should be between MW=18 and MW=22 values
    double headMW18 = multiSpeedChart.getChartAtMW(18.0).getPolytropicHead(3500.0, 10000);
    double headMW22 = multiSpeedChart.getChartAtMW(22.0).getPolytropicHead(3500.0, 10000);
    assertTrue(headAt20 <= headMW18 && headAt20 >= headMW22,
        "Head at MW=20 should be between MW=18 and MW=22");
  }

  @Test
  void testMultiSpeedCurveWithSeparateFlowArraysNoChartConditions() {
    // Test multi-speed with (MW, speed[], flow[][], head[][], flowEff[][], polyEff[][])
    CompressorChartMWInterpolation multiSpeedChart = new CompressorChartMWInterpolation();
    multiSpeedChart.setHeadUnit("kJ/kg");

    double[] speeds = {10000, 11000};
    double[][] flowHead = {{3000, 3500, 4000, 4500}, {3300, 3800, 4300, 4800}};
    double[][] heads = {{120, 115, 108, 98}, {138, 132, 124, 113}};
    // Efficiency at different flow points
    double[][] flowEff = {{3100, 3600, 4100}, {3400, 3900, 4400}};
    double[][] effs = {{76, 79, 77}, {75, 78, 76}};

    // Add map without chartConditions
    multiSpeedChart.addMapAtMW(20.0, speeds, flowHead, heads, flowEff, effs);

    assertEquals(1, multiSpeedChart.getNumberOfMaps());

    multiSpeedChart.setOperatingMW(20.0);
    double headVal = multiSpeedChart.getPolytropicHead(3500.0, 10000);
    double effVal = multiSpeedChart.getPolytropicEfficiency(3500.0, 10000);

    assertTrue(headVal > 0, "Head should be positive");
    assertTrue(effVal > 0 && effVal <= 100, "Efficiency should be between 0 and 100");
  }

  @Test
  void testSurgeHeadInterpolation() {
    chart.generateAllSurgeCurves();
    chart.setOperatingMW(20.0);

    double speed = 10000.0;
    double surgeHead18 = chart.getChartAtMW(18.0).getSurgeHeadAtSpeed(speed);
    double surgeHead22 = chart.getChartAtMW(22.0).getSurgeHeadAtSpeed(speed);
    double expectedHead = (surgeHead18 + surgeHead22) / 2.0;

    double actualHead = chart.getSurgeHeadAtSpeed(speed);
    assertEquals(expectedHead, actualHead, 0.01,
        "Surge head should be interpolated between MW maps");
  }

  @Test
  void testStoneWallHeadInterpolation() {
    chart.generateAllStoneWallCurves();
    chart.setOperatingMW(20.0);

    double speed = 10000.0;
    double stoneHead18 = chart.getChartAtMW(18.0).getStoneWallHeadAtSpeed(speed);
    double stoneHead22 = chart.getChartAtMW(22.0).getStoneWallHeadAtSpeed(speed);
    double expectedHead = (stoneHead18 + stoneHead22) / 2.0;

    double actualHead = chart.getStoneWallHeadAtSpeed(speed);
    assertEquals(expectedHead, actualHead, 0.01,
        "Stone wall head should be interpolated between MW maps");
  }

  @Test
  void testExtrapolationBelowLowestMW() {
    // Enable extrapolation
    chart.setAllowExtrapolation(true);
    assertTrue(chart.isAllowExtrapolation());

    // Set MW below the lowest map (18.0)
    double mwBelow = 16.0;
    chart.setOperatingMW(mwBelow);

    double flow = 3500;
    double speed = 10000;

    // Get values from both maps
    double head18 = chart.getChartAtMW(18.0).getPolytropicHead(flow, speed);
    double head22 = chart.getChartAtMW(22.0).getPolytropicHead(flow, speed);

    // Extrapolation uses linear extrapolation: head18 + (head18 - head22) * (18 - mwBelow) / (22 -
    // 18)
    // fraction = (18.0 - 16.0) / (22.0 - 18.0) = -0.5 (negative means extrapolate below)
    // result = head18 * (1 - (-0.5)) + head22 * (-0.5) = head18 * 1.5 - head22 * 0.5
    double expectedHead = head18 * 1.5 - head22 * 0.5;

    double actualHead = chart.getPolytropicHead(flow, speed);
    assertEquals(expectedHead, actualHead, 0.01, "Head should be extrapolated below lowest MW");
  }

  @Test
  void testExtrapolationAboveHighestMW() {
    // Enable extrapolation
    chart.setAllowExtrapolation(true);

    // Set MW above the highest map (22.0)
    double mwAbove = 26.0;
    chart.setOperatingMW(mwAbove);

    double flow = 3500;
    double speed = 10000;

    // Get values from both maps
    double head18 = chart.getChartAtMW(18.0).getPolytropicHead(flow, speed);
    double head22 = chart.getChartAtMW(22.0).getPolytropicHead(flow, speed);

    // For MW > 22.0:
    // fraction = (26.0 - 18.0) / (22.0 - 18.0) = 2.0 (above 1 means extrapolate above)
    // result = head18 * (1 - 2.0) + head22 * 2.0 = -head18 + 2 * head22
    double expectedHead = head18 * (1 - 2.0) + head22 * 2.0;

    double actualHead = chart.getPolytropicHead(flow, speed);
    assertEquals(expectedHead, actualHead, 0.01, "Head should be extrapolated above highest MW");
  }

  @Test
  void testExtrapolationDisabledBelowRange() {
    // Extrapolation is disabled by default
    assertFalse(chart.isAllowExtrapolation());

    // Set MW below the lowest map (18.0)
    chart.setOperatingMW(16.0);

    double flow = 3500;
    double speed = 10000;

    // Should use the lowest MW map (18.0) when extrapolation is disabled
    double expectedHead = chart.getChartAtMW(18.0).getPolytropicHead(flow, speed);
    double actualHead = chart.getPolytropicHead(flow, speed);

    assertEquals(expectedHead, actualHead, 0.01,
        "Should use lowest MW map when extrapolation is disabled");
  }

  @Test
  void testExtrapolationDisabledAboveRange() {
    // Extrapolation is disabled by default
    chart.setAllowExtrapolation(false);

    // Set MW above the highest map (22.0)
    chart.setOperatingMW(26.0);

    double flow = 3500;
    double speed = 10000;

    // Should use the highest MW map (22.0) when extrapolation is disabled
    double expectedHead = chart.getChartAtMW(22.0).getPolytropicHead(flow, speed);
    double actualHead = chart.getPolytropicHead(flow, speed);

    assertEquals(expectedHead, actualHead, 0.01,
        "Should use highest MW map when extrapolation is disabled");
  }

  @Test
  void testUseActualMWDefaultsToTrue() {
    // By default, useActualMW should be true
    assertTrue(chart.isUseActualMW(), "useActualMW should default to true");
  }

  @Test
  void testSetUseActualMW() {
    chart.setUseActualMW(false);
    assertFalse(chart.isUseActualMW());

    chart.setUseActualMW(true);
    assertTrue(chart.isUseActualMW());
  }

  @Test
  void testAutoMWFromInletStream() {
    // Create a gas with known MW
    SystemInterface gas = new SystemSrkEos(298.15, 30.0);
    gas.addComponent("methane", 0.9); // MW ~16.04
    gas.addComponent("ethane", 0.1); // MW ~30.07
    gas.setMixingRule("classic");

    Stream inletStream = new Stream("testInlet", gas);
    inletStream.setFlowRate(10000, "kg/hr");
    inletStream.run();

    // Get the actual MW from the stream
    double expectedMW = gas.getMolarMass() * 1000.0; // Convert kg/mol to g/mol

    // Set up chart with inlet stream
    chart.setInletStream(inletStream);
    assertTrue(chart.isUseActualMW(), "useActualMW should be true");

    // Call a method that should trigger MW update
    double flow = 3500;
    double speed = 10000;
    chart.getPolytropicHead(flow, speed);

    // The chart should now use the stream's MW
    assertEquals(expectedMW, chart.getOperatingMW(), 0.1, "Chart should use MW from inlet stream");
  }

  @Test
  void testAutoMWDisabledWhenUseActualMWFalse() {
    // Create a gas with known MW
    SystemInterface gas = new SystemSrkEos(298.15, 30.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");

    Stream inletStream = new Stream("testInlet", gas);
    inletStream.setFlowRate(10000, "kg/hr");
    inletStream.run();

    // Set a specific operating MW
    double setMW = 20.0;
    chart.setOperatingMW(setMW);

    // Disable auto MW
    chart.setUseActualMW(false);
    chart.setInletStream(inletStream);

    // Call a method - should NOT update MW from stream
    double flow = 3500;
    double speed = 10000;
    chart.getPolytropicHead(flow, speed);

    // MW should remain as set, not updated from stream
    assertEquals(setMW, chart.getOperatingMW(), 0.001,
        "MW should remain as set when useActualMW is false");
  }

  @Test
  void testInletStreamGetterSetter() {
    assertNull(chart.getInletStream(), "Inlet stream should be null initially");

    SystemInterface gas = new SystemSrkEos(298.15, 30.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");

    Stream inletStream = new Stream("testInlet", gas);
    chart.setInletStream(inletStream);

    assertEquals(inletStream, chart.getInletStream(), "Inlet stream should be set");
  }

  @Test
  void testAutoMWWithCompressorIntegration() {
    // Create a gas composition
    SystemInterface gas = new SystemSrkEos(298.15, 30.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.10);
    gas.addComponent("propane", 0.05);
    gas.setMixingRule("classic");

    Stream inletStream = new Stream("inlet", gas);
    inletStream.setFlowRate(10000, "kg/hr");
    inletStream.run();

    // Enable chart MW interpolation
    chart.setInletStream(inletStream);
    chart.setUseActualMW(true);

    // Call a method that triggers MW update
    double flow = 3500;
    double speed = 10000;
    chart.getPolytropicHead(flow, speed);

    // After calling a method, check that MW is updated from stream
    double expectedMW = inletStream.getFluid().getMolarMass() * 1000.0;
    assertEquals(expectedMW, chart.getOperatingMW(), 0.1,
        "Operating MW should be auto-updated from inlet stream");
  }

  @Test
  void testExtrapolationEfficiency() {
    chart.setAllowExtrapolation(true);

    // Extrapolate below lowest MW
    chart.setOperatingMW(15.0);

    double flow = 3500;
    double speed = 10000;

    // Get values from both maps
    double eff18 = chart.getChartAtMW(18.0).getPolytropicEfficiency(flow, speed);
    double eff22 = chart.getChartAtMW(22.0).getPolytropicEfficiency(flow, speed);

    // fraction = (18.0 - 15.0) / (22.0 - 18.0) = -0.75
    // result = eff18 * (1 - (-0.75)) + eff22 * (-0.75) = eff18 * 1.75 - eff22 * 0.75
    double expectedEff = eff18 * 1.75 - eff22 * 0.75;

    double actualEff = chart.getPolytropicEfficiency(flow, speed);
    assertEquals(expectedEff, actualEff, 0.01, "Efficiency should be extrapolated below lowest MW");
  }

  @Test
  void testExtrapolationSurgeFlow() {
    chart.generateAllSurgeCurves();
    chart.setAllowExtrapolation(true);

    // Extrapolate above highest MW
    chart.setOperatingMW(24.0);

    double speed = 10000;

    // Get values from both maps
    double surge18 = chart.getChartAtMW(18.0).getSurgeFlowAtSpeed(speed);
    double surge22 = chart.getChartAtMW(22.0).getSurgeFlowAtSpeed(speed);

    // fraction = (24.0 - 18.0) / (22.0 - 18.0) = 1.5
    // result = surge18 * (1 - 1.5) + surge22 * 1.5 = -0.5 * surge18 + 1.5 * surge22
    double expectedSurge = surge18 * (1 - 1.5) + surge22 * 1.5;

    double actualSurge = chart.getSurgeFlowAtSpeed(speed);
    assertEquals(expectedSurge, actualSurge, 0.01,
        "Surge flow should be extrapolated above highest MW");
  }

  @Test
  void testCompressorRunSetsInletStreamOnChart() {
    // Create a gas composition
    SystemInterface gas = new SystemSrkEos(298.15, 30.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.10);
    gas.addComponent("propane", 0.05);
    gas.setMixingRule("classic");

    Stream inletStream = new Stream("inlet", gas);
    inletStream.setFlowRate(10000, "kg/hr");

    ProcessSystem process = new ProcessSystem();
    process.add(inletStream);

    // Create compressor with MW interpolation chart
    Compressor comp = new Compressor("comp", inletStream);
    comp.setUsePolytropicCalc(true);
    comp.setOutletPressure(60.0);

    // Create a fresh chart and set it on the compressor
    CompressorChartMWInterpolation mwChart = new CompressorChartMWInterpolation();
    mwChart.setHeadUnit("kJ/kg");
    mwChart.addMapAtMW(18.0, chartConditions, speed18, flow18, head18, polyEff18);
    mwChart.addMapAtMW(22.0, chartConditions, speed22, flow22, head22, polyEff22);
    comp.setCompressorChart(mwChart);

    // Before running, inlet stream should not be set
    assertNull(mwChart.getInletStream(), "Inlet stream should not be set before run()");

    process.add(comp);
    process.run();

    // After running, the compressor should have set the inlet stream on the chart
    assertNotNull(mwChart.getInletStream(), "Inlet stream should be set after run()");
    assertEquals(inletStream, mwChart.getInletStream(),
        "Chart's inlet stream should be the compressor's inlet stream");

    // The chart should now use the stream's MW
    double expectedMW = inletStream.getFluid().getMolarMass() * 1000.0;
    assertEquals(expectedMW, mwChart.getOperatingMW(), 0.1,
        "Operating MW should be auto-updated from inlet stream after run()");
  }

  @Test
  void testCompressorRunAutoUpdatesMW() {
    // Create a gas with known composition
    SystemInterface gas = new SystemSrkEos(298.15, 30.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.10);
    gas.setMixingRule("classic");

    Stream inletStream = new Stream("inlet", gas);
    inletStream.setFlowRate(5000, "Am3/hr");

    ProcessSystem process = new ProcessSystem();
    process.add(inletStream);

    // Create compressor with MW interpolation chart
    Compressor comp = new Compressor("comp", inletStream);
    comp.setOutletPressure(50.0);
    comp.setSpeed(10500);

    // Create chart
    CompressorChartMWInterpolation mwChart = new CompressorChartMWInterpolation();
    mwChart.setHeadUnit("kJ/kg");
    mwChart.addMapAtMW(18.0, chartConditions, speed18, flow18, head18, polyEff18);
    mwChart.addMapAtMW(22.0, chartConditions, speed22, flow22, head22, polyEff22);
    comp.setCompressorChart(mwChart);

    process.add(comp);
    process.run();

    // Check that MW was detected from stream
    double streamMW = inletStream.getFluid().getMolarMass() * 1000.0;
    assertEquals(streamMW, mwChart.getOperatingMW(), 0.1,
        "Chart should use MW from compressor's inlet stream");

    // Now change the gas composition and run again
    gas.addComponent("propane", 0.05);
    gas.init(0);
    process.run();

    // MW should be updated to new composition
    double newStreamMW = inletStream.getFluid().getMolarMass() * 1000.0;
    assertEquals(newStreamMW, mwChart.getOperatingMW(), 0.1,
        "Chart MW should update when gas composition changes");
  }
}
