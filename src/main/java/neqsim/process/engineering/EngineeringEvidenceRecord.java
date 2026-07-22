package neqsim.process.engineering;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Controlled engineering-document or vendor-data evidence linked to generated design objects. */
public final class EngineeringEvidenceRecord implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String documentId;
  private final String documentType;
  private final String revision;
  private final List<String> equipmentTags = new ArrayList<String>();
  private final List<String> requirementIds = new ArrayList<String>();
  private String title = "";
  private String sourceOrganization = "";
  private String checksum = "";
  private String reviewRecord = "";
  private EngineeringApprovalStatus approvalStatus = EngineeringApprovalStatus.REVIEW_REQUIRED;

  /**
   * Creates a revision-controlled evidence record.
   *
   * @param documentId controlled document identifier
   * @param documentType document category, for example HAZOP, LOPA, SRS or vendor data
   * @param revision controlled revision
   */
  public EngineeringEvidenceRecord(String documentId, String documentType, String revision) {
    this.documentId = requireText(documentId, "documentId");
    this.documentType = requireText(documentType, "documentType");
    this.revision = requireText(revision, "revision");
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }

  public EngineeringEvidenceRecord setTitle(String value) {
    title = requireText(value, "title");
    return this;
  }

  public EngineeringEvidenceRecord setSourceOrganization(String value) {
    sourceOrganization = requireText(value, "sourceOrganization");
    return this;
  }

  public EngineeringEvidenceRecord setChecksum(String value) {
    checksum = requireText(value, "checksum");
    return this;
  }

  public EngineeringEvidenceRecord linkEquipment(String equipmentTag) {
    String value = requireText(equipmentTag, "equipmentTag");
    if (!equipmentTags.contains(value)) {
      equipmentTags.add(value);
    }
    return this;
  }

  public EngineeringEvidenceRecord linkRequirement(String requirementId) {
    String value = requireText(requirementId, "requirementId");
    if (!requirementIds.contains(value)) {
      requirementIds.add(value);
    }
    return this;
  }

  public EngineeringEvidenceRecord approve(String value) {
    reviewRecord = requireText(value, "reviewRecord");
    approvalStatus = EngineeringApprovalStatus.APPROVED;
    return this;
  }

  public List<String> getMissingFields() {
    List<String> missing = new ArrayList<String>();
    if (title.isEmpty()) {
      missing.add("title");
    }
    if (sourceOrganization.isEmpty()) {
      missing.add("sourceOrganization");
    }
    if (equipmentTags.isEmpty() && requirementIds.isEmpty()) {
      missing.add("engineeringObjectLink");
    }
    return missing;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("documentId", documentId);
    map.put("documentType", documentType);
    map.put("revision", revision);
    map.put("title", title);
    map.put("sourceOrganization", sourceOrganization);
    map.put("checksum", checksum);
    map.put("equipmentTags", new ArrayList<String>(equipmentTags));
    map.put("requirementIds", new ArrayList<String>(requirementIds));
    map.put("approvalStatus", approvalStatus.name());
    map.put("reviewRecord", reviewRecord);
    map.put("missingFields", getMissingFields());
    return map;
  }

  public String getDocumentId() {
    return documentId;
  }

  public String getDocumentType() {
    return documentType;
  }

  public String getRevision() {
    return revision;
  }

  public List<String> getEquipmentTags() {
    return Collections.unmodifiableList(equipmentTags);
  }

  public List<String> getRequirementIds() {
    return Collections.unmodifiableList(requirementIds);
  }

  public EngineeringApprovalStatus getApprovalStatus() {
    return approvalStatus;
  }
}
