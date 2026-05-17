package neqsim.process.safety.opendrain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One area, drain system, bunded equipment group, or STID-derived item in an open-drain review.
 *
 * <p>
 * The class is intentionally map-backed so STID/P&amp;ID extracts, line-list records, area safety
 * charts, and tagreader summaries can be normalized outside the Java core and then evaluated by a
 * deterministic review engine.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class OpenDrainReviewItem implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Area, drain-system, or equipment group identifier. */
  private String areaId = "";
  /** Area type such as process, wellhead, drilling, utility, helideck, or storage. */
  private String areaType = "";
  /** Drain system type such as hazardous open drain or non-hazardous open drain. */
  private String drainSystemType = "";
  /** Traceable source references from STID, P&amp;ID, line list, or tagreader export. */
  private final List<String> sourceReferences = new ArrayList<String>();
  /** Flexible normalized review data. */
  private final Map<String, Object> values = new LinkedHashMap<String, Object>();

  /**
   * Creates an empty open-drain review item.
   */
  public OpenDrainReviewItem() {}

  /**
   * Creates a review item from a generic map.
   *
   * @param source source map containing area identity, drain data, evidence, and nested design data
   * @return populated open-drain review item
   */
  @SuppressWarnings("unchecked")
  public static OpenDrainReviewItem fromMap(Map<String, Object> source) {
    OpenDrainReviewItem item = new OpenDrainReviewItem();
    if (source == null) {
      return item;
    }
    item.setAreaId(firstString(source, "areaId", "area", "tag", "lineNumber", "name"));
    item.setAreaType(firstString(source, "areaType", "type", "mainArea", "service"));
    item.setDrainSystemType(firstString(source, "drainSystemType", "drainType", "systemType"));
    Object references = firstObject(source, "sourceReferences", "sourceRefs", "evidenceRefs");
    addReferences(item, references);
    Object sourceDocument = firstObject(source, "sourceDocument", "documentId", "stidDocument");
    if (sourceDocument != null) {
      item.addSourceReference(String.valueOf(sourceDocument));
    }
    for (String key : new String[] {"design", "requirements", "service", "operationalData",
        "tagreaderData", "historianData"}) {
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
   * Sets the area identifier.
   *
   * @param areaId area, drain-system, or equipment group identifier
   * @return this item for fluent construction
   */
  public OpenDrainReviewItem setAreaId(String areaId) {
    this.areaId = areaId == null ? "" : areaId.trim();
    return this;
  }

  /**
   * Gets the area identifier.
   *
   * @return area identifier
   */
  public String getAreaId() {
    return areaId;
  }

  /**
   * Sets the area type.
   *
   * @param areaType area type or main-area classification
   * @return this item for fluent construction
   */
  public OpenDrainReviewItem setAreaType(String areaType) {
    this.areaType = areaType == null ? "" : areaType.trim();
    return this;
  }

  /**
   * Gets the area type.
   *
   * @return area type
   */
  public String getAreaType() {
    return areaType;
  }

  /**
   * Sets the drain system type.
   *
   * @param drainSystemType drain system type
   * @return this item for fluent construction
   */
  public OpenDrainReviewItem setDrainSystemType(String drainSystemType) {
    this.drainSystemType = drainSystemType == null ? "" : drainSystemType.trim();
    return this;
  }

  /**
   * Gets the drain system type.
   *
   * @return drain system type
   */
  public String getDrainSystemType() {
    return drainSystemType;
  }

  /**
   * Adds a source reference.
   *
   * @param sourceReference source reference text
   * @return this item for fluent construction
   */
  public OpenDrainReviewItem addSourceReference(String sourceReference) {
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
  public OpenDrainReviewItem put(String key, Object value) {
    if (key != null && !key.trim().isEmpty() && value != null && isJsonSafe(value)) {
      values.put(key, value);
    }
    return this;
  }

  /**
   * Tests whether a value can be written as standards-compliant JSON.
   *
   * @param value value to test
   * @return true when the value is safe for JSON serialization
   */
  private static boolean isJsonSafe(Object value) {
    if (value instanceof Double) {
      Double number = (Double) value;
      return !number.isNaN() && !number.isInfinite();
    }
    if (value instanceof Float) {
      Float number = (Float) value;
      return !number.isNaN() && !number.isInfinite();
    }
    return true;
  }

  /**
   * Checks if a key is present.
   *
   * @param key key to test
   * @return true if present
   */
  public boolean has(String key) {
    return key != null && values.containsKey(key);
  }

  /**
   * Checks if any key is present.
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
   * Reads a value as a list of strings.
   *
   * @param keys keys to test in order
   * @return list of string values
   */
  public List<String> getStringList(String... keys) {
    for (String key : keys) {
      Object value = get(key);
      if (value instanceof List<?>) {
        List<String> result = new ArrayList<String>();
        for (Object item : (List<?>) value) {
          if (item != null) {
            result.add(String.valueOf(item));
          }
        }
        return result;
      }
      if (value != null) {
        return Collections.singletonList(String.valueOf(value));
      }
    }
    return Collections.emptyList();
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
   * Merges another item into this item by overlaying non-empty identity and value fields.
   *
   * @param other other item to merge
   */
  public void mergeFrom(OpenDrainReviewItem other) {
    if (other == null) {
      return;
    }
    if (!other.getAreaType().isEmpty()) {
      setAreaType(other.getAreaType());
    }
    if (!other.getDrainSystemType().isEmpty()) {
      setDrainSystemType(other.getDrainSystemType());
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
    map.put("areaId", areaId);
    map.put("areaType", areaType);
    map.put("drainSystemType", drainSystemType);
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
  private static void addReferences(OpenDrainReviewItem item, Object references) {
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
    return "areaId".equals(key) || "area".equals(key) || "tag".equals(key)
        || "lineNumber".equals(key) || "name".equals(key) || "areaType".equals(key)
        || "type".equals(key) || "mainArea".equals(key) || "service".equals(key)
        || "drainSystemType".equals(key) || "drainType".equals(key) || "systemType".equals(key)
        || "sourceReferences".equals(key) || "sourceRefs".equals(key) || "evidenceRefs".equals(key)
        || "sourceDocument".equals(key) || "documentId".equals(key) || "stidDocument".equals(key)
        || "design".equals(key) || "requirements".equals(key) || "operationalData".equals(key)
        || "tagreaderData".equals(key) || "historianData".equals(key);
  }
}
