package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for CheckValve.
 *
 * @author ESOL
 */
class CheckValveTest {
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
  void testCheckValveCreation() {
    CheckValve valve = new CheckValve("CV-101", inletStream);
    assertNotNull(valve);
    assertEquals("CV-101", valve.getName());
  }

  @Test
  void testSetCrackingPressure() {
    CheckValve valve = new CheckValve("CV-101", inletStream);
    valve.setCrackingPressure(0.5);
    assertEquals(0.5, valve.getCrackingPressure(), 0.001);
  }

  @Test
  void testDefaultCrackingPressure() {
    CheckValve valve = new CheckValve("CV-101", inletStream);
    assertEquals(0.1, valve.getCrackingPressure(), 0.001);
  }

  @Test
  void testValveOpensWithForwardPressure() {
    CheckValve valve = new CheckValve("CV-101", inletStream);
    valve.setCrackingPressure(0.2);
    valve.setCv(250.0);
    valve.setOutletPressure(45.0); // 5 bar drop, well above cracking pressure
    valve.run();

    assertTrue(valve.isOpen(), "Valve should be open with forward pressure");
  }

  @Test
  void testValveClosesWithInsufficientPressure() {
    CheckValve valve = new CheckValve("CV-101", inletStream);
    valve.setCrackingPressure(10.0); // High cracking pressure
    valve.setCv(250.0);
    valve.setOutletPressure(49.0); // Only 1 bar drop, below cracking pressure
    valve.run();

    assertFalse(valve.isOpen(), "Valve should be closed with insufficient pressure");
  }

  @Test
  void testToString() {
    CheckValve valve = new CheckValve("CV-101", inletStream);
    valve.setCrackingPressure(0.3);
    valve.setCv(250.0);

    String result = valve.toString();
    assertTrue(result.contains("CV-101"));
    assertTrue(result.contains("Check Valve"));
    assertTrue(result.contains("0") && result.contains("3")); // Flexible for locale
    assertTrue(result.contains("250")); // Will match "250,0" or "250.0"
  }

  @Test
  void testValveRun() {
    CheckValve valve = new CheckValve("CV-101", inletStream);
    valve.setCrackingPressure(0.2);
    valve.setCv(300.0);
    valve.setOutletPressure(45.0);
    valve.run();

    assertNotNull(valve.getOutletStream());
    assertTrue(valve.getOutletStream().getFlowRate("kg/hr") > 0);
  }
}
