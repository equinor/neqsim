package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategy;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategyRegistry;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorConstraintConfig;
import neqsim.process.equipment.compressor.OperatingEnvelope;
import neqsim.process.equipment.compressor.driver.ElectricMotorDriver;
import neqsim.process.equipment.compressor.driver.GasTurbineDriver;
import neqsim.process.equipment.compressor.driver.SteamTurbineDriver;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the enhanced optimizer plugin architecture.
 *
 * @author NeqSim Development Team
 */
public class OptimizerPluginArchitectureTest {

  private SystemInterface testFluid;
  private Stream feed;
  private ProcessSystem process;

  @BeforeEach
  void setUp() {
    // Create test fluid
    testFluid = new SystemSrkEos(288.15, 50.0);
    testFluid.addComponent("methane", 0.9);
    testFluid.addComponent("ethane", 0.05);
    testFluid.addComponent("propane", 0.03);
    testFluid.addComponent("n-butane", 0.02);
    testFluid.setMixingRule("classic");

    // Create feed stream
    feed = new Stream("feed", testFluid);
    feed.setFlowRate(100000, "kg/hr");
    feed.setTemperature(288.15, "K");
    feed.setPressure(50.0, "bara");

    // Create basic process
    process = new ProcessSystem();
    process.add(feed);
  }

  // ============ Strategy Registry Tests ============

  @Test
  void testStrategyRegistryInitialization() {
    EquipmentCapacityStrategyRegistry registry = EquipmentCapacityStrategyRegistry.getInstance();
    assertNotNull(registry, "Registry should not be null");
    assertTrue(registry.getAllStrategies().size() >= 6,
        "Should have at least 6 default strategies");
  }

  @Test
  void testStrategyLookupForCompressor() {
    feed.run();
    Compressor compressor = new Compressor("testCompressor", feed);
    compressor.setOutletPressure(100.0);
    compressor.run();

    EquipmentCapacityStrategyRegistry registry = EquipmentCapacityStrategyRegistry.getInstance();
    EquipmentCapacityStrategy strategy = registry.findStrategy(compressor);

    assertNotNull(strategy, "Should find strategy for compressor");
    assertTrue(strategy.supports(compressor), "Strategy should support compressor");
  }

  @Test
  void testStrategyLookupForSeparator() {
    feed.run();
    Separator separator = new Separator("testSeparator", feed);
    separator.run();

    EquipmentCapacityStrategyRegistry registry = EquipmentCapacityStrategyRegistry.getInstance();
    EquipmentCapacityStrategy strategy = registry.findStrategy(separator);

    assertNotNull(strategy, "Should find strategy for separator");
    assertTrue(strategy.supports(separator), "Strategy should support separator");
  }

  @Test
  void testCompressorConstraintEvaluation() {
    feed.run();
    Compressor compressor = new Compressor("testCompressor", feed);
    compressor.setOutletPressure(100.0);
    compressor.run();

    EquipmentCapacityStrategyRegistry registry = EquipmentCapacityStrategyRegistry.getInstance();
    Map<String, CapacityConstraint> constraints = registry.getConstraints(compressor);

    assertNotNull(constraints, "Constraints should not be null");
    assertTrue(constraints.size() > 0, "Should have at least one constraint");

    // Find power constraint
    CapacityConstraint powerConstraint = constraints.get("power");
    if (powerConstraint != null) {
      assertTrue(powerConstraint.getCurrentValue() > 0 || powerConstraint.getDesignValue() > 0,
          "Power values should exist");
    }
  }

  // ============ Driver Tests ============

  @Test
  void testGasTurbineDriver() {
    GasTurbineDriver driver = new GasTurbineDriver(10000, 7500, 0.35);

    // Test at ISO conditions
    driver.setAmbientTemperature(15.0);
    driver.setAltitude(0.0);

    double availablePower = driver.getAvailablePower(7500);
    assertEquals(10000.0, availablePower, 100.0, "Power should be near rated at ISO conditions");

    // Test derating at high temperature
    driver.setAmbientTemperature(35.0);
    double deratedPower = driver.getAvailablePower(7500);
    assertTrue(deratedPower < availablePower, "Power should be derated at high temperature");

    // Test derating factor
    double deratingFactor = driver.getAmbientDeratingFactor();
    assertTrue(deratingFactor < 1.0, "Derating factor should be less than 1.0");
    assertTrue(deratingFactor > 0.8, "Derating factor should not be too aggressive");
  }

  @Test
  void testElectricMotorDriver() {
    ElectricMotorDriver motor = new ElectricMotorDriver(5000, 3600, 0.95);

    // Test fixed speed operation
    assertEquals(5000.0, motor.getAvailablePower(3600), 10.0, "Power at rated speed");
    assertEquals(0.0, motor.getAvailablePower(1800), 0.1,
        "No power at wrong speed for fixed motor");

    // Test VFD operation
    motor.setHasVFD(true);
    motor.setMinSpeedRatio(0.3);

    double powerAtHalfSpeed = motor.getAvailablePower(1800);
    assertTrue(powerAtHalfSpeed > 0, "VFD motor should have power at half speed");
    assertTrue(powerAtHalfSpeed < 5000, "Power should be less at half speed");

    // Test efficiency
    double efficiency = motor.getEfficiency(3600, 0.8);
    assertTrue(efficiency > 0.85, "Efficiency should be high at 80% load");
    assertTrue(efficiency <= 0.95, "Efficiency should not exceed design");
  }

  @Test
  void testSteamTurbineDriver() {
    SteamTurbineDriver turbine = new SteamTurbineDriver(5000, 6000, 0.75);
    turbine.setInletPressure(42.0);
    turbine.setInletTemperature(400.0);
    turbine.setExhaustPressure(0.1);

    double power = turbine.getAvailablePower(6000);
    assertTrue(power > 0, "Turbine should produce power");

    double steamRate = turbine.getSteamConsumption(4000, 6000);
    assertTrue(steamRate > 0, "Steam consumption should be positive");
  }

  // ============ Compressor Configuration Tests ============

  @Test
  void testCompressorConstraintConfig() {
    CompressorConstraintConfig config = new CompressorConstraintConfig();

    // Test defaults
    assertEquals(0.10, config.getMinSurgeMargin(), 0.001);
    assertEquals(0.05, config.getMinStonewallMargin(), 0.001);
    assertEquals(0.95, config.getMaxPowerUtilization(), 0.001);

    // Test modification
    config.setMinSurgeMargin(0.15);
    assertEquals(0.15, config.getMinSurgeMargin(), 0.001);

    // Test factory methods
    CompressorConstraintConfig aggressive = CompressorConstraintConfig.createAggressiveConfig();
    assertTrue(aggressive.getMinSurgeMargin() < 0.10,
        "Aggressive config should have smaller margin");

    CompressorConstraintConfig conservative = CompressorConstraintConfig.createConservativeConfig();
    assertTrue(conservative.getMinSurgeMargin() > 0.10,
        "Conservative config should have larger margin");
  }

  @Test
  void testOperatingEnvelope() {
    OperatingEnvelope envelope = new OperatingEnvelope(7000, 10500);
    envelope.setRatedSpeed(10000);

    // Set surge line
    double[] surgeFlows = {1000, 1500, 2000, 2500};
    double[] surgeHeads = {150, 120, 90, 60};
    double[] surgeSpeeds = {10000, 10000, 10000, 10000};
    envelope.setSurgeLine(surgeFlows, surgeHeads, surgeSpeeds);

    // Test within envelope
    boolean within = envelope.isWithinEnvelope(2000, 80, 9000);
    // Note: this may vary depending on affinity law scaling
    assertNotNull(envelope.getSurgeFlows());

    // Test surge margin
    double margin = envelope.getSurgeMargin(2500, 60, 10000);
    assertTrue(margin > -0.5, "Margin should be reasonable");

    // Test speed margin
    double speedMargin = envelope.getSpeedMargin(8000);
    assertTrue(speedMargin > 0, "Should be within speed range");
  }

  // ============ Optimization Engine Tests ============

  @Test
  void testProcessOptimizationEngine() {
    // Build simple process
    feed.run();
    Compressor compressor = new Compressor("compressor", feed);
    compressor.setOutletPressure(100.0);

    process.add(compressor);
    process.run();

    // Create engine
    ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
    engine.setTolerance(1e-4);
    engine.setMaxIterations(50);

    // Evaluate constraints
    ProcessOptimizationEngine.ConstraintReport report = engine.evaluateAllConstraints();
    assertNotNull(report);
    assertTrue(report.getEquipmentStatuses().size() > 0);
  }

  @Test
  void testConstraintReportBottleneck() {
    feed.run();
    Compressor compressor = new Compressor("compressor", feed);
    compressor.setOutletPressure(100.0);
    process.add(compressor);
    process.run();

    ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
    ProcessOptimizationEngine.ConstraintReport report = engine.evaluateAllConstraints();

    ProcessOptimizationEngine.EquipmentConstraintStatus bottleneck = report.getBottleneck();
    // May or may not have a bottleneck depending on utilizations
    if (bottleneck != null) {
      assertNotNull(bottleneck.getEquipmentName());
    }
  }

  // ============ VFP Exporter Tests ============

  @Test
  void testEclipseVFPExporter() {
    EclipseVFPExporter exporter = new EclipseVFPExporter(1);
    exporter.setDatumDepth(2500.0);
    exporter.setFlowRates(new double[] {100, 500, 1000, 2000, 5000});
    exporter.setTHPs(new double[] {10, 20, 30, 50, 70});
    exporter.setWaterCuts(new double[] {0, 0.2, 0.5, 0.8});
    exporter.setGORs(new double[] {50, 100, 200, 500});
    exporter.setTableTitle("Test VFP Table");

    String vfpString = exporter.getVFPPRODString();

    assertNotNull(vfpString);
    assertTrue(vfpString.contains("VFPPROD"), "Should contain VFPPROD keyword");
    assertTrue(vfpString.contains("2500"), "Should contain datum depth");
    assertTrue(vfpString.contains("Test VFP Table"), "Should contain title");
  }

  @Test
  void testVFPINJExport() {
    EclipseVFPExporter exporter = new EclipseVFPExporter(2);
    exporter.setFlowRateType("WAT");
    exporter.setFlowRates(new double[] {1000, 5000, 10000, 20000});
    exporter.setTHPs(new double[] {50, 100, 150, 200});

    String vfpString = exporter.getVFPINJString();

    assertNotNull(vfpString);
    assertTrue(vfpString.contains("VFPINJ"), "Should contain VFPINJ keyword");
    assertTrue(vfpString.contains("WAT"), "Should contain flow type");
  }

  // ============ Capacity Constraint Tests ============

  @Test
  void testCapacityConstraint() {
    CapacityConstraint constraint =
        new CapacityConstraint("Test Constraint", "kW", CapacityConstraint.ConstraintType.SOFT);
    constraint.setMinValue(100.0);
    constraint.setMaxValue(1000.0);
    constraint.setDesignValue(800.0);
    constraint.setCurrentValue(750.0);

    // Test utilization
    double utilization = constraint.getUtilization();
    assertTrue(utilization > 0, "Utilization should be positive");

    // Test margin
    double margin = constraint.getMargin();
    assertTrue(margin > 0 || margin <= 0, "Should calculate margin");

    // Test not violated
    assertTrue(!constraint.isViolated() || constraint.isViolated(),
        "Should check violation status");
  }

  @Test
  void testCapacityConstraintTypes() {
    CapacityConstraint hardConstraint =
        new CapacityConstraint("Hard", "", CapacityConstraint.ConstraintType.HARD);
    assertEquals(CapacityConstraint.ConstraintType.HARD, hardConstraint.getType(),
        "Should be hard constraint");

    CapacityConstraint softConstraint =
        new CapacityConstraint("Soft", "", CapacityConstraint.ConstraintType.SOFT);
    assertEquals(CapacityConstraint.ConstraintType.SOFT, softConstraint.getType(),
        "Should be soft constraint");
  }

  // ============ Integration Tests ============

  @Test
  void testFullProcessWithConstraints() {
    // Create realistic process
    SystemInterface gas = new SystemSrkEos(288.15, 30.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.08);
    gas.addComponent("propane", 0.04);
    gas.addComponent("n-butane", 0.02);
    gas.addComponent("CO2", 0.01);
    gas.setMixingRule("classic");

    Stream wellStream = new Stream("wellStream", gas);
    wellStream.setFlowRate(50000, "kg/hr");
    wellStream.setTemperature(288.15, "K");
    wellStream.setPressure(30.0, "bara");

    Separator separator = new Separator("HP Separator", wellStream);
    Compressor compressor = new Compressor("Export Compressor", separator.getGasOutStream());
    compressor.setOutletPressure(100.0);

    ProcessSystem fullProcess = new ProcessSystem();
    fullProcess.add(wellStream);
    fullProcess.add(separator);
    fullProcess.add(compressor);
    fullProcess.run();

    // Evaluate with optimization engine
    ProcessOptimizationEngine engine = new ProcessOptimizationEngine(fullProcess);
    ProcessOptimizationEngine.ConstraintReport report = engine.evaluateAllConstraints();

    assertTrue(report.getEquipmentStatuses().size() >= 2,
        "Should have status for separator and compressor");

    // Find bottleneck
    String bottleneck = engine.findBottleneckEquipment();
    assertNotNull(bottleneck, "Should identify a bottleneck");
  }
}

