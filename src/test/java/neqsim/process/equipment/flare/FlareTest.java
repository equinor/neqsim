package neqsim.process.equipment.flare;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Test of the Flare unit operation. */
public class FlareTest {
  ProcessSystem processOps;
  Flare flare;

  @BeforeEach
  public void setUp() {
    SystemSrkEos testSystem = new SystemSrkEos(298.15, 1.0);
    testSystem.addComponent("methane", 1.0);
    Stream gasStream = new Stream("gas stream", testSystem);
    gasStream.setFlowRate(1.0, "MSm3/day");

    processOps = new ProcessSystem();
    flare = new Flare("flare", gasStream);
    processOps.add(gasStream);
    processOps.add(flare);
    processOps.run();
  }

  @Test
  public void testFlareCalculations() {
    assertTrue(flare.getHeatDuty() > 0.0);
    assertTrue(flare.getCO2Emission() > 0.0);
  }
}
