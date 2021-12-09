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
public class TPflashCAPEOPEN {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new TPflash
     */
    public TPflashCAPEOPEN() {
    }

    public static void main(String[] args) {

        SystemInterface testSystem = new SystemSrkEos(288.15 + 5, 15.01325);//
        // SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 25.0, 88.8);//
        testSystem.addComponent("nitrogen", 1.681146444);
        testSystem.addComponent("CO2", 2.185242497);
        testSystem.addComponent("methane", 78.0590685);
        testSystem.addComponent("ethane", 10.04443372);
        testSystem.addComponent("propane", 5.588061435);
        testSystem.addComponent("i-butane", 0.647553889);
        testSystem.addComponent("n-butane", 1.386874239);
        testSystem.addComponent("i-pentane", 0.288952839);
        testSystem.addComponent("n-pentane", 1.446888586);
        testSystem.addComponent("n-hexane", 1.446888586);
        testSystem.addComponent("nC10", 1.446888586);
        testSystem.addComponent("water", 10.35509484);
        testSystem.addComponent("MEG", 0.083156844);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        testSystem.setMultiPhaseCheck(true);
        testSystem.readObject(3469);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        testOps.TPflash();
        testSystem.display();

        testSystem.init(0);
        testSystem.setNumberOfPhases(1);
        testSystem.setMolarComposition(
                new double[] { 0.0, 0.01, 0.01, 7.2, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0 });
        testSystem.init(0, 0);
        testSystem.setPhaseType(0, "gas");
        testSystem.init(3);
        testSystem.initPhysicalProperties();

        testSystem.setMolarComposition(
                new double[] { 0.0, 0.01, 0.01, 7.2, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0 });
        testSystem.init(0, 0);
        testSystem.setPhaseType(0, "liquid");
        testSystem.init(3);
        testSystem.initPhysicalProperties();
        
        testSystem.saveFluid(3469);
    }
}
