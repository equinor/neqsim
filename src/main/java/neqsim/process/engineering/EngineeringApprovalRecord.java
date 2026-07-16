package neqsim.process.engineering;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Accountable, evidence-backed approval decision for one canonical engineering graph object. */
public final class EngineeringApprovalRecord implements Serializable {
  private static final long serialVersionUID = 1000L;

  public enum Status {
    REVIEW_REQUIRED, APPROVED, REJECTED
  }

  private final String id;
  private final String subjectNodeId;
  private final String discipline;
  private final Status status;
  private final String reviewer;
  private final String reviewReference;
  private final String effectiveDate;
  private String supersedesRecordId = "";

  public EngineeringApprovalRecord(String id, String subjectNodeId, String discipline, Status status, String reviewer,
      String reviewReference, String effectiveDate) {
    this.id = requireText(id, "id");
    this.subjectNodeId = requireText(subjectNodeId, "subjectNodeId");
    this.discipline = requireText(discipline, "discipline");
    if (status == null) {
      throw new IllegalArgumentException("status must not be null");
    }
    this.status = status;
    this.reviewer = textOrEmpty(reviewer);
    this.reviewReference = textOrEmpty(reviewReference);
    this.effectiveDate = textOrEmpty(effectiveDate);
    if (status != Status.REVIEW_REQUIRED
        && (this.reviewer.isEmpty() || this.reviewReference.isEmpty() || this.effectiveDate.isEmpty())) {
      throw new IllegalArgumentException("Approved or rejected decisions require reviewer, reference and date");
    }
  }

  public EngineeringApprovalRecord supersedes(String recordId) {
    supersedesRecordId = requireText(recordId, "recordId");
    if (id.equals(supersedesRecordId)) {
      throw new IllegalArgumentException("Approval record must not supersede itself");
    }
    return this;
  }

  public String getId() {
    return id;
  }

  public String getSubjectNodeId() {
    return subjectNodeId;
  }

  public String getDiscipline() {
    return discipline;
  }

  public Status getStatus() {
    return status;
  }

  public String getReviewer() {
    return reviewer;
  }

  public String getReviewReference() {
    return reviewReference;
  }

  public String getEffectiveDate() {
    return effectiveDate;
  }

  public String getSupersedesRecordId() {
    return supersedesRecordId;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("subjectNodeId", subjectNodeId);
    result.put("discipline", discipline);
    result.put("status", status.name());
    result.put("reviewer", reviewer);
    result.put("reviewReference", reviewReference);
    result.put("effectiveDate", effectiveDate);
    result.put("supersedesRecordId", supersedesRecordId);
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
