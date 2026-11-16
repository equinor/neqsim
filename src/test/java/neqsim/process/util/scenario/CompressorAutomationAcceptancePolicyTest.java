package neqsim.process.util.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.scenario.DynamicScenarioCriterion;
import neqsim.process.util.scenario.CompressorAutomationAcceptancePolicy.CriterionPolicy;
import neqsim.process.util.scenario.CompressorAutomationAcceptancePolicy.DeadlineBasis;
import neqsim.process.util.scenario.CompressorAutomationStudy.CaseDefinition;
import neqsim.process.util.scenario.CompressorAutomationStudy.Profile;

/**
 * Completeness, validation, determinism and physical-decoupling regressions for compressor acceptance policies.
 *
 * @author NeqSim
 * @version 1.0
 */
class CompressorAutomationAcceptancePolicyTest {
  /** Default policy is complete, ordered and canonically deterministic. */
  @Test
  void defaultPolicyCoversEveryPublicProfileDeterministically() {
    CompressorAutomationAcceptancePolicy first = CompressorAutomationAcceptancePolicy.defaultPolicy();
    CompressorAutomationAcceptancePolicy second = CompressorAutomationAcceptancePolicy.defaultPolicy();
    assertEquals(first.getFingerprint(), second.getFingerprint());
    assertEquals(new Gson().toJson(first.toMap()), new Gson().toJson(second.toMap()));
    assertEquals(Profile.values().length, ((java.util.List<?>) first.toMap().get("profiles")).size());
    assertEquals(
        Arrays.asList("DISCHARGE_ENVELOPE", "SURGE_MARGIN", "NO_UNWANTED_TRIP", "GAS_CV_APPLICABILITY",
            "SUCTION_ENERGY", "DISCHARGE_ENERGY"),
        first.getProfilePolicy(Profile.NORMAL).getCriteria().stream().map(CriterionPolicy::getId)
            .collect(java.util.stream.Collectors.toList()));
  }

  /** Invalid coverage, identities, units, duplicate overrides, bounds and deadlines fail before execution. */
  @Test
  void invalidPolicyContractsFailClosed() {
    CompressorAutomationAcceptancePolicy defaults = CompressorAutomationAcceptancePolicy.defaultPolicy();
    assertThrows(IllegalArgumentException.class, () -> new CompressorAutomationAcceptancePolicy(
        Collections.singletonList(defaults.getProfilePolicy(Profile.NORMAL))));
    CriterionPolicy discharge = defaults.getCriterion(Profile.NORMAL, "DISCHARGE_ENVELOPE");
    assertThrows(IllegalArgumentException.class, () -> defaults.withOverrides(Profile.NORMAL, discharge, discharge));
    CriterionPolicy unknown = new CriterionPolicy("UNKNOWN", "boolean", 1.0, 1.0, DeadlineBasis.CASE_HORIZON, null,
        DynamicScenarioCriterion.EvaluationMode.MAINTAIN_BOUNDS, true);
    assertThrows(IllegalArgumentException.class, () -> defaults.withOverrides(Profile.NORMAL, unknown));
    CriterionPolicy wrongUnit = new CriterionPolicy(discharge.getId(), "bar gauge", discharge.getLowerLimit(),
        discharge.getUpperLimit(), discharge.getDeadlineBasis(), discharge.getDeadlineSeconds(),
        discharge.getEvaluationMode(), discharge.isRequired());
    assertThrows(IllegalArgumentException.class, () -> defaults.withOverrides(Profile.NORMAL, wrongUnit));
    assertThrows(IllegalArgumentException.class,
        () -> discharge.withAcceptanceRange(Double.valueOf(10.0), Double.valueOf(1.0)));
    assertThrows(IllegalArgumentException.class,
        () -> discharge.withAcceptanceRange(Double.valueOf(Double.NaN), Double.valueOf(1.0)));
    CriterionPolicy late = discharge.withDeadlineSeconds(20.0);
    CompressorAutomationAcceptancePolicy latePolicy = defaults.withOverrides(Profile.NORMAL, late);
    CaseDefinition shortCase = new CaseDefinition(Profile.NORMAL, 0.2, 8.0, 1.0);
    assertThrows(IllegalArgumentException.class, () -> CompressorAutomationStudy.createCase(
        CompressorAutomationStudy.create(new CompressorAutomationStudy.Design()), shortCase, null, latePolicy));
  }

  /** Policy-only changes revise verdict evidence while preserving every exact physical sample. */
  @Test
  void policyOnlyLimitChangePreservesPhysicalTrajectory() {
    CaseDefinition definition = new CaseDefinition(Profile.NORMAL, 0.2, 8.0, 1.0);
    CompressorAutomationAcceptancePolicy defaults = CompressorAutomationAcceptancePolicy.defaultPolicy();
    CriterionPolicy impossiblePressure = defaults.getCriterion(Profile.NORMAL, "DISCHARGE_ENVELOPE")
        .withAcceptanceRange(1.0, 1.0);
    CompressorAutomationAcceptancePolicy revised = defaults.withOverrides(Profile.NORMAL, impossiblePressure);

    CompressorAutomationStudy.Result baseline = CompressorAutomationStudy
        .run(CompressorAutomationStudy.create(new CompressorAutomationStudy.Design()), definition, defaults);
    CompressorAutomationStudy.Result changed = CompressorAutomationStudy
        .run(CompressorAutomationStudy.create(new CompressorAutomationStudy.Design()), definition, revised);

    assertTrue(baseline.getNativeResult().isPassed(), baseline.getNativeResult().toMap().toString());
    assertFalse(changed.getNativeResult().isPassed());
    assertEquals(new Gson().toJson(baseline.getSamples()), new Gson().toJson(changed.getSamples()));
    assertNotEquals(defaults.getFingerprint(), revised.getFingerprint());
    assertEquals(defaults.getFingerprint(), baseline.toMap().get("acceptancePolicyFingerprint"));
    assertEquals(revised.getFingerprint(), changed.toMap().get("acceptancePolicyFingerprint"));
    assertTrue(new Gson().toJson(changed.getNativeResult().toMap()).contains("DISCHARGE_ENVELOPE"));
  }
}
