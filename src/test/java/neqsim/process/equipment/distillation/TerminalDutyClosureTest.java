package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Checks terminal duties against independently initialized published products.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TerminalDutyClosureTest {
  /**
   * Checks all condenser modes, including zero reflux and repeated changed-feed runs.
   *
   * @param mode equilibrium, ratio, total or fixed-liquid-reflux mode
   */
  @ParameterizedTest
  @ValueSource(strings = {"equilibrium", "ratio", "total", "fixed"})
  void condenserDutyClosesForPublishedProducts(String mode) {
    Stream feed = createFeed();
    Condenser condenser = new Condenser("condenser");
    condenser.addStream(feed);
    condenser.setOutletTemperature(278.15);
    if ("ratio".equals(mode) || "total".equals(mode)) {
      condenser.setRefluxRatio(1.5);
      condenser.setTotalCondenser("total".equals(mode));
    } else if ("fixed".equals(mode)) {
      condenser.setSeparation_with_liquid_reflux(true, 10.0, "kg/hr");
    }
    for (double temperature : new double[] {298.15, 303.15, 298.15}) {
      feed.setTemperature(temperature, "K");
      feed.run();
      condenser.run();
      assertClosure(feed, condenser.getDuty(), condenser.getGasOutStream(), condenser.getLiquidOutStream(),
          condenser.getLiquidProductStream());
      assertEquals(condenser.getDuty(), condenser.getEnergyPort("heatDuty").getDuty(), 1.0e-9);
    }
    if ("ratio".equals(mode) || "total".equals(mode)) {
      condenser.setRefluxRatio(0.0);
      condenser.run();
      assertClosure(feed, condenser.getDuty(), condenser.getGasOutStream(), condenser.getLiquidOutStream());
    }
  }

  /**
   * Checks equilibrium and explicit boilup modes over repeated feed changes.
   *
   * @param ratio whether to specify a vapor boilup ratio
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void reboilerDutyClosesForPublishedProducts(boolean ratio) {
    Stream feed = createFeed();
    Reboiler reboiler = new Reboiler("reboiler");
    reboiler.addStream(feed);
    reboiler.setOutletTemperature(320.15);
    if (ratio) {
      reboiler.setRefluxRatio(1.5);
    }
    for (double temperature : new double[] {298.15, 303.15, 298.15}) {
      feed.setTemperature(temperature, "K");
      feed.run();
      reboiler.run();
      assertClosure(feed, reboiler.getDuty(), reboiler.getGasOutStream(), reboiler.getLiquidOutStream());
      assertEquals(reboiler.getDuty(), reboiler.getEnergyPort("heatDuty").getDuty(), 1.0e-9);
    }
    if (ratio) {
      reboiler.setRefluxRatio(0.0);
      reboiler.run();
      assertClosure(feed, reboiler.getDuty(), reboiler.getGasOutStream(), reboiler.getLiquidOutStream());
    }
  }

  /**
   * Creates a two-phase hydrocarbon feed with a nontrivial latent duty.
   *
   * @return initialized feed at 8 bara and 100 kg/h
   */
  private static Stream createFeed() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 8.0);
    fluid.addComponent("methane", 0.05);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.25);
    fluid.addComponent("n-butane", 0.30);
    fluid.addComponent("n-pentane", 0.30);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();
    return feed;
  }

  /**
   * Independently checks energy, mass and every component without reflashing products.
   *
   * @param feed material inlet
   * @param duty reported duty in W
   * @param products every published material product, with optional null entries
   */
  private static void assertClosure(Stream feed, double duty, StreamInterface... products) {
    feed.getFluid().init(2);
    double productEnthalpy = 0.0;
    double productMass = 0.0;
    double[] componentFlows = new double[feed.getFluid().getNumberOfComponents()];
    for (StreamInterface product : products) {
      if (product == null) {
        continue;
      }
      double mass = product.getFlowRate("kg/hr");
      assertTrue(Double.isFinite(mass) && mass >= 0.0);
      productMass += mass;
      if (mass > 1.0e-12) {
        product.getFluid().init(2);
        productEnthalpy += product.getFluid().getEnthalpy();
        for (int i = 0; i < componentFlows.length; i++) {
          componentFlows[i] += product.getFlowRate("mol/hr") * product.getFluid().getComponent(i).getz();
        }
      }
    }
    assertEquals(feed.getFlowRate("kg/hr"), productMass, 1.0e-7);
    for (int i = 0; i < componentFlows.length; i++) {
      double input = feed.getFlowRate("mol/hr") * feed.getFluid().getComponent(i).getz();
      assertEquals(input, componentFlows[i], Math.max(1.0e-8, Math.abs(input) * 1.0e-8));
    }
    double expectedDuty = productEnthalpy - feed.getFluid().getEnthalpy();
    assertTrue(Double.isFinite(duty));
    assertEquals(expectedDuty, duty, Math.max(1.0e-6, Math.abs(expectedDuty) * 1.0e-8),
        "duty must equal published product enthalpy rates minus feed enthalpy rates");
  }
}
