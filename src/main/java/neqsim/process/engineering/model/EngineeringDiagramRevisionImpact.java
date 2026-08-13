package neqsim.process.engineering.model;

import com.google.gson.Gson;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Deterministic semantic-object, sheet, and drawing impact between two controlled diagram revisions. */
public final class EngineeringDiagramRevisionImpact implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Gson GSON = new Gson();
  public static final String SCHEMA_VERSION = "neqsim_engineering_diagram_revision_impact.v1";

  /** Comparison outcome. */
  public enum Status {
    /** No semantic-object content changed. */
    UNCHANGED,
    /** At least one semantic object was added, removed, or modified. */
    CHANGED
  }

  private final String documentSetId;
  private final String plantId;
  private final String baselineRevision;
  private final String revisedRevision;
  private final String baselineFingerprint;
  private final String revisedFingerprint;
  private final Status status;
  private final List<String> addedSemanticObjectIds;
  private final List<String> removedSemanticObjectIds;
  private final List<String> modifiedSemanticObjectIds;
  private final List<String> affectedSheetIds;
  private final List<String> affectedDrawingIds;

  private EngineeringDiagramRevisionImpact(EngineeringDiagramDocumentSet baseline,
      EngineeringDiagramDocumentSet revised, List<String> addedIds, List<String> removedIds, List<String> modifiedIds,
      List<String> sheetIds, List<String> drawingIds) {
    this.documentSetId = baseline.getId();
    this.plantId = baseline.getPlantId();
    this.baselineRevision = baseline.getRevision();
    this.revisedRevision = revised.getRevision();
    this.baselineFingerprint = String.valueOf(baseline.toMap().get("fingerprint"));
    this.revisedFingerprint = String.valueOf(revised.toMap().get("fingerprint"));
    this.addedSemanticObjectIds = immutableStrings(addedIds);
    this.removedSemanticObjectIds = immutableStrings(removedIds);
    this.modifiedSemanticObjectIds = immutableStrings(modifiedIds);
    this.affectedSheetIds = immutableStrings(sheetIds);
    this.affectedDrawingIds = immutableStrings(drawingIds);
    this.status = addedIds.isEmpty() && removedIds.isEmpty() && modifiedIds.isEmpty() ? Status.UNCHANGED
        : Status.CHANGED;
  }

  /**
   * Compares two revisions of the same controlled diagram document set.
   *
   * <p>
   * Impact is based on canonical semantic-object content, including reviewed designations. Added, removed, and modified
   * object identities are projected to every sheet and drawing that contains the object in either revision.
   * </p>
   *
   * @param baseline controlled baseline revision
   * @param revised controlled revised revision
   * @return deterministic revision impact
   */
  public static EngineeringDiagramRevisionImpact compare(EngineeringDiagramDocumentSet baseline,
      EngineeringDiagramDocumentSet revised) {
    if (baseline == null || revised == null) {
      throw new IllegalArgumentException("baseline and revised must not be null");
    }
    if (!baseline.getId().equals(revised.getId()) || !baseline.getPlantId().equals(revised.getPlantId())) {
      throw new IllegalArgumentException("Revision impact requires the same document-set and plant identity");
    }
    Map<String, EngineeringDiagramDocumentSet.SemanticObject> baselineObjects = semanticObjectsById(baseline);
    Map<String, EngineeringDiagramDocumentSet.SemanticObject> revisedObjects = semanticObjectsById(revised);
    Set<String> allIds = new TreeSet<String>();
    allIds.addAll(baselineObjects.keySet());
    allIds.addAll(revisedObjects.keySet());
    List<String> added = new ArrayList<String>();
    List<String> removed = new ArrayList<String>();
    List<String> modified = new ArrayList<String>();
    for (String objectId : allIds) {
      EngineeringDiagramDocumentSet.SemanticObject before = baselineObjects.get(objectId);
      EngineeringDiagramDocumentSet.SemanticObject after = revisedObjects.get(objectId);
      if (before == null) {
        added.add(objectId);
      } else if (after == null) {
        removed.add(objectId);
      } else if (!GSON.toJson(before.toMap()).equals(GSON.toJson(after.toMap()))) {
        modified.add(objectId);
      }
    }
    Set<String> changedIds = new TreeSet<String>();
    changedIds.addAll(added);
    changedIds.addAll(removed);
    changedIds.addAll(modified);
    Set<String> sheets = new TreeSet<String>();
    Set<String> drawings = new TreeSet<String>();
    collectAffectedViews(baseline, changedIds, sheets, drawings);
    collectAffectedViews(revised, changedIds, sheets, drawings);
    return new EngineeringDiagramRevisionImpact(baseline, revised, added, removed, modified,
        new ArrayList<String>(sheets), new ArrayList<String>(drawings));
  }

  public String getDocumentSetId() {
    return documentSetId;
  }

  public String getPlantId() {
    return plantId;
  }

  public String getBaselineRevision() {
    return baselineRevision;
  }

  public String getRevisedRevision() {
    return revisedRevision;
  }

  public String getBaselineFingerprint() {
    return baselineFingerprint;
  }

  public String getRevisedFingerprint() {
    return revisedFingerprint;
  }

  public Status getStatus() {
    return status;
  }

  public List<String> getAddedSemanticObjectIds() {
    return immutableStrings(addedSemanticObjectIds);
  }

  public List<String> getRemovedSemanticObjectIds() {
    return immutableStrings(removedSemanticObjectIds);
  }

  public List<String> getModifiedSemanticObjectIds() {
    return immutableStrings(modifiedSemanticObjectIds);
  }

  public List<String> getAffectedSheetIds() {
    return immutableStrings(affectedSheetIds);
  }

  public List<String> getAffectedDrawingIds() {
    return immutableStrings(affectedDrawingIds);
  }

  /** @return deterministic machine-readable revision-impact representation */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", SCHEMA_VERSION);
    result.put("documentSetId", documentSetId);
    result.put("plantId", plantId);
    result.put("baselineRevision", baselineRevision);
    result.put("revisedRevision", revisedRevision);
    result.put("baselineFingerprint", baselineFingerprint);
    result.put("revisedFingerprint", revisedFingerprint);
    result.put("status", status.name());
    result.put("addedSemanticObjectIds", immutableStrings(addedSemanticObjectIds));
    result.put("removedSemanticObjectIds", immutableStrings(removedSemanticObjectIds));
    result.put("modifiedSemanticObjectIds", immutableStrings(modifiedSemanticObjectIds));
    result.put("affectedSheetIds", immutableStrings(affectedSheetIds));
    result.put("affectedDrawingIds", immutableStrings(affectedDrawingIds));
    result.put("fingerprint", fingerprint(result));
    return result;
  }

  /** @return pretty-printed deterministic JSON */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  private static Map<String, EngineeringDiagramDocumentSet.SemanticObject> semanticObjectsById(
      EngineeringDiagramDocumentSet set) {
    Map<String, EngineeringDiagramDocumentSet.SemanticObject> result = new TreeMap<String, EngineeringDiagramDocumentSet.SemanticObject>();
    for (EngineeringDiagramDocumentSet.SemanticObject object : set.getSemanticObjects()) {
      result.put(object.getId(), object);
    }
    return result;
  }

  private static void collectAffectedViews(EngineeringDiagramDocumentSet set, Set<String> objectIds,
      Set<String> sheetIds, Set<String> drawingIds) {
    for (EngineeringDiagramDocumentSet.Drawing drawing : set.getDrawings()) {
      boolean drawingAffected = false;
      for (EngineeringDiagramDocumentSet.Sheet sheet : drawing.getSheets()) {
        if (!Collections.disjoint(sheet.getObjectNodeIds(), objectIds)) {
          sheetIds.add(sheet.getId());
          drawingAffected = true;
        }
      }
      if (drawingAffected) {
        drawingIds.add(drawing.getId());
      }
    }
  }

  private static List<String> immutableStrings(List<String> values) {
    return Collections.unmodifiableList(new ArrayList<String>(values));
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
}
