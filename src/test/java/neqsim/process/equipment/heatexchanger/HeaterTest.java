package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class HeaterTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  double pressure_inlet = 85.0;
  double temperature_inlet = 35.0;
  double gasFlowRate = 5.0;
  ProcessSystem processOps = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  public void setUpBeforeClass() {
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    processOps = new ProcessSystem();
    Stream inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");

    Heater heater1 = new Heater("heater 1", inletStream);
    heater1.setOutTemperature(310.0);
    processOps.add(inletStream);
    processOps.add(heater1);
    processOps.run();
  }

  @Test
  void testNeedRecalculation() {
    ((Heater) processOps.getUnit("heater 1")).setOutTemperature(348.1, "K");
    assertTrue(((Heater) processOps.getUnit("heater 1")).needRecalculation());
    processOps.run();
    assertFalse(((Heater) processOps.getUnit("heater 1")).needRecalculation());

    ((Heater) processOps.getUnit("heater 1")).setOutPressure(10.0, "bara");
    assertTrue(((Heater) processOps.getUnit("heater 1")).needRecalculation());
    processOps.run();
    assertFalse(((Heater) processOps.getUnit("heater 1")).needRecalculation());
  }
}
