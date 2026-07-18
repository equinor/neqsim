package neqsim.process.processmodel.lifecycle.event;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import neqsim.process.engineering.model.EngineeringGraphDiff;
import neqsim.process.processmodel.lifecycle.event.ModelChangeSubject.ChangeKind;
import neqsim.process.processmodel.lifecycle.event.ModelChangeSubject.SubjectType;

/** Versioned, deterministic and transport-neutral notification that a governed model changed. */
public final class ModelChangeEvent implements Serializable {
  private static final long serialVersionUID = 1000L;
  public static final String SCHEMA_VERSION = "neqsim_model_change_event.v1";
  public static final String SCHEMA_URI = "urn:neqsim:schema:model-change-event:v1";
  private static final Gson COMPACT_GSON = new GsonBuilder().create();
  private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

  public enum EventType {
    MODEL_CREATED, MODEL_REVISED, DEPENDENCY_CHANGED, QUALIFICATION_CHANGED, MODEL_ARCHIVED
  }

  private final String eventId;
  private final String idempotencyKey;
  private final EventType eventType;
  private final Instant occurredAt;
  private final String sourceSystem;
  private final String actor;
  private final String correlationId;
  private final String assetId;
  private final String modelId;
  private final String baseRevision;
  private final String targetRevision;
  private final String reason;
  private final List<ModelChangeSubject> subjects;
  private final List<String> impactHintNodeIds;
  private final List<String> evidenceReferences;
  private final String payloadFingerprint;

  public ModelChangeEvent(String eventId, String idempotencyKey, EventType eventType, Instant occurredAt,
      String sourceSystem, String actor, String correlationId, String assetId, String modelId, String baseRevision,
      String targetRevision, String reason, List<ModelChangeSubject> subjects, List<String> impactHintNodeIds,
      List<String> evidenceReferences) {
    this.eventId = requireText(eventId, "eventId");
    this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
    if (eventType == null) {
      throw new IllegalArgumentException("eventType must not be null");
    }
    if (occurredAt == null) {
      throw new IllegalArgumentException("occurredAt must not be null");
    }
    this.eventType = eventType;
    this.occurredAt = occurredAt;
    this.sourceSystem = requireText(sourceSystem, "sourceSystem");
    this.actor = requireText(actor, "actor");
    this.correlationId = text(correlationId);
    this.assetId = requireText(assetId, "assetId");
    this.modelId = requireText(modelId, "modelId");
    this.baseRevision = text(baseRevision);
    this.targetRevision = requireText(targetRevision, "targetRevision");
    this.reason = requireText(reason, "reason");
    this.subjects = sortedSubjects(subjects);
    this.impactHintNodeIds = sortedStrings(impactHintNodeIds, "impactHintNodeId");
    this.evidenceReferences = sortedStrings(evidenceReferences, "evidenceReference");
    payloadFingerprint = fingerprint(payloadMap());
  }

  /** Converts a canonical engineering graph difference to a model-revision event. */
  public static ModelChangeEvent fromEngineeringGraphDiff(EngineeringGraphDiff diff, String eventId,
      String idempotencyKey, Instant occurredAt, String sourceSystem, String actor, String assetId, String modelId,
      String reason) {
    if (diff == null) {
      throw new IllegalArgumentException("diff must not be null");
    }
    List<ModelChangeSubject> subjects = new ArrayList<ModelChangeSubject>();
    addSubjects(subjects, diff.getAddedNodeIds(), SubjectType.ENGINEERING_NODE, ChangeKind.ADDED);
    addSubjects(subjects, diff.getModifiedNodeIds(), SubjectType.ENGINEERING_NODE, ChangeKind.MODIFIED);
    addSubjects(subjects, diff.getRemovedNodeIds(), SubjectType.ENGINEERING_NODE, ChangeKind.REMOVED);
    addSubjects(subjects, diff.getAddedEdgeIds(), SubjectType.ENGINEERING_EDGE, ChangeKind.ADDED);
    addSubjects(subjects, diff.getRemovedEdgeIds(), SubjectType.ENGINEERING_EDGE, ChangeKind.REMOVED);
    return new ModelChangeEvent(eventId, idempotencyKey, EventType.MODEL_REVISED, occurredAt, sourceSystem, actor, "",
        assetId, modelId, diff.getFromRevision(), diff.getToRevision(), reason, subjects,
        new ArrayList<String>(diff.getImpactedNodeIds()), Collections.<String>emptyList());
  }

  /** Parses and verifies a v1 event, including its content fingerprint. */
  public static ModelChangeEvent fromJson(String json) {
    if (json == null || json.trim().isEmpty()) {
      throw new IllegalArgumentException("json must not be blank");
    }
    Map<String, Object> root = COMPACT_GSON.fromJson(json, new TypeToken<Map<String, Object>>() {
    }.getType());
    if (root == null || !SCHEMA_VERSION.equals(String.valueOf(root.get("schemaVersion")))) {
      throw new IllegalArgumentException("Unsupported model-change event schema version");
    }
    if (!SCHEMA_URI.equals(String.valueOf(root.get("schemaUri")))) {
      throw new IllegalArgumentException("Unsupported model-change event schema URI");
    }
    List<ModelChangeSubject> subjects = new ArrayList<ModelChangeSubject>();
    for (Map<String, Object> value : maps(root.get("subjects"), "subjects")) {
      subjects.add(ModelChangeSubject.fromMap(value));
    }
    ModelChangeEvent result = new ModelChangeEvent(required(root, "eventId"), required(root, "idempotencyKey"),
        EventType.valueOf(required(root, "eventType")), Instant.parse(required(root, "occurredAt")),
        required(root, "sourceSystem"), required(root, "actor"), optional(root, "correlationId"),
        required(root, "assetId"), required(root, "modelId"), optional(root, "baseRevision"),
        required(root, "targetRevision"), required(root, "reason"), subjects,
        stringList(root.get("impactHintNodeIds"), "impactHintNodeIds"),
        stringList(root.get("evidenceReferences"), "evidenceReferences"));
    if (!result.getPayloadFingerprint().equals(required(root, "payloadFingerprint"))) {
      throw new IllegalArgumentException("Model-change event fingerprint does not match its payload");
    }
    return result;
  }

  public String getEventId() {
    return eventId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public EventType getEventType() {
    return eventType;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public String getSourceSystem() {
    return sourceSystem;
  }

  public String getActor() {
    return actor;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public String getAssetId() {
    return assetId;
  }

  public String getModelId() {
    return modelId;
  }

  public String getBaseRevision() {
    return baseRevision;
  }

  public String getTargetRevision() {
    return targetRevision;
  }

  public String getReason() {
    return reason;
  }

  public List<ModelChangeSubject> getSubjects() {
    return Collections.unmodifiableList(subjects);
  }

  public List<String> getImpactHintNodeIds() {
    return Collections.unmodifiableList(impactHintNodeIds);
  }

  public List<String> getEvidenceReferences() {
    return Collections.unmodifiableList(evidenceReferences);
  }

  public String getPayloadFingerprint() {
    return payloadFingerprint;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = payloadMap();
    result.put("payloadFingerprint", payloadFingerprint);
    return result;
  }

  public String toJson() {
    return PRETTY_GSON.toJson(toMap());
  }

  public String toCompactJson() {
    return COMPACT_GSON.toJson(toMap());
  }

  private Map<String, Object> payloadMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", SCHEMA_VERSION);
    result.put("schemaUri", SCHEMA_URI);
    result.put("eventId", eventId);
    result.put("idempotencyKey", idempotencyKey);
    result.put("eventType", eventType.name());
    result.put("occurredAt", occurredAt.toString());
    result.put("sourceSystem", sourceSystem);
    result.put("actor", actor);
    result.put("correlationId", correlationId);
    result.put("assetId", assetId);
    result.put("modelId", modelId);
    result.put("baseRevision", baseRevision);
    result.put("targetRevision", targetRevision);
    result.put("reason", reason);
    List<Map<String, Object>> subjectMaps = new ArrayList<Map<String, Object>>();
    for (ModelChangeSubject subject : subjects) {
      subjectMaps.add(subject.toMap());
    }
    result.put("subjects", subjectMaps);
    result.put("impactHintNodeIds", new ArrayList<String>(impactHintNodeIds));
    result.put("evidenceReferences", new ArrayList<String>(evidenceReferences));
    result.put("governance", "An event reports change; it does not approve the changed engineering model");
    return result;
  }

  private static void addSubjects(List<ModelChangeSubject> result, List<String> ids, SubjectType subjectType,
      ChangeKind changeKind) {
    for (String id : ids) {
      result.add(new ModelChangeSubject(id, subjectType, kindFromId(id, subjectType), changeKind,
          Collections.<String>emptyList()));
    }
  }

  private static String kindFromId(String id, SubjectType type) {
    String[] parts = id.split(":", 3);
    if (type == SubjectType.ENGINEERING_EDGE && parts.length > 1) {
      return parts[1].toUpperCase();
    }
    return parts.length > 0 ? parts[0].toUpperCase() : "UNKNOWN";
  }

  private static List<ModelChangeSubject> sortedSubjects(List<ModelChangeSubject> values) {
    if (values == null || values.isEmpty()) {
      throw new IllegalArgumentException("subjects must not be empty");
    }
    List<ModelChangeSubject> result = new ArrayList<ModelChangeSubject>(values);
    if (result.contains(null)) {
      throw new IllegalArgumentException("subjects must not contain null");
    }
    Collections.sort(result);
    return result;
  }

  private static List<String> sortedStrings(List<String> values, String field) {
    List<String> result = values == null ? new ArrayList<String>() : new ArrayList<String>(values);
    for (int i = 0; i < result.size(); i++) {
      result.set(i, requireText(result.get(i), field));
    }
    Collections.sort(result);
    return result;
  }

  @SuppressWarnings("unchecked")
  static List<String> stringList(Object value, String field) {
    if (!(value instanceof List)) {
      throw new IllegalArgumentException(field + " must be an array");
    }
    List<String> result = new ArrayList<String>();
    for (Object item : (List<Object>) value) {
      result.add(requireText(item == null ? null : String.valueOf(item), field + " item"));
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> maps(Object value, String field) {
    if (!(value instanceof List)) {
      throw new IllegalArgumentException(field + " must be an array");
    }
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (Object item : (List<Object>) value) {
      if (!(item instanceof Map)) {
        throw new IllegalArgumentException(field + " item must be an object");
      }
      result.add((Map<String, Object>) item);
    }
    return result;
  }

  private static String required(Map<String, Object> value, String field) {
    return requireText(value.get(field) == null ? null : String.valueOf(value.get(field)), field);
  }

  private static String optional(Map<String, Object> value, String field) {
    return value.get(field) == null ? "" : String.valueOf(value.get(field));
  }

  private static String fingerprint(Map<String, Object> value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(COMPACT_GSON.toJson(value).getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (byte item : hash) {
        result.append(String.format("%02x", Integer.valueOf(item & 0xff)));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
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
}
