package neqsim.thermodynamicOperations.flashOps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

class PhaseEnvelopeTest {
    static neqsim.thermo.system.SystemInterface testSystem = null;
    static neqsim.thermo.ThermodynamicModelTest testModel = null;

    /**
     * <p>
     * setUp.
     * </p>
     */
    @BeforeAll
    public static void setUp() {
      
    }

   
    /**
     * <p>
     * checkPhaseEnvelope.
     * </p>
     * 
     * @throws Exception
     */
    @Test
    @DisplayName("calculate phase envelope using PR-EoS")
    public void checkPhaseEnvelope() throws Exception {
        testSystem = new neqsim.thermo.system.SystemUMRPRUMCEos(298.0, 10.0);
        testSystem.addComponent("methane", 8.64358E-1);
        testSystem.addComponent("ethane", 1.15591E-1);
        testSystem.addComponent("n-pentane", 2.00496E-2);
        testSystem.addComponent("nC10", 1.38248E-6);
      //  testSystem.addTBPfraction("C16'", 1.38248E-6, 226.4500 / 1000.0, 773.0 / 1000);
        testSystem.createDatabase(true);
        testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        try {
            testOps.calcPTphaseEnvelope();
            System.out.println("Cricondenbar " + (testOps.get("cricondenbar")[0] - 273.15) + " "
                    + testOps.get("cricondenbar")[1]);
        } catch (Exception e) {
            assertTrue(false);
            throw new Exception(e);
        }
       // assertEquals(testOps.get("cricondenbar")[1], 97.76008416942318, 0.02);
    }
}
