package neqsim.process.processmodel;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.util.report.Report;
import neqsim.util.validation.ValidationResult;

/**
 * <p>
 * ProcessModel class. Manages a collection of processes that can be run in steps or continuously.
 * </p>
 *
 * <p>
 * This class supports serialization via {@link #saveToNeqsim(String)} and
 * {@link #loadFromNeqsim(String)} for full model persistence.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ProcessModel implements Runnable, Serializable {
  private static final long serialVersionUID = 1001L;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ProcessModel.class);
  private Map<String, ProcessSystem> processes = new LinkedHashMap<>();

  private boolean runStep = false;
  private int maxIterations = 50;
  private boolean useOptimizedExecution = true;

  // Convergence tolerances (relative errors)
  private double flowTolerance = 1e-4;
  private double temperatureTolerance = 1e-4;
  private double pressureTolerance = 1e-4;

  // Convergence tracking
  private int lastIterationCount = 0;
  private double lastMaxFlowError = Double.MAX_VALUE;
  private double lastMaxTemperatureError = Double.MAX_VALUE;
  private double lastMaxPressureError = Double.MAX_VALUE;
  private boolean modelConverged = false;

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
   * Check if optimized execution is enabled for individual ProcessSystems.
   *
   * <p>
   * When enabled (default), each ProcessSystem uses {@link ProcessSystem#runOptimized()} which
   * auto-selects the best execution strategy based on topology.
   * </p>
   *
   * @return true if optimized execution is enabled
   */
  public boolean isUseOptimizedExecution() {
    return useOptimizedExecution;
  }

  /**
   * Enable or disable optimized execution for individual ProcessSystems.
   *
   * <p>
   * When enabled (default), each ProcessSystem uses {@link ProcessSystem#runOptimized()} which
   * auto-selects the best execution strategy (parallel for feed-forward, hybrid for recycle
   * processes). When disabled, uses standard sequential {@link ProcessSystem#run()}.
   * </p>
   *
   * @param useOptimizedExecution true to enable optimized execution
   */
  public void setUseOptimizedExecution(boolean useOptimizedExecution) {
    this.useOptimizedExecution = useOptimizedExecution;
  }

  /**
   * Get the maximum number of iterations for the model.
   *
   * @return maximum number of iterations
   */
  public int getMaxIterations() {
    return maxIterations;
  }

  /**
   * Set the maximum number of iterations for the model.
   *
   * @param maxIterations maximum number of iterations
   */
  public void setMaxIterations(int maxIterations) {
    this.maxIterations = maxIterations;
  }

  /**
   * Get flow tolerance for convergence check (relative error).
   *
   * @return flow tolerance
   */
  public double getFlowTolerance() {
    return flowTolerance;
  }

  /**
   * Set flow tolerance for convergence check (relative error).
   *
   * @param flowTolerance relative tolerance for flow rate convergence (e.g., 1e-4 = 0.01%)
   */
  public void setFlowTolerance(double flowTolerance) {
    this.flowTolerance = flowTolerance;
  }

  /**
   * Get temperature tolerance for convergence check (relative error).
   *
   * @return temperature tolerance
   */
  public double getTemperatureTolerance() {
    return temperatureTolerance;
  }

  /**
   * Set temperature tolerance for convergence check (relative error).
   *
   * @param temperatureTolerance relative tolerance for temperature convergence
   */
  public void setTemperatureTolerance(double temperatureTolerance) {
    this.temperatureTolerance = temperatureTolerance;
  }

  /**
   * Get pressure tolerance for convergence check (relative error).
   *
   * @return pressure tolerance
   */
  public double getPressureTolerance() {
    return pressureTolerance;
  }

  /**
   * Set pressure tolerance for convergence check (relative error).
   *
   * @param pressureTolerance relative tolerance for pressure convergence
   */
  public void setPressureTolerance(double pressureTolerance) {
    this.pressureTolerance = pressureTolerance;
  }

  /**
   * Set all tolerances at once.
   *
   * @param tolerance relative tolerance for all variables (flow, temperature, pressure)
   */
  public void setTolerance(double tolerance) {
    this.flowTolerance = tolerance;
    this.temperatureTolerance = tolerance;
    this.pressureTolerance = tolerance;
  }

  /**
   * Get the number of iterations from the last run.
   *
   * @return iteration count
   */
  public int getLastIterationCount() {
    return lastIterationCount;
  }

  /**
   * Check if the model converged in the last run.
   *
   * @return true if converged
   */
  public boolean isModelConverged() {
    return modelConverged;
  }

  /**
   * Get maximum flow error from the last iteration.
   *
   * @return maximum relative flow error
   */
  public double getLastMaxFlowError() {
    return lastMaxFlowError;
  }

  /**
   * Get maximum temperature error from the last iteration.
   *
   * @return maximum relative temperature error
   */
  public double getLastMaxTemperatureError() {
    return lastMaxTemperatureError;
  }

  /**
   * Get maximum pressure error from the last iteration.
   *
   * @return maximum relative pressure error
   */
  public double getLastMaxPressureError() {
    return lastMaxPressureError;
  }

  /**
   * Get the maximum error across all variables (flow, temperature, pressure).
   *
   * <p>
   * This is the largest relative error from the last iteration, useful for quick convergence check.
   * </p>
   *
   * @return maximum relative error across all variables
   */
  public double getError() {
    return Math.max(lastMaxFlowError, Math.max(lastMaxTemperatureError, lastMaxPressureError));
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
   * If forceIteration is true, the loop runs all maxIterations regardless of convergence.
   * </p>
   *
   * <p>
   * When {@link #isUseOptimizedExecution()} is true (default), each ProcessSystem uses
   * {@link ProcessSystem#runOptimized()} for best performance.
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
      // Reset convergence tracking
      lastIterationCount = 0;
      modelConverged = false;
      lastMaxFlowError = Double.MAX_VALUE;
      lastMaxTemperatureError = Double.MAX_VALUE;
      lastMaxPressureError = Double.MAX_VALUE;

      // Capture initial stream states for convergence tracking
      Map<String, double[]> previousStreamStates = captureStreamStates();

      int iterations = 0;
      while (!Thread.currentThread().isInterrupted() && iterations < maxIterations) {
        // Run all processes
        for (ProcessSystem process : processes.values()) {
          if (Thread.currentThread().isInterrupted()) {
            logger.debug("Thread was interrupted, exiting run()...");
            return;
          }
          try {
            if (useOptimizedExecution) {
              process.runOptimized();
            } else {
              process.run();
            }
          } catch (Exception e) {
            System.err.println("Error running process: " + e.getMessage());
            e.printStackTrace();
          }
        }
        iterations++;

        // Capture current stream states and calculate errors
        Map<String, double[]> currentStreamStates = captureStreamStates();
        double[] errors = calculateConvergenceErrors(previousStreamStates, currentStreamStates);
        lastMaxFlowError = errors[0];
        lastMaxTemperatureError = errors[1];
        lastMaxPressureError = errors[2];

        // Check if model has converged
        boolean allProcessesSolved = isFinished();
        boolean valuesConverged =
            lastMaxFlowError < flowTolerance && lastMaxTemperatureError < temperatureTolerance
                && lastMaxPressureError < pressureTolerance;

        if (logger.isDebugEnabled()) {
          logger.debug("Iteration " + iterations + ": flowErr=" + lastMaxFlowError + ", tempErr="
              + lastMaxTemperatureError + ", pressErr=" + lastMaxPressureError + ", allSolved="
              + allProcessesSolved + ", valuesConverged=" + valuesConverged);
        }

        // Converged if all processes solved AND values are not changing
        if (allProcessesSolved && valuesConverged && iterations > 1) {
          modelConverged = true;
          logger.debug("ProcessModel converged after " + iterations + " iterations");
          break;
        }

        // Update previous states for next iteration
        previousStreamStates = currentStreamStates;
      }
      lastIterationCount = iterations;

      if (!modelConverged && iterations >= maxIterations) {
        logger.warn("ProcessModel reached max iterations (" + maxIterations
            + ") without full convergence. Flow error: " + lastMaxFlowError + ", Temp error: "
            + lastMaxTemperatureError);
      }
    }
  }

  /**
   * Capture current state of all streams in all processes.
   *
   * @return map of stream name to [flowRate, temperature, pressure]
   */
  private Map<String, double[]> captureStreamStates() {
    Map<String, double[]> states = new LinkedHashMap<>();
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      String processName = entry.getKey();
      ProcessSystem process = entry.getValue();
      for (Object unit : process.getUnitOperations()) {
        if (unit instanceof StreamInterface) {
          StreamInterface stream = (StreamInterface) unit;
          String key = processName + "." + stream.getName();
          try {
            double flow = stream.getFlowRate("kg/hr");
            double temp = stream.getTemperature("K");
            double press = stream.getPressure("bara");
            states.put(key, new double[] {flow, temp, press});
          } catch (Exception e) {
            // Skip streams that can't be read
          }
        }
      }
    }
    return states;
  }

  /**
   * Calculate maximum relative errors between previous and current stream states.
   *
   * @param previous previous stream states
   * @param current current stream states
   * @return array of [maxFlowError, maxTempError, maxPressError]
   */
  private double[] calculateConvergenceErrors(Map<String, double[]> previous,
      Map<String, double[]> current) {
    double maxFlowErr = 0.0;
    double maxTempErr = 0.0;
    double maxPressErr = 0.0;

    for (String key : current.keySet()) {
      if (previous.containsKey(key)) {
        double[] prev = previous.get(key);
        double[] curr = current.get(key);

        // Flow rate relative error (with min threshold to avoid div by zero)
        double flowBase = Math.max(Math.abs(prev[0]), 1e-10);
        double flowErr = Math.abs(curr[0] - prev[0]) / flowBase;
        maxFlowErr = Math.max(maxFlowErr, flowErr);

        // Temperature relative error (use Kelvin to avoid issues near 0)
        double tempBase = Math.max(prev[1], 1.0);
        double tempErr = Math.abs(curr[1] - prev[1]) / tempBase;
        maxTempErr = Math.max(maxTempErr, tempErr);

        // Pressure relative error
        double pressBase = Math.max(prev[2], 1e-10);
        double pressErr = Math.abs(curr[2] - prev[2]) / pressBase;
        maxPressErr = Math.max(maxPressErr, pressErr);
      }
    }

    return new double[] {maxFlowErr, maxTempErr, maxPressErr};
  }

  /**
   * Get a summary of the convergence status after running the model.
   *
   * @return formatted convergence summary string
   */
  public String getConvergenceSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== ProcessModel Convergence Summary ===\n");
    sb.append("Converged: ").append(modelConverged ? "YES" : "NO").append("\n");
    sb.append("Iterations: ").append(lastIterationCount).append(" / ").append(maxIterations)
        .append("\n");
    sb.append("\nFinal Errors (relative):\n");
    sb.append(String.format("  Flow rate:    %.2e (tolerance: %.2e) %s\n", lastMaxFlowError,
        flowTolerance, lastMaxFlowError < flowTolerance ? "OK" : "NOT CONVERGED"));
    sb.append(String.format("  Temperature:  %.2e (tolerance: %.2e) %s\n", lastMaxTemperatureError,
        temperatureTolerance,
        lastMaxTemperatureError < temperatureTolerance ? "OK" : "NOT CONVERGED"));
    sb.append(String.format("  Pressure:     %.2e (tolerance: %.2e) %s\n", lastMaxPressureError,
        pressureTolerance, lastMaxPressureError < pressureTolerance ? "OK" : "NOT CONVERGED"));

    sb.append("\nProcess Status:\n");
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      sb.append(String.format("  %-30s: %s\n", entry.getKey(),
          entry.getValue().solved() ? "SOLVED" : "NOT SOLVED"));
    }
    return sb.toString();
  }

  /**
   * Gets a combined execution partition analysis for all ProcessSystems.
   *
   * <p>
   * This method provides insight into how each ProcessSystem will be executed, including:
   * </p>
   * <ul>
   * <li>Whether each system has recycle loops</li>
   * <li>Number of units and parallel levels</li>
   * <li>Which execution strategy will be used</li>
   * </ul>
   *
   * @return combined execution partition info for all ProcessSystems
   */
  public String getExecutionPartitionInfo() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== ProcessModel Execution Analysis ===\n");
    sb.append("Total ProcessSystems: ").append(processes.size()).append("\n");
    sb.append("Optimized execution: ").append(useOptimizedExecution ? "enabled" : "disabled")
        .append("\n\n");

    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      sb.append("--- ProcessSystem: ").append(entry.getKey()).append(" ---\n");
      ProcessSystem process = entry.getValue();
      sb.append("Units: ").append(process.getUnitOperations().size()).append("\n");
      sb.append("Has recycles: ").append(process.hasRecycleLoops()).append("\n");
      if (useOptimizedExecution) {
        sb.append("Strategy: ")
            .append(process.hasRecycleLoops() ? "Hybrid (parallel + iterative)" : "Parallel")
            .append("\n");
      } else {
        sb.append("Strategy: Sequential\n");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  /**
   * Runs this model in a separate thread using the global NeqSim thread pool.
   *
   * <p>
   * This method submits the model to the shared {@link neqsim.util.NeqSimThreadPool} and returns a
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
   * Starts this model in a new thread and returns that thread.
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

  /**
   * Validates the setup of all processes in this model.
   *
   * <p>
   * This method iterates through all ProcessSystems and validates each one. The results are
   * aggregated into a single ValidationResult. Use this method before running the model to identify
   * configuration issues.
   * </p>
   *
   * @return a {@link neqsim.util.validation.ValidationResult} containing all validation issues
   *         across all processes
   */
  public ValidationResult validateSetup() {
    ValidationResult result = new ValidationResult();

    // Check if model has any processes
    if (processes.isEmpty()) {
      result.addError("ProcessModel", "ProcessModel has no processes added",
          "Add at least one ProcessSystem using add(name, process)");
    }

    // Validate each ProcessSystem
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      String processName = entry.getKey();
      ProcessSystem process = entry.getValue();
      ValidationResult processResult = process.validateSetup();

      // Add all issues from the process, prefixed with process name
      for (ValidationResult.ValidationIssue issue : processResult.getIssues()) {
        if (issue.getSeverity() == ValidationResult.Severity.CRITICAL) {
          result.addError("[" + processName + "] " + issue.getCategory(), issue.getMessage(),
              issue.getRemediation());
        } else {
          result.addWarning("[" + processName + "] " + issue.getCategory(), issue.getMessage(),
              issue.getRemediation());
        }
      }
    }

    return result;
  }

  /**
   * Validates all processes and returns results organized by process name.
   *
   * <p>
   * This method provides detailed validation results for each ProcessSystem separately, making it
   * easier to identify which process has issues.
   * </p>
   *
   * @return a {@link java.util.Map} mapping process names to their validation results
   */
  public Map<String, ValidationResult> validateAll() {
    Map<String, ValidationResult> results = new LinkedHashMap<>();

    // Add ProcessModel-level validation
    ValidationResult modelResult = new ValidationResult();
    if (processes.isEmpty()) {
      modelResult.addError("ProcessModel", "ProcessModel has no processes added",
          "Add at least one ProcessSystem using add(name, process)");
    }
    results.put("ProcessModel", modelResult);

    // Validate each ProcessSystem
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      String processName = entry.getKey();
      ProcessSystem process = entry.getValue();
      results.put(processName, process.validateSetup());
    }

    return results;
  }

  /**
   * Checks if all processes in the model are ready to run.
   *
   * <p>
   * This is a convenience method that returns true if no CRITICAL validation errors exist across
   * all processes. Use this for a quick go/no-go check before running the model.
   * </p>
   *
   * @return true if no critical validation errors exist, false otherwise
   */
  public boolean isReadyToRun() {
    ValidationResult result = validateSetup();
    // Check if there are any CRITICAL errors
    for (ValidationResult.ValidationIssue issue : result.getIssues()) {
      if (issue.getSeverity() == ValidationResult.Severity.CRITICAL) {
        return false;
      }
    }
    return true;
  }

  /**
   * Get a formatted validation report for all processes.
   *
   * <p>
   * This method provides a human-readable summary of all validation issues across all processes in
   * the model.
   * </p>
   *
   * @return a formatted validation report string
   */
  public String getValidationReport() {
    StringBuilder report = new StringBuilder();
    report.append("=== ProcessModel Validation Report ===\n\n");

    Map<String, ValidationResult> allResults = validateAll();

    int totalIssues = 0;
    int criticalCount = 0;
    int majorCount = 0;

    for (Map.Entry<String, ValidationResult> entry : allResults.entrySet()) {
      String name = entry.getKey();
      ValidationResult result = entry.getValue();

      if (!result.getIssues().isEmpty()) {
        report.append("--- ").append(name).append(" ---\n");
        for (ValidationResult.ValidationIssue issue : result.getIssues()) {
          report.append("  [").append(issue.getSeverity()).append("] ");
          report.append(issue.getMessage()).append("\n");
          if (issue.getRemediation() != null && !issue.getRemediation().isEmpty()) {
            report.append("    Fix: ").append(issue.getRemediation()).append("\n");
          }
          totalIssues++;
          if (issue.getSeverity() == ValidationResult.Severity.CRITICAL) {
            criticalCount++;
          } else if (issue.getSeverity() == ValidationResult.Severity.MAJOR) {
            majorCount++;
          }
        }
        report.append("\n");
      }
    }

    if (totalIssues == 0) {
      report.append("No validation issues found. Model is ready to run.\n");
    } else {
      report.append("Summary: ").append(totalIssues).append(" issue(s) found");
      report.append(" (").append(criticalCount).append(" critical, ");
      report.append(majorCount).append(" major)\n");
      report.append("Ready to run: ").append(criticalCount == 0 ? "YES" : "NO").append("\n");
    }

    return report.toString();
  }

  // ============ NEQSIM FILE SERIALIZATION ============

  /**
   * Saves this ProcessModel (with all ProcessSystems) to a compressed .neqsim file.
   *
   * <p>
   * This is the recommended format for production use, providing compact storage with full model
   * state preservation including all ProcessSystems. The file can be loaded with
   * {@link #loadFromNeqsim(String)}.
   * </p>
   *
   * <p>
   * Example usage:
   *
   * <pre>
   * ProcessModel model = new ProcessModel();
   * model.add("upstream", upstreamProcess);
   * model.add("downstream", downstreamProcess);
   * model.run();
   * model.saveToNeqsim("multi_process_model.neqsim");
   * </pre>
   *
   * @param filename the file path to save to (recommended extension: .neqsim)
   * @return true if save was successful, false otherwise
   */
  public boolean saveToNeqsim(String filename) {
    boolean success = neqsim.util.serialization.NeqSimXtream.saveNeqsim(this, filename);
    if (success) {
      logger.info("ProcessModel saved to: " + filename);
    } else {
      logger.error("Failed to save ProcessModel to: " + filename);
    }
    return success;
  }

  /**
   * Loads a ProcessModel from a compressed .neqsim file.
   *
   * <p>
   * After loading, the model is automatically run to reinitialize calculations. This ensures the
   * internal state is consistent for all ProcessSystems.
   * </p>
   *
   * <p>
   * Example usage:
   *
   * <pre>
   * ProcessModel loaded = ProcessModel.loadFromNeqsim("multi_process_model.neqsim");
   * // Model is already run and ready to use
   * ProcessSystem upstream = loaded.get("upstream");
   * </pre>
   *
   * @param filename the file path to load from
   * @return the loaded ProcessModel, or null if loading fails
   */
  public static ProcessModel loadFromNeqsim(String filename) {
    try {
      Object loaded = neqsim.util.serialization.NeqSimXtream.openNeqsim(filename);
      if (loaded instanceof ProcessModel) {
        ProcessModel model = (ProcessModel) loaded;
        model.run();
        logger.info("ProcessModel loaded from: " + filename);
        return model;
      } else {
        logger.error("Loaded object is not a ProcessModel: "
            + (loaded != null ? loaded.getClass().getName() : "null"));
        return null;
      }
    } catch (IOException e) {
      logger.error("Failed to load ProcessModel from file: " + filename, e);
      return null;
    }
  }

  /**
   * Saves this ProcessModel with automatic format detection based on file extension.
   *
   * <p>
   * File format is determined by extension:
   * <ul>
   * <li>.neqsim → XStream compressed XML (full serialization)</li>
   * <li>.json → JSON state (lightweight, Git-friendly, requires ProcessModelState)</li>
   * <li>other → Java binary serialization (legacy)</li>
   * </ul>
   *
   * @param filename the file path to save to
   * @return true if save was successful
   */
  public boolean saveAuto(String filename) {
    if (filename.endsWith(".neqsim")) {
      return saveToNeqsim(filename);
    } else if (filename.endsWith(".json")) {
      return saveStateToFile(filename);
    } else {
      // Legacy binary serialization
      try (java.io.ObjectOutputStream oos =
          new java.io.ObjectOutputStream(new java.io.FileOutputStream(filename))) {
        oos.writeObject(this);
        logger.info("ProcessModel saved (binary) to: " + filename);
        return true;
      } catch (IOException e) {
        logger.error("Failed to save ProcessModel to: " + filename, e);
        return false;
      }
    }
  }

  /**
   * Loads a ProcessModel with automatic format detection based on file extension.
   *
   * <p>
   * File format is determined by extension:
   * <ul>
   * <li>.neqsim → XStream compressed XML (full serialization)</li>
   * <li>.json → JSON state (requires matching ProcessSystems already configured)</li>
   * <li>other → Java binary serialization (legacy)</li>
   * </ul>
   *
   * @param filename the file path to load from
   * @return the loaded ProcessModel, or null if loading fails
   */
  public static ProcessModel loadAuto(String filename) {
    if (filename.endsWith(".neqsim")) {
      return loadFromNeqsim(filename);
    } else if (filename.endsWith(".json")) {
      return loadStateFromFile(filename);
    } else {
      // Legacy binary serialization
      try (java.io.ObjectInputStream ois =
          new java.io.ObjectInputStream(new java.io.FileInputStream(filename))) {
        ProcessModel model = (ProcessModel) ois.readObject();
        model.run();
        logger.info("ProcessModel loaded (binary) from: " + filename);
        return model;
      } catch (IOException | ClassNotFoundException e) {
        logger.error("Failed to load ProcessModel from: " + filename, e);
        return null;
      }
    }
  }

  // ============ JSON STATE SERIALIZATION ============

  /**
   * Exports the current state of this ProcessModel to a JSON file.
   *
   * <p>
   * This exports state for all ProcessSystems in the model. The JSON format is Git-friendly and
   * human-readable, suitable for version control and diffing.
   * </p>
   *
   * @param filename the file path to save to (recommended extension: .json)
   * @return true if save was successful
   */
  public boolean saveStateToFile(String filename) {
    try {
      neqsim.process.processmodel.lifecycle.ProcessModelState state =
          neqsim.process.processmodel.lifecycle.ProcessModelState.fromProcessModel(this);
      state.saveToFile(filename);
      logger.info("ProcessModel state saved to: " + filename);
      return true;
    } catch (Exception e) {
      logger.error("Failed to save ProcessModel state to: " + filename, e);
      return false;
    }
  }

  /**
   * Loads ProcessModel state from a JSON file.
   *
   * <p>
   * Note: This returns a new ProcessModel with ProcessSystems initialized from the saved state.
   * Full reconstruction requires the original equipment configuration.
   * </p>
   *
   * @param filename the file path to load from
   * @return the loaded ProcessModel, or null if loading fails
   */
  public static ProcessModel loadStateFromFile(String filename) {
    try {
      neqsim.process.processmodel.lifecycle.ProcessModelState state =
          neqsim.process.processmodel.lifecycle.ProcessModelState.loadFromFile(filename);
      ProcessModel model = state.toProcessModel();
      logger.info("ProcessModel state loaded from: " + filename);
      return model;
    } catch (Exception e) {
      logger.error("Failed to load ProcessModel state from: " + filename, e);
      return null;
    }
  }

  /**
   * Exports the current state of this ProcessModel for inspection or modification.
   *
   * @return a ProcessModelState snapshot of the current model
   */
  public neqsim.process.processmodel.lifecycle.ProcessModelState exportState() {
    return neqsim.process.processmodel.lifecycle.ProcessModelState.fromProcessModel(this);
  }

  // ============ AUTO-SIZING METHODS ============

  /**
   * Auto-sizes all equipment in this model that implements
   * {@link neqsim.process.design.AutoSizeable}.
   *
   * <p>
   * This method iterates through all process systems in the model and calls autoSize() on each
   * equipment that implements the AutoSizeable interface. The equipment is sized using the default
   * safety factor (1.2 = 20% margin).
   * </p>
   *
   * <p>
   * <strong>Important:</strong> This method should be called AFTER running the process model so
   * that flow rates and conditions are known for sizing calculations.
   * </p>
   *
   * <p>
   * Example usage:
   * </p>
   * 
   * <pre>
   * ProcessModel model = new ProcessModel();
   * model.add("upstream", upstreamProcess);
   * model.add("downstream", downstreamProcess);
   * model.run();
   * model.autoSizeEquipment(); // Size all equipment based on actual flow rates
   * model.run(); // Re-run with sized equipment
   * </pre>
   *
   * @return the number of equipment items that were auto-sized
   */
  public int autoSizeEquipment() {
    return autoSizeEquipment(1.2);
  }

  /**
   * Auto-sizes all equipment in this model with the specified safety factor.
   *
   * <p>
   * This method iterates through all process systems in the model and calls autoSize() on each
   * equipment that implements the AutoSizeable interface.
   * </p>
   *
   * @param safetyFactor multiplier for design capacity, typically 1.1-1.3 (10-30% over design)
   * @return the number of equipment items that were auto-sized
   */
  public int autoSizeEquipment(double safetyFactor) {
    int count = 0;
    for (ProcessSystem processSystem : processes.values()) {
      count += processSystem.autoSizeEquipment(safetyFactor);
    }
    return count;
  }

  /**
   * Auto-sizes all equipment in this model using company-specific design standards.
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
    for (ProcessSystem processSystem : processes.values()) {
      count += processSystem.autoSizeEquipment(companyStandard, trDocument);
    }
    return count;
  }

  /**
   * Enables or disables capacity analysis for all equipment in all process systems.
   *
   * <p>
   * This is a convenience method that applies the setting to all equipment in all processes. When
   * disabled, equipment is excluded from:
   * <ul>
   * <li>System bottleneck detection</li>
   * <li>Capacity utilization summaries</li>
   * <li>Equipment near capacity lists</li>
   * <li>Optimization constraint checking</li>
   * </ul>
   * </p>
   *
   * @param enabled true to enable capacity analysis for all equipment, false to disable
   * @return the number of equipment items that were updated
   */
  public int setCapacityAnalysisEnabled(boolean enabled) {
    int count = 0;
    for (ProcessSystem processSystem : processes.values()) {
      count += processSystem.setCapacityAnalysisEnabled(enabled);
    }
    return count;
  }
}
