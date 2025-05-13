package neqsim.process.processmodel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
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
   * <p>
   * runAsThread.
   * </p>
   *
   * @return a {@link java.lang.Thread} object
   */
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
}
