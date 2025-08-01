package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.Map;
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
    assertEquals(7000.0000000, valve1.getOutletStream().getFlowRate("Sm3/hr"), 7000 / 100);
    assertEquals(870.9206, valve1.getCv(), 1e-2);

    Map<String, Object> result = valve1.getMechanicalDesign().calcValveSize();
    double Cv = (double) result.get("Cv");
    assertEquals(50.339544308, Cv, 1e-2);

    // assertEquals(50.34619045, valve1.getCv(), 1e-2);
    assertEquals(26127.620294, valve1.getCg(), 1e-2);
    assertEquals(870.920676, valve1.getCv("SI"), 1e-2);
    assertEquals(100.0, valve1.getPercentValveOpening(), 1e-2);

    valve1.setCalculateSteadyState(false);
    valve1.runTransient(0.1);
    assertEquals(7000.0000000, valve1.getOutletStream().getFlowRate("Sm3/hr"), 7000 / 100);
    valve1.setPercentValveOpening(80);
    valve1.runTransient(0.1);
    assertEquals(5600.00000000, valve1.getOutletStream().getFlowRate("Sm3/hr"), 7000 / 100);

    valve1.setIsCalcOutPressure(true);
    valve1.run();
    assertEquals(5.00000724433, valve1.getOutletStream().getPressure("bara"), 0.01); // choked
                                                                                     // flow up
    // to 9.5 bar?
  }

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
  void testCalcCvGas2() {
    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem2.addComponent("methane", 1.0);
    testSystem2.setMixingRule(2);

    Stream stream1 = new Stream("Stream1", testSystem2);
    stream1.setFlowRate(47.2, "Sm3/sec");
    stream1.setPressure(14.8, "bara");
    stream1.setTemperature(16.0, "C");
    stream1.run();

    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    valve1.setOutletPressure(4.46);
    valve1.setPercentValveOpening(100);
    valve1.run();

    assertEquals(11245.871568, valve1.getCv(), 1e-2);
    assertEquals(337376.147048, valve1.getCg(), 1e-2);
    assertEquals(11245.87156, valve1.getCv("SI"), 1e-2);
    assertEquals(204.8428336660, valve1.getCv("US"), 1e-2);
  }

  @Test
  void testCalcCvLiquid2() {
    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem2.addComponent("water", 1.0);
    testSystem2.setMixingRule(2);

    Stream stream1 = new Stream("Stream1", testSystem2);
    stream1.setFlowRate(100.0, "Am3/hr");
    stream1.setPressure(14.8, "bara");
    stream1.setTemperature(15.0, "C");
    stream1.run();

    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    valve1.setOutletPressure(4.46);
    valve1.setPercentValveOpening(100);
    valve1.run();

    assertEquals(0.27564816, valve1.getCv(), 1e-2);
    assertEquals(0.2756481, valve1.getCv("SI"), 1e-2);
    assertEquals(0.00502091374, valve1.getCv("US"), 1e-2);
  }

  @Test
  void testCalcCvLiquidPropane() {
    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem2.addComponent("propane", 1.0);
    testSystem2.setMixingRule(2);

    Stream stream1 = new Stream("Stream1", testSystem2);
    stream1.setFlowRate(0.0504721571, "idSm3/sec");
    stream1.setPressure(20.7, "bara");
    stream1.setTemperature(19.0, "C");
    stream1.run();

    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    valve1.setOutletPressure(19);
    valve1.run();

    assertEquals(117.041092, valve1.getCv(), 1e-2);
    assertEquals(117.041092, valve1.getCv("SI"), 1e-2);
    assertEquals(2.131896041, valve1.getCv("US"), 1e-2);
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
    valve1.setCalculateSteadyState(false);
    valve1.run();

    // assertEquals(0.451532797, stream1.getFlowRate("gallons/min"), 1e-2);
    assertEquals(0.016563208826, valve1.getCv("SI"), 1e-2);
    assertEquals(100.0, valve1.getPercentValveOpening(), 1e-2);
    assertEquals(100.0, stream1.getFlowRate("kg/hr"), 1e-2);
    assertEquals(3.016977928380948E-4, valve1.getCv("US"), 1e-2);

    // valve1.setCalculateSteadyState(false);
    // valve1.runTransient(0.1);
    valve1.setOutletPressure(55.0);
    valve1.runTransient(0.1);
    assertEquals(94.868329805, valve1.getInletStream().getFlowRate("kg/hr"), 1e-5);

    valve1.setIsCalcOutPressure(true);
    valve1.runTransient(0.1);
    assertEquals(55.000000000000, valve1.getOutletStream().getPressure("bara"), 0.01);

    valve1.run();
    assertEquals(55.000000000000, valve1.getOutletStream().getPressure("bara"), 0.01);
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
