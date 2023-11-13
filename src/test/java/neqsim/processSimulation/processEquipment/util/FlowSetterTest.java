package neqsim.processSimulation.processEquipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.measurementDevice.MultiPhaseMeter;
import neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class FlowSetterTest {
  @Test
  void testMain() {
    SystemInterface testFluid = new SystemSrkEos(338.15, 50.0);
    testFluid.addComponent("nitrogen", 1.205);
    testFluid.addComponent("CO2", 1.340);
    testFluid.addComponent("methane", 87.974);
    testFluid.addComponent("ethane", 5.258);
    testFluid.addComponent("propane", 3.283);
    testFluid.addComponent("i-butane", 0.082);
    testFluid.addComponent("n-butane", 0.487);
    testFluid.addComponent("i-pentane", 0.056);
    testFluid.addComponent("n-pentane", 1.053);
    testFluid.addComponent("nC10", 4.053);
    testFluid.addComponent("water", 10.00);
    testFluid.setMixingRule(2);
    testFluid.setMultiPhaseCheck(true);

    Stream stream_1 = new Stream("Stream1", testFluid);
    stream_1.run();

    MultiPhaseMeter multiPhaseMeter = new MultiPhaseMeter("test", stream_1);
    multiPhaseMeter.setTemperature(15.0, "C");
    multiPhaseMeter.setPressure(1.01325, "bara");

    FlowSetter flowset = new FlowSetter("flowset", stream_1);
    flowset.setTemperature(15.0, "C");
    flowset.setPressure(1.01325, "bara");
    flowset.setGasFlowRate(multiPhaseMeter.getMeasuredValue("Gas Flow Rate", "Sm3/hr"), "Sm3/hr");
    flowset.setOilFlowRate(multiPhaseMeter.getMeasuredValue("Oil Flow Rate", "m3/hr"), "m3/hr");
    flowset.setWaterFlowRate(multiPhaseMeter.getMeasuredValue("Water Flow Rate", "m3/hr"), "m3/hr");
    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(stream_1);
    operations.add(multiPhaseMeter);
    operations.add(flowset);
    operations.run();

    assertEquals(flowset.getOutletStream().getFlowRate("kg/sec"), stream_1.getFlowRate("kg/sec"),
        1.0);

    flowset.getOutletStream().getFluid().prettyPrint();
  }

  @Test
  void testProcessWithFlowSetter() {

    double gasFlow = 10.0; // MSm3/day
    double oilFlow = 2000.0; // m3/hr
    double waterFLow = 1000.0; // m3/hr
    SystemInterface testFluid = new SystemSrkEos(338.15, 50.0);
    testFluid.addComponent("nitrogen", 1.205);
    testFluid.addComponent("CO2", 1.340);
    testFluid.addComponent("methane", 87.974);
    testFluid.addComponent("ethane", 5.258);
    testFluid.addComponent("propane", 3.283);
    testFluid.addComponent("i-butane", 0.082);
    testFluid.addComponent("n-butane", 0.487);
    testFluid.addComponent("i-pentane", 0.056);
    testFluid.addComponent("n-pentane", 1.053);
    testFluid.addComponent("nC10", 4.053);
    testFluid.addComponent("water", 10.00);
    testFluid.setMixingRule(2);
    testFluid.setMultiPhaseCheck(true);

    Stream stream_1 = new Stream("Stream1", testFluid);
    stream_1.run();
    stream_1.setPressure(10.0, "bara");
    stream_1.setTemperature(80.0, "C");

    FlowSetter flowset = new FlowSetter("flowset", stream_1);
    flowset.setTemperature(15.0, "C");
    flowset.setPressure(1.01325, "bara");
    flowset.setGasFlowRate(gasFlow, "MSm3/day");
    flowset.setOilFlowRate(oilFlow, "m3/hr");
    flowset.setWaterFlowRate(waterFLow, "m3/hr");

    StreamInterface feedStream = flowset.getOutletStream();

    ThreePhaseSeparator separator = new ThreePhaseSeparator(feedStream);

    StreamInterface gasFromSepStream = separator.getGasOutStream();


    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(stream_1);
    operations.add(flowset);
    operations.add(separator);
    operations.run();

    feedStream.getThermoSystem().prettyPrint();
    assertEquals(gasFlow, gasFromSepStream.getFlowRate("MSm3/day"), 1.0);
  }
}
