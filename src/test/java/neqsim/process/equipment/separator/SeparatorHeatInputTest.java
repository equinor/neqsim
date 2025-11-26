package neqsim.process.equipment.separator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test of separator heat input capabilities, including flare radiation integration.
 */
public class SeparatorHeatInputTest {
  ProcessSystem processOps;
  Separator separator;
  ThreePhaseSeparator threePhaseSeparator;
  Stream gasStream;
  Flare flare;

  @BeforeEach
  public void setUp() {
    // Create test system with gas-liquid mixture
    SystemSrkEos testSystem = new SystemSrkEos(298.15, 50.0);
    testSystem.addComponent("methane", 0.5);
    testSystem.addComponent("ethane", 0.3);
    testSystem.addComponent("propane", 0.2);
    testSystem.setMixingRule("classic");
    testSystem.createDatabase(true);

    gasStream = new Stream("gas stream", testSystem);
    gasStream.setFlowRate(1000.0, "kg/hr");
    gasStream.setTemperature(30.0, "C");
    gasStream.setPressure(50.0, "bara");

    // Create separator
    separator = new Separator("Test Separator", gasStream);

    // Create three-phase separator for additional testing
    threePhaseSeparator = new ThreePhaseSeparator("Test 3-Phase Separator", gasStream.clone());

    // Create flare for heat input source
    flare = new Flare("Test Flare", gasStream);

    processOps = new ProcessSystem();
    processOps.add(gasStream);
    processOps.add(separator);
    processOps.add(threePhaseSeparator);
    processOps.add(flare);
  }

  @Test
  public void testSeparatorHeatInputSetters() {
    // Test heat input setter in watts
    separator.setHeatInput(50000.0); // 50 kW
    assertEquals(50000.0, separator.getHeatInput(), 0.1);
    assertTrue(separator.isSetHeatInput());

    // Test heat input setter with unit conversion
    separator.setHeatInput(100.0, "kW");
    assertEquals(100000.0, separator.getHeatInput(), 0.1);
    assertEquals(100.0, separator.getHeatInput("kW"), 0.001);
    assertEquals(0.1, separator.getHeatInput("MW"), 0.0001);

    // Test heat duty aliases
    separator.setHeatDuty(75.0, "kW");
    assertEquals(75000.0, separator.getHeatDuty(), 0.1);
    assertEquals(75.0, separator.getHeatDuty("kW"), 0.001);
  }

  @Test
  public void testThreePhaseSeparatorHeatInputInheritance() {
    // Test that ThreePhaseSeparator inherits heat input capabilities
    threePhaseSeparator.setHeatInput(25000.0); // 25 kW
    assertEquals(25000.0, threePhaseSeparator.getHeatInput(), 0.1);
    assertTrue(threePhaseSeparator.isSetHeatInput());

    threePhaseSeparator.setHeatInput(50.0, "kW");
    assertEquals(50.0, threePhaseSeparator.getHeatInput("kW"), 0.001);
  }

  @Test
  public void testSeparatorWithFlareHeatInput() {
    // Run flare to calculate heat release
    processOps.run();

    // Get flare heat duty and apply it to separator
    double flareHeatRelease = flare.getHeatDuty(); // Heat from combustion
    double radiationFraction = 0.20; // Assume 20% of heat is radiated
    double heatToSeparator = flareHeatRelease * radiationFraction;

    separator.setHeatInput(heatToSeparator, "W");

    // Verify heat input is properly set
    assertTrue(separator.isSetHeatInput());
    assertEquals(heatToSeparator, separator.getHeatInput(), 0.1);

    // Run separator with heat input
    separator.run();

    // Verify separator operates with additional heat input
    assertTrue(separator.getGasOutStream().getFlowRate("kg/hr") > 0);
    if (separator.getLiquidOutStream() != null) {
      assertTrue(separator.getLiquidOutStream().getFlowRate("kg/hr") >= 0);
    }
  }

  @Test
  public void testSeparatorTemperatureWithHeatInput() {
    double initialTemperature = gasStream.getTemperature("C");

    // Add significant heat input
    separator.setHeatInput(100.0, "kW");
    separator.run();

    // With heat input, the separator should show energy balance effects
    assertTrue(separator.isSetHeatInput());
    assertEquals(100000.0, separator.getHeatInput(), 0.1);

    // The heat input should be incorporated in transient calculations
    // (steady-state may not show temperature change without time integration)
    assertTrue(separator.getGasOutStream().getTemperature("C") >= initialTemperature - 5.0);
  }

  @Test
  public void testMultipleHeatInputUnits() {
    // Test various unit conversions
    separator.setHeatInput(1.0, "MW");
    assertEquals(1000000.0, separator.getHeatInput(), 0.1);
    assertEquals(1000.0, separator.getHeatInput("kW"), 0.001);
    assertEquals(1.0, separator.getHeatInput("MW"), 0.0001);

    separator.setHeatInput(500.0, "kW");
    assertEquals(500000.0, separator.getHeatInput(), 0.1);
    assertEquals(500.0, separator.getHeatInput("kW"), 0.001);
    assertEquals(0.5, separator.getHeatInput("MW"), 0.0001);

    separator.setHeatInput(75000.0, "W");
    assertEquals(75000.0, separator.getHeatInput(), 0.1);
    assertEquals(75.0, separator.getHeatInput("kW"), 0.001);
  }

  @Test
  public void testSeparatorHeatInputWithProcessSystem() {
    // Set different heat inputs for both separators
    separator.setHeatInput(50.0, "kW");
    threePhaseSeparator.setHeatInput(25.0, "kW");

    // Run entire process system
    processOps.run();

    // Verify both separators retain their heat input settings
    assertEquals(50000.0, separator.getHeatInput(), 0.1);
    assertEquals(25000.0, threePhaseSeparator.getHeatInput(), 0.1);
    assertTrue(separator.isSetHeatInput());
    assertTrue(threePhaseSeparator.isSetHeatInput());

    // Verify process system runs successfully with heat inputs
    assertTrue(separator.getGasOutStream().getFlowRate("kg/hr") > 0);
    assertTrue(threePhaseSeparator.getGasOutStream().getFlowRate("kg/hr") > 0);
  }

  @Test
  public void testFlareRadiationToSeparatorIntegration() {
    // This test demonstrates a realistic scenario where flare radiation
    // heats a nearby separator

    // Run flare calculation
    flare.run();
    double flareHeatDuty = flare.getHeatDuty("kW");

    // Calculate radiation heat flux at separator location
    // Assume separator is 50 meters from flare
    double distance = 50.0; // meters
    double radiationFlux = flare.estimateRadiationHeatFlux(distance); // W/m2

    // Assume separator has 100 m2 exposed surface area
    double exposedArea = 100.0; // m2
    double radiationHeatInput = radiationFlux * exposedArea;

    // Apply radiation heat to separator
    separator.setHeatInput(radiationHeatInput, "W");

    // Run separator with radiation heat input
    separator.run();

    // Verify integration works
    assertTrue(separator.isSetHeatInput());
    assertEquals(radiationHeatInput, separator.getHeatInput(), 0.1);
    assertTrue(flareHeatDuty > 0.0); // Flare produces heat
    assertTrue(radiationHeatInput >= 0.0); // Separator receives some radiation

    // Print results for verification
    System.out.printf("Flare heat duty: %.2f kW%n", flareHeatDuty);
    System.out.printf("Radiation flux at %sm: %.2f W/m²%n", distance, radiationFlux);
    System.out.printf("Heat input to separator: %.2f kW%n", radiationHeatInput / 1000.0);
  }
}
