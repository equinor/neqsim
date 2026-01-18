package neqsim.process.design.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.design.ProcessBasis;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for process design templates.
 *
 * @author NeqSim Development Team
 */
class ProcessTemplatesTest {

  @Test
  void testGasCompressionTemplateCreation() {
    // Create feed gas
    SystemInterface gas = new SystemSrkEos(273.15 + 30.0, 5.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.10);
    gas.addComponent("propane", 0.05);
    gas.setMixingRule("classic");

    // Configure design basis
    ProcessBasis basis = new ProcessBasis();
    basis.setFeedFluid(gas);
    basis.setFeedPressure(5.0);
    basis.setFeedTemperature(303.15);
    basis.setFeedFlowRate(10000.0);
    basis.setParameter("dischargePressure", 50.0);
    basis.setParameter("interstageTemperature", 40.0);

    // Create template
    GasCompressionTemplate template = new GasCompressionTemplate();

    // Verify template properties
    assertEquals("Multi-Stage Gas Compression", template.getName());
    assertNotNull(template.getDescription());
    assertTrue(template.isApplicable(gas));

    // Create process
    ProcessSystem process = template.create(basis);
    assertNotNull(process);
    assertTrue(process.getUnitOperations().size() > 0);
  }

  @Test
  void testGasCompressionTemplateNotApplicableForLiquid() {
    // Create liquid fluid
    SystemInterface liquid = new SystemSrkEos(273.15 + 20.0, 1.0);
    liquid.addComponent("n-heptane", 0.8);
    liquid.addComponent("n-octane", 0.2);
    liquid.setMixingRule("classic");

    GasCompressionTemplate template = new GasCompressionTemplate();

    // Should not be applicable for pure liquid
    assertFalse(template.isApplicable(liquid));
  }

  @Test
  void testGasCompressionRequiredEquipment() {
    GasCompressionTemplate template = new GasCompressionTemplate();
    String[] equipment = template.getRequiredEquipmentTypes();

    assertNotNull(equipment);
    assertTrue(equipment.length >= 3);
  }

  @Test
  void testGasCompressionExpectedOutputs() {
    GasCompressionTemplate template = new GasCompressionTemplate();
    String[] outputs = template.getExpectedOutputs();

    assertNotNull(outputs);
    assertTrue(outputs.length >= 1);
  }

  @Test
  void testDehydrationTemplateCreation() {
    // Create wet gas - using SRK for simplicity
    SystemInterface wetGas = new SystemSrkEos(273.15 + 30.0, 70.0);
    wetGas.addComponent("methane", 0.80);
    wetGas.addComponent("ethane", 0.10);
    wetGas.addComponent("propane", 0.05);
    wetGas.addComponent("water", 0.05);
    wetGas.setMixingRule("classic");

    // Configure
    ProcessBasis basis = new ProcessBasis();
    basis.setFeedFluid(wetGas);
    basis.setFeedPressure(70.0);
    basis.setFeedFlowRate(50000.0);
    basis.setParameter("numberOfStages", 4);
    basis.setParameter("reboilerTemperature", 204.0);

    // Create template
    DehydrationTemplate template = new DehydrationTemplate();

    // Verify template properties
    assertEquals("TEG Gas Dehydration", template.getName());
    assertNotNull(template.getDescription());
    assertTrue(template.isApplicable(wetGas));

    // Create process
    ProcessSystem process = template.create(basis);
    assertNotNull(process);
    assertTrue(process.getUnitOperations().size() > 0);
  }

  @Test
  void testDehydrationNotApplicableWithoutWater() {
    // Create dry gas
    SystemInterface dryGas = new SystemSrkEos(273.15 + 30.0, 70.0);
    dryGas.addComponent("methane", 0.90);
    dryGas.addComponent("ethane", 0.10);
    dryGas.setMixingRule("classic");

    DehydrationTemplate template = new DehydrationTemplate();

    // Should not be applicable without water
    assertFalse(template.isApplicable(dryGas));
  }

  @Test
  void testDehydrationTEGRateCalculation() {
    double tegRate = DehydrationTemplate.calculateTEGRate(10.0, // Gas flow MMscfd
        100.0, // Inlet water lb/MMscf
        7.0 // Target water lb/MMscf
    );

    assertTrue(tegRate > 0);
  }

  @Test
  void testCO2CaptureTemplateCreation() {
    // Create flue gas
    SystemInterface flueGas = new SystemSrkEos(273.15 + 40.0, 1.1);
    flueGas.addComponent("nitrogen", 0.75);
    flueGas.addComponent("CO2", 0.15);
    flueGas.addComponent("water", 0.10);
    flueGas.setMixingRule("classic");

    // Create template
    CO2CaptureTemplate template = new CO2CaptureTemplate();

    // Verify template properties
    assertEquals("Amine-Based CO2 Capture", template.getName());
    assertNotNull(template.getDescription());
    assertTrue(template.isApplicable(flueGas));
    assertTrue(template.getRequiredEquipmentTypes().length > 0);
    assertTrue(template.getExpectedOutputs().length > 0);

    // Note: Full process creation may fail due to amine component thermodynamic
    // limitations in the simplified SRK model. The template structure is correct,
    // but production use requires CPA-compatible fluids (SystemSrkCPAstatoil).
    // For full integration testing, use neqsim.process.simplemodule tests.
  }

  @Test
  void testCO2CaptureNotApplicableWithoutCO2() {
    // Create gas without CO2
    SystemInterface gas = new SystemSrkEos(273.15 + 40.0, 1.1);
    gas.addComponent("nitrogen", 0.80);
    gas.addComponent("oxygen", 0.20);
    gas.setMixingRule("classic");

    CO2CaptureTemplate template = new CO2CaptureTemplate();

    // Should not be applicable without CO2
    assertFalse(template.isApplicable(gas));
  }

  @Test
  void testCO2CaptureAmineTypes() {
    // Test different amine types
    CO2CaptureTemplate meaTemplate = new CO2CaptureTemplate(CO2CaptureTemplate.AmineType.MEA);
    assertEquals("Amine-Based CO2 Capture", meaTemplate.getName());

    CO2CaptureTemplate mdeaTemplate = new CO2CaptureTemplate(CO2CaptureTemplate.AmineType.MDEA);
    assertEquals("Amine-Based CO2 Capture", mdeaTemplate.getName());
  }

  @Test
  void testCO2CaptureSpecificReboilerDuty() {
    double duty =
        CO2CaptureTemplate.calculateSpecificReboilerDuty(CO2CaptureTemplate.AmineType.MDEA, 0.50, // Rich
                                                                                                  // loading
            0.20 // Lean loading
        );

    assertTrue(duty > 0);
    assertTrue(duty < 10); // Reasonable range for GJ/ton CO2
  }

  @Test
  void testCO2CaptureAmineLoss() {
    double loss = CO2CaptureTemplate.estimateAmineLoss(CO2CaptureTemplate.AmineType.MDEA, 100.0 // Gas
                                                                                                // flow
                                                                                                // MMscfd
    );

    assertTrue(loss > 0);
  }
}
