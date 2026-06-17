package neqsim.process.safety.processsafetysystem;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One process safety function, final element, protection layer, or evidence record in a Clause 10
 * review.
 *
 * <p>
 * The class is map-backed so C&amp;E extracts, SRS tables, PSV lists, instrument data, STID/P&amp;ID
 * evidence, and tagreader summaries can be normalized outside the Java core and evaluated by a
 * deterministic standards engine.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class ProcessSafetySystemReviewItem implements Serializable {
  private static final long serialVersionUID = 1000L;

  private String functionId = "";
  private String functionType = "";
  private String equipmentTag = "";
  private final List<String> sourceReferences = new ArrayList<String>();
  private final Map<String, Object> values = new LinkedHashMap<String, Object>();

  /**
   * Creates an empty process safety system review item.
   */
  public ProcessSafetySystemReviewItem() {}

  /**
   * Creates a review item from a generic map.
   *
   * @param source source map containing identity, normalized evidence, and nested data
   * @return populated process safety system review item
   */
  @SuppressWarnings("unchecked")
  public static ProcessSafetySystemReviewItem fromMap(Map<String, Object> source) {
    ProcessSafetySystemReviewItem item = new ProcessSafetySystemReviewItem();
    if (source == null) {
      return item;
    }
    item.setFunctionId(firstString(source, "functionId", "id", "tag", "sifId", "psvTag",
        "psdValveTag", "alarmTag", "name"));
    item.setFunctionType(firstString(source, "functionType", "type", "category", "safetyType"));
    item.setEquipmentTag(firstString(source, "equipmentTag", "protectedEquipmentTag",
        "protectedEquipment", "unitTag", "tag"));
    addReferences(item, firstObject(source, "sourceReferences", "sourceRefs", "evidenceRefs"));
    Object sourceDocument = firstObject(source, "sourceDocument", "documentId", "stidDocument",
        "causeAndEffectDocument", "srsDocument");
    if (sourceDocument != null) {
      item.addSourceReference(String.valueOf(sourceDocument));
    }
    for (String key : new String[] {"design", "requirements", "instrumentData", "tagreaderData",
        "operationalData", "secondaryPressureProtection"}) {
      Object nested = source.get(key);
      if (nested instanceof Map<?, ?>) {
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) nested).entrySet()) {
          item.put(entry.getKey(), entry.getValue());
        }
      }
    }
    for (Map.Entry<String, Object> entry : source.entrySet()) {
      if (!isCoreKey(entry.getKey())) {
        item.put(entry.getKey(), entry.getValue());
      }
    }
    return item;
  }

  /**
   * Sets the safety function identifier.
   *
   * @param functionId safety function, final element, alarm, or equipment identifier
   * @return this item for fluent construction
   */
  public ProcessSafetySystemReviewItem setFunctionId(String functionId) {
    this.functionId = functionId == null ? "" : functionId.trim();
    return this;
  }

  /**
   * Gets the safety function identifier.
   *
   * @return function identifier
   */
  public String getFunctionId() {
    return functionId;
  }

  /**
   * Sets the safety function type.
   *
   * @param functionType function type such as PSD, PSV, ALARM, SIF, UTILITY, or SURVIVABILITY
   * @return this item for fluent construction
   */
  public ProcessSafetySystemReviewItem setFunctionType(String functionType) {
    this.functionType = functionType == null ? "" : functionType.trim();
    return this;
  }

  /**
   * Gets the safety function type.
   *
   * @return function type
   */
  public String getFunctionType() {
    return functionType;
  }

  /**
   * Sets the protected or associated equipment tag.
   *
   * @param equipmentTag equipment tag
   * @return this item for fluent construction
   */
  public ProcessSafetySystemReviewItem setEquipmentTag(String equipmentTag) {
    this.equipmentTag = equipmentTag == null ? "" : equipmentTag.trim();
    return this;
  }

  /**
   * Gets the protected or associated equipment tag.
   *
   * @return equipment tag
   */
  public String getEquipmentTag() {
    return equipmentTag;
  }

  /**
   * Adds a source reference.
   *
   * @param sourceReference source reference text
   * @return this item for fluent construction
   */
  public ProcessSafetySystemReviewItem addSourceReference(String sourceReference) {
    if (sourceReference != null && !sourceReference.trim().isEmpty()) {
      sourceReferences.add(sourceReference.trim());
    }
    return this;
  }

  /**
   * Gets source references.
   *
   * @return immutable source-reference list
   */
  public List<String> getSourceReferences() {
    return Collections.unmodifiableList(sourceReferences);
  }

  /**
   * Adds or replaces one normalized value.
   *
   * @param key normalized key
   * @param value value to store
   * @return this item for fluent construction
   */
  public ProcessSafetySystemReviewItem put(String key, Object value) {
    if (key != null && !key.trim().isEmpty() && value != null) {
      values.put(key, value);
    }
    return this;
  }

  /**
   * Checks whether a key is present.
   *
   * @param key key to test
   * @return true if the key is present
   */
  public boolean has(String key) {
    return key != null && values.containsKey(key);
  }

  /**
   * Checks whether any key is present.
   *
   * @param keys keys to test
   * @return true if any key is present
   */
  public boolean hasAny(String... keys) {
    for (String key : keys) {
      if (has(key)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gets a raw value.
   *
   * @param key key to read
   * @return value, or null when absent
   */
  public Object get(String key) {
    return key == null ? null : values.get(key);
  }

  /**
   * Reads the first present value as a double.
   *
   * @param defaultValue value returned when no numeric key is present
   * @param keys keys to test in order
   * @return numeric value or default value
   */
  public double getDouble(double defaultValue, String... keys) {
    for (String key : keys) {
      Object value = get(key);
      if (value instanceof Number) {
        return ((Number) value).doubleValue();
      }
      if (value instanceof String) {
        try {
          return Double.parseDouble(((String) value).trim());
        } catch (NumberFormatException ex) {
          return defaultValue;
        }
      }
    }
    return defaultValue;
  }

  /**
   * Reads the first present value as a Boolean.
   *
   * @param keys keys to test in order
   * @return Boolean value, or null when no key is present or parseable
   */
  public Boolean getBooleanObject(String... keys) {
    for (String key : keys) {
      Object value = get(key);
      if (value instanceof Boolean) {
        return (Boolean) value;
      }
      if (value instanceof String) {
        String text = ((String) value).trim();
        if ("true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text)
            || "y".equalsIgnoreCase(text) || "1".equals(text)) {
          return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text) || "no".equalsIgnoreCase(text)
            || "n".equalsIgnoreCase(text) || "0".equals(text)) {
          return Boolean.FALSE;
        }
      }
    }
    return null;
  }

  /**
   * Reads the first present value as a boolean.
   *
   * @param defaultValue value returned when no boolean-like key is present
   * @param keys keys to test in order
   * @return boolean value or default value
   */
  public boolean getBoolean(boolean defaultValue, String... keys) {
    Boolean value = getBooleanObject(keys);
    return value == null ? defaultValue : value.booleanValue();
  }

  /**
   * Reads the first present value as a string.
   *
   * @param defaultValue value returned when missing
   * @param keys keys to test in order
   * @return string value or default value
   */
  public String getString(String defaultValue, String... keys) {
    for (String key : keys) {
      Object value = get(key);
      if (value != null) {
        return String.valueOf(value);
      }
    }
    return defaultValue;
  }

  /**
   * Gets all normalized values.
   *
   * @return immutable value map
   */
  public Map<String, Object> getValues() {
    return Collections.unmodifiableMap(values);
  }

  /**
   * Merges another item into this item.
   *
   * @param other other item to merge
   */
  public void mergeFrom(ProcessSafetySystemReviewItem other) {
    if (other == null) {
      return;
    }
    if (!other.getFunctionType().isEmpty()) {
      setFunctionType(other.getFunctionType());
    }
    if (!other.getEquipmentTag().isEmpty()) {
      setEquipmentTag(other.getEquipmentTag());
    }
    for (String reference : other.getSourceReferences()) {
      addSourceReference(reference);
    }
    for (Map.Entry<String, Object> entry : other.getValues().entrySet()) {
      put(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Converts this item to a JSON-ready map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("functionId", functionId);
    map.put("functionType", functionType);
    map.put("equipmentTag", equipmentTag);
    map.put("sourceReferences", new ArrayList<String>(sourceReferences));
    map.put("values", new LinkedHashMap<String, Object>(values));
    return map;
  }

  /**
   * Adds one or more references from an object.
   *
   * @param item item receiving source references
   * @param references source reference object
   */
  private static void addReferences(ProcessSafetySystemReviewItem item, Object references) {
    if (references instanceof List<?>) {
      for (Object reference : (List<?>) references) {
        if (reference != null) {
          item.addSourceReference(String.valueOf(reference));
        }
      }
    } else if (references != null) {
      item.addSourceReference(String.valueOf(references));
    }
  }

  /**
   * Returns the first non-empty string value.
   *
   * @param source source map
   * @param keys keys to test
   * @return first non-empty string, or an empty string
   */
  private static String firstString(Map<String, Object> source, String... keys) {
    Object value = firstObject(source, keys);
    return value == null ? "" : String.valueOf(value);
  }

  /**
   * Returns the first non-null object value.
   *
   * @param source source map
   * @param keys keys to test
   * @return first non-null value, or null
   */
  private static Object firstObject(Map<String, Object> source, String... keys) {
    for (String key : keys) {
      if (source.containsKey(key) && source.get(key) != null) {
        return source.get(key);
      }
    }
    return null;
  }

  /**
   * Tests whether a source key is interpreted directly by this item.
   *
   * @param key key to test
   * @return true if the key is an identity or nested-data key
   */
  private static boolean isCoreKey(String key) {
    return "functionId".equals(key) || "id".equals(key) || "tag".equals(key)
        || "sifId".equals(key) || "psvTag".equals(key) || "psdValveTag".equals(key)
        || "alarmTag".equals(key) || "name".equals(key) || "functionType".equals(key)
        || "type".equals(key) || "category".equals(key) || "safetyType".equals(key)
        || "equipmentTag".equals(key) || "protectedEquipmentTag".equals(key)
        || "protectedEquipment".equals(key) || "unitTag".equals(key)
        || "sourceReferences".equals(key) || "sourceRefs".equals(key)
        || "evidenceRefs".equals(key) || "sourceDocument".equals(key)
        || "documentId".equals(key) || "stidDocument".equals(key)
        || "causeAndEffectDocument".equals(key) || "srsDocument".equals(key)
        || "design".equals(key) || "requirements".equals(key)
        || "instrumentData".equals(key) || "tagreaderData".equals(key)
        || "operationalData".equals(key) || "secondaryPressureProtection".equals(key);
  }
}