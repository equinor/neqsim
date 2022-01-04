package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPCSAFT;

/**
 * <p>CalcActivityFromPR class.</p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class CalcActivityFromPR {
    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        SystemInterface testSystem = new SystemPCSAFT(150.0, 10.0);
        testSystem.addComponent("methane", 1.0);
        testSystem.addComponent("n-hexane", 10.0001);
        testSystem.setMixingRule(1);
        testSystem.createDatabase(true);
        testSystem.init(0);
        testSystem.init(3);
        // System.out.println("activity coefficient " +
        // testSystem.getPhase(1).getActivityCoefficient(1,1));
        testSystem.display();
    }
}
