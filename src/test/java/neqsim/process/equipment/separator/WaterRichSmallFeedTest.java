package neqsim.process.equipment.separator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos1978;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Public synthetic regressions for water-rich placeholder feeds in issue #4427.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class WaterRichSmallFeedTest {
  /** Checks that a gas-free water/oil feed cannot create gas mass. */
  @Test
  void gasFreeFeedConservesEveryComponent() {
    for (boolean entrainment : new boolean[] {false, true}) {
      SystemInterface fluid = createFluid(0.0, 1.0);
      Stream feed = new Stream("feed", fluid);
      feed.run();
      assertTrue(!feed.getFluid().hasPhaseType("gas"));
      ThreePhaseSeparator separator = new ThreePhaseSeparator("separator", feed);
      if (entrainment) {
        separator.setEntrainment(0.01, "mole", "feed", "oil", "aqueous");
      }
      separator.run();
      assertEquals(0.0, separator.getGasOutStream().getFlowRate("kg/hr"), 1.0e-12);
      assertBalanced(feed, separator);
    }
  }

  /** Reused high-pressure liquid seeds must agree with a fresh flash after a P/T setter. */
  @Test
  void warmProcessWithChangingFeedRecoversThreePhases() {
    boolean previousWarmStart = ThermodynamicModelSettings.isUseWarmStartKValues();
    ThermodynamicModelSettings.setUseWarmStartKValues(true);
    try {
      Stream feed = new Stream("feed", createFluid(0.06, 1.0));
      Mixer mixer = new Mixer("mixer");
      mixer.addStream(feed);
      Heater heater = new Heater("TP setter", mixer.getOutletStream());
      heater.setOutletPressure(12.53, "barg");
      heater.setOutletTemperature(20.75, "C");
      ThreePhaseSeparator separator = new ThreePhaseSeparator("separator", heater.getOutletStream());
      ProcessSystem process = new ProcessSystem();
      process.add(feed);
      process.add(mixer);
      process.add(heater);
      process.add(separator);
      double[] methaneFractions = {0.06, 0.05, 0.06};
      double[] inletPressures = {300.0, 200.0, 13.54325};
      for (int point = 0; point < methaneFractions.length; point++) {
        feed.getFluid()
            .setMolarComposition(new double[] {methaneFractions[point], 0.10 - methaneFractions[point], 0.90});
        feed.setPressure(inletPressures[point], "bara");
        process.run();
        SystemInterface fresh = createFluid(methaneFractions[point], 1.0);
        new ThermodynamicOperations(fresh).TPflash();
        assertEquals(3, heater.getOutletStream().getFluid().getNumberOfPhases());
        for (String phase : new String[] {"gas", "oil", "aqueous"}) {
          assertEquals(fresh.getPhase(phase).getBeta(), heater.getOutletStream().getFluid().getPhase(phase).getBeta(),
              1.0e-7);
        }
        assertBalanced(feed, separator);
      }
    } finally {
      ThermodynamicModelSettings.setUseWarmStartKValues(previousWarmStart);
    }
  }

  /** Checks small and large feeds through the reported mixer/heater/separator topology. */
  @Test
  void heaterMatchesFreshFlashAtEveryFlowScale() {
    for (double flow : new double[] {1.0, 1.0e-6, 1.0e6}) {
      Stream feed = new Stream("feed", createFluid(0.06, flow));
      feed.run();
      Mixer mixer = new Mixer("mixer");
      mixer.addStream(feed);
      Heater heater = new Heater("TP setter", mixer.getOutletStream());
      heater.setOutletPressure(12.53, "barg");
      heater.setOutletTemperature(20.75, "C");
      ThreePhaseSeparator separator = new ThreePhaseSeparator("separator", heater.getOutletStream());
      for (int repeat = 0; repeat < 3; repeat++) {
        mixer.run();
        heater.run();
        SystemInterface fresh = createFluid(0.06, flow);
        new ThermodynamicOperations(fresh).TPflash();
        assertEquals(3, fresh.getNumberOfPhases());
        assertEquals(3, heater.getOutletStream().getFluid().getNumberOfPhases());
        for (String phase : new String[] {"gas", "oil", "aqueous"}) {
          assertEquals(fresh.getPhase(phase).getBeta(), heater.getOutletStream().getFluid().getPhase(phase).getBeta(),
              1.0e-7);
        }
        separator.run();
        assertBalanced(feed, separator);
      }
    }
  }

  /**
   * Builds a public PR78 fluid with water last, at the reported outlet conditions.
   *
   * @param methane methane mole fraction
   * @param flow mass flow in kg/hr
   * @return configured synthetic feed
   */
  private SystemInterface createFluid(double methane, double flow) {
    SystemInterface fluid = new SystemPrEos1978(293.90, 13.54325);
    fluid.addComponent("methane", methane);
    fluid.addComponent("nC10", 0.10 - methane);
    fluid.addComponent("water", 0.90);
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);
    fluid.setTotalFlowRate(flow, "kg/hr");
    return fluid;
  }

  /**
   * Checks component molar flows and total mass independently of getMassBalance's small-flow filtering.
   *
   * @param feed inlet stream
   * @param separator tested separator
   */
  private void assertBalanced(Stream feed, ThreePhaseSeparator separator) {
    StreamInterface[] outlets = {separator.getGasOutStream(), separator.getOilOutStream(),
        separator.getWaterOutStream()};
    double mass = 0.0;
    for (StreamInterface outlet : outlets) {
      double flow = outlet.getFlowRate("kg/hr");
      assertTrue(Double.isFinite(flow) && flow >= 0.0);
      mass += flow;
    }
    assertEquals(feed.getFlowRate("kg/hr"), mass, feed.getFlowRate("kg/hr") * 1.0e-8);
    for (int component = 0; component < feed.getFluid().getNumberOfComponents(); component++) {
      double expected = feed.getFluid().getComponent(component).getNumberOfmoles();
      double actual = 0.0;
      for (StreamInterface outlet : outlets) {
        actual += outlet.getFluid().getComponent(component).getNumberOfmoles();
      }
      assertEquals(expected, actual, Math.max(1.0e-30, Math.abs(expected) * 1.0e-8));
    }
  }
}
