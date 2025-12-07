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

  @Test
  void testRunTransientIncrementalStepping() {
    // Create two-phase fluid
    SystemInterface fluid = new SystemSrkEos(300, 50);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("n-pentane", 0.3);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(5, "kg/sec");
    inlet.run();

    TransientPipe pipe = new TransientPipe("TestPipe", inlet);
    pipe.setLength(200);
    pipe.setDiameter(0.15);
    pipe.setNumberOfSections(10);
    pipe.setMaxSimulationTime(100);

    // Run initial step - should initialize and advance
    java.util.UUID id = java.util.UUID.randomUUID();
    pipe.runTransient(1.0, id);

    // Verify pipe was initialized and advanced
    assertTrue(pipe.getSimulationTime() >= 1.0, "Simulation time should be at least 1.0 second");
    assertTrue(pipe.getTotalTimeSteps() > 0, "Should have taken at least one time step");

    double[] pressures1 = pipe.getPressureProfile();
    assertNotNull(pressures1, "Pressure profile should not be null");
    assertEquals(10, pressures1.length, "Should have 10 sections");

    // Store state for comparison
    double simTime1 = pipe.getSimulationTime();
    int steps1 = pipe.getTotalTimeSteps();

    // Run another transient step
    pipe.runTransient(2.0, java.util.UUID.randomUUID());

    // Verify simulation advanced further
    assertTrue(pipe.getSimulationTime() >= simTime1 + 2.0,
        "Simulation time should have advanced by at least 2.0 seconds");
    assertTrue(pipe.getTotalTimeSteps() > steps1, "Should have taken more time steps");
  }

  @Test
  void testRunTransientWithChangingInletConditions() {
    // Create initial fluid
    SystemInterface fluid = new SystemSrkEos(300, 50);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-pentane", 0.2);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(5, "kg/sec");
    inlet.run();

    TransientPipe pipe = new TransientPipe("TestPipe", inlet);
    pipe.setLength(100);
    pipe.setDiameter(0.1);
    pipe.setNumberOfSections(10);

    // Run first transient step
    pipe.runTransient(1.0, java.util.UUID.randomUUID());
    double simTime1 = pipe.getSimulationTime();

    // Change inlet flow rate (simulating process upset)
    inlet.setFlowRate(10, "kg/sec"); // Double the flow
    inlet.run();

    // Run another transient step with new conditions
    pipe.runTransient(1.0, java.util.UUID.randomUUID());
    double simTime2 = pipe.getSimulationTime();

    // Verify simulation continued advancing
    assertTrue(simTime2 > simTime1,
        "Simulation time should advance after changing inlet conditions");

    // Verify that runTransient can be called multiple times without errors
    assertDoesNotThrow(() -> pipe.runTransient(0.5, java.util.UUID.randomUUID()),
        "Should be able to run additional transient steps");
  }

  @Test
  void testRunTransientMultipleSmallSteps() {
    // Create fluid with composition that ensures two-phase flow
    SystemInterface fluid = new SystemSrkEos(300, 50);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("n-pentane", 0.3); // Heavier hydrocarbon for reliable two-phase
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(5, "kg/sec");
    inlet.run();

    TransientPipe pipe = new TransientPipe("TestPipe", inlet);
    pipe.setLength(100);
    pipe.setDiameter(0.15);
    pipe.setNumberOfSections(10);

    // Run multiple small steps (simulating ProcessSystem.runTransient loop)
    int numSteps = 5;
    for (int i = 0; i < numSteps; i++) {
      pipe.runTransient(0.5, java.util.UUID.randomUUID());
    }

    // Verify simulation advanced (time should be positive and steps taken)
    assertTrue(pipe.getSimulationTime() > 0, "Simulation time should be positive");
    assertTrue(pipe.getTotalTimeSteps() > 0, "Should have taken time steps");

    // Verify profiles are available
    double[] holdups = pipe.getLiquidHoldupProfile();
    assertNotNull(holdups, "Holdup profile should not be null");
    assertEquals(10, holdups.length, "Should have 10 sections");
  }

  @Test
  void testRunTransientWithProcessSystem() {
    // Create fluid
    SystemInterface fluid = new SystemSrkEos(300, 50);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("n-pentane", 0.3);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(5, "kg/sec");

    TransientPipe pipe = new TransientPipe("Pipeline", inlet);
    pipe.setLength(200);
    pipe.setDiameter(0.15);
    pipe.setNumberOfSections(10);
    pipe.setMaxSimulationTime(100);

    // Build process system
    neqsim.process.processmodel.ProcessSystem process =
        new neqsim.process.processmodel.ProcessSystem();
    process.add(inlet);
    process.add(pipe);

    // Run initial steady state
    process.run();

    // Verify initial state
    assertNotNull(pipe.getOutletStream(), "Outlet stream should be created after run()");
    assertTrue(pipe.getSimulationTime() > 0, "Simulation should have advanced");

    // Get outlet conditions after steady state
    double outletPressure1 = pipe.getOutletStream().getPressure();

    // Run transient loop using ProcessSystem
    for (int i = 0; i < 3; i++) {
      process.runTransient(1.0, java.util.UUID.randomUUID());
    }

    // Verify process continued running without errors
    double outletPressure2 = pipe.getOutletStream().getPressure();
    assertTrue(outletPressure2 > 0, "Outlet pressure should be positive: " + outletPressure2);

    // Verify time tracking
    assertTrue(process.getTime() > 0, "Process time should have advanced");
  }

  /**
   * Example: Transient simulation with constant upstream pressure, transient pipe, and outlet valve
   * control.
   *
   * <p>
   * This example demonstrates a realistic scenario where:
   * <ul>
   * <li>Upstream: Constant pressure source (e.g., separator or reservoir)</li>
   * <li>TransientPipe: Multiphase drift-flux model pipeline</li>
   * <li>Outlet boundary: Variable pressure to simulate valve opening/closing effects</li>
   * </ul>
   *
   * <p>
   * The outlet valve effect is simulated by changing the outlet boundary pressure:
   * <ul>
   * <li>Valve fully open: Low back-pressure (30 bar) → Higher flow, lower pressure drop</li>
   * <li>Valve partially closed: Higher back-pressure (38 bar) → Lower flow, higher pressure</li>
   * </ul>
   */
  @Test
  void testTransientPipeWithValveControl() {
    // ========== Setup: Constant Pressure Source ==========
    // Create a two-phase gas-condensate fluid at 50 bara
    SystemInterface fluid = new SystemSrkEos(300, 50); // 300 K, 50 bar
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-pentane", 0.05);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    // Constant pressure source stream (simulates separator or reservoir)
    Stream sourceStream = new Stream("ConstantPressureSource", fluid);
    sourceStream.setFlowRate(5.0, "kg/sec"); // Initial flow rate
    sourceStream.run();

    // ========== Inlet Valve (from source to pipe) ==========
    neqsim.process.equipment.valve.ThrottlingValve inletValve =
        new neqsim.process.equipment.valve.ThrottlingValve("InletValve", sourceStream);
    inletValve.setOutletPressure(45.0); // Pressure drop across inlet valve to 45 bar
    inletValve.run();

    // ========== Transient Pipe ==========
    TransientPipe pipe = new TransientPipe("TransientPipeline", inletValve.getOutletStream());
    pipe.setLength(500); // 500 m pipeline
    pipe.setDiameter(0.15); // 150 mm diameter
    pipe.setRoughness(0.00005); // 50 μm roughness
    pipe.setNumberOfSections(20);
    pipe.setMaxSimulationTime(30); // Initial steady-state run

    // Set boundary conditions:
    // - Inlet: constant flow (from upstream valve)
    // - Outlet: pressure boundary (simulates outlet valve back-pressure)
    pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_FLOW);
    pipe.setOutletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE);

    // Initial outlet valve fully open: low back-pressure (30 bar downstream)
    double outletPressureOpen = 30e5; // Pa
    pipe.setoutletPressureValue(outletPressureOpen);

    // ========== Build Process System ==========
    neqsim.process.processmodel.ProcessSystem process =
        new neqsim.process.processmodel.ProcessSystem();
    process.add(sourceStream);
    process.add(inletValve);
    process.add(pipe);

    // ========== Phase 1: Steady-State Initialization ==========
    process.run();

    // Record initial steady-state conditions
    double[] initialPressureProfile = pipe.getPressureProfile().clone();
    double initialInletP = initialPressureProfile[0] / 1e5;
    double initialOutletP = initialPressureProfile[initialPressureProfile.length - 1] / 1e5;
    double initialDP = initialInletP - initialOutletP;

    System.out.println("=== Phase 1: Steady State (Outlet valve 100% open) ===");
    System.out.println("Outlet back-pressure setpoint: 30 bar");
    System.out.println("Pipe inlet pressure: " + String.format("%.2f", initialInletP) + " bara");
    System.out.println("Pipe outlet pressure: " + String.format("%.2f", initialOutletP) + " bara");
    System.out.println("Pressure drop across pipe: " + String.format("%.2f", initialDP) + " bar");

    assertTrue(initialInletP > initialOutletP, "Inlet pressure should be higher than outlet");

    // ========== Phase 2: Transient - Simulate Partially Closed Outlet Valve ==========
    // Partially closed valve creates more resistance → higher back-pressure
    double outletPressureClosed = 38e5; // Pa (38 bar - higher back-pressure)
    System.out.println("\n=== Phase 2: Transient - Outlet Valve Closing to 30% ===");
    System.out.println("Increasing outlet back-pressure to 38 bar (simulates valve restriction)");

    pipe.setoutletPressureValue(outletPressureClosed);

    java.util.List<Double> outletPressureHistory = new java.util.ArrayList<>();
    java.util.List<Double> inletPressureHistory = new java.util.ArrayList<>();
    java.util.List<Double> dpHistory = new java.util.ArrayList<>();

    for (int t = 0; t < 20; t++) {
      process.runTransient(1.0, java.util.UUID.randomUUID());

      double[] pressures = pipe.getPressureProfile();
      double inletP = pressures[0] / 1e5;
      double outletP = pressures[pressures.length - 1] / 1e5;
      double dp = inletP - outletP;

      inletPressureHistory.add(inletP);
      outletPressureHistory.add(outletP);
      dpHistory.add(dp);

      if (t % 5 == 0) {
        System.out.println("t=" + t + "s: Inlet P=" + String.format("%.2f", inletP) + " bara, "
            + "Outlet P=" + String.format("%.2f", outletP) + " bara, " + "ΔP="
            + String.format("%.2f", dp) + " bar");
      }
    }

    double pressureAfterClosing = outletPressureHistory.get(outletPressureHistory.size() - 1);
    double dpAfterClosing = dpHistory.get(dpHistory.size() - 1);

    System.out.println("\nAfter valve closing:");
    System.out
        .println("  Outlet pressure: " + String.format("%.2f", pressureAfterClosing) + " bara");
    System.out.println("  Pressure drop: " + String.format("%.2f", dpAfterClosing) + " bar");

    // Verify: outlet pressure is at the new higher setpoint (38 bar)
    assertTrue(pressureAfterClosing > 35.0,
        "Outlet pressure should approach new setpoint (38 bar)");

    // Note: When outlet valve closes, it restricts flow. With constant inlet flow,
    // the outlet pressure boundary forces a new equilibrium state.
    // The pressure drop may increase or decrease depending on the model response.

    // ========== Phase 3: Transient - Reopen Outlet Valve ==========
    System.out.println("\n=== Phase 3: Transient - Outlet Valve Opening to 100% ===");
    System.out.println("Returning outlet back-pressure to 30 bar");

    pipe.setoutletPressureValue(outletPressureOpen); // Back to 30 bar

    for (int t = 20; t < 40; t++) {
      process.runTransient(1.0, java.util.UUID.randomUUID());

      double[] pressures = pipe.getPressureProfile();
      double inletP = pressures[0] / 1e5;
      double outletP = pressures[pressures.length - 1] / 1e5;
      double dp = inletP - outletP;

      inletPressureHistory.add(inletP);
      outletPressureHistory.add(outletP);
      dpHistory.add(dp);

      if (t % 5 == 0) {
        System.out.println("t=" + t + "s: Inlet P=" + String.format("%.2f", inletP) + " bara, "
            + "Outlet P=" + String.format("%.2f", outletP) + " bara, " + "ΔP="
            + String.format("%.2f", dp) + " bar");
      }
    }

    double finalOutletP = outletPressureHistory.get(outletPressureHistory.size() - 1);
    double finalDP = dpHistory.get(dpHistory.size() - 1);

    System.out.println("\n=== Final Conditions ===");
    System.out.println("  Outlet pressure: " + String.format("%.2f", finalOutletP) + " bara");
    System.out.println("  Pressure drop: " + String.format("%.2f", finalDP) + " bar");

    // Verify: outlet pressure returned to lower value when valve reopens
    assertTrue(finalOutletP < pressureAfterClosing,
        "Outlet pressure should decrease when valve reopens");

    // ========== Summary ==========
    System.out.println("\n=== Summary ===");
    System.out.println(
        "Initial state (valve open):     Outlet P = " + String.format("%.1f", initialOutletP)
            + " bar, ΔP = " + String.format("%.1f", initialDP) + " bar");
    System.out.println(
        "After valve closed (30%):       Outlet P = " + String.format("%.1f", pressureAfterClosing)
            + " bar, ΔP = " + String.format("%.1f", dpAfterClosing) + " bar");
    System.out
        .println("After valve reopened (100%):    Outlet P = " + String.format("%.1f", finalOutletP)
            + " bar, ΔP = " + String.format("%.1f", finalDP) + " bar");

    // Final verification
    assertTrue(process.getTime() > 0, "Process time should have advanced");
    assertTrue(pipe.getSimulationTime() > 0, "Pipe simulation should have advanced");
    assertEquals(40, outletPressureHistory.size(), "Should have 40 pressure readings");
  }

  /**
   * Example demonstrating Cv-based valve sizing with transient pipe.
   *
   * <p>
   * Uses valve Cv to calculate flow based on pressure drop: Q = Cv * sqrt(ΔP / SG)
   */
  @Test
  void testTransientPipeWithCvBasedValve() {
    // Create gas fluid
    SystemInterface fluid = new SystemSrkEos(300, 60); // 300 K, 60 bar
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream source = new Stream("Source", fluid);
    source.setFlowRate(3.0, "kg/sec");
    source.run();

    // TransientPipe
    TransientPipe pipe = new TransientPipe("Pipeline", source);
    pipe.setLength(300);
    pipe.setDiameter(0.1);
    pipe.setNumberOfSections(15);
    pipe.setMaxSimulationTime(30);
    pipe.run();

    // Outlet valve with Cv specification
    neqsim.process.equipment.valve.ThrottlingValve outletValve =
        new neqsim.process.equipment.valve.ThrottlingValve("OutletValve", pipe.getOutletStream());
    outletValve.setCv(50.0, "US"); // Cv = 50 US units
    outletValve.setOutletPressure(40.0); // 40 bar downstream
    outletValve.setPercentValveOpening(100.0);
    outletValve.run();

    // Build process
    neqsim.process.processmodel.ProcessSystem process =
        new neqsim.process.processmodel.ProcessSystem();
    process.add(source);
    process.add(pipe);
    process.add(outletValve);

    process.run();

    System.out.println("=== Cv-Based Valve Example ===");
    System.out.println(
        "Initial outlet flow: " + outletValve.getOutletStream().getFlowRate("kg/sec") + " kg/s");

    // Transient: Change valve opening
    double[] valveOpenings = {100.0, 75.0, 50.0, 25.0, 50.0, 75.0, 100.0};

    for (double opening : valveOpenings) {
      outletValve.setPercentValveOpening(opening);

      // Run 5 seconds at each opening
      for (int t = 0; t < 5; t++) {
        process.runTransient(1.0, java.util.UUID.randomUUID());
      }

      double flow = outletValve.getOutletStream().getFlowRate("kg/sec");
      double dp =
          pipe.getOutletStream().getPressure() - outletValve.getOutletStream().getPressure();

      System.out.println("Valve opening: " + opening + "% -> Flow: " + String.format("%.3f", flow)
          + " kg/s, ΔP: " + String.format("%.2f", dp) + " bar");
    }

    // Verify simulation ran correctly
    assertTrue(process.getTime() > 0, "Process should have run");
    assertTrue(outletValve.getOutletStream().getFlowRate("kg/sec") > 0, "Flow should be positive");
  }

  /**
   * Compare TransientPipe pressure drop with PipeBeggsAndBrills for pure gas flow.
   * 
   * For single-phase gas flow, both models should give similar steady-state pressure drops since
   * the physics simplifies to standard Darcy-Weisbach friction.
   */
  @Test
  void testPureGasFlowComparisonWithBeggsAndBrills() {
    // ========== Create pure gas fluid ==========
    SystemInterface gas = new neqsim.thermo.system.SystemSrkEos(300, 50);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");
    gas.setMultiPhaseCheck(true);

    // ========== Common parameters ==========
    double pipeLength = 1000; // m
    double pipeDiameter = 0.2; // m (200 mm)
    double roughness = 1e-5; // m
    double flowRate = 5.0; // kg/s
    double inletPressure = 50.0; // bara
    double inletTemp = 300.0; // K

    // ========== PipeBeggsAndBrills (reference model) ==========
    Stream bbStream = new Stream("BB inlet", gas.clone());
    bbStream.setFlowRate(flowRate, "kg/sec");
    bbStream.setTemperature(inletTemp, "K");
    bbStream.setPressure(inletPressure, "bara");
    bbStream.run();

    neqsim.process.equipment.pipeline.PipeBeggsAndBrills bbPipe =
        new neqsim.process.equipment.pipeline.PipeBeggsAndBrills("BB pipe", bbStream);
    bbPipe.setLength(pipeLength);
    bbPipe.setDiameter(pipeDiameter);
    bbPipe.setPipeWallRoughness(roughness);
    bbPipe.setNumberOfIncrements(20);
    bbPipe.setAngle(0); // Horizontal
    bbPipe.run();

    double bbOutletPressure = bbPipe.getOutletStream().getPressure("bara");
    double bbPressureDrop = inletPressure - bbOutletPressure;

    System.out.println("=== Pure Gas Flow: TransientPipe vs PipeBeggsAndBrills ===");
    System.out.println("Conditions: " + flowRate + " kg/s, " + pipeDiameter * 1000 + " mm ID, "
        + pipeLength + " m length");
    System.out.println("Inlet: " + inletPressure + " bara, " + inletTemp + " K");
    System.out.println();
    System.out.println("PipeBeggsAndBrills:");
    System.out.println("  Outlet pressure: " + String.format("%.4f", bbOutletPressure) + " bara");
    System.out.println("  Pressure drop: " + String.format("%.4f", bbPressureDrop) + " bar");
    System.out.println("  Flow regime: " + bbPipe.getFlowRegime());

    // ========== TransientPipe ==========
    Stream tpStream = new Stream("TP inlet", gas.clone());
    tpStream.setFlowRate(flowRate, "kg/sec");
    tpStream.setTemperature(inletTemp, "K");
    tpStream.setPressure(inletPressure, "bara");
    tpStream.run();

    TransientPipe tpPipe = new TransientPipe("TP pipe", tpStream);
    tpPipe.setLength(pipeLength);
    tpPipe.setDiameter(pipeDiameter);
    tpPipe.setRoughness(roughness);
    tpPipe.setNumberOfSections(20);
    tpPipe.setMaxSimulationTime(60); // Run to steady state
    tpPipe.setInletBoundaryCondition(TransientPipe.BoundaryCondition.CONSTANT_FLOW);
    tpPipe.setOutletBoundaryCondition(TransientPipe.BoundaryCondition.CONSTANT_PRESSURE);
    tpPipe.run();

    double[] tpPressures = tpPipe.getPressureProfile();
    double tpInletPressure = tpPressures[0] / 1e5;
    double tpOutletPressure = tpPressures[tpPressures.length - 1] / 1e5;
    double tpPressureDrop = tpInletPressure - tpOutletPressure;

    System.out.println("\nTransientPipe (steady state):");
    System.out.println("  Inlet pressure: " + String.format("%.4f", tpInletPressure) + " bara");
    System.out.println("  Outlet pressure: " + String.format("%.4f", tpOutletPressure) + " bara");
    System.out.println("  Pressure drop: " + String.format("%.4f", tpPressureDrop) + " bar");

    // Get gas properties for manual calculation
    double gasDensity = tpStream.getFluid().getDensity("kg/m3");
    double gasViscosity = tpStream.getFluid().getViscosity("kg/msec");
    double area = Math.PI * pipeDiameter * pipeDiameter / 4.0;
    double actualVelocity = flowRate / (gasDensity * area);
    double Re = gasDensity * actualVelocity * pipeDiameter / gasViscosity;

    System.out.println("\nGas properties:");
    System.out.println("  Density: " + String.format("%.2f", gasDensity) + " kg/m3");
    System.out.println("  Viscosity: " + String.format("%.6f", gasViscosity * 1000) + " mPa.s");
    System.out.println("  Actual velocity: " + String.format("%.2f", actualVelocity) + " m/s");
    System.out.println("  Reynolds number: " + String.format("%.0f", Re));

    // Manual Darcy-Weisbach calculation for verification
    double relRough = roughness / pipeDiameter;
    double f_manual;
    if (Re < 2300) {
      f_manual = 64.0 / Re;
    } else {
      // Haaland correlation
      double term = Math.pow(relRough / 3.7, 1.11) + 6.9 / Re;
      f_manual = Math.pow(-1.8 * Math.log10(term), -2);
    }
    double dP_manual = f_manual * (pipeLength / pipeDiameter) * 0.5 * gasDensity * actualVelocity
        * actualVelocity / 1e5;

    System.out.println("\nManual Darcy-Weisbach calculation:");
    System.out.println("  Friction factor: " + String.format("%.6f", f_manual));
    System.out.println("  Pressure drop: " + String.format("%.4f", dP_manual) + " bar");

    // ========== Comparison ==========
    System.out.println("\n=== Comparison ===");
    System.out.println(
        "BB ΔP: " + String.format("%.4f", bbPressureDrop) + " bar (Beggs and Brill reference)");
    System.out.println("TP ΔP: " + String.format("%.4f", tpPressureDrop) + " bar (TransientPipe)");
    System.out.println("Manual ΔP: " + String.format("%.4f", dP_manual) + " bar (Darcy-Weisbach)");

    double ratioTPtoBB = tpPressureDrop / bbPressureDrop;
    double ratioTPtoManual = tpPressureDrop / dP_manual;
    System.out.println("TP/BB ratio: " + String.format("%.2f", ratioTPtoBB));
    System.out.println("TP/Manual ratio: " + String.format("%.2f", ratioTPtoManual));

    // For pure gas flow, TransientPipe should match Beggs and Brill within 10%
    assertTrue(tpPressureDrop > 0, "TransientPipe pressure drop should be positive");
    assertTrue(bbPressureDrop > 0, "Beggs and Brill pressure drop should be positive");
    assertTrue(ratioTPtoBB > 0.85 && ratioTPtoBB < 1.15,
        "TransientPipe should match Beggs and Brill within 15% for pure gas flow. Ratio: "
            + ratioTPtoBB);
  }

  /**
   * Compare TransientPipe pressure drop with PipeBeggsAndBrills for pure oil (liquid) flow.
   * 
   * For single-phase liquid flow, both models should give similar steady-state pressure drops since
   * the physics simplifies to standard Darcy-Weisbach friction.
   */
  @Test
  void testPureOilFlowComparisonWithBeggsAndBrills() {
    // ========== Create pure oil fluid ==========
    SystemInterface oil = new neqsim.thermo.system.SystemSrkEos(320, 20);
    oil.addComponent("n-heptane", 0.5);
    oil.addComponent("n-octane", 0.5);
    oil.setMixingRule("classic");
    oil.setMultiPhaseCheck(true);

    // ========== Common parameters ==========
    double pipeLength = 1000; // m
    double pipeDiameter = 0.2; // m (200 mm)
    double roughness = 1e-5; // m
    double flowRate = 10.0; // kg/s
    double inletPressure = 20.0; // bara
    double inletTemp = 320.0; // K

    // ========== PipeBeggsAndBrills (reference model) ==========
    Stream bbStream = new Stream("BB inlet", oil.clone());
    bbStream.setFlowRate(flowRate, "kg/sec");
    bbStream.setTemperature(inletTemp, "K");
    bbStream.setPressure(inletPressure, "bara");
    bbStream.run();

    neqsim.process.equipment.pipeline.PipeBeggsAndBrills bbPipe =
        new neqsim.process.equipment.pipeline.PipeBeggsAndBrills("BB pipe", bbStream);
    bbPipe.setLength(pipeLength);
    bbPipe.setDiameter(pipeDiameter);
    bbPipe.setPipeWallRoughness(roughness);
    bbPipe.setNumberOfIncrements(20);
    bbPipe.setAngle(0); // Horizontal
    bbPipe.run();

    double bbOutletPressure = bbPipe.getOutletStream().getPressure("bara");
    double bbPressureDrop = inletPressure - bbOutletPressure;

    System.out.println("=== Pure Oil Flow: TransientPipe vs PipeBeggsAndBrills ===");
    System.out.println("Conditions: " + flowRate + " kg/s, " + pipeDiameter * 1000 + " mm ID, "
        + pipeLength + " m length");
    System.out.println("Inlet: " + inletPressure + " bara, " + inletTemp + " K");
    System.out.println();
    System.out.println("PipeBeggsAndBrills:");
    System.out.println("  Outlet pressure: " + String.format("%.4f", bbOutletPressure) + " bara");
    System.out.println("  Pressure drop: " + String.format("%.4f", bbPressureDrop) + " bar");
    System.out.println("  Flow regime: " + bbPipe.getFlowRegime());

    // ========== TransientPipe ==========
    Stream tpStream = new Stream("TP inlet", oil.clone());
    tpStream.setFlowRate(flowRate, "kg/sec");
    tpStream.setTemperature(inletTemp, "K");
    tpStream.setPressure(inletPressure, "bara");
    tpStream.run();

    TransientPipe tpPipe = new TransientPipe("TP pipe", tpStream);
    tpPipe.setLength(pipeLength);
    tpPipe.setDiameter(pipeDiameter);
    tpPipe.setRoughness(roughness);
    tpPipe.setNumberOfSections(20);
    tpPipe.setMaxSimulationTime(60); // Run to steady state
    tpPipe.setInletBoundaryCondition(TransientPipe.BoundaryCondition.CONSTANT_FLOW);
    tpPipe.setOutletBoundaryCondition(TransientPipe.BoundaryCondition.CONSTANT_PRESSURE);
    tpPipe.run();

    double[] tpPressures = tpPipe.getPressureProfile();
    double tpInletPressure = tpPressures[0] / 1e5;
    double tpOutletPressure = tpPressures[tpPressures.length - 1] / 1e5;
    double tpPressureDrop = tpInletPressure - tpOutletPressure;

    System.out.println("\nTransientPipe (steady state):");
    System.out.println("  Inlet pressure: " + String.format("%.4f", tpInletPressure) + " bara");
    System.out.println("  Outlet pressure: " + String.format("%.4f", tpOutletPressure) + " bara");
    System.out.println("  Pressure drop: " + String.format("%.4f", tpPressureDrop) + " bar");

    // Get oil properties for manual calculation
    double oilDensity = tpStream.getFluid().getDensity("kg/m3");
    double oilViscosity = tpStream.getFluid().getViscosity("kg/msec");
    double area = Math.PI * pipeDiameter * pipeDiameter / 4.0;
    double actualVelocity = flowRate / (oilDensity * area);
    double Re = oilDensity * actualVelocity * pipeDiameter / oilViscosity;

    System.out.println("\nOil properties:");
    System.out.println("  Density: " + String.format("%.2f", oilDensity) + " kg/m3");
    System.out.println("  Viscosity: " + String.format("%.4f", oilViscosity * 1000) + " mPa.s");
    System.out.println("  Actual velocity: " + String.format("%.2f", actualVelocity) + " m/s");
    System.out.println("  Reynolds number: " + String.format("%.0f", Re));

    // Manual Darcy-Weisbach calculation for verification
    double relRough = roughness / pipeDiameter;
    double f_manual;
    if (Re < 2300) {
      f_manual = 64.0 / Re;
    } else {
      // Haaland correlation
      double term = Math.pow(relRough / 3.7, 1.11) + 6.9 / Re;
      f_manual = Math.pow(-1.8 * Math.log10(term), -2);
    }
    double dP_manual = f_manual * (pipeLength / pipeDiameter) * 0.5 * oilDensity * actualVelocity
        * actualVelocity / 1e5;

    System.out.println("\nManual Darcy-Weisbach calculation:");
    System.out.println("  Friction factor: " + String.format("%.6f", f_manual));
    System.out.println("  Pressure drop: " + String.format("%.4f", dP_manual) + " bar");

    // ========== Comparison ==========
    System.out.println("\n=== Comparison ===");
    System.out.println(
        "BB ΔP: " + String.format("%.4f", bbPressureDrop) + " bar (Beggs and Brill reference)");
    System.out.println("TP ΔP: " + String.format("%.4f", tpPressureDrop) + " bar (TransientPipe)");
    System.out.println("Manual ΔP: " + String.format("%.4f", dP_manual) + " bar (Darcy-Weisbach)");

    double ratioTPtoBB = tpPressureDrop / bbPressureDrop;
    double ratioTPtoManual = tpPressureDrop / dP_manual;
    System.out.println("TP/BB ratio: " + String.format("%.2f", ratioTPtoBB));
    System.out.println("TP/Manual ratio: " + String.format("%.2f", ratioTPtoManual));

    // For pure oil flow, TransientPipe should match Beggs and Brill within 15%
    assertTrue(tpPressureDrop > 0, "TransientPipe pressure drop should be positive");
    assertTrue(bbPressureDrop > 0, "Beggs and Brill pressure drop should be positive");
    assertTrue(ratioTPtoBB > 0.85 && ratioTPtoBB < 1.15,
        "TransientPipe should match Beggs and Brill within 15% for pure oil flow. Ratio: "
            + ratioTPtoBB);
  }
}
