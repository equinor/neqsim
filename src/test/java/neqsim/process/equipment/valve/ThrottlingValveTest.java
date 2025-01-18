package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;

public class ThrottlingValveTest {

  /**
   * Test method for calculating the flow coefficient (Cv) of a gas through a throttling valve.
   * <p>
   * This test sets up a thermodynamic system using the SRK EOS model with methane as the component.
   * It creates a stream with specified flow rate, pressure, and temperature, and then passes it
   * through a throttling valve. The outlet pressure and valve opening percentage are set, and the
   * valve is run. The test asserts that the calculated Cv values in both US and SI units are as
   * expected.
   * </p>
   */
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

    assertEquals(69.482318, valve1.getCv("SI"), 1e-2);
    assertEquals(69.482318 / 54.9, valve1.getCv("US"), 1e-2);
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

  @Test
  void testSetDeltaPressure() {
    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem2.addComponent("methane", 1.0);
    testSystem2.setMixingRule(2);

    Stream stream1 = new Stream("Stream1", testSystem2);
    stream1.setFlowRate(100.0, "kg/hr");
    stream1.setPressure(100.0, "bara");
    stream1.setTemperature(55.0, "C");
    stream1.run();

    double deltaPressure = 10.0;
    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    valve1.setDeltaPressure(deltaPressure, "bara");
    valve1.run();

    Stream stream2 = new Stream("Stream1", valve1.getOutletStream());
    stream2.getPressure("bara");
    stream2.run();

    assertEquals(deltaPressure, valve1.getDeltaPressure(), 1e-2);
    assertEquals(deltaPressure, stream1.getPressure("bara") - stream2.getPressure("bara"), 1e-4);
    assertEquals(52.269428855, stream2.getTemperature("C"), 1e-2);
  }

  @Test
  void testSetDeltaPressure2() {
    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem2.addComponent("methane", 1.0);
    testSystem2.setMixingRule(2);

    Stream stream1 = new Stream("Stream1", testSystem2);
    stream1.setFlowRate(100.0, "kg/hr");
    stream1.setPressure(100.0, "bara");
    stream1.setTemperature(55.0, "C");
    stream1.run();

    double deltaPressure = 0.0;
    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    valve1.run();

    Stream stream2 = new Stream("Stream1", valve1.getOutletStream());
    stream2.getPressure("bara");
    stream2.run();

    assertEquals(deltaPressure, valve1.getDeltaPressure(), 1e-2);
    assertEquals(deltaPressure, stream1.getPressure("bara") - stream2.getPressure("bara"), 1e-4);
    assertEquals(55.0, stream2.getTemperature("C"), 1e-2);
  }
}
