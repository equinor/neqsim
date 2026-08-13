package neqsim.process.engineering.model;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable controlled-document view of one canonical {@link EngineeringGraph}.
 *
 * <p>
 * The document set owns drawing and sheet identity, revision/status metadata, and paired off-page references without
 * changing the semantic plant graph. It is an engineering proposal unless accountable approval evidence is supplied
 * explicitly.
 * </p>
 */
public final class EngineeringDiagramDocumentSet implements Serializable {
  private static final long serialVersionUID = 1000L;
  public static final String SCHEMA_VERSION = "neqsim_engineering_diagram_document_set.v1";

  /** Drawing content profiles. */
  public enum ContentProfile {
    /** Block flow diagram. */
    BFD,
    /** Process flow diagram. */
    PFD,
    /** Piping and instrumentation diagram proposal. */
    PID
  }

  /** Controlled-document lifecycle status. */
  public enum DocumentStatus {
    /** Generated working proposal. */
    WORKING,
    /** Issued for accountable review. */
    FOR_REVIEW,
    /** Review evidence exists but approval has not been recorded. */
    REVIEWED,
    /** Accountable approval evidence has been recorded. */
    APPROVED
  }

  /** Purpose for which a drawing set is issued. */
  public enum IssuePurpose {
    /** Simulation-driven engineering proposal. */
    ENGINEERING_PROPOSAL,
    /** Issued for information. */
    INFORMATION,
    /** Issued for design after accountable review. */
    DESIGN,
    /** Issued for construction after accountable approval. */
    CONSTRUCTION
  }

  /** Structured validation severity. */
  public enum Severity {
    /** Informational mapping evidence. */
    INFO,
    /** Recoverable limitation requiring review. */
    WARNING,
    /** Broken document-set semantics. */
    ERROR
  }

  /** End of a paired off-page reference. */
  public enum ConnectorRole {
    /** Connector on the semantic connection source sheet. */
    SOURCE,
    /** Connector on the semantic connection target sheet. */
    TARGET
  }

  /** Immutable document-set validation or adaptation diagnostic. */
  public static final class Diagnostic implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Severity severity;
    private final String code;
    private final String message;
    private final String subjectId;

    public Diagnostic(Severity severity, String code, String message, String subjectId) {
      if (severity == null) {
        throw new IllegalArgumentException("severity must not be null");
      }
      this.severity = severity;
      this.code = requireText(code, "code");
      this.message = requireText(message, "message");
      this.subjectId = subjectId == null ? "" : subjectId.trim();
    }

    public Severity getSeverity() {
      return severity;
    }

    public String getCode() {
      return code;
    }

    public String getMessage() {
      return message;
    }

    public String getSubjectId() {
      return subjectId;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("severity", severity.name());
      result.put("code", code);
      result.put("message", message);
      result.put("subjectId", subjectId);
      return result;
    }
  }

  /** Immutable controlled revision entry. */
  public static final class RevisionEntry implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String revision;
    private final String description;
    private final String preparedBy;
    private final String checkedBy;
    private final String approvedBy;
    private final String approvalReference;

    public RevisionEntry(String revision, String description, String preparedBy, String checkedBy, String approvedBy,
        String approvalReference) {
      this.revision = requireText(revision, "revision");
      this.description = requireText(description, "description");
      this.preparedBy = optionalText(preparedBy);
      this.checkedBy = optionalText(checkedBy);
      this.approvedBy = optionalText(approvedBy);
      this.approvalReference = optionalText(approvalReference);
      if (!this.approvedBy.isEmpty() && this.approvalReference.isEmpty()) {
        throw new IllegalArgumentException("approvedBy requires approvalReference");
      }
    }

    public String getRevision() {
      return revision;
    }

    public String getDescription() {
      return description;
    }

    public String getPreparedBy() {
      return preparedBy;
    }

    public String getCheckedBy() {
      return checkedBy;
    }

    public String getApprovedBy() {
      return approvedBy;
    }

    public String getApprovalReference() {
      return approvalReference;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("revision", revision);
      result.put("description", description);
      result.put("preparedBy", preparedBy);
      result.put("checkedBy", checkedBy);
      result.put("approvedBy", approvedBy);
      result.put("approvalReference", approvalReference);
      return result;
    }
  }

  /** Immutable provenance snapshot carried into the controlled document set. */
  public static final class ProvenanceRecord implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String sourceType;
    private final String sourceReference;
    private final String method;
    private final String designCaseId;
    private final String approvalStatus;
    private final List<String> evidenceReferences;

    private ProvenanceRecord(EngineeringProvenance source) {
      this.sourceType = source.getSourceType();
      this.sourceReference = source.getSourceReference();
      this.method = source.getMethod();
      this.designCaseId = source.getDesignCaseId();
      this.approvalStatus = source.getApprovalStatus();
      this.evidenceReferences = Collections.unmodifiableList(new ArrayList<String>(source.getEvidenceReferences()));
    }

    /**
     * Returns the provenance source classification.
     *
     * @return source classification
     */
    public String getSourceType() {
      return sourceType;
    }

    /**
     * Returns the stable source semantic-object reference.
     *
     * @return source reference
     */
    public String getSourceReference() {
      return sourceReference;
    }

    /**
     * Returns the calculation or inference method.
     *
     * @return method name
     */
    public String getMethod() {
      return method;
    }

    /**
     * Returns the operating or design case identity.
     *
     * @return case identity
     */
    public String getDesignCaseId() {
      return designCaseId;
    }

    /**
     * Returns the approval state carried by the provenance record.
     *
     * @return approval state
     */
    public String getApprovalStatus() {
      return approvalStatus;
    }

    /**
     * Returns immutable supporting evidence references.
     *
     * @return evidence references
     */
    public List<String> getEvidenceReferences() {
      return evidenceReferences;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("sourceType", sourceType);
      result.put("sourceReference", sourceReference);
      result.put("method", method);
      result.put("designCaseId", designCaseId);
      result.put("approvalStatus", approvalStatus);
      result.put("evidenceReferences", evidenceReferences);
      return result;
    }
  }

  /** Immutable governed view of one canonical semantic object. */
  public static final class SemanticObject implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String id;
    private final EngineeringNode.Kind kind;
    private final String externalKey;
    private final String label;
    private final Map<String, Object> properties;
    private final List<ProvenanceRecord> provenance;
    private final List<EngineeringDiagramDesignationRegister.Designation> designations;

    private SemanticObject(EngineeringNode source,
        List<EngineeringDiagramDesignationRegister.Designation> designations) {
      this.id = source.getId();
      this.kind = source.getKind();
      this.externalKey = source.getExternalKey();
      this.label = source.getLabel();
      this.properties = immutablePropertyMap(source.getProperties());
      List<ProvenanceRecord> records = new ArrayList<ProvenanceRecord>();
      for (EngineeringProvenance item : source.getProvenance()) {
        records.add(new ProvenanceRecord(item));
      }
      this.provenance = Collections.unmodifiableList(records);
      this.designations = Collections
          .unmodifiableList(new ArrayList<EngineeringDiagramDesignationRegister.Designation>(designations));
    }

    /**
     * Returns the stable canonical semantic-object identity.
     *
     * @return semantic-object identity
     */
    public String getId() {
      return id;
    }

    /**
     * Returns the canonical semantic-object kind.
     *
     * @return object kind
     */
    public EngineeringNode.Kind getKind() {
      return kind;
    }

    /**
     * Returns the stable external source key.
     *
     * @return external source key
     */
    public String getExternalKey() {
      return externalKey;
    }

    /**
     * Returns the human-readable source label.
     *
     * @return source label
     */
    public String getLabel() {
      return label;
    }

    /**
     * Returns immutable, key-sorted semantic properties.
     *
     * @return semantic properties
     */
    public Map<String, Object> getProperties() {
      return properties;
    }

    /**
     * Returns immutable provenance snapshots.
     *
     * @return provenance snapshots
     */
    public List<ProvenanceRecord> getProvenance() {
      return provenance;
    }

    /**
     * Returns reviewed project designations without replacing the canonical source label.
     *
     * @return immutable reviewed designations
     */
    public List<EngineeringDiagramDesignationRegister.Designation> getDesignations() {
      return designations;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("id", id);
      result.put("kind", kind.name());
      result.put("externalKey", externalKey);
      result.put("label", label);
      result.put("properties", properties);
      List<Map<String, Object>> provenanceMaps = new ArrayList<Map<String, Object>>();
      for (ProvenanceRecord item : provenance) {
        provenanceMaps.add(item.toMap());
      }
      result.put("provenance", provenanceMaps);
      if (!designations.isEmpty()) {
        List<Map<String, Object>> designationMaps = new ArrayList<Map<String, Object>>();
        for (EngineeringDiagramDesignationRegister.Designation designation : designations) {
          designationMaps.add(designation.toMap());
        }
        result.put("designations", designationMaps);
      }
      return result;
    }

    private static Map<String, Object> immutablePropertyMap(Map<String, Object> source) {
      Map<String, Object> result = new TreeMap<String, Object>();
      for (Map.Entry<String, Object> entry : source.entrySet()) {
        result.put(entry.getKey(), immutablePropertyValue(entry.getValue()));
      }
      return Collections.unmodifiableMap(result);
    }

    private static Object immutablePropertyValue(Object value) {
      if (value instanceof Map<?, ?>) {
        Map<String, Object> result = new TreeMap<String, Object>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
          result.put(String.valueOf(entry.getKey()), immutablePropertyValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
      }
      if (value instanceof List<?>) {
        List<Object> result = new ArrayList<Object>();
        for (Object item : (List<?>) value) {
          result.add(immutablePropertyValue(item));
        }
        return Collections.unmodifiableList(result);
      }
      return value;
    }
  }

  /** Immutable end of a cross-sheet semantic connection. */
  public static final class OffPageConnector implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String id;
    private final String pairId;
    private final String semanticConnectionId;
    private final ConnectorRole role;
    private final String sheetId;
    private final String peerSheetId;
    private final String peerConnectorId;
    private final String zoneReference;

    private OffPageConnector(String id, String pairId, String semanticConnectionId, ConnectorRole role, String sheetId,
        String peerSheetId, String peerConnectorId, String zoneReference) {
      this.id = requireText(id, "connector id");
      this.pairId = requireText(pairId, "pairId");
      this.semanticConnectionId = requireText(semanticConnectionId, "semanticConnectionId");
      if (role == null) {
        throw new IllegalArgumentException("connector role must not be null");
      }
      this.role = role;
      this.sheetId = requireText(sheetId, "sheetId");
      this.peerSheetId = requireText(peerSheetId, "peerSheetId");
      this.peerConnectorId = requireText(peerConnectorId, "peerConnectorId");
      this.zoneReference = requireText(zoneReference, "zoneReference");
    }

    public String getId() {
      return id;
    }

    public String getPairId() {
      return pairId;
    }

    public String getSemanticConnectionId() {
      return semanticConnectionId;
    }

    public ConnectorRole getRole() {
      return role;
    }

    public String getSheetId() {
      return sheetId;
    }

    public String getPeerSheetId() {
      return peerSheetId;
    }

    public String getPeerConnectorId() {
      return peerConnectorId;
    }

    public String getZoneReference() {
      return zoneReference;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("id", id);
      result.put("pairId", pairId);
      result.put("semanticConnectionId", semanticConnectionId);
      result.put("role", role.name());
      result.put("sheetId", sheetId);
      result.put("peerSheetId", peerSheetId);
      result.put("peerConnectorId", peerConnectorId);
      result.put("zoneReference", zoneReference);
      return result;
    }
  }

  /** Immutable drawing sheet containing views of canonical semantic objects. */
  public static final class Sheet implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String id;
    private final String number;
    private final String title;
    private final List<String> areaNodeIds;
    private final List<String> objectNodeIds;
    private final List<OffPageConnector> offPageConnectors;

    private Sheet(String id, String number, String title, List<String> areaNodeIds, List<String> objectNodeIds,
        List<OffPageConnector> connectors) {
      this.id = requireText(id, "sheet id");
      this.number = requireText(number, "sheet number");
      this.title = requireText(title, "sheet title");
      this.areaNodeIds = immutableStrings(areaNodeIds, "areaNodeIds");
      this.objectNodeIds = immutableStrings(objectNodeIds, "objectNodeIds");
      this.offPageConnectors = Collections.unmodifiableList(new ArrayList<OffPageConnector>(connectors));
    }

    public String getId() {
      return id;
    }

    public String getNumber() {
      return number;
    }

    public String getTitle() {
      return title;
    }

    public List<String> getAreaNodeIds() {
      return areaNodeIds;
    }

    public List<String> getObjectNodeIds() {
      return objectNodeIds;
    }

    public List<OffPageConnector> getOffPageConnectors() {
      return offPageConnectors;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("id", id);
      result.put("number", number);
      result.put("title", title);
      result.put("areaNodeIds", areaNodeIds);
      result.put("objectNodeIds", objectNodeIds);
      List<Map<String, Object>> connectorMaps = new ArrayList<Map<String, Object>>();
      for (OffPageConnector connector : offPageConnectors) {
        connectorMaps.add(connector.toMap());
      }
      result.put("offPageConnectors", connectorMaps);
      return result;
    }
  }

  /** Immutable drawing containing one or more controlled sheets. */
  public static final class Drawing implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String id;
    private final String number;
    private final String title;
    private final ContentProfile contentProfile;
    private final List<Sheet> sheets;

    private Drawing(String id, String number, String title, ContentProfile contentProfile, List<Sheet> sheets) {
      this.id = requireText(id, "drawing id");
      this.number = requireText(number, "drawing number");
      this.title = requireText(title, "drawing title");
      if (contentProfile == null) {
        throw new IllegalArgumentException("contentProfile must not be null");
      }
      this.contentProfile = contentProfile;
      this.sheets = Collections.unmodifiableList(new ArrayList<Sheet>(sheets));
    }

    public String getId() {
      return id;
    }

    public String getNumber() {
      return number;
    }

    public String getTitle() {
      return title;
    }

    public ContentProfile getContentProfile() {
      return contentProfile;
    }

    public List<Sheet> getSheets() {
      return sheets;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("id", id);
      result.put("number", number);
      result.put("title", title);
      result.put("contentProfile", contentProfile.name());
      List<Map<String, Object>> sheetMaps = new ArrayList<Map<String, Object>>();
      for (Sheet sheet : sheets) {
        sheetMaps.add(sheet.toMap());
      }
      result.put("sheets", sheetMaps);
      return result;
    }
  }

  private final String id;
  private final String plantId;
  private final String title;
  private final String revision;
  private final String sourceGraphFingerprint;
  private final DocumentStatus status;
  private final IssuePurpose issuePurpose;
  private final String accountableApprovalReference;
  private final List<RevisionEntry> revisionHistory;
  private final List<SemanticObject> semanticObjects;
  private final List<Drawing> drawings;
  private final List<Diagnostic> diagnostics;

  private EngineeringDiagramDocumentSet(String id, EngineeringGraph graph, String title, DocumentStatus status,
      IssuePurpose issuePurpose, String accountableApprovalReference, List<RevisionEntry> revisionHistory,
      List<Drawing> drawings, List<Diagnostic> diagnostics, EngineeringDiagramDesignationRegister designationRegister) {
    this.id = requireText(id, "document set id");
    this.plantId = graph.getProjectId();
    this.title = requireText(title, "title");
    this.revision = graph.getRevision();
    this.sourceGraphFingerprint = String.valueOf(graph.toMap().get("fingerprint"));
    this.status = status;
    this.issuePurpose = issuePurpose;
    this.accountableApprovalReference = optionalText(accountableApprovalReference);
    if ((status == DocumentStatus.APPROVED || issuePurpose == IssuePurpose.CONSTRUCTION)
        && this.accountableApprovalReference.isEmpty()) {
      throw new IllegalArgumentException("approved or construction issue requires accountableApprovalReference");
    }
    this.revisionHistory = Collections.unmodifiableList(new ArrayList<RevisionEntry>(revisionHistory));
    this.drawings = Collections.unmodifiableList(new ArrayList<Drawing>(drawings));
    List<Diagnostic> assessed = new ArrayList<Diagnostic>(diagnostics);
    this.semanticObjects = semanticObjects(graph, designationRegister, assessed);
    assessed.addAll(validateSemanticObjects(semanticObjects));
    assessed.addAll(validate(drawings));
    this.diagnostics = Collections.unmodifiableList(assessed);
  }

  /**
   * Creates a deterministic area-sheet view of a canonical graph.
   *
   * <p>
   * A single-area graph produces one sheet. A multi-area graph produces one sheet per area and projects each cross-area
   * connection through reciprocal off-page connectors while preserving one semantic connection identity.
   * </p>
   *
   * @param graph canonical semantic graph
   * @param drawingNumber controlled drawing number
   * @param title document-set title
   * @param profile requested content profile
   * @param inheritedDiagnostics diagnostics produced by the source adapter
   * @return immutable controlled-document proposal
   */
  public static EngineeringDiagramDocumentSet fromGraph(EngineeringGraph graph, String drawingNumber, String title,
      ContentProfile profile, List<Diagnostic> inheritedDiagnostics) {
    return fromGraph(graph, drawingNumber, title, profile, inheritedDiagnostics,
        new EngineeringDiagramDesignationRegister());
  }

  /**
   * Creates a deterministic area-sheet view with reviewed project designations.
   *
   * @param graph canonical semantic graph
   * @param drawingNumber controlled drawing number
   * @param title document-set title
   * @param profile requested content profile
   * @param inheritedDiagnostics diagnostics produced by the source adapter
   * @param designationRegister reviewed project designation evidence
   * @return immutable controlled-document proposal
   */
  public static EngineeringDiagramDocumentSet fromGraph(EngineeringGraph graph, String drawingNumber, String title,
      ContentProfile profile, List<Diagnostic> inheritedDiagnostics,
      EngineeringDiagramDesignationRegister designationRegister) {
    if (graph == null) {
      throw new IllegalArgumentException("graph must not be null");
    }
    if (profile == null) {
      throw new IllegalArgumentException("profile must not be null");
    }
    if (designationRegister == null) {
      throw new IllegalArgumentException("designationRegister must not be null");
    }
    String normalizedNumber = requireText(drawingNumber, "drawingNumber");
    List<Diagnostic> diagnostics = inheritedDiagnostics == null ? new ArrayList<Diagnostic>()
        : new ArrayList<Diagnostic>(inheritedDiagnostics);
    List<EngineeringNode> areas = nodesOfKind(graph, EngineeringNode.Kind.AREA);
    List<MutableSheet> mutableSheets = new ArrayList<MutableSheet>();
    Map<String, MutableSheet> sheetsByAreaName = new LinkedHashMap<String, MutableSheet>();
    if (areas.isEmpty()) {
      MutableSheet sheet = new MutableSheet(sheetId(normalizedNumber, "plant"), "1", title);
      sheet.objectNodeIds.addAll(visualNodeIds(graph));
      mutableSheets.add(sheet);
    } else {
      int index = 1;
      for (EngineeringNode area : areas) {
        String areaName = stringProperty(area, "areaName", area.getLabel());
        MutableSheet sheet = new MutableSheet(sheetId(normalizedNumber, area.getExternalKey()), String.valueOf(index),
            areaName);
        sheet.areaNodeIds.add(area.getId());
        for (EngineeringNode node : graph.getNodes().values()) {
          if (belongsToArea(node, areaName) && isVisual(node.getKind())) {
            sheet.objectNodeIds.add(node.getId());
          }
        }
        mutableSheets.add(sheet);
        sheetsByAreaName.put(areaName, sheet);
        index++;
      }
    }
    addCrossSheetReferences(graph, mutableSheets, sheetsByAreaName, diagnostics);
    List<Sheet> sheets = new ArrayList<Sheet>();
    for (MutableSheet mutable : mutableSheets) {
      sheets.add(mutable.toSheet());
    }
    String drawingId = "drawing:" + EngineeringIds.canonical(normalizedNumber);
    Drawing drawing = new Drawing(drawingId, normalizedNumber, title, profile, sheets);
    List<RevisionEntry> history = Collections.singletonList(
        new RevisionEntry(graph.getRevision(), "Initial simulation-driven engineering proposal", "NEQSIM", "", "", ""));
    return new EngineeringDiagramDocumentSet("document-set:" + EngineeringIds.canonical(normalizedNumber), graph, title,
        DocumentStatus.WORKING, IssuePurpose.ENGINEERING_PROPOSAL, "", history, Collections.singletonList(drawing),
        diagnostics, designationRegister);
  }

  /** Convenience overload without inherited source diagnostics. */
  public static EngineeringDiagramDocumentSet fromGraph(EngineeringGraph graph, String drawingNumber, String title,
      ContentProfile profile) {
    return fromGraph(graph, drawingNumber, title, profile, Collections.<Diagnostic>emptyList());
  }

  /** Convenience overload with reviewed project designations and no inherited diagnostics. */
  public static EngineeringDiagramDocumentSet fromGraph(EngineeringGraph graph, String drawingNumber, String title,
      ContentProfile profile, EngineeringDiagramDesignationRegister designationRegister) {
    return fromGraph(graph, drawingNumber, title, profile, Collections.<Diagnostic>emptyList(), designationRegister);
  }

  public String getId() {
    return id;
  }

  public String getPlantId() {
    return plantId;
  }

  public String getTitle() {
    return title;
  }

  public String getRevision() {
    return revision;
  }

  public String getSourceGraphFingerprint() {
    return sourceGraphFingerprint;
  }

  public DocumentStatus getStatus() {
    return status;
  }

  public IssuePurpose getIssuePurpose() {
    return issuePurpose;
  }

  public String getAccountableApprovalReference() {
    return accountableApprovalReference;
  }

  public List<RevisionEntry> getRevisionHistory() {
    return revisionHistory;
  }

  /**
   * Returns immutable canonical semantic-object snapshots used by every drawing view.
   *
   * @return semantic-object snapshots
   */
  public List<SemanticObject> getSemanticObjects() {
    return semanticObjects;
  }

  public List<Drawing> getDrawings() {
    return drawings;
  }

  public List<Diagnostic> getDiagnostics() {
    return diagnostics;
  }

  /** @return true when no broken document-set semantic was found */
  public boolean isValid() {
    for (Diagnostic diagnostic : diagnostics) {
      if (diagnostic.getSeverity() == Severity.ERROR) {
        return false;
      }
    }
    return true;
  }

  /** @return deterministic machine-readable document-set representation */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", SCHEMA_VERSION);
    result.put("id", id);
    result.put("plantId", plantId);
    result.put("title", title);
    result.put("revision", revision);
    result.put("sourceGraphFingerprint", sourceGraphFingerprint);
    result.put("status", status.name());
    result.put("issuePurpose", issuePurpose.name());
    result.put("accountableApprovalReference", accountableApprovalReference);
    List<Map<String, Object>> revisionMaps = new ArrayList<Map<String, Object>>();
    for (RevisionEntry entry : revisionHistory) {
      revisionMaps.add(entry.toMap());
    }
    result.put("revisionHistory", revisionMaps);
    List<Map<String, Object>> semanticObjectMaps = new ArrayList<Map<String, Object>>();
    for (SemanticObject semanticObject : semanticObjects) {
      semanticObjectMaps.add(semanticObject.toMap());
    }
    result.put("semanticObjects", semanticObjectMaps);
    List<Map<String, Object>> drawingMaps = new ArrayList<Map<String, Object>>();
    for (Drawing drawing : drawings) {
      drawingMaps.add(drawing.toMap());
    }
    result.put("drawings", drawingMaps);
    List<Map<String, Object>> diagnosticMaps = new ArrayList<Map<String, Object>>();
    for (Diagnostic diagnostic : diagnostics) {
      diagnosticMaps.add(diagnostic.toMap());
    }
    result.put("diagnostics", diagnosticMaps);
    result.put("fingerprint", fingerprint(result));
    return result;
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  /**
   * Compares this controlled revision with a newer revision of the same document set.
   *
   * @param newer newer controlled revision
   * @return deterministic semantic-object and affected-view impact
   */
  public EngineeringDiagramRevisionImpact compareTo(EngineeringDiagramDocumentSet newer) {
    return EngineeringDiagramRevisionImpact.compare(this, newer);
  }

  private static void addCrossSheetReferences(EngineeringGraph graph, List<MutableSheet> allSheets,
      Map<String, MutableSheet> sheetsByAreaName, List<Diagnostic> diagnostics) {
    for (EngineeringNode node : graph.getNodes().values()) {
      if (!Boolean.TRUE.equals(node.getProperties().get("crossArea"))) {
        continue;
      }
      String sourceArea = stringProperty(node, "sourceArea", "");
      String targetArea = stringProperty(node, "targetArea", "");
      MutableSheet sourceSheet = sheetsByAreaName.get(sourceArea);
      MutableSheet targetSheet = sheetsByAreaName.get(targetArea);
      if (sourceSheet == null || targetSheet == null) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "DIAGRAM_DOCUMENT_BROKEN_CROSS_SHEET_REFERENCE",
            "Cross-area semantic connection does not resolve to both controlled sheets", node.getId()));
        continue;
      }
      if (sourceSheet.id.equals(targetSheet.id)) {
        diagnostics.add(new Diagnostic(Severity.WARNING, "DIAGRAM_DOCUMENT_REDUNDANT_OFF_PAGE_REFERENCE",
            "Cross-area connection resolved to one sheet and does not need off-page connectors", node.getId()));
        continue;
      }
      String key = EngineeringIds.canonical(node.getId()) + "-" + shortHash(node.getId());
      String pairId = "offpage-pair:" + key;
      String sourceId = "offpage:" + key + ":source";
      String targetId = "offpage:" + key + ":target";
      sourceSheet.objectNodeIds.add(node.getId());
      targetSheet.objectNodeIds.add(node.getId());
      sourceSheet.connectors.add(new OffPageConnector(sourceId, pairId, node.getId(), ConnectorRole.SOURCE,
          sourceSheet.id, targetSheet.id, targetId, "AUTO"));
      targetSheet.connectors.add(new OffPageConnector(targetId, pairId, node.getId(), ConnectorRole.TARGET,
          targetSheet.id, sourceSheet.id, sourceId, "AUTO"));
    }
    if (allSheets.size() > 1 && sheetsByAreaName.isEmpty()) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "DIAGRAM_DOCUMENT_MISSING_AREA_SHEET_INDEX",
          "Multi-sheet document set has no area-to-sheet index", ""));
    }
  }

  private static List<Diagnostic> validate(List<Drawing> drawings) {
    List<Diagnostic> result = new ArrayList<Diagnostic>();
    Map<String, Sheet> sheets = new LinkedHashMap<String, Sheet>();
    Map<String, OffPageConnector> connectors = new LinkedHashMap<String, OffPageConnector>();
    Map<String, Integer> pairCounts = new LinkedHashMap<String, Integer>();
    for (Drawing drawing : drawings) {
      for (Sheet sheet : drawing.getSheets()) {
        if (sheets.put(sheet.getId(), sheet) != null) {
          result.add(new Diagnostic(Severity.ERROR, "DIAGRAM_DOCUMENT_DUPLICATE_SHEET_ID",
              "Sheet identity is not unique in the document set", sheet.getId()));
        }
        for (OffPageConnector connector : sheet.getOffPageConnectors()) {
          if (connectors.put(connector.getId(), connector) != null) {
            result.add(new Diagnostic(Severity.ERROR, "DIAGRAM_DOCUMENT_DUPLICATE_CONNECTOR_ID",
                "Off-page connector identity is not unique", connector.getId()));
          }
          Integer count = pairCounts.get(connector.getPairId());
          pairCounts.put(connector.getPairId(), Integer.valueOf(count == null ? 1 : count.intValue() + 1));
        }
      }
    }
    for (OffPageConnector connector : connectors.values()) {
      OffPageConnector peer = connectors.get(connector.getPeerConnectorId());
      if (!sheets.containsKey(connector.getPeerSheetId()) || peer == null
          || !connector.getId().equals(peer.getPeerConnectorId()) || !connector.getPairId().equals(peer.getPairId())
          || !connector.getSemanticConnectionId().equals(peer.getSemanticConnectionId())
          || connector.getRole() == peer.getRole()) {
        result.add(new Diagnostic(Severity.ERROR, "DIAGRAM_DOCUMENT_BROKEN_OFF_PAGE_PAIR",
            "Off-page connector does not have one reciprocal peer on the referenced sheet", connector.getId()));
      }
    }
    for (Map.Entry<String, Integer> entry : pairCounts.entrySet()) {
      if (entry.getValue().intValue() != 2) {
        result.add(new Diagnostic(Severity.ERROR, "DIAGRAM_DOCUMENT_OFF_PAGE_PAIR_CARDINALITY",
            "Off-page connector pair must contain exactly two reciprocal ends", entry.getKey()));
      }
    }
    return result;
  }

  private static List<SemanticObject> semanticObjects(EngineeringGraph graph,
      EngineeringDiagramDesignationRegister designationRegister, List<Diagnostic> diagnostics) {
    Map<String, List<EngineeringDiagramDesignationRegister.Designation>> designationsByObject = validDesignations(graph,
        designationRegister, diagnostics);
    List<SemanticObject> result = new ArrayList<SemanticObject>();
    for (EngineeringNode node : graph.getNodes().values()) {
      List<EngineeringDiagramDesignationRegister.Designation> designations = designationsByObject.get(node.getId());
      result.add(new SemanticObject(node,
          designations == null ? Collections.<EngineeringDiagramDesignationRegister.Designation>emptyList()
              : designations));
    }
    return Collections.unmodifiableList(result);
  }

  private static Map<String, List<EngineeringDiagramDesignationRegister.Designation>> validDesignations(
      EngineeringGraph graph, EngineeringDiagramDesignationRegister designationRegister, List<Diagnostic> diagnostics) {
    Map<String, List<EngineeringDiagramDesignationRegister.Designation>> result = new LinkedHashMap<String, List<EngineeringDiagramDesignationRegister.Designation>>();
    for (EngineeringDiagramDesignationRegister.Designation designation : designationRegister.getDesignations()) {
      EngineeringNode node = graph.getNode(designation.getSemanticObjectId());
      if (node == null) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "DIAGRAM_DOCUMENT_DESIGNATION_UNKNOWN_OBJECT",
            "Reviewed designation references an unknown semantic object", designation.getSemanticObjectId()));
        continue;
      }
      if (!supportsDesignation(node.getKind(), designation.getKind())) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "DIAGRAM_DOCUMENT_DESIGNATION_KIND_MISMATCH",
            "Reviewed designation type is not valid for the target semantic-object kind", node.getId()));
        continue;
      }
      List<EngineeringDiagramDesignationRegister.Designation> values = result.get(node.getId());
      if (values == null) {
        values = new ArrayList<EngineeringDiagramDesignationRegister.Designation>();
        result.put(node.getId(), values);
      }
      values.add(designation);
    }
    return result;
  }

  private static boolean supportsDesignation(EngineeringNode.Kind nodeKind,
      EngineeringDiagramDesignationRegister.Kind designationKind) {
    if (designationKind == EngineeringDiagramDesignationRegister.Kind.EQUIPMENT_TAG) {
      return nodeKind == EngineeringNode.Kind.EQUIPMENT;
    }
    return nodeKind == EngineeringNode.Kind.LINE || nodeKind == EngineeringNode.Kind.PIPE_SEGMENT;
  }

  private static List<Diagnostic> validateSemanticObjects(List<SemanticObject> objects) {
    List<Diagnostic> result = new ArrayList<Diagnostic>();
    Map<String, SemanticObject> objectsById = new LinkedHashMap<String, SemanticObject>();
    for (SemanticObject object : objects) {
      if (objectsById.put(object.getId(), object) != null) {
        result.add(new Diagnostic(Severity.ERROR, "DIAGRAM_DOCUMENT_DUPLICATE_SEMANTIC_OBJECT_ID",
            "Semantic object identity is not unique in the document set", object.getId()));
      }
      if (object.getKind() == EngineeringNode.Kind.CALCULATION && object.getProperties().containsKey("resultValue")
          && !hasGovernedValueMetadata(object)) {
        Severity severity = hasProvenanceSource(object, "SIMULATION_RESULT") ? Severity.ERROR : Severity.WARNING;
        result.add(new Diagnostic(severity, "DIAGRAM_DOCUMENT_INCOMPLETE_GOVERNED_VALUE",
            "Calculated value is missing unit, case, engineering state, approval state, or provenance",
            object.getId()));
      }
    }
    return result;
  }

  private static boolean hasGovernedValueMetadata(SemanticObject object) {
    Map<String, Object> properties = object.getProperties();
    return hasPropertyText(properties, "resultUnit") && hasPropertyText(properties, "designCaseId")
        && (hasPropertyText(properties, "engineeringState") || hasPropertyText(properties, "status"))
        && (hasPropertyText(properties, "approvalStatus") || hasProvenanceApprovalState(object))
        && !object.getProvenance().isEmpty();
  }

  private static boolean hasPropertyText(Map<String, Object> properties, String name) {
    Object value = properties.get(name);
    return value != null && !value.toString().trim().isEmpty();
  }

  private static boolean hasProvenanceApprovalState(SemanticObject object) {
    for (ProvenanceRecord record : object.getProvenance()) {
      if (!record.getApprovalStatus().trim().isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasProvenanceSource(SemanticObject object, String sourceType) {
    for (ProvenanceRecord record : object.getProvenance()) {
      if (sourceType.equals(record.getSourceType())) {
        return true;
      }
    }
    return false;
  }

  private static List<EngineeringNode> nodesOfKind(EngineeringGraph graph, EngineeringNode.Kind kind) {
    List<EngineeringNode> result = new ArrayList<EngineeringNode>();
    for (EngineeringNode node : graph.getNodes().values()) {
      if (node.getKind() == kind) {
        result.add(node);
      }
    }
    return result;
  }

  private static List<String> visualNodeIds(EngineeringGraph graph) {
    List<String> result = new ArrayList<String>();
    for (EngineeringNode node : graph.getNodes().values()) {
      if (isVisual(node.getKind())) {
        result.add(node.getId());
      }
    }
    return result;
  }

  private static boolean isVisual(EngineeringNode.Kind kind) {
    return kind == EngineeringNode.Kind.AREA || kind == EngineeringNode.Kind.EQUIPMENT
        || kind == EngineeringNode.Kind.LINE || kind == EngineeringNode.Kind.INSTRUMENT
        || kind == EngineeringNode.Kind.BOUNDARY || kind == EngineeringNode.Kind.PORT
        || kind == EngineeringNode.Kind.NOZZLE || kind == EngineeringNode.Kind.PIPE_SEGMENT
        || kind == EngineeringNode.Kind.SIGNAL_CONNECTION || kind == EngineeringNode.Kind.ENERGY_CONNECTION;
  }

  private static boolean belongsToArea(EngineeringNode node, String areaName) {
    if (Boolean.TRUE.equals(node.getProperties().get("crossArea"))) {
      return false;
    }
    String declaredArea = stringProperty(node, "areaName", "");
    if (!declaredArea.isEmpty()) {
      return areaName.equals(declaredArea);
    }
    return areaName.equals(stringProperty(node, "sourceArea", ""))
        && areaName.equals(stringProperty(node, "targetArea", ""));
  }

  private static String sheetId(String drawingNumber, String areaKey) {
    return "sheet:" + EngineeringIds.canonical(drawingNumber) + ":" + EngineeringIds.canonical(areaKey);
  }

  private static String stringProperty(EngineeringNode node, String name, String fallback) {
    Object value = node.getProperties().get(name);
    return value == null || value.toString().trim().isEmpty() ? fallback : value.toString();
  }

  private static List<String> immutableStrings(List<String> values, String name) {
    if (values == null) {
      throw new IllegalArgumentException(name + " must not be null");
    }
    List<String> result = new ArrayList<String>();
    for (String value : values) {
      result.add(requireText(value, name + " item"));
    }
    return Collections.unmodifiableList(result);
  }

  private static String fingerprint(Map<String, Object> map) {
    Map<String, Object> content = new LinkedHashMap<String, Object>(map);
    content.remove("fingerprint");
    String json = new GsonBuilder().create().toJson(content);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (byte value : hash) {
        result.append(String.format("%02x", value & 0xff));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  private static String shortHash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (int index = 0; index < 6; index++) {
        result.append(String.format("%02x", hash[index] & 0xff));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  private static String optionalText(String value) {
    return value == null ? "" : value.trim();
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }

  private static final class MutableSheet {
    private final String id;
    private final String number;
    private final String title;
    private final List<String> areaNodeIds = new ArrayList<String>();
    private final List<String> objectNodeIds = new ArrayList<String>();
    private final List<OffPageConnector> connectors = new ArrayList<OffPageConnector>();

    private MutableSheet(String id, String number, String title) {
      this.id = id;
      this.number = number;
      this.title = title;
    }

    private Sheet toSheet() {
      return new Sheet(id, number, title, areaNodeIds, objectNodeIds, connectors);
    }
  }
}
