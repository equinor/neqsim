package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
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
public class TestmercuryTPflash {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new TPflash
     */
    public TestmercuryTPflash() {
    }

    public static void main(String[] args) {

      SystemInterface testSystem = new SystemSrkEos(288.15, 2.0);
     
        testSystem.addComponent("c-hexane", 1);
        
        testSystem.addComponent("mercury",1);

      //  testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        testSystem.setMultiPhaseCheck(true);
      
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        try {
            testOps.TPflash();
            testSystem.display();
        } catch (Exception e) {
            System.out.println(e.toString());
        }
    }
}
