package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Calculator;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.serialization.NeqSimXtream;

public class SafeSplineSurgeCurveTest {
  @Test
  public void testSurgeCurve10250() {
    double[] flow10250 = {9758.49, 9578.11, 9397.9, 9248.64, 9006.93, 8749.97, 8508.5, 8179.81,
        7799.81, 7111.75, 6480.26, 6007.91, 5607.45};

    double[] head10250 = {112.65, 121.13, 127.56, 132.13, 137.29, 140.73, 142.98, 144.76, 146.14,
        148.05, 148.83, 149.54, 150.0};

    // Initialize curve
    SafeSplineSurgeCurve curve = new SafeSplineSurgeCurve(flow10250, head10250);

    // Should be active
    assertTrue(curve.isActive());

    // Check interpolation within range
    double midFlow = 8000.0;
    double interpolatedHead = curve.getSurgeHead(midFlow);
    assertTrue(interpolatedHead > 0.0);

    // Check extrapolation below minimum
    double lowFlow = 5000.0;
    double extrapolatedHeadLow = curve.getSurgeHead(lowFlow);
    assertTrue(extrapolatedHeadLow >= 0.0);

    // Check extrapolation above maximum
    double highFlow = 10000.0;
    double extrapolatedHeadHigh = curve.getSurgeHead(highFlow);
    assertTrue(extrapolatedHeadHigh >= 0.0);

    // Sanity check: increasing flow should not increase head (head drops with more flow)
    assertTrue(curve.getSurgeHead(6000.0) > curve.getSurgeHead(9500.0));

    double surgeflow = curve.getSurgeFlow(130.0); // Test flow to head conversion
    assertEquals(9321.84315857, surgeflow, 1.0);

    surgeflow = curve.getSurgeFlow(190.0); // Test flow to head conversion
    assertEquals(0.0, surgeflow, 1.0);

    surgeflow = curve.getSurgeFlow(10.0); // Test flow to head conversion
    assertEquals(11941.98139, surgeflow, 1.0);

    surgeflow = curve.getSurgeFlow(130.0); // Test flow to head conversion
    assertEquals(9321.84315, surgeflow, 1.0);

    surgeflow = curve.getSurgeHead(9321.84315); // Test flow to head conversion
    assertEquals(130.0, surgeflow, 1.0);
  }

  @Test
  public void testSurgeCurve2() {
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

    ProcessSystem process1 = new ProcessSystem("Test process");

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

    Compressor firstStageCompressor =
        new Compressor("1st stage compressor", cooler.getOutletStream());
    firstStageCompressor.setUsePolytropicCalc(true);
    firstStageCompressor.setPolytropicEfficiency(0.8);
    firstStageCompressor.setOutletPressure(12.0, "bara");
    firstStageCompressor.getCompressorChart().setHeadUnit("kJ/kg");

    double[] flow10250 = {9758.49, 9578.11, 9397.9, 9248.64, 9006.93, 8749.97, 8508.5, 8179.81,
        7799.81, 7111.75, 6480.26, 6007.91, 5607.45};

    double[] head10250 = {112.65, 121.13, 127.56, 132.13, 137.29, 140.73, 142.98, 144.76, 146.14,
        148.05, 148.83, 149.54, 150};
    firstStageCompressor.getCompressorChart().getSurgeCurve().setCurve(null, flow10250, head10250);
    firstStageCompressor.run();
    process1.add(firstStageCompressor);

    Splitter splitter1 = process1.addUnit("1st stage anti surge splitter", "splitter");
    splitter1.setFlowRates(new double[] {-1, 1.0}, "kg/hr");
    splitter1.run();


    ThrottlingValve valve1 = process1.addUnit("1st stage anti surge valve", "valve");
    valve1.setInletStream(splitter1.getSplitStream(1));
    valve1.setOutletPressure(4.0, "bara");
    valve1.run();

    Calculator antisurgeCalculator = process1.addUnit("anti surge calculator", "calculator");
    antisurgeCalculator.addInputVariable(firstStageCompressor);
    antisurgeCalculator.setOutputVariable(splitter1);
    antisurgeCalculator.run();

    Recycle recycle1 = process1.addUnit("recycle 1", "recycle");
    recycle1.addStream(valve1.getOutletStream());
    recycle1.setOutletStream(resyclestream);
    recycle1.setTolerance(1e-3);
    recycle1.run();

    process1.run();

    // Check interpolation w ithin ran
    assertEquals(460.2791444, stream1.getFlowRate("m3/hr"), 2);
    assertEquals(124.687839, firstStageCompressor.getPolytropicFluidHead(), 0.1);
    assertEquals(9482.59928657, firstStageCompressor.getSurgeFlowRate(), 1);
    assertEquals(9482.599286, firstStageCompressor.getInletStream().getFlowRate("m3/hr"), 0.1);

    assertEquals(34710.50779, resyclestream.getFlowRate("kg/hr"), 2);
    assertEquals(37811.178897, firstStageCompressor.getInletStream().getFlowRate("kg/hr"), 2);
    assertEquals(8704.987415, resyclestream.getFlowRate("m3/hr"), 2);

    stream1.setFlowRate(39985.43, "kg/hr");
    process1.run();


    // Check interpolation within ran
    assertEquals(124.68783987, firstStageCompressor.getPolytropicFluidHead(), 0.1);
    assertEquals(10027.87590980, firstStageCompressor.getInletStream().getFlowRate("m3/hr"), 0.1);
    assertEquals(9482.599286570, firstStageCompressor.getSurgeFlowRate(), 1);
    assertEquals(0.0, resyclestream.getFlowRate("kg/hr"), 0.1);
    assertEquals(39985.43, firstStageCompressor.getInletStream().getFlowRate("kg/hr"), 1);

    stream1.setFlowRate(39.43, "kg/hr");
    process1.run();

    // Check interpolation within ran
    assertEquals(124.687839875, firstStageCompressor.getPolytropicFluidHead(), 0.1);
    // assertEquals(9024.72462749, firstStageCompressor.getInletStream().getFlowRate("m3/hr"), 0.1);
    assertEquals(9482.5992865704, firstStageCompressor.getSurgeFlowRate(), 1);
    assertEquals(37771.748897, resyclestream.getFlowRate("kg/hr"), 15);
    assertEquals(37811.178897, firstStageCompressor.getInletStream().getFlowRate("kg/hr"), 15);

    stream1.setFlowRate(39000.43, "kg/hr");
    process1.run();

    // Check interpolation within ran
    assertEquals(124.687839875, firstStageCompressor.getPolytropicFluidHead(), 0.1);
    assertEquals(9780.8494861, firstStageCompressor.getInletStream().getFlowRate("m3/hr"), 0.1);
    assertEquals(9482.59928657, firstStageCompressor.getSurgeFlowRate(), 1);
    assertEquals(0.04206591466, resyclestream.getFlowRate("kg/hr"), 0.1);
    assertEquals(39000.43000000, firstStageCompressor.getInletStream().getFlowRate("kg/hr"), 1);

    stream1.setFlowRate(390.43, "kg/hr");
    firstStageCompressor.setOutletPressure(10.0, "bara");

    process1.run();

    assertEquals(101.9356767734, firstStageCompressor.getPolytropicFluidHead(), 0.1);
    assertEquals(9986.3967952, firstStageCompressor.getInletStream().getFlowRate("m3/hr"), 0.1);
    assertEquals(9986.39679526, firstStageCompressor.getSurgeFlowRate(), 1);
    assertEquals(39429.60503, resyclestream.getFlowRate("kg/hr"), 0.1);
    assertEquals(39820.035030, firstStageCompressor.getInletStream().getFlowRate("kg/hr"), 1);

  }


  @Test
  public void testSurgeCurve22() {
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

    ProcessSystem process1 = new ProcessSystem("Test process");

    Stream stream1 = new neqsim.process.equipment.stream.Stream("Feed stream", testFluid);
    stream1.run();
    process1.add(stream1);

    Stream resyclestream =
        new neqsim.process.equipment.stream.Stream("recycle stream", stream1.clone());
    resyclestream.setFlowRate(100.0, "kg/hr");
    resyclestream.run();
    process1.add(resyclestream);

    Mixer mixer = new neqsim.process.equipment.mixer.Mixer("mixer");
    mixer.addStream(stream1);
    mixer.addStream(resyclestream);
    mixer.run();
    process1.add(mixer);

    Cooler cooler = new neqsim.process.equipment.heatexchanger.Cooler("cooler");
    cooler.setInletStream(mixer.getOutletStream());
    cooler.setOutTemperature(20.0, "C");
    cooler.run();
    process1.add(cooler);

    Compressor firstStageCompressor =
        new Compressor("1st stage compressor", cooler.getOutletStream());
    firstStageCompressor.setUsePolytropicCalc(true);
    firstStageCompressor.setPolytropicEfficiency(0.8);
    firstStageCompressor.setOutletPressure(12.0, "bara");
    firstStageCompressor.getCompressorChart().setHeadUnit("kJ/kg");

    double[] flow10250 = {9758.49, 9578.11, 9397.9, 9248.64, 9006.93, 8749.97, 8508.5, 8179.81,
        7799.81, 7111.75, 6480.26, 6007.91, 5607.45};

    double[] head10250 = {112.65, 121.13, 127.56, 132.13, 137.29, 140.73, 142.98, 144.76, 146.14,
        148.05, 148.83, 149.54, 150};
    firstStageCompressor.getCompressorChart().getSurgeCurve().setCurve(null, flow10250, head10250);
    firstStageCompressor.run();
    process1.add(firstStageCompressor);

    Splitter splitter1 = new neqsim.process.equipment.splitter.Splitter(
        "1st stage anti surge splitter", firstStageCompressor.getOutletStream());
    splitter1.setFlowRates(new double[] {-1, 1.0}, "kg/hr");
    splitter1.run();
    process1.add(splitter1);

    Calculator antisurgeCalculator =
        new neqsim.process.equipment.util.Calculator("anti surge calculator");
    antisurgeCalculator.addInputVariable(firstStageCompressor);
    antisurgeCalculator.setOutputVariable(splitter1);
    antisurgeCalculator.run();
    process1.add(antisurgeCalculator);


    ThrottlingValve valve1 =
        new neqsim.process.equipment.valve.ThrottlingValve("1st stage anti surge valve");
    valve1.setInletStream(splitter1.getSplitStream(1));
    valve1.setOutletPressure(4.0, "bara");
    valve1.run();
    process1.add(valve1);


    Recycle recycle1 = new neqsim.process.equipment.util.Recycle("recycle 1");
    recycle1.addStream(valve1.getOutletStream());
    recycle1.setOutletStream(resyclestream);
    recycle1.setTolerance(1e-6);
    recycle1.run();
    process1.add(recycle1);

    process1.run();
    // Check interpolation w ithin ran
    assertEquals(460.2791444, stream1.getFlowRate("m3/hr"), 2);
    assertEquals(124.687839, firstStageCompressor.getPolytropicFluidHead(), 0.1);
    assertEquals(9482.59928657, firstStageCompressor.getSurgeFlowRate(), 1);
    assertEquals(9482.599286, firstStageCompressor.getInletStream().getFlowRate("m3/hr"), 0.1);

    assertEquals(35083.7888978, resyclestream.getFlowRate("kg/hr"), 2);
    assertEquals(37811.1788978, firstStageCompressor.getInletStream().getFlowRate("kg/hr"), 2);
    // assertEquals(8798.6019285, resyclestream.getFlowRate("m3/hr"), 2);

    stream1.setFlowRate(39985.43, "kg/hr");
    process1.run();

    // Check interpolation within ran
    assertEquals(124.68783987, firstStageCompressor.getPolytropicFluidHead(), 0.1);
    assertEquals(10027.87590980, firstStageCompressor.getInletStream().getFlowRate("m3/hr"), 0.1);
    assertEquals(9482.599286570, firstStageCompressor.getSurgeFlowRate(), 1);
    assertEquals(0.0, resyclestream.getFlowRate("kg/hr"), 0.1);
    assertEquals(39985.43, firstStageCompressor.getInletStream().getFlowRate("kg/hr"), 1);

    stream1.setFlowRate(39.43, "kg/hr");
    process1.run();
    // Check interpolation within ran
    assertEquals(124.687839875, firstStageCompressor.getPolytropicFluidHead(), 0.1);
    assertEquals(9482.5992865, firstStageCompressor.getInletStream().getFlowRate("m3/hr"), 0.1);
    assertEquals(9482.5992865704, firstStageCompressor.getSurgeFlowRate(), 1);
    assertEquals(37771.7488978, resyclestream.getFlowRate("kg/hr"), 15);
    assertEquals(37811.17889784, firstStageCompressor.getInletStream().getFlowRate("kg/hr"), 15);

    stream1.setFlowRate(39000.43, "kg/hr");
    process1.run();

    // Check interpolation within ran
    assertEquals(124.687839875, firstStageCompressor.getPolytropicFluidHead(), 0.1);
    assertEquals(9780.8494861, firstStageCompressor.getInletStream().getFlowRate("m3/hr"), 0.1);
    assertEquals(9482.59928657, firstStageCompressor.getSurgeFlowRate(), 1);
    assertEquals(0.0, resyclestream.getFlowRate("kg/hr"), 0.1);
    assertEquals(39000.43000000, firstStageCompressor.getInletStream().getFlowRate("kg/hr"), 1);

    stream1.setFlowRate(390.43, "kg/hr");
    firstStageCompressor.setOutletPressure(10.0, "bara");

    process1.run();

    // firstStageCompressor.run();

    assertEquals(101.9356767734, firstStageCompressor.getPolytropicFluidHead(), 0.1);
    assertEquals(9986.3967952, firstStageCompressor.getInletStream().getFlowRate("m3/hr"), 0.1);
    assertEquals(9986.39679526, firstStageCompressor.getSurgeFlowRate(), 1);
    assertEquals(39429.6050303, resyclestream.getFlowRate("kg/hr"), 0.1);
    assertEquals(39820.03503030, firstStageCompressor.getInletStream().getFlowRate("kg/hr"), 1);

  }

  // @Test
  public void testSurgeCurve3() {
    try {
      ProcessModel processModel = (ProcessModel) NeqSimXtream.openNeqsim(
          "/workspaces/neqsim/src/test/java/neqsim/process/equipment/compressor/neqsim_model_base_2.neqsim");
      processModel.run();
      processModel.run();
      processModel.run();
      processModel.run();
      processModel.run();
      processModel.run();
    } catch (IOException e) {
      e.printStackTrace();
      assertTrue(false, "Failed to open neqsim model: " + e.getMessage());
    }
  }

}
