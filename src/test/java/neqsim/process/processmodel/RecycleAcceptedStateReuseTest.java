package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression coverage for reuse of a previously accepted recycle state between process runs.
 */
class RecycleAcceptedStateReuseTest {
  private enum ExecutionMode {
    SEQUENTIAL, HYBRID, PROGRESS
  }

  private static final class Fixture {
    private final ProcessSystem process;
    private final Stream feed;
    private final StreamInterface product;
    private final Splitter splitter;
    private final Recycle recycle;

    Fixture(ProcessSystem process, Stream feed, StreamInterface product, Splitter splitter, Recycle recycle) {
      this.process = process;
      this.feed = feed;
      this.product = product;
      this.splitter = splitter;
      this.recycle = recycle;
    }
  }

  private SystemInterface createFluid(boolean cpa) {
    SystemInterface fluid = cpa ? new SystemSrkCPAstatoil(303.15, 60.0) : new SystemSrkEos(303.15, 60.0);
    fluid.addComponent("methane", cpa ? 0.89 : 0.90);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.04);
    if (cpa) {
      fluid.addComponent("water", 0.01);
      fluid.setMixingRule(10);
      fluid.setMultiPhaseCheck(true);
    } else {
      fluid.setMixingRule("classic");
    }
    return fluid;
  }

  private Fixture createFixture(boolean cpa, double feedFlow) {
    SystemInterface fluid = createFluid(cpa);
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(feedFlow, "kg/hr");

    Stream recycleBack = new Stream("recycle back", fluid.clone());
    recycleBack.setFlowRate(0.0, "kg/hr");

    Mixer mixer = new Mixer("mixer");
    mixer.addStream(feed);
    mixer.addStream(recycleBack);

    Separator separator = new Separator("separator", mixer.getOutletStream());
    Splitter splitter = new Splitter("splitter", separator.getGasOutStream(), 2);
    splitter.setSplitFactors(new double[] { 0.2, 0.8 });

    Recycle recycle = new Recycle("recycle");
    recycle.addStream(splitter.getSplitStream(1));
    recycle.setOutletStream(recycleBack);

    ProcessSystem process = new ProcessSystem("accepted recycle state");
    process.add(feed);
    process.add(recycleBack);
    process.add(mixer);
    process.add(separator);
    process.add(splitter);
    process.add(recycle);
    process.setProfilingEnabled(true);

    return new Fixture(process, feed, splitter.getSplitStream(0), splitter, recycle);
  }

  private void run(Fixture fixture, ExecutionMode mode, UUID id) throws Exception {
    switch (mode) {
    case SEQUENTIAL:
      fixture.process.runSequential(id);
      break;
    case HYBRID:
      fixture.process.runHybrid(id);
      break;
    case PROGRESS:
      fixture.process.runWithProgress(id);
      break;
    default:
      throw new IllegalStateException("Unsupported execution mode " + mode);
    }
  }

  private long calls(ProcessSystem process, String unitName) {
    Map<String, double[]> profile = process.getExecutionProfile();
    double[] timing = profile.get(unitName);
    return timing == null ? 0L : Math.round(timing[1]);
  }

  private void settle(Fixture fixture, ExecutionMode mode) throws Exception {
    for (int i = 0; i < 12; i++) {
      run(fixture, mode, UUID.randomUUID());
    }
  }

  private void assertStableReuse(ExecutionMode mode, boolean cpa) throws Exception {
    Fixture fixture = createFixture(cpa, 50000.0);
    settle(fixture, mode);

    long mixerCalls = calls(fixture.process, "mixer");
    long separatorCalls = calls(fixture.process, "separator");
    long splitterCalls = calls(fixture.process, "splitter");
    long recycleCalls = calls(fixture.process, "recycle");
    double productFlow = fixture.product.getFlowRate("kg/hr");
    double productTemperature = fixture.product.getTemperature("K");
    double productPressure = fixture.product.getPressure("bara");
    int phases = fixture.product.getThermoSystem().getNumberOfPhases();

    UUID repeatId = UUID.randomUUID();
    run(fixture, mode, repeatId);

    assertEquals(1L, calls(fixture.process, "mixer") - mixerCalls);
    assertEquals(1L, calls(fixture.process, "separator") - separatorCalls);
    assertEquals(1L, calls(fixture.process, "splitter") - splitterCalls);
    assertEquals(1L, calls(fixture.process, "recycle") - recycleCalls);
    assertEquals(2, fixture.recycle.getIterations());
    assertTrue(fixture.recycle.solved());
    assertEquals(productFlow, fixture.product.getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(productTemperature, fixture.product.getTemperature("K"), 1.0e-10);
    assertEquals(productPressure, fixture.product.getPressure("bara"), 1.0e-10);
    assertEquals(phases, fixture.product.getThermoSystem().getNumberOfPhases());
    assertEquals(repeatId, fixture.recycle.getCalculationIdentifier());
    assertEquals(repeatId, fixture.process.getCalculationIdentifier());
  }

  @Test
  void stableSrkRecycleUsesOnePhysicalConfirmationAcrossExecutionModes() throws Exception {
    for (ExecutionMode mode : ExecutionMode.values()) {
      assertStableReuse(mode, false);
    }
  }

  @Test
  void toleranceLevelConvergenceRetainsLegacyConfirmationPass() throws Exception {
    Fixture fixture = createFixture(false, 50000.0);
    run(fixture, ExecutionMode.SEQUENTIAL, UUID.randomUUID());
    long recycleCalls = calls(fixture.process, "recycle");

    run(fixture, ExecutionMode.SEQUENTIAL, UUID.randomUUID());

    assertEquals(2L, calls(fixture.process, "recycle") - recycleCalls);
    assertEquals(2, fixture.recycle.getIterations());
    assertTrue(fixture.recycle.solved());
  }

  @Test
  void stableCpaRecycleUsesOnePhysicalConfirmation() throws Exception {
    assertStableReuse(ExecutionMode.SEQUENTIAL, true);
  }

  @Test
  void changedSplitterConfigurationRetainsLegacyConfirmationPass() throws Exception {
    Fixture fixture = createFixture(false, 50000.0);
    settle(fixture, ExecutionMode.SEQUENTIAL);
    long mixerCalls = calls(fixture.process, "mixer");
    long separatorCalls = calls(fixture.process, "separator");
    long splitterCalls = calls(fixture.process, "splitter");
    long recycleCalls = calls(fixture.process, "recycle");

    fixture.splitter.setFlowRates(new double[] { 50000.0, 0.0 }, "kg/hr");
    run(fixture, ExecutionMode.SEQUENTIAL, UUID.randomUUID());

    assertEquals(2L, calls(fixture.process, "mixer") - mixerCalls);
    assertEquals(2L, calls(fixture.process, "separator") - separatorCalls);
    assertEquals(2L, calls(fixture.process, "splitter") - splitterCalls);
    assertEquals(1L, calls(fixture.process, "recycle") - recycleCalls);
    assertEquals(2, fixture.recycle.getIterations());
    assertEquals(50000.0, fixture.product.getFlowRate("kg/hr"), 1.0e-6);
    assertEquals(0.0, fixture.splitter.getSplitStream(1).getFlowRate("kg/hr"), 1.0e-6);
    assertTrue(fixture.recycle.solved());
  }

  @Test
  void changedBoundaryStillIteratesAndMatchesFreshSolve() throws Exception {
    Fixture reused = createFixture(false, 50000.0);
    settle(reused, ExecutionMode.SEQUENTIAL);

    reused.feed.setFlowRate(50500.0, "kg/hr");
    UUID changedId = UUID.randomUUID();
    run(reused, ExecutionMode.SEQUENTIAL, changedId);
    assertTrue(reused.recycle.getIterations() > 2);
    assertTrue(reused.recycle.solved());
    assertEquals(changedId, reused.recycle.getCalculationIdentifier());
    assertEquals(changedId, reused.process.getCalculationIdentifier());

    settle(reused, ExecutionMode.SEQUENTIAL);

    Fixture fresh = createFixture(false, 50500.0);
    settle(fresh, ExecutionMode.SEQUENTIAL);

    assertEquals(fresh.product.getFlowRate("kg/hr"), reused.product.getFlowRate("kg/hr"), 0.1);
    assertEquals(fresh.product.getTemperature("K"), reused.product.getTemperature("K"), 1.0e-9);
    assertEquals(fresh.product.getPressure("bara"), reused.product.getPressure("bara"), 1.0e-9);
    assertEquals(fresh.product.getThermoSystem().getNumberOfPhases(),
        reused.product.getThermoSystem().getNumberOfPhases());
    assertEquals(reused.process.getCalculationIdentifier(), reused.recycle.getCalculationIdentifier());
  }
}
