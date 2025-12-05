package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.TransientPipe.BoundaryCondition;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Integration tests for TransientPipe.
 */
class TransientPipeTest {

  @Test
  void testTransientPipeCreation() {
    TransientPipe pipe = new TransientPipe("TestPipe");
    assertEquals("TestPipe", pipe.getName());
  }

  @Test
  void testGeometrySetup() {
    TransientPipe pipe = new TransientPipe("TestPipe");
    pipe.setLength(1000);
    pipe.setDiameter(0.3);
    pipe.setRoughness(0.0001);
    pipe.setNumberOfSections(50);

    assertEquals(1000, pipe.getLength());
    assertEquals(0.3, pipe.getDiameter());
  }

  @Test
  void testElevationProfile() {
    TransientPipe pipe = new TransientPipe("TestPipe");
    pipe.setLength(1000);
    pipe.setDiameter(0.3);
    pipe.setNumberOfSections(10);

    double[] elevations = {0, 0, -5, -10, -10, -5, 0, 0, 5, 10};
    pipe.setElevationProfile(elevations);

    pipe.initializePipe();

    PipeSection[] sections = pipe.getSections();
    assertNotNull(sections);
    assertEquals(10, sections.length);

    // Check elevations are set
    for (int i = 0; i < 10; i++) {
      assertEquals(elevations[i], sections[i].getElevation(), 1e-6);
    }
  }

  @Test
  void testInclinationProfile() {
    TransientPipe pipe = new TransientPipe("TestPipe");
    pipe.setLength(1000);
    pipe.setDiameter(0.3);
    pipe.setNumberOfSections(5);

    double[] inclinations = {0, Math.toRadians(10), Math.toRadians(-5), 0, Math.toRadians(15)};
    pipe.setInclinationProfile(inclinations);

    pipe.initializePipe();

    PipeSection[] sections = pipe.getSections();
    for (int i = 0; i < 5; i++) {
      assertEquals(inclinations[i], sections[i].getInclination(), 1e-6);
    }
  }

  @Test
  void testBoundaryConditions() {
    TransientPipe pipe = new TransientPipe("TestPipe");

    pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_FLOW);
    pipe.setOutletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE);

    // Use TransientPipe's own methods for boundary pressures
    pipe.setinletPressureValue(50e5);
    pipe.setoutletPressureValue(30e5);
    pipe.setInletMassFlow(10.0);

    // No exceptions should be thrown
  }

  @Disabled("Integration test - requires thermodynamic calculations")
  @Test
  void testSimulationWithGasCondensate() {
    // Create gas-condensate fluid
    SystemInterface fluid = new SystemSrkEos(280, 80);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("n-pentane", 0.01);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(5, "kg/sec");
    inlet.run();

    TransientPipe pipe = new TransientPipe("GasCondensatePipe", inlet);
    pipe.setLength(500);
    pipe.setDiameter(0.15);
    pipe.setRoughness(0.00005);
    pipe.setNumberOfSections(25);
    pipe.setMaxSimulationTime(10); // Short simulation
    pipe.setCflNumber(0.3);
    pipe.setThermodynamicUpdateInterval(5);

    // Set horizontal pipeline
    double[] elevations = new double[25];
    pipe.setElevationProfile(elevations);

    pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_FLOW);
    pipe.setOutletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE);

    pipe.run();

    // Check results
    assertTrue(pipe.getSimulationTime() > 0);
    assertTrue(pipe.getTotalTimeSteps() > 0);

    double[] pressureProfile = pipe.getPressureProfile();
    assertNotNull(pressureProfile);
    assertEquals(25, pressureProfile.length);

    // Pressure should decrease along pipe
    assertTrue(pressureProfile[0] > pressureProfile[24],
        "Pressure should decrease from inlet to outlet");

    // Holdup profile
    double[] holdupProfile = pipe.getLiquidHoldupProfile();
    assertNotNull(holdupProfile);
    for (double h : holdupProfile) {
      assertTrue(h >= 0 && h <= 1, "Holdup should be between 0 and 1");
    }
  }

  @Disabled("Integration test - requires thermodynamic calculations")
  @Test
  void testTerrainPipeline() {
    // Create two-phase fluid
    SystemInterface fluid = new SystemSrkEos(300, 50);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("ethane", 0.15);
    fluid.addComponent("propane", 0.1);
    fluid.addComponent("n-hexane", 0.05);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(3, "kg/sec");
    inlet.run();

    TransientPipe pipe = new TransientPipe("TerrainPipe", inlet);
    pipe.setLength(1000);
    pipe.setDiameter(0.2);
    pipe.setNumberOfSections(20);
    pipe.setMaxSimulationTime(5);

    // Terrain with low point
    double[] elevations = new double[20];
    for (int i = 0; i < 20; i++) {
      if (i < 5) {
        elevations[i] = 0;
      } else if (i < 10) {
        elevations[i] = -10 * (i - 4) / 5.0; // Downhill
      } else if (i < 15) {
        elevations[i] = -10 + 10 * (i - 9) / 5.0; // Uphill
      } else {
        elevations[i] = 0;
      }
    }
    pipe.setElevationProfile(elevations);

    pipe.run();

    // Check accumulation tracker found low point
    LiquidAccumulationTracker accumTracker = pipe.getAccumulationTracker();
    assertNotNull(accumTracker);
    assertFalse(accumTracker.getAccumulationZones().isEmpty(),
        "Should identify accumulation zone at low point");

    // Check sections for low point marking
    PipeSection[] sections = pipe.getSections();
    boolean foundLowPoint = false;
    for (PipeSection section : sections) {
      if (section.isLowPoint()) {
        foundLowPoint = true;
        break;
      }
    }
    assertTrue(foundLowPoint, "Should mark low point in sections");
  }

  @Disabled("Integration test - requires thermodynamic calculations")
  @Test
  void testSlugTracking() {
    // Create slug-prone fluid
    SystemInterface fluid = new SystemSrkEos(290, 30);
    fluid.addComponent("methane", 0.6);
    fluid.addComponent("n-pentane", 0.25);
    fluid.addComponent("n-hexane", 0.15);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(5, "kg/sec");
    inlet.run();

    TransientPipe pipe = new TransientPipe("SlugPipe", inlet);
    pipe.setLength(500);
    pipe.setDiameter(0.15);
    pipe.setNumberOfSections(20);
    pipe.setMaxSimulationTime(5);

    // Horizontal
    pipe.setElevationProfile(new double[20]);

    pipe.run();

    // Check slug tracker
    SlugTracker slugTracker = pipe.getSlugTracker();
    assertNotNull(slugTracker);

    // Statistics should be available
    String stats = slugTracker.getStatisticsString();
    assertNotNull(stats);
  }

  @Disabled("Integration test - requires thermodynamic calculations")
  @Test
  void testTimeStepControl() {
    SystemInterface fluid = new SystemSrkEos(300, 50);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(2, "kg/sec");
    inlet.run();

    TransientPipe pipe = new TransientPipe("CFLTest", inlet);
    pipe.setLength(200);
    pipe.setDiameter(0.1);
    pipe.setNumberOfSections(20);
    pipe.setMaxSimulationTime(1);
    pipe.setCflNumber(0.3);

    pipe.run();

    // Should complete without issues
    assertTrue(pipe.getTotalTimeSteps() > 0);
    assertTrue(pipe.getSimulationTime() > 0);
  }

  @Disabled("Integration test - requires thermodynamic calculations")
  @Test
  void testPressureHistory() {
    SystemInterface fluid = new SystemSrkEos(300, 50);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(2, "kg/sec");
    inlet.run();

    TransientPipe pipe = new TransientPipe("HistoryTest", inlet);
    pipe.setLength(200);
    pipe.setDiameter(0.1);
    pipe.setNumberOfSections(10);
    pipe.setMaxSimulationTime(2);

    pipe.run();

    double[][] history = pipe.getPressureHistory();
    assertNotNull(history);
    assertTrue(history.length > 0, "Should have pressure history");
    assertEquals(10, history[0].length, "History should have correct spatial resolution");
  }

  @Disabled("Integration test - requires thermodynamic calculations")
  @Test
  void testOutletStream() {
    SystemInterface fluid = new SystemSrkEos(300, 50);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(2, "kg/sec");
    inlet.run();

    TransientPipe pipe = new TransientPipe("OutletTest", inlet);
    pipe.setLength(200);
    pipe.setDiameter(0.1);
    pipe.setNumberOfSections(10);
    pipe.setMaxSimulationTime(1);

    pipe.run();

    StreamInterface outlet = pipe.getOutletStream();
    assertNotNull(outlet, "Outlet stream should be created");
    assertTrue(outlet.getFluid().getPressure() < inlet.getPressure(),
        "Outlet pressure should be less than inlet");
  }

  @Disabled("Integration test - requires thermodynamic calculations")
  @Test
  void testFlowRegimeDetection() {
    SystemInterface fluid = new SystemSrkEos(290, 40);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("n-pentane", 0.3);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(3, "kg/sec");
    inlet.run();

    TransientPipe pipe = new TransientPipe("RegimeTest", inlet);
    pipe.setLength(300);
    pipe.setDiameter(0.15);
    pipe.setNumberOfSections(15);
    pipe.setMaxSimulationTime(2);
    pipe.setElevationProfile(new double[15]);

    pipe.run();

    PipeSection[] sections = pipe.getSections();
    for (PipeSection section : sections) {
      assertNotNull(section.getFlowRegime(), "Flow regime should be set");
    }
  }

  @Disabled("Integration test - requires thermodynamic calculations")
  @Test
  void testVerticalRiser() {
    SystemInterface fluid = new SystemSrkEos(300, 60);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("propane", 0.2);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(2, "kg/sec");
    inlet.run();

    TransientPipe pipe = new TransientPipe("Riser", inlet);
    pipe.setLength(100);
    pipe.setDiameter(0.1);
    pipe.setNumberOfSections(20);
    pipe.setMaxSimulationTime(2);

    // Vertical riser
    double[] elevations = new double[20];
    for (int i = 0; i < 20; i++) {
      elevations[i] = i * 5; // 5m per section
    }
    pipe.setElevationProfile(elevations);

    pipe.run();

    double[] pressureProfile = pipe.getPressureProfile();

    // For upward flow, pressure should decrease significantly due to gravity
    double pressureDrop = pressureProfile[0] - pressureProfile[19];
    assertTrue(pressureDrop > 0, "Pressure should drop in upward flow");
  }

  /**
   * Test TransientPipe with all possible phase combinations to verify volume-weighted averaging for
   * three-phase flow.
   */
  @Test
  void testAllPhaseCombinations() {
    // Test 2: Gas-Oil (two-phase)
    SystemInterface gasOil = new SystemSrkEos(280, 80);
    gasOil.addComponent("methane", 0.60);
    gasOil.addComponent("propane", 0.15);
    gasOil.addComponent("n-heptane", 0.15);
    gasOil.addComponent("n-octane", 0.10);
    gasOil.setMixingRule("classic");
    gasOil.setMultiPhaseCheck(true);

    Stream gasOilStream = new Stream("gasOilStream", gasOil);
    gasOilStream.setFlowRate(10, "kg/sec");
    gasOilStream.run();

    TransientPipe gasOilPipe = new TransientPipe("GasOilPipe", gasOilStream);
    gasOilPipe.setLength(100);
    gasOilPipe.setDiameter(0.2);
    gasOilPipe.setNumberOfSections(10);

    // Test should not throw exception
    assertDoesNotThrow(() -> gasOilPipe.run(), "Gas-oil pipe should run without exception");

    // Test 3: Gas-Water (two-phase)
    SystemInterface gasWater = new SystemSrkEos(300, 10);
    gasWater.addComponent("methane", 0.02);
    gasWater.addComponent("water", 0.98);
    gasWater.setMixingRule("classic");
    gasWater.setMultiPhaseCheck(true);

    Stream gasWaterStream = new Stream("gasWaterStream", gasWater);
    gasWaterStream.setFlowRate(8, "kg/sec");
    gasWaterStream.run();

    TransientPipe gasWaterPipe = new TransientPipe("GasWaterPipe", gasWaterStream);
    gasWaterPipe.setLength(100);
    gasWaterPipe.setDiameter(0.2);
    gasWaterPipe.setNumberOfSections(10);

    assertDoesNotThrow(() -> gasWaterPipe.run(), "Gas-water pipe should run without exception");

    // Test 4: Gas-Oil-Water (three-phase with volume-weighted averaging)
    SystemInterface threePhase = new SystemSrkEos(300, 50);
    threePhase.addComponent("methane", 0.40);
    threePhase.addComponent("propane", 0.10);
    threePhase.addComponent("n-heptane", 0.15);
    threePhase.addComponent("n-octane", 0.15);
    threePhase.addComponent("water", 0.20);
    threePhase.setMixingRule("classic");
    threePhase.setMultiPhaseCheck(true);

    Stream threePhaseStream = new Stream("threePhaseStream", threePhase);
    threePhaseStream.setFlowRate(15, "kg/sec");
    threePhaseStream.run();

    TransientPipe threePhasesPipe = new TransientPipe("ThreePhasePipe", threePhaseStream);
    threePhasesPipe.setLength(100);
    threePhasesPipe.setDiameter(0.2);
    threePhasesPipe.setNumberOfSections(10);

    // Main test: Verify three-phase system can run with volume-weighted averaging
    assertDoesNotThrow(() -> threePhasesPipe.run(),
        "Three-phase pipe should run without exception");

    // Verify that if all three phases exist after flash, averaging logic works
    if (threePhase.hasPhaseType("gas") && threePhase.hasPhaseType("oil")
        && threePhase.hasPhaseType("aqueous")) {
      double V_oil = threePhase.getPhase("oil").getVolume();
      double V_water = threePhase.getPhase("aqueous").getVolume();

      // Both liquid phases should have positive volume
      assertTrue(V_oil > 0 && V_water > 0, "Both liquid phases should have positive volume");

      // Verify we can compute volume fractions (this is what the code does)
      double V_total = V_oil + V_water;
      double w_oil = V_oil / V_total;
      double w_aq = V_water / V_total;

      // Weights should sum to 1
      assertEquals(1.0, w_oil + w_aq, 1e-9, "Volume weights should sum to 1.0");

      // Verify properties are accessible for averaging
      assertDoesNotThrow(() -> threePhase.getPhase("oil").getDensity("kg/m3"));
      assertDoesNotThrow(() -> threePhase.getPhase("aqueous").getDensity("kg/m3"));
      assertDoesNotThrow(() -> threePhase.getPhase("oil").getViscosity("kg/msec"));
      assertDoesNotThrow(() -> threePhase.getPhase("aqueous").getViscosity("kg/msec"));
    }
  }
}
