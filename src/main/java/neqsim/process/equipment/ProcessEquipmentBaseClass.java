/*
 * ProcessEquipmentBaseClass.java
 *
 * Created on 6. juni 2006, 15:12
 */

package neqsim.process.equipment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.SimulationBaseClass;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.failure.EquipmentFailureMode;
import neqsim.process.equipment.iec81346.ReferenceDesignation;
import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyStream;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.util.report.Report;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Abstract ProcessEquipmentBaseClass class.
 *
 * <p>
 * <b>Identity equality.</b> Process equipment does not override {@link Object#equals(Object)} or
 * {@link Object#hashCode()}, so two units are equal only when they are the same instance. The previous value-based
 * implementations hashed mutable state ({@code report}, {@code properties}, the attached controllers and the
 * thermodynamic system), all of which is rewritten by {@code run()}; an entry stored in a {@link java.util.HashMap}
 * before a run therefore became unreachable afterwards, and two distinct units sharing a name compared equal across
 * process areas of a {@code ProcessModel}. Registries, caches and graph-traversal sets keyed on equipment may now use a
 * plain {@link java.util.HashMap}/{@link java.util.HashSet}; {@link java.util.IdentityHashMap} remains equivalent and
 * is used where the intent is explicitly identity based. To compare two models by value use
 * {@link neqsim.process.processmodel.lifecycle.ProcessModelState#compare}.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public abstract class ProcessEquipmentBaseClass extends SimulationBaseClass implements ProcessEquipmentInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Logger for this class hierarchy (used for low-flow propagation diagnostics). */
  private static final Logger logger = LogManager.getLogger(ProcessEquipmentBaseClass.class);

  private ControllerDeviceInterface controller = null;
  ControllerDeviceInterface flowValveController = null;
  public boolean hasController = false;

  /**
   * Map of controller tag name to controller device. Supports multiple controllers per equipment.
   */
  private final Map<String, ControllerDeviceInterface> controllerMap = new LinkedHashMap<String, ControllerDeviceInterface>();
  private String specification = "TP";
  public String[][] report = new String[0][0];
  public HashMap<String, String> properties = new HashMap<String, String>();
  public EnergyStream energyStream = new EnergyStream();
  private boolean isSetEnergyStream = false;
  private final Map<String, EnergyPort> energyPorts = new LinkedHashMap<String, EnergyPort>();
  private final Set<String> externallyConnectedEnergyPorts = new LinkedHashSet<String>();
  protected boolean isSolved = true;
  private boolean isActive = true;
  private boolean lockedInactive = false;

  /**
   * Default low-flow bypass threshold in kg/hr. This value acts as an "off" sentinel: equipment that only bypasses on
   * an explicitly configured threshold tests for {@code getMinimumFlow() > DEFAULT_MINIMUM_FLOW}.
   */
  public static final double DEFAULT_MINIMUM_FLOW = 1e-20;

  private double minimumFlow = DEFAULT_MINIMUM_FLOW;

  /** Whether the caller, rather than the process auto-tuner, selected {@link #minimumFlow}. */
  private boolean minimumFlowExplicitlyConfigured = false;

  /** Whether {@link #minimumFlow} is currently owned by the automatic convergence tuner. */
  private boolean minimumFlowAutoManaged = false;

  /** Forces one real equipment evaluation after the low-flow threshold changes. */
  private boolean minimumFlowRecalculationPending = false;

  /** Guards the public setter while the auto-tuner assigns or clears its own value. */
  private transient boolean assigningAutoMinimumFlow = false;

  /**
   * Flag to enable/disable capacity analysis for this equipment. When disabled, this equipment is excluded from
   * bottleneck detection, capacity utilization summaries, and optimization routines.
   */
  private boolean capacityAnalysisEnabled = true;

  /**
   * Current failure mode of the equipment. Null means equipment is operating normally.
   */
  private EquipmentFailureMode failureMode = null;

  /**
   * Flag indicating if the equipment is in a failed state.
   */
  private boolean isFailed = false;

  /**
   * IEC 81346 reference designation for this equipment. Contains the function, product, and location aspects per IEC
   * 81346 standard.
   */
  private ReferenceDesignation referenceDesignation = new ReferenceDesignation();

  /**
   * Declared nameplate design conditions (design pressure, design temperatures, relief set pressure, construction
   * material, fail-safe action) for this equipment. Lazily created on first access.
   */
  private neqsim.process.mechanicaldesign.DesignConditions designConditions = null;

  /**
   * Capacity constraints for this equipment, keyed by constraint name. Marked transient because
   * {@link CapacityConstraint} instances may hold non-serializable lambda value suppliers. After deserialization,
   * subclasses should call {@link #initializeDefaultConstraints()} to rebuild.
   */
  private transient Map<String, CapacityConstraint> capacityConstraints;

  /**
   * Constructor for ProcessEquipmentBaseClass.
   *
   * @param name a {@link java.lang.String} object
   */
  public ProcessEquipmentBaseClass(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
  }

  /**
   * Create deep copy.
   *
   * @return a deep copy of the unit operation/process equipment
   */
  public ProcessEquipmentInterface copy() {
    try {
      ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(byteOut);
      out.writeObject(this);
      out.flush();
      ByteArrayInputStream byteIn = new ByteArrayInputStream(byteOut.toByteArray());
      ObjectInputStream in = new ObjectInputStream(byteIn);
      return (ProcessEquipmentInterface) in.readObject();
    } catch (Exception e) {
      throw new RuntimeException("Failed to copy ProcessEquipmentBaseClass", e);
    }
  }

  /**
   * getProperty.
   *
   * @param propertyName a {@link java.lang.String} object
   * @return a {@link java.lang.Object} object
   */
  public Object getProperty(String propertyName) {
    // if(properties.containsKey(propertyName)) {
    // return properties.get(properties).getValue();
    // }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void setRegulatorOutSignal(double signal) {
  }

  /** {@inheritDoc} */
  @Override
  public void setController(ControllerDeviceInterface controller) {
    this.controller = controller;
    hasController = controller != null;
    if (controller != null) {
      String tag = controller instanceof neqsim.util.NamedInterface ? controller.getName() : "default";
      controllerMap.put(tag, controller);
    }
  }

  /**
   * Setter for the field <code>flowValveController</code>.
   *
   * @param controller a {@link neqsim.process.controllerdevice.ControllerDeviceInterface} object
   */
  public void setFlowValveController(ControllerDeviceInterface controller) {
    this.flowValveController = controller;
    if (controller != null) {
      String tag = controller instanceof neqsim.util.NamedInterface ? controller.getName() : "flowValve";
      controllerMap.put(tag, controller);
    }
  }

  /** {@inheritDoc} */
  @Override
  public ControllerDeviceInterface getController() {
    return controller;
  }

  /** {@inheritDoc} */
  @Override
  public void addController(String tag, ControllerDeviceInterface controller) {
    controllerMap.put(tag, controller);
    if (this.controller == null) {
      this.controller = controller;
      hasController = true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public ControllerDeviceInterface getController(String tag) {
    return controllerMap.get(tag);
  }

  /** {@inheritDoc} */
  @Override
  public Collection<ControllerDeviceInterface> getControllers() {
    if (controllerMap.isEmpty() && controller != null) {
      return Collections.singletonList(controller);
    }
    return Collections.unmodifiableCollection(controllerMap.values());
  }

  /** {@inheritDoc} */
  @Override
  public MechanicalDesign getMechanicalDesign() {
    return new MechanicalDesign(this);
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.process.mechanicaldesign.DesignConditions getDesignConditions() {
    if (designConditions == null) {
      designConditions = new neqsim.process.mechanicaldesign.DesignConditions();
    }
    return designConditions;
  }

  /** {@inheritDoc} */
  @Override
  public void setDesignConditions(neqsim.process.mechanicaldesign.DesignConditions designConditions) {
    this.designConditions = designConditions;
  }

  /** {@inheritDoc} */
  @Override
  public void initMechanicalDesign() {
  }

  /** {@inheritDoc} */
  @Override
  public void initElectricalDesign() {
  }

  /** {@inheritDoc} */
  @Override
  public void initInstrumentDesign() {
  }

  /** {@inheritDoc} */
  @Override
  public String getSpecification() {
    return specification;
  }

  /** {@inheritDoc} */
  @Override
  public void setSpecification(String specification) {
    this.specification = specification;
  }

  /** {@inheritDoc} */
  @Override
  public String[][] reportResults() {
    return report;
  }

  /** {@inheritDoc} */
  @Override
  public boolean solved() {
    return isSolved;
  }

  /**
   * Getter for the field <code>energyStream</code>.
   *
   * @return a {@link neqsim.process.equipment.stream.EnergyStream} object
   */
  public EnergyStream getEnergyStream() {
    return energyStream;
  }

  /**
   * Registers a typed energy port on this equipment.
   *
   * <p>
   * The first registered port is connected to the equipment's existing internal energy stream so legacy result
   * reporting remains available without marking the stream as an external specification.
   *
   * @param portName unique port name
   * @param energyType physical energy domain
   * @param direction physical transfer direction
   * @param mode calculation role
   * @return the registered port
   * @throws IllegalArgumentException if the port name is already registered
   */
  public EnergyPort registerEnergyPort(String portName, EnergyType energyType, EnergyPortDirection direction,
      EnergyPortMode mode) {
    if (energyPorts.containsKey(portName)) {
      throw new IllegalArgumentException("Energy port already registered: " + portName);
    }
    EnergyPort port = new EnergyPort(portName, energyType, direction, mode);
    port.setOwnerName(getName());
    if (energyPorts.isEmpty() && energyStream != null) {
      port.connect(energyStream);
    }
    energyPorts.put(portName, port);
    return port;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, EnergyPort> getEnergyPorts() {
    return Collections.unmodifiableMap(energyPorts);
  }

  /**
   * Gets a named energy port.
   *
   * @param portName port name
   * @return the port, or {@code null} when no port has that name
   */
  public EnergyPort getEnergyPort(String portName) {
    return energyPorts.get(portName);
  }

  /**
   * Connects an energy stream to a named port and marks it as externally connected.
   *
   * @param portName port name
   * @param stream energy stream to connect
   * @throws IllegalArgumentException if the port does not exist or its type is incompatible
   */
  public void connectEnergyStream(String portName, EnergyStream stream) {
    EnergyPort port = requireEnergyPort(portName);
    port.connect(stream);
    externallyConnectedEnergyPorts.add(portName);
    if (isLegacyEnergyPort(port)) {
      energyStream = stream;
    }
    updateExternalEnergySpecificationFlag();
  }

  /**
   * Connects an energy stream with an explicit calculation role.
   *
   * @param portName port name
   * @param stream energy stream to connect
   * @param mode calculation role for this connection
   */
  public void connectEnergyStream(String portName, EnergyStream stream, EnergyPortMode mode) {
    EnergyPort port = requireEnergyPort(portName);
    Objects.requireNonNull(mode, "mode cannot be null");
    port.connect(stream);
    port.setMode(mode);
    externallyConnectedEnergyPorts.add(portName);
    if (isLegacyEnergyPort(port)) {
      energyStream = stream;
    }
    updateExternalEnergySpecificationFlag();
  }

  /**
   * Sets the calculation role of a named energy port.
   *
   * @param portName port name
   * @param mode calculation role
   * @throws IllegalArgumentException if the port does not exist
   */
  public void setEnergyPortMode(String portName, EnergyPortMode mode) {
    EnergyPort port = requireEnergyPort(portName);
    port.setMode(mode);
    updateExternalEnergySpecificationFlag();
  }

  /**
   * Disconnects the stream from a named energy port.
   *
   * @param portName port name
   * @throws IllegalArgumentException if the port does not exist
   */
  public void disconnectEnergyStream(String portName) {
    EnergyPort port = requireEnergyPort(portName);
    externallyConnectedEnergyPorts.remove(portName);
    port.disconnect();
    if (isLegacyEnergyPort(port)) {
      energyStream = new EnergyStream(getName() + "." + portName + ".internal", port.getEnergyType());
      port.connect(energyStream);
    }
    updateExternalEnergySpecificationFlag();
  }

  /**
   * Setter for the field <code>energyStream</code>.
   *
   * <p>
   * For equipment exposing exactly one typed energy port, this legacy method also connects that port. Existing
   * equipment without typed ports retains its original behavior.
   *
   * @param energyStream a {@link neqsim.process.equipment.stream.EnergyStream} object
   */
  public void setEnergyStream(EnergyStream energyStream) {
    Objects.requireNonNull(energyStream, "energyStream cannot be null");
    if (energyPorts.isEmpty()) {
      this.energyStream = energyStream;
      setEnergyStream(true);
      return;
    }
    EnergyPort legacyPort = energyPorts.values().iterator().next();
    legacyPort.connect(energyStream);
    externallyConnectedEnergyPorts.add(legacyPort.getName());
    this.energyStream = energyStream;
    updateExternalEnergySpecificationFlag();
  }

  /**
   * Setter for the field <code>energyStream</code>.
   *
   * @param isSetEnergyStream a boolean
   */
  public void setEnergyStream(boolean isSetEnergyStream) {
    this.isSetEnergyStream = isSetEnergyStream;
  }

  /**
   * isSetEnergyStream.
   *
   * @return a boolean
   */
  public boolean isSetEnergyStream() {
    if (energyPorts.isEmpty()) {
      return isSetEnergyStream;
    }
    for (String portName : externallyConnectedEnergyPorts) {
      EnergyPort port = energyPorts.get(portName);
      if (port != null && port.isConnected() && port.getMode() == EnergyPortMode.SPECIFICATION) {
        return true;
      }
    }
    return false;
  }

  private EnergyPort requireEnergyPort(String portName) {
    EnergyPort port = energyPorts.get(portName);
    if (port == null) {
      throw new IllegalArgumentException("Unknown energy port: " + portName);
    }
    return port;
  }

  private boolean isLegacyEnergyPort(EnergyPort port) {
    return !energyPorts.isEmpty() && energyPorts.values().iterator().next() == port;
  }

  private void updateExternalEnergySpecificationFlag() {
    isSetEnergyStream = false;
    for (String portName : externallyConnectedEnergyPorts) {
      EnergyPort port = energyPorts.get(portName);
      if (port != null && port.isConnected() && port.getMode() == EnergyPortMode.SPECIFICATION) {
        isSetEnergyStream = true;
        return;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getPressure() {
    return getFluid().getPressure();
  }

  /** {@inheritDoc} */
  @Override
  public double getPressure(String unit) {
    return getFluid().getPressure(unit);
  }

  /** {@inheritDoc} */
  @Override
  public void setPressure(double pressure) {
    getFluid().setPressure(pressure);
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
  public void setTemperature(double temperature) {
    getFluid().setTemperature(temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropyProduction(String unit) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance() {
    return getMassBalance("kg/sec");
  }

  /** {@inheritDoc} */
  @Override
  public double getExergyChange(String unit, double surroundingTemperature) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public void runConditionAnalysis(ProcessEquipmentInterface refExchanger) {
  }

  public String conditionAnalysisMessage = "";

  /** {@inheritDoc} */
  @Override
  public String getConditionAnalysisMessage() {
    return conditionAnalysisMessage;
  }

  /** {@inheritDoc} */
  @Override
  public String[][] getResultTable() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    return toJson();
  }

  /** {@inheritDoc} */
  @Override
  public String getReport_json() {
    return new Report(this).generateJsonReport();
  }

  /** {@inheritDoc} */
  @Override
  public void run_step(UUID id) {
  }

  /**
   * Getter for the field <code>minimumFlow</code>, the low-flow bypass threshold in kg/hr.
   *
   * @return low-flow bypass threshold in kg/hr
   */
  @Override
  public double getMinimumFlow() {
    return minimumFlow;
  }

  /**
   * Setter for the field <code>minimumFlow</code>, the low-flow bypass threshold in kg/hr.
   *
   * <p>
   * Equipment whose primary inlet mass flow falls below this value auto-bypasses via
   * {@link #checkAndHandleLowFlow(neqsim.process.equipment.stream.StreamInterface, UUID)}. The unit is kg/hr for every
   * equipment type.
   * </p>
   *
   * @param minimumFlow low-flow bypass threshold in kg/hr
   */
  @Override
  public void setMinimumFlow(double minimumFlow) {
    this.minimumFlow = minimumFlow;
    minimumFlowRecalculationPending = true;
    if (!assigningAutoMinimumFlow) {
      minimumFlowExplicitlyConfigured = Double.compare(minimumFlow, DEFAULT_MINIMUM_FLOW) != 0;
      minimumFlowAutoManaged = false;
    }
  }

  /**
   * Applies a low-flow threshold owned by the automatic process-convergence tuner.
   *
   * <p>
   * A caller-supplied non-default value always wins. Ownership is stored on the equipment itself so it survives
   * {@code ProcessSystem.copy()} and so a caller override made after tuning cannot be overwritten by a later retune or
   * reset.
   * </p>
   *
   * @param minimumFlow automatically derived threshold in kg/hr
   * @return true when this equipment is managed by the auto-tuner, false when an explicit value is protected
   * @throws IllegalArgumentException if {@code minimumFlow} is negative or not finite
   */
  public boolean applyAutoMinimumFlow(double minimumFlow) {
    if (Double.isNaN(minimumFlow) || Double.isInfinite(minimumFlow) || minimumFlow < 0.0) {
      throw new IllegalArgumentException(
          "Automatic minimum flow must be a finite non-negative number, was " + minimumFlow);
    }
    if (minimumFlowExplicitlyConfigured) {
      return false;
    }
    if (!minimumFlowAutoManaged && Double.compare(getMinimumFlow(), DEFAULT_MINIMUM_FLOW) != 0) {
      minimumFlowExplicitlyConfigured = true;
      return false;
    }
    assigningAutoMinimumFlow = true;
    try {
      setMinimumFlow(minimumFlow);
    } finally {
      assigningAutoMinimumFlow = false;
    }
    minimumFlowAutoManaged = true;
    return true;
  }

  /**
   * Clears an automatically assigned low-flow threshold.
   *
   * @return true when an automatic threshold was cleared, false when the current value belongs to the caller
   */
  public boolean resetAutoMinimumFlow() {
    if (!minimumFlowAutoManaged) {
      return false;
    }
    assigningAutoMinimumFlow = true;
    try {
      setMinimumFlow(DEFAULT_MINIMUM_FLOW);
    } finally {
      assigningAutoMinimumFlow = false;
    }
    minimumFlowAutoManaged = false;
    minimumFlowExplicitlyConfigured = false;
    return true;
  }

  /**
   * Returns whether the caller explicitly configured a non-default low-flow threshold.
   *
   * @return true for a caller-owned threshold
   */
  public boolean isMinimumFlowExplicitlyConfigured() {
    return minimumFlowExplicitlyConfigured;
  }

  /**
   * Returns whether the current low-flow threshold is owned by the process auto-tuner.
   *
   * @return true for an automatically managed threshold
   */
  public boolean isMinimumFlowAutoManaged() {
    return minimumFlowAutoManaged;
  }

  /**
   * Returns whether the threshold changed since the last successful equipment evaluation.
   *
   * @return true when process scheduling must force one evaluation
   */
  public boolean isMinimumFlowRecalculationPending() {
    return minimumFlowRecalculationPending;
  }

  /** Marks the current low-flow threshold as evaluated successfully. */
  public void clearMinimumFlowRecalculationPending() {
    minimumFlowRecalculationPending = false;
  }

  /**
   * Sets the low-flow bypass threshold in a chosen mass-flow unit.
   *
   * <p>
   * The threshold is stored internally in kg/hr. This overload removes the need for callers to hand-convert, which was
   * a recurring source of silent errors (a "50" meant as tonnes/day silently becoming 50 kg/hr, or vice versa).
   * </p>
   *
   * @param minimumFlow low-flow bypass threshold expressed in {@code unit}
   * @param unit mass-flow unit; one of kg/hr, kg/h, kg/sec, kg/s, kg/min, tonne/hr, ton/hr, tonne/day, ton/day, MT/hr,
   * lb/hr, lbm/hr
   * @throws IllegalArgumentException if the unit is not a recognised mass-flow unit
   */
  public void setMinimumFlow(double minimumFlow, String unit) {
    setMinimumFlow(minimumFlow * massFlowConversionToKgPerHour(unit));
  }

  /**
   * Gets the low-flow bypass threshold expressed in a chosen mass-flow unit.
   *
   * @param unit mass-flow unit; see {@link #setMinimumFlow(double, String)} for the accepted values
   * @return the low-flow bypass threshold in {@code unit}
   * @throws IllegalArgumentException if the unit is not a recognised mass-flow unit
   */
  public double getMinimumFlow(String unit) {
    return getMinimumFlow() / massFlowConversionToKgPerHour(unit);
  }

  /**
   * Conversion factor from a mass-flow unit to kg/hr.
   *
   * <p>
   * Deliberately limited to mass-flow units: the low-flow threshold is compared against
   * {@code stream.getFlowRate("kg/hr")}, and volumetric or molar units would require a fluid that a not-yet-solved unit
   * operation does not have.
   * </p>
   *
   * @param unit mass-flow unit name (case-insensitive, surrounding whitespace ignored)
   * @return the factor that converts a value in {@code unit} to kg/hr
   * @throws IllegalArgumentException if {@code unit} is null or not a recognised mass-flow unit
   */
  public static double massFlowConversionToKgPerHour(String unit) {
    if (unit == null) {
      throw new IllegalArgumentException("Mass-flow unit must not be null");
    }
    String key = unit.trim().toLowerCase(java.util.Locale.US);
    if (key.equals("kg/hr") || key.equals("kg/h") || key.equals("kg/hour")) {
      return 1.0;
    } else if (key.equals("kg/sec") || key.equals("kg/s")) {
      return 3600.0;
    } else if (key.equals("kg/min")) {
      return 60.0;
    } else if (key.equals("kg/day")) {
      return 1.0 / 24.0;
    } else if (key.equals("tonne/hr") || key.equals("ton/hr") || key.equals("mt/hr") || key.equals("t/hr")) {
      return 1000.0;
    } else if (key.equals("tonne/day") || key.equals("ton/day") || key.equals("t/day")) {
      return 1000.0 / 24.0;
    } else if (key.equals("lb/hr") || key.equals("lbm/hr")) {
      return 0.45359237;
    }
    throw new IllegalArgumentException("Unsupported mass-flow unit '" + unit
        + "' for the low-flow threshold. Use kg/hr, kg/sec, kg/min, kg/day, " + "tonne/hr, tonne/day or lb/hr.");
  }

  /**
   * Getter for the field <code>isActive</code>.
   *
   * @return a boolean
   */
  @Override
  public boolean isActive() {
    return isActive;
  }

  /**
   * Setter for the field <code>isActive</code>.
   *
   * @param isActive a boolean
   */
  @Override
  public void isActive(boolean isActive) {
    this.isActive = isActive;
  }

  /**
   * Convenience helper for equipment to auto-bypass when its primary inlet flow is below the configured low-flow
   * threshold.
   *
   * <p>
   * Typical usage at the start of {@code run(UUID)}:
   * </p>
   *
   * <pre>
   * if (checkAndHandleLowFlow(getInletStream(), id)) {
   *   return;
   * }
   * </pre>
   *
   * <p>
   * When the inlet mass flow is below {@link #getMinimumFlow()} the equipment is marked inactive via
   * {@link #isActive(boolean)} and {@link #setCalculationIdentifier(UUID)} is called so the scheduler treats the unit
   * as solved for the current calculation pass. Otherwise the equipment is (re)marked active and {@code false} is
   * returned so the caller can continue normal execution.
   * </p>
   *
   * @param inlet primary inlet stream (may be null, in which case no bypass is applied)
   * @param id current calculation identifier
   * @return true if the equipment was auto-bypassed and {@code run()} should return immediately, false if the equipment
   * should execute normally
   */
  protected boolean checkAndHandleLowFlow(neqsim.process.equipment.stream.StreamInterface inlet, UUID id) {
    if (inlet == null) {
      return false;
    }
    double flow;
    try {
      flow = inlet.getFlowRate("kg/hr");
    } catch (NullPointerException ex) {
      // Inlet stream has not been solved yet (no thermo system attached). Treat as
      // "not low flow" so the unit runs normally and surfaces the real error.
      return false;
    }
    if (flow < getMinimumFlow()) {
      isActive(false);
      setCalculationIdentifier(id);
      return true;
    }
    isActive(true);
    return false;
  }

  /**
   * Convenience helper for auto-bypassing equipment to propagate zero mass flow to one or more outlet streams, so
   * downstream equipment also auto-bypasses via
   * {@link #checkAndHandleLowFlow(neqsim.process.equipment.stream.StreamInterface, UUID)}.
   *
   * <p>
   * Each outlet stream's total flow rate is set to {@code 0.0 kg/hr} and the calculation identifier {@code id} is
   * stamped onto it so the scheduler treats it as solved for the current pass. Null outlets are skipped silently.
   * Failures to mutate the thermo system are logged at DEBUG level and otherwise swallowed so a missing thermo system
   * on one outlet does not abort propagation to the others.
   * </p>
   *
   * @param id current calculation identifier
   * @param outlets outlet streams to zero out (may include null entries)
   */
  protected void propagateZeroFlow(UUID id, neqsim.process.equipment.stream.StreamInterface... outlets) {
    if (outlets == null) {
      return;
    }
    for (neqsim.process.equipment.stream.StreamInterface outlet : outlets) {
      if (outlet == null) {
        continue;
      }
      try {
        outlet.getThermoSystem().setTotalFlowRate(0.0, "kg/hr");
        outlet.setCalculationIdentifier(id);
      } catch (NullPointerException ex) {
        logger.debug(
            "Could not propagate zero flow from inactive '" + getName() + "' (outlet has no thermo system attached)",
            ex);
      }
    }
  }

  /**
   * Returns whether this equipment has been explicitly (manually) deactivated and should remain bypassed across
   * simulation runs.
   *
   * <p>
   * Unlike the transient {@link #isActive()} flag — which is set automatically by {@link #checkAndHandleLowFlow} based
   * on the current inlet flow — {@code lockedInactive} is a user-controlled "hard bypass" flag.
   * {@link neqsim.process.processmodel.ProcessSystem} resets {@code isActive} to {@code true} at the start of each run
   * for every unit where {@code lockedInactive == false}; locked units remain inactive and their {@code run()} method
   * is never invoked.
   * </p>
   *
   * @return true if the equipment is manually locked in the inactive state
   */
  @Override
  public boolean isLockedInactive() {
    return lockedInactive;
  }

  /**
   * Manually lock or unlock this equipment in the inactive (bypassed) state. When set to {@code true} the equipment is
   * also marked inactive ({@link #isActive(boolean)}) so the next scheduler pass skips it; when set to {@code false}
   * the equipment is re-marked active and will be evaluated on the next simulation run.
   *
   * @param lockedInactive true to bypass this equipment indefinitely; false to allow normal execution (default)
   */
  @Override
  public void setLockedInactive(boolean lockedInactive) {
    this.lockedInactive = lockedInactive;
    if (lockedInactive) {
      isActive(false);
    } else {
      isActive(true);
    }
  }

  /**
   * Checks if capacity analysis is enabled for this equipment.
   *
   * <p>
   * When disabled, this equipment is excluded from bottleneck detection, capacity utilization summaries, and
   * optimization routines. The equipment still tracks its constraints but doesn't contribute to system-level analysis.
   * </p>
   *
   * @return true if capacity analysis is enabled (default is true)
   */
  public boolean isCapacityAnalysisEnabled() {
    return capacityAnalysisEnabled;
  }

  /**
   * Enables or disables capacity analysis for this equipment.
   *
   * <p>
   * When disabled, this equipment is excluded from:
   * <ul>
   * <li>System bottleneck detection ({@code ProcessSystem.findBottleneck()})</li>
   * <li>Capacity utilization summaries ({@code ProcessSystem.getCapacityUtilizationSummary()})</li>
   * <li>Equipment near capacity lists ({@code ProcessSystem.getEquipmentNearCapacityLimit()})</li>
   * <li>Optimization constraint checking</li>
   * </ul>
   * <p>
   * The equipment still calculates and tracks its constraints internally.
   * </p>
   *
   * @param enabled true to include in capacity analysis, false to exclude
   */
  public void setCapacityAnalysisEnabled(boolean enabled) {
    this.capacityAnalysisEnabled = enabled;
  }

  /**
   * Gets the current failure mode of the equipment.
   *
   * @return the failure mode, or null if equipment is operating normally
   */
  public EquipmentFailureMode getFailureMode() {
    return failureMode;
  }

  /**
   * Sets a failure mode on the equipment.
   *
   * <p>
   * When a failure mode is set, the equipment is marked as failed and its behavior changes according to the failure
   * mode characteristics (capacity factor, etc.). Setting null clears the failure.
   * </p>
   *
   * @param failureMode the failure mode to apply, or null to clear failure
   */
  public void setFailureMode(EquipmentFailureMode failureMode) {
    this.failureMode = failureMode;
    this.isFailed = (failureMode != null);
    if (isFailed && failureMode.isCompleteFailure()) {
      this.isActive = false;
      this.capacityAnalysisEnabled = false;
    }
  }

  /**
   * Checks if the equipment is in a failed state.
   *
   * @return true if the equipment has a failure mode set
   */
  public boolean isFailed() {
    return isFailed;
  }

  /**
   * Simulates a trip (complete failure) on the equipment.
   *
   * <p>
   * Convenience method that applies a standard trip failure mode. The equipment becomes inactive and is excluded from
   * capacity analysis.
   * </p>
   */
  public void simulateTrip() {
    setFailureMode(EquipmentFailureMode.trip(this.getClass().getSimpleName()));
  }

  /**
   * Simulates degraded operation at a specified capacity.
   *
   * @param capacityPercent remaining capacity percentage (0-100)
   */
  public void simulateDegradedOperation(double capacityPercent) {
    setFailureMode(EquipmentFailureMode.degraded(capacityPercent));
  }

  /**
   * Restores the equipment from a failed state.
   *
   * <p>
   * Clears any failure mode and restores the equipment to normal operation.
   * </p>
   */
  public void restoreFromFailure() {
    this.failureMode = null;
    this.isFailed = false;
    this.isActive = true;
    this.capacityAnalysisEnabled = true;
  }

  /** {@inheritDoc} */
  @Override
  public ReferenceDesignation getReferenceDesignation() {
    return referenceDesignation;
  }

  /** {@inheritDoc} */
  @Override
  public void setReferenceDesignation(ReferenceDesignation referenceDesignation) {
    this.referenceDesignation = referenceDesignation != null ? referenceDesignation : new ReferenceDesignation();
  }

  /**
   * Gets the effective capacity factor considering any failure mode.
   *
   * @return capacity factor (0.0 to 1.0), where 1.0 is full capacity
   */
  public double getEffectiveCapacityFactor() {
    if (failureMode == null) {
      return 1.0;
    }
    return failureMode.getCapacityFactor();
  }

  // ============================================================
  // Capacity Constraint Support (universal base implementation)
  // ============================================================

  /**
   * Ensures the capacity constraints map is initialized.
   *
   * <p>
   * The map is transient (not serialized) so it may be null after deserialization. This method lazily initializes it
   * and calls {@link #initializeDefaultConstraints()} to let subclasses re-attach their lambda value suppliers.
   * </p>
   */
  private void ensureCapacityConstraintsInitialized() {
    if (capacityConstraints == null) {
      capacityConstraints = new LinkedHashMap<String, CapacityConstraint>();
      initializeDefaultConstraints();
    }
  }

  /**
   * Hook for subclasses to set up default capacity constraints.
   *
   * <p>
   * Called lazily when constraints are first accessed, and after deserialization. Subclasses should override this to
   * add equipment-specific constraints using {@link #addCapacityConstraint(CapacityConstraint)}. The default
   * implementation does nothing.
   * </p>
   */
  protected void initializeDefaultConstraints() {
    // Default no-op — subclasses override to add equipment-specific constraints
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getCapacityConstraints() {
    ensureCapacityConstraintsInitialized();
    return Collections.unmodifiableMap(capacityConstraints);
  }

  /** {@inheritDoc} */
  @Override
  public void addCapacityConstraint(CapacityConstraint constraint) {
    ensureCapacityConstraintsInitialized();
    if (constraint != null) {
      capacityConstraints.put(constraint.getName(), constraint);
    }
  }

  /**
   * Removes a capacity constraint by name.
   *
   * @param constraintName the name of the constraint to remove
   * @return true if the constraint was found and removed
   */
  public boolean removeCapacityConstraint(String constraintName) {
    ensureCapacityConstraintsInitialized();
    return capacityConstraints.remove(constraintName) != null;
  }

  /**
   * Clears all capacity constraints from this equipment.
   */
  public void clearCapacityConstraints() {
    ensureCapacityConstraintsInitialized();
    capacityConstraints.clear();
  }

  /**
   * Derives capacity constraints from this equipment's mechanical-design limits and registers them for
   * capacity/utilization analysis.
   *
   * <p>
   * This is the opt-in bridge that makes the limits configured on the equipment's
   * {@link neqsim.process.mechanicaldesign.MechanicalDesign} (for example
   * {@code getMechanicalDesign().setMaxDesignPower(kW)} or {@code setMaxDesignVolumeFlow(...)}) surface in
   * {@link #getMaxUtilization()}, {@link #getBottleneckConstraint()} and the utilization snapshot. Call it after the
   * design limits have been set and after the process has run, since the derived metrics depend on live stream
   * conditions.
   * </p>
   *
   * <p>
   * The constraints are added through the polymorphic {@link #addCapacityConstraint(CapacityConstraint)} method so they
   * are registered in the correct constraint map even for equipment types (such as heat exchangers, valves and
   * compressors) that maintain their own capacity-constraint storage. The derived constraints use stable names (for
   * example {@code "design pressure drop"}), so the method is idempotent: re-invoking it overwrites the previous values
   * rather than creating duplicates. Call it again whenever design limits or operating conditions change. It never
   * throws — failures to read the mechanical design are treated as "no derived constraints".
   * </p>
   *
   * @return the number of mechanical-design-derived constraints that were registered
   */
  @Override
  public int applyMechanicalDesignCapacityConstraints() {
    int added = 0;
    try {
      MechanicalDesign design = getMechanicalDesign();
      if (design != null) {
        List<CapacityConstraint> derived = design.getDesignCapacityConstraints();
        if (derived != null) {
          for (CapacityConstraint constraint : derived) {
            if (constraint != null) {
              addCapacityConstraint(constraint);
              added++;
            }
          }
        }
      }
    } catch (RuntimeException ex) {
      logger.debug("Could not derive mechanical-design capacity constraints", ex);
    }
    return added;
  }

  /** {@inheritDoc} */
  @Override
  public CapacityConstraint getBottleneckConstraint() {
    ensureCapacityConstraintsInitialized();
    CapacityConstraint bottleneck = null;
    double maxUtil = -1.0;
    for (CapacityConstraint c : capacityConstraints.values()) {
      if (c.isEnabled()) {
        double util = c.getUtilization();
        if (util > maxUtil) {
          maxUtil = util;
          bottleneck = c;
        }
      }
    }
    return bottleneck;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCapacityExceeded() {
    ensureCapacityConstraintsInitialized();
    for (CapacityConstraint c : capacityConstraints.values()) {
      if (c.isEnabled() && c.isViolated()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHardLimitExceeded() {
    ensureCapacityConstraintsInitialized();
    for (CapacityConstraint c : capacityConstraints.values()) {
      if (c.isEnabled() && c.isHardLimitExceeded()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public double getMaxUtilization() {
    ensureCapacityConstraintsInitialized();
    double maxUtil = 0.0;
    for (CapacityConstraint c : capacityConstraints.values()) {
      if (c.isEnabled()) {
        double util = c.getUtilization();
        if (util > maxUtil) {
          maxUtil = util;
        }
      }
    }
    return maxUtil;
  }

  /** {@inheritDoc} */
  @Override
  public double getMaxUtilizationPercent() {
    return getMaxUtilization() * 100.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getAvailableMargin() {
    return 1.0 - getMaxUtilization();
  }

  /** {@inheritDoc} */
  @Override
  public double getAvailableMarginPercent() {
    return getAvailableMargin() * 100.0;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isNearCapacityLimit() {
    ensureCapacityConstraintsInitialized();
    for (CapacityConstraint c : capacityConstraints.values()) {
      if (c.isEnabled() && c.isNearLimit()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Double> getUtilizationSummary() {
    ensureCapacityConstraintsInitialized();
    Map<String, Double> summary = new java.util.LinkedHashMap<String, Double>();
    for (Map.Entry<String, CapacityConstraint> entry : capacityConstraints.entrySet()) {
      CapacityConstraint c = entry.getValue();
      if (c.isEnabled()) {
        summary.put(entry.getKey(), c.getUtilization() * 100.0);
      }
    }
    return summary;
  }

  /**
   * Evaluates all capacity constraints and returns a summary string.
   *
   * <p>
   * Useful for logging and diagnostics. Each enabled constraint is evaluated and its utilization is reported.
   * Constraints that are violated or near their limit are flagged.
   * </p>
   *
   * @return multi-line summary of constraint status
   */
  public String getConstraintEvaluationReport() {
    ensureCapacityConstraintsInitialized();
    StringBuilder sb = new StringBuilder();
    sb.append("Capacity constraints for ").append(getName()).append(":\n");
    for (Map.Entry<String, CapacityConstraint> entry : capacityConstraints.entrySet()) {
      CapacityConstraint c = entry.getValue();
      if (c.isEnabled()) {
        sb.append("  ").append(entry.getKey());
        sb.append(": ").append(String.format("%.1f%%", c.getUtilization() * 100.0));
        if (c.isViolated()) {
          sb.append(" [VIOLATED]");
        } else if (c.isNearLimit()) {
          sb.append(" [WARNING]");
        }
        sb.append("\n");
      }
    }
    return sb.toString();
  }

  /**
   * Reports whether base-equipment state can be captured without losing attached mutable objects.
   *
   * <p>
   * The reusable base snapshot covers simulation clock/identifier state, activation and low-flow state, specification,
   * reports, properties, capacity-analysis enablement and IEC 81346 designation. Equipment with attached controllers,
   * active energy ports, failure state, design conditions or runtime capacity constraints remains fail-closed until the
   * concrete participant explicitly composes those independently owned objects into its transaction.
   * </p>
   *
   * @return {@code null} when the reusable base snapshot is complete, otherwise a deterministic blocking diagnostic
   */
  protected final String getBaseTransientStateCoverageIssue() {
    if (controller != null || flowValveController != null || hasController || !controllerMap.isEmpty()) {
      return "attached controller state must participate independently";
    }
    if (isSetEnergyStream || !energyPorts.isEmpty() || !externallyConnectedEnergyPorts.isEmpty()) {
      return "connected energy-stream and energy-port state is not covered by the equipment snapshot";
    }
    if (failureMode != null || isFailed) {
      return "equipment failure state is not covered by the reusable base snapshot";
    }
    if (designConditions != null) {
      return "mutable design-condition state is not covered by the reusable base snapshot";
    }
    if (capacityConstraints != null && !capacityConstraints.isEmpty()) {
      return "runtime capacity-constraint state is not covered by the reusable base snapshot";
    }
    return null;
  }

  /**
   * Captures mutable state owned directly by {@code ProcessEquipmentBaseClass}.
   *
   * @return immutable, serializable base-equipment checkpoint
   */
  protected final ProcessEquipmentTransientState captureBaseTransientState() {
    return new ProcessEquipmentTransientState(getName(), calcIdentifier, calculateSteadyState, time, isRunInSteps(),
        specification, copyTransientReport(report), properties == null ? null : new HashMap<String, String>(properties),
        isSolved, isActive, lockedInactive, minimumFlow, minimumFlowExplicitlyConfigured, minimumFlowAutoManaged,
        minimumFlowRecalculationPending, capacityAnalysisEnabled, referenceDesignation,
        copyTransientReferenceDesignation(referenceDesignation));
  }

  /**
   * Restores a checkpoint captured by {@link #captureBaseTransientState()} without replacing this equipment instance.
   *
   * @param snapshot base-equipment checkpoint
   */
  protected final void restoreBaseTransientState(ProcessEquipmentTransientState snapshot) {
    Objects.requireNonNull(snapshot, "base-equipment transient snapshot cannot be null");
    setName(snapshot.name);
    calcIdentifier = snapshot.calculationIdentifier;
    calculateSteadyState = snapshot.calculateSteadyState;
    time = snapshot.time;
    setRunInSteps(snapshot.runInSteps);
    specification = snapshot.specification;
    report = copyTransientReport(snapshot.report);
    properties = snapshot.properties == null ? null : new HashMap<String, String>(snapshot.properties);
    isSolved = snapshot.solved;
    isActive = snapshot.active;
    lockedInactive = snapshot.lockedInactive;
    minimumFlow = snapshot.minimumFlow;
    minimumFlowExplicitlyConfigured = snapshot.minimumFlowExplicitlyConfigured;
    minimumFlowAutoManaged = snapshot.minimumFlowAutoManaged;
    minimumFlowRecalculationPending = snapshot.minimumFlowRecalculationPending;
    assigningAutoMinimumFlow = false;
    capacityAnalysisEnabled = snapshot.capacityAnalysisEnabled;
    referenceDesignation = snapshot.referenceDesignationReference;
    restoreTransientReferenceDesignation(referenceDesignation, snapshot.referenceDesignationState);
  }

  private static String[][] copyTransientReport(String[][] source) {
    if (source == null) {
      return null;
    }
    String[][] copy = new String[source.length][];
    for (int i = 0; i < source.length; i++) {
      copy[i] = source[i] == null ? null : source[i].clone();
    }
    return copy;
  }

  private static ReferenceDesignation copyTransientReferenceDesignation(ReferenceDesignation source) {
    if (source == null) {
      return null;
    }
    ReferenceDesignation copy = new ReferenceDesignation();
    copy.setFunctionDesignation(source.getFunctionDesignation());
    copy.setProductDesignation(source.getProductDesignation());
    copy.setLocationDesignation(source.getLocationDesignation());
    copy.setLetterCode(source.getLetterCode());
    copy.setSequenceNumber(source.getSequenceNumber());
    return copy;
  }

  private static void restoreTransientReferenceDesignation(ReferenceDesignation target, ReferenceDesignation source) {
    if (target == null || source == null) {
      if (target != source) {
        throw new IllegalArgumentException("Reference-designation snapshot structure changed");
      }
      return;
    }
    target.setFunctionDesignation(source.getFunctionDesignation());
    target.setProductDesignation(source.getProductDesignation());
    target.setLocationDesignation(source.getLocationDesignation());
    target.setLetterCode(source.getLetterCode());
    target.setSequenceNumber(source.getSequenceNumber());
  }

  /** Serializable state owned directly by {@code ProcessEquipmentBaseClass}. */
  protected static final class ProcessEquipmentTransientState implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final UUID calculationIdentifier;
    private final boolean calculateSteadyState;
    private final double time;
    private final boolean runInSteps;
    private final String specification;
    private final String[][] report;
    private final HashMap<String, String> properties;
    private final boolean solved;
    private final boolean active;
    private final boolean lockedInactive;
    private final double minimumFlow;
    private final boolean minimumFlowExplicitlyConfigured;
    private final boolean minimumFlowAutoManaged;
    private final boolean minimumFlowRecalculationPending;
    private final boolean capacityAnalysisEnabled;
    private final ReferenceDesignation referenceDesignationReference;
    private final ReferenceDesignation referenceDesignationState;

    private ProcessEquipmentTransientState(String name, UUID calculationIdentifier, boolean calculateSteadyState,
        double time, boolean runInSteps, String specification, String[][] report, HashMap<String, String> properties,
        boolean solved, boolean active, boolean lockedInactive, double minimumFlow,
        boolean minimumFlowExplicitlyConfigured, boolean minimumFlowAutoManaged,
        boolean minimumFlowRecalculationPending, boolean capacityAnalysisEnabled,
        ReferenceDesignation referenceDesignationReference, ReferenceDesignation referenceDesignationState) {
      this.name = name;
      this.calculationIdentifier = calculationIdentifier;
      this.calculateSteadyState = calculateSteadyState;
      this.time = time;
      this.runInSteps = runInSteps;
      this.specification = specification;
      this.report = report;
      this.properties = properties;
      this.solved = solved;
      this.active = active;
      this.lockedInactive = lockedInactive;
      this.minimumFlow = minimumFlow;
      this.minimumFlowExplicitlyConfigured = minimumFlowExplicitlyConfigured;
      this.minimumFlowAutoManaged = minimumFlowAutoManaged;
      this.minimumFlowRecalculationPending = minimumFlowRecalculationPending;
      this.capacityAnalysisEnabled = capacityAnalysisEnabled;
      this.referenceDesignationReference = referenceDesignationReference;
      this.referenceDesignationState = referenceDesignationState;
    }
  }

}
