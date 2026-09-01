package neqsim.process.equipment.energy;

import java.io.Serializable;
import com.google.gson.GsonBuilder;

/**
 * Immutable thermodynamic state used to convert a thermal-utility duty into mass flow.
 *
 * <p>
 * The state deliberately stores specific enthalpy explicitly rather than embedding an approximate steam or water
 * correlation. Callers can therefore obtain properties from NeqSim, vendor data, or another qualified property package
 * and still use one consistent utility-network balance.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class ThermalUtilityState implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final double temperature;
  private final double pressure;
  private final double specificEnthalpy;

  /**
   * Creates one utility state.
   *
   * @param temperature temperature in K
   * @param pressure pressure in Pa
   * @param specificEnthalpy specific enthalpy in J/kg
   */
  public ThermalUtilityState(double temperature, double pressure, double specificEnthalpy) {
    if (!Double.isFinite(temperature) || temperature <= 0.0) {
      throw new IllegalArgumentException("Utility-state temperature must be positive and finite");
    }
    if (!Double.isFinite(pressure) || pressure <= 0.0) {
      throw new IllegalArgumentException("Utility-state pressure must be positive and finite");
    }
    if (!Double.isFinite(specificEnthalpy)) {
      throw new IllegalArgumentException("Utility-state specific enthalpy must be finite");
    }
    this.temperature = temperature;
    this.pressure = pressure;
    this.specificEnthalpy = specificEnthalpy;
  }

  /**
   * Gets temperature.
   *
   * @return temperature in K
   */
  public double getTemperature() {
    return temperature;
  }

  /**
   * Gets pressure.
   *
   * @return pressure in Pa
   */
  public double getPressure() {
    return pressure;
  }

  /**
   * Gets specific enthalpy.
   *
   * @return specific enthalpy in J/kg
   */
  public double getSpecificEnthalpy() {
    return specificEnthalpy;
  }

  /**
   * Serializes the state as JSON.
   *
   * @return JSON state
   */
  public String toJson() {
    return new GsonBuilder().create().toJson(this);
  }
}
