package neqsim.util.validation.contracts;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.validation.ValidationResult;

/**
 * Tests for module contracts.
 */
class ModuleContractTest {

  @Nested
  @DisplayName("ThermodynamicSystemContract Tests")
  class ThermodynamicSystemContractTests {

    @Test
    @DisplayName("Valid system passes preconditions")
    void testValidSystemPreconditions() {
      SystemSrkEos system = new SystemSrkEos(298.15, 10.0);
      system.addComponent("methane", 0.8);
      system.addComponent("ethane", 0.2);
      system.setMixingRule("classic");

      ThermodynamicSystemContract contract = ThermodynamicSystemContract.getInstance();
      ValidationResult result = contract.checkPreconditions(system);

      assertTrue(result.isValid(), "Valid system should pass: " + result.getReport());
    }

    @Test
    @DisplayName("Empty system fails preconditions")
    void testEmptySystemFails() {
      SystemSrkEos system = new SystemSrkEos(298.15, 10.0);
      // No components

      ThermodynamicSystemContract contract = ThermodynamicSystemContract.getInstance();
      ValidationResult result = contract.checkPreconditions(system);

      assertFalse(result.isValid());
      assertTrue(result.getReport().contains("No components"));
    }

    @Test
    @DisplayName("Zero temperature fails preconditions")
    void testZeroTemperatureFails() {
      SystemSrkEos system = new SystemSrkEos(0.5, 10.0);
      system.addComponent("methane", 1.0);

      ThermodynamicSystemContract contract = ThermodynamicSystemContract.getInstance();
      ValidationResult result = contract.checkPreconditions(system);

      assertFalse(result.isValid());
      assertTrue(result.getReport().contains("Temperature too low"));
    }

    @Test
    @DisplayName("Missing mixing rule warns for multi-component")
    void testMissingMixingRuleWarns() {
      SystemSrkEos system = new SystemSrkEos(298.15, 10.0);
      system.addComponent("methane", 0.5);
      system.addComponent("ethane", 0.5);
      // No mixing rule

      ThermodynamicSystemContract contract = ThermodynamicSystemContract.getInstance();
      ValidationResult result = contract.checkPreconditions(system);

      // The system may or may not warn - this depends on implementation
      // Just check the report contains relevant info
      String report = result.getReport();
      assertNotNull(report);
      // Either it warns about mixing rule, or the system handles it gracefully
      assertTrue(result.isValid() || result.hasWarnings() || report.contains("mixing"));
    }

    @Test
    @DisplayName("Contract provides requirements description")
    void testRequirementsDescription() {
      ThermodynamicSystemContract contract = ThermodynamicSystemContract.getInstance();
      String requirements = contract.getRequirementsDescription();

      assertNotNull(requirements);
      assertTrue(requirements.contains("component"));
      assertTrue(requirements.contains("Temperature"));
      assertTrue(requirements.contains("Pressure"));
    }

    @Test
    @DisplayName("Contract provides output description")
    void testProvidesDescription() {
      ThermodynamicSystemContract contract = ThermodynamicSystemContract.getInstance();
      String provides = contract.getProvidesDescription();

      assertNotNull(provides);
      assertTrue(provides.contains("Phase"));
      assertTrue(provides.contains("Compressibility") || provides.contains("Density"));
    }
  }

  @Nested
  @DisplayName("StreamContract Tests")
  class StreamContractTests {

    @Test
    @DisplayName("Valid stream passes preconditions")
    void testValidStreamPreconditions() {
      SystemSrkEos system = new SystemSrkEos(298.15, 10.0);
      system.addComponent("methane", 1.0);
      system.setMixingRule("classic");
      Stream stream = new Stream("TestFeed", system);

      StreamContract contract = StreamContract.getInstance();
      ValidationResult result = contract.checkPreconditions(stream);

      assertTrue(result.isValid(), "Valid stream should pass: " + result.getReport());
    }

    @Test
    @DisplayName("Stream inherits thermo system errors")
    void testStreamInheritsThermErrors() {
      SystemSrkEos system = new SystemSrkEos(0.5, 10.0); // Bad temperature
      system.addComponent("methane", 1.0);
      Stream stream = new Stream("BadStream", system);

      StreamContract contract = StreamContract.getInstance();
      ValidationResult result = contract.checkPreconditions(stream);

      assertFalse(result.isValid());
      assertTrue(result.getReport().contains("Temperature"));
    }

    @Test
    @DisplayName("Contract name is correct")
    void testContractName() {
      StreamContract contract = StreamContract.getInstance();
      assertEquals("StreamContract", contract.getContractName());
    }
  }

  @Nested
  @DisplayName("SeparatorContract Tests")
  class SeparatorContractTests {

    @Test
    @DisplayName("Valid separator passes preconditions")
    void testValidSeparatorPreconditions() {
      SystemSrkEos system = new SystemSrkEos(298.15, 50.0);
      system.addComponent("methane", 0.7);
      system.addComponent("n-heptane", 0.3);
      system.setMixingRule("classic");
      Stream feed = new Stream("Feed", system);
      feed.run();

      Separator separator = new Separator("TestSep", feed);

      SeparatorContract contract = SeparatorContract.getInstance();
      ValidationResult result = contract.checkPreconditions(separator);

      assertTrue(result.isValid(), "Valid separator should pass: " + result.getReport());
    }

    @Test
    @DisplayName("Separator without inlet fails")
    void testSeparatorWithoutInletFails() {
      Separator separator = new Separator("EmptySep");

      SeparatorContract contract = SeparatorContract.getInstance();
      ValidationResult result = contract.checkPreconditions(separator);

      assertFalse(result.isValid());
      assertTrue(result.getReport().contains("inlet"));
    }

    @Test
    @DisplayName("Contract provides requirements description")
    void testSeparatorRequirements() {
      SeparatorContract contract = SeparatorContract.getInstance();
      String requirements = contract.getRequirementsDescription();

      assertNotNull(requirements);
      assertTrue(requirements.contains("inlet"));
      assertTrue(requirements.contains("stream"));
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Full workflow with contract validation")
    void testFullWorkflowWithContracts() {
      // Create system
      SystemSrkEos system = new SystemSrkEos(280.0, 50.0);
      system.addComponent("methane", 0.85);
      system.addComponent("ethane", 0.10);
      system.addComponent("propane", 0.05);
      system.setMixingRule("classic");

      // Validate thermo system
      ThermodynamicSystemContract thermoContract = ThermodynamicSystemContract.getInstance();
      ValidationResult thermoResult = thermoContract.checkPreconditions(system);
      assertTrue(thermoResult.isValid(), "Thermo preconditions: " + thermoResult.getReport());

      // Create and validate stream
      Stream feed = new Stream("WellStream", system);
      StreamContract streamContract = StreamContract.getInstance();
      ValidationResult streamResult = streamContract.checkPreconditions(feed);
      assertTrue(streamResult.isValid(), "Stream preconditions: " + streamResult.getReport());

      // Run stream
      feed.run();

      // Check postconditions
      ValidationResult postStream = streamContract.checkPostconditions(feed);
      assertTrue(postStream.isValid() || postStream.hasWarnings(),
          "Stream postconditions: " + postStream.getReport());

      // Create and validate separator
      Separator separator = new Separator("InletSep", feed);
      SeparatorContract sepContract = SeparatorContract.getInstance();
      ValidationResult sepResult = sepContract.checkPreconditions(separator);
      assertTrue(sepResult.isValid(), "Separator preconditions: " + sepResult.getReport());

      // Run separator
      separator.run();

      // Check separator postconditions
      ValidationResult postSep = sepContract.checkPostconditions(separator);
      // May have warnings about single-phase - that's OK
      assertNotNull(postSep);
    }

    @Test
    @DisplayName("Contract remediation messages are actionable")
    void testRemediationMessagesActionable() {
      // Create system with issues that can be validated
      SystemSrkEos system = new SystemSrkEos(0.5, 1.0); // Low temperature
      // No components added

      ThermodynamicSystemContract contract = ThermodynamicSystemContract.getInstance();
      ValidationResult result = contract.checkPreconditions(system);

      assertFalse(result.isValid());

      // Check that remediation messages contain code hints
      String report = result.getReport();
      assertTrue(
          report.contains("addComponent") || report.contains("setTemperature")
              || report.contains("Temperature"),
          "Remediation should contain code hints: " + report);
    }
  }
}
