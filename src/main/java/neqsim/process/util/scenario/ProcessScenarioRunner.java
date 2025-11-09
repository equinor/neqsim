package neqsim.process.util.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.logic.LogicState;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.ProcessSafetyScenario;
import neqsim.process.util.monitor.ScenarioKPI;

/**
 * Utility class for running process scenarios with integrated logic execution.
 * 
 * <p>
 * This runner coordinates:
 * <ul>
 * <li>Process system transient simulation</li>
 * <li>Process logic execution (ESD, startup, shutdown, etc.)</li>
 * <li>Scenario perturbation application</li>
 * <li>Status monitoring and reporting</li>
 * </ul>
 * 
 * <p>
 * Example usage:
 * 
 * <pre>
 * ProcessSystem system = new ProcessSystem();
 * // ... configure system ...
 * 
 * ProcessScenarioRunner runner = new ProcessScenarioRunner(system);
 * runner.addLogic(esdLogic);
 * runner.addLogic(startupLogic);
 * 
 * ProcessSafetyScenario scenario = ProcessSafetyScenario.builder("High Pressure")
 *     .customManipulator("Feed", stream -&gt; stream.setPressure(80.0, "bara")).build();
 * 
 * runner.runScenario("High Pressure Test", scenario, 60.0, 1.0);
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class ProcessScenarioRunner {
  private final ProcessSystem system;
  private final List<ProcessLogic> logicSequences;
  private UUID simulationId;

  /**
   * Creates a scenario runner for the given process system.
   *
   * @param system process system to simulate
   */
  public ProcessScenarioRunner(ProcessSystem system) {
    this.system = system;
    this.logicSequences = new ArrayList<>();
    this.simulationId = UUID.randomUUID();
  }

  /**
   * Ensures the process system has a valid steady-state solution before running scenarios. This
   * method should be called before running any scenarios to establish baseline conditions.
   * 
   * @throws RuntimeException if steady-state calculation fails
   */
  public void initializeSteadyState() {
    System.out.println("Initializing process system with steady-state solution...");
    try {
      // Validate system has units before running
      if (system.getUnitOperations().isEmpty()) {
        throw new IllegalStateException("Process system has no unit operations");
      }

      system.run();
      System.out.println("✓ Steady-state calculation completed successfully");

      // Validate results are reasonable
      validateProcessConditions();

      // Print initial process conditions
      System.out.println("Initial Process Conditions:");
      system.getUnitOperations().stream()
          .filter(unit -> unit.getName().contains("Separator") || unit.getName().contains("Stream"))
          .forEach(unit -> {
            try {
              if (unit instanceof neqsim.process.equipment.separator.Separator) {
                neqsim.process.equipment.separator.Separator sep =
                    (neqsim.process.equipment.separator.Separator) unit;
                System.out.printf("  %s: P=%.1f bara, T=%.1f °C%n", unit.getName(),
                    sep.getGasOutStream().getPressure("bara"),
                    sep.getGasOutStream().getTemperature("C"));
              } else if (unit instanceof neqsim.process.equipment.stream.Stream) {
                neqsim.process.equipment.stream.Stream stream =
                    (neqsim.process.equipment.stream.Stream) unit;
                System.out.printf("  %s: P=%.1f bara, T=%.1f °C, Flow=%.1f kg/hr%n", unit.getName(),
                    stream.getPressure("bara"), stream.getTemperature("C"),
                    stream.getFlowRate("kg/hr"));
              }
            } catch (Exception e) {
              System.out.println(
                  "  " + unit.getName() + ": Unable to read conditions - " + e.getMessage());
            }
          });
      System.out.println();

    } catch (Exception e) {
      System.err.println("✗ Failed to initialize steady-state: " + e.getMessage());
      throw new RuntimeException("Failed to initialize steady-state: " + e.getMessage(), e);
    }
  }

  /**
   * Validates that process conditions are within reasonable ranges to prevent simulation errors.
   */
  private void validateProcessConditions() {
    for (Object unit : system.getUnitOperations()) {
      try {
        if (unit instanceof neqsim.process.equipment.stream.Stream) {
          neqsim.process.equipment.stream.Stream stream =
              (neqsim.process.equipment.stream.Stream) unit;
          double pressure = stream.getPressure("bara");
          double temperature = stream.getTemperature("C");

          if (Double.isNaN(pressure) || Double.isInfinite(pressure) || pressure <= 0) {
            throw new IllegalStateException(
                "Invalid pressure in " + stream.getName() + ": " + pressure);
          }
          if (Double.isNaN(temperature) || Double.isInfinite(temperature)) {
            throw new IllegalStateException(
                "Invalid temperature in " + stream.getName() + ": " + temperature);
          }
        }
      } catch (Exception e) {
        // Log warning but don't fail initialization for non-critical validation issues
        System.out.println("Warning: Could not validate " + unit + ": " + e.getMessage());
      }
    }
  }

  /**
   * Adds a process logic sequence to be executed.
   *
   * @param logic logic sequence to add
   */
  public void addLogic(ProcessLogic logic) {
    if (!logicSequences.contains(logic)) {
      logicSequences.add(logic);
    }
  }

  /**
   * Removes a process logic sequence.
   *
   * @param logic logic sequence to remove
   */
  public void removeLogic(ProcessLogic logic) {
    logicSequences.remove(logic);
  }

  /**
   * Removes a logic sequence by name.
   * 
   * @param logicName name of the logic sequence to remove
   * @return true if logic was found and removed, false otherwise
   */
  public boolean removeLogic(String logicName) {
    ProcessLogic toRemove = findLogic(logicName);
    if (toRemove != null) {
      logicSequences.remove(toRemove);
      return true;
    }
    return false;
  }

  /**
   * Clears all registered logic sequences.
   */
  public void clearAllLogic() {
    logicSequences.clear();
  }

  /**
   * Gets all registered logic sequences.
   *
   * @return list of logic sequences
   */
  public List<ProcessLogic> getLogicSequences() {
    return new ArrayList<>(logicSequences);
  }

  /**
   * Runs a scenario with the given parameters.
   *
   * @param scenarioName descriptive name for logging
   * @param scenario scenario perturbations to apply (can be null)
   * @param duration simulation duration in seconds
   * @param timeStep time step in seconds
   * @return scenario execution summary
   */
  public ScenarioExecutionSummary runScenario(String scenarioName, ProcessSafetyScenario scenario,
      double duration, double timeStep) {
    return runScenarioWithLogic(scenarioName, scenario, duration, timeStep, null);
  }

  /**
   * Runs a scenario with only specific logic sequences enabled.
   * 
   * <p>
   * This allows you to run a scenario with a subset of registered logic sequences. Only the logic
   * sequences with names matching the provided list will be executed during this scenario.
   * 
   * <p>
   * Example:
   * 
   * <pre>
   * runner.addLogic(hippsLogic);
   * runner.addLogic(esdLogic);
   * runner.addLogic(startupLogic);
   * 
   * // Run scenario with only ESD logic active (HIPPS and startup will be ignored)
   * runner.runScenarioWithLogic("ESD Test", scenario, 30.0, 1.0, List.of("ESD Level 1"));
   * </pre>
   * 
   * @param scenarioName descriptive name for logging
   * @param scenario scenario perturbations to apply (can be null)
   * @param duration simulation duration in seconds
   * @param timeStep time step in seconds
   * @param enabledLogicNames names of logic sequences to enable (null = all logic enabled)
   * @return scenario execution summary
   */
  public ScenarioExecutionSummary runScenarioWithLogic(String scenarioName,
      ProcessSafetyScenario scenario, double duration, double timeStep,
      List<String> enabledLogicNames) {
    System.out.println("╔══════════════════════════════════════════════════════════════╗");
    System.out.printf("║  RUNNING SCENARIO: %-42s ║%n", scenarioName);
    System.out.println("╚══════════════════════════════════════════════════════════════╝");

    ScenarioExecutionSummary summary = new ScenarioExecutionSummary(scenarioName);

    // Determine which logic sequences to use
    List<ProcessLogic> activeLogic;
    if (enabledLogicNames == null || enabledLogicNames.isEmpty()) {
      // Use all registered logic
      activeLogic = logicSequences;
      System.out
          .println("Running with all " + logicSequences.size() + " registered logic sequences");
    } else {
      // Filter to only enabled logic
      activeLogic = new ArrayList<>();
      for (ProcessLogic logic : logicSequences) {
        if (enabledLogicNames.contains(logic.getName())) {
          activeLogic.add(logic);
        }
      }
      System.out.println("Running with " + activeLogic.size() + " of " + logicSequences.size()
          + " logic sequences:");
      for (ProcessLogic logic : activeLogic) {
        System.out.println("  - " + logic.getName());
      }
    }

    // Initialize KPI tracking
    ScenarioKPI.Builder kpiBuilder = ScenarioKPI.builder();
    double simulationStartTime = System.currentTimeMillis() / 1000.0;

    // Apply scenario perturbations
    if (scenario != null) {
      scenario.applyTo(system);
      System.out.println("Applied scenario perturbations:");
      if (!scenario.getBlockedOutletUnits().isEmpty()) {
        System.out.println("  - Blocked outlets: " + scenario.getBlockedOutletUnits());
      }
      if (!scenario.getUtilityLossUnits().isEmpty()) {
        System.out.println("  - Utility losses: " + scenario.getUtilityLossUnits());
      }
      if (!scenario.getControllerSetPointOverrides().isEmpty()) {
        System.out
            .println("  - Controller overrides: " + scenario.getControllerSetPointOverrides());
      }
      summary.setScenario(scenario);
    }

    // Print header for status monitoring
    System.out.println("Time(s) | Active Logic Sequences | Process Status");
    System.out.println("--------|------------------------|------------------");

    double time = 0.0;
    int stepCount = 0;
    int errorCount = 0;
    int consecutiveErrors = 0;
    final int MAX_CONSECUTIVE_ERRORS = 5;

    while (time < duration) {
      // Execute process logic (only enabled logic for this scenario)
      for (ProcessLogic logic : activeLogic) {
        if (logic.isActive()) {
          try {
            logic.execute(timeStep);
          } catch (Exception e) {
            System.out.println("Logic execution error at t=" + time + "s: " + e.getMessage());
            summary.addError("Logic error (" + logic.getName() + "): " + e.getMessage());
          }
        }
      }

      // Run transient simulation with enhanced error handling
      try {
        system.runTransient(timeStep, simulationId);
        consecutiveErrors = 0; // Reset counter on success

      } catch (Exception e) {
        errorCount++;
        consecutiveErrors++;

        String errorMsg =
            "Simulation error at t=" + String.format("%.1f", time) + "s: " + e.getMessage();
        System.out.println(errorMsg);
        summary.addError(errorMsg);

        // Stop simulation if too many consecutive errors
        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
          System.err
              .println("✗ Stopping simulation due to " + consecutiveErrors + " consecutive errors");
          summary.addError("Simulation stopped: Too many consecutive errors");
          break;
        }

        // Try to recover by skipping this time step
        System.out.println("  → Attempting to continue with next time step...");
      }

      // Validate process state periodically
      if (stepCount % 10 == 0) {
        try {
          validateProcessConditions();
        } catch (Exception e) {
          System.out
              .println("Warning: Process validation failed at t=" + time + "s: " + e.getMessage());
          summary.addWarning("Process validation warning: " + e.getMessage());
        }
      }

      // Print status every 5 seconds or when logic state changes
      if (time % 5.0 < timeStep || hasLogicStateChanged(activeLogic)) {
        printStatus(time, activeLogic);
      }

      time += timeStep;
      stepCount++;
    }

    // Calculate simulation duration
    double simulationEndTime = System.currentTimeMillis() / 1000.0;
    kpiBuilder.simulationDuration(duration);
    kpiBuilder.errorCount(errorCount);

    // Build KPI and attach to summary
    ScenarioKPI kpi = kpiBuilder.build();
    summary.setKPI(kpi);

    // Final status
    System.out.println("\n" + scenarioName + " completed after " + stepCount + " time steps.");
    if (errorCount > 0) {
      System.out.println("⚠ Warning: " + errorCount + " error(s) occurred during simulation");
    } else {
      System.out.println("✓ Simulation completed successfully with no errors");
    }
    printFinalSummary(summary, activeLogic);

    return summary;
  }

  /**
   * Checks if any logic sequence has changed state since last check.
   *
   * @param logicList list of logic sequences to check
   * @return true if state has changed
   */
  private boolean hasLogicStateChanged(List<ProcessLogic> logicList) {
    // Simple implementation - in practice, you'd track previous states
    for (ProcessLogic logic : logicList) {
      if (logic.getState() == LogicState.RUNNING) {
        return true;
      }
    }
    return false;
  }

  /**
   * Prints current status of scenario execution.
   *
   * @param time current simulation time
   * @param logicList list of logic sequences to monitor
   */
  private void printStatus(double time, List<ProcessLogic> logicList) {
    StringBuilder activeLogic = new StringBuilder();
    int activeCount = 0;

    for (ProcessLogic logic : logicList) {
      if (logic.isActive()) {
        if (activeCount > 0)
          activeLogic.append(", ");
        activeLogic.append(logic.getName()).append("(").append(getShortState(logic.getState()))
            .append(")");
        activeCount++;
      }
    }

    if (activeCount == 0) {
      activeLogic.append("None");
    }

    // Get key process parameters for monitoring
    String processStatus = getProcessStatus();

    System.out.printf("%7.1f | %-22s | %s%n", time,
        activeLogic.length() > 22 ? activeLogic.substring(0, 19) + "..." : activeLogic.toString(),
        processStatus);
  }

  /**
   * Gets a summary of key process parameters for status monitoring.
   */
  private String getProcessStatus() {
    try {
      // Find separator or key process unit for monitoring
      for (Object unit : system.getUnitOperations()) {
        if (unit instanceof neqsim.process.equipment.separator.Separator) {
          neqsim.process.equipment.separator.Separator sep =
              (neqsim.process.equipment.separator.Separator) unit;
          double pressure = sep.getGasOutStream().getPressure("bara");
          double temperature = sep.getGasOutStream().getTemperature("C");
          return String.format("P=%.1f bara, T=%.1f°C", pressure, temperature);
        }
      }

      // Fallback to first stream
      for (Object unit : system.getUnitOperations()) {
        if (unit instanceof neqsim.process.equipment.stream.Stream) {
          neqsim.process.equipment.stream.Stream stream =
              (neqsim.process.equipment.stream.Stream) unit;
          double pressure = stream.getPressure("bara");
          double flow = stream.getFlowRate("kg/hr");
          return String.format("P=%.1f bara, F=%.0f kg/hr", pressure, flow);
        }
      }

      return "Running";
    } catch (Exception e) {
      return "Running (status error)";
    }
  }

  /**
   * Gets short form of logic state for display.
   *
   * @param state logic state
   * @return abbreviated state string
   */
  private String getShortState(LogicState state) {
    switch (state) {
      case IDLE:
        return "IDLE";
      case RUNNING:
        return "RUN";
      case COMPLETED:
        return "DONE";
      case FAILED:
        return "FAIL";
      case PAUSED:
        return "PAUSE";
      case WAITING_PERMISSIVES:
        return "WAIT";
      default:
        return "?";
    }
  }

  /**
   * Prints final summary of scenario execution.
   *
   * @param summary execution summary
   * @param logicList list of logic sequences that were active in this scenario
   */
  private void printFinalSummary(ScenarioExecutionSummary summary, List<ProcessLogic> logicList) {
    System.out.println("\n=== SCENARIO SUMMARY ===");

    for (ProcessLogic logic : logicList) {
      System.out.println("Logic: " + logic.getName());
      System.out.println("  Final State: " + logic.getState());
      System.out.println("  Status: " + logic.getStatusDescription());

      summary.addLogicResult(logic.getName(), logic.getState(), logic.getStatusDescription());
    }

    if (summary.getErrors().isEmpty()) {
      System.out.println("Status: SUCCESSFUL");
    } else {
      System.out.println("Status: COMPLETED WITH ERRORS");
      for (String error : summary.getErrors()) {
        System.out.println("  Error: " + error);
      }
    }
  }

  /**
   * Resets all logic sequences to prepare for next scenario.
   */
  public void resetLogic() {
    for (ProcessLogic logic : logicSequences) {
      logic.reset();
    }
  }

  /**
   * Gets a new simulation ID for the next run.
   */
  public void renewSimulationId() {
    this.simulationId = UUID.randomUUID();
  }

  /**
   * Resets the system for the next scenario.
   * 
   * <p>
   * This method resets logic states and re-establishes steady-state conditions to ensure clean
   * starting conditions for each scenario.
   */
  public void reset() {
    System.out.println("\nResetting system for next scenario...");
    try {
      resetLogic();
      renewSimulationId();

      // Re-establish steady-state to ensure clean starting conditions
      System.out.println("Re-initializing steady-state...");
      system.run();
      System.out.println("✓ System reset complete\n");

    } catch (Exception e) {
      System.err.println("⚠ Warning: Error during system reset: " + e.getMessage());
      System.err.println("  → Continuing with partial reset");
    }
  }

  /**
   * Gets the process system being simulated.
   *
   * @return the process system
   */
  public ProcessSystem getSystem() {
    return system;
  }

  /**
   * Activates a logic sequence by name.
   * 
   * @param logicName name of the logic sequence to activate
   * @return true if logic was found and activated, false otherwise
   */
  public boolean activateLogic(String logicName) {
    for (ProcessLogic logic : logicSequences) {
      if (logic.getName().equals(logicName)) {
        logic.activate();
        return true;
      }
    }
    return false;
  }

  /**
   * Finds a logic sequence by name.
   * 
   * @param logicName name of the logic sequence to find
   * @return the logic sequence if found, null otherwise
   */
  public ProcessLogic findLogic(String logicName) {
    for (ProcessLogic logic : logicSequences) {
      if (logic.getName().equals(logicName)) {
        return logic;
      }
    }
    return null;
  }
}
