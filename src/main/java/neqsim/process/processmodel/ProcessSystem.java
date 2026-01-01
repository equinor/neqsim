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
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.SimulationBaseClass;
import neqsim.process.alarm.ProcessAlarmManager;
import neqsim.process.conditionmonitor.ConditionMonitor;
import neqsim.process.equipment.EquipmentEnum;
import neqsim.process.equipment.EquipmentFactory;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.util.RecycleController;
import neqsim.process.equipment.util.Setter;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.processmodel.graph.ProcessGraph;
import neqsim.process.processmodel.graph.ProcessGraphBuilder;
import neqsim.process.processmodel.graph.ProcessNode;
import neqsim.process.util.report.Report;
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
  private ProcessAlarmManager alarmManager = new ProcessAlarmManager();
  RecycleController recycleController = new RecycleController();
  private double timeStep = 1.0;
  private boolean runStep = false;

  private final Map<String, Integer> equipmentCounter = new HashMap<>();
  private ProcessEquipmentInterface lastAddedUnit = null;
  private transient ProcessSystem initialStateSnapshot;
  private double massBalanceErrorThreshold = 0.1; // Default 0.1% error threshold
  private double minimumFlowForMassBalanceError = 1e-6; // Default 1e-6 kg/sec

  // Graph-based execution fields
  /** Cached process graph for topology analysis. */
  private transient ProcessGraph cachedGraph = null;
  /** Flag indicating if the cached graph needs to be rebuilt. */
  private boolean graphDirty = true;
  /** Whether to use graph-based execution order instead of insertion order. */
  private boolean useGraphBasedExecution = false;

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
  public void add(ProcessEquipmentInterface operation) {
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
  public void add(int position, ProcessEquipmentInterface operation) {
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
  public void add(MeasurementDeviceInterface measurementDevice) {
    measurementDevices.add(measurementDevice);
    alarmManager.register(measurementDevice);
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
      ProcessEquipmentInterface unit = getUnit(name);
      unit = newObject;
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }
    return true;
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
  public void replaceObject(String unitName, ProcessEquipmentBaseClass operation) {
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
   * <p>
   * removeUnit.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void removeUnit(String name) {
    for (int i = 0; i < unitOperations.size(); i++) {
      if (unitOperations.get(i).getName().equals(name)) {
        unitOperations.remove(i);
      }
    }
  }

  /**
   * <p>
   * clearAll.
   * </p>
   */
  public void clearAll() {
    unitOperations.clear();
  }

  /**
   * <p>
   * clear.
   * </p>
   */
  public void clear() {
    unitOperations = new ArrayList<ProcessEquipmentInterface>(0);
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
   * <li>For processes WITHOUT recycles: uses parallel execution for maximum speed</li>
   * <li>For processes WITH recycles: uses hybrid execution - parallel for feed-forward sections,
   * then graph-based iteration for recycle sections</li>
   * </ul>
   *
   * @param id calculation identifier for tracking
   */
  public void runOptimized(UUID id) {
    if (hasRecycleLoops()) {
      // Process has recycles - use hybrid execution:
      // 1. Run feed-forward units (before first recycle dependency) in parallel
      // 2. Run recycle section with graph-based iteration
      try {
        runHybrid(id);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.warn("Hybrid execution interrupted, falling back to graph-based run");
        boolean previousSetting = useGraphBasedExecution;
        useGraphBasedExecution = true;
        run(id);
        useGraphBasedExecution = previousSetting;
      }
    } else {
      // Feed-forward process - use parallel execution for maximum speed
      // Units at the same level (no dependencies) run concurrently
      try {
        runParallel(id);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.warn("Parallel execution interrupted, falling back to regular run");
        run(id);
      }
    }
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
  public void runHybrid(UUID id) throws InterruptedException {
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

    // Phase 1: Run feed-forward levels in parallel (before any recycle units)
    int firstRecycleLevel = -1;
    List<List<ProcessNode>> levels = partition.getLevels();

    for (int levelIdx = 0; levelIdx < levels.size(); levelIdx++) {
      List<ProcessNode> level = levels.get(levelIdx);
      boolean hasRecycleUnit = false;
      for (ProcessNode node : level) {
        if (recycleNodes.contains(node)) {
          hasRecycleUnit = true;
          break;
        }
      }
      if (hasRecycleUnit) {
        firstRecycleLevel = levelIdx;
        break;
      }

      // This level is feed-forward - run in parallel
      if (level.size() == 1) {
        ProcessEquipmentInterface unit = level.get(0).getEquipment();
        if (!(unit instanceof Setter)) {
          try {
            unit.run(id);
          } catch (Exception ex) {
            logger.error("equipment: " + unit.getName() + " error: " + ex.getMessage(), ex);
          }
        }
      } else if (level.size() > 1) {
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (ProcessNode node : level) {
          ProcessEquipmentInterface unit = node.getEquipment();
          if (!(unit instanceof Setter)) {
            final ProcessEquipmentInterface unitToRun = unit;
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

    // Phase 2: Run recycle section with graph-based iteration
    if (firstRecycleLevel >= 0) {
      // Build list of remaining units in topological order
      List<ProcessEquipmentInterface> recycleSection = new ArrayList<>();
      for (int levelIdx = firstRecycleLevel; levelIdx < levels.size(); levelIdx++) {
        for (ProcessNode node : levels.get(levelIdx)) {
          recycleSection.add(node.getEquipment());
        }
      }

      // Initialize recycle controller for these units
      recycleController.clear();
      for (ProcessEquipmentInterface unit : recycleSection) {
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

        for (ProcessEquipmentInterface unit : recycleSection) {
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

        for (ProcessEquipmentInterface unit : recycleSection) {
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
  public void runParallel(UUID id) throws InterruptedException {
    ProcessGraph graph = buildGraph();
    ProcessGraph.ParallelPartition partition = graph.partitionForParallelExecution();

    // Run setters first (sequential, they set conditions)
    for (ProcessEquipmentInterface unit : unitOperations) {
      if (unit instanceof Setter) {
        unit.run(id);
      }
    }

    // Execute each level in parallel
    for (List<ProcessNode> level : partition.getLevels()) {
      if (level.size() == 1) {
        // Single unit at this level - run directly
        ProcessEquipmentInterface unit = level.get(0).getEquipment();
        if (!(unit instanceof Setter)) {
          try {
            unit.run(id);
          } catch (Exception ex) {
            logger.error("equipment: " + unit.getName() + " error: " + ex.getMessage(), ex);
          }
        }
      } else if (level.size() > 1) {
        // Multiple units at this level - run in parallel
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();

        for (ProcessNode node : level) {
          ProcessEquipmentInterface unit = node.getEquipment();
          if (!(unit instanceof Setter)) {
            final ProcessEquipmentInterface unitToRun = unit;
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
        }

        // Wait for all units at this level to complete before moving to next level
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

    // Check for recycles - they require sequential iterative execution
    for (ProcessEquipmentInterface unit : unitOperations) {
      if (unit instanceof Recycle) {
        return false;
      }
      if (unit instanceof Adjuster) {
        return false;
      }
    }

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
  public void run(UUID id) {
    // Determine execution order: use graph-based if enabled, otherwise use insertion order
    List<ProcessEquipmentInterface> executionOrder;
    if (useGraphBasedExecution) {
      List<ProcessEquipmentInterface> topoOrder = getTopologicalOrder();
      executionOrder = (topoOrder != null) ? topoOrder : unitOperations;
    } else {
      executionOrder = unitOperations;
    }

    // Run setters first to set conditions
    for (int i = 0; i < executionOrder.size(); i++) {
      ProcessEquipmentInterface unit = executionOrder.get(i);
      if (unit instanceof Setter) {
        unit.run(id);
      }
    }

    boolean hasRecycle = false;
    // boolean hasAdjuster = false;

    // Initializing recycle controller
    recycleController.clear();
    for (int i = 0; i < executionOrder.size(); i++) {
      ProcessEquipmentInterface unit = executionOrder.get(i);
      if (unit instanceof Recycle) {
        hasRecycle = true;
        recycleController.addRecycle((Recycle) unit);
      }
      if (unit instanceof Adjuster) {
        // hasAdjuster = true;
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
            // String error = ex.getMessage();
            logger.error("error running unit uperation " + unit.getName() + " " + ex.getMessage(),
                ex);
            ex.printStackTrace();
          }
        }
        if (unit instanceof Recycle && recycleController.doSolveRecycle((Recycle) unit)) {
          try {
            unit.run(id);
          } catch (Exception ex) {
            // String error = ex.getMessage();
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
        // isConverged=true;
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

      /*
       * signalDB = new String[1000][1 + 3 * measurementDevices.size()];
       *
       * signalDB[timeStepNumber] = new String[1 + 3 * measurementDevices.size()]; for (int i = 0; i
       * < measurementDevices.size(); i++) { signalDB[timeStepNumber][0] = Double.toString(time);
       * signalDB[timeStepNumber][3 * i + 1] = ((MeasurementDeviceInterface)
       * measurementDevices.get(i)) .getName(); signalDB[timeStepNumber][3 * i + 2] = Double
       * .toString(((MeasurementDeviceInterface) measurementDevices.get(i)).getMeasuredValue());
       * signalDB[timeStepNumber][3 * i + 3] = ((MeasurementDeviceInterface)
       * measurementDevices.get(i)) .getUnit(); }
       */
    } while (((!isConverged || (iter < 2 && hasRecycle)) && iter < 100) && !runStep
        && !Thread.currentThread().isInterrupted());

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
  public void runTransient(double dt, UUID id) {
    ensureInitialStateSnapshot();
    for (int i = 0; i < unitOperations.size(); i++) {
      ProcessEquipmentInterface unit = unitOperations.get(i);
      if (unit instanceof Setter) {
        unit.run(id);
      }
    }

    setTimeStep(dt);
    increaseTime(dt);

    for (int i = 0; i < unitOperations.size(); i++) {
      unitOperations.get(i).runTransient(dt, id);
    }

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
  public ProcessSystem copy() {
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

    // If the provided name is null or empty, generate a unique name based on the equipment type.
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
      e.printStackTrace();
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
      e.printStackTrace();
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
   * <p>
   * getBottleneck.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public ProcessEquipmentInterface getBottleneck() {
    ProcessEquipmentInterface bottleneck = null;
    double maxUtilization = 0.0;
    for (ProcessEquipmentInterface unit : unitOperations) {
      double capacity = unit.getCapacityMax();
      double duty = unit.getCapacityDuty();
      if (capacity > 1e-12) {
        double utilization = duty / capacity;
        if (utilization > maxUtilization) {
          maxUtilization = utilization;
          bottleneck = unit;
        }
      }
    }
    return bottleneck;
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
   * @see neqsim.process.util.optimization.BatchStudy
   */
  public neqsim.process.util.optimization.BatchStudy.Builder createBatchStudy() {
    return neqsim.process.util.optimization.BatchStudy.builder(this);
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
}
