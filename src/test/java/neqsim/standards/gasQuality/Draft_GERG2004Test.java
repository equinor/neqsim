package neqsim.standards.gasQuality;

import org.junit.jupiter.api.Test;
import neqsim.standards.StandardInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class Draft_GERG2004Test {
    @Test
    void testCalculate() {
        SystemInterface testSystem = new SystemSrkEos(273.15 + 20.0, 200.0);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testSystem.addComponent("methane", 90.9047);
        testSystem.addComponent("ethane", 10.095);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        testSystem.init(0);
        StandardInterface standard = new Draft_GERG2004(testSystem);
        standard.calculate();
        standard.display("test");
    }
}
