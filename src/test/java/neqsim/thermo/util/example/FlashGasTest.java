package neqsim.thermo.util.example;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

class FlashGasTest {

	static SystemInterface fluid1 = null;
	static ThermodynamicOperations thermoOps;
	
	static String[] components = new String[]{"water", "nitrogen", "CO2", "H2S", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane", "n-pentane", "n-hexane"};
	static double[] fractions1 = new double[] {2.18, 0.48, 1.77, 0, 62.88, 12.36, 9.58, 2.21, 2.47, 0.98, 0.46, 4.63};
	static double[] fractions2 = new double[] {0.054, 0.454, 1.514, 0, 89.92, 5.324, 1.535, 0.232, 0.329, 0.094, 0.107, 0.437};
	


	static double[] P_bar = new double[] { 1, 10, 100, 200, 1, 10, 100, 200, 1, 10, 100, 200};
	static double[] T_C = new double[] {15, 15, 15, 15, 30, 30, 30, 30, 150, 150, 150, 150 };

	static double[] enthalpy = new double[P_bar.length];
	static double[] entropy = new double[P_bar.length];

	static double[] errH = new double[P_bar.length];
	static double[] errS = new double[P_bar.length];

	@BeforeAll
	public static void setUp() {
		fluid1 = new SystemSrkEos(298.0, 10.0);
		for (int i = 0; i < components.length; i++) {
		fluid1.addComponent(components[i], fractions1[i]);
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
			System.out.println("err Enthalpy " + errH[i]);
			assertTrue(Math.abs(errH[i]) < 1e-2);
		}
	}
	
	@Test
	public void testPSflash() {
		for (int i = 0; i < P_bar.length; i++) {
			fluid1.setPressure(P_bar[i]);
			thermoOps.PSflash(entropy[i]);
			errS[i] = fluid1.getTemperature() - T_C[i] - 273.15;
			//System.out.println("err " + errS[i]);
			assertTrue(Math.abs(errS[i]) < 1e-2);
		}
	}

}
