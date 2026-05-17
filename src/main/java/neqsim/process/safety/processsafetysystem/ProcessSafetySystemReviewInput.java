package neqsim.process.safety.processsafetysystem;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalized input for a NORSOK S-001 Clause 10 process safety system review.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class ProcessSafetySystemReviewInput implements Serializable {
  private static final long serialVersionUID = 1000L;

  private String projectName = "process-safety-system-review";
  private final List<ProcessSafetySystemReviewItem> items =
      new ArrayList<ProcessSafetySystemReviewItem>();
  private final Map<String, Object> metadata = new LinkedHashMap<String, Object>();

  /**
   * Creates an empty review input.
   */
  public ProcessSafetySystemReviewInput() {}

  /**
   * Parses input from JSON text.
   *
   * @param json JSON string with process safety system evidence
   * @return parsed review input
   */
  public static ProcessSafetySystemReviewInput fromJson(String json) {
    return fromJsonObject(JsonParser.parseString(json).getAsJsonObject());
  }

  /**
   * Parses input from a JSON object.
   *
   * @param object JSON object to parse
   * @return parsed review input
   */
  public static ProcessSafetySystemReviewInput fromJsonObject(JsonObject object) {
    ProcessSafetySystemReviewInput input = new ProcessSafetySystemReviewInput();
    input.setProjectName(getString(object, "projectName", "process-safety-system-review"));
    addTopLevelArrays(input, object);
    if (object.has("stidData") && object.get("stidData").isJsonObject()) {
      input.mergeFrom(new StidProcessSafetySystemDataSource(object.getAsJsonObject("stidData"))
          .read());
    }
    if (object.has("tagreaderData") && object.get("tagreaderData").isJsonObject()) {
      input.mergeFrom(new StidProcessSafetySystemDataSource(object.getAsJsonObject("tagreaderData"))
          .read());
      mergeSharedEvidence(input, object.getAsJsonObject("tagreaderData"), "tagreaderData");
    }
    if (object.has("lifecycleEvidence") && object.get("lifecycleEvidence").isJsonObject()) {
      mergeSharedEvidence(input, object.getAsJsonObject("lifecycleEvidence"),
          "lifecycleEvidence");
    }
    for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
      if (!isCoreKey(entry.getKey())) {
        input.putMetadata(entry.getKey(), toObject(entry.getValue()));
      }
    }
    return input;
  }

  /**
   * Sets the project name.
   *
   * @param projectName project, asset, or review name
   * @return this input for fluent construction
   */
  public ProcessSafetySystemReviewInput setProjectName(String projectName) {
    this.projectName = projectName == null || projectName.trim().isEmpty()
        ? "process-safety-system-review" : projectName.trim();
    return this;
  }

  /**
   * Gets the project name.
   *
   * @return project name
   */
  public String getProjectName() {
    return projectName;
  }

  /**
   * Adds one review item.
   *
   * @param item item to add
   * @return this input for fluent construction
   */
  public ProcessSafetySystemReviewInput addItem(ProcessSafetySystemReviewItem item) {
    if (item != null) {
      items.add(item);
    }
    return this;
  }

  /**
   * Gets review items.
   *
   * @return immutable review item list
   */
  public List<ProcessSafetySystemReviewItem> getItems() {
    return Collections.unmodifiableList(items);
  }

  /**
   * Adds input metadata.
   *
   * @param key metadata key
   * @param value metadata value
   * @return this input for fluent construction
   */
  public ProcessSafetySystemReviewInput putMetadata(String key, Object value) {
    if (key != null && !key.trim().isEmpty() && value != null) {
      metadata.put(key, value);
    }
    return this;
  }

  /**
   * Merges another input by function identifier.
   *
   * @param other input to merge
   */
  public void mergeFrom(ProcessSafetySystemReviewInput other) {
    if (other == null) {
      return;
    }
    if (!"process-safety-system-review".equals(other.getProjectName())) {
      setProjectName(other.getProjectName());
    }
    for (ProcessSafetySystemReviewItem otherItem : other.getItems()) {
      ProcessSafetySystemReviewItem existing = findByFunctionId(otherItem.getFunctionId());
      if (existing == null || otherItem.getFunctionId().isEmpty()) {
        addItem(otherItem);
      } else {
        existing.mergeFrom(otherItem);
      }
    }
  }

  /**
   * Converts the input to a JSON-ready map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("projectName", projectName);
    List<Map<String, Object>> itemMaps = new ArrayList<Map<String, Object>>();
    for (ProcessSafetySystemReviewItem item : items) {
      itemMaps.add(item.toMap());
    }
    map.put("items", itemMaps);
    map.put("metadata", new LinkedHashMap<String, Object>(metadata));
    return map;
  }

  /**
   * Adds all known top-level item arrays to the input.
   *
   * @param input input receiving parsed items
   * @param object source JSON object
   */
  private static void addTopLevelArrays(ProcessSafetySystemReviewInput input, JsonObject object) {
    for (String key : StidProcessSafetySystemDataSource.REVIEW_ARRAY_KEYS) {
      if (object.has(key) && object.get(key).isJsonArray()) {
        addArray(input, object.getAsJsonArray(key), key);
      }
    }
  }

  /**
   * Adds all records from one JSON array.
   *
   * @param input input receiving parsed items
   * @param array source array
   * @param sourceKey source array key
   */
  private static void addArray(ProcessSafetySystemReviewInput input, JsonArray array,
      String sourceKey) {
    for (int index = 0; index < array.size(); index++) {
      JsonElement element = array.get(index);
      if (element.isJsonObject()) {
        ProcessSafetySystemReviewItem item = ProcessSafetySystemReviewItem.fromMap(
            toMap(element.getAsJsonObject()));
        item.put("sourceArray", sourceKey);
        if (item.getFunctionType().isEmpty()) {
          item.setFunctionType(StidProcessSafetySystemDataSource.inferFunctionType(sourceKey));
        }
        input.addItem(item);
      }
    }
  }

  /**
   * Merges a shared object into all review items or creates one global item when no items exist.
   *
   * @param input input receiving shared evidence
   * @param object source JSON object
   * @param sourceKey source object key
   */
  private static void mergeSharedEvidence(ProcessSafetySystemReviewInput input, JsonObject object,
      String sourceKey) {
    if (!hasScalarEvidence(object)) {
      return;
    }
    ProcessSafetySystemReviewItem shared = ProcessSafetySystemReviewItem.fromMap(toMap(object));
    shared.put("sourceObject", sourceKey);
    if (!shared.getFunctionId().isEmpty()) {
      ProcessSafetySystemReviewInput wrapper = new ProcessSafetySystemReviewInput();
      wrapper.addItem(shared);
      input.mergeFrom(wrapper);
      return;
    }
    if (input.items.isEmpty()) {
      shared.setFunctionId("GLOBAL-" + sourceKey).setFunctionType(sourceKey);
      input.addItem(shared);
      return;
    }
    for (ProcessSafetySystemReviewItem item : input.items) {
      item.mergeFrom(shared);
    }
  }

  /**
   * Tests whether an object contains scalar evidence in addition to nested arrays or objects.
   *
   * @param object JSON object to inspect
   * @return true when at least one scalar value is present
   */
  private static boolean hasScalarEvidence(JsonObject object) {
    for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
      JsonElement value = entry.getValue();
      if (value != null && !value.isJsonNull() && !value.isJsonArray() && !value.isJsonObject()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Finds an item by function identifier.
   *
   * @param functionId function identifier to search for
   * @return matching item, or null when absent
   */
  private ProcessSafetySystemReviewItem findByFunctionId(String functionId) {
    if (functionId == null || functionId.trim().isEmpty()) {
      return null;
    }
    for (ProcessSafetySystemReviewItem item : items) {
      if (functionId.equalsIgnoreCase(item.getFunctionId())) {
        return item;
      }
    }
    return null;
  }

  /**
   * Gets a string value from JSON.
   *
   * @param object JSON object
   * @param key key to read
   * @param defaultValue default value
   * @return string value or default
   */
  private static String getString(JsonObject object, String key, String defaultValue) {
    return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString()
        : defaultValue;
  }

  /**
   * Converts a JSON object to a map.
   *
   * @param object JSON object
   * @return map representation
   */
  private static Map<String, Object> toMap(JsonObject object) {
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
  private static Object toObject(JsonElement element) {
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

  /**
   * Tests whether a top-level key is interpreted directly by this input.
   *
   * @param key key to test
   * @return true when the key is a core input key
   */
  private static boolean isCoreKey(String key) {
    if ("projectName".equals(key) || "stidData".equals(key) || "tagreaderData".equals(key)
        || "lifecycleEvidence".equals(key)) {
      return true;
    }
    for (String arrayKey : StidProcessSafetySystemDataSource.REVIEW_ARRAY_KEYS) {
      if (arrayKey.equals(key)) {
        return true;
      }
    }
    return false;
  }
}