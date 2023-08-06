package neqsim.standards.salesContract;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemGERGwaterEos;
import neqsim.thermo.system.SystemInterface;

public class BaseContractTest extends neqsim.NeqSimTest {
  @Test
  void testRunCheck() {
    SystemInterface testSystem = new SystemGERGwaterEos(273.15 - 5.0, 20.0);

    testSystem.addComponent("methane", 0.9);
    testSystem.addComponent("ethane", 0.04);
    testSystem.addComponent("propane", 0.02);
    testSystem.addComponent("n-heptane", 0.00012);
    testSystem.addComponent("H2S", 0.000012);
    testSystem.addComponent("water", 0.0000071);
    testSystem.addComponent("oxygen", 0.0012);
    testSystem.addComponent("CO2", 0.0022);
    testSystem.addComponent("nitrogen", 0.022);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(8);

    testSystem.init(0);

    ContractInterface standard = new BaseContract(testSystem, "UK-GSMR1996", "UK");
    standard.runCheck();
    standard.prettyPrint();
  }
}
