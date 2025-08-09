package neqsim.process.equipment.flare;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Test of the FlareStack unit operation. */
public class FlareStackTest {
  ProcessSystem processOps;
  FlareStack flareStack;

  @BeforeEach
  public void setUp() {
    SystemSrkEos testSystem = new SystemSrkEos(298.15, 1.0);
    testSystem.addComponent("methane", 1.0);
    Stream gasStream = new Stream("gas stream", testSystem);
    gasStream.setFlowRate(1.0, "MSm3/day");
    SystemSrkEos airSystem = new SystemSrkEos(298.15, 1.0);
    airSystem.addComponent("nitrogen", 0.79);
    airSystem.addComponent("oxygen", 0.21);
    Stream airStream = new Stream("air stream", airSystem);
    airStream.setFlowRate(1000.0, "kg/hr");

    flareStack = new FlareStack("flare stack");
    flareStack.setReliefInlet(gasStream);
    flareStack.setAirAssist(airStream);

    processOps = new ProcessSystem();
    processOps.add(gasStream);
    processOps.add(airStream);
    processOps.add(flareStack);
    processOps.run();
  }

  @Test
  public void testFlareStackCalculations() {
    assertTrue(flareStack.getHeatReleaseMW() > 0.0);
    assertTrue(flareStack.getEmissionsKgPerHr().get("CO2_kg_h") > 0.0);
  }
}

