package neqsim.processSimulation.processEquipment.util;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.processSimulation.processEquipment.TwoPortEquipment;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * FlowSetter class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class FlowSetter extends TwoPortEquipment {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(GORfitter.class);

  double pressure = 1.01325;
  double temperature = 15.0;
  String unitT = "C";
  String unitP = "bara";

  private double gasFlowRate;
  private double oilFlowRate;
  private double waterFlowRate;
  String unitGasFlowRate = "Sm3/day";
  String unitOilFlowRate = "m3/hr";
  String unitWaterFlowRate = "m3/hr";

  @Deprecated
  /**
   * <p>
   * Constructor for FlowSetter.
   * </p>
   */
  public FlowSetter() {
    super("Flow Setter");
  }

  /**
   * <p>
   * Constructor for FlowSetter.
   * </p>
   *
   * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
   */
  @Deprecated
  public FlowSetter(StreamInterface stream) {
    this("Flow Setter", stream);
  }

  /**
   * <p>
   * Constructor for FlowSetter.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
   */
  public FlowSetter(String name, StreamInterface stream) {
    super(name, stream);
  }


  /**
   * {@inheritDoc}
   *
   * <p>
   * Setter for the field <code>inletStream</code>.
   * </p>
   */
  public void setInletStream(StreamInterface inletStream) {
    this.inStream = inletStream;
    try {
      this.outStream = inletStream.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
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

  /**
   * <p>
   * Get setGasFlowRate
   * </p>
   * 
   * @param flowRate flow rate
   * @param flowUnit Supported units are Sm3/sec, Sm3/hr, Sm3/day, MSm3/day
   * 
   */
  public void setGasFlowRate(double flowRate, String flowUnit) {
    double conversionFactor = 1.0;
    switch (flowUnit) {
      case "Sm3/sec":
        conversionFactor = 1.0;
        break;
      case "Sm3/hr":
        conversionFactor = 1.0 / 3600.0;
        break;
      case "Sm3/day":
        conversionFactor = 1.0 / 3600.0 / 24.0;
        break;
      case "MSm3/day":
        conversionFactor = 1.0 / 3600.0 / 24.0 * 1e6;
        break;
      default:
        throw new RuntimeException("unit not supported " + flowUnit);
    }
    gasFlowRate = flowRate * conversionFactor;
  }

  /**
   * <p>
   * Get getGasFlowRate
   * </p>
   * 
   * @param flowUnit Supported units are Sm3/sec, Sm3/hr, Sm3/day, MSm3/day
   * @return gas flow rate in unit sm3/sec
   */
  public double getGasFlowRate(String flowUnit) {
    double conversionFactor = 1.0;
    switch (flowUnit) {
      case "Sm3/sec":
        conversionFactor = 1.0;
        break;
      case "Sm3/hr":
        conversionFactor = 1.0 / 3600.0;
        break;
      case "Sm3/day":
        conversionFactor = 1.0 / 3600.0 / 24.0;
        break;
      case "MSm3/day":
        conversionFactor = 1.0 / 3600.0 / 24.0 / 1e6;
        break;
      default:
        throw new RuntimeException("unit not supported " + flowUnit);
    }
    return gasFlowRate * conversionFactor;
  }

  /**
   * <p>
   * Get setOilFlowRate
   * </p>
   * 
   * @param flowRate flow rate
   * @param flowUnit Supported units are m3/sec, m3/hr, m3/day
   * 
   */
  public void setOilFlowRate(double flowRate, String flowUnit) {
    double conversionFactor = 1.0;
    switch (flowUnit) {
      case "m3/sec":
        conversionFactor = 1.0;
        break;
      case "m3/hr":
        conversionFactor = 1.0 / 3600.0;
        break;
      case "m3/day":
        conversionFactor = 1.0 / 3600.0 / 24.0;
        break;
      default:
        throw new RuntimeException("unit not supported " + flowUnit);
    }
    oilFlowRate = flowRate * conversionFactor;
  }

  /**
   * <p>
   * Get getOilFlowRate
   * </p>
   * 
   * @param flowUnit Supported units are m3/sec, m3/hr, m3/day
   * @return oil flow rate in unit m3/sec
   */
  public double getOilFlowRate(String flowUnit) {
    double conversionFactor = 1.0;
    switch (flowUnit) {
      case "m3/sec":
        conversionFactor = 1.0;
        break;
      case "m3/hr":
        conversionFactor = 1.0 / 3600.0;
        break;
      case "m3/day":
        conversionFactor = 1.0 / 3600.0 / 24.0;
        break;
      default:
        throw new RuntimeException("unit not supported " + flowUnit);
    }
    return oilFlowRate * conversionFactor;
  }

  /**
   * <p>
   * Get setWaterFlowRate
   * </p>
   * 
   * @param flowRate flow rate
   * @param flowUnit Supported units are m3/sec, m3/hr, m3/day
   * 
   */
  public void setWaterFlowRate(double flowRate, String flowUnit) {
    double conversionFactor = 1.0;
    switch (flowUnit) {
      case "m3/sec":
        conversionFactor = 1.0;
        break;
      case "m3/hr":
        conversionFactor = 1.0 / 3600.0;
        break;
      case "m3/day":
        conversionFactor = 1.0 / 3600.0 / 24.0;
        break;
      default:
        throw new RuntimeException("unit not supported " + flowUnit);
    }
    waterFlowRate = flowRate * conversionFactor;
  }

  /**
   * <p>
   * Get getWaterFlowRate
   * </p>
   * 
   * @param flowUnit Supported units are m3/sec, m3/hr, m3/day
   * @return water flow rate in unit m3/sec
   */
  public double getWaterFlowRate(String flowUnit) {
    double conversionFactor = 1.0;
    switch (flowUnit) {
      case "m3/sec":
        conversionFactor = 1.0;
        break;
      case "m3/hr":
        conversionFactor = 1.0 / 3600.0;
        break;
      case "m3/day":
        conversionFactor = 1.0 / 3600.0 / 24.0;
        break;
      default:
        throw new RuntimeException("unit not supported " + flowUnit);
    }
    return waterFlowRate * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface tempFluid = inStream.getThermoSystem().clone();

    if (tempFluid.getFlowRate("kg/sec") < 1e-6) {
      outStream.setThermoSystem(tempFluid);
      return;
    }

    tempFluid.setTemperature(temperature, unitT);
    tempFluid.setPressure(pressure, unitP);

    ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
    try {
      thermoOps.TPflash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    tempFluid.initPhysicalProperties("density");

    double[] moleChange = new double[tempFluid.getNumberOfComponents()];
    for (int i = 0; i < tempFluid.getNumberOfComponents(); i++) {
      moleChange[i] = tempFluid.getPhase("gas").getComponent(i).getx()
          * (getGasFlowRate("Sm3/sec") / tempFluid.getPhase("gas").getMolarVolume("m3/mol"))
          + tempFluid.getPhase("oil").getComponent(i).getx()
              * (getOilFlowRate("m3/sec") / tempFluid.getPhase("oil").getMolarVolume("m3/mol"))
          - tempFluid.getComponent(i).getNumberOfmoles();
    }
    tempFluid.init(0);
    for (int i = 0; i < tempFluid.getNumberOfComponents(); i++) {
      tempFluid.addComponent(i, moleChange[i]);
    }
    if (waterFlowRate > 0) {
      tempFluid.addComponent("water", waterFlowRate * 1000.0, "kg/sec");
    }
    tempFluid.setPressure((inStream.getThermoSystem()).getPressure());
    tempFluid.setTemperature((inStream.getThermoSystem()).getTemperature());

    try {
      thermoOps.TPflash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    outStream.setThermoSystem(tempFluid);
    outStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }

}
