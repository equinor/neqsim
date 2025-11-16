package neqsim.process.processmodel;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.util.report.Report;

/**
 * <p>
 * ProcessModel class. Manages a collection of processes that can be run in steps or continuously.
 * </p>
 *
 * @author ESOL
 */
public class ProcessModel implements Runnable {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ProcessModel.class);
  private Map<String, ProcessSystem> processes = new LinkedHashMap<>();

  private boolean runStep = false;
  private int maxIterations = 50;

  /**
   * Checks if the model is running in step mode.
   *
   * @return a boolean
   */
  public boolean isRunStep() {
    return runStep;
  }

  /**
   * Sets the step mode for the process.
   *
   * @param runStep a boolean
   */
  public void setRunStep(boolean runStep) {
    this.runStep = runStep;
  }

  /**
   * Adds a process to the model.
   *
   * @param name a {@link java.lang.String} object
   * @param process a {@link neqsim.process.processmodel.ProcessSystem} object
   * @return a boolean
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
   * @param name a {@link java.lang.String} object
   * @return a {@link neqsim.process.processmodel.ProcessSystem} object
   */
  public ProcessSystem get(String name) {
    return processes.get(name);
  }

  /**
   * Removes a process by its name.
   *
   * @param name a {@link java.lang.String} object
   * @return a boolean
   */
  public boolean remove(String name) {
    return processes.remove(name) != null;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * - If runStep == true, each process is run in "step" mode exactly once. - Otherwise (continuous
   * mode), it loops up to maxIterations or until all processes are finished (isFinished() == true).
   * </p>
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
   *
   * @return a {@link java.lang.Thread} object
   */
  public Thread runAsThread() {
    Thread processThread = new Thread(this);
    processThread.start();
    return processThread;
  }

  /**
   * Checks if all processes are finished.
   *
   * @return a boolean
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
   *
   * @return a {@link java.util.Map} object
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
   * Retrieves a list of all processes.
   *
   * @return a {@link java.util.Collection} of {@link neqsim.process.processmodel.ProcessSystem}
   *         objects
   */
  public Collection<ProcessSystem> getAllProcesses() {
    return processes.values();
  }

  /**
   * Check mass balance of all unit operations in all processes.
   *
   * @param unit unit for mass flow rate (e.g., "kg/sec", "kg/hr", "mole/sec")
   * @return a map with process name and unit operation name as key and mass balance result as value
   */
  public Map<String, Map<String, ProcessSystem.MassBalanceResult>> checkMassBalance(String unit) {
    Map<String, Map<String, ProcessSystem.MassBalanceResult>> allMassBalanceResults =
        new LinkedHashMap<>();
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      String processName = entry.getKey();
      ProcessSystem process = entry.getValue();
      Map<String, ProcessSystem.MassBalanceResult> massBalanceResults =
          process.checkMassBalance(unit);
      allMassBalanceResults.put(processName, massBalanceResults);
    }
    return allMassBalanceResults;
  }

  /**
   * Check mass balance of all unit operations in all processes using kg/sec.
   *
   * @return a map with process name and unit operation name as key and mass balance result as value
   *         in kg/sec
   */
  public Map<String, Map<String, ProcessSystem.MassBalanceResult>> checkMassBalance() {
    return checkMassBalance("kg/sec");
  }

  /**
   * Get unit operations that failed mass balance check in all processes based on percentage error
   * threshold.
   *
   * @param unit unit for mass flow rate (e.g., "kg/sec", "kg/hr", "mole/sec")
   * @param percentThreshold percentage error threshold (default: 0.1%)
   * @return a map with process name and a map of failed unit operation names and their mass balance
   *         results
   */
  public Map<String, Map<String, ProcessSystem.MassBalanceResult>> getFailedMassBalance(String unit,
      double percentThreshold) {
    Map<String, Map<String, ProcessSystem.MassBalanceResult>> allFailedResults =
        new LinkedHashMap<>();
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      String processName = entry.getKey();
      ProcessSystem process = entry.getValue();
      Map<String, ProcessSystem.MassBalanceResult> failedResults =
          process.getFailedMassBalance(unit, percentThreshold);
      if (!failedResults.isEmpty()) {
        allFailedResults.put(processName, failedResults);
      }
    }
    return allFailedResults;
  }

  /**
   * Get unit operations that failed mass balance check in all processes using kg/sec and default
   * threshold.
   *
   * @return a map with process name and a map of failed unit operation names and their mass balance
   *         results
   */
  public Map<String, Map<String, ProcessSystem.MassBalanceResult>> getFailedMassBalance() {
    Map<String, Map<String, ProcessSystem.MassBalanceResult>> allFailedResults =
        new LinkedHashMap<>();
    for (ProcessSystem process : processes.values()) {
      Map<String, ProcessSystem.MassBalanceResult> failedResults = process.getFailedMassBalance();
      if (!failedResults.isEmpty()) {
        allFailedResults.put(process.getName(), failedResults);
      }
    }
    return allFailedResults;
  }

  /**
   * Get unit operations that failed mass balance check in all processes using specified threshold.
   *
   * @param percentThreshold percentage error threshold
   * @return a map with process name and a map of failed unit operation names and their mass balance
   *         results in kg/sec
   */
  public Map<String, Map<String, ProcessSystem.MassBalanceResult>> getFailedMassBalance(
      double percentThreshold) {
    return getFailedMassBalance("kg/sec", percentThreshold);
  }

  /**
   * Get a formatted mass balance report for all processes.
   *
   * @param unit unit for mass flow rate (e.g., "kg/sec", "kg/hr", "mole/sec")
   * @return a formatted string report with process name and mass balance results
   */
  public String getMassBalanceReport(String unit) {
    StringBuilder report = new StringBuilder();
    Map<String, Map<String, ProcessSystem.MassBalanceResult>> allResults = checkMassBalance(unit);

    for (Map.Entry<String, Map<String, ProcessSystem.MassBalanceResult>> processEntry : allResults
        .entrySet()) {
      report.append("\nProcess: ").append(processEntry.getKey()).append("\n");
      report.append(String.format("%0" + 60 + "d", 0).replace('0', '=')).append("\n");

      Map<String, ProcessSystem.MassBalanceResult> unitResults = processEntry.getValue();
      if (unitResults.isEmpty()) {
        report.append("No unit operations found.\n");
      } else {
        for (Map.Entry<String, ProcessSystem.MassBalanceResult> unitEntry : unitResults
            .entrySet()) {
          String unitName = unitEntry.getKey();
          ProcessSystem.MassBalanceResult result = unitEntry.getValue();
          report.append(String.format("  %-30s: %s\n", unitName, result.toString()));
        }
      }
    }
    return report.toString();
  }

  /**
   * Get a formatted mass balance report for all processes using kg/sec.
   *
   * @return a formatted string report with process name and mass balance results
   */
  public String getMassBalanceReport() {
    return getMassBalanceReport("kg/sec");
  }

  /**
   * Get a formatted report of failed mass balance checks for all processes.
   *
   * @param unit unit for mass flow rate (e.g., "kg/sec", "kg/hr", "mole/sec")
   * @param percentThreshold percentage error threshold
   * @return a formatted string report with process name and failed unit operations
   */
  public String getFailedMassBalanceReport(String unit, double percentThreshold) {
    StringBuilder report = new StringBuilder();
    Map<String, Map<String, ProcessSystem.MassBalanceResult>> failedResults =
        getFailedMassBalance(unit, percentThreshold);

    if (failedResults.isEmpty()) {
      report.append("All unit operations passed mass balance check.\n");
    } else {
      for (Map.Entry<String, Map<String, ProcessSystem.MassBalanceResult>> processEntry : failedResults
          .entrySet()) {
        report.append("\nProcess: ").append(processEntry.getKey()).append("\n");
        report.append(String.format("%0" + 60 + "d", 0).replace('0', '=')).append("\n");

        Map<String, ProcessSystem.MassBalanceResult> unitResults = processEntry.getValue();
        for (Map.Entry<String, ProcessSystem.MassBalanceResult> unitEntry : unitResults
            .entrySet()) {
          String unitName = unitEntry.getKey();
          ProcessSystem.MassBalanceResult result = unitEntry.getValue();
          report.append(String.format("  %-30s: %s\n", unitName, result.toString()));
        }
      }
    }
    return report.toString();
  }

  /**
   * Get a formatted report of failed mass balance checks for all processes using kg/sec and default
   * threshold.
   *
   * @return a formatted string report with process name and failed unit operations
   */
  public String getFailedMassBalanceReport() {
    return getFailedMassBalanceReport("kg/sec", 0.1);
  }

  /**
   * Get a formatted report of failed mass balance checks for all processes using specified
   * threshold.
   *
   * @param percentThreshold percentage error threshold
   * @return a formatted string report with process name and failed unit operations
   */
  public String getFailedMassBalanceReport(double percentThreshold) {
    return getFailedMassBalanceReport("kg/sec", percentThreshold);
  }

  /**
   * <p>
   * getReport_json.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getReport_json() {
    return new Report(this).generateJsonReport();
  }
}
