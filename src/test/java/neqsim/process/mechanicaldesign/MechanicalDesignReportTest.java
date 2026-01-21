package neqsim.process.mechanicaldesign;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for MechanicalDesignReport and ProcessInterconnectionDesign.
 *
 * <p>
 * Tests whole-process mechanical design capabilities.
 * </p>
 *
 * @author NeqSim Development Team
 */
public class MechanicalDesignReportTest {
  private MechanicalDesignReport report;

  @BeforeEach
  public void setUp() {
    // Create a simple process with multiple equipment types
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("propane", 0.1);
    fluid.addComponent("n-pentane", 0.05);
    fluid.addComponent("water", 0.05);
    fluid.setMixingRule("classic");

    // Create process system
    process = new ProcessSystem();

    // Feed stream
    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.setTemperature(50.0, "C");
    feed.setPressure(50.0, "bara");
    process.add(feed);

    // Inlet separator
    Separator inletSep = new Separator("InletSeparator", feed);
    process.add(inletSep);

    // Compressor
    Compressor comp = new Compressor("MainCompressor", inletSep.getGasOutStream());
    comp.setOutletPressure(100.0);
    comp.setIsentropicEfficiency(0.75);
    process.add(comp);

    // Cooler
    Heater cooler = new Heater("AfterCooler", comp.getOutletStream());
    cooler.setOutTemperature(40.0, "C");
    process.add(cooler);

    // JT Valve
    ThrottlingValve valve = new ThrottlingValve("JTValve", cooler.getOutletStream());
    valve.setOutletPressure(30.0);
    process.add(valve);

    // Run the process
    process.run();

    // Create the report
    report = new MechanicalDesignReport(process);
  }

  @Test
  public void testReportCreation() {
    assertNotNull(report, "Report should not be null");
  }

  @Test
  public void testDesignCalculations() {
    report.runDesignCalculations();
    assertNotNull(report.getSystemDesign(), "System design should not be null");
    assertNotNull(report.getPipingDesign(), "Piping design should not be null");
  }

  @Test
  public void testEquipmentListCSV() {
    report.runDesignCalculations();
    String csv = report.generateEquipmentListCSV();

    assertNotNull(csv, "CSV should not be null");
    assertTrue(csv.length() > 0, "CSV should have content");
    assertTrue(csv.contains("Tag"), "CSV should have header");
    assertTrue(csv.contains("MainCompressor"), "CSV should contain equipment names");

    System.out.println("Equipment List CSV:");
    System.out.println(csv);
  }

  @Test
  public void testPipingLineListCSV() {
    report.runDesignCalculations();
    String csv = report.generatePipingLineListCSV();

    assertNotNull(csv, "CSV should not be null");
    assertTrue(csv.length() > 0, "CSV should have content");
    assertTrue(csv.contains("Line Number"), "CSV should have header");

    System.out.println("Piping Line List CSV:");
    System.out.println(csv);
  }

  @Test
  public void testWeightReport() {
    report.runDesignCalculations();
    String weightReport = report.generateWeightReport();

    assertNotNull(weightReport, "Weight report should not be null");
    assertTrue(weightReport.contains("OVERALL SUMMARY"), "Should have summary section");
    assertTrue(weightReport.contains("WEIGHT BY EQUIPMENT TYPE"), "Should have type breakdown");
    assertTrue(weightReport.contains("UTILITY REQUIREMENTS"), "Should have utility section");

    System.out.println("Weight Report:");
    System.out.println(weightReport);
  }

  @Test
  public void testEquipmentDataSheets() {
    report.runDesignCalculations();
    String dataSheets = report.generateEquipmentDataSheets();

    assertNotNull(dataSheets, "Data sheets should not be null");
    assertTrue(dataSheets.contains("EQUIPMENT DATA SHEETS"), "Should have title");

    System.out.println("Equipment Data Sheets:");
    System.out.println(dataSheets);
  }

  @Test
  public void testCompleteReport() {
    report.runDesignCalculations();
    String complete = report.generateCompleteReport();

    assertNotNull(complete, "Complete report should not be null");
    assertTrue(complete.length() > 1000, "Complete report should be substantial");

    System.out.println("Complete Report Length: " + complete.length() + " characters");
  }

  @Test
  public void testProcessInterconnectionDesign() {
    ProcessInterconnectionDesign piping = new ProcessInterconnectionDesign(process);
    piping.calculatePipingEstimates();

    assertTrue(piping.getTotalPipingWeight() >= 0, "Piping weight should be non-negative");
    assertTrue(piping.getTotalPipingLength() >= 0, "Piping length should be non-negative");

    String pipingReport = piping.generatePipingReport();
    assertNotNull(pipingReport, "Piping report should not be null");

    System.out.println("Piping Report:");
    System.out.println(pipingReport);
  }

  @Test
  public void testSystemMechanicalDesignEnhancements() {
    SystemMechanicalDesign sysMech = new SystemMechanicalDesign(process);
    sysMech.runDesignCalculation();

    // Test weight breakdowns
    assertNotNull(sysMech.getWeightByEquipmentType(), "Weight by type should not be null");
    assertNotNull(sysMech.getWeightByDiscipline(), "Weight by discipline should not be null");
    assertNotNull(sysMech.getEquipmentCountByType(), "Equipment count should not be null");
    assertNotNull(sysMech.getEquipmentList(), "Equipment list should not be null");

    // Test utilities
    assertTrue(sysMech.getTotalPowerRequired() >= 0, "Power required should be non-negative");
    assertTrue(sysMech.getTotalPowerRecovered() >= 0, "Power recovered should be non-negative");

    // Test reports
    String summary = sysMech.generateSummaryReport();
    assertNotNull(summary, "Summary report should not be null");
    assertTrue(summary.contains("MECHANICAL DESIGN"), "Should have title");

    String json = sysMech.generateJsonSummary();
    assertNotNull(json, "JSON summary should not be null");
    assertTrue(json.contains("totalWeight"), "JSON should contain key fields");

    System.out.println("System Summary Report:");
    System.out.println(summary);
  }

  @Test
  public void testToJson() {
    report.runDesignCalculations();
    String json = report.toJson();

    assertNotNull(json, "JSON should not be null");
    assertTrue(json.length() > 0, "JSON should have content");

    // Verify key sections are present
    assertTrue(json.contains("\"processName\""), "Should have process name");
    assertTrue(json.contains("\"reportType\""), "Should have report type");
    assertTrue(json.contains("\"systemSummary\""), "Should have system summary");
    assertTrue(json.contains("\"utilityRequirements\""), "Should have utility requirements");
    assertTrue(json.contains("\"weightByEquipmentType\""), "Should have weight by type");
    assertTrue(json.contains("\"weightByDiscipline\""), "Should have weight by discipline");
    assertTrue(json.contains("\"equipment\""), "Should have equipment list");
    assertTrue(json.contains("\"pipingDesign\""), "Should have piping design");
    assertTrue(json.contains("\"pipeSegments\""), "Should have pipe segments");

    System.out.println("JSON Report (first 2000 chars):");
    System.out.println(json.substring(0, Math.min(2000, json.length())));
  }

  @Test
  public void testToCompactJson() {
    report.runDesignCalculations();
    String json = report.toJson();
    String compactJson = report.toCompactJson();

    assertNotNull(compactJson, "Compact JSON should not be null");
    assertTrue(compactJson.length() > 0, "Compact JSON should have content");
    assertTrue(compactJson.length() < json.length(), "Compact should be shorter than pretty");
    // Compact JSON should not have newlines with indentation
    assertTrue(!compactJson.contains("\n  "), "Should not have formatted indentation");
  }
}
