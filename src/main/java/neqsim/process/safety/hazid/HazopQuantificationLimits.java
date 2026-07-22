package neqsim.process.safety.hazid;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Design limits used by {@link HazopConsequenceAutoPopulator#quantify} to turn a computed consequence value into a
 * deterministic PASS / EXCEEDS verdict.
 *
 * <p>
 * Two screening limits are carried, each with a sensible default and an optional per-unit override keyed by unit name:
 * </p>
 *
 * <ul>
 * <li><b>Maximum discharge temperature</b> — the highest gas temperature a compressor or expander discharge may reach
 * before seal, lube-oil or downstream material limits are challenged. Default 150 &deg;C (a common API 617 screening
 * value).</li>
 * <li><b>Minimum design metal temperature (MDMT)</b> — the lowest metal temperature the material remains tough at,
 * below which Joule-Thomson auto-refrigeration across a valve risks brittle fracture. Default -46 &deg;C (a common
 * low-temperature carbon-steel value per ASME UCS-66).</li>
 * </ul>
 *
 * <p>
 * The defaults are conservative screening values only; a competent engineer should supply the actual equipment data
 * sheet and material MDMT before relying on a verdict.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class HazopQuantificationLimits implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Default maximum discharge temperature in degrees Celsius (API 617 screening value). */
  public static final double DEFAULT_MAX_DISCHARGE_TEMPERATURE_C = 150.0;

  /**
   * Default minimum design metal temperature in degrees Celsius (ASME UCS-66 low-temperature carbon steel).
   */
  public static final double DEFAULT_MIN_DESIGN_METAL_TEMPERATURE_C = -46.0;

  private double maxDischargeTemperatureC = DEFAULT_MAX_DISCHARGE_TEMPERATURE_C;
  private double minDesignMetalTemperatureC = DEFAULT_MIN_DESIGN_METAL_TEMPERATURE_C;

  private final Map<String, Double> maxDischargeTemperatureByUnit = new HashMap<String, Double>();
  private final Map<String, Double> minDesignMetalTemperatureByUnit = new HashMap<String, Double>();

  /**
   * Construct a limits holder with the conservative default screening limits.
   */
  public HazopQuantificationLimits() {
  }

  /**
   * Sets the default maximum discharge temperature applied to compressors and expanders without a per-unit override.
   *
   * @param temperatureC maximum discharge temperature in degrees Celsius
   * @return this holder for chaining
   */
  public HazopQuantificationLimits setMaxDischargeTemperatureC(double temperatureC) {
    this.maxDischargeTemperatureC = temperatureC;
    return this;
  }

  /**
   * Sets the default minimum design metal temperature applied to valves without a per-unit override.
   *
   * @param temperatureC minimum design metal temperature in degrees Celsius
   * @return this holder for chaining
   */
  public HazopQuantificationLimits setMinDesignMetalTemperatureC(double temperatureC) {
    this.minDesignMetalTemperatureC = temperatureC;
    return this;
  }

  /**
   * Adds a per-unit maximum discharge temperature override.
   *
   * @param unitName the unit operation name the override applies to
   * @param temperatureC maximum discharge temperature in degrees Celsius for that unit
   * @return this holder for chaining
   */
  public HazopQuantificationLimits setMaxDischargeTemperatureC(String unitName, double temperatureC) {
    if (unitName != null) {
      maxDischargeTemperatureByUnit.put(unitName, Double.valueOf(temperatureC));
    }
    return this;
  }

  /**
   * Adds a per-unit minimum design metal temperature override.
   *
   * @param unitName the unit operation name the override applies to
   * @param temperatureC minimum design metal temperature in degrees Celsius for that unit
   * @return this holder for chaining
   */
  public HazopQuantificationLimits setMinDesignMetalTemperatureC(String unitName, double temperatureC) {
    if (unitName != null) {
      minDesignMetalTemperatureByUnit.put(unitName, Double.valueOf(temperatureC));
    }
    return this;
  }

  /**
   * Resolves the maximum discharge temperature limit for a unit, preferring a per-unit override over the default.
   *
   * @param unitName the unit operation name
   * @return the applicable maximum discharge temperature in degrees Celsius
   */
  public double maxDischargeTemperatureC(String unitName) {
    Double override = unitName == null ? null : maxDischargeTemperatureByUnit.get(unitName);
    return override != null ? override.doubleValue() : maxDischargeTemperatureC;
  }

  /**
   * Resolves the minimum design metal temperature limit for a unit, preferring a per-unit override over the default.
   *
   * @param unitName the unit operation name
   * @return the applicable minimum design metal temperature in degrees Celsius
   */
  public double minDesignMetalTemperatureC(String unitName) {
    Double override = unitName == null ? null : minDesignMetalTemperatureByUnit.get(unitName);
    return override != null ? override.doubleValue() : minDesignMetalTemperatureC;
  }

  /**
   * Produces an auditable basis string explaining the maximum discharge temperature limit applied to a unit, stating
   * the value, whether it is a per-unit override or the screening default, and the governing standard.
   *
   * @param unitName the unit operation name
   * @return a human-readable provenance string for the maximum discharge temperature limit
   */
  public String basisForMaxDischargeTemperature(String unitName) {
    boolean hasOverride = unitName != null && maxDischargeTemperatureByUnit.containsKey(unitName);
    double value = maxDischargeTemperatureC(unitName);
    if (hasOverride) {
      return String.format(java.util.Locale.ROOT,
          "Max discharge temperature %.1f C (per-unit override for '%s'; basis: equipment data sheet / API 617)", value,
          unitName);
    }
    return String.format(java.util.Locale.ROOT,
        "Max discharge temperature %.1f C (screening default; basis: API 617 / API 521)", value);
  }

  /**
   * Produces an auditable basis string explaining the minimum design metal temperature (MDMT) limit applied to a unit,
   * stating the value, whether it is a per-unit override or the screening default, and the governing standard.
   *
   * @param unitName the unit operation name
   * @return a human-readable provenance string for the minimum design metal temperature limit
   */
  public String basisForMinDesignMetalTemperature(String unitName) {
    boolean hasOverride = unitName != null && minDesignMetalTemperatureByUnit.containsKey(unitName);
    double value = minDesignMetalTemperatureC(unitName);
    if (hasOverride) {
      return String.format(java.util.Locale.ROOT,
          "MDMT %.1f C (per-unit override for '%s'; basis: material certificate / ASME UCS-66)", value, unitName);
    }
    return String.format(java.util.Locale.ROOT,
        "MDMT %.1f C (screening default; basis: ASME UCS-66 low-temperature carbon steel / API 521)", value);
  }

  /**
   * Serialise the effective limits and any per-unit overrides to a pretty-printed JSON object so the policy used for a
   * HAZOP screening run is auditable.
   *
   * @return JSON representation of the limits holder
   */
  public String toJson() {
    return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
