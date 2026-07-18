package neqsim.process.engineering.safety.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neqsim.process.engineering.EngineeringApprovalStatus;
import neqsim.process.engineering.safety.lifecycle.ProtectionLayerDefinition.LayerType;
import neqsim.process.engineering.safety.lifecycle.SafetyRequirementSpecificationDraft.TripDirection;
import org.junit.jupiter.api.Test;

/** Tests IPL eligibility, LOPA arithmetic, and review-required draft-SRS creation. */
class HazopLopaSrsWorkflowTest {

  @Test
  void creditsOnlyEligibleLayersAndCreatesTraceableDraftSrs() {
    LopaScenarioDefinition scenario = LopaScenarioDefinition.builder("LOPA-HP-101", "HAZOP-NODE-07", "MORE-PRESSURE-03")
        .equipmentTag("V-101").initiatingEvent("Inlet control valve fails open", 0.1)
        .consequence("HP separator rupture and hydrocarbon release").targetFrequencyPerYear(2.0e-6)
        .frequencyBasisReference("QRA-BASIS-2026-R2")
        .addProtectionLayer(eligibleLayer("IPL-BPCS", "Independent pressure control", LayerType.BPCS, 0.1))
        .addProtectionLayer(ProtectionLayerDefinition
            .builder("IPL-ALARM", "High-pressure alarm and operator", LayerType.ALARM_AND_OPERATOR, 0.1)
            .independentFromInitiatingEvent(false).independentFromOtherLayers(true).specific(true).auditable(true)
            .proofTestIntervalHours(8760.0).evidenceReference("ALARM-TEST-101").build())
        .addProtectionLayer(eligibleLayer("IPL-PSV", "PSV to flare", LayerType.RELIEF, 0.1)).build();
    HazopLopaSrsWorkflow.SrsDesignInputs srsInputs = srsInputs();

    HazopLopaSrsWorkflow.Result result = HazopLopaSrsWorkflow.run(scenario, srsInputs);

    assertEquals(2, result.getLopaResult().getLayers().size());
    assertEquals(1.0e-3, result.getLopaResult().getMitigatedFrequency(), 1.0e-15);
    assertFalse(result.getLopaResult().isTargetMet());
    assertEquals(2, result.getLopaResult().getRequiredAdditionalSIL());
    assertTrue(result.getFindings().get(0).contains("IPL-ALARM"));
    SafetyRequirementSpecificationDraft draft = result.getSrsDraft();
    assertNotNull(draft);
    assertEquals("SRS-SIF-HP-101", draft.getSrsRequirementId());
    assertEquals("SIF-HP-101", draft.getSifTag());
    assertEquals(2, draft.getRequiredSil());
    assertEquals(EngineeringApprovalStatus.REVIEW_REQUIRED, draft.getApprovalStatus());
    assertEquals(Boolean.FALSE, result.toMap().get("fitForConstruction"));
  }

  @Test
  void doesNotCreateSifDraftWhenCreditedLayersMeetTarget() {
    LopaScenarioDefinition scenario = LopaScenarioDefinition.builder("LOPA-OK-01", "HAZOP-NODE-01", "NO-FLOW-01")
        .equipmentTag("P-101").initiatingEvent("Pump trip", 1.0e-2).consequence("Production interruption")
        .targetFrequencyPerYear(1.1e-3).frequencyBasisReference("OPS-FREQ-2026")
        .addProtectionLayer(eligibleLayer("IPL-STANDBY", "Automatic standby pump", LayerType.BPCS, 0.1)).build();

    HazopLopaSrsWorkflow.Result result = HazopLopaSrsWorkflow.run(scenario, srsInputs());

    assertTrue(result.getLopaResult().isTargetMet());
    assertNull(result.getSrsDraft());
    assertEquals(Boolean.FALSE, result.toMap().get("srsDraftCreated"));
  }

  private static ProtectionLayerDefinition eligibleLayer(String id, String name, LayerType type, double pfd) {
    return ProtectionLayerDefinition.builder(id, name, type, pfd).independentFromInitiatingEvent(true)
        .independentFromOtherLayers(true).specific(true).auditable(true).proofTestIntervalHours(8760.0)
        .evidenceReference("EVIDENCE-" + id).build();
  }

  private static HazopLopaSrsWorkflow.SrsDesignInputs srsInputs() {
    return HazopLopaSrsWorkflow.SrsDesignInputs.builder("SRS-SIF-HP-101", "SIF-HP-101", "LOPA-HP-101-R1")
        .trip("separator pressure", TripDirection.HIGH, 60.0, "bara")
        .safeState("ESDV-101 closed and HP separator isolated").maximumResponseTimeSeconds(5.0)
        .votingArchitecture("2oo3").proofTestIntervalHours(8760.0)
        .resetPolicy("Manual reset after process permissive and field verification")
        .bypassPolicy("Permit-controlled single-channel bypass with compensating measures and time limit").build();
  }
}
