package neqsim.process.util.example;

import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.logic.action.ConditionalAction;
import neqsim.process.logic.action.ParallelActionGroup;
import neqsim.process.logic.condition.PressureCondition;
import neqsim.process.logic.condition.TemperatureCondition;
import neqsim.process.logic.condition.TimerCondition;
import neqsim.process.logic.shutdown.ShutdownLogic;
import neqsim.process.logic.startup.StartupLogic;
import neqsim.process.logic.voting.VotingEvaluator;
import neqsim.process.logic.voting.VotingPattern;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Comprehensive example demonstrating advanced process logic features.
 * 
 * <p>
 * This example showcases:
 * <ul>
 * <li>Startup logic with permissive checks</li>
 * <li>Conditional branching (if-then-else)</li>
 * <li>Parallel action execution</li>
 * <li>Controlled shutdown with ramp-down</li>
 * <li>Voting logic for redundant sensors</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class AdvancedProcessLogicExample {
  /**
   * Main method to run the advanced logic example.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    System.out.println("=".repeat(80));
    System.out.println("ADVANCED PROCESS LOGIC FEATURES EXAMPLE");
    System.out.println("=".repeat(80));
    System.out.println();

    // Create process equipment
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 85.0);
    fluid.addComponent("ethane", 10.0);
    fluid.addComponent("propane", 5.0);
    fluid.setMixingRule("classic");

    Stream feedStream = new Stream("Feed", fluid);
    feedStream.setFlowRate(5000.0, "kg/hr");
    feedStream.setTemperature(25.0, "C");
    feedStream.setPressure(10.0, "bara");

    ThrottlingValve feedValve = new ThrottlingValve("Feed Valve", feedStream);
    feedValve.setPercentValveOpening(0.0); // Closed initially

    Stream separatorFeed = new Stream("Separator Feed", feedValve.getOutletStream());

    Separator separator = new Separator("HP Separator", separatorFeed);

    // Run initial state
    feedStream.run();
    feedValve.run();
    separatorFeed.run();
    separator.run();

    // ======================================================================================
    // FEATURE 1: VOTING LOGIC FOR REDUNDANT SENSORS
    // ======================================================================================
    System.out.println("=".repeat(80));
    System.out.println("FEATURE 1: VOTING LOGIC (2oo3 for Pressure Sensors)");
    System.out.println("=".repeat(80));

    // Simulate 3 redundant pressure transmitters
    double actualPressure = feedStream.getPressure();
    double pt1Reading = actualPressure + 0.1; // Slight variation
    double pt2Reading = actualPressure - 0.05;
    double pt3Reading = actualPressure + 0.15;

    System.out.printf("Actual pressure: %.2f bara\n", actualPressure);
    System.out.printf("PT-101: %.2f bara (Good)\n", pt1Reading);
    System.out.printf("PT-102: %.2f bara (Good)\n", pt2Reading);
    System.out.printf("PT-103: %.2f bara (Good)\n", pt3Reading);
    System.out.println();

    // Analog voting - median selection
    VotingEvaluator<Double> analogVoting = new VotingEvaluator<>(VotingPattern.TWO_OUT_OF_THREE);
    analogVoting.addInput(pt1Reading, false);
    analogVoting.addInput(pt2Reading, false);
    analogVoting.addInput(pt3Reading, false);

    double votedPressure = analogVoting.evaluateMedian();
    System.out.printf("Voted pressure (median): %.2f bara\n", votedPressure);
    System.out.printf("Valid inputs: %d/%d\n", analogVoting.getValidInputCount(),
        analogVoting.getTotalInputCount());
    System.out.println();

    // Digital voting - high pressure alarm
    double alarmSetpoint = 15.0;
    boolean pt1High = pt1Reading > alarmSetpoint;
    boolean pt2High = pt2Reading > alarmSetpoint;
    boolean pt3High = pt3Reading > alarmSetpoint;

    VotingEvaluator<Boolean> digitalVoting = new VotingEvaluator<>(VotingPattern.TWO_OUT_OF_THREE);
    digitalVoting.addInput(pt1High, false);
    digitalVoting.addInput(pt2High, false);
    digitalVoting.addInput(pt3High, false);

    boolean highPressureAlarm = digitalVoting.evaluateDigital();
    System.out.printf("High pressure alarm (>%.1f bara): %s\n", alarmSetpoint,
        highPressureAlarm ? "ACTIVE" : "INACTIVE");
    System.out.println();

    // ======================================================================================
    // FEATURE 2: STARTUP LOGIC WITH PERMISSIVES
    // ======================================================================================
    System.out.println("=".repeat(80));
    System.out.println("FEATURE 2: STARTUP LOGIC WITH PERMISSIVE CHECKS");
    System.out.println("=".repeat(80));

    StartupLogic startupLogic = new StartupLogic("Separator Startup");

    // Add permissive conditions
    PressureCondition minPressure = new PressureCondition(feedStream, 5.0, ">");
    TemperatureCondition tempReady = new TemperatureCondition(feedStream, 50.0, "<");
    TimerCondition warmupTime = new TimerCondition(5.0); // 5 second warm-up

    startupLogic.addPermissive(minPressure);
    startupLogic.addPermissive(tempReady);
    startupLogic.addPermissive(warmupTime);

    // Add startup actions (using lambda-style inline actions for simplicity)
    startupLogic.addAction(new SimpleAction("Open feed valve to 25%", () -> {
      feedValve.setPercentValveOpening(25.0);
      return feedValve.getPercentValveOpening() >= 24.0;
    }), 0.0);

    startupLogic.addAction(new SimpleAction("Open feed valve to 50%", () -> {
      feedValve.setPercentValveOpening(50.0);
      return feedValve.getPercentValveOpening() >= 49.0;
    }), 2.0);

    startupLogic.addAction(new SimpleAction("Open feed valve to 100%", () -> {
      feedValve.setPercentValveOpening(100.0);
      return feedValve.getPercentValveOpening() >= 99.0;
    }), 2.0);

    System.out.println("Startup sequence configured:");
    System.out.println("  Permissives: 3 conditions");
    System.out.println("  Actions: 3 steps");
    System.out.println();

    // Start the timer permissive
    warmupTime.start();

    // Activate startup
    startupLogic.activate();
    System.out.println("Startup activated...\n");

    // Simulate startup execution
    double timeStep = 1.0;
    double totalTime = 15.0;

    System.out.println("Time (s) | Status");
    System.out.println("---------|" + "-".repeat(70));

    for (double time = 0.0; time <= totalTime; time += timeStep) {
      warmupTime.update(timeStep);
      startupLogic.execute(timeStep);

      System.out.printf("%8.1f | %s\n", time, startupLogic.getStatusDescription());

      if (startupLogic.isComplete()) {
        break;
      }
    }

    System.out.println();
    if (startupLogic.isComplete()) {
      System.out.println("✓ Startup completed successfully");
    } else if (startupLogic.isAborted()) {
      System.out.println("✗ Startup aborted: " + startupLogic.getAbortReason());
    }
    System.out.println();

    // ======================================================================================
    // FEATURE 3: CONDITIONAL BRANCHING
    // ======================================================================================
    System.out.println("=".repeat(80));
    System.out.println("FEATURE 3: CONDITIONAL BRANCHING (If-Then-Else Logic)");
    System.out.println("=".repeat(80));

    // Scenario: If pressure > 12 bara, reduce flow; else increase flow
    PressureCondition highPressure = new PressureCondition(feedStream, 12.0, ">");

    SimpleAction reduceFlow = new SimpleAction("Reduce flow to 75%", () -> {
      feedValve.setPercentValveOpening(75.0);
      return true;
    });

    SimpleAction increaseFlow = new SimpleAction("Increase flow to 100%", () -> {
      feedValve.setPercentValveOpening(100.0);
      return true;
    });

    ConditionalAction conditionalAction =
        new ConditionalAction(highPressure, reduceFlow, increaseFlow, "Pressure Control");

    System.out.printf("Current pressure: %.1f bara\n", feedStream.getPressure());
    System.out.printf("Condition: Pressure > 12.0 bara? %s\n",
        highPressure.evaluate() ? "YES" : "NO");
    System.out.println();

    conditionalAction.execute();
    System.out.println("Conditional action executed:");
    System.out.println("  " + conditionalAction.getDescription());
    System.out.println("  Result: Feed valve set to " + feedValve.getPercentValveOpening() + "%");
    System.out.println();

    // ======================================================================================
    // FEATURE 4: PARALLEL ACTION EXECUTION
    // ======================================================================================
    System.out.println("=".repeat(80));
    System.out.println("FEATURE 4: PARALLEL ACTION EXECUTION");
    System.out.println("=".repeat(80));

    // Create multiple actions to execute in parallel
    ParallelActionGroup parallelActions = new ParallelActionGroup("Open all valves");

    parallelActions.addAction(new SimpleAction("Open valve 1", () -> {
      // Simulate valve opening
      return true;
    }));

    parallelActions.addAction(new SimpleAction("Open valve 2", () -> {
      // Simulate valve opening
      return true;
    }));

    parallelActions.addAction(new SimpleAction("Open valve 3", () -> {
      // Simulate valve opening
      return true;
    }));

    System.out.println("Parallel group: " + parallelActions.getDescription());
    System.out.println("Executing all actions simultaneously...");

    parallelActions.execute();

    System.out.printf("Completion: %d/%d actions (%.0f%%)\n", parallelActions.getCompletedCount(),
        parallelActions.getTotalCount(), parallelActions.getCompletionPercentage());

    if (parallelActions.isComplete()) {
      System.out.println("✓ All parallel actions completed");
    }
    System.out.println();

    // ======================================================================================
    // FEATURE 5: CONTROLLED SHUTDOWN WITH RAMP-DOWN
    // ======================================================================================
    System.out.println("=".repeat(80));
    System.out.println("FEATURE 5: CONTROLLED SHUTDOWN WITH RAMP-DOWN");
    System.out.println("=".repeat(80));

    ShutdownLogic shutdownLogic = new ShutdownLogic("Separator Shutdown");
    shutdownLogic.setRampDownTime(10.0); // 10 second controlled shutdown

    // Add shutdown actions
    shutdownLogic.addAction(new SimpleAction("Reduce to 75%", () -> {
      feedValve.setPercentValveOpening(75.0);
      return true;
    }), 0.0);

    shutdownLogic.addAction(new SimpleAction("Reduce to 50%", () -> {
      feedValve.setPercentValveOpening(50.0);
      return true;
    }), 3.0);

    shutdownLogic.addAction(new SimpleAction("Reduce to 25%", () -> {
      feedValve.setPercentValveOpening(25.0);
      return true;
    }), 6.0);

    shutdownLogic.addAction(new SimpleAction("Close valve", () -> {
      feedValve.setPercentValveOpening(0.0);
      return true;
    }), 10.0);

    System.out.println("Shutdown sequence configured:");
    System.out.printf("  Mode: %s\n", shutdownLogic.isEmergencyMode() ? "EMERGENCY" : "CONTROLLED");
    System.out.printf("  Duration: %.0f seconds\n", shutdownLogic.getEffectiveShutdownTime());
    System.out.println("  Actions: 4 steps");
    System.out.println();

    // Activate shutdown
    shutdownLogic.activate();
    System.out.println("Shutdown activated...\n");

    System.out.println("Time (s) | Valve (%) | Progress | Status");
    System.out.println("---------|-----------|----------|" + "-".repeat(50));

    for (double time = 0.0; time <= 12.0; time += timeStep) {
      shutdownLogic.execute(timeStep);

      System.out.printf("%8.1f | %9.1f | %7.0f%% | ", time, feedValve.getPercentValveOpening(),
          shutdownLogic.getProgress());

      if (shutdownLogic.isComplete()) {
        System.out.println("COMPLETED");
        break;
      } else {
        System.out.println("RUNNING");
      }
    }

    System.out.println();
    if (shutdownLogic.isComplete()) {
      System.out.println("✓ Shutdown completed successfully");
    }
    System.out.println();

    // ======================================================================================
    // FEATURE 6: EMERGENCY SHUTDOWN (FASTER)
    // ======================================================================================
    System.out.println("=".repeat(80));
    System.out.println("FEATURE 6: EMERGENCY SHUTDOWN (Accelerated Timing)");
    System.out.println("=".repeat(80));

    // Reset valve
    feedValve.setPercentValveOpening(100.0);

    ShutdownLogic emergencyShutdown = new ShutdownLogic("Emergency Shutdown");
    emergencyShutdown.setRampDownTime(10.0); // Normal: 10 seconds
    emergencyShutdown.setEmergencyShutdownTime(2.0); // Emergency: 2 seconds
    emergencyShutdown.setEmergencyMode(true);

    // Same actions, but executed much faster due to emergency mode
    emergencyShutdown.addAction(new SimpleAction("Emergency close", () -> {
      feedValve.setPercentValveOpening(0.0);
      return true;
    }), 0.0);

    System.out.println("Emergency shutdown activated...");
    System.out.printf("  Normal duration: %.0f seconds\n", emergencyShutdown.getRampDownTime());
    System.out.printf("  Emergency duration: %.0f seconds\n",
        emergencyShutdown.getEmergencyShutdownTime());
    System.out.println();

    emergencyShutdown.activate();

    System.out.println("Time (s) | Valve (%) | Status");
    System.out.println("---------|-----------|--------");

    for (double time = 0.0; time <= 3.0; time += 0.5) {
      emergencyShutdown.execute(0.5);

      System.out.printf("%8.1f | %9.1f | %s\n", time, feedValve.getPercentValveOpening(),
          emergencyShutdown.isComplete() ? "COMPLETED" : "RUNNING");

      if (emergencyShutdown.isComplete()) {
        break;
      }
    }

    System.out.println();
    if (emergencyShutdown.isComplete()) {
      System.out.println("✓ Emergency shutdown completed");
    }
    System.out.println();

    // ======================================================================================
    // SUMMARY
    // ======================================================================================
    System.out.println("=".repeat(80));
    System.out.println("FEATURES DEMONSTRATED");
    System.out.println("=".repeat(80));
    System.out.println("✓ Voting Logic: 2oo3 digital and analog voting for redundant sensors");
    System.out.println("✓ Startup Logic: Permissive checks with temperature, pressure, and timer");
    System.out.println("✓ Conditional Branching: If-then-else logic for dynamic decision making");
    System.out.println("✓ Parallel Execution: Simultaneous execution of multiple actions");
    System.out.println("✓ Controlled Shutdown: Gradual ramp-down over configurable time");
    System.out.println("✓ Emergency Shutdown: Accelerated shutdown with scaled timing");
    System.out.println();

    System.out.println("=".repeat(80));
    System.out.println("EXAMPLE COMPLETED");
    System.out.println("=".repeat(80));
  }

  /**
   * Simple action implementation for example.
   */
  private static class SimpleAction implements neqsim.process.logic.LogicAction {
    private final String description;
    private final java.util.function.Supplier<Boolean> executor;
    private boolean complete = false;

    SimpleAction(String description, java.util.function.Supplier<Boolean> executor) {
      this.description = description;
      this.executor = executor;
    }

    @Override
    public void execute() {
      if (!complete) {
        complete = executor.get();
      }
    }

    @Override
    public boolean isComplete() {
      return complete;
    }

    @Override
    public String getDescription() {
      return description;
    }

    @Override
    public String getTargetName() {
      return "target";
    }
  }
}
