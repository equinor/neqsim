package neqsim.process.equipment.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for dynamic filter loading and regeneration behavior.
 *
 * @author esol
 * @version 1.0
 */
public class FilterTest {

  /**
   * Creates a methane stream for filter tests.
   *
   * @return initialized feed stream
   */
  private Stream createFeedStream() {
    SystemInterface fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 100.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();
    return feed;
  }

  /**
   * Tests that dynamic filtration accumulates loading, pressure drop, residence time, and breakthrough.
   */
  @Test
  public void testDynamicLoadingBuildsPressureDropAndBreakthrough() {
    Stream feed = createFeedStream();
    Filter filter = new Filter("dynamic filter", feed);
    filter.setDeltaP(0.10);
    filter.setHoldupVolume(1.0);
    filter.setSolidsLoadingRate(5.0);
    filter.setLoadingCapacity(10.0);
    filter.setPressureDropIncreaseAtCapacity(1.0);
    filter.setBreakthroughStartFraction(0.5);
    filter.setCalculateSteadyState(false);

    filter.runTransient(3600.0, UUID.randomUUID());

    assertEquals(5.0, filter.getSolidsLoading(), 1.0e-12);
    assertEquals(0.5, filter.getLoadingFraction(), 1.0e-12);
    assertEquals(0.0, filter.getBreakthroughFraction(), 1.0e-12);
    assertEquals(0.60, filter.getDeltaP(), 1.0e-12);
    assertTrue(filter.getHoldupResidenceTime() > 0.0);

    filter.runTransient(1800.0, UUID.randomUUID());

    assertEquals(7.5, filter.getSolidsLoading(), 1.0e-12);
    assertEquals(0.75, filter.getLoadingFraction(), 1.0e-12);
    assertEquals(0.5, filter.getBreakthroughFraction(), 1.0e-12);
    assertEquals(0.85, filter.getDeltaP(), 1.0e-12);
  }

  /**
   * Tests that backwash and regeneration remove loading and reduce pressure drop.
   */
  @Test
  public void testBackwashAndRegenerationReduceLoading() {
    Stream feed = createFeedStream();
    Filter filter = new Filter("regenerating filter", feed);
    filter.setDeltaP(0.2);
    filter.setSolidsLoading(9.0);
    filter.setLoadingCapacity(10.0);
    filter.setPressureDropIncreaseAtCapacity(1.0);
    filter.setBreakthroughStartFraction(0.5);
    filter.setSolidsLoadingRate(0.0);
    filter.setBackwashRemovalRate(4.0);
    filter.setRegenerationRemovalRate(2.0);
    filter.setCalculateSteadyState(false);

    assertEquals(1.1, filter.getDeltaP(), 1.0e-12);
    assertEquals(0.8, filter.getBreakthroughFraction(), 1.0e-12);

    filter.startBackwash();
    filter.startRegeneration();
    filter.runTransient(1800.0, UUID.randomUUID());

    assertTrue(filter.isBackwashActive());
    assertTrue(filter.isRegenerationActive());
    assertEquals(6.0, filter.getSolidsLoading(), 1.0e-12);
    assertEquals(0.6, filter.getLoadingFraction(), 1.0e-12);
    assertEquals(0.2, filter.getBreakthroughFraction(), 1.0e-12);
    assertEquals(0.8, filter.getDeltaP(), 1.0e-12);

    filter.resetDynamicState();

    assertFalse(filter.isBackwashActive());
    assertFalse(filter.isRegenerationActive());
    assertEquals(0.0, filter.getSolidsLoading(), 1.0e-12);
    assertEquals(0.0, filter.getBreakthroughFraction(), 1.0e-12);
    assertEquals(0.2, filter.getDeltaP(), 1.0e-12);
  }
}
