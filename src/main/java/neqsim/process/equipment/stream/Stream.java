/*
 * Stream.java
 *
 * Created on 12. mars 2001, 13:11
 */

package neqsim.process.equipment.stream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.measurementdevice.HydrocarbonDewPointAnalyser;
import neqsim.process.util.monitor.StreamResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.standards.gasquality.Standard_ISO6976;
import neqsim.standards.oilquality.Standard_ASTM_D6377;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;
import neqsim.util.exception.InvalidInputException;

/**
 * Stream class.
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
  /** Cached specification for skip-if-unchanged check in {@link #run(UUID)}. */
  protected String lastSpecification = null;

  /**
   * Level of physical-property initialization performed at the end of {@link Stream#run(java.util.UUID)}.
   *
   * <p>
   * {@link #FULL} evaluates every physical property (mass density, viscosity, thermal conductivity and diffusivity),
   * which is what a stream needs when transport properties are read downstream (pipelines, heat exchangers, mechanical
   * design). {@link #DENSITY_ONLY} evaluates the mass density alone and skips the transport-property correlations,
   * which is roughly an order of magnitude cheaper and sufficient for pure material-balance flowsheets.
   * </p>
   *
   * <p>
   * <b>Warning.</b> Under {@link #DENSITY_ONLY} the skipped properties are not flagged as unavailable - they read back
   * as {@code 0.0}. See {@link Stream#setPropertyInitLevel(PropertyInitLevel)}.
   * </p>
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public enum PropertyInitLevel {
    /** init(2) followed by all physical properties - the historical (default) behaviour. */
    FULL,
    /** init(2) followed by mass density only - skips viscosity, conductivity and diffusivity. */
    DENSITY_ONLY
  }

  /** Property-initialization level applied after the flash in {@link #run(java.util.UUID)}. */
  private PropertyInitLevel propertyInitLevel = PropertyInitLevel.FULL;

  /** Cached vapor-pressure standard, reused while the fluid composition and reference temperature are unchanged. */
  private transient Standard_ASTM_D6377 cachedRvpStandard = null;
  /** Fluid instance the cached vapor-pressure standard was evaluated for. */
  private transient SystemInterface cachedRvpFluid = null;
  /** Molar composition the cached vapor-pressure standard was evaluated for. */
  private transient double[] cachedRvpComposition = null;
  /** Reference temperature the cached vapor-pressure standard was evaluated for. */
  private transient double cachedRvpReferenceTemperature = Double.NaN;
  /** Reference-temperature unit the cached vapor-pressure standard was evaluated for. */
  private transient String cachedRvpReferenceTemperatureUnit = null;

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
   * <p>
   * NB! This construct uses the input stream object internally, i.e., it is not cloned. Use
   * <code>streamObject.clone(newName)</code> rather than <code>new Stream(newName,streamObject)</code>
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
   * Constructor for Stream.
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
   * Getter for the field <code>gasQuality</code>.
   *
   * @return the gasQuality
   */
  public double getGasQuality() {
    return gasQuality;
  }

  /**
   * Setter for the field <code>gasQuality</code>.
   *
   * @param gasQuality the gasQuality to set
   */
  public void setGasQuality(double gasQuality) {
    this.gasQuality = gasQuality;
  }

  /**
   * Getter for the field <code>propertyInitLevel</code>.
   *
   * @return the physical-property initialization level used by {@link #run(java.util.UUID)}; never null
   */
  public PropertyInitLevel getPropertyInitLevel() {
    return propertyInitLevel;
  }

  /**
   * Setter for the field <code>propertyInitLevel</code>.
   *
   * <p>
   * Use {@link PropertyInitLevel#DENSITY_ONLY} to skip the viscosity, thermal-conductivity and diffusivity correlations
   * when the flowsheet only needs mass balances and densities. This makes {@link #run(java.util.UUID)} substantially
   * cheaper on large flowsheets.
   * </p>
   *
   * <p>
   * <b>Warning - transport properties read back as zero.</b> {@link PropertyInitLevel#DENSITY_ONLY} does not throw when
   * a transport property is requested afterwards: {@code getViscosity()}, {@code getThermalConductivity()} and the
   * diffusion coefficients simply return {@code 0.0}. Set the level back to {@link PropertyInitLevel#FULL} (or call
   * {@code getFluid().initProperties()} explicitly) before any pipeline, heat-exchanger, mechanical-design or
   * flow-assurance calculation that reads transport properties from this stream.
   * </p>
   *
   * @param propertyInitLevel the level to use; null is treated as {@link PropertyInitLevel#FULL}
   */
  public void setPropertyInitLevel(PropertyInitLevel propertyInitLevel) {
    this.propertyInitLevel = propertyInitLevel == null ? PropertyInitLevel.FULL : propertyInitLevel;
  }

  /**
   * Initializes the physical properties of the internal fluid at the configured {@link PropertyInitLevel}.
   *
   * <p>
   * Called at the end of {@link #run(java.util.UUID)} after the flash has converged.
   * </p>
   */
  protected void initStreamProperties() {
    if (propertyInitLevel == PropertyInitLevel.DENSITY_ONLY) {
      thermoSystem.init(2);
      thermoSystem.initPhysicalProperties(neqsim.physicalproperties.PhysicalPropertyType.MASS_DENSITY);
    } else {
      thermoSystem.initProperties();
    }
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
   * getSolidFormationTemperature.
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
      logger.error(ex.getMessage());
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
    if (this.getName().equals(name)) {
      throw new RuntimeException(new InvalidInputException(this, "clone", "name", "- Same name as in original object"));
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
        this.thermoSystem = thermoSystem.phaseToSystem(thermoSystem.getPhaseNumberOfPhase("aqueous"));
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
      // If wrapping another stream, check if upstream needs recalculation first
      if (stream.needRecalculation()) {
        return true;
      }
      thermoSystem = stream.getFluid();
    }
    SystemInterface fluid = getFluid();
    if (fluid == null || lastComposition == null) {
      return true;
    }
    // Specification change (e.g., TP -> PH) produces a different flash even with same inputs.
    if (!java.util.Objects.equals(getSpecification(), lastSpecification)) {
      return true;
    }
    // Cheap scalar checks first - avoid allocating the composition array if any fail.
    if (fluid.getTemperature() != lastTemperature || fluid.getPressure() != lastPressure) {
      return true;
    }
    double flow = fluid.getFlowRate("kg/hr");
    if (flow <= 0.0 || lastFlowRate <= 0.0 || Math.abs(flow - lastFlowRate) / flow >= 1e-6) {
      return true;
    }
    // Allocation-free composition comparison: read component z() values directly against
    // the cached array instead of calling getMolarComposition() which allocates a double[].
    neqsim.thermo.phase.PhaseInterface ph0 = fluid.getPhase(0);
    int n = ph0.getNumberOfComponents();
    if (n != lastComposition.length) {
      return true;
    }
    for (int i = 0; i < n; i++) {
      if (ph0.getComponent(i).getz() != lastComposition[i]) {
        return true;
      }
    }
    return false;
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
      lastSpecification = getSpecification();

      if (stream != null) {
        stream.setFluid(thermoSystem);
      }
      // logger.info("number of phases: " + thermoSystem.getNumberOfPhases());
      // logger.info("beta: " + thermoSystem.getBeta());
      setCalculationIdentifier(id);
      return;
    }

    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);

    if (stream != null && thermoSystem.getNumberOfComponents() == 1 && getSpecification().equals("TP")) {
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
        thermoSystem.setPressure(getPressure());
        thermoOps.constantPhaseFractionTemperatureFlash(getGasQuality());
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

    initStreamProperties();

    lastFlowRate = thermoSystem.getFlowRate("kg/hr");
    lastTemperature = thermoSystem.getTemperature();
    lastPressure = thermoSystem.getPressure();
    lastComposition = thermoSystem.getMolarComposition();
    lastSpecification = getSpecification();

    if (stream != null) {
      // initProperties() already called above at line ~428; the duplicate call here
      // was pure overhead on every Stream.run() invocation.
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
   * runController.
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
   * phaseEnvelope.
   */
  public void phaseEnvelope() {
    SystemInterface localSyst = getFluid().clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(localSyst);
    ops.setRunAsThread(true);
    ops.calcPTphaseEnvelope();
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
    ops.calcPTphaseEnvelope();
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
    ops.calcPTphaseEnvelope();
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

  /**
   * Returns a {@link Standard_ASTM_D6377} evaluated for the current fluid at the given reference temperature.
   *
   * <p>
   * A single {@code calculate()} populates every RVP variant, and the result depends only on the fluid and the
   * reference temperature (the standard overrides temperature and pressure itself). The evaluated standard is therefore
   * cached and reused until the fluid instance, its composition or the reference temperature changes, which removes the
   * repeated bubble-point and vapor-fraction flashes when several RVP variants are read from the same stream.
   * </p>
   *
   * @param referenceTemperature the reference temperature, e.g. 37.8
   * @param unit the reference-temperature unit, e.g. "C"
   * @return the evaluated standard, or null if the calculation failed
   */
  private Standard_ASTM_D6377 getVapourPressureStandard(double referenceTemperature, String unit) {
    SystemInterface fluid = getFluid();
    double[] composition = fluid.getMolarComposition();
    if (cachedRvpStandard != null && cachedRvpFluid == fluid && cachedRvpReferenceTemperature == referenceTemperature
        && (unit == null ? cachedRvpReferenceTemperatureUnit == null : unit.equals(cachedRvpReferenceTemperatureUnit))
        && java.util.Arrays.equals(cachedRvpComposition, composition)) {
      return cachedRvpStandard;
    }
    SystemInterface localSyst = fluid.clone();
    Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(localSyst);
    standard.setReferenceTemperature(referenceTemperature, unit);
    try {
      standard.calculate();
    } catch (Exception ex) {
      logger.debug("RVP calculation failed: {}", ex.getMessage());
      cachedRvpStandard = null;
      cachedRvpFluid = null;
      cachedRvpComposition = null;
      cachedRvpReferenceTemperature = Double.NaN;
      cachedRvpReferenceTemperatureUnit = null;
      return null;
    }
    cachedRvpStandard = standard;
    cachedRvpFluid = fluid;
    cachedRvpComposition = composition;
    cachedRvpReferenceTemperature = referenceTemperature;
    cachedRvpReferenceTemperatureUnit = unit;
    return standard;
  }

  /** {@inheritDoc} */
  @Override
  public double getRVP(double referenceTemperature, String unit, String returnUnit) {
    Standard_ASTM_D6377 standard = getVapourPressureStandard(referenceTemperature, unit);
    if (standard == null) {
      return 0.0;
    }
    standard.setMethodRVP(Standard_ASTM_D6377.RvpMethod.VPCR4);
    return standard.getValue("RVP", returnUnit);
  }

  /** {@inheritDoc} */
  @Override
  public double getRVP(double referenceTemperature, String unit, String returnUnit, String rvpMethod) {
    Standard_ASTM_D6377 standard = getVapourPressureStandard(referenceTemperature, unit);
    if (standard == null) {
      return 0.0;
    }
    standard.setMethodRVP(rvpMethod);
    return standard.getValue("RVP", returnUnit);
  }

  /** {@inheritDoc} */
  @Override
  public String[][] reportResults() {
    return getFluid().getResultTable();
  }

  /**
   * getProperty.
   *
   * @param propertyName a {@link java.lang.String} object
   * @param unit a {@link java.lang.String} object
   * @param phase a {@link java.lang.String} object
   * @param component a {@link java.lang.String} object
   * @return a {@link java.lang.Object} object
   */
  public Object getProperty(String propertyName, String unit, String phase, String component) {
    throw new RuntimeException(new neqsim.util.exception.NotImplementedException(this, "getProperty"));
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
  public double getHydrocarbonDewPoint(String temperatureUnit, double refpressure, String refPressureUnit) {
    HydrocarbonDewPointAnalyser dewPointAnalyser = new HydrocarbonDewPointAnalyser("dew point analyser", this);
    dewPointAnalyser.setReferencePressure(refpressure);
    return dewPointAnalyser.getMeasuredValue(temperatureUnit);
  }

  /** {@inheritDoc} */
  @Override
  public double getGCV(String unit, double refTVolume, double refTCombustion) {
    Standard_ISO6976 standard = new Standard_ISO6976(getFluid().clone(), refTVolume, refTCombustion, unit);
    standard.setReferenceState("real");
    standard.calculate();
    return standard.getValue("SuperiorCalorificValue") * 1.0e3;
  }

  /** {@inheritDoc} */
  @Override
  public double getWI(String unit, double refTVolume, double refTCombustion) {
    Standard_ISO6976 standard = new Standard_ISO6976(getFluid().clone(), refTVolume, refTCombustion, unit);
    standard.setReferenceState("real");
    standard.calculate();
    return standard.getValue("SuperiorWobbeIndex") * 1.0e3;
  }

  /** {@inheritDoc} */
  @Override
  public Standard_ISO6976 getISO6976(String unit, double refTVolume, double refTCombustion) {
    Standard_ISO6976 standard = new Standard_ISO6976(getFluid().clone(), refTVolume, refTCombustion, unit);
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
   * Setter for the field <code>stream</code>.
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void setStream(StreamInterface stream) {
    this.stream = stream;
  }

  /**
   * Setter for the field <code>stream</code>.
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void setInletStream(StreamInterface stream) {
    this.setStream(stream);
  }

  /**
   * Gets the outlet stream.
   *
   * @return the outlet stream as a {@link neqsim.process.equipment.stream.StreamInterface} object.
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
   * getReport.
   *
   * @return a String object
   */
  public ArrayList<String[]> getReport() {
    HashMap<String, String> gasprops = new HashMap<String, String>();
    ArrayList<String> phases = new ArrayList<String>();

    phases.add("Total");
    if (getFluid().hasPhaseType("gas")) {
      phases.add("Gas");
      gasprops.put("temperature", Double.toString(getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))));
    }
    if (getFluid().hasPhaseType("oil")) {
      phases.add("oil");
    }
    if (getFluid().hasPhaseType("aqueous")) {
      phases.add("aqueous");
    }

    ArrayList<String[]> report = new ArrayList<String[]>();
    report.add(phases.toArray(new String[0]));
    report.add(
        new String[] { "temperature", Double.toString(getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
            neqsim.util.unit.Units.getSymbol("temperature") });
    report.add(new String[] { "pressure", Double.toString(getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
        neqsim.util.unit.Units.getSymbol("pressure") });
    report.add(new String[] { "mass flow", Double.toString(getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
        neqsim.util.unit.Units.getSymbol("mass flow") });
    report
        .add(new String[] { "molar flow", Double.toString(getFlowRate(neqsim.util.unit.Units.getSymbol("molar flow"))),
            neqsim.util.unit.Units.getSymbol("molar flow") });
    report.add(
        new String[] { "volume flow", Double.toString(getFlowRate(neqsim.util.unit.Units.getSymbol("volume flow"))),
            neqsim.util.unit.Units.getSymbol("volume flow") });
    return report;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(new StreamResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    StreamResponse res = new StreamResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(res);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Validates the stream setup before execution. Checks that:
   * <ul>
   * <li>Equipment has a valid name</li>
   * <li>Thermodynamic system is set and valid</li>
   * <li>Temperature and pressure are in valid ranges</li>
   * </ul>
   *
   * @return validation result with errors and warnings
   */
  @Override
  public neqsim.util.validation.ValidationResult validateSetup() {
    neqsim.util.validation.ValidationResult result = new neqsim.util.validation.ValidationResult(getName());

    // Check: Equipment has a valid name
    if (getName() == null || getName().trim().isEmpty()) {
      result.addError("equipment", "Stream has no name",
          "Set stream name in constructor: new Stream(\"MyStream\", thermoSystem)");
    }

    // Check: Thermodynamic system is set
    SystemInterface fluid = getFluid();
    if (fluid == null) {
      result.addError("thermo", "Stream has no thermodynamic system",
          "Create stream with fluid: new Stream(\"name\", thermoSystem)");
      return result; // Can't check further without fluid
    }

    // Check: Has components
    if (fluid.getNumberOfComponents() == 0) {
      result.addError("thermo", "Stream fluid has no components",
          "Add components: thermoSystem.addComponent(\"methane\", 1.0)");
    }

    // Check: Temperature is valid
    if (fluid.getTemperature() < 1.0) {
      result.addError("thermo", "Stream temperature too low: " + fluid.getTemperature() + " K",
          "Set temperature above 1 K: stream.setTemperature(298.15, \"K\")");
    }

    // Check: Pressure is valid
    if (fluid.getPressure() <= 0) {
      result.addError("thermo", "Stream pressure must be positive: " + fluid.getPressure() + " bar",
          "Set positive pressure: stream.setPressure(1.0, \"bar\")");
    }

    // Check: Mixing rule for multi-component systems
    if (fluid.getNumberOfComponents() > 1 && fluid.getMixingRuleName() == null) {
      result.addWarning("thermo", "Mixing rule not set for multi-component stream",
          "Set mixing rule: thermoSystem.setMixingRule(\"classic\")");
    }

    return result;
  }
}
