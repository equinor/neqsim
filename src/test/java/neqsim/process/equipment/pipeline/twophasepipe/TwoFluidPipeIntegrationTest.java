package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Integration tests for TwoFluidPipe transient multiphase flow solver.
 *
 * <p>
 * These tests validate the complete solver workflow including initialization, steady-state solving,
 * and transient simulation with thermodynamic coupling.
 * </p>
 */
class TwoFluidPipeIntegrationTest {
  private static final int NUM_SECTIONS = 5; // Reduced for faster tests
  private TwoFluidPipe pipe;
  private static Stream sharedInletStream;
  private static SystemInterface sharedFluid;

  @BeforeAll
  static void setUpOnce() {
    // Create a two-phase gas-condensate fluid (shared across tests)
    sharedFluid = new SystemSrkEos(303.15, 50.0); // 30°C, 50 bar
    sharedFluid.addComponent("methane", 0.85);
    sharedFluid.addComponent("ethane", 0.08);
    sharedFluid.addComponent("propane", 0.04);
    sharedFluid.addComponent("n-heptane", 0.03);
    sharedFluid.setMixingRule("classic");

    // Create inlet stream (shared)
    sharedInletStream = new Stream("inlet", sharedFluid);
    sharedInletStream.setFlowRate(10.0, "kg/sec");
    sharedInletStream.setTemperature(30.0, "C");
    sharedInletStream.setPressure(50.0, "bara");
    sharedInletStream.run();
  }

  @BeforeEach
  void setUp() {
    // Create fresh pipe for each test using shared stream
    pipe = new TwoFluidPipe("test-pipe", sharedInletStream);
    pipe.setLength(500.0); // 500 m (reduced)
    pipe.setDiameter(0.1); // 100 mm
    pipe.setNumberOfSections(NUM_SECTIONS);
  }

  @Test
  void testPipeConfiguration() {
    assertEquals(NUM_SECTIONS, pipe.getNumberOfSections(), "Should have correct sections");
    assertEquals(500.0, pipe.getLength(), 1e-6, "Length should be 500 m");
    assertEquals(0.1, pipe.getDiameter(), 1e-6, "Diameter should be 0.1 m");
  }

  @Test
  void testRoughnessConfiguration() {
    pipe.setRoughness(5e-5);
    assertEquals(5e-5, pipe.getRoughness(), 1e-15, "Roughness should be set correctly");
  }

  @Test
  void testSteadyStateRun() {
    pipe.run();

    // After run, profiles should be populated
    double[] pressureProfile = pipe.getPressureProfile();
    assertTrue(pressureProfile.length > 0, "Pressure profile should be populated after run");
  }

  @Test
  void testLiquidInventoryCalculation() {
    pipe.run();

    double liquidInventory = pipe.getLiquidInventory("m3");
    assertTrue(liquidInventory >= 0, "Liquid inventory should be non-negative");

    // Test with different units
    double liquidInventoryBbl = pipe.getLiquidInventory("bbl");
    assertTrue(liquidInventoryBbl >= 0, "Liquid inventory in bbl should be non-negative");
  }

  @Test
  void testPressureProfileAfterRun() {
    pipe.run();

    double[] pressureProfile = pipe.getPressureProfile();
    assertNotNull(pressureProfile, "Pressure profile should not be null");

    // All pressures should be positive
    for (int i = 0; i < pressureProfile.length; i++) {
      assertTrue(pressureProfile[i] >= 0, "Pressure should be non-negative at index " + i);
    }
  }

  @Test
  void testHoldupProfileInValidRange() {
    pipe.run();

    double[] holdupProfile = pipe.getLiquidHoldupProfile();
    assertNotNull(holdupProfile, "Holdup profile should not be null");

    for (int i = 0; i < holdupProfile.length; i++) {
      assertTrue(holdupProfile[i] >= 0.0, "Liquid holdup should be >= 0 at index " + i);
      assertTrue(holdupProfile[i] <= 1.0, "Liquid holdup should be <= 1 at index " + i);
    }
  }

  @Test
  void testVelocityProfiles() {
    pipe.run();

    double[] gasVel = pipe.getGasVelocityProfile();
    double[] liqVel = pipe.getLiquidVelocityProfile();

    assertNotNull(gasVel, "Gas velocity profile should not be null");
    assertNotNull(liqVel, "Liquid velocity profile should not be null");

    // Velocities should be finite
    for (double v : gasVel) {
      assertTrue(Double.isFinite(v), "Gas velocity should be finite");
    }
    for (double v : liqVel) {
      assertTrue(Double.isFinite(v), "Liquid velocity should be finite");
    }
  }

  @Test
  void testTemperatureProfile() {
    pipe.run();

    double[] tempProfile = pipe.getTemperatureProfile();
    assertNotNull(tempProfile, "Temperature profile should not be null");

    for (double t : tempProfile) {
      assertTrue(t > 0, "Temperature should be positive (in Kelvin)");
    }
  }

  @Test
  void testFlowRegimeDetection() {
    pipe.run();

    // Flow regime should be detected for each section
    PipeSection.FlowRegime[] regimes = pipe.getFlowRegimeProfile();
    assertNotNull(regimes, "Flow regime profile should not be null");

    for (PipeSection.FlowRegime regime : regimes) {
      assertNotNull(regime, "Each section should have a flow regime assigned");
    }
  }

  @Test
  void testPositionProfile() {
    pipe.run();

    double[] positions = pipe.getPositionProfile();
    assertNotNull(positions, "Position profile should not be null");
    assertEquals(NUM_SECTIONS, positions.length, "Should have positions for all sections");

    // Positions should be increasing
    for (int i = 1; i < positions.length; i++) {
      assertTrue(positions[i] > positions[i - 1], "Positions should be increasing");
    }
  }

  @Test
  void testSimulationTimeTracking() {
    pipe.run();

    double simTime = pipe.getSimulationTime();
    assertTrue(simTime >= 0, "Simulation time should be non-negative");
  }

  @Test
  void testCflNumberSetting() {
    pipe.setCflNumber(0.3);
    // Just verify no exception is thrown
    pipe.run();
  }

  @Test
  void testElevationProfileSetting() {
    double[] elevations = new double[NUM_SECTIONS];
    for (int i = 0; i < NUM_SECTIONS; i++) {
      elevations[i] = 5.0 * Math.sin(i * Math.PI / (NUM_SECTIONS - 1));
    }
    pipe.setElevationProfile(elevations);

    pipe.run();

    // Should complete without error
    double[] pressures = pipe.getPressureProfile();
    assertTrue(pressures.length > 0, "Should have pressure profile after run with elevations");
  }

  @Test
  void testSlugTracker() {
    pipe.setEnableSlugTracking(true);
    pipe.run();

    SlugTracker tracker = pipe.getSlugTracker();
    assertNotNull(tracker, "Slug tracker should not be null");
  }

  @Test
  void testAccumulationTracker() {
    pipe.run();

    LiquidAccumulationTracker tracker = pipe.getAccumulationTracker();
    assertNotNull(tracker, "Accumulation tracker should not be null");
  }

  @Test
  void testMassTransferSetting() {
    pipe.setIncludeMassTransfer(true);
    pipe.run();
    // Just verify no exception
  }

  @Test
  void testEnergyEquationSetting() {
    pipe.setIncludeEnergyEquation(true);
    pipe.run();
    // Just verify no exception
  }

  @Test
  void testThermodynamicUpdateInterval() {
    pipe.setThermodynamicUpdateInterval(5);
    pipe.run();
    // Just verify no exception
  }

  @Test
  void testOutletPressureSetting() {
    pipe.setOutletPressure(40.0, "bara");
    pipe.run();
    // Just verify no exception
  }
}
