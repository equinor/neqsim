package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.log4j.Logger;

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
public class ReadFluidData {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(ReadFluidData.class);
    
    /** Creates new TPflash */
    public ReadFluidData() {
    }
    public static void main(String args[]){
        
          SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 25.0, 88.8);//
        testSystem.addComponent("nitrogen", 12.681146444);
        testSystem.addComponent("CO2", 2.185242497);
        testSystem.addComponent("water", 78.0590685);
        testSystem.createDatabase(true);
        testSystem.init(0);
         testSystem.init(1);
                
         testSystem.saveFluid(55);
      //  testSystem.readFluid("AsgardB");
        testSystem = testSystem.readObject(55);
        testSystem.init(0);
        testSystem.setPressure(23);
        testSystem.setTemperature(273.15 + 21.5625);
        //testSystem.setMultiPhaseCheck(true);
       //testSystem.getCharacterization().characterisePlusFraction();
        
     //   testSystem.createDatabase(true);
     //   testSystem.setMixingRule(2);
        
        
        
        
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
       
        try{
            testOps.TPflash();
            testSystem.display();
        }
        catch(Exception e){
            logger.error(e.toString());
        }
        
        testSystem.saveObjectToFile("c:/app/fluid.neqsim", "");
        
       SystemInterface testSystem2 = testSystem.readObjectFromFile("c:/app/fluid.neqsim", "");
        
          testOps = new ThermodynamicOperations(testSystem2);
       
        try{
            testOps.TPflash();
            testSystem2.display();
        }
        catch(Exception e){
            logger.error(e.toString());
        }
    }
}
