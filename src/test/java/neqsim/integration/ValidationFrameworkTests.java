package neqsim.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.integration.ValidationFramework.CommonErrors;
import neqsim.integration.ValidationFramework.ValidationError;
import neqsim.integration.ValidationFramework.ValidationResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the ValidationFramework and validators.
 */
public class ValidationFrameworkTests {
  /**
   * Test: ValidationBuilder creates valid result for good system.
   */
  @Test
  public void testValidationBuilderForValidSystem() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    system.addComponent("methane", 0.9);
    system.addComponent("ethane", 0.1);
    system.setMixingRule("classic");
    system.init(0);

    ValidationResult result = ThermoValidator.validateSystem(system);

    assertTrue(result.isReady(), "Valid system should be ready");
    assertEquals(0, result.getErrors().size(), "Should have no errors");
  }

  /**
   * Test: ValidationBuilder detects missing mixing rule.
   */
  @Test
  public void testValidationDetectsMissingMixingRule() {
    // Create system with component but NO mixing rule
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    system.addComponent("methane", 1.0);
    // Missing: system.setMixingRule("classic")
    system.init(0);

    ValidationResult result = ThermoValidator.validateSystem(system);

    // Should have warning about mixing rule
    assertTrue(result.getWarnings().size() > 0 || !result.isReady(),
        "Validation should warn about missing mixing rule");
  }

  /**
   * Test: ValidationError has correct severity.
   */
  @Test
  public void testValidationErrorSeverity() {
    // Constructor order: Severity, category, message, remediation
    ValidationError criticalError =
        new ValidationError(ValidationError.Severity.CRITICAL, "test", "Critical error", "Fix it");
    ValidationError majorError =
        new ValidationError(ValidationError.Severity.MAJOR, "test", "Major error", "Fix it");

    assertEquals(ValidationError.Severity.CRITICAL, criticalError.getSeverity());
    assertEquals(ValidationError.Severity.MAJOR, majorError.getSeverity());
  }

  /**
   * Test: ValidationResult summary generation.
   */
  @Test
  public void testValidationResultSummary() {
    // Create system without mixing rule (has component, so init works)
    SystemInterface systemWithWarning = new SystemSrkEos(298.15, 10.0);
    systemWithWarning.addComponent("methane", 1.0);
    // Missing mixing rule
    systemWithWarning.init(0);

    ValidationResult result = ThermoValidator.validateSystem(systemWithWarning);
    String summary = result.getErrorsSummary();

    assertNotNull(summary, "Summary should not be null");
  }

  /**
   * Test: CommonErrors constants are defined.
   */
  @Test
  public void testCommonErrorsConstants() {
    assertNotNull(CommonErrors.MIXING_RULE_NOT_SET);
    assertNotNull(CommonErrors.NO_COMPONENTS);
    assertNotNull(CommonErrors.FEED_STREAM_NOT_SET);
    assertNotNull(CommonErrors.INVALID_TEMPERATURE);
    assertNotNull(CommonErrors.INVALID_PRESSURE);
  }

  /**
   * Test: ThermoValidator handles pressure at boundary.
   */
  @Test
  public void testThermoValidatorPressureBoundary() {
    SystemInterface system = new SystemSrkEos(298.15, 0.001); // Very low pressure
    system.addComponent("methane", 1.0);
    system.setMixingRule("classic");
    system.init(0);

    ValidationResult result = ThermoValidator.validateSystem(system);

    // Should still be valid (pressure > 0)
    assertTrue(result.isReady(), "Low but positive pressure should be valid");
  }

  /**
   * Test: ThermoValidator handles temperature at boundary.
   */
  @Test
  public void testThermoValidatorTemperatureBoundary() {
    SystemInterface system = new SystemSrkEos(100.0, 10.0); // Low temperature
    system.addComponent("methane", 1.0);
    system.setMixingRule("classic");
    system.init(0);

    ValidationResult result = ThermoValidator.validateSystem(system);

    // Temperature of 100K is valid (above absolute zero, below our 1.0 threshold)
    // The validator warns about temp < 1.0 K which would be unphysical
    assertNotNull(result, "Result should not be null");
  }
}
