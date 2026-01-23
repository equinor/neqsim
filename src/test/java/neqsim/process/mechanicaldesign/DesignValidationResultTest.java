package neqsim.process.mechanicaldesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import neqsim.process.mechanicaldesign.DesignValidationResult.Severity;
import neqsim.process.mechanicaldesign.DesignValidationResult.ValidationMessage;

/**
 * Unit tests for DesignValidationResult.
 *
 * @author esol
 */
class DesignValidationResultTest {
  private DesignValidationResult result;

  @BeforeEach
  void setUp() {
    result = new DesignValidationResult();
  }

  @Nested
  @DisplayName("Initial State Tests")
  class InitialStateTests {
    @Test
    @DisplayName("Should start with empty messages")
    void shouldStartWithEmptyMessages() {
      assertTrue(result.getMessages().isEmpty());
    }

    @Test
    @DisplayName("Should start as valid")
    void shouldStartAsValid() {
      assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Should start with no warnings")
    void shouldStartWithNoWarnings() {
      assertFalse(result.hasWarnings());
    }

    @Test
    @DisplayName("Should start with no errors")
    void shouldStartWithNoErrors() {
      assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("Should start as not run")
    void shouldStartAsNotRun() {
      assertFalse(result.hasRun());
    }
  }

  @Nested
  @DisplayName("Add Message Tests")
  class AddMessageTests {
    @Test
    @DisplayName("Should add info message")
    void shouldAddInfoMessage() {
      result.addInfo("Equipment1", "Info message");

      assertEquals(1, result.getMessages().size());
      assertEquals(Severity.INFO, result.getMessages().get(0).getSeverity());
    }

    @Test
    @DisplayName("Should add warning message")
    void shouldAddWarningMessage() {
      result.addWarning("Category", "Equipment1", "Warning message", "Fix it");

      assertEquals(1, result.getMessages().size());
      assertEquals(Severity.WARNING, result.getMessages().get(0).getSeverity());
      assertTrue(result.hasWarnings());
    }

    @Test
    @DisplayName("Should add error message")
    void shouldAddErrorMessage() {
      result.addError("Category", "Equipment1", "Error message", "Fix it");

      assertEquals(1, result.getMessages().size());
      assertEquals(Severity.ERROR, result.getMessages().get(0).getSeverity());
      assertTrue(result.hasErrors());
    }

    @Test
    @DisplayName("Should add critical message")
    void shouldAddCriticalMessage() {
      result.addCritical("Category", "Equipment1", "Critical message", "Fix immediately");

      assertEquals(1, result.getMessages().size());
      assertEquals(Severity.CRITICAL, result.getMessages().get(0).getSeverity());
      assertTrue(result.hasErrors());
    }

    @Test
    @DisplayName("Should mark as run after adding message")
    void shouldMarkAsRunAfterAddingMessage() {
      result.addInfo("Equipment1", "Info");
      assertTrue(result.hasRun());
    }

    @Test
    @DisplayName("Should support method chaining")
    void shouldSupportChaining() {
      DesignValidationResult chainedResult = result.addInfo("E1", "Info1")
          .addWarning("Cat", "E2", "Warn", "Fix").addError("Cat", "E3", "Err", "Fix");

      assertEquals(result, chainedResult);
      assertEquals(3, result.getMessages().size());
    }
  }

  @Nested
  @DisplayName("Validity Tests")
  class ValidityTests {
    @Test
    @DisplayName("Should be valid with only info")
    void shouldBeValidWithOnlyInfo() {
      result.addInfo("Equipment1", "Info message");
      assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Should be valid with only warnings")
    void shouldBeValidWithOnlyWarnings() {
      result.addWarning("Category", "Equipment1", "Warning", "Fix");
      assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Should be invalid with errors")
    void shouldBeInvalidWithErrors() {
      result.addError("Category", "Equipment1", "Error", "Fix");
      assertFalse(result.isValid());
    }

    @Test
    @DisplayName("Should be invalid with critical errors")
    void shouldBeInvalidWithCritical() {
      result.addCritical("Category", "Equipment1", "Critical", "Fix");
      assertFalse(result.isValid());
    }
  }

  @Nested
  @DisplayName("Filter Tests")
  class FilterTests {
    @Test
    @DisplayName("Should filter by severity")
    void shouldFilterBySeverity() {
      result.addInfo("E1", "Info").addWarning("Cat", "E2", "Warn", "Fix").addError("Cat", "E3",
          "Err", "Fix");

      List<ValidationMessage> warnings = result.getMessages(Severity.WARNING);
      assertEquals(1, warnings.size());
      assertEquals("E2", warnings.get(0).getEquipmentName());
    }

    @Test
    @DisplayName("Should return empty list for missing severity")
    void shouldReturnEmptyForMissingSeverity() {
      result.addInfo("E1", "Info");

      List<ValidationMessage> critical = result.getMessages(Severity.CRITICAL);
      assertTrue(critical.isEmpty());
    }
  }

  @Nested
  @DisplayName("Count Tests")
  class CountTests {
    @Test
    @DisplayName("Should count messages by severity")
    void shouldCountBySeverity() {
      result.addInfo("E1", "Info1").addInfo("E2", "Info2").addWarning("Cat", "E3", "Warn", "Fix");

      assertEquals(2, result.getCount(Severity.INFO));
      assertEquals(1, result.getCount(Severity.WARNING));
      assertEquals(0, result.getCount(Severity.ERROR));
    }

    @Test
    @DisplayName("Should provide summary counts")
    void shouldProvideSummaryCounts() {
      result.addInfo("E1", "Info").addError("Cat", "E2", "Err", "Fix");

      Map<Severity, Integer> counts = result.getSummaryCounts();
      assertEquals(1, counts.get(Severity.INFO).intValue());
      assertEquals(0, counts.get(Severity.WARNING).intValue());
      assertEquals(1, counts.get(Severity.ERROR).intValue());
      assertEquals(0, counts.get(Severity.CRITICAL).intValue());
    }
  }

  @Nested
  @DisplayName("Metrics Tests")
  class MetricsTests {
    @Test
    @DisplayName("Should add metrics")
    void shouldAddMetrics() {
      result.addMetric("TotalWeight", 1000.0);
      result.addMetric("EquipmentCount", 5);

      Map<String, Object> metrics = result.getMetrics();
      assertEquals(2, metrics.size());
      assertEquals(1000.0, metrics.get("TotalWeight"));
      assertEquals(5, metrics.get("EquipmentCount"));
    }

    @Test
    @DisplayName("Should support metric chaining")
    void shouldSupportMetricChaining() {
      DesignValidationResult chainedResult =
          result.addMetric("M1", 1).addMetric("M2", 2).addMetric("M3", 3);

      assertEquals(result, chainedResult);
      assertEquals(3, result.getMetrics().size());
    }
  }

  @Nested
  @DisplayName("Merge Tests")
  class MergeTests {
    @Test
    @DisplayName("Should merge results")
    void shouldMergeResults() {
      DesignValidationResult other = new DesignValidationResult();
      other.addWarning("Cat", "E1", "Warn1", "Fix");

      result.addInfo("E2", "Info").merge(other);

      assertEquals(2, result.getMessages().size());
    }

    @Test
    @DisplayName("Should merge metrics")
    void shouldMergeMetrics() {
      result.addMetric("M1", 1);

      DesignValidationResult other = new DesignValidationResult();
      other.addMetric("M2", 2);

      result.merge(other);
      assertEquals(2, result.getMetrics().size());
    }

    @Test
    @DisplayName("Should handle null merge")
    void shouldHandleNullMerge() {
      result.addInfo("E1", "Info");
      result.merge(null);

      assertEquals(1, result.getMessages().size());
    }

    @Test
    @DisplayName("Merge should update hasRun")
    void mergeShouldUpdateHasRun() {
      DesignValidationResult other = new DesignValidationResult();
      other.addInfo("E1", "Info");

      result.merge(other);
      assertTrue(result.hasRun());
    }
  }

  @Nested
  @DisplayName("Summary Tests")
  class SummaryTests {
    @Test
    @DisplayName("Should generate summary")
    void shouldGenerateSummary() {
      result.addInfo("E1", "Info").addWarning("Cat", "E2", "Warn", "Fix");

      String summary = result.getSummary();
      assertNotNull(summary);
      assertTrue(summary.contains("VALID"));
    }

    @Test
    @DisplayName("Summary should show counts")
    void summaryShouldShowCounts() {
      result.addError("Cat", "E1", "Err", "Fix");

      String summary = result.getSummary();
      assertTrue(summary.contains("Errors: 1"));
    }
  }

  @Nested
  @DisplayName("ValidationMessage Tests")
  class ValidationMessageTests {
    @Test
    @DisplayName("Should store all properties")
    void shouldStoreAllProperties() {
      ValidationMessage msg = new ValidationMessage(Severity.WARNING, "Pressure", "Separator",
          "High pressure", "Reduce");

      assertEquals(Severity.WARNING, msg.getSeverity());
      assertEquals("Pressure", msg.getCategory());
      assertEquals("Separator", msg.getEquipmentName());
      assertEquals("High pressure", msg.getMessage());
      assertEquals("Reduce", msg.getRemediation());
    }

    @Test
    @DisplayName("ToString should include key info")
    void toStringShouldIncludeKeyInfo() {
      ValidationMessage msg = new ValidationMessage(Severity.ERROR, "Cat", "Equip", "Msg", "Fix");

      String str = msg.toString();
      assertTrue(str.contains("ERROR"));
      assertTrue(str.contains("Equip"));
    }
  }
}
