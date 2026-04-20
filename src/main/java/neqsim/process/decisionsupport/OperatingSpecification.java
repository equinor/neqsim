package neqsim.process.decisionsupport;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;

import neqsim.process.decisionsupport.EngineeringRecommendation.ConstraintStatus;

/**
 * Reusable, serializable set of operating limits and product specifications.
 *
 * <p>
 * Can be loaded from JSON to define plant-specific limits independent of the simulation model.
 * These specifications are checked against a running process model to determine compliance.
 * </p>
 *
 * <pre>
 * OperatingSpecification spec = new OperatingSpecification();
 * spec.addProductSpec("waterDewPoint", -18.0, Double.NaN, "C", "ISO 6327");
 * spec.addProductSpec("wobbeIndex", 46.1, 52.2, "MJ/Sm3", "EN 16726");
 * spec.addEquipmentLimit("Compressor-K100", "surgeMargin", 0.10, Double.NaN, "fraction",
 *     "API 617");
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class OperatingSpecification implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Gson GSON = GsonFactory.instance();

  private String name;
  private final Map<String, SpecLimit> productSpecs;
  private final List<EquipmentLimit> equipmentLimits;

  /**
   * Creates an empty operating specification.
   */
  public OperatingSpecification() {
    this.name = "Default";
    this.productSpecs = new HashMap<>();
    this.equipmentLimits = new ArrayList<>();
  }

  /**
   * Creates an operating specification with a name.
   *
   * @param name the specification name
   */
  public OperatingSpecification(String name) {
    this();
    this.name = name;
  }

  /**
   * Loads an OperatingSpecification from JSON.
   *
   * @param json the JSON string
   * @return the deserialized specification
   */
  public static OperatingSpecification fromJson(String json) {
    return GSON.fromJson(json, OperatingSpecification.class);
  }

  /**
   * Serializes this specification to JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return GSON.toJson(this);
  }

  /**
   * Adds a product specification limit.
   *
   * @param specName the specification name (e.g., "waterDewPoint")
   * @param minValue minimum allowed value (use Double.NaN for no minimum)
   * @param maxValue maximum allowed value (use Double.NaN for no maximum)
   * @param unit the engineering unit
   * @param standardRef reference standard (e.g., "ISO 6976")
   */
  public void addProductSpec(String specName, double minValue, double maxValue, String unit,
      String standardRef) {
    productSpecs.put(specName, new SpecLimit(specName, minValue, maxValue, unit, standardRef));
  }

  /**
   * Adds an equipment-specific operating limit.
   *
   * @param equipmentName the equipment name
   * @param parameterName the parameter name (e.g., "surgeMargin")
   * @param minValue minimum allowed value (use Double.NaN for no minimum)
   * @param maxValue maximum allowed value (use Double.NaN for no maximum)
   * @param unit the engineering unit
   * @param standardRef reference standard
   */
  public void addEquipmentLimit(String equipmentName, String parameterName, double minValue,
      double maxValue, String unit, String standardRef) {
    equipmentLimits.add(
        new EquipmentLimit(equipmentName, parameterName, minValue, maxValue, unit, standardRef));
  }

  /**
   * Checks a set of measured/calculated values against these specifications.
   *
   * @param values map of spec name to measured value
   * @return list of spec check results
   */
  public List<SpecCheckResult> checkValues(Map<String, Double> values) {
    List<SpecCheckResult> results = new ArrayList<>();
    for (Map.Entry<String, SpecLimit> entry : productSpecs.entrySet()) {
      String specName = entry.getKey();
      SpecLimit limit = entry.getValue();
      Double value = values.get(specName);
      if (value == null) {
        results.add(new SpecCheckResult(specName, ConstraintStatus.WARN, Double.NaN, limit.minValue,
            limit.maxValue, limit.unit, "Value not available"));
        continue;
      }

      ConstraintStatus status = ConstraintStatus.PASS;
      String message = "Within spec";

      if (!Double.isNaN(limit.minValue) && value < limit.minValue) {
        status = ConstraintStatus.FAIL;
        message = "Below minimum (" + limit.minValue + " " + limit.unit + ")";
      } else if (!Double.isNaN(limit.maxValue) && value > limit.maxValue) {
        status = ConstraintStatus.FAIL;
        message = "Above maximum (" + limit.maxValue + " " + limit.unit + ")";
      }

      results.add(new SpecCheckResult(specName, status, value, limit.minValue, limit.maxValue,
          limit.unit, message));
    }
    return results;
  }

  /**
   * Gets the specification name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the specification name.
   *
   * @param name the name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets all product specifications.
   *
   * @return unmodifiable map of spec name to limit
   */
  public Map<String, SpecLimit> getProductSpecs() {
    return java.util.Collections.unmodifiableMap(productSpecs);
  }

  /**
   * Gets all equipment limits.
   *
   * @return unmodifiable list of equipment limits
   */
  public List<EquipmentLimit> getEquipmentLimits() {
    return java.util.Collections.unmodifiableList(equipmentLimits);
  }

  /**
   * A single product specification limit.
   */
  public static class SpecLimit implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String specName;
    private final double minValue;
    private final double maxValue;
    private final String unit;
    private final String standardRef;

    /**
     * Creates a spec limit.
     *
     * @param specName the specification name
     * @param minValue minimum allowed value (Double.NaN for no minimum)
     * @param maxValue maximum allowed value (Double.NaN for no maximum)
     * @param unit the engineering unit
     * @param standardRef reference standard
     */
    public SpecLimit(String specName, double minValue, double maxValue, String unit,
        String standardRef) {
      this.specName = specName;
      this.minValue = minValue;
      this.maxValue = maxValue;
      this.unit = unit;
      this.standardRef = standardRef;
    }

    /**
     * Gets the spec name.
     *
     * @return the spec name
     */
    public String getSpecName() {
      return specName;
    }

    /**
     * Gets the minimum value.
     *
     * @return the minimum value (Double.NaN if no minimum)
     */
    public double getMinValue() {
      return minValue;
    }

    /**
     * Gets the maximum value.
     *
     * @return the maximum value (Double.NaN if no maximum)
     */
    public double getMaxValue() {
      return maxValue;
    }

    /**
     * Gets the unit.
     *
     * @return the unit
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Gets the standard reference.
     *
     * @return the standard reference
     */
    public String getStandardRef() {
      return standardRef;
    }
  }

  /**
   * An equipment-specific operating limit.
   */
  public static class EquipmentLimit implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String equipmentName;
    private final String parameterName;
    private final double minValue;
    private final double maxValue;
    private final String unit;
    private final String standardRef;

    /**
     * Creates an equipment limit.
     *
     * @param equipmentName the equipment name
     * @param parameterName the parameter name
     * @param minValue minimum allowed value (Double.NaN for no minimum)
     * @param maxValue maximum allowed value (Double.NaN for no maximum)
     * @param unit the engineering unit
     * @param standardRef reference standard
     */
    public EquipmentLimit(String equipmentName, String parameterName, double minValue,
        double maxValue, String unit, String standardRef) {
      this.equipmentName = equipmentName;
      this.parameterName = parameterName;
      this.minValue = minValue;
      this.maxValue = maxValue;
      this.unit = unit;
      this.standardRef = standardRef;
    }

    /**
     * Gets the equipment name.
     *
     * @return the equipment name
     */
    public String getEquipmentName() {
      return equipmentName;
    }

    /**
     * Gets the parameter name.
     *
     * @return the parameter name
     */
    public String getParameterName() {
      return parameterName;
    }

    /**
     * Gets the minimum value.
     *
     * @return the minimum value (Double.NaN if no minimum)
     */
    public double getMinValue() {
      return minValue;
    }

    /**
     * Gets the maximum value.
     *
     * @return the maximum value (Double.NaN if no maximum)
     */
    public double getMaxValue() {
      return maxValue;
    }

    /**
     * Gets the unit.
     *
     * @return the unit
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Gets the standard reference.
     *
     * @return the standard reference
     */
    public String getStandardRef() {
      return standardRef;
    }
  }

  /**
   * Result of checking a value against a specification.
   */
  public static class SpecCheckResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String specName;
    private final ConstraintStatus status;
    private final double measuredValue;
    private final double minLimit;
    private final double maxLimit;
    private final String unit;
    private final String message;

    /**
     * Creates a spec check result.
     *
     * @param specName the specification name
     * @param status the check status
     * @param measuredValue the measured value
     * @param minLimit the minimum limit
     * @param maxLimit the maximum limit
     * @param unit the unit
     * @param message human-readable message
     */
    public SpecCheckResult(String specName, ConstraintStatus status, double measuredValue,
        double minLimit, double maxLimit, String unit, String message) {
      this.specName = specName;
      this.status = status;
      this.measuredValue = measuredValue;
      this.minLimit = minLimit;
      this.maxLimit = maxLimit;
      this.unit = unit;
      this.message = message;
    }

    /**
     * Gets the specification name.
     *
     * @return the spec name
     */
    public String getSpecName() {
      return specName;
    }

    /**
     * Gets the check status.
     *
     * @return the status
     */
    public ConstraintStatus getStatus() {
      return status;
    }

    /**
     * Gets the measured value.
     *
     * @return the measured value
     */
    public double getMeasuredValue() {
      return measuredValue;
    }

    /**
     * Gets the minimum limit.
     *
     * @return the minimum limit
     */
    public double getMinLimit() {
      return minLimit;
    }

    /**
     * Gets the maximum limit.
     *
     * @return the maximum limit
     */
    public double getMaxLimit() {
      return maxLimit;
    }

    /**
     * Gets the unit.
     *
     * @return the unit
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Gets the human-readable message.
     *
     * @return the message
     */
    public String getMessage() {
      return message;
    }
  }
}
