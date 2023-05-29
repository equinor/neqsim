/*
 * Stream.java
 *
 * Created on 12. mars 2001, 13:11
 */

package neqsim.processSimulation.processEquipment.stream;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.standards.gasQuality.Standard_ISO6976;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * Stream class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Stream extends ProcessEquipmentBaseClass implements StreamInterface, Cloneable {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(Stream.class);

  protected SystemInterface thermoSystem;

  protected int streamNumber = 0;
  /** Constant <code>numberOfStreams=0</code>. */
  protected static int numberOfStreams = 0;
  private double gasQuality = 0.5;
  protected StreamInterface stream = null;

  // Results from previous run
  protected double lastTemperature = 0.0;
  protected double lastPressure = 0.0;
  protected double lastFlowRate = 0.0;


  /**
   * <p>
   * Constructor for Stream.
   * </p>
   */
  @Deprecated
  public Stream() {
    super("Stream");
  }

  /**
   * <p>
   * Constructor for Stream.
   * </p>
   *
   * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
   */
  @Deprecated
  public Stream(StreamInterface stream) {
    this("Stream", stream);
  }

  /**
   * <p>
   * Constructor for Stream.
   * </p>
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  @Deprecated
  public Stream(SystemInterface thermoSystem) {
    this("Stream", thermoSystem);
  }

  /**
   * Constructor for Stream.
   *
   * @param name name of stream
   */
  public Stream(String name) {
    super(name);
  }

  /**
   * Constructor for Stream.
   *
   * @param name name of stream
   * @param stream input stream
   */
  public Stream(String name, StreamInterface stream) {
    super(name);
    this.setStream(stream);
    thermoSystem = stream.getThermoSystem();
    numberOfStreams++;
    streamNumber = numberOfStreams;
  }

  /**
   * <p>
   * Constructor for Stream.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Stream(String name, SystemInterface thermoSystem) {
    super(name);
    this.thermoSystem = thermoSystem;
    numberOfStreams++;
    streamNumber = numberOfStreams;
  }

  /**
   * <p>
   * Getter for the field <code>gasQuality</code>.
   * </p>
   *
   * @return the gasQuality
   */
  public double getGasQuality() {
    return gasQuality;
  }

  /**
   * <p>
   * Setter for the field <code>gasQuality</code>.
   * </p>
   *
   * @param gasQuality the gasQuality to set
   */
  public void setGasQuality(double gasQuality) {
    this.gasQuality = gasQuality;
  }

  /** {@inheritDoc} */
  @Override
  public double getHydrateEquilibriumTemperature() {
    if (!thermoSystem.getPhase(0).hasComponent("water")) {
      System.out.println("ny hydrate: no water in stream: " + name);
      return 0.0;
    }
    try {
      SystemInterface copySystem = thermoSystem.clone();
      copySystem.setHydrateCheck(true);
      ThermodynamicOperations thermoOps = new ThermodynamicOperations(copySystem);
      thermoOps.hydrateFormationTemperature();
      return copySystem.getTemperature();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return 0.0;
  }

  /**
   * <p>
   * getSolidFormationTemperature.
   * </p>
   *
   * @param solidName a {@link java.lang.String} object
   * @return a double
   */
  public double getSolidFormationTemperature(String solidName) {
    SystemInterface copySystem = thermoSystem.clone();

    try {
      if (solidName.equals("hydrate")) {
        copySystem.setHydrateCheck(true);
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(copySystem);
        thermoOps.hydrateFormationTemperature();
      } else {
        copySystem.setSolidPhaseCheck(false);
        copySystem.setSolidPhaseCheck(solidName);
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(copySystem);
        thermoOps.freezingPointTemperatureFlash();
      }
      return copySystem.getTemperature();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public Stream clone() {
    Stream clonedSystem = null;
    try {
      clonedSystem = (Stream) super.clone();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    if (stream != null) {
      clonedSystem.setStream((Stream) stream.clone());
    }
    clonedSystem.thermoSystem = getThermoSystem().clone();
    return clonedSystem;
  }

  /** {@inheritDoc} */
  @Override
  public double getTemperature() {
    return thermoSystem.getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public double getTemperature(String unit) {
    return getFluid().getTemperature(unit);
  }

  /** {@inheritDoc} */
  @Override
  public double getPressure() {
    return thermoSystem.getPressure();
  }

  /** {@inheritDoc} */
  @Override
  public double getPressure(String unit) {
    return getFluid().getPressure(unit);
  }

  /** {@inheritDoc} */
  @Override
  public double getMolarRate() {
    return thermoSystem.getTotalNumberOfMoles();
  }

  /** {@inheritDoc} */
  @Override
  public void setThermoSystem(SystemInterface thermoSystem) {
    this.thermoSystem = thermoSystem;
    // TODO: when is stream not null?
    if (stream != null) {
      stream.setThermoSystem(thermoSystem);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setFluid(SystemInterface fluid) {
    this.setThermoSystem(fluid);
  }

  /** {@inheritDoc} */
  @Override
  public void setThermoSystemFromPhase(SystemInterface thermoSystem, String phaseTypeName) {
    if (phaseTypeName.equals("liquid")) {
      if (thermoSystem.hasPhaseType("oil") && thermoSystem.hasPhaseType("aqueous")) {
        this.thermoSystem = thermoSystem.phaseToSystem(thermoSystem.getPhaseNumberOfPhase("oil"),
            thermoSystem.getPhaseNumberOfPhase("aqueous"));
      } else if (thermoSystem.hasPhaseType("oil")) {
        this.thermoSystem = thermoSystem.phaseToSystem(thermoSystem.getPhaseNumberOfPhase("oil"));
      } else if (thermoSystem.hasPhaseType("aqueous")) {
        this.thermoSystem =
            thermoSystem.phaseToSystem(thermoSystem.getPhaseNumberOfPhase("aqueous"));
      } else {
        System.out.println("no phase of type " + phaseTypeName);
        System.out.println("...returning empty system ");
        setEmptyThermoSystem(thermoSystem);
      }
      return;
    }
    if (thermoSystem.hasPhaseType(phaseTypeName)) {
      this.thermoSystem = thermoSystem.phaseToSystem(phaseTypeName);
    } else {
      System.out.println("no phase of type " + phaseTypeName);
      System.out.println("...returning empty system ");
      setEmptyThermoSystem(thermoSystem);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setEmptyThermoSystem(SystemInterface thermoSystem) {
    this.thermoSystem = thermoSystem.getEmptySystemClone();
    this.thermoSystem.setNumberOfPhases(0);
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return this.thermoSystem;
  }

  /** {@inheritDoc} */
  @Override
  public void setFlowRate(double flowrate, String unit) {
    this.getFluid().setTotalFlowRate(flowrate, unit);
  }

  /** {@inheritDoc} */
  @Override
  public void setPressure(double pressure, String unit) {
    getFluid().setPressure(pressure, unit);
  }

  /** {@inheritDoc} */
  @Override
  public void setTemperature(double temperature, String unit) {
    getFluid().setTemperature(temperature, unit);
  }

  /** {@inheritDoc} */
  @Override
  public double getFlowRate(String unit) {
    return this.getFluid().getFlowRate(unit);
  }

  /** {@inheritDoc} */
  @Override
  public void runTPflash() {
    if (stream != null) {
      thermoSystem = this.stream.getThermoSystem().clone();
    }

    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
    thermoOps.TPflash();
    thermoSystem.initProperties();
  }

  /** {@inheritDoc} */
  @Override
  public boolean needRecalculation() {
    if (getFluid().getTemperature() == lastTemperature && getFluid().getPressure() == lastPressure
        && Math.abs(getFluid().getFlowRate("kg/hr") - lastFlowRate)
            / getFluid().getFlowRate("kg/hr") < 1e-6) {
      return false;
    } else {
      return true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // System.out.println("start flashing stream... " + streamNumber);
    if (stream != null) {
      thermoSystem = this.stream.getThermoSystem().clone();
    }
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);

    if (stream != null && getThermoSystem().getNumberOfComponents() == 1
        && getSpecification().equals("TP")) {
      setSpecification("PH");
    }
    if (getSpecification().equals("TP")) {
      thermoOps.TPflash();
    } else if (getSpecification().equals("dewP")) {
      try {
        thermoOps.dewPointTemperatureFlash();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
        thermoOps.TPflash();
      }
    } else if (getSpecification().equals("dewT")) {
      try {
        thermoOps.dewPointPressureFlash();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
        thermoOps.TPflash();
      }
    } else if (getSpecification().equals("gas quality")) {
      try {
        thermoSystem.init(0);
        thermoSystem.init(2);
        double gasEnthalpy = thermoSystem.getPhase(0).getEnthalpy();
        double liquidEnthalpy = thermoSystem.getPhase(1).getEnthalpy();

        double enthalpySpec =
            getGasQuality() * gasEnthalpy + (1.0 - getGasQuality()) * liquidEnthalpy;
        thermoOps.PHflash(enthalpySpec);
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
        thermoOps.TPflash();
      }
    } else if (getSpecification().equals("bubP")) {
      try {
        thermoOps.bubblePointTemperatureFlash();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
        thermoOps.TPflash();
      }
    } else if (getSpecification().equals("bubT")) {
      try {
        thermoOps.bubblePointPressureFlash(false);
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
        thermoOps.TPflash();
      }
    } else if (getSpecification().equals("PH")) {
      try {
        thermoOps.PHflash(getThermoSystem().getEnthalpy(), 0);
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
        thermoOps.TPflash();
      }
    } else {
      thermoOps.TPflash();
    }

    thermoSystem.initProperties();

    lastFlowRate = getFluid().getFlowRate("kg/hr");
    lastTemperature = getFluid().getTemperature();
    lastPressure = getFluid().getPressure();

    // System.out.println("number of phases: " + thermoSystem.getNumberOfPhases());
    // System.out.println("beta: " + thermoSystem.getBeta());
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {
    thermoSystem.display(name);
  }

  /**
   * <p>
   * getResultTable.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[][] getResultTable() {
    return thermoSystem.getResultTable();
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    if (hasController) {
      // This adjusts the flow rate through this stream.
      // Typically used to match/manipulate mass balance.
      runController(dt, id);
      this.setFlowRate(getController().getResponse(), "kg/hr");
    }

    run(id);
    increaseTime(dt);
  }

  /**
   * <p>
   * runController.
   * </p>
   *
   * @param dt a double
   * @param id UUID of run
   */
  public void runController(double dt, UUID id) {
    if (hasController) {
      getController().runTransient(this.getFlowRate("kg/hr"), dt, id);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void flashStream() {
    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
    ops.TPflash();
  }

  /**
   * <p>
   * phaseEnvelope.
   * </p>
   */
  public void phaseEnvelope() {
    SystemInterface localSyst = thermoSystem.clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(localSyst);
    ops.setRunAsThread(true);
    ops.calcPTphaseEnvelope(true);
    ops.waitAndCheckForFinishedCalculation(10000);
    ops.displayResult();
    // ops.getJfreeChart();
  }

  /** {@inheritDoc} */
  @Override
  public double CCB(String unit) {
    SystemInterface localSyst = thermoSystem.clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(localSyst);
    ops.setRunAsThread(true);
    ops.calcPTphaseEnvelope(true);
    ops.waitAndCheckForFinishedCalculation(10000);
    if (unit.equals("bara") || unit.equals("bar")) {
      return ops.get("cricondenbar")[1];
    } else {
      if (unit.equals("C")) {
        return ops.get("cricondenbar")[0] - 273.15;
      } else {
        return ops.get("cricondenbar")[0];
      }
    }
    // return ops.get
    // ops.getJfreeChart();
  }

  /** {@inheritDoc} */
  @Override
  public double CCT(String unit) {
    SystemInterface localSyst = thermoSystem.clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(localSyst);
    ops.setRunAsThread(true);
    ops.calcPTphaseEnvelope(true);
    ops.waitAndCheckForFinishedCalculation(10000);
    if (unit.equals("bara") || unit.equals("bar")) {
      return ops.get("cricondentherm")[1];
    } else {
      if (unit.equals("C")) {
        return ops.get("cricondentherm")[0] - 273.15;
      } else {
        return ops.get("cricondentherm")[0];
      }
    }
    // return ops.get
    // ops.getJfreeChart();
  }

  /** {@inheritDoc} */
  @Override
  public double TVP(double temperature, String unit) {
    SystemInterface localSyst = thermoSystem.clone();
    localSyst.setTemperature(temperature, unit);
    ThermodynamicOperations ops = new ThermodynamicOperations(localSyst);
    try {
      ops.bubblePointPressureFlash(false);
    } catch (Exception ex) {
    }
    return localSyst.getPressure(unit);
  }

  /** {@inheritDoc} */
  @Override
  public String[][] reportResults() {
    return thermoSystem.getResultTable();
  }

  /**
   * <p>
   * getProperty.
   * </p>
   *
   * @param propertyName a {@link java.lang.String} object
   * @param unit a {@link java.lang.String} object
   * @param phase a {@link java.lang.String} object
   * @param component a {@link java.lang.String} object
   * @return a {@link java.lang.Object} object
   */
  public Object getProperty(String propertyName, String unit, String phase, String component) {
    // getFluid().getProperties.containsKey(propertyName)) {
    // if(properties.containsKey(propertyName)) {
    // }
    // else
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public double GCV() {
    Standard_ISO6976 standard = new Standard_ISO6976(thermoSystem.clone(), 0, 15.55, "volume");
    standard.setReferenceState("real");
    standard.calculate();
    return standard.getValue("GCV") * 1.0e3;
  }

  /** {@inheritDoc} */
  @Override
  public double LCV() {
    Standard_ISO6976 standard = new Standard_ISO6976(thermoSystem.clone(), 0, 15.55, "volume");
    standard.setReferenceState("real");
    standard.calculate();
    return standard.getValue("InferiorCalorificValue") * 1.0e3;
  }

  /**
   * <p>
   * Setter for the field <code>stream</code>.
   * </p>
   *
   * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
   */
  public void setStream(StreamInterface stream) {
    this.stream = stream;
  }
}
