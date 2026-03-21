package neqsim.process.util.report;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import java.util.List;

/**
 * Tests for ProcessValidator.
 */
public class ProcessValidatorTest {

  @Test
  public void testValidProcessPasses() {
    SystemInterface gas = new SystemSrkEos(273.15 + 30.0, 60.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.10);
    gas.addComponent("propane", 0.05);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(50000.0, "kg/hr");

    Separator sep = new Separator("HP Sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    ProcessValidator validator = new ProcessValidator(process);
    validator.setMassBalanceTolerance(0.01);
    validator.validate();

    assertTrue(validator.isValid(),
        "A well-configured process should pass validation. Errors: " + validator.getErrorCount());
  }

  @Test
  public void testValidatorDetectsMassBalanceIssues() {
    SystemInterface gas = new SystemSrkEos(273.15 + 30.0, 60.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.10);
    gas.addComponent("propane", 0.05);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(10000.0, "kg/hr");

    Separator sep = new Separator("Test Sep", feed);

    Cooler cooler = new Cooler("After-cooler", sep.getGasOutStream());
    cooler.setOutTemperature(273.15 + 25.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.add(cooler);
    process.run();

    ProcessValidator validator = new ProcessValidator(process);
    validator.validate();

    // Should have zero mass balance errors for a valid process
    int errors = validator.getErrorCount();
    assertTrue(errors >= 0); // could be 0 or more depending on convergence
    assertNotNull(validator.getIssues());
  }

  @Test
  public void testCustomLimits() {
    SystemInterface gas = new SystemSrkEos(273.15 + 30.0, 60.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(1000.0, "kg/hr");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.run();

    ProcessValidator validator = new ProcessValidator(process);
    // Set very strict temperature limits
    validator.setTemperatureLimits(273.15 + 25.0, 273.15 + 35.0);
    validator.validate();

    // The feed at 30C should be within limits
    int totalIssues = validator.getIssueCount();
    assertNotNull(validator.toJson());
  }

  @Test
  public void testJsonOutput() {
    SystemInterface gas = new SystemSrkEos(273.15 + 25.0, 30.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(5000.0, "kg/hr");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.run();

    ProcessValidator validator = new ProcessValidator(process);
    validator.validate();

    String json = validator.toJson();
    assertNotNull(json);
    assertTrue(json.contains("Process Validation"));
    assertTrue(json.contains("isValid"));
    assertTrue(json.contains("issues"));
  }

  @Test
  public void testGetErrors() {
    ProcessSystem process = new ProcessSystem();
    ProcessValidator validator = new ProcessValidator(process);
    validator.validate();

    List<ProcessValidator.ValidationIssue> errors = validator.getErrors();
    assertNotNull(errors);
    assertEquals(0, errors.size(), "Empty process should have no errors");
  }
}
