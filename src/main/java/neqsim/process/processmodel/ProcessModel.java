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
 * This class is a simple model that can run multiple processes in parallel. It
 * can run in two modes:
 * - Step mode: each process is run once in a loop, in the order they were
 * added. - Continuous mode:
 * each process is run in a loop until all processes are finished or a maximum
 * number of iterations
 * is reached.
 * 
 * You can also create groups of processes and run them separately.
 */
public class ProcessModel implements Runnable {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ProcessModel.class);

  /** Map of process name -> ProcessSystem. */
  private Map<String, ProcessSystem> processes = new LinkedHashMap<>();

  /**
   * Map of group name -> list of process names in that group.
   * 
   * We store process *names* here, pointing to the actual ProcessSystem in
   * `processes`.
   * Alternatively, you can store the ProcessSystem references directly.
   */
  private Map<String, List<String>> groups = new LinkedHashMap<>();

  private boolean runStep = false;
  private int maxIterations = 50;

  /**
   * Checks if the model is running in step mode.
   */
  public boolean isRunStep() {
    return runStep;
  }

  /**
   * Sets the step mode for the process.
   */
  public void setRunStep(boolean runStep) {
    this.runStep = runStep;
  }

  /**
   * Adds a process to the model.
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
   */
  public ProcessSystem get(String name) {
    return processes.get(name);
  }

  /**
   * Removes a process by its name.
   */
  public boolean remove(String name) {
    return processes.remove(name) != null;
  }

  /**
   * Creates a new group or clears it if it already exists.
   */
  public void createGroup(String groupName) {
    if (groupName == null || groupName.isEmpty()) {
      throw new IllegalArgumentException("Group name cannot be null or empty");
    }
    groups.put(groupName, new ArrayList<>());
  }

  /**
   * Add a process to a given group (by process name).
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
   * Remove a process from a given group.
   */
  public void removeProcessFromGroup(String groupName, String processName) {
    if (groups.containsKey(groupName)) {
      groups.get(groupName).remove(processName);
    }
  }

  /**
   * Runs all processes in the specified group (step or continuous).
   * 
   * If the group name doesn't exist or is empty, does nothing.
   */
  public void runGroup(String groupName) {
    if (!groups.containsKey(groupName) || groups.get(groupName).isEmpty()) {
      logger.debug("Group '{}' does not exist or is empty, nothing to run", groupName);
      return;
    }

    List<String> groupProcesses = groups.get(groupName);
    if (runStep) {
      // Step mode: just run each process once in step mode
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
   * Check if the group has all processes finished.
   */
  public boolean isGroupFinished(String groupName) {
    if (!groups.containsKey(groupName)) {
      // no group or group doesn't exist -> consider it "finished" or throw exception
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
   * The core run method.
   * 
   * - If runStep == true, each process is run in "step" mode exactly once. -
   * Otherwise (continuous
   * mode), it loops up to maxIterations or until all processes are finished
   * (isFinished() == true).
   */
  @Override
  public void run() {
    if (runStep) {
      // Step mode: just run each process once in step mode
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
   */
  public Thread runAsThread() {
    Thread processThread = new Thread(this);
    processThread.start();
    return processThread;
  }

  /**
   * Checks if all processes are finished.
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
   * Runs all processes in a single step (used outside of the thread model).
   */
  public void runStep() {
    for (ProcessSystem process : processes.values()) {
      try {
        if (Thread.currentThread().isInterrupted()) {
          logger.debug("Thread was interrupted, exiting run()...");
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
   * (Optional) Creates separate threads for each process (if you need them).
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
   * @param unit the unit of power to be used (e.g., "kW", "MW").
   * @return the total power consumption of all processes in the specified unit.
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
   * @param unit the unit for which the heater duty is to be calculated
   * @return the total heater duty for the specified unit
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
   * @param unit the unit for which the cooler duty is to be calculated
   * @return the total cooler duty for the specified unit
   */
  public double getCoolerDuty(String unit) {
    double totalDuty = 0.0;
    for (ProcessSystem process : processes.values()) {
      totalDuty += process.getCoolerDuty(unit);
    }
    return totalDuty;
  }
}
