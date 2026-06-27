package neqsim.process.safety.overpressure;

import java.io.Serializable;

/**
 * A pressure-containing item protected by an overpressure protection device, with the pressure and temperature design
 * data required for TR3001 / API STD 521 relief evaluation.
 *
 * <p>
 * The maximum allowable working pressure (MAWP) is the basis for the accumulated-pressure acceptance limits (ASME VIII
 * Div 1 / TR3001 section 2). The pressure relief device set pressure defaults to the MAWP but may be set lower.
 * Built-up back pressure is used by downstream sizing.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class ProtectedItem implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String name;
  private double maximumAllowableWorkingPressureBara;
  private double reliefSetPressureBara;
  private double designTemperatureC = Double.NaN;
  private double backPressureBara = 1.01325;

  /**
   * Creates a protected item with a given name and MAWP. The relief device set pressure defaults to the MAWP.
   *
   * @param name the item name (vessel/equipment tag); not null
   * @param maximumAllowableWorkingPressureBara the MAWP in bara; must be positive
   */
  public ProtectedItem(String name, double maximumAllowableWorkingPressureBara) {
    this.name = name;
    this.maximumAllowableWorkingPressureBara = maximumAllowableWorkingPressureBara;
    this.reliefSetPressureBara = maximumAllowableWorkingPressureBara;
  }

  /**
   * Gets the item name.
   *
   * @return the item name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the maximum allowable working pressure (MAWP).
   *
   * @return the MAWP in bara
   */
  public double getMaximumAllowableWorkingPressureBara() {
    return maximumAllowableWorkingPressureBara;
  }

  /**
   * Sets the maximum allowable working pressure (MAWP).
   *
   * @param maximumAllowableWorkingPressureBara the MAWP in bara; must be positive
   * @return this item for chaining
   */
  public ProtectedItem setMaximumAllowableWorkingPressureBara(double maximumAllowableWorkingPressureBara) {
    this.maximumAllowableWorkingPressureBara = maximumAllowableWorkingPressureBara;
    return this;
  }

  /**
   * Gets the pressure relief device set pressure.
   *
   * @return the set pressure in bara
   */
  public double getReliefSetPressureBara() {
    return reliefSetPressureBara;
  }

  /**
   * Sets the pressure relief device set pressure.
   *
   * @param reliefSetPressureBara the set pressure in bara; must be positive and not exceed the MAWP
   * @return this item for chaining
   */
  public ProtectedItem setReliefSetPressureBara(double reliefSetPressureBara) {
    this.reliefSetPressureBara = reliefSetPressureBara;
    return this;
  }

  /**
   * Gets the design temperature.
   *
   * @return the design temperature in degrees Celsius, or NaN if not set
   */
  public double getDesignTemperatureC() {
    return designTemperatureC;
  }

  /**
   * Sets the design temperature.
   *
   * @param designTemperatureC the design temperature in degrees Celsius
   * @return this item for chaining
   */
  public ProtectedItem setDesignTemperatureC(double designTemperatureC) {
    this.designTemperatureC = designTemperatureC;
    return this;
  }

  /**
   * Gets the built-up/total back pressure at the relief device outlet.
   *
   * @return the back pressure in bara
   */
  public double getBackPressureBara() {
    return backPressureBara;
  }

  /**
   * Sets the built-up/total back pressure at the relief device outlet.
   *
   * @param backPressureBara the back pressure in bara; must be non-negative
   * @return this item for chaining
   */
  public ProtectedItem setBackPressureBara(double backPressureBara) {
    this.backPressureBara = backPressureBara;
    return this;
  }
}
