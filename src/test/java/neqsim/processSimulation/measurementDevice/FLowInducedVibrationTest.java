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

  /**
   * @throws java.lang.Exception
   */
  @BeforeAll
  static void setUpBeforeClass() throws Exception {

  }

  @Test
  public void testSetUnit() {

    double pressure = 58.3 + 1.01325; // bara
    double temperature = 90; // C
    double massFlowRate = 1000.000000000;


    neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkEos(
        (273.15 + 45), ThermodynamicConstantsInterface.referencePressure);

    testSystem.addComponent("H2S", 0.3);
    testSystem.addComponent("nitrogen", 0.333);
    testSystem.addComponent("CO2", 7.215);
    testSystem.addComponent("methane", 35.5);
    testSystem.addComponent("ethane", 7.955);
    testSystem.addComponent("propane", 3.847);
    testSystem.addComponent("i-butane", 0.42);
    testSystem.addComponent("n-butane", 1.136);
    testSystem.addComponent("i-pentane", 0.281);
    testSystem.addComponent("n-pentane", 0.319);
    testSystem.addTBPfraction("C6", 0.402, 86.1800003051758 / 1000,
        86.1800003051758 / (1000 * 0.131586722637079));
    testSystem.addTBPfraction("C7", 0.178, 94.8470001220703 / 1000,
        94.8470001220703 / (1000 * 0.141086913827126));
    testSystem.addTBPfraction("C8", 0.082, 106.220001220703 / 1000,
        106.220001220703 / (1000 * 0.141086913827126));
    testSystem.addTBPfraction("C9", 0.019, 120.457000732422 / 1000,
        120.457000732422 / (1000 * 0.156630031108116));
    testSystem.addTBPfraction("C10_C11", 0.005, 140.369003295898 / 1000,
        140.369003295898 / (1000 * 0.178710051949529));
    testSystem.addTBPfraction("C12_C13", 0.005, 167.561996459961 / 1000,
        167.561996459961 / (1000 * 0.208334072812978));
    testSystem.addTBPfraction("C14_C15", 0.005, 197.501007080078 / 1000,
        197.501007080078 / (1000 * 0.240670271622303));
    testSystem.addTBPfraction("C16_C17", 0.005, 229.033996582031 / 1000,
        229.033996582031 / (1000 * 0.274302534479916));
    testSystem.addTBPfraction("C18_C20", 0.0005, 262.010986328125 / 1000,
        262.010986328125 / (1000 * 0.308134346902454));
    testSystem.addTBPfraction("C21_C23", 0.0005, 303.558990478516 / 1000,
        303.558990478516 / (1000 * 0.350224115520606));
    testSystem.addTBPfraction("C24_C28", 0.0005, 355.920013427734 / 1000,
        355.920013427734 / (1000 * 0.402198101307449));
    testSystem.addTBPfraction("C29_C35", 0.0005, 437.281005859375 / 1000,
        437.281005859375 / (1000 * 0.481715346021770));
    testSystem.addComponent("water", 50.0);
    testSystem.setMixingRule(2);
    testSystem.init(0);
    testSystem.useVolumeCorrection(true);
    testSystem.setPressure(pressure, "bara");
    testSystem.setTemperature(temperature, "C");
    testSystem.setTotalFlowRate(massFlowRate, "kg/hr");
    testSystem.setMultiPhaseCheck(true);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initPhysicalProperties();

    Stream stream_1 = new Stream("Stream1", testSystem);
    stream_1.setFlowRate(massFlowRate, "kg/hr");

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

    flowRateAdj.setAdjustedFlowRates(42487.5, 26.7, 22.2, "Sm3/hr");
    flowRateAdj.run();

    StreamInterface adjustedStream2 = flowRateAdj.getOutletStream();
    double adjGasVol = adjustedStream2.getFluid().getPhase("gas").getFlowRate("Sm3/hr");
    double adjOilVol = adjustedStream2.getFluid().getPhase("oil").getFlowRate("m3/hr");
    double adjWaterVol = adjustedStream2.getFluid().getPhase("aqueous").getFlowRate("m3/hr");

    Assertions.assertEquals(adjGasVol, 42487.5, 0.05);
    Assertions.assertEquals(adjOilVol, 26.7, 0.05);
    Assertions.assertEquals(adjWaterVol, 22.2, 0.05);

    System.out
        .println("Oil density " + adjustedStream2.getFluid().getPhase("oil").getDensity("kg/m3"));

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills(adjustedStream2);
    pipe.setPipeWallRoughness(0);
    pipe.setLength(40.0);
    pipe.setElevation(0);
    pipe.setDiameter(0.1985);
    pipe.setThickness(0.0103);
    pipe.setNumberOfIncrements(10);

    flowInducedVibrationAnalyser =
        new FlowInducedVibrationAnalyser("Flow Induced Vibrations Analyzer", pipe);
    flowInducedVibrationAnalyser.setMethod("LOF");


    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(stream_1);
    operations.add(pipe);
    operations.add(flowInducedVibrationAnalyser);
    operations.run();

    double LOF = ((FlowInducedVibrationAnalyser) operations
        .getMeasurementDevice("Flow Induced Vibrations Analyzer")).getMeasuredValue();
    Assertions.assertEquals(LOF, 0.072, 0.05);

  }


}
