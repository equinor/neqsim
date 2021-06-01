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
import neqsim.processSimulation.processSystem.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import neqsim.processSimulation.processEquipment.compressor.Compressor;
/**
 *
 * @author ESOL
 */
public class DistillationColumnTest {
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
}
