package neqsim.process.mechanicaldesign.subsea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Normalized STID tag-and-document JSON bridge that builds a {@link WellBarrierSchematic} from externally normalized
 * well-barrier evidence.
 *
 * <p>
 * This class does not connect to STID or document-management systems directly. Those systems should normalize their
 * well tag registers, barrier-element lists, and cross-referenced documents into JSON and pass it to this deterministic
 * Java adapter, so the resulting barrier schematic stays fully traceable and offline-reproducible. No live inference is
 * performed; the screening output is intended for human review.
 * </p>
 *
 * <p>
 * Expected JSON shape (all keys optional):
 * </p>
 *
 * <pre>
 * {
 *   "wellId": "WELL-A1",
 *   "wellType": "OIL_PRODUCER",
 *   "installationCode": "AAA",
 *   "annulusMonitoringRequired": true,
 *   "minPrimaryElements": 2,
 *   "minSecondaryElements": 2,
 *   "primaryEnvelope": {
 *     "name": "Primary",
 *     "elements": [
 *       { "type": "TUBING", "name": "Production Tubing", "status": "INTACT", "verified": true, "depthMD": 2500.0 },
 *       { "type": "DHSV", "name": "SCSSV", "status": "INTACT", "verified": true, "depthMD": 350.0 },
 *       { "type": "XMAS_TREE", "name": "Subsea Xmas Tree", "status": "INTACT", "verified": true }
 *     ]
 *   },
 *   "secondaryEnvelope": {
 *     "name": "Secondary",
 *     "elements": [
 *       { "type": "CASING", "name": "Production Casing", "status": "INTACT", "verified": true },
 *       { "type": "CEMENT", "name": "Casing Cement", "status": "INTACT", "verified": true },
 *       { "type": "WELLHEAD", "name": "Wellhead", "status": "INTACT", "verified": true }
 *     ]
 *   }
 * }
 * </pre>
 *
 * @author NeqSim contributors
 * @version 1.0
 * @see WellBarrierSchematic
 * @see WellIntegrityScreening
 */
public class StidWellBarrierDataSource {
  private static final Logger logger = LogManager.getLogger(StidWellBarrierDataSource.class);

  /** Keys interpreted as the primary envelope object. */
  public static final String[] PRIMARY_KEYS = new String[] { "primaryEnvelope", "primary" };

  /** Keys interpreted as the secondary envelope object. */
  public static final String[] SECONDARY_KEYS = new String[] { "secondaryEnvelope", "secondary" };

  /** Keys interpreted as the element array within an envelope. */
  public static final String[] ELEMENT_ARRAY_KEYS = new String[] { "elements", "barrierElements" };

  private final JsonObject source;

  /**
   * Creates a source from JSON text.
   *
   * @param json normalized STID tag-and-document JSON text
   */
  public StidWellBarrierDataSource(String json) {
    this(JsonParser.parseString(json).getAsJsonObject());
  }

  /**
   * Creates a source from a JSON object.
   *
   * @param source normalized JSON object, or null for an empty source
   */
  public StidWellBarrierDataSource(JsonObject source) {
    this.source = source == null ? new JsonObject() : source;
  }

  /**
   * Reads the optional well identifier used for traceability.
   *
   * @return well identifier, or empty string when not present
   */
  public String getWellId() {
    return optString(source, "wellId", "");
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
   * Builds a well barrier schematic from the normalized JSON.
   *
   * @return populated well barrier schematic (never null)
   */
  public WellBarrierSchematic read() {
    WellBarrierSchematic schematic = new WellBarrierSchematic();
    String wellType = optString(source, "wellType", "");
    if (!wellType.trim().isEmpty()) {
      schematic.setWellType(wellType);
    }

    int minPrimary = optInt(source, "minPrimaryElements", -1);
    int minSecondary = optInt(source, "minSecondaryElements", -1);
    if (minPrimary > 0 && minSecondary > 0) {
      schematic.setMinimumElements(minPrimary, minSecondary);
    }
    if (source.has("dhsvRequired") && !source.get("dhsvRequired").isJsonNull()) {
      schematic.setDhsvRequired(source.get("dhsvRequired").getAsBoolean());
    }
    if (source.has("isvRequired") && !source.get("isvRequired").isJsonNull()) {
      schematic.setIsvRequired(source.get("isvRequired").getAsBoolean());
    }
    if (source.has("annulusMonitoringRequired") && !source.get("annulusMonitoringRequired").isJsonNull()) {
      schematic.setAnnulusMonitoringRequired(source.get("annulusMonitoringRequired").getAsBoolean());
    }

    JsonObject primary = firstObject(source, PRIMARY_KEYS);
    if (primary != null) {
      schematic.setPrimaryEnvelope(buildEnvelope(primary, "Primary"));
    }
    JsonObject secondary = firstObject(source, SECONDARY_KEYS);
    if (secondary != null) {
      schematic.setSecondaryEnvelope(buildEnvelope(secondary, "Secondary"));
    }
    logger.info("Built well barrier schematic for {} ({})", getWellId(), wellType);
    return schematic;
  }

  /**
   * Builds a barrier envelope from a record.
   *
   * @param record envelope JSON object
   * @param defaultName fallback envelope name when none is supplied
   * @return populated barrier envelope
   */
  private BarrierEnvelope buildEnvelope(JsonObject record, String defaultName) {
    BarrierEnvelope envelope = new BarrierEnvelope(optString(record, "name", defaultName));
    JsonArray elements = firstArray(record, ELEMENT_ARRAY_KEYS);
    if (elements != null) {
      for (int i = 0; i < elements.size(); i++) {
        JsonElement element = elements.get(i);
        if (element.isJsonObject()) {
          envelope.addElement(buildElement(element.getAsJsonObject()));
        }
      }
    }
    return envelope;
  }

  /**
   * Builds a barrier element from a record.
   *
   * @param record element JSON object
   * @return populated barrier element
   */
  private BarrierElement buildElement(JsonObject record) {
    BarrierElement.ElementType type = parseElementType(optString(record, "type", ""));
    String name = optString(record, "name", type.name());
    double depthMD = record.has("depthMD") && !record.get("depthMD").isJsonNull() ? record.get("depthMD").getAsDouble()
        : 0.0;
    BarrierElement element = new BarrierElement(type, name, depthMD);
    element.setStatus(parseStatus(optString(record, "status", "")));
    if (record.has("verified") && !record.get("verified").isJsonNull()) {
      element.setVerified(record.get("verified").getAsBoolean());
    }
    return element;
  }

  /**
   * Parses a barrier element type, defaulting to {@code CASING} when unrecognized.
   *
   * @param raw raw type string
   * @return element type
   */
  private static BarrierElement.ElementType parseElementType(String raw) {
    if (raw == null || raw.trim().isEmpty()) {
      return BarrierElement.ElementType.CASING;
    }
    try {
      return BarrierElement.ElementType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return BarrierElement.ElementType.CASING;
    }
  }

  /**
   * Parses a barrier element status, defaulting to {@code UNKNOWN} when unrecognized.
   *
   * @param raw raw status string
   * @return element status
   */
  private static BarrierElement.Status parseStatus(String raw) {
    if (raw == null || raw.trim().isEmpty()) {
      return BarrierElement.Status.UNKNOWN;
    }
    try {
      return BarrierElement.Status.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return BarrierElement.Status.UNKNOWN;
    }
  }

  /**
   * Reads an optional string field with a default.
   *
   * @param object source object
   * @param key field key
   * @param fallback default value
   * @return field value, or fallback when missing or null
   */
  private static String optString(JsonObject object, String key, String fallback) {
    if (object != null && object.has(key) && !object.get(key).isJsonNull()) {
      return object.get(key).getAsString();
    }
    return fallback;
  }

  /**
   * Reads an optional integer field with a default.
   *
   * @param object source object
   * @param key field key
   * @param fallback default value
   * @return field value, or fallback when missing or null
   */
  private static int optInt(JsonObject object, String key, int fallback) {
    if (object != null && object.has(key) && !object.get(key).isJsonNull()) {
      return object.get(key).getAsInt();
    }
    return fallback;
  }

  /**
   * Returns the first matching object field among candidate keys.
   *
   * @param object source object
   * @param keys candidate keys
   * @return first matching object, or null when none present
   */
  private static JsonObject firstObject(JsonObject object, String[] keys) {
    for (int i = 0; i < keys.length; i++) {
      if (object.has(keys[i]) && object.get(keys[i]).isJsonObject()) {
        return object.getAsJsonObject(keys[i]);
      }
    }
    return null;
  }

  /**
   * Returns the first matching array field among candidate keys.
   *
   * @param object source object
   * @param keys candidate keys
   * @return first matching array, or null when none present
   */
  private static JsonArray firstArray(JsonObject object, String[] keys) {
    for (int i = 0; i < keys.length; i++) {
      if (object.has(keys[i]) && object.get(keys[i]).isJsonArray()) {
        return object.getAsJsonArray(keys[i]);
      }
    }
    return null;
  }
}
