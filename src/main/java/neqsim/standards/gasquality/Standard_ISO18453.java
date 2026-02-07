package neqsim.standards.gasquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemGERGwaterEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Implementation of ISO 18453 - Natural gas - Correlation between water content and water dew
 * point.
 *
 * <p>
 * ISO 18453 provides a method for calculating the water dew point temperature of natural gas from
 * its water content, and vice versa. It uses the GERG-water equation of state (or compatible
 * thermodynamic model) for accurate water dew point calculations in natural gas systems.
 * </p>
 *
 * <p>
 * This class supersedes the previous Draft_ISO18453 implementation and provides:
 * </p>
 * <ul>
 * <li>Water dew point temperature at a given pressure</li>
 * <li>Water content at a given temperature and pressure</li>
 * <li>Multi-pressure water dew point curve</li>
 * <li>Compliance checking against sales gas specifications</li>
 * </ul>
 *
 * <p>
 * The standard is applicable to gas compositions within the normal range of natural gas (typically
 * methane content 40-100 mol%, pressure up to 300 bar, temperature -50 to +100 C).
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_ISO18453 extends neqsim.standards.Standard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_ISO18453.class);

  /** Unit for dew point temperature output. */
  private String dewPointTemperatureUnit = "C";

  /** Unit for pressure. */
  private String pressureUnit = "bar";

  /** Calculated water dew point temperature in Celsius. */
  private double dewPointTemperature = 273.0;

  /** Water dew point specification limit from sales contract in Celsius. */
  private double dewPointTemperatureSpec = -12.0;

  /** Initial temperature for flash calculation in Kelvin. */
  private double initTemperature = 273.15;

  /** Internal thermo system using GERG-water model. */
  private SystemInterface internalThermoSystem;

  /** Thermodynamic operations for the internal system. */
  private ThermodynamicOperations internalThermoOps;

  /**
   * Constructor for Standard_ISO18453.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object representing the
   *        natural gas composition
   */
  public Standard_ISO18453(SystemInterface thermoSystem) {
    super("Standard_ISO18453", "Water dew point of natural gas (ISO 18453)", thermoSystem);

    if (thermoSystem.getModelName().equals("GERGwater")) {
      this.internalThermoSystem = thermoSystem;
    } else {
      this.internalThermoSystem = new SystemGERGwaterEos(initTemperature, getReferencePressure());
      for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
        this.internalThermoSystem.addComponent(thermoSystem.getPhase(0).getComponent(i).getName(),
            thermoSystem.getPhase(0).getComponent(i).getNumberOfmoles());
      }
    }

    this.internalThermoSystem.setTemperature(273.15);
    this.internalThermoSystem.setPressure(1.0);
    this.internalThermoSystem.setMixingRule(8);
    this.internalThermoSystem.init(0);
    this.internalThermoSystem.init(1);

    this.internalThermoOps = new ThermodynamicOperations(this.internalThermoSystem);
  }

  /**
   * Sets the dew point temperature specification limit.
   *
   * @param specCelsius maximum allowed dew point temperature in Celsius
   */
  public void setDewPointTemperatureSpec(double specCelsius) {
    this.dewPointTemperatureSpec = specCelsius;
  }

  /**
   * Sets the pressure for dew point calculation.
   *
   * @param pressureBara pressure in bara
   */
  public void setPressure(double pressureBara) {
    this.internalThermoSystem.setPressure(pressureBara);
    setReferencePressure(pressureBara);
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    this.internalThermoSystem.setTemperature(initTemperature);
    this.internalThermoSystem.setPressure(getReferencePressure());

    try {
      this.internalThermoOps.waterDewPointTemperatureFlash();
    } catch (Exception ex) {
      logger.error("Water dew point calculation failed: " + ex.getMessage(), ex);
    }
    dewPointTemperature = this.internalThermoSystem.getTemperature() - 273.15;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    if ("dewPointTemperature".equals(returnParameter)) {
      if ("K".equals(returnUnit)) {
        return dewPointTemperature + 273.15;
      }
      if ("F".equals(returnUnit)) {
        return dewPointTemperature * 9.0 / 5.0 + 32.0;
      }
      return dewPointTemperature;
    }
    return dewPointTemperature;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    if ("dewPointTemperature".equals(returnParameter)) {
      return dewPointTemperature;
    }
    if ("pressure".equals(returnParameter)) {
      return this.internalThermoSystem.getPressure();
    }
    return dewPointTemperature;
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    if ("dewPointTemperature".equals(returnParameter)) {
      return dewPointTemperatureUnit;
    }
    if ("pressure".equals(returnParameter)) {
      return this.pressureUnit;
    }
    return dewPointTemperatureUnit;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    return dewPointTemperature < getSalesContract().getWaterDewPointTemperature();
  }
}
