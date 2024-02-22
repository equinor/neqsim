package neqsim.thermodynamicOperations.phaseEnvelopeOps.multicomponentEnvelopeOps;

import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PTPhaseEnvelopeTest {
    static neqsim.thermo.system.SystemInterface testSystem = null;
    static ThermodynamicOperations testOps = null;

    @BeforeEach
    void setUp() {
        testSystem = new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
    }

    /**
     * Test method for {@link neqsim.thermodynamicOperations.phaseEnvelopeOps.multicomponentEnvelopeOps.pTphaseEnvelope}.
     */
    @Test
    void testDewP() {
        testSystem.addComponent("nitrogen", 0.01);
        testSystem.addComponent("CO2", 0.01);
        testSystem.addComponent("methane", 0.98);
        testSystem.setMixingRule("classic");

        testOps = new ThermodynamicOperations(testSystem);
        testOps.TPflash();
        testSystem.initProperties();
        testOps.calcPTphaseEnvelope();
        double[] dewPointPressures = testOps.get("dewP");
        double[] expectedDewPointPressures =
                new double[]{
                        1.1051709180756477, 1.2214027581601699, 1.3498588075760032, 1.4918246976412703,
                        1.6652911949458864, 1.8794891289619104, 2.1418131227502055, 2.4690864123141987,
                        2.881197018974799, 3.404779997613969, 4.075230307874481, 4.938583914869986, 6.051801019586486,
                        7.477304695462727, 9.260793952051571, 11.364101185282063, 13.480106047577934, 14.53423776629387,
                        13.607498029406681, 11.181207439509638, 9.189487040488075, 9.612827246459474,
                        10.706126846063928, 12.501491987760147, 15.075672692089958, 18.51283799420178,
                        23.330378296334104, 29.71319711031059, 37.25532259549197, 43.660805656603934, 45.75836660678656,
                        46.42490219574348, 46.83203503669948, 46.869568345957006, 46.903557772489435
                };
        System.out.println(Arrays.toString(dewPointPressures));
        assertArrayEquals(expectedDewPointPressures, dewPointPressures, 10E-10);

    }

    @Test
    void testFailingCaseWithWater() {
        testSystem.addComponent("nitrogen", 0.04);
        testSystem.addComponent("CO2", 0.06);
        testSystem.addComponent("methane", 0.80);
        testSystem.addComponent("water", 0.00000000001);

        testSystem.setMixingRule("classic");

        testOps = new ThermodynamicOperations(testSystem);
        testOps.TPflash();
        testSystem.initProperties();


        Exception exception = assertThrows(ArrayIndexOutOfBoundsException.class, () -> testOps.calcPTphaseEnvelope());

    }


}
