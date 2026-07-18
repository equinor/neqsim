package neqsim.process.engineering.production;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable receipt for a controlled decision or guarantee issued outside the simulator. */
public final class EngineeringExternalEvidenceRecord implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** External evidence classes that must be closed by accountable organizations. */
  public enum Type {
    ACCOUNTABLE_ENGINEERING_APPROVAL, VENDOR_GUARANTEE, HAZOP_DECISION, LOPA_DECISION, SRS_APPROVAL,
    INDEPENDENT_VALIDATION, CONSTRUCTION_AUTHORITY
  }

  /** Current controlled decision state for one evidence revision. */
  public enum Status {
    DRAFT, ACCEPTED, REJECTED, SUPERSEDED
  }

  private final String id;
  private final Type type;
  private final String documentId;
  private final String revision;
  private final String title;
  private final String documentReference;
  private final String sha256;
  private final String issuingOrganization;
  private final String issuedBy;
  private final String issuedDate;
  private final List<String> scopeReferences;
  private final Status status;
  private final String decisionBy;
  private final String decisionRole;
  private final String decisionDate;
  private final String decisionReference;
  private final String independenceStatement;
  private final String authorityJurisdiction;
  private final String supersedesRecordId;

  private EngineeringExternalEvidenceRecord(Builder builder) {
    id = builder.id;
    type = builder.type;
    documentId = builder.documentId;
    revision = builder.revision;
    title = builder.title;
    documentReference = builder.documentReference;
    sha256 = builder.sha256;
    issuingOrganization = builder.issuingOrganization;
    issuedBy = builder.issuedBy;
    issuedDate = builder.issuedDate;
    scopeReferences = Collections.unmodifiableList(new ArrayList<String>(builder.scopeReferences));
    status = builder.status;
    decisionBy = builder.decisionBy;
    decisionRole = builder.decisionRole;
    decisionDate = builder.decisionDate;
    decisionReference = builder.decisionReference;
    independenceStatement = builder.independenceStatement;
    authorityJurisdiction = builder.authorityJurisdiction;
    supersedesRecordId = builder.supersedesRecordId;
  }

  /** Creates a controlled evidence-record builder. */
  public static Builder builder(String id, Type type, String documentId, String revision) {
    return new Builder(id, type, documentId, revision);
  }

  /** Returns fields that prevent this revision from being accepted as external evidence. */
  public List<String> getMissingFields() {
    List<String> missing = new ArrayList<String>();
    missing(missing, title, "title");
    missing(missing, documentReference, "documentReference");
    if (!sha256.matches("[0-9a-fA-F]{64}")) {
      missing.add("sha256");
    }
    missing(missing, issuingOrganization, "issuingOrganization");
    missing(missing, issuedBy, "issuedBy");
    date(missing, issuedDate, "issuedDate");
    if (scopeReferences.isEmpty()) {
      missing.add("scopeReference");
    }
    if (status == Status.ACCEPTED || status == Status.REJECTED) {
      missing(missing, decisionBy, "decisionBy");
      missing(missing, decisionRole, "decisionRole");
      date(missing, decisionDate, "decisionDate");
      missing(missing, decisionReference, "decisionReference");
    }
    if (type == Type.INDEPENDENT_VALIDATION) {
      missing(missing, independenceStatement, "independenceStatement");
    }
    if (type == Type.CONSTRUCTION_AUTHORITY) {
      missing(missing, authorityJurisdiction, "authorityJurisdiction");
    }
    return missing;
  }

  /** Returns true only for a complete, explicitly accepted external decision. */
  public boolean isAcceptedAndComplete() {
    return status == Status.ACCEPTED && getMissingFields().isEmpty();
  }

  public String getId() {
    return id;
  }

  public Type getType() {
    return type;
  }

  public String getDocumentId() {
    return documentId;
  }

  public String getRevision() {
    return revision;
  }

  public String getTitle() {
    return title;
  }

  public String getDocumentReference() {
    return documentReference;
  }

  public String getSha256() {
    return sha256;
  }

  public String getIssuingOrganization() {
    return issuingOrganization;
  }

  public String getIssuedBy() {
    return issuedBy;
  }

  public String getIssuedDate() {
    return issuedDate;
  }

  public List<String> getScopeReferences() {
    return scopeReferences;
  }

  public Status getStatus() {
    return status;
  }

  public String getDecisionRole() {
    return decisionRole;
  }

  public String getDecisionBy() {
    return decisionBy;
  }

  public String getDecisionDate() {
    return decisionDate;
  }

  public String getDecisionReference() {
    return decisionReference;
  }

  public String getIndependenceStatement() {
    return independenceStatement;
  }

  public String getAuthorityJurisdiction() {
    return authorityJurisdiction;
  }

  public String getSupersedesRecordId() {
    return supersedesRecordId;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("type", type.name());
    result.put("documentId", documentId);
    result.put("revision", revision);
    result.put("title", title);
    result.put("documentReference", documentReference);
    result.put("sha256", sha256);
    result.put("issuingOrganization", issuingOrganization);
    result.put("issuedBy", issuedBy);
    result.put("issuedDate", issuedDate);
    result.put("scopeReferences", new ArrayList<String>(scopeReferences));
    result.put("status", status.name());
    result.put("decisionBy", decisionBy);
    result.put("decisionRole", decisionRole);
    result.put("decisionDate", decisionDate);
    result.put("decisionReference", decisionReference);
    result.put("independenceStatement", independenceStatement);
    result.put("authorityJurisdiction", authorityJurisdiction);
    result.put("supersedesRecordId", supersedesRecordId);
    result.put("missingFields", getMissingFields());
    result.put("acceptedAndComplete", Boolean.valueOf(isAcceptedAndComplete()));
    return result;
  }

  private static void missing(List<String> missing, String value, String field) {
    if (value.isEmpty()) {
      missing.add(field);
    }
  }

  private static void date(List<String> missing, String value, String field) {
    if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}(T.*)?")) {
      missing.add(field);
    }
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static String text(String value) {
    return value == null ? "" : value.trim();
  }

  /** Fluent builder that keeps draft and rejected evidence serializable but never accepted implicitly. */
  public static final class Builder {
    private final String id;
    private final Type type;
    private final String documentId;
    private final String revision;
    private String title = "";
    private String documentReference = "";
    private String sha256 = "";
    private String issuingOrganization = "";
    private String issuedBy = "";
    private String issuedDate = "";
    private final List<String> scopeReferences = new ArrayList<String>();
    private Status status = Status.DRAFT;
    private String decisionBy = "";
    private String decisionRole = "";
    private String decisionDate = "";
    private String decisionReference = "";
    private String independenceStatement = "";
    private String authorityJurisdiction = "";
    private String supersedesRecordId = "";

    private Builder(String id, Type type, String documentId, String revision) {
      this.id = requireText(id, "id");
      if (type == null) {
        throw new IllegalArgumentException("type must not be null");
      }
      this.type = type;
      this.documentId = requireText(documentId, "documentId");
      this.revision = requireText(revision, "revision");
    }

    public Builder title(String value) {
      title = requireText(value, "title");
      return this;
    }

    public Builder controlledDocument(String reference, String contentSha256) {
      documentReference = requireText(reference, "documentReference");
      sha256 = requireText(contentSha256, "sha256");
      return this;
    }

    public Builder issuer(String organization, String person, String date) {
      issuingOrganization = requireText(organization, "issuingOrganization");
      issuedBy = requireText(person, "issuedBy");
      issuedDate = requireText(date, "issuedDate");
      return this;
    }

    public Builder addScopeReference(String value) {
      String scope = requireText(value, "scopeReference");
      if (!scopeReferences.contains(scope)) {
        scopeReferences.add(scope);
      }
      return this;
    }

    public Builder decision(Status value, String person, String role, String date, String reference) {
      if (value == null) {
        throw new IllegalArgumentException("status must not be null");
      }
      status = value;
      decisionBy = text(person);
      decisionRole = text(role);
      decisionDate = text(date);
      decisionReference = text(reference);
      return this;
    }

    public Builder independenceStatement(String value) {
      independenceStatement = requireText(value, "independenceStatement");
      return this;
    }

    public Builder authorityJurisdiction(String value) {
      authorityJurisdiction = requireText(value, "authorityJurisdiction");
      return this;
    }

    public Builder supersedes(String recordId) {
      supersedesRecordId = requireText(recordId, "supersedesRecordId");
      if (id.equals(supersedesRecordId)) {
        throw new IllegalArgumentException("Evidence record must not supersede itself");
      }
      return this;
    }

    public EngineeringExternalEvidenceRecord build() {
      return new EngineeringExternalEvidenceRecord(this);
    }
  }
}
