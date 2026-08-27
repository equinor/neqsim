package neqsim.process.processmodel.dexpi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.Drawing;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.OffPageConnector;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.SemanticObject;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.Sheet;
import neqsim.process.engineering.model.EngineeringDiagramRevisionImpact;

/**
 * Projects assessed DEXPI package changes onto controlled diagram-document views and registers.
 *
 * <p>
 * The projection consumes independently assessed package evidence and immutable diagram document sets. It never
 * reconstructs or executes a {@code ProcessModel}. Results identify review scope; they are not an MOC decision, drawing
 * approval, native whole-plant DEXPI exchange, or fitness-for-construction statement.
 * </p>
 */
public final class Dexpi20ProcessModelPackageDocumentImpact implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Gson GSON = new Gson();

  public static final String SCHEMA_VERSION = "neqsim_dexpi20_process_model_package_document_impact.v1";

  /** Overall cross-artifact projection status. */
  public enum Status {
    /** Assessed package and controlled-document content are unchanged. */
    UNCHANGED,
    /** All changed stable identities were projected and accountable review is required. */
    REVIEW_REQUIRED,
    /** At least one changed package identity has no controlled-document view. */
    INCOMPLETE
  }

  /** Artifact families requiring coordinated review after the assessed change. */
  public enum ReviewScope {
    DEXPI_PACKAGE, DEXPI_AREA_EXCHANGE, CONTROLLED_DOCUMENT_SET, CONTROLLED_DRAWING, CONTROLLED_SHEET,
    DESIGNATION_REGISTER, LAYOUT_REGISTER, OPERATING_CASE_TABLES, INFORMATION_REGISTER, ENGINEERING_STUDY
  }

  private final String plantId;
  private final String documentSetId;
  private final String baselinePackageFingerprint;
  private final String revisedPackageFingerprint;
  private final String baselineDocumentFingerprint;
  private final String revisedDocumentFingerprint;
  private final String baselineRevision;
  private final String revisedRevision;
  private final boolean documentSetContentChanged;
  private final Status status;
  private final List<String> changedAreaIds;
  private final List<String> changedConnectionIds;
  private final List<String> changedSemanticObjectIds;
  private final List<String> affectedSheetIds;
  private final List<String> affectedDrawingIds;
  private final List<String> affectedDesignationObjectIds;
  private final List<String> affectedLayoutSheetIds;
  private final List<String> unmatchedAreaIds;
  private final List<String> unmatchedConnectionIds;
  private final List<ReviewScope> reviewScopes;

  private Dexpi20ProcessModelPackageDocumentImpact(Builder builder) {
    plantId = builder.plantId;
    documentSetId = builder.documentSetId;
    baselinePackageFingerprint = builder.baselinePackageFingerprint;
    revisedPackageFingerprint = builder.revisedPackageFingerprint;
    baselineDocumentFingerprint = builder.baselineDocumentFingerprint;
    revisedDocumentFingerprint = builder.revisedDocumentFingerprint;
    baselineRevision = builder.baselineRevision;
    revisedRevision = builder.revisedRevision;
    documentSetContentChanged = builder.documentSetContentChanged;
    changedAreaIds = immutableStrings(builder.changedAreaIds);
    changedConnectionIds = immutableStrings(builder.changedConnectionIds);
    changedSemanticObjectIds = immutableStrings(builder.changedSemanticObjectIds);
    affectedSheetIds = immutableStrings(builder.affectedSheetIds);
    affectedDrawingIds = immutableStrings(builder.affectedDrawingIds);
    affectedDesignationObjectIds = immutableStrings(builder.affectedDesignationObjectIds);
    affectedLayoutSheetIds = immutableStrings(builder.affectedLayoutSheetIds);
    unmatchedAreaIds = immutableStrings(builder.unmatchedAreaIds);
    unmatchedConnectionIds = immutableStrings(builder.unmatchedConnectionIds);
    reviewScopes = immutableScopes(builder.reviewScopes);
    if (!unmatchedAreaIds.isEmpty() || !unmatchedConnectionIds.isEmpty()) {
      status = Status.INCOMPLETE;
    } else if (reviewScopes.isEmpty()) {
      status = Status.UNCHANGED;
    } else {
      status = Status.REVIEW_REQUIRED;
    }
  }

  /**
   * Projects one assessed package revision comparison onto matching controlled document revisions.
   *
   * @param packageImpact assessed package comparison
   * @param baselineDocument controlled baseline document set
   * @param revisedDocument controlled revised document set
   * @return deterministic cross-artifact impact evidence
   */
  public static Dexpi20ProcessModelPackageDocumentImpact project(Dexpi20ProcessModelPackageRevisionImpact packageImpact,
      EngineeringDiagramDocumentSet baselineDocument, EngineeringDiagramDocumentSet revisedDocument) {
    requireCompatible(packageImpact, baselineDocument, revisedDocument);
    Builder builder = new Builder(packageImpact, baselineDocument, revisedDocument);
    collectPackageChanges(packageImpact, builder);
    EngineeringDiagramRevisionImpact documentImpact = baselineDocument.compareTo(revisedDocument);
    builder.changedSemanticObjectIds.addAll(documentImpact.getAddedSemanticObjectIds());
    builder.changedSemanticObjectIds.addAll(documentImpact.getRemovedSemanticObjectIds());
    builder.changedSemanticObjectIds.addAll(documentImpact.getModifiedSemanticObjectIds());
    builder.affectedSheetIds.addAll(documentImpact.getAffectedSheetIds());
    builder.affectedDrawingIds.addAll(documentImpact.getAffectedDrawingIds());
    collectChangedSheets(baselineDocument, revisedDocument, builder);
    collectPackageViews(baselineDocument, builder);
    collectPackageViews(revisedDocument, builder);
    collectRegisterImpact(baselineDocument, builder);
    collectRegisterImpact(revisedDocument, builder);
    completeScopes(packageImpact, builder);
    return new Dexpi20ProcessModelPackageDocumentImpact(builder);
  }

  public String getPlantId() {
    return plantId;
  }

  public String getDocumentSetId() {
    return documentSetId;
  }

  public String getBaselinePackageFingerprint() {
    return baselinePackageFingerprint;
  }

  public String getRevisedPackageFingerprint() {
    return revisedPackageFingerprint;
  }

  public String getBaselineDocumentFingerprint() {
    return baselineDocumentFingerprint;
  }

  public String getRevisedDocumentFingerprint() {
    return revisedDocumentFingerprint;
  }

  public String getBaselineRevision() {
    return baselineRevision;
  }

  public String getRevisedRevision() {
    return revisedRevision;
  }

  public boolean isDocumentSetContentChanged() {
    return documentSetContentChanged;
  }

  public Status getStatus() {
    return status;
  }

  public List<String> getChangedAreaIds() {
    return immutableStrings(changedAreaIds);
  }

  public List<String> getChangedConnectionIds() {
    return immutableStrings(changedConnectionIds);
  }

  public List<String> getChangedSemanticObjectIds() {
    return immutableStrings(changedSemanticObjectIds);
  }

  public List<String> getAffectedSheetIds() {
    return immutableStrings(affectedSheetIds);
  }

  public List<String> getAffectedDrawingIds() {
    return immutableStrings(affectedDrawingIds);
  }

  public List<String> getAffectedDesignationObjectIds() {
    return immutableStrings(affectedDesignationObjectIds);
  }

  public List<String> getAffectedLayoutSheetIds() {
    return immutableStrings(affectedLayoutSheetIds);
  }

  public List<String> getUnmatchedAreaIds() {
    return immutableStrings(unmatchedAreaIds);
  }

  public List<String> getUnmatchedConnectionIds() {
    return immutableStrings(unmatchedConnectionIds);
  }

  public List<ReviewScope> getReviewScopes() {
    return immutableScopes(reviewScopes);
  }

  public boolean isProjectionComplete() {
    return unmatchedAreaIds.isEmpty() && unmatchedConnectionIds.isEmpty();
  }

  public String getApprovalStatus() {
    return "REVIEW_REQUIRED";
  }

  public boolean isEngineeringReviewRequired() {
    return true;
  }

  public boolean isFitnessForConstruction() {
    return false;
  }

  public boolean isNativeWholePlantDexpiExchange() {
    return false;
  }

  /** @return deterministic machine-readable evidence */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", SCHEMA_VERSION);
    result.put("plantId", plantId);
    result.put("documentSetId", documentSetId);
    result.put("baselineRevision", baselineRevision);
    result.put("revisedRevision", revisedRevision);
    result.put("baselinePackageFingerprint", baselinePackageFingerprint);
    result.put("revisedPackageFingerprint", revisedPackageFingerprint);
    result.put("baselineDocumentFingerprint", baselineDocumentFingerprint);
    result.put("revisedDocumentFingerprint", revisedDocumentFingerprint);
    result.put("documentSetContentChanged", Boolean.valueOf(documentSetContentChanged));
    result.put("status", status.name());
    result.put("projectionComplete", Boolean.valueOf(isProjectionComplete()));
    result.put("changedAreaIds", immutableStrings(changedAreaIds));
    result.put("changedConnectionIds", immutableStrings(changedConnectionIds));
    result.put("changedSemanticObjectIds", immutableStrings(changedSemanticObjectIds));
    result.put("affectedSheetIds", immutableStrings(affectedSheetIds));
    result.put("affectedDrawingIds", immutableStrings(affectedDrawingIds));
    result.put("affectedDesignationObjectIds", immutableStrings(affectedDesignationObjectIds));
    result.put("affectedLayoutSheetIds", immutableStrings(affectedLayoutSheetIds));
    result.put("unmatchedAreaIds", immutableStrings(unmatchedAreaIds));
    result.put("unmatchedConnectionIds", immutableStrings(unmatchedConnectionIds));
    List<String> scopes = new ArrayList<String>();
    for (ReviewScope scope : reviewScopes) {
      scopes.add(scope.name());
    }
    result.put("reviewScopes", scopes);
    result.put("approvalStatus", getApprovalStatus());
    result.put("engineeringReviewRequired", Boolean.TRUE);
    result.put("fitnessForConstruction", Boolean.FALSE);
    result.put("nativeWholePlantDexpiExchange", Boolean.FALSE);
    result.put("scope", "Assessed DEXPI package and immutable controlled-document change projection");
    result.put("limitations",
        Arrays.asList("Does not reconstruct or execute a ProcessModel",
            "Does not decide management of change, drawing approval, or study completeness",
            "Unmatched stable identities require explicit accountable reconciliation"));
    result.put("fingerprint", fingerprint(result));
    return result;
  }

  /** @return pretty-printed deterministic JSON */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  private static void requireCompatible(Dexpi20ProcessModelPackageRevisionImpact packageImpact,
      EngineeringDiagramDocumentSet baselineDocument, EngineeringDiagramDocumentSet revisedDocument) {
    if (packageImpact == null || baselineDocument == null || revisedDocument == null) {
      throw new IllegalArgumentException("package impact and document revisions must not be null");
    }
    if (!baselineDocument.getId().equals(revisedDocument.getId())) {
      throw new IllegalArgumentException("Document projection requires the same controlled document-set identity");
    }
    if (!packageImpact.getPlantId().equals(baselineDocument.getPlantId())
        || !packageImpact.getPlantId().equals(revisedDocument.getPlantId())) {
      throw new IllegalArgumentException("Package and controlled documents must use the same plant identity");
    }
  }

  private static void collectPackageChanges(Dexpi20ProcessModelPackageRevisionImpact impact, Builder builder) {
    for (Dexpi20ProcessModelPackageRevisionImpact.AreaChange change : impact.getAreaChanges()) {
      if (change.getChangeType() != Dexpi20ProcessModelPackageRevisionImpact.ChangeType.UNCHANGED) {
        builder.changedAreaIds.add(change.getAreaId());
      }
    }
    for (Dexpi20ProcessModelPackageRevisionImpact.ConnectionChange change : impact.getConnectionChanges()) {
      if (change.getChangeType() != Dexpi20ProcessModelPackageRevisionImpact.ChangeType.UNCHANGED) {
        builder.changedConnectionIds.add(change.getConnectionId());
        Dexpi20ProcessModelPackageRevisionImpact.ConnectionState state = change.getRevised() == null
            ? change.getBaseline()
            : change.getRevised();
        if (state != null) {
          builder.changedConnectionTypes.add(state.getConnectionType());
        }
      }
    }
  }

  private static void collectChangedSheets(EngineeringDiagramDocumentSet baseline,
      EngineeringDiagramDocumentSet revised, Builder builder) {
    Map<String, SheetView> before = sheetsById(baseline);
    Map<String, SheetView> after = sheetsById(revised);
    Set<String> ids = new TreeSet<String>();
    ids.addAll(before.keySet());
    ids.addAll(after.keySet());
    for (String id : ids) {
      SheetView first = before.get(id);
      SheetView second = after.get(id);
      if (first == null || second == null || !first.signature.equals(second.signature)) {
        builder.affectedSheetIds.add(id);
        if (first != null) {
          builder.affectedDrawingIds.add(first.drawingId);
        } else if (second != null) {
          builder.affectedDrawingIds.add(second.drawingId);
        }
      }
    }
  }

  private static Map<String, SheetView> sheetsById(EngineeringDiagramDocumentSet document) {
    Map<String, SheetView> result = new TreeMap<String, SheetView>();
    for (Drawing drawing : document.getDrawings()) {
      for (Sheet sheet : drawing.getSheets()) {
        result.put(sheet.getId(), new SheetView(drawing.getId(), GSON.toJson(sheet)));
      }
    }
    return result;
  }

  private static void collectPackageViews(EngineeringDiagramDocumentSet document, Builder builder) {
    Set<String> foundAreas = new TreeSet<String>();
    Set<String> foundConnections = new TreeSet<String>();
    for (Drawing drawing : document.getDrawings()) {
      for (Sheet sheet : drawing.getSheets()) {
        boolean affected = false;
        for (String areaId : builder.changedAreaIds) {
          if (sheet.getAreaNodeIds().contains(areaId) || sheet.getObjectNodeIds().contains(areaId)) {
            foundAreas.add(areaId);
            affected = true;
          }
        }
        for (String connectionId : builder.changedConnectionIds) {
          if (sheet.getObjectNodeIds().contains(connectionId) || hasConnector(sheet, connectionId)) {
            foundConnections.add(connectionId);
            affected = true;
          }
        }
        if (affected) {
          builder.affectedSheetIds.add(sheet.getId());
          builder.affectedDrawingIds.add(drawing.getId());
        }
      }
    }
    builder.matchedAreaIds.addAll(foundAreas);
    builder.matchedConnectionIds.addAll(foundConnections);
  }

  private static boolean hasConnector(Sheet sheet, String connectionId) {
    for (OffPageConnector connector : sheet.getOffPageConnectors()) {
      if (connectionId.equals(connector.getSemanticConnectionId())) {
        return true;
      }
    }
    return false;
  }

  private static void collectRegisterImpact(EngineeringDiagramDocumentSet document, Builder builder) {
    Set<String> relevantObjects = new TreeSet<String>();
    relevantObjects.addAll(builder.changedAreaIds);
    relevantObjects.addAll(builder.changedConnectionIds);
    relevantObjects.addAll(builder.changedSemanticObjectIds);
    for (SemanticObject object : document.getSemanticObjects()) {
      if (relevantObjects.contains(object.getId()) && !object.getDesignations().isEmpty()) {
        builder.affectedDesignationObjectIds.add(object.getId());
      }
    }
    for (Drawing drawing : document.getDrawings()) {
      for (Sheet sheet : drawing.getSheets()) {
        if (!builder.affectedSheetIds.contains(sheet.getId())) {
          continue;
        }
        if (sheet.getManualDefinition() != null || !sheet.getManualAssignments().isEmpty()
            || !sheet.getPinnedPositions().isEmpty() || !sheet.getProtectedRoutes().isEmpty()) {
          builder.affectedLayoutSheetIds.add(sheet.getId());
        }
      }
    }
  }

  private static void completeScopes(Dexpi20ProcessModelPackageRevisionImpact impact, Builder builder) {
    builder.unmatchedAreaIds.addAll(builder.changedAreaIds);
    builder.unmatchedAreaIds.removeAll(builder.matchedAreaIds);
    builder.unmatchedConnectionIds.addAll(builder.changedConnectionIds);
    builder.unmatchedConnectionIds.removeAll(builder.matchedConnectionIds);
    if (impact.getStatus() == Dexpi20ProcessModelPackageRevisionImpact.Status.CHANGED) {
      builder.reviewScopes.add(ReviewScope.DEXPI_PACKAGE);
      builder.reviewScopes.add(ReviewScope.ENGINEERING_STUDY);
    }
    if (!builder.changedAreaIds.isEmpty()) {
      builder.reviewScopes.add(ReviewScope.DEXPI_AREA_EXCHANGE);
    }
    if (builder.documentSetContentChanged) {
      builder.reviewScopes.add(ReviewScope.CONTROLLED_DOCUMENT_SET);
      builder.reviewScopes.add(ReviewScope.ENGINEERING_STUDY);
    }
    if (!builder.affectedDrawingIds.isEmpty()) {
      builder.reviewScopes.add(ReviewScope.CONTROLLED_DRAWING);
    }
    if (!builder.affectedSheetIds.isEmpty()) {
      builder.reviewScopes.add(ReviewScope.CONTROLLED_SHEET);
    }
    if (!builder.affectedDesignationObjectIds.isEmpty()) {
      builder.reviewScopes.add(ReviewScope.DESIGNATION_REGISTER);
    }
    if (!builder.affectedLayoutSheetIds.isEmpty()) {
      builder.reviewScopes.add(ReviewScope.LAYOUT_REGISTER);
    }
    if (builder.changedConnectionTypes.contains("MATERIAL") || builder.changedConnectionTypes.contains("ENERGY")
        || !sameText(impact.getBaselineOperatingCaseId(), impact.getRevisedOperatingCaseId())) {
      builder.reviewScopes.add(ReviewScope.OPERATING_CASE_TABLES);
    }
    if (builder.changedConnectionTypes.contains("SIGNAL")) {
      builder.reviewScopes.add(ReviewScope.INFORMATION_REGISTER);
    }
  }

  private static boolean sameText(String first, String second) {
    return first == null ? second == null : first.equals(second);
  }

  private static List<String> immutableStrings(Iterable<String> values) {
    List<String> result = new ArrayList<String>();
    for (String value : values) {
      result.add(value);
    }
    Collections.sort(result);
    return Collections.unmodifiableList(result);
  }

  private static List<ReviewScope> immutableScopes(Iterable<ReviewScope> values) {
    List<ReviewScope> result = new ArrayList<ReviewScope>();
    for (ReviewScope value : values) {
      result.add(value);
    }
    Collections.sort(result, new Comparator<ReviewScope>() {
      @Override
      public int compare(ReviewScope first, ReviewScope second) {
        return first.name().compareTo(second.name());
      }
    });
    return Collections.unmodifiableList(result);
  }

  private static String fingerprint(Map<String, Object> map) {
    Map<String, Object> content = new LinkedHashMap<String, Object>(map);
    content.remove("fingerprint");
    String json = GSON.toJson(content);
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

  private static final class SheetView {
    private final String drawingId;
    private final String signature;

    private SheetView(String drawingId, String signature) {
      this.drawingId = drawingId;
      this.signature = signature;
    }
  }

  private static final class Builder {
    private final String plantId;
    private final String documentSetId;
    private final String baselinePackageFingerprint;
    private final String revisedPackageFingerprint;
    private final String baselineDocumentFingerprint;
    private final String revisedDocumentFingerprint;
    private final String baselineRevision;
    private final String revisedRevision;
    private final boolean documentSetContentChanged;
    private final Set<String> changedAreaIds = new TreeSet<String>();
    private final Set<String> changedConnectionIds = new TreeSet<String>();
    private final Set<String> changedConnectionTypes = new TreeSet<String>();
    private final Set<String> changedSemanticObjectIds = new TreeSet<String>();
    private final Set<String> affectedSheetIds = new TreeSet<String>();
    private final Set<String> affectedDrawingIds = new TreeSet<String>();
    private final Set<String> affectedDesignationObjectIds = new TreeSet<String>();
    private final Set<String> affectedLayoutSheetIds = new TreeSet<String>();
    private final Set<String> matchedAreaIds = new TreeSet<String>();
    private final Set<String> matchedConnectionIds = new TreeSet<String>();
    private final Set<String> unmatchedAreaIds = new TreeSet<String>();
    private final Set<String> unmatchedConnectionIds = new TreeSet<String>();
    private final Set<ReviewScope> reviewScopes = new TreeSet<ReviewScope>();

    private Builder(Dexpi20ProcessModelPackageRevisionImpact impact, EngineeringDiagramDocumentSet baseline,
        EngineeringDiagramDocumentSet revised) {
      plantId = impact.getPlantId();
      documentSetId = baseline.getId();
      baselinePackageFingerprint = impact.getBaselinePackageFileSha256();
      revisedPackageFingerprint = impact.getRevisedPackageFileSha256();
      baselineDocumentFingerprint = String.valueOf(baseline.toMap().get("fingerprint"));
      revisedDocumentFingerprint = String.valueOf(revised.toMap().get("fingerprint"));
      baselineRevision = impact.getBaselineRevision();
      revisedRevision = impact.getRevisedRevision();
      documentSetContentChanged = !baselineDocumentFingerprint.equals(revisedDocumentFingerprint);
    }
  }
}
