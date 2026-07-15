package neqsim.process.engineering;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A traceable control, instrumentation, or safeguarding requirement. */
public final class EngineeringRequirement implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Requirement discipline. */
  public enum Type {
    CONTROL, INSTRUMENT, ALARM, TRIP, RELIEF, MECHANICAL_PROTECTION, FIRE_AND_GAS, ENGINEERING_ASSESSMENT
  }

  private final String id;
  private final String equipmentTag;
  private final Type type;
  private final String title;
  private final String rationale;
  private final List<String> standardReferences = new ArrayList<String>();
  private EngineeringOrigin origin = EngineeringOrigin.RULE_INFERRED;
  private EngineeringApprovalStatus approvalStatus = EngineeringApprovalStatus.REVIEW_REQUIRED;
  private String silTarget = "SIL_UNASSIGNED";
  private String reviewRecord = "";

  /**
   * Creates a proposed engineering requirement.
   *
   * @param id stable requirement identifier
   * @param equipmentTag related equipment tag
   * @param type requirement discipline
   * @param title concise requirement title
   * @param rationale engineering rationale
   */
  public EngineeringRequirement(String id, String equipmentTag, Type type, String title, String rationale) {
    this.id = requireText(id, "id");
    this.equipmentTag = requireText(equipmentTag, "equipmentTag");
    this.type = Objects.requireNonNull(type, "type");
    this.title = requireText(title, "title");
    this.rationale = requireText(rationale, "rationale");
  }

  private static String requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }

  /**
   * Adds a standard and edition reference.
   *
   * @param reference standard and edition reference
   * @return this requirement
   */
  public EngineeringRequirement addStandardReference(String reference) {
    if (reference != null && !reference.trim().isEmpty() && !standardReferences.contains(reference)) {
      standardReferences.add(reference);
    }
    return this;
  }

  /**
   * Records an externally determined SIL target. This method does not approve the requirement; the target must be
   * justified by the project's hazard and risk assessment and approved separately.
   *
   * @param silTarget SIL target or NOT_SIL_RATED
   * @param riskAssessmentReference reference to the LOPA/QRA/SIL assessment
   * @return this requirement
   */
  public EngineeringRequirement setSilTarget(String silTarget, String riskAssessmentReference) {
    this.silTarget = requireText(silTarget, "silTarget");
    this.reviewRecord = requireText(riskAssessmentReference, "riskAssessmentReference");
    this.origin = EngineeringOrigin.USER_SPECIFIED;
    this.approvalStatus = EngineeringApprovalStatus.REVIEW_REQUIRED;
    return this;
  }

  /**
   * Approves the requirement after discipline review.
   *
   * @param approvalRecord accountable engineer and document/reference
   * @return this requirement
   */
  public EngineeringRequirement approve(String approvalRecord) {
    this.reviewRecord = requireText(approvalRecord, "approvalRecord");
    this.origin = EngineeringOrigin.ENGINEER_APPROVED;
    this.approvalStatus = EngineeringApprovalStatus.APPROVED;
    return this;
  }

  /** @return stable requirement identifier */
  public String getId() {
    return id;
  }

  /** @return related equipment tag */
  public String getEquipmentTag() {
    return equipmentTag;
  }

  /** @return requirement discipline */
  public Type getType() {
    return type;
  }

  /** @return concise requirement title */
  public String getTitle() {
    return title;
  }

  /** @return engineering rationale */
  public String getRationale() {
    return rationale;
  }

  /** @return immutable standard-reference list */
  public List<String> getStandardReferences() {
    return Collections.unmodifiableList(standardReferences);
  }

  /** @return information origin */
  public EngineeringOrigin getOrigin() {
    return origin;
  }

  /** @return approval lifecycle state */
  public EngineeringApprovalStatus getApprovalStatus() {
    return approvalStatus;
  }

  /** @return assigned SIL target or SIL_UNASSIGNED */
  public String getSilTarget() {
    return silTarget;
  }

  /** @return approval or risk-assessment reference */
  public String getReviewRecord() {
    return reviewRecord;
  }
}
