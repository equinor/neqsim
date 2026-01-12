package neqsim.util.validation;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for AIIntegrationHelper.
 */
class AIIntegrationHelperTest {

  private ProcessSystem process;
  private SystemInterface fluid;

  @BeforeEach
  void setUp() {
    fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");

    process = new ProcessSystem();
    process.add(feed);
  }

  @Nested
  @DisplayName("Factory Method Tests")
  class FactoryMethodTests {

    @Test
    @DisplayName("Should create helper for process")
    void testForProcess() {
      AIIntegrationHelper helper = AIIntegrationHelper.forProcess(process);
      assertNotNull(helper);
      assertSame(process, helper.getProcess());
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("Should validate process")
    void testValidate() {
      AIIntegrationHelper helper = AIIntegrationHelper.forProcess(process);
      ValidationResult result = helper.validate();
      assertNotNull(result);
    }

    @Test
    @DisplayName("Should check if ready")
    void testIsReady() {
      AIIntegrationHelper helper = AIIntegrationHelper.forProcess(process);
      boolean ready = helper.isReady();
      // Should be ready with valid setup
      assertTrue(ready);
    }

    @Test
    @DisplayName("Should get validation report")
    void testGetValidationReport() {
      AIIntegrationHelper helper = AIIntegrationHelper.forProcess(process);
      String report = helper.getValidationReport();
      assertNotNull(report);
      assertTrue(report.contains("Validation"));
    }

    @Test
    @DisplayName("Should get issues as text")
    void testGetIssuesAsText() {
      // Create an invalid process to generate issues
      ProcessSystem emptyProcess = new ProcessSystem();
      AIIntegrationHelper helper = AIIntegrationHelper.forProcess(emptyProcess);

      String[] issues = helper.getIssuesAsText();
      assertNotNull(issues);
      // Empty process should have issues
      assertTrue(issues.length > 0);
    }
  }

  @Nested
  @DisplayName("Execution Tests")
  class ExecutionTests {

    @Test
    @DisplayName("Should run process safely")
    void testSafeRun() {
      AIIntegrationHelper helper = AIIntegrationHelper.forProcess(process);
      AIIntegrationHelper.ExecutionResult result = helper.safeRun();

      assertNotNull(result);
      // Should succeed or at least not error
      assertNotEquals(AIIntegrationHelper.ExecutionResult.Status.ERROR, result.getStatus());
    }

    @Test
    @DisplayName("Should fail pre-validation on empty process")
    void testSafeRunFailsOnEmpty() {
      ProcessSystem emptyProcess = new ProcessSystem();
      AIIntegrationHelper helper = AIIntegrationHelper.forProcess(emptyProcess);
      AIIntegrationHelper.ExecutionResult result = helper.safeRun();

      assertEquals(AIIntegrationHelper.ExecutionResult.Status.FAILURE, result.getStatus());
      assertTrue(result.getMessage().contains("validation failed"));
    }

    @Test
    @DisplayName("ExecutionResult should generate AI report")
    void testExecutionResultToAIReport() {
      AIIntegrationHelper helper = AIIntegrationHelper.forProcess(process);
      AIIntegrationHelper.ExecutionResult result = helper.safeRun();

      String report = result.toAIReport();
      assertNotNull(report);
      assertTrue(report.contains("## Execution Result"));
      assertTrue(report.contains("**Status:**"));
    }
  }

  @Nested
  @DisplayName("Documentation Tests")
  class DocumentationTests {

    @Test
    @DisplayName("Should get API documentation")
    void testGetAPIDocumentation() {
      AIIntegrationHelper helper = AIIntegrationHelper.forProcess(process);
      String docs = helper.getAPIDocumentation();

      assertNotNull(docs);
      assertTrue(docs.contains("NeqSim Quick Start"));
      assertTrue(docs.contains("Creating a Fluid"));
      assertTrue(docs.contains("SystemSrkEos"));
    }
  }

  @Nested
  @DisplayName("Component Validation Tests")
  class ComponentValidationTests {

    @Test
    @DisplayName("Should validate individual fluid")
    void testValidateFluid() {
      AIIntegrationHelper helper = AIIntegrationHelper.forProcess(process);
      ValidationResult result = helper.validateFluid(fluid);

      assertNotNull(result);
      assertTrue(result.isValid(), "Valid fluid should pass validation");
    }

    @Test
    @DisplayName("Should validate equipment")
    void testValidateEquipment() {
      Stream stream = new Stream("test", fluid);
      stream.setFlowRate(100.0, "kg/hr");

      AIIntegrationHelper helper = AIIntegrationHelper.forProcess(process);
      ValidationResult result = helper.validateEquipment(stream);

      assertNotNull(result);
    }
  }

  @Nested
  @DisplayName("RL Environment Tests")
  class RLEnvironmentTests {

    @Test
    @DisplayName("Should create RL environment")
    void testCreateRLEnvironment() {
      AIIntegrationHelper helper = AIIntegrationHelper.forProcess(process);
      Object env = helper.createRLEnvironment();

      assertNotNull(env);
      // Should be an RLEnvironment instance
      assertEquals("RLEnvironment", env.getClass().getSimpleName());
    }
  }

  @Nested
  @DisplayName("ExecutionResult Status Tests")
  class ExecutionResultStatusTests {

    @Test
    @DisplayName("Success result should be success")
    void testSuccessResult() {
      ValidationResult validation = new ValidationResult();
      AIIntegrationHelper.ExecutionResult result =
          AIIntegrationHelper.ExecutionResult.success(validation);

      assertTrue(result.isSuccess());
      assertEquals(AIIntegrationHelper.ExecutionResult.Status.SUCCESS, result.getStatus());
      assertNotNull(result.getValidation());
      assertNull(result.getException());
    }

    @Test
    @DisplayName("Warning result should not be success")
    void testWarningResult() {
      ValidationResult validation = new ValidationResult();
      validation.addWarning("minor issue");
      AIIntegrationHelper.ExecutionResult result =
          AIIntegrationHelper.ExecutionResult.warning("Some warning", validation);

      assertFalse(result.isSuccess());
      assertEquals(AIIntegrationHelper.ExecutionResult.Status.WARNING, result.getStatus());
    }

    @Test
    @DisplayName("Error result should contain exception")
    void testErrorResult() {
      RuntimeException ex = new RuntimeException("Test error");
      AIIntegrationHelper.ExecutionResult result =
          AIIntegrationHelper.ExecutionResult.error("Error occurred", ex);

      assertFalse(result.isSuccess());
      assertEquals(AIIntegrationHelper.ExecutionResult.Status.ERROR, result.getStatus());
      assertSame(ex, result.getException());
    }
  }
}
