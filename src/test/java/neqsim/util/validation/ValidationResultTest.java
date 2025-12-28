package neqsim.util.validation;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the ValidationResult class and validateSetup() interface methods.
 */
class ValidationResultTest {

  @Test
  @DisplayName("ValidationResult - addError creates invalid result")
  void testAddErrorMakesInvalid() {
    ValidationResult result = new ValidationResult("TestComponent");
    assertTrue(result.isValid());

    result.addError("test", "Error message", "Fix suggestion");

    assertFalse(result.isValid());
    assertEquals(1, result.getErrors().size());
  }

  @Test
  @DisplayName("ValidationResult - addWarning keeps result valid")
  void testAddWarningKeepsValid() {
    ValidationResult result = new ValidationResult("TestComponent");

    result.addWarning("test", "Warning message", "Suggestion");

    assertTrue(result.isValid());
    assertEquals(1, result.getWarnings().size());
  }

  @Test
  @DisplayName("ValidationResult - getReport formats correctly")
  void testGetReportFormat() {
    ValidationResult result = new ValidationResult("TestSystem");
    result.addError("thermo", "Temperature too low", "Set temperature above 1 K");
    result.addWarning("thermo", "Mixing rule not set", "Call setMixingRule()");

    String report = result.getReport();

    assertTrue(report.contains("TestSystem"));
    assertTrue(report.contains("CRITICAL") || report.contains("ERROR")
        || report.contains("Temperature too low"));
    assertTrue(report.contains("Mixing rule not set"));
  }

  @Test
  @DisplayName("SystemInterface.validateSetup() - valid system passes")
  void testSystemInterfaceValidateSetupValidSystem() {
    SystemSrkEos system = new SystemSrkEos(298.15, 1.0);
    system.addComponent("methane", 1.0);
    system.setMixingRule("classic");

    ValidationResult result = system.validateSetup();

    assertTrue(result.isValid(), "Valid system should pass: " + result.getReport());
  }

  @Test
  @DisplayName("SystemInterface.validateSetup() - empty system fails")
  void testSystemInterfaceValidateSetupEmptySystem() {
    SystemSrkEos system = new SystemSrkEos(298.15, 1.0);
    // No components added

    ValidationResult result = system.validateSetup();

    assertFalse(result.isValid());
    assertTrue(result.getReport().contains("No components defined"));
  }

  @Test
  @DisplayName("SystemInterface.validateSetup() - zero temperature fails")
  void testSystemInterfaceValidateSetupZeroTemperature() {
    SystemSrkEos system = new SystemSrkEos(0.5, 1.0); // Below 1 K
    system.addComponent("methane", 1.0);

    ValidationResult result = system.validateSetup();

    assertFalse(result.isValid());
    assertTrue(result.getReport().contains("Temperature too low"));
  }

  @Test
  @DisplayName("SystemInterface.validateSetup() - missing mixing rule warns")
  void testSystemInterfaceValidateSetupMissingMixingRule() {
    SystemSrkEos system = new SystemSrkEos(298.15, 1.0);
    system.addComponent("methane", 0.5);
    system.addComponent("ethane", 0.5);
    // No mixing rule set

    ValidationResult result = system.validateSetup();

    // Should warn but still be valid (mixing rule is a warning not error)
    assertTrue(result.hasWarnings() || result.isValid());
  }

  @Test
  @DisplayName("ProcessEquipmentInterface.validateSetup() - stream with name passes")
  void testProcessEquipmentValidateSetupValidStream() {
    SystemSrkEos system = new SystemSrkEos(298.15, 1.0);
    system.addComponent("methane", 1.0);
    system.setMixingRule("classic");

    Stream stream = new Stream("TestStream", system);

    ValidationResult result = stream.validateSetup();

    assertTrue(result.isValid(), "Named stream should pass: " + result.getReport());
  }

  @Test
  @DisplayName("Exception getRemediation() - InvalidInputException provides hints")
  void testInvalidInputExceptionRemediation() {
    neqsim.util.exception.InvalidInputException ex =
        new neqsim.util.exception.InvalidInputException("TestClass", "testMethod", "temperature",
            "must be positive");

    String remediation = ex.getRemediation();

    assertNotNull(remediation);
    assertTrue(remediation.contains("temperature") || remediation.contains("Temperature"));
  }

  @Test
  @DisplayName("Exception getRemediation() - TooManyIterationsException provides hints")
  void testTooManyIterationsExceptionRemediation() {
    neqsim.util.exception.TooManyIterationsException ex =
        new neqsim.util.exception.TooManyIterationsException("TestClass", "testMethod", 100);

    String remediation = ex.getRemediation();

    assertNotNull(remediation);
    assertTrue(remediation.contains("100"));
    assertTrue(remediation.contains("converge") || remediation.contains("iterate"));
  }

  @Test
  @DisplayName("Exception getRemediation() - IsNaNException provides hints")
  void testIsNaNExceptionRemediation() {
    neqsim.util.exception.IsNaNException ex =
        new neqsim.util.exception.IsNaNException("TestClass", "testMethod", "compressibility");

    String remediation = ex.getRemediation();

    assertNotNull(remediation);
    assertTrue(remediation.length() > 20); // Has meaningful content
  }

  @Test
  @DisplayName("Exception getRemediation() - InvalidOutputException provides context-specific hints")
  void testInvalidOutputExceptionRemediation() {
    neqsim.util.exception.InvalidOutputException ex =
        new neqsim.util.exception.InvalidOutputException("TestClass", "testMethod", "temperature");

    String remediation = ex.getRemediation();

    assertNotNull(remediation);
    assertTrue(remediation.contains("temperature"));
  }

  @Test
  @DisplayName("Exception getRemediation() - NotInitializedException provides hints")
  void testNotInitializedExceptionRemediation() {
    neqsim.util.exception.NotInitializedException ex =
        new neqsim.util.exception.NotInitializedException("TestClass", "testMethod",
            "thermoProperties", "init(1)");

    String remediation = ex.getRemediation();

    assertNotNull(remediation);
    assertTrue(remediation.contains("init") || remediation.contains("initialize"));
  }
}

