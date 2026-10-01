package neqsim.process.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.splitter.ComponentSplitter;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Checks published outlet caloric properties against independently constructed streams.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PublishedOutletCaloricStateTest {
  /**
   * Creates and runs a fresh PR stream with the supplied component inventory.
   *
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @param names component names
   * @param moles component molar rates in mol/s
   * @return initialized stream
   */
  private Stream freshStream(double temperature, double pressure, String[] names, double[] moles) {
    SystemInterface fluid = new SystemPrEos(temperature, pressure);
    double total = 0.0;
    for (int i = 0; i < names.length; i++) {
      fluid.addComponent(names[i], moles[i]);
      total += moles[i];
    }
    fluid.setMixingRule(2);
    Stream stream = new Stream("reference", fluid);
    stream.setFlowRate(total, "mol/sec");
    stream.run();
    return stream;
  }

  /**
   * Checks caloric values without first reinitializing the published outlet.
   *
   * @param actual published stream
   * @param expected independently constructed reference
   */
  private void assertCaloricState(StreamInterface actual, StreamInterface expected) {
    assertEquals(expected.getFluid().getEnthalpy("J/mol"), actual.getFluid().getEnthalpy("J/mol"), 1e-5);
    assertEquals(expected.getFluid().getEntropy("J/molK"), actual.getFluid().getEntropy("J/molK"), 1e-6);
    assertEquals(expected.getFluid().getNumberOfPhases(), actual.getFluid().getNumberOfPhases());
  }

  /** Verifies composition changes, component closure, repeat use and changed temperature. */
  @Test
  void componentSplitPublishesFreshCaloricState() {
    String[] names = {"methane", "n-butane", "n-pentane"};
    double[] feedMoles = {0.5, 0.3, 0.2};
    double[] factors = {0.98, 0.05, 0.02};
    Stream feed = freshStream(300.0, 20.0, names, feedMoles);
    ComponentSplitter splitter = new ComponentSplitter("splitter", feed);
    splitter.setSplitFactors(factors);
    for (double temperature : new double[] {300.0, 300.0, 315.0}) {
      feed.setTemperature(temperature, "K");
      feed.run();
      splitter.run();
      for (int outlet = 0; outlet < 2; outlet++) {
        double[] outletMoles = new double[names.length];
        for (int component = 0; component < names.length; component++) {
          outletMoles[component] = feedMoles[component] * (outlet == 0 ? factors[component] : 1.0 - factors[component]);
        }
        assertCaloricState(splitter.getSplitStream(outlet), freshStream(temperature, 20.0, names, outletMoles));
      }
      for (int component = 0; component < names.length; component++) {
        assertEquals(feedMoles[component],
            splitter.getSplitStream(0).getFluid().getComponent(component).getNumberOfmoles()
                + splitter.getSplitStream(1).getFluid().getComponent(component).getNumberOfmoles(),
            1e-10);
      }
    }
  }

  /** Verifies both temperature-pin choices, unit conversion, repeat use and energy closure. */
  @Test
  void temperaturePinUsesFreshEnthalpyInEnergyBalance() {
    String[] hotNames = {"methane", "n-butane"};
    String[] coldNames = {"n-butane", "n-pentane"};
    double[] hotMoles = {0.8, 0.2};
    double[] coldMoles = {0.5, 0.5};
    for (int pinned = 0; pinned < 2; pinned++) {
      Stream hot = freshStream(400.0, 20.0, hotNames, hotMoles);
      Stream cold = freshStream(300.0, 5.0, coldNames, coldMoles);
      HeatExchanger exchanger = new HeatExchanger("exchanger", hot, cold);
      exchanger.setUAvalue(100.0);
      exchanger.setOutStreamSpecificationNumber(pinned);
      for (double pin : new double[] {pinned == 0 ? 380.0 : 320.0, pinned == 0 ? 380.0 : 320.0,
          pinned == 0 ? 375.0 : 325.0}) {
        exchanger.setOutTemperature(pin - 273.15, "C");
        exchanger.run();
        for (int side = 0; side < 2; side++) {
          StreamInterface outlet = exchanger.getOutStream(side);
          assertCaloricState(outlet, freshStream(outlet.getTemperature("K"), side == 0 ? 20.0 : 5.0,
              side == 0 ? hotNames : coldNames, side == 0 ? hotMoles : coldMoles));
        }
        assertEquals(pin, exchanger.getOutStream(pinned).getTemperature("K"), 1e-8);
        double inletEnthalpy = hot.getFluid().getEnthalpy() + cold.getFluid().getEnthalpy();
        double outletEnthalpy = exchanger.getOutStream(0).getFluid().getEnthalpy()
            + exchanger.getOutStream(1).getFluid().getEnthalpy();
        assertEquals(inletEnthalpy, outletEnthalpy, 1e-3);
      }
    }
  }

  /** Verifies positive and closed branches preserve intensive properties and energy. */
  @Test
  void proportionalSplitPreservesCaloricStateAndEnergy() {
    Stream feed = freshStream(300.0, 20.0, new String[] {"methane", "n-butane", "n-pentane"},
        new double[] {0.5, 0.3, 0.2});
    Splitter splitter = new Splitter("splitter", feed, 2);
    for (double[] factors : new double[][] {{0.3, 0.7}, {0.0, 1.0}, {1.0, 0.0}, {0.3, 0.7}}) {
      splitter.setSplitFactors(factors);
      splitter.run();
      double outletEnthalpy = 0.0;
      double outletEntropy = 0.0;
      for (int outlet = 0; outlet < 2; outlet++) {
        StreamInterface branch = splitter.getSplitStream(outlet);
        assertEquals(factors[outlet], branch.getFlowRate("mol/sec"), 1e-10);
        if (factors[outlet] > 0.0) {
          assertCaloricState(branch, feed);
          outletEnthalpy += branch.getFluid().getEnthalpy();
          outletEntropy += branch.getFluid().getEntropy();
        }
      }
      assertEquals(feed.getFluid().getEnthalpy(), outletEnthalpy, 1e-5);
      assertEquals(feed.getFluid().getEntropy(), outletEntropy, 1e-6);
    }
  }
}
