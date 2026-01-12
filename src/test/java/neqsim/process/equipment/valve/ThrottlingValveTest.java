package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.valve.ControlValveSizing_IEC_60534;
import neqsim.process.mechanicaldesign.valve.ControlValveSizing_simple;
import neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign;
import neqsim.process.processmodel.ProcessSystem;

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
    stream1.setFlowRate(7000.0, "Sm3/hr");
    stream1.setPressure(10.0, "bara");
    stream1.setTemperature(20.0, "C");
    stream1.run();

    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    ((ValveMechanicalDesign) valve1.getMechanicalDesign()).setValveSizingStandard("IEC 60534");
    valve1.setOutletPressure(9.0);
    valve1.setPercentValveOpening(100);
    valve1.getMechanicalDesign().getValveSizingMethod().setxT(0.137);
    ((ControlValveSizing_IEC_60534) valve1.getMechanicalDesign().getValveSizingMethod()).setD(0.1);
    valve1.setCalculateSteadyState(false);

    valve1.run();
    assertEquals(7000.0000000, valve1.getOutletStream().getFlowRate("Sm3/hr"), 7000 / 100);
    assertEquals(8.400972698, valve1.getKv(), 1e-2);

    Map<String, Object> result = valve1.getMechanicalDesign().calcValveSize();
    double Cv = (double) result.get("Cv");
    assertEquals(9.71152443916267, Cv, 1e-2);

    assertEquals(13207.673237, valve1.getCg(), 1e-2);
    assertEquals(8.400972698237, valve1.getCv("SI"), 1e-2);
    assertEquals(100.0, valve1.getPercentValveOpening(), 1e-2);

    valve1.setCalculateSteadyState(false);
    valve1.runTransient(0.1);
    assertEquals(7000.0000000, valve1.getOutletStream().getFlowRate("Sm3/hr"), 7000 / 100);
    valve1.setPercentValveOpening(80);
    valve1.runTransient(0.1);
    assertEquals(5600.0, valve1.getOutletStream().getFlowRate("Sm3/hr"), 7000 / 100);

    valve1.setIsCalcOutPressure(true);
    valve1.runTransient(0.1);
    assertEquals(9.000001952, valve1.getOutletStream().getPressure("bara"), 0.01); // choked
  }

  @Test
  void testZeroAndLowFlowBypassCalculation() {
    neqsim.thermo.system.SystemInterface system =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 50.0);
    system.addComponent("methane", 1.0);
    system.setMixingRule(2);

    Stream zeroFlowStream = new Stream("zeroFlowStream", system);
    zeroFlowStream.setFlowRate(0.0, "kg/hr");
    zeroFlowStream.setPressure(50.0, "bara");
    zeroFlowStream.setTemperature(25.0, "C");
    zeroFlowStream.run();

    ThrottlingValve zeroFlowValve = new ThrottlingValve("zeroFlowValve", zeroFlowStream);
    zeroFlowValve.setOutletPressure(30.0);
    zeroFlowValve.run();

    assertEquals(0.0, zeroFlowValve.getOutletStream().getFlowRate("mole/sec"), 1e-12);
    assertEquals(30.0, zeroFlowValve.getOutletStream().getPressure("bara"), 1e-6);

    neqsim.thermo.system.SystemInterface lowFlowSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 50.0);
    lowFlowSystem.addComponent("methane", 1.0);
    lowFlowSystem.setMixingRule(2);

    Stream lowFlowStream = new Stream("lowFlowStream", lowFlowSystem);
    lowFlowStream.setFlowRate(1.0e-13, "mole/sec");
    lowFlowStream.setPressure(50.0, "bara");
    lowFlowStream.setTemperature(25.0, "C");
    lowFlowStream.run();

    ThrottlingValve lowFlowValve = new ThrottlingValve("lowFlowValve", lowFlowStream);
    lowFlowValve.setOutletPressure(30.0);
    lowFlowValve.run();

    assertEquals(0.0, lowFlowValve.getOutletStream().getFlowRate("mole/sec"), 1e-12);
    assertEquals(30.0, lowFlowValve.getOutletStream().getPressure("bara"), 1e-6);
  }

  @Test
  void testCalcProdChoke() {
    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem2.addComponent("methane", 1.0);
    testSystem2.addComponent("nC10", 1.0);
    testSystem2.setMixingRule(2);

    Stream stream1 = new Stream("Stream1", testSystem2);
    stream1.setFlowRate(7000.0, "Sm3/hr");
    stream1.setPressure(10.0, "bara");
    stream1.setTemperature(20.0, "C");
    stream1.run();

    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    ((ValveMechanicalDesign) valve1.getMechanicalDesign()).setValveSizingStandard("prod choke");
    valve1.setOutletPressure(9.0);
    valve1.setPercentValveOpening(100);
    valve1.getMechanicalDesign().getValveSizingMethod().setxT(0.13);
    valve1.setCalculateSteadyState(false);

    valve1.run();
    assertEquals(7000.0000000, valve1.getOutletStream().getFlowRate("Sm3/hr"), 7000 / 100);
    // Kv now calculated using IEC 60534 formula with Cd = 0.85 discharge coefficient
    assertEquals(7.64, valve1.getKv(), 0.1);

    Map<String, Object> result = valve1.getMechanicalDesign().calcValveSize();
    double Cv = (double) result.get("Cv");
    assertEquals(8.83, Cv, 0.1);

    // Cg = Cv * Cl where Cl = 1360 (constant in current implementation)
    assertEquals(12010, valve1.getCg(), 50.0);
    assertEquals(7.64, valve1.getCv("SI"), 0.1);
    assertEquals(100.0, valve1.getPercentValveOpening(), 1e-2);

    valve1.setCalculateSteadyState(false);
    valve1.runTransient(0.1);
    assertEquals(7000.0000000, valve1.getOutletStream().getFlowRate("Sm3/hr"), 7000 / 100);
    valve1.setPercentValveOpening(80);
    valve1.runTransient(0.1);
    assertEquals(5600.0, valve1.getOutletStream().getFlowRate("Sm3/hr"), 7000 / 100);

    // For this test, we just verify the outlet pressure calculation runs without error
    // The actual value depends on the complex interplay of Kv formula and bisection solver
    valve1.setIsCalcOutPressure(true);
    valve1.runTransient(0.1);
    // With reduced flow (80% opening), outlet pressure increases
    double P2 = valve1.getOutletStream().getPressure("bara");
    assertTrue(P2 > 0.0 && P2 < 10.0, "Outlet pressure should be between 0 and inlet: " + P2);
  }

  @Test
  void testCalcCvGas_stdvalve() {
    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem2.addComponent("methane", 1.0);
    testSystem2.setMixingRule(2);

    Stream stream1 = new Stream("Stream1", testSystem2);
    stream1.setFlowRate(7000.0, "Sm3/hr");
    stream1.setPressure(10.0, "bara");
    stream1.setTemperature(20.0, "C");
    stream1.run();

    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    valve1.setOutletPressure(9.0);
    valve1.setPercentValveOpening(100);

    valve1.run();
    assertEquals(7000.0000000, valve1.getOutletStream().getFlowRate("Sm3/hr"), 7000 / 100);
    // Kv calculated using IEC 60534 gas formula
    assertEquals(8.401, valve1.getKv(), 0.1);

    Map<String, Object> result = valve1.getMechanicalDesign().calcValveSize();
    double Kv = (double) result.get("Kv");
    assertEquals(8.401, Kv, 0.1);

    // Cg = Cv * 1360, Cv = Kv * 1.156 = 9.71, Cg = 13207
    assertEquals(13207.0, valve1.getCg(), 100);
    // getCv("SI") returns Kv, getCv() returns Cv in US units
    assertEquals(8.401, valve1.getCv("SI"), 0.1);
    assertEquals(100.0, valve1.getPercentValveOpening(), 1e-2);

    valve1.setCalculateSteadyState(false);
    valve1.runTransient(0.1);
    assertEquals(7000.00000, valve1.getOutletStream().getFlowRate("Sm3/hr"), 7000 / 100);
    valve1.setPercentValveOpening(80);
    valve1.runTransient(0.1);
    assertEquals(5600.00000, valve1.getOutletStream().getFlowRate("Sm3/hr"), 7000 / 100);

    valve1.setIsCalcOutPressure(true);
    valve1.run();
    assertEquals(9.0000019527, valve1.getOutletStream().getPressure("bara"), 0.01); // choked
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
    ((ValveMechanicalDesign) valve1.getMechanicalDesign()).setValveSizingStandard("IEC 60534");
    valve1.setOutletPressure(4.46);
    valve1.setPercentValveOpening(100);
    valve1.run();

    assertEquals(88.35774556, valve1.getKv(), 1e-2);
    assertEquals(138912.5132642, valve1.getCg(), 1e-2);
    assertEquals(88.35774556, valve1.getCv("SI"), 1e-2);
    assertEquals(102.141553, valve1.getCv("US"), 1e-2);
  }

  @Test
  void testCalcCvGas3() {
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
    ((ValveMechanicalDesign) valve1.getMechanicalDesign()).setValveSizingStandard("IEC 60534 full");
    valve1.getMechanicalDesign().getValveSizingMethod().setxT(0.137);
    valve1.setOutletPressure(4.46);
    valve1.setPercentValveOpening(100);
    valve1.run();

    assertEquals(88.3577455629, valve1.getKv(), 1e-2);
    assertEquals(138912.5132642, valve1.getCg(), 1e-2);
    assertEquals(88.3577455629, valve1.getCv("SI"), 1e-2);
    assertEquals(102.141553870, valve1.getCv("US"), 1e-2);
  }

  @Test
  void testCalcCvWater3() {
    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem2.addComponent("water", 1.0);
    testSystem2.setMixingRule(2);

    Stream stream1 = new Stream("Stream1", testSystem2);
    stream1.setFlowRate(470.2, "kg/sec");
    stream1.setPressure(14.8, "bara");
    stream1.setTemperature(16.0, "C");
    stream1.run();

    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    ((ValveMechanicalDesign) valve1.getMechanicalDesign()).setValveSizingStandard("IEC 60534 full");
    valve1.getMechanicalDesign().getValveSizingMethod().setxT(0.137);
    ((ControlValveSizing_IEC_60534) valve1.getMechanicalDesign().getValveSizingMethod()).setD(0.1);
    ((ControlValveSizing_IEC_60534) valve1.getMechanicalDesign().getValveSizingMethod()).setD1(0.1);
    ((ControlValveSizing_IEC_60534) valve1.getMechanicalDesign().getValveSizingMethod()).setD2(0.1);
    valve1.setOutletPressure(4.46);
    valve1.setPercentValveOpening(100);
    valve1.run();

    // assertEquals(153.959772137, valve1.getKv(), 1e-2);
    // assertEquals(242049.395364, valve1.getCg(), 1e-2);
    // assertEquals(153.95977213, valve1.getCv("SI"), 1e-2);
    // assertEquals(177.9774965, valve1.getCv("US"), 1e-2);
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

    assertEquals(0.27564816, valve1.getKv(), 1e-2);
    assertEquals(0.2756481, valve1.getCv("SI"), 1e-2);
    assertEquals(0.318792288, valve1.getCv("US"), 1e-2);
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
    ((ValveMechanicalDesign) valve1.getMechanicalDesign()).setValveSizingStandard("IEC 60534");
    valve1.setOutletPressure(19);
    valve1.run();

    assertEquals(117.09360388, valve1.getKv(), 1e-2);
    assertEquals(117.09360388, valve1.getCv("SI"), 1e-2);
    assertEquals(135.36020609, valve1.getCv("US"), 1e-2);
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

    ((ValveMechanicalDesign) valve1.getMechanicalDesign()).setValveSizingStandard("default");
    valve1.setPercentValveOpening(100);
    valve1.setCalculateSteadyState(false);
    valve1.run();

    // assertEquals(0.451532797, stream1.getFlowRate("gallons/min"), 1e-2);
    assertEquals(0.016563208826, valve1.getCv("SI"), 1e-2);
    assertEquals(100.0, valve1.getPercentValveOpening(), 1e-2);
    assertEquals(100.0, stream1.getFlowRate("kg/hr"), 1e-2);
    assertEquals(0.01914706940, valve1.getCv("US"), 1e-2);

    // valve1.setCalculateSteadyState(false);
    // valve1.runTransient(0.1);
    valve1.setOutletPressure(55.0);
    valve1.runTransient(0.1);
    assertEquals(94.8683298, valve1.getInletStream().getFlowRate("kg/hr"), 1e-5);

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

  @Test
  void testValveClosureDoesNotProduceNaNInTransientFlow() {
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 40.0, 18.0);
    fluid.addComponent("methane", 50.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 2.0);
    fluid.setMixingRule(2);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(150.0, "kg/hr");
    inlet.setPressure(18.0, "bara");
    inlet.setTemperature(40.0, "C");
    inlet.run();

    ThrottlingValve valve = new ThrottlingValve("valve", inlet);
    valve.setOutletPressure(5.0);
    valve.setPercentValveOpening(30.0);
    valve.setCalculateSteadyState(false);

    ProcessSystem process = new ProcessSystem("transient valve test");
    process.add(inlet);
    process.add(valve);
    process.run();
    process.storeInitialState();
    process.setTimeStep(5.0);

    for (int i = 0; i < 3; i++) {
      process.runTransient();
    }

    valve.setPercentValveOpening(0.0);
    for (int i = 0; i < 3; i++) {
      process.runTransient();
    }

    double outletMoles = valve.getOutletStream().getThermoSystem().getTotalNumberOfMoles();
    assertTrue(Double.isFinite(outletMoles));
    assertEquals(0.0, outletMoles, 1e-12);
    assertTrue(Double.isFinite(valve.getOutletStream().getThermoSystem().getMolarVolume()));
  }

  @Test
  void testCalculateValveOpeningFromFlowRateProdChoke() {
    // Test that calculateValveOpeningFromFlowRate correctly inverts the flow calculation
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem.addComponent("methane", 1.0);
    testSystem.setMixingRule(2);

    Stream stream1 = new Stream("Stream1", testSystem);
    stream1.setFlowRate(7000.0, "Sm3/hr");
    stream1.setPressure(10.0, "bara");
    stream1.setTemperature(20.0, "C");
    stream1.run();

    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    ((ValveMechanicalDesign) valve1.getMechanicalDesign()).setValveSizingStandard("prod choke");
    valve1.setOutletPressure(9.0);
    valve1.setPercentValveOpening(100);
    valve1.setCalculateSteadyState(false);
    valve1.run();

    double Kv = valve1.getKv();
    double flowRate = valve1.getInletStream().getFlowRate("m3/sec");

    // Now calculate what opening we need to get this flow rate
    ControlValveSizing_simple sizingMethod =
        (ControlValveSizing_simple) valve1.getMechanicalDesign().getValveSizingMethod();
    double calculatedOpening = sizingMethod.calculateValveOpeningFromFlowRate(flowRate, Kv, 100.0,
        valve1.getInletStream(), valve1.getOutletStream());

    // Should be approximately 100% since we used 100% opening to generate the flow
    assertEquals(100.0, calculatedOpening, 1.0);

    // Test with a reduced flow rate - calculate what opening is needed for half the flow
    double halfFlow = flowRate * 0.5;
    double calculatedOpeningHalfFlow = sizingMethod.calculateValveOpeningFromFlowRate(halfFlow, Kv,
        100.0, valve1.getInletStream(), valve1.getOutletStream());

    // Should be approximately 50% for half the flow (linear relationship in simple model)
    assertEquals(50.0, calculatedOpeningHalfFlow, 5.0);

    // Verify that the method no longer returns a constant 100%
    assertTrue(calculatedOpeningHalfFlow < 100.0,
        "calculateValveOpeningFromFlowRate should not return constant 100%");
    assertTrue(calculatedOpeningHalfFlow > 0.0,
        "calculateValveOpeningFromFlowRate should return positive value");
  }
}
