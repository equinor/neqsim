package neqsim.process.equipment.splitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Regression coverage for exact component removal and empty split branches.
 *
 * @author NeqSim
 * @version 1.0
 */
class ComponentSplitterZeroFlowTest {
  /** Verifies that a closed branch remains empty when run and sent to a separator. */
  @Test
  void emptyBranchCanRunDownstream() {
    SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(303.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("water", 0.1);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();
    ComponentSplitter splitter = new ComponentSplitter("dehydrator", feed);
    splitter.setSplitFactors(new double[] {0.0, 0.0});
    splitter.run();
    Stream empty = new Stream("empty", splitter.getSplitStream(0));
    empty.run();
    assertEquals(0.0, empty.getFlowRate("kg/hr"), 1e-10);
    Separator scrubber = new Separator("scrubber", empty);
    scrubber.run();
    assertEquals(0.0, scrubber.getGasOutStream().getFlowRate("kg/hr"), 1e-10);
    assertEquals(0.0, scrubber.getLiquidOutStream().getFlowRate("kg/hr"), 1e-10);
    assertEquals(0.0, splitter.getMassBalance("kg/hr"), 1e-6);
  }

  /** Verifies complete water removal and component conservation on repeated flashes. */
  @Test
  void exactWaterRemovalConservesFlow() {
    SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(303.15, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("water", 0.1);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();
    ComponentSplitter splitter = new ComponentSplitter("dehydrator", feed);
    splitter.setSplitFactors(new double[] {1.0, 1.0, 0.0});
    Separator scrubber = new Separator("scrubber", splitter.getSplitStream(0));
    for (int i = 0; i < 3; i++) {
      splitter.run();
      splitter.getSplitStream(0).run();
      scrubber.run();
      assertEquals(0.0, splitter.getSplitStream(0).getFluid().getComponent("water").getNumberOfmoles(), 1e-12);
      assertEquals(0.0, splitter.getMassBalance("kg/hr"), 1e-6);
      double downstream = scrubber.getGasOutStream().getFlowRate("kg/hr")
          + scrubber.getLiquidOutStream().getFlowRate("kg/hr");
      assertTrue(Double.isFinite(downstream));
      assertEquals(splitter.getSplitStream(0).getFlowRate("kg/hr"), downstream, 1e-6);
    }
  }

  /** Verifies invalid factors are rejected and caller mutation cannot change the specification. */
  @Test
  void validatesFactorsWithoutRejectingExactEndpoints() {
    SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(303.15, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.addComponent("water", 0.1);
    fluid.setMixingRule(10);
    Stream feed = new Stream("feed", fluid);
    feed.run();
    ComponentSplitter splitter = new ComponentSplitter("splitter", feed);
    double[] factors = {1.0, 0.0};
    splitter.setSplitFactors(factors);
    factors[0] = Double.NaN;
    splitter.getSplitFactors()[1] = -1.0;
    assertTrue(splitter.validateSetup().isValid());
    splitter.run();
    splitter.setSplitFactors(new double[] {1.0});
    assertFalse(splitter.validateSetup().isValid());
    assertThrows(IllegalArgumentException.class, () -> splitter.run());
    splitter.setSplitFactors(new double[] {1.0, Double.NaN});
    assertFalse(splitter.validateSetup().isValid());
    splitter.setSplitFactors(new double[] {1.0, 1.1});
    assertFalse(splitter.validateSetup().isValid());
  }
}
