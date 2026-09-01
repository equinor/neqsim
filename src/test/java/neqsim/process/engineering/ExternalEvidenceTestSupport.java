package neqsim.process.engineering;

import neqsim.process.engineering.production.EngineeringExternalEvidenceRecord;
import neqsim.process.engineering.production.EngineeringExternalEvidenceRegister;

/** Shared controlled external-evidence fixtures. */
final class ExternalEvidenceTestSupport {
  static final String SHA256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  private ExternalEvidenceTestSupport() {
  }

  static EngineeringExternalEvidenceRegister completeRegister(String scope) {
    EngineeringExternalEvidenceRegister result = EngineeringExternalEvidenceRegister.productionMinimum(scope);
    for (EngineeringExternalEvidenceRecord.Type type : EngineeringExternalEvidenceRecord.Type.values()) {
      result.addRecord(accepted(type, scope, "EV-" + type.name() + "-A"));
    }
    return result;
  }

  static EngineeringExternalEvidenceRecord accepted(EngineeringExternalEvidenceRecord.Type type, String scope,
      String id) {
    EngineeringExternalEvidenceRecord.Builder builder = EngineeringExternalEvidenceRecord
        .builder(id, type, "DOC-" + id, "A").title(type.name() + " controlled evidence")
        .controlledDocument("stid://DOC-" + id + "/A", SHA256)
        .issuer("Accountable external organization", "Authorized issuer", "2026-07-18").addScopeReference(scope)
        .decision(EngineeringExternalEvidenceRecord.Status.ACCEPTED, "Authorized approver", "Technical authority",
            "2026-07-18", "workflow://" + id + "/accepted");
    if (type == EngineeringExternalEvidenceRecord.Type.INDEPENDENT_VALIDATION) {
      builder.independenceStatement("Validator is organizationally independent from the producing team");
    }
    if (type == EngineeringExternalEvidenceRecord.Type.CONSTRUCTION_AUTHORITY) {
      builder.authorityJurisdiction("Norway / project construction authority");
    }
    return builder.build();
  }
}
