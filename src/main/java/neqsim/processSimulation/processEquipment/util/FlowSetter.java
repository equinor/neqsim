package neqsim.processSimulation.processEquipment.util;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.processSimulation.processEquipment.TwoPortEquipment;
import neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processSystem.ProcessSystem;
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
  double[] pressure = new double[] {1.01325};
  double[] temperature = new double[] {15};
  String unitT = "C";
  String unitP = "bara";

  private double gasFlowRate;
  private double oilFlowRate;
  private double waterFlowRate;
  String unitGasFlowRate = "Sm3/day";
  String unitOilFlowRate = "m3/hr";
  String unitWaterFlowRate = "m3/hr";

  ProcessSystem referenceProcess = null;

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

    if (referenceProcess == null) {
      referenceProcess = getReferenceProcess(inStream);
    }

    ((StreamInterface) referenceProcess.getUnit("feed stream")).setFluid(inStream.getFluid());
    referenceProcess.run();

    if (tempFluid.getFlowRate("kg/sec") < 1e-6) {
      outStream.setThermoSystem(tempFluid);
      return;
    }

    ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
    try {
      thermoOps.TPflash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    tempFluid.initPhysicalProperties("density");

    double[] moleChange = new double[tempFluid.getNumberOfComponents()];
    for (int i = 0; i < tempFluid.getNumberOfComponents(); i++) {
      moleChange[i] = tempFluid.getPhase("gas").getComponent(i).getNumberOfMolesInPhase()
          * (getGasFlowRate("Sm3/hr")
              / ((SystemInterface) referenceProcess.getUnit("gas")).getFlowRate("Sm3/hr"))
          + tempFluid.getPhase("oil").getComponent(i).getNumberOfMolesInPhase()
              * (getOilFlowRate("m3/hr")
                  / ((SystemInterface) referenceProcess.getUnit("oil")).getFlowRate("m3/hr"))
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

  public ProcessSystem getReferenceProcess(StreamInterface feedStream) {

    StreamInterface feedStream1 = new Stream("feed stream", feedStream.getFluid().clone());
    feedStream1.setTemperature(temperature[0], unitT);
    feedStream1.setPressure(pressure[0], unitP);
    ThreePhaseSeparator separator1ststage =
        new ThreePhaseSeparator("1st stage separator", feedStream1);
    StreamInterface gasExport = new Stream("gas", separator1ststage.getGasOutStream());
    StreamInterface oilExport = new Stream("oil", separator1ststage.getOilOutStream());


    ProcessSystem referenceProcess = new ProcessSystem();
    referenceProcess.add(feedStream1);
    referenceProcess.add(separator1ststage);
    referenceProcess.add(gasExport);
    referenceProcess.add(oilExport);
    referenceProcess.run();
    gasExport.getFluid().initPhysicalProperties("density");
    oilExport.getFluid().initPhysicalProperties("density");
    return referenceProcess;
  }

}
