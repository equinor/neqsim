package neqsim.standards.util.example;

import neqsim.standards.StandardInterface;
import neqsim.standards.gasQuality.Draft_GERG2004;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>Test_GERG2004 class.</p>
 *
 * @author esol
 * @since 2.2.3
 * @version $Id: $Id
 */
public class Test_GERG2004 {
    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    @SuppressWarnings("unused")
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
