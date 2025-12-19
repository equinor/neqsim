package neqsim.process.sustainability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for EmissionsTracker sustainability module.
 */
public class EmissionsTrackerTest {

  private ProcessSystem process;
  private EmissionsTracker tracker;

  @BeforeEach
  void setUp() {
    // Create a simple process with energy-consuming equipment
    SystemInterface fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    Stream feedStream = new Stream("feed", fluid);
    feedStream.setFlowRate(1000.0, "kg/hr");
    feedStream.setTemperature(25.0, "C");
    feedStream.setPressure(20.0, "bara");

    Separator separator = new Separator("inlet-separator", feedStream);

    Compressor compressor = new Compressor("export-compressor", separator.getGasOutStream());
    compressor.setOutletPressure(80.0, "bara");

    Cooler aftercooler = new Cooler("aftercooler", compressor.getOutletStream());
    aftercooler.setOutTemperature(35.0, "C");

    process = new ProcessSystem();
    process.setName("GasExportProcess");
    process.add(feedStream);
    process.add(separator);
    process.add(compressor);
    process.add(aftercooler);

    tracker = new EmissionsTracker(process);
  }

  @Test
  void testCalculateEmissions() {
    process.run();

    EmissionsTracker.EmissionsReport report = tracker.calculateEmissions();

    assertNotNull(report);
    assertNotNull(report.processName);
  }

  @Test
  void testEmissionsReportContainsEquipment() {
    process.run();

    EmissionsTracker.EmissionsReport report = tracker.calculateEmissions();

    assertFalse(report.equipmentEmissions.isEmpty());

    // Should have emissions from compressor (electrical)
    boolean hasCompressorEmissions =
        report.equipmentEmissions.keySet().stream().anyMatch(k -> k.contains("compressor"));
    assertTrue(hasCompressorEmissions);
  }

  @Test
  void testGridEmissionFactor() {
    process.run();

    // Test with different grid emission factors
    tracker.setGridEmissionFactor(0.05); // Norway (low carbon)
    EmissionsTracker.EmissionsReport reportNorway = tracker.calculateEmissions();

    tracker.setGridEmissionFactor(0.4); // Global average
    EmissionsTracker.EmissionsReport reportGlobal = tracker.calculateEmissions();

    // Global average should have higher emissions than Norway
    assertTrue(reportGlobal.getTotalCO2e("kg/hr") >= reportNorway.getTotalCO2e("kg/hr"));
  }

  @Test
  void testEmissionCategories() {
    process.run();

    EmissionsTracker.EmissionsReport report = tracker.calculateEmissions();

    // Should have category breakdown
    assertNotNull(report.getEmissionsByCategory());

    // Check that categories exist
    for (EmissionsTracker.EmissionCategory category : EmissionsTracker.EmissionCategory.values()) {
      assertNotNull(category);
    }
  }

  @Test
  void testTotalCO2eCalculation() {
    process.run();

    EmissionsTracker.EmissionsReport report = tracker.calculateEmissions();

    double total = report.getTotalCO2e("kg/hr");

    // Total should be non-negative
    assertTrue(total >= 0.0);
  }

  @Test
  void testExportToCSV(@TempDir File tempDir) throws Exception {
    process.run();

    EmissionsTracker.EmissionsReport report = tracker.calculateEmissions();

    File csvFile = new File(tempDir, "emissions_report.csv");
    report.exportToCSV(csvFile.getAbsolutePath());

    assertTrue(csvFile.exists());
    assertTrue(csvFile.length() > 0);
  }

  @Test
  void testProcessSystemConvenienceMethod() {
    process.run();

    // Test the convenience method on ProcessSystem
    EmissionsTracker.EmissionsReport report = process.getEmissions();

    assertNotNull(report);
  }

  @Test
  void testProcessSystemGetEmissionsWithFactor() {
    process.run();

    // Test with custom emission factor
    EmissionsTracker.EmissionsReport report = process.getEmissions(0.1);

    assertNotNull(report);
  }

  @Test
  void testGetTotalCO2EmissionsConvenience() {
    process.run();

    double totalEmissions = process.getTotalCO2Emissions();

    assertTrue(totalEmissions >= 0.0);
  }

  @Test
  void testEmptyProcessEmissions() {
    ProcessSystem emptyProcess = new ProcessSystem();
    emptyProcess.setName("EmptyProcess");

    EmissionsTracker emptyTracker = new EmissionsTracker(emptyProcess);
    EmissionsTracker.EmissionsReport report = emptyTracker.calculateEmissions();

    assertNotNull(report);
    assertTrue(report.equipmentEmissions.isEmpty());
  }

  @Test
  void testDefaultGridEmissionFactor() {
    // Default should be a reasonable global average
    double defaultFactor = tracker.getGridEmissionFactor();
    assertTrue(defaultFactor > 0.0);
    assertTrue(defaultFactor < 1.0); // Less than 1 kg CO2/kWh
  }

  @Test
  void testGetTotalPower() {
    process.run();

    EmissionsTracker.EmissionsReport report = tracker.calculateEmissions();

    double powerKW = report.getTotalPower("kW");
    double powerMW = report.getTotalPower("MW");

    assertTrue(powerKW >= 0.0);
    assertTrue(Math.abs(powerKW / 1000.0 - powerMW) < 0.001);
  }

  @Test
  void testRecordSnapshot() {
    process.run();

    tracker.recordSnapshot();
    tracker.recordSnapshot();

    assertTrue(tracker.getHistory().size() >= 2);
  }
}
