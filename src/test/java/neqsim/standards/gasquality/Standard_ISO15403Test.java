package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class Standard_ISO15403Test {
  private static SystemInterface testSystem = null;
  private static Standard_ISO15403 ISO15403 = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeAll
  static void setUpBeforeClass() throws Exception {
    testSystem = new SystemSrkEos(273.15 + 20.0, 1.0);
    testSystem.addComponent("methane", 0.82);
    testSystem.addComponent("ethane", 0.1);
    testSystem.addComponent("propane", 0.01);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("CO2", 0.01);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();

    ISO15403 = new Standard_ISO15403(testSystem);
    ISO15403.calculate();
  }

  @Test
  void testCalculate() {
    ISO15403.calculate();
  }

  @Test
  void testGetUnit() {
    assertEquals("", ISO15403.getUnit("MON"));
    assertEquals("", ISO15403.getUnit("NM"));
  }

  @Test
  void testGetValue() {
    assertEquals(75.8729229473, ISO15403.getValue("NM"), 1e-6);
    assertEquals(124.078147368421, ISO15403.getValue("MON"), 1e-6);

    assertEquals(75.8729229473, ISO15403.getValue("NM", "-"), 1e-6);
    assertEquals(124.078147368421, ISO15403.getValue("MON", "-"), 1e-6);
  }

  @Test
  void testIsOnSpec() {
    assertEquals(true, ISO15403.isOnSpec());
  }

  @Test
  @Disabled
  void testDisplay() {
    Standard_ISO15403 s = new Standard_ISO15403(null);
    s.display("test");

    s = new Standard_ISO15403(testSystem);
    s.display("test");
  }
}
