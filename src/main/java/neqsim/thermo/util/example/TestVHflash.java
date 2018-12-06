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
 * @author  esol
 * @version
 */
public class TestVHflash {

    private static final long serialVersionUID = 1000;
    
    /** Creates new TPflash */
    public TestVHflash() {
    }
    
    public static void main(String args[]){
        
        double pressureInTank = 1.01325; // Pa
        double temperatureInTank = 293.15;
        double totalMolesInTank = 136000 * pressureInTank*1.0e5/8.314/temperatureInTank;
        double molefractionNitrogenInTank = 0.95;
        
        double molesInjectedLNG = 200000.0;
        double molesInjecedVacumBreakerGas = 18 * pressureInTank*1.0e5/8.314/temperatureInTank;
        
        SystemInterface testSystem = new SystemSrkEos(temperatureInTank, pressureInTank);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testSystem.addComponent("methane", totalMolesInTank*(1.0-molefractionNitrogenInTank));
        testSystem.addComponent("nitrogen", totalMolesInTank*molefractionNitrogenInTank);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        
        SystemInterface testSystem2 = new SystemSrkEos(273.15-165.0, pressureInTank);
        ThermodynamicOperations testOps2 = new ThermodynamicOperations(testSystem2);
        testSystem2.addComponent("methane",  molesInjectedLNG);
        testSystem2.createDatabase(true);
        testSystem2.setMixingRule(2);
        
        SystemInterface testSystem3 = new SystemSrkEos(temperatureInTank, pressureInTank);
        ThermodynamicOperations testOps3 = new ThermodynamicOperations(testSystem3);
        testSystem3.addComponent("methane", totalMolesInTank*(1.0-molefractionNitrogenInTank)  + molesInjectedLNG);
        testSystem3.addComponent("nitrogen", totalMolesInTank*molefractionNitrogenInTank);
        testSystem3.createDatabase(true);
        testSystem3.setMixingRule(2);
        
        SystemInterface testSystem4 = new SystemSrkEos(temperatureInTank, pressureInTank);
        ThermodynamicOperations testOps4 = new ThermodynamicOperations(testSystem4);
        testSystem4.addComponent("nitrogen", molesInjecedVacumBreakerGas);
        testSystem4.createDatabase(true);
        testSystem4.setMixingRule(2);
        
        try{
            testOps.TPflash();
            testOps2.TPflash();
            testOps3.TPflash();
            testOps4.TPflash();
            testSystem.display();
            testSystem2.display();
            testSystem3.display();
            testSystem4.display();
            //System.out.println("Cp " + testSystem.getPhase(0).getCp()/testSystem.getPhase(0).getNumberOfMolesInPhase());
            
//
            System.out.println("Volume Nitrogen " + testSystem.getPhase(0).getMolarMass()*testSystem.getNumberOfMoles()/testSystem.getPhase(0).getPhysicalProperties().getDensity());
            System.out.println("Volume Liquid Methane " +  testSystem2.getPhase(0).getMolarMass()*testSystem2.getNumberOfMoles()/testSystem2.getPhase(0).getPhysicalProperties().getDensity());
            System.out.println("Volume Nitrogen from vacum breaker system " + testSystem4.getPhase(0).getMolarMass()*testSystem4.getNumberOfMoles()/testSystem4.getPhase(0).getPhysicalProperties().getDensity());
//
            testOps3.VHflash(testSystem.getEnthalpy()+testSystem2.getEnthalpy(), testSystem.getVolume());
            testSystem3.display();
//            System.out.println("total number of moles " + testSystem3.getTotalNumberOfMoles() );
        } catch(Exception e){
            System.out.println(e.toString());
        }
//        System.out.println("JT " + testSystem.getPhase(0).getJouleThomsonCoefficient());
        //System.out.println("wt%MEG " + testSystem.getPhase(1).getComponent("MEG").getMolarMass()*testSystem.getPhase(1).getComponent("MEG").getx()/testSystem.getPhase(1).getMolarMass());
//        System.out.println("fug" +testSystem.getPhase(0).getComponent("water").getx()*testSystem.getPhase(0).getPressure()*testSystem.getPhase(0).getComponent(0).getFugasityCoefficient());
    }
}
//        testSystem = testSystem.setModel("GERG-water");
//        testSystem.setMixingRule(8);
//
//        testSystem = testSystem.autoSelectModel();
//        testSystem.autoSelectMixingRule();
//          testSystem.setMultiPhaseCheck(true);
//        testOps.setSystem(testSystem);
//
//        System.out.println("new model name " + testSystem.getModelName());
//        try{
//            testOps.TPflash();
//            testSystem.display();
//        }
//        catch(Exception e){
//            System.out.println(e.toString());
//        }
//    }
//}