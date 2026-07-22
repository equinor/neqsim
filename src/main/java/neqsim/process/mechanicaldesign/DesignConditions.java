package neqsim.process.mechanicaldesign;

import java.io.Serializable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import com.google.gson.GsonBuilder;
import neqsim.process.mechanicaldesign.DesignConditionValue.Type;

/**
 * Lightweight holder for the explicit nameplate design conditions of a piece of process equipment.
 *
 * <p>
 * Where {@link MechanicalDesign} carries the <em>computed</em> mechanical result of a sizing run (wall thickness,
 * weight, sized diameters), this class carries the small set of <em>declared</em> engineering-data-sheet values that a
 * P&amp;ID-driven HAZOP or safety review needs regardless of whether a sizing calculation has been run:
 * </p>
 *
 * <ul>
 * <li><b>Design pressure</b> — the maximum allowable working pressure (MAWP) from the data sheet.</li>
 * <li><b>Maximum / minimum design temperature</b> — the upper design temperature and the minimum design metal
 * temperature (MDMT) the material remains tough at.</li>
 * <li><b>Relief set pressure</b> — the set pressure of the protecting pressure safety valve (PSV).</li>
 * <li><b>Construction material</b> — the nameplate material specification.</li>
 * <li><b>Corrosion allowance</b> — the corrosion allowance from the data sheet.</li>
 * <li><b>Failure action</b> — the fail-safe position of a valve actuator on loss of motive power/signal.</li>
 * </ul>
 *
 * <p>
 * Every value is optional. Numeric values default to {@link Double#NaN} and {@code isXxxSet()} helpers report whether a
 * value has been supplied, so a consumer (for example the DEXPI writer) can export only the populated attributes. The
 * holder is {@link Serializable} so it can be stored directly on a process equipment item and survive cloning.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class DesignConditions implements Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * Fail-safe position a valve actuator moves to on loss of motive power or control signal.
   */
  public enum FailureAction {
    /** Valve closes on failure (fail-closed). */
    FAIL_CLOSED,
    /** Valve opens on failure (fail-open). */
    FAIL_OPEN,
    /** Valve stays in its last position on failure (fail-last / fail-as-is). */
    FAIL_LAST,
    /** Failure action is known to be undefined / indeterminate. */
    FAIL_INDETERMINATE,
    /** No failure action has been specified. */
    NOT_SPECIFIED
  }

  private double designPressureBara = Double.NaN;
  private double maxDesignTemperatureC = Double.NaN;
  private double minDesignTemperatureC = Double.NaN;
  private double reliefSetPressureBara = Double.NaN;
  private double corrosionAllowanceMm = Double.NaN;
  private String constructionMaterial = null;
  private FailureAction failureAction = FailureAction.NOT_SPECIFIED;

  /**
   * Construct an empty design-conditions holder with no values set.
   */
  public DesignConditions() {
  }

  /**
   * Sets the design pressure (maximum allowable working pressure, MAWP).
   *
   * @param bara design pressure in bara
   * @return this holder for chaining
   */
  public DesignConditions setDesignPressure(double bara) {
    this.designPressureBara = bara;
    return this;
  }

  /**
   * Sets the design pressure using an explicit absolute or gauge pressure unit.
   *
   * @param value design pressure
   * @param unit pressure unit supported by {@code PressureUnit}
   * @return this holder for chaining
   */
  public DesignConditions setDesignPressure(double value, String unit) {
    return setCondition(DesignConditionValue.of(Type.DESIGN_PRESSURE, value, unit));
  }

  /**
   * Gets the design pressure (maximum allowable working pressure, MAWP).
   *
   * @return design pressure in bara, or {@link Double#NaN} if not set
   */
  public double getDesignPressure() {
    return designPressureBara;
  }

  /**
   * Gets the design pressure in an explicit pressure unit.
   *
   * @param unit requested pressure unit
   * @return converted pressure, or {@link Double#NaN} if not set
   */
  public double getDesignPressure(String unit) {
    return convertIfSet(Type.DESIGN_PRESSURE, designPressureBara, unit);
  }

  /**
   * Indicates whether a design pressure has been set.
   *
   * @return true if a design pressure value has been supplied
   */
  public boolean isDesignPressureSet() {
    return !Double.isNaN(designPressureBara);
  }

  /**
   * Sets the maximum design temperature.
   *
   * @param celsius maximum design temperature in degrees Celsius
   * @return this holder for chaining
   */
  public DesignConditions setMaxDesignTemperature(double celsius) {
    this.maxDesignTemperatureC = celsius;
    return this;
  }

  /**
   * Sets the maximum design temperature using an explicit temperature unit.
   *
   * @param value maximum design temperature
   * @param unit temperature unit supported by {@code TemperatureUnit}
   * @return this holder for chaining
   */
  public DesignConditions setMaxDesignTemperature(double value, String unit) {
    return setCondition(DesignConditionValue.of(Type.MAX_DESIGN_TEMPERATURE, value, unit));
  }

  /**
   * Gets the maximum design temperature.
   *
   * @return maximum design temperature in degrees Celsius, or {@link Double#NaN} if not set
   */
  public double getMaxDesignTemperature() {
    return maxDesignTemperatureC;
  }

  /**
   * Gets the maximum design temperature in an explicit unit.
   *
   * @param unit requested temperature unit
   * @return converted temperature, or {@link Double#NaN} if not set
   */
  public double getMaxDesignTemperature(String unit) {
    return convertIfSet(Type.MAX_DESIGN_TEMPERATURE, maxDesignTemperatureC, unit);
  }

  /**
   * Indicates whether a maximum design temperature has been set.
   *
   * @return true if a maximum design temperature value has been supplied
   */
  public boolean isMaxDesignTemperatureSet() {
    return !Double.isNaN(maxDesignTemperatureC);
  }

  /**
   * Sets the minimum design metal temperature (MDMT).
   *
   * @param celsius minimum design metal temperature in degrees Celsius
   * @return this holder for chaining
   */
  public DesignConditions setMinDesignTemperature(double celsius) {
    this.minDesignTemperatureC = celsius;
    return this;
  }

  /**
   * Sets the minimum design metal temperature using an explicit temperature unit.
   *
   * @param value minimum design metal temperature
   * @param unit temperature unit supported by {@code TemperatureUnit}
   * @return this holder for chaining
   */
  public DesignConditions setMinDesignTemperature(double value, String unit) {
    return setCondition(DesignConditionValue.of(Type.MIN_DESIGN_TEMPERATURE, value, unit));
  }

  /**
   * Gets the minimum design metal temperature (MDMT).
   *
   * @return minimum design metal temperature in degrees Celsius, or {@link Double#NaN} if not set
   */
  public double getMinDesignTemperature() {
    return minDesignTemperatureC;
  }

  /**
   * Gets the minimum design metal temperature in an explicit unit.
   *
   * @param unit requested temperature unit
   * @return converted temperature, or {@link Double#NaN} if not set
   */
  public double getMinDesignTemperature(String unit) {
    return convertIfSet(Type.MIN_DESIGN_TEMPERATURE, minDesignTemperatureC, unit);
  }

  /**
   * Indicates whether a minimum design metal temperature has been set.
   *
   * @return true if a minimum design metal temperature value has been supplied
   */
  public boolean isMinDesignTemperatureSet() {
    return !Double.isNaN(minDesignTemperatureC);
  }

  /**
   * Sets the relief (PSV) set pressure protecting the equipment.
   *
   * @param bara relief set pressure in bara
   * @return this holder for chaining
   */
  public DesignConditions setReliefSetPressure(double bara) {
    this.reliefSetPressureBara = bara;
    return this;
  }

  /**
   * Sets the relief set pressure using an explicit absolute or gauge pressure unit.
   *
   * @param value relief set pressure
   * @param unit pressure unit supported by {@code PressureUnit}
   * @return this holder for chaining
   */
  public DesignConditions setReliefSetPressure(double value, String unit) {
    return setCondition(DesignConditionValue.of(Type.RELIEF_SET_PRESSURE, value, unit));
  }

  /**
   * Gets the relief (PSV) set pressure protecting the equipment.
   *
   * @return relief set pressure in bara, or {@link Double#NaN} if not set
   */
  public double getReliefSetPressure() {
    return reliefSetPressureBara;
  }

  /**
   * Gets the relief set pressure in an explicit pressure unit.
   *
   * @param unit requested pressure unit
   * @return converted pressure, or {@link Double#NaN} if not set
   */
  public double getReliefSetPressure(String unit) {
    return convertIfSet(Type.RELIEF_SET_PRESSURE, reliefSetPressureBara, unit);
  }

  /**
   * Indicates whether a relief set pressure has been set.
   *
   * @return true if a relief set pressure value has been supplied
   */
  public boolean isReliefSetPressureSet() {
    return !Double.isNaN(reliefSetPressureBara);
  }

  /**
   * Sets the corrosion allowance.
   *
   * @param millimetre corrosion allowance in mm
   * @return this holder for chaining
   */
  public DesignConditions setCorrosionAllowance(double millimetre) {
    this.corrosionAllowanceMm = millimetre;
    return this;
  }

  /**
   * Sets the corrosion allowance using an explicit length unit.
   *
   * @param value corrosion allowance
   * @param unit length unit supported by {@code LengthUnit}
   * @return this holder for chaining
   */
  public DesignConditions setCorrosionAllowance(double value, String unit) {
    return setCondition(DesignConditionValue.of(Type.CORROSION_ALLOWANCE, value, unit));
  }

  /**
   * Gets the corrosion allowance.
   *
   * @return corrosion allowance in mm, or {@link Double#NaN} if not set
   */
  public double getCorrosionAllowance() {
    return corrosionAllowanceMm;
  }

  /**
   * Gets the corrosion allowance in an explicit length unit.
   *
   * @param unit requested length unit
   * @return converted allowance, or {@link Double#NaN} if not set
   */
  public double getCorrosionAllowance(String unit) {
    return convertIfSet(Type.CORROSION_ALLOWANCE, corrosionAllowanceMm, unit);
  }

  /**
   * Indicates whether a corrosion allowance has been set.
   *
   * @return true if a corrosion allowance value has been supplied
   */
  public boolean isCorrosionAllowanceSet() {
    return !Double.isNaN(corrosionAllowanceMm);
  }

  /**
   * Sets the construction material specification.
   *
   * @param material the nameplate construction material, for example "Carbon steel" or "Duplex 22Cr"
   * @return this holder for chaining
   */
  public DesignConditions setConstructionMaterial(String material) {
    this.constructionMaterial = material;
    return this;
  }

  /**
   * Gets the construction material specification.
   *
   * @return the construction material, or null if not set
   */
  public String getConstructionMaterial() {
    return constructionMaterial;
  }

  /**
   * Indicates whether a construction material has been set.
   *
   * @return true if a non-empty construction material has been supplied
   */
  public boolean isConstructionMaterialSet() {
    return constructionMaterial != null && !constructionMaterial.trim().isEmpty();
  }

  /**
   * Sets the valve fail-safe action.
   *
   * @param action the fail-safe action; null is treated as {@link FailureAction#NOT_SPECIFIED}
   * @return this holder for chaining
   */
  public DesignConditions setFailureAction(FailureAction action) {
    this.failureAction = action != null ? action : FailureAction.NOT_SPECIFIED;
    return this;
  }

  /**
   * Gets the valve fail-safe action.
   *
   * @return the fail-safe action, never null
   */
  public FailureAction getFailureAction() {
    return failureAction;
  }

  /**
   * Indicates whether a fail-safe action has been specified.
   *
   * @return true if the fail-safe action is anything other than {@link FailureAction#NOT_SPECIFIED}
   */
  public boolean isFailureActionSet() {
    return failureAction != null && failureAction != FailureAction.NOT_SPECIFIED;
  }

  /**
   * Set one immutable typed design condition.
   *
   * @param condition typed value to store
   * @return this holder for chaining
   */
  public DesignConditions setCondition(DesignConditionValue condition) {
    if (condition == null) {
      throw new IllegalArgumentException("condition cannot be null");
    }
    switch (condition.getType()) {
    case DESIGN_PRESSURE:
      return setDesignPressure(condition.getCanonicalValue());
    case MAX_DESIGN_TEMPERATURE:
      return setMaxDesignTemperature(condition.getCanonicalValue());
    case MIN_DESIGN_TEMPERATURE:
      return setMinDesignTemperature(condition.getCanonicalValue());
    case RELIEF_SET_PRESSURE:
      return setReliefSetPressure(condition.getCanonicalValue());
    case CORROSION_ALLOWANCE:
      return setCorrosionAllowance(condition.getCanonicalValue());
    default:
      throw new IllegalStateException("Unsupported design condition " + condition.getType());
    }
  }

  /**
   * Get one typed condition when it has been declared.
   *
   * @param type condition type
   * @return typed value, or empty when the condition is unset
   */
  public Optional<DesignConditionValue> getCondition(Type type) {
    if (type == null) {
      throw new IllegalArgumentException("type cannot be null");
    }
    switch (type) {
    case DESIGN_PRESSURE:
      return conditionIfSet(type, designPressureBara);
    case MAX_DESIGN_TEMPERATURE:
      return conditionIfSet(type, maxDesignTemperatureC);
    case MIN_DESIGN_TEMPERATURE:
      return conditionIfSet(type, minDesignTemperatureC);
    case RELIEF_SET_PRESSURE:
      return conditionIfSet(type, reliefSetPressureBara);
    case CORROSION_ALLOWANCE:
      return conditionIfSet(type, corrosionAllowanceMm);
    default:
      throw new IllegalStateException("Unsupported design condition " + type);
    }
  }

  /**
   * Get an immutable snapshot of all declared typed numeric conditions.
   *
   * @return map keyed by condition type
   */
  public Map<Type, DesignConditionValue> getConditions() {
    Map<Type, DesignConditionValue> result = new EnumMap<Type, DesignConditionValue>(Type.class);
    for (Type type : Type.values()) {
      Optional<DesignConditionValue> condition = getCondition(type);
      if (condition.isPresent()) {
        result.put(type, condition.get());
      }
    }
    return Collections.unmodifiableMap(result);
  }

  private Optional<DesignConditionValue> conditionIfSet(Type type, double canonicalValue) {
    if (Double.isNaN(canonicalValue)) {
      return Optional.empty();
    }
    return Optional.of(DesignConditionValue.of(type, canonicalValue, type.getCanonicalUnit()));
  }

  private double convertIfSet(Type type, double canonicalValue, String unit) {
    if (Double.isNaN(canonicalValue)) {
      return Double.NaN;
    }
    return DesignConditionValue.of(type, canonicalValue, type.getCanonicalUnit()).getValue(unit);
  }

  /**
   * Indicates whether any design condition value has been supplied.
   *
   * @return true if at least one design condition has been set
   */
  public boolean isEmpty() {
    return !(isDesignPressureSet() || isMaxDesignTemperatureSet() || isMinDesignTemperatureSet()
        || isReliefSetPressureSet() || isCorrosionAllowanceSet() || isConstructionMaterialSet()
        || isFailureActionSet());
  }

  /**
   * Serialise these design conditions to a pretty-printed JSON object.
   *
   * @return JSON representation of the design conditions
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
