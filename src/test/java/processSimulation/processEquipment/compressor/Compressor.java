package processSimulation.processEquipment.compressor;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processSystem.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

class Compressor {

	static neqsim.thermo.system.SystemInterface testSystem = null;

	double pressure_inlet = 85.0;
	double temperature_inlet = 35.0;
	double gasFlowRate = 5.0;
	double pressure_Out = 150.0;
	double polytropicEff = 0.77;
	ProcessSystem processOps = null;
	neqsim.processSimulation.processEquipment.compressor.Compressor compressor1 = null;

	@BeforeEach
	public void setUp() throws Exception {
		testSystem = new SystemSrkEos(298.0, 10.0);
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
	public void testCompressorSchultzMethod() {
		compressor1.setPolytropicEfficiency(polytropicEff);
		compressor1.setUsePolytropicCalc(true);
		compressor1.setPolytropicMethod("schultz");
		processOps.run();
		System.out.println("schultz compressor power " + compressor1.getPower() / 1e6 + " MW");
		assertEquals(compressor1.getPower() / 1e6, 4.668373797540108, "Test case for compressor Schultz method polytropic calculation should return approximate 4.67 MW");
	}
	
	@Test
	public void testCompressorRigorousMethod() {
		compressor1.setPolytropicEfficiency(polytropicEff);
		compressor1.setUsePolytropicCalc(true);
		compressor1.setPolytropicMethod("detailed");
		processOps.run();
		System.out.println("rigorous compressor power " + compressor1.getPower() / 1e6 + " MW");
		assertEquals(compressor1.getPower() / 1e6, 4.655081035416562,"Test case for rigorous polytropic compressor calculation should return approximate 4.66 MW");
	}

	@Test
	public void testIsentropicCalcMethod() {
		compressor1.setIsentropicEfficiency(polytropicEff);
		compressor1.setUsePolytropicCalc(false);
		processOps.run();
		System.out.println("compressor power " + compressor1.getPower() / 1e6 + " MW");
		assertEquals(compressor1.getPower() / 1e6, 4.5621157449685);
	}

}
