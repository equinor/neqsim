package neqsim.process.materials;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flexible service-condition envelope for a material review item.
 *
 * <p>
 * The envelope stores normalized process and integrity parameters such as temperature, pressure,
 * CO2/H2S/H2 content, chloride concentration, free-water presence, oxygen, coating age, insulation
 * type, wall thickness, and inspection data. A map-backed representation keeps the class compatible
 * with STID, line-list, inspection, and project technical database extracts where fields vary
 * between projects.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class MaterialServiceEnvelope implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Envelope values keyed by normalized parameter name. */
  private final Map<String, Object> values = new LinkedHashMap<String, Object>();

  /**
   * Creates an empty service envelope.
   */
  public MaterialServiceEnvelope() {}

  /**
   * Creates an envelope from an existing map.
   *
   * @param source values to copy into the envelope
   * @return a populated envelope
   */
  public static MaterialServiceEnvelope fromMap(Map<String, Object> source) {
    MaterialServiceEnvelope envelope = new MaterialServiceEnvelope();
    if (source != null) {
      for (Map.Entry<String, Object> entry : source.entrySet()) {
        envelope.set(entry.getKey(), entry.getValue());
      }
    }
    return envelope;
  }

  /**
   * Adds or replaces one service-envelope value.
   *
   * @param key normalized key name
   * @param value value to store
   * @return this envelope for fluent construction
   */
  public MaterialServiceEnvelope set(String key, Object value) {
    if (key != null && !key.trim().isEmpty() && value != null) {
      values.put(key, value);
    }
    return this;
  }

  /**
   * Tests if a value exists for a key.
   *
   * @param key key to test
   * @return true if the key exists
   */
  public boolean has(String key) {
    return key != null && values.containsKey(key);
  }

  /**
   * Gets a raw value by key.
   *
   * @param key key to retrieve
   * @return raw value, or null when absent
   */
  public Object get(String key) {
    return key == null ? null : values.get(key);
  }

  /**
   * Reads a value as a double.
   *
   * @param key key to retrieve
   * @param defaultValue value returned when missing or not numeric
   * @return numeric value or default value
   */
  public double getDouble(String key, double defaultValue) {
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
    return defaultValue;
  }

  /**
   * Reads a value as a string.
   *
   * @param key key to retrieve
   * @param defaultValue value returned when missing
   * @return string value or default value
   */
  public String getString(String key, String defaultValue) {
    Object value = get(key);
    if (value == null) {
      return defaultValue;
    }
    return String.valueOf(value);
  }

  /**
   * Reads a value as a boolean.
   *
   * @param key key to retrieve
   * @param defaultValue value returned when missing or not boolean-like
   * @return boolean value or default value
   */
  public boolean getBoolean(String key, boolean defaultValue) {
    Object value = get(key);
    if (value instanceof Boolean) {
      return ((Boolean) value).booleanValue();
    }
    if (value instanceof String) {
      String text = ((String) value).trim();
      if ("true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text)
          || "y".equalsIgnoreCase(text) || "1".equals(text)) {
        return true;
      }
      if ("false".equalsIgnoreCase(text) || "no".equalsIgnoreCase(text)
          || "n".equalsIgnoreCase(text) || "0".equals(text)) {
        return false;
      }
    }
    return defaultValue;
  }

  /**
   * Reads a value as a list of strings.
   *
   * @param key key to retrieve
   * @return list of string values, empty when missing
   */
  public List<String> getStringList(String key) {
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
    return Collections.emptyList();
  }

  /**
   * Returns an immutable view of the envelope values.
   *
   * @return immutable values map
   */
  public Map<String, Object> getValues() {
    return Collections.unmodifiableMap(values);
  }

  /**
   * Converts this envelope to a mutable map suitable for JSON serialization.
   *
   * @return map representation of the envelope
   */
  public Map<String, Object> toMap() {
    return new LinkedHashMap<String, Object>(values);
  }
}
