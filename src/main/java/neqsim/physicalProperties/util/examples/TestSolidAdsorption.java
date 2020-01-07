/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.physicalProperties.util.examples;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.log4j.Logger;

/**
 *
 * @author esol
// */
public class TestSolidAdsorption {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(TestSolidAdsorption.class);

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemSrkEos(288.15,1.4);
        testSystem.addComponent("methane", 1.0);
        testSystem.addComponent("CO2", 0.1);
        testSystem.addComponent("n-heptane", 0.1);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        try {
            testOps.TPflash();
            testSystem.display();
        } catch (Exception e) {
            logger.error(e.toString());
        }

        testSystem.getInterphaseProperties().initAdsorption();
        testSystem.getInterphaseProperties().setSolidAdsorbentMaterial("AC"); // AC Norit R1
        testSystem.getInterphaseProperties().calcAdsorption();
       // testSystem.initPhysicalProperties();
        System.out.println("surface excess CO2 from gas "+ testSystem.getInterphaseProperties().getAdsorptionCalc("gas").getSurfaceExcess("CO2") + " kg CO2/kg AC");
        System.out.println("surface excess CO2 from oil "+ testSystem.getInterphaseProperties().getAdsorptionCalc("oil").getSurfaceExcess("CO2") + " kg CO2/kg AC");

    }
}
