package neqsim.process.safety.processsafetysystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * Tests for NORSOK S-001 Clause 10 process safety system review functionality.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ProcessSafetySystemReviewEngineTest {

  /**
   * Verifies that complete normalized evidence returns a passing review.
   */
  @Test
  void completeClause10EvidencePasses() {
    ProcessSafetySystemReviewInput input = new ProcessSafetySystemReviewInput()
        .setProjectName("Synthetic Clause 10 review");
    input.addItem(baseItem("PSD-1001", "PSD").put("shutdownActionDefined", Boolean.TRUE)
        .put("psdValveFailsSafe", Boolean.TRUE).put("psdValveIsolationAdequate", Boolean.TRUE)
        .put("requiredResponseTimeSeconds", 30.0).put("actualResponseTimeSeconds", 18.0)
        .put("logicSolverCertified", Boolean.TRUE).put("logicSolverIndependent", Boolean.TRUE)
        .put("causeAndEffectTested", Boolean.TRUE).put("psdIndependentFromControl", Boolean.TRUE)
        .put("manualShutdownAvailable", Boolean.TRUE));
    input.addItem(baseItem("PSV-1001", "PSV").put("psvSizingBasisDocumented", Boolean.TRUE)
        .put("reliefScenarioDocumented", Boolean.TRUE)
        .put("protectedEquipmentDocumented", Boolean.TRUE).put("requiredReliefLoadKgPerS", 10.0)
        .put("psvCapacityKgPerS", 14.0));
    input.addItem(baseItem("PAHH-1001", "ALARM").put("alarmActionDefined", Boolean.TRUE)
        .put("alarmSetpointDocumented", Boolean.TRUE).put("operatorResponseTimeSeconds", 120.0)
        .put("availableOperatorResponseTimeSeconds", 300.0)
        .put("requiredResponseTimeSeconds", 300.0).put("actualResponseTimeSeconds", 120.0));
    input.addItem(baseItem("SIF-1001", "SECONDARY_PRESSURE_PROTECTION")
        .put("logicSolverCertified", Boolean.TRUE).put("logicSolverIndependent", Boolean.TRUE)
        .put("causeAndEffectTested", Boolean.TRUE).put("maximumEventPressureBara", 120.0)
        .put("designPressureBara", 100.0).put("testPressureBara", 150.0)
        .put("demandFrequencyPerYear", 1.0e-4).put("reliefLeakageAssessed", Boolean.TRUE)
        .put("reliefLeakageToSafeLocation", Boolean.TRUE).put("proofTestIntervalMonths", 6.0)
        .put("requiredResponseTimeSeconds", 10.0).put("actualResponseTimeSeconds", 5.0));

    ProcessSafetySystemReviewReport report = new ProcessSafetySystemReviewEngine().evaluate(input);

    JsonObject json = JsonParser.parseString(report.toJson()).getAsJsonObject();
    assertEquals("success", json.get("status").getAsString());
    assertEquals("PASS", json.get("overallVerdict").getAsString());
    assertTrue(report.toJson().contains("10.4.7"));
  }

  /**
   * Verifies that secondary pressure protection fails when event pressure exceeds the test pressure.
   */
  @Test
  void secondaryPressureProtectionFailsAboveTestPressure() {
    ProcessSafetySystemReviewInput input = new ProcessSafetySystemReviewInput()
        .setProjectName("Secondary pressure protection fail case");
    input.addItem(baseItem("SIF-HP-001", "SECONDARY_PRESSURE_PROTECTION")
        .put("logicSolverCertified", Boolean.TRUE).put("logicSolverIndependent", Boolean.TRUE)
        .put("causeAndEffectTested", Boolean.TRUE).put("maximumEventPressureBara", 175.0)
        .put("designPressureBara", 100.0).put("testPressureBara", 150.0)
        .put("demandFrequencyPerYear", 1.0e-3).put("reliefLeakageAssessed", Boolean.TRUE)
        .put("reliefLeakageToSafeLocation", Boolean.TRUE).put("proofTestIntervalMonths", 6.0)
        .put("requiredResponseTimeSeconds", 10.0).put("actualResponseTimeSeconds", 5.0));

    ProcessSafetySystemReviewReport report = new ProcessSafetySystemReviewEngine().evaluate(input);

    assertEquals("FAIL", report.getOverallVerdict());
    assertTrue(report.toJson().contains("PSS-10.4.7-SECONDARY"));
  }

  /**
   * Verifies that instrument data with active bypass fails the review.
   */
  @Test
  void activeBypassFromInstrumentDataFailsReview() {
    ProcessSafetySystemReviewInput input = new ProcessSafetySystemReviewInput()
        .setProjectName("Instrument data fail case");
    input.addItem(baseItem("PSD-1002", "PSD").put("shutdownActionDefined", Boolean.TRUE)
        .put("psdValveFailsSafe", Boolean.TRUE).put("psdValveIsolationAdequate", Boolean.TRUE)
        .put("requiredResponseTimeSeconds", 30.0).put("actualResponseTimeSeconds", 18.0)
        .put("logicSolverCertified", Boolean.TRUE).put("logicSolverIndependent", Boolean.TRUE)
        .put("causeAndEffectTested", Boolean.TRUE).put("psdIndependentFromControl", Boolean.TRUE)
        .put("manualShutdownAvailable", Boolean.TRUE).put("bypassActive", Boolean.TRUE));

    ProcessSafetySystemReviewReport report = new ProcessSafetySystemReviewEngine().evaluate(input);

    assertEquals("FAIL", report.getOverallVerdict());
    assertTrue(report.toJson().contains("active bypass"));
  }

  /**
   * Verifies that explicit lifecycle verification failure is treated as a failing finding.
   */
  @Test
  void missingVerificationTestingOperationFailsReview() {
    ProcessSafetySystemReviewInput input = new ProcessSafetySystemReviewInput()
        .setProjectName("Lifecycle fail case");
    input.addItem(baseItem("SIF-VERIFY-001", "SIF").put("logicSolverCertified", Boolean.TRUE)
        .put("logicSolverIndependent", Boolean.TRUE).put("causeAndEffectTested", Boolean.TRUE)
        .put("requiredResponseTimeSeconds", 10.0).put("actualResponseTimeSeconds", 5.0)
        .put("verificationTestingOperationConfirmed", Boolean.FALSE));

    ProcessSafetySystemReviewReport report = new ProcessSafetySystemReviewEngine().evaluate(input);

    assertEquals("FAIL", report.getOverallVerdict());
    assertTrue(report.toJson().contains("PSS-LIFECYCLE-VERIFY-TEST-OPERATE"));
  }

  /**
   * Creates a common passing review item with general Clause 10 evidence.
   *
   * @param functionId function identifier
   * @param functionType function type
   * @return configured review item
   */
  private ProcessSafetySystemReviewItem baseItem(String functionId, String functionType) {
    return new ProcessSafetySystemReviewItem().setFunctionId(functionId).setFunctionType(functionType)
        .setEquipmentTag("V-100").addSourceReference("synthetic C&E")
        .addSourceReference("synthetic SRS").put("processSafetyRoleDefined", Boolean.TRUE)
        .put("hazidHazopLopaCompleted", Boolean.TRUE)
        .put("srsDefinesRequiredFunctions", Boolean.TRUE)
        .put("sisEsdFgsDesignImplemented", Boolean.TRUE)
        .put("verificationTestingOperationConfirmed", Boolean.TRUE)
        .put("interfacesDefined", Boolean.TRUE).put("protectionLayersDocumented", Boolean.TRUE)
        .put("designBasisDocumented", Boolean.TRUE)
        .put("processSafetyPrinciplesDocumented", Boolean.TRUE)
        .put("bypassManagementDocumented", Boolean.TRUE)
        .put("requiredUtilitiesIdentified", Boolean.TRUE).put("utilityDependent", Boolean.TRUE)
        .put("failSafeOnUtilityLoss", Boolean.TRUE)
        .put("survivabilityRequirementDocumented", Boolean.TRUE)
        .put("requiredSurvivabilityTimeMin", 30.0).put("survivabilityTimeMin", 60.0)
        .put("tagreaderSource", "synthetic instrument data").put("overrideActive", Boolean.FALSE)
        .put("proofTestOverdue", Boolean.FALSE).put("tripDemandFailures", 0.0);
  }
}