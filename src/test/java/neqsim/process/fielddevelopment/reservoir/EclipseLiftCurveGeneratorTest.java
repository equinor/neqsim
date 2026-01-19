package neqsim.process.fielddevelopment.reservoir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.fielddevelopment.reservoir.ReservoirCouplingExporter.FlowRateType;
import neqsim.process.fielddevelopment.reservoir.ReservoirCouplingExporter.VfpTable;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for EclipseLiftCurveGenerator.
 *
 * <p>
 * Tests the generation of Eclipse VFP lift curves using Beggs and Brill pipeline calculations.
 * </p>
 *
 * @author ESOL
 */
class EclipseLiftCurveGeneratorTest {

  private SystemInterface baseFluid;
  private PipeBeggsAndBrills pipeline;
  private Stream inlet;

  @BeforeEach
  void setUp() {
    // Create a typical reservoir fluid
    baseFluid = new SystemSrkEos(330.0, 50.0);
    baseFluid.addComponent("methane", 0.70);
    baseFluid.addComponent("ethane", 0.10);
    baseFluid.addComponent("propane", 0.05);
    baseFluid.addComponent("n-heptane", 0.10);
    baseFluid.addComponent("water", 0.05);
    baseFluid.setMixingRule("classic");
    baseFluid.init(0);
    baseFluid.init(1);

    // Create inlet stream
    inlet = new Stream("inlet", baseFluid);
    inlet.setFlowRate(50000, "kg/hr");
    inlet.run();

    // Create reference pipeline (riser)
    pipeline = new PipeBeggsAndBrills("riser", inlet);
    pipeline.setDiameter(0.1524); // 6 inch
    pipeline.setLength(2000.0); // 2000 m
    pipeline.setElevation(2000.0); // Vertical riser
    pipeline.setNumberOfIncrements(20);
    pipeline.setPipeWallRoughness(1e-5);
  }

  @Test
  void testGeneratorCreation() {
    EclipseLiftCurveGenerator generator = new EclipseLiftCurveGenerator(pipeline, baseFluid);

    // Set pipeline parameters explicitly since getElevation() returns 0 before run()
    generator.setPipelineParameters(0.1524, 2000.0, 2000.0);

    assertNotNull(generator);
    assertEquals(0.1524, generator.getPipelineDiameter(), 1e-6);
    assertEquals(2000.0, generator.getPipelineLength(), 1e-6);
    assertEquals(2000.0, generator.getPipelineElevation(), 1e-6);
  }

  @Test
  void testStandaloneGeneratorCreation() {
    EclipseLiftCurveGenerator generator =
        new EclipseLiftCurveGenerator(baseFluid, 0.2032, 5000.0, 1500.0);
    assertNotNull(generator);
    assertEquals(0.2032, generator.getPipelineDiameter(), 1e-6);
    assertEquals(5000.0, generator.getPipelineLength(), 1e-6);
    assertEquals(1500.0, generator.getPipelineElevation(), 1e-6);
  }

  @Test
  void testVfpTableGeneration() {
    EclipseLiftCurveGenerator generator = new EclipseLiftCurveGenerator(pipeline, baseFluid);
    generator.setPipelineParameters(0.1524, 2000.0, 2000.0);

    // Set smaller ranges for faster testing
    generator.setFlowRateRange(1000, 5000, 3);
    generator.setThpRange(30, 60, 3);
    generator.setWaterCutRange(0.0, 0.5, 2);
    generator.setGorRange(100, 300, 2);

    VfpTable vfp = generator.generateVfpTable(1, "TEST-WELL");

    assertNotNull(vfp);
    assertEquals(1, vfp.getTableNumber());
    assertEquals("TEST-WELL", vfp.getWellName());
    assertEquals(3, vfp.getFlowRates().length);
    assertEquals(3, vfp.getThpValues().length);
    assertEquals(2, vfp.getWctValues().length);
    assertEquals(2, vfp.getGorValues().length);

    // Verify BHP values are calculated
    double[][][][][] bhpValues = vfp.getBhpValues();
    assertNotNull(bhpValues);
    assertEquals(3, bhpValues.length); // flow rates
    assertEquals(3, bhpValues[0].length); // thp values

    // BHP should be greater than THP for production wells
    for (int iFlow = 0; iFlow < 3; iFlow++) {
      for (int iThp = 0; iThp < 3; iThp++) {
        double bhp = bhpValues[iFlow][iThp][0][0][0];
        double thp = vfp.getThpValues()[iThp];
        assertTrue(bhp >= thp, "BHP should be >= THP for production well");
      }
    }
  }

  @Test
  void testEclipseKeywordGeneration() {
    EclipseLiftCurveGenerator generator = new EclipseLiftCurveGenerator(pipeline, baseFluid);
    generator.setPipelineParameters(0.1524, 2000.0, 2000.0);

    // Small ranges for testing
    generator.setFlowRateRange(1000, 3000, 2);
    generator.setThpRange(30, 50, 2);
    generator.setWaterCutRange(0.0, 0.3, 2);
    generator.setGorRange(150, 250, 2);

    generator.generateVfpTable(1, "PROD-A1");

    String keywords = generator.getEclipseKeywords();

    // Verify Eclipse format
    assertTrue(keywords.contains("VFPPROD"));
    assertTrue(keywords.contains("PROD-A1"));
    assertTrue(keywords.contains("Beggs & Brill"));
    assertTrue(keywords.contains("-- Flow rates"));
    assertTrue(keywords.contains("-- THP"));
    assertTrue(keywords.contains("-- Water cut"));
    assertTrue(keywords.contains("-- GOR"));
    assertTrue(keywords.contains("-- BHP values"));
    assertTrue(keywords.contains("METRIC"));
  }

  @Test
  void testCsvExport() {
    EclipseLiftCurveGenerator generator = new EclipseLiftCurveGenerator(pipeline, baseFluid);
    generator.setPipelineParameters(0.1524, 2000.0, 2000.0);

    generator.setFlowRateRange(1000, 2000, 2);
    generator.setThpRange(40, 50, 2);
    generator.setWaterCutRange(0.0, 0.2, 2);
    generator.setGorRange(200, 200, 1);

    generator.generateVfpTable(1, "WELL-1");

    String csv = generator.exportToCsv();

    assertTrue(csv.contains("TableNumber,WellName,FlowRate_Sm3d"));
    assertTrue(csv.contains("WELL-1"));
    assertTrue(csv.contains("1000.0"));
    assertTrue(csv.contains("2000.0"));
  }

  @Test
  void testJsonExport() {
    EclipseLiftCurveGenerator generator = new EclipseLiftCurveGenerator(pipeline, baseFluid);
    generator.setPipelineParameters(0.1524, 2000.0, 2000.0);

    generator.setFlowRateRange(1000, 2000, 2);
    generator.setThpRange(40, 50, 2);
    generator.setWaterCutRange(0.0, 0.1, 1);
    generator.setGorRange(200, 200, 1);

    generator.generateVfpTable(1, "JSON-TEST");

    String json = generator.toJson();

    assertTrue(json.contains("\"pipelineModel\": \"Beggs and Brill\""));
    assertTrue(json.contains("\"tableNumber\": 1"));
    assertTrue(json.contains("\"wellName\": \"JSON-TEST\""));
    assertTrue(json.contains("\"bhpData\""));
  }

  @Test
  void testFlowRateTypeConfiguration() {
    EclipseLiftCurveGenerator generator = new EclipseLiftCurveGenerator(pipeline, baseFluid);
    generator.setPipelineParameters(0.1524, 2000.0, 2000.0);
    generator.setFlowRateType(FlowRateType.GAS);

    generator.setFlowRateRange(10000, 50000, 2);
    generator.setThpRange(30, 50, 2);
    generator.setWaterCutRange(0.0, 0.0, 1);
    generator.setGorRange(1000, 1000, 1);

    VfpTable vfp = generator.generateVfpTable(2, "GAS-WELL");

    assertEquals(FlowRateType.GAS, vfp.getFlowRateType());

    String keywords = generator.getEclipseKeywords();
    assertTrue(keywords.contains("'GAS'"));
  }

  @Test
  void testExplicitValueArrays() {
    EclipseLiftCurveGenerator generator = new EclipseLiftCurveGenerator(pipeline, baseFluid);
    generator.setPipelineParameters(0.1524, 2000.0, 2000.0);

    double[] rates = new double[] {500, 1500, 3000, 5000, 8000};
    double[] thps = new double[] {25, 40, 55, 70};
    double[] wcts = new double[] {0.0, 0.2, 0.5};
    double[] gors = new double[] {100, 250, 400};

    generator.setFlowRates(rates);
    generator.setThpValues(thps);
    generator.setWaterCutValues(wcts);
    generator.setGorValues(gors);

    VfpTable vfp = generator.generateVfpTable(3, "CUSTOM-WELL");

    assertEquals(5, vfp.getFlowRates().length);
    assertEquals(4, vfp.getThpValues().length);
    assertEquals(3, vfp.getWctValues().length);
    assertEquals(3, vfp.getGorValues().length);

    assertEquals(500, vfp.getFlowRates()[0], 1e-6);
    assertEquals(8000, vfp.getFlowRates()[4], 1e-6);
  }

  @Test
  void testInletTemperatureConfiguration() {
    EclipseLiftCurveGenerator generator = new EclipseLiftCurveGenerator(pipeline, baseFluid);
    generator.setPipelineParameters(0.1524, 2000.0, 2000.0);

    // Test Kelvin
    generator.setInletTemperature(350.0);

    // Test Celsius
    generator.setInletTemperature(60.0, "C");

    // Test Fahrenheit
    generator.setInletTemperature(140.0, "F");

    // Generator should work with any temperature setting
    generator.setFlowRateRange(1000, 2000, 2);
    generator.setThpRange(40, 50, 2);
    generator.setWaterCutRange(0.0, 0.0, 1);
    generator.setGorRange(200, 200, 1);

    VfpTable vfp = generator.generateVfpTable(1, "TEMP-TEST");
    assertNotNull(vfp);
  }

  @Test
  void testClearMethod() {
    EclipseLiftCurveGenerator generator = new EclipseLiftCurveGenerator(pipeline, baseFluid);
    generator.setPipelineParameters(0.1524, 2000.0, 2000.0);

    generator.setFlowRateRange(1000, 2000, 2);
    generator.setThpRange(40, 50, 2);
    generator.setWaterCutRange(0.0, 0.0, 1);
    generator.setGorRange(200, 200, 1);

    generator.generateVfpTable(1, "WELL-1");
    assertEquals(1, generator.getVfpTables().size());

    generator.clear();
    assertEquals(0, generator.getVfpTables().size());
    // After clear, only the header remains which is about 250 chars
    assertTrue(generator.getEclipseKeywords().length() < 300,
        "Keywords should only contain header after clear");
  }

  @Test
  void testMultipleTableGeneration() {
    EclipseLiftCurveGenerator generator = new EclipseLiftCurveGenerator(pipeline, baseFluid);
    generator.setPipelineParameters(0.1524, 2000.0, 2000.0);

    generator.setFlowRateRange(1000, 2000, 2);
    generator.setThpRange(40, 50, 2);
    generator.setWaterCutRange(0.0, 0.0, 1);
    generator.setGorRange(200, 200, 1);

    generator.generateVfpTable(1, "WELL-A");
    generator.generateVfpTable(2, "WELL-B");
    generator.generateVfpTable(3, "WELL-C");

    assertEquals(3, generator.getVfpTables().size());

    String keywords = generator.getEclipseKeywords();
    assertTrue(keywords.contains("WELL-A"));
    assertTrue(keywords.contains("WELL-B"));
    assertTrue(keywords.contains("WELL-C"));

    // Count VFPPROD keywords
    int count = 0;
    int index = 0;
    while ((index = keywords.indexOf("VFPPROD", index)) != -1) {
      count++;
      index++;
    }
    assertEquals(3, count);
  }

  @Test
  void testHorizontalPipeline() {
    // Create horizontal pipeline
    PipeBeggsAndBrills horizontalPipe = new PipeBeggsAndBrills("horizontal", inlet);
    horizontalPipe.setDiameter(0.2032); // 8 inch
    horizontalPipe.setLength(10000.0); // 10 km
    horizontalPipe.setElevation(0.0); // Horizontal
    horizontalPipe.setNumberOfIncrements(30);

    EclipseLiftCurveGenerator generator = new EclipseLiftCurveGenerator(horizontalPipe, baseFluid);
    generator.setPipelineParameters(0.2032, 10000.0, 0.0);

    generator.setFlowRateRange(20000, 50000, 3);
    generator.setThpRange(20, 50, 3);
    generator.setWaterCutRange(0.0, 0.3, 2);
    generator.setGorRange(100, 300, 2);

    VfpTable vfp = generator.generateVfpTable(1, "HORIZONTAL-WELL");
    assertNotNull(vfp);

    // For horizontal pipe, pressure drop should be smaller than vertical
    // BHP should still be >= THP due to friction
    double[][][][][] bhpValues = vfp.getBhpValues();
    for (int iFlow = 0; iFlow < 3; iFlow++) {
      for (int iThp = 0; iThp < 3; iThp++) {
        double bhp = bhpValues[iFlow][iThp][0][0][0];
        double thp = vfp.getThpValues()[iThp];
        assertTrue(bhp >= thp, "BHP should be >= THP even for horizontal pipe");
      }
    }
    String ans = generator.toJson();
    assertNotNull(ans);
  }

  @Test
  void testBhpIncreasesWithFlowRate() {
    EclipseLiftCurveGenerator generator = new EclipseLiftCurveGenerator(pipeline, baseFluid);
    generator.setPipelineParameters(0.1524, 2000.0, 2000.0);

    generator.setFlowRateRange(500, 10000, 5);
    generator.setThpRange(40, 40, 1); // Fixed THP
    generator.setWaterCutRange(0.2, 0.2, 1); // Fixed WCT
    generator.setGorRange(200, 200, 1); // Fixed GOR

    VfpTable vfp = generator.generateVfpTable(1, "RATE-TEST");

    double[][][][][] bhpValues = vfp.getBhpValues();

    // BHP should generally increase with flow rate (more friction loss)
    double prevBhp = bhpValues[0][0][0][0][0];
    for (int i = 1; i < 5; i++) {
      double currentBhp = bhpValues[i][0][0][0][0];
      // Allow some tolerance since correlations may have local variations
      assertTrue(currentBhp >= prevBhp - 5.0, "BHP should generally increase with flow rate");
      prevBhp = currentBhp;
    }
  }

  @Test
  void testBhpIncreasesWithThp() {
    EclipseLiftCurveGenerator generator = new EclipseLiftCurveGenerator(pipeline, baseFluid);
    generator.setPipelineParameters(0.1524, 2000.0, 2000.0);

    generator.setFlowRateRange(3000, 3000, 1); // Fixed rate
    generator.setThpRange(20, 80, 5);
    generator.setWaterCutRange(0.1, 0.1, 1);
    generator.setGorRange(150, 150, 1);

    VfpTable vfp = generator.generateVfpTable(1, "THP-TEST");

    double[][][][][] bhpValues = vfp.getBhpValues();

    // BHP should increase with THP
    for (int i = 1; i < 5; i++) {
      double prevBhp = bhpValues[0][i - 1][0][0][0];
      double currentBhp = bhpValues[0][i][0][0][0];
      assertTrue(currentBhp > prevBhp, "BHP should increase with THP");
    }
  }
}
