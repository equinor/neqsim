package neqsim.process.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OperatingTrialAssessment} and {@link TrialAssessmentResult}.
 *
 * @author ESOL
 * @version 1.0
 */
class OperatingTrialAssessmentTest {

  /**
   * Adds a flat signal to an assessment across both windows.
   *
   * @param assessment assessment to populate
   * @param name signal name
   * @param preValue value during the pre window
   * @param postValue value during the post window
   * @param unit engineering unit
   */
  private static void addFlatSignal(OperatingTrialAssessment assessment, String name, double preValue, double postValue,
      String unit) {
    for (int t = 0; t <= 4; t++) {
      assessment.addSignal(name, t, preValue, "Good", unit);
    }
    for (int t = 6; t <= 10; t++) {
      assessment.addSignal(name, t, postValue, "Good", unit);
    }
  }

  /**
   * Verifies that a simultaneous large flow change is flagged as a confounder and blocks the causal claim while still
   * reporting the descriptive power effect.
   */
  @Test
  void flagsFlowChangeAsConfounder() {
    OperatingTrialAssessment assessment = new OperatingTrialAssessment("Separator pressure trial");
    addFlatSignal(assessment, "separator.pressure", 24.0, 20.0, "barg");
    addFlatSignal(assessment, "compressor.power", 5.0, 4.6, "MW");
    addFlatSignal(assessment, "feed.flow", 100.0, 120.0, "kg/hr");

    assessment.setIntervention("separator.pressure", 24.0, 20.0, 5.0);
    assessment.addConfounder("feed.flow", 0.05);

    TrialAssessmentResult result = assessment.assess(0.0, 4.0, 6.0, 10.0);

    assertFalse(result.isCausalClaimAllowed());
    assertEquals(TrialAssessmentResult.RESULT_TYPE_DESCRIPTIVE, result.getResultType());
    assertTrue(result.isInterventionApplied());

    TrialAssessmentResult.SignalEffect powerEffect = result.getSignalEffect("compressor.power");
    assertNotNull(powerEffect);
    assertEquals(-0.4, powerEffect.getDelta(), 1.0e-9);

    TrialAssessmentResult.SignalEffect flowEffect = result.getSignalEffect("feed.flow");
    assertEquals(0.2, flowEffect.getNormalizedChange(), 1.0e-9);
  }

  /**
   * Verifies that a controlled trial with a stable confounder and good data supports a causal reading.
   */
  @Test
  void allowsCausalClaimWhenConfoundersStable() {
    OperatingTrialAssessment assessment = new OperatingTrialAssessment("Controlled pressure trial");
    addFlatSignal(assessment, "separator.pressure", 24.0, 20.0, "barg");
    addFlatSignal(assessment, "compressor.power", 5.0, 4.6, "MW");
    addFlatSignal(assessment, "feed.flow", 100.0, 101.0, "kg/hr");

    assessment.setIntervention("separator.pressure", 24.0, 20.0, 5.0);
    assessment.addConfounder("feed.flow", 0.05);

    TrialAssessmentResult result = assessment.assess(0.0, 4.0, 6.0, 10.0);

    assertTrue(result.isCausalClaimAllowed());
    assertEquals(TrialAssessmentResult.RESULT_TYPE_CAUSAL_SUPPORTED, result.getResultType());
    assertTrue(result.getWarnings().isEmpty());
  }

  /**
   * Verifies that poor data quality in a window blocks the causal claim.
   */
  @Test
  void rejectsCausalClaimOnPoorDataQuality() {
    OperatingTrialAssessment assessment = new OperatingTrialAssessment("Poor data trial");
    assessment.setMinGoodDataFraction(0.8);
    for (int t = 0; t <= 4; t++) {
      assessment.addSignal("separator.pressure", t, 24.0, "Good", "barg");
    }
    // Post window mostly bad quality.
    assessment.addSignal("separator.pressure", 6, 20.0, "Good", "barg");
    for (int t = 7; t <= 10; t++) {
      assessment.addSignal("separator.pressure", t, 20.0, "Bad", "barg");
    }

    assessment.setIntervention("separator.pressure", 24.0, 20.0, 5.0);

    TrialAssessmentResult result = assessment.assess(0.0, 4.0, 6.0, 10.0);

    assertFalse(result.isCausalClaimAllowed());
    TrialAssessmentResult.SignalEffect effect = result.getSignalEffect("separator.pressure");
    assertFalse(effect.isDataQualityOk());
  }

  /**
   * Verifies that a setpoint that did not actually move fails the intervention gate.
   */
  @Test
  void rejectsCausalClaimWhenInterventionNotConfirmed() {
    OperatingTrialAssessment assessment = new OperatingTrialAssessment("No move trial");
    addFlatSignal(assessment, "separator.pressure", 24.0, 24.0, "barg");

    assessment.setIntervention("separator.pressure", 24.0, 20.0, 5.0);

    TrialAssessmentResult result = assessment.assess(0.0, 4.0, 6.0, 10.0);

    assertFalse(result.isInterventionApplied());
    assertFalse(result.isCausalClaimAllowed());
  }

  /**
   * Verifies that JSON evidence is schema-versioned and includes the result.
   */
  @Test
  void emitsSchemaVersionedJson() {
    OperatingTrialAssessment assessment = new OperatingTrialAssessment("JSON trial");
    addFlatSignal(assessment, "separator.pressure", 24.0, 20.0, "barg");
    assessment.setIntervention("separator.pressure", 24.0, 20.0, 5.0);
    assessment.assess(0.0, 4.0, 6.0, 10.0);

    String json = assessment.toJson();
    assertTrue(json.contains("\"schemaVersion\""));
    assertTrue(json.contains(TrialAssessmentResult.SCHEMA_VERSION));
    assertTrue(json.contains("\"result\""));
  }

  /**
   * Verifies that assessing without an intervention fails fast.
   */
  @Test
  void requiresInterventionBeforeAssessment() {
    OperatingTrialAssessment assessment = new OperatingTrialAssessment("Missing intervention");
    addFlatSignal(assessment, "separator.pressure", 24.0, 20.0, "barg");
    assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        assessment.assess(0.0, 4.0, 6.0, 10.0);
      }
    });
  }
}
