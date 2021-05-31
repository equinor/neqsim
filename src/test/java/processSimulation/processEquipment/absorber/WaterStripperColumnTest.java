package processSimulation.processEquipment.absorber;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import neqsim.processSimulation.processEquipment.absorber.WaterStripperColumn;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processSystem.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
class WaterStripperColumnTest {

	ProcessSystem processOps = null;

	@BeforeEach
	public void setUp() throws Exception {
		neqsim.thermo.system.SystemInterface strippingGas = new neqsim.thermo.system.SystemSrkCPAstatoil(273.15+80.0, 1.201325);
		strippingGas.addComponent("methane", 1.0);
		strippingGas.addComponent("water",  0);
		strippingGas.addComponent("TEG", 0);
		strippingGas.setMixingRule(10);
		strippingGas.setMultiPhaseCheck(false);
		//strippingGas.setMolarComposition(new double[] { 1.0, 0.0, 0.0});

		SystemInterface feedTEG = (neqsim.thermo.system.SystemInterface) strippingGas.clone();
		feedTEG.setMolarComposition(new double[] { 0.0, 0.09, 0.93});

		StreamInterface strippingGasStream = new Stream(strippingGas);
		strippingGasStream.setFlowRate(0.09, "Sm3/hr");
		StreamInterface leanTEGStream = new Stream(feedTEG);
		leanTEGStream.setFlowRate(7000.0, "kg/hr");
		leanTEGStream.setTemperature(202.0, "C");
		WaterStripperColumn stripCol = new WaterStripperColumn("water stripper");
		stripCol.setNumberOfStages(2);
		stripCol.setStageEfficiency(0.55);
		stripCol.addGasInStream(strippingGasStream);
		stripCol.addSolventInStream(leanTEGStream);

		processOps = new ProcessSystem();
		processOps.add(strippingGasStream);
		processOps.add(leanTEGStream);
		processOps.add(stripCol);
		

	}

	@Test
	@DisplayName("Test water stripping columns")
	@Disabled
	public void testWaterStripperMethod() {
		processOps.run();
		double wtFracWaterIn = ((WaterStripperColumn)processOps.getUnit("water stripper")).getSolventInStream().getFluid().getPhase(0).getWtFrac("water");
		System.out.println("water wt% in " + wtFracWaterIn*100);
		double wtFracWaterOut = ((WaterStripperColumn)processOps.getUnit("water stripper")).getSolventOutStream().getFluid().getPhase(0).getWtFrac("water");
		System.out.println("water wt% frac out " + wtFracWaterOut*100);
		assertEquals(wtFracWaterOut, 0.0009766235744093311
		,"testing water concentration out of stripper....");
	}

}
