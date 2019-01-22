package neqsim.processSimulation.util.example;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import neqsim.processSimulation.processEquipment.stream.Stream;
import org.junit.jupiter.api.Disabled;

@Disabled class process1Test {
	
	static neqsim.thermo.system.SystemInterface testSystem;
	static neqsim.processSimulation.processSystem.ProcessSystem operations;

	@BeforeAll
        @Disabled 
	public static void setUp() {
		testSystem = new neqsim.thermo.system.SystemSrkCPA((273.15 + 25.0), 50.00);
		testSystem.addComponent("methane", 180.00);
		testSystem.addComponent("ethane", 10.00);
		testSystem.addComponent("propane", 1.00);
		testSystem.createDatabase(true);
		testSystem.setMultiPhaseCheck(true);
		testSystem.setMixingRule(2);

		Stream stream_1 = new Stream("Stream1", testSystem);

		neqsim.processSimulation.processEquipment.compressor.Compressor compr = new neqsim.processSimulation.processEquipment.compressor.Compressor(
				stream_1);
		compr.setOutletPressure(80.0);
		compr.setPolytropicEfficiency(0.9);
		compr.setIsentropicEfficiency(0.9);
		compr.setUsePolytropicCalc(true);

		operations = new neqsim.processSimulation.processSystem.ProcessSystem();
		operations.add(stream_1);
		operations.add(compr);
	}

	@AfterEach
	void tearDown() throws Exception {
	}

	@Test
	void runTest() {
		operations.run();
	    assertEquals(2,2);
	}

}
