package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests the dedicated {@link AntiSurgeCalculator} class. It must reproduce the proven anti-surge recycle behaviour of
 * the generic {@code Calculator("anti surge calculator")} mechanism while using an arbitrary (non-magic) unit name.
 */
public class AntiSurgeCalculatorTest {

  /**
   * Builds the same anti-surge loop used by the legacy name-based test, but wires the recycle branch through an
   * {@link AntiSurgeCalculator} carrying a neutral name. The compressor must stay pinned on its surge line at low
   * demand and the recycle must close at high demand.
   */
  @Test
  public void testDedicatedAntiSurgeCalculatorMatchesNameBased() {
    SystemInterface testFluid = new SystemSrkEos(298.15, 50.0);
    testFluid.addComponent("nitrogen", 1.205);
    testFluid.addComponent("CO2", 1.340);
    testFluid.addComponent("methane", 52.974);
    testFluid.addComponent("ethane", 15.258);
    testFluid.addComponent("propane", 13.283);
    testFluid.addComponent("i-butane", 0.082);
    testFluid.addComponent("n-butane", 0.487);
    testFluid.setMixingRule(2);
    testFluid.setTemperature(25.0, "C");
    testFluid.setPressure(6.0, "bara");
    testFluid.setTotalFlowRate(2727.390, "kg/hr");

    ProcessSystem process1 = new ProcessSystem("Anti-surge test process");

    Stream stream1 = process1.addUnit("Feed stream", "stream");
    stream1.setFluid(testFluid);
    stream1.run();

    Stream resyclestream = (Stream) process1.addUnit("recycle stream", stream1.clone());
    resyclestream.setFlowRate(100.0, "kg/hr");
    resyclestream.run();

    Mixer mixer = (Mixer) process1.addUnit("mixer", "mixer");
    mixer.addStream(stream1);
    mixer.addStream(resyclestream);
    mixer.run();

    Cooler cooler = (Cooler) process1.addUnit("cooler", "cooler");
    cooler.setInletStream(mixer.getOutletStream());
    cooler.setOutTemperature(20.0, "C");
    cooler.run();

    Compressor firstStageCompressor = new Compressor("1st stage compressor", cooler.getOutletStream());
    firstStageCompressor.setPolytropicMethod("detailed");
    firstStageCompressor.setUsePolytropicCalc(true);
    firstStageCompressor.setPolytropicEfficiency(0.8);
    firstStageCompressor.setOutletPressure(12.0, "bara");
    firstStageCompressor.getCompressorChart().setHeadUnit("kJ/kg");

    double[] flow10250 = { 9758.49, 9578.11, 9397.9, 9248.64, 9006.93, 8749.97, 8508.5, 8179.81, 7799.81, 7111.75,
        6480.26, 6007.91, 5607.45 };
    double[] head10250 = { 112.65, 121.13, 127.56, 132.13, 137.29, 140.73, 142.98, 144.76, 146.14, 148.05, 148.83,
        149.54, 150 };
    firstStageCompressor.getCompressorChart().getSurgeCurve().setCurve(null, flow10250, head10250);
    firstStageCompressor.run();
    process1.add(firstStageCompressor);

    Splitter splitter1 = process1.addUnit("anti surge splitter", "splitter");
    splitter1.setFlowRates(new double[] { -1, 1.0 }, "kg/hr");
    splitter1.run();

    // Dedicated class with a NEUTRAL name (no "anti surge calculator" prefix).
    AntiSurgeCalculator antisurgeCalculator = new AntiSurgeCalculator("AS-100", firstStageCompressor, splitter1);
    antisurgeCalculator.run();
    process1.add(antisurgeCalculator);

    ThrottlingValve valve1 = process1.addUnit("anti surge valve", "valve");
    valve1.setInletStream(splitter1.getSplitStream(1));
    valve1.setOutletPressure(4.0, "bara");
    valve1.run();

    Recycle recycle1 = process1.addUnit("recycle 1", "recycle");
    recycle1.addStream(valve1.getOutletStream());
    recycle1.setOutletStream(resyclestream);
    recycle1.setTolerance(1e-6);
    recycle1.run();

    // Low demand: recycle must open to keep the compressor inlet on the surge line.
    process1.run();
    assertEquals(9482.59928657, firstStageCompressor.getSurgeFlowRate(), 1);
    assertEquals(9482.599286, firstStageCompressor.getInletStream().getFlowRate("m3/hr"), 1.0);
    assertTrue(resyclestream.getFlowRate("kg/hr") > 100.0, "recycle should open at low demand");
    assertEquals(35083.7888978, resyclestream.getFlowRate("kg/hr"), 50.0);

    // High demand above surge: recycle must close to (effectively) zero.
    stream1.setFlowRate(39985.43, "kg/hr");
    process1.run();
    assertEquals(0.0, resyclestream.getFlowRate("kg/hr"), 1.0);
    assertEquals(39985.43, firstStageCompressor.getInletStream().getFlowRate("kg/hr"), 5.0);
  }
}
