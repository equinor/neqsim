package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

class SystemUMRPRUMCEosNewTest {
    static neqsim.thermo.system.SystemInterface testSystem = null;
    static neqsim.thermo.ThermodynamicModelTest testModel = null;

    /**
     * <p>setUp.</p>
     */
    @BeforeAll
    public static void setUp() {
        testSystem = new SystemUMRPRUMCEosNew(298.0, 10.0);
        testSystem.addComponent("nitrogen", 0.01);
        testSystem.addComponent("CO2", 0.01);
        testSystem.addComponent("methane", 0.68);
        testSystem.addComponent("ethane", 0.1);
        testSystem.addComponent("n-heptane", 0.2);
        testSystem.createDatabase(true);
        testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
        testModel = new neqsim.thermo.ThermodynamicModelTest(testSystem);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testOps.TPflash();
        testSystem.initProperties();
    }

    /**
     * <p>testTPflash2.</p>
     */
    @Test
    @DisplayName("test a TPflash2")
    public void testTPflash2() {
        assertEquals(testSystem.getNumberOfPhases(), 2);
    }

    /**
     * <p>testTPflash.</p>
     */
    @Test
    @DisplayName("test a TPflash of the fluid (should return two phases)")
    public void testTPflash() {
        assertEquals(testSystem.getNumberOfPhases(), 2);
    }

    /**
     * <p>testFugacityCoefficients.</p>
     */
    @Test
    @DisplayName("test the fugacity coeficients calcutated")
    public void testFugacityCoefficients() {
        assertTrue(testModel.checkFugasityCoeffisients());
    }

    /**
     * <p>checkFugasityCoeffisientsDP.</p>
     */
    @Test
    @DisplayName("test derivative of fugacity coeficients with respect to pressure")
    public void checkFugasityCoeffisientsDP() {
        assertTrue(testModel.checkFugasityCoeffisientsDP());
    }

    /**
     * <p>checkFugasityCoeffisientsDT.</p>
     */
    @Test
    @DisplayName("test derivative of fugacity coeficients with respect to temperature")
    public void checkFugasityCoeffisientsDT() {
        assertTrue(testModel.checkFugasityCoeffisientsDT());
    }

    /**
     * <p>checkFugasityCoeffisientsDn.</p>
     */
    @Test
    @DisplayName("test derivative of fugacity coeficients with respect to composition")
    public void checkFugasityCoeffisientsDn() {
        assertTrue(testModel.checkFugasityCoeffisientsDn());
    }

    /**
     * <p>checkFugasityCoeffisientsDn2.</p>
     */
    @Test
    @DisplayName("test derivative of fugacity coeficients with respect to composition (2nd method)")
    public void checkFugasityCoeffisientsDn2() {
        assertTrue(testModel.checkFugasityCoeffisientsDn2());
    }

    /**
     * <p>checkCompressibility.</p>
     */
    @Test
    @DisplayName("calculate compressibility of gas phase")
    public void checkCompressibility() {
        System.out.println("gas compressibility " + testSystem.getPhase("gas").getZ());
        assertTrue(testSystem.getPhase("gas").getZ() == 0.9711401538454589);
    }
}
