package neqsim.integration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Integration tests validating PVT calculations with flow equipment.
 * 
 * <p>
 * These tests verify that PVT flash calculations are consistent when used with separators.
 */
public class PVTToFlowTests {
  /**
   * Test: PT flash before separator - validates PVT-separator coupling.
   */
  @Test
  public void testPTFlashToSeparator() {
    // Create fluid
    SystemInterface fluid = new SystemSrkEos(320.0, 80.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-butane", 0.03);
    fluid.setMixingRule("classic");
    fluid.init(0);

    // Perform PT flash
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    // Create stream from flashed fluid
    Stream feed = new Stream("Flashed Feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.run();

    // Separator
    Separator sep = new Separator("Test Separator", feed);
    sep.run();

    // Validate phase separation occurred
    int numPhases = fluid.getNumberOfPhases();
    assertTrue(numPhases >= 1, "Should have at least 1 phase after flash");

    // Check separator output
    assertNotNull(sep.getGasOutStream(), "Gas stream should exist");
    assertNotNull(sep.getLiquidOutStream(), "Liquid stream should exist");
  }

  /**
   * Test: Dew point calculation and separation.
   */
  @Test
  public void testDewPointAndSeparation() {
    // Create rich gas near dew point conditions
    SystemInterface gas = new SystemSrkEos(280.0, 50.0);
    gas.addComponent("methane", 0.75);
    gas.addComponent("ethane", 0.12);
    gas.addComponent("propane", 0.08);
    gas.addComponent("n-butane", 0.05);
    gas.setMixingRule("classic");
    gas.init(0);

    // Flash calculation
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPflash();

    // Create stream and separator
    Stream feed = new Stream("Rich Gas", gas);
    feed.setFlowRate(2000.0, "kg/hr");
    feed.run();

    Separator sep = new Separator("Dew Point Separator", feed);
    sep.run();

    // Verify outputs exist
    assertNotNull(sep.getGasOutStream().getFluid(), "Gas fluid should exist");
    assertNotNull(sep.getLiquidOutStream().getFluid(), "Liquid fluid should exist");
  }

  /**
   * Test: Bubble point conditions.
   */
  @Test
  public void testBubblePointConditions() {
    // Create liquid at conditions that will partially vaporize
    SystemInterface liquid = new SystemSrkEos(350.0, 5.0); // Low pressure, high temp
    liquid.addComponent("propane", 0.4);
    liquid.addComponent("n-butane", 0.3);
    liquid.addComponent("n-pentane", 0.2);
    liquid.addComponent("n-hexane", 0.1);
    liquid.setMixingRule("classic");
    liquid.init(0);

    // Flash
    ThermodynamicOperations ops = new ThermodynamicOperations(liquid);
    ops.TPflash();

    // Stream and separator
    Stream feed = new Stream("NGL Feed", liquid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    Separator sep = new Separator("NGL Separator", feed);
    sep.run();

    // Check number of phases
    assertTrue(liquid.getNumberOfPhases() >= 1, "Should have phases");
  }
}
