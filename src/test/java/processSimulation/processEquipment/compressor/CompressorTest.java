package processSimulation.processEquipment.compressor;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processSystem.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

class CompressorTest {

	double pressure_inlet = 85.0;
	double temperature_inlet = 35.0;
	double gasFlowRate = 5.0;
	double pressure_Out = 150.0;
	double polytropicEff = 0.77;
	ProcessSystem processOps = null;
	neqsim.processSimulation.processEquipment.compressor.Compressor compressor1 = null;

	@BeforeEach
	public void setUp() throws Exception {
		neqsim.thermo.system.SystemInterface testSystem = new SystemSrkEos(298.0, 10.0);
		testSystem.addComponent("methane", 100.0);
		processOps = new ProcessSystem();
		Stream inletStream = new Stream(testSystem);
		inletStream.setPressure(pressure_inlet, "bara");
		inletStream.setTemperature(temperature_inlet, "C");
		inletStream.setFlowRate(gasFlowRate, "MSm3/day");
		compressor1 = new neqsim.processSimulation.processEquipment.compressor.Compressor("Compressor1", inletStream);
		compressor1.setOutletPressure(pressure_Out);
		processOps.add(inletStream);
		processOps.add(compressor1);

	}

	@Test
	@DisplayName("Test compressor calculation using Schultz polytropic calculation method")
	public void testCompressorSchultzMethod() {
		compressor1.setPolytropicEfficiency(polytropicEff);
		compressor1.setUsePolytropicCalc(true);
		compressor1.setPolytropicMethod("schultz");
		processOps.run();
		// System.out.println("schultz compressor power " + compressor1.getPower() / 1e6
		// + " MW");
		assertEquals(4.668373797540108, compressor1.getPower() / 1e6,
				"Test case for compressor Schultz method polytropic calculation should return approximate 4.67 MW");
	}

	@Test
	@DisplayName("Test compressor calculation using rigorous polytropic calculation method")
	public void testCompressorRigorousMethod() {
		compressor1.setPolytropicEfficiency(polytropicEff);
		compressor1.setUsePolytropicCalc(true);
		compressor1.setPolytropicMethod("detailed");
		processOps.run();
		// System.out.println("rigorous compressor power " + compressor1.getPower() /
		// 1e6 + " MW");
		assertEquals(4.655081035416562, compressor1.getPower() / 1e6,
				"Test case for rigorous polytropic compressor calculation should return approximate 4.66 MW");
	}

	@Test
	@DisplayName("Test compressor calculation using a adiabatic efficiency method")
	public void testIsentropicCalcMethod() {
		compressor1.setIsentropicEfficiency(polytropicEff);
		compressor1.setUsePolytropicCalc(false);
		processOps.run();
		// System.out.println("compressor power " + compressor1.getPower() / 1e6 + "
		// MW");
		assertEquals(4.5621157449685, compressor1.getPower() / 1e6);
	}

	@Test
	@DisplayName("Test compressor calculation using compressor curves method")
	public void testCompressorCurvesCalcMethod() {
		double[] chartConditions = new double[] { 0.3, 1.0, 1.0, 1.0 };
		double[] speed = new double[] { 12913, 12298, 11683, 11098, 10453, 9224, 8609, 8200 };
		double[][] flow = new double[][] {
				{ 2789.1285, 3174.0375, 3689.2288, 4179.4503, 4570.2768, 4954.7728, 5246.0329, 5661.0331 },
				{ 2571.1753, 2943.7254, 3440.2675, 3837.4448, 4253.0898, 4668.6643, 4997.1926, 5387.4952 },
				{ 2415.3793, 2763.0706, 3141.7095, 3594.7436, 4047.6467, 4494.1889, 4853.7353, 5138.7858 },
				{ 2247.2043, 2799.7342, 3178.3428, 3656.1551, 4102.778, 4394.1591, 4648.3224, 4840.4998 },
				{ 2072.8397, 2463.9483, 2836.4078, 3202.5266, 3599.6333, 3978.0203, 4257.0022, 4517.345 },
				{ 1835.9552, 2208.455, 2618.1322, 2940.8034, 3244.7852, 3530.1279, 3753.3738, 3895.9746 },
				{ 1711.3386, 1965.8848, 2356.9431, 2685.9247, 3008.5154, 3337.2855, 3591.5092 },
				{ 1636.5807, 2002.8708, 2338.0319, 2642.1245, 2896.4894, 3113.6264, 3274.8764, 3411.2977 } };
		double[][] head = new double[][] { { 80.0375, 78.8934, 76.2142, 71.8678, 67.0062, 60.6061, 53.0499, 39.728 },
				{ 72.2122, 71.8369, 68.9009, 65.8341, 60.7167, 54.702, 47.2749, 35.7471 },
				{ 65.1576, 64.5253, 62.6118, 59.1619, 54.0455, 47.0059, 39.195, 31.6387 },
				{ 58.6154, 56.9627, 54.6647, 50.4462, 44.4322, 38.4144, 32.9084, 28.8109 },
				{ 52.3295, 51.0573, 49.5283, 46.3326, 42.3685, 37.2502, 31.4884, 25.598 },
				{ 40.6578, 39.6416, 37.6008, 34.6603, 30.9503, 27.1116, 23.2713, 20.4546 },
				{ 35.2705, 34.6359, 32.7228, 31.0645, 27.0985, 22.7482, 18.0113 },
				{ 32.192, 31.1756, 29.1329, 26.833, 23.8909, 21.3324, 18.7726, 16.3403 }, };
		double[][] polyEff = new double[][] {
				{ 77.2452238409573, 79.4154186459363, 80.737960012489, 80.5229826589649, 79.2210931638144,
						75.4719133864634, 69.6034181197298, 58.7322388482707 },
				{ 77.0107837113504, 79.3069974136389, 80.8941189021135, 80.7190194665918, 79.5313242980328,
						75.5912622896367, 69.6846136362097, 60.0043057990909 },
				{ 77.0043065299874, 79.1690958847856, 80.8038169975675, 80.6543975614197, 78.8532389102705,
						73.6664774270613, 66.2735600426727, 57.671664571658 },
				{ 77.0716623789093, 80.4629750233093, 81.1390811169072, 79.6374242667478, 75.380928428817,
						69.5332969549779, 63.7997587622339, 58.8120614497758 },
				{ 76.9705872525642, 79.8335492585324, 80.9468133671171, 80.5806471927835, 78.0462158225426,
						73.0403707523258, 66.5572286338589, 59.8624822515064 },
				{ 77.5063036680357, 80.2056198362559, 81.0339108025933, 79.6085962687939, 76.3814534404405,
						70.8027503005902, 64.6437367160571, 60.5299349982342 },
				{ 77.8175271586685, 80.065165942218, 81.0631362122632, 79.8955051771299, 76.1983240929369,
						69.289982774309, 60.8567149372229 },
				{ 78.0924334304045, 80.9353551568667, 80.7904437766234, 78.8639325223295, 75.2170936751143,
						70.3105081673411, 65.5507568533569, 61.0391468300337 } };
		compressor1.getCompressorChart().setCurves(chartConditions, speed, flow, head, polyEff);
		compressor1.getCompressorChart().setHeadUnit("kJ/kg");
		compressor1.setUsePolytropicCalc(true);
		compressor1.setSpeed(10000);
		processOps.run();
		//System.out.println("compressor flow " + compressor1.getThermoSystem().getFlowRate("m3/hr") + " m3/hr");
		//System.out.println("compressor power " + compressor1.getPower() / 1e6 + " MW");
		//System.out.println("compressor polytropic head " + compressor1.getPolytropicFluidHead() + " kJ/kg");
		//System.out.println("compressor out pressure " + compressor1.getThermoSystem().getPressure("bara") + " bara");
		assertEquals(2.304965752880393, compressor1.getPower() / 1e6);
	}

}
