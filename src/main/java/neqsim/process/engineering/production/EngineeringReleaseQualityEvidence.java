package neqsim.process.engineering.production;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Release-level software quality evidence required for supported preliminary-engineering use. */
public final class EngineeringReleaseQualityEvidence implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String releaseId;
  private boolean fullCiPassed;
  private boolean supportedJavaMatrixPassed;
  private boolean deterministicConvergencePassed;
  private boolean performanceAcceptancePassed;
  private boolean apiCompatibilityPassed;
  private boolean serializationMigrationPassed;
  private boolean securityReviewPassed;
  private String evidenceReference = "";
  private String accountableReviewer = "";

  public EngineeringReleaseQualityEvidence(String releaseId) {
    if (releaseId == null || releaseId.trim().isEmpty()) {
      throw new IllegalArgumentException("releaseId must not be blank");
    }
    this.releaseId = releaseId.trim();
  }

  public EngineeringReleaseQualityEvidence fullCiPassed(boolean value) {
    fullCiPassed = value;
    return this;
  }

  public EngineeringReleaseQualityEvidence supportedJavaMatrixPassed(boolean value) {
    supportedJavaMatrixPassed = value;
    return this;
  }

  public EngineeringReleaseQualityEvidence deterministicConvergencePassed(boolean value) {
    deterministicConvergencePassed = value;
    return this;
  }

  public EngineeringReleaseQualityEvidence performanceAcceptancePassed(boolean value) {
    performanceAcceptancePassed = value;
    return this;
  }

  public EngineeringReleaseQualityEvidence apiCompatibilityPassed(boolean value) {
    apiCompatibilityPassed = value;
    return this;
  }

  public EngineeringReleaseQualityEvidence serializationMigrationPassed(boolean value) {
    serializationMigrationPassed = value;
    return this;
  }

  public EngineeringReleaseQualityEvidence securityReviewPassed(boolean value) {
    securityReviewPassed = value;
    return this;
  }

  public EngineeringReleaseQualityEvidence evidenceReference(String value) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("evidenceReference must not be blank");
    }
    evidenceReference = value.trim();
    return this;
  }

  public EngineeringReleaseQualityEvidence accountableReviewer(String value) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("accountableReviewer must not be blank");
    }
    accountableReviewer = value.trim();
    return this;
  }

  public boolean isPassed() {
    return fullCiPassed && supportedJavaMatrixPassed && deterministicConvergencePassed && performanceAcceptancePassed
        && apiCompatibilityPassed && serializationMigrationPassed && securityReviewPassed
        && !evidenceReference.isEmpty() && !accountableReviewer.isEmpty();
  }

  public List<String> getMissingGates() {
    List<String> result = new ArrayList<String>();
    addIfFalse(result, fullCiPassed, "FULL_CI");
    addIfFalse(result, supportedJavaMatrixPassed, "SUPPORTED_JAVA_MATRIX");
    addIfFalse(result, deterministicConvergencePassed, "DETERMINISTIC_CONVERGENCE");
    addIfFalse(result, performanceAcceptancePassed, "PERFORMANCE_ACCEPTANCE");
    addIfFalse(result, apiCompatibilityPassed, "API_COMPATIBILITY");
    addIfFalse(result, serializationMigrationPassed, "SERIALIZATION_MIGRATION");
    addIfFalse(result, securityReviewPassed, "SECURITY_REVIEW");
    addIfFalse(result, !evidenceReference.isEmpty(), "EVIDENCE_REFERENCE");
    addIfFalse(result, !accountableReviewer.isEmpty(), "ACCOUNTABLE_REVIEWER");
    return result;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("releaseId", releaseId);
    result.put("fullCiPassed", Boolean.valueOf(fullCiPassed));
    result.put("supportedJavaMatrixPassed", Boolean.valueOf(supportedJavaMatrixPassed));
    result.put("deterministicConvergencePassed", Boolean.valueOf(deterministicConvergencePassed));
    result.put("performanceAcceptancePassed", Boolean.valueOf(performanceAcceptancePassed));
    result.put("apiCompatibilityPassed", Boolean.valueOf(apiCompatibilityPassed));
    result.put("serializationMigrationPassed", Boolean.valueOf(serializationMigrationPassed));
    result.put("securityReviewPassed", Boolean.valueOf(securityReviewPassed));
    result.put("evidenceReference", evidenceReference);
    result.put("accountableReviewer", accountableReviewer);
    result.put("missingGates", getMissingGates());
    result.put("passed", Boolean.valueOf(isPassed()));
    return result;
  }

  private static void addIfFalse(List<String> target, boolean condition, String value) {
    if (!condition) {
      target.add(value);
    }
  }
}
