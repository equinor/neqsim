package neqsim.standards.gasQuality;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class Standard_ISO6578Test {
    @Test
    void testCalculate() {
        SystemInterface testSystem = new SystemSrkEos(273.15 - 160.0, 1.0);

        testSystem.addComponent("nitrogen", 0.006538);
        testSystem.addComponent("methane", 0.91863);
        testSystem.addComponent("ethane", 0.058382);
        testSystem.addComponent("propane", 0.011993);
        // testSystem.addComponent("i-butane", 0.00);
        testSystem.addComponent("n-butane", 0.003255);
        testSystem.addComponent("i-pentane", 0.000657);
        testSystem.addComponent("n-pentane", 0.000545);

        testSystem.createDatabase(true);
        // testSystem.setMultiphaseWaxCheck(true);
        testSystem.setMixingRule(2);

        testSystem.init(0);
        Standard_ISO6578 standard = new Standard_ISO6578(testSystem);// testSystem);
        standard.calculate();
        testSystem.display();

        Assertions.assertEquals(0.30930700620842033, standard.getCorrFactor1());
        // ((Standard_ISO6578) standard).useISO6578VolumeCorrectionFacotrs(false);

        standard.calculate();
        Assertions.assertEquals(0.30930700620842033, standard.getCorrFactor1());
        // testSystem.display();
    }
}
