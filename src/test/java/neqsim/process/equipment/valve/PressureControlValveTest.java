package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.PressureControlValve.ControlMode;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for PressureControlValve.
 *
 * @author ESOL
 */
class PressureControlValveTest {
  SystemInterface testSystem;
  Stream inletStream;

  @BeforeEach
  void setUp() {
    // Create test system
    testSystem = new SystemSrkEos(298.15, 50.0);
    testSystem.addComponent("methane", 10.0);
    testSystem.addComponent("ethane", 5.0);
    testSystem.setMixingRule(2);

    inletStream = new Stream("Inlet", testSystem);
    inletStream.setFlowRate(10000.0, "kg/hr");
    inletStream.setPressure(50.0, "bara");
    inletStream.setTemperature(25.0, "C");
    inletStream.run();
  }

  @Test
  void testPressureControlValveCreation() {
    PressureControlValve valve = new PressureControlValve("PCV-101", inletStream);
    assertNotNull(valve);
    assertEquals("PCV-101", valve.getName());
  }

  @Test
  void testSetPressureSetpoint() {
    PressureControlValve valve = new PressureControlValve("PCV-101", inletStream);
    valve.setPressureSetpoint(25.0);
    assertEquals(25.0, valve.getPressureSetpoint(), 0.01);
  }

  @Test
  void testSetControlMode() {
    PressureControlValve valve = new PressureControlValve("PCV-101", inletStream);
    valve.setControlMode(ControlMode.UPSTREAM);
    assertEquals(ControlMode.UPSTREAM, valve.getControlMode());
  }

  @Test
  void testDefaultControlMode() {
    PressureControlValve valve = new PressureControlValve("PCV-101", inletStream);
    assertEquals(ControlMode.DOWNSTREAM, valve.getControlMode());
  }

  @Test
  void testSetControllerGain() {
    PressureControlValve valve = new PressureControlValve("PCV-101", inletStream);
    valve.setControllerGain(8.0);
    assertEquals(8.0, valve.getControllerGain(), 0.01);
  }

  @Test
  void testAutoModeToggle() {
    PressureControlValve valve = new PressureControlValve("PCV-101", inletStream);
    assertTrue(valve.isAutoMode(), "Should default to auto mode");

    valve.setAutoMode(false);
    assertFalse(valve.isAutoMode());

    valve.setAutoMode(true);
    assertTrue(valve.isAutoMode());
  }

  @Test
  void testValveRun() {
    PressureControlValve valve = new PressureControlValve("PCV-101", inletStream);
    valve.setPressureSetpoint(25.0);
    valve.setControllerGain(5.0);
    valve.setCv(300.0);
    valve.setOutletPressure(30.0);
    valve.run();

    assertNotNull(valve.getOutletStream());
    assertTrue(valve.getProcessVariable() > 0, "Process variable should be positive");
  }

  @Test
  void testManualMode() {
    PressureControlValve valve = new PressureControlValve("PCV-101", inletStream);
    valve.setAutoMode(false);
    valve.setPercentValveOpening(75.0);
    valve.setCv(300.0);
    valve.setOutletPressure(40.0);
    valve.run();

    assertEquals(75.0, valve.getPercentValveOpening(), 0.1,
        "Manual mode should maintain set opening");
  }

  @Test
  void testToString() {
    PressureControlValve valve = new PressureControlValve("PCV-101", inletStream);
    valve.setPressureSetpoint(25.0);
    valve.setControllerGain(5.0);

    String result = valve.toString();
    assertTrue(result.contains("PCV-101"));
    assertTrue(result.contains("Pressure Control Valve"));
    assertTrue(result.contains("25"));
  }

  @Test
  void testControlError() {
    PressureControlValve valve = new PressureControlValve("PCV-101", inletStream);
    valve.setPressureSetpoint(25.0);
    valve.setCv(300.0);
    valve.setOutletPressure(30.0);
    valve.run();

    double error = valve.getControlError();
    assertNotNull(error);
    // Error should be setpoint - PV
  }
}
