package neqsim.process.safety.risk.dynamic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for DynamicRiskSimulator.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class DynamicRiskSimulatorTest {

  private ProcessSystem processSystem;
  private DynamicRiskSimulator simulator;

  @BeforeEach
  void setUp() {
    // Create a simple process system
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");

    Separator separator = new Separator("HP Separator", feed);

    processSystem = new ProcessSystem();
    processSystem.add(feed);
    processSystem.add(separator);
    processSystem.run();

    simulator = new DynamicRiskSimulator(processSystem);
  }

  @Test
  void testSimulatorCreation() {
    assertNotNull(simulator, "Simulator should not be null");
  }

  @Test
  void testSetTimestepHours() {
    simulator.setTimestepHours(0.5);
    assertEquals(0.5, simulator.getTimestepHours(), 0.001);
  }

  @Test
  void testSetRampUpTimeHours() {
    simulator.setRampUpTimeHours(4.0);
    assertEquals(4.0, simulator.getRampUpTimeHours(), 0.001);
  }

  @Test
  void testSetShutdownTimeHours() {
    // Verify by setting and getting - method returns this for chaining
    DynamicRiskSimulator result = simulator.setShutdownTimeHours(1.5);
    assertNotNull(result);
  }

  @Test
  void testSetSimulateTransients() {
    // Verify by checking that method returns this for chaining
    DynamicRiskSimulator result = simulator.setSimulateTransients(true);
    assertNotNull(result);
  }

  @Test
  void testRampUpProfileSetting() {
    DynamicRiskSimulator result =
        simulator.setRampUpProfile(DynamicRiskSimulator.RampProfile.LINEAR);
    assertNotNull(result);

    result = simulator.setRampUpProfile(DynamicRiskSimulator.RampProfile.EXPONENTIAL);
    assertNotNull(result);

    result = simulator.setRampUpProfile(DynamicRiskSimulator.RampProfile.S_CURVE);
    assertNotNull(result);
  }

  @Test
  void testShutdownProfileSetting() {
    DynamicRiskSimulator result =
        simulator.setShutdownProfile(DynamicRiskSimulator.RampProfile.STEP);
    assertNotNull(result);
  }

  @Test
  void testRampProfileEnumValues() {
    DynamicRiskSimulator.RampProfile[] profiles = DynamicRiskSimulator.RampProfile.values();
    assertEquals(4, profiles.length);
    assertEquals(DynamicRiskSimulator.RampProfile.LINEAR,
        DynamicRiskSimulator.RampProfile.valueOf("LINEAR"));
    assertEquals(DynamicRiskSimulator.RampProfile.EXPONENTIAL,
        DynamicRiskSimulator.RampProfile.valueOf("EXPONENTIAL"));
    assertEquals(DynamicRiskSimulator.RampProfile.S_CURVE,
        DynamicRiskSimulator.RampProfile.valueOf("S_CURVE"));
    assertEquals(DynamicRiskSimulator.RampProfile.STEP,
        DynamicRiskSimulator.RampProfile.valueOf("STEP"));
  }

  @Test
  void testProductionProfileCreation() {
    ProductionProfile profile = new ProductionProfile("test_equipment");
    assertNotNull(profile, "Profile should not be null");
    assertEquals("test_equipment", profile.getEquipmentName());
  }

  @Test
  void testProductionProfileWithFailureMode() {
    ProductionProfile profile = new ProductionProfile("compressor", "TRIP");
    assertNotNull(profile, "Profile should not be null");
    assertEquals("compressor", profile.getEquipmentName());
    assertEquals("TRIP", profile.getFailureMode());
  }

  @Test
  void testProductionProfileSetters() {
    ProductionProfile profile = new ProductionProfile("test_equipment");
    profile.setBaselineProduction(1000.0);
    profile.setDegradedProduction(500.0);
    profile.setRepairDuration(10.0);
    profile.setShutdownTransientLoss(50.0);
    profile.setRampUpTransientLoss(75.0);
    profile.setSteadyStateLoss(200.0);

    assertEquals(1000.0, profile.getBaselineProduction(), 0.001);
    assertEquals(500.0, profile.getDegradedProduction(), 0.001);
    assertEquals(10.0, profile.getRepairDuration(), 0.001);
    assertEquals(50.0, profile.getShutdownTransientLoss(), 0.001);
    assertEquals(75.0, profile.getRampUpTransientLoss(), 0.001);
    assertEquals(200.0, profile.getSteadyStateLoss(), 0.001);
  }

  @Test
  void testProductionProfileCalculateTotals() {
    ProductionProfile profile = new ProductionProfile("test_equipment");
    profile.setBaselineProduction(1000.0);
    profile.setDegradedProduction(500.0);
    profile.setRepairDuration(10.0);
    profile.setShutdownTransientLoss(50.0);
    profile.setRampUpTransientLoss(75.0);
    profile.setSteadyStateLoss(200.0);
    profile.calculateTotals();

    // Total loss = shutdown + ramp-up + steady-state
    assertEquals(325.0, profile.getTotalLoss(), 0.001);
    // Total transient = shutdown + ramp-up
    assertEquals(125.0, profile.getTotalTransientLoss(), 0.001);
  }

  @Test
  void testTransientLossStatisticsCreation() {
    TransientLossStatistics stats = new TransientLossStatistics();
    assertNotNull(stats, "TransientLossStatistics should not be null");

    // Default values should be zero
    assertEquals(0.0, stats.getTotalTransientLoss(), 0.001);
    assertEquals(0.0, stats.getTotalShutdownLoss(), 0.001);
    assertEquals(0.0, stats.getTotalRampUpLoss(), 0.001);
    assertEquals(0.0, stats.getTotalSteadyStateLoss(), 0.001);
  }

  @Test
  void testTransientLossStatisticsAddProfile() {
    TransientLossStatistics stats = new TransientLossStatistics();

    ProductionProfile profile = new ProductionProfile("test_equipment");
    profile.setBaselineProduction(1000.0);
    profile.setDegradedProduction(500.0);
    profile.setRepairDuration(10.0);
    profile.setShutdownTransientLoss(100.0);
    profile.setRampUpTransientLoss(200.0);
    profile.setSteadyStateLoss(300.0);
    profile.calculateTotals();

    stats.addProfile(profile);

    assertEquals(100.0, stats.getTotalShutdownLoss(), 0.001);
    assertEquals(200.0, stats.getTotalRampUpLoss(), 0.001);
    assertEquals(300.0, stats.getTotalSteadyStateLoss(), 0.001);
    assertEquals(300.0, stats.getTotalTransientLoss(), 0.001); // shutdown + rampup
    assertEquals(600.0, stats.getTotalLoss(), 0.001);
  }

  @Test
  void testTransientLossStatisticsReset() {
    TransientLossStatistics stats = new TransientLossStatistics();

    ProductionProfile profile = new ProductionProfile("test_equipment");
    profile.setShutdownTransientLoss(100.0);
    profile.setRampUpTransientLoss(200.0);
    profile.setSteadyStateLoss(300.0);
    profile.calculateTotals();

    stats.addProfile(profile);
    stats.reset();

    assertEquals(0.0, stats.getTotalLoss(), 0.001);
  }

  @Test
  void testDynamicRiskResultCreation() {
    DynamicRiskResult result = new DynamicRiskResult();
    assertNotNull(result, "DynamicRiskResult should not be null");
  }

  @Test
  void testDynamicRiskResultSetters() {
    DynamicRiskResult result = new DynamicRiskResult();
    result.setSimulateTransients(true);
    result.setRampUpTimeHours(2.0);
    result.setTimestepHours(0.1);

    assertTrue(result.isSimulateTransients());
    assertEquals(2.0, result.getRampUpTimeHours(), 0.001);
    assertEquals(0.1, result.getTimestepHours(), 0.001);
  }

  @Test
  void testMethodChaining() {
    // Test fluent API
    DynamicRiskSimulator configured =
        simulator.setTimestepHours(0.5).setRampUpTimeHours(2.0).setShutdownTimeHours(0.5)
            .setSimulateTransients(true).setRampUpProfile(DynamicRiskSimulator.RampProfile.LINEAR)
            .setShutdownProfile(DynamicRiskSimulator.RampProfile.EXPONENTIAL);

    assertNotNull(configured, "Configured simulator should not be null");
    assertEquals(0.5, configured.getTimestepHours(), 0.001);
    assertEquals(2.0, configured.getRampUpTimeHours(), 0.001);
  }

  @Test
  void testGetTransientStats() {
    TransientLossStatistics stats = simulator.getTransientStats();
    assertNotNull(stats, "TransientStats should not be null");
  }

  @Test
  void testGetProductionProfiles() {
    java.util.List<ProductionProfile> profiles = simulator.getProductionProfiles();
    assertNotNull(profiles, "ProductionProfiles should not be null");
    assertTrue(profiles.isEmpty(), "Initial profiles should be empty");
  }
}
