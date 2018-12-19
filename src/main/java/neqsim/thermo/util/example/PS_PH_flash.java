package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
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
public class PS_PH_flash {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new TPflash
     */
    public PS_PH_flash() {
    }

    public static void main(String args[]) {
        //SystemInterface testSystem = new SystemSrkMathiasCopeman(273.15 + 5, 80);
        SystemInterface testSystem = new SystemSrkEos(273.15 + 70, 19.0);
        //  SystemInterface testSystem = new SystemGERG2004Eos(277.59,689.474483);
        //SystemInterface testSystem = new SystemSrkSchwartzentruberEos(350.15,30.00);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        //  testSystem.addComponent("methane", 0.95);
        testSystem.addComponent("ethane", 0.005);
        testSystem.addComponent("propane", 0.004);
        testSystem.addComponent("n-butane", 0.4);
        testSystem.addComponent("i-butane", 0.4);
        testSystem.addComponent("i-pentane", 0.004);
        // testSystem.addComponent("ethane", 0.05);
       //  testSystem.addComponent("water", 1.19299e-1);
        //  testSystem.addComponent("n-butane", 3.53465e-1);
        // testSystem.addComponent("propane", 50);
        //testSystem.addComponent("CO2", 50);
        //testSystem.addComponent("water", 20);

        // 1- orginal no interaction 2- classic w interaction
        // 3- Huron-Vidal 4- Wong-Sandler
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        try {
            testOps.bubblePointTemperatureFlash();
        } catch (Exception e) {

        }
        testSystem.display();

        testSystem.setPressure(testSystem.getPressure() - 1.2);

        // double entropy = testSystem.getEntropy(); //
        //System.out.println("entropy spec" + entropy); 
        double enthalpy = testSystem.getEntropy();
        // System.out.println("enthalpy spec" + enthalpy);

        double entropy = testSystem.getEntropy();
        // System.out.println("entropy spec" + entropy);

        //   testSystem.setPressure(20.894745);
        testOps.PHflash(enthalpy, 0);
        testSystem.display();

        //    testSystem.display();
        //     testOps.PSflash(entropy);
        // testSystem.display();
        // System.out.println("enthalpy spec" + testSystem.getEnthalpy());
    }
}
