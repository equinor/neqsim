package neqsim.process.mechanicaldesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for FieldDevelopmentDesignOrchestrator.
 *
 * @author esol
 */
class FieldDevelopmentDesignOrchestratorTest {
  private ProcessSystem processSystem;
  private FieldDevelopmentDesignOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    // Create a simple process system for testing
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.15);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");

    Separator separator = new Separator("Separator", feed);

    processSystem = new ProcessSystem();
    processSystem.add(feed);
    processSystem.add(separator);

    orchestrator = new FieldDevelopmentDesignOrchestrator(processSystem, "TEST-001");
  }

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {
    @Test
    @DisplayName("Should create orchestrator with valid process system")
    void shouldCreateWithValidProcessSystem() {
      assertNotNull(orchestrator);
      assertEquals("TEST-001", orchestrator.getProjectId());
      assertNotNull(orchestrator.getProcessSystem());
      assertEquals(processSystem, orchestrator.getProcessSystem());
    }

    @Test
    @DisplayName("Should throw exception for null process system")
    void shouldThrowForNullProcessSystem() {
      assertThrows(IllegalArgumentException.class,
          () -> new FieldDevelopmentDesignOrchestrator(null, "PROJECT"));
    }

    @Test
    @DisplayName("Should use DEFAULT for null project ID")
    void shouldUseDefaultForNullProjectId() {
      FieldDevelopmentDesignOrchestrator orch =
          new FieldDevelopmentDesignOrchestrator(processSystem, null);
      assertEquals("DEFAULT", orch.getProjectId());
    }

    @Test
    @DisplayName("Should initialize with FEED phase by default")
    void shouldInitializeWithFeedPhase() {
      assertEquals(DesignPhase.FEED, orchestrator.getDesignPhase());
    }

    @Test
    @DisplayName("Should have default design cases")
    void shouldHaveDefaultDesignCases() {
      List<DesignCase> cases = orchestrator.getDesignCases();
      assertFalse(cases.isEmpty());
      assertTrue(cases.contains(DesignCase.NORMAL));
      assertTrue(cases.contains(DesignCase.MAXIMUM));
    }
  }

  @Nested
  @DisplayName("Design Phase Tests")
  class DesignPhaseTests {
    @Test
    @DisplayName("Should set design phase")
    void shouldSetDesignPhase() {
      orchestrator.setDesignPhase(DesignPhase.CONCEPT_SELECT);
      assertEquals(DesignPhase.CONCEPT_SELECT, orchestrator.getDesignPhase());
    }

    @Test
    @DisplayName("Should support method chaining")
    void shouldSupportChaining() {
      FieldDevelopmentDesignOrchestrator result =
          orchestrator.setDesignPhase(DesignPhase.DETAIL_DESIGN);
      assertEquals(orchestrator, result);
    }
  }

  @Nested
  @DisplayName("Design Case Tests")
  class DesignCaseTests {
    @Test
    @DisplayName("Should add design case")
    void shouldAddDesignCase() {
      orchestrator.addDesignCase(DesignCase.UPSET);
      assertTrue(orchestrator.getDesignCases().contains(DesignCase.UPSET));
    }

    @Test
    @DisplayName("Should not add duplicate design case")
    void shouldNotAddDuplicateCase() {
      int initialSize = orchestrator.getDesignCases().size();
      orchestrator.addDesignCase(DesignCase.NORMAL);
      assertEquals(initialSize, orchestrator.getDesignCases().size());
    }

    @Test
    @DisplayName("Should set design cases")
    void shouldSetDesignCases() {
      List<DesignCase> cases = Arrays.asList(DesignCase.STARTUP, DesignCase.SHUTDOWN);
      orchestrator.setDesignCases(cases);
      assertEquals(2, orchestrator.getDesignCases().size());
      assertTrue(orchestrator.getDesignCases().contains(DesignCase.STARTUP));
      assertTrue(orchestrator.getDesignCases().contains(DesignCase.SHUTDOWN));
    }

    @Test
    @DisplayName("Should handle null design cases list")
    void shouldHandleNullCasesList() {
      orchestrator.setDesignCases(null);
      assertTrue(orchestrator.getDesignCases().isEmpty());
    }
  }

  @Nested
  @DisplayName("TORG Tests")
  class TorgTests {
    @Test
    @DisplayName("Should have TORG manager")
    void shouldHaveTorgManager() {
      assertNotNull(orchestrator.getTorgManager());
    }

    @Test
    @DisplayName("Should return null for active TORG before loading")
    void shouldReturnNullBeforeLoading() {
      assertNull(orchestrator.getActiveTorg());
    }

    @Test
    @DisplayName("Should return false when loading non-existent TORG")
    void shouldReturnFalseForMissingTorg() {
      boolean result = orchestrator.loadTorg("NON-EXISTENT");
      assertFalse(result);
    }
  }

  @Nested
  @DisplayName("Workflow Tests")
  class WorkflowTests {
    @Test
    @DisplayName("Should run complete design workflow")
    void shouldRunCompleteWorkflow() {
      boolean result = orchestrator.runCompleteDesignWorkflow();

      // Workflow should complete (even with validation warnings)
      assertNotNull(orchestrator.getRunId());
      assertNotNull(orchestrator.getSystemMechanicalDesign());
      assertNotNull(orchestrator.getValidationResult());
    }

    @Test
    @DisplayName("Should populate workflow history")
    void shouldPopulateWorkflowHistory() {
      orchestrator.runCompleteDesignWorkflow();

      List<FieldDevelopmentDesignOrchestrator.WorkflowStep> history =
          orchestrator.getWorkflowHistory();
      assertFalse(history.isEmpty());

      // Check key steps are recorded
      boolean hasInitStep = false;
      boolean hasSimStep = false;
      boolean hasDesignStep = false;

      for (FieldDevelopmentDesignOrchestrator.WorkflowStep step : history) {
        if (step.getStepName().contains("Initialize")) {
          hasInitStep = true;
        }
        if (step.getStepName().contains("Simulation")) {
          hasSimStep = true;
        }
        if (step.getStepName().contains("Mechanical")) {
          hasDesignStep = true;
        }
      }

      assertTrue(hasInitStep);
      assertTrue(hasSimStep);
      assertTrue(hasDesignStep);
    }

    @Test
    @DisplayName("Should capture design case results")
    void shouldCaptureDesignCaseResults() {
      orchestrator.runCompleteDesignWorkflow();

      assertFalse(orchestrator.getCaseResults().isEmpty());
      assertTrue(orchestrator.getCaseResults().containsKey(DesignCase.NORMAL));
    }

    @Test
    @DisplayName("Should generate unique run ID for each workflow")
    void shouldGenerateUniqueRunId() {
      orchestrator.runCompleteDesignWorkflow();
      java.util.UUID firstRunId = orchestrator.getRunId();

      orchestrator.runCompleteDesignWorkflow();
      java.util.UUID secondRunId = orchestrator.getRunId();

      assertNotNull(firstRunId);
      assertNotNull(secondRunId);
      assertFalse(firstRunId.equals(secondRunId));
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {
    @Test
    @DisplayName("Should have validation result after workflow")
    void shouldHaveValidationResult() {
      orchestrator.runCompleteDesignWorkflow();

      DesignValidationResult result = orchestrator.getValidationResult();
      assertNotNull(result);
      assertTrue(result.hasRun());
    }

    @Test
    @DisplayName("Should generate validation messages")
    void shouldGenerateValidationMessages() {
      orchestrator.runCompleteDesignWorkflow();

      DesignValidationResult result = orchestrator.getValidationResult();
      // At minimum there should be some info messages
      assertFalse(result.getMessages().isEmpty());
    }
  }

  @Nested
  @DisplayName("Report Generation Tests")
  class ReportTests {
    @Test
    @DisplayName("Should generate design report")
    void shouldGenerateReport() {
      orchestrator.runCompleteDesignWorkflow();

      String report = orchestrator.generateDesignReport();
      assertNotNull(report);
      assertFalse(report.isEmpty());
    }

    @Test
    @DisplayName("Report should contain project information")
    void reportShouldContainProjectInfo() {
      orchestrator.runCompleteDesignWorkflow();

      String report = orchestrator.generateDesignReport();
      assertTrue(report.contains("TEST-001"));
      assertTrue(report.contains("FEED"));
    }

    @Test
    @DisplayName("Report should contain design cases")
    void reportShouldContainDesignCases() {
      orchestrator.runCompleteDesignWorkflow();

      String report = orchestrator.generateDesignReport();
      assertTrue(report.contains("DESIGN CASES"));
      assertTrue(report.contains("Normal"));
    }

    @Test
    @DisplayName("Report should contain validation results")
    void reportShouldContainValidation() {
      orchestrator.runCompleteDesignWorkflow();

      String report = orchestrator.generateDesignReport();
      assertTrue(report.contains("VALIDATION RESULTS"));
    }

    @Test
    @DisplayName("Report should contain workflow execution")
    void reportShouldContainWorkflowExecution() {
      orchestrator.runCompleteDesignWorkflow();

      String report = orchestrator.generateDesignReport();
      assertTrue(report.contains("WORKFLOW EXECUTION"));
    }
  }

  private void assertNull(Object obj) {
    org.junit.jupiter.api.Assertions.assertNull(obj);
  }
}
