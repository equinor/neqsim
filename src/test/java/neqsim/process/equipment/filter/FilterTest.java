package neqsim.process.equipment.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

  /** Tests beta-ratio capture and the associated contaminant mass balance. */
  @Test
  public void testBetaRatioCurveCalculatesParticleCapture() {
    Stream feed = createFeedStream();
    FilterPerformanceCurve curve = new FilterPerformanceCurve(new double[] { 5.0, 10.0, 20.0 },
        new double[] { 2.0, 100.0, 1000.0 });
    curve.setTestStandard("ISO 16889:2022");

    Filter filter = new Filter("beta rated filter", feed);
    filter.setPerformanceCurve(curve);
    filter.setParticleSize(10.0);
    filter.setInletParticleConcentration(100.0);
    filter.run();

    assertEquals(0.99, filter.getNominalRemovalEfficiency(), 1.0e-12);
    assertEquals(1.0, filter.getOutletParticleConcentration(), 1.0e-12);
    assertEquals(0.099, filter.getCalculatedCapturedRate(), 1.0e-12);
    assertEquals("ISO 16889:2022", filter.getPerformanceCurve().getTestStandard());
  }

  /** Tests type-specific reference-flow scaling for an inertial strainer. */
  @Test
  public void testFlowScaledPressureDrop() {
    Stream feed = createFeedStream();
    double referenceFlow = feed.getFlowRate("m3/hr");

    Filter filter = new Filter("flow scaled strainer", feed);
    filter.setFilterServiceType(FilterType.Y_STRAINER);
    filter.setDeltaP(0.10);
    filter.setReferenceFlowRate(referenceFlow);
    filter.run();
    assertEquals(0.10, filter.getDeltaP(), 1.0e-10);

    feed.setFlowRate(2000.0, "kg/hr");
    feed.run();
    filter.run();

    assertEquals(0.40, filter.getDeltaP(), 2.0e-3);
  }

  /** Tests interpolation of a measured pressure-drop versus flow curve. */
  @Test
  public void testTabulatedPressureDropCurve() {
    Stream feed = createFeedStream();
    double flow = feed.getFlowRate("m3/hr");
    FilterPressureDropCurve curve = new FilterPressureDropCurve(new double[] { 0.5 * flow, 1.5 * flow },
        new double[] { 0.10, 0.50 });
    curve.setTestStandard("ISO 3968:2017");

    Filter filter = new Filter("tested cartridge", feed);
    filter.setPressureDropCurve(curve);
    filter.run();

    assertEquals(0.30, filter.getCalculatedCleanDeltaP(), 1.0e-10);
    assertEquals("ISO 3968:2017", filter.getPressureDropCurve().getTestStandard());
  }

  /** Tests packed-media Ergun pressure drop and its physically monotonic flow trend. */
  @Test
  public void testErgunPressureDropIncreasesWithFlow() {
    Stream feed = createFeedStream();
    Filter filter = new Filter("granular guard bed", feed);
    filter.setFilterServiceType(FilterType.GRANULAR_MEDIA);
    filter.setMediaGeometry(1.0, 1.5, 0.002, 0.40);
    filter.run();
    double lowFlowPressureDrop = filter.getDeltaP();

    feed.setFlowRate(2000.0, "kg/hr");
    feed.run();
    filter.run();

    assertTrue(lowFlowPressureDrop > 0.0);
    assertTrue(filter.getDeltaP() > lowFlowPressureDrop);
  }

  /** Tests automatic bypass opening when loaded-element differential pressure reaches its setting. */
  @Test
  public void testBypassLimitsPressureDropAndReducesCapture() {
    Stream feed = createFeedStream();
    Filter filter = new Filter("bypass cartridge", feed);
    filter.setDeltaP(0.20);
    filter.setNominalRemovalEfficiency(0.99);
    filter.setInletParticleConcentration(100.0);
    filter.setLoadingCapacity(10.0);
    filter.setSolidsLoading(5.0);
    filter.setPressureDropIncreaseAtCapacity(3.6);
    filter.setBypassCrackingDeltaP(0.5);
    filter.run();

    assertEquals(2.0, filter.getUnrestrictedDeltaP(), 1.0e-12);
    assertEquals(0.5, filter.getDeltaP(), 1.0e-12);
    assertEquals(0.5, filter.getBypassFraction(), 1.0e-12);
    assertEquals(0.495, filter.getCurrentRemovalEfficiency(), 1.0e-12);
  }

  /** Tests differential-pressure unit conversion and invalid curve input validation. */
  @Test
  public void testPressureDropUnitsAndCurveValidation() {
    Stream feed = createFeedStream();
    Filter filter = new Filter("unit conversion filter", feed);
    filter.setDeltaP(50.0, "kPa");
    filter.run();

    assertEquals(0.5, filter.getDeltaP(), 1.0e-12);
    assertThrows(IllegalArgumentException.class,
        () -> new FilterPerformanceCurve(new double[] { 10.0, 5.0 }, new double[] { 100.0, 200.0 }));
    assertThrows(IllegalArgumentException.class,
        () -> new FilterPressureDropCurve(new double[] { 1.0 }, new double[] { -0.1 }));
  }
}
