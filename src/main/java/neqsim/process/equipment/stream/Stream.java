/*
 * Stream.java
 *
 * Created on 12. mars 2001, 13:11
 */

package neqsim.process.equipment.stream;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.measurementdevice.HydrocarbonDewPointAnalyser;
import neqsim.process.util.monitor.StreamResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.standards.gasquality.Standard_ISO6976;
import neqsim.standards.oilquality.Standard_ASTM_D6377;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.phase.PhaseEosInterface;
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
public class Stream extends ProcessEquipmentBaseClass
    implements StreamInterface, Cloneable, TransientStateParticipant<Stream.TransientState> {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Stream.class);
  /**
   * Tolerance used when testing whether a cricondenpoint merely echoes the source fluid state. Applied in Kelvin to the
   * temperature and in bara to the pressure.
   */
  private static final double CRICONDEN_ECHO_TOLERANCE = 1.0e-6;
  /** Initial value for the deterministic criconden-envelope input fingerprint. */
  private static final long CRICONDEN_SIGNATURE_SEED = 1125899906842597L;

  /** Stable transaction identity retained by Java serialization. */
  private String transientStateIdentity = UUID.randomUUID().toString();

  /** Fingerprint of the fluid state used for the cached criconden envelope. */
  private transient long cachedCricondenInputSignature = Long.MIN_VALUE;
  /** Whether both cached cricondenpoints were resolved successfully. */
  private transient boolean hasCachedCricondenEnvelope = false;
  /** Cached cricondentherm as temperature in Kelvin and pressure in bara. */
  private transient double[] cachedCricondenTherm = null;
  /** Cached cricondenbar as temperature in Kelvin and pressure in bara. */
  private transient double[] cachedCricondenBar = null;

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
    Stream clonedSystem;
    try {
      clonedSystem = (Stream) super.clone();
    } catch (Exception ex) {
      logger.error("Failed to clone stream {}", getName(), ex);
      throw new IllegalStateException("Unable to clone stream '" + getName() + "'", ex);
    }
    if (stream != null) {
      clonedSystem.setStream(stream.clone());
    }
    if (thermoSystem != null) {
      clonedSystem.thermoSystem = thermoSystem.clone();
    }
    clonedSystem.transientStateIdentity = UUID.randomUUID().toString();
    clonedSystem.lastComposition = lastComposition == null ? null : lastComposition.clone();
    clonedSystem.invalidateDerivedTransientCaches();

    return clonedSystem;
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateIdentity() {
    if (transientStateIdentity == null || transientStateIdentity.trim().isEmpty()) {
      transientStateIdentity = UUID.randomUUID().toString();
    }
    return "equipment:stream:" + transientStateIdentity;
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateCoverageIssue() {
    if (getClass() != Stream.class) {
      return "stream subclass " + getClass().getName() + " must extend the snapshot for subclass-owned mutable state";
    }
    String baseIssue = getBaseTransientStateCoverageIssue();
    if (baseIssue != null) {
      return baseIssue;
    }
    if (stream != null) {
      return "wrapper streams delegate mutations to another stream and require coordinated state ownership";
    }
    if (thermoSystem == null) {
      return "stream has no thermodynamic system to capture";
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public TransientState captureTransientState() {
    String coverageIssue = getTransientStateCoverageIssue();
    if (coverageIssue != null) {
      throw new IllegalStateException("Cannot capture stream '" + getName() + "': " + coverageIssue);
    }
    return new TransientState(this);
  }

  /** {@inheritDoc} */
  @Override
  public void restoreTransientState(TransientState snapshot) {
    Objects.requireNonNull(snapshot, "stream transient snapshot cannot be null");
    if (!getTransientStateIdentity().equals(snapshot.stateIdentity)) {
      throw new IllegalArgumentException("Transient snapshot belongs to another stream");
    }

    restoreBaseTransientState(snapshot.baseState);
    thermoSystem = snapshot.thermoSystem.clone();
    stream = null;
    streamNumber = snapshot.streamNumber;
    gasQuality = snapshot.gasQuality;
    lastTemperature = snapshot.lastTemperature;
    lastPressure = snapshot.lastPressure;
    lastFlowRate = snapshot.lastFlowRate;
    lastComposition = snapshot.lastComposition == null ? null : snapshot.lastComposition.clone();
    lastSpecification = snapshot.lastSpecification;
    propertyInitLevel = snapshot.propertyInitLevel;
    invalidateDerivedTransientCaches();
  }

  /** Clears derived property caches so rejected trials cannot leak cached results. */
  private void invalidateDerivedTransientCaches() {
    cachedCricondenInputSignature = Long.MIN_VALUE;
    hasCachedCricondenEnvelope = false;
    cachedCricondenTherm = null;
    cachedCricondenBar = null;
    cachedRvpStandard = null;
    cachedRvpFluid = null;
    cachedRvpComposition = null;
    cachedRvpReferenceTemperature = Double.NaN;
    cachedRvpReferenceTemperatureUnit = null;
  }

  /** Immutable serializable checkpoint for a concrete local stream. */
  public static final class TransientState implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String stateIdentity;
    private final ProcessEquipmentTransientState baseState;
    private final SystemInterface thermoSystem;
    private final int streamNumber;
    private final double gasQuality;
    private final double lastTemperature;
    private final double lastPressure;
    private final double lastFlowRate;
    private final double[] lastComposition;
    private final String lastSpecification;
    private final PropertyInitLevel propertyInitLevel;

    private TransientState(Stream source) {
      stateIdentity = source.getTransientStateIdentity();
      baseState = source.captureBaseTransientState();
      thermoSystem = source.thermoSystem.clone();
      streamNumber = source.streamNumber;
      gasQuality = source.gasQuality;
      lastTemperature = source.lastTemperature;
      lastPressure = source.lastPressure;
      lastFlowRate = source.lastFlowRate;
      lastComposition = source.lastComposition == null ? null : source.lastComposition.clone();
      lastSpecification = source.lastSpecification;
      propertyInitLevel = source.propertyInitLevel;
    }
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
    SystemInterface fluid = getFluid();
    if (fluid == null) {
      // A named but unconfigured stream is a valid inactive topology placeholder. Treat it as
      // solved for this pass so ProcessSystem execution, diagram generation, and exchange export
      // can retain the placeholder without inventing a thermodynamic state.
      isActive(false);
      lastFlowRate = 0.0;
      lastTemperature = Double.NaN;
      lastPressure = Double.NaN;
      lastComposition = null;
      lastSpecification = getSpecification();
      setCalculationIdentifier(id);
      return;
    }
    if (!fluid.isInitialized()) {
      fluid.init(0);
    }
    thermoSystem = fluid.clone();

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

  /**
   * Compute a cricondenpoint of the stream fluid from its PT phase envelope.
   *
   * <p>
   * The envelope is traced with the two-argument {@code calcPTphaseEnvelope(true, 1.0)} overload because the
   * no-argument overload fails to trace some fluids - notably lean export gases that carry heavy pseudo-components at
   * (near) zero moles.
   * </p>
   *
   * <p>
   * A single envelope contains both cricondenpoints. The result is therefore cached against a deterministic fingerprint
   * of the complete EOS input used by the trace, so repeated {@link #CCT(String)} and {@link #CCB(String)} calls do not
   * repeat the expensive envelope calculation. Temperature and pressure are part of the fingerprint because they seed
   * the numerical trace. Composition, pseudo-component properties and EOS binary-interaction parameters are included to
   * prevent stale reuse after direct mutation of the stream fluid.
   * </p>
   *
   * <p>
   * When the trace fails, {@code PTphaseEnvelope} falls back to reporting the source fluid's own temperature and
   * pressure. That fallback is indistinguishable from a real result to the caller, so it is detected here and reported
   * as unresolved rather than returned as a value. A genuine cricondenpoint that coincides with the stream temperature
   * <em>and</em> the stream pressure to within {@value #CRICONDEN_ECHO_TOLERANCE} is treated as unresolved as well; a
   * stream sitting exactly on its own cricondenpoint is not distinguishable from the fallback and is vanishingly rare
   * in practice.
   * </p>
   *
   * @param pointName envelope point to read, either {@code "cricondentherm"} or {@code "cricondenbar"}
   * @return a two-element array holding the temperature in Kelvin at index 0 and the pressure in bara at index 1, or
   * {@code null} when the point could not be resolved
   */
  private synchronized double[] calcCricondenPoint(String pointName) {
    SystemInterface sourceSystem = getFluid();
    long inputSignature = calculateCricondenInputSignature(sourceSystem);
    if (!hasCachedCricondenEnvelope || inputSignature != cachedCricondenInputSignature) {
      SystemInterface localSyst = sourceSystem.clone();
      // Captured before the trace runs, because tracing mutates the cloned system's state.
      double sourceTemperatureK = localSyst.getTemperature();
      double sourcePressureBara = localSyst.getPressure();

      ThermodynamicOperations ops = createCricondenOperations(localSyst);
      ops.setRunAsThread(true);
      ops.calcPTphaseEnvelope(true, 1.0);
      ops.waitAndCheckForFinishedCalculation(10000);

      cachedCricondenTherm = validateCricondenPoint(ops.get("cricondentherm"), "cricondentherm", sourceTemperatureK,
          sourcePressureBara);
      cachedCricondenBar = validateCricondenPoint(ops.get("cricondenbar"), "cricondenbar", sourceTemperatureK,
          sourcePressureBara);
      cachedCricondenInputSignature = inputSignature;
      hasCachedCricondenEnvelope = cachedCricondenTherm != null && cachedCricondenBar != null;
    }

    if ("cricondentherm".equals(pointName)) {
      return cachedCricondenTherm;
    }
    return cachedCricondenBar;
  }

  /**
   * Create the operations object used to trace a criconden envelope.
   *
   * <p>
   * Package access keeps the production API unchanged while allowing the cache behavior to be counted in a focused
   * regression test.
   * </p>
   *
   * @param system cloned thermodynamic system to trace
   * @return operations object for the supplied system
   */
  ThermodynamicOperations createCricondenOperations(SystemInterface system) {
    return new ThermodynamicOperations(system);
  }

  /**
   * Validate an envelope point and copy the two values retained by the stream cache.
   *
   * @param point raw point returned by the envelope operation
   * @param pointName name used in diagnostics
   * @param sourceTemperatureK source-stream temperature in Kelvin
   * @param sourcePressureBara source-stream pressure in bara
   * @return copied temperature-pressure pair, or {@code null} when unresolved
   */
  private double[] validateCricondenPoint(double[] point, String pointName, double sourceTemperatureK,
      double sourcePressureBara) {
    if (point == null || point.length < 2 || !Double.isFinite(point[0]) || !Double.isFinite(point[1])) {
      logger.error("{}: phase envelope did not resolve {} for stream {}", getClass().getSimpleName(), pointName,
          getName());
      return null;
    }

    boolean echoesSourceState = Math.abs(point[0] - sourceTemperatureK) <= CRICONDEN_ECHO_TOLERANCE
        && Math.abs(point[1] - sourcePressureBara) <= CRICONDEN_ECHO_TOLERANCE;
    if (echoesSourceState) {
      logger.error(
          "{}: phase envelope failed to trace for stream {}; {} returned the stream's own state "
              + "({} K, {} bara) and is reported as unresolved",
          getClass().getSimpleName(), getName(), pointName, sourceTemperatureK, sourcePressureBara);
      return null;
    }
    return new double[] { point[0], point[1] };
  }

  /**
   * Calculate a deterministic fingerprint of every EOS input that can affect the traced envelope.
   *
   * @param system stream fluid to fingerprint
   * @return exact criconden-envelope input fingerprint
   */
  private long calculateCricondenInputSignature(SystemInterface system) {
    long signature = CRICONDEN_SIGNATURE_SEED;
    signature = updateCricondenInputSignature(signature, system.getClass().getName());
    signature = updateCricondenInputSignature(signature, system.getModelName());
    signature = updateCricondenInputSignature(signature, system.getMixingRuleName());
    signature = updateCricondenInputSignature(signature, system.getTemperature());
    signature = updateCricondenInputSignature(signature, system.getPressure());

    int componentCount = system.getNumberOfComponents();
    signature = updateCricondenInputSignature(signature, componentCount);
    for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
      ComponentInterface component = system.getPhase(0).getComponent(componentIndex);
      signature = updateCricondenInputSignature(signature, component.getComponentName());
      signature = updateCricondenInputSignature(signature, component.getz());
      signature = updateCricondenInputSignature(signature, component.getMolarMass());
      signature = updateCricondenInputSignature(signature, component.getNormalLiquidDensity());
      signature = updateCricondenInputSignature(signature, component.getTC());
      signature = updateCricondenInputSignature(signature, component.getPC());
      signature = updateCricondenInputSignature(signature, component.getAcentricFactor());
    }

    if (system.getPhase(0) instanceof PhaseEosInterface) {
      EosMixingRulesInterface mixingRule = ((PhaseEosInterface) system.getPhase(0)).getEosMixingRule();
      if (mixingRule != null) {
        signature = updateCricondenInputSignature(signature, ((long) componentCount) * componentCount);
        for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
          for (int otherComponentIndex = 0; otherComponentIndex < componentCount; otherComponentIndex++) {
            signature = updateCricondenInputSignature(signature,
                mixingRule.getBinaryInteractionParameter(componentIndex, otherComponentIndex));
            signature = updateCricondenInputSignature(signature,
                mixingRule.getBinaryInteractionParameterT1(componentIndex, otherComponentIndex));
          }
        }
      }
    }
    return signature;
  }

  /**
   * Add one numeric input to a criconden-envelope fingerprint.
   *
   * @param signature fingerprint accumulated so far
   * @param value numeric input value
   * @return updated fingerprint
   */
  private long updateCricondenInputSignature(long signature, double value) {
    return updateCricondenInputSignature(signature, Double.doubleToLongBits(value));
  }

  /**
   * Add one integral input to a criconden-envelope fingerprint.
   *
   * @param signature fingerprint accumulated so far
   * @param value integral input value
   * @return updated fingerprint
   */
  private long updateCricondenInputSignature(long signature, long value) {
    return 31L * signature + value;
  }

  /**
   * Add complete text content to a criconden-envelope fingerprint.
   *
   * @param signature fingerprint accumulated so far
   * @param value text input, which may be null
   * @return updated fingerprint
   */
  private long updateCricondenInputSignature(long signature, String value) {
    if (value == null) {
      return updateCricondenInputSignature(signature, -1L);
    }
    long updatedSignature = updateCricondenInputSignature(signature, value.length());
    for (int index = 0; index < value.length(); index++) {
      updatedSignature ^= value.charAt(index);
      updatedSignature *= 0x100000001b3L;
    }
    return updatedSignature;
  }

  /**
   * Convert a cricondenpoint to the requested unit.
   *
   * @param point envelope point as returned by {@link #calcCricondenPoint(String)}, holding the temperature in Kelvin
   * at index 0 and the pressure in bara at index 1, or {@code null} when unresolved
   * @param unit {@code "bara"} or {@code "bar"} for the pressure in bara, {@code "C"} for the temperature in degrees
   * Celsius, anything else for the temperature in Kelvin
   * @return the requested value, or {@link Double#NaN} when {@code point} is {@code null}
   */
  private double convertCricondenPoint(double[] point, String unit) {
    if (point == null) {
      return Double.NaN;
    }
    if (unit.equals("bara") || unit.equals("bar")) {
      return point[1];
    }
    if (unit.equals("C")) {
      return point[0] - 273.15;
    }
    return point[0];
  }

  /** {@inheritDoc} */
  @Override
  public double CCB(String unit) {
    return convertCricondenPoint(calcCricondenPoint("cricondenbar"), unit);
  }

  /** {@inheritDoc} */
  @Override
  public double CCT(String unit) {
    return convertCricondenPoint(calcCricondenPoint("cricondentherm"), unit);
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
   * Returns the stream wrapped by this stream unit, when present.
   *
   * <p>
   * A stream constructed from another stream is a topology node as well as a fluid-state view. Reporting that wrapped
   * stream as its inlet lets graph, exchange, and diagram APIs retain explicit terminal product streams. A standalone
   * feed or empty stream has no inlet.
   * </p>
   *
   * @return an immutable singleton containing the wrapped stream, or an empty list
   */
  @Override
  public List<StreamInterface> getInletStreams() {
    return stream == null || stream == this ? Collections.<StreamInterface>emptyList()
        : Collections.singletonList(stream);
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
