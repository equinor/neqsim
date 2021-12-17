package neqsim.thermo.util.example;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

class FlashWaterTest {
    static SystemInterface waterSystem = null;
    static ThermodynamicOperations thermoOps;

    static double[] P_bar = new double[] {1, 1, 1, 1, 10, 10, 10, 10, 100, 100, 100, 100};
    static double[] T_C = new double[] {0, 15, 30, 100, 0, 15, 30, 100, 0, 15, 30, 100};

    static double[] enthalpy = new double[P_bar.length];
    static double[] entropy = new double[P_bar.length];

    static double[] errH = new double[P_bar.length];
    static double[] errS = new double[P_bar.length];

    @BeforeAll
    public static void setUp() {
        waterSystem = new SystemSrkEos(298.0, 10.0);
        waterSystem.addComponent("water", 10.0);
        waterSystem.createDatabase(true);
        waterSystem.setMixingRule(2);
        waterSystem.setMultiPhaseCheck(true);
        thermoOps = new ThermodynamicOperations(waterSystem);

        for (int i = 0; i < P_bar.length; i++) {
            waterSystem.setTemperature(T_C[i] + 273.15);
            waterSystem.setPressure(P_bar[i]);
            thermoOps.TPflash();
            waterSystem.init(2);
            waterSystem.initPhysicalProperties();
            enthalpy[i] = waterSystem.getEnthalpy();
            entropy[i] = waterSystem.getEntropy();
        }
    }

    @Test
    public void testPHflash() {
        for (int i = 0; i < P_bar.length; i++) {
            waterSystem.setPressure(P_bar[i]);
            thermoOps.PHflash(enthalpy[i]);
            errH[i] = waterSystem.getTemperature() - T_C[i] - 273.15;
            // System.out.println("err " + errH[i]);
            assertTrue(Math.abs(errH[i]) < 1e-2);
        }
    }

    @Test
    public void testPSflash() {
        for (int i = 0; i < P_bar.length; i++) {
            waterSystem.setPressure(P_bar[i]);
            thermoOps.PSflash(entropy[i]);
            errS[i] = waterSystem.getTemperature() - T_C[i] - 273.15;
            // System.out.println("err " + errS[i]);
            assertTrue(Math.abs(errS[i]) < 1e-2);
        }
    }
}
