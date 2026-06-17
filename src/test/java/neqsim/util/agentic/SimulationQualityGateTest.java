package neqsim.util.agentic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for SimulationQualityGate.
 */
class SimulationQualityGateTest {

  @Test
  void testValidProcessPasses() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.setPressure(50.0, "bara");
    feed.setTemperature(25.0, "C");

    Separator sep = new Separator("HP sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    SimulationQualityGate gate = new SimulationQualityGate(process);
    gate.validate();

    assertTrue(gate.isPassed(), "Valid process should pass QA gate");
    assertEquals(0, gate.getErrorCount());
  }

  @Test
  void testJsonOutput() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");

    Separator sep = new Separator("HP sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    SimulationQualityGate gate = new SimulationQualityGate(process);
    gate.validate();

    String json = gate.toJson();
    assertNotNull(json);
    assertTrue(json.contains("passed"));
    assertTrue(json.contains("errorCount"));
    assertTrue(json.contains("warningCount"));
  }

  @Test
  void testNullProcessThrows() {
    assertThrows(IllegalArgumentException.class, () -> new SimulationQualityGate(null));
  }

  @Test
  void testCheckBeforeValidateThrows() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);

    SimulationQualityGate gate = new SimulationQualityGate(process);
    assertThrows(IllegalStateException.class, () -> gate.isPassed());
  }

  @Test
  void testCustomTolerances() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");

    Separator sep = new Separator("sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    SimulationQualityGate gate = new SimulationQualityGate(process);
    gate.setMassBalanceTolerance(0.001);
    gate.setEnergyBalanceTolerance(0.01);
    gate.validate();

    // Should still pass — NeqSim process sim is internally consistent
    assertTrue(gate.isPassed());
  }

  @Test
  void testIssuesCaptured() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");

    Separator sep = new Separator("sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    SimulationQualityGate gate = new SimulationQualityGate(process);
    gate.validate();

    // Issues list should be accessible regardless of pass/fail
    assertNotNull(gate.getIssues());
  }
}
