package neqsim.processSimulation.processEquipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.processsimulation.processequipment.stream.Stream;
import neqsim.processsimulation.processequipment.valve.ThrottlingValve;

public class ThrottlingValveTest {
  @Test
  void testCalcCvGas() {
    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem2.addComponent("methane", 1.0);
    testSystem2.setMixingRule(2);

    Stream stream1 = new Stream("Stream1", testSystem2);
    stream1.setFlowRate(7000, "Sm3/hr");
    stream1.setPressure(10.0, "bara");
    stream1.setTemperature(55.0, "C");
    stream1.run();

    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    valve1.setOutletPressure(5.0);
    valve1.setPercentValveOpening(100);
    valve1.run();

    assertEquals(48.2652, valve1.getCv("US"), 1e-2);
    assertEquals(2649.7612, valve1.getCv("SI"), 1e-2);
  }

  @Test
  void testCalcCvLiquid() {
    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem2.addComponent("water", 1.0);
    testSystem2.setMixingRule(2);

    Stream stream1 = new Stream("Stream1", testSystem2);
    stream1.setFlowRate(100.0, "kg/hr");
    stream1.setPressure(100.0, "bara");
    stream1.setTemperature(55.0, "C");
    stream1.run();

    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    valve1.setOutletPressure(50.0);
    valve1.setPercentValveOpening(100);
    valve1.run();

    assertEquals(0.4515327970, stream1.getFlowRate("gallons/min"), 1e-2);
    assertEquals(0.0165567743765, valve1.getCv("SI"), 1e-2);
    assertEquals(100.0, valve1.getPercentValveOpening(), 1e-2);
    assertEquals(100, stream1.getFlowRate("kg/hr"), 1e-2);
    assertEquals(3.015805897362369E-4, valve1.getCv("US"), 1e-2);
  }
}
