package neqsim.process.corrosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.pipeline.PipelineMechanicalDesign;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Integration tests for pipeline corrosion analysis.
 *
 * <p>
 * Tests the full workflow of creating a pipeline with CO2-containing gas, running the process
 * simulation, and then running NORSOK M-506/M-001 corrosion analysis through the pipeline
 * convenience methods and via PipelineMechanicalDesign.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
class PipelineCorrosionIntegrationTest {

  private Stream inletStream;

  /**
   * Set up a wet gas stream with CO2 and H2S for corrosion testing.
   */
  @BeforeEach
  void setUp() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 60.0, 80.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("CO2", 0.03);
    fluid.addComponent("H2S", 0.002);
    fluid.addComponent("water", 0.01);
    fluid.addComponent("nitrogen", 0.005);
    fluid.setMixingRule("classic");

    inletStream = new Stream("feed", fluid);
    inletStream.setPressure(80.0, "bara");
    inletStream.setTemperature(60.0, "C");
    inletStream.setFlowRate(5.0, "MSm3/day");
    inletStream.run();
  }

  /**
   * Test corrosion analysis on AdiabaticPipe using convenience methods.
   */
  @Test
  void testAdiabaticPipeCorrosionAnalysis() {
    AdiabaticPipe pipe = new AdiabaticPipe("export pipeline", inletStream);
    pipe.setLength(50000.0);
    pipe.setDiameter(0.4064); // 16 inch
    pipe.setPipeWallRoughness(4.6e-5);
    pipe.run();

    // Run corrosion analysis via convenience method
    pipe.setDesignLifeYears(25.0);
    pipe.runCorrosionAnalysis();

    // Verify corrosion rate is calculated
    double corrosionRate = pipe.getCorrosionRate();
    assertTrue(corrosionRate > 0.0, "Corrosion rate should be positive for CO2-containing gas");
    assertTrue(corrosionRate < 50.0, "Corrosion rate should be reasonable (< 50 mm/yr)");

    // Verify material recommendation exists
    String material = pipe.getRecommendedMaterial();
    assertNotNull(material, "Material recommendation should not be null");
    assertTrue(material.length() > 0, "Material recommendation should not be empty");

    // Verify corrosion allowance
    double ca = pipe.getRecommendedCorrosionAllowanceMm();
    assertTrue(ca >= 0.0, "Corrosion allowance should be non-negative");
  }

  /**
   * Test corrosion analysis on PipeBeggsAndBrills with two-phase flow.
   */
  @Test
  void testBeggsAndBrillsCorrosionAnalysis() {
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("subsea pipeline", inletStream);
    pipe.setPipeWallRoughness(1.5e-5);
    pipe.setLength(25.0);
    pipe.setElevation(0.0);
    pipe.setDiameter(0.3048); // 12 inch
    pipe.run();

    // Run corrosion analysis
    pipe.setDesignLifeYears(30.0);
    pipe.setInhibitorEfficiency(0.0);
    pipe.runCorrosionAnalysis();

    double corrosionRate = pipe.getCorrosionRate();
    assertTrue(corrosionRate > 0.0, "Corrosion rate should be positive");

    String material = pipe.getRecommendedMaterial();
    assertNotNull(material);
  }

  /**
   * Test that corrosion analysis integrates with PipelineMechanicalDesign.
   */
  @Test
  void testMechanicalDesignCorrosionIntegration() {
    AdiabaticPipe pipe = new AdiabaticPipe("pipeline", inletStream);
    pipe.setLength(30000.0);
    pipe.setDiameter(0.3048);
    pipe.run();

    // Access mechanical design directly
    pipe.initMechanicalDesign();
    PipelineMechanicalDesign mechDesign = pipe.getMechanicalDesign();
    assertNotNull(mechDesign, "Mechanical design should be initialized");

    mechDesign.setDesignLifeYears(20.0);
    mechDesign.setInhibitorEfficiency(0.8); // 80% inhibitor
    mechDesign.runCorrosionAnalysis();

    // Corrosion rate should be reduced by inhibitor
    double corrosionRate = mechDesign.getCorrosionRate();
    assertTrue(corrosionRate > 0.0,
        "Corrosion rate should be positive, got " + corrosionRate);

    // Run same pipe without inhibitor for comparison
    SystemSrkEos fluid2 = new SystemSrkEos(273.15 + 60.0, 80.0);
    fluid2.addComponent("methane", 0.80);
    fluid2.addComponent("ethane", 0.05);
    fluid2.addComponent("propane", 0.03);
    fluid2.addComponent("CO2", 0.03);
    fluid2.addComponent("H2S", 0.002);
    fluid2.addComponent("water", 0.01);
    fluid2.addComponent("nitrogen", 0.005);
    fluid2.setMixingRule("classic");
    Stream stream2 = new Stream("feed2", fluid2);
    stream2.setPressure(80.0, "bara");
    stream2.setTemperature(60.0, "C");
    stream2.setFlowRate(5.0, "MSm3/day");
    stream2.run();

    AdiabaticPipe pipe2 = new AdiabaticPipe("pipeline2", stream2);
    pipe2.setLength(30000.0);
    pipe2.setDiameter(0.3048);
    pipe2.run();

    pipe2.setDesignLifeYears(20.0);
    pipe2.setInhibitorEfficiency(0.0); // no inhibitor
    pipe2.runCorrosionAnalysis();

    double uninhibitedRate = pipe2.getCorrosionRate();
    // With inhibitor should give lower corrosion rate
    assertTrue(corrosionRate < uninhibitedRate,
        "Inhibited corrosion rate should be lower than uninhibited");
  }

  /**
   * Test corrosion analysis with glycol injection.
   */
  @Test
  void testGlycolCorrectionInCorrosionAnalysis() {
    AdiabaticPipe pipe = new AdiabaticPipe("pipeline", inletStream);
    pipe.setLength(30000.0);
    pipe.setDiameter(0.3048);
    pipe.run();

    pipe.setDesignLifeYears(25.0);
    pipe.setGlycolWeightFraction(0.5); // 50% glycol
    pipe.runCorrosionAnalysis();

    double corrosionRate = pipe.getCorrosionRate();
    assertTrue(corrosionRate >= 0.0, "Corrosion rate should be non-negative with glycol");
  }

  /**
   * Test that corrosion results appear in mechanical design JSON output.
   */
  @Test
  void testCorrosionInMechanicalDesignJson() {
    AdiabaticPipe pipe = new AdiabaticPipe("pipeline", inletStream);
    pipe.setLength(30000.0);
    pipe.setDiameter(0.3048);
    pipe.run();

    pipe.initMechanicalDesign();
    PipelineMechanicalDesign mechDesign = pipe.getMechanicalDesign();
    mechDesign.runCorrosionAnalysis();

    String json = mechDesign.toJson();
    assertNotNull(json, "JSON should not be null");
    assertTrue(json.contains("corrosionAnalysis_NORSOK_M506"),
        "JSON should contain corrosion analysis section");
    assertTrue(json.contains("materialSelection_NORSOK_M001"),
        "JSON should contain material selection section");
  }

  /**
   * Test that sour service is detected for H2S-containing gas.
   */
  @Test
  void testSourServiceDetection() {
    AdiabaticPipe pipe = new AdiabaticPipe("sour pipeline", inletStream);
    pipe.setLength(10000.0);
    pipe.setDiameter(0.3048);
    pipe.run();

    pipe.initMechanicalDesign();
    PipelineMechanicalDesign mechDesign = pipe.getMechanicalDesign();
    mechDesign.runCorrosionAnalysis();

    // With 0.2 mol% H2S at 80 bar, the H2S partial pressure is ~0.16 bar,
    // which is well above the 0.003 bar sour service threshold
    NorsokM506CorrosionRate corrosionModel = mechDesign.getCorrosionModel();
    assertNotNull(corrosionModel, "Corrosion model should be initialized");
    assertTrue(corrosionModel.isSourService(),
        "Should be classified as sour service with 0.2% H2S at 80 bar");
  }
}
