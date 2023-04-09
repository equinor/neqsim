package neqsim.thermo.component;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class ComponentTest  extends neqsim.NeqSimTest{
    static SystemInterface thermoSystem = null;

    /**
     * @throws java.lang.Exception
     */
    @BeforeAll
    static void setUpBeforeClass() throws Exception {
        thermoSystem = new SystemSrkEos(298.0, 100.0);
        thermoSystem.addComponent("water", 1.0);
    }

    @Test
    public void testGetFlowRate() {
        Assertions.assertEquals(thermoSystem.getComponent("water").getFlowRate("kg/min"),
                60 * thermoSystem.getComponent("water").getFlowRate("kg/sec"));
        Assertions.assertEquals(thermoSystem.getComponent("water").getFlowRate("kg/hr"),
                60 * thermoSystem.getComponent("water").getFlowRate("kg/min"));
        Assertions.assertEquals(thermoSystem.getComponent("water").getFlowRate("tonnes/year"),
                thermoSystem.getComponent("water").getFlowRate("kg/hr")*24*365/1000);
        Assertions.assertEquals(thermoSystem.getComponent("water").getFlowRate("m3/min"),
                60 * thermoSystem.getComponent("water").getFlowRate("m3/sec"));
        Assertions.assertEquals(thermoSystem.getComponent("water").getFlowRate("m3/hr"),
                60 * thermoSystem.getComponent("water").getFlowRate("m3/min"));
        Assertions.assertEquals(thermoSystem.getComponent("water").getFlowRate("mole/min"),
                60 * thermoSystem.getComponent("water").getFlowRate("mole/sec"));
        Assertions.assertEquals(thermoSystem.getComponent("water").getFlowRate("mole/hr"),
                60 * thermoSystem.getComponent("water").getFlowRate("mole/min"));
        // throw new RuntimeException("failed.. unit: " + flowunit + " not supported");
    }

    @Test
    public void testGetTotalFlowRate() {
        Assertions.assertEquals(thermoSystem.getComponent("water").getTotalFlowRate("kg/min"),
                60 * thermoSystem.getComponent("water").getTotalFlowRate("kg/sec"));
        Assertions.assertEquals(thermoSystem.getComponent("water").getTotalFlowRate("kg/hr"),
                60 * thermoSystem.getComponent("water").getTotalFlowRate("kg/min"));

        // Assertions.assertEquals(thermoSystem.getComponent("water").getTotalFlowRate("m3/min"),60
        // * thermoSystem.getComponent("water").getTotalFlowRate("m3/sec"));
        // Assertions.assertEquals(thermoSystem.getComponent("water").getTotalFlowRate("m3/hr"),60 *
        // thermoSystem.getComponent("water").getTotalFlowRate("m3/min"));

        Assertions.assertEquals(thermoSystem.getComponent("water").getTotalFlowRate("mole/min"),
                60 * thermoSystem.getComponent("water").getTotalFlowRate("mole/sec"));
        Assertions.assertEquals(thermoSystem.getComponent("water").getTotalFlowRate("mole/hr"),
                60 * thermoSystem.getComponent("water").getTotalFlowRate("mole/min"));
        // throw new RuntimeException("failed.. unit: " + flowunit + " not supported");
    }

    @Test
    public void nmVOCFlowRateTest() {
      thermoSystem = new SystemSrkEos(298.0, 100.0);
      thermoSystem.addComponent("methane", 1.0);
      thermoSystem.addComponent("ethane", 1.0);
      thermoSystem.addComponent("propane", 1.0);
      thermoSystem.addComponent("i-butane", 1.0);
      thermoSystem.addComponent("n-butane", 1.0);
      thermoSystem.addComponent("i-pentane", 1.0);
      thermoSystem.addComponent("n-pentane", 1.0);
      Assertions.assertEquals(thermoSystem.getnmVOCFlowRate("tonnes/year"),
      10555.540704);
    }
}
