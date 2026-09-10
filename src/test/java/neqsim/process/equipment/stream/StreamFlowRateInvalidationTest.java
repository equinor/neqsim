package neqsim.process.equipment.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * A rate setter must not let a reset phase split bypass the stream recalculation cache.
 */
class StreamFlowRateInvalidationTest {
  private Stream gasFeed(double flow) {
    SystemInterface fluid = new SystemSrkEos(303.15, 20.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(flow, "kg/hr");
    feed.run();
    return feed;
  }

  @Test
  void exactRateAssignmentKeepsSolvedPhaseStateAndCache() {
    Stream feed = gasFeed(10000.0);
    double rate = feed.getFlowRate("kg/hr");
    double enthalpy = feed.getFluid().getEnthalpy();
    int phases = feed.getFluid().getNumberOfPhases();
    assertFalse(feed.needRecalculation());
    feed.setFlowRate(rate, "kg/hr");
    assertFalse(feed.needRecalculation());
    assertEquals(phases, feed.getFluid().getNumberOfPhases());
    assertEquals(enthalpy, feed.getFluid().getEnthalpy(), 1.0e-9);
  }

  @Test
  void changedRateBelowCacheToleranceRequiresFlashAndPreservesMass() {
    Stream feed = gasFeed(10000.0);
    double target = feed.getFlowRate("kg/hr") * (1.0 + 1.0e-8);
    feed.setFlowRate(target, "kg/hr");
    assertTrue(feed.needRecalculation());
    feed.run();
    Stream fresh = gasFeed(target);
    assertEquals(1, feed.getFluid().getNumberOfPhases());
    assertEquals(target, feed.getFlowRate("kg/hr"), 1.0e-7);
    assertEquals(fresh.getFluid().getEnthalpy(), feed.getFluid().getEnthalpy(), 1.0e-6);
    assertFalse(feed.needRecalculation());
  }

  @Test
  void wrappedFlowSetterInvalidatesUpstreamAndWrapper() {
    Stream original = gasFeed(10000.0);
    Stream wrapped = new Stream("wrapped", original);
    wrapped.run();
    wrapped.setFlowRate(original.getFlowRate("kg/hr") * (1.0 + 1.0e-8), "kg/hr");
    assertTrue(original.needRecalculation());
    assertTrue(wrapped.needRecalculation());
    original.run();
    wrapped.run();
    assertEquals(1, original.getFluid().getNumberOfPhases());
    assertEquals(original.getFlowRate("kg/hr"), wrapped.getFlowRate("kg/hr"), 1.0e-8);
  }

  @Test
  void repeatedPressureCandidatesMatchFreshCompressionAndConserveMass() {
    for (boolean polytropic : new boolean[] { false, true }) {
      ProcessSystem reused = compressionProcess(50000.0, 80.0, polytropic);
      Stream feed = (Stream) reused.getUnit("feed");
      Compressor compressor = (Compressor) reused.getUnit("compressor");
      for (double pressure : new double[] { 60.01, 60.0, 60.02, 59.99, 60.0 }) {
        feed.setFlowRate(10000.0, "kg/hr");
        compressor.setOutletPressure(pressure);
        reused.run();
        ProcessSystem fresh = compressionProcess(10000.0, pressure, polytropic);
        Compressor expected = (Compressor) fresh.getUnit("compressor");
        assertTrue(Double.isFinite(compressor.getPower("kW")));
        assertTrue(compressor.getPower("kW") > 0.0);
        assertEquals(expected.getPower("kW"), compressor.getPower("kW"), 1.0e-5);
        assertEquals(expected.getOutletStream().getTemperature(), compressor.getOutletStream().getTemperature(),
            1.0e-5);
        Cooler cooler = (Cooler) reused.getUnit("cooler");
        assertEquals(feed.getFlowRate("kg/hr"), cooler.getOutletStream().getFlowRate("kg/hr"), 1.0e-6);
      }
    }
  }

  private ProcessSystem compressionProcess(double flow, double pressure, boolean polytropic) {
    Stream feed = gasFeed(flow);
    Compressor compressor = new Compressor("compressor", feed);
    compressor.setOutletPressure(pressure);
    compressor.setUsePolytropicCalc(polytropic);
    Cooler cooler = new Cooler("cooler", compressor.getOutletStream());
    cooler.setOutletTemperature(313.15);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(compressor);
    process.add(cooler);
    process.run();
    return process;
  }
}
