package neqsim.process.equipment.refrigeration;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable thermodynamic state point of a single-component refrigerant.
 *
 * <p>
 * Enthalpy and entropy are mass specific and share the reference state of the equation of state that produced them, so
 * differences between two states of the same {@link RefrigerantFluid} are meaningful while absolute values are not.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class RefrigerantState implements Serializable {
  /** Serialization version identifier. */
  private static final long serialVersionUID = 1000L;

  /** Pressure in bara. */
  private final double pressure;

  /** Temperature in K. */
  private final double temperature;

  /** Specific enthalpy in J/kg. */
  private final double enthalpy;

  /** Specific entropy in J/(kg K). */
  private final double entropy;

  /** Density in kg/m3. */
  private final double density;

  /** Vapour mass fraction, 0 for liquid and 1 for vapour. */
  private final double vaporFraction;

  /**
   * Creates a state point.
   *
   * @param pressure pressure in bara
   * @param temperature temperature in K
   * @param enthalpy specific enthalpy in J/kg
   * @param entropy specific entropy in J/(kg K)
   * @param density density in kg/m3
   * @param vaporFraction vapour mass fraction between 0 and 1
   */
  public RefrigerantState(double pressure, double temperature, double enthalpy, double entropy, double density,
      double vaporFraction) {
    this.pressure = pressure;
    this.temperature = temperature;
    this.enthalpy = enthalpy;
    this.entropy = entropy;
    this.density = density;
    this.vaporFraction = vaporFraction;
  }

  /**
   * Gets the pressure.
   *
   * @return pressure in bara
   */
  public double getPressure() {
    return pressure;
  }

  /**
   * Gets the temperature.
   *
   * @return temperature in K
   */
  public double getTemperature() {
    return temperature;
  }

  /**
   * Gets the specific enthalpy.
   *
   * @return enthalpy in J/kg
   */
  public double getEnthalpy() {
    return enthalpy;
  }

  /**
   * Gets the specific entropy.
   *
   * @return entropy in J/(kg K)
   */
  public double getEntropy() {
    return entropy;
  }

  /**
   * Gets the density.
   *
   * @return density in kg/m3
   */
  public double getDensity() {
    return density;
  }

  /**
   * Gets the vapour mass fraction.
   *
   * @return vapour mass fraction, 0 for liquid and 1 for vapour
   */
  public double getVaporFraction() {
    return vaporFraction;
  }

  /**
   * Builds an ordered map of the state for reporting.
   *
   * @return map with pressure in bara, temperature in C, enthalpy in kJ/kg, entropy in kJ/(kg K), density in kg/m3 and
   * the vapour mass fraction
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("pressure_bara", pressure);
    map.put("temperature_C", temperature - 273.15);
    map.put("enthalpy_kJ_per_kg", enthalpy / 1000.0);
    map.put("entropy_kJ_per_kgK", entropy / 1000.0);
    map.put("density_kg_per_m3", density);
    map.put("vaporFraction", vaporFraction);
    return map;
  }
}
