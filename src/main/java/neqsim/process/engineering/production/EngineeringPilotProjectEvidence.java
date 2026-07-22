package neqsim.process.engineering.production;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Independently reviewed comparison with a representative engineering pilot project. */
public final class EngineeringPilotProjectEvidence implements Serializable {
  private static final long serialVersionUID = 1000L;

  public enum Scope {
    SEPARATION_AND_COMPRESSION, PUMPING_AND_HEAT_EXCHANGE, RELIEF_BLOWDOWN_AND_FLARE
  }

  private final String projectId;
  private final Scope scope;
  private final String referencePackage;
  private final int comparisonCount;
  private final int unresolvedMaterialDiscrepancyCount;
  private final String independentReviewer;
  private final String acceptanceRecord;

  public EngineeringPilotProjectEvidence(String projectId, Scope scope, String referencePackage, int comparisonCount,
      int unresolvedMaterialDiscrepancyCount, String independentReviewer, String acceptanceRecord) {
    this.projectId = text(projectId, "projectId");
    if (scope == null) {
      throw new IllegalArgumentException("scope must not be null");
    }
    this.scope = scope;
    this.referencePackage = text(referencePackage, "referencePackage");
    if (comparisonCount <= 0 || unresolvedMaterialDiscrepancyCount < 0) {
      throw new IllegalArgumentException("comparisonCount must be positive and discrepancy count non-negative");
    }
    this.comparisonCount = comparisonCount;
    this.unresolvedMaterialDiscrepancyCount = unresolvedMaterialDiscrepancyCount;
    this.independentReviewer = text(independentReviewer, "independentReviewer");
    this.acceptanceRecord = text(acceptanceRecord, "acceptanceRecord");
  }

  public Scope getScope() {
    return scope;
  }

  public boolean isAccepted() {
    return unresolvedMaterialDiscrepancyCount == 0;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("projectId", projectId);
    result.put("scope", scope.name());
    result.put("referencePackage", referencePackage);
    result.put("comparisonCount", Integer.valueOf(comparisonCount));
    result.put("unresolvedMaterialDiscrepancyCount", Integer.valueOf(unresolvedMaterialDiscrepancyCount));
    result.put("independentReviewer", independentReviewer);
    result.put("acceptanceRecord", acceptanceRecord);
    result.put("accepted", Boolean.valueOf(isAccepted()));
    return result;
  }

  private static String text(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
