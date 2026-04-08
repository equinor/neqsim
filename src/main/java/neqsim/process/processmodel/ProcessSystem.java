package neqsim.process.processmodel;

// Reorganized imports into proper groups and order
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.SimulationBaseClass;
import neqsim.process.ProcessElementInterface;
import neqsim.process.alarm.ProcessAlarmManager;
import neqsim.process.conditionmonitor.ConditionMonitor;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.equipment.EquipmentEnum;
import neqsim.process.equipment.EquipmentFactory;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.ejector.Ejector;
import neqsim.process.equipment.expander.TurboExpanderCompressor;
import neqsim.process.equipment.flare.FlareStack;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.reactor.FurnaceBurner;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.heatexchanger.MultiStreamHeatExchangerInterface;
import neqsim.process.equipment.manifold.Manifold;
import neqsim.process.equipment.mixer.MixerInterface;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.util.RecycleController;
import neqsim.process.equipment.util.Setter;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.processmodel.graph.ProcessEdge;
import neqsim.process.processmodel.graph.ProcessGraph;
import neqsim.process.processmodel.graph.ProcessGraphBuilder;
import neqsim.process.processmodel.graph.ProcessNode;
import neqsim.process.util.event.ProcessEvent;
import neqsim.process.util.event.ProcessEventBus;
import neqsim.process.util.report.Report;
import neqsim.process.util.optimizer.FlowRateOptimizer;
import neqsim.process.util.optimizer.ProcessOptimizationEngine;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Represents a process system containing unit operations.
 *
 * @author esol
 */
public class ProcessSystem extends SimulationBaseClass {
  /**
   * Serialization version UID.
   */
  private static final long serialVersionUID = 1000;

  /**
   * Logger object for class.
   */
  static Logger logger = LogManager.getLogger(ProcessSystem.class);

  transient Thread thisThread;
  private MeasurementHistory measurementHistory = new MeasurementHistory();
  private double surroundingTemperature = 288.15;
  private int timeStepNumber = 0;
  /**
   * List of unit operations in the process system.
   */
  private List<ProcessEquipmentInterface> unitOperations = new ArrayList<>();
  List<MeasurementDeviceInterface> measurementDevices =
      new ArrayList<MeasurementDeviceInterface>(0);
  List<ControllerDeviceInterface> controllerDevices = new ArrayList<ControllerDeviceInterface>(0);
  private List<ProcessConnection> connections = new ArrayList<ProcessConnection>(0);
  private ProcessAlarmManager alarmManager = new ProcessAlarmManager();
  RecycleController recycleController = new RecycleController();
  private double timeStep = 1.0;
  private boolean runStep = false;

  private final Map<String, Integer> equipmentCounter = new HashMap<>();
  private ProcessEquipmentInterface lastAddedUnit = null;
  private transient ProcessSystem initialStateSnapshot;
  private double massBalanceErrorThreshold = 0.1; // Default 0.1% error threshold
  private double minimumFlowForMassBalanceError = 1e-6; // Default 1e-6 kg/sec

  // Transient simulation settings
  private int maxTransientIterations = 3; // Number of iterations within each time step
  private boolean enableMassBalanceTracking = false;
  private double previousTotalMass = 0.0;
  private double massBalanceError = 0.0;

  // Graph-based execution fields
  /** Cached process graph for topology analysis. */
  private transient ProcessGraph cachedGraph = null;
  /** Flag indicating if the cached graph needs to be rebuilt. */
  private boolean graphDirty = true;
  /** Cached parallel execution plan: grouped nodes per level for runParallel(). */
  private transient List<List<List<ProcessNode>>> cachedParallelPlan = null;
  /** Cached result of hasAdjusters() - null means not yet computed. */
  private transient Boolean cachedHasAdjusters = null;
  /** Cached result of hasRecycles() - null means not yet computed. */
  private transient Boolean cachedHasRecycles = null;
  /** Whether to use graph-based execution order instead of insertion order. */
  private boolean useGraphBasedExecution = false;
  /**
   * Whether to use optimized execution (parallel/hybrid) by default when run() is called. When
   * true, run() delegates to runOptimized() which automatically selects the best strategy. When
   * false, run() uses sequential execution in insertion order (legacy behavior). Default is true
   * for optimal performance - runOptimized() automatically falls back to sequential execution for
   * processes with multi-input equipment (mixers, heat exchangers, etc.) to preserve correct mass
   * balance.
   */
  private boolean useOptimizedExecution = true;

  /**
   * Transient listener for simulation progress callbacks. Used for real-time visualization in
   * Jupyter notebooks and digital twin dashboards. Marked transient to avoid serialization issues.
   */
  private transient SimulationProgressListener progressListener = null;

  /**
   * When true, lifecycle events are published to the ProcessEventBus singleton during simulation.
   * Default is false for zero overhead when not needed. Enable via setPublishEvents(true).
   */
  private boolean publishEvents = false;

  /**
   * When true, validateSetup() is auto-invoked on each equipment unit before the first iteration.
   * Validation warnings are logged but do not abort execution. Enable via setAutoValidate(true).
   */
  private boolean autoValidate = false;

  /**
   * Interface for monitoring simulation progress during execution. Implementations receive
   * callbacks after each unit operation completes, enabling real-time visualization, progress
   * tracking, and early termination detection.
   *
   * <p>
   * This interface is designed for integration with:
   * <ul>
   * <li>Jupyter notebooks for live plotting</li>
   * <li>Digital twin dashboards for production monitoring</li>
   * <li>Debugging tools for convergence analysis</li>
   * <li>Training/educational applications</li>
   * </ul>
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public interface SimulationProgressListener {
    /**
     * Called after each unit operation completes successfully.
     *
     * @param unit the completed unit operation
     * @param unitIndex zero-based index of current unit in execution order
     * @param totalUnits total number of unit operations in the system
     * @param iterationNumber current iteration number (for recycle loops, starts at 1)
     */
    void onUnitComplete(ProcessEquipmentInterface unit, int unitIndex, int totalUnits,
        int iterationNumber);

    /**
     * Called when an iteration of the process system completes. For systems with recycles, this is
     * called after each complete pass through all units.
     *
     * @param iterationNumber the iteration that just completed (starts at 1)
     * @param converged true if all recycles have converged
     * @param recycleError maximum relative error across all recycle streams (0.0 if no recycles)
     */
    default void onIterationComplete(int iterationNumber, boolean converged, double recycleError) {
      // Default implementation does nothing
    }

    /**
     * Called if a unit operation encounters an error during execution.
     *
     * @param unit the unit operation that failed
     * @param exception the exception that was thrown
     * @return true to continue with next unit, false to abort simulation
     */
    default boolean onUnitError(ProcessEquipmentInterface unit, Exception exception) {
      return false; // Default: abort on error
    }

    /**
     * Called before each unit operation is executed. Useful for state inspection, cache warming, or
     * injecting external data before a unit runs.
     *
     * @param unit the unit about to be executed
     * @param unitIndex zero-based index of the unit in execution order
     * @param totalUnits total number of unit operations
     * @param iterationNumber current iteration number (starts at 1)
     */
    default void onBeforeUnit(ProcessEquipmentInterface unit, int unitIndex, int totalUnits,
        int iterationNumber) {
      // Default implementation does nothing
    }

    /**
     * Called at the start of each iteration before any units are executed. Useful for resetting
     * state, applying external data, or logging iteration starts.
     *
     * @param iterationNumber the iteration about to start (starts at 1)
     */
    default void onBeforeIteration(int iterationNumber) {
      // Default implementation does nothing
    }

    /**
     * Called once when the simulation begins, before the first iteration.
     *
     * @param totalUnits total number of unit operations in the system
     */
    default void onSimulationStart(int totalUnits) {
      // Default implementation does nothing
    }

    /**
     * Called once when the simulation ends, after all iterations complete.
     *
     * @param totalIterations total number of iterations performed
     * @param converged true if the simulation converged
     */
    default void onSimulationComplete(int totalIterations, boolean converged) {
      // Default implementation does nothing
    }
  }

  /**
   * <p>
   * Constructor for ProcessSystem.
   * </p>
   */
  public ProcessSystem() {
    this("Process system");
  }

  /**
   * Constructor for ProcessSystem.
   *
   * @param name name of process
   */
  public ProcessSystem(String name) {
    super(name);
  }

  /**
   * <p>
   * Add to end.
   * </p>
   *
   * @param operation a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public synchronized void add(ProcessEquipmentInterface operation) {
    // Add to end
    add(this.getUnitOperations().size(), operation);
  }

  /**
   * <p>
   * Add to specific position.
   * </p>
   *
   * @param position 0-based position
   * @param operation a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public synchronized void add(int position, ProcessEquipmentInterface operation) {
    List<ProcessEquipmentInterface> units = this.getUnitOperations();

    for (ProcessEquipmentInterface unit : units) {
      if (unit == operation) {
        logger.info("Equipment " + operation.getName() + " is already included in ProcessSystem");
        return;
      }
    }

    if (getAllUnitNames().contains(operation.getName())) {
      ProcessEquipmentInterface existing = this.getUnit(operation.getName());
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException("ProcessSystem",
          "add", "operation", "- Process equipment of type " + existing.getClass().getSimpleName()
              + " named " + operation.getName() + " already included in ProcessSystem"));
    }

    getUnitOperations().add(position, operation);
    graphDirty = true; // Mark graph for rebuild when units change
    cachedParallelPlan = null;
    cachedHasAdjusters = null;
    cachedHasRecycles = null;
    if (operation instanceof ModuleInterface) {
      ((ModuleInterface) operation).initializeModule();
    }
  }

  /**
   * <p>
   * Add measurementdevice.
   * </p>
   *
   * @param measurementDevice a {@link neqsim.process.measurementdevice.MeasurementDeviceInterface}
   *        object
   */
  public synchronized void add(MeasurementDeviceInterface measurementDevice) {
    measurementDevices.add(measurementDevice);
    alarmManager.register(measurementDevice);
  }

  /**
   * Add a standalone controller device to the process system. Controllers added here participate in
   * the explicit controller scan during {@code runTransient}.
   *
   * @param controllerDevice a {@link neqsim.process.controllerdevice.ControllerDeviceInterface}
   *        object
   */
  public synchronized void add(ControllerDeviceInterface controllerDevice) {
    controllerDevices.add(controllerDevice);
  }

  /**
   * Returns an unmodifiable list of all process elements — equipment, measurement devices, and
   * controllers — registered in this system.
   *
   * @return list of all {@link neqsim.process.ProcessElementInterface} objects
   */
  public List<ProcessElementInterface> getAllElements() {
    List<ProcessElementInterface> all = new ArrayList<ProcessElementInterface>(
        unitOperations.size() + measurementDevices.size() + controllerDevices.size());
    all.addAll(unitOperations);
    all.addAll(measurementDevices);
    all.addAll(controllerDevices);
    return all;
  }

  /**
   * Returns the list of measurement devices registered in this process system.
   *
   * @return list of {@link MeasurementDeviceInterface} objects
   */
  public List<MeasurementDeviceInterface> getMeasurementDevices() {
    return Collections.unmodifiableList(measurementDevices);
  }

  /**
   * Returns the list of controller devices registered in this process system.
   *
   * @return list of {@link ControllerDeviceInterface} objects
   */
  public List<ControllerDeviceInterface> getControllerDevices() {
    return Collections.unmodifiableList(controllerDevices);
  }

  /**
   * Declares an explicit connection between two equipment ports. This is a metadata record; it does
   * not create or wire stream objects. Interchange formats like DEXPI and topology analyses can
   * query the connection list via {@link #getConnections()}.
   *
   * @param sourceEquipment name of upstream equipment
   * @param sourcePort port name on source (e.g. "gasOut")
   * @param targetEquipment name of downstream equipment
   * @param targetPort port name on target (e.g. "inlet")
   * @param type connection type
   */
  public void connect(String sourceEquipment, String sourcePort, String targetEquipment,
      String targetPort, ProcessConnection.ConnectionType type) {
    connections
        .add(new ProcessConnection(sourceEquipment, sourcePort, targetEquipment, targetPort, type));
  }

  /**
   * Declares a material connection between two equipment ports with default port names.
   *
   * @param sourceEquipment name of upstream equipment
   * @param targetEquipment name of downstream equipment
   */
  public void connect(String sourceEquipment, String targetEquipment) {
    connections.add(new ProcessConnection(sourceEquipment, targetEquipment));
  }

  /**
   * Returns an unmodifiable view of the declared connections.
   *
   * @return unmodifiable list of {@link ProcessConnection} objects
   */
  public List<ProcessConnection> getConnections() {
    return Collections.unmodifiableList(connections);
  }

  /**
   * <p>
   * Add multiple process equipment to end.
   * </p>
   *
   * @param operations an array of {@link neqsim.process.equipment.ProcessEquipmentInterface}
   *        objects
   */
  public void add(ProcessEquipmentInterface[] operations) {
    getUnitOperations().addAll(Arrays.asList(operations));
  }

  /**
   * <p>
   * Replace a unitoperation.
   * </p>
   *
   * @param name Name of the object to replace
   * @param newObject the object to replace it with
   * @return a {@link java.lang.Boolean} object
   */
  public boolean replaceUnit(String name, ProcessEquipmentInterface newObject) {
    try {
      for (int i = 0; i < unitOperations.size(); i++) {
        if (unitOperations.get(i).getName().equals(name)) {
          unitOperations.set(i, newObject);
          return true;
        }
      }
      logger.error("Unit operation with name '" + name + "' not found for replacement");
      return false;
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }
    return false;
  }

  /**
   * <p>
   * Get process equipmen by name.
   * </p>
   *
   * @param name Name of
   * @return a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public ProcessEquipmentInterface getUnit(String name) {
    for (int i = 0; i < getUnitOperations().size(); i++) {
      if (getUnitOperations().get(i) instanceof ModuleInterface) {
        for (int j = 0; j < ((ModuleInterface) getUnitOperations().get(i)).getOperations()
            .getUnitOperations().size(); j++) {
          if (((ModuleInterface) getUnitOperations().get(i)).getOperations().getUnitOperations()
              .get(j).getName().equals(name)) {
            return ((ModuleInterface) getUnitOperations().get(i)).getOperations()
                .getUnitOperations().get(j);
          }
        }
      } else if (getUnitOperations().get(i).getName().equals(name)) {
        return getUnitOperations().get(i);
      }
    }
    return null;
  }

  /**
   * Looks up a process equipment unit by its IEC 81346 reference designation string (e.g.
   * {@code "=A1.B1"}, {@code "-B1"}).
   *
   * @param refDesignation the reference designation string to match
   * @return the matching equipment, or {@code null} if not found
   */
  public ProcessEquipmentInterface getUnitByReferenceDesignation(String refDesignation) {
    if (refDesignation == null || refDesignation.trim().isEmpty()) {
      return null;
    }
    for (int i = 0; i < getUnitOperations().size(); i++) {
      ProcessEquipmentInterface unit = getUnitOperations().get(i);
      if (unit instanceof ModuleInterface) {
        for (int j = 0; j < ((ModuleInterface) unit).getOperations().getUnitOperations()
            .size(); j++) {
          ProcessEquipmentInterface inner =
              ((ModuleInterface) unit).getOperations().getUnitOperations().get(j);
          if (refDesignation.equals(inner.getReferenceDesignationString())) {
            return inner;
          }
        }
      } else if (refDesignation.equals(unit.getReferenceDesignationString())) {
        return unit;
      }
    }
    return null;
  }

  /**
   * Generates IEC 81346 reference designations for all equipment in this process system. This is a
   * convenience wrapper around
   * {@link neqsim.process.equipment.iec81346.ReferenceDesignationGenerator}.
   *
   * @param functionPrefix the function-aspect prefix (e.g. "A1" for the first process area)
   * @param locationPrefix the location-aspect prefix (e.g. "G1" for a specific platform)
   * @return the generator instance (for further queries such as {@code toJson()})
   */
  public neqsim.process.equipment.iec81346.ReferenceDesignationGenerator generateReferenceDesignations(
      String functionPrefix, String locationPrefix) {
    neqsim.process.equipment.iec81346.ReferenceDesignationGenerator gen =
        new neqsim.process.equipment.iec81346.ReferenceDesignationGenerator();
    gen.setFunctionPrefix(functionPrefix);
    gen.setLocationPrefix(locationPrefix);
    gen.generate(this);
    return gen;
  }

  /**
   * <p>
   * hasUnitName.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @return a boolean
   */
  public boolean hasUnitName(String name) {
    if (getUnit(name) == null) {
      return false;
    } else {
      return true;
    }
  }

  /**
   * <p>
   * Get MeasureDevice by name.
   * </p>
   *
   * @param name Name of measurement device
   * @return a {@link neqsim.process.measurementdevice.MeasurementDeviceInterface} object
   */
  public MeasurementDeviceInterface getMeasurementDevice(String name) {
    for (int i = 0; i < measurementDevices.size(); i++) {
      if (measurementDevices.get(i).getName().equals(name)) {
        return measurementDevices.get(i);
      }
    }
    return null;
  }

  /**
   * Look up a measurement device by its instrument tag. Tags are assigned via
   * {@link MeasurementDeviceInterface#setTag(String)} and typically correspond to plant historian
   * signal identifiers (e.g. "PT-101", "TT-201").
   *
   * @param tag the instrument tag to search for
   * @return the matching device, or {@code null} if no device carries the given tag
   */
  public MeasurementDeviceInterface getMeasurementDeviceByTag(String tag) {
    for (int i = 0; i < measurementDevices.size(); i++) {
      if (tag.equals(measurementDevices.get(i).getTag())) {
        return measurementDevices.get(i);
      }
    }
    return null;
  }

  /**
   * Returns all measurement devices that have the specified
   * {@link neqsim.process.measurementdevice.InstrumentTagRole}.
   *
   * @param role the tag role to filter on
   * @return unmodifiable list of matching devices (may be empty)
   */
  public List<MeasurementDeviceInterface> getMeasurementDevicesByRole(
      neqsim.process.measurementdevice.InstrumentTagRole role) {
    List<MeasurementDeviceInterface> result = new ArrayList<MeasurementDeviceInterface>();
    for (int i = 0; i < measurementDevices.size(); i++) {
      if (measurementDevices.get(i).getTagRole() == role) {
        result.add(measurementDevices.get(i));
      }
    }
    return Collections.unmodifiableList(result);
  }

  /**
   * Sets field data values on measurement devices identified by their instrument tags. Devices with
   * role {@link neqsim.process.measurementdevice.InstrumentTagRole#INPUT INPUT} will push the
   * values into the model via {@link MeasurementDeviceInterface#applyFieldValue()}.
   *
   * @param fieldData map of instrument tag to field value
   */
  public void setFieldData(Map<String, Double> fieldData) {
    for (Map.Entry<String, Double> entry : fieldData.entrySet()) {
      MeasurementDeviceInterface device = getMeasurementDeviceByTag(entry.getKey());
      if (device != null) {
        device.setFieldValue(entry.getValue());
      }
    }
  }

  /**
   * Applies field values from all {@link neqsim.process.measurementdevice.InstrumentTagRole#INPUT
   * INPUT} instruments to their connected streams or equipment. Call this before running the
   * process to push field boundary conditions into the model.
   */
  public void applyFieldInputs() {
    for (int i = 0; i < measurementDevices.size(); i++) {
      MeasurementDeviceInterface device = measurementDevices.get(i);
      if (device.getTagRole() == neqsim.process.measurementdevice.InstrumentTagRole.INPUT
          && device.hasFieldValue()) {
        device.applyFieldValue();
      }
    }
  }

  /**
   * Returns a map of instrument tag to deviation (model minus field) for all
   * {@link neqsim.process.measurementdevice.InstrumentTagRole#BENCHMARK BENCHMARK} instruments that
   * have field data. Useful for model validation and parameter optimisation.
   *
   * @return map of tag to deviation value
   */
  public Map<String, Double> getBenchmarkDeviations() {
    Map<String, Double> deviations = new HashMap<String, Double>();
    for (int i = 0; i < measurementDevices.size(); i++) {
      MeasurementDeviceInterface device = measurementDevices.get(i);
      if (device.getTagRole() == neqsim.process.measurementdevice.InstrumentTagRole.BENCHMARK
          && device.hasFieldValue()) {
        deviations.put(device.getTag(), device.getDeviation());
      }
    }
    return deviations;
  }

  /**
   * <p>
   * getUnitNumber.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @return a int
   */
  public int getUnitNumber(String name) {
    for (int i = 0; i < getUnitOperations().size(); i++) {
      if (getUnitOperations().get(i) instanceof ModuleInterface) {
        for (int j = 0; j < ((ModuleInterface) getUnitOperations().get(i)).getOperations()
            .getUnitOperations().size(); j++) {
          if (((ModuleInterface) getUnitOperations().get(i)).getOperations().getUnitOperations()
              .get(j).getName().equals(name)) {
            return j;
          }
        }
      } else if (getUnitOperations().get(i).getName().equals(name)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * <p>
   * replaceObject.
   * </p>
   *
   * @param unitName a {@link java.lang.String} object
   * @param operation a {@link neqsim.process.equipment.ProcessEquipmentBaseClass} object
   */
  public synchronized void replaceObject(String unitName, ProcessEquipmentBaseClass operation) {
    Objects.requireNonNull(unitName, "unitName");
    Objects.requireNonNull(operation, "operation");

    int index = getUnitNumber(unitName);
    if (index < 0 || index >= unitOperations.size() || getUnit(unitName) == null) {
      throw new IllegalArgumentException(
          "No process equipment named '" + unitName + "' exists in this ProcessSystem");
    }

    operation.setName(unitName);
    unitOperations.set(index, operation);
  }

  /**
   * <p>
   * getAllUnitNames.
   * </p>
   *
   * @return a {@link java.util.ArrayList} object
   */
  public ArrayList<String> getAllUnitNames() {
    ArrayList<String> unitNames = new ArrayList<String>();
    for (int i = 0; i < getUnitOperations().size(); i++) {
      if (getUnitOperations().get(i) instanceof ModuleInterface) {
        for (int j = 0; j < ((ModuleInterface) getUnitOperations().get(i)).getOperations()
            .getUnitOperations().size(); j++) {
          unitNames.add(((ModuleInterface) getUnitOperations().get(i)).getOperations()
              .getUnitOperations().get(j).getName());
        }
      }
      unitNames.add(unitOperations.get(i).getName());
    }
    return unitNames;
  }

  /**
   * <p>
   * Gets the list of unit operations.
   * </p>
   *
   * @return the list of unit operations
   */
  public List<ProcessEquipmentInterface> getUnitOperations() {
    return unitOperations;
  }

  /**
   * Validates the process system setup before execution.
   *
   * <p>
   * This method validates:
   * <ul>
   * <li>The process system has at least one unit operation</li>
   * <li>Each unit operation passes its own validateSetup() check</li>
   * <li>Feed streams are defined</li>
   * <li>Equipment names are unique (warning)</li>
   * </ul>
   *
   * @return validation result with errors and warnings for all equipment
   */
  public neqsim.util.validation.ValidationResult validateSetup() {
    neqsim.util.validation.ValidationResult result =
        new neqsim.util.validation.ValidationResult(getName());

    // Check: Has unit operations
    if (unitOperations.isEmpty()) {
      result.addError("process", "No unit operations in process system",
          "Add equipment: processSystem.add(stream); processSystem.add(separator);");
      return result;
    }

    // Validate each unit operation
    int feedStreamCount = 0;
    java.util.Set<String> names = new java.util.HashSet<>();
    for (int i = 0; i < unitOperations.size(); i++) {
      ProcessEquipmentInterface equipment = unitOperations.get(i);

      if (equipment == null) {
        result.addError("unit[" + i + "]", "Null equipment at index " + i,
            "Ensure all equipment is properly initialized before adding");
        continue;
      }

      // Check for duplicate names
      String name = equipment.getName();
      if (name != null && !name.isEmpty()) {
        if (names.contains(name)) {
          result.addWarning("unit." + name, "Duplicate equipment name: " + name,
              "Use unique names for each equipment for easier debugging");
        }
        names.add(name);
      }

      // Count feed streams
      if (equipment.getClass().getSimpleName().contains("Stream")) {
        feedStreamCount++;
      }

      // Validate individual equipment
      neqsim.util.validation.ValidationResult equipResult = equipment.validateSetup();
      for (neqsim.util.validation.ValidationResult.ValidationIssue issue : equipResult
          .getIssues()) {
        String equipName = (name != null && !name.isEmpty()) ? name : "unit[" + i + "]";
        if (issue.getSeverity() == neqsim.util.validation.ValidationResult.Severity.CRITICAL) {
          result.addError(equipName + "." + issue.getCategory(), issue.getMessage(),
              issue.getRemediation());
        } else if (issue.getSeverity() == neqsim.util.validation.ValidationResult.Severity.MAJOR) {
          result.addWarning(equipName + "." + issue.getCategory(), issue.getMessage(),
              issue.getRemediation());
        }
      }
    }

    // Check: Has at least one feed stream
    if (feedStreamCount == 0) {
      result.addWarning("feeds", "No obvious feed streams detected",
          "Ensure process has input streams with defined compositions");
    }

    return result;
  }

  /**
   * Validates all equipment in the process system and returns individual results.
   *
   * <p>
   * Unlike {@link #validateSetup()} which returns a combined result, this method returns a map of
   * equipment names to their individual validation results, making it easier to identify specific
   * issues.
   * </p>
   *
   * @return map of equipment names to their validation results
   */
  public java.util.Map<String, neqsim.util.validation.ValidationResult> validateAll() {
    java.util.Map<String, neqsim.util.validation.ValidationResult> results =
        new java.util.LinkedHashMap<>();

    for (int i = 0; i < unitOperations.size(); i++) {
      ProcessEquipmentInterface equipment = unitOperations.get(i);
      if (equipment != null) {
        String name = equipment.getName();
        if (name == null || name.isEmpty()) {
          name = "unit[" + i + "]";
        }
        results.put(name, equipment.validateSetup());
      }
    }

    return results;
  }

  /**
   * Checks if the process system is ready to run.
   *
   * <p>
   * Convenience method that returns true if validateSetup() finds no critical errors.
   * </p>
   *
   * @return true if process system passes validation, false otherwise
   */
  public boolean isReadyToRun() {
    return validateSetup().isValid();
  }

  /**
   * Validates the process setup and returns a structured SimulationResult.
   *
   * <p>
   * Converts ValidationResult issues into SimulationResult.ErrorDetail objects for web API
   * consumption. Returns a success result if no critical errors are found.
   * </p>
   *
   * @return a SimulationResult with validation errors or success status
   */
  public SimulationResult validateAndReport() {
    neqsim.util.validation.ValidationResult valResult = validateSetup();
    List<SimulationResult.ErrorDetail> errorDetails = new ArrayList<>();
    List<String> warningMessages = new ArrayList<>();

    for (neqsim.util.validation.ValidationResult.ValidationIssue issue : valResult.getIssues()) {
      if (issue.getSeverity() == neqsim.util.validation.ValidationResult.Severity.CRITICAL) {
        errorDetails.add(new SimulationResult.ErrorDetail(
            "VALIDATION_" + issue.getCategory().toUpperCase().replace('.', '_'), issue.getMessage(),
            issue.getCategory().contains(".") ? issue.getCategory().split("\\.")[0] : null,
            issue.getRemediation()));
      } else {
        warningMessages.add(issue.toString());
      }
    }

    if (!errorDetails.isEmpty()) {
      return SimulationResult.failure(this, errorDetails, warningMessages);
    }
    return SimulationResult.success(this, null, warningMessages);
  }

  /**
   * Runs the process system and returns a structured SimulationResult.
   *
   * <p>
   * Validates the setup first, then runs the simulation. Returns a structured result containing the
   * full JSON report on success, or detailed errors on failure. Designed for web API integration.
   * </p>
   *
   * @return a SimulationResult with the simulation report or errors
   */
  public SimulationResult runAndReport() {
    // Validate first
    SimulationResult validation = validateAndReport();
    if (validation.isError()) {
      return validation;
    }

    List<String> warningMessages = new ArrayList<>(validation.getWarnings());

    try {
      run();
      String report = getReport_json();
      return SimulationResult.success(this, report, warningMessages);
    } catch (Exception e) {
      List<SimulationResult.ErrorDetail> errorDetails = new ArrayList<>();
      errorDetails.add(new SimulationResult.ErrorDetail("SIMULATION_ERROR",
          "Simulation failed: " + e.getMessage(), null,
          "Check equipment configuration, fluid definitions, and stream connections"));
      return SimulationResult.failure(this, errorDetails, warningMessages);
    }
  }

  /**
   * <p>
   * removeUnit.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public synchronized void removeUnit(String name) {
    for (int i = 0; i < unitOperations.size(); i++) {
      if (unitOperations.get(i).getName().equals(name)) {
        unitOperations.remove(i);
        graphDirty = true; // Invalidate graph when structure changes
        cachedParallelPlan = null;
        cachedHasAdjusters = null;
        cachedHasRecycles = null;
      }
    }
  }

  /**
   * <p>
   * clearAll.
   * </p>
   */
  public synchronized void clearAll() {
    unitOperations.clear();
    graphDirty = true; // Invalidate graph when structure changes
    cachedParallelPlan = null;
    cachedHasAdjusters = null;
    cachedHasRecycles = null;
  }

  /**
   * <p>
   * clear.
   * </p>
   */
  public synchronized void clear() {
    unitOperations = new ArrayList<ProcessEquipmentInterface>(0);
    graphDirty = true; // Invalidate graph when structure changes
    cachedParallelPlan = null;
    cachedHasAdjusters = null;
    cachedHasRecycles = null;
  }

  /**
   * <p>
   * setFluid.
   * </p>
   *
   * @param fluid1 a {@link neqsim.thermo.system.SystemInterface} object
   * @param fluid2 a {@link neqsim.thermo.system.SystemInterface} object
   * @param addNewComponents a boolean
   */
  public void setFluid(SystemInterface fluid1, SystemInterface fluid2, boolean addNewComponents) {
    fluid1.setEmptyFluid();
    boolean addedComps = false;
    for (int i = 0; i < fluid2.getNumberOfComponents(); i++) {
      if (fluid1.getPhase(0).hasComponent(fluid2.getComponent(i).getName())) {
        fluid1.addComponent(fluid2.getComponent(i).getName(),
            fluid2.getComponent(i).getNumberOfmoles());
      } else {
        if (addNewComponents) {
          addedComps = true;
          if (fluid2.getComponent(i).isIsTBPfraction()
              || fluid2.getComponent(i).isIsPlusFraction()) {
            fluid1.addTBPfraction(fluid2.getComponent(i).getName(),
                fluid2.getComponent(i).getNumberOfmoles(), fluid2.getComponent(i).getMolarMass(),
                fluid2.getComponent(i).getNormalLiquidDensity());
          } else {
            fluid1.addComponent(fluid2.getComponent(i).getName(),
                fluid2.getComponent(i).getNumberOfmoles());
          }
        }
      }
    }
    if (addedComps) {
      fluid1.createDatabase(true);
    }
    fluid1.init(0);
    fluid1.setTemperature(fluid2.getTemperature());
    fluid1.setPressure(fluid2.getPressure());
  }

  /**
   * <p>
   * setFluid.
   * </p>
   *
   * @param fluid1 a {@link neqsim.thermo.system.SystemInterface} object
   * @param fluid2 a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setFluid(SystemInterface fluid1, SystemInterface fluid2) {
    fluid1.setEmptyFluid();
    boolean addedComps = false;
    for (int i = 0; i < fluid2.getNumberOfComponents(); i++) {
      if (fluid1.getPhase(0).hasComponent(fluid2.getComponent(i).getName())) {
        fluid1.addComponent(fluid2.getComponent(i).getName(),
            fluid2.getComponent(i).getNumberOfmoles());
      } else {
        addedComps = true;
        if (fluid2.getComponent(i).isIsTBPfraction() || fluid2.getComponent(i).isIsPlusFraction()) {
          fluid1.addTBPfraction(fluid2.getComponent(i).getName(),
              fluid2.getComponent(i).getNumberOfmoles(), fluid2.getComponent(i).getMolarMass(),
              fluid2.getComponent(i).getNormalLiquidDensity());
        } else {
          fluid1.addComponent(fluid2.getComponent(i).getName(),
              fluid2.getComponent(i).getNumberOfmoles());
        }
      }
    }
    if (addedComps) {
      fluid1.createDatabase(true);
    }
    fluid1.init(0);
    fluid1.setTemperature(fluid2.getTemperature());
    fluid1.setPressure(fluid2.getPressure());
  }

  /**
   * Runs this process in a separate thread using the global NeqSim thread pool.
   *
   * <p>
   * This method submits the process to the shared {@link neqsim.util.NeqSimThreadPool} and returns
   * a {@link java.util.concurrent.Future} that can be used to monitor completion, cancel the task,
   * or retrieve any exceptions that occurred.
   * </p>
   *
   * @return a {@link java.util.concurrent.Future} representing the pending completion of the task
   * @see neqsim.util.NeqSimThreadPool
   */
  public java.util.concurrent.Future<?> runAsTask() {
    return neqsim.util.NeqSimThreadPool.submit(this);
  }

  /**
   * Runs the process system using the optimal execution strategy based on topology analysis.
   *
   * <p>
   * This method automatically selects the best execution mode:
   * </p>
   * <ul>
   * <li>For processes WITHOUT recycles: uses parallel execution for maximum speed</li>
   * <li>For processes WITH recycles: uses graph-based execution with optimized ordering</li>
   * </ul>
   *
   * <p>
   * This is the recommended method for most use cases as it provides the best performance without
   * requiring manual configuration.
   * </p>
   */
  public void runOptimized() {
    runOptimized(UUID.randomUUID());
  }

  /**
   * Runs the process system using the optimal execution strategy based on topology analysis.
   *
   * <p>
   * This method automatically selects the best execution mode:
   * </p>
   * <ul>
   * <li>For processes with adjusters: sequential execution (adjusters modify upstream variables and
   * read downstream targets, creating implicit feedback loops)</li>
   * <li>For processes with recycles (no adjusters): sequential execution for full convergence</li>
   * <li>For processes with multi-input equipment (Mixer, Manifold, HeatExchanger, etc.): sequential
   * execution to ensure correct mass balance</li>
   * <li>For simple feed-forward processes: parallel execution for maximum speed</li>
   * </ul>
   *
   * @param id calculation identifier for tracking
   */
  public void runOptimized(UUID id) {
    if (hasAdjusters()) {
      // Adjusters create implicit feedback loops (they modify upstream variables
      // and read downstream targets), requiring all units to re-run each iteration.
      // Use sequential execution for correct convergence.
      runSequential(id);
    } else if (hasRecycles()) {
      // Process has Recycle units - use sequential execution for full convergence.
      // This ensures all units are re-evaluated in each iteration using insertion
      // order, which may be carefully chosen for convergence in complex processes
      // (e.g. TEG dehydration with regen column and makeup).
      runSequential(id);
    } else if (hasMultiInputEquipment()) {
      // Process has multi-input equipment (Mixer, Manifold, HeatExchanger, etc.)
      // These require sequential execution to ensure correct mass balance.
      // Parallel execution can change the order in which input streams are processed.
      runSequential(id);
    } else {
      // Feed-forward process with single-input equipment only - use parallel execution.
      // Units at the same level (no dependencies) run concurrently for maximum speed.
      try {
        runParallel(id);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.warn("Parallel execution interrupted, falling back to sequential");
        runSequential(id);
      }
    }
  }

  /**
   * Checks if the process contains any Adjuster units that require iterative convergence.
   *
   * @return true if there are Adjuster units in the process
   */
  public boolean hasAdjusters() {
    if (cachedHasAdjusters != null) {
      return cachedHasAdjusters;
    }
    for (ProcessEquipmentInterface unit : unitOperations) {
      if (unit instanceof Adjuster) {
        cachedHasAdjusters = true;
        return true;
      }
    }
    cachedHasAdjusters = false;
    return false;
  }

  /**
   * Checks if the process contains any Recycle units.
   *
   * <p>
   * This method directly checks for Recycle units in the process, which is more reliable than
   * graph-based cycle detection for determining if iterative execution is needed.
   * </p>
   *
   * @return true if there are Recycle units in the process
   */
  public boolean hasRecycles() {
    if (cachedHasRecycles != null) {
      return cachedHasRecycles;
    }
    for (ProcessEquipmentInterface unit : unitOperations) {
      if (unit instanceof Recycle) {
        cachedHasRecycles = true;
        return true;
      }
    }
    cachedHasRecycles = false;
    return false;
  }

  /**
   * Checks if the process contains any multi-input equipment.
   *
   * <p>
   * Multi-input equipment (Mixer, Manifold, TurboExpanderCompressor, Ejector, HeatExchanger,
   * MultiStreamHeatExchanger) require sequential execution to ensure correct mass balance. Parallel
   * execution can change the order in which input streams are processed, leading to incorrect
   * results.
   * </p>
   *
   * @return true if there are multi-input equipment units in the process
   */
  public boolean hasMultiInputEquipment() {
    for (ProcessEquipmentInterface unit : unitOperations) {
      if (unit instanceof MixerInterface || unit instanceof Manifold
          || unit instanceof TurboExpanderCompressor || unit instanceof Ejector
          || unit instanceof HeatExchanger || unit instanceof MultiStreamHeatExchangerInterface
          || unit instanceof FurnaceBurner || unit instanceof FlareStack) {
        return true;
      }
      // Check if Separator has multiple input streams (uses internal mixer)
      if (unit instanceof neqsim.process.equipment.separator.Separator) {
        neqsim.process.equipment.separator.Separator sep =
            (neqsim.process.equipment.separator.Separator) unit;
        if (sep.numberOfInputStreams > 1) {
          return true;
        }
      }
      // Check if Tank has multiple input streams (uses internal mixer like Separator)
      if (unit instanceof neqsim.process.equipment.tank.Tank) {
        try {
          java.lang.reflect.Field field =
              neqsim.process.equipment.tank.Tank.class.getDeclaredField("numberOfInputStreams");
          field.setAccessible(true);
          int numInputs = field.getInt(unit);
          if (numInputs > 1) {
            return true;
          }
        } catch (Exception e) {
          // Ignore reflection errors
        }
      }
    }
    return false;
  }

  /**
   * Runs the process using hybrid execution strategy.
   *
   * <p>
   * This method partitions the process into:
   * </p>
   * <ul>
   * <li>Feed-forward section: Units at the beginning with no recycle dependencies - run in
   * parallel</li>
   * <li>Recycle section: Units that are part of or depend on recycle loops - run with graph-based
   * iteration</li>
   * </ul>
   *
   * @param id calculation identifier for tracking
   * @throws InterruptedException if thread is interrupted during parallel execution
   */
  public synchronized void runHybrid(UUID id) throws InterruptedException {
    ProcessGraph graph = buildGraph();
    ProcessGraph.ParallelPartition partition = graph.partitionForParallelExecution();
    java.util.Set<ProcessNode> recycleNodes = graph.getNodesInRecycleLoops();

    // Build set of units in recycle loops for fast lookup
    java.util.Set<ProcessEquipmentInterface> recycleUnits = new java.util.HashSet<>();
    for (ProcessNode node : recycleNodes) {
      recycleUnits.add(node.getEquipment());
    }

    // Run setters first (sequential, they set conditions)
    for (ProcessEquipmentInterface unit : unitOperations) {
      if (unit instanceof Setter) {
        unit.run(id);
      }
    }

    // Phase 1: Run feed-forward levels in parallel (before any recycle units or
    // adjusters)
    int firstRecycleLevel = -1;
    int firstAdjusterLevel = -1;
    List<List<ProcessNode>> levels = partition.getLevels();

    for (int levelIdx = 0; levelIdx < levels.size(); levelIdx++) {
      List<ProcessNode> level = levels.get(levelIdx);
      boolean hasRecycleUnit = false;
      boolean hasAdjusterUnit = false;
      for (ProcessNode node : level) {
        if (recycleNodes.contains(node)) {
          hasRecycleUnit = true;
        }
        if (node.getEquipment() instanceof Adjuster) {
          hasAdjusterUnit = true;
        }
      }
      if (hasRecycleUnit && firstRecycleLevel < 0) {
        firstRecycleLevel = levelIdx;
      }
      if (hasAdjusterUnit && firstAdjusterLevel < 0) {
        firstAdjusterLevel = levelIdx;
      }
      // Stop at first level with either recycle or adjuster
      if (hasRecycleUnit || hasAdjusterUnit) {
        break;
      }

      // This level is feed-forward - run in parallel
      // Group nodes that share input streams to run them sequentially
      List<List<ProcessNode>> groups = groupNodesBySharedInputStreams(level);

      if (groups.size() == 1) {
        // Single group - run sequentially to avoid race conditions
        for (ProcessNode node : groups.get(0)) {
          ProcessEquipmentInterface unit = node.getEquipment();
          if (!(unit instanceof Setter)) {
            try {
              unit.run(id);
            } catch (Exception ex) {
              logger.error("equipment: " + unit.getName() + " error: " + ex.getMessage(), ex);
            }
          }
        }
      } else {
        // Multiple independent groups - run groups in parallel
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (List<ProcessNode> group : groups) {
          final List<ProcessNode> groupToRun = group;
          final UUID calcId = id;
          futures.add(neqsim.util.NeqSimThreadPool.submit(() -> {
            for (ProcessNode node : groupToRun) {
              ProcessEquipmentInterface unit = node.getEquipment();
              if (!(unit instanceof Setter)) {
                try {
                  unit.run(calcId);
                } catch (Exception ex) {
                  logger.error("equipment: " + unit.getName() + " error: " + ex.getMessage(), ex);
                }
              }
            }
          }));
        }
        for (java.util.concurrent.Future<?> future : futures) {
          try {
            future.get();
          } catch (java.util.concurrent.ExecutionException ex) {
            logger.error("Parallel execution error: " + ex.getMessage(), ex);
          }
        }
      }
    }

    // Phase 2: Run recycle/adjuster section with graph-based iteration
    // Take the minimum of both (earlier level starts iteration)
    int firstIterativeLevel = -1;
    if (firstRecycleLevel >= 0 && firstAdjusterLevel >= 0) {
      firstIterativeLevel = Math.min(firstRecycleLevel, firstAdjusterLevel);
    } else if (firstRecycleLevel >= 0) {
      firstIterativeLevel = firstRecycleLevel;
    } else if (firstAdjusterLevel >= 0) {
      firstIterativeLevel = firstAdjusterLevel;
    }
    if (firstIterativeLevel >= 0) {
      // Build list of remaining units in topological order
      List<ProcessEquipmentInterface> iterativeSection = new ArrayList<>();
      for (int levelIdx = firstIterativeLevel; levelIdx < levels.size(); levelIdx++) {
        for (ProcessNode node : levels.get(levelIdx)) {
          iterativeSection.add(node.getEquipment());
        }
      }

      // Initialize recycle controller for these units
      recycleController.clear();
      for (ProcessEquipmentInterface unit : iterativeSection) {
        if (unit instanceof Recycle) {
          recycleController.addRecycle((Recycle) unit);
        }
      }
      recycleController.init();

      // Iterate until convergence
      boolean isConverged = true;
      int iter = 0;
      do {
        iter++;
        isConverged = true;

        for (ProcessEquipmentInterface unit : iterativeSection) {
          if (Thread.currentThread().isInterrupted()) {
            logger.debug("Process simulation was interrupted, exiting runHybrid()..." + getName());
            break;
          }
          if (!(unit instanceof Recycle)) {
            try {
              if (iter == 1 || unit.needRecalculation()) {
                unit.run(id);
              }
            } catch (Exception ex) {
              logger.error("error running unit operation " + unit.getName() + " " + ex.getMessage(),
                  ex);
            }
          }
          if (unit instanceof Recycle && recycleController.doSolveRecycle((Recycle) unit)) {
            try {
              unit.run(id);
            } catch (Exception ex) {
              logger.error(ex.getMessage(), ex);
            }
          }
        }

        if (!recycleController.solvedAll() || recycleController.hasHigherPriorityLevel()) {
          isConverged = false;
        }
        if (recycleController.solvedCurrentPriorityLevel()) {
          recycleController.nextPriorityLevel();
        } else if (recycleController.hasLoverPriorityLevel() && !recycleController.solvedAll()) {
          recycleController.resetPriorityLevel();
        }

        for (ProcessEquipmentInterface unit : iterativeSection) {
          if (unit instanceof Adjuster) {
            if (!((Adjuster) unit).solved()) {
              isConverged = false;
              break;
            }
          }
        }
      } while ((!isConverged || (iter < 2)) && iter < 100
          && !Thread.currentThread().isInterrupted());
    }

    // Update calculation identifiers
    for (ProcessEquipmentInterface unit : unitOperations) {
      unit.setCalculationIdentifier(id);
    }
    setCalculationIdentifier(id);
  }

  /**
   * Gets a description of how the process will be partitioned for execution.
   *
   * <p>
   * This method analyzes the process topology and returns information about:
   * </p>
   * <ul>
   * <li>Whether the process has recycle loops</li>
   * <li>Number of parallel execution levels</li>
   * <li>Maximum parallelism achievable</li>
   * <li>Which units are in recycle loops</li>
   * </ul>
   *
   * @return description of the execution partitioning
   */
  public String getExecutionPartitionInfo() {
    ProcessGraph graph = buildGraph();
    StringBuilder sb = new StringBuilder();
    sb.append("=== Execution Partition Analysis ===\n");
    sb.append("Total units: ").append(unitOperations.size()).append("\n");
    sb.append("Has recycle loops: ").append(hasRecycleLoops()).append("\n");

    ProcessGraph.ParallelPartition partition = graph.partitionForParallelExecution();
    sb.append("Parallel levels: ").append(partition.getLevelCount()).append("\n");
    sb.append("Max parallelism: ").append(partition.getMaxParallelism()).append("\n");

    // Show units in recycle loops
    java.util.Set<ProcessNode> recycleNodes = graph.getNodesInRecycleLoops();
    if (!recycleNodes.isEmpty()) {
      sb.append("Units in recycle loops: ").append(recycleNodes.size()).append("\n");
      for (ProcessNode node : recycleNodes) {
        sb.append("  - ").append(node.getName()).append("\n");
      }
    }

    // Calculate hybrid partition info
    List<List<ProcessNode>> levels = partition.getLevels();
    int feedForwardLevels = 0;
    int feedForwardUnits = 0;
    int recycleSectionLevels = 0;
    int recycleSectionUnits = 0;
    boolean inRecycleSection = false;

    for (List<ProcessNode> level : levels) {
      boolean hasRecycleUnit = false;
      for (ProcessNode node : level) {
        if (recycleNodes.contains(node)) {
          hasRecycleUnit = true;
          break;
        }
      }
      if (hasRecycleUnit) {
        inRecycleSection = true;
      }
      if (inRecycleSection) {
        recycleSectionLevels++;
        recycleSectionUnits += level.size();
      } else {
        feedForwardLevels++;
        feedForwardUnits += level.size();
      }
    }

    sb.append("\n=== Hybrid Execution Strategy ===\n");
    sb.append("Phase 1 (Parallel): ").append(feedForwardLevels).append(" levels, ")
        .append(feedForwardUnits).append(" units\n");
    sb.append("Phase 2 (Iterative): ").append(recycleSectionLevels).append(" levels, ")
        .append(recycleSectionUnits).append(" units\n");

    // Show execution levels
    sb.append("\nExecution levels:\n");
    int levelNum = 0;
    inRecycleSection = false;
    for (java.util.List<ProcessNode> level : partition.getLevels()) {
      boolean hasRecycleUnit = false;
      for (ProcessNode node : level) {
        if (recycleNodes.contains(node)) {
          hasRecycleUnit = true;
          break;
        }
      }
      if (hasRecycleUnit && !inRecycleSection) {
        inRecycleSection = true;
        sb.append("  --- Recycle Section Start (iterative) ---\n");
      }

      sb.append("  Level ").append(levelNum++);
      if (!inRecycleSection) {
        sb.append(" [PARALLEL]");
      }
      sb.append(": ");
      for (int i = 0; i < level.size(); i++) {
        if (i > 0) {
          sb.append(", ");
        }
        ProcessNode node = level.get(i);
        sb.append(node.getName());
        if (recycleNodes.contains(node)) {
          sb.append(" [RECYCLE]");
        }
      }
      sb.append("\n");
    }

    return sb.toString();
  }

  /**
   * Runs the process system using parallel execution for independent equipment.
   *
   * <p>
   * This method uses the process graph to identify equipment that can run in parallel (i.e.,
   * equipment with no dependencies between them). Equipment at the same "level" in the dependency
   * graph are executed concurrently using the NeqSim thread pool.
   * </p>
   *
   * <p>
   * Note: This method does not handle recycles or adjusters - use regular {@link #run()} for
   * processes with recycle loops. This is suitable for feed-forward processes where maximum
   * parallelism is desired.
   * </p>
   *
   * @throws InterruptedException if the thread is interrupted while waiting for tasks
   */
  public void runParallel() throws InterruptedException {
    runParallel(UUID.randomUUID());
  }

  /**
   * Runs the process system using parallel execution for independent equipment.
   *
   * <p>
   * This method uses the process graph to identify equipment that can run in parallel (i.e.,
   * equipment with no dependencies between them). Equipment at the same "level" in the dependency
   * graph are executed concurrently using the NeqSim thread pool.
   * </p>
   *
   * @param id calculation identifier for tracking
   * @throws InterruptedException if the thread is interrupted while waiting for tasks
   */
  public synchronized void runParallel(UUID id) throws InterruptedException {
    // Publish simulation start event
    publishEvent(new ProcessEvent(ProcessEvent.generateId(), ProcessEvent.EventType.INFO, getName(),
        "Parallel simulation started with " + unitOperations.size() + " units",
        ProcessEvent.Severity.INFO));

    // Auto-validate equipment setup before first run
    if (autoValidate) {
      runAutoValidation(unitOperations);
    }

    // Build and cache the parallel execution plan (grouped nodes per level)
    if (cachedParallelPlan == null) {
      ProcessGraph graph = buildGraph();
      ProcessGraph.ParallelPartition partition = graph.partitionForParallelExecution();
      List<List<List<ProcessNode>>> plan = new ArrayList<>();
      for (List<ProcessNode> level : partition.getLevels()) {
        if (level.size() <= 1) {
          // Single node level - wrap as single group
          List<List<ProcessNode>> singleGroup = new ArrayList<>();
          singleGroup.add(level);
          plan.add(singleGroup);
        } else {
          plan.add(groupNodesBySharedInputStreams(level));
        }
      }
      cachedParallelPlan = plan;
    }

    // Run setters first (sequential, they set conditions)
    for (ProcessEquipmentInterface unit : unitOperations) {
      if (unit instanceof Setter) {
        unit.run(id);
      }
    }

    // Execute each level using the cached plan
    for (List<List<ProcessNode>> levelGroups : cachedParallelPlan) {
      if (levelGroups.size() == 1 && levelGroups.get(0).size() == 1) {
        // Single unit at this level - run directly (no thread pool overhead)
        ProcessEquipmentInterface unit = levelGroups.get(0).get(0).getEquipment();
        if (!(unit instanceof Setter)) {
          try {
            unit.run(id);
          } catch (Exception ex) {
            logger.error("equipment: " + unit.getName() + " error: " + ex.getMessage(), ex);
          }
        }
      } else if (levelGroups.size() == 1) {
        // Single group with multiple units sharing input streams - run sequentially
        for (ProcessNode node : levelGroups.get(0)) {
          ProcessEquipmentInterface unit = node.getEquipment();
          if (!(unit instanceof Setter)) {
            try {
              unit.run(id);
            } catch (Exception ex) {
              logger.error("equipment: " + unit.getName() + " error: " + ex.getMessage(), ex);
            }
          }
        }
      } else {
        // Multiple independent groups - run in parallel
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (List<ProcessNode> group : levelGroups) {
          if (group.size() == 1) {
            final ProcessEquipmentInterface unitToRun = group.get(0).getEquipment();
            if (!(unitToRun instanceof Setter)) {
              final UUID calcId = id;
              futures.add(neqsim.util.NeqSimThreadPool.submit(() -> {
                try {
                  unitToRun.run(calcId);
                } catch (Exception ex) {
                  logger.error("equipment: " + unitToRun.getName() + " error: " + ex.getMessage(),
                      ex);
                }
              }));
            }
          } else {
            final List<ProcessNode> groupToRun = group;
            final UUID calcId = id;
            futures.add(neqsim.util.NeqSimThreadPool.submit(() -> {
              for (ProcessNode node : groupToRun) {
                ProcessEquipmentInterface unit = node.getEquipment();
                if (!(unit instanceof Setter)) {
                  try {
                    unit.run(calcId);
                  } catch (Exception ex) {
                    logger.error("equipment: " + unit.getName() + " error: " + ex.getMessage(), ex);
                  }
                }
              }
            }));
          }
        }
        // Wait for all groups at this level to complete before moving to next level
        for (java.util.concurrent.Future<?> future : futures) {
          try {
            future.get();
          } catch (java.util.concurrent.ExecutionException ex) {
            logger.error("Parallel execution error: " + ex.getMessage(), ex);
          }
        }
      }
    }

    // Update calculation identifiers
    for (ProcessEquipmentInterface unit : unitOperations) {
      unit.setCalculationIdentifier(id);
    }
    setCalculationIdentifier(id);

    // Publish simulation complete event
    publishEvent(
        new ProcessEvent(ProcessEvent.generateId(), ProcessEvent.EventType.SIMULATION_COMPLETE,
            getName(), "Parallel simulation completed", ProcessEvent.Severity.INFO));
  }

  /**
   * Groups nodes by shared input streams for parallel execution safety.
   *
   * <p>
   * Units that share the same input stream cannot safely run in parallel because they would
   * concurrently read from the same thermo system. This method uses Union-Find to group nodes that
   * share any input stream, so they can be run sequentially within their group while different
   * groups run in parallel.
   * </p>
   *
   * @param nodes list of nodes at the same execution level
   * @return list of groups, where each group contains nodes that share input streams
   */
  private List<List<ProcessNode>> groupNodesBySharedInputStreams(List<ProcessNode> nodes) {
    // Use Union-Find to group nodes that share input streams
    // Map each node to its group representative
    Map<ProcessNode, ProcessNode> parent = new IdentityHashMap<>();
    for (ProcessNode node : nodes) {
      parent.put(node, node);
    }

    // Find with path compression
    java.util.function.Function<ProcessNode, ProcessNode> find =
        new java.util.function.Function<ProcessNode, ProcessNode>() {
          @Override
          public ProcessNode apply(ProcessNode node) {
            if (parent.get(node) != node) {
              parent.put(node, this.apply(parent.get(node)));
            }
            return parent.get(node);
          }
        };

    // Union operation
    java.util.function.BiConsumer<ProcessNode, ProcessNode> union = (a, b) -> {
      ProcessNode rootA = find.apply(a);
      ProcessNode rootB = find.apply(b);
      if (rootA != rootB) {
        parent.put(rootA, rootB);
      }
    };

    // Build a map from input stream to nodes that use it
    Map<StreamInterface, List<ProcessNode>> streamToNodes = new IdentityHashMap<>();
    for (ProcessNode node : nodes) {
      for (ProcessEdge edge : node.getIncomingEdges()) {
        StreamInterface stream = edge.getStream();
        if (stream != null) {
          if (!streamToNodes.containsKey(stream)) {
            streamToNodes.put(stream, new ArrayList<>());
          }
          streamToNodes.get(stream).add(node);
        }
      }
    }

    // Union nodes that share the same input stream
    for (List<ProcessNode> nodesWithSameStream : streamToNodes.values()) {
      if (nodesWithSameStream.size() > 1) {
        ProcessNode first = nodesWithSameStream.get(0);
        for (int i = 1; i < nodesWithSameStream.size(); i++) {
          union.accept(first, nodesWithSameStream.get(i));
        }
      }
    }

    // Group nodes by their root representative
    Map<ProcessNode, List<ProcessNode>> groups = new IdentityHashMap<>();
    for (ProcessNode node : nodes) {
      ProcessNode root = find.apply(node);
      if (!groups.containsKey(root)) {
        groups.put(root, new ArrayList<>());
      }
      groups.get(root).add(node);
    }

    return new ArrayList<>(groups.values());
  }

  /**
   * Gets the parallel execution partition for this process.
   *
   * <p>
   * This method returns information about how the process can be parallelized, including: - The
   * number of parallel levels - Maximum parallelism (max units that can run concurrently) - Which
   * units are at each level
   * </p>
   *
   * @return parallel partition result, or null if graph cannot be built
   */
  public ProcessGraph.ParallelPartition getParallelPartition() {
    ProcessGraph graph = buildGraph();
    return graph.partitionForParallelExecution();
  }

  /**
   * Checks if parallel execution would be beneficial for this process.
   *
   * <p>
   * Parallel execution is considered beneficial when:
   * <ul>
   * <li>There are at least 2 units that can run in parallel (maxParallelism &gt;= 2)</li>
   * <li>The process has no recycle loops (which require iterative sequential execution)</li>
   * <li>There are enough units to justify thread overhead (typically &gt; 4 units)</li>
   * </ul>
   *
   * @return true if parallel execution is recommended
   */
  public boolean isParallelExecutionBeneficial() {
    // Need enough units to justify parallelism overhead
    if (unitOperations.size() < 4) {
      return false;
    }

    // Check for recycles and adjusters - they require iterative execution
    for (ProcessEquipmentInterface unit : unitOperations) {
      if (unit instanceof Recycle) {
        return false;
      }
      if (unit instanceof Adjuster) {
        return false;
      }
    }
    // Note: multi-input equipment (Mixer, HeatExchanger, etc.) is handled safely
    // by level-based parallel execution - multi-input units are placed at a level
    // after all their inputs, and groupNodesBySharedInputStreams() prevents race
    // conditions on shared streams.

    // Check parallel partition
    ProcessGraph.ParallelPartition partition = getParallelPartition();
    if (partition == null) {
      return false;
    }

    // Need at least 2 units that can run in parallel
    return partition.getMaxParallelism() >= 2;
  }

  /**
   * Runs the process using the optimal execution strategy.
   *
   * <p>
   * This method automatically determines whether to use parallel or sequential execution based on
   * the process structure. It will use parallel execution if:
   * <ul>
   * <li>The process has independent branches that can benefit from parallelism</li>
   * <li>There are no recycle loops or adjusters requiring iterative execution</li>
   * <li>The process is large enough to justify the thread management overhead</li>
   * </ul>
   *
   * <p>
   * For processes with recycles or adjusters, this method falls back to the standard sequential
   * {@link #run()} method which properly handles convergence iterations.
   */
  public void runOptimal() {
    runOptimal(UUID.randomUUID());
  }

  /**
   * Runs the process using the optimal execution strategy with calculation ID tracking.
   *
   * @param id calculation identifier for tracking
   * @see #runOptimal()
   */
  public void runOptimal(UUID id) {
    if (isParallelExecutionBeneficial()) {
      try {
        runParallel(id);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.warn("Parallel execution interrupted, falling back to sequential", e);
        run(id);
      }
    } else {
      run(id);
    }
  }

  /**
   * <p>
   * runAsThread.
   * </p>
   *
   * @return a {@link java.lang.Thread} object
   * @deprecated Use {@link #runAsTask()} instead for better resource management. This method
   *             creates a new unmanaged thread directly.
   */
  @Deprecated
  public Thread runAsThread() {
    Thread processThread = new Thread(this);
    processThread.start();
    return processThread;
  }

  /** {@inheritDoc} */
  @Override
  public synchronized void run(UUID id) {
    // Use optimized execution by default for best performance
    if (useOptimizedExecution) {
      runOptimized(id);
      return;
    }
    // Legacy sequential execution path
    runSequential(id);
  }

  /**
   * Runs the process system using sequential execution.
   *
   * <p>
   * This method executes units in insertion order (or topological order if useGraphBasedExecution
   * is enabled). It handles recycle loops by iterating until convergence. This is the legacy
   * execution mode preserved for backward compatibility.
   * </p>
   *
   * @param id calculation identifier for tracking
   */
  public synchronized void runSequential(UUID id) {
    // Determine execution order: use graph-based if enabled, otherwise use
    // insertion order
    List<ProcessEquipmentInterface> executionOrder;
    if (useGraphBasedExecution) {
      List<ProcessEquipmentInterface> topoOrder = getTopologicalOrder();
      executionOrder = (topoOrder != null) ? topoOrder : unitOperations;
    } else {
      executionOrder = unitOperations;
    }

    // Publish simulation start event
    publishEvent(new ProcessEvent(ProcessEvent.generateId(), ProcessEvent.EventType.INFO, getName(),
        "Sequential simulation started with " + executionOrder.size() + " units",
        ProcessEvent.Severity.INFO));

    // Auto-validate equipment setup before first run
    if (autoValidate) {
      runAutoValidation(executionOrder);
    }

    // Run setters first to set conditions
    for (int i = 0; i < executionOrder.size(); i++) {
      ProcessEquipmentInterface unit = executionOrder.get(i);
      if (unit instanceof Setter) {
        unit.run(id);
      }
    }

    boolean hasRecycle = false;

    // Initializing recycle controller
    recycleController.clear();
    for (int i = 0; i < executionOrder.size(); i++) {
      ProcessEquipmentInterface unit = executionOrder.get(i);
      if (unit instanceof Recycle) {
        hasRecycle = true;
        recycleController.addRecycle((Recycle) unit);
      }
    }
    recycleController.init();

    boolean isConverged = true;
    int iter = 0;
    do {
      iter++;
      isConverged = true;
      for (int i = 0; i < executionOrder.size(); i++) {
        ProcessEquipmentInterface unit = executionOrder.get(i);
        if (Thread.currentThread().isInterrupted()) {
          logger.debug("Process simulation was interrupted, exiting run()..." + getName());
          break;
        }
        if (!(unit instanceof Recycle)) {
          try {
            if (iter == 1 || unit.needRecalculation()) {
              unit.run(id);
            }
          } catch (Exception ex) {
            logger.error("error running unit uperation " + unit.getName() + " " + ex.getMessage(),
                ex);
            publishEvent(new ProcessEvent(ProcessEvent.generateId(), ProcessEvent.EventType.ERROR,
                unit.getName(), "Unit error: " + ex.getMessage(), ProcessEvent.Severity.ERROR));
          }
        }
        if (unit instanceof Recycle && recycleController.doSolveRecycle((Recycle) unit)) {
          try {
            unit.run(id);
          } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
          }
        }
      }
      if (!recycleController.solvedAll() || recycleController.hasHigherPriorityLevel()) {
        isConverged = false;
      }

      if (recycleController.solvedCurrentPriorityLevel()) {
        recycleController.nextPriorityLevel();
      } else if (recycleController.hasLoverPriorityLevel() && !recycleController.solvedAll()) {
        recycleController.resetPriorityLevel();
      }

      for (int i = 0; i < executionOrder.size(); i++) {
        ProcessEquipmentInterface unit = executionOrder.get(i);
        if (unit instanceof Adjuster) {
          if (!((Adjuster) unit).solved()) {
            isConverged = false;
            break;
          }
        }
      }
    } while (((!isConverged || (iter < 2 && hasRecycle)) && iter < 100) && !runStep
        && !Thread.currentThread().isInterrupted());

    // Publish simulation complete event
    publishEvent(new ProcessEvent(ProcessEvent.generateId(),
        ProcessEvent.EventType.SIMULATION_COMPLETE, getName(),
        "Sequential simulation completed after " + iter + " iterations, converged=" + isConverged,
        isConverged ? ProcessEvent.Severity.INFO : ProcessEvent.Severity.WARNING));

    for (int i = 0; i < executionOrder.size(); i++) {
      executionOrder.get(i).setCalculationIdentifier(id);
    }

    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void run_step(UUID id) {
    for (int i = 0; i < unitOperations.size(); i++) {
      try {
        if (Thread.currentThread().isInterrupted()) {
          logger.debug("Process simulation was interrupted, exiting run()..." + getName());
          break;
        }
        unitOperations.get(i).run(id);
        // }
      } catch (Exception ex) {
        // String error = ex.getMessage();
        logger.error(
            "equipment: " + unitOperations.get(i).getName() + " errror: " + ex.getMessage(), ex);
      }
    }
    for (int i = 0; i < unitOperations.size(); i++) {
      unitOperations.get(i).setCalculationIdentifier(id);
    }
    setCalculationIdentifier(id);
  }

  /**
   * Set a listener to receive progress updates during simulation. Useful for real-time
   * visualization in Jupyter notebooks and digital twin dashboards.
   *
   * <p>
   * Example usage in Python/Jupyter:
   *
   * <pre>
   * class MyListener(ProcessSystem.SimulationProgressListener):
   *     def onUnitComplete(self, unit, index, total, iteration):
   *         print(f"Completed {unit.getName()} ({index+1}/{total})")
   *
   * process.setProgressListener(MyListener())
   * process.run()
   * </pre>
   *
   * @param listener the progress listener, or null to disable callbacks
   */
  public void setProgressListener(SimulationProgressListener listener) {
    this.progressListener = listener;
  }

  /**
   * Get the current progress listener.
   *
   * @return the current listener, or null if none is set
   */
  public SimulationProgressListener getProgressListener() {
    return this.progressListener;
  }

  /**
   * Enables or disables event publishing to the ProcessEventBus singleton. When enabled, lifecycle
   * events (simulation start/complete, unit errors, threshold crossings) are published to the event
   * bus during steady-state and transient execution.
   *
   * @param publish true to enable event publishing, false to disable (default)
   */
  public void setPublishEvents(boolean publish) {
    this.publishEvents = publish;
  }

  /**
   * Returns whether event publishing is enabled.
   *
   * @return true if events are published to ProcessEventBus during simulation
   */
  public boolean isPublishEvents() {
    return this.publishEvents;
  }

  /**
   * Enables or disables automatic equipment validation before the first simulation iteration. When
   * enabled, validateSetup() is called on each equipment unit before the first run. Validation
   * failures are logged as warnings but do not abort execution.
   *
   * @param validate true to enable auto-validation, false to disable (default)
   */
  public void setAutoValidate(boolean validate) {
    this.autoValidate = validate;
  }

  /**
   * Returns whether auto-validation is enabled.
   *
   * @return true if equipment setup is validated before simulation runs
   */
  public boolean isAutoValidate() {
    return this.autoValidate;
  }

  /**
   * Run simulation with a simple callback for each completed unit operation. This is a convenience
   * method for Python/Jupyter integration where implementing the full SimulationProgressListener
   * interface may be cumbersome.
   *
   * <p>
   * Example usage in Python/Jupyter:
   *
   * <pre>
   * def on_complete(unit):
   *     print(f"Completed: {unit.getName()}")
   *     temp = unit.getOutletStream().getTemperature("C")
   *     # Update live plot...
   *
   * process.runWithCallback(on_complete)
   * </pre>
   *
   * @param callback Consumer function called with each completed unit operation. May be null for no
   *        callbacks (equivalent to regular run()).
   */
  public void runWithCallback(java.util.function.Consumer<ProcessEquipmentInterface> callback) {
    runWithCallback(callback, UUID.randomUUID());
  }

  /**
   * Run simulation with a simple callback for each completed unit operation.
   *
   * @param callback Consumer function called with each completed unit operation
   * @param id calculation identifier for tracking
   */
  public void runWithCallback(java.util.function.Consumer<ProcessEquipmentInterface> callback,
      UUID id) {
    // Wrap the simple callback in a full listener if provided
    SimulationProgressListener originalListener = this.progressListener;
    if (callback != null) {
      this.progressListener = new SimulationProgressListener() {
        @Override
        public void onUnitComplete(ProcessEquipmentInterface unit, int unitIndex, int totalUnits,
            int iterationNumber) {
          callback.accept(unit);
        }
      };
    }

    try {
      runWithProgress(id);
    } finally {
      // Restore original listener
      this.progressListener = originalListener;
    }
  }

  /**
   * Run simulation with full progress monitoring. This method executes the process system and
   * invokes the registered SimulationProgressListener after each unit operation and iteration
   * completes.
   *
   * <p>
   * This is the primary method for digital twin applications requiring real-time feedback. It
   * supports:
   * <ul>
   * <li>Progress callbacks after each unit operation</li>
   * <li>Iteration callbacks for recycle convergence monitoring</li>
   * <li>Error callbacks with optional continuation</li>
   * <li>Thread interruption handling</li>
   * </ul>
   *
   * @param id calculation identifier for tracking
   */
  public void runWithProgress(UUID id) {
    // Determine execution order
    List<ProcessEquipmentInterface> executionOrder;
    if (useGraphBasedExecution) {
      List<ProcessEquipmentInterface> topoOrder = getTopologicalOrder();
      executionOrder = (topoOrder != null) ? topoOrder : unitOperations;
    } else {
      executionOrder = unitOperations;
    }

    int totalUnits = executionOrder.size();

    // Notify simulation start
    notifySimulationStart(totalUnits);
    publishEvent(new ProcessEvent(ProcessEvent.generateId(), ProcessEvent.EventType.INFO, getName(),
        "Simulation started with " + totalUnits + " units", ProcessEvent.Severity.INFO));

    // Auto-validate equipment setup before first run
    if (autoValidate) {
      runAutoValidation(executionOrder);
    }

    // Run setters first
    for (int i = 0; i < totalUnits; i++) {
      ProcessEquipmentInterface unit = executionOrder.get(i);
      if (unit instanceof Setter) {
        unit.run(id);
        notifyUnitComplete(unit, i, totalUnits, 0);
      }
    }

    // Initialize recycle controller
    boolean hasRecycle = false;
    recycleController.clear();
    for (int i = 0; i < totalUnits; i++) {
      ProcessEquipmentInterface unit = executionOrder.get(i);
      if (unit instanceof Recycle) {
        hasRecycle = true;
        recycleController.addRecycle((Recycle) unit);
      }
    }
    recycleController.init();

    // Main iteration loop
    boolean isConverged = true;
    int iter = 0;
    do {
      iter++;
      isConverged = true;

      // Notify before-iteration
      notifyBeforeIteration(iter);
      publishEvent(new ProcessEvent(ProcessEvent.generateId(), ProcessEvent.EventType.STATE_CHANGE,
          getName(), "Starting iteration " + iter, ProcessEvent.Severity.DEBUG));

      for (int i = 0; i < totalUnits; i++) {
        ProcessEquipmentInterface unit = executionOrder.get(i);

        if (Thread.currentThread().isInterrupted()) {
          logger.debug(
              "Process simulation was interrupted, exiting runWithProgress()..." + getName());
          break;
        }

        if (!(unit instanceof Recycle)) {
          try {
            if (iter == 1 || unit.needRecalculation()) {
              notifyBeforeUnit(unit, i, totalUnits, iter);
              unit.run(id);
            }
            notifyUnitComplete(unit, i, totalUnits, iter);
          } catch (Exception ex) {
            logger.error("Error running unit operation " + unit.getName() + " " + ex.getMessage(),
                ex);
            publishEvent(new ProcessEvent(ProcessEvent.generateId(), ProcessEvent.EventType.ERROR,
                unit.getName(), "Unit error: " + ex.getMessage(), ProcessEvent.Severity.ERROR));
            if (!notifyUnitError(unit, ex)) {
              // Listener requested abort
              return;
            }
          }
        }

        if (unit instanceof Recycle && recycleController.doSolveRecycle((Recycle) unit)) {
          try {
            notifyBeforeUnit(unit, i, totalUnits, iter);
            unit.run(id);
            notifyUnitComplete(unit, i, totalUnits, iter);
          } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            if (!notifyUnitError(unit, ex)) {
              return;
            }
          }
        }
      }

      // Check convergence
      if (!recycleController.solvedAll() || recycleController.hasHigherPriorityLevel()) {
        isConverged = false;
      }

      if (recycleController.solvedCurrentPriorityLevel()) {
        recycleController.nextPriorityLevel();
      } else if (recycleController.hasLoverPriorityLevel() && !recycleController.solvedAll()) {
        recycleController.resetPriorityLevel();
      }

      for (int i = 0; i < totalUnits; i++) {
        ProcessEquipmentInterface unit = executionOrder.get(i);
        if (unit instanceof Adjuster) {
          if (!((Adjuster) unit).solved()) {
            isConverged = false;
            break;
          }
        }
      }

      // Notify iteration complete
      double recycleError = recycleController.getMaxResidualError();
      notifyIterationComplete(iter, isConverged, recycleError);

    } while (((!isConverged || (iter < 2 && hasRecycle)) && iter < 100) && !runStep
        && !Thread.currentThread().isInterrupted());

    // Notify simulation complete
    notifySimulationComplete(iter, isConverged);
    publishEvent(new ProcessEvent(ProcessEvent.generateId(),
        ProcessEvent.EventType.SIMULATION_COMPLETE, getName(),
        "Simulation completed after " + iter + " iterations, converged=" + isConverged,
        isConverged ? ProcessEvent.Severity.INFO : ProcessEvent.Severity.WARNING));

    for (int i = 0; i < totalUnits; i++) {
      executionOrder.get(i).setCalculationIdentifier(id);
    }
    setCalculationIdentifier(id);
  }

  /**
   * Notify the progress listener that a unit operation has completed.
   *
   * @param unit the completed unit
   * @param unitIndex index of the unit
   * @param totalUnits total number of units
   * @param iterationNumber current iteration
   */
  private void notifyUnitComplete(ProcessEquipmentInterface unit, int unitIndex, int totalUnits,
      int iterationNumber) {
    if (progressListener != null) {
      try {
        progressListener.onUnitComplete(unit, unitIndex, totalUnits, iterationNumber);
      } catch (Exception ex) {
        logger.warn("Progress listener threw exception in onUnitComplete: " + ex.getMessage());
      }
    }
  }

  /**
   * Notify the progress listener that an iteration has completed.
   *
   * @param iterationNumber the completed iteration
   * @param converged whether the system has converged
   * @param recycleError maximum recycle error
   */
  private void notifyIterationComplete(int iterationNumber, boolean converged,
      double recycleError) {
    if (progressListener != null) {
      try {
        progressListener.onIterationComplete(iterationNumber, converged, recycleError);
      } catch (Exception ex) {
        logger.warn("Progress listener threw exception in onIterationComplete: " + ex.getMessage());
      }
    }
  }

  /**
   * Notify the progress listener that a unit operation encountered an error.
   *
   * @param unit the unit that failed
   * @param exception the exception
   * @return true to continue, false to abort
   */
  private boolean notifyUnitError(ProcessEquipmentInterface unit, Exception exception) {
    if (progressListener != null) {
      try {
        return progressListener.onUnitError(unit, exception);
      } catch (Exception ex) {
        logger.warn("Progress listener threw exception in onUnitError: " + ex.getMessage());
      }
    }
    return false; // Default: abort on error
  }

  /**
   * Notify the progress listener that a unit operation is about to start.
   *
   * @param unit the unit about to run
   * @param unitIndex index of the unit
   * @param totalUnits total number of units
   * @param iterationNumber current iteration
   */
  private void notifyBeforeUnit(ProcessEquipmentInterface unit, int unitIndex, int totalUnits,
      int iterationNumber) {
    if (progressListener != null) {
      try {
        progressListener.onBeforeUnit(unit, unitIndex, totalUnits, iterationNumber);
      } catch (Exception ex) {
        logger.warn("Progress listener threw exception in onBeforeUnit: " + ex.getMessage());
      }
    }
  }

  /**
   * Notify the progress listener that an iteration is about to start.
   *
   * @param iterationNumber the iteration about to start
   */
  private void notifyBeforeIteration(int iterationNumber) {
    if (progressListener != null) {
      try {
        progressListener.onBeforeIteration(iterationNumber);
      } catch (Exception ex) {
        logger.warn("Progress listener threw exception in onBeforeIteration: " + ex.getMessage());
      }
    }
  }

  /**
   * Notify the progress listener that the simulation is starting.
   *
   * @param totalUnits total number of unit operations
   */
  private void notifySimulationStart(int totalUnits) {
    if (progressListener != null) {
      try {
        progressListener.onSimulationStart(totalUnits);
      } catch (Exception ex) {
        logger.warn("Progress listener threw exception in onSimulationStart: " + ex.getMessage());
      }
    }
  }

  /**
   * Notify the progress listener that the simulation has completed.
   *
   * @param totalIterations total number of iterations executed
   * @param converged whether the simulation converged
   */
  private void notifySimulationComplete(int totalIterations, boolean converged) {
    if (progressListener != null) {
      try {
        progressListener.onSimulationComplete(totalIterations, converged);
      } catch (Exception ex) {
        logger
            .warn("Progress listener threw exception in onSimulationComplete: " + ex.getMessage());
      }
    }
  }

  /**
   * Publish a process event to the event bus if event publishing is enabled.
   *
   * @param event the event to publish
   */
  private void publishEvent(ProcessEvent event) {
    if (publishEvents && event != null) {
      try {
        ProcessEventBus.getInstance().publish(event);
      } catch (Exception ex) {
        logger.warn("Failed to publish process event: " + ex.getMessage());
      }
    }
  }

  /**
   * Run auto-validation on all equipment units. Called once before the first iteration when
   * autoValidate is enabled.
   *
   * @param executionOrder the list of units to validate
   */
  private void runAutoValidation(List<ProcessEquipmentInterface> executionOrder) {
    for (int i = 0; i < executionOrder.size(); i++) {
      ProcessEquipmentInterface unit = executionOrder.get(i);
      try {
        neqsim.util.validation.ValidationResult result = unit.validateSetup();
        if (result != null && !result.isValid()) {
          logger.warn("Validation warning for " + unit.getName() + ": " + result);
          if (publishEvents) {
            publishEvent(ProcessEvent.warning(unit.getName(),
                "Setup validation failed: " + result.toString()));
          }
        }
      } catch (Exception ex) {
        logger.debug("Could not validate " + unit.getName() + ": " + ex.getMessage());
      }
    }
  }

  /*
   * signalDB = new String[1000][1 + 3 * measurementDevices.size()];
   *
   * signalDB[timeStepNumber] = new String[1 + 3 * measurementDevices.size()]; for (int i = 0; i <
   * measurementDevices.size(); i++) { signalDB[timeStepNumber][0] = Double.toString(time);
   * signalDB[timeStepNumber][3 * i + 1] = ((MeasurementDeviceInterface) measurementDevices.get(i))
   * .getName(); signalDB[timeStepNumber][3 * i + 2] = Double
   * .toString(((MeasurementDeviceInterface) measurementDevices.get(i)).getMeasuredValue());
   * signalDB[timeStepNumber][3 * i + 3] = ((MeasurementDeviceInterface) measurementDevices.get(i))
   * .getUnit(); }
   */

  /**
   * <p>
   * runTransient.
   * </p>
   */
  public void runTransient() {
    runTransient(getTimeStep(), UUID.randomUUID());
  }

  /** {@inheritDoc} */
  @Override
  public synchronized void runTransient(double dt, UUID id) {
    ensureInitialStateSnapshot();

    // Publish pre-timestep event
    publishEvent(new ProcessEvent(ProcessEvent.generateId(), ProcessEvent.EventType.STATE_CHANGE,
        getName(), "Transient timestep " + timeStepNumber + " starting at t="
            + String.format("%.3f", time) + " s, dt=" + String.format("%.4f", dt) + " s",
        ProcessEvent.Severity.DEBUG));

    for (int i = 0; i < unitOperations.size(); i++) {
      ProcessEquipmentInterface unit = unitOperations.get(i);
      if (unit instanceof Setter) {
        unit.run(id);
      }
    }

    setTimeStep(dt);
    increaseTime(dt);

    // Apply field data from INPUT instruments before running the model
    applyFieldInputs();

    // Track mass before transient step
    if (enableMassBalanceTracking) {
      double currentMass = calculateTotalSystemMass();
      if (previousTotalMass > 0) {
        massBalanceError = Math.abs(currentMass - previousTotalMass) / previousTotalMass * 100.0;
        if (massBalanceError > massBalanceErrorThreshold) {
          logger.warn("Mass balance error: " + String.format("%.3f", massBalanceError)
              + "% (threshold: " + massBalanceErrorThreshold + "%) at time " + time + " s");
          publishEvent(ProcessEvent.thresholdCrossed(getName(), "massBalanceError",
              massBalanceError, massBalanceErrorThreshold, true));
        }
      }
      previousTotalMass = currentMass;
    }

    // Run equipment transient calculations
    // Note: Multiple iterations cause accumulation errors - run once per time step
    for (int i = 0; i < unitOperations.size(); i++) {
      unitOperations.get(i).runTransient(dt, id);
    }

    // Explicit controller scan phase: run standalone controllers registered via
    // add(ControllerDeviceInterface). Equipment-embedded controllers already ran above
    // inside each equipment's runTransient() for backward compatibility.
    for (int i = 0; i < controllerDevices.size(); i++) {
      ControllerDeviceInterface ctrl = controllerDevices.get(i);
      if (ctrl.isActive()) {
        ctrl.runTransient(ctrl.getResponse(), dt, id);
      }
    }

    // Publish post-controller, pre-measurement event
    publishEvent(
        new ProcessEvent(ProcessEvent.generateId(), ProcessEvent.EventType.STATE_CHANGE, getName(),
            "Controllers completed for timestep " + timeStepNumber, ProcessEvent.Severity.DEBUG));

    timeStepNumber++;
    String[] row = new String[1 + 3 * measurementDevices.size()];
    if (row.length > 0) {
      row[0] = Double.toString(time);
    }
    for (int i = 0; i < measurementDevices.size(); i++) {
      MeasurementDeviceInterface device = measurementDevices.get(i);
      double measuredValue = device.getMeasuredValue();
      row[3 * i + 1] = device.getName();
      row[3 * i + 2] = Double.toString(measuredValue);
      row[3 * i + 3] = device.getUnit();
      alarmManager.evaluateMeasurement(device, measuredValue, dt, time);
    }
    if (measurementDevices.isEmpty()) {
      row[0] = Double.toString(time);
    }
    measurementHistory.add(row);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public boolean solved() {
    /* */
    if (recycleController.solvedAll()) {
      for (int i = 0; i < unitOperations.size(); i++) {
        logger.info("unit " + unitOperations.get(i).getName() + " solved: "
            + unitOperations.get(i).solved());
        if (!unitOperations.get(i).solved()) {
          return false;
        }
      }
    } else {
      return false;
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public double getTime() {
    return time;
  }

  /**
   * <p>
   * Getter for the field <code>time</code>.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getTime(String unit) {
    if (unit.equals("sec")) {
    } else if (unit.equals("hr")) {
      return time / 3600.0;
    } else if (unit.equals("day")) {
      return time / (3600.0 * 24);
    } else if (unit.equals("year")) {
      return time / (3600.0 * 24 * 365);
    }
    return time;
  }

  /**
   * Calculate total system mass across all equipment and streams.
   *
   * @return Total mass in kg
   */
  private double calculateTotalSystemMass() {
    double totalMass = 0.0;
    for (ProcessEquipmentInterface unit : unitOperations) {
      try {
        SystemInterface system = unit.getThermoSystem();
        if (system != null && system.getTotalNumberOfMoles() > 0) {
          totalMass += system.getTotalNumberOfMoles() * system.getMolarMass();
        }
      } catch (Exception e) {
        // Some units may not have a thermo system
      }
    }
    return totalMass;
  }

  /**
   * Enable or disable mass balance tracking during transient simulations.
   *
   * @param enable true to enable tracking
   */
  public void setEnableMassBalanceTracking(boolean enable) {
    this.enableMassBalanceTracking = enable;
    if (enable) {
      previousTotalMass = calculateTotalSystemMass();
    }
  }

  /**
   * Get the current mass balance error percentage.
   *
   * @return Mass balance error in percent
   */
  public double getMassBalanceError() {
    return massBalanceError;
  }

  /**
   * Set the maximum number of iterations within each transient time step.
   *
   * <p>
   * Multiple iterations help converge circular dependencies between equipment. Default is 3. Set to
   * 1 to disable iterative convergence.
   * </p>
   *
   * @param iterations Number of iterations (must be &gt;= 1)
   */
  public void setMaxTransientIterations(int iterations) {
    if (iterations < 1) {
      throw new IllegalArgumentException("Iterations must be >= 1");
    }
    this.maxTransientIterations = iterations;
  }

  /**
   * Get the maximum number of iterations within each transient time step.
   *
   * @return Number of iterations
   */
  public int getMaxTransientIterations() {
    return maxTransientIterations;
  }

  /**
   * <p>
   * size.
   * </p>
   *
   * @return a int
   */
  public int size() {
    return unitOperations.size();
  }

  /**
   * <p>
   * view.
   * </p>
   */
  public void view() {
    this.displayResult();
  }

  /**
   * <p>
   * displayResult.
   * </p>
   */
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    try {
      thisThread.join();
    } catch (Exception ex) {
      logger.error("Thread did not finish", ex);
    }
    for (int i = 0; i < unitOperations.size(); i++) {
      unitOperations.get(i).displayResult();
    }

    /*
     * JFrame frame = new JFrame(); frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
     * frame.setLayout(new GridLayout(1, 0, 5, 5)); JTextArea area1 = new JTextArea(10, 10); JTable
     * Jtab = new JTable(reportResults(), reportResults()[0]); frame.add(area1); frame.pack();
     * frame.setLocationRelativeTo(null); frame.setVisible(true);
     */
  }

  /**
   * <p>
   * reportMeasuredValues.
   * </p>
   */
  public void reportMeasuredValues() {
    try {
      thisThread.join();
    } catch (Exception ex) {
      logger.error("Thread did not finish", ex);
    }
    for (int i = 0; i < measurementDevices.size(); i++) {
      System.out.println("Measurements Device Name: " + measurementDevices.get(i).getName());
      System.out.println("Value: " + measurementDevices.get(i).getMeasuredValue() + " "
          + measurementDevices.get(i).getUnit());
      if (measurementDevices.get(i).isOnlineSignal()) {
        System.out.println("Online value: " + measurementDevices.get(i).getOnlineSignal().getValue()
            + " " + measurementDevices.get(i).getOnlineSignal().getUnit());
      }
    }
  }

  /**
   * <p>
   * save.
   * </p>
   *
   * @param filePath a {@link java.lang.String} object
   */
  public void save(String filePath) {
    try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filePath, false))) {
      out.writeObject(this);
      logger.info("process file saved to:  " + filePath);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /**
   * <p>
   * open.
   * </p>
   *
   * @param filePath a {@link java.lang.String} object
   * @return a {@link neqsim.process.processmodel.ProcessSystem} object
   */
  public static ProcessSystem open(String filePath) {
    try (ObjectInputStream objectinputstream =
        new ObjectInputStream(new FileInputStream(filePath))) {
      return (ProcessSystem) objectinputstream.readObject();
      // logger.info("process file open ok: " + filePath);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return null;
  }

  /**
   * <p>
   * reportResults.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[][] reportResults() {
    String[][] text = new String[200][];

    int numb = 0;
    for (int i = 0; i < unitOperations.size(); i++) {
      for (int k = 0; k < unitOperations.get(i).reportResults().length; k++) {
        text[numb++] = unitOperations.get(i).reportResults()[k];
      }
    }
    return text;
  }

  /**
   * Returns a consolidated formatted stream summary table showing key properties for all streams in
   * this process system. Similar to the Workbook/Stream Summary in commercial process simulators.
   *
   * <p>
   * The table includes: stream name, temperature (C), pressure (bara), total molar flow (kmole/hr),
   * mass flow (kg/hr), vapor fraction, molar mass (kg/kmol), and mole fraction of each component.
   * </p>
   *
   * @return formatted string table of all stream properties
   */
  public String getStreamSummaryTable() {
    List<StreamInterface> streams = getAllStreams();
    if (streams.isEmpty()) {
      return "No streams found in process system.\n";
    }

    // Collect component names from the first stream
    SystemInterface firstFluid = streams.get(0).getFluid();
    int numComponents = firstFluid.getNumberOfComponents();
    List<String> componentNames = new ArrayList<>();
    for (int i = 0; i < numComponents; i++) {
      componentNames.add(firstFluid.getPhase(0).getComponent(i).getComponentName());
    }

    // Define rows
    List<String> rowLabels = new ArrayList<>();
    rowLabels.add("Temperature [C]");
    rowLabels.add("Pressure [bara]");
    rowLabels.add("Total Flow [kg/hr]");
    rowLabels.add("Total Flow [kmole/hr]");
    rowLabels.add("Vapor Fraction [mole]");
    rowLabels.add("Molar Mass [kg/kmol]");
    rowLabels.add("--- Mole Fractions ---");
    for (String compName : componentNames) {
      rowLabels.add(compName);
    }

    // Determine column width
    int labelWidth = 0;
    for (String label : rowLabels) {
      labelWidth = Math.max(labelWidth, label.length());
    }
    labelWidth = Math.max(labelWidth, 22);
    int colWidth = 0;
    for (StreamInterface stream : streams) {
      colWidth = Math.max(colWidth, stream.getName().length());
    }
    colWidth = Math.max(colWidth, 14);

    StringBuilder sb = new StringBuilder();

    // Header row
    sb.append(String.format("%-" + labelWidth + "s", ""));
    for (StreamInterface stream : streams) {
      sb.append(String.format("  %" + colWidth + "s", stream.getName()));
    }
    sb.append("\n");

    // Separator line
    int totalWidth = labelWidth + streams.size() * (colWidth + 2);
    for (int i = 0; i < totalWidth; i++) {
      sb.append("-");
    }
    sb.append("\n");

    // Data rows
    for (int row = 0; row < rowLabels.size(); row++) {
      String label = rowLabels.get(row);
      sb.append(String.format("%-" + labelWidth + "s", label));

      if (label.startsWith("---")) {
        // Section header - no data
        sb.append("\n");
        continue;
      }

      for (StreamInterface stream : streams) {
        SystemInterface fluid = stream.getFluid();
        String value;

        if (row == 0) {
          // Temperature in C
          value = String.format("%.2f", fluid.getTemperature("C"));
        } else if (row == 1) {
          // Pressure in bara
          value = String.format("%.4f", fluid.getPressure("bara"));
        } else if (row == 2) {
          // Mass flow kg/hr
          value = String.format("%.2f", stream.getFlowRate("kg/hr"));
        } else if (row == 3) {
          // Molar flow kmole/hr
          value = String.format("%.4f", fluid.getTotalNumberOfMoles() * 3600.0);
        } else if (row == 4) {
          // Vapor fraction
          if (fluid.getNumberOfPhases() > 1) {
            value = String.format("%.6f", fluid.getBeta());
          } else {
            if (fluid.getPhase(0).getType() == neqsim.thermo.phase.PhaseType.GAS) {
              value = "1.000000";
            } else {
              value = "0.000000";
            }
          }
        } else if (row == 5) {
          // Molar mass kg/kmol
          value = String.format("%.4f", fluid.getMolarMass() * 1000.0);
        } else {
          // Component mole fractions (row >= 7, component index = row - 7)
          int compIdx = row - 7;
          if (compIdx >= 0 && compIdx < fluid.getPhase(0).getNumberOfComponents()) {
            value = String.format("%.6f", fluid.getPhase(0).getComponent(compIdx).getz());
          } else {
            value = "---";
          }
        }

        sb.append(String.format("  %" + colWidth + "s", value));
      }
      sb.append("\n");
    }

    return sb.toString();
  }

  /**
   * Returns a consolidated stream summary as a JSON string. Each stream is a key in the JSON object
   * containing temperature, pressure, flow rates, vapor fraction, molar mass, and composition.
   *
   * @return JSON string with stream summary data
   */
  public String getStreamSummaryJson() {
    List<StreamInterface> streams = getAllStreams();
    com.google.gson.JsonObject root = new com.google.gson.JsonObject();

    for (StreamInterface stream : streams) {
      SystemInterface fluid = stream.getFluid();
      com.google.gson.JsonObject streamObj = new com.google.gson.JsonObject();

      streamObj.addProperty("temperature_C", fluid.getTemperature("C"));
      streamObj.addProperty("pressure_bara", fluid.getPressure("bara"));
      streamObj.addProperty("massFlow_kg_hr", stream.getFlowRate("kg/hr"));
      streamObj.addProperty("molarFlow_kmole_hr", fluid.getTotalNumberOfMoles() * 3600.0);

      if (fluid.getNumberOfPhases() > 1) {
        streamObj.addProperty("vaporFraction", fluid.getBeta());
      } else {
        if (fluid.getPhase(0).getType() == neqsim.thermo.phase.PhaseType.GAS) {
          streamObj.addProperty("vaporFraction", 1.0);
        } else {
          streamObj.addProperty("vaporFraction", 0.0);
        }
      }

      streamObj.addProperty("molarMass_kg_kmol", fluid.getMolarMass() * 1000.0);

      // Composition
      com.google.gson.JsonObject compObj = new com.google.gson.JsonObject();
      for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
        compObj.addProperty(fluid.getPhase(0).getComponent(i).getComponentName(),
            fluid.getPhase(0).getComponent(i).getz());
      }
      streamObj.add("composition_mole", compObj);

      root.add(stream.getName(), streamObj);
    }

    return new com.google.gson.GsonBuilder().setPrettyPrinting()
        .serializeSpecialFloatingPointValues().create().toJson(root);
  }

  /**
   * Returns a list of all streams in this process system. Collects all inlet and outlet streams
   * from all equipment, removing duplicates.
   *
   * @return list of unique streams in the process
   */
  public List<StreamInterface> getAllStreams() {
    java.util.LinkedHashSet<StreamInterface> streamSet = new java.util.LinkedHashSet<>();
    for (ProcessEquipmentInterface unit : unitOperations) {
      if (unit instanceof StreamInterface) {
        streamSet.add((StreamInterface) unit);
      }
    }
    return new ArrayList<>(streamSet);
  }

  /**
   * <p>
   * printLogFile.
   * </p>
   *
   * @param filename a {@link java.lang.String} object
   */
  public void printLogFile(String filename) {
    neqsim.datapresentation.filehandling.TextFile tempFile =
        new neqsim.datapresentation.filehandling.TextFile();
    tempFile.setOutputFileName(filename);
    tempFile.setValues(measurementHistory.toArray());
    tempFile.createFile();
  }

  /**
   * Clears all stored transient measurement history entries and resets the time step counter.
   */
  public void clearHistory() {
    measurementHistory.clear();
    timeStepNumber = 0;
    alarmManager.clearHistory();
  }

  /**
   * Returns the number of stored transient measurement entries.
   *
   * @return number of stored history entries
   */
  public int getHistorySize() {
    return measurementHistory.size();
  }

  /**
   * Returns the alarm manager responsible for coordinating alarm evaluation.
   *
   * @return alarm manager
   */
  public ProcessAlarmManager getAlarmManager() {
    return alarmManager;
  }

  /**
   * Returns a snapshot of the transient measurement history.
   *
   * @return the measurement history as a two-dimensional array
   */
  public String[][] getHistorySnapshot() {
    return measurementHistory.toArray();
  }

  /**
   * Sets the maximum number of entries retained in the measurement history. A value less than or
   * equal to zero disables truncation (unbounded history).
   *
   * @param maxEntries maximum number of entries to keep, or non-positive for unlimited
   */
  public void setHistoryCapacity(int maxEntries) {
    measurementHistory.setMaxSize(maxEntries);
  }

  /**
   * Returns the configured history capacity. A value less than or equal to zero means the history
   * grows without bounds.
   *
   * @return configured maximum number of history entries or non-positive for unlimited
   */
  public int getHistoryCapacity() {
    return measurementHistory.getMaxSize();
  }

  /**
   * Stores a snapshot of the current process system state that can later be restored with
   * {@link #reset()}.
   */
  public void storeInitialState() {
    captureInitialState(true);
  }

  /**
   * Restores the process system to the stored initial state. The initial state is captured
   * automatically the first time a transient run is executed, or manually via
   * {@link #storeInitialState()}.
   */
  public void reset() {
    if (initialStateSnapshot == null) {
      throw new IllegalStateException(
          "Initial state has not been stored. Call storeInitialState() before reset().");
    }
    ProcessSystem restored = initialStateSnapshot.copy();
    applyState(restored);
  }

  private void ensureInitialStateSnapshot() {
    captureInitialState(false);
  }

  private void captureInitialState(boolean force) {
    if (!force && initialStateSnapshot != null) {
      return;
    }

    ProcessSystem previousSnapshot = initialStateSnapshot;
    try {
      initialStateSnapshot = null;
      ProcessSystem snapshot = deepCopy();
      initialStateSnapshot = snapshot;
    } catch (RuntimeException ex) {
      initialStateSnapshot = previousSnapshot;
      throw ex;
    }
  }

  private void applyState(ProcessSystem source) {
    setName(source.getName());
    String tagName = source.getTagName();
    if (tagName != null) {
      setTagName(tagName);
    }
    setCalculateSteadyState(source.getCalculateSteadyState());
    setRunInSteps(source.isRunInSteps());
    setTime(source.getTime());
    surroundingTemperature = source.surroundingTemperature;
    timeStepNumber = source.timeStepNumber;
    unitOperations = new ArrayList<>(source.unitOperations);
    measurementDevices = new ArrayList<>(source.measurementDevices);
    if (source.alarmManager != null) {
      alarmManager.applyFrom(source.alarmManager, measurementDevices);
    } else {
      alarmManager = new ProcessAlarmManager();
      alarmManager.registerAll(measurementDevices);
    }
    recycleController = source.recycleController;
    timeStep = source.timeStep;
    runStep = source.runStep;
    equipmentCounter.clear();
    equipmentCounter.putAll(source.equipmentCounter);
    lastAddedUnit = source.lastAddedUnit;
    measurementHistory = source.measurementHistory.copy();
    thisThread = null;
    setCalculationIdentifier(source.getCalculationIdentifier());
  }

  /**
   * <p>
   * Getter for the field <code>timeStep</code>.
   * </p>
   *
   * @return a double
   */
  public double getTimeStep() {
    return timeStep;
  }

  /**
   * <p>
   * Setter for the field <code>timeStep</code>.
   * </p>
   *
   * @param timeStep a double
   */
  public void setTimeStep(double timeStep) {
    this.timeStep = timeStep;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return name;
  }

  /** {@inheritDoc} */
  @Override
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Setter for the field <code>runStep</code>.
   *
   * @param runStep A <code>boolean</code> value if run only one iteration
   */
  public void setRunStep(boolean runStep) {
    this.runStep = runStep;
  }

  /**
   * Getter for the field <code>runStep</code>.
   *
   * @return A <code>boolean</code> value if run only one iteration
   */
  public boolean isRunStep() {
    return runStep;
  }

  /**
   * <p>
   * getEntropyProduction.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getEntropyProduction(String unit) {
    double entropyProduction = 0.0;
    for (int i = 0; i < unitOperations.size(); i++) {
      entropyProduction += unitOperations.get(i).getEntropyProduction(unit);
      System.out.println("unit " + unitOperations.get(i).getName() + " entropy production "
          + unitOperations.get(i).getEntropyProduction(unit));
    }
    return entropyProduction;
  }

  /**
   * <p>
   * getExergyChange.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getExergyChange(String unit) {
    double exergyChange = 0.0;
    for (int i = 0; i < unitOperations.size(); i++) {
      exergyChange += unitOperations.get(i).getExergyChange("J", getSurroundingTemperature());
      System.out.println("unit " + unitOperations.get(i).getName() + " exergy change  "
          + unitOperations.get(i).getExergyChange("J", getSurroundingTemperature()));
    }
    return exergyChange;
  }

  /**
   * <p>
   * getPower.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getPower(String unit) {
    double power = 0.0;
    for (int i = 0; i < unitOperations.size(); i++) {
      ProcessEquipmentInterface unitOp = unitOperations.get(i);
      if (unitOp instanceof Compressor) {
        power += ((Compressor) unitOp).getPower();
      } else if (unitOp instanceof Pump) {
        power += ((Pump) unitOp).getPower();
      }
    }
    if (unit.equals("MW")) {
      return power / 1.0e6;
    } else if (unit.equals("kW")) {
      return power / 1.0e3;
    } else {
      return power;
    }
  }

  /**
   * <p>
   * getCoolerDuty.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getCoolerDuty(String unit) {
    double heat = 0.0;
    for (int i = 0; i < unitOperations.size(); i++) {
      ProcessEquipmentInterface unitOp = unitOperations.get(i);
      if (unitOp instanceof Cooler) {
        heat += ((Cooler) unitOp).getDuty();
      }
    }
    if (unit.equals("MW")) {
      return heat / 1.0e6;
    } else if (unit.equals("kW")) {
      return heat / 1.0e3;
    } else {
      return heat;
    }
  }

  /**
   * <p>
   * getHeaterDuty.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getHeaterDuty(String unit) {
    double heat = 0.0;
    for (int i = 0; i < unitOperations.size(); i++) {
      ProcessEquipmentInterface unitOp = unitOperations.get(i);
      if (unitOp instanceof Heater) {
        heat += ((Heater) unitOp).getDuty();
      }
    }
    if (unit.equals("MW")) {
      return heat / 1.0e6;
    } else if (unit.equals("kW")) {
      return heat / 1.0e3;
    } else {
      return heat;
    }
  }

  /**
   * <p>
   * Getter for the field <code>surroundingTemperature</code>.
   * </p>
   *
   * @return a double
   */
  public double getSurroundingTemperature() {
    return surroundingTemperature;
  }

  /**
   * <p>
   * Setter for the field <code>surroundingTemperature</code>.
   * </p>
   *
   * @param surroundingTemperature a double
   */
  public void setSurroundingTemperature(double surroundingTemperature) {
    this.surroundingTemperature = surroundingTemperature;
  }

  /**
   * <p>
   * Create deep copy.
   * </p>
   *
   * @return a {@link neqsim.process.processmodel.ProcessSystem} object
   */
  public synchronized ProcessSystem copy() {
    ProcessSystem snapshot = initialStateSnapshot;
    try {
      initialStateSnapshot = null;
      return deepCopy();
    } finally {
      initialStateSnapshot = snapshot;
    }
  }

  private ProcessSystem deepCopy() {
    try {
      ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
      try (ObjectOutputStream out = new ObjectOutputStream(byteOut)) {
        out.writeObject(this);
      }
      ByteArrayInputStream byteIn = new ByteArrayInputStream(byteOut.toByteArray());
      try (ObjectInputStream in = new ObjectInputStream(byteIn)) {
        return (ProcessSystem) in.readObject();
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to copy ProcessSystem", e);
    }
  }

  /**
   * <p>
   * getConditionMonitor.
   * </p>
   *
   * @return a {@link neqsim.process.conditionmonitor.ConditionMonitor} object
   */
  public ConditionMonitor getConditionMonitor() {
    return new ConditionMonitor(this);
  }

  /**
   * Check mass balance of all unit operations in the process system.
   *
   * @param unit unit for mass flow rate (e.g., "kg/sec", "kg/hr", "mole/sec")
   * @return a map with unit operation name as key and mass balance result as value
   */
  public Map<String, MassBalanceResult> checkMassBalance(String unit) {
    Map<String, MassBalanceResult> massBalanceResults = new HashMap<>();
    for (ProcessEquipmentInterface unitOp : unitOperations) {
      try {
        double massBalanceError = unitOp.getMassBalance(unit);
        double inletFlow = calculateInletFlow(unitOp, unit);
        double percentError = calculatePercentError(massBalanceError, inletFlow);
        massBalanceResults.put(unitOp.getName(),
            new MassBalanceResult(massBalanceError, percentError, unit));
      } catch (Exception e) {
        logger.warn("Failed to calculate mass balance for unit: " + unitOp.getName(), e);
        massBalanceResults.put(unitOp.getName(),
            new MassBalanceResult(Double.NaN, Double.NaN, unit));
      }
    }
    return massBalanceResults;
  }

  /**
   * Check mass balance of all unit operations in the process system using kg/sec.
   *
   * @return a map with unit operation name as key and mass balance result as value in kg/sec
   */
  public Map<String, MassBalanceResult> checkMassBalance() {
    return checkMassBalance("kg/sec");
  }

  /**
   * Get unit operations that failed mass balance check based on percentage error threshold.
   *
   * @param unit unit for mass flow rate (e.g., "kg/sec", "kg/hr", "mole/sec")
   * @param percentThreshold percentage error threshold (default: 0.1%)
   * @return a map with failed unit operation names and their mass balance results
   */
  public Map<String, MassBalanceResult> getFailedMassBalance(String unit, double percentThreshold) {
    Map<String, MassBalanceResult> allResults = checkMassBalance(unit);
    Map<String, MassBalanceResult> failedUnits = new HashMap<>();

    // Convert minimum flow threshold to the requested unit
    double minimumFlowInUnit = minimumFlowForMassBalanceError;
    if (unit.equals("kg/hr")) {
      minimumFlowInUnit *= 3600.0; // kg/sec to kg/hr
    } else if (unit.equals("mole/sec")) {
      // For mole/sec, we use the kg/sec threshold as approximation
      // since we don't have molecular weight info here
      minimumFlowInUnit = minimumFlowForMassBalanceError;
    }

    for (Map.Entry<String, MassBalanceResult> entry : allResults.entrySet()) {
      MassBalanceResult result = entry.getValue();
      ProcessEquipmentInterface unitOp = getUnit(entry.getKey());
      double inletFlow = calculateInletFlow(unitOp, unit);

      // Skip units with insignificant inlet flow
      if (Math.abs(inletFlow) < minimumFlowInUnit) {
        continue;
      }

      if (Double.isNaN(result.getPercentError())
          || Math.abs(result.getPercentError()) > percentThreshold) {
        failedUnits.put(entry.getKey(), result);
      }
    }
    return failedUnits;
  }

  /**
   * Get unit operations that failed mass balance check using kg/sec and default threshold.
   *
   * @return a map with failed unit operation names and their mass balance results
   */
  public Map<String, MassBalanceResult> getFailedMassBalance() {
    return getFailedMassBalance("kg/sec", massBalanceErrorThreshold);
  }

  /**
   * Get unit operations that failed mass balance check using specified threshold.
   *
   * @param percentThreshold percentage error threshold
   * @return a map with failed unit operation names and their mass balance results in kg/sec
   */
  public Map<String, MassBalanceResult> getFailedMassBalance(double percentThreshold) {
    return getFailedMassBalance("kg/sec", percentThreshold);
  }

  /**
   * Get a formatted mass balance report for this process system.
   *
   * @param unit unit for mass flow rate (e.g., "kg/sec", "kg/hr", "mole/sec")
   * @return a formatted string report with mass balance results
   */
  public String getMassBalanceReport(String unit) {
    StringBuilder report = new StringBuilder();
    report.append("Process: ").append(getName()).append("\n");
    report.append(String.format("%0" + 60 + "d", 0).replace('0', '=')).append("\n");

    Map<String, MassBalanceResult> results = checkMassBalance(unit);
    if (results.isEmpty()) {
      report.append("No unit operations found.\n");
    } else {
      for (Map.Entry<String, MassBalanceResult> entry : results.entrySet()) {
        report.append(String.format("  %-30s: %s\n", entry.getKey(), entry.getValue().toString()));
      }
    }
    return report.toString();
  }

  /**
   * Get a formatted mass balance report for this process system using kg/sec.
   *
   * @return a formatted string report with mass balance results
   */
  public String getMassBalanceReport() {
    return getMassBalanceReport("kg/sec");
  }

  /**
   * Get a formatted report of failed mass balance checks for this process system.
   *
   * @param unit unit for mass flow rate (e.g., "kg/sec", "kg/hr", "mole/sec")
   * @param percentThreshold percentage error threshold
   * @return a formatted string report with failed unit operations
   */
  public String getFailedMassBalanceReport(String unit, double percentThreshold) {
    StringBuilder report = new StringBuilder();
    Map<String, MassBalanceResult> failedResults = getFailedMassBalance(unit, percentThreshold);

    if (failedResults.isEmpty()) {
      report.append("All unit operations passed mass balance check.\n");
    } else {
      report.append("Process: ").append(getName()).append("\n");
      report.append(String.format("%0" + 60 + "d", 0).replace('0', '=')).append("\n");
      for (Map.Entry<String, MassBalanceResult> entry : failedResults.entrySet()) {
        report.append(String.format("  %-30s: %s\n", entry.getKey(), entry.getValue().toString()));
      }
    }
    return report.toString();
  }

  /**
   * Get a formatted report of failed mass balance checks for this process system using kg/sec and
   * default threshold.
   *
   * @return a formatted string report with failed unit operations
   */
  public String getFailedMassBalanceReport() {
    return getFailedMassBalanceReport("kg/sec", massBalanceErrorThreshold);
  }

  /**
   * Get a formatted report of failed mass balance checks for this process system using specified
   * threshold.
   *
   * @param percentThreshold percentage error threshold
   * @return a formatted string report with failed unit operations in kg/sec
   */
  public String getFailedMassBalanceReport(double percentThreshold) {
    return getFailedMassBalanceReport("kg/sec", percentThreshold);
  }

  /**
   * Set the default mass balance error threshold for this process system.
   *
   * @param percentThreshold percentage error threshold (e.g., 0.1 for 0.1%)
   */
  public void setMassBalanceErrorThreshold(double percentThreshold) {
    this.massBalanceErrorThreshold = percentThreshold;
  }

  /**
   * Get the default mass balance error threshold for this process system.
   *
   * @return percentage error threshold
   */
  public double getMassBalanceErrorThreshold() {
    return massBalanceErrorThreshold;
  }

  /**
   * Set the minimum flow threshold for mass balance error checking. Units with inlet flow below
   * this threshold are not considered errors.
   *
   * @param minimumFlow minimum flow in kg/sec (e.g., 1e-6)
   */
  public void setMinimumFlowForMassBalanceError(double minimumFlow) {
    this.minimumFlowForMassBalanceError = minimumFlow;
  }

  /**
   * Get the minimum flow threshold for mass balance error checking.
   *
   * @return minimum flow in kg/sec
   */
  public double getMinimumFlowForMassBalanceError() {
    return minimumFlowForMassBalanceError;
  }

  private double calculateInletFlow(ProcessEquipmentInterface unitOp, String unit) {
    try {
      // Try to get inlet flow from the unit operation's thermodynamic system
      if (unitOp.getThermoSystem() != null) {
        return unitOp.getThermoSystem().getFlowRate(unit);
      }
    } catch (Exception e) {
      // Ignore and return 0
    }
    return 0.0;
  }

  private double calculatePercentError(double massBalanceError, double inletFlow) {
    if (inletFlow == 0.0 || Double.isNaN(inletFlow) || Double.isNaN(massBalanceError)) {
      return Double.NaN;
    }
    return 100.0 * Math.abs(massBalanceError) / Math.abs(inletFlow);
  }

  /**
   * Inner class to hold mass balance results.
   */
  public static class MassBalanceResult {
    private final double absoluteError;
    private final double percentError;
    private final String unit;

    /**
     * Constructor for MassBalanceResult.
     *
     * @param absoluteError absolute mass balance error (outlet - inlet)
     * @param percentError percentage error
     * @param unit unit of measurement
     */
    public MassBalanceResult(double absoluteError, double percentError, String unit) {
      this.absoluteError = absoluteError;
      this.percentError = percentError;
      this.unit = unit;
    }

    /**
     * Get the absolute mass balance error.
     *
     * @return absolute error (outlet - inlet)
     */
    public double getAbsoluteError() {
      return absoluteError;
    }

    /**
     * Get the percentage error.
     *
     * @return percentage error
     */
    public double getPercentError() {
      return percentError;
    }

    /**
     * Get the unit of measurement.
     *
     * @return unit string
     */
    public String getUnit() {
      return unit;
    }

    @Override
    public String toString() {
      return String.format("%.6f %s (%.4f%%)", absoluteError, unit, percentError);
    }
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Objects.hash(alarmManager, measurementDevices, measurementHistory,
        name, recycleController, surroundingTemperature, time, timeStep, timeStepNumber,
        unitOperations);
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    ProcessSystem other = (ProcessSystem) obj;
    return Objects.equals(alarmManager, other.alarmManager)
        && Objects.equals(measurementDevices, other.measurementDevices)
        && Objects.equals(name, other.name)
        && Objects.equals(recycleController, other.recycleController)
        && Objects.equals(measurementHistory, other.measurementHistory)
        && Double.doubleToLongBits(surroundingTemperature) == Double
            .doubleToLongBits(other.surroundingTemperature)
        && Double.doubleToLongBits(time) == Double.doubleToLongBits(other.time)
        && Double.doubleToLongBits(timeStep) == Double.doubleToLongBits(other.timeStep)
        && timeStepNumber == other.timeStepNumber
        && Objects.equals(unitOperations, other.unitOperations);
  }

  /** {@inheritDoc} */
  @Override
  public String getReport_json() {
    return new Report(this).generateJsonReport();
  }

  /**
   * <p>
   * addUnit.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentType a {@link java.lang.String} object
   * @param <T> a T class
   * @return a T object
   */
  @SuppressWarnings("unchecked")
  public <T extends ProcessEquipmentInterface> T addUnit(String name, String equipmentType) {
    ProcessEquipmentInterface unit = EquipmentFactory.createEquipment(name, equipmentType);

    // If the provided name is null or empty, generate a unique name based on the
    // equipment type.
    if (name == null || name.trim().isEmpty()) {
      name = generateUniqueName(equipmentType);
    }

    unit.setName(name);

    // Auto-connect streams if possible.
    autoConnect(lastAddedUnit, unit);

    this.add(unit);
    lastAddedUnit = unit; // Update last added unit
    return (T) unit;
  }

  /**
   * <p>
   * addUnit.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentEnum a {@link neqsim.process.equipment.EquipmentEnum} object
   * @param <T> a T class
   * @return a T object
   */
  @SuppressWarnings("unchecked")
  public <T extends ProcessEquipmentInterface> T addUnit(String name, EquipmentEnum equipmentEnum) {
    return (T) addUnit(name, equipmentEnum.name());
  }

  // New overload: addUnit only with equipmentType String
  /**
   * <p>
   * addUnit.
   * </p>
   *
   * @param equipmentType a {@link java.lang.String} object
   * @param <T> a T class
   * @return a T object
   */
  @SuppressWarnings("unchecked")
  public <T extends ProcessEquipmentInterface> T addUnit(String equipmentType) {
    return (T) addUnit(null, equipmentType);
  }

  // New overload: addUnit only with EquipmentEnum
  /**
   * <p>
   * addUnit.
   * </p>
   *
   * @param equipmentEnum a {@link neqsim.process.equipment.EquipmentEnum} object
   * @param <T> a T class
   * @return a T object
   */
  @SuppressWarnings("unchecked")
  public <T extends ProcessEquipmentInterface> T addUnit(EquipmentEnum equipmentEnum) {
    return (T) addUnit(null, equipmentEnum);
  }

  /**
   * Adds a new process equipment unit of the specified type and name, and sets its inlet stream.
   *
   * @param <T> the type of process equipment
   * @param name the name of the equipment (if null or empty, a unique name is generated)
   * @param equipmentType the type of equipment to create (as a String)
   * @param stream the inlet stream to set for the new equipment
   * @return the created and added process equipment unit
   */
  public <T extends ProcessEquipmentInterface> T addUnit(String name, String equipmentType,
      neqsim.process.equipment.stream.StreamInterface stream) {
    ProcessEquipmentInterface unit = EquipmentFactory.createEquipment(name, equipmentType);

    if (name == null || name.trim().isEmpty()) {
      name = generateUniqueName(equipmentType);
    }
    unit.setName(name);

    // Set the inlet stream if possible
    try {
      java.lang.reflect.Method setInlet = unit.getClass().getMethod("setInletStream",
          neqsim.process.equipment.stream.StreamInterface.class);
      setInlet.invoke(unit, stream);
    } catch (NoSuchMethodException ignored) {
      // If the method does not exist, do nothing
    } catch (Exception e) {
      logger.error("Error setting inlet stream on equipment: " + e.getMessage(), e);
    }

    this.add(unit);
    lastAddedUnit = unit;
    return (T) unit;
  }

  /**
   * <p>
   * addUnit.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param equipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   * @return a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public ProcessEquipmentInterface addUnit(String name, ProcessEquipmentInterface equipment) {
    unitOperations.add(equipment);
    equipment.setName(name);
    lastAddedUnit = equipment;
    equipment.run();
    return equipment;
  }

  /**
   * <p>
   * addUnit.
   * </p>
   *
   * @param equipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   * @return a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public ProcessEquipmentInterface addUnit(ProcessEquipmentInterface equipment) {
    String generatedName = generateUniqueName(equipment.getClass().getSimpleName());
    return addUnit(generatedName, equipment);
  }

  private String generateUniqueName(String equipmentType) {
    int count = equipmentCounter.getOrDefault(equipmentType, 0) + 1;
    equipmentCounter.put(equipmentType, count);
    String formatted = equipmentType.substring(0, 1).toLowerCase() + equipmentType.substring(1);
    return formatted + "_" + count;
  }

  // --- Auto Connection (Outlet -> Inlet) ---

  private void autoConnect(ProcessEquipmentInterface fromUnit, ProcessEquipmentInterface toUnit) {
    if (fromUnit == null) {
      return;
    }
    fromUnit.run();
    try {
      java.lang.reflect.Method getOutlet = fromUnit.getClass().getMethod("getOutletStream");
      Object outletStream = getOutlet.invoke(fromUnit);

      if (outletStream != null) {
        java.lang.reflect.Method setInlet = toUnit.getClass().getMethod("setInletStream",
            neqsim.process.equipment.stream.StreamInterface.class);
        setInlet.invoke(toUnit, outletStream);
      }
    } catch (NoSuchMethodException ignored) {
    } catch (Exception e) {
      logger.error("Error in autoConnect: " + e.getMessage(), e);
    }
  }

  private static final class MeasurementHistory implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private int maxSize;
    private Deque<String[]> entries = new LinkedList<>();

    void add(String[] entry) {
      if (entry == null) {
        throw new IllegalArgumentException("History entry cannot be null");
      }
      if (maxSize > 0 && entries.size() >= maxSize) {
        entries.removeFirst();
      }
      entries.addLast(Arrays.copyOf(entry, entry.length));
    }

    void clear() {
      entries.clear();
    }

    int size() {
      return entries.size();
    }

    int getMaxSize() {
      return maxSize;
    }

    void setMaxSize(int maxSize) {
      this.maxSize = maxSize;
      if (maxSize > 0) {
        while (entries.size() > maxSize) {
          entries.removeFirst();
        }
      }
    }

    MeasurementHistory copy() {
      MeasurementHistory copy = new MeasurementHistory();
      copy.maxSize = maxSize;
      for (String[] entry : entries) {
        copy.entries.addLast(Arrays.copyOf(entry, entry.length));
      }
      return copy;
    }

    String[][] toArray() {
      String[][] array = new String[entries.size()][];
      int index = 0;
      for (String[] entry : entries) {
        array[index++] = Arrays.copyOf(entry, entry.length);
      }
      return array;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + maxSize;
      result = prime * result + Arrays.deepHashCode(toArray());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      MeasurementHistory other = (MeasurementHistory) obj;
      return maxSize == other.maxSize && Arrays.deepEquals(toArray(), other.toArray());
    }
  }

  /**
   * <p>
   * exportToGraphviz.
   * </p>
   *
   * @param filename a {@link java.lang.String} object
   */
  public void exportToGraphviz(String filename) {
    new ProcessSystemGraphvizExporter().export(this, filename);
  }

  /**
   * Export the process to Graphviz with configurable stream annotations.
   *
   * @param filename the Graphviz output file
   * @param options export options controlling stream annotations and table output
   */
  public void exportToGraphviz(String filename,
      ProcessSystemGraphvizExporter.GraphvizExportOptions options) {
    new ProcessSystemGraphvizExporter().export(this, filename, options);
  }

  /**
   * Load a process from a YAML file.
   *
   * @param yamlFile the YAML file to load
   */
  public void loadProcessFromYaml(File yamlFile) {
    try {
      neqsim.process.processmodel.ProcessLoader.loadProcessFromYaml(yamlFile, this);
    } catch (Exception e) {
      logger.error("Error loading process from YAML file", e);
    }
  }

  /**
   * Builds a ProcessSystem from a JSON process definition string.
   *
   * <p>
   * This is the primary entry point for web API and Python integration. Accepts a declarative JSON
   * definition of fluids, equipment, and stream connections and returns a structured result with
   * the built ProcessSystem or detailed error information.
   * </p>
   *
   * @param json the JSON process definition string
   * @return a SimulationResult containing the built process or errors
   * @see JsonProcessBuilder
   */
  public static SimulationResult fromJson(String json) {
    return new JsonProcessBuilder().build(json);
  }

  /**
   * Exports this ProcessSystem to the JSON schema consumed by {@link JsonProcessBuilder}.
   *
   * <p>
   * The exported JSON is round-trippable: the output can be fed back into {@link #fromJson(String)}
   * to reconstruct an equivalent ProcessSystem. This enables exporting NeqSim process models to
   * external simulators (e.g., UniSim Design via COM automation).
   * </p>
   *
   * @return JSON string representing this process system
   * @see JsonProcessExporter
   * @see JsonProcessBuilder
   */
  public String toJson() {
    return new JsonProcessExporter().toJson(this);
  }

  /**
   * Builds and immediately runs a ProcessSystem from a JSON definition.
   *
   * <p>
   * Convenience method that combines building and execution in a single call. The result contains
   * the full simulation report JSON.
   * </p>
   *
   * @param json the JSON process definition string
   * @return a SimulationResult containing the executed process and report, or errors
   * @see JsonProcessBuilder#buildAndRun(String)
   */
  public static SimulationResult fromJsonAndRun(String json) {
    return JsonProcessBuilder.buildAndRun(json);
  }

  /**
   * Resolves a named stream reference within this process system.
   *
   * <p>
   * Supports dot-notation for specific outlet ports:
   * <ul>
   * <li>"unitName" — default outlet stream</li>
   * <li>"unitName.gasOut" — gas outlet of separator</li>
   * <li>"unitName.liquidOut" — liquid outlet of separator</li>
   * <li>"unitName.outlet" — explicit outlet stream</li>
   * </ul>
   *
   * @param ref the stream reference (e.g., "feed", "HP Sep.gasOut")
   * @return the resolved StreamInterface, or null if not found
   */
  public StreamInterface resolveStreamReference(String ref) {
    if (ref == null || ref.trim().isEmpty()) {
      return null;
    }

    String unitName;
    String port = "outlet";

    if (ref.contains(".")) {
      String[] parts = ref.split("\\.", 2);
      unitName = parts[0];
      port = parts[1].toLowerCase();
    } else {
      unitName = ref;
    }

    ProcessEquipmentInterface unit = getUnit(unitName);
    if (unit == null) {
      return null;
    }

    // If the unit is a Stream, return it directly
    if (unit instanceof StreamInterface) {
      return (StreamInterface) unit;
    }

    try {
      switch (port) {
        case "gasout":
        case "gas":
          return (StreamInterface) unit.getClass().getMethod("getGasOutStream").invoke(unit);
        case "liquidout":
        case "liquid":
          return (StreamInterface) unit.getClass().getMethod("getLiquidOutStream").invoke(unit);
        case "oilout":
        case "oil":
          return (StreamInterface) unit.getClass().getMethod("getOilOutStream").invoke(unit);
        case "waterout":
        case "water":
          return (StreamInterface) unit.getClass().getMethod("getWaterOutStream").invoke(unit);
        case "outlet":
        default:
          // Handle indexed split streams: "split0", "split1", etc.
          if (port.startsWith("split") && port.length() > 5) {
            try {
              int idx = Integer.parseInt(port.substring(5));
              return (StreamInterface) unit.getClass().getMethod("getSplitStream", int.class)
                  .invoke(unit, idx);
            } catch (NumberFormatException nfe) {
              // fall through to default outlet
            }
          }
          // Handle HeatExchanger which uses getOutStream(int) instead of getOutletStream()
          if (unit instanceof HeatExchanger) {
            return ((HeatExchanger) unit).getOutStream(0);
          }
          return (StreamInterface) unit.getClass().getMethod("getOutletStream").invoke(unit);
      }
    } catch (NoSuchMethodException e) {
      // Fallback chain: getOutStream(int) -> getOutletStreams().get(0) -> getOutStream()
      try {
        return (StreamInterface) unit.getClass().getMethod("getOutStream", int.class).invoke(unit,
            0);
      } catch (Exception ex2) {
        try {
          List<StreamInterface> outlets = unit.getOutletStreams();
          if (outlets != null && !outlets.isEmpty()) {
            return outlets.get(0);
          }
        } catch (Exception ex3) {
          // ignore
        }
        try {
          return (StreamInterface) unit.getClass().getMethod("getOutStream").invoke(unit);
        } catch (Exception ex4) {
          return null;
        }
      }
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Wires an inlet stream from a named reference to a target equipment unit.
   *
   * <p>
   * Resolves the stream reference by name and connects it to the target unit.
   * </p>
   *
   * @param targetUnitName the name of the equipment to wire the inlet to
   * @param sourceRef the stream reference (e.g., "feed", "HP Sep.gasOut")
   * @return true if wiring succeeded, false if source or target not found
   */
  public boolean wireStream(String targetUnitName, String sourceRef) {
    ProcessEquipmentInterface target = getUnit(targetUnitName);
    if (target == null) {
      logger.warn("wireStream: target unit '{}' not found", targetUnitName);
      return false;
    }

    StreamInterface stream = resolveStreamReference(sourceRef);
    if (stream == null) {
      logger.warn("wireStream: source reference '{}' could not be resolved", sourceRef);
      return false;
    }

    try {
      java.lang.reflect.Method setInlet =
          target.getClass().getMethod("setInletStream", StreamInterface.class);
      setInlet.invoke(target, stream);
      return true;
    } catch (NoSuchMethodException e) {
      try {
        java.lang.reflect.Method addStream =
            target.getClass().getMethod("addStream", StreamInterface.class);
        addStream.invoke(target, stream);
        return true;
      } catch (Exception ex) {
        logger.warn("wireStream: cannot set inlet on '{}': {}", targetUnitName, ex.getMessage());
        return false;
      }
    } catch (Exception e) {
      logger.warn("wireStream: error wiring '{}' to '{}': {}", sourceRef, targetUnitName,
          e.getMessage());
      return false;
    }
  }

  /**
   * <p>
   * getBottleneck.
   * </p>
   *
   * <p>
   * Identifies the equipment with the highest capacity utilization. This method checks both:
   * </p>
   * <ul>
   * <li>Traditional capacity: {@code getCapacityDuty() / getCapacityMax()}</li>
   * <li>Multi-constraint capacity: Equipment implementing
   * {@link neqsim.process.equipment.capacity.CapacityConstrainedEquipment}</li>
   * </ul>
   *
   * @return a {@link neqsim.process.equipment.ProcessEquipmentInterface} object representing the
   *         bottleneck, or null if no equipment has capacity defined
   */
  public ProcessEquipmentInterface getBottleneck() {
    ProcessEquipmentInterface bottleneck = null;
    double maxUtilization = 0.0;

    for (ProcessEquipmentInterface unit : unitOperations) {
      double utilization = 0.0;

      // Check if equipment implements CapacityConstrainedEquipment (multi-constraint)
      if (unit instanceof neqsim.process.equipment.capacity.CapacityConstrainedEquipment) {
        neqsim.process.equipment.capacity.CapacityConstrainedEquipment constrained =
            (neqsim.process.equipment.capacity.CapacityConstrainedEquipment) unit;
        utilization = constrained.getMaxUtilization();
      } else {
        // Fall back to traditional single capacity metric
        double capacity = unit.getCapacityMax();
        double duty = unit.getCapacityDuty();
        if (capacity > 1e-12) {
          utilization = duty / capacity;
        }
      }

      if (!Double.isNaN(utilization) && !Double.isInfinite(utilization)
          && utilization > maxUtilization) {
        maxUtilization = utilization;
        bottleneck = unit;
      }
    }
    return bottleneck;
  }

  /**
   * Gets the utilization ratio of the bottleneck equipment.
   *
   * @return utilization as fraction (1.0 = 100%), or 0.0 if no bottleneck found
   */
  public double getBottleneckUtilization() {
    ProcessEquipmentInterface bottleneck = getBottleneck();
    if (bottleneck == null) {
      return 0.0;
    }

    if (bottleneck instanceof neqsim.process.equipment.capacity.CapacityConstrainedEquipment) {
      return ((neqsim.process.equipment.capacity.CapacityConstrainedEquipment) bottleneck)
          .getMaxUtilization();
    }

    double capacity = bottleneck.getCapacityMax();
    if (capacity > 1e-12) {
      return bottleneck.getCapacityDuty() / capacity;
    }
    return 0.0;
  }

  // ============ GRAPH-BASED PROCESS REPRESENTATION ============

  /**
   * Builds an explicit graph representation of this process system.
   *
   * <p>
   * The graph representation enables:
   * <ul>
   * <li>Automatic detection of calculation order (derived from topology, not insertion order)</li>
   * <li>Partitioning for parallel execution</li>
   * <li>AI agents to reason about flowsheet structure</li>
   * <li>Cycle detection for recycle handling</li>
   * <li>Graph neural network compatible representation</li>
   * </ul>
   *
   * <p>
   * Example usage:
   *
   * <pre>
   * ProcessSystem system = new ProcessSystem();
   * // ... add units ...
   * ProcessGraph graph = system.buildGraph();
   *
   * // Get topology-based calculation order
   * List&lt;ProcessEquipmentInterface&gt; order = graph.getCalculationOrder();
   *
   * // Partition for parallel execution
   * ProcessGraph.ParallelPartition partition = graph.partitionForParallelExecution();
   * </pre>
   *
   * @return the process graph
   * @see ProcessGraph
   * @see ProcessGraphBuilder
   */
  public ProcessGraph buildGraph() {
    if (cachedGraph == null || graphDirty) {
      cachedGraph = ProcessGraphBuilder.buildGraph(this);
      graphDirty = false;
    }
    return cachedGraph;
  }

  /**
   * Forces a rebuild of the process graph on next access.
   *
   * <p>
   * Use this method when you have made structural changes to the process that the automatic
   * detection may have missed (e.g., modifying stream connections directly).
   * </p>
   */
  public void invalidateGraph() {
    graphDirty = true;
    cachedGraph = null;
    cachedParallelPlan = null;
    cachedHasAdjusters = null;
    cachedHasRecycles = null;
  }

  /**
   * Sets whether to use graph-based execution order.
   *
   * <p>
   * When enabled, the run() method will execute units in topological order derived from stream
   * connections rather than the order units were added. This can be safer when unit insertion order
   * doesn't match the physical flow.
   * </p>
   *
   * @param useGraphBased true to use topological execution order, false to use insertion order
   */
  public void setUseGraphBasedExecution(boolean useGraphBased) {
    this.useGraphBasedExecution = useGraphBased;
  }

  /**
   * Returns whether graph-based execution order is enabled.
   *
   * @return true if using topological execution order
   */
  public boolean isUseGraphBasedExecution() {
    return useGraphBasedExecution;
  }

  /**
   * Sets whether to use optimized execution (parallel/hybrid) by default when run() is called.
   *
   * <p>
   * When enabled (default), run() automatically selects the best execution strategy:
   * </p>
   * <ul>
   * <li>For processes WITHOUT recycles: parallel execution for maximum speed (28-57% faster)</li>
   * <li>For processes WITH recycles: hybrid execution - parallel for feed-forward sections, then
   * iterative for recycle sections (28-38% faster)</li>
   * </ul>
   *
   * <p>
   * When disabled, run() uses sequential execution in insertion order (legacy behavior). This may
   * be useful for debugging or when deterministic single-threaded execution is required.
   * </p>
   *
   * @param useOptimized true to use optimized execution (default), false for sequential execution
   */
  public void setUseOptimizedExecution(boolean useOptimized) {
    this.useOptimizedExecution = useOptimized;
  }

  /**
   * Returns whether optimized execution is enabled.
   *
   * <p>
   * When true (default), run() delegates to runOptimized() which automatically selects the best
   * execution strategy based on process topology.
   * </p>
   *
   * @return true if optimized execution is enabled
   */
  public boolean isUseOptimizedExecution() {
    return useOptimizedExecution;
  }

  /**
   * Gets the calculation order derived from process topology.
   *
   * <p>
   * This method returns units in the order they should be calculated based on stream connections,
   * not the order they were added to the ProcessSystem. This is safer than relying on insertion
   * order, which can lead to wrong results if units are rearranged or recycles are added late.
   * </p>
   *
   * @return list of equipment in topology-derived calculation order
   */
  public List<ProcessEquipmentInterface> getTopologicalOrder() {
    ProcessGraph graph = buildGraph();
    return graph.getCalculationOrder();
  }

  /**
   * Checks if the process has recycle loops that require iterative solving.
   *
   * @return true if the process contains cycles (recycles)
   */
  public boolean hasRecycleLoops() {
    ProcessGraph graph = buildGraph();
    return graph.hasCycles();
  }

  /**
   * Gets the number of levels for parallel execution.
   *
   * <p>
   * Units at the same level have no dependencies on each other and can be executed in parallel.
   * </p>
   *
   * @return number of parallel execution levels
   */
  public int getParallelLevelCount() {
    ProcessGraph graph = buildGraph();
    return graph.partitionForParallelExecution().getLevelCount();
  }

  /**
   * Gets the maximum parallelism (max units that can run simultaneously).
   *
   * @return maximum number of units that can execute in parallel
   */
  public int getMaxParallelism() {
    ProcessGraph graph = buildGraph();
    return graph.partitionForParallelExecution().getMaxParallelism();
  }

  /**
   * Validates the process structure and returns any issues found.
   *
   * <p>
   * Checks include:
   * <ul>
   * <li>Isolated units (no connections)</li>
   * <li>Duplicate edges</li>
   * <li>Self-loops</li>
   * <li>Unhandled cycles</li>
   * </ul>
   *
   * @return list of validation issues (empty if valid)
   */
  public List<String> validateStructure() {
    ProcessGraph graph = buildGraph();
    return graph.validate();
  }

  /**
   * Gets a summary of the process graph structure.
   *
   * @return summary string with node/edge counts, cycles, parallelism info
   */
  public String getGraphSummary() {
    ProcessGraph graph = buildGraph();
    return graph.getSummary();
  }

  /**
   * Gets the strongly connected components (SCCs) in the process graph.
   *
   * <p>
   * SCCs with more than one unit represent recycle loops that require iterative convergence. This
   * method uses Tarjan's algorithm to identify these components.
   * </p>
   *
   * @return list of SCCs, each containing a list of equipment in that component
   */
  public List<List<ProcessEquipmentInterface>> getRecycleBlocks() {
    ProcessGraph graph = buildGraph();
    ProcessGraph.SCCResult sccResult = graph.findStronglyConnectedComponents();
    List<List<ProcessNode>> recycleLoops = sccResult.getRecycleLoops();

    // Convert from ProcessNode lists to ProcessEquipmentInterface lists
    List<List<ProcessEquipmentInterface>> recycleBlocks = new java.util.ArrayList<>();
    for (List<ProcessNode> loop : recycleLoops) {
      List<ProcessEquipmentInterface> block = new java.util.ArrayList<>();
      for (ProcessNode node : loop) {
        block.add(node.getEquipment());
      }
      recycleBlocks.add(block);
    }
    return recycleBlocks;
  }

  /**
   * Gets the number of recycle blocks (cycles) in the process.
   *
   * @return number of strongly connected components with more than one unit
   */
  public int getRecycleBlockCount() {
    return getRecycleBlocks().size();
  }

  /**
   * Checks if a specific unit is part of a recycle loop.
   *
   * @param unit the unit to check
   * @return true if the unit is in a recycle block
   */
  public boolean isInRecycleLoop(ProcessEquipmentInterface unit) {
    for (List<ProcessEquipmentInterface> block : getRecycleBlocks()) {
      if (block.contains(unit)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gets a diagnostic report of recycle blocks for debugging.
   *
   * @return formatted string describing each recycle block
   */
  public String getRecycleBlockReport() {
    List<List<ProcessEquipmentInterface>> blocks = getRecycleBlocks();
    if (blocks.isEmpty()) {
      return "No recycle blocks detected in process.";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("Recycle Blocks Report\n");
    sb.append("=====================\n");
    sb.append("Total recycle blocks: ").append(blocks.size()).append("\n\n");

    int blockNum = 1;
    for (List<ProcessEquipmentInterface> block : blocks) {
      sb.append("Block ").append(blockNum++).append(" (").append(block.size()).append(" units):\n");
      for (ProcessEquipmentInterface unit : block) {
        sb.append("  - ").append(unit.getName()).append(" [")
            .append(unit.getClass().getSimpleName()).append("]\n");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  // ============ DIGITAL TWIN LIFECYCLE & SUSTAINABILITY ============

  /**
   * Exports the current state of this process system for checkpointing or versioning.
   *
   * <p>
   * This method captures all equipment states, fluid compositions, and operating conditions into a
   * serializable format that can be saved to disk, stored in a database, or used for model
   * versioning in digital twin applications.
   * </p>
   *
   * <p>
   * Example usage:
   *
   * <pre>
   * ProcessSystem system = new ProcessSystem();
   * // ... configure and run ...
   * ProcessSystemState state = system.exportState();
   * state.saveToFile("checkpoint_v1.json");
   * </pre>
   *
   * @return the current state as a serializable object
   * @see neqsim.process.processmodel.lifecycle.ProcessSystemState
   */
  public neqsim.process.processmodel.lifecycle.ProcessSystemState exportState() {
    return neqsim.process.processmodel.lifecycle.ProcessSystemState.fromProcessSystem(this);
  }

  /**
   * Exports the current state to a JSON file for versioning or backup.
   *
   * @param filename the file path to save the state
   */
  public void exportStateToFile(String filename) {
    exportState().saveToFile(filename);
  }

  /**
   * Loads process state from a JSON file and applies it to this system.
   *
   * <p>
   * Note: This method updates equipment states but does not recreate equipment. The process
   * structure must match the saved state.
   * </p>
   *
   * @param filename the file path to load state from
   */
  public void loadStateFromFile(String filename) {
    neqsim.process.processmodel.lifecycle.ProcessSystemState state =
        neqsim.process.processmodel.lifecycle.ProcessSystemState.loadFromFile(filename);
    if (state != null) {
      state.applyTo(this);
    }
  }

  // ============ NEQSIM FILE SERIALIZATION ============

  /**
   * Saves this process system to a compressed .neqsim file using XStream serialization.
   *
   * <p>
   * This is the recommended format for production use, providing compact storage with full process
   * state preservation. The file can be loaded with {@link #loadFromNeqsim(String)}.
   * </p>
   *
   * <p>
   * Example usage:
   *
   * <pre>
   * ProcessSystem process = new ProcessSystem();
   * // ... configure and run ...
   * process.saveToNeqsim("my_model.neqsim");
   * </pre>
   *
   * @param filename the file path to save to (recommended extension: .neqsim)
   * @return true if save was successful, false otherwise
   */
  public boolean saveToNeqsim(String filename) {
    return neqsim.util.serialization.NeqSimXtream.saveNeqsim(this, filename);
  }

  /**
   * Loads a process system from a compressed .neqsim file.
   *
   * <p>
   * After loading, the process is automatically run to reinitialize calculations. This ensures the
   * internal state is consistent.
   * </p>
   *
   * <p>
   * Example usage:
   *
   * <pre>
   * ProcessSystem loaded = ProcessSystem.loadFromNeqsim("my_model.neqsim");
   * // Process is already run and ready to use
   * Stream outlet = (Stream) loaded.getUnit("outlet");
   * </pre>
   *
   * @param filename the file path to load from
   * @return the loaded ProcessSystem, or null if loading fails
   */
  public static ProcessSystem loadFromNeqsim(String filename) {
    try {
      Object loaded = neqsim.util.serialization.NeqSimXtream.openNeqsim(filename);
      if (loaded instanceof ProcessSystem) {
        ProcessSystem process = (ProcessSystem) loaded;
        process.run();
        return process;
      } else {
        logger.error("Loaded object is not a ProcessSystem: "
            + (loaded != null ? loaded.getClass().getName() : "null"));
        return null;
      }
    } catch (java.io.IOException e) {
      logger.error("Failed to load process from file: " + filename, e);
      return null;
    }
  }

  /**
   * Saves the current state to a file with automatic format detection.
   *
   * <p>
   * File format is determined by extension:
   * <ul>
   * <li>.neqsim → XStream compressed XML (full serialization)</li>
   * <li>.json → JSON state (lightweight, Git-friendly)</li>
   * <li>other → Java binary serialization (legacy)</li>
   * </ul>
   *
   * @param filename the file path to save to
   * @return true if save was successful
   */
  public boolean saveAuto(String filename) {
    String lowerName = filename.toLowerCase();
    if (lowerName.endsWith(".neqsim")) {
      return saveToNeqsim(filename);
    } else if (lowerName.endsWith(".json")) {
      try {
        exportStateToFile(filename);
        return true;
      } catch (Exception e) {
        logger.error("Failed to save JSON state: " + filename, e);
        return false;
      }
    } else {
      try {
        save(filename);
        return true;
      } catch (Exception e) {
        logger.error("Failed to save process: " + filename, e);
        return false;
      }
    }
  }

  /**
   * Loads a process system from a file with automatic format detection.
   *
   * <p>
   * File format is determined by extension:
   * <ul>
   * <li>.neqsim → XStream compressed XML</li>
   * <li>other → Java binary serialization (legacy)</li>
   * </ul>
   *
   * @param filename the file path to load from
   * @return the loaded ProcessSystem, or null if loading fails
   */
  public static ProcessSystem loadAuto(String filename) {
    String lowerName = filename.toLowerCase();
    if (lowerName.endsWith(".neqsim")) {
      return loadFromNeqsim(filename);
    } else {
      return open(filename);
    }
  }

  /**
   * Calculates current emissions from all equipment in this process system.
   *
   * <p>
   * Tracks CO2-equivalent emissions from:
   * <ul>
   * <li>Flares (combustion emissions)</li>
   * <li>Furnaces and burners</li>
   * <li>Compressors (electricity-based, using grid emission factor)</li>
   * <li>Pumps (electricity-based)</li>
   * <li>Heaters and coolers (if fuel-fired or electric)</li>
   * </ul>
   *
   * <p>
   * Example usage:
   *
   * <pre>
   * ProcessSystem system = new ProcessSystem();
   * // ... configure and run ...
   * EmissionsReport report = system.getEmissions();
   * System.out.println("Total CO2e: " + report.getTotalCO2e("kg/hr") + " kg/hr");
   * report.exportToCSV("emissions_report.csv");
   * </pre>
   *
   * @return emissions report with breakdown by equipment and category
   * @see neqsim.process.sustainability.EmissionsTracker
   */
  public neqsim.process.sustainability.EmissionsTracker.EmissionsReport getEmissions() {
    neqsim.process.sustainability.EmissionsTracker tracker =
        new neqsim.process.sustainability.EmissionsTracker(this);
    return tracker.calculateEmissions();
  }

  /**
   * Calculates emissions using a custom grid emission factor.
   *
   * <p>
   * Different regions have different electricity grid carbon intensities. Use this method to apply
   * location-specific emission factors.
   * </p>
   *
   * @param gridEmissionFactor kg CO2 per kWh of electricity (e.g., 0.05 for Norway, 0.4 for global
   *        average)
   * @return emissions report with equipment breakdown
   */
  public neqsim.process.sustainability.EmissionsTracker.EmissionsReport getEmissions(
      double gridEmissionFactor) {
    neqsim.process.sustainability.EmissionsTracker tracker =
        new neqsim.process.sustainability.EmissionsTracker(this);
    tracker.setGridEmissionFactor(gridEmissionFactor);
    return tracker.calculateEmissions();
  }

  /**
   * Gets total CO2-equivalent emissions from this process in kg/hr.
   *
   * <p>
   * This is a convenience method for quick emission checks. For detailed breakdown, use
   * {@link #getEmissions()}.
   * </p>
   *
   * @return total CO2e emissions in kg/hr
   */
  public double getTotalCO2Emissions() {
    return getEmissions().getTotalCO2e("kg/hr");
  }

  // ============ BATCH STUDY & OPTIMIZATION ============

  /**
   * Creates a batch study builder for running parallel parameter studies on this process.
   *
   * <p>
   * Batch studies allow exploring the design space by running many variations of this process in
   * parallel. Useful for concept screening, sensitivity analysis, and optimization.
   * </p>
   *
   * <p>
   * Example usage:
   *
   * <pre>
   * BatchStudy study =
   *     system.createBatchStudy().addParameter("separator1", "pressure", 30.0, 50.0, 70.0)
   *         .addParameter("compressor1", "outletPressure", 80.0, 100.0, 120.0)
   *         .addObjective("totalPower", true) // minimize
   *         .withParallelism(4).build();
   * BatchStudyResult result = study.run();
   * </pre>
   *
   * @return a new batch study builder configured for this process
   * @see neqsim.process.util.optimizer.BatchStudy
   */
  public neqsim.process.util.optimizer.BatchStudy.Builder createBatchStudy() {
    return neqsim.process.util.optimizer.BatchStudy.builder(this);
  }

  // ============ SAFETY SCENARIO GENERATION ============

  /**
   * Generates automatic safety scenarios based on equipment failure modes.
   *
   * <p>
   * This method analyzes the process structure and generates scenarios for common failure modes
   * such as:
   * <ul>
   * <li>Cooling system failure</li>
   * <li>Valve stuck open/closed</li>
   * <li>Compressor/pump trips</li>
   * <li>Power failure</li>
   * <li>Blocked outlets</li>
   * </ul>
   *
   * <p>
   * Example usage:
   *
   * <pre>
   * List&lt;ProcessSafetyScenario&gt; scenarios = system.generateSafetyScenarios();
   * for (ProcessSafetyScenario scenario : scenarios) {
   *   scenario.applyTo(system.copy());
   *   system.run();
   *   // Check for dangerous conditions
   * }
   * </pre>
   *
   * @return list of safety scenarios for this process
   * @see neqsim.process.safety.scenario.AutomaticScenarioGenerator
   */
  public List<neqsim.process.safety.ProcessSafetyScenario> generateSafetyScenarios() {
    neqsim.process.safety.scenario.AutomaticScenarioGenerator generator =
        new neqsim.process.safety.scenario.AutomaticScenarioGenerator(this);
    return generator.enableAllFailureModes().generateSingleFailures();
  }

  /**
   * Generates combination failure scenarios (multiple simultaneous failures).
   *
   * <p>
   * This is useful for analyzing cascading failures and common-cause scenarios.
   * </p>
   *
   * @param maxSimultaneousFailures maximum number of failures to combine (2-3 recommended)
   * @return list of combination scenarios
   */
  public List<neqsim.process.safety.ProcessSafetyScenario> generateCombinationScenarios(
      int maxSimultaneousFailures) {
    neqsim.process.safety.scenario.AutomaticScenarioGenerator generator =
        new neqsim.process.safety.scenario.AutomaticScenarioGenerator(this);
    return generator.enableAllFailureModes().generateCombinations(maxSimultaneousFailures);
  }

  // ============ DIAGRAM EXPORT ============

  /**
   * Exports the process as a DOT format diagram string.
   *
   * <p>
   * Generates a professional oil &amp; gas style process flow diagram (PFD) following industry
   * conventions:
   * </p>
   * <ul>
   * <li>Gravity logic - Gas equipment at top, liquid at bottom</li>
   * <li>Phase-aware styling - Streams colored by vapor/liquid fraction</li>
   * <li>Separator semantics - Gas exits top, liquid exits bottom</li>
   * <li>Equipment shapes matching P&amp;ID symbols</li>
   * </ul>
   *
   * <p>
   * Example usage:
   * </p>
   *
   * <pre>
   * String dot = process.toDOT();
   * Files.writeString(Path.of("process.dot"), dot);
   * // Render with: dot -Tsvg process.dot -o process.svg
   * </pre>
   *
   * @return Graphviz DOT format string
   * @see neqsim.process.processmodel.diagram.ProcessDiagramExporter
   */
  public String toDOT() {
    return new neqsim.process.processmodel.diagram.ProcessDiagramExporter(this).toDOT();
  }

  /**
   * Exports the process as a DOT format diagram with specified detail level.
   *
   * @param detailLevel the level of detail to include (CONCEPTUAL, ENGINEERING, DEBUG)
   * @return Graphviz DOT format string
   * @see neqsim.process.processmodel.diagram.DiagramDetailLevel
   */
  public String toDOT(neqsim.process.processmodel.diagram.DiagramDetailLevel detailLevel) {
    return new neqsim.process.processmodel.diagram.ProcessDiagramExporter(this)
        .setDetailLevel(detailLevel).toDOT();
  }

  /**
   * Creates a diagram exporter for this process with full configuration options.
   *
   * <p>
   * Example usage:
   * </p>
   *
   * <pre>
   * process.createDiagramExporter().setTitle("Gas Processing Plant")
   *     .setDetailLevel(DiagramDetailLevel.ENGINEERING).setVerticalLayout(true)
   *     .exportSVG(Path.of("diagram.svg"));
   * </pre>
   *
   * @return a new ProcessDiagramExporter configured for this process
   * @see neqsim.process.processmodel.diagram.ProcessDiagramExporter
   */
  public neqsim.process.processmodel.diagram.ProcessDiagramExporter createDiagramExporter() {
    return new neqsim.process.processmodel.diagram.ProcessDiagramExporter(this);
  }

  /**
   * Exports the process diagram to SVG format.
   *
   * <p>
   * Requires Graphviz (dot) to be installed and available in PATH.
   * </p>
   *
   * @param path the output file path
   * @throws java.io.IOException if export fails
   */
  public void exportDiagramSVG(java.nio.file.Path path) throws java.io.IOException {
    new neqsim.process.processmodel.diagram.ProcessDiagramExporter(this).exportSVG(path);
  }

  /**
   * Exports the process diagram to PNG format.
   *
   * <p>
   * Requires Graphviz (dot) to be installed and available in PATH.
   * </p>
   *
   * @param path the output file path
   * @throws java.io.IOException if export fails
   */
  public void exportDiagramPNG(java.nio.file.Path path) throws java.io.IOException {
    new neqsim.process.processmodel.diagram.ProcessDiagramExporter(this).exportPNG(path);
  }

  // ==================== Capacity Constraint Methods ====================

  /**
   * Enables or disables capacity analysis for all equipment in this process system.
   *
   * <p>
   * This is a convenience method that applies the setting to all equipment that extends
   * {@link ProcessEquipmentBaseClass}. When disabled, equipment is excluded from:
   * <ul>
   * <li>System bottleneck detection ({@code findBottleneck()})</li>
   * <li>Capacity utilization summaries ({@code getCapacityUtilizationSummary()})</li>
   * <li>Equipment near capacity lists ({@code getEquipmentNearCapacityLimit()})</li>
   * <li>Optimization constraint checking</li>
   * </ul>
   *
   * @param enabled true to enable capacity analysis for all equipment, false to disable
   * @return the number of equipment items that were updated
   */
  public int setCapacityAnalysisEnabled(boolean enabled) {
    int count = 0;
    for (ProcessEquipmentInterface unit : unitOperations) {
      if (unit instanceof ProcessEquipmentBaseClass) {
        ((ProcessEquipmentBaseClass) unit).setCapacityAnalysisEnabled(enabled);
        count++;
      }
    }
    return count;
  }

  /**
   * Gets all capacity-constrained equipment in the process.
   *
   * <p>
   * Returns equipment that implements the CapacityConstrainedEquipment interface, such as
   * separators, compressors, pumps, etc.
   * </p>
   *
   * @return list of capacity-constrained equipment
   */
  public java.util.List<neqsim.process.equipment.capacity.CapacityConstrainedEquipment> getConstrainedEquipment() {
    java.util.List<neqsim.process.equipment.capacity.CapacityConstrainedEquipment> result =
        new java.util.ArrayList<>();
    for (ProcessEquipmentInterface unit : unitOperations) {
      if (unit instanceof neqsim.process.equipment.capacity.CapacityConstrainedEquipment) {
        result.add((neqsim.process.equipment.capacity.CapacityConstrainedEquipment) unit);
      }
    }
    return result;
  }

  /**
   * Finds the process bottleneck with detailed constraint information.
   *
   * <p>
   * This method extends {@link #getBottleneck()} by returning detailed information about which
   * specific constraint is limiting the bottleneck equipment. Only works for equipment that
   * implements {@link neqsim.process.equipment.capacity.CapacityConstrainedEquipment}.
   * </p>
   *
   * <p>
   * For simple bottleneck detection without constraint details, use {@link #getBottleneck()}.
   * </p>
   *
   * @return BottleneckResult containing the bottleneck equipment, limiting constraint, and
   *         utilization; returns empty result if no constrained equipment found
   * @see #getBottleneck()
   */
  public neqsim.process.equipment.capacity.BottleneckResult findBottleneck() {
    neqsim.process.equipment.capacity.CapacityConstrainedEquipment bottleneckEquipment = null;
    neqsim.process.equipment.capacity.CapacityConstraint limitingConstraint = null;
    double maxUtil = 0.0;

    for (neqsim.process.equipment.capacity.CapacityConstrainedEquipment equip : getConstrainedEquipment()) {
      // Skip equipment with capacity analysis disabled
      if (!equip.isCapacityAnalysisEnabled()) {
        continue;
      }
      neqsim.process.equipment.capacity.CapacityConstraint constraint =
          equip.getBottleneckConstraint();
      if (constraint != null && constraint.isEnabled()) {
        double util = constraint.getUtilization();
        if (!Double.isNaN(util) && util > maxUtil) {
          maxUtil = util;
          bottleneckEquipment = equip;
          limitingConstraint = constraint;
        }
      }
    }

    if (bottleneckEquipment == null) {
      return neqsim.process.equipment.capacity.BottleneckResult.empty();
    }
    return new neqsim.process.equipment.capacity.BottleneckResult(
        (ProcessEquipmentInterface) bottleneckEquipment, limitingConstraint, maxUtil);
  }

  /**
   * Checks if any equipment in the process is overloaded (exceeds design capacity).
   *
   * <p>
   * Only equipment with capacity analysis enabled is checked.
   * </p>
   *
   * @return true if any equipment has capacity utilization above 100%
   */
  public boolean isAnyEquipmentOverloaded() {
    for (neqsim.process.equipment.capacity.CapacityConstrainedEquipment equip : getConstrainedEquipment()) {
      if (equip.isCapacityAnalysisEnabled() && equip.isCapacityExceeded()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if any equipment exceeds a HARD capacity limit.
   *
   * <p>
   * HARD limits represent absolute equipment limits that cannot be exceeded without trip or damage,
   * such as maximum compressor speed or surge limits. Only equipment with capacity analysis enabled
   * is checked.
   * </p>
   *
   * @return true if any HARD constraint is exceeded
   */
  public boolean isAnyHardLimitExceeded() {
    for (neqsim.process.equipment.capacity.CapacityConstrainedEquipment equip : getConstrainedEquipment()) {
      if (equip.isCapacityAnalysisEnabled() && equip.isHardLimitExceeded()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gets a summary of capacity utilization for all constrained equipment.
   *
   * <p>
   * Returns a map of equipment names to their maximum constraint utilization. Only equipment with
   * capacity analysis enabled is included. Useful for displaying overall process capacity status.
   * </p>
   *
   * @return map of equipment name to utilization percentage
   */
  public java.util.Map<String, Double> getCapacityUtilizationSummary() {
    java.util.Map<String, Double> summary = new java.util.LinkedHashMap<>();
    for (neqsim.process.equipment.capacity.CapacityConstrainedEquipment equip : getConstrainedEquipment()) {
      // Skip equipment with capacity analysis disabled
      if (!equip.isCapacityAnalysisEnabled()) {
        continue;
      }
      ProcessEquipmentInterface unit = (ProcessEquipmentInterface) equip;
      double util = equip.getMaxUtilization();
      if (!Double.isNaN(util)) {
        summary.put(unit.getName(), util * 100.0);
      }
    }
    return summary;
  }

  /**
   * Gets equipment that is near its capacity limit (above warning threshold).
   *
   * <p>
   * Returns equipment where at least one constraint is above its warning threshold (typically 90%
   * of design). Only equipment with capacity analysis enabled is included. Useful for identifying
   * potential future bottlenecks.
   * </p>
   *
   * @return list of equipment names that are near capacity limits
   */
  public java.util.List<String> getEquipmentNearCapacityLimit() {
    java.util.List<String> nearLimit = new java.util.ArrayList<>();
    for (neqsim.process.equipment.capacity.CapacityConstrainedEquipment equip : getConstrainedEquipment()) {
      if (equip.isCapacityAnalysisEnabled() && equip.isNearCapacityLimit()) {
        nearLimit.add(((ProcessEquipmentInterface) equip).getName());
      }
    }
    return nearLimit;
  }

  /**
   * Disables all capacity constraints on all equipment in the process system.
   *
   * <p>
   * Use this for what-if scenarios where you want to ignore capacity limits and see what the
   * process would do without constraints. To re-enable, call {@link #enableAllConstraints()}.
   * </p>
   *
   * <p>
   * This method also sets {@code capacityAnalysisEnabled = false} on each equipment, which prevents
   * the optimizer from using fallback capacity rules for equipment types.
   * </p>
   *
   * @return the total number of constraints that were disabled
   */
  public int disableAllConstraints() {
    int totalCount = 0;
    for (neqsim.process.equipment.capacity.CapacityConstrainedEquipment equip : getConstrainedEquipment()) {
      totalCount += equip.disableAllConstraints();
      equip.setCapacityAnalysisEnabled(false);
    }
    return totalCount;
  }

  /**
   * Enables all capacity constraints on all equipment in the process system.
   *
   * <p>
   * Re-enables all constraints that were previously disabled. This restores normal capacity
   * analysis mode for the entire process.
   * </p>
   *
   * <p>
   * This method also sets {@code capacityAnalysisEnabled = true} on each equipment.
   * </p>
   *
   * @return the total number of constraints that were enabled
   */
  public int enableAllConstraints() {
    int totalCount = 0;
    for (neqsim.process.equipment.capacity.CapacityConstrainedEquipment equip : getConstrainedEquipment()) {
      equip.setCapacityAnalysisEnabled(true);
      totalCount += equip.enableAllConstraints();
    }
    return totalCount;
  }

  // ==========================================================================
  // AUTO-SIZING METHODS
  // ==========================================================================

  /**
   * Automatically sizes all equipment in the process system that implements AutoSizeable.
   *
   * <p>
   * This method iterates through all unit operations and calls autoSize() on each one that
   * implements the {@link neqsim.process.design.AutoSizeable} interface. Equipment dimensions are
   * calculated based on current flow conditions, so the process should be run before calling this
   * method.
   * </p>
   *
   * <p>
   * Example usage:
   * </p>
   *
   * <pre>
   * processSystem.run(); // Run first to establish flow conditions
   * int sized = processSystem.autoSizeEquipment(); // Size all equipment
   * processSystem.run(); // Run again to update calculations with new dimensions
   * </pre>
   *
   * @return the number of equipment items that were auto-sized
   */
  public int autoSizeEquipment() {
    return autoSizeEquipment(1.2);
  }

  /**
   * Automatically sizes all equipment in the process system with specified safety factor.
   *
   * <p>
   * This method iterates through all unit operations and calls autoSize() on each one that
   * implements the {@link neqsim.process.design.AutoSizeable} interface. Equipment dimensions are
   * calculated based on current flow conditions, so the process should be run before calling this
   * method.
   * </p>
   *
   * @param safetyFactor multiplier for design capacity, typically 1.1-1.3 (10-30% over design)
   * @return the number of equipment items that were auto-sized
   */
  public int autoSizeEquipment(double safetyFactor) {
    int count = 0;
    for (ProcessEquipmentInterface equipment : unitOperations) {
      if (equipment instanceof neqsim.process.design.AutoSizeable) {
        ((neqsim.process.design.AutoSizeable) equipment).autoSize(safetyFactor);
        count++;
      }
    }
    return count;
  }

  /**
   * Automatically sizes all equipment using company-specific design standards.
   *
   * <p>
   * This method applies design rules from the specified company's technical requirements (TR)
   * documents. The standards are loaded from the NeqSim design database.
   * </p>
   *
   * @param companyStandard company name (e.g., "Equinor", "Shell", "TotalEnergies")
   * @param trDocument TR document reference (e.g., "TR2000", "DEP-31.38.01.11")
   * @return the number of equipment items that were auto-sized
   */
  public int autoSizeEquipment(String companyStandard, String trDocument) {
    int count = 0;
    for (ProcessEquipmentInterface equipment : unitOperations) {
      if (equipment instanceof neqsim.process.design.AutoSizeable) {
        ((neqsim.process.design.AutoSizeable) equipment).autoSize(companyStandard, trDocument);
        count++;
      }
    }
    return count;
  }

  /**
   * Gets the design report for all auto-sized equipment in JSON format.
   *
   * <p>
   * Returns a JSON object containing design reports for all equipment that implements AutoSizeable.
   * Each equipment's report includes design basis, calculated dimensions, and capacity constraints.
   * </p>
   *
   * <p>
   * Example usage:
   * </p>
   *
   * <pre>
   * processSystem.run();
   * processSystem.autoSizeEquipment();
   * String designReport = processSystem.getDesignReportJson();
   * System.out.println(designReport);
   * </pre>
   *
   * @return JSON string with design reports for all auto-sized equipment
   */
  public String getDesignReportJson() {
    com.google.gson.JsonObject report = new com.google.gson.JsonObject();
    report.addProperty("processName", getName());
    report.addProperty("timestamp", java.time.Instant.now().toString());

    com.google.gson.JsonArray equipmentArray = new com.google.gson.JsonArray();

    for (ProcessEquipmentInterface equipment : unitOperations) {
      if (equipment instanceof neqsim.process.design.AutoSizeable) {
        neqsim.process.design.AutoSizeable sizeable =
            (neqsim.process.design.AutoSizeable) equipment;

        com.google.gson.JsonObject equipReport = new com.google.gson.JsonObject();
        equipReport.addProperty("name", equipment.getName());
        equipReport.addProperty("type", equipment.getClass().getSimpleName());
        equipReport.addProperty("autoSized", sizeable.isAutoSized());

        // Get JSON sizing report if available
        String jsonReport = sizeable.getSizingReportJson();
        if (jsonReport != null && !jsonReport.equals("{}")) {
          try {
            com.google.gson.JsonObject sizingData =
                com.google.gson.JsonParser.parseString(jsonReport).getAsJsonObject();
            equipReport.add("sizingData", sizingData);
          } catch (Exception e) {
            equipReport.addProperty("sizingData", jsonReport);
          }
        }

        // Add capacity constraints if equipment is capacity-constrained
        if (equipment instanceof neqsim.process.equipment.capacity.CapacityConstrainedEquipment) {
          neqsim.process.equipment.capacity.CapacityConstrainedEquipment constrained =
              (neqsim.process.equipment.capacity.CapacityConstrainedEquipment) equipment;

          com.google.gson.JsonObject capacityData = new com.google.gson.JsonObject();
          capacityData.addProperty("maxUtilization", constrained.getMaxUtilization() * 100.0);
          capacityData.addProperty("capacityExceeded", constrained.isCapacityExceeded());

          com.google.gson.JsonArray constraintsArray = new com.google.gson.JsonArray();
          for (java.util.Map.Entry<String, neqsim.process.equipment.capacity.CapacityConstraint> entry : constrained
              .getCapacityConstraints().entrySet()) {
            neqsim.process.equipment.capacity.CapacityConstraint constraint = entry.getValue();
            com.google.gson.JsonObject constraintObj = new com.google.gson.JsonObject();
            constraintObj.addProperty("name", constraint.getName());
            constraintObj.addProperty("currentValue", constraint.getCurrentValue());
            constraintObj.addProperty("designValue", constraint.getDesignValue());
            constraintObj.addProperty("unit", constraint.getUnit());
            constraintObj.addProperty("utilization", constraint.getUtilization() * 100.0);
            constraintObj.addProperty("violated", constraint.isViolated());
            constraintsArray.add(constraintObj);
          }
          capacityData.add("constraints", constraintsArray);
          equipReport.add("capacityConstraints", capacityData);
        }

        equipmentArray.add(equipReport);
      }
    }

    report.add("equipment", equipmentArray);

    return new com.google.gson.GsonBuilder().setPrettyPrinting()
        .serializeSpecialFloatingPointValues().create().toJson(report);
  }

  /**
   * Gets a summary design report for all auto-sized equipment.
   *
   * <p>
   * Returns a human-readable text report summarizing the design of all equipment.
   * </p>
   *
   * @return formatted text report
   */
  public String getDesignReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== PROCESS DESIGN REPORT ===\n");
    sb.append("Process: ").append(getName()).append("\n");
    sb.append("Generated: ").append(java.time.Instant.now()).append("\n\n");

    for (ProcessEquipmentInterface equipment : unitOperations) {
      if (equipment instanceof neqsim.process.design.AutoSizeable) {
        neqsim.process.design.AutoSizeable sizeable =
            (neqsim.process.design.AutoSizeable) equipment;
        sb.append("--- ").append(equipment.getName()).append(" (")
            .append(equipment.getClass().getSimpleName()).append(") ---\n");
        sb.append("Auto-sized: ").append(sizeable.isAutoSized()).append("\n");
        sb.append(sizeable.getSizingReport()).append("\n");
      }
    }

    return sb.toString();
  }

  // ==========================================================================
  // OPTIMIZATION METHODS
  // ==========================================================================

  /**
   * Creates a ProcessOptimizationEngine for this process system.
   *
   * <p>
   * The optimization engine provides advanced optimization capabilities including:
   * </p>
   * <ul>
   * <li>Maximum throughput optimization</li>
   * <li>Constraint evaluation and bottleneck detection</li>
   * <li>Sensitivity analysis</li>
   * <li>Lift curve generation</li>
   * </ul>
   *
   * <p>
   * Example usage:
   * </p>
   *
   * <pre>
   * ProcessOptimizationEngine engine = process.createOptimizer();
   * engine.setSearchAlgorithm(SearchAlgorithm.BFGS);
   * OptimizationResult result = engine.findMaximumThroughput(50, 10, 1000, 100000);
   * </pre>
   *
   * @return a new ProcessOptimizationEngine configured for this process
   */
  public ProcessOptimizationEngine createOptimizer() {
    return new ProcessOptimizationEngine(this);
  }

  /**
   * Creates a FlowRateOptimizer for this process system.
   *
   * <p>
   * The FlowRateOptimizer provides detailed flow rate optimization capabilities including lift
   * curve generation and Eclipse VFP export.
   * </p>
   *
   * @param inletStreamName name of the inlet stream
   * @param outletStreamName name of the outlet stream (or equipment)
   * @return a new FlowRateOptimizer configured for this process
   */
  public FlowRateOptimizer createFlowRateOptimizer(String inletStreamName,
      String outletStreamName) {
    return new FlowRateOptimizer(this, inletStreamName, outletStreamName);
  }

  /**
   * Finds the maximum throughput for given pressure boundaries.
   *
   * <p>
   * This is a convenience method that creates an optimizer, runs the optimization, and returns the
   * result. For more control over the optimization process, use {@link #createOptimizer()}.
   * </p>
   *
   * @param inletPressure inlet pressure in bara
   * @param outletPressure outlet pressure in bara
   * @param minFlow minimum flow rate to consider in kg/hr
   * @param maxFlow maximum flow rate to consider in kg/hr
   * @return the maximum feasible flow rate in kg/hr, or NaN if optimization fails
   */
  public double findMaxThroughput(double inletPressure, double outletPressure, double minFlow,
      double maxFlow) {
    ProcessOptimizationEngine engine = createOptimizer();
    ProcessOptimizationEngine.OptimizationResult result =
        engine.findMaximumThroughput(inletPressure, outletPressure, minFlow, maxFlow);
    return result.getOptimalValue();
  }

  /**
   * Finds the maximum throughput using default flow bounds.
   *
   * <p>
   * Uses default minimum flow of 100 kg/hr and maximum flow of 1,000,000 kg/hr.
   * </p>
   *
   * @param inletPressure inlet pressure in bara
   * @param outletPressure outlet pressure in bara
   * @return the maximum feasible flow rate in kg/hr, or NaN if optimization fails
   */
  public double findMaxThroughput(double inletPressure, double outletPressure) {
    return findMaxThroughput(inletPressure, outletPressure, 100.0, 1000000.0);
  }

  /**
   * Optimizes the process throughput and returns detailed results.
   *
   * <p>
   * This method provides a complete optimization result including the optimal flow rate, constraint
   * status, and bottleneck information.
   * </p>
   *
   * @param inletPressure inlet pressure in bara
   * @param outletPressure outlet pressure in bara
   * @param minFlow minimum flow rate to consider in kg/hr
   * @param maxFlow maximum flow rate to consider in kg/hr
   * @return optimization result with detailed information
   */
  public ProcessOptimizationEngine.OptimizationResult optimizeThroughput(double inletPressure,
      double outletPressure, double minFlow, double maxFlow) {
    ProcessOptimizationEngine engine = createOptimizer();
    return engine.findMaximumThroughput(inletPressure, outletPressure, minFlow, maxFlow);
  }

  /**
   * Evaluates all equipment constraints in the process.
   *
   * <p>
   * Returns a detailed report of all capacity constraints across all equipment in the process.
   * Useful for understanding the current operating status and identifying potential bottlenecks.
   * </p>
   *
   * @return constraint report with utilization information for all equipment
   */
  public ProcessOptimizationEngine.ConstraintReport evaluateConstraints() {
    ProcessOptimizationEngine engine = createOptimizer();
    return engine.evaluateAllConstraints();
  }

  /**
   * Generates a lift curve for this process.
   *
   * <p>
   * Creates a table of maximum flow rates for different pressure and temperature conditions. The
   * result can be exported to Eclipse VFP format for reservoir simulation.
   * </p>
   *
   * @param pressures array of pressures to evaluate (bara)
   * @param temperatures array of temperatures to evaluate (K)
   * @param waterCuts array of water cuts as fraction
   * @param GORs array of gas-oil ratios in Sm3/Sm3
   * @return lift curve data
   */
  public ProcessOptimizationEngine.LiftCurveData generateLiftCurve(double[] pressures,
      double[] temperatures, double[] waterCuts, double[] GORs) {
    ProcessOptimizationEngine engine = createOptimizer();
    return engine.generateLiftCurve(pressures, temperatures, waterCuts, GORs);
  }

  /**
   * Performs sensitivity analysis at the given flow rate.
   *
   * <p>
   * Calculates how sensitive the process is to changes in flow rate, identifying which constraints
   * become binding and the rate of change of key variables.
   * </p>
   *
   * @param optimalFlow optimal flow rate to analyze in kg/hr
   * @param inletPressure inlet pressure in bara
   * @param outletPressure outlet pressure in bara
   * @return sensitivity analysis result
   */
  public ProcessOptimizationEngine.SensitivityResult analyzeSensitivity(double optimalFlow,
      double inletPressure, double outletPressure) {
    ProcessOptimizationEngine engine = createOptimizer();
    return engine.analyzeSensitivity(optimalFlow, inletPressure, outletPressure);
  }

  /**
   * Creates a fluent optimization builder for this process.
   *
   * <p>
   * The builder provides a convenient way to configure and run optimizations with method chaining.
   * </p>
   *
   * <p>
   * Example usage:
   * </p>
   *
   * <pre>
   * double maxFlow = process.optimize().withPressures(50, 10).withFlowBounds(1000, 100000)
   *     .usingAlgorithm(SearchAlgorithm.BFGS).findMaxThroughput();
   * </pre>
   *
   * @return a new OptimizationBuilder for this process
   */
  public OptimizationBuilder optimize() {
    return new OptimizationBuilder(this);
  }

  /**
   * Fluent builder for process optimization.
   *
   * <p>
   * Provides a convenient API for configuring and running process optimizations.
   * </p>
   */
  public static class OptimizationBuilder {
    private final ProcessSystem process;
    private double inletPressure = 50.0;
    private double outletPressure = 10.0;
    private double minFlow = 100.0;
    private double maxFlow = 1000000.0;
    private ProcessOptimizationEngine.SearchAlgorithm algorithm =
        ProcessOptimizationEngine.SearchAlgorithm.GOLDEN_SECTION;
    private int maxIterations = 50;
    private double tolerance = 1e-4;

    /**
     * Creates a new optimization builder for the given process.
     *
     * @param process the process to optimize
     */
    public OptimizationBuilder(ProcessSystem process) {
      this.process = process;
    }

    /**
     * Sets the inlet and outlet pressures.
     *
     * @param inlet inlet pressure in bara
     * @param outlet outlet pressure in bara
     * @return this builder for chaining
     */
    public OptimizationBuilder withPressures(double inlet, double outlet) {
      this.inletPressure = inlet;
      this.outletPressure = outlet;
      return this;
    }

    /**
     * Sets the flow rate bounds.
     *
     * @param min minimum flow rate in kg/hr
     * @param max maximum flow rate in kg/hr
     * @return this builder for chaining
     */
    public OptimizationBuilder withFlowBounds(double min, double max) {
      this.minFlow = min;
      this.maxFlow = max;
      return this;
    }

    /**
     * Sets the search algorithm.
     *
     * @param algorithm the optimization algorithm to use
     * @return this builder for chaining
     */
    public OptimizationBuilder usingAlgorithm(ProcessOptimizationEngine.SearchAlgorithm algorithm) {
      this.algorithm = algorithm;
      return this;
    }

    /**
     * Sets the maximum number of iterations.
     *
     * @param iterations maximum iterations
     * @return this builder for chaining
     */
    public OptimizationBuilder withMaxIterations(int iterations) {
      this.maxIterations = iterations;
      return this;
    }

    /**
     * Sets the convergence tolerance.
     *
     * @param tol convergence tolerance
     * @return this builder for chaining
     */
    public OptimizationBuilder withTolerance(double tol) {
      this.tolerance = tol;
      return this;
    }

    /**
     * Finds the maximum throughput with the configured settings.
     *
     * @return the maximum feasible flow rate in kg/hr
     */
    public double findMaxThroughput() {
      ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
      engine.setSearchAlgorithm(algorithm);
      engine.setMaxIterations(maxIterations);
      engine.setTolerance(tolerance);
      ProcessOptimizationEngine.OptimizationResult result =
          engine.findMaximumThroughput(inletPressure, outletPressure, minFlow, maxFlow);
      return result.getOptimalValue();
    }

    /**
     * Runs optimization and returns detailed results.
     *
     * @return optimization result with full details
     */
    public ProcessOptimizationEngine.OptimizationResult optimize() {
      ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
      engine.setSearchAlgorithm(algorithm);
      engine.setMaxIterations(maxIterations);
      engine.setTolerance(tolerance);
      return engine.findMaximumThroughput(inletPressure, outletPressure, minFlow, maxFlow);
    }

    /**
     * Generates a lift curve with the configured settings.
     *
     * @param pressures array of pressures to evaluate (bara)
     * @param temperatures array of temperatures to evaluate (K)
     * @param waterCuts array of water cuts as fraction
     * @param GORs array of gas-oil ratios in Sm3/Sm3
     * @return lift curve data
     */
    public ProcessOptimizationEngine.LiftCurveData generateLiftCurve(double[] pressures,
        double[] temperatures, double[] waterCuts, double[] GORs) {
      ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
      engine.setSearchAlgorithm(algorithm);
      engine.setMaxIterations(maxIterations);
      engine.setTolerance(tolerance);
      return engine.generateLiftCurve(pressures, temperatures, waterCuts, GORs);
    }
  }

  /*
   * @XmlRootElement private class Report extends Object{ public Double name; public
   * ArrayList<ReportInterface> unitOperationsReports = new ArrayList<ReportInterface>();
   *
   * Report(){ name= getName();
   *
   * for (int i = 0; i < unitOperations.size(); i++) {
   * unitOperationsReports.add(unitOperations.getReport()); } } }
   *
   * public Report getReport(){ return this.new Report(); }
   */

  // ============================================================================
  // Mechanical Design and Cost Estimation API
  // ============================================================================

  /** System-level mechanical design (lazy initialized). */
  private transient neqsim.process.mechanicaldesign.SystemMechanicalDesign systemMechanicalDesign;

  /** Process-level cost estimate (lazy initialized). */
  private transient neqsim.process.costestimation.ProcessCostEstimate processCostEstimate;

  /**
   * Initialize mechanical design for all equipment in the process.
   *
   * <p>
   * This method calls initMechanicalDesign() on each equipment item, preparing them for mechanical
   * design calculations. Should be called after process simulation has run.
   * </p>
   *
   * <p>
   * Workflow: ProcessSystem.run() → initAllMechanicalDesigns() → runAllMechanicalDesigns() →
   * getCostEstimate()
   * </p>
   */
  public void initAllMechanicalDesigns() {
    for (ProcessEquipmentInterface equipment : unitOperations) {
      if (equipment != null) {
        equipment.initMechanicalDesign();
      }
    }
  }

  /**
   * Run mechanical design calculations for all equipment in the process.
   *
   * <p>
   * This method calls calcDesign() on each equipment's mechanical design. Equipment must have had
   * initMechanicalDesign() called first, or this method will call it automatically.
   * </p>
   *
   * <p>
   * Workflow: ProcessSystem.run() → initAllMechanicalDesigns() → runAllMechanicalDesigns() →
   * getCostEstimate()
   * </p>
   */
  public void runAllMechanicalDesigns() {
    for (ProcessEquipmentInterface equipment : unitOperations) {
      if (equipment != null) {
        // Ensure mechanical design is initialized
        equipment.initMechanicalDesign();
        neqsim.process.mechanicaldesign.MechanicalDesign mecDesign =
            equipment.getMechanicalDesign();
        if (mecDesign != null) {
          mecDesign.calcDesign();
        }
      }
    }
  }

  /**
   * Get the system-level mechanical design aggregator.
   *
   * <p>
   * The SystemMechanicalDesign provides aggregated views of all equipment mechanical designs,
   * including total weights, dimensions, and utility requirements.
   * </p>
   *
   * <p>
   * Example:
   * </p>
   *
   * <pre>
   * {@code
   * process.run();
   * SystemMechanicalDesign mecDesign = process.getSystemMechanicalDesign();
   * mecDesign.runDesignCalculation();
   * System.out.println("Total weight: " + mecDesign.getTotalWeight() + " kg");
   * System.out.println(mecDesign.toJson());
   * }
   * </pre>
   *
   * @return the system mechanical design object
   */
  public neqsim.process.mechanicaldesign.SystemMechanicalDesign getSystemMechanicalDesign() {
    if (systemMechanicalDesign == null) {
      systemMechanicalDesign = new neqsim.process.mechanicaldesign.SystemMechanicalDesign(this);
    }
    return systemMechanicalDesign;
  }

  /**
   * Get the process-level cost estimate.
   *
   * <p>
   * The ProcessCostEstimate provides comprehensive cost estimation for the entire process,
   * including purchased equipment cost, bare module cost, total module cost, and grass roots cost.
   * </p>
   *
   * <p>
   * Workflow: ProcessSystem.run() → getCostEstimate() → calculateAllCosts() → toJson()
   * </p>
   *
   * <p>
   * Example:
   * </p>
   *
   * <pre>
   * {@code
   * process.run();
   * ProcessCostEstimate costEst = process.getCostEstimate();
   * costEst.calculateAllCosts();
   * System.out.println("PEC: $" + costEst.getTotalPurchasedEquipmentCost());
   * System.out.println("Grass Roots: $" + costEst.getTotalGrassRootsCost());
   * System.out.println(costEst.toJson());
   * }
   * </pre>
   *
   * @return the process cost estimate object
   */
  public neqsim.process.costestimation.ProcessCostEstimate getCostEstimate() {
    if (processCostEstimate == null) {
      processCostEstimate = new neqsim.process.costestimation.ProcessCostEstimate(this);
    }
    return processCostEstimate;
  }

  /**
   * Run complete mechanical design and cost estimation for the process.
   *
   * <p>
   * This is a convenience method that performs the full workflow:
   * </p>
   * <ol>
   * <li>Initialize all mechanical designs</li>
   * <li>Run all mechanical design calculations</li>
   * <li>Calculate system-level mechanical design aggregations</li>
   * <li>Calculate process cost estimates</li>
   * </ol>
   *
   * <p>
   * Example:
   * </p>
   *
   * <pre>
   * {@code
   * process.run();
   * process.runMechanicalDesignAndCostEstimation();
   *
   * // Get results
   * SystemMechanicalDesign mecDesign = process.getSystemMechanicalDesign();
   * ProcessCostEstimate costEst = process.getCostEstimate();
   *
   * System.out.println("Total weight: " + mecDesign.getTotalWeight() + " kg");
   * System.out.println("Grass Roots Cost: $" + costEst.getTotalGrassRootsCost());
   * }
   * </pre>
   */
  public void runMechanicalDesignAndCostEstimation() {
    // Run individual equipment mechanical designs
    runAllMechanicalDesigns();

    // Calculate system-level aggregations
    getSystemMechanicalDesign().runDesignCalculation();

    // Calculate cost estimates
    getCostEstimate().calculateAllCosts();
  }

  /**
   * Get a comprehensive JSON report of mechanical design and cost estimation.
   *
   * <p>
   * This method runs the full mechanical design and cost estimation workflow if not already done,
   * then returns a combined JSON report including both mechanical design data and cost estimates.
   * </p>
   *
   * @return JSON string with combined mechanical design and cost estimate data
   */
  public String getMechanicalDesignAndCostEstimateJson() {
    runMechanicalDesignAndCostEstimation();

    // Combine both reports
    java.util.Map<String, Object> combined = new java.util.LinkedHashMap<String, Object>();
    combined.put("processName", getName());
    combined.put("reportType", "MechanicalDesignAndCostEstimate");
    combined.put("generatedAt", java.time.Instant.now().toString());

    // Add mechanical design summary
    neqsim.process.mechanicaldesign.SystemMechanicalDesign mecDesign = getSystemMechanicalDesign();
    java.util.Map<String, Object> mecSummary = new java.util.LinkedHashMap<String, Object>();
    mecSummary.put("totalWeight_kg", mecDesign.getTotalWeight());
    mecSummary.put("totalVolume_m3", mecDesign.getTotalVolume());
    mecSummary.put("totalPlotSpace_m2", mecDesign.getTotalPlotSpace());
    mecSummary.put("equipmentCount", mecDesign.getEquipmentList().size());
    mecSummary.put("totalPowerRequired_kW", mecDesign.getTotalPowerRequired());
    mecSummary.put("totalPowerRecovered_kW", mecDesign.getTotalPowerRecovered());
    mecSummary.put("totalHeatingDuty_kW", mecDesign.getTotalHeatingDuty());
    mecSummary.put("totalCoolingDuty_kW", mecDesign.getTotalCoolingDuty());
    combined.put("mechanicalDesignSummary", mecSummary);

    // Add cost estimate summary
    neqsim.process.costestimation.ProcessCostEstimate costEst = getCostEstimate();
    java.util.Map<String, Object> costSummary = new java.util.LinkedHashMap<String, Object>();
    costSummary.put("purchasedEquipmentCost_USD", costEst.getTotalPurchasedEquipmentCost());
    costSummary.put("bareModuleCost_USD", costEst.getTotalBareModuleCost());
    costSummary.put("totalModuleCost_USD", costEst.getTotalModuleCost());
    costSummary.put("grassRootsCost_USD", costEst.getTotalGrassRootsCost());
    costSummary.put("installationManHours", costEst.getTotalInstallationManHours());
    combined.put("costEstimateSummary", costSummary);

    // Add weight breakdown
    combined.put("weightByEquipmentType_kg", mecDesign.getWeightByEquipmentType());
    combined.put("weightByDiscipline_kg", mecDesign.getWeightByDiscipline());

    // Add cost breakdown
    combined.put("costByEquipmentType_USD", costEst.getCostByEquipmentType());
    combined.put("costByDiscipline_USD", costEst.getCostByDiscipline());

    return new com.google.gson.GsonBuilder().setPrettyPrinting()
        .serializeSpecialFloatingPointValues().create().toJson(combined);
  }

  /**
   * Get cost estimate for a specific equipment by name.
   *
   * @param equipmentName name of the equipment
   * @return the cost estimate for the equipment, or null if not found
   */
  public neqsim.process.costestimation.UnitCostEstimateBaseClass getEquipmentCostEstimate(
      String equipmentName) {
    ProcessEquipmentInterface equipment = getUnit(equipmentName);
    if (equipment == null) {
      return null;
    }
    equipment.initMechanicalDesign();
    neqsim.process.mechanicaldesign.MechanicalDesign mecDesign = equipment.getMechanicalDesign();
    if (mecDesign == null) {
      return null;
    }
    mecDesign.calcDesign();
    mecDesign.calculateCostEstimate();
    return mecDesign.getCostEstimate();
  }

  /**
   * Get mechanical design for a specific equipment by name.
   *
   * @param equipmentName name of the equipment
   * @return the mechanical design for the equipment, or null if not found
   */
  public neqsim.process.mechanicaldesign.MechanicalDesign getEquipmentMechanicalDesign(
      String equipmentName) {
    ProcessEquipmentInterface equipment = getUnit(equipmentName);
    if (equipment == null) {
      return null;
    }
    equipment.initMechanicalDesign();
    neqsim.process.mechanicaldesign.MechanicalDesign mecDesign = equipment.getMechanicalDesign();
    if (mecDesign != null) {
      mecDesign.calcDesign();
    }
    return mecDesign;
  }

  // ============================================================================
  // Electrical Design API
  // ============================================================================

  /**
   * Initialize electrical design for all equipment in the process.
   *
   * <p>
   * Calls initElectricalDesign() on each equipment item, preparing them for electrical design
   * calculations. Should be called after process simulation has run.
   * </p>
   */
  public void initAllElectricalDesigns() {
    for (ProcessEquipmentInterface equipment : unitOperations) {
      if (equipment != null) {
        equipment.initElectricalDesign();
      }
    }
  }

  /**
   * Run electrical design calculations for all equipment in the process.
   *
   * <p>
   * Calls calcDesign() on each equipment's electrical design. Automatically initializes electrical
   * designs that have not been initialized.
   * </p>
   */
  public void runAllElectricalDesigns() {
    for (ProcessEquipmentInterface equipment : unitOperations) {
      if (equipment != null) {
        neqsim.process.electricaldesign.ElectricalDesign elecDesign =
            equipment.getElectricalDesign();
        if (elecDesign != null) {
          elecDesign.calcDesign();
        }
      }
    }
  }

  /**
   * Get the electrical load list for all equipment in the process.
   *
   * <p>
   * Aggregates electrical loads from all equipment into a single load list with summary
   * calculations for transformer and generator sizing.
   * </p>
   *
   * <p>
   * Example:
   * </p>
   *
   * <pre>
   * {@code
   * process.run();
   * process.runAllElectricalDesigns();
   * ElectricalLoadList loadList = process.getElectricalLoadList();
   * System.out.println("Total demand: " + loadList.getMaximumDemandKW() + " kW");
   * System.out.println(loadList.toJson());
   * }
   * </pre>
   *
   * @return the electrical load list
   */
  public neqsim.process.electricaldesign.loadanalysis.ElectricalLoadList getElectricalLoadList() {
    neqsim.process.electricaldesign.loadanalysis.ElectricalLoadList loadList =
        new neqsim.process.electricaldesign.loadanalysis.ElectricalLoadList(getName());

    for (ProcessEquipmentInterface equipment : unitOperations) {
      if (equipment == null) {
        continue;
      }
      neqsim.process.electricaldesign.ElectricalDesign elecDesign = equipment.getElectricalDesign();
      if (elecDesign == null || elecDesign.getElectricalInputKW() <= 0) {
        continue;
      }

      neqsim.process.electricaldesign.loadanalysis.LoadItem item =
          new neqsim.process.electricaldesign.loadanalysis.LoadItem(equipment.getName(),
              equipment.getClass().getSimpleName(), elecDesign.getMotor().getRatedPowerKW());
      item.setAbsorbedPowerKW(elecDesign.getElectricalInputKW());
      item.setApparentPowerKVA(elecDesign.getApparentPowerKVA());
      item.setPowerFactor(elecDesign.getPowerFactor());
      item.setRatedVoltageV(elecDesign.getRatedVoltageV());
      item.setRatedCurrentA(elecDesign.getFullLoadCurrentA());
      item.setHasVFD(elecDesign.isUseVFD());
      item.setDiversityFactor(elecDesign.getDiversityFactor());

      loadList.addLoadItem(item);
    }

    loadList.calculateSummary();
    return loadList;
  }

  /**
   * Get electrical design for a specific equipment by name.
   *
   * @param equipmentName name of the equipment
   * @return the electrical design, or null if not found
   */
  public neqsim.process.electricaldesign.ElectricalDesign getEquipmentElectricalDesign(
      String equipmentName) {
    ProcessEquipmentInterface equipment = getUnit(equipmentName);
    if (equipment == null) {
      return null;
    }
    neqsim.process.electricaldesign.ElectricalDesign elecDesign = equipment.getElectricalDesign();
    if (elecDesign != null) {
      elecDesign.calcDesign();
    }
    return elecDesign;
  }

  /**
   * Create a system-level electrical design for the entire process.
   *
   * <p>
   * Runs all equipment-level electrical designs and produces a plant-wide summary including utility
   * loads, UPS loads, and main transformer/generator sizing.
   * </p>
   *
   * @return the system electrical design with aggregated results
   */
  public neqsim.process.electricaldesign.system.SystemElectricalDesign getSystemElectricalDesign() {
    neqsim.process.electricaldesign.system.SystemElectricalDesign systemDesign =
        new neqsim.process.electricaldesign.system.SystemElectricalDesign(this);
    systemDesign.calcDesign();
    return systemDesign;
  }

  /**
   * <p>
   * Get a system-wide instrument design summary that aggregates instrument lists, I/O counts, DCS
   * and SIS cabinet sizing, and cost estimates across all equipment in this process system.
   * </p>
   *
   * @return the system instrument design with aggregated results
   */
  public neqsim.process.instrumentdesign.system.SystemInstrumentDesign getSystemInstrumentDesign() {
    neqsim.process.instrumentdesign.system.SystemInstrumentDesign systemDesign =
        new neqsim.process.instrumentdesign.system.SystemInstrumentDesign(this);
    systemDesign.calcDesign();
    return systemDesign;
  }

  // ========================== Automation API ==========================

  /**
   * Returns an automation facade for this process system. The facade provides a stable,
   * string-addressable API for scripts and AI agents to interact with the simulation without
   * navigating Java object hierarchies.
   *
   * @return a {@link neqsim.process.automation.ProcessAutomation} facade
   */
  public neqsim.process.automation.ProcessAutomation getAutomation() {
    return new neqsim.process.automation.ProcessAutomation(this);
  }

  /**
   * Returns the names of all unit operations in this process system. Convenience delegate for
   * {@link neqsim.process.automation.ProcessAutomation#getUnitList()}.
   *
   * @return unmodifiable list of unit operation names
   */
  public List<String> getUnitNames() {
    return getAutomation().getUnitList();
  }

  /**
   * Returns all available variables for the named unit operation. Convenience delegate for
   * {@link neqsim.process.automation.ProcessAutomation#getVariableList(String)}.
   *
   * @param unitName the name of the unit operation
   * @return list of variable descriptors
   * @throws IllegalArgumentException if the unit is not found
   */
  public List<neqsim.process.automation.SimulationVariable> getVariableList(String unitName) {
    return getAutomation().getVariableList(unitName);
  }

  /**
   * Reads the current value of a simulation variable by its dot-notation address. Convenience
   * delegate for
   * {@link neqsim.process.automation.ProcessAutomation#getVariableValue(String, String)}.
   *
   * @param address the dot-notation address, e.g. "separator-1.gasOutStream.temperature"
   * @param unitOfMeasure the desired unit, e.g. "C", "bara", "kg/hr"
   * @return the variable value in the requested unit
   * @throws IllegalArgumentException if the address cannot be resolved
   */
  public double getVariableValue(String address, String unitOfMeasure) {
    return getAutomation().getVariableValue(address, unitOfMeasure);
  }

  /**
   * Sets the value of a simulation input variable. Convenience delegate for
   * {@link neqsim.process.automation.ProcessAutomation#setVariableValue(String, double, String)}.
   *
   * @param address the dot-notation address, e.g. "Compressor.outletPressure"
   * @param value the value to set
   * @param unitOfMeasure the unit of the provided value, e.g. "bara", "C"
   * @throws IllegalArgumentException if the address cannot be resolved or the variable is read-only
   */
  public void setVariableValue(String address, double value, String unitOfMeasure) {
    getAutomation().setVariableValue(address, value, unitOfMeasure);
  }
}
