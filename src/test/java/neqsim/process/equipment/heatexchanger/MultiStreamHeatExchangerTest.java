package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class MultiStreamHeatExchangerTest {
  private static class InitTrackingSystemSrkEos extends SystemSrkEos {
    private static final long serialVersionUID = 1000L;
    private AtomicInteger levelTwoCalls = new AtomicInteger();
    private AtomicInteger levelThreeCalls = new AtomicInteger();

    InitTrackingSystemSrkEos(double temperature, double pressure) {
      super(temperature, pressure);
    }

    @Override
    public InitTrackingSystemSrkEos clone() {
      InitTrackingSystemSrkEos cloned = (InitTrackingSystemSrkEos) super.clone();
      if (cloned == null) {
        throw new IllegalStateException("Failed to clone initialization-tracking fluid");
      }
      cloned.levelTwoCalls = levelTwoCalls;
      cloned.levelThreeCalls = levelThreeCalls;
      return cloned;
    }

    @Override
    public void init(int initType) {
      super.init(initType);
      if (levelTwoCalls != null && initType == 2) {
        levelTwoCalls.incrementAndGet();
      } else if (levelThreeCalls != null && initType == 3) {
        levelThreeCalls.incrementAndGet();
      }
    }

    void resetInitCounts() {
      levelTwoCalls.set(0);
      levelThreeCalls.set(0);
    }

    int getLevelTwoCalls() {
      return levelTwoCalls.get();
    }

    int getLevelThreeCalls() {
      return levelThreeCalls.get();
    }
  }

  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(MultiStreamHeatExchangerTest.class);

  static neqsim.thermo.system.SystemInterface testSystem;
  Stream gasStream;

  @BeforeEach
  void setUp() {
    testSystem = new neqsim.thermo.system.SystemSrkEos((273.15 + 60.0), 20.00);
    testSystem.addComponent("methane", 120.00);
    testSystem.addComponent("ethane", 120.0);
    testSystem.addComponent("n-heptane", 3.0);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
  }

  @Test
  void testRun1() {
    Stream stream_Hot = new Stream("Stream1", testSystem);
    stream_Hot.setTemperature(100.0, "C");
    stream_Hot.setFlowRate(1000.0, "kg/hr");

    Stream stream_Cold = new Stream("Stream2", testSystem.clone());
    stream_Cold.setTemperature(20.0, "C");
    stream_Cold.setFlowRate(310.0, "kg/hr");

    Stream stream_Cold2 = new Stream("Stream3", testSystem.clone());
    stream_Cold2.setTemperature(0.0, "C");
    stream_Cold2.setFlowRate(50.0, "kg/hr");

    MultiStreamHeatExchanger heatEx = new MultiStreamHeatExchanger("heatEx");
    heatEx.addInStream(stream_Hot);
    heatEx.addInStream(stream_Cold);
    heatEx.addInStream(stream_Cold2);
    // heatEx.setUAvalue(1000);
    heatEx.setTemperatureApproach(5);

    neqsim.process.processmodel.ProcessSystem operations = new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_Hot);
    operations.add(stream_Cold);
    operations.add(stream_Cold2);
    operations.add(heatEx);

    operations.run();

    assertEquals(95, heatEx.getOutStream(1).getTemperature("C"), 1e-3);
    assertEquals(95, heatEx.getOutStream(2).getTemperature("C"), 1e-3);
    assertEquals(70.5921794735, heatEx.getOutStream(0).getTemperature("C"), 1e-3);

    heatEx.setUAvalue(1000);

    operations.run();
    assertEquals(97.992627692, heatEx.getOutStream(1).getTemperature("C"), 1e-3);
    assertEquals(97.992627692, heatEx.getOutStream(2).getTemperature("C"), 1e-3);
    assertEquals(69.477801, heatEx.getOutStream(0).getTemperature("C"), 1e-3);
    assertEquals(1000, heatEx.getUAvalue(), 0.1);
  }

  @Test
  void testRun2() {
    neqsim.process.processmodel.ProcessSystem operations = new neqsim.process.processmodel.ProcessSystem();

    Stream feed_stream = new Stream("Stream1", testSystem);
    feed_stream.setTemperature(30.0, "C");
    feed_stream.setPressure(75.0, "bara");
    feed_stream.setFlowRate(1000.0, "kg/hr");
    feed_stream.run();
    operations.add(feed_stream);

    Separator separator = new Separator("sep 1", feed_stream);
    operations.add(separator);

    Stream stream_Cold = new Stream("Stream2", testSystem.clone());
    stream_Cold.setTemperature(-5.0, "C");
    stream_Cold.setPressure(50.0, "bara");
    stream_Cold.setFlowRate(310.0, "kg/hr");
    stream_Cold.run();
    operations.add(stream_Cold);

    Stream stream_Cold2 = new Stream("Stream3", testSystem.clone());
    stream_Cold2.setTemperature(-5.0, "C");
    stream_Cold2.setPressure(50.0, "bara");
    stream_Cold2.setFlowRate(50.0, "kg/hr");
    stream_Cold2.run();
    operations.add(stream_Cold2);

    MultiStreamHeatExchanger heatEx = new MultiStreamHeatExchanger("heatEx");
    heatEx.addInStream(separator.getGasOutStream());
    heatEx.addInStream(stream_Cold);
    heatEx.addInStream(stream_Cold2);
    // heatEx.setUAvalue(1000);
    heatEx.setTemperatureApproach(5);
    heatEx.run();
    operations.add(heatEx);

    Separator dewseparator = new Separator("sep 2", heatEx.getOutStream(0));
    dewseparator.run();
    operations.add(dewseparator);

    Expander expander = new Expander("expander", dewseparator.getGasOutStream());
    expander.setOutletPressure(50., "bara");
    expander.run();
    operations.add(expander);

    ThrottlingValve jt_valve = new ThrottlingValve("JT valve", dewseparator.getLiquidOutStream());
    jt_valve.setOutletPressure(50.0, "bara");
    jt_valve.run();
    operations.add(jt_valve);

    Separator separator2 = new Separator("sep 3", expander.getOutletStream());
    separator2.addStream(jt_valve.getOutletStream());
    separator2.run();
    operations.add(separator2);

    Recycle gas_expander_resycle = new neqsim.process.equipment.util.Recycle("gas recycl");
    gas_expander_resycle.addStream(separator2.getGasOutStream());
    gas_expander_resycle.setOutletStream(stream_Cold);
    gas_expander_resycle.setTolerance(1e-3);
    gas_expander_resycle.run();
    operations.add(gas_expander_resycle);

    Recycle liq_expander_resycle = new neqsim.process.equipment.util.Recycle("liq recycl");
    liq_expander_resycle.addStream(separator2.getLiquidOutStream());
    liq_expander_resycle.setOutletStream(stream_Cold2);
    liq_expander_resycle.setTolerance(1e-3);
    liq_expander_resycle.run();
    operations.add(liq_expander_resycle);

    operations.run();

    // separator2.getFluid().prettyPrint();
    // heatEx.getOutStream(0).getFluid().prettyPrint();

    double hotInletTemperature = separator.getGasOutStream().getTemperature("C");
    for (int coldStreamIndex = 1; coldStreamIndex <= 2; coldStreamIndex++) {
      double coldInletTemperature = heatEx.getInStream(coldStreamIndex).getTemperature("C");
      double coldOutletTemperature = heatEx.getOutStream(coldStreamIndex).getTemperature("C");
      double actualTemperatureApproach = hotInletTemperature - coldOutletTemperature;
      assertTrue(coldOutletTemperature > coldInletTemperature, "cold stream " + coldStreamIndex
          + " should be heated: inlet=" + coldInletTemperature + " C, outlet=" + coldOutletTemperature + " C");
      assertTrue(actualTemperatureApproach >= heatEx.getTemperatureApproach() - 1e-3,
          "cold stream " + coldStreamIndex + " must respect the specified minimum temperature approach: specified="
              + heatEx.getTemperatureApproach() + " C, actual=" + actualTemperatureApproach + " C");
    }

    heatEx.setUAvalue(5000);
    operations.run();

    assertEquals(-29.927013822102793, separator2.getFluid().getTemperature("C"), 2e-2);
    // Allow the small Java 8/Linux convergence variation while retaining a tight temperature check.
    assertEquals(14.151, heatEx.getOutStream(1).getTemperature("C"), 5e-3);

    double heatBalance = 0.0;
    double maxAbsDuty = 0.0;
    for (int i = 0; i < 3; i++) {
      double streamDuty = heatEx.getDuty(i);
      heatBalance += streamDuty;
      maxAbsDuty = Math.max(maxAbsDuty, Math.abs(streamDuty));
    }
    assertEquals(0.0, heatBalance / maxAbsDuty, 1e-3);

    heatEx.toJson();
  }

  /**
   * Entropy requires caloric properties but not level-3 composition derivatives. The diagnostic must preserve its value
   * and all stream states while using the minimum thermodynamic initialization level.
   */
  @Test
  void testEntropyProductionUsesMinimumThermodynamicInitializationLevel() {
    InitTrackingSystemSrkEos fluid = new InitTrackingSystemSrkEos(373.15, 40.0);
    fluid.addComponent("nitrogen", 0.02);
    fluid.addComponent("CO2", 0.03);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-heptane", 0.04);
    fluid.setMixingRule("classic");

    Stream hot = new Stream("tracked hot", fluid);
    hot.setTemperature(100.0, "C");
    hot.setFlowRate(1000.0, "kg/hr");
    Stream cold = new Stream("tracked cold", fluid.clone());
    cold.setTemperature(20.0, "C");
    cold.setFlowRate(310.0, "kg/hr");
    Stream coldest = new Stream("tracked coldest", fluid.clone());
    coldest.setTemperature(0.0, "C");
    coldest.setFlowRate(50.0, "kg/hr");

    MultiStreamHeatExchanger heatExchanger = new MultiStreamHeatExchanger("tracked exchanger");
    heatExchanger.addInStream(hot);
    heatExchanger.addInStream(cold);
    heatExchanger.addInStream(coldest);
    heatExchanger.setTemperatureApproach(5.0);

    ProcessSystem process = new ProcessSystem();
    process.add(hot);
    process.add(cold);
    process.add(coldest);
    process.add(heatExchanger);
    process.run();

    assertMinimumEntropyInitialization(fluid, heatExchanger);

    hot.setTemperature(105.0, "C");
    process.run();
    assertMinimumEntropyInitialization(fluid, heatExchanger);
  }

  private static void assertMinimumEntropyInitialization(InitTrackingSystemSrkEos fluid,
      MultiStreamHeatExchanger heatExchanger) {
    int streamCount = heatExchanger.getInletStreams().size();
    double[] inletEnthalpies = new double[streamCount];
    double[] outletEnthalpies = new double[streamCount];
    double[] inletFlows = new double[streamCount];
    double[] outletFlows = new double[streamCount];
    int[] inletPhases = new int[streamCount];
    int[] outletPhases = new int[streamCount];
    for (int index = 0; index < streamCount; index++) {
      inletEnthalpies[index] = heatExchanger.getInStream(index).getFluid().getEnthalpy();
      outletEnthalpies[index] = heatExchanger.getOutStream(index).getFluid().getEnthalpy();
      inletFlows[index] = heatExchanger.getInStream(index).getFlowRate("kg/hr");
      outletFlows[index] = heatExchanger.getOutStream(index).getFlowRate("kg/hr");
      inletPhases[index] = heatExchanger.getInStream(index).getFluid().getNumberOfPhases();
      outletPhases[index] = heatExchanger.getOutStream(index).getFluid().getNumberOfPhases();
    }

    double expectedEntropy = referenceEntropyProduction(heatExchanger, "J/K");
    fluid.resetInitCounts();
    double actualEntropy = heatExchanger.getEntropyProduction("J/K");
    int actualLevelTwoCalls = fluid.getLevelTwoCalls();
    int actualLevelThreeCalls = fluid.getLevelThreeCalls();

    assertEquals(expectedEntropy, actualEntropy, Math.max(1.0e-10, Math.abs(expectedEntropy) * 1.0e-12));
    assertTrue(actualLevelTwoCalls >= 2 * streamCount, "Every inlet and outlet still requires caloric initialization");
    assertEquals(0, actualLevelThreeCalls, "Entropy diagnostics must not calculate composition derivatives");

    double heatBalance = 0.0;
    double maxAbsDuty = 0.0;
    for (int index = 0; index < streamCount; index++) {
      assertEquals(inletEnthalpies[index], heatExchanger.getInStream(index).getFluid().getEnthalpy(),
          Math.max(1.0e-6, Math.abs(inletEnthalpies[index]) * 1.0e-12));
      assertEquals(outletEnthalpies[index], heatExchanger.getOutStream(index).getFluid().getEnthalpy(),
          Math.max(1.0e-6, Math.abs(outletEnthalpies[index]) * 1.0e-12));
      assertEquals(inletFlows[index], heatExchanger.getInStream(index).getFlowRate("kg/hr"), 1.0e-8);
      assertEquals(outletFlows[index], heatExchanger.getOutStream(index).getFlowRate("kg/hr"), 1.0e-8);
      assertEquals(inletFlows[index], outletFlows[index], 1.0e-8);
      assertEquals(inletPhases[index], heatExchanger.getInStream(index).getFluid().getNumberOfPhases());
      assertEquals(outletPhases[index], heatExchanger.getOutStream(index).getFluid().getNumberOfPhases());
      double streamDuty = heatExchanger.getDuty(index);
      heatBalance += streamDuty;
      maxAbsDuty = Math.max(maxAbsDuty, Math.abs(streamDuty));
    }
    assertEquals(0.0, heatBalance, Math.max(1.0e-6, maxAbsDuty * 1.0e-8));
  }

  private static double referenceEntropyProduction(MultiStreamHeatExchanger heatExchanger, String unit) {
    double entropy = 0.0;
    for (int index = 0; index < heatExchanger.getInletStreams().size(); index++) {
      UUID id = UUID.randomUUID();
      heatExchanger.getInStream(index).run(id);
      heatExchanger.getInStream(index).getFluid().init(3);
      heatExchanger.getOutStream(index).run(id);
      heatExchanger.getOutStream(index).getFluid().init(3);
      entropy += heatExchanger.getOutStream(index).getThermoSystem().getEntropy(unit)
          - heatExchanger.getInStream(index).getThermoSystem().getEntropy(unit);
    }

    int coldStream = heatExchanger.getInletStreams().size() - 1;
    return entropy + Math.abs(heatExchanger.getDuty()) * (1.0 / heatExchanger.getInStream(coldStream).getTemperature()
        - 1.0 / heatExchanger.getInStream(0).getTemperature());
  }

  /**
   * Every feed and product must be reachable through the generic stream-introspection API. Topology walks, DEXPI export
   * and the ProcessModel boundary-stream detection use these lists, so a missing outlet silently hides a cross-area
   * link and lets a plant report convergence while a downstream consumer is out of balance.
   */
  @Test
  void testAllStreamsAreExposedForTopology() {
    Stream stream1 = new Stream("Stream1", testSystem.clone());
    stream1.setTemperature(100.0, "C");
    stream1.setFlowRate(1000.0, "kg/hr");
    stream1.run();

    Stream stream2 = new Stream("Stream2", testSystem.clone());
    stream2.setTemperature(20.0, "C");
    stream2.setFlowRate(310.0, "kg/hr");
    stream2.run();

    Stream stream3 = new Stream("Stream3", testSystem.clone());
    stream3.setTemperature(0.0, "C");
    stream3.setFlowRate(200.0, "kg/hr");
    stream3.run();

    MultiStreamHeatExchanger heatEx = new MultiStreamHeatExchanger("heatEx");
    heatEx.addInStream(stream1);
    heatEx.addInStream(stream2);
    heatEx.addInStream(stream3);

    assertEquals(3, heatEx.getInletStreams().size());
    assertEquals(3, heatEx.getOutletStreams().size());
    for (int i = 0; i < 3; i++) {
      assertTrue(heatEx.getOutletStreams().contains(heatEx.getOutStream(i)),
          "outlet " + i + " must be discoverable through getOutletStreams()");
    }
    assertTrue(heatEx.getInletStreams().contains(stream3), "the third feed must be discoverable");
    assertEquals(heatEx.getOutStream(0), heatEx.getOutletStream());
  }
}
