package neqsim.process.processmodel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A process model that can run multiple processes in parallel.
 *
 * <p>
 * This class is a simple model that can run multiple processes in parallel. It
 * can
 * run in two modes:
 * <ul>
 * <li><strong>Step mode:</strong> each process is run once in a loop, in the
 * order they were added.</li>
 * <li><strong>Continuous mode:</strong> each process is run in a loop until all
 * processes are
 * finished or a maximum number of iterations is reached.</li>
 * </ul>
 *
 * <p>
 * You can also create groups of processes and run them separately.
 */
public class ProcessModel implements Runnable {

  /** Logger object for this class. */
  static Logger logger = LogManager.getLogger(ProcessModel.class);

  /**
   * A map containing process names and their corresponding {@link ProcessSystem}
   * objects.
   */
  private Map<String, ProcessSystem> processes = new LinkedHashMap<>();

  /**
   * A map of group names to a list of process names in that group.
   *
   * <p>
   * We store process <em>names</em> here, pointing to the actual
   * {@code ProcessSystem} in
   * {@link #processes}. Alternatively, you can store the {@code ProcessSystem}
   * references directly.
   */
  private Map<String, List<String>> groups = new LinkedHashMap<>();

  private boolean runStep = false;
  private int maxIterations = 50;

  /**
   * Checks if the model is running in step mode.
   *
   * @return {@code true} if the model runs in step mode, otherwise {@code false}
   */
  public boolean isRunStep() {
    return runStep;
  }

  /**
   * Sets the step mode for the process.
   *
   * @param runStep {@code true} to run in step mode, {@code false} for continuous
   *                mode
   */
  public void setRunStep(boolean runStep) {
    this.runStep = runStep;
  }

  /**
   * Adds a process to the model.
   *
   * @param name    the name of the process to add
   * @param process the {@link ProcessSystem} instance to be added
   * @return {@code true} if the process was added successfully
   * @throws IllegalArgumentException if {@code name} is null or empty, if
   *                                  {@code process} is null,
   *                                  or if a process with the given name already
   *                                  exists
   */
  public boolean add(String name, ProcessSystem process) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("Name cannot be null or empty");
    }
    if (process == null) {
      throw new IllegalArgumentException("Process cannot be null");
    }
    if (processes.containsKey(name)) {
      throw new IllegalArgumentException("A process with the given name already exists");
    }
    process.setName(name);
    processes.put(name, process);
    return true;
  }

  /**
   * Retrieves a process by its name.
   *
   * @param name the name of the process to retrieve
   * @return the {@link ProcessSystem} corresponding to the given name,
   *         or {@code null} if no matching process is found
   */
  public ProcessSystem get(String name) {
    return processes.get(name);
  }

  /**
   * Removes a process by its name.
   *
   * @param name the name of the process to remove
   * @return {@code true} if the process was removed, otherwise {@code false}
   */
  public boolean remove(String name) {
    return processes.remove(name) != null;
  }

  /**
   * Creates a new group or clears it if it already exists.
   *
   * @param groupName the name of the group to create
   * @throws IllegalArgumentException if {@code groupName} is null or empty
   */
  public void createGroup(String groupName) {
    if (groupName == null || groupName.isEmpty()) {
      throw new IllegalArgumentException("Group name cannot be null or empty");
    }
    groups.put(groupName, new ArrayList<>());
  }

  /**
   * Adds a process (by its name) to a given group.
   *
   * @param groupName   the name of the group to which the process will be added
   * @param processName the name of the process to add
   * @throws IllegalArgumentException if there is no process with the given
   *                                  {@code processName}
   */
  public void addProcessToGroup(String groupName, String processName) {
    if (!processes.containsKey(processName)) {
      throw new IllegalArgumentException("No process with name " + processName + " found");
    }
    groups.computeIfAbsent(groupName, key -> new ArrayList<>());
    List<String> groupProcesses = groups.get(groupName);
    if (!groupProcesses.contains(processName)) {
      groupProcesses.add(processName);
    }
  }

  /**
   * Removes a process from a given group.
   *
   * @param groupName   the name of the group from which the process will be
   *                    removed
   * @param processName the name of the process to remove
   */
  public void removeProcessFromGroup(String groupName, String processName) {
    if (groups.containsKey(groupName)) {
      groups.get(groupName).remove(processName);
    }
  }

  /**
   * Runs all processes in the specified group, honoring the current mode (step or
   * continuous).
   *
   * <p>
   * If the group name doesn't exist or is empty, this method does nothing.
   *
   * @param groupName the name of the group to run
   */
  public void runGroup(String groupName) {
    if (!groups.containsKey(groupName) || groups.get(groupName).isEmpty()) {
      logger.debug("Group '{}' does not exist or is empty, nothing to run", groupName);
      return;
    }

    List<String> groupProcesses = groups.get(groupName);
    if (runStep) {
      // Step mode: run each process once in step mode
      for (String processName : groupProcesses) {
        try {
          if (Thread.currentThread().isInterrupted()) {
            logger.debug("Thread was interrupted, exiting runGroup()...");
            return;
          }
          processes.get(processName).run_step();
        } catch (Exception e) {
          System.err.println("Error running process step: " + e.getMessage());
          e.printStackTrace();
        }
      }
    } else {
      // Continuous mode
      int iterations = 0;
      while (!Thread.currentThread().isInterrupted() && !isGroupFinished(groupName)
          && iterations < maxIterations) {
        for (String processName : groupProcesses) {
          if (Thread.currentThread().isInterrupted()) {
            logger.debug("Thread was interrupted, exiting runGroup()...");
            return;
          }
          try {
            processes.get(processName).run();
          } catch (Exception e) {
            System.err.println("Error running process: " + e.getMessage());
            e.printStackTrace();
          }
        }
        iterations++;
      }
    }
  }

  /**
   * Checks if all processes in the specified group have finished.
   *
   * @param groupName the name of the group to check
   * @return {@code true} if the group exists (or not) and all processes within
   *         the group are finished,
   *         otherwise {@code false}
   */
  public boolean isGroupFinished(String groupName) {
    if (!groups.containsKey(groupName)) {
      // No group found -> treat as finished or handle differently if needed
      return true;
    }
    for (String processName : groups.get(groupName)) {
      ProcessSystem process = processes.get(processName);
      if (process != null && !process.solved()) {
        return false;
      }
    }
    return true;
  }

  /**
   * The core run method for this model, which iterates over all processes.
   *
   * <p>
   * If {@code runStep} is {@code true}, each process is run exactly once in step
   * mode.
   * Otherwise, processes are run continuously up to {@code maxIterations} or
   * until
   * {@link #isFinished()} is {@code true}.
   */
  @Override
  public void run() {
    if (runStep) {
      // Step mode: just run each process once
      for (ProcessSystem process : processes.values()) {
        try {
          if (Thread.currentThread().isInterrupted()) {
            logger.debug("Thread was interrupted, exiting run()...");
            return;
          }
          process.run_step();
        } catch (Exception e) {
          System.err.println("Error running process step: " + e.getMessage());
          e.printStackTrace();
        }
      }
    } else {
      // Continuous mode
      int iterations = 0;
      while (!Thread.currentThread().isInterrupted() && !isFinished()
          && iterations < maxIterations) {
        for (ProcessSystem process : processes.values()) {
          if (Thread.currentThread().isInterrupted()) {
            logger.debug("Thread was interrupted, exiting run()...");
            return;
          }
          try {
            process.run(); // the process's continuous run
          } catch (Exception e) {
            System.err.println("Error running process: " + e.getMessage());
            e.printStackTrace();
          }
        }
        iterations++;
      }
    }
  }

  /**
   * Starts this model in a new thread and returns that thread.
   *
   * @return the newly created {@link Thread} running this {@code ProcessModel}
   */
  public Thread runAsThread() {
    Thread processThread = new Thread(this);
    processThread.start();
    return processThread;
  }

  /**
   * Checks if all processes in the model have finished.
   *
   * @return {@code true} if all processes are finished, otherwise {@code false}
   */
  public boolean isFinished() {
    for (ProcessSystem process : processes.values()) {
      if (!process.solved()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Runs all processes in a single step (used outside of the threaded model).
   */
  public void runStep() {
    for (ProcessSystem process : processes.values()) {
      try {
        if (Thread.currentThread().isInterrupted()) {
          logger.debug("Thread was interrupted, exiting runStep()...");
          return;
        }
        process.run_step();
      } catch (Exception e) {
        System.err.println("Error in runStep: " + e.getMessage());
        e.printStackTrace();
      }
    }
  }

  /**
   * Creates separate threads for each process, if needed.
   *
   * @return a map of process names to their corresponding {@link Thread} objects
   */
  public Map<String, Thread> getThreads() {
    Map<String, Thread> threads = new LinkedHashMap<>();
    try {
      for (ProcessSystem process : processes.values()) {
        Thread thread = new Thread(process);
        thread.setName(process.getName() + " thread");
        threads.put(process.getName(), thread);
      }
    } catch (Exception ex) {
      logger.debug(ex.getMessage(), ex);
    }
    return threads;
  }

  /**
   * Calculates the total power consumption of all processes in the specified
   * unit.
   *
   * @param unit the power unit (e.g., "kW", "MW")
   * @return the total power consumption in the given unit
   */
  public double getPower(String unit) {
    double totalPower = 0.0;
    for (ProcessSystem process : processes.values()) {
      totalPower += process.getPower(unit);
    }
    return totalPower;
  }

  /**
   * Calculates the total heater duty for all processes in the specified unit.
   *
   * @param unit the heater duty unit (e.g., "kW", "MW")
   * @return the total heater duty in the given unit
   */
  public double getHeaterDuty(String unit) {
    double totalDuty = 0.0;
    for (ProcessSystem process : processes.values()) {
      totalDuty += process.getHeaterDuty(unit);
    }
    return totalDuty;
  }

  /**
   * Calculates the total cooler duty for all processes in the specified unit.
   *
   * @param unit the cooler duty unit (e.g., "kW", "MW")
   * @return the total cooler duty in the given unit
   */
  public double getCoolerDuty(String unit) {
    double totalDuty = 0.0;
    for (ProcessSystem process : processes.values()) {
      totalDuty += process.getCoolerDuty(unit);
    }
    return totalDuty;
  }
}
