package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.LevelControlValve.ControlAction;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for LevelControlValve.
 *
 * @author ESOL
 */
class LevelControlValveTest {
  SystemInterface testSystem;
  Stream inletStream;

  @BeforeEach
  void setUp() {
    // Create test system
    testSystem = new SystemSrkEos(298.15, 20.0);
    testSystem.addComponent("methane", 5.0);
    testSystem.addComponent("propane", 10.0);
    testSystem.setMixingRule(2);

    inletStream = new Stream("Inlet", testSystem);
    inletStream.setFlowRate(5000.0, "kg/hr");
    inletStream.setPressure(20.0, "bara");
    inletStream.setTemperature(25.0, "C");
    inletStream.run();
  }

  @Test
  void testLevelControlValveCreation() {
    LevelControlValve valve = new LevelControlValve("LCV-101", inletStream);
    assertNotNull(valve);
    assertEquals("LCV-101", valve.getName());
  }

  @Test
  void testSetLevelSetpoint() {
    LevelControlValve valve = new LevelControlValve("LCV-101", inletStream);
    valve.setLevelSetpoint(60.0);
    assertEquals(60.0, valve.getLevelSetpoint(), 0.01);
  }

  @Test
  void testLevelSetpointLimits() {
    LevelControlValve valve = new LevelControlValve("LCV-101", inletStream);

    valve.setLevelSetpoint(120.0);
    assertEquals(100.0, valve.getLevelSetpoint(), 0.01, "Setpoint should be limited to 100%");

    valve.setLevelSetpoint(-10.0);
    assertEquals(0.0, valve.getLevelSetpoint(), 0.01, "Setpoint should be limited to 0%");
  }

  @Test
  void testSetMeasuredLevel() {
    LevelControlValve valve = new LevelControlValve("LCV-101", inletStream);
    valve.setMeasuredLevel(45.0);
    assertEquals(45.0, valve.getMeasuredLevel(), 0.01);
  }

  @Test
  void testMeasuredLevelLimits() {
    LevelControlValve valve = new LevelControlValve("LCV-101", inletStream);

    valve.setMeasuredLevel(150.0);
    assertEquals(100.0, valve.getMeasuredLevel(), 0.01, "Level should be limited to 100%");

    valve.setMeasuredLevel(-20.0);
    assertEquals(0.0, valve.getMeasuredLevel(), 0.01, "Level should be limited to 0%");
  }

  @Test
  void testSetControlAction() {
    LevelControlValve valve = new LevelControlValve("LCV-101", inletStream);
    valve.setControlAction(ControlAction.REVERSE);
    assertEquals(ControlAction.REVERSE, valve.getControlAction());
  }

  @Test
  void testDefaultControlAction() {
    LevelControlValve valve = new LevelControlValve("LCV-101", inletStream);
    assertEquals(ControlAction.DIRECT, valve.getControlAction());
  }

  @Test
  void testSetControllerGain() {
    LevelControlValve valve = new LevelControlValve("LCV-101", inletStream);
    valve.setControllerGain(5.0);
    assertEquals(5.0, valve.getControllerGain(), 0.01);
  }

  @Test
  void testAutoModeToggle() {
    LevelControlValve valve = new LevelControlValve("LCV-101", inletStream);
    assertTrue(valve.isAutoMode(), "Should default to auto mode");

    valve.setAutoMode(false);
    assertFalse(valve.isAutoMode());

    valve.setAutoMode(true);
    assertTrue(valve.isAutoMode());
  }

  @Test
  void testSetFailSafePosition() {
    LevelControlValve valve = new LevelControlValve("LCV-101", inletStream);
    valve.setFailSafePosition(100.0); // Fail-open
    assertEquals(100.0, valve.getFailSafePosition(), 0.01);

    valve.setFailSafePosition(0.0); // Fail-closed
    assertEquals(0.0, valve.getFailSafePosition(), 0.01);
  }

  @Test
  void testControlError() {
    LevelControlValve valve = new LevelControlValve("LCV-101", inletStream);
    valve.setLevelSetpoint(50.0);
    valve.setMeasuredLevel(45.0);
    valve.setCv(150.0);
    valve.setOutletPressure(15.0);
    valve.run();

    assertEquals(5.0, valve.getControlError(), 0.01,
        "Control error should be setpoint - measured level");
  }

  @Test
  void testValveRun() {
    LevelControlValve valve = new LevelControlValve("LCV-101", inletStream);
    valve.setLevelSetpoint(50.0);
    valve.setMeasuredLevel(60.0); // High level
    valve.setControllerGain(3.0);
    valve.setCv(150.0);
    valve.setOutletPressure(15.0);
    valve.run();

    assertNotNull(valve.getOutletStream());
    assertTrue(valve.getOutletStream().getFlowRate("kg/hr") > 0);
  }

  @Test
  void testManualMode() {
    LevelControlValve valve = new LevelControlValve("LCV-101", inletStream);
    valve.setAutoMode(false);
    valve.setPercentValveOpening(40.0);
    valve.setCv(150.0);
    valve.setOutletPressure(15.0);
    valve.run();

    assertEquals(40.0, valve.getPercentValveOpening(), 0.1,
        "Manual mode should maintain set opening");
  }

  @Test
  void testToString() {
    LevelControlValve valve = new LevelControlValve("LCV-101", inletStream);
    valve.setLevelSetpoint(50.0);
    valve.setMeasuredLevel(45.0);
    valve.setControllerGain(3.0);

    String result = valve.toString();
    assertTrue(result.contains("LCV-101"));
    assertTrue(result.contains("Level Control Valve"));
    assertTrue(result.contains("50"));
    assertTrue(result.contains("45"));
  }
}
