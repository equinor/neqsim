package neqsim.process.util.scenario;

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
}
