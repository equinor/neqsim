package neqsim.process.processmodel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.SimulationBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.util.report.Report;

/**
 * A class representing a process module class that can contain unit operations and other modules.
 * Module will be runnning until all recycles in this module are solved. If no recycle in the module
 * then run only once.
 *
 * @author [seros]
 * @version 1.0
 */
public class ProcessModule extends SimulationBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  private static final Logger logger = LogManager.getLogger(ProcessModule.class);

  private int unitIndex = 0;

  private final List<ProcessEquipmentInterface> recycleModules = new ArrayList<>();

  private List<ProcessSystem> addedUnitOperations = new ArrayList<>();
  private List<Integer> operationsIndex = new ArrayList<>();

  private List<ProcessModule> addedModules = new ArrayList<>();
  private List<Integer> modulesIndex = new ArrayList<>();

  private boolean solved = false;

  /**
   * Constructor that takes a name as a parameter.
   *
   * @param name the name of the process module
   */
  public ProcessModule(String name) {
    super(name);
  }

  /**
   * Add an unit operation to the process module.
   *
   * @param processSystem the process system that contains the unit operations to be added.
   */
  public void add(ProcessSystem processSystem) {
    addedUnitOperations.add(processSystem);
    operationsIndex.add(unitIndex++);
  }

  /**
   * Add a process module to the process module.
   *
   * @param module the process module to be added
   */
  public void add(ProcessModule module) {
    addedModules.add(module);
    modulesIndex.add(unitIndex++);
  }

  /**
   * Get the list of added unit operations.
   *
   * @return the list of added unit operations
   */
  public List<ProcessSystem> getAddedUnitOperations() {
    return addedUnitOperations;
  }

  /**
   * Get the list of operations index. The operations index is used to follow the correct order of
   * calculations.
   *
   * @return the list of operations index
   */
  public List<Integer> getOperationsIndex() {
    return operationsIndex;
  }

  /**
   * Get the list of added process modules.
   *
   * @return the list of added process modules
   */
  public List<ProcessModule> getAddedModules() {
    return addedModules;
  }

  /**
   * Get all ProcessSystem instances from this module and all nested modules recursively.
   *
   * @return list of all ProcessSystem instances in the module hierarchy
   */
  public List<ProcessSystem> getAllProcessSystems() {
    List<ProcessSystem> allSystems = new ArrayList<>();

    // Add directly added process systems
    allSystems.addAll(addedUnitOperations);

    // Recursively add from nested modules
    for (ProcessModule nestedModule : addedModules) {
      allSystems.addAll(nestedModule.getAllProcessSystems());
    }

    return allSystems;
  }

  /**
   * Get the list of module index. The module index is used to follow the correct order of
   * calculations.
   *
   * @return the list of module index
   */
  public List<Integer> getModulesIndex() {
    return modulesIndex;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    logger.info("Running module " + getName());
    checkModulesRecycles();
    int iteration = 0;
    do {
      for (int i = 0; i < unitIndex; i++) {
        if (operationsIndex.contains(i)) {
          int index = operationsIndex.indexOf(i);
          for (ProcessEquipmentInterface unitOperation : addedUnitOperations.get(index)
              .getUnitOperations()) {
            if (iteration == 0 || unitOperation.needRecalculation()) {
              unitOperation.run(id);
            }
          }
        } else if (modulesIndex.contains(i)) {
          int index = modulesIndex.indexOf(i);
          addedModules.get(index).run(id);
        }
      }
      iteration++;
      logger.info("Iteration : " + iteration + "  module : " + getName() + " ");
    } while (!recyclesSolved() && iteration <= 100);
    logger.info("Finished running module " + getName());
    solved = true;
  }

  /**
   * Adds all recycle operations from addedUnitOperations to recycleModules list.
   */
  public void checkModulesRecycles() {
    for (ProcessSystem operation : addedUnitOperations) {
      for (ProcessEquipmentInterface unitOperation : operation.getUnitOperations()) {
        if (unitOperation instanceof Recycle) {
          recycleModules.add(unitOperation);
        }
      }
    }
  }

  /**
   * Checks if all recycle operations in recycleModules are solved.
   *
   * @return true if all recycle operations are solved, false otherwise
   */
  public boolean recyclesSolved() {
    for (ProcessEquipmentInterface recycle : recycleModules) {
      if (!recycle.solved()) {
        return false;
      }
    }
    return true;
  }

  /** {@inheritDoc} */

  @Override
  public boolean solved() {
    return solved;
  }

  /**
   * Runs this module in a separate thread using the global NeqSim thread pool.
   *
   * <p>
   * This method submits the module to the shared {@link neqsim.util.NeqSimThreadPool} and returns a
   * {@link java.util.concurrent.Future} that can be used to monitor completion, cancel the task, or
   * retrieve any exceptions that occurred.
   * </p>
   *
   * @return a {@link java.util.concurrent.Future} representing the pending completion of the task
   * @see neqsim.util.NeqSimThreadPool
   */
  public java.util.concurrent.Future<?> runAsTask() {
    return neqsim.util.NeqSimThreadPool.submit(this);
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

  /**
   * Returns the unit with the given name from the list of added unit operations and list of added
   * modules.
   *
   * @param name the name of the unit to retrieve
   * @return the unit with the given name, or {@code null} if no such unit is found
   */
  public Object getUnit(String name) {
    for (ProcessSystem processSystem : addedUnitOperations) {
      Object unit = processSystem.getUnit(name);
      if (unit != null) {
        return unit;
      }
    }

    for (ProcessModule processModule : addedModules) {
      Object unit = processModule.getUnit(name);
      if (unit != null) {
        return unit;
      }
    }
    return null; // no unit found with the given name
  }

  /**
   * Returns the unit with the given name from the list of added unit operations and list of added
   * modules.
   *
   * @param name the name of the unit to retrieve
   * @return the unit with the given name, or {@code null} if no such unit is found
   */
  public Object getMeasurementDevice(String name) {
    for (ProcessSystem processSystem : addedUnitOperations) {
      Object unit = processSystem.getMeasurementDevice(name);
      if (unit != null) {
        return unit;
      }
    }

    for (ProcessModule processModule : addedModules) {
      Object unit = processModule.getMeasurementDevice(name);
      if (unit != null) {
        return unit;
      }
    }
    return null; // no unit found with the given name
  }

  /**
   * <p>
   * Create deep copy.
   * </p>
   *
   * @return a {@link neqsim.process.processmodel.ProcessModule} object
   */
  public ProcessModule copy() {
    try {
      ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(byteOut);
      out.writeObject(this);
      out.flush();
      ByteArrayInputStream byteIn = new ByteArrayInputStream(byteOut.toByteArray());
      ObjectInputStream in = new ObjectInputStream(byteIn);
      return (ProcessModule) in.readObject();
    } catch (Exception e) {
      throw new RuntimeException("Failed to copy ProcessModule", e);
    }
  }

  /**
   * <p>
   * getReport.
   * </p>
   *
   * @return a {@link java.util.ArrayList} object
   */
  public ArrayList<String[]> getReport() {
    return null;
  }

  /**
   * Check mass balance of all unit operations in all process systems within this module.
   *
   * @param unit unit for mass flow rate (e.g., "kg/sec", "kg/hr", "mole/sec")
   * @return a map with unit operation name as key and mass balance result as value
   */
  public Map<String, ProcessSystem.MassBalanceResult> checkMassBalance(String unit) {
    Map<String, ProcessSystem.MassBalanceResult> allResults = new HashMap<>();

    // Check mass balance for all process systems
    for (ProcessSystem processSystem : addedUnitOperations) {
      Map<String, ProcessSystem.MassBalanceResult> systemResults =
          processSystem.checkMassBalance(unit);
      allResults.putAll(systemResults);
    }

    // Recursively check mass balance for all nested modules
    for (ProcessModule module : addedModules) {
      Map<String, ProcessSystem.MassBalanceResult> moduleResults = module.checkMassBalance(unit);
      allResults.putAll(moduleResults);
    }

    return allResults;
  }

  /**
   * Check mass balance of all unit operations in all process systems using kg/sec.
   *
   * @return a map with unit operation name as key and mass balance result as value in kg/sec
   */
  public Map<String, ProcessSystem.MassBalanceResult> checkMassBalance() {
    return checkMassBalance("kg/sec");
  }

  /**
   * Get unit operations that failed mass balance check based on percentage error threshold.
   *
   * @param unit unit for mass flow rate (e.g., "kg/sec", "kg/hr", "mole/sec")
   * @param percentThreshold percentage error threshold (default: 0.1%)
   * @return a map with failed unit operation names and their mass balance results
   */
  public Map<String, ProcessSystem.MassBalanceResult> getFailedMassBalance(String unit,
      double percentThreshold) {
    Map<String, ProcessSystem.MassBalanceResult> allResults = checkMassBalance(unit);
    Map<String, ProcessSystem.MassBalanceResult> failedUnits = new HashMap<>();

    for (Map.Entry<String, ProcessSystem.MassBalanceResult> entry : allResults.entrySet()) {
      ProcessSystem.MassBalanceResult result = entry.getValue();
      if (Double.isNaN(result.getPercentError())
          || Math.abs(result.getPercentError()) > percentThreshold) {
        failedUnits.put(entry.getKey(), result);
      }
    }
    return failedUnits;
  }

  /**
   * Get unit operations that failed mass balance check using kg/sec and 0.1% threshold.
   *
   * @return a map with failed unit operation names and their mass balance results
   */
  public Map<String, ProcessSystem.MassBalanceResult> getFailedMassBalance() {
    return getFailedMassBalance("kg/sec", 0.1);
  }

  /**
   * Get unit operations that failed mass balance check using specified threshold.
   *
   * @param percentThreshold percentage error threshold
   * @return a map with failed unit operation names and their mass balance results in kg/sec
   */
  public Map<String, ProcessSystem.MassBalanceResult> getFailedMassBalance(
      double percentThreshold) {
    return getFailedMassBalance("kg/sec", percentThreshold);
  }

  /** {@inheritDoc} */
  @Override
  public String getReport_json() {
    return new Report(this).generateJsonReport();
  }

  /** {@inheritDoc} */
  @Override
  public void run_step(UUID id) {
    run(id);
  }

  // ====== Graph-Based Representation Methods ======

  /**
   * Builds a graph representation of this module and all its sub-systems.
   *
   * <p>
   * The returned {@link neqsim.process.processmodel.graph.ProcessModelGraph} contains:
   * <ul>
   * <li>Individual graphs for each ProcessSystem</li>
   * <li>A unified flattened graph for the entire module</li>
   * <li>Information about inter-system connections</li>
   * </ul>
   *
   * @return the graph representation of this module
   */
  public neqsim.process.processmodel.graph.ProcessModelGraph buildModelGraph() {
    return neqsim.process.processmodel.graph.ProcessModelGraphBuilder.buildModelGraph(this);
  }

  /**
   * Gets the topologically-sorted calculation order for all equipment in this module.
   *
   * <p>
   * This order respects stream dependencies across all sub-systems.
   *
   * @return list of equipment in calculation order, or null if cycles prevent ordering
   */
  public List<ProcessEquipmentInterface> getCalculationOrder() {
    return buildModelGraph().getCalculationOrder();
  }

  /**
   * Checks if this module (or any sub-system) contains recycle loops.
   *
   * @return true if cycles exist
   */
  public boolean hasRecycleLoops() {
    return buildModelGraph().hasCycles();
  }

  /**
   * Gets the number of sub-systems in this module.
   *
   * @return number of ProcessSystems and nested ProcessModules
   */
  public int getSubSystemCount() {
    return addedUnitOperations.size() + addedModules.size();
  }

  /**
   * Gets a summary of the module's graph structure.
   *
   * @return human-readable summary string
   */
  public String getGraphSummary() {
    return buildModelGraph().getSummary();
  }

  /**
   * Validates the structural integrity of this module.
   *
   * @return list of validation issues, empty if valid
   */
  public List<String> validateStructure() {
    List<String> issues = new ArrayList<>();

    if (addedUnitOperations.isEmpty() && addedModules.isEmpty()) {
      issues.add("Module has no unit operations or sub-modules");
      return issues;
    }

    neqsim.process.processmodel.graph.ProcessModelGraph modelGraph = buildModelGraph();

    if (modelGraph.getTotalNodeCount() == 0) {
      issues.add("Module has no equipment nodes");
    }

    if (modelGraph.hasCycles()) {
      neqsim.process.processmodel.graph.ProcessGraph.CycleAnalysisResult cycles =
          modelGraph.analyzeCycles();
      issues.add("Module contains " + cycles.getCycleCount()
          + " cycle(s) - ensure recycle operations are properly configured");
    }

    // Check for disconnected sub-systems
    if (modelGraph.getSubSystemCount() > 1 && modelGraph.getInterSystemConnectionCount() == 0) {
      issues.add("Module has multiple sub-systems but no inter-system connections detected");
    }

    return issues;
  }

  // ============================================================================
  // CAPACITY CONSTRAINT METHODS
  // ============================================================================

  /**
   * Gets all capacity-constrained equipment in this module and all nested modules/systems.
   *
   * <p>
   * Returns equipment that implements the CapacityConstrainedEquipment interface, such as
   * separators, compressors, pumps, pipelines, manifolds, etc.
   * </p>
   *
   * @return list of capacity-constrained equipment from all systems in this module
   */
  public java.util.List<neqsim.process.equipment.capacity.CapacityConstrainedEquipment> getConstrainedEquipment() {
    java.util.List<neqsim.process.equipment.capacity.CapacityConstrainedEquipment> result =
        new java.util.ArrayList<>();

    // Get from all process systems
    for (ProcessSystem processSystem : addedUnitOperations) {
      result.addAll(processSystem.getConstrainedEquipment());
    }

    // Recursively get from nested modules
    for (ProcessModule module : addedModules) {
      result.addAll(module.getConstrainedEquipment());
    }

    return result;
  }

  /**
   * Finds the process bottleneck with detailed constraint information across all systems.
   *
   * <p>
   * This method searches all ProcessSystems and nested ProcessModules to find the equipment with
   * the highest capacity utilization.
   * </p>
   *
   * @return BottleneckResult containing the bottleneck equipment, limiting constraint, and
   *         utilization; returns empty result if no constrained equipment found
   */
  public neqsim.process.equipment.capacity.BottleneckResult findBottleneck() {
    neqsim.process.equipment.capacity.CapacityConstrainedEquipment bottleneckEquipment = null;
    neqsim.process.equipment.capacity.CapacityConstraint limitingConstraint = null;
    double maxUtil = 0.0;

    for (neqsim.process.equipment.capacity.CapacityConstrainedEquipment equip : getConstrainedEquipment()) {
      neqsim.process.equipment.capacity.CapacityConstraint constraint =
          equip.getBottleneckConstraint();
      if (constraint != null) {
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
   * Checks if any equipment in this module is overloaded (exceeds design capacity).
   *
   * @return true if any equipment has capacity utilization above 100%
   */
  public boolean isAnyEquipmentOverloaded() {
    for (neqsim.process.equipment.capacity.CapacityConstrainedEquipment equip : getConstrainedEquipment()) {
      if (equip.isCapacityExceeded()) {
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
   * such as maximum compressor speed or surge limits.
   * </p>
   *
   * @return true if any HARD constraint is exceeded
   */
  public boolean isAnyHardLimitExceeded() {
    for (neqsim.process.equipment.capacity.CapacityConstrainedEquipment equip : getConstrainedEquipment()) {
      if (equip.isHardLimitExceeded()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gets a summary of capacity utilization for all constrained equipment in this module.
   *
   * <p>
   * Returns a map of equipment names to their maximum constraint utilization. Useful for displaying
   * overall module capacity status.
   * </p>
   *
   * @return map of equipment name to utilization percentage
   */
  public java.util.Map<String, Double> getCapacityUtilizationSummary() {
    java.util.Map<String, Double> summary = new java.util.LinkedHashMap<>();
    for (neqsim.process.equipment.capacity.CapacityConstrainedEquipment equip : getConstrainedEquipment()) {
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
   * of design). Useful for identifying potential future bottlenecks.
   * </p>
   *
   * @return list of equipment names that are near capacity limits
   */
  public java.util.List<String> getEquipmentNearCapacityLimit() {
    java.util.List<String> nearLimit = new java.util.ArrayList<>();
    for (neqsim.process.equipment.capacity.CapacityConstrainedEquipment equip : getConstrainedEquipment()) {
      if (equip.isNearCapacityLimit()) {
        nearLimit.add(((ProcessEquipmentInterface) equip).getName());
      }
    }
    return nearLimit;
  }
}
