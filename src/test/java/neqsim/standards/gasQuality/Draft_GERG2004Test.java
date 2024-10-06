package neqsim.standards.gasQuality;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.standards.StandardInterface;
import neqsim.standards.gasquality.Draft_GERG2004;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class Draft_GERG2004Test extends neqsim.NeqSimTest{
    @Disabled
    @Test
    void testCalculate() {
        SystemInterface testSystem = new SystemSrkEos(273.15 + 20.0, 200.0);
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
