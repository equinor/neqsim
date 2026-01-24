package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Unit tests for Acoustic-Induced Vibration (AIV) calculations.
 */
public class AIVCalculationTest {

  @Test
  @DisplayName("Test AIV calculation on PipeBeggsAndBrills with pressure drop")
  void testPipeAIVCalculation() {
    // Create high-pressure gas system
    SystemInterface thermoSystem = new SystemSrkEos(298.15, 100.0);
    thermoSystem.addComponent("methane", 0.85);
    thermoSystem.addComponent("ethane", 0.10);
    thermoSystem.addComponent("propane", 0.05);
    thermoSystem.setMixingRule("classic");
    thermoSystem.setTotalFlowRate(50000.0, "kg/hr"); // High flow rate

    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
    ops.TPflash();
    thermoSystem.initPhysicalProperties();

    // Create stream and pipe with significant pressure drop
    Stream stream = new Stream("inlet stream", thermoSystem);
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("test pipe", stream);
    pipe.setDiameter(0.2); // 200 mm
    pipe.setThickness(0.015); // 15 mm
    pipe.setLength(1000.0); // 1 km - will create pressure drop
    pipe.setElevation(0.0);
    pipe.setPipeWallRoughness(1.0e-5);
    pipe.setNumberOfIncrements(10);

    // Create process
    ProcessSystem process = new ProcessSystem();
    process.add(stream);
    process.add(pipe);
    process.run();

    // Calculate AIV
    double aivPower = pipe.calculateAIV();
    double aivLOF = pipe.calculateAIVLikelihoodOfFailure();

    // AIV should be calculated (may be low for gradual pressure drop)
    assertTrue(aivPower >= 0.0, "AIV power should be non-negative");
    assertTrue(aivLOF >= 0.0 && aivLOF <= 1.0, "AIV LOF should be between 0 and 1");

    // Verify AIV is included in FIV analysis
    java.util.Map<String, Object> fivAnalysis = pipe.getFIVAnalysis();
    assertTrue(fivAnalysis.containsKey("AIV_power_kW"), "FIV analysis should include AIV power");
    assertTrue(fivAnalysis.containsKey("AIV_risk"), "FIV analysis should include AIV risk");
    assertTrue(fivAnalysis.containsKey("AIV_LOF"), "FIV analysis should include AIV LOF");

    // Verify constraint is present
    assertTrue(pipe.getCapacityConstraints().containsKey("AIV"),
        "AIV should be in capacity constraints");
  }

  @Test
  @DisplayName("Test AIV calculation on ThrottlingValve with high pressure drop")
  void testValveAIVCalculation() {
    // Create high-pressure gas system
    SystemInterface thermoSystem = new SystemSrkEos(298.15, 100.0);
    thermoSystem.addComponent("methane", 0.85);
    thermoSystem.addComponent("ethane", 0.10);
    thermoSystem.addComponent("propane", 0.05);
    thermoSystem.setMixingRule("classic");
    thermoSystem.setTotalFlowRate(50000.0, "kg/hr"); // High flow rate

    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
    ops.TPflash();
    thermoSystem.initPhysicalProperties();

    // Create stream and valve with large pressure drop
    Stream stream = new Stream("inlet stream", thermoSystem);
    ThrottlingValve valve = new ThrottlingValve("test valve", stream);
    valve.setOutletPressure(50.0, "bara"); // 50 bar drop from 100 bara

    // Create process
    ProcessSystem process = new ProcessSystem();
    process.add(stream);
    process.add(valve);
    process.run();

    // Calculate AIV
    double aivPower = valve.calculateAIV();

    // With 50 bar pressure drop and high flow, AIV should be significant
    assertTrue(aivPower > 0.0, "AIV power should be positive with pressure drop");

    // Verify constraint is present
    assertTrue(valve.getCapacityConstraints().containsKey("AIV"),
        "AIV should be in valve capacity constraints");

    // Check AIV LOF with typical downstream pipe geometry
    double aivLOF = valve.calculateAIVLikelihoodOfFailure(0.2, 0.015); // 200mm OD, 15mm wall
    assertTrue(aivLOF >= 0.0 && aivLOF <= 1.0, "AIV LOF should be between 0 and 1");
  }

  @Test
  @DisplayName("Test AIV is zero with no pressure drop")
  void testAIVZeroWithNoPressureDrop() {
    // Create low-pressure system
    SystemInterface thermoSystem = new SystemSrkEos(298.15, 10.0);
    thermoSystem.addComponent("methane", 1.0);
    thermoSystem.setMixingRule("classic");
    thermoSystem.setTotalFlowRate(100.0, "kg/hr"); // Low flow

    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
    ops.TPflash();
    thermoSystem.initPhysicalProperties();

    // Create stream and very short pipe (minimal pressure drop)
    Stream stream = new Stream("inlet stream", thermoSystem);
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("test pipe", stream);
    pipe.setDiameter(0.5); // Large diameter
    pipe.setThickness(0.02);
    pipe.setLength(1.0); // Very short
    pipe.setNumberOfIncrements(2);

    ProcessSystem process = new ProcessSystem();
    process.add(stream);
    process.add(pipe);
    process.run();

    // AIV should be very low or zero
    double aivPower = pipe.calculateAIV();
    assertTrue(aivPower < 1.0, "AIV should be very low with minimal pressure drop");
  }

  @Test
  @DisplayName("Test AIV design limit setters/getters on pipe")
  void testPipeAIVDesignLimits() {
    SystemInterface thermoSystem = new SystemSrkEos(298.15, 50.0);
    thermoSystem.addComponent("methane", 1.0);
    thermoSystem.setMixingRule("classic");
    thermoSystem.setTotalFlowRate(1000.0, "kg/hr");

    Stream stream = new Stream("inlet stream", thermoSystem);
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("test pipe", stream);

    // Test default
    assertEquals(25.0, pipe.getMaxDesignAIV(), 0.01);

    // Test setter
    pipe.setMaxDesignAIV(15.0);
    assertEquals(15.0, pipe.getMaxDesignAIV(), 0.01);
  }

  @Test
  @DisplayName("Test AIV design limit setters/getters on valve")
  void testValveAIVDesignLimits() {
    SystemInterface thermoSystem = new SystemSrkEos(298.15, 50.0);
    thermoSystem.addComponent("methane", 1.0);
    thermoSystem.setMixingRule("classic");
    thermoSystem.setTotalFlowRate(1000.0, "kg/hr");

    Stream stream = new Stream("inlet stream", thermoSystem);
    ThrottlingValve valve = new ThrottlingValve("test valve", stream);

    // Test default
    assertEquals(10.0, valve.getMaxDesignAIV(), 0.01);

    // Test setter
    valve.setMaxDesignAIV(20.0);
    assertEquals(20.0, valve.getMaxDesignAIV(), 0.01);
  }
}
