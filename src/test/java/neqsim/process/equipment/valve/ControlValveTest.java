package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for ControlValve.
 *
 * @author ESOL
 */
class ControlValveTest {
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
  void testControlValveCreation() {
    ControlValve valve = new ControlValve("FCV-101", inletStream);
    assertNotNull(valve);
    assertEquals("FCV-101", valve.getName());
  }

  @Test
  void testSetPercentValveOpening() {
    ControlValve valve = new ControlValve("FCV-101", inletStream);
    valve.setPercentValveOpening(50.0);
    assertEquals(50.0, valve.getPercentValveOpening(), 0.01);
  }

  @Test
  void testSetCv() {
    ControlValve valve = new ControlValve("FCV-101", inletStream);
    valve.setCv(300.0);
    assertEquals(300.0, valve.getCv(), 0.01);
  }

  @Test
  void testValveRun() {
    ControlValve valve = new ControlValve("FCV-101", inletStream);
    valve.setPercentValveOpening(50.0);
    valve.setCv(300.0);
    valve.setOutletPressure(45.0);
    valve.run();

    assertNotNull(valve.getOutletStream());
    assertTrue(valve.getOutletStream().getFlowRate("kg/hr") > 0);
  }

  @Test
  void testToString() {
    ControlValve valve = new ControlValve("FCV-101", inletStream);
    valve.setPercentValveOpening(50.0);
    valve.setCv(300.0);

    String result = valve.toString();
    assertTrue(result.contains("FCV-101"));
    assertTrue(result.contains("Control Valve"));
    assertTrue(result.contains("50"));
    assertTrue(result.contains("300"));
  }

  @Test
  void testInheritsThrottlingValveBehavior() {
    // Verify that ControlValve inherits all ThrottlingValve functionality
    ControlValve valve = new ControlValve("FCV-101", inletStream);
    valve.setPercentValveOpening(75.0);
    valve.setCv(250.0);
    valve.setOutletPressure(40.0);
    valve.run();

    // Should behave exactly like ThrottlingValve
    assertEquals(75.0, valve.getPercentValveOpening(), 0.01);
    assertEquals(250.0, valve.getCv(), 0.01);
    assertNotNull(valve.getOutletStream());

    // Check that pressure drop occurred
    double inletPressure = inletStream.getPressure("bara");
    double outletPressure = valve.getOutletStream().getPressure("bara");
    assertTrue(outletPressure < inletPressure);
  }
}
