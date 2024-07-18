package neqsim.processSimulation.processEquipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.stream.Stream;

public class ThrottlingValveTest {
  @Test
  void testCalcCvGas() {
    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem2.addComponent("methane", 1.0);
    testSystem2.setMixingRule(2);

    Stream stream1 = new Stream("Stream1", testSystem2);
    stream1.setFlowRate(7000, "Sm3/hr");
    stream1.setPressure(100.0, "bara");
    stream1.setTemperature(55.0, "C");
    stream1.run();

    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    valve1.setOutletPressure(90.0);
    valve1.setPercentValveOpening(100);
    valve1.run();

    assertEquals(570.0941, valve1.getCv(), 1e-2);

  }

  @Test
  void testCalcCvLiquid() {
    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem2.addComponent("water", 1.0);
    testSystem2.setMixingRule(2);

    Stream stream1 = new Stream("Stream1", testSystem2);
    stream1.setFlowRate(10000000, "kg/hr");
    stream1.setPressure(100.0, "bara");
    stream1.setTemperature(55.0, "C");
    stream1.run();

    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    valve1.setOutletPressure(50.0);
    valve1.setPercentValveOpening(100);
    valve1.run();

    assertEquals(1655.6774, valve1.getCv(), 1e-2);

  }
}
