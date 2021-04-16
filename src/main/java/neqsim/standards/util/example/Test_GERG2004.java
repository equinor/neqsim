package neqsim.standards.util.example;

import neqsim.standards.StandardInterface;
import neqsim.standards.gasQuality.Draft_GERG2004;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/*
 * PhaseEnvelope.java
 *
 * Created on 27. september 2001, 10:21
 */

/**
 *
 * @author esol
 * @version
 */
public class Test_GERG2004 {

    private static final long serialVersionUID = 1000;

    /** Creates new PhaseEnvelope */
    public Test_GERG2004() {
    }

    public static void main(String args[]) {

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
