package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for running compressor module calculation.
 */
public class CompressorModuleTest extends neqsim.NeqSimTest {
  // Test methods

  @Test
  public void testProcess() {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.addComponent("water", 51.0);
    thermoSystem.addComponent("nitrogen", 51.0);
    thermoSystem.addComponent("CO2", 51.0);
    thermoSystem.addComponent("methane", 51.0);
    thermoSystem.addComponent("ethane", 51.0);
    thermoSystem.addComponent("propane", 51.0);
    thermoSystem.addComponent("i-butane", 51.0);
    thermoSystem.addComponent("n-butane", 51.0);
    thermoSystem.addComponent("iC5", 51.0);
    thermoSystem.addComponent("nC5", 1.0);

    thermoSystem.addTBPfraction("C6", 1.0, 86.0 / 1000.0, 0.66);
    thermoSystem.addTBPfraction("C7", 1.0, 91.0 / 1000.0, 0.74);
    thermoSystem.addTBPfraction("C8", 1.0, 103.0 / 1000.0, 0.77);
    thermoSystem.addTBPfraction("C9", 1.0, 117.0 / 1000.0, 0.79);
    thermoSystem.addPlusFraction("C10_C12", 1.0, 145.0 / 1000.0, 0.80);
    thermoSystem.addPlusFraction("C13_C14", 1.0, 181.0 / 1000.0, 0.8279);
    thermoSystem.addPlusFraction("C15_C16", 1.0, 212.0 / 1000.0, 0.837);
    thermoSystem.addPlusFraction("C17_C19", 1.0, 248.0 / 1000.0, 0.849);
    thermoSystem.addPlusFraction("C20_C22", 1.0, 289.0 / 1000.0, 0.863);
    thermoSystem.addPlusFraction("C23_C25", 1.0, 330.0 / 1000.0, 0.875);
    thermoSystem.addPlusFraction("C26_C30", 1.0, 387.0 / 1000.0, 0.88);
    thermoSystem.addPlusFraction("C31_C38", 1.0, 471.0 / 1000.0, 0.90);
    thermoSystem.addPlusFraction("C38_C80", 1.0, 662.0 / 1000.0, 0.92);
    thermoSystem.setMixingRule("classic");
    thermoSystem.setMultiPhaseCheck(true);
    thermoSystem.setMolarComposition(new double[] {0.034266, 0.005269, 0.039189, 0.700553, 0.091154,
        0.050908, 0.007751, 0.014665, 0.004249, 0.004878, 0.004541, 0.007189, 0.006904, 0.004355,
        0.007658, 0.003861, 0.003301, 0.002624, 0.001857, 0.001320, 0.001426, 0.001164, 0.000916});

    Stream feedStream = new Stream("feed stream", thermoSystem);
    feedStream.setFlowRate(604094, "kg/hr");
    feedStream.setTemperature(25.5, "C");
    feedStream.setPressure(26.0, "bara");

    Separator inletSeparator = new Separator("inlet separator", feedStream);

    Heater oilHeater = new Heater("oil heater", inletSeparator.getLiquidOutStream());
    oilHeater.setOutTemperature(60.0, "C");

    ThrottlingValve valve1 = new ThrottlingValve("valve oil", oilHeater.getOutletStream());
    valve1.setOutletPressure(10.0, "bara");

    StreamInterface recycleScrubberStream = feedStream.clone("Recycle stream");
    recycleScrubberStream.setFlowRate(1.0, "kg/hr");

    ThreePhaseSeparator secondStageSeparator =
        new ThreePhaseSeparator("2nd stage separator", valve1.getOutletStream());
    secondStageSeparator.addStream(recycleScrubberStream);

    StreamInterface gasRecycleStream = feedStream.clone("gas recycle stream");
    gasRecycleStream.setFlowRate(1.0, "kg/hr");

    Mixer gasmixer = new Mixer("gas recycle mixer");
    gasmixer.addStream(secondStageSeparator.getGasOutStream());
    gasmixer.addStream(gasRecycleStream);

    // Setting up compressor module
    Compressor seccondStageCompressor =
        new Compressor("2nd stage compressor", gasmixer.getOutletStream());
    seccondStageCompressor.setUsePolytropicCalc(true);
    seccondStageCompressor.setPolytropicEfficiency(0.9);
    seccondStageCompressor.setOutletPressure(26.0, "bara");

    Cooler afterCooler =
        new Cooler("2nd stage after cooler", seccondStageCompressor.getOutletStream());
    afterCooler.setOutTemperature(25.0, "C");

    Separator scrubber1 = new Separator("after cooler scrubber", afterCooler.getOutletStream());

    Stream gasFromScrubber = new Stream("gas from scrubber", scrubber1.getGasOutStream());

    Splitter gassplitter = new Splitter("gas splitter", gasFromScrubber);
    gassplitter.setSplitFactors(new double[] {0.1, 0.9});

    ThrottlingValve recycleValve =
        new ThrottlingValve("antisurge valve", gassplitter.getSplitStream(0));
    recycleValve.setOutletPressure(10.0, "bara");

    Recycle recycle2 = new Recycle("recycle 2");
    recycle2.addStream(recycleValve.getOutletStream());
    recycle2.setOutletStream(gasRecycleStream);

    Recycle recycle1 = new Recycle("recycle 1");
    recycle1.addStream(scrubber1.getLiquidOutStream());
    recycle1.setOutletStream(recycleScrubberStream);

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(feedStream);
    operations.add(inletSeparator);
    operations.add(oilHeater);
    operations.add(valve1);
    operations.add(recycleScrubberStream);
    operations.add(secondStageSeparator);
    operations.add(gasRecycleStream);
    operations.add(gasmixer);
    operations.add(seccondStageCompressor);
    operations.add(afterCooler);
    operations.add(scrubber1);
    operations.add(gasFromScrubber);
    operations.add(gassplitter);
    operations.add(recycleValve);
    operations.add(recycle2);
    operations.add(recycle1);

    operations.run();

    assertEquals(2046.0527989, recycleScrubberStream.getFlowRate("kg/hr"), 0.1);

    neqsim.process.equipment.compressor.CompressorChartGenerator compchartgenerator =
        new neqsim.process.equipment.compressor.CompressorChartGenerator(seccondStageCompressor);
    compchartgenerator.generateCompressorChart("mid range");

    seccondStageCompressor.setCompressorChart(compchartgenerator.generateCompressorChart("normal"));
    seccondStageCompressor.getCompressorChart().setUseCompressorChart(true);
    operations.run();

    assertEquals(25.86689, seccondStageCompressor.getOutletStream().getPressure("bara"), 0.5);

    seccondStageCompressor.setSpeed(seccondStageCompressor.getSpeed() + 500);
    operations.run();

    assertEquals(40.5019771, seccondStageCompressor.getOutletStream().getPressure("bara"), 0.5);

    feedStream.setFlowRate(204094, "kg/hr");
    operations.run();


    assertTrue(seccondStageCompressor.isSurge(seccondStageCompressor.getPolytropicFluidHead(),
        seccondStageCompressor.getInletStream().getFlowRate("m3/hr")));


    double pressurespeedclac = seccondStageCompressor.getOutletPressure();
    double speedcomp = seccondStageCompressor.getSpeed();

    seccondStageCompressor.setSolveSpeed(true);
    operations.run();

    assertEquals(pressurespeedclac, seccondStageCompressor.getOutletStream().getPressure("bara"),
        0.5);
    assertEquals(speedcomp, seccondStageCompressor.getSpeed(), 0.5);
    assertEquals(261.9994710, seccondStageCompressor.getInletStream().getFlowRate("m3/hr"), 5.2);

    feedStream.setFlowRate(304094, "kg/hr");
    operations.run();

    assertEquals(pressurespeedclac, seccondStageCompressor.getOutletStream().getPressure("bara"),
        0.5);
    assertEquals(3526, seccondStageCompressor.getSpeed(), 10);
    assertEquals(389.69982, seccondStageCompressor.getInletStream().getFlowRate("m3/hr"), 0.2);
    assertTrue(seccondStageCompressor.isSurge(seccondStageCompressor.getPolytropicFluidHead(),
        seccondStageCompressor.getInletStream().getFlowRate("m3/hr")));

    feedStream.setFlowRate(704094, "kg/hr");
    operations.run();

    assertEquals(pressurespeedclac, seccondStageCompressor.getOutletStream().getPressure("bara"),
        0.5);
    assertEquals(4191.3866577, seccondStageCompressor.getSpeed(), 10);
    assertFalse(seccondStageCompressor.isSurge(seccondStageCompressor.getPolytropicFluidHead(),
        seccondStageCompressor.getInletStream().getFlowRate("m3/hr")));
  }
}
