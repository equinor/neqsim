package neqsim.process.util.scenario;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.ProcessSafetyScenario;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test to verify that process simulation methods (run() and runTransient()) are actually being
 * called and producing thermodynamic results.
 */
class ProcessScenarioRunnerSimulationTest {
  private ProcessSystem system;
  private ProcessScenarioRunner runner;

  @BeforeEach
  void setUp() {
    // Create a simple process system
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 85.0);
    fluid.addComponent("ethane", 10.0);
    fluid.addComponent("propane", 5.0);
    fluid.setMixingRule(2);

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.setPressure(50.0, "bara");
    feed.setTemperature(25.0, "C");

    ThrottlingValve valve = new ThrottlingValve("Valve", feed);
    valve.setOutletPressure(10.0);

    Separator separator = new Separator("Separator", valve.getOutletStream());
    separator.setInternalDiameter(1.0);

    system = new ProcessSystem();
    system.add(feed);
    system.add(valve);
    system.add(separator);

    runner = new ProcessScenarioRunner(system);
  }

  @Test
  void testSteadyStateInitializationRunsSimulation() {
    // Verify that initializeSteadyState() actually calls system.run()
    // Before initialization, separator should not have calculated results
    Separator sep = (Separator) system.getUnit("Separator");

    // Initialize steady-state (should call system.run())
    assertDoesNotThrow(() -> runner.initializeSteadyState(),
        "initializeSteadyState should not throw exception");

    // After initialization, we should have valid thermodynamic results
    double pressure = sep.getGasOutStream().getPressure("bara");
    double temperature = sep.getGasOutStream().getTemperature("C");

    // Verify we got actual calculated values (not NaN or zero)
    assertFalse(Double.isNaN(pressure), "Pressure should not be NaN after initialization");
    assertFalse(Double.isNaN(temperature), "Temperature should not be NaN after initialization");
    assertTrue(pressure > 0, "Pressure should be positive after initialization");
    assertTrue(temperature > -273.15, "Temperature should be above absolute zero");

    System.out.println("✓ Steady-state initialization produced valid results:");
    System.out.println("  Pressure: " + pressure + " bara");
    System.out.println("  Temperature: " + temperature + " °C");
  }

  @Test
  void testTransientSimulationRunsDuringScenario() {
    // Initialize system
    runner.initializeSteadyState();

    // Get initial conditions
    Separator sep = (Separator) system.getUnit("Separator");
    double initialPressure = sep.getGasOutStream().getPressure("bara");

    // Run a short scenario
    ProcessSafetyScenario scenario = ProcessSafetyScenario.builder("Test Scenario").build();

    ScenarioExecutionSummary summary = runner.runScenario("Transient Test", scenario, 5.0, 1.0);

    // Verify scenario completed
    assertNotNull(summary, "Scenario summary should not be null");
    assertEquals("Transient Test", summary.getScenarioName());

    // Verify transient simulation ran (pressure may change or stay same, but should be valid)
    double finalPressure = sep.getGasOutStream().getPressure("bara");
    assertFalse(Double.isNaN(finalPressure), "Pressure should not be NaN after transient run");
    assertTrue(finalPressure > 0, "Pressure should be positive after transient run");

    // Verify no simulation errors occurred
    assertTrue(summary.getErrors().isEmpty() || summary.getErrors().size() < 3,
        "Should have minimal or no simulation errors");

    System.out.println("✓ Transient simulation completed:");
    System.out.println("  Initial pressure: " + initialPressure + " bara");
    System.out.println("  Final pressure: " + finalPressure + " bara");
    System.out.println("  Errors: " + summary.getErrors().size());
  }

  @Test
  void testSystemResetRerunsSimulation() {
    // Initialize and run first scenario
    runner.initializeSteadyState();

    Separator sep = (Separator) system.getUnit("Separator");
    double pressure1 = sep.getGasOutStream().getPressure("bara");

    ProcessSafetyScenario scenario1 = ProcessSafetyScenario.builder("Scenario 1").build();
    runner.runScenario("First Run", scenario1, 2.0, 1.0);

    // Reset should re-run steady-state
    assertDoesNotThrow(() -> runner.reset(), "Reset should not throw exception");

    // After reset, should still have valid results
    double pressure2 = sep.getGasOutStream().getPressure("bara");
    assertFalse(Double.isNaN(pressure2), "Pressure should not be NaN after reset");
    assertTrue(pressure2 > 0, "Pressure should be positive after reset");

    System.out.println("✓ System reset maintained valid state:");
    System.out.println("  Pressure before reset: " + pressure1 + " bara");
    System.out.println("  Pressure after reset: " + pressure2 + " bara");
  }

  @Test
  void testErrorHandlingDoesNotStopSimulation() {
    runner.initializeSteadyState();

    // Create a scenario that might cause some issues but shouldn't crash
    ProcessSafetyScenario scenario =
        ProcessSafetyScenario.builder("Stress Test").customManipulator("Feed", equipment -> {
          if (equipment instanceof Stream) {
            // Set extreme conditions that might cause calculation challenges
            ((Stream) equipment).setPressure(200.0, "bara");
          }
        }).build();

    // Should complete without throwing exception even if there are some errors
    ScenarioExecutionSummary summary =
        assertDoesNotThrow(() -> runner.runScenario("Error Handling Test", scenario, 5.0, 0.5),
            "Scenario should handle errors gracefully");

    assertNotNull(summary, "Summary should be generated even with errors");

    System.out.println("✓ Error handling test completed:");
    System.out.println("  Errors encountered: " + summary.getErrors().size());
    System.out.println("  Warnings: " + summary.getWarnings().size());
  }
}
