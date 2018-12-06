package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPsrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/*
 * TPflash.java
 *
 * Created on 27. september 2001, 09:43
 */

/*
 *
 * @author  esol
 * @version
 */
public class TestUNIFAC {

    private static final long serialVersionUID = 1000;
    
    /** Creates new TPflash */
    public TestUNIFAC() {
    }
    public static void main(String args[]){
        //
        SystemInterface testSystem = new SystemPsrkEos(273.15 + 120.0, 0.15);
        // SystemInterface testSystem = new SystemSrkSchwartzentruberEos(273.15 + 25.0, 1.01325301325);
        //SystemInterface testSystem = new SystemNRTL(273.15 + 174.0,1.301325);
        //SystemInterface testSystem = new SystemPsrkEos(273.15 + 74.0,1.301325);
        //SystemInterface testSystem = new SystemSrkEos(143.15,1.301325);
        //SystemInterface testSystem = new SystemSrkSchwartzentruberEos(193.15 ,10.301325);
        
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        // testSystem.addComponent("acetone", 100.0);
        // testSystem.addComponent("n-pentane", 100.00047);
        // testSystem.addComponent("c-C6", 90.0);
        // testSystem.addComponent("methane", 10.0);
        // testSystem.addComponent("ethane", 10.0);
        testSystem.addComponent("TEG", 0.05);
        testSystem.addComponent("MDEA", 0.95);
        //testSystem.addComponent("water", 10.0);
        testSystem.createDatabase(true);
        //testSystem.setMixingRule(4);
        testSystem.setMixingRule("HV","UNIFAC_PSRK");
        testSystem.init(0); 
        testSystem.init(1);
        System.out.println(testSystem.getPhase(1).getActivityCoefficient(0));
        System.out.println("gibbs " + testSystem.getPhase(1).getExessGibbsEnergy());
        try{
             //testOps.bubblePointPressureFlash(false);
             testOps.dewPointPressureFlash();
             //testOps.bubblePointTemperatureFlash();
        }
        catch(Exception e){
            System.out.println(e.toString());
            e.printStackTrace();
        }
        testSystem.display();
        System.out.println(testSystem.getPhase(1).getActivityCoefficient(0));
        System.out.println("gibbs " + testSystem.getPhase(1).getGibbsEnergy());
    }
    
}
