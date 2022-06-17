/**
 * 
 */
package neqsim.thermodynamicOperations.flashOps;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import neqsim.util.database.NeqSimDataBase;

/**
 * @author ESOL
 *
 */
class WaxFlashTest {

  static neqsim.thermo.system.SystemInterface testSystem = null;
  static ThermodynamicOperations testOps = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  void setUp() throws Exception {
    NeqSimDataBase.setConnectionString("jdbc:derby:C:/Users/esol/OneDrive - Equinor/programming/neqsim/src/main/resources/data/neqsimtestdatabase");
    NeqSimDataBase.setCreateTemporaryTables(true);

    testSystem = new SystemSrkEos(273.0 + 30, 50.0);
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
    testSystem.addPlusFraction("C9", 0.095, 207.0 / 1000.0, 0.8331);
    testSystem.getCharacterization().characterisePlusFraction();
    testSystem.getWaxModel().addTBPWax();
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.addSolidComplexPhase("wax");
    // testSystem.setSolidPhaseCheck("nC14");
    testSystem.setMultiphaseWaxCheck(true);
    NeqSimDataBase.setConnectionString("jdbc:derby:classpath:data/neqsimthermodatabase");
    NeqSimDataBase.setCreateTemporaryTables(false);
    //testSystem.display();
  }

  /**
   * Test method for {@link neqsim.thermodynamicOperations.flashOps.PHFlash#run()}.
   */
  @Test
  @Disabled
  void testRun() {
    testOps = new ThermodynamicOperations(testSystem);
    try {
        testOps.calcWAT();
        testOps.TPflash();
    } catch (Exception e) {
        e.printStackTrace();
    }
    double waxVolumeFrac = 0;
    if (testSystem.hasPhaseType("wax")) {
      waxVolumeFrac = testSystem.getWtFraction(testSystem.getPhaseIndexOfPhase("wax"));
    }
  }

}
