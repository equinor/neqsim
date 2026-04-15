package neqsim.mcp.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A numeric value with its associated unit.
 *
 * <p>
 * Used in MCP request/response models to represent physical quantities that can be specified with
 * different units. Supports JSON deserialization from either a bare number (uses default unit) or
 * an object with {@code value} and {@code unit} fields.
 * </p>
 *
 * <pre>{@code
 * // Bare number — default unit assumed
 * 25.0
 *
 * // Object with unit
 * {"value": 25.0, "unit": "C"}
 * }</pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ValueWithUnit {

  private final double value;
  private final String unit;

  /**
   * Creates a value with unit.
   *
   * @param value the numeric value
   * @param unit the unit string
   */
  public ValueWithUnit(double value, String unit) {
    this.value = value;
    this.unit = unit;
  }

  /**
   * Gets the numeric value.
   *
   * @return the value
   */
  public double getValue() {
    return value;
  }

  /**
   * Gets the unit string.
   *
   * @return the unit
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Parses a ValueWithUnit from a JSON element. Accepts either a bare number (using defaultUnit) or
   * an object with "value" and optional "unit" fields.
   *
   * @param element the JSON element to parse
   * @param defaultUnit the unit to use if not specified
   * @return the parsed ValueWithUnit, or null if parsing fails
   */
  public static ValueWithUnit fromJson(JsonElement element, String defaultUnit) {
    if (element == null) {
      return null;
    }
    try {
      if (element.isJsonPrimitive()) {
        return new ValueWithUnit(element.getAsDouble(), defaultUnit);
      }
      if (element.isJsonObject()) {
        JsonObject obj = element.getAsJsonObject();
        double val = obj.get("value").getAsDouble();
        String unit = obj.has("unit") ? obj.get("unit").getAsString() : defaultUnit;
        return new ValueWithUnit(val, unit);
      }
    } catch (Exception e) {
      // fall through to null
    }
    return null;
  }

  /**
   * Converts this value to a JSON element. Returns a JsonObject with "value" and "unit" fields.
   *
   * @return the JSON representation
   */
  public JsonElement toJson() {
    JsonObject obj = new JsonObject();
    obj.addProperty("value", value);
    obj.addProperty("unit", unit);
    return obj;
  }

  @Override
  public String toString() {
    return value + " " + unit;
  }
}
