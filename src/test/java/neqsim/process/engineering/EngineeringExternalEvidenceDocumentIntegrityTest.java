package neqsim.process.engineering;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import neqsim.process.engineering.production.EngineeringExternalEvidenceDocumentIntegrity;
import neqsim.process.engineering.production.EngineeringExternalEvidenceRecord;
import neqsim.process.engineering.production.EngineeringExternalEvidenceRegister;
import neqsim.process.engineering.production.EngineeringProductionReadinessAssessment;
import neqsim.process.engineering.production.EngineeringProductionReadinessBasis;
import neqsim.process.processmodel.ProcessSystem;
import org.junit.jupiter.api.Test;

/** Verifies that accepted external receipts are bound to the actual supplied document content. */
class EngineeringExternalEvidenceDocumentIntegrityTest {

  @Test
  void verifiesEveryAcceptedDocumentWithoutGrantingApproval() throws Exception {
    String scope = "project:document-integrity";
    String content = "controlled external engineering evidence revision A";
    String contentHash = sha256(content);
    EngineeringExternalEvidenceRegister register = EngineeringExternalEvidenceRegister.productionMinimum(scope);
    EngineeringExternalEvidenceDocumentIntegrity integrity = new EngineeringExternalEvidenceDocumentIntegrity();

    for (EngineeringExternalEvidenceRecord.Type type : EngineeringExternalEvidenceRecord.Type.values()) {
      String reference = "memory://" + type.name() + "/A";
      register.addRecord(accepted(type, scope, reference, contentHash));
      integrity.addDocument(reference, content);
    }

    EngineeringExternalEvidenceDocumentIntegrity.Result result = integrity.assess(register);
    assertTrue(result.isPassed());
    assertTrue(result.getVerifiedRecordIds().size() == EngineeringExternalEvidenceRecord.Type.values().length);
    assertTrue(Boolean.FALSE.equals(result.toMap().get("approvalGrantedBySimulator")));

    EngineeringProject project = new EngineeringProject("document-integrity", new ProcessSystem(),
        new EngineeringDesignBasis());
    EngineeringProductionReadinessBasis basis = new EngineeringProductionReadinessBasis()
        .externalEvidenceRegister(register).externalEvidenceDocumentIntegrity(integrity);
    EngineeringProductionReadinessAssessment.Result readiness = EngineeringProductionReadinessAssessment.assess(project,
        basis);
    assertFalse(readiness.getFailedGates().contains("EXTERNAL_EVIDENCE_DOCUMENT_INTEGRITY"));
    assertTrue(Boolean.FALSE.equals(readiness.toMap().get("fitnessForConstruction")));
  }

  @Test
  void failsClosedForMissingOrChangedDocumentContent() throws Exception {
    String scope = "project:document-integrity-negative";
    String reference = "memory://VENDOR/A";
    EngineeringExternalEvidenceRegister register = EngineeringExternalEvidenceRegister.productionMinimum(scope)
        .addRecord(accepted(EngineeringExternalEvidenceRecord.Type.VENDOR_GUARANTEE, scope, reference,
            sha256("controlled vendor guarantee")));

    EngineeringExternalEvidenceDocumentIntegrity.Result missing = new EngineeringExternalEvidenceDocumentIntegrity()
        .assess(register);
    assertFalse(missing.isPassed());
    assertTrue(missing.getFindings().toString().contains("DOCUMENT_NOT_SUPPLIED"));

    EngineeringExternalEvidenceDocumentIntegrity changed = new EngineeringExternalEvidenceDocumentIntegrity()
        .addDocument(reference, "changed vendor guarantee");
    EngineeringExternalEvidenceDocumentIntegrity.Result mismatch = changed.assess(register);
    assertFalse(mismatch.isPassed());
    assertTrue(mismatch.getFindings().toString().contains("DOCUMENT_HASH_MISMATCH"));
  }

  @Test
  void rejectsConflictingContentForTheSameControlledReference() {
    EngineeringExternalEvidenceDocumentIntegrity integrity = new EngineeringExternalEvidenceDocumentIntegrity()
        .addDocument("memory://DOC/A", "first");
    assertThrows(IllegalArgumentException.class, () -> integrity.addDocument("memory://DOC/A", "second"));
  }

  private static EngineeringExternalEvidenceRecord accepted(EngineeringExternalEvidenceRecord.Type type, String scope,
      String reference, String contentHash) {
    EngineeringExternalEvidenceRecord.Builder builder = EngineeringExternalEvidenceRecord
        .builder("EV-" + type.name() + "-A", type, "DOC-" + type.name(), "A")
        .title(type.name() + " controlled evidence").controlledDocument(reference, contentHash)
        .issuer("Accountable external organization", "Authorized issuer", "2026-07-18").addScopeReference(scope)
        .decision(EngineeringExternalEvidenceRecord.Status.ACCEPTED, "Authorized approver", "Technical authority",
            "2026-07-18", "workflow://" + type.name() + "/accepted");
    if (type == EngineeringExternalEvidenceRecord.Type.INDEPENDENT_VALIDATION) {
      builder.independenceStatement("Validator is organizationally independent from the producing team");
    }
    if (type == EngineeringExternalEvidenceRecord.Type.CONSTRUCTION_AUTHORITY) {
      builder.authorityJurisdiction("Norway / project construction authority");
    }
    return builder.build();
  }

  private static String sha256(String value) throws Exception {
    byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    StringBuilder result = new StringBuilder();
    for (byte item : bytes) {
      result.append(String.format("%02x", Integer.valueOf(item & 0xff)));
    }
    return result.toString();
  }
}
