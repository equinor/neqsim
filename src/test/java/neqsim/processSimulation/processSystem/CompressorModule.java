package neqsim.processSimulation.processSystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.compressor.CompressorChart;
import neqsim.processSimulation.processEquipment.heatExchanger.Cooler;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for running compressor module calculation.
 */
public class CompressorModule extends neqsim.NeqSimTest {
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

    StreamInterface resycleScrubberStream = feedStream.clone();
    resycleScrubberStream.setFlowRate(1.0, "kg/hr");

    ThreePhaseSeparator secondStageSeparator =
        new ThreePhaseSeparator("inlet separator", valve1.getOutletStream());
    secondStageSeparator.addStream(resycleScrubberStream);


    // Setting up compressor module
    Compressor seccondStageCompressor =
        new Compressor("2nd stage compressor", secondStageSeparator.getGasOutStream());
    seccondStageCompressor.setUsePolytropicCalc(true);
    seccondStageCompressor.setPolytropicEfficiency(0.9);
    seccondStageCompressor.setOutletPressure(26.0, "bara");

    Cooler afterCooler =
        new Cooler("2nd stage after cooler", seccondStageCompressor.getOutletStream());
    afterCooler.setOutTemperature(25.0, "C");

    Separator scrubber1 = new Separator("after cooler scrubber", afterCooler.getOutletStream());

    Recycle recycle1 = new Recycle("recycle 1");
    recycle1.addStream(scrubber1.getLiquidOutStream());
    recycle1.setOutletStream(resycleScrubberStream);

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(feedStream);
    operations.add(inletSeparator);
    operations.add(oilHeater);
    operations.add(valve1);
    operations.add(resycleScrubberStream);
    operations.add(secondStageSeparator);
    operations.add(seccondStageCompressor);
    operations.add(afterCooler);
    operations.add(scrubber1);
    operations.add(recycle1);

    operations.run();

    System.out.println("secondStageSeparator temperature "
        + secondStageSeparator.getLiquidOutStream().getTemperature("C") + " C");
    System.out.println("flow recycle " + resycleScrubberStream.getFlowRate("kg/hr") + " kg/hr");
    System.out.println("flow compressor "
        + seccondStageCompressor.getOutletStream().getFlowRate("MSm3/day") + " MSm3/day");

    assertEquals(2053.083, resycleScrubberStream.getFlowRate("kg/hr"), 0.1);


    neqsim.processSimulation.processEquipment.compressor.CompressorChartGenerator compchartgenerator =
        new neqsim.processSimulation.processEquipment.compressor.CompressorChartGenerator(
            seccondStageCompressor);
    CompressorChart compChart1 = compchartgenerator.generateCompressorChart("mid range");

    seccondStageCompressor.setCompressorChart(compChart1);
    seccondStageCompressor.getCompressorChart().setUseCompressorChart(true);
    operations.run();
    System.out.println("pressure compressor "
        + seccondStageCompressor.getOutletStream().getPressure("bara") + " bara");

    assertEquals(26.0, seccondStageCompressor.getOutletStream().getPressure("bara"), 0.1);

  }
}
