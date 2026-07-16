package neqsim.process.engineering.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Traceable origin, method, evidence and review state for an engineering model element. */
public final class EngineeringProvenance implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String sourceType;
  private final String sourceReference;
  private String method = "";
  private String designCaseId = "";
  private String approvalStatus = "REVIEW_REQUIRED";
  private final List<String> evidenceReferences = new ArrayList<String>();

  public EngineeringProvenance(String sourceType, String sourceReference) {
    this.sourceType = requireText(sourceType, "sourceType");
    this.sourceReference = requireText(sourceReference, "sourceReference");
  }

  public EngineeringProvenance setMethod(String value) {
    method = textOrEmpty(value);
    return this;
  }

  public EngineeringProvenance setDesignCaseId(String value) {
    designCaseId = textOrEmpty(value);
    return this;
  }

  public EngineeringProvenance setApprovalStatus(String value) {
    approvalStatus = requireText(value, "approvalStatus");
    return this;
  }

  public EngineeringProvenance addEvidenceReference(String value) {
    String normalized = textOrEmpty(value);
    if (!normalized.isEmpty() && !evidenceReferences.contains(normalized)) {
      evidenceReferences.add(normalized);
    }
    return this;
  }

  public String getSourceType() {
    return sourceType;
  }

  public String getSourceReference() {
    return sourceReference;
  }

  public String getMethod() {
    return method;
  }

  public String getDesignCaseId() {
    return designCaseId;
  }

  public String getApprovalStatus() {
    return approvalStatus;
  }

  public List<String> getEvidenceReferences() {
    return Collections.unmodifiableList(evidenceReferences);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("sourceType", sourceType);
    result.put("sourceReference", sourceReference);
    result.put("method", method);
    result.put("designCaseId", designCaseId);
    result.put("approvalStatus", approvalStatus);
    result.put("evidenceReferences", new ArrayList<String>(evidenceReferences));
    return result;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }

  private static String textOrEmpty(String value) {
    return value == null ? "" : value.trim();
  }
}
