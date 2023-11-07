package neqsim.processSimulation.processEquipment.util;

import org.junit.jupiter.api.Test;
import neqsim.processSimulation.measurementDevice.MultiPhaseMeter;
import neqsim.processSimulation.processEquipment.stream.Stream;
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
    testFluid.setMixingRule(2);
    testFluid.setMultiPhaseCheck(true);

    testFluid.setTemperature(90.0, "C");
    testFluid.setPressure(60.0, "bara");
    testFluid.setTotalFlowRate(1e6, "kg/hr");

    Stream stream_1 = new Stream("Stream1", testFluid);
    stream_1.run();

    MultiPhaseMeter multiPhaseMeter = new MultiPhaseMeter("test", stream_1);
    multiPhaseMeter.setTemperature(90.0, "C");
    multiPhaseMeter.setPressure(60.0, "bara");

    FlowSetter flowset = new FlowSetter("flowset", stream_1);
    flowset.setGasFlowRate(multiPhaseMeter.getMeasuredValue("Gas Flow Rate", "Sm3/hr"), "Sm3/hr");
    flowset.setOilFlowRate(multiPhaseMeter.getMeasuredValue("Oil Flow Rate", "Sm3/hr"), "Sm3/hr");

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(stream_1);
    operations.add(multiPhaseMeter);
    operations.add(flowset);
    operations.run();
  }
}
