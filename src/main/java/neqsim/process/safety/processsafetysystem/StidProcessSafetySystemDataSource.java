package neqsim.process.safety.processsafetysystem;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalized STID, C&amp;E, SRS, PSV list, and tagreader JSON bridge for Clause 10 reviews.
 *
 * <p>
 * This class does not connect to STID, document-management systems, or historians directly. Those
 * systems should normalize evidence into JSON and pass it to this deterministic Java adapter.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class StidProcessSafetySystemDataSource {
  /** Top-level arrays interpreted as process safety review items. */
  public static final String[] REVIEW_ARRAY_KEYS = new String[] {"items", "processSafetyFunctions",
      "safetyFunctions", "psdValves", "shutdownValves", "psvs", "psvValves", "alarms",
      "alarmActions", "sifs", "safetyInstrumentedFunctions", "secondaryPressureProtection",
      "utilityDependencies", "survivabilityItems", "logicSolvers", "causeAndEffectActions",
      "instrumentData", "tagreaderEvidence"};

  private final JsonObject source;

  /**
   * Creates a source from JSON text.
   *
   * @param json normalized STID, C&amp;E, SRS, PSV, or tagreader JSON text
   */
  public StidProcessSafetySystemDataSource(String json) {
    this(JsonParser.parseString(json).getAsJsonObject());
  }

  /**
   * Creates a source from a JSON object.
   *
   * @param source normalized JSON object
   */
  public StidProcessSafetySystemDataSource(JsonObject source) {
    this.source = source == null ? new JsonObject() : source;
  }

  /**
   * Reads normalized review input.
   *
   * @return process safety system review input
   */
  public ProcessSafetySystemReviewInput read() {
    ProcessSafetySystemReviewInput input = new ProcessSafetySystemReviewInput();
    if (source.has("projectName")) {
      input.setProjectName(source.get("projectName").getAsString());
    }
    for (String key : REVIEW_ARRAY_KEYS) {
      addArray(input, key);
    }
    return input;
  }

  /**
   * Infers a function type from the source array key.
   *
   * @param sourceKey source array key
   * @return inferred function type, or empty string when unknown
   */
  public static String inferFunctionType(String sourceKey) {
    String key = sourceKey == null ? "" : sourceKey.toLowerCase();
    if (key.contains("psd") || key.contains("shutdown")) {
      return "PSD";
    }
    if (key.contains("psv")) {
      return "PSV";
    }
    if (key.contains("alarm")) {
      return "ALARM";
    }
    if (key.contains("sif") || key.contains("logic") || key.contains("causeandeffect")) {
      return "SIF";
    }
    if (key.contains("secondarypressure")) {
      return "SECONDARY_PRESSURE_PROTECTION";
    }
    if (key.contains("utility")) {
      return "UTILITY";
    }
    if (key.contains("survivability")) {
      return "SURVIVABILITY";
    }
    if (key.contains("instrument") || key.contains("tagreader")) {
      return "INSTRUMENT_DATA";
    }
    return "";
  }

  /**
   * Adds records from one named array.
   *
   * @param input input receiving records
   * @param key source array key
   */
  private void addArray(ProcessSafetySystemReviewInput input, String key) {
    if (!source.has(key) || !source.get(key).isJsonArray()) {
      return;
    }
    JsonArray array = source.getAsJsonArray(key);
    for (int index = 0; index < array.size(); index++) {
      JsonElement element = array.get(index);
      if (element.isJsonObject()) {
        ProcessSafetySystemReviewItem item = fromRecord(element.getAsJsonObject(), key);
        ProcessSafetySystemReviewInput single = new ProcessSafetySystemReviewInput();
        single.addItem(item);
        input.mergeFrom(single);
      }
    }
  }

  /**
   * Converts one record to a review item.
   *
   * @param record source record
   * @param sourceKey source array key
   * @return review item
   */
  private ProcessSafetySystemReviewItem fromRecord(JsonObject record, String sourceKey) {
    ProcessSafetySystemReviewItem item = ProcessSafetySystemReviewItem.fromMap(toMap(record));
    item.addSourceReference(sourceKey);
    item.put("sourceArray", sourceKey);
    if (item.getFunctionType().isEmpty()) {
      item.setFunctionType(inferFunctionType(sourceKey));
    }
    return item;
  }

  /**
   * Converts a JSON object to a map.
   *
   * @param object JSON object
   * @return map representation
   */
  private Map<String, Object> toMap(JsonObject object) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
      map.put(entry.getKey(), toObject(entry.getValue()));
    }
    return map;
  }

  /**
   * Converts a JSON element to Java primitives, maps, and lists.
   *
   * @param element JSON element to convert
   * @return converted Java object
   */
  private Object toObject(JsonElement element) {
    if (element == null || element.isJsonNull()) {
      return null;
    }
    if (element.isJsonObject()) {
      return toMap(element.getAsJsonObject());
    }
    if (element.isJsonArray()) {
      List<Object> list = new ArrayList<Object>();
      for (JsonElement child : element.getAsJsonArray()) {
        list.add(toObject(child));
      }
      return list;
    }
    if (element.getAsJsonPrimitive().isBoolean()) {
      return Boolean.valueOf(element.getAsBoolean());
    }
    if (element.getAsJsonPrimitive().isNumber()) {
      return Double.valueOf(element.getAsDouble());
    }
    return element.getAsString();
  }
}