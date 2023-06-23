package neqsim.processSimulation.processEquipment.powerGeneration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

public class GasTurbineTest extends neqsim.NeqSimTest {
  static Logger logger = LogManager.getLogger(GasTurbineTest.class);

  static neqsim.thermo.system.SystemInterface testSystem;
  static Stream gasStream;

  @BeforeAll
  static void setUp() {
    testSystem = new SystemSrkEos(298.15, 1.0);
    testSystem.addComponent("methane", 1.0);

    gasStream = new Stream("turbine stream", testSystem);
    gasStream.setFlowRate(1.0, "MSm3/day");
    gasStream.setTemperature(50.0, "C");
    gasStream.setPressure(2.0, "bara");
  }

  @Test
  void testSetInletStream() {
    GasTurbine gasturb = new GasTurbine("turbine");
    gasturb.setInletStream(gasStream);

    Assertions.assertEquals(new GasTurbine("turbine", gasStream), gasturb);
  }

  @Test
  void testGetMechanicalDesign() {
  }

  @Disabled
  @Test
  void testRun() {
    // TODO: test not working
    gasStream.run();
    GasTurbine gasturb = new GasTurbine("turbine", gasStream);

    // gasStream.run();
    gasturb.run();

    logger.info("power generated " + gasturb.getPower() / 1.0e6);
    logger.info("heat generated " + gasturb.getHeat() / 1.0e6);
  }
}
