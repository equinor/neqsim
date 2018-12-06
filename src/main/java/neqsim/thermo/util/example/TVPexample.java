/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.thermo.util.example;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author MLLU
 */
public class TVPexample {
    private static final long serialVersionUID = 1000;
    public TVPexample(){};
    
    public static void main(String[] args) {
        
        SystemInterface testSystem = new SystemSrkEos(275.15+37.7778, 1.0);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testSystem.addComponent("methane", 0.1);
        testSystem.addComponent("ethane", 0.2);
        testSystem.addComponent("propane", 0.3);
        testSystem.addComponent("i-butane", 0.3);
        testSystem.addComponent("n-butane", 0.1);
        testSystem.addComponent("i-pentane", 0.1);
        testSystem.addComponent("n-pentane", 100.0);
        testSystem.addComponent("n-hexane", 100.0);
        testSystem.addComponent("n-heptane", 100.0);
        testSystem.addComponent("n-octane", 100.0);
        
        testSystem.createDatabase(true);
      //  testSystem.setMixingRule(10);
        
        testOps.TPflash();
        testSystem.display();
        
        try{
        testOps.bubblePointPressureFlash(false);
        }catch(Exception e){
            System.out.println("Exception thrown in bubble point flash");
            }
        testSystem.display();
        
        
        
        
        
    }
    
}
