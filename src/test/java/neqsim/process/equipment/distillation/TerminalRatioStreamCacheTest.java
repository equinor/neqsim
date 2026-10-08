package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verify terminal ratio flashes publish the current inlet inventory on every run.
 *
 * @author NeqSim
 * @version 1.0
 */
class TerminalRatioStreamCacheTest {
  /** Verify a partial condenser refreshes previously requested outlet streams. */
  @Test
  void condenserRefreshesProductsAfterFeedFlowChange() {
    Stream feed = createFeed(350.0);
    Condenser condenser = new Condenser("partial condenser");
    condenser.addStream(feed);
    condenser.setRefluxRatio(1.8);
    condenser.run();
    double firstGas = condenser.getGasOutStream().getFlowRate("mol/hr");
    double firstLiquid = condenser.getLiquidOutStream().getFlowRate("mol/hr");
    feed.setFlowRate(200.0, "mol/hr");
    feed.run();
    condenser.run();
    assertEquals(2.0 * firstGas, condenser.getGasOutStream().getFlowRate("mol/hr"), 1.0e-5);
    assertEquals(2.0 * firstLiquid, condenser.getLiquidOutStream().getFlowRate("mol/hr"), 1.0e-5);
  }

  /** Verify a reboiler refreshes previously requested outlet streams. */
  @Test
  void reboilerRefreshesProductsAfterFeedFlowChange() {
    Stream feed = createFeed(300.0);
    Reboiler reboiler = new Reboiler("reboiler");
    reboiler.addStream(feed);
    reboiler.setRefluxRatio(1.2);
    reboiler.run();
    double firstGas = reboiler.getGasOutStream().getFlowRate("mol/hr");
    double firstLiquid = reboiler.getLiquidOutStream().getFlowRate("mol/hr");
    feed.setFlowRate(200.0, "mol/hr");
    feed.run();
    reboiler.run();
    assertEquals(2.0 * firstGas, reboiler.getGasOutStream().getFlowRate("mol/hr"), 1.0e-5);
    assertEquals(2.0 * firstLiquid, reboiler.getLiquidOutStream().getFlowRate("mol/hr"), 1.0e-5);
  }

  /**
   * Create a common hydrocarbon feed.
   *
   * @param temperature feed temperature in kelvin
   * @return flashed feed at 100 mol/hr
   */
  private Stream createFeed(double temperature) {
    SystemSrkEos fluid = new SystemSrkEos(temperature, 10.0);
    fluid.addComponent("propane", 0.35);
    fluid.addComponent("n-butane", 0.45);
    fluid.addComponent("n-pentane", 0.20);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100.0, "mol/hr");
    feed.run();
    return feed;
  }
}
