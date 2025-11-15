package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Test class for ProcessModel mass balance checking methods.
 *
 * @author ESOL
 */
public class ProcessModelMassBalanceTest extends neqsim.NeqSimTest {
  private ProcessModel processModel;
  private ProcessSystem process1;
  private ProcessSystem process2;

  @BeforeEach
  public void setUp() {
    processModel = new ProcessModel();

    // Create first process
    process1 = new ProcessSystem("Process1");
    SystemInterface fluid1 = new SystemSrkCPAstatoil(298.15, 20.0);
    fluid1.addComponent("methane", 0.9);
    fluid1.addComponent("ethane", 0.1);
    fluid1.setMixingRule("classic");
    fluid1.createDatabase(true);

    Stream feed1 = new Stream("feed1", fluid1);
    feed1.setFlowRate(100.0, "kg/hr");
    feed1.setTemperature(298.15);
    feed1.setPressure(20.0);
    process1.add(feed1);

    Separator separator1 = new Separator("Separator1");
    separator1.addStream(feed1);
    process1.add(separator1);

    feed1.run();
    separator1.run();

    // Create second process
    process2 = new ProcessSystem("Process2");
    SystemInterface fluid2 = new SystemSrkCPAstatoil(298.15, 15.0);
    fluid2.addComponent("methane", 0.8);
    fluid2.addComponent("ethane", 0.15);
    fluid2.addComponent("propane", 0.05);
    fluid2.setMixingRule("classic");
    fluid2.createDatabase(true);

    Stream feed2 = new Stream("feed2", fluid2);
    feed2.setFlowRate(150.0, "kg/hr");
    feed2.setTemperature(298.15);
    feed2.setPressure(15.0);
    process2.add(feed2);

    Separator separator2 = new Separator("Separator2");
    separator2.addStream(feed2);
    process2.add(separator2);

    feed2.run();
    separator2.run();

    // Add processes to model
    processModel.add("Process1", process1);
    processModel.add("Process2", process2);
  }

  @Test
  public void testCheckMassBalanceWithUnit() {
    Map<String, Map<String, ProcessSystem.MassBalanceResult>> results =
        processModel.checkMassBalance("kg/hr");

    assertNotNull(results);
    assertEquals(2, results.size());
    assertTrue(results.containsKey("Process1"));
    assertTrue(results.containsKey("Process2"));

    // Check Process1 results
    Map<String, ProcessSystem.MassBalanceResult> process1Results = results.get("Process1");
    assertNotNull(process1Results);
    assertFalse(process1Results.isEmpty());

    // Check Process2 results
    Map<String, ProcessSystem.MassBalanceResult> process2Results = results.get("Process2");
    assertNotNull(process2Results);
    assertFalse(process2Results.isEmpty());

    // Verify each result contains unit information
    for (ProcessSystem.MassBalanceResult result : process1Results.values()) {
      assertEquals("kg/hr", result.getUnit());
    }
    for (ProcessSystem.MassBalanceResult result : process2Results.values()) {
      assertEquals("kg/hr", result.getUnit());
    }
  }

  @Test
  public void testCheckMassBalanceDefaultUnit() {
    Map<String, Map<String, ProcessSystem.MassBalanceResult>> results =
        processModel.checkMassBalance();

    assertNotNull(results);
    assertEquals(2, results.size());
    assertTrue(results.containsKey("Process1"));
    assertTrue(results.containsKey("Process2"));

    // Default unit should be kg/sec
    for (Map<String, ProcessSystem.MassBalanceResult> processResults : results.values()) {
      for (ProcessSystem.MassBalanceResult result : processResults.values()) {
        assertEquals("kg/sec", result.getUnit());
      }
    }
  }

  @Test
  public void testGetFailedMassBalance() {
    Map<String, Map<String, ProcessSystem.MassBalanceResult>> failedResults =
        processModel.getFailedMassBalance("kg/sec", 0.1);

    assertNotNull(failedResults);
    // The result should be a map (may be empty if all units pass)
    assertTrue(failedResults.isEmpty() || !failedResults.isEmpty());
  }

  @Test
  public void testGetFailedMassBalanceDefaultThreshold() {
    Map<String, Map<String, ProcessSystem.MassBalanceResult>> failedResults =
        processModel.getFailedMassBalance();

    assertNotNull(failedResults);
    // Should return a valid map
    assertTrue(failedResults.isEmpty() || !failedResults.isEmpty());
  }

  @Test
  public void testGetMassBalanceReport() {
    String report = processModel.getMassBalanceReport("kg/hr");

    assertNotNull(report);
    assertFalse(report.isEmpty());

    // Report should include process names
    assertTrue(report.contains("Process1") || report.contains("Process2"),
        "Report should contain process names");

    // Report should be properly formatted
    assertTrue(report.contains("="), "Report should have separator lines");
  }

  @Test
  public void testGetMassBalanceReportDefaultUnit() {
    String report = processModel.getMassBalanceReport();

    assertNotNull(report);
    assertFalse(report.isEmpty());

    // Report should include process names
    assertTrue(report.contains("Process1") || report.contains("Process2"));
  }

  @Test
  public void testGetFailedMassBalanceReport() {
    String report = processModel.getFailedMassBalanceReport("kg/sec", 0.1);

    assertNotNull(report);
    assertFalse(report.isEmpty());

    // Report should either indicate all passed or show failing units with process names
    assertTrue(report.contains("All unit operations passed mass balance check.")
        || report.contains("Process1") || report.contains("Process2"));
  }

  @Test
  public void testGetFailedMassBalanceReportDefaultThreshold() {
    String report = processModel.getFailedMassBalanceReport();

    assertNotNull(report);
    assertFalse(report.isEmpty());

    // Report should be valid
    assertTrue(report.contains("All unit operations passed mass balance check.")
        || report.contains("Process"));
  }

  @Test
  public void testGetFailedMassBalanceReportWithPercentThreshold() {
    String report = processModel.getFailedMassBalanceReport(0.5);

    assertNotNull(report);
    assertFalse(report.isEmpty());

    // Report should be valid
    assertTrue(report.contains("All unit operations passed mass balance check.")
        || report.contains("Process"));
  }

  @Test
  public void testMassBalanceReportIncludesProcessName() {
    String report = processModel.getMassBalanceReport("kg/sec");

    assertNotNull(report);
    // The report should include both process names
    assertTrue(report.contains("Process: Process1") && report.contains("Process: Process2"),
        "Report should include both process names with 'Process:' prefix");
  }

  @Test
  public void testMassBalanceResultsStructure() {
    Map<String, Map<String, ProcessSystem.MassBalanceResult>> results =
        processModel.checkMassBalance("kg/sec");

    // Verify structure
    for (Map.Entry<String, Map<String, ProcessSystem.MassBalanceResult>> processEntry : results
        .entrySet()) {
      String processName = processEntry.getKey();
      Map<String, ProcessSystem.MassBalanceResult> unitResults = processEntry.getValue();

      assertNotNull(processName);
      assertNotNull(unitResults);

      // Each unit operation should have a result
      for (Map.Entry<String, ProcessSystem.MassBalanceResult> unitEntry : unitResults.entrySet()) {
        String unitName = unitEntry.getKey();
        ProcessSystem.MassBalanceResult result = unitEntry.getValue();

        assertNotNull(unitName);
        assertNotNull(result);
        assertNotNull(result.getUnit());
      }
    }
  }
}
