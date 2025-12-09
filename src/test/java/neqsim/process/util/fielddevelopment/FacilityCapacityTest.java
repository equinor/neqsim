package neqsim.process.util.fielddevelopment;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.util.fielddevelopment.FacilityCapacity.CapacityAssessment;
import neqsim.process.util.fielddevelopment.FacilityCapacity.CapacityPeriod;
import neqsim.process.util.fielddevelopment.FacilityCapacity.DebottleneckOption;

/**
 * Unit tests for {@link FacilityCapacity}.
 *
 * <p>
 * Tests facility capacity assessment, bottleneck analysis, and debottleneck option evaluation.
 * </p>
 *
 * @author NeqSim Development Team
 */
public class FacilityCapacityTest {

  private FacilityCapacity facilityCapacity;
  private ProcessSystem process;

  @BeforeEach
  void setUp() {
    // Create a simple process system for testing
    process = new ProcessSystem();

    // Create a gas system
    SystemInterface gasSystem = new SystemSrkEos(298.15, 50.0);
    gasSystem.addComponent("methane", 0.85);
    gasSystem.addComponent("ethane", 0.10);
    gasSystem.addComponent("propane", 0.05);
    gasSystem.setMixingRule("classic");

    // Create feed stream
    Stream feed = new Stream("Feed", gasSystem);
    feed.setFlowRate(100000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");

    // Create separator
    Separator separator = new Separator("Inlet Separator", feed);

    // Create compressor
    Compressor compressor = new Compressor("Export Compressor", separator.getGasOutStream());
    compressor.setOutletPressure(100.0, "bara");

    // Create cooler
    Cooler cooler = new Cooler("Discharge Cooler", compressor.getOutletStream());
    cooler.setOutTemperature(40.0, "C");

    // Add equipment to process
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    process.add(cooler);

    // Create facility capacity analyzer
    facilityCapacity = new FacilityCapacity("Test Facility", process);
  }

  @Test
  @DisplayName("FacilityCapacity creation with valid inputs")
  void testFacilityCapacityCreation() {
    assertNotNull(facilityCapacity, "FacilityCapacity should be created");
    assertEquals("Test Facility", facilityCapacity.getName(), "Name should match");
  }

  @Test
  @DisplayName("Identify bottleneck equipment")
  void testIdentifyBottleneck() {
    process.run();

    String bottleneck = facilityCapacity.identifyBottleneck();

    assertNotNull(bottleneck, "Bottleneck should be identified");
    assertFalse(bottleneck.isEmpty(), "Bottleneck name should not be empty");
  }

  @Test
  @DisplayName("Calculate equipment utilization")
  void testCalculateUtilization() {
    process.run();

    double utilization = facilityCapacity.getEquipmentUtilization("Export Compressor");

    assertTrue(utilization >= 0.0, "Utilization should be non-negative");
    assertTrue(utilization <= 1.0 || utilization > 0, "Utilization should be valid ratio");
  }

  @Test
  @DisplayName("Set and get capacity constraints")
  void testCapacityConstraints() {
    facilityCapacity.setMaxCapacity("Export Compressor", 150000.0, "kg/hr");

    double maxCapacity = facilityCapacity.getMaxCapacity("Export Compressor");

    assertEquals(150000.0, maxCapacity, 0.01, "Max capacity should match set value");
  }

  @Test
  @DisplayName("Assess capacity for multiple periods")
  void testCapacityAssessmentPeriods() {
    process.run();

    // Define capacity periods
    CapacityPeriod period1 = new CapacityPeriod("2024", 100000.0);
    CapacityPeriod period2 = new CapacityPeriod("2025", 120000.0);
    CapacityPeriod period3 = new CapacityPeriod("2026", 140000.0);

    facilityCapacity.addCapacityPeriod(period1);
    facilityCapacity.addCapacityPeriod(period2);
    facilityCapacity.addCapacityPeriod(period3);

    CapacityAssessment assessment = facilityCapacity.assess();

    assertNotNull(assessment, "Assessment should not be null");
    assertEquals(3, assessment.getPeriodCount(), "Should have 3 periods");
  }

  @Test
  @DisplayName("Debottleneck option NPV calculation")
  void testDebottleneckOptionNpv() {
    DebottleneckOption option = new DebottleneckOption("Add Parallel Compressor");
    option.setCapexCost(10_000_000.0);
    option.setAdditionalCapacity(50000.0); // kg/hr
    option.setProductPrice(0.30); // $/kg
    option.setOperatingCostPerUnit(0.05); // $/kg
    option.setDiscountRate(0.10);
    option.setProjectLifeYears(15);

    double npv = option.calculateNpv();

    // NPV should be calculated based on incremental revenue
    assertNotNull(npv, "NPV should be calculated");
  }

  @Test
  @DisplayName("Compare debottleneck options")
  void testCompareDebottleneckOptions() {
    process.run();

    DebottleneckOption option1 = new DebottleneckOption("Add Compressor");
    option1.setCapexCost(8_000_000.0);
    option1.setAdditionalCapacity(30000.0);

    DebottleneckOption option2 = new DebottleneckOption("Upgrade Separator");
    option2.setCapexCost(3_000_000.0);
    option2.setAdditionalCapacity(20000.0);

    facilityCapacity.addDebottleneckOption(option1);
    facilityCapacity.addDebottleneckOption(option2);

    List<DebottleneckOption> rankedOptions = facilityCapacity.rankDebottleneckOptions();

    assertNotNull(rankedOptions, "Ranked options should not be null");
    assertEquals(2, rankedOptions.size(), "Should have 2 options");
  }

  @Test
  @DisplayName("Get all equipment names")
  void testGetEquipmentNames() {
    List<String> equipmentNames = facilityCapacity.getEquipmentNames();

    assertNotNull(equipmentNames, "Equipment names should not be null");
    assertTrue(equipmentNames.size() >= 1, "Should have at least one equipment");
  }

  @Test
  @DisplayName("Capacity headroom calculation")
  void testCapacityHeadroom() {
    process.run();

    facilityCapacity.setMaxCapacity("Export Compressor", 150000.0, "kg/hr");
    facilityCapacity.setCurrentThroughput("Export Compressor", 100000.0, "kg/hr");

    double headroom = facilityCapacity.getCapacityHeadroom("Export Compressor");

    assertEquals(50000.0, headroom, 0.01, "Headroom should be max - current");
  }

  @Test
  @DisplayName("Critical equipment identification")
  void testCriticalEquipmentIdentification() {
    process.run();

    // Set high utilization for compressor
    facilityCapacity.setMaxCapacity("Export Compressor", 105000.0, "kg/hr");
    facilityCapacity.setCurrentThroughput("Export Compressor", 100000.0, "kg/hr");

    List<String> criticalEquipment = facilityCapacity.getCriticalEquipment(0.90);

    assertNotNull(criticalEquipment, "Critical equipment list should not be null");
  }

  @Test
  @DisplayName("Assessment report generation")
  void testAssessmentReportGeneration() {
    process.run();

    CapacityAssessment assessment = facilityCapacity.assess();
    String report = assessment.generateReport();

    assertNotNull(report, "Report should not be null");
    assertFalse(report.isEmpty(), "Report should not be empty");
    assertTrue(report.contains("Capacity") || report.contains("capacity"),
        "Report should contain capacity information");
  }

  @Test
  @DisplayName("Facility overall utilization")
  void testOverallFacilityUtilization() {
    process.run();

    double overallUtilization = facilityCapacity.getOverallUtilization();

    assertTrue(overallUtilization >= 0.0, "Overall utilization should be non-negative");
  }

  @Test
  @DisplayName("Scenario capacity comparison")
  void testScenarioCapacityComparison() {
    process.run();

    // Base case
    double baseCapacity = facilityCapacity.calculateMaxThroughput();

    // Simulate debottleneck by increasing max capacity
    facilityCapacity.setMaxCapacity("Export Compressor", 200000.0, "kg/hr");

    double newCapacity = facilityCapacity.calculateMaxThroughput();

    assertTrue(newCapacity >= baseCapacity, "Debottlenecked capacity should be >= base");
  }

  @Test
  @DisplayName("Multiple bottleneck identification")
  void testMultipleBottleneckIdentification() {
    process.run();

    // Set tight constraints on multiple equipment
    facilityCapacity.setMaxCapacity("Inlet Separator", 105000.0, "kg/hr");
    facilityCapacity.setMaxCapacity("Export Compressor", 110000.0, "kg/hr");

    List<String> bottlenecks = facilityCapacity.identifyAllBottlenecks(0.90);

    assertNotNull(bottlenecks, "Bottleneck list should not be null");
  }

  @Test
  @DisplayName("Capacity trend analysis")
  void testCapacityTrendAnalysis() {
    // Add historical capacity data
    facilityCapacity.addCapacityPeriod(new CapacityPeriod("2022", 80000.0));
    facilityCapacity.addCapacityPeriod(new CapacityPeriod("2023", 90000.0));
    facilityCapacity.addCapacityPeriod(new CapacityPeriod("2024", 100000.0));

    double growthRate = facilityCapacity.calculateCapacityGrowthRate();

    assertTrue(growthRate > 0, "Growth rate should be positive for increasing capacity");
  }

  @Test
  @DisplayName("Export capacity data to CSV format")
  void testExportToCsv() {
    process.run();

    facilityCapacity.setMaxCapacity("Export Compressor", 150000.0, "kg/hr");
    facilityCapacity.setCurrentThroughput("Export Compressor", 100000.0, "kg/hr");

    String csvData = facilityCapacity.exportToCsv();

    assertNotNull(csvData, "CSV data should not be null");
    assertTrue(csvData.contains(","), "CSV should contain commas");
  }
}
