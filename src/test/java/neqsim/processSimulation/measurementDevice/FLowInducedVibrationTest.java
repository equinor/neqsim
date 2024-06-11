package neqsim.processSimulation.measurementDevice;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.pipeline.PipeBeggsAndBrills;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.util.FlowRateAdjuster;
import neqsim.processSimulation.processSystem.ProcessSystem;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class FLowInducedVibrationTest extends neqsim.NeqSimTest {
  static ProcessSystem process1;
  static FlowInducedVibrationAnalyser flowInducedVibrationAnalyser;
  static FlowInducedVibrationAnalyser flowInducedVibrationAnalyser2;
  static FlowInducedVibrationAnalyser flowInducedVibrationAnalyser3;
  static FlowInducedVibrationAnalyser flowInducedVibrationAnalyserFRMS;
  static FlowInducedVibrationAnalyser flowInducedVibrationAnalyserFRMS2;
  static FlowInducedVibrationAnalyser flowInducedVibrationAnalyserFRMS3;


  /**
   * @throws java.lang.Exception
   */
  @BeforeAll
  static void setUpBeforeClass() throws Exception {

  }

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
    testSystem.setTotalFlowRate(1000.0, "kg/hr");
    testSystem.setMultiPhaseCheck(true);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initPhysicalProperties();

    Stream stream_1 = new Stream("Stream1", testSystem);
    stream_1.setFlowRate(1000.0, "kg/hr");

    FlowRateAdjuster flowRateAdj = new FlowRateAdjuster("Flow rate adjuster");
    flowRateAdj.setAdjustedStream(stream_1);
    flowRateAdj.setAdjustedFlowRates(41886.7, 88700.0, 22000.0, "kg/hr");
    flowRateAdj.run();

    StreamInterface adjustedStream = flowRateAdj.getOutletStream();

    double adjGasMass = adjustedStream.getFluid().getPhase("gas").getFlowRate("kg/hr");
    double adjOilMass = adjustedStream.getFluid().getPhase("oil").getFlowRate("kg/hr");
    double adjWaterMass = adjustedStream.getFluid().getPhase("aqueous").getFlowRate("kg/hr");

    Assertions.assertEquals(adjGasMass, 41886.7, 0.05);
    Assertions.assertEquals(adjOilMass, 88700.0, 0.05);
    Assertions.assertEquals(adjWaterMass, 22000.0, 0.05);

    flowRateAdj.setAdjustedFlowRates(gas_flow_rate, oil_flow_rate, water_flow_rate, "Sm3/hr");
    flowRateAdj.run();

    StreamInterface adjustedStream2 = flowRateAdj.getOutletStream();
    double adjGasVol = adjustedStream2.getFluid().getPhase("gas").getFlowRate("Sm3/hr");
    double adjOilVol = adjustedStream2.getFluid().getPhase("oil").getFlowRate("m3/hr");
    double adjWaterVol = adjustedStream2.getFluid().getPhase("aqueous").getFlowRate("m3/hr");

    Assertions.assertEquals(adjGasVol, gas_flow_rate, 0.05);
    Assertions.assertEquals(adjOilVol, oil_flow_rate, 0.05);
    Assertions.assertEquals(adjWaterVol, water_flow_rate, 0.05);

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills(adjustedStream2);
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

    PipeBeggsAndBrills pipe2 = new PipeBeggsAndBrills(pipe.getOutStream());
    pipe2.setPipeWallRoughness(1e-6);
    pipe2.setLength(1);
    pipe2.setElevation(0.0);
    pipe2.setPipeSpecification(8.0, "ED202");
    pipe2.setNumberOfIncrements(10);

    flowInducedVibrationAnalyser2 =
        new FlowInducedVibrationAnalyser("Flow Induced Vibrations Analyzer 2", pipe2);
    flowInducedVibrationAnalyser2.setMethod("LOF");

    flowInducedVibrationAnalyserFRMS2 =
        new FlowInducedVibrationAnalyser("Flow Induced Vibrations Analyzer FRMS 2", pipe2);
    flowInducedVibrationAnalyserFRMS2.setMethod("FRMS");

    PipeBeggsAndBrills pipe3 = new PipeBeggsAndBrills(pipe2.getOutStream());
    pipe3.setPipeWallRoughness(1e-6);
    pipe3.setLength(25);
    pipe3.setElevation(0.0);
    pipe3.setPipeSpecification(16.0, "ED202");
    pipe3.setNumberOfIncrements(10);

    flowInducedVibrationAnalyser3 =
        new FlowInducedVibrationAnalyser("Flow Induced Vibrations Analyzer 3", pipe3);
    flowInducedVibrationAnalyser3.setMethod("LOF");

    flowInducedVibrationAnalyserFRMS3 =
        new FlowInducedVibrationAnalyser("Flow Induced Vibrations Analyzer FRMS 3", pipe3);
    flowInducedVibrationAnalyserFRMS3.setMethod("FRMS");


    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(stream_1);
    operations.add(pipe);
    operations.add(flowInducedVibrationAnalyser);
    operations.add(flowInducedVibrationAnalyserFRMS);
    operations.add(pipe2);
    operations.add(flowInducedVibrationAnalyser2);
    operations.add(flowInducedVibrationAnalyserFRMS2);
    operations.add(pipe3);
    operations.add(flowInducedVibrationAnalyser3);
    operations.add(flowInducedVibrationAnalyserFRMS3);
    operations.run();

    double LOF = ((FlowInducedVibrationAnalyser) operations
        .getMeasurementDevice("Flow Induced Vibrations Analyzer 1")).getMeasuredValue();
    Assertions.assertEquals(LOF, 0.161, 0.05);

    double LOF2 = ((FlowInducedVibrationAnalyser) operations
        .getMeasurementDevice("Flow Induced Vibrations Analyzer 2")).getMeasuredValue();
    Assertions.assertEquals(LOF2, 0.1545, 0.05);

    double LOF3 = ((FlowInducedVibrationAnalyser) operations
        .getMeasurementDevice("Flow Induced Vibrations Analyzer 3")).getMeasuredValue();
    Assertions.assertEquals(LOF3, 0.00585, 0.05);

    double FRMS = ((FlowInducedVibrationAnalyser) operations
        .getMeasurementDevice("Flow Induced Vibrations Analyzer FRMS")).getMeasuredValue();
    Assertions.assertEquals(FRMS, 176, 5);

    double FRMS2 = ((FlowInducedVibrationAnalyser) operations
        .getMeasurementDevice("Flow Induced Vibrations Analyzer FRMS 2")).getMeasuredValue();
    Assertions.assertEquals(FRMS2, 144, 5);

    double FRMS3 = ((FlowInducedVibrationAnalyser) operations
        .getMeasurementDevice("Flow Induced Vibrations Analyzer FRMS 3")).getMeasuredValue();
    Assertions.assertEquals(FRMS3, 87.74, 5);

  }


}
