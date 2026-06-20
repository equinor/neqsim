package neqsim.mcp.runners;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.mechanicaldesign.subsea.StidWellBarrierDataSource;
import neqsim.process.mechanicaldesign.subsea.WellBarrierSchematic;
import neqsim.process.mechanicaldesign.subsea.WellIntegrityScreening;

/**
 * Runner for offline well-integrity screening and well-barrier verification handoff generation.
 *
 * <p>
 * The runner accepts JSON normalized from STID tag-and-document evidence plus caller-supplied annulus readings, and
 * returns a screening view combining NORSOK D-010 two-barrier verification with API RP 90 annulus / sustained casing
 * pressure classification. It performs no live inference and makes no operational decision; the result is a screening
 * disposition intended for review by a competent well-integrity engineer.
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
 *   "primaryEnvelope": { "elements": [ ... ] },
 *   "secondaryEnvelope": { "elements": [ ... ] },
 *   "annuli": [
 *     { "id": "A", "measuredPressureBara": 35.0, "maaspBara": 80.0,
 *       "bleedsToZero": false, "rebuildsAfterBleed": true, "thermalEffectsExcluded": true }
 *   ]
 * }
 * </pre>
 *
 * @author NeqSim contributors
 * @version 1.0
 * @see WellBarrierSchematic
 * @see WellIntegrityScreening
 * @see StidWellBarrierDataSource
 */
public final class WellIntegrityRunner {
  /** Schema name and version for the emitted JSON. */
  public static final String SCHEMA = "neqsim.well_integrity.v1";

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor for utility class.
   */
  private WellIntegrityRunner() {
  }

  /**
   * Runs well-barrier verification and annulus screening, returning a JSON handoff.
   *
   * @param json normalized STID well-barrier JSON with optional annulus readings
   * @return JSON string with verification, annulus screening, and disposition blocks
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("JSON input is null or empty");
    }
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();

      StidWellBarrierDataSource dataSource = new StidWellBarrierDataSource(input);
      WellBarrierSchematic schematic = dataSource.read();
      boolean barrierPassed = schematic.validate();

      WellIntegrityScreening screening = new WellIntegrityScreening(dataSource.getWellId());
      screening.setWellType(optString(input, "wellType", schematic.getWellType()));
      screening.setBarrierSchematic(schematic);
      addAnnuli(input, screening);
      WellIntegrityScreening.IntegrityDisposition disposition = screening.screen();

      JsonObject out = new JsonObject();
      out.addProperty("schema", SCHEMA);
      out.addProperty("status", "success");
      out.addProperty("standard", "NORSOK D-010 Rev 5 / API RP 90 / ISO 16530");
      out.addProperty("wellId", dataSource.getWellId());
      out.addProperty("installationCode", dataSource.getInstallationCode());
      out.addProperty("reviewRequired", true);
      out.addProperty("disposition", disposition.name());

      JsonObject barrierVerification = new JsonObject();
      barrierVerification.addProperty("verificationPassed", barrierPassed);
      barrierVerification.addProperty("issueCount", schematic.getIssueCount());
      barrierVerification.add("issues", toJsonArray(schematic.getIssues()));
      barrierVerification.add("appliedStandards", toJsonArray(schematic.getAppliedStandards()));
      out.add("barrierVerification", barrierVerification);

      out.add("screening", GSON.toJsonTree(screening.toMap()));
      out.add("barrierSchematic", GSON.toJsonTree(schematic.toMap()));
      return GSON.toJson(out);
    } catch (Exception e) {
      return errorJson("Well integrity screening failed: " + e.getMessage());
    }
  }

  /**
   * Adds caller-supplied annulus readings to the screening.
   *
   * @param input top-level input object
   * @param screening target screening
   */
  private static void addAnnuli(JsonObject input, WellIntegrityScreening screening) {
    if (!input.has("annuli") || !input.get("annuli").isJsonArray()) {
      return;
    }
    JsonArray annuli = input.getAsJsonArray("annuli");
    for (int i = 0; i < annuli.size(); i++) {
      JsonElement element = annuli.get(i);
      if (!element.isJsonObject()) {
	continue;
      }
      JsonObject record = element.getAsJsonObject();
      String id = optString(record, "id", "A" + (i + 1));
      double measured = optDouble(record, "measuredPressureBara", 0.0);
      double maasp = optDouble(record, "maaspBara", 0.0);
      WellIntegrityScreening.AnnulusReading reading = new WellIntegrityScreening.AnnulusReading(id, measured, maasp);
      reading.setBleedsToZero(optBoolean(record, "bleedsToZero", false));
      reading.setRebuildsAfterBleed(optBoolean(record, "rebuildsAfterBleed", false));
      reading.setThermalEffectsExcluded(optBoolean(record, "thermalEffectsExcluded", true));
      screening.addAnnulus(reading);
    }
  }

  /**
   * Builds a JSON array from a list of strings.
   *
   * @param values string values
   * @return JSON array
   */
  private static JsonArray toJsonArray(java.util.List<String> values) {
    JsonArray array = new JsonArray();
    if (values != null) {
      for (int i = 0; i < values.size(); i++) {
	array.add(values.get(i));
      }
    }
    return array;
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
   * Reads an optional double field with a default.
   *
   * @param object source object
   * @param key field key
   * @param fallback default value
   * @return field value, or fallback when missing or null
   */
  private static double optDouble(JsonObject object, String key, double fallback) {
    if (object != null && object.has(key) && !object.get(key).isJsonNull()) {
      return object.get(key).getAsDouble();
    }
    return fallback;
  }

  /**
   * Reads an optional boolean field with a default.
   *
   * @param object source object
   * @param key field key
   * @param fallback default value
   * @return field value, or fallback when missing or null
   */
  private static boolean optBoolean(JsonObject object, String key, boolean fallback) {
    if (object != null && object.has(key) && !object.get(key).isJsonNull()) {
      return object.get(key).getAsBoolean();
    }
    return fallback;
  }

  /**
   * Builds a standardized error JSON response.
   *
   * @param message error message
   * @return JSON error string
   */
  private static String errorJson(String message) {
    JsonObject out = new JsonObject();
    out.addProperty("schema", SCHEMA);
    out.addProperty("status", "error");
    out.addProperty("message", message);
    return GSON.toJson(out);
  }
}
