package neqsim.process.advisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for PredictionResult advisory system output.
 */
public class PredictionResultTest {
  private PredictionResult prediction;

  @BeforeEach
  void setUp() {
    prediction = new PredictionResult(Duration.ofHours(2), "Test Scenario");
  }

  @Test
  void testCreation() {
    assertNotNull(prediction);
  }

  @Test
  void testCreationWithHorizonOnly() {
    PredictionResult simple = new PredictionResult(Duration.ofHours(1));
    assertNotNull(simple);
  }

  @Test
  void testAddPredictedValue() {
    PredictionResult.PredictedValue value = new PredictionResult.PredictedValue(50.0, 2.5, "bara");

    prediction.addPredictedValue("separator.pressure", value);

    PredictionResult.PredictedValue retrieved = prediction.getValue("separator.pressure");
    assertNotNull(retrieved);
    assertEquals(50.0, retrieved.getMean(), 0.001);
  }

  @Test
  void testGetNonExistentValue() {
    assertNull(prediction.getValue("non-existent"));
  }

  @Test
  void testNoViolationsInitially() {
    assertFalse(prediction.hasViolations());
  }

  @Test
  void testAddViolation() {
    PredictionResult.ConstraintViolation violation =
        new PredictionResult.ConstraintViolation("pressure-limit", "separator.pressure", 85.0, 80.0,
            "bara", Duration.ofMinutes(30), PredictionResult.ConstraintViolation.Severity.MEDIUM);

    prediction.addViolation(violation);

    assertTrue(prediction.hasViolations());
  }

  @Test
  void testViolationSummary() {
    String summary = prediction.getViolationSummary();
    assertNotNull(summary);
    // Should mention "No constraint violations" when no violations
    assertTrue(summary.contains("No constraint violation"));
  }

  @Test
  void testViolationSummaryWithViolations() {
    prediction.addViolation(new PredictionResult.ConstraintViolation("test", "var", 10.0, 5.0,
        "bar", Duration.ofMinutes(10), PredictionResult.ConstraintViolation.Severity.MEDIUM));

    String summary = prediction.getViolationSummary();
    assertNotNull(summary);
    assertFalse(summary.contains("No constraint violation"));
  }

  @Test
  void testAdvisoryRecommendation() {
    String advice = prediction.getAdvisoryRecommendation();
    assertNotNull(advice);
    // Should mention "No action required" when no violations
    assertTrue(advice.contains("No action required"));
  }

  @Test
  void testAdvisoryRecommendationWithViolations() {
    prediction.addViolation(new PredictionResult.ConstraintViolation("pressure-limit",
        "separator.pressure", 85.0, 80.0, "bara", Duration.ofMinutes(10),
        PredictionResult.ConstraintViolation.Severity.CRITICAL));

    String advice = prediction.getAdvisoryRecommendation();
    assertNotNull(advice);
    assertTrue(advice.contains("ADVISORY"));
  }

  @Test
  void testAddAssumption() {
    prediction.addAssumption("Constant inlet conditions");
    assertTrue(prediction.getAssumptions().contains("Constant inlet conditions"));
  }

  @Test
  void testSetExplanation() {
    prediction.setExplanation("Pressure increasing due to cooling loss");
    assertEquals("Pressure increasing due to cooling loss", prediction.getExplanation());
  }

  @Test
  void testSetOverallConfidence() {
    prediction.setOverallConfidence(0.85);
    assertEquals(0.85, prediction.getOverallConfidence(), 0.001);
  }

  @Test
  void testOverallConfidenceBounds() {
    prediction.setOverallConfidence(1.5); // Should clamp to 1.0
    assertEquals(1.0, prediction.getOverallConfidence(), 0.001);

    prediction.setOverallConfidence(-0.5); // Should clamp to 0.0
    assertEquals(0.0, prediction.getOverallConfidence(), 0.001);
  }

  @Test
  void testPredictionStatus() {
    assertEquals(PredictionResult.PredictionStatus.SUCCESS,
        PredictionResult.PredictionStatus.SUCCESS);
    assertEquals(PredictionResult.PredictionStatus.WARNING,
        PredictionResult.PredictionStatus.WARNING);
    assertEquals(PredictionResult.PredictionStatus.FAILED,
        PredictionResult.PredictionStatus.FAILED);
    assertEquals(PredictionResult.PredictionStatus.DATA_QUALITY_ISSUE,
        PredictionResult.PredictionStatus.DATA_QUALITY_ISSUE);
  }

  @Test
  void testPredictedValueWithBounds() {
    PredictionResult.PredictedValue value =
        new PredictionResult.PredictedValue(50.0, 45.0, 55.0, "bara", 0.95);

    assertEquals(50.0, value.getMean(), 0.001);
    assertEquals(45.0, value.getLower95(), 0.001);
    assertEquals(55.0, value.getUpper95(), 0.001);
    assertEquals(0.95, value.getConfidence(), 0.001);
  }

  @Test
  void testPredictedValueDeterministic() {
    PredictionResult.PredictedValue value =
        PredictionResult.PredictedValue.deterministic(100.0, "kg/hr");

    assertEquals(100.0, value.getMean(), 0.001);
    assertEquals(0.0, value.getStandardDeviation(), 0.001);
  }

  @Test
  void testPredictedValueToString() {
    PredictionResult.PredictedValue value = new PredictionResult.PredictedValue(50.0, 2.5, "bara");

    String str = value.toString();
    assertNotNull(str);
    assertTrue(str.contains("bara"));
  }

  @Test
  void testConstraintViolationSeverity() {
    assertEquals(PredictionResult.ConstraintViolation.Severity.LOW,
        PredictionResult.ConstraintViolation.Severity.LOW);
    assertEquals(PredictionResult.ConstraintViolation.Severity.MEDIUM,
        PredictionResult.ConstraintViolation.Severity.MEDIUM);
    assertEquals(PredictionResult.ConstraintViolation.Severity.HIGH,
        PredictionResult.ConstraintViolation.Severity.HIGH);
    assertEquals(PredictionResult.ConstraintViolation.Severity.CRITICAL,
        PredictionResult.ConstraintViolation.Severity.CRITICAL);
  }

  @Test
  void testConstraintViolationWithSuggestedAction() {
    PredictionResult.ConstraintViolation violation =
        new PredictionResult.ConstraintViolation("pressure-limit", "sep.pressure", 85.0, 80.0,
            "bara", Duration.ofMinutes(30), PredictionResult.ConstraintViolation.Severity.MEDIUM);
    violation.setSuggestedAction("Reduce inlet flow");

    assertEquals("Reduce inlet flow", violation.getSuggestedAction());
  }

  @Test
  void testConstraintViolationDescription() {
    PredictionResult.ConstraintViolation violation =
        new PredictionResult.ConstraintViolation("pressure-limit", "sep.pressure", 85.0, 80.0,
            "bara", Duration.ofMinutes(30), PredictionResult.ConstraintViolation.Severity.MEDIUM);

    String description = violation.getDescription();
    assertNotNull(description);
    assertTrue(description.contains("pressure-limit"));
  }

  @Test
  void testConstraintViolationGetters() {
    PredictionResult.ConstraintViolation violation =
        new PredictionResult.ConstraintViolation("pressure-limit", "sep.pressure", 85.0, 80.0,
            "bara", Duration.ofMinutes(30), PredictionResult.ConstraintViolation.Severity.HIGH);

    assertEquals("pressure-limit", violation.getConstraintName());
    assertEquals("sep.pressure", violation.getVariableName());
    assertEquals(85.0, violation.getPredictedValue(), 0.001);
    assertEquals(80.0, violation.getLimitValue(), 0.001);
    assertEquals("bara", violation.getUnit());
    assertEquals(Duration.ofMinutes(30), violation.getTimeToViolation());
    assertEquals(PredictionResult.ConstraintViolation.Severity.HIGH, violation.getSeverity());
  }

  @Test
  void testGetStatus() {
    PredictionResult.PredictionStatus status = prediction.getStatus();
    assertEquals(PredictionResult.PredictionStatus.SUCCESS, status);
  }

  @Test
  void testStatusChangesWithViolation() {
    prediction.addViolation(new PredictionResult.ConstraintViolation("test", "var", 10.0, 5.0,
        "bar", Duration.ofMinutes(10), PredictionResult.ConstraintViolation.Severity.MEDIUM));

    assertEquals(PredictionResult.PredictionStatus.WARNING, prediction.getStatus());
  }

  @Test
  void testGetPredictionTime() {
    assertNotNull(prediction.getPredictionTime());
  }

  @Test
  void testGetHorizon() {
    assertEquals(Duration.ofHours(2), prediction.getHorizon());
  }

  @Test
  void testGetScenarioName() {
    assertEquals("Test Scenario", prediction.getScenarioName());
  }

  @Test
  void testGetAllPredictedValues() {
    prediction.addPredictedValue("var1", new PredictionResult.PredictedValue(1.0, 0.1, "unit"));
    prediction.addPredictedValue("var2", new PredictionResult.PredictedValue(2.0, 0.2, "unit"));

    assertEquals(2, prediction.getAllPredictedValues().size());
  }

  @Test
  void testSetStatus() {
    prediction.setStatus(PredictionResult.PredictionStatus.FAILED);
    assertEquals(PredictionResult.PredictionStatus.FAILED, prediction.getStatus());
  }
}
