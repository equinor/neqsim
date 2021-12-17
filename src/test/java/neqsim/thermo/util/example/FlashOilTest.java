package neqsim.thermo.util.example;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

class FlashOilTest {
    static SystemInterface fluid1 = null;
    static ThermodynamicOperations thermoOps;

    static String[] components = new String[] {"water", "nitrogen", "CO2", "methane", "ethane",
            "propane", "i-butane", "n-butane", "i-pentane", "n-pentane", "CHCmp_1", "CHCmp_2",
            "CHCmp_3", "CHCmp_4", "CHCmp_5", "CHCmp_6", "CHCmp_7", "CHCmp_8", "CHCmp_9", "CHCmp_10",
            "CHCmp_11", "CHCmp_12", "CHCmp_13"};
    static double[] fractions1 = new double[] {1.63588003488258E-06, 9.69199026590317E-14,
            4.56441011920106E-07, 2.34691992773151E-08, 0.000086150337010622, 0.0186985325813293,
            0.0239006042480469, 0.0813478374481201, 0.0550192785263062, 0.0722700452804565,
            0.11058749198913600, 0.17813117980957000, 0.14764604568481400, 0.10463774681091300,
            0.08433451652526860, 0.03788370132446290, 0.02444351673126220, 0.01481210947036740,
            0.01158336877822880, 0.01286722421646120, 0.00838199377059937, 0.00746552944183350,
            0.00590102910995483};
    static double[] molarmass = new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0861780014038086,
            0.0909560012817383, 0.1034290008544920, 0.1171869964599610, 0.1458090057373050,
            0.1813300018310550, 0.2122779998779300, 0.2481419982910160, 0.2892170104980470,
            0.3303389892578130, 0.3846969909667970, 0.4711579895019530, 0.6624600219726560};
    static double[] density = new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.662663996219635,
            0.740698456764221, 0.769004046916962, 0.789065659046173, 0.80481481552124,
            0.825066685676575, 0.837704122066498, 0.849904119968414, 0.863837122917175,
            0.875513017177582, 0.888606309890747, 0.906100511550903, 0.936200380325317};

    static double[] P_bar = new double[] {1, 1, 1, 1, 10, 10, 10, 10, 100, 100, 100, 100};
    static double[] T_C = new double[] {0, 15, 30, 100, 0, 15, 30, 100, 0, 15, 30, 100};

    static double[] enthalpy = new double[P_bar.length];
    static double[] entropy = new double[P_bar.length];

    static double[] errH = new double[P_bar.length];
    static double[] errS = new double[P_bar.length];

    @BeforeAll
    public static void setUp() {
        fluid1 = new SystemSrkEos(298.0, 10.0);
        for (int i = 0; i < components.length; i++) {
            if (components[i].startsWith("CH")) {
                fluid1.addTBPfraction(components[i], fractions1[i], molarmass[i], density[i]);
            } else {
                fluid1.addComponent(components[i], fractions1[i]);
            }
        }

        fluid1.createDatabase(true);
        fluid1.setMixingRule(2);
        fluid1.setMultiPhaseCheck(true);
        thermoOps = new ThermodynamicOperations(fluid1);

        for (int i = 0; i < P_bar.length; i++) {
            fluid1.setTemperature(T_C[i] + 273.15);
            fluid1.setPressure(P_bar[i]);
            thermoOps.TPflash();
            fluid1.init(2);
            fluid1.initPhysicalProperties();
            enthalpy[i] = fluid1.getEnthalpy();
            entropy[i] = fluid1.getEntropy();
        }
    }

    @Test
    public void testPHflash() {
        for (int i = 0; i < P_bar.length; i++) {
            fluid1.setPressure(P_bar[i]);
            thermoOps.PHflash(enthalpy[i]);
            errH[i] = fluid1.getTemperature() - T_C[i] - 273.15;
            // System.out.println("err " + errH[i]);
            assertTrue(Math.abs(errH[i]) < 1e-2);
        }
    }

    @Test
    public void testPSflash() {
        for (int i = 0; i < P_bar.length; i++) {
            fluid1.setPressure(P_bar[i]);
            thermoOps.PSflash(entropy[i]);
            errS[i] = fluid1.getTemperature() - T_C[i] - 273.15;
            // System.out.println("err " + errS[i]);
            assertTrue(Math.abs(errS[i]) < 1e-2);
        }
    }
}
