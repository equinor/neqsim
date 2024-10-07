package neqsim.standards.salesContract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.standards.salescontract.BaseContract;
import neqsim.standards.salescontract.ContractInterface;
import neqsim.thermo.system.SystemGERGwaterEos;
import neqsim.thermo.system.SystemInterface;

/**
 * @author ESOL
 */
class BaseContractTest extends neqsim.NeqSimTest {
  static SystemInterface testSystem = null;
  static ContractInterface standard = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeAll
  static void testRunCheck() throws Exception {
    testSystem = new SystemGERGwaterEos(273.15 - 5.0, 20.0);
    testSystem.addComponent("methane", 0.9);
    testSystem.addComponent("ethane", 0.04);
    testSystem.addComponent("propane", 0.02);
    testSystem.addComponent("n-heptane", 0.00012);
    testSystem.addComponent("H2S", 0.000012);
    testSystem.addComponent("water", 0.000071);
    testSystem.addComponent("oxygen", 0.0012);
    testSystem.addComponent("CO2", 0.022);
    testSystem.addComponent("nitrogen", 0.022);
    testSystem.setMixingRule(8);
    testSystem.init(0);
    standard = new BaseContract(testSystem, "UK-GSMR1996", "UK");
  }

  /**
   * Test method
   */
  @Test
  void testUKGSMR1996() {
    standard.runCheck();
    // standard.prettyPrint();
  }

  @Test
  void testUKGSMR19962() {
    standard = new BaseContract(testSystem, "central", "Brazil");
    standard.runCheck();
    assertEquals(2.18817727816606, Double.parseDouble(standard.getResultTable()[1][1]), 1e-6);
    // standard.prettyPrint();
  }

  @Test
  @Disabled
  void testDisplay() {
    standard.display();

    BaseContract bc = new BaseContract();
    bc.display();
  }
}
