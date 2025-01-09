package neqsim.process.measurementdevice;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class MultiPhaseMeterTest {
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

    testFluid.setTemperature(24.0, "C");
    testFluid.setPressure(48.0, "bara");
    testFluid.setTotalFlowRate(4.5, "MSm3/day");

    Stream stream_1 = new Stream("Stream1", testFluid);

    MultiPhaseMeter multiPhaseMeter = new MultiPhaseMeter("test", stream_1);
    multiPhaseMeter.setTemperature(90.0, "C");
    multiPhaseMeter.setPressure(60.0, "bara");

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_1);
    operations.add(multiPhaseMeter);
    operations.run();

    Assertions.assertEquals(51.3073530232923, multiPhaseMeter.getMeasuredValue("GOR", ""), 1e-5);
    Assertions.assertEquals(3106.770827796345, multiPhaseMeter.getMeasuredValue("GOR_std", ""),
        1e-2);
  }
}
