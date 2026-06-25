package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.filter.SulfurFilter;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for sulfur formation from H2S oxidation and downstream sulfur filtration.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class SulfurOxidationReactorTest extends neqsim.NeqSimTest {

  /**
   * Verifies stoichiometric S8 production from sour methane with oxygen.
   */
  @Test
  public void testStoichiometricSulfurFormation() {
    Stream feed = createSourOxygenFeed();
    SulfurOxidationReactor reactor = new SulfurOxidationReactor("sulfur reactor", feed);
    reactor.setH2SConversionTarget(1.0);
    reactor.run();

    double expectedH2SConsumed = HydrogenProductionUtils.getComponentMoles(feed.getThermoSystem(), "H2S");
    double expectedOxygenConsumed = 0.5 * expectedH2SConsumed;
    assertEquals(expectedH2SConsumed, reactor.getH2SConsumedMoles(), 1.0e-8);
    assertEquals(expectedOxygenConsumed, reactor.getOxygenConsumedMoles(), 1.0e-8);
    assertEquals(expectedH2SConsumed, reactor.getWaterProducedMoles(), 1.0e-8);
    assertEquals(expectedH2SConsumed / 8.0, reactor.getS8ProducedMoles(), 1.0e-8);
    assertEquals(1.0, reactor.getH2SConversion(), 1.0e-8);
    assertEquals("H2S", reactor.getLimitingReactant());
    assertTrue(reactor.isSolidSulfurPresent(), "Produced S8 should be available as solid sulfur");
  }

  /**
   * Verifies reactor-produced sulfur is captured by a sulfur filter and builds dynamic pressure drop.
   */
  @Test
  public void testProducedSulfurIsFilteredWithPressureDropBuildUp() {
    Stream feed = createSourOxygenFeed();
    SulfurOxidationReactor reactor = new SulfurOxidationReactor("sulfur reactor", feed);
    reactor.setH2SConversionTarget(1.0);
    reactor.run();

    SulfurFilter filter = new SulfurFilter("sulfur filter", reactor.getOutletStream());
    filter.setRemovalEfficiency(1.0);
    filter.setDeltaP(0.10);
    filter.setFilterElementCapacity(1000.0);
    filter.setNumberOfElements(1);
    filter.setPressureDropIncreaseAtCapacity(1.0);
    filter.setBreakthroughStartFraction(0.9);
    filter.setCalculateSteadyState(false);
    filter.runTransient(3600.0, UUID.randomUUID());

    assertTrue(filter.isSolidS8Detected(), "Sulfur filter should detect solid S8 from reactor outlet");
    assertTrue(filter.getSolidSulfurRemovalRate() > 0.0, "Sulfur removal rate should be positive");
    assertTrue(filter.getSolidsLoading() > 0.0, "Captured sulfur should accumulate as filter loading");
    assertTrue(filter.getDeltaP() > 0.10, "Filter pressure drop should build above clean pressure drop");
  }

  /**
   * Creates a sour methane feed with enough oxygen to form S8 solids.
   *
   * @return initialized feed stream
   */
  private Stream createSourOxygenFeed() {
    SystemInterface gas = new SystemSrkEos(283.15, 20.0);
    gas.addComponent("methane", 80.0);
    gas.addComponent("H2S", 80.0);
    gas.addComponent("oxygen", 40.0);
    gas.addComponent("water", 1.0e-12);
    gas.addComponent("S8", 1.0e-12);
    gas.setMixingRule("classic");
    gas.setMultiPhaseCheck(true);
    gas.setSolidPhaseCheck("S8");

    Stream feed = new Stream("sour methane feed", gas);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();
    return feed;
  }
}
