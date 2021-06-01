/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.processSimulation.processEquipment.distillation;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.util.StreamSaturatorUtil;
import neqsim.processSimulation.processSystem.ProcessSystem;
/**
 *
 * @author ESOL
 */
public class DistillationColumnTest {
	static neqsim.thermo.system.SystemInterface feedGas = null;

	double feedGasPressure = 65.0;
	double feedGasTemperature = 35.0;
	double feedGasFlowRate = 5.0;
	double leanTEGFlowRate = 7000.0;
	double leanTEGTemperature = 40.0;
	double absorberFeedGasPressure = 65.0;

	ProcessSystem processOps = null;
	neqsim.processSimulation.processEquipment.compressor.Compressor compressor1 = null;

	@BeforeEach
	public void setUp() throws Exception {
		neqsim.thermo.system.SystemInterface feedGas = new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
		feedGas.addComponent("nitrogen", 1.03);
		feedGas.addComponent("CO2", 1.42);
		feedGas.addComponent("methane", 83.88);
		feedGas.addComponent("ethane", 8.07);
		feedGas.addComponent("propane", 3.54);
		feedGas.addComponent("i-butane", 0.54);
		feedGas.addComponent("n-butane", 0.84);
		feedGas.addComponent("i-pentane", 0.21);
		feedGas.addComponent("n-pentane", 0.19);
		feedGas.addComponent("n-hexane", 0.28);
		feedGas.addComponent("water", 0.0);
		feedGas.addComponent("TEG", 0);
		feedGas.createDatabase(true);
		feedGas.setMixingRule(10);
		feedGas.setMultiPhaseCheck(false);

		Stream dryFeedGas = new Stream("dry feed gas", feedGas);
		dryFeedGas.setFlowRate(feedGasFlowRate, "MSm3/day");
		dryFeedGas.setTemperature(feedGasTemperature, "C");
		dryFeedGas.setPressure(feedGasPressure, "bara");

		StreamSaturatorUtil saturatedFeedGas = new StreamSaturatorUtil(dryFeedGas);
		saturatedFeedGas.setName("water saturator");
		
		Stream waterSaturatedFeedGas = new Stream(saturatedFeedGas.getOutStream());
		waterSaturatedFeedGas.setName("water saturated feed gas");

		neqsim.thermo.system.SystemInterface feedTEG = (neqsim.thermo.system.SystemInterface) feedGas.clone();
		feedTEG.setMolarComposition(new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.03, 0.97 });

		Stream TEGFeed = new Stream("lean TEG to absorber", feedTEG);
		TEGFeed.setFlowRate(leanTEGFlowRate, "kg/hr");
		TEGFeed.setTemperature(leanTEGTemperature, "C");
		TEGFeed.setPressure(absorberFeedGasPressure, "bara");

		DistillationColumn column = new DistillationColumn(3, false, false);
		column.setName("TEG regeneration column");
		
		column.addFeedStream(TEGFeed, 0);
		column.addFeedStream(TEGFeed, 2);
		column.setTopPressure(65.0);
		column.setBottomPressure(65.0);

		processOps = new ProcessSystem();
		processOps.add(dryFeedGas);
		processOps.add(saturatedFeedGas);
		processOps.add(waterSaturatedFeedGas);
		processOps.add(waterSaturatedFeedGas);
		processOps.add(TEGFeed);
		processOps.add(column);
		processOps.run();
	}

	@Test
	public void testProcessOps() {
		processOps.run();
		assertEquals(1, 1);
	}
}
