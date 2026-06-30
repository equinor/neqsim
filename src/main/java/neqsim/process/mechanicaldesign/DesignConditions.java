package neqsim.process.mechanicaldesign;

import java.io.Serializable;
import com.google.gson.GsonBuilder;

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
   * Gets the design pressure (maximum allowable working pressure, MAWP).
   *
   * @return design pressure in bara, or {@link Double#NaN} if not set
   */
  public double getDesignPressure() {
    return designPressureBara;
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
   * Gets the maximum design temperature.
   *
   * @return maximum design temperature in degrees Celsius, or {@link Double#NaN} if not set
   */
  public double getMaxDesignTemperature() {
    return maxDesignTemperatureC;
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
   * Gets the minimum design metal temperature (MDMT).
   *
   * @return minimum design metal temperature in degrees Celsius, or {@link Double#NaN} if not set
   */
  public double getMinDesignTemperature() {
    return minDesignTemperatureC;
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
   * Gets the relief (PSV) set pressure protecting the equipment.
   *
   * @return relief set pressure in bara, or {@link Double#NaN} if not set
   */
  public double getReliefSetPressure() {
    return reliefSetPressureBara;
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
   * Gets the corrosion allowance.
   *
   * @return corrosion allowance in mm, or {@link Double#NaN} if not set
   */
  public double getCorrosionAllowance() {
    return corrosionAllowanceMm;
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
