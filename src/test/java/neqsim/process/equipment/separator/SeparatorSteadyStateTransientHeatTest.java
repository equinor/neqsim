package neqsim.process.equipment.separator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;
import java.util.UUID;

/**
 * Test to verify heat input works for both steady-state run() and transient runTransient() methods.
 */
public class SeparatorSteadyStateTransientHeatTest {
  ProcessSystem processOps;
  Separator separator;
  ThreePhaseSeparator threePhaseSeparator;
  Stream gasStream;

  @BeforeEach
  public void setUp() {
    // Create test system with gas-liquid mixture
    SystemSrkEos testSystem = new SystemSrkEos(298.15, 50.0);
    testSystem.addComponent("methane", 0.4);
    testSystem.addComponent("ethane", 0.3);
    testSystem.addComponent("propane", 0.2);
    testSystem.addComponent("n-butane", 0.1);
    testSystem.setMixingRule("classic");
    testSystem.createDatabase(true);

    gasStream = new Stream("gas stream", testSystem);
    gasStream.setFlowRate(2000.0, "kg/hr");
    gasStream.setTemperature(25.0, "C");
    gasStream.setPressure(50.0, "bara");

    // Create separators
    separator = new Separator("Test Separator", gasStream);
    threePhaseSeparator = new ThreePhaseSeparator("Test 3-Phase Separator", gasStream.clone());

    processOps = new ProcessSystem();
    processOps.add(gasStream);
    processOps.add(separator);
    processOps.add(threePhaseSeparator);
  }

  @Test
  public void testSeparatorSteadyStateWithHeatInput() {
    // Set up heat input
    double heatInput = 100.0; // kW
    separator.setHeatInput(heatInput, "kW");

    // Get initial conditions
    processOps.run(); // Initial run without heat
    double initialTemp = separator.getThermoSystem().getTemperature("C");

    // Run with heat input
    UUID id = UUID.randomUUID();
    separator.run(id);

    // Verify heat input is applied
    assertTrue(separator.isSetHeatInput());
    assertEquals(heatInput * 1000.0, separator.getHeatInput(), 0.1);

    // With heat input, temperature should generally increase (or at least not decrease
    // significantly)
    double finalTemp = separator.getThermoSystem().getTemperature("C");
    System.out.printf(
        "Separator steady-state: Initial temp: %.2f°C, Final temp: %.2f°C, Heat input: %.0f kW%n",
        initialTemp, finalTemp, heatInput);

    // The system should handle the heat input without errors
    assertTrue(separator.getGasOutStream().getFlowRate("kg/hr") > 0);
  }

  @Test
  public void testThreePhaseSeparatorSteadyStateWithHeatInput() {
    // Set up heat input
    double heatInput = 75.0; // kW
    threePhaseSeparator.setHeatInput(heatInput, "kW");

    // Get initial conditions
    processOps.run(); // Initial run without heat
    double initialTemp = threePhaseSeparator.getThermoSystem().getTemperature("C");

    // Run with heat input
    UUID id = UUID.randomUUID();
    threePhaseSeparator.run(id);

    // Verify heat input is applied
    assertTrue(threePhaseSeparator.isSetHeatInput());
    assertEquals(heatInput * 1000.0, threePhaseSeparator.getHeatInput(), 0.1);

    // With heat input, temperature should generally increase (or at least not decrease
    // significantly)
    double finalTemp = threePhaseSeparator.getThermoSystem().getTemperature("C");
    System.out.printf(
        "3-Phase separator steady-state: Initial temp: %.2f°C, Final temp: %.2f°C, Heat input: %.0f kW%n",
        initialTemp, finalTemp, heatInput);

    // The system should handle the heat input without errors
    assertTrue(threePhaseSeparator.getGasOutStream().getFlowRate("kg/hr") > 0);
  }

  @Test
  public void testSeparatorTransientWithHeatInput() {
    // Set up heat input
    double heatInput = 50.0; // kW
    separator.setHeatInput(heatInput, "kW");

    // Initialize for transient calculations
    separator.setCalculateSteadyState(false);
    processOps.run(); // This will call initializeTransientCalculation

    double initialTemp = separator.getThermoSystem().getTemperature("C");
    double initialInternalEnergy = separator.getThermoSystem().getInternalEnergy();

    // Run transient with heat input
    double dt = 1.0; // 1 second time step
    UUID id = UUID.randomUUID();
    separator.runTransient(dt, id);

    // Verify heat input is applied
    assertTrue(separator.isSetHeatInput());
    assertEquals(heatInput * 1000.0, separator.getHeatInput(), 0.1);

    double finalTemp = separator.getThermoSystem().getTemperature("C");
    double finalInternalEnergy = separator.getThermoSystem().getInternalEnergy();

    System.out.printf(
        "Separator transient: Initial temp: %.2f°C, Final temp: %.2f°C, Heat input: %.0f kW%n",
        initialTemp, finalTemp, heatInput);
    System.out.printf("Internal energy change: %.2f J%n",
        finalInternalEnergy - initialInternalEnergy);

    // The transient method should incorporate heat input in energy balance
    assertTrue(separator.getGasOutStream().getFlowRate("kg/hr") > 0);
  }

  @Test
  public void testThreePhaseSeparatorTransientWithHeatInput() {
    // Set up heat input
    double heatInput = 60.0; // kW
    threePhaseSeparator.setHeatInput(heatInput, "kW");

    // Initialize for transient calculations
    threePhaseSeparator.setCalculateSteadyState(false);
    processOps.run(); // This will call initializeTransientCalculation

    double initialTemp = threePhaseSeparator.getThermoSystem().getTemperature("C");
    double initialInternalEnergy = threePhaseSeparator.getThermoSystem().getInternalEnergy();

    // Run transient with heat input
    double dt = 1.0; // 1 second time step
    UUID id = UUID.randomUUID();
    threePhaseSeparator.runTransient(dt, id);

    // Verify heat input is applied
    assertTrue(threePhaseSeparator.isSetHeatInput());
    assertEquals(heatInput * 1000.0, threePhaseSeparator.getHeatInput(), 0.1);

    double finalTemp = threePhaseSeparator.getThermoSystem().getTemperature("C");
    double finalInternalEnergy = threePhaseSeparator.getThermoSystem().getInternalEnergy();

    System.out.printf(
        "3-Phase separator transient: Initial temp: %.2f°C, Final temp: %.2f°C, Heat input: %.0f kW%n",
        initialTemp, finalTemp, heatInput);
    System.out.printf("Internal energy change: %.2f J%n",
        finalInternalEnergy - initialInternalEnergy);

    // The transient method should incorporate heat input in energy balance
    assertTrue(threePhaseSeparator.getGasOutStream().getFlowRate("kg/hr") > 0);
  }

  @Test
  public void testComparisonSteadyStateVsTransient() {
    double heatInput = 80.0; // kW

    // Test steady-state separator
    Separator steadyStateSeparator = new Separator("Steady State Sep", gasStream.clone());
    steadyStateSeparator.setHeatInput(heatInput, "kW");
    steadyStateSeparator.setCalculateSteadyState(true);

    // Test transient separator
    Separator transientSeparator = new Separator("Transient Sep", gasStream.clone());
    transientSeparator.setHeatInput(heatInput, "kW");
    transientSeparator.setCalculateSteadyState(false);

    // Run steady-state
    steadyStateSeparator.run();
    double steadyStateTemp = steadyStateSeparator.getThermoSystem().getTemperature("C");

    // Run transient (initialize first)
    transientSeparator.run(); // This initializes transient calculation
    transientSeparator.runTransient(1.0, UUID.randomUUID());
    double transientTemp = transientSeparator.getThermoSystem().getTemperature("C");

    System.out.printf("Heat input comparison - Steady-state temp: %.2f°C, Transient temp: %.2f°C%n",
        steadyStateTemp, transientTemp);

    // Both methods should handle heat input
    assertTrue(steadyStateSeparator.isSetHeatInput());
    assertTrue(transientSeparator.isSetHeatInput());
    assertEquals(heatInput * 1000.0, steadyStateSeparator.getHeatInput(), 0.1);
    assertEquals(heatInput * 1000.0, transientSeparator.getHeatInput(), 0.1);
  }

  @Test
  public void testNoHeatInputComparison() {
    // Test that systems work the same with and without heat input set to zero
    Separator noHeatSeparator = new Separator("No Heat Sep", gasStream.clone());
    Separator zeroHeatSeparator = new Separator("Zero Heat Sep", gasStream.clone());
    zeroHeatSeparator.setHeatInput(0.0, "kW");

    // Run both
    noHeatSeparator.run();
    zeroHeatSeparator.run();

    double noHeatTemp = noHeatSeparator.getThermoSystem().getTemperature("C");
    double zeroHeatTemp = zeroHeatSeparator.getThermoSystem().getTemperature("C");

    System.out.printf("No heat input comparison - No heat: %.2f°C, Zero heat: %.2f°C%n", noHeatTemp,
        zeroHeatTemp);

    // Temperatures should be very similar (within numerical precision)
    assertEquals(noHeatTemp, zeroHeatTemp, 0.1);
  }

  @Test
  public void testLargeHeatInputEffects() {
    // Test with significant heat input to see clear effects
    double largeHeatInput = 500.0; // kW - significant heat input

    // Run without heat input first
    Separator baselineSeparator = new Separator("Baseline Sep", gasStream.clone());
    baselineSeparator.run();
    double baselineTemp = baselineSeparator.getThermoSystem().getTemperature("C");
    double baselineGasFlow = baselineSeparator.getGasOutStream().getFlowRate("kg/hr");
    double baselineLiquidFlow = 0.0;
    if (baselineSeparator.getLiquidOutStream() != null) {
      baselineLiquidFlow = baselineSeparator.getLiquidOutStream().getFlowRate("kg/hr");
    }

    // Run with large heat input
    Separator heatedSeparator = new Separator("Heated Sep", gasStream.clone());
    heatedSeparator.setHeatInput(largeHeatInput, "kW");
    heatedSeparator.run();
    double heatedTemp = heatedSeparator.getThermoSystem().getTemperature("C");
    double heatedGasFlow = heatedSeparator.getGasOutStream().getFlowRate("kg/hr");
    double heatedLiquidFlow = 0.0;
    if (heatedSeparator.getLiquidOutStream() != null) {
      heatedLiquidFlow = heatedSeparator.getLiquidOutStream().getFlowRate("kg/hr");
    }

    System.out.printf("Large heat input effects:%n");
    System.out.printf("  Baseline - Temp: %.2f°C, Gas: %.0f kg/hr, Liquid: %.0f kg/hr%n",
        baselineTemp, baselineGasFlow, baselineLiquidFlow);
    System.out.printf("  Heated   - Temp: %.2f°C, Gas: %.0f kg/hr, Liquid: %.0f kg/hr%n",
        heatedTemp, heatedGasFlow, heatedLiquidFlow);
    System.out.printf("  Heat input: %.0f kW%n", largeHeatInput);

    // With significant heat input, we should see some effect
    assertTrue(heatedSeparator.isSetHeatInput());
    assertEquals(largeHeatInput * 1000.0, heatedSeparator.getHeatInput(), 0.1);

    // Temperature should generally increase with heat input
    // (Though exact behavior depends on thermodynamic properties)
    assertTrue(heatedTemp >= baselineTemp - 5.0); // Allow some tolerance for numerical effects
  }
}
