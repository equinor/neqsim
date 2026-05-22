package neqsim.process.materials;

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
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Normalized input for a process-wide materials review.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class MaterialsReviewInput implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Project, asset, or study name. */
  private String projectName = "materials-review";

  /** Design life used when an item does not specify its own value. */
  private double designLifeYears = 25.0;

  /** Items to evaluate. */
  private final List<MaterialReviewItem> items = new ArrayList<MaterialReviewItem>();

  /** Additional input metadata. */
  private final Map<String, Object> metadata = new LinkedHashMap<String, Object>();

  /**
   * Creates an empty materials review input.
   */
  public MaterialsReviewInput() {}

  /**
   * Parses input from JSON.
   *
   * @param json JSON string with items, materialsRegister, stidData, or process-derived items
   * @return parsed review input
   */
  public static MaterialsReviewInput fromJson(String json) {
    JsonObject object = JsonParser.parseString(json).getAsJsonObject();
    return fromJsonObject(object);
  }

  /**
   * Parses input from a JSON object.
   *
   * @param object JSON object to parse
   * @return parsed review input
   */
  public static MaterialsReviewInput fromJsonObject(JsonObject object) {
    MaterialsReviewInput input = new MaterialsReviewInput();
    input.setProjectName(getString(object, "projectName", "materials-review"));
    input.setDesignLifeYears(getDouble(object, "designLifeYears", 25.0));
    JsonArray itemArray =
        getFirstArray(object, "items", "materialsRegister", "equipment", "lineList", "lines");
    if (itemArray != null) {
      for (int i = 0; i < itemArray.size(); i++) {
        if (itemArray.get(i).isJsonObject()) {
          input.addItem(MaterialReviewItem.fromMap(toMap(itemArray.get(i).getAsJsonObject())));
        }
      }
    }
    for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
      if (!isCoreKey(entry.getKey())) {
        input.putMetadata(entry.getKey(), toObject(entry.getValue()));
      }
    }
    if (object.has("stidData") && object.get("stidData").isJsonObject()) {
      input.mergeFrom(
          StidMaterialsDataSource.fromJsonObject(object.getAsJsonObject("stidData")).read());
    }
    return input;
  }

  /**
   * Creates review input from a simulated process system.
   *
   * <p>
   * The method extracts equipment names, equipment classes, pressure, temperature, and selected
   * component mole fractions. A materials register can later be merged by tag to add material
   * grade, wall thickness, coating, insulation, and inspection data.
   * </p>
   *
   * @param process process system to inspect
   * @return review input containing one item per unit operation
   */
  public static MaterialsReviewInput fromProcessSystem(ProcessSystem process) {
    MaterialsReviewInput input = new MaterialsReviewInput();
    if (process == null) {
      return input;
    }
    input
        .setProjectName(process.getName() == null ? "process-materials-review" : process.getName());
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      MaterialReviewItem item = new MaterialReviewItem();
      item.setTag(unit.getName());
      item.setEquipmentType(unit.getClass().getSimpleName());
      MaterialServiceEnvelope envelope = new MaterialServiceEnvelope();
      try {
        SystemInterface fluid = unit.getThermoSystem();
        if (fluid != null) {
          envelope.set("temperature_C", fluid.getTemperature("C"));
          envelope.set("pressure_bara", fluid.getPressure("bara"));
          envelope.set("co2_mole_fraction", getComponentFraction(fluid, "CO2"));
          envelope.set("h2s_mole_fraction", getComponentFraction(fluid, "H2S"));
          envelope.set("h2_mole_fraction", getComponentFraction(fluid, "hydrogen"));
          envelope.set("water_mole_fraction", getComponentFraction(fluid, "water"));
          envelope.set("free_water",
              fluid.hasComponent("water") && getComponentFraction(fluid, "water") > 1.0e-8);
        }
      } catch (Exception ex) {
        item.putMetadata("processExtractionWarning",
            "Thermo system extraction failed for this unit.");
      }
      item.setServiceEnvelope(envelope);
      item.addSourceReference("ProcessSystem:" + unit.getName());
      input.addItem(item);
    }
    return input;
  }

  /**
   * Sets the project name.
   *
   * @param projectName project or asset name
   * @return this input for fluent construction
   */
  public MaterialsReviewInput setProjectName(String projectName) {
    this.projectName =
        projectName == null || projectName.trim().isEmpty() ? "materials-review" : projectName;
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
   * Sets the design life.
   *
   * @param designLifeYears design life in years
   * @return this input for fluent construction
   */
  public MaterialsReviewInput setDesignLifeYears(double designLifeYears) {
    this.designLifeYears = Math.max(1.0, designLifeYears);
    return this;
  }

  /**
   * Gets the default design life.
   *
   * @return design life in years
   */
  public double getDesignLifeYears() {
    return designLifeYears;
  }

  /**
   * Adds an item to the review.
   *
   * @param item item to add
   * @return this input for fluent construction
   */
  public MaterialsReviewInput addItem(MaterialReviewItem item) {
    if (item != null) {
      items.add(item);
    }
    return this;
  }

  /**
   * Gets items to evaluate.
   *
   * @return immutable item list
   */
  public List<MaterialReviewItem> getItems() {
    return Collections.unmodifiableList(items);
  }

  /**
   * Adds input metadata.
   *
   * @param key metadata key
   * @param value metadata value
   * @return this input for fluent construction
   */
  public MaterialsReviewInput putMetadata(String key, Object value) {
    if (key != null && !key.trim().isEmpty() && value != null) {
      metadata.put(key, value);
    }
    return this;
  }

  /**
   * Merges another input by tag, adding new items and overlaying matching item data.
   *
   * @param other input to merge
   */
  public void mergeFrom(MaterialsReviewInput other) {
    if (other == null) {
      return;
    }
    if (!"materials-review".equals(other.getProjectName())) {
      setProjectName(other.getProjectName());
    }
    setDesignLifeYears(other.getDesignLifeYears());
    for (MaterialReviewItem otherItem : other.getItems()) {
      MaterialReviewItem existing = findByTag(otherItem.getTag());
      if (existing == null) {
        addItem(otherItem);
      } else {
        existing.mergeFrom(otherItem);
      }
    }
  }

  /**
   * Converts this input to a JSON-ready map.
   *
   * @return map representation of the input
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("projectName", projectName);
    map.put("designLifeYears", designLifeYears);
    List<Map<String, Object>> itemMaps = new ArrayList<Map<String, Object>>();
    for (MaterialReviewItem item : items) {
      itemMaps.add(item.toMap());
    }
    map.put("items", itemMaps);
    map.put("metadata", new LinkedHashMap<String, Object>(metadata));
    return map;
  }

  /**
   * Finds an item by tag.
   *
   * @param tag tag to search for
   * @return item if found, otherwise null
   */
  private MaterialReviewItem findByTag(String tag) {
    if (tag == null || tag.trim().isEmpty()) {
      return null;
    }
    for (MaterialReviewItem item : items) {
      if (tag.equalsIgnoreCase(item.getTag())) {
        return item;
      }
    }
    return null;
  }

  /**
   * Reads a component overall mole fraction safely.
   *
   * @param fluid thermo system
   * @param componentName component name
   * @return overall mole fraction or zero
   */
  private static double getComponentFraction(SystemInterface fluid, String componentName) {
    try {
      if (fluid.hasComponent(componentName)) {
        ComponentInterface component = fluid.getComponent(componentName);
        return component == null ? 0.0 : component.getz();
      }
    } catch (Exception ex) {
      return 0.0;
    }
    return 0.0;
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
   * Converts a JSON object to a generic map.
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
   * @return Java value
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
   * Tests whether a top-level key is interpreted directly by this class.
   *
   * @param key key to test
   * @return true if the key is core input data
   */
  private static boolean isCoreKey(String key) {
    return "projectName".equals(key) || "designLifeYears".equals(key) || "items".equals(key)
        || "materialsRegister".equals(key) || "equipment".equals(key) || "lineList".equals(key)
        || "lines".equals(key) || "stidData".equals(key);
  }
}
