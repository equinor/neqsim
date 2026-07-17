package neqsim.process.engineering.production;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Controlled maturity and applicability record for one engineering calculation method version. */
public final class EngineeringMethodQualification implements Serializable {
  private static final long serialVersionUID = 1000L;

  public enum Level {
    SCREENING, BENCHMARKED, INDEPENDENTLY_VERIFIED, PROJECT_QUALIFIED
  }

  private final String methodId;
  private final String version;
  private final Level level;
  private final List<String> standardReferences = new ArrayList<String>();
  private final List<String> applicabilityLimits = new ArrayList<String>();
  private final List<String> evidenceReferences = new ArrayList<String>();
  private String approvalRecord = "";

  public EngineeringMethodQualification(String methodId, String version, Level level) {
    this.methodId = text(methodId, "methodId");
    this.version = text(version, "version");
    if (level == null) {
      throw new IllegalArgumentException("level must not be null");
    }
    this.level = level;
  }

  public EngineeringMethodQualification addStandardReference(String value) {
    addUnique(standardReferences, value, "standardReference");
    return this;
  }

  public EngineeringMethodQualification addApplicabilityLimit(String value) {
    addUnique(applicabilityLimits, value, "applicabilityLimit");
    return this;
  }

  public EngineeringMethodQualification addEvidenceReference(String value) {
    addUnique(evidenceReferences, value, "evidenceReference");
    return this;
  }

  public EngineeringMethodQualification approve(String value) {
    approvalRecord = text(value, "approvalRecord");
    return this;
  }

  public String getMethodId() {
    return methodId;
  }

  public String getMethodKey() {
    return methodId + "@" + version;
  }

  public boolean isProjectQualified() {
    return level == Level.PROJECT_QUALIFIED && !standardReferences.isEmpty() && !applicabilityLimits.isEmpty()
        && !evidenceReferences.isEmpty() && !approvalRecord.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("methodId", methodId);
    result.put("version", version);
    result.put("level", level.name());
    result.put("standardReferences", new ArrayList<String>(standardReferences));
    result.put("applicabilityLimits", new ArrayList<String>(applicabilityLimits));
    result.put("evidenceReferences", new ArrayList<String>(evidenceReferences));
    result.put("approvalRecord", approvalRecord);
    result.put("projectQualified", Boolean.valueOf(isProjectQualified()));
    return result;
  }

  private static void addUnique(List<String> target, String value, String field) {
    String item = text(value, field);
    if (!target.contains(item)) {
      target.add(item);
    }
  }

  private static String text(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
