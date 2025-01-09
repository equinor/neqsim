package neqsim.process.equipment.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * @author ESOL
 */
class StreamTest extends neqsim.NeqSimTest {
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

    processOps.add(inletStream);
    processOps.run();
  }

  @Test
  public void testLCV() {
    processOps.run();
    ((Stream) processOps.getUnit("inlet stream")).LCV();
    assertEquals(3.58980282482032E7, ((Stream) processOps.getUnit("inlet stream")).LCV(), 1.0);
    // 18978 J/Sm3
  }

  @Test
  public void testNoFlow() {
    testSystem.setTotalFlowRate(0, "MSm3/day");
    RuntimeException thrown = Assertions.assertThrows(RuntimeException.class, () -> {
      testSystem.initProperties();
    });
    Assertions.assertEquals(
        "neqsim.util.exception.InvalidInputException: PhaseSrkEos:init - Input totalNumberOfMoles must be larger than or equal to zero.",
        thrown.getMessage());
  }

  @Test
  public void testNeedRecalculation() {
    ((Stream) processOps.getUnit("inlet stream")).setTemperature(298.1, "K");
    assertTrue(((Stream) processOps.getUnit("inlet stream")).needRecalculation());
    processOps.run();
    assertFalse(((Stream) processOps.getUnit("inlet stream")).needRecalculation());

    ((Stream) processOps.getUnit("inlet stream")).setPressure(98.1, "bara");
    assertTrue(((Stream) processOps.getUnit("inlet stream")).needRecalculation());
    processOps.run();
    assertFalse(((Stream) processOps.getUnit("inlet stream")).needRecalculation());

    ((Stream) processOps.getUnit("inlet stream")).setFlowRate(12.1, "kg/hr");
    assertTrue(((Stream) processOps.getUnit("inlet stream")).needRecalculation());
    processOps.run();
    assertFalse(((Stream) processOps.getUnit("inlet stream")).needRecalculation());
  }
}
