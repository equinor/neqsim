package neqsim.process.safety.barrier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Locale;

/**
 * Normalized STID tag-and-document JSON bridge that builds a {@link BarrierRegister} from externally normalized
 * evidence.
 *
 * <p>
 * This class does not connect to STID or document-management systems directly. Those systems should normalize their tag
 * registers, safety-critical-element lists, and cross-referenced documents into JSON and pass it to this deterministic
 * Java adapter so the resulting barrier register and its document evidence stay fully traceable.
 * </p>
 *
 * <p>
 * Expected JSON shape (all keys optional):
 * </p>
 *
 * <pre>
 * {
 *   "registerId": "REG-001",
 *   "name": "Example installation barrier register",
 *   "installationCode": "AAA",
 *   "safetyCriticalElements": [
 *     {
 *       "id": "SCE-PSD",
 *       "tag": "PSD",
 *       "name": "Process shutdown",
 *       "type": "INSTRUMENTED_FUNCTION",
 *       "equipmentTags": ["VA-2001"],
 *       "barriers": [ { "id": "B-PSD-2001", "status": "AVAILABLE", "pfd": 0.01 } ]
 *     }
 *   ],
 *   "barriers": [
 *     {
 *       "id": "B-PSV-2001",
 *       "name": "PSV on inlet separator",
 *       "type": "MITIGATION",
 *       "status": "AVAILABLE",
 *       "equipmentTags": ["VA-2001"],
 *       "evidence": [ { "documentId": "PSV-LIST", "installationCode": "AAA", "excerpt": "PSV-2001 set 75 barg" } ]
 *     }
 *   ]
 * }
 * </pre>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class StidBarrierRegisterDataSource {
  /** Top-level array keys interpreted as safety critical elements. */
  public static final String[] SCE_ARRAY_KEYS = new String[] { "safetyCriticalElements", "sces", "elements" };

  /** Top-level array keys interpreted as standalone barriers. */
  public static final String[] BARRIER_ARRAY_KEYS = new String[] { "barriers", "safetyBarriers" };

  /** Top-level array keys interpreted as register-level document evidence. */
  public static final String[] EVIDENCE_ARRAY_KEYS = new String[] { "evidence", "documents", "documentEvidence" };

  private final JsonObject source;

  /**
   * Creates a source from JSON text.
   *
   * @param json normalized STID tag-and-document JSON text
   */
  public StidBarrierRegisterDataSource(String json) {
    this(JsonParser.parseString(json).getAsJsonObject());
  }

  /**
   * Creates a source from a JSON object.
   *
   * @param source normalized JSON object, or null for an empty source
   */
  public StidBarrierRegisterDataSource(JsonObject source) {
    this.source = source == null ? new JsonObject() : source;
  }

  /**
   * Reads the optional installation code used for traceability.
   *
   * @return installation code, or empty string when not present
   */
  public String getInstallationCode() {
    return optString(source, "installationCode", "");
  }

  /**
   * Builds a barrier register from the normalized JSON.
   *
   * @return populated barrier register (never null)
   */
  public BarrierRegister read() {
    String registerId = optString(source, "registerId", "REG-001");
    BarrierRegister register = new BarrierRegister(registerId);
    register.setName(optString(source, "name", ""));
    String installationCode = getInstallationCode();

    JsonArray sces = firstArray(source, SCE_ARRAY_KEYS);
    if (sces != null) {
      for (int i = 0; i < sces.size(); i++) {
	JsonElement element = sces.get(i);
	if (element.isJsonObject()) {
	  register.addSafetyCriticalElement(buildSce(element.getAsJsonObject(), installationCode));
	}
      }
    }

    JsonArray barriers = firstArray(source, BARRIER_ARRAY_KEYS);
    if (barriers != null) {
      for (int i = 0; i < barriers.size(); i++) {
	JsonElement element = barriers.get(i);
	if (element.isJsonObject()) {
	  register.addBarrier(buildBarrier(element.getAsJsonObject(), installationCode));
	}
      }
    }

    JsonArray evidence = firstArray(source, EVIDENCE_ARRAY_KEYS);
    if (evidence != null) {
      for (int i = 0; i < evidence.size(); i++) {
	JsonElement element = evidence.get(i);
	if (element.isJsonObject()) {
	  register.addEvidence(buildEvidence(element.getAsJsonObject(), installationCode, i));
	}
      }
    }
    return register;
  }

  /**
   * Builds a safety critical element from a record.
   *
   * @param record SCE JSON object
   * @param installationCode default installation code for traceability
   * @return populated safety critical element
   */
  private SafetyCriticalElement buildSce(JsonObject record, String installationCode) {
    String id = optString(record, "id", optString(record, "tag", "SCE"));
    SafetyCriticalElement sce = new SafetyCriticalElement(id);
    sce.setTag(optString(record, "tag", ""));
    sce.setName(optString(record, "name", ""));
    sce.setOwner(optString(record, "owner", ""));
    sce.setType(parseElementType(optString(record, "type", "")));
    addTags(record, sce);
    JsonArray barriers = firstArray(record, BARRIER_ARRAY_KEYS);
    if (barriers != null) {
      for (int i = 0; i < barriers.size(); i++) {
	JsonElement element = barriers.get(i);
	if (element.isJsonObject()) {
	  sce.addBarrier(buildBarrier(element.getAsJsonObject(), installationCode));
	}
      }
    }
    JsonArray evidence = firstArray(record, EVIDENCE_ARRAY_KEYS);
    if (evidence != null) {
      for (int i = 0; i < evidence.size(); i++) {
	JsonElement element = evidence.get(i);
	if (element.isJsonObject()) {
	  sce.addEvidence(buildEvidence(element.getAsJsonObject(), installationCode, i));
	}
      }
    }
    return sce;
  }

  /**
   * Builds a safety barrier from a record.
   *
   * @param record barrier JSON object
   * @param installationCode default installation code for traceability
   * @return populated safety barrier
   */
  private SafetyBarrier buildBarrier(JsonObject record, String installationCode) {
    String id = optString(record, "id", "BARRIER");
    SafetyBarrier barrier = new SafetyBarrier(id);
    barrier.setName(optString(record, "name", ""));
    barrier.setDescription(optString(record, "description", ""));
    barrier.setSafetyFunction(optString(record, "safetyFunction", ""));
    barrier.setOwner(optString(record, "owner", ""));
    barrier.setType(parseBarrierType(optString(record, "type", "")));
    barrier.setStatus(parseBarrierStatus(optString(record, "status", "")));
    if (record.has("pfd") && !record.get("pfd").isJsonNull()) {
      barrier.setPfd(record.get("pfd").getAsDouble());
    } else if (record.has("effectiveness") && !record.get("effectiveness").isJsonNull()) {
      barrier.setEffectiveness(record.get("effectiveness").getAsDouble());
    }
    addBarrierTags(record, barrier);
    addHazardIds(record, barrier);
    JsonArray evidence = firstArray(record, EVIDENCE_ARRAY_KEYS);
    if (evidence != null) {
      for (int i = 0; i < evidence.size(); i++) {
	JsonElement element = evidence.get(i);
	if (element.isJsonObject()) {
	  barrier.addEvidence(buildEvidence(element.getAsJsonObject(), installationCode, i));
	}
      }
    }
    return barrier;
  }

  /**
   * Builds a document evidence record, inheriting the register installation code when none is given.
   *
   * @param record evidence JSON object
   * @param defaultInstallationCode register-level installation code
   * @param index 0-based index used for default identifiers
   * @return populated document evidence
   */
  private DocumentEvidence buildEvidence(JsonObject record, String defaultInstallationCode, int index) {
    String evidenceId = optString(record, "evidenceId", "EV-" + (index + 1));
    String documentId = optString(record, "documentId", "");
    String documentTitle = optString(record, "documentTitle", "");
    String revision = optString(record, "revision", "");
    String section = optString(record, "section", "");
    int page = record.has("page") && !record.get("page").isJsonNull() ? record.get("page").getAsInt() : 0;
    String sourceReference = optString(record, "sourceReference", "");
    String excerpt = optString(record, "excerpt", "");
    double confidence = record.has("confidence") && !record.get("confidence").isJsonNull()
	? record.get("confidence").getAsDouble()
	: 1.0;
    String installationCode = optString(record, "installationCode", defaultInstallationCode);
    return new DocumentEvidence(evidenceId, documentId, documentTitle, revision, section, page, sourceReference,
	excerpt, confidence, installationCode);
  }

  /**
   * Adds equipment tags to an SCE from a record.
   *
   * @param record source object
   * @param sce target SCE
   */
  private static void addTags(JsonObject record, SafetyCriticalElement sce) {
    if (record.has("equipmentTags") && record.get("equipmentTags").isJsonArray()) {
      JsonArray tags = record.getAsJsonArray("equipmentTags");
      for (int i = 0; i < tags.size(); i++) {
	if (!tags.get(i).isJsonNull()) {
	  sce.addEquipmentTag(tags.get(i).getAsString());
	}
      }
    }
  }

  /**
   * Adds equipment tags to a barrier from a record.
   *
   * @param record source object
   * @param barrier target barrier
   */
  private static void addBarrierTags(JsonObject record, SafetyBarrier barrier) {
    if (record.has("equipmentTags") && record.get("equipmentTags").isJsonArray()) {
      JsonArray tags = record.getAsJsonArray("equipmentTags");
      for (int i = 0; i < tags.size(); i++) {
	if (!tags.get(i).isJsonNull()) {
	  barrier.addEquipmentTag(tags.get(i).getAsString());
	}
      }
    }
  }

  /**
   * Adds hazard identifiers to a barrier from a record.
   *
   * @param record source object
   * @param barrier target barrier
   */
  private static void addHazardIds(JsonObject record, SafetyBarrier barrier) {
    if (record.has("hazardIds") && record.get("hazardIds").isJsonArray()) {
      JsonArray hazards = record.getAsJsonArray("hazardIds");
      for (int i = 0; i < hazards.size(); i++) {
	if (!hazards.get(i).isJsonNull()) {
	  barrier.addHazardId(hazards.get(i).getAsString());
	}
      }
    }
  }

  /**
   * Parses a barrier type, tolerant to spacing, case, and underscores.
   *
   * @param raw raw type text
   * @return matching barrier type, defaulting to PREVENTION
   */
  static SafetyBarrier.BarrierType parseBarrierType(String raw) {
    String key = normalizeToken(raw);
    for (SafetyBarrier.BarrierType value : SafetyBarrier.BarrierType.values()) {
      if (value.name().equals(key)) {
	return value;
      }
    }
    return SafetyBarrier.BarrierType.PREVENTION;
  }

  /**
   * Parses a barrier status, tolerant to spacing, case, and underscores.
   *
   * @param raw raw status text
   * @return matching barrier status, defaulting to UNKNOWN
   */
  static SafetyBarrier.BarrierStatus parseBarrierStatus(String raw) {
    String key = normalizeToken(raw);
    for (SafetyBarrier.BarrierStatus value : SafetyBarrier.BarrierStatus.values()) {
      if (value.name().equals(key)) {
	return value;
      }
    }
    return SafetyBarrier.BarrierStatus.UNKNOWN;
  }

  /**
   * Parses an SCE element type, tolerant to spacing, case, and underscores.
   *
   * @param raw raw type text
   * @return matching element type, defaulting to OTHER
   */
  static SafetyCriticalElement.ElementType parseElementType(String raw) {
    String key = normalizeToken(raw);
    for (SafetyCriticalElement.ElementType value : SafetyCriticalElement.ElementType.values()) {
      if (value.name().equals(key)) {
	return value;
      }
    }
    return SafetyCriticalElement.ElementType.OTHER;
  }

  /**
   * Normalizes an enum-like token to upper-case with underscores.
   *
   * @param raw raw token text
   * @return normalized token, or empty string when null/blank
   */
  private static String normalizeToken(String raw) {
    if (raw == null) {
      return "";
    }
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) {
      return "";
    }
    return trimmed.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
  }

  /**
   * Returns the first JSON array present under any of the candidate keys.
   *
   * @param object source object
   * @param keys candidate array keys
   * @return first matching JSON array, or null when none present
   */
  private static JsonArray firstArray(JsonObject object, String[] keys) {
    for (String key : keys) {
      if (object.has(key) && object.get(key).isJsonArray()) {
	return object.getAsJsonArray(key);
      }
    }
    return null;
  }

  /**
   * Reads an optional string field with a fallback default.
   *
   * @param object source object
   * @param key field key
   * @param fallback default value when the field is absent or null
   * @return field value, or the fallback
   */
  private static String optString(JsonObject object, String key, String fallback) {
    if (object.has(key) && !object.get(key).isJsonNull()) {
      return object.get(key).getAsString();
    }
    return fallback;
  }
}
