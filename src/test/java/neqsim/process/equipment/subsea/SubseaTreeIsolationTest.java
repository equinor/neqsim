package neqsim.process.equipment.subsea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies production-path isolation and restart on the connected outlet.
 *
 * @author NeqSim
 * @version 1.0
 */
class SubseaTreeIsolationTest {
  /** Exercise each series restriction independently and retain the feed during PSD. */
  @Test
  void isolateAndRestartOnSameStream() {
    SystemSrkEos fluid = new SystemSrkEos(315.0, 80.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-decane", 0.2);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("Well", fluid);
    feed.setFlowRate(20.0, "kg/sec");
    feed.run();
    SubseaTree tree = new SubseaTree("Tree", feed);
    StreamInterface outlet = tree.getOutletStream();
    tree.run();
    SubseaManifold manifold = new SubseaManifold("Manifold", 1);
    manifold.addWellStream(outlet, "Well");
    manifold.run();
    StreamInterface production = manifold.getProductionStream();
    manifold.routeToTest("Well");
    manifold.run();
    StreamInterface testHeader = manifold.getTestStream();
    assertEquals(0.0, production.getFlowRate("kg/sec"));
    assertEquals(20.0, testHeader.getFlowRate("kg/sec"), 1e-8);
    manifold.shutInWell("Well");
    manifold.run();
    assertEquals(0.0, testHeader.getFlowRate("kg/sec"));
    manifold.routeToProduction("Well");
    manifold.run();
    assertSame(production, manifold.getProductionStream());
    assertEquals(20.0, production.getFlowRate("kg/sec"), 1e-8);
    tree.setProductionMasterValveOpen(false);
    tree.run();
    assertEquals(0.0, outlet.getFlowRate("kg/sec"));
    tree.setProductionMasterValveOpen(true);
    tree.setProductionWingValveOpen(false);
    tree.run();
    assertEquals(0.0, outlet.getFlowRate("kg/sec"));
    tree.emergencyShutdown();
    UUID step = UUID.randomUUID();
    tree.run(step);
    manifold.run(step);
    assertEquals(step, production.getCalculationIdentifier());
    assertEquals(0.0, production.getFlowRate("kg/sec"));
    assertEquals(step, outlet.getCalculationIdentifier());
    assertEquals(0.0, outlet.getFluid().getComponent("n-decane").getNumberOfmoles());
    assertEquals(20.0, feed.getFlowRate("kg/sec"), 1e-8);
    tree.openForProduction();
    tree.run();
    assertEquals(0.0, outlet.getFlowRate("kg/sec"));
    tree.setChokeOpening(100.0);
    tree.run();
    assertEquals(20.0, outlet.getFlowRate("kg/sec"), 1e-8);
    manifold.run();
    assertEquals(20.0, production.getFlowRate("kg/sec"), 1e-8);
  }
}
