package neqsim.process.measurementdevice;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.FlowRateAdjuster;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class FLowInducedVibrationTest extends neqsim.NeqSimTest {
  static ProcessSystem process1;
  static FlowInducedVibrationAnalyser flowInducedVibrationAnalyser;
  static FlowInducedVibrationAnalyser flowInducedVibrationAnalyserFRMS;

  @Test
  public void testSetUnit() {
    double pressure = 58.3 + 1.01325; // bara of the separator
    double temperature = 90; // temperature of the separator
    double gas_flow_rate = 54559.25; // Sm3/hr
    double oil_flow_rate = 50.66; // Sm3/hr
    double water_flow_rate = 22.0; // Sm3/hr

    neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkEos(
        (273.15 + 45), ThermodynamicConstantsInterface.referencePressure);

    testSystem.addComponent("H2S", 0.00016);
    testSystem.addComponent("nitrogen", 0.0032);
    testSystem.addComponent("CO2", 0.06539);
    testSystem.addComponent("methane", 0.5686);
    testSystem.addComponent("ethane", 0.073);
    testSystem.addComponent("propane", 0.04149);
    testSystem.addComponent("i-butane", 0.005189);
    testSystem.addComponent("n-butane", 0.015133);
    testSystem.addComponent("i-pentane", 0.004601);
    testSystem.addComponent("n-pentane", 0.006);
    testSystem.addTBPfraction("C6", 0.0077, 86.1800003051758 / 1000,
        86.1800003051758 / (1000 * 0.131586722637079));
    testSystem.addTBPfraction("C7", 0.0132, 94.8470001220703 / 1000,
        94.8470001220703 / (1000 * 0.141086913827126));
    testSystem.addTBPfraction("C8", 0.0138, 106.220001220703 / 1000,
        106.220001220703 / (1000 * 0.141086913827126));
    testSystem.addTBPfraction("C9", 0.009357, 120.457000732422 / 1000,
        120.457000732422 / (1000 * 0.156630031108116));
    testSystem.addTBPfraction("C10_C11", 0.0062, 140.369003295898 / 1000,
        140.369003295898 / (1000 * 0.178710051949529));
    testSystem.addTBPfraction("C12_C13", 0.0089, 167.561996459961 / 1000,
        167.561996459961 / (1000 * 0.208334072812978));
    testSystem.addTBPfraction("C14_C15", 0.0069, 197.501007080078 / 1000,
        197.501007080078 / (1000 * 0.240670271622303));
    testSystem.addTBPfraction("C16_C17", 0.0053, 229.033996582031 / 1000,
        229.033996582031 / (1000 * 0.274302534479916));
    testSystem.addTBPfraction("C18_C20", 0.0047, 262.010986328125 / 1000,
        262.010986328125 / (1000 * 0.308134346902454));
    testSystem.addTBPfraction("C21_C23", 0.004295, 303.558990478516 / 1000,
        303.558990478516 / (1000 * 0.350224115520606));
    testSystem.addTBPfraction("C24_C28", 0.003374, 355.920013427734 / 1000,
        355.920013427734 / (1000 * 0.402198101307449));
    testSystem.addTBPfraction("C29_C35", 0.005, 437.281005859375 / 1000,
        437.281005859375 / (1000 * 0.481715346021770));
    testSystem.addComponent("water", 0.127294);
    testSystem.setMixingRule(2);
    testSystem.init(0);
    testSystem.useVolumeCorrection(true);
    testSystem.setPressure(pressure, "bara");
    testSystem.setTemperature(temperature, "C");
    testSystem.setTotalFlowRate(100.0, "kg/hr");
    testSystem.setMultiPhaseCheck(true);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initPhysicalProperties();

    Stream stream_1 = new Stream("Stream1", testSystem);
    stream_1.setFlowRate(100.0, "kg/hr");

    FlowRateAdjuster flowRateAdj = new FlowRateAdjuster("Flow rate adjuster", stream_1);
    flowRateAdj.setAdjustedFlowRates(gas_flow_rate, oil_flow_rate, water_flow_rate, "Sm3/hr");

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("pipe1 ", flowRateAdj.getOutletStream());
    pipe.setPipeWallRoughness(1e-6);
    pipe.setLength(25);
    pipe.setElevation(0.0);
    pipe.setPipeSpecification(8.0, "LD201");
    pipe.setNumberOfIncrements(10);

    flowInducedVibrationAnalyser =
        new FlowInducedVibrationAnalyser("Flow Induced Vibrations Analyzer 1", pipe);
    flowInducedVibrationAnalyser.setMethod("LOF");

    flowInducedVibrationAnalyserFRMS =
        new FlowInducedVibrationAnalyser("Flow Induced Vibrations Analyzer FRMS", pipe);
    flowInducedVibrationAnalyserFRMS.setMethod("FRMS");
    pipe.getOutletStream();

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_1);
    operations.add(flowRateAdj);
    operations.add(pipe);
    operations.add(flowInducedVibrationAnalyser);
    operations.add(flowInducedVibrationAnalyserFRMS);
    operations.run();

    double LOF = ((FlowInducedVibrationAnalyser) operations
        .getMeasurementDevice("Flow Induced Vibrations Analyzer 1")).getMeasuredValue();
    Assertions.assertEquals(0.161, LOF, 0.05);

    double FRMS = ((FlowInducedVibrationAnalyser) operations
        .getMeasurementDevice("Flow Induced Vibrations Analyzer FRMS")).getMeasuredValue();
    Assertions.assertEquals(176, FRMS, 5);
  }
}
