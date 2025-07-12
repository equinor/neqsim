package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Simple tests for the AirCooler unit operation.
 */
public class AirCoolerTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  ProcessSystem processOps = null;
  AirCooler airCooler = null;

  @BeforeEach
  public void setUp() {
    testSystem = new SystemSrkEos(353.15, 10.0);
    testSystem.addComponent("methane", 100.0);
    Stream inlet = new Stream("inlet", testSystem);
    inlet.setFlowRate(1.0, "MSm3/day");
    inlet.setPressure(10.0, "bara");
    inlet.setTemperature(80.0, "C");

    airCooler = new AirCooler("air cooler", inlet);
    airCooler.setOutTemperature(40.0, "C");
    airCooler.setAirInletTemperature(20.0, "C");
    airCooler.setAirOutletTemperature(30.0, "C");
    airCooler.setRelativeHumidity(0.5);

    processOps = new ProcessSystem();
    processOps.add(inlet);
    processOps.add(airCooler);
    processOps.run();
  }

  @Test
  public void testAirFlowIsPositive() {
    assertTrue(airCooler.getAirMassFlow() > 0.0);
    assertTrue(airCooler.getAirVolumeFlow() > 0.0);
  }
}
