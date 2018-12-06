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
public class TestUNIFAC_1 {

    private static final long serialVersionUID = 1000;
    
    /** Creates new TPflash */
    public TestUNIFAC_1() {
    }
    public static void main(String args[]){
        //
        SystemInterface testSystem = new SystemPsrkEos(273.15 + 120.0, 0.15);
        SystemInterface testSystem2 = new SystemPsrkEos(273.15 + 120.0, 0.15);
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
        testSystem.addComponent("TEG", 0.02);
        testSystem.addComponent("MDEA", 0.1);
        testSystem.addComponent("Piperazine", 0.015);
        testSystem.addComponent("water", 0.765);
        testSystem.createDatabase(true);
        //testSystem.setMixingRule(4);
        testSystem.setMixingRule("HV","UNIFAC_PSRK");
        testSystem.init(0);
        testSystem.init(1);
        
        System.out.println("wt% TEG " + (testSystem.getPhase(1).getComponent(0).getx()*testSystem.getPhase(1).getComponent(0).getMolarMass()/testSystem.getPhase(1).getMolarMass()*100.0));
        System.out.println("wt% MDEA " + (testSystem.getPhase(1).getComponent(1).getx()*testSystem.getPhase(1).getComponent(1).getMolarMass()/testSystem.getPhase(1).getMolarMass()*100.0));
        System.out.println("wt% Piperazine " + (testSystem.getPhase(1).getComponent(2).getx()*testSystem.getPhase(1).getComponent(2).getMolarMass()/testSystem.getPhase(1).getMolarMass()*100.0));
        System.out.println("wt% water " + (testSystem.getPhase(1).getComponent(3).getx()*testSystem.getPhase(1).getComponent(3).getMolarMass()/testSystem.getPhase(1).getMolarMass()*100.0));
        
        System.out.println(testSystem.getPhase(1).getActivityCoefficient(0));
        System.out.println("gibbs " + testSystem.getPhase(1).getExessGibbsEnergy());
        for(int i=0;i<2000;i++){
            try{
                //testOps.bubblePointPressureFlash(false);
                testOps.bubblePointPressureFlash(false);
                testSystem2.addComponent("TEG", testSystem.getPhase(0).getComponent(0).getx()/2.0e3);
                testSystem2.addComponent("MDEA", testSystem.getPhase(0).getComponent(1).getx()/2.0e3);
                testSystem2.addComponent("Piperazine", testSystem.getPhase(0).getComponent(2).getx()/2.0e3);
                testSystem2.addComponent("water", testSystem.getPhase(0).getComponent(3).getx()/2.0e3);
                testSystem.addComponent("TEG", -testSystem.getPhase(0).getComponent(0).getx()/2.0e3);
                testSystem.addComponent("MDEA", -testSystem.getPhase(0).getComponent(1).getx()/2.0e3);
                testSystem.addComponent("Piperazine", -testSystem.getPhase(0).getComponent(2).getx()/2.0e3);
                testSystem.addComponent("water", -testSystem.getPhase(0).getComponent(3).getx()/2.0e3);
                testSystem2.init(0);
                testSystem2.init(1);
                testSystem.init(1);
                System.out.println("teg act " + testSystem.getPhase(1).getActivityCoefficient(0));
                System.out.println("MDEA act " + testSystem.getPhase(1).getActivityCoefficient(1));
                System.out.println("Piperazine act " + testSystem.getPhase(1).getActivityCoefficient(2));
                System.out.println("water act " + testSystem.getPhase(1).getActivityCoefficient(3));
                double percentBack = (testSystem2.getPhase(0).getComponent(1).getNumberOfmoles())/0.1*100.0;
                double percentBackPip = (testSystem2.getPhase(0).getComponent(2).getNumberOfmoles())/0.015*100.0;
                double percentBackWater = (testSystem2.getPhase(0).getComponent(3).getNumberOfmoles())/0.765*100.0;
                
                //System.out.println("Pressure " + testSystem.getPressure() + " " + (testSystem.getPhase(1).getComponent(0).getx()*testSystem.getPhase(1).getComponent(0).getMolarMass()/testSystem.getPhase(1).getMolarMass()*100.0) + " " + (testSystem2.getPhase(1).getComponent(0).getx()*testSystem2.getPhase(1).getComponent(0).getMolarMass()/testSystem2.getPhase(1).getMolarMass()*100.0) + "  percent MDEA succefully reclaimed "+ percentBack+ "  percent Piperazine succefully reclaimed "+ percentBackPip+ "  percent water succefully reclaimed "+ percentBackWater);
                //testOps.bubblePointTemperatureFlash();
            } catch(Exception e){
                System.out.println(e.toString());
                e.printStackTrace();
            }
        }
        testSystem.display();
        System.out.println(testSystem.getPhase(1).getActivityCoefficient(0));
        System.out.println("gibbs " + testSystem.getPhase(1).getGibbsEnergy());
    }
    
}
