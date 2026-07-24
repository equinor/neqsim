package neqsim.process.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.mixer.StaticMixer;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Executable regression coverage for the mixers and splitters documentation.
 */
class MixerSplitterDocumentationTest {
  private static Stream createGasStream(String name, double temperatureK, double pressureBara, double flowKgPerHour,
      double[] composition) {
    SystemSrkEos fluid = new SystemSrkEos(temperatureK, pressureBara);
    fluid.addComponent("methane", composition[0]);
    fluid.addComponent("ethane", composition[1]);
    fluid.addComponent("propane", composition[2]);
    fluid.setMixingRule("classic");

    Stream stream = new Stream(name, fluid);
    stream.setFlowRate(flowKgPerHour, "kg/hr");
    stream.run();
    return stream;
  }

  @Test
  void mixerExampleClosesBalancesAndReportsPressureMismatch() {
    Stream richGas = createGasStream("rich gas", 300.0, 30.0, 5000.0, new double[] { 0.80, 0.15, 0.05 });
    Stream leanGas = createGasStream("lean gas", 310.0, 32.0, 3000.0, new double[] { 0.95, 0.04, 0.01 });
    double inletEnthalpyJ = richGas.getFluid().getEnthalpy("J") + leanGas.getFluid().getEnthalpy("J");
    double richMolarFlow = richGas.getFlowRate("mole/sec");
    double leanMolarFlow = leanGas.getFlowRate("mole/sec");
    double expectedMethaneFraction = (richMolarFlow * richGas.getFluid().getMolarComposition()[0]
        + leanMolarFlow * leanGas.getFluid().getMolarComposition()[0]) / (richMolarFlow + leanMolarFlow);
    double enthalpyToleranceJ = Math.max(1.0e-3, Math.abs(inletEnthalpyJ) * 1.0e-8);

    Mixer mixer = new Mixer("M-100");
    mixer.addStream(richGas);
    mixer.addStream(leanGas);
    mixer.setPressureMismatchTolerance(0.5);
    mixer.run();

    StreamInterface mixedGas = mixer.getOutletStream();
    assertEquals(8000.0, mixedGas.getFlowRate("kg/hr"), 1.0e-6);
    assertEquals(inletEnthalpyJ, mixedGas.getFluid().getEnthalpy("J"), enthalpyToleranceJ);
    assertEquals(expectedMethaneFraction, mixedGas.getFluid().getMolarComposition()[0], 1.0e-10);
    assertEquals(30.0, mixedGas.getPressure("bara"), 1.0e-6);
    assertTrue(mixer.isPressureMismatch());
    assertEquals(2.0, mixer.getInletPressureSpread(), 1.0e-6);
    assertEquals(30.0, mixer.getMinInletPressure(), 1.0e-6);
    assertEquals(32.0, mixer.getMaxInletPressure(), 1.0e-6);
  }

  @Test
  void specifiedTemperatureAndStaticMixerExamplesUseCurrentApis() {
    Stream richGas = createGasStream("rich gas", 300.0, 30.0, 5000.0, new double[] { 0.80, 0.15, 0.05 });
    Stream leanGas = createGasStream("lean gas", 310.0, 30.0, 3000.0, new double[] { 0.95, 0.04, 0.01 });

    Mixer specifiedTemperatureMixer = new Mixer("specified-temperature mixer");
    specifiedTemperatureMixer.addStream(richGas);
    specifiedTemperatureMixer.addStream(leanGas);
    specifiedTemperatureMixer.setOutletTemperature(305.15);
    specifiedTemperatureMixer.run();
    assertEquals(305.15, specifiedTemperatureMixer.getOutletStream().getTemperature("K"), 1.0e-6);

    StaticMixer staticMixer = new StaticMixer("MX-101");
    staticMixer.addStream(richGas);
    staticMixer.addStream(leanGas);
    staticMixer.run();
    StreamInterface staticMixerOutlet = staticMixer.getOutletStream();
    assertEquals(8000.0, staticMixerOutlet.getFlowRate("kg/hr"), 1.0e-6);
  }

  @Test
  void splitterExamplesNormalizeFactorsAndConserveSpecifiedFlows() {
    Stream inlet = createGasStream("splitter inlet", 305.0, 30.0, 8000.0, new double[] { 0.90, 0.08, 0.02 });

    Splitter ratioSplitter = new Splitter("SP-100", inlet, 2);
    ratioSplitter.setSplitFactors(new double[] { 7.0, 3.0 });
    ratioSplitter.run();

    StreamInterface product = ratioSplitter.getSplitStream(0);
    StreamInterface recycle = ratioSplitter.getSplitStream(1);
    assertEquals(5600.0, product.getFlowRate("kg/hr"), 1.0e-3);
    assertEquals(2400.0, recycle.getFlowRate("kg/hr"), 1.0e-3);
    assertEquals(0.0, ratioSplitter.getMassBalance("kg/hr"), 1.0e-3);

    Splitter flowSplitter = new Splitter("distribution splitter", inlet, 2);
    flowSplitter.setFlowRates(new double[] { 2500.0, Splitter.REMAINDER }, "kg/hr");
    flowSplitter.run();

    StreamInterface fixedDemand = flowSplitter.getSplitStream(0);
    StreamInterface remainingFlow = flowSplitter.getSplitStream(1);
    assertEquals(2500.0, fixedDemand.getFlowRate("kg/hr"), 1.0e-3);
    assertEquals(5500.0, remainingFlow.getFlowRate("kg/hr"), 1.0e-3);
    assertEquals(0.0, flowSplitter.getMassBalance("kg/hr"), 1.0e-3);
  }
}
