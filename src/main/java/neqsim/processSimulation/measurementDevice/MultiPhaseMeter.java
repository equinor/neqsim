package neqsim.processSimulation.measurementDevice;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * MultiPhaseMeter class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class MultiPhaseMeter extends MeasurementDeviceBaseClass {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(MultiPhaseMeter.class);

  protected StreamInterface stream = null;

  double pressure = 10.0;
  double temperature = 298.15;
  String unitT;
  String unitP;

  /**
   * <p>
   * Constructor for MultiPhaseMeter.
   * </p>
   */
  public MultiPhaseMeter() {
    name = "Multi Phase Meter";
  }

  /**
   * <p>
   * Constructor for MultiPhaseMeter.
   * </p>
   *
   * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
   */
  public MultiPhaseMeter(StreamInterface stream) {
    this();
    name = "Multi Phase Meter";
    this.stream = stream;
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
    this();
    this.name = name;
    this.stream = stream;
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
  public double getMeasuredValue() {
    return stream.getThermoSystem().getFlowRate("kg/hr");
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String measurement) {
    if (measurement.equals("mass rate")) {
      return stream.getThermoSystem().getFlowRate("kg/hr");
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
      return 0.0;
    }
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    SystemInterface testFluid = new SystemSrkEos(338.15, 50.0);
    testFluid.addComponent("nitrogen", 1.205);
    testFluid.addComponent("CO2", 1.340);
    testFluid.addComponent("methane", 87.974);
    testFluid.addComponent("ethane", 5.258);
    testFluid.addComponent("propane", 3.283);
    testFluid.addComponent("i-butane", 0.082);
    testFluid.addComponent("n-butane", 0.487);
    testFluid.addComponent("i-pentane", 0.056);
    testFluid.addComponent("n-pentane", 1.053);
    testFluid.addComponent("nC10", 4.053);
    testFluid.setMixingRule(2);
    testFluid.setMultiPhaseCheck(true);

    testFluid.setTemperature(24.0, "C");
    testFluid.setPressure(48.0, "bara");
    testFluid.setTotalFlowRate(4.5, "MSm3/day");

    Stream stream_1 = new Stream("Stream1", testFluid);

    MultiPhaseMeter multiPhaseMeter = new MultiPhaseMeter("test", stream_1);
    multiPhaseMeter.setTemperature(90.0, "C");
    multiPhaseMeter.setPressure(60.0, "bara");

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(stream_1);
    operations.add(multiPhaseMeter);
    operations.run();
    System.out.println("GOR " + multiPhaseMeter.getMeasuredValue("GOR"));
    System.out.println("GOR_std " + multiPhaseMeter.getMeasuredValue("GOR_std"));
  }
}
