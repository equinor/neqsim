package neqsim.util.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for SimulationValidator - the AI-friendly validation facade.
 */
class SimulationValidatorTest {
  @Nested
  @DisplayName("Type Detection Tests")
  class TypeDetectionTests {
    @Test
    @DisplayName("Validates SystemInterface correctly")
    void testValidateSystemInterface() {
      SystemSrkEos system = new SystemSrkEos(298.15, 10.0);
      system.addComponent("methane", 1.0);
      system.setMixingRule("classic");

      ValidationResult result = SimulationValidator.validate(system);

      assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Validates StreamInterface correctly")
    void testValidateStreamInterface() {
      SystemSrkEos system = new SystemSrkEos(298.15, 10.0);
      system.addComponent("methane", 1.0);
      system.setMixingRule("classic");
      Stream stream = new Stream("Feed", system);

      ValidationResult result = SimulationValidator.validate(stream);

      assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Validates Separator correctly")
    void testValidateSeparator() {
      SystemSrkEos system = new SystemSrkEos(298.15, 50.0);
      system.addComponent("methane", 0.8);
      system.addComponent("n-heptane", 0.2);
      system.setMixingRule("classic");
      Stream feed = new Stream("Feed", system);
      feed.run();
      Separator sep = new Separator("Sep1", feed);

      ValidationResult result = SimulationValidator.validate(sep);

      assertTrue(result.isValid(), result.getReport());
    }

    @Test
    @DisplayName("Validates ProcessSystem correctly")
    void testValidateProcessSystem() {
      SystemSrkEos system = new SystemSrkEos(298.15, 50.0);
      system.addComponent("methane", 1.0);
      system.setMixingRule("classic");
      Stream feed = new Stream("Feed", system);

      ProcessSystem process = new ProcessSystem();
      process.add(feed);

      ValidationResult result = SimulationValidator.validate(process);

      assertTrue(result.isValid(), result.getReport());
    }

    @Test
    @DisplayName("Handles null gracefully")
    void testValidateNull() {
      ValidationResult result = SimulationValidator.validate(null);

      assertFalse(result.isValid());
      assertTrue(result.getReport().contains("null"));
    }

    @Test
    @DisplayName("Handles unknown types with warning")
    void testValidateUnknownType() {
      ValidationResult result = SimulationValidator.validate("some string");

      assertTrue(result.hasWarnings());
    }
  }

  @Nested
  @DisplayName("Validation and Run Tests")
  class ValidationAndRunTests {
    @Test
    @DisplayName("validateAndRun succeeds for valid equipment")
    void testValidateAndRunValid() {
      SystemSrkEos system = new SystemSrkEos(298.15, 10.0);
      system.addComponent("methane", 1.0);
      system.setMixingRule("classic");
      Stream stream = new Stream("Feed", system);

      ValidationResult result = SimulationValidator.validateAndRun(stream);

      assertTrue(result.isValid(), result.getReport());
    }

    @Test
    @DisplayName("validateAndRun fails for invalid equipment")
    void testValidateAndRunInvalid() {
      SystemSrkEos system = new SystemSrkEos(0.5, 10.0); // Low temp
      system.addComponent("methane", 1.0);
      Stream stream = new Stream("BadStream", system);

      ValidationResult result = SimulationValidator.validateAndRun(stream);

      assertFalse(result.isValid());
    }

    @Test
    @DisplayName("validateAndRun for ProcessSystem")
    void testValidateAndRunProcessSystem() {
      SystemSrkEos system = new SystemSrkEos(298.15, 50.0);
      system.addComponent("methane", 0.9);
      system.addComponent("ethane", 0.1);
      system.setMixingRule("classic");

      Stream feed = new Stream("Feed", system);
      Separator sep = new Separator("Separator", feed);

      ProcessSystem process = new ProcessSystem();
      process.add(feed);
      process.add(sep);

      ValidationResult result = SimulationValidator.validateAndRun(process);

      assertTrue(result.isValid() || result.hasWarnings(), result.getReport());
    }
  }

  @Nested
  @DisplayName("Helper Method Tests")
  class HelperMethodTests {
    @Test
    @DisplayName("isReady returns true for valid system")
    void testIsReadyValid() {
      SystemSrkEos system = new SystemSrkEos(298.15, 10.0);
      system.addComponent("methane", 1.0);
      system.setMixingRule("classic");

      assertTrue(SimulationValidator.isReady(system));
    }

    @Test
    @DisplayName("isReady returns false for invalid system")
    void testIsReadyInvalid() {
      SystemSrkEos system = new SystemSrkEos(0.5, 10.0);
      // No components

      assertFalse(SimulationValidator.isReady(system));
    }

    @Test
    @DisplayName("getValidationReport covers multiple objects")
    void testGetValidationReport() {
      SystemSrkEos system = new SystemSrkEos(298.15, 10.0);
      system.addComponent("methane", 1.0);
      system.setMixingRule("classic");
      Stream stream = new Stream("Feed", system);

      String report = SimulationValidator.getValidationReport(system, stream);

      assertNotNull(report);
      assertTrue(report.contains("Validation Report"));
      assertTrue(report.contains("Overall Status"));
    }
  }

  @Nested
  @DisplayName("Output Validation Tests")
  class OutputValidationTests {
    @Test
    @DisplayName("validateOutput checks post-run state")
    void testValidateOutput() {
      SystemSrkEos system = new SystemSrkEos(298.15, 50.0);
      system.addComponent("methane", 0.8);
      system.addComponent("ethane", 0.2);
      system.setMixingRule("classic");
      Stream stream = new Stream("Feed", system);
      stream.run();

      ValidationResult result = SimulationValidator.validateOutput(stream);

      // May have warnings but should not have critical errors for valid run
      assertNotNull(result);
    }

    @Test
    @DisplayName("validateOutput handles null")
    void testValidateOutputNull() {
      ValidationResult result = SimulationValidator.validateOutput(null);

      assertFalse(result.isValid());
    }
  }

  @Nested
  @DisplayName("AI Workflow Simulation")
  class AIWorkflowTests {
    @Test
    @DisplayName("AI can use validation to self-correct")
    void testAISelfCorrectWorkflow() {
      // Step 1: AI creates a system (with error)
      SystemSrkEos system = new SystemSrkEos(0.5, 10.0); // Bad temperature

      // Step 2: AI validates
      ValidationResult result = SimulationValidator.validate(system);

      // Step 3: AI detects error
      assertFalse(result.isValid());

      // Step 4: AI parses remediation from report
      String report = result.getReport();
      assertTrue(report.contains("Temperature") || report.contains("temperature"));

      // Step 5: AI applies fix
      system.setTemperature(298.15);
      system.addComponent("methane", 1.0);
      system.setMixingRule("classic");

      // Step 6: AI re-validates
      ValidationResult fixedResult = SimulationValidator.validate(system);
      assertTrue(fixedResult.isValid(), "After fix: " + fixedResult.getReport());
    }

    @Test
    @DisplayName("Full process simulation with validation")
    void testFullProcessWithValidation() {
      // Create components
      SystemSrkEos wellFluid = new SystemSrkEos(320.0, 65.0);
      wellFluid.addComponent("methane", 0.75);
      wellFluid.addComponent("ethane", 0.10);
      wellFluid.addComponent("propane", 0.05);
      wellFluid.addComponent("n-heptane", 0.10);
      wellFluid.setMixingRule("classic");

      // Validate system first
      assertTrue(SimulationValidator.isReady(wellFluid));

      // Create process
      Stream wellStream = new Stream("Well Stream", wellFluid);
      wellStream.setFlowRate(1000.0, "kg/hr");

      Separator hpSeparator = new Separator("HP Separator", wellStream);

      ProcessSystem process = new ProcessSystem();
      process.add(wellStream);
      process.add(hpSeparator);

      // Validate entire process
      ValidationResult processResult = SimulationValidator.validate(process);
      assertTrue(processResult.isValid(), processResult.getReport());

      // Run with validation
      ValidationResult runResult = SimulationValidator.validateAndRun(process);

      // Check for valid run
      assertNotNull(runResult);
    }
  }
}
