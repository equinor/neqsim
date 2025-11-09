package neqsim.process.util.scenario;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.safety.ProcessSafetyScenario;
import neqsim.process.util.monitor.KPIDashboard;

/**
 * Utility class for running and managing multiple test scenarios with automatic KPI collection.
 * 
 * <p>
 * This class simplifies the execution of multiple scenarios by:
 * <ul>
 * <li>Automating scenario execution with proper reset between scenarios</li>
 * <li>Collecting KPI data automatically into a dashboard</li>
 * <li>Providing consistent formatting and reporting</li>
 * <li>Handling scenario numbering and headers</li>
 * </ul>
 * 
 * <p>
 * Example usage:
 * 
 * <pre>
 * ProcessScenarioRunner runner = new ProcessScenarioRunner(processSystem);
 * runner.initializeSteadyState();
 * 
 * ScenarioTestRunner testRunner = new ScenarioTestRunner(runner);
 * 
 * // Execute scenarios
 * testRunner.executeScenario("Normal Startup", normalScenario, "System Startup", 30.0, 1.0);
 * testRunner.executeScenario("Manual ESD", esdScenario, "ESD Level 1", 25.0, 0.5);
 * 
 * // Display results
 * testRunner.displayDashboard();
 * </pre>
 * 
 * <p>
 * Batch execution example using builder:
 * 
 * <pre>
 * testRunner.printHeader();
 * testRunner.batch().add("Normal Startup", normalScenario, "System Startup", 30.0, 1.0)
 *     .add("Manual ESD", esdScenario, "ESD Level 1", 25.0, 0.5).addDelayed("High Pressure",
 *         highPressureScenario, "ESD Level 1", 8000, "HIGH PRESSURE DETECTED", 30.0, 1.0)
 *     .execute();
 * testRunner.displayDashboard();
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class ScenarioTestRunner {
  private final ProcessScenarioRunner runner;
  private final KPIDashboard dashboard;
  private int scenarioCounter = 0;

  /**
   * Creates a new scenario test runner.
   *
   * @param runner the process scenario runner to use for executing scenarios
   */
  public ScenarioTestRunner(ProcessScenarioRunner runner) {
    this.runner = runner;
    this.dashboard = new KPIDashboard();
  }

  /**
   * Prints the test header with formatting.
   */
  public void printHeader() {
    String separator = new String(new char[70]).replace("\0", "=");
    System.out.println(separator);
    System.out.println("RUNNING TEST SCENARIOS");
    System.out.println(separator);
  }

  /**
   * Executes a scenario with automatic logic activation, KPI collection, and reset.
   * 
   * @param scenarioName the display name for the scenario
   * @param scenario the safety scenario to execute
   * @param logicToActivate the name of the logic sequence to activate (can be null)
   * @param duration the simulation duration in seconds
   * @param timeStep the time step in seconds
   * @return the scenario execution summary
   */
  public ScenarioExecutionSummary executeScenario(String scenarioName,
      ProcessSafetyScenario scenario, String logicToActivate, double duration, double timeStep) {
    scenarioCounter++;

    // Print scenario header
    System.out
        .println("\n### SCENARIO " + scenarioCounter + ": " + scenarioName.toUpperCase() + " ###");

    // Activate logic if specified
    if (logicToActivate != null && !logicToActivate.isEmpty()) {
      runner.activateLogic(logicToActivate);
    }

    // Run scenario
    ScenarioExecutionSummary summary =
        runner.runScenario(scenarioName, scenario, duration, timeStep);

    // Print results
    summary.printResults();

    // Collect KPIs if available
    if (summary.getKPI() != null) {
      // Shorten dashboard key if needed (max 15 chars for formatting)
      String dashboardKey =
          scenarioName.length() > 15 ? scenarioName.substring(0, 15) : scenarioName;
      dashboard.addScenario(dashboardKey, summary.getKPI());
    }

    // Reset for next scenario
    runner.reset();

    return summary;
  }

  /**
   * Executes a scenario without activating any logic.
   * 
   * @param scenarioName the display name for the scenario
   * @param scenario the safety scenario to execute
   * @param duration the simulation duration in seconds
   * @param timeStep the time step in seconds
   * @return the scenario execution summary
   */
  public ScenarioExecutionSummary executeScenario(String scenarioName,
      ProcessSafetyScenario scenario, double duration, double timeStep) {
    return executeScenario(scenarioName, scenario, null, duration, timeStep);
  }

  /**
   * Executes a scenario with delayed logic activation using a background thread.
   * 
   * <p>
   * This is useful for simulating manual interventions or automatic triggers that occur after some
   * time during the scenario.
   * 
   * @param scenarioName the display name for the scenario
   * @param scenario the safety scenario to execute
   * @param logicToActivate the name of the logic sequence to activate
   * @param activationDelay the delay in milliseconds before activating logic
   * @param activationMessage the message to print when activating logic
   * @param duration the simulation duration in seconds
   * @param timeStep the time step in seconds
   * @return the scenario execution summary
   */
  public ScenarioExecutionSummary executeScenarioWithDelayedActivation(String scenarioName,
      ProcessSafetyScenario scenario, String logicToActivate, long activationDelay,
      String activationMessage, double duration, double timeStep) {
    scenarioCounter++;

    // Print scenario header
    System.out
        .println("\n### SCENARIO " + scenarioCounter + ": " + scenarioName.toUpperCase() + " ###");

    // Start background thread for delayed activation
    new Thread(() -> {
      try {
        Thread.sleep(activationDelay);
        System.out.println(">>> " + activationMessage + " <<<");
        runner.activateLogic(logicToActivate);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }).start();

    // Run scenario
    ScenarioExecutionSummary summary =
        runner.runScenario(scenarioName, scenario, duration, timeStep);

    // Print results
    summary.printResults();

    // Collect KPIs if available
    if (summary.getKPI() != null) {
      String dashboardKey =
          scenarioName.length() > 15 ? scenarioName.substring(0, 15) : scenarioName;
      dashboard.addScenario(dashboardKey, summary.getKPI());
    }

    // Reset for next scenario
    runner.reset();

    return summary;
  }

  /**
   * Displays the KPI dashboard comparing all executed scenarios.
   */
  public void displayDashboard() {
    System.out.println("\n\n");
    dashboard.printDashboard();
  }

  /**
   * Gets the KPI dashboard for manual inspection or export.
   *
   * @return the KPI dashboard
   */
  public KPIDashboard getDashboard() {
    return dashboard;
  }

  /**
   * Gets the underlying process scenario runner.
   *
   * @return the process scenario runner
   */
  public ProcessScenarioRunner getRunner() {
    return runner;
  }

  /**
   * Gets the current scenario counter (number of scenarios executed).
   *
   * @return the scenario counter
   */
  public int getScenarioCount() {
    return scenarioCounter;
  }

  /**
   * Resets the scenario counter.
   */
  public void resetCounter() {
    scenarioCounter = 0;
  }

  /**
   * Creates a batch executor for running multiple scenarios in sequence.
   * 
   * <p>
   * This provides a fluent API for defining and executing multiple scenarios:
   * 
   * <pre>
   * testRunner.batch().add("Scenario 1", scenario1, "Logic 1", 30.0, 1.0)
   *     .add("Scenario 2", scenario2, null, 25.0, 0.5)
   *     .addDelayed("Scenario 3", scenario3, "ESD Logic", 5000, "ESD TRIGGERED", 30.0, 1.0)
   *     .execute();
   * </pre>
   *
   * @return a new batch executor
   */
  public BatchExecutor batch() {
    return new BatchExecutor();
  }

  /**
   * Fluent builder for batch execution of multiple scenarios.
   * 
   * <p>
   * This inner class provides a convenient way to define and execute multiple scenarios in sequence
   * with automatic header printing and dashboard display at the end.
   */
  public class BatchExecutor {
    private final List<ScenarioConfig> scenarios = new ArrayList<>();

    /**
     * Adds a standard scenario to the batch.
     *
     * @param name the display name for the scenario
     * @param scenario the safety scenario to execute
     * @param logicToActivate the name of the logic to activate (can be null)
     * @param duration the simulation duration in seconds
     * @param timeStep the time step in seconds
     * @return this batch executor for method chaining
     */
    public BatchExecutor add(String name, ProcessSafetyScenario scenario, String logicToActivate,
        double duration, double timeStep) {
      scenarios.add(new ScenarioConfig(name, scenario, logicToActivate, duration, timeStep));
      return this;
    }

    /**
     * Adds a scenario with delayed logic activation to the batch.
     *
     * @param name the display name for the scenario
     * @param scenario the safety scenario to execute
     * @param logicToActivate the name of the logic to activate
     * @param activationDelay the delay in milliseconds before activating logic
     * @param activationMessage the message to print when activating logic
     * @param duration the simulation duration in seconds
     * @param timeStep the time step in seconds
     * @return this batch executor for method chaining
     */
    public BatchExecutor addDelayed(String name, ProcessSafetyScenario scenario,
        String logicToActivate, long activationDelay, String activationMessage, double duration,
        double timeStep) {
      scenarios.add(new ScenarioConfig(name, scenario, logicToActivate, duration, timeStep,
          activationDelay, activationMessage));
      return this;
    }

    /**
     * Executes all scenarios in the batch with automatic header and dashboard display.
     */
    public void execute() {
      printHeader();

      for (ScenarioConfig config : scenarios) {
        if (config.isDelayed()) {
          executeScenarioWithDelayedActivation(config.name, config.scenario, config.logicToActivate,
              config.activationDelay, config.activationMessage, config.duration, config.timeStep);
        } else {
          executeScenario(config.name, config.scenario, config.logicToActivate, config.duration,
              config.timeStep);
        }
      }

      displayDashboard();
    }

    /**
     * Executes all scenarios in the batch without printing header or dashboard (manual control).
     */
    public void executeWithoutWrapper() {
      for (ScenarioConfig config : scenarios) {
        if (config.isDelayed()) {
          executeScenarioWithDelayedActivation(config.name, config.scenario, config.logicToActivate,
              config.activationDelay, config.activationMessage, config.duration, config.timeStep);
        } else {
          executeScenario(config.name, config.scenario, config.logicToActivate, config.duration,
              config.timeStep);
        }
      }
    }
  }

  /**
   * Internal configuration class for batch scenario execution.
   */
  private static class ScenarioConfig {
    final String name;
    final ProcessSafetyScenario scenario;
    final String logicToActivate;
    final double duration;
    final double timeStep;
    final long activationDelay;
    final String activationMessage;

    // Standard scenario constructor
    ScenarioConfig(String name, ProcessSafetyScenario scenario, String logicToActivate,
        double duration, double timeStep) {
      this.name = name;
      this.scenario = scenario;
      this.logicToActivate = logicToActivate;
      this.duration = duration;
      this.timeStep = timeStep;
      this.activationDelay = 0;
      this.activationMessage = null;
    }

    // Delayed activation constructor
    ScenarioConfig(String name, ProcessSafetyScenario scenario, String logicToActivate,
        double duration, double timeStep, long activationDelay, String activationMessage) {
      this.name = name;
      this.scenario = scenario;
      this.logicToActivate = logicToActivate;
      this.duration = duration;
      this.timeStep = timeStep;
      this.activationDelay = activationDelay;
      this.activationMessage = activationMessage;
    }

    boolean isDelayed() {
      return activationDelay > 0;
    }
  }
}
