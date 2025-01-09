package neqsim.process.measurementdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class CombustionEmissionsCalculatorTest {
  @Test
  void testGetMeasuredValue() {
    SystemInterface fluid = new SystemSrkEos(190, 10);
    fluid.addComponent("nitrogen", 0.01);
    fluid.addComponent("CO2", 0.01);
    fluid.addComponent("methane", 1);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.01);
    fluid.addComponent("n-butane", 0.001);
    fluid.addComponent("i-butane", 0.001);

    Stream stream1 = new Stream("stream1", fluid);
    stream1.setFlowRate(1.0, "kg/hr");
    stream1.run();
    CombustionEmissionsCalculator comp = new CombustionEmissionsCalculator("name1", stream1);
    assertEquals(2.77772643250, comp.getMeasuredValue("kg/hr"), 0.0001);
  }
}
