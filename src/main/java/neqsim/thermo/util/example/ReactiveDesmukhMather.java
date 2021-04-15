package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemDesmukhMather;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/*
 * TPflash.java
 *
 * Created on 27. september 2001, 09:43
 */

/**
 *
 * @author esol
 * @version
 */
public class ReactiveDesmukhMather {

    private static final long serialVersionUID = 1000;

    /** Creates new TPflash */
    public ReactiveDesmukhMather() {
    }

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemDesmukhMather(290.0, 5.1);

        testSystem.addComponent("CO2", 0.9000509251997);
        testSystem.addComponent("MDEA", 1.0);
        testSystem.addComponent("water", 9.0);

        testSystem.chemicalReactionInit();
        testSystem.setMixingRule(2);
        testSystem.createDatabase(true);
        // testSystem.setPhysicalPropertyModel(3);

        ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);

        try {
            ops.bubblePointPressureFlash(false);
        } catch (Exception e) {
        }
        testSystem.display();
    }
}
