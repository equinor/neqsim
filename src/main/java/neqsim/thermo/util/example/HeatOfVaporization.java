package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/*
 * TPflash.java
 *
 * Created on 27. september 2001, 09:43
 */

/*
 *
 * @author esol @version
 */
public class HeatOfVaporization {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new TPflash
     */
    public HeatOfVaporization() {
    }

    public static void main(String[] args) {

        SystemInterface testSystem = new SystemSrkCPAstatoil(288.15000000, 0.001);//
        testSystem.addComponent("TEG", 1);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(10);
        testSystem.init(0);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        try {
            testOps.bubblePointPressureFlash(false);
            testSystem.display();
            double heatVap = testSystem.getHeatOfVaporization();
            System.out.println("heat of vaporization " + heatVap + " J/mol");
            System.out.println("heat of vaporization " + (heatVap/testSystem.getMolarMass()) + " J/kg");
        } catch (Exception e) {
            System.out.println(e.toString());
        }

    }
}
