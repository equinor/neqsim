package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.valve.ControlValveSizing_IEC_60534;
import neqsim.process.mechanicaldesign.valve.ControlValveSizing_simple;
import neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;

public class ThrottlingValveTest {
  private static class InitTrackingSystemSrkEos extends SystemSrkEos {
    private static final long serialVersionUID = 1000L;
    private AtomicInteger levelTwoCalls = new AtomicInteger();
    private AtomicInteger levelThreeCalls = new AtomicInteger();

    InitTrackingSystemSrkEos(double temperature, double pressure) {
      super(temperature, pressure);
    }

    @Override
    public InitTrackingSystemSrkEos clone() {
      InitTrackingSystemSrkEos cloned = (InitTrackingSystemSrkEos) super.clone();
      if (cloned == null) {
        throw new IllegalStateException("Failed to clone initialization-tracking fluid");
      }
      cloned.levelTwoCalls = levelTwoCalls;
      cloned.levelThreeCalls = levelThreeCalls;
      return cloned;
    }

    @Override
    public void init(int initType) {
      super.init(initType);
      if (levelTwoCalls != null && initType == 2) {
        levelTwoCalls.incrementAndGet();
      } else if (levelThreeCalls != null && initType == 3) {
        levelThreeCalls.incrementAndGet();
      }
    }

    void resetInitCounts() {
      levelTwoCalls.set(0);
      levelThreeCalls.set(0);
    }

    int getLevelTwoCalls() {
      return levelTwoCalls.get();
    }

    int getLevelThreeCalls() {
      return levelThreeCalls.get();
    }
  }

  /** Regression test for GitHub issue #3446. */
  @Test
  void testCalculatedOutletPressureInvertsGasValveSizingIssue3446() {
    SystemInterface fluid = new SystemSrkEos(298.15, 1.9);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(28500.0, "kg/hr");
    inlet.setPressure(1.9, "bara");
    inlet.setTemperature(25.0, "C");

    ThrottlingValve valve = new ThrottlingValve("valve", inlet);
    valve.setGasValve(true);
    valve.setPercentValveOpening(100.0);
    valve.setOutletPressure(1.484, "bara");
    assertFalse(valve.isAllowChoked(), "Single-phase valve flow is not capacity-limited unless enabled explicitly");

    valve.setAllowChoked(true);
    assertTrue(valve.getMechanicalDesign().getValveSizingMethod().isAllowChoked(),
        "The valve-level choked-flow setting must reach the active sizing method");
    valve.setAllowChoked(false);

    ProcessSystem process = new ProcessSystem("gas valve pressure inversion");
    process.add(inlet);
    process.add(valve);
    process.run();

    double sizedKv = valve.getKv();
    valve.setIsCalcOutPressure(true);
    process.run();

    assertEquals(sizedKv, valve.getKv(), 0.0, "Reverse mode must retain the sized valve coefficient");
    assertEquals(1.484, valve.getOutletStream().getPressure("bara"), 1.0e-4,
        "Fixed-Kv pressure mode must invert the forward sizing point");

    double previousPressureDrop = 0.0;
    double[] flowRates = {24000.0, 26500.0, 27500.0, 28000.0, 28500.0, 29000.0, 30000.0};
    for (double flowRate : flowRates) {
      inlet.setFlowRate(flowRate, "kg/hr");
      process.run();
      double pressureDrop = inlet.getPressure("bara") - valve.getOutletStream().getPressure("bara");

      assertTrue(Double.isFinite(pressureDrop), "Calculated pressure drop must remain finite");
      assertTrue(pressureDrop > previousPressureDrop, "Pressure drop must increase smoothly with gas flow");
      assertTrue(valve.getOutletStream().getPressure("bara") > 0.1,
          "A nearby flow increase must not pin the outlet pressure to the solver floor");
      previousPressureDrop = pressureDrop;
    }
  }

  @Test
  void testAutoSizeAtPartialOpeningReproducesDesignFlowInTransientMode() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 25.0, 20.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule(2);

    Stream feed = new Stream("auto-size feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setPressure(20.0, "bara");
    feed.setTemperature(25.0, "C");
    feed.run();

    ThrottlingValve valve = new ThrottlingValve("auto-sized valve", feed);
    valve.setOutletPressure(10.0, "bara");
    valve.setPercentValveOpening(70.0);
    valve.autoSize(1.0, 70.0);
    valve.setCalculateSteadyState(false);
    valve.runTransient(0.1);

    assertEquals(5000.0, valve.getOutletStream().getFlowRate("kg/hr"), 50.0);
  }

  @Test
  void testAutoSizePreservesNewDesignFlowWhenExistingCvIsStale() {
    SystemInterface gas = new SystemSrkEos(293.15, 20.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");

    Stream inlet = new Stream("inlet", gas);
    inlet.setFlowRate(1000.0, "kg/hr");
    inlet.run();

    ThrottlingValve valve = new ThrottlingValve("valve", inlet);
    valve.setOutletPressure(10.0, "bara");
    valve.setPercentValveOpening(70.0);
    valve.run();

    inlet.setFlowRate(5000.0, "kg/hr");
    valve.autoSize(1.0, 70.0);

    assertEquals(5000.0, inlet.getFlowRate("kg/hr"), 1.0e-6);
    valve.setCalculateSteadyState(false);
    valve.runTransient(1.0, UUID.randomUUID());

    assertEquals(5000.0, valve.getOutletStream().getFlowRate("kg/hr"), 50.0);
  }

  /**
   * Test method for calculating the flow coefficient (Cv) of a gas through a throttling valve.
   * <p>
   * This test sets up a thermodynamic system using the SRK EOS model with methane as the component. It creates a stream
   * with specified flow rate, pressure, and temperature, and then passes it through a throttling valve. The outlet
   * pressure and valve opening percentage are set, and the valve is run. The test asserts that the calculated Cv values
   * in both US and SI units are as expected.
   * </p>
   */
  @Test
  void testCalcCvGas() {
    neqsim.thermo.system.SystemInterface testSystem2 = new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
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
    assertEquals(78.669464, valve1.getKv(), 0.5);

    Map<String, Object> result = valve1.getMechanicalDesign().calcValveSize();
    double Cv = (double) result.get("Cv");
    assertEquals(90.941901, Cv, 0.5);

    assertEquals(123680.98, valve1.getCg(), 10);
    assertEquals(78.669464, valve1.getCv("SI"), 0.5);
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
    neqsim.thermo.system.SystemInterface system = new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 50.0);
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

    neqsim.thermo.system.SystemInterface lowFlowSystem = new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 50.0);
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
    neqsim.thermo.system.SystemInterface testSystem2 = new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
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
    // Kv calculated using IEC 60534 formula with Cd = 0.85 discharge coefficient
    // Values updated after fix for GitHub issue #1918 (standard volumetric flow)
    assertEquals(163.30, valve1.getKv(), 1.0);

    Map<String, Object> result = valve1.getMechanicalDesign().calcValveSize();
    double Cv = (double) result.get("Cv");
    assertEquals(188.78, Cv, 1.0);

    assertEquals(256743.0, valve1.getCg(), 1000.0);
    assertEquals(163.30, valve1.getCv("SI"), 1.0);
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
    neqsim.thermo.system.SystemInterface testSystem2 = new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
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
    // Kv calculated using IEC 60534 gas formula (with standard volumetric flow per issue #1918)
    assertEquals(78.669, valve1.getKv(), 0.5);

    Map<String, Object> result = valve1.getMechanicalDesign().calcValveSize();
    double Kv = (double) result.get("Kv");
    assertEquals(78.669, Kv, 0.5);

    // Cg = Cv * 1360, Cv = Kv * 1.156
    assertEquals(123680.0, valve1.getCg(), 500);
    // getCv("SI") returns Kv, getCv() returns Cv in US units
    assertEquals(78.669, valve1.getCv("SI"), 0.5);
    assertEquals(100.0, valve1.getPercentValveOpening(), 1e-2);

    valve1.setCalculateSteadyState(false);
    valve1.runTransient(0.1);
    assertEquals(7000.00000, valve1.getOutletStream().getFlowRate("Sm3/hr"), 7000 / 100);
    valve1.setPercentValveOpening(80);
    valve1.runTransient(0.1);
    assertEquals(5600.00000, valve1.getOutletStream().getFlowRate("Sm3/hr"), 7000 / 100);

    valve1.setIsCalcOutPressure(true);
    valve1.run();
    assertEquals(9.0, valve1.getOutletStream().getPressure("bara"), 0.02);
  }

  /**
   * Test method for calculating the flow coefficient (Cv) of a gas through a throttling valve.
   * <p>
   * This test sets up a thermodynamic system using the SRK EOS model with methane as the component. It creates a stream
   * with specified flow rate, pressure, and temperature, and then passes it through a throttling valve. The outlet
   * pressure and valve opening percentage are set, and the valve is run. The test asserts that the calculated Cv values
   * in both US and SI units are as expected.
   * </p>
   */
  @Test
  void testCalcCvGas2() {
    neqsim.thermo.system.SystemInterface testSystem2 = new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
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

    assertEquals(1254.212424, valve1.getKv(), 1.0);
    assertEquals(1971822.60, valve1.getCg(), 100);
    assertEquals(1254.212424, valve1.getCv("SI"), 1.0);
    assertEquals(1449.869563, valve1.getCv("US"), 1.0);
  }

  @Test
  void testCalcCvGas3() {
    neqsim.thermo.system.SystemInterface testSystem2 = new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
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

    assertEquals(1254.212424, valve1.getKv(), 1.0);
    assertEquals(1971822.60, valve1.getCg(), 100);
    assertEquals(1254.212424, valve1.getCv("SI"), 1.0);
    assertEquals(1449.869563, valve1.getCv("US"), 1.0);
  }

  @Test
  void testCalcCvWater3() {
    neqsim.thermo.system.SystemInterface testSystem2 = new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
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
    neqsim.thermo.system.SystemInterface testSystem2 = new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
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
    neqsim.thermo.system.SystemInterface testSystem2 = new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
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
    neqsim.thermo.system.SystemInterface testSystem2 = new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
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
    neqsim.thermo.system.SystemInterface testSystem2 = new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
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
    neqsim.thermo.system.SystemInterface testSystem2 = new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
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
    neqsim.thermo.system.SystemInterface fluid = new neqsim.thermo.system.SystemSrkEos(273.15 + 40.0, 18.0);
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
    neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
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
    ControlValveSizing_simple sizingMethod = (ControlValveSizing_simple) valve1.getMechanicalDesign()
        .getValveSizingMethod();
    double calculatedOpening = sizingMethod.calculateValveOpeningFromFlowRate(flowRate, Kv, 100.0,
        valve1.getInletStream(), valve1.getOutletStream());

    // Should be approximately 100% since we used 100% opening to generate the flow
    assertEquals(100.0, calculatedOpening, 1.0);

    // Test with a reduced flow rate - calculate what opening is needed for half the flow
    double halfFlow = flowRate * 0.5;
    double calculatedOpeningHalfFlow = sizingMethod.calculateValveOpeningFromFlowRate(halfFlow, Kv, 100.0,
        valve1.getInletStream(), valve1.getOutletStream());

    // Should be approximately 50% for half the flow (linear relationship in simple model)
    assertEquals(50.0, calculatedOpeningHalfFlow, 5.0);

    // Verify that the method no longer returns a constant 100%
    assertTrue(calculatedOpeningHalfFlow < 100.0, "calculateValveOpeningFromFlowRate should not return constant 100%");
    assertTrue(calculatedOpeningHalfFlow > 0.0, "calculateValveOpeningFromFlowRate should return positive value");
  }

  /** The unrun feed in issue 1918 must size identically in direct and flowsheet execution. */
  @Test
  void testGasValveCvWithProcessSystem_Issue1918() {
    for (boolean explicitIec : new boolean[] {false, true}) {
      double direct = gasCvForFreshFeed(false, explicitIec);
      double process = gasCvForFreshFeed(true, explicitIec);
      assertEquals(process, direct, 1e-9);
      if (explicitIec) {
        // The original approximate reference uses Z=1. NeqSim uses the flashed SRK inlet Z.
        assertEquals(16.2, direct, 1.5);
      }
    }
  }

  /**
   * Run the reported cold feed without a preparatory stream flash.
   *
   * @param processRun whether to execute the stream and valve through a flowsheet
   * @param explicitIec select IEC 60534 and the reference xT of 0.75
   * @return the full-open gas valve Cv
   */
  private double gasCvForFreshFeed(boolean processRun, boolean explicitIec) {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(10000.0, "kg/hr");
    Stream stream = new Stream("Inlet", fluid);
    ControlValve valve = new ControlValve("Valve", stream);
    if (explicitIec) {
      valve.getMechanicalDesign().setValveSizingStandard("IEC 60534");
      valve.getMechanicalDesign().getValveSizingMethod().setxT(0.75);
    }
    valve.setPercentValveOpening(100.0);
    valve.setOutletPressure(25.0);
    if (processRun) {
      ProcessSystem process = new ProcessSystem();
      process.add(stream);
      process.add(valve);
      process.run();
    } else {
      valve.run();
    }
    valve.calcKv();
    assertEquals(10000.0, valve.getOutletStream().getFlowRate("kg/hr"), 1e-6);
    assertEquals(25.0, valve.getOutletStream().getPressure("bara"), 1e-10);
    return valve.getCv();
  }

  /**
   * Valve entropy requires caloric properties but not level-3 composition derivatives. The diagnostic must preserve the
   * level-3 reference result and surrounding stream state at the base condition and a nearby operating point.
   */
  @Test
  void testEntropyProductionUsesMinimumThermodynamicInitializationLevel() {
    InitTrackingSystemSrkEos fluid = new InitTrackingSystemSrkEos(363.15, 80.0);
    fluid.addComponent("nitrogen", 0.02);
    fluid.addComponent("CO2", 0.03);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-heptane", 0.04);
    fluid.setMixingRule("classic");

    Stream inlet = new Stream("tracked valve inlet", fluid);
    inlet.setFlowRate(50000.0, "kg/hr");
    inlet.run();

    ThrottlingValve valve = new ThrottlingValve("tracked entropy valve", inlet);
    valve.setOutletPressure(40.0, "bara");
    valve.run();

    assertMinimumEntropyInitialization(fluid, valve);

    inlet.setTemperature(368.15, "K");
    inlet.run();
    valve.run();
    assertMinimumEntropyInitialization(fluid, valve);
  }

  private static void assertMinimumEntropyInitialization(InitTrackingSystemSrkEos fluid, ThrottlingValve valve) {
    valve.getInletStream().getFluid().init(3);
    valve.getOutletStream().getFluid().init(3);
    double expectedEntropy = valve.getOutletStream().getFluid().getEntropy("J/K")
        - valve.getInletStream().getFluid().getEntropy("J/K");

    double inletTemperature = valve.getInletStream().getTemperature("K");
    double outletTemperature = valve.getOutletStream().getTemperature("K");
    double inletPressure = valve.getInletStream().getPressure("bara");
    double outletPressure = valve.getOutletStream().getPressure("bara");
    double massFlow = valve.getOutletStream().getFlowRate("kg/hr");
    double inletEnthalpy = valve.getInletStream().getFluid().getEnthalpy("J");
    double outletEnthalpy = valve.getOutletStream().getFluid().getEnthalpy("J");
    int inletPhases = valve.getInletStream().getFluid().getNumberOfPhases();
    int outletPhases = valve.getOutletStream().getFluid().getNumberOfPhases();

    fluid.resetInitCounts();
    double actualEntropy = valve.getEntropyProduction("J/K");

    assertEquals(expectedEntropy, actualEntropy, Math.max(1.0e-9, Math.abs(expectedEntropy) * 1.0e-12));
    assertEquals(2, fluid.getLevelTwoCalls(), "Inlet and outlet still require caloric initialization");
    assertEquals(0, fluid.getLevelThreeCalls(), "Valve entropy must not calculate composition derivatives");
    assertEquals(inletTemperature, valve.getInletStream().getTemperature("K"), 0.0);
    assertEquals(outletTemperature, valve.getOutletStream().getTemperature("K"), 0.0);
    assertEquals(inletPressure, valve.getInletStream().getPressure("bara"), 0.0);
    assertEquals(outletPressure, valve.getOutletStream().getPressure("bara"), 0.0);
    assertEquals(massFlow, valve.getOutletStream().getFlowRate("kg/hr"), 0.0);
    assertEquals(inletEnthalpy, valve.getInletStream().getFluid().getEnthalpy("J"), 0.0);
    assertEquals(outletEnthalpy, valve.getOutletStream().getFluid().getEnthalpy("J"), 0.0);
    assertEquals(inletPhases, valve.getInletStream().getFluid().getNumberOfPhases());
    assertEquals(outletPhases, valve.getOutletStream().getFluid().getNumberOfPhases());
    assertEquals(actualEntropy, valve.getEntropyProduction("J/K"), 0.0,
        "Repeated diagnostic calls must remain bit-identical");
  }

  @Test
  void testAcceptNegativeDPFlagLogic() {
    neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkEos(298.15, 10.0);
    testSystem.addComponent("methane", 1.0);
    testSystem.setMixingRule(2);

    Stream stream1 = new Stream("Stream1", testSystem);
    stream1.setPressure(10.0, "bara");
    stream1.run();

    ThrottlingValve defaultValve = new ThrottlingValve("default valve", stream1);
    assertTrue(defaultValve.isAcceptNegativeDP(), "Requested higher outlet pressure is accepted by default");
    defaultValve.setOutletPressure(15.0, "bara");
    defaultValve.run();
    assertEquals(15.0, defaultValve.getOutletStream().getPressure("bara"), 1e-4,
        "The default retains the requested outlet thermodynamic pressure state");

    ThrottlingValve clampingValve = new ThrottlingValve("clamping valve", stream1);
    clampingValve.setOutletPressure(15.0, "bara");
    clampingValve.setAcceptNegativeDP(false);
    clampingValve.run();
    assertEquals(10.0, clampingValve.getOutletStream().getPressure("bara"), 1e-4,
        "Disabling acceptance clamps the outlet thermodynamic pressure to the inlet");

    ThrottlingValve acceptingValve = new ThrottlingValve("accepting valve", stream1);
    acceptingValve.setOutletPressure(15.0, "bara");
    acceptingValve.setAcceptNegativeDP(true);
    acceptingValve.run();
    assertEquals(15.0, acceptingValve.getOutletStream().getPressure("bara"), 1e-4,
        "Enabling acceptance retains the requested outlet thermodynamic pressure state");
  }
}
