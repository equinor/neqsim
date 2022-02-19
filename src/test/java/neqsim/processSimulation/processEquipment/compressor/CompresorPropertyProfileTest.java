package neqsim.processSimulation.processEquipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processSystem.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

class CompresorPropertyProfileTest {
	static neqsim.thermo.system.SystemInterface testSystem = null;

	double pressure_inlet = 85.0;
	double temperature_inlet = 35.0;
	double gasFlowRate = 5.0;
	double pressure_Out = 150.0;
	ProcessSystem processOps = null;
	neqsim.processSimulation.processEquipment.compressor.Compressor compressor1 = null;

	/**
	 * <p>
	 * setUp.
	 * </p>
	 *
	 * @throws java.lang.Exception if any.
	 */
	@BeforeEach
	public void setUp() throws Exception {
		testSystem = new SystemSrkEos(298.0, 10.0);
		testSystem.addComponent("methane", 100.0);
		processOps = new ProcessSystem();
        Stream inletStream = new Stream("inletStream", testSystem);
		inletStream.setPressure(pressure_inlet, "bara");
		inletStream.setTemperature(temperature_inlet, "C");
		inletStream.setFlowRate(gasFlowRate, "MSm3/day");
		compressor1 = new neqsim.processSimulation.processEquipment.compressor.Compressor("Compressor1", inletStream);
		compressor1.setOutletPressure(pressure_Out);
		compressor1.setUsePolytropicCalc(true);
		compressor1.setPolytropicEfficiency(0.89);
		processOps.add(inletStream);
		processOps.add(compressor1);
	}

	@Test
	public void testRunCalculation() {
		compressor1.setNumberOfCompressorCalcSteps(40);
		compressor1.getPropertyProfile().setActive(true);
		processOps.run();
		double density3 = compressor1.getPropertyProfile().getFluid().get(3).getDensity("kg/m3");
		double density39 = compressor1.getPropertyProfile().getFluid().get(39).getDensity("kg/m3");
		assertEquals(85.4664664074326, density39, 59.465718447138336 / 100.0);
	}

	@Test
	public void testFailRunCalculation() {
		try {
			compressor1.setNumberOfCompressorCalcSteps(40);
			compressor1.getPropertyProfile().setActive(false);
			processOps.run();
			compressor1.getPropertyProfile().getFluid().get(3);
			assert (false);
		} catch (Exception e) {
			assert (true);
		}
	}

}
