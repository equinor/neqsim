package neqsim.process.engineering;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neqsim.process.engineering.production.EngineeringExternalEvidenceAssessment;
import neqsim.process.engineering.production.EngineeringExternalEvidenceRecord;
import neqsim.process.engineering.production.EngineeringExternalEvidenceRegister;
import neqsim.process.engineering.production.EngineeringProductionReadinessAssessment;
import neqsim.process.engineering.production.EngineeringProductionReadinessBasis;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringGraphBuilder;
import neqsim.process.engineering.model.EngineeringIds;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.processmodel.ProcessSystem;
import org.junit.jupiter.api.Test;

/** Verifies fail-closed handling of external engineering decisions and guarantees. */
class EngineeringExternalEvidenceAssessmentTest {

  @Test
  void acceptsCompleteControlledReceiptsWithoutGrantingAuthority() {
    String scope = "project:external-evidence";
    EngineeringExternalEvidenceRegister register = ExternalEvidenceTestSupport.completeRegister(scope);
    EngineeringExternalEvidenceAssessment.Result result = EngineeringExternalEvidenceAssessment.assess(register);

    assertTrue(result.isPassed());
    assertTrue(result.isTypePassed(EngineeringExternalEvidenceRecord.Type.VENDOR_GUARANTEE));
    assertTrue(result.isTypePassed(EngineeringExternalEvidenceRecord.Type.CONSTRUCTION_AUTHORITY));
    assertTrue(Boolean.FALSE.equals(result.toMap().get("simulatorGrantedConstructionAuthority")));

    EngineeringProject project = new EngineeringProject("external-evidence", new ProcessSystem(),
        new EngineeringDesignBasis());
    EngineeringProductionReadinessBasis basis = new EngineeringProductionReadinessBasis()
        .externalEvidenceRegister(register);
    project.setProductionReadinessBasis(basis);
    EngineeringProductionReadinessAssessment.Result readiness = EngineeringProductionReadinessAssessment.assess(project,
        basis);
    assertFalse(readiness.getFailedGates().contains("ACCOUNTABLE_ENGINEERING_APPROVALS"));
    assertFalse(readiness.getFailedGates().contains("VENDOR_GUARANTEES"));
    assertFalse(readiness.getFailedGates().contains("HAZOP_DECISIONS"));
    assertFalse(readiness.getFailedGates().contains("LOPA_DECISIONS"));
    assertFalse(readiness.getFailedGates().contains("SRS_APPROVAL"));
    assertFalse(readiness.getFailedGates().contains("INDEPENDENT_VALIDATION_ACCEPTANCE"));
    assertFalse(readiness.getFailedGates().contains("CONSTRUCTION_AUTHORITY_EVIDENCE"));
    assertTrue(Boolean.FALSE.equals(readiness.toMap().get("fitnessForConstruction")));
    EngineeringGraph graph = EngineeringGraphBuilder.fromProject(project);
    assertTrue(graph.getNode(EngineeringIds.nodeId(EngineeringNode.Kind.DOCUMENT, "EV-VENDOR_GUARANTEE-A")) != null);
  }

  @Test
  void rejectsIncompleteConflictingAndSupersededDecisions() {
    String scope = "project:external-conflict";
    EngineeringExternalEvidenceRegister register = ExternalEvidenceTestSupport.completeRegister(scope);
    register.addRecord(EngineeringExternalEvidenceRecord
        .builder("EV-VENDOR-REJECTED", EngineeringExternalEvidenceRecord.Type.VENDOR_GUARANTEE, "DOC-VENDOR-REJECTED",
            "A")
        .title("Rejected vendor guarantee")
        .controlledDocument("stid://DOC-VENDOR-REJECTED/A", ExternalEvidenceTestSupport.SHA256)
        .issuer("Vendor", "Vendor authority", "2026-07-18").addScopeReference(scope)
        .decision(EngineeringExternalEvidenceRecord.Status.REJECTED, "Machinery authority", "Technical authority",
            "2026-07-18", "workflow://vendor/rejected")
        .build());

    EngineeringExternalEvidenceAssessment.Result conflict = EngineeringExternalEvidenceAssessment.assess(register);
    assertFalse(conflict.isPassed());
    assertFalse(conflict.isTypePassed(EngineeringExternalEvidenceRecord.Type.VENDOR_GUARANTEE));
    assertTrue(conflict.getFindings().toString().contains("CONFLICTING_ACTIVE_DECISIONS"));

    EngineeringExternalEvidenceRegister superseded = ExternalEvidenceTestSupport.completeRegister(scope);
    superseded.addRecord(EngineeringExternalEvidenceRecord
        .builder("EV-VENDOR-DRAFT-B", EngineeringExternalEvidenceRecord.Type.VENDOR_GUARANTEE, "DOC-VENDOR-DRAFT", "B")
        .title("Replacement vendor guarantee")
        .controlledDocument("stid://DOC-VENDOR-DRAFT/B", ExternalEvidenceTestSupport.SHA256)
        .issuer("Vendor", "Vendor authority", "2026-07-19").addScopeReference(scope).supersedes("EV-VENDOR_GUARANTEE-A")
        .build());
    EngineeringExternalEvidenceAssessment.Result draft = EngineeringExternalEvidenceAssessment.assess(superseded);
    assertFalse(draft.isTypePassed(EngineeringExternalEvidenceRecord.Type.VENDOR_GUARANTEE));

    EngineeringExternalEvidenceRegister invalidHash = ExternalEvidenceTestSupport.completeRegister(scope);
    invalidHash.addRecord(EngineeringExternalEvidenceRecord
        .builder("EV-HAZOP-BAD-HASH", EngineeringExternalEvidenceRecord.Type.HAZOP_DECISION, "DOC-HAZOP-BAD", "A")
        .title("Hazop decision with unverified content").controlledDocument("stid://DOC-HAZOP-BAD/A", "not-a-hash")
        .issuer("Hazop team", "Hazop chair", "2026-07-18").addScopeReference(scope)
        .decision(EngineeringExternalEvidenceRecord.Status.ACCEPTED, "Process safety authority", "Technical authority",
            "2026-07-18", "workflow://hazop/accepted")
        .build());
    EngineeringExternalEvidenceAssessment.Result incomplete = EngineeringExternalEvidenceAssessment.assess(invalidHash);
    assertFalse(incomplete.isTypePassed(EngineeringExternalEvidenceRecord.Type.HAZOP_DECISION));
    assertTrue(incomplete.getFindings().toString().contains("INCOMPLETE_ACCEPTED_RECORD"));
  }
}
