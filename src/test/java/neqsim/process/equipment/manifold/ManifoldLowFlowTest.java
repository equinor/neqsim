package neqsim.process.equipment.manifold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies that a {@link Manifold} honours the low-flow bypass threshold.
 *
 * <p>
 * A Manifold delegates all work to an internal mixer and splitter, so the threshold must be propagated to those units.
 * Without the propagation a {@code setMinimumFlow()} or a plant-wide {@code ProcessSystem.setSectionLowFlowThreshold()}
 * call on a manifold is silently ignored and a stagnant dead leg keeps being flashed.
 * </p>
 *
 * @author NeqSim
 * @version $Id: $Id
 */
public class ManifoldLowFlowTest extends neqsim.NeqSimTest {

  /**
   * Builds a simple two-component gas at the requested mass flow.
   *
   * @param flowKgHr total mass flow in kg/hr
   * @return a ready-to-use thermodynamic system
   */
  private static SystemInterface makeGas(double flowKgHr) {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(flowKgHr, "kg/hr");
    return fluid;
  }

  /**
   * Builds a one-inlet, two-outlet manifold inside a process and runs it.
   *
   * @param feedKgHr manifold feed mass flow in kg/hr
   * @param thresholdKgHr low-flow bypass threshold in kg/hr
   * @return the manifold after the process has run
   */
  private static Manifold runManifold(double feedKgHr, double thresholdKgHr) {
    Stream feed = new Stream("feed", makeGas(feedKgHr));
    feed.setPressure(50.0, "bara");
    feed.setTemperature(25.0, "C");

    Manifold manifold = new Manifold("manifold");
    manifold.addStream(feed);
    manifold.setSplitFactors(new double[] { 0.5, 0.5 });

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(manifold);
    process.setSectionLowFlowThreshold(thresholdKgHr);
    process.run();
    return manifold;
  }

  @Test
  public void lowFlowThresholdBypassesManifoldAndZeroesOutlets() {
    Manifold manifold = runManifold(0.05, 1.0);

    assertFalse(manifold.isActive());
    assertEquals(0.0, manifold.getSplitStream(0).getFlowRate("kg/hr"), 1e-9);
    assertEquals(0.0, manifold.getSplitStream(1).getFlowRate("kg/hr"), 1e-9);
  }

  @Test
  public void normalFlowIsUnaffectedByTheThreshold() {
    Manifold manifold = runManifold(1000.0, 1.0);

    assertTrue(manifold.isActive());
    assertEquals(500.0, manifold.getSplitStream(0).getFlowRate("kg/hr"), 1.0);
    assertEquals(500.0, manifold.getSplitStream(1).getFlowRate("kg/hr"), 1.0);
  }

  @Test
  public void thresholdIsPropagatedToInternalUnits() {
    Manifold manifold = runManifold(1000.0, 25.0);
    assertEquals(25.0, manifold.getMinimumFlow(), 1e-12);
    // The internal splitter is what actually performs the bypass, so it must carry
    // the same threshold after a run.
    assertEquals(25.0, manifold.localsplitter.getMinimumFlow(), 1e-12);
    assertEquals(25.0, manifold.localmixer.getMinimumFlow(), 1e-12);
  }
}
