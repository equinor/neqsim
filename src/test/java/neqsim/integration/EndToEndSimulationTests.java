package neqsim.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * End-to-end integration tests for complete process simulations.
 * 
 * <p>
 * These tests validate multi-unit flowsheets spanning thermo, PVT, and process modules.
 */
public class EndToEndSimulationTests {

  /**
   * Test: Complete well-to-separator train.
   */
  @Test
  public void testWellToSeparatorTrain() {
    // Reservoir fluid
    SystemInterface reservoir = new SystemSrkEos(370.0, 300.0);
    reservoir.addComponent("methane", 0.65);
    reservoir.addComponent("ethane", 0.10);
    reservoir.addComponent("propane", 0.08);
    reservoir.addComponent("n-butane", 0.07);
    reservoir.addComponent("n-pentane", 0.05);
    reservoir.addComponent("n-hexane", 0.05);
    reservoir.setMixingRule("classic");
    reservoir.init(0);

    // Well stream
    Stream wellStream = new Stream("Well Stream", reservoir);
    wellStream.setFlowRate(50000.0, "kg/hr");
    wellStream.run();

    // Validate thermo
    ValidationFramework.ValidationResult thermoResult = ThermoValidator.validateSystem(reservoir);
    assertTrue(thermoResult.isReady(), "Reservoir fluid should be valid");

    // Choke valve simulation - reduce pressure
    Stream chokedStream = new Stream("Choked Stream", wellStream.getFluid().clone());
    chokedStream.setPressure(50.0, "bara");
    chokedStream.run();

    // HP Separator
    Separator hpSep = new Separator("HP Separator", chokedStream);
    hpSep.run();

    // Validate outputs
    assertNotNull(hpSep.getGasOutStream(), "HP gas should exist");
    assertNotNull(hpSep.getLiquidOutStream(), "HP liquid should exist");

    // Mass balance check
    double inMass = chokedStream.getFluid().getFlowRate("kg/hr");
    double outMass = hpSep.getGasOutStream().getFluid().getFlowRate("kg/hr")
        + hpSep.getLiquidOutStream().getFluid().getFlowRate("kg/hr");
    assertEquals(inMass, outMass, inMass * 0.02, "Mass balance within 2%");
  }

  /**
   * Test: ProcessSystem with heater and separator.
   */
  @Test
  public void testProcessSystemWithHeaterAndSeparator() {
    // Cold gas
    SystemInterface gas = new SystemSrkEos(260.0, 80.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.06);
    gas.addComponent("propane", 0.04);
    gas.setMixingRule("classic");
    gas.init(0);

    // Feed stream
    Stream feed = new Stream("Cold Feed", gas);
    feed.setFlowRate(10000.0, "kg/hr");

    // Heater
    Heater heater = new Heater("Gas Heater", feed);
    heater.setOutTemperature(320.0);

    // Separator after heater
    Separator sep = new Separator("Separator", heater.getOutletStream());

    // Build process system
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(heater);
    process.add(sep);
    process.run();

    // Validate heater output
    double outTemp = heater.getOutletStream().getFluid().getTemperature();
    assertEquals(320.0, outTemp, 1.0, "Heater should reach target temperature");

    // Validate separator
    assertNotNull(sep.getGasOutStream(), "Separator gas output should exist");
  }

  /**
   * Test: Multi-separator cascade.
   */
  @Test
  public void testMultiSeparatorCascade() {
    // Heavy oil
    SystemInterface oil = new SystemSrkEos(400.0, 150.0);
    oil.addComponent("methane", 0.20);
    oil.addComponent("ethane", 0.10);
    oil.addComponent("propane", 0.15);
    oil.addComponent("n-butane", 0.15);
    oil.addComponent("n-pentane", 0.15);
    oil.addComponent("n-hexane", 0.25);
    oil.setMixingRule("classic");
    oil.init(0);

    Stream feed = new Stream("Heavy Oil", oil);
    feed.setFlowRate(20000.0, "kg/hr");
    feed.run();

    // Three-stage separation cascade
    // Stage 1: 150 bar
    Separator stage1 = new Separator("Stage 1", feed);
    stage1.run();

    // Stage 2: 50 bar
    Stream stage2Feed = new Stream("Stage 2 Feed", stage1.getLiquidOutStream().getFluid().clone());
    stage2Feed.setPressure(50.0, "bara");
    stage2Feed.run();
    Separator stage2 = new Separator("Stage 2", stage2Feed);
    stage2.run();

    // Stage 3: 10 bar
    Stream stage3Feed = new Stream("Stage 3 Feed", stage2.getLiquidOutStream().getFluid().clone());
    stage3Feed.setPressure(10.0, "bara");
    stage3Feed.run();
    Separator stage3 = new Separator("Stage 3", stage3Feed);
    stage3.run();

    // Final products should exist
    assertNotNull(stage3.getGasOutStream(), "Final gas should exist");
    assertNotNull(stage3.getLiquidOutStream(), "Final oil should exist");
  }
}
