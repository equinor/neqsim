package neqsim.process.safety.opendrain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Normalized input for an open-drain review against NORSOK S-001 Clause 9.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class OpenDrainReviewInput implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Project, asset, or review name. */
  private String projectName = "open-drain-review";
  /** Default liquid leak rate associated with worst credible process fire [kg/s]. */
  private double defaultLiquidLeakRateKgPerS = 5.0;
  /** Items to evaluate. */
  private final List<OpenDrainReviewItem> items = new ArrayList<OpenDrainReviewItem>();
  /** Additional input metadata. */
  private final Map<String, Object> metadata = new LinkedHashMap<String, Object>();

  /**
   * Creates an empty open-drain review input.
   */
  public OpenDrainReviewInput() {}

  /**
   * Parses input from JSON text.
   *
   * @param json JSON string with items, openDrainAreas, drainAreas, or stidData
   * @return parsed review input
   */
  public static OpenDrainReviewInput fromJson(String json) {
    JsonObject object = JsonParser.parseString(json).getAsJsonObject();
    return fromJsonObject(object);
  }

  /**
   * Parses input from a JSON object.
   *
   * @param object JSON object to parse
   * @return parsed review input
   */
  public static OpenDrainReviewInput fromJsonObject(JsonObject object) {
    OpenDrainReviewInput input = new OpenDrainReviewInput();
    input.setProjectName(getString(object, "projectName", "open-drain-review"));
    input.setDefaultLiquidLeakRateKgPerS(getDouble(object, "defaultLiquidLeakRateKgPerS", 5.0));
    JsonArray itemArray =
        getFirstArray(object, "items", "openDrainAreas", "drainAreas", "areas", "drainSystems");
    if (itemArray != null) {
      for (int i = 0; i < itemArray.size(); i++) {
        if (itemArray.get(i).isJsonObject()) {
          input.addItem(OpenDrainReviewItem.fromMap(toMap(itemArray.get(i).getAsJsonObject())));
        }
      }
    }
    if (object.has("stidData") && object.get("stidData").isJsonObject()) {
      input.mergeFrom(
          StidOpenDrainDataSource.fromJsonObject(object.getAsJsonObject("stidData")).read());
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
  public OpenDrainReviewInput setProjectName(String projectName) {
    this.projectName = projectName == null || projectName.trim().isEmpty() ? "open-drain-review"
        : projectName.trim();
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
   * Sets the default liquid leak rate for areas missing a specific value.
   *
   * @param leakRateKgPerS default liquid leak rate in kg/s
   * @return this input for fluent construction
   */
  public OpenDrainReviewInput setDefaultLiquidLeakRateKgPerS(double leakRateKgPerS) {
    this.defaultLiquidLeakRateKgPerS = Math.max(0.0, leakRateKgPerS);
    return this;
  }

  /**
   * Gets the default liquid leak rate.
   *
   * @return default liquid leak rate in kg/s
   */
  public double getDefaultLiquidLeakRateKgPerS() {
    return defaultLiquidLeakRateKgPerS;
  }

  /**
   * Adds one review item.
   *
   * @param item item to add
   * @return this input for fluent construction
   */
  public OpenDrainReviewInput addItem(OpenDrainReviewItem item) {
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
  public List<OpenDrainReviewItem> getItems() {
    return Collections.unmodifiableList(items);
  }

  /**
   * Adds input metadata.
   *
   * @param key metadata key
   * @param value metadata value
   * @return this input for fluent construction
   */
  public OpenDrainReviewInput putMetadata(String key, Object value) {
    if (key != null && !key.trim().isEmpty() && value != null) {
      metadata.put(key, value);
    }
    return this;
  }

  /**
   * Merges another input by area identifier.
   *
   * @param other input to merge
   */
  public void mergeFrom(OpenDrainReviewInput other) {
    if (other == null) {
      return;
    }
    if (!"open-drain-review".equals(other.getProjectName())) {
      setProjectName(other.getProjectName());
    }
    setDefaultLiquidLeakRateKgPerS(other.getDefaultLiquidLeakRateKgPerS());
    for (OpenDrainReviewItem otherItem : other.getItems()) {
      OpenDrainReviewItem existing = findByAreaId(otherItem.getAreaId());
      if (existing == null || otherItem.getAreaId().isEmpty()) {
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
    map.put("defaultLiquidLeakRateKgPerS", defaultLiquidLeakRateKgPerS);
    List<Map<String, Object>> itemMaps = new ArrayList<Map<String, Object>>();
    for (OpenDrainReviewItem item : items) {
      itemMaps.add(item.toMap());
    }
    map.put("items", itemMaps);
    map.put("metadata", new LinkedHashMap<String, Object>(metadata));
    return map;
  }

  /**
   * Finds an item by area identifier.
   *
   * @param areaId area identifier to search for
   * @return matching item, or null when absent
   */
  private OpenDrainReviewItem findByAreaId(String areaId) {
    if (areaId == null || areaId.trim().isEmpty()) {
      return null;
    }
    for (OpenDrainReviewItem item : items) {
      if (areaId.equalsIgnoreCase(item.getAreaId())) {
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
   * Gets a double value from JSON.
   *
   * @param object JSON object
   * @param key key to read
   * @param defaultValue default value
   * @return double value or default
   */
  private static double getDouble(JsonObject object, String key, double defaultValue) {
    return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsDouble()
        : defaultValue;
  }

  /**
   * Gets the first available array from a list of keys.
   *
   * @param object JSON object
   * @param keys keys to test
   * @return first array found, or null
   */
  private static JsonArray getFirstArray(JsonObject object, String... keys) {
    for (String key : keys) {
      if (object.has(key) && object.get(key).isJsonArray()) {
        return object.getAsJsonArray(key);
      }
    }
    return null;
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
    return "projectName".equals(key) || "defaultLiquidLeakRateKgPerS".equals(key)
        || "items".equals(key) || "openDrainAreas".equals(key) || "drainAreas".equals(key)
        || "areas".equals(key) || "drainSystems".equals(key) || "stidData".equals(key);
  }
}
