package neqsim.processSimulation.processEquipment.manifold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processSystem.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class ManifoldTest {
  @Test
  void testRun() {

    SystemSrkEos testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("propane", 10.0);

    SystemSrkEos testSystem2 = testSystem.clone();
    testSystem2.setMolarComposition(new double[] {0.1, 0.4, 0.4});

    ProcessSystem processOps = new ProcessSystem();

    Stream inletStream = new Stream("inletStream", testSystem);
    inletStream.setName("inlet stream");
    inletStream.setPressure(10.0, "bara");
    inletStream.setTemperature(20.0, "C");
    inletStream.setFlowRate(3.0, "MSm3/day");
    inletStream.run();

    Stream inletStream2 = new Stream("inletStream", testSystem2);
    inletStream2.setName("inlet stream");
    inletStream2.setPressure(10.0, "bara");
    inletStream2.setTemperature(20.0, "C");
    inletStream2.setFlowRate(2.0, "MSm3/day");
    inletStream2.run();

    Manifold manifold1 = new Manifold("manifold 1");
    manifold1.addStream(inletStream);
    manifold1.addStream(inletStream2);
    manifold1.setSplitFactors(new double[] {0.1, 0.5, 0.4});
    manifold1.run();

    assertEquals(0.5, manifold1.getSplitStream(0).getFlowRate("MSm3/day"), 0.01);
    assertEquals(manifold1.getSplitStream(1).getFluid().getComponent(0).getx(),
        manifold1.getSplitStream(0).getFluid().getComponent(0).getx(), 1e-6);



  }
}
