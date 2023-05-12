package neqsim.processSimulation.measurementDevice;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * MultiPhaseMeter class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class MultiPhaseMeter extends StreamMeasurementDeviceBaseClass {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(MultiPhaseMeter.class);

  double pressure = 10.0;
  double temperature = 298.15;
  String unitT;
  String unitP;

  /**
   * <p>
   * Constructor for MultiPhaseMeter.
   * </p>
   *
   * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
   */
  public MultiPhaseMeter(StreamInterface stream) {
    this("Multi Phase Meter", stream);
  }

  /**
   * <p>
   * Constructor for MultiPhaseMeter.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
   */
  public MultiPhaseMeter(String name, StreamInterface stream) {
    super(name, "kg/hr", stream);
  }

  /**
   * <p>
   * Getter for the field <code>pressure</code>.
   * </p>
   *
   * @return a double
   */
  public double getPressure() {
    return pressure;
  }

  /**
   * <p>
   * Setter for the field <code>pressure</code>.
   * </p>
   *
   * @param pressure a double
   * @param unitP a {@link java.lang.String} object
   */
  public void setPressure(double pressure, String unitP) {
    this.pressure = pressure;
    this.unitP = unitP;
  }

  /**
   * <p>
   * getTemperature.
   * </p>
   *
   * @return a double
   */
  public double getTemperature() {
    return temperature;
  }

  /**
   * <p>
   * Setter for the field <code>temperature</code>.
   * </p>
   *
   * @param temperature a double
   * @param unitT a {@link java.lang.String} object
   */
  public void setTemperature(double temperature, String unitT) {
    this.temperature = temperature;
    this.unitT = unitT;
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    return stream.getThermoSystem().getFlowRate(unit);
  }

  /**
   * Get specific measurement type. Supports "mass rate", "GOR", "gasDensity", "oilDensity",
   * "waterDensity" and "GOR_std".
   *
   * @param measurement Measurement value to get.
   * @param unit Unit to get value in
   * @return Measured value
   */
  public double getMeasuredValue(String measurement, String unit) {
    if (measurement.equals("mass rate")) {
      return stream.getThermoSystem().getFlowRate(unit);
    }

    if (stream.getThermoSystem().getFlowRate("kg/hr") < 1e-10) {
      return Double.NaN;
    }

    if (measurement.equals("GOR")) {
      SystemInterface tempFluid = stream.getThermoSystem().clone();
      tempFluid.setTemperature(temperature, unitT);
      tempFluid.setPressure(pressure, unitP);
      ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
      try {
        thermoOps.TPflash();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
        return Double.NaN;
      }
      // tempFluid.display();
      if (!tempFluid.hasPhaseType("gas")) {
        return Double.NaN;
      }
      if (!tempFluid.hasPhaseType("oil")) {
        return Double.NaN;
      }
      tempFluid.initPhysicalProperties("density");
      return tempFluid.getPhase("gas").getCorrectedVolume()
          / tempFluid.getPhase("oil").getCorrectedVolume();
    }
    if (measurement.equals("gasDensity") || measurement.equals("oilDensity")
        || measurement.equals("waterDensity")) {
      SystemInterface tempFluid = stream.getThermoSystem().clone();
      tempFluid.setTemperature(temperature, unitT);
      tempFluid.setPressure(pressure, unitP);
      ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
      try {
        thermoOps.TPflash();
      } catch (Exception ex) {
        logger.error(ex.getStackTrace());
        return Double.NaN;
      }
      tempFluid.initPhysicalProperties();
      if (measurement.equals("gasDensity")) {
        if (!tempFluid.hasPhaseType("gas")) {
          return 0.0;
        } else {
          return tempFluid.getPhase("gas").getDensity("kg/m3");
        }
      }
      if (measurement.equals("oilDensity")) {
        if (!tempFluid.hasPhaseType("oil")) {
          return 0.0;
        } else {
          return tempFluid.getPhase("oil").getDensity("kg/m3");
        }
      }
      if (measurement.equals("waterDensity")) {
        if (!tempFluid.hasPhaseType("aqueous")) {
          return 0.0;
        } else {
          return tempFluid.getPhase("aqueous").getDensity("kg/m3");
        }
      }
      return 0.0;
    } else if (measurement.equals("GOR_std")) {
      SystemInterface tempFluid = stream.getThermoSystem().clone();
      tempFluid.setTemperature(15.0, "C");
      tempFluid.setPressure(1.01325, "bara");
      ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
      try {
        thermoOps.TPflash();
      } catch (Exception ex) {
        logger.error(ex.getStackTrace());
        return Double.NaN;
      }
      if (!tempFluid.hasPhaseType("gas")) {
        return Double.NaN;
      }
      if (!tempFluid.hasPhaseType("oil")) {
        return Double.NaN;
      }
      tempFluid.initPhysicalProperties("density");
      return tempFluid.getPhase("gas").getCorrectedVolume()
          / tempFluid.getPhase("oil").getCorrectedVolume();
    } else {
      logger.warn("Measurement type " + measurement + " is not found");
      return 0.0;
    }
  }
}
