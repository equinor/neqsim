package neqsim.process.ml.surrogate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for PhysicsConstraintValidator AI action validation.
 */
public class PhysicsConstraintValidatorTest {

  private ProcessSystem process;
  private PhysicsConstraintValidator validator;

  @BeforeEach
  void setUp() {
    SystemInterface fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(20.0, "bara");

    Separator separator = new Separator("separator", feed);

    process = new ProcessSystem();
    process.setName("TestProcess");
    process.add(feed);
    process.add(separator);
    process.run();

    validator = new PhysicsConstraintValidator(process);
  }

  @Test
  void testValidatorCreation() {
    assertNotNull(validator);
  }

  @Test
  void testAddPressureLimit() {
    validator.addPressureLimit("separator", 10.0, 80.0, "bara");
    assertNotNull(validator);
  }

  @Test
  void testAddTemperatureLimit() {
    validator.addTemperatureLimit("heater-outlet", 0.0, 300.0, "C");
    assertNotNull(validator);
  }

  @Test
  void testAddFlowLimit() {
    validator.addFlowLimit("feed", 0.0, 1000.0, "kg/hr");
    assertNotNull(validator);
  }

  @Test
  void testValidateValidAction() {
    // Disable balance checks for this simple test
    validator.setEnforceMassBalance(false);
    validator.setEnforceEnergyBalance(false);

    Map<String, Double> proposedAction = new HashMap<>();
    proposedAction.put("valve.opening", 0.5); // Valid: 0-1 range

    PhysicsConstraintValidator.ValidationResult result = validator.validate(proposedAction);

    assertTrue(result.isValid());
  }

  @Test
  void testValidateInvalidValveOpening() {
    Map<String, Double> proposedAction = new HashMap<>();
    proposedAction.put("valve.opening", 1.5); // Invalid: outside 0-1 range

    PhysicsConstraintValidator.ValidationResult result = validator.validate(proposedAction);

    assertFalse(result.isValid());
  }

  @Test
  void testValidateNegativePressure() {
    Map<String, Double> proposedAction = new HashMap<>();
    proposedAction.put("pressure", -10.0); // Invalid: negative pressure

    PhysicsConstraintValidator.ValidationResult result = validator.validate(proposedAction);

    assertFalse(result.isValid());
  }

  @Test
  void testValidateNegativeTemperature() {
    Map<String, Double> proposedAction = new HashMap<>();
    proposedAction.put("temperature", -100.0); // Invalid: negative Kelvin

    PhysicsConstraintValidator.ValidationResult result = validator.validate(proposedAction);

    assertFalse(result.isValid());
  }

  @Test
  void testValidateNegativeFlow() {
    Map<String, Double> proposedAction = new HashMap<>();
    proposedAction.put("flow", -10.0); // Invalid: negative flow

    PhysicsConstraintValidator.ValidationResult result = validator.validate(proposedAction);

    assertFalse(result.isValid());
  }

  @Test
  void testValidationResultGetRejectionReason() {
    Map<String, Double> proposedAction = new HashMap<>();
    proposedAction.put("valve.opening", 1.5);

    PhysicsConstraintValidator.ValidationResult result = validator.validate(proposedAction);

    assertNotNull(result.getRejectionReason());
  }

  @Test
  void testValidationResultNoRejectionWhenValid() {
    // Disable balance checks for this simple test
    validator.setEnforceMassBalance(false);
    validator.setEnforceEnergyBalance(false);

    Map<String, Double> proposedAction = new HashMap<>();
    proposedAction.put("valve.opening", 0.5);

    PhysicsConstraintValidator.ValidationResult result = validator.validate(proposedAction);

    assertNull(result.getRejectionReason());
  }

  @Test
  void testGetViolations() {
    Map<String, Double> proposedAction = new HashMap<>();
    proposedAction.put("valve.opening", 1.5);

    PhysicsConstraintValidator.ValidationResult result = validator.validate(proposedAction);

    assertFalse(result.getViolations().isEmpty());
  }

  @Test
  void testConstraintViolationDetails() {
    Map<String, Double> proposedAction = new HashMap<>();
    proposedAction.put("valve.opening", 1.5);

    PhysicsConstraintValidator.ValidationResult result = validator.validate(proposedAction);

    assertFalse(result.getViolations().isEmpty());
    PhysicsConstraintValidator.ConstraintViolation violation = result.getViolations().get(0);
    assertNotNull(violation.getConstraintName());
    assertNotNull(violation.getVariable());
    assertEquals(1.5, violation.getValue(), 0.001);
    assertNotNull(violation.getMessage());
  }

  @Test
  void testConstraintViolationToString() {
    Map<String, Double> proposedAction = new HashMap<>();
    proposedAction.put("valve.opening", 1.5);

    PhysicsConstraintValidator.ValidationResult result = validator.validate(proposedAction);

    assertFalse(result.getViolations().isEmpty());
    String violationStr = result.getViolations().get(0).toString();
    assertNotNull(violationStr);
    assertFalse(violationStr.isEmpty());
  }

  @Test
  void testSetMassBalanceTolerance() {
    validator.setMassBalanceTolerance(0.02); // 2%
    assertNotNull(validator);
  }

  @Test
  void testSetEnergyBalanceTolerance() {
    validator.setEnergyBalanceTolerance(0.10); // 10%
    assertNotNull(validator);
  }

  @Test
  void testEnforceMassBalance() {
    validator.setEnforceMassBalance(true);
    assertTrue(validator.isEnforceMassBalance());

    validator.setEnforceMassBalance(false);
    assertFalse(validator.isEnforceMassBalance());
  }

  @Test
  void testEnforceEnergyBalance() {
    validator.setEnforceEnergyBalance(true);
    assertTrue(validator.isEnforceEnergyBalance());

    validator.setEnforceEnergyBalance(false);
    assertFalse(validator.isEnforceEnergyBalance());
  }

  @Test
  void testEnforcePhysicalBounds() {
    validator.setEnforcePhysicalBounds(true);
    assertTrue(validator.isEnforcePhysicalBounds());

    validator.setEnforcePhysicalBounds(false);
    assertFalse(validator.isEnforcePhysicalBounds());
  }

  @Test
  void testValidateEmptyAction() {
    // Disable balance checks for this simple test
    validator.setEnforceMassBalance(false);
    validator.setEnforceEnergyBalance(false);

    Map<String, Double> proposedAction = new HashMap<>();

    PhysicsConstraintValidator.ValidationResult result = validator.validate(proposedAction);

    // Empty action should generally be valid
    assertTrue(result.isValid());
  }

  @Test
  void testValidateCurrentState() {
    PhysicsConstraintValidator.ValidationResult result = validator.validateCurrentState();

    assertNotNull(result);
  }

  @Test
  void testMultipleViolations() {
    Map<String, Double> proposedAction = new HashMap<>();
    proposedAction.put("valve.opening", 1.5); // Invalid
    proposedAction.put("pressure", -10.0); // Invalid
    proposedAction.put("flow", -5.0); // Invalid

    PhysicsConstraintValidator.ValidationResult result = validator.validate(proposedAction);

    assertFalse(result.isValid());
    assertTrue(result.getViolations().size() >= 3);
  }
}
