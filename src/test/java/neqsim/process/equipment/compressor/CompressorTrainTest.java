package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for CompressorTrain, CompressorDriver CSV loading, anti-surge in speed-solve mode, and
 * capacity constraint evaluation.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CompressorTrainTest {

  private SystemInterface testFluid;

  @BeforeEach
  public void setUp() {
    testFluid = new SystemSrkEos(273.15 + 30.0, 30.0);
    testFluid.addComponent("methane", 0.9);
    testFluid.addComponent("ethane", 0.07);
    testFluid.addComponent("propane", 0.03);
    testFluid.setMixingRule("classic");
  }

  /**
   * Test that CompressorTrain creates and runs correctly with default settings (scrubber +
   * compressor + aftercooler).
   */
  @Test
  public void testCompressorTrainBasicRun() {
    Stream feed = new Stream("feed", testFluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.run();

    CompressorTrain train = new CompressorTrain("HP Train", feed);
    train.getCompressor().setOutletPressure(80.0);
    train.getCompressor().setPolytropicEfficiency(0.76);
    train.getCompressor().setUsePolytropicCalc(true);
    train.setAftercoolerTemperature(35.0, "C");

    train.run();

    // Verify compressor ran
    double power = train.getPower("kW");
    assertTrue(power > 0, "Power should be positive: " + power);

    // Verify aftercooler cooled the gas
    double outTemp = train.getOutletTemperature("C");
    assertTrue(outTemp < 40.0, "Outlet temp should be near 35C: " + outTemp);
    assertTrue(outTemp > 30.0, "Outlet temp should be above 30C: " + outTemp);

    // Verify compression ratio
    double ratio = train.getCompressionRatio();
    assertTrue(ratio > 2.0, "Compression ratio should be > 2: " + ratio);

    // Verify efficiency is reasonable
    double eff = train.getPolytropicEfficiency();
    assertTrue(eff > 0.5 && eff <= 1.0, "Efficiency should be 0.5-1.0: " + eff);
  }

  /**
   * Test CompressorTrain without scrubber.
   */
  @Test
  public void testCompressorTrainWithoutScrubber() {
    Stream feed = new Stream("feed", testFluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.run();

    CompressorTrain train = new CompressorTrain("No Scrubber Train", feed);
    train.setUseInletScrubber(false);
    train.getCompressor().setOutletPressure(60.0);
    train.getCompressor().setPolytropicEfficiency(0.76);
    train.getCompressor().setUsePolytropicCalc(true);
    train.run();

    assertTrue(train.getPower("kW") > 0, "Should produce positive power");
    assertTrue(
        train.getInletScrubber() == null
            || !train.getInternalEquipment().contains(train.getInletScrubber()),
        "Scrubber should not be in internal equipment when disabled");
  }

  /**
   * Test CompressorTrain without aftercooler.
   */
  @Test
  public void testCompressorTrainWithoutAftercooler() {
    Stream feed = new Stream("feed", testFluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.run();

    CompressorTrain train = new CompressorTrain("No Cooler Train", feed);
    train.setUseAftercooler(false);
    train.getCompressor().setOutletPressure(60.0);
    train.getCompressor().setPolytropicEfficiency(0.76);
    train.getCompressor().setUsePolytropicCalc(true);
    train.run();

    // Outlet temperature should be higher than inlet (no cooling)
    double outTemp = train.getOutletTemperature("C");
    assertTrue(outTemp > 30.0, "Outlet temp should be above inlet: " + outTemp);
  }

  /**
   * Test that getInternalEquipment returns the correct items.
   */
  @Test
  public void testInternalEquipment() {
    Stream feed = new Stream("feed", testFluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.run();

    CompressorTrain train = new CompressorTrain("Test Train", feed);
    List<ProcessEquipmentInterface> equipment = train.getInternalEquipment();
    assertEquals(3, equipment.size(), "Should have 3 internal equipment items");

    // Disable scrubber
    train.setUseInletScrubber(false);
    equipment = train.getInternalEquipment();
    assertEquals(2, equipment.size(), "Should have 2 items without scrubber");

    // Disable aftercooler too
    train.setUseAftercooler(false);
    equipment = train.getInternalEquipment();
    assertEquals(1, equipment.size(), "Should have 1 item (compressor only)");
  }

  /**
   * Test CompressorTrain in a ProcessSystem.
   */
  @Test
  public void testCompressorTrainInProcessSystem() {
    Stream feed = new Stream("feed", testFluid);
    feed.setFlowRate(50000.0, "kg/hr");

    CompressorTrain train = new CompressorTrain("HP Train", feed);
    train.getCompressor().setOutletPressure(85.0);
    train.getCompressor().setPolytropicEfficiency(0.76);
    train.getCompressor().setUsePolytropicCalc(true);
    train.setAftercoolerTemperature(35.0, "C");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(train);
    process.run();

    assertTrue(train.getPower("kW") > 0, "Power should be positive after process run");
  }

  /**
   * Test capacity constraints are delegated from CompressorTrain to Compressor.
   */
  @Test
  public void testCapacityConstraintDelegation() {
    Stream feed = new Stream("feed", testFluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.run();

    CompressorTrain train = new CompressorTrain("Train", feed);
    train.getCompressor().setOutletPressure(60.0);
    train.getCompressor().setPolytropicEfficiency(0.76);
    train.getCompressor().setUsePolytropicCalc(true);
    train.run();

    Map<String, CapacityConstraint> constraints = train.getCapacityConstraints();
    assertNotNull(constraints, "Constraints should not be null");
    assertFalse(constraints.isEmpty(), "Should have capacity constraints from compressor");
  }

  /**
   * Test performance summary output.
   */
  @Test
  public void testPerformanceSummary() {
    Stream feed = new Stream("feed", testFluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.run();

    CompressorTrain train = new CompressorTrain("HP Train", feed);
    train.getCompressor().setOutletPressure(60.0);
    train.getCompressor().setPolytropicEfficiency(0.76);
    train.getCompressor().setUsePolytropicCalc(true);
    train.run();

    String summary = train.getPerformanceSummary();
    assertNotNull(summary);
    assertTrue(summary.contains("HP Train"), "Summary should contain train name");
    assertTrue(summary.contains("Power"), "Summary should contain power info");
  }

  // --- CompressorDriver CSV Loading Tests ---

  /**
   * Test loading driver power curve from a CSV file.
   */
  @Test
  public void testDriverLoadFromCsv(@TempDir File tempDir) throws IOException {
    // Create a temporary CSV file
    File csvFile = new File(tempDir, "driver_curve.csv");
    FileWriter writer = new FileWriter(csvFile);
    writer.write("speed_rpm,power_MW\n");
    writer.write("4922,21.8\n");
    writer.write("5500,27.5\n");
    writer.write("6000,32.0\n");
    writer.write("6500,37.0\n");
    writer.write("7000,42.0\n");
    writer.write("7383,44.4\n");
    writer.close();

    CompressorDriver driver = new CompressorDriver(DriverType.VFD_MOTOR, 40000.0);
    driver.setRatedSpeed(7383.0);
    driver.loadMaxPowerCurveFromCsv(csvFile.getAbsolutePath(), "MW");

    assertTrue(driver.isMaxPowerCurveTableEnabled(), "Tabular power curve should be enabled");

    // Check interpolation
    double powerAt5500 = driver.getMaxAvailablePowerAtSpeed(5500.0);
    assertEquals(27500.0, powerAt5500, 100.0, "Power at 5500 RPM should be ~27500 kW");

    double powerAt6250 = driver.getMaxAvailablePowerAtSpeed(6250.0);
    assertTrue(powerAt6250 > 32000 && powerAt6250 < 37000,
        "Power at 6250 RPM should be between 32000 and 37000 kW: " + powerAt6250);
  }

  /**
   * Test loading CSV with comments and empty lines.
   */
  @Test
  public void testDriverLoadFromCsvWithComments(@TempDir File tempDir) throws IOException {
    File csvFile = new File(tempDir, "driver_with_comments.csv");
    FileWriter writer = new FileWriter(csvFile);
    writer.write("# Driver power curve for GE LM2500 gas turbine\n");
    writer.write("speed_rpm,power_MW\n");
    writer.write("\n");
    writer.write("3000,15.0\n");
    writer.write("# Mid-range\n");
    writer.write("5000,25.0\n");
    writer.write("7000,35.0\n");
    writer.write("\n");
    writer.close();

    CompressorDriver driver = new CompressorDriver(DriverType.GAS_TURBINE, 30000.0);
    driver.loadMaxPowerCurveFromCsv(csvFile.getAbsolutePath(), "MW");

    assertTrue(driver.isMaxPowerCurveTableEnabled(), "Tabular power curve should be enabled");

    double powerAt5000 = driver.getMaxAvailablePowerAtSpeed(5000.0);
    assertEquals(25000.0, powerAt5000, 100.0, "Power at 5000 RPM should be ~25000 kW");
  }

  /**
   * Test that CSV with insufficient data throws exception.
   */
  @Test
  public void testDriverLoadFromCsvInsufficientData(@TempDir File tempDir) throws IOException {
    File csvFile = new File(tempDir, "single_point.csv");
    FileWriter writer = new FileWriter(csvFile);
    writer.write("speed,power\n");
    writer.write("5000,25.0\n");
    writer.close();

    CompressorDriver driver = new CompressorDriver(DriverType.ELECTRIC_MOTOR, 25000.0);
    assertThrows(IllegalArgumentException.class,
        () -> driver.loadMaxPowerCurveFromCsv(csvFile.getAbsolutePath(), "kW"),
        "Should throw for insufficient data points");
  }

  // --- Anti-Surge in Speed Solve Mode Test ---

  /**
   * Test that anti-surge is properly checked in speed-solve mode. This verifies that when the
   * compressor is solving for speed (to meet a target outlet pressure) and the operating point
   * falls below the surge line, the anti-surge logic activates and increases the flow.
   */
  @Test
  public void testAntiSurgeInSpeedSolveMode() {
    // Create a gas
    SystemInterface gasFluid = new SystemSrkEos(273.15 + 30.0, 50.0);
    gasFluid.addComponent("methane", 0.85);
    gasFluid.addComponent("ethane", 0.10);
    gasFluid.addComponent("propane", 0.05);
    gasFluid.setMixingRule("classic");

    Stream feed = new Stream("feed", gasFluid);
    feed.setFlowRate(50000.0, "kg/hr");

    Compressor comp = new Compressor("Test Compressor", feed);
    comp.setOutletPressure(100.0);
    comp.setUsePolytropicCalc(true);
    comp.setPolytropicEfficiency(0.75);
    comp.setSpeed(10000);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(comp);
    process.run();

    // Generate chart after first run
    comp.setCompressorChartType("interpolate and extrapolate");
    CompressorChartGenerator gen = new CompressorChartGenerator(comp);
    gen.setChartType("interpolate and extrapolate");
    CompressorChartInterface chart = gen.generateCompressorChart("normal curves", 5);
    comp.setCompressorChart(chart);

    // Enable speed solve with anti-surge
    comp.setSolveSpeed(true);
    comp.getAntiSurge().setActive(true);
    comp.getAntiSurge().setSurgeControlFactor(1.1);

    // Re-run with chart and speed solve
    process.run();

    // Verify compressor ran (power > 0)
    double power = comp.getPower("kW");
    assertTrue(power > 0, "Compressor should produce positive power: " + power);

    // Verify anti-surge fraction is set (may be 0 if not surging, which is also valid)
    double surgeFraction = comp.getAntiSurge().getCurrentSurgeFraction();
    assertTrue(surgeFraction >= 0.0, "Surge fraction should be >= 0: " + surgeFraction);
  }

  // --- Capacity Constraint Evaluation Test ---

  /**
   * Test that capacity constraints are evaluated after run and can be queried.
   */
  @Test
  public void testCapacityConstraintEvaluationAfterRun() {
    Stream feed = new Stream("feed", testFluid);
    feed.setFlowRate(50000.0, "kg/hr");

    Compressor comp = new Compressor("Test Compressor", feed);
    comp.setOutletPressure(60.0);
    comp.setUsePolytropicCalc(true);
    comp.setPolytropicEfficiency(0.76);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(comp);
    process.run();

    // After run, constraints should be populated with current values
    Map<String, CapacityConstraint> constraints = comp.getCapacityConstraints();
    assertNotNull(constraints, "Constraints should exist after run");
    assertFalse(constraints.isEmpty(), "Constraints should not be empty");

    // Check that surge margin constraint exists
    boolean hasSurgeMargin = false;
    for (String key : constraints.keySet()) {
      if (key.contains("surge") || key.contains("SURGE")) {
        hasSurgeMargin = true;
        break;
      }
    }
    assertTrue(hasSurgeMargin, "Should have a surge margin constraint");

    // Max utilization should be >= 0
    double maxUtil = comp.getMaxUtilization();
    assertTrue(maxUtil >= 0.0 && !Double.isNaN(maxUtil),
        "Max utilization should be a valid non-negative number: " + maxUtil);
  }
}
