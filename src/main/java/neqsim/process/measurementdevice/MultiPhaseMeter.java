package neqsim.process.measurementdevice;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * MultiPhaseMeter class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class MultiPhaseMeter extends StreamMeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(MultiPhaseMeter.class);

  double pressure = 1.01325;
  double temperature = 288.15;
  String unitT;
  String unitP;

  /**
   * <p>
   * Constructor for MultiPhaseMeter.
   * </p>
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
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
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
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
      return stream.getFlowRate(unit);
    }

    if (stream.getFlowRate("kg/hr") < 1e-10) {
      return Double.NaN;
    }

    if (measurement.equals("GOR")) {
      SystemInterface tempFluid = stream.getFluid().clone();
      tempFluid.setTemperature(temperature, unitT);
      tempFluid.setPressure(pressure, unitP);
      ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
      try {
        thermoOps.TPflash();
      } catch (Exception ex) {
        logger.error(ex.getMessage());
        return Double.NaN;
      }
      // tempFluid.display();
      if (!tempFluid.hasPhaseType("gas")) {
        return Double.NaN;
      }
      if (!tempFluid.hasPhaseType("oil")) {
        return Double.NaN;
      }
      tempFluid.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
      return tempFluid.getPhase("gas").getCorrectedVolume()
          / tempFluid.getPhase("oil").getCorrectedVolume();
    }
    if (measurement.equals("Gas Flow Rate")) {
      SystemInterface tempFluid = stream.getThermoSystem().clone();
      tempFluid.setTemperature(temperature, unitT);
      tempFluid.setPressure(pressure, unitP);
      ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
      try {
        thermoOps.TPflash();
      } catch (Exception ex) {
        logger.error(ex.getMessage());
        return Double.NaN;
      }
      // tempFluid.display();
      if (!tempFluid.hasPhaseType("gas")) {
        return Double.NaN;
      }
      tempFluid.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
      return tempFluid.getPhase("gas").getFlowRate(unit);
    }
    if (measurement.equals("Oil Flow Rate")) {
      SystemInterface tempFluid = stream.getThermoSystem().clone();
      tempFluid.setTemperature(temperature, unitT);
      tempFluid.setPressure(pressure, unitP);
      ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
      try {
        thermoOps.TPflash();
      } catch (Exception ex) {
        logger.error(ex.getMessage());
        return Double.NaN;
      }
      // tempFluid.display();
      if (!tempFluid.hasPhaseType("oil")) {
        return Double.NaN;
      }
      tempFluid.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
      return tempFluid.getPhase("oil").getFlowRate(unit);
    }
    if (measurement.equals("Water Flow Rate")) {
      SystemInterface tempFluid = stream.getThermoSystem().clone();
      tempFluid.setTemperature(temperature, unitT);
      tempFluid.setPressure(pressure, unitP);
      ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
      try {
        thermoOps.TPflash();
      } catch (Exception ex) {
        logger.error(ex.getMessage());
        return Double.NaN;
      }
      // tempFluid.display();
      if (!tempFluid.hasPhaseType("aqueous")) {
        return Double.NaN;
      }
      tempFluid.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
      return tempFluid.getPhase("aqueous").getFlowRate(unit);
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
        logger.error(ex.getMessage());
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
        } else {
          return tempFluid.getPhase("aqueous").getDensity("kg/m3");
        }
      }
      return 0.0;
    } else if (measurement.equals("GOR_std")) {
      SystemInterface tempFluid = stream.getThermoSystem().clone();

      tempFluid.setTemperature(15.0, "C");
      tempFluid.setPressure(ThermodynamicConstantsInterface.referencePressure, "bara");
      ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
      try {
        thermoOps.TPflash();
      } catch (Exception ex) {
        logger.error(ex.getMessage());
        return Double.NaN;
      }
      if (!tempFluid.hasPhaseType("gas")) {
        return Double.NaN;
      }
      if (!tempFluid.hasPhaseType("oil")) {
        return Double.NaN;
      }
      tempFluid.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);

      double GOR_in_sm3_sm3 = tempFluid.getPhase("gas").getFlowRate("Sm3/hr")
          / tempFluid.getPhase("oil").getFlowRate("m3/hr");
      double GOR_via_corrected_volume = tempFluid.getPhase("gas").getCorrectedVolume()
          / tempFluid.getPhase("oil").getCorrectedVolume();

      // System.out.println("Stream 2 (results inside MPM) " + " GOR sm3/sm3 " + GOR_in_sm3_sm3
      // + " GOR Corrected by volume " + GOR_via_corrected_volume);

      // System.out.println("Stream 2 (results inside MPM) getPhase(gas).getCorrectedVolume() "
      // + tempFluid.getPhase("gas").getCorrectedVolume());
      // System.out.println("Stream 2 (results inside MPM) getPhase(oil).getCorrectedVolume() "
      // + tempFluid.getPhase("oil").getCorrectedVolume());

      // GOR_via_corrected_volume and GOR_in_sm3_sm3 should not be so different ?
      return tempFluid.getPhase("gas").getCorrectedVolume()
          / tempFluid.getPhase("oil").getCorrectedVolume();
    } else {
      logger.warn("Measurement type " + measurement + " is not found");
      return 0.0;
    }
  }
}
