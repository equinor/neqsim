/*
 * Stream.java
 *
 * Created on 12. mars 2001, 13:11
 */

package neqsim.process.equipment.stream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.measurementdevice.HydrocarbonDewPointAnalyser;
import neqsim.process.util.monitor.StreamResponse;
import neqsim.standards.gasquality.Standard_ISO6976;
import neqsim.standards.oilquality.Standard_ASTM_D6377;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;
import neqsim.util.exception.InvalidInputException;

/**
 * <p>
 * Stream class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Stream extends ProcessEquipmentBaseClass implements StreamInterface, Cloneable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Stream.class);

  protected SystemInterface thermoSystem;

  // todo: is streamNumber ever anything besides 0 and 1? consider removing along
  // with
  // numberOfStreams?
  protected int streamNumber = 0;
  /** Constant <code>numberOfStreams=0</code>. */
  protected static int numberOfStreams = 0;
  private double gasQuality = 0.5;
  protected StreamInterface stream = null;

  // Results from previous run
  protected double lastTemperature = 0.0;
  protected double lastPressure = 0.0;
  protected double lastFlowRate = 0.0;
  protected double[] lastComposition = null;

  /**
   * Constructor for Stream.
   *
   * @param name name of stream
   */
  public Stream(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for Stream.
   * </p>
   *
   * <p>
   * NB! This construct uses the input stream object internally, i.e., it is not cloned. Use
   * <code>streamObject.clone(newName)</code> rather than
   * <code>new Stream(newName,streamObject)</code>
   * </p>
   *
   * @param name name of stream
   * @param stream Stream to use as internal Stream.
   */
  public Stream(String name, StreamInterface stream) {
    this(name);
    this.setStream(stream);
    numberOfStreams++;
    streamNumber = numberOfStreams;
  }

  /**
   * <p>
   * Constructor for Stream.
   * </p>
   *
   * <p>
   * NB! This construct uses the input thermoSystem object internally, i.e., it is not cloned.
   * </p>
   *
   * @param name name of stream
   * @param thermoSystem System to use as internal System.
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
    if (!getFluid().getPhase(0).hasComponent("water")) {
      logger.info("no hydrate: no water in stream: " + name);
      return 0.0;
    }
    try {
      SystemInterface copySystem = getFluid().clone();
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
    SystemInterface copySystem = getFluid().clone();

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
      logger.error(ex.getMessage());;
    }
    if (stream != null) {
      clonedSystem.setStream(stream.clone());
    }
    if (thermoSystem != null) {
      clonedSystem.thermoSystem = thermoSystem.clone();
    }

    return clonedSystem;
  }

  /** {@inheritDoc} */
  @Override
  public Stream clone(String name) {
    if (this.getName() == name) {
      throw new RuntimeException(
          new InvalidInputException(this, "clone", "name", "- Same name as in original object"));
    }
    Stream s = this.clone();
    s.setName(name);
    return s;
  }

  /** {@inheritDoc} */
  @Override
  public double getTemperature() {
    return getFluid().getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public double getTemperature(String unit) {
    return getFluid().getTemperature(unit);
  }

  /** {@inheritDoc} */
  @Override
  public double getMolarRate() {
    return getFluid().getTotalNumberOfMoles();
  }

  /** {@inheritDoc} */
  @Override
  public void setThermoSystem(SystemInterface thermoSystem) {
    if (stream != null) {
      stream.setThermoSystem(thermoSystem);
    } else {
      this.thermoSystem = thermoSystem;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setFluid(SystemInterface fluid) {
    if (stream != null) {
      stream.setFluid(fluid);
    } else {
      this.setThermoSystem(fluid);
    }
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
        logger.warn("no phase of type " + phaseTypeName);
        logger.warn("...returning empty system ");
        setEmptyThermoSystem(thermoSystem);
      }
      return;
    }
    if (thermoSystem.hasPhaseType(phaseTypeName)) {
      this.thermoSystem = thermoSystem.phaseToSystem(phaseTypeName);
    } else {
      logger.warn("no phase of type " + phaseTypeName);
      logger.warn("...returning empty system ");
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
    return this.getFluid();
  }

  /** {@inheritDoc} */
  @Override
  public void setFlowRate(double flowrate, String unit) {
    getFluid().setTotalFlowRate(flowrate, unit);
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
  public void runTPflash() {
    if (stream != null) {
      thermoSystem = stream.getThermoSystem().clone();
    }

    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
    thermoOps.TPflash();
    thermoSystem.initProperties();
    if (stream != null) {
      stream.setFluid(thermoSystem);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean needRecalculation() {
    if (stream != null) {
      thermoSystem = stream.getFluid();
    }
    if (getFluid().getTemperature() == lastTemperature && getFluid().getPressure() == lastPressure
        && Math.abs(getFluid().getFlowRate("kg/hr") - lastFlowRate)
            / getFluid().getFlowRate("kg/hr") < 1e-6
        && Arrays.equals(getFluid().getMolarComposition(), lastComposition)) {
      return false;
    } else {
      return true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (!getFluid().isInitialized()) {
      getFluid().init(0);
    }
    thermoSystem = getFluid().clone();

    if (getFlowRate("kg/hr") < getMinimumFlow()) {
      isActive(false);

      lastFlowRate = thermoSystem.getFlowRate("kg/hr");
      lastTemperature = thermoSystem.getTemperature();
      lastPressure = thermoSystem.getPressure();
      lastComposition = thermoSystem.getMolarComposition();

      if (stream != null) {
        stream.setFluid(thermoSystem);
      }
      // logger.info("number of phases: " + thermoSystem.getNumberOfPhases());
      // logger.info("beta: " + thermoSystem.getBeta());
      setCalculationIdentifier(id);
      return;
    }

    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);

    if (stream != null && thermoSystem.getNumberOfComponents() == 1
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
        thermoOps.PHflash(thermoSystem.getEnthalpy(), 0);
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
        thermoOps.TPflash();
      }
    } else {
      thermoOps.TPflash();
    }

    thermoSystem.initProperties();

    lastFlowRate = thermoSystem.getFlowRate("kg/hr");
    lastTemperature = thermoSystem.getTemperature();
    lastPressure = thermoSystem.getPressure();
    lastComposition = thermoSystem.getMolarComposition();

    if (stream != null) {
      stream.setFluid(thermoSystem);
    }
    // logger.info("number of phases: " + thermoSystem.getNumberOfPhases());
    // logger.info("beta: " + thermoSystem.getBeta());
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    getFluid().display(name);
  }

  /** {@inheritDoc} */
  @Override
  public String[][] getResultTable() {
    return getFluid().calcResultTable();
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
    ThermodynamicOperations ops = new ThermodynamicOperations(getFluid());
    ops.TPflash();
  }

  /**
   * <p>
   * phaseEnvelope.
   * </p>
   */
  public void phaseEnvelope() {
    SystemInterface localSyst = getFluid().clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(localSyst);
    ops.setRunAsThread(true);
    ops.calcPTphaseEnvelope2();
    ops.waitAndCheckForFinishedCalculation(10000);
    ops.displayResult();
    // ops.getJfreeChart();
  }

  /** {@inheritDoc} */
  @Override
  public double CCB(String unit) {
    SystemInterface localSyst = getFluid().clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(localSyst);
    ops.setRunAsThread(true);
    ops.calcPTphaseEnvelope2();
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
    SystemInterface localSyst = getFluid().clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(localSyst);
    ops.setRunAsThread(true);
    ops.calcPTphaseEnvelope2();
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
    SystemInterface localSyst = getFluid().clone();
    localSyst.setTemperature(temperature, unit);
    ThermodynamicOperations ops = new ThermodynamicOperations(localSyst);
    try {
      ops.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
      return 0.0;
    }
    return localSyst.getPressure();
  }

  /** {@inheritDoc} */
  @Override
  public double getTVP(double referenceTemperature, String unit, String returnUnit) {
    SystemInterface localSyst = getFluid().clone();
    localSyst.setTemperature(referenceTemperature, unit);
    ThermodynamicOperations ops = new ThermodynamicOperations(localSyst);
    try {
      ops.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
      return 0.0;
    }
    return localSyst.getPressure(returnUnit);
  }

  /** {@inheritDoc} */
  @Override
  public double getRVP(double referenceTemperature, String unit, String returnUnit) {
    SystemInterface localSyst = getFluid().clone();
    Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(localSyst);
    standard.setReferenceTemperature(referenceTemperature, unit);
    try {
      standard.calculate();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
      return 0.0;
    }
    return standard.getValue("RVP", returnUnit);
  }

  /** {@inheritDoc} */
  @Override
  public double getRVP(double referenceTemperature, String unit, String returnUnit,
      String rvpMethod) {
    SystemInterface localSyst = getFluid().clone();
    Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(localSyst);
    standard.setReferenceTemperature(referenceTemperature, unit);
    standard.setMethodRVP(rvpMethod);
    try {
      standard.calculate();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
      return 0.0;
    }
    return standard.getValue("RVP", returnUnit);
  }

  /** {@inheritDoc} */
  @Override
  public String[][] reportResults() {
    return getFluid().getResultTable();
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
    throw new RuntimeException(
        new neqsim.util.exception.NotImplementedException(this, "getProperty"));
    // getFluid().getProperties.containsKey(propertyName)) {
    // if(properties.containsKey(propertyName)) {
    // }
    // else

    // return null;
  }

  /** {@inheritDoc} */
  @Override
  public double GCV() {
    Standard_ISO6976 standard = new Standard_ISO6976(getFluid().clone(), 0, 15.55, "volume");
    standard.setReferenceState("real");
    standard.calculate();
    return standard.getValue("GCV") * 1.0e3;
  }

  /** {@inheritDoc} */
  @Override
  public double getHydrocarbonDewPoint(String temperatureUnit, double refpressure,
      String refPressureUnit) {
    HydrocarbonDewPointAnalyser dewPointAnalyser =
        new HydrocarbonDewPointAnalyser("dew point analyser", this);
    dewPointAnalyser.setReferencePressure(refpressure);
    return dewPointAnalyser.getMeasuredValue(temperatureUnit);
  }

  /** {@inheritDoc} */
  @Override
  public double getGCV(String unit, double refTVolume, double refTCombustion) {
    Standard_ISO6976 standard =
        new Standard_ISO6976(getFluid().clone(), refTVolume, refTCombustion, unit);
    standard.setReferenceState("real");
    standard.calculate();
    return standard.getValue("SuperiorCalorificValue") * 1.0e3;
  }

  /** {@inheritDoc} */
  @Override
  public double getWI(String unit, double refTVolume, double refTCombustion) {
    Standard_ISO6976 standard =
        new Standard_ISO6976(getFluid().clone(), refTVolume, refTCombustion, unit);
    standard.setReferenceState("real");
    standard.calculate();
    return standard.getValue("SuperiorWobbeIndex") * 1.0e3;
  }

  /** {@inheritDoc} */
  @Override
  public Standard_ISO6976 getISO6976(String unit, double refTVolume, double refTCombustion) {
    Standard_ISO6976 standard =
        new Standard_ISO6976(getFluid().clone(), refTVolume, refTCombustion, unit);
    standard.setReferenceState("real");
    return standard;
  }

  /** {@inheritDoc} */
  @Override
  public double LCV() {
    Standard_ISO6976 standard = new Standard_ISO6976(getFluid().clone(), 0, 15.55, "volume");
    standard.setReferenceState("real");
    standard.calculate();
    return standard.getValue("InferiorCalorificValue") * 1.0e3;
  }

  /**
   * <p>
   * Setter for the field <code>stream</code>.
   * </p>
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void setStream(StreamInterface stream) {
    this.stream = stream;
  }

  /**
   * <p>
   * Setter for the field <code>stream</code>.
   * </p>
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void setInletStream(StreamInterface stream) {
    this.setStream(stream);
  }

  /**
   * <p>
   * Getter for stream.
   * </p>
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getOutletStream() {
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getFluid() {
    if (stream != null) {
      return stream.getFluid();
    } else {
      return thermoSystem;
    }
  }

  /**
   * <p>
   * getReport.
   * </p>
   *
   * @return a String object
   */
  public ArrayList<String[]> getReport() {
    HashMap<String, String> gasprops = new HashMap<String, String>();
    ArrayList<String> phases = new ArrayList<String>();

    phases.add("Total");
    if (getFluid().hasPhaseType("gas")) {
      phases.add("Gas");
      gasprops.put("temperature",
          Double.toString(getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))));
    }
    if (getFluid().hasPhaseType("oil")) {
      phases.add("oil");
    }
    if (getFluid().hasPhaseType("aqueous")) {
      phases.add("aqueous");
    }

    ArrayList<String[]> report = new ArrayList<String[]>();
    report.add(phases.toArray(new String[0]));
    report.add(new String[] {"temperature",
        Double.toString(getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
        neqsim.util.unit.Units.getSymbol("temperature")});
    report.add(new String[] {"pressure",
        Double.toString(getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
        neqsim.util.unit.Units.getSymbol("pressure")});
    report.add(new String[] {"mass flow",
        Double.toString(getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
        neqsim.util.unit.Units.getSymbol("mass flow")});
    report.add(new String[] {"molar flow",
        Double.toString(getFlowRate(neqsim.util.unit.Units.getSymbol("molar flow"))),
        neqsim.util.unit.Units.getSymbol("molar flow")});
    report.add(new String[] {"volume flow",
        Double.toString(getFlowRate(neqsim.util.unit.Units.getSymbol("volume flow"))),
        neqsim.util.unit.Units.getSymbol("volume flow")});
    return report;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().create().toJson(new StreamResponse(this));
  }


}
