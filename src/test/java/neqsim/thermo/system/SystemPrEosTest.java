package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

class SystemPrEoSTest {
    static neqsim.thermo.system.SystemInterface testSystem = null;
    static neqsim.thermo.ThermodynamicModelTest testModel = null;

    /**
     * <p>setUp.</p>
     */
    @BeforeAll
    public static void setUp() {
        testSystem = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
        testSystem.addComponent("nitrogen", 0.01);
        testSystem.addComponent("CO2", 0.01);
        testSystem.addComponent("methane", 0.68);
        testSystem.addComponent("ethane", 0.1);
        testSystem.addComponent("n-heptane", 0.2);
        testSystem.setMixingRule("classic");
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
        assertEquals(testSystem.getPhase("gas").getZ(), 0.9708455641951108, 1e-5);
    }
    
    /**
     * <p>calcProperties.</p>
     */
    @Test
    @DisplayName("calculate properties when flow rate is 0")
    public void calcProperties() {
    	testSystem.setTotalFlowRate(1.0, "mol/sec");
    	 ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    	 testOps.TPflash();
         testSystem.initProperties();
         System.out.print("enthalpy " + testSystem.getEnthalpy("kJ/kg"));
    	assertEquals(testSystem.getEnthalpy("kJ/kg"), -165.60627184389855, Math.abs(-165.60627184389855/1000.0));
    }
}
