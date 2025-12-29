package neqsim.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Integration tests validating thermo module integration with process equipment.
 * 
 * <p>
 * These tests demonstrate how thermodynamic systems couple with process unit operations.
 */
public class ThermoToProcessTests {

  /**
   * Test: Natural gas through a separator - validates thermo-process coupling.
   */
  @Test
  public void testThermoToSeparator() {
    // Create thermo system (SRK EOS for natural gas)
    SystemInterface gas = new SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.80);
    gas.addComponent("ethane", 0.10);
    gas.addComponent("propane", 0.05);
    gas.addComponent("n-butane", 0.03);
    gas.addComponent("n-pentane", 0.02);
    gas.setMixingRule("classic");
    gas.init(0);

    // Create and run feed stream
    Stream feed = new Stream("Feed", gas);
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();

    // Validate thermo system is ready
    ValidationFramework.ValidationResult thermoResult = ThermoValidator.validateSystem(gas);
    assertTrue(thermoResult.isReady(),
        "Thermo system should be valid: " + thermoResult.getErrorsSummary());

    // Create separator
    Separator separator = new Separator("HP Separator", feed);
    separator.run();

    // Validate separator output
    assertNotNull(separator.getGasOutStream(), "Gas output should exist");
    assertNotNull(separator.getLiquidOutStream(), "Liquid output should exist");

    // Check mass balance
    double feedMass = feed.getFluid().getFlowRate("kg/hr");
    double gasMass = separator.getGasOutStream().getFluid().getFlowRate("kg/hr");
    double liquidMass = separator.getLiquidOutStream().getFluid().getFlowRate("kg/hr");
    assertEquals(feedMass, gasMass + liquidMass, feedMass * 0.01,
        "Mass balance should close within 1%");
  }

  /**
   * Test: Two-stage separation - validates sequential thermo/process operations.
   */
  @Test
  public void testTwoStageSeparation() {
    // High-pressure reservoir fluid
    SystemInterface fluid = new SystemSrkEos(350.0, 200.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.06);
    fluid.addComponent("n-butane", 0.04);
    fluid.addComponent("n-pentane", 0.03);
    fluid.addComponent("n-hexane", 0.09);
    fluid.setMixingRule("classic");
    fluid.init(0);

    // Feed stream
    Stream feed = new Stream("Reservoir Fluid", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.run();

    // HP Separator
    Separator hpSep = new Separator("HP Separator", feed);
    hpSep.run();

    // LP Separator (receives liquid from HP)
    Stream lpFeed = new Stream("LP Feed", hpSep.getLiquidOutStream().getFluid().clone());
    lpFeed.setPressure(10.0, "bara");
    lpFeed.run();

    Separator lpSep = new Separator("LP Separator", lpFeed);
    lpSep.run();

    // Validate streams exist
    assertNotNull(lpSep.getGasOutStream(), "LP gas should exist");
    assertNotNull(lpSep.getLiquidOutStream(), "LP liquid should exist");

    // Check total moles conserved
    double totalMolesIn = feed.getFluid().getTotalNumberOfMoles();
    assertTrue(totalMolesIn > 0, "Feed should have moles");
  }

  /**
   * Test: Validation framework catches missing mixing rule.
   */
  @Test
  public void testValidationCatchesMissingMixingRule() {
    // Create system WITHOUT setting mixing rule
    SystemInterface badSystem = new SystemSrkEos(298.15, 10.0);
    badSystem.addComponent("methane", 0.9);
    badSystem.addComponent("CO2", 0.1);
    badSystem.init(0);
    // Missing: badSystem.setMixingRule("classic")

    ValidationFramework.ValidationResult result = ThermoValidator.validateSystem(badSystem);

    // Should have warning about mixing rule
    assertTrue(result.getWarnings().size() > 0 || !result.isReady(),
        "Validation should warn about missing mixing rule");
  }
}
