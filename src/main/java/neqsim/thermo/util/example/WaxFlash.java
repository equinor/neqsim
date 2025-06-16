package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import neqsim.util.database.NeqSimDataBase;

import org.apache.logging.log4j.*;

public class WaxFlash {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(WaxFlash.class);

    public static void main(String args[]) {
    	
        String connectionString = System.getenv("NEQSIM_DB_CONNECTION_STRING");
        if (connectionString == null || connectionString.isEmpty()) {
            logger.error("Environment variable NEQSIM_DB_CONNECTION_STRING is not set or empty.");
            throw new IllegalStateException("Database connection string is not configured.");
        }
        NeqSimDataBase.setConnectionString(connectionString);
    	NeqSimDataBase.setCreateTemporaryTables(true);
    	
    	
        SystemInterface testSystem = new SystemSrkEos(273.0 + 30, 50.0);
        testSystem.addComponent("CO2", 0.018);
        testSystem.addComponent("nitrogen", 0.333);
        testSystem.addComponent("methane", 96.702);
        testSystem.addComponent("ethane", 1.773);
        testSystem.addComponent("propane", 0.496);
        testSystem.addComponent("i-butane", 0.099);
        testSystem.addComponent("n-butane", 0.115);
        testSystem.addComponent("i-pentane", 0.004);
        testSystem.addComponent("n-pentane", 0.024);
        testSystem.addComponent("n-heptane", 0.324);
       // testSystem.addComponent("ethane", 4.5);
       // testSystem.addTBPfraction("C7", 10.0, 93.30 / 1000.0, 0.73);
       // testSystem.addTBPfraction("C8", 10.0, 106.60 / 1000.0, 0.7533);
        testSystem.addPlusFraction("C11", 0.095, 207.0 / 1000.0, 0.8331);
         testSystem.getCharacterization().characterisePlusFraction();
        testSystem.getWaxModel().addTBPWax();
        
        
        
        
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        testSystem.addSolidComplexPhase("wax");
        //  testSystem.setSolidPhaseCheck("nC14");
        testSystem.setMultiphaseWaxCheck(true);
      
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        try {

            testOps.calcWAT();
         //   testOps.TPflash();
            //testSystem.display();
         
        } catch (Exception e) {
            logger.error("error", e);
        }
        double waxVOlumeFrac = 0;
        if (testSystem.hasPhaseType("wax")) {
            waxVOlumeFrac = testSystem.getWtFraction(testSystem.getPhaseIndexOfPhase("wax"));
        }
        //    testSystem.getPhase("oil").getPhysicalProperties().getViscosityOfWaxyOil(waxVOlumeFrac, 1000.0);
        //  System.out.println("viscosity wax-oil suspension " + testSystem.getPhase("oil").getPhysicalProperties().getViscosityOfWaxyOil(waxVOlumeFrac, 1000.0));
    }
}
