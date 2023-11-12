package neqsim.processSimulation.processEquipment.util;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.processSimulation.processEquipment.TwoPortEquipment;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processSystem.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import neqsim.util.exception.InvalidInputException;

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
  private static final Logger logger = LogManager.getLogger(FlowSetter.class);
  double[] pressure = new double[] {1.01325};
  double[] temperature = new double[] {15.0};
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
        conversionFactor = 1.0 * 3600.0;
        break;
      case "Sm3/day":
        conversionFactor = 1.0 * 3600.0 * 24.0;
        break;
      case "MSm3/day":
        conversionFactor = 1.0 * 3600.0 * 24.0 / 1e6;
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
        conversionFactor = 1.0 * 3600.0;
        break;
      case "m3/day":
        conversionFactor = 1.0 * 3600.0 * 24.0;
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
        conversionFactor = 1.0 * 3600.0;
        break;
      case "m3/day":
        conversionFactor = 1.0 * 3600.0 * 24.0;
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
      referenceProcess = createReferenceProcess(inStream);
    }

    if (tempFluid.getFlowRate("kg/sec") < 1e-6) {
      outStream.setThermoSystem(tempFluid);
      return;
    }

    for (int l = 0; l < 25; l++) {
      // ((StreamInterface) referenceProcess.getUnit("feed stream")).setFluid(tempFluid);
      Stream ins = new Stream(tempFluid);
      ins.run();
      referenceProcess = createReferenceProcess(ins);
      referenceProcess.run();
      ((StreamInterface) referenceProcess.getUnit("gas")).getFluid()
          .initPhysicalProperties("density");
      ((StreamInterface) referenceProcess.getUnit("oil")).getFluid()
          .initPhysicalProperties("density");

      double[] moleChange = new double[tempFluid.getNumberOfComponents()];
      for (int i = 0; i < tempFluid.getNumberOfComponents(); i++) {
        moleChange[i] = ((StreamInterface) referenceProcess.getUnit("gas")).getFluid()
            .getComponent(i).getNumberOfMolesInPhase()
            * (getGasFlowRate("Sm3/hr")
                / ((StreamInterface) referenceProcess.getUnit("gas")).getFlowRate("Sm3/hr"))
            - ((StreamInterface) referenceProcess.getUnit("gas")).getFluid().getComponent(i)
                .getNumberOfMolesInPhase();

        // +

        // ((StreamInterface) referenceProcess.getUnit("oil")).getFluid().getComponent(i)
        // .getNumberOfMolesInPhase()
        // * (getOilFlowRate("m3/hr")
        // / ((StreamInterface) referenceProcess.getUnit("oil")).getFlowRate("m3/hr"))
        // - ((StreamInterface) referenceProcess.getUnit("oil")).getFluid().getComponent(i)
        // .getNumberOfMolesInPhase();
      }
      tempFluid.init(0);
      for (int i = 0; i < tempFluid.getNumberOfComponents(); i++) {
        tempFluid.addComponent(i, moleChange[i]);
      }

    }

    for (int l = 0; l < 25; l++) {
      // ((StreamInterface) referenceProcess.getUnit("feed stream")).setFluid(tempFluid);
      Stream ins = new Stream(tempFluid);
      ins.run();
      referenceProcess = createReferenceProcess(ins);
      referenceProcess.run();
      ((StreamInterface) referenceProcess.getUnit("gas")).getFluid()
          .initPhysicalProperties("density");
      ((StreamInterface) referenceProcess.getUnit("oil")).getFluid()
          .initPhysicalProperties("density");

      double[] moleChange = new double[tempFluid.getNumberOfComponents()];
      for (int i = 0; i < tempFluid.getNumberOfComponents(); i++) {
        moleChange[i] = ((StreamInterface) referenceProcess.getUnit("oil")).getFluid()
            .getComponent(i).getNumberOfMolesInPhase()
            * (getOilFlowRate("m3/hr")
                / ((StreamInterface) referenceProcess.getUnit("oil")).getFlowRate("m3/hr"))
            - ((StreamInterface) referenceProcess.getUnit("oil")).getFluid().getComponent(i)
                .getNumberOfMolesInPhase();
      }
      tempFluid.init(0);
      for (int i = 0; i < tempFluid.getNumberOfComponents(); i++) {
        tempFluid.addComponent(i, moleChange[i]);
      }

    }


    if (waterFlowRate > 0) {
      tempFluid.addComponent("water", waterFlowRate * 1000.0, "kg/sec");
    }

    ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
    try {
      thermoOps.TPflash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    outStream.setThermoSystem(tempFluid);
    outStream.run();
    outStream.getFluid().initPhysicalProperties();
    
    outStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }

  public ProcessSystem createReferenceProcess(StreamInterface feedStream) {

    ProcessSystem referenceProcess = new ProcessSystem();

    StreamInterface feedStream1 = new Stream("feed stream", feedStream.getFluid().clone());
    feedStream1.setTemperature(temperature[0], unitT);
    feedStream1.setPressure(pressure[0], unitP);
    referenceProcess.add(feedStream1);

    ThreePhaseSeparator separator1ststage =
        new ThreePhaseSeparator("1st stage separator", feedStream1);
    referenceProcess.add(separator1ststage);

    Mixer gasMixer = new Mixer("gas mixer");
    gasMixer.addStream(separator1ststage.getGasOutStream());

    StreamInterface gasExport = new Stream("gas", gasMixer.getOutletStream());
    StreamInterface oilExport = null;

    if (temperature.length == 0 || temperature == null) {
      throw new RuntimeException(
          new InvalidInputException(this, "getReferenceProcess", "temperature", "can not be null"));
    } else if (temperature.length == 1) {
      oilExport = new Stream("oil", separator1ststage.getOilOutStream());
    } else if (temperature.length == 2) {
      Heater heater2ndstage = new Heater("2nd stage heater", separator1ststage.getOilOutStream());
      heater2ndstage.setOutPressure(pressure[1]);
      heater2ndstage.setOutTemperature(temperature[1]);
      referenceProcess.add(heater2ndstage);

      ThreePhaseSeparator separator2ndstage =
          new ThreePhaseSeparator("2nd stage separator", heater2ndstage.getOutletStream());
      referenceProcess.add(separator2ndstage);

      gasMixer.addStream(separator2ndstage.getGasOutStream());
      oilExport = new Stream("oil", separator2ndstage.getOilOutStream());
    } else if (temperature.length == 3) {
      Heater heater2ndstage = new Heater("2nd stage heater", separator1ststage.getOilOutStream());
      heater2ndstage.setOutPressure(pressure[1]);
      heater2ndstage.setOutTemperature(temperature[1]);
      referenceProcess.add(heater2ndstage);

      ThreePhaseSeparator separator2ndstage =
          new ThreePhaseSeparator("2nd stage separator", heater2ndstage.getOutletStream());
      referenceProcess.add(separator2ndstage);

      Heater heater3rdstage = new Heater("3rd stage heater", separator2ndstage.getOilOutStream());
      heater3rdstage.setOutPressure(pressure[2]);
      heater3rdstage.setOutTemperature(temperature[2]);
      referenceProcess.add(heater3rdstage);

      ThreePhaseSeparator separator3rdstage =
          new ThreePhaseSeparator("3rd stage separator", heater3rdstage.getOutletStream());
      referenceProcess.add(separator3rdstage);

      gasMixer.addStream(separator2ndstage.getGasOutStream());
      gasMixer.addStream(separator3rdstage.getGasOutStream());
      oilExport = new Stream("oil", separator3rdstage.getOilOutStream());
    }

    referenceProcess.add(gasMixer);
    referenceProcess.add(gasExport);
    referenceProcess.add(oilExport);

    return referenceProcess;
  }

  public ProcessSystem getReferenceProcess() {
    return referenceProcess;
  }

}
