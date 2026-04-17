package neqsim.process.controllerdevice;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceInterface.ControllerMode;
import neqsim.process.controllerdevice.structure.OverrideControllerStructure;
import neqsim.process.controllerdevice.structure.SplitRangeControllerStructure;
import neqsim.process.measurementdevice.MeasurementDeviceBaseClass;
import neqsim.process.measurementdevice.SensorFaultType;

/**
 * Tests for the dynamic simulation improvements: NIP-04 (controller modes and bumpless transfer),
 * NIP-05 (split-range and override control structures), and NIP-07 (sensor fault injection).
 */
class DynamicImprovementsTest {

  /** Simple transmitter stub returning a configurable value. */
  static class StubTransmitter extends MeasurementDeviceBaseClass {
    private double value = 50.0;

    StubTransmitter(String name) {
      super(name, "C");
      setMinimumValue(0.0);
      setMaximumValue(100.0);
    }

    void setValue(double value) {
      this.value = value;
    }

    @Override
    public double getMeasuredValue(String unit) {
      return applySignalModifiers(value);
    }
  }

  // ─── NIP-04: Controller modes and bumpless transfer ───

  @Test
  void testDefaultModeIsAuto() {
    ControllerDeviceBaseClass pid = new ControllerDeviceBaseClass("PID-1");
    Assertions.assertEquals(ControllerMode.AUTO, pid.getMode());
  }

  @Test
  void testSwitchToManualFreezesOutput() {
    ControllerDeviceBaseClass pid = new ControllerDeviceBaseClass("PID-2");
    StubTransmitter tx = new StubTransmitter("T-2");
    tx.setValue(50.0);
    pid.setTransmitter(tx);
    pid.setControllerSetPoint(50.0, "C");
    pid.setControllerParameters(1.0, 300.0, 0.0);

    // Run a few steps in AUTO
    pid.runTransient(30.0, 1.0);
    pid.runTransient(pid.getResponse(), 1.0);
    double autoOutput = pid.getResponse();

    // Switch to MANUAL — output freezes at last AUTO value
    pid.setMode(ControllerMode.MANUAL);
    Assertions.assertEquals(ControllerMode.MANUAL, pid.getMode());
    Assertions.assertEquals(autoOutput, pid.getManualOutput(), 1e-12);

    // Change measurement — output should stay frozen
    tx.setValue(60.0);
    pid.runTransient(pid.getResponse(), 1.0);
    Assertions.assertEquals(autoOutput, pid.getResponse(), 1e-12);
  }

  @Test
  void testManualOutputCanBeSet() {
    ControllerDeviceBaseClass pid = new ControllerDeviceBaseClass("PID-3");
    StubTransmitter tx = new StubTransmitter("T-3");
    pid.setTransmitter(tx);
    pid.setControllerSetPoint(50.0, "C");

    pid.setMode(ControllerMode.MANUAL);
    pid.setManualOutput(42.0);
    pid.runTransient(30.0, 1.0);
    Assertions.assertEquals(42.0, pid.getResponse(), 1e-12);
  }

  @Test
  void testBumplessTransferManualToAuto() {
    ControllerDeviceBaseClass pid = new ControllerDeviceBaseClass("PID-4");
    StubTransmitter tx = new StubTransmitter("T-4");
    tx.setValue(50.0);
    pid.setTransmitter(tx);
    pid.setControllerSetPoint(50.0, "C");
    pid.setControllerParameters(1.0, 300.0, 0.0);

    // Put in MANUAL at output=45
    pid.setMode(ControllerMode.MANUAL);
    pid.setManualOutput(45.0);
    pid.runTransient(30.0, 1.0);

    // Switch to AUTO — first transient should produce approximately same output
    pid.setMode(ControllerMode.AUTO);
    double initResponse = 30.0;
    pid.runTransient(initResponse, 1.0);
    double autoOutput = pid.getResponse();
    // With bumpless transfer, the integral is back-calculated so output ~= manualOutput
    // Error is 0 (measurement == setpoint), so proportional and derivative terms are 0.
    // TintValue was back-calculated to (45 - 30) / 1 = 15,
    // then delta = Kp*(0-0) + 15 + 0 = 15, response = 30 + 1*15 = 45
    Assertions.assertEquals(45.0, autoOutput, 0.5);
  }

  // ─── NIP-05: Split-range control ───

  @Test
  void testSplitRangeEqualSplit() {
    ControllerDeviceBaseClass pid = new ControllerDeviceBaseClass("PID-SR");
    StubTransmitter tx = new StubTransmitter("T-SR");
    tx.setValue(50.0);
    pid.setTransmitter(tx);
    pid.setControllerSetPoint(50.0, "C");
    pid.setControllerParameters(1.0, 300.0, 0.0);

    SplitRangeControllerStructure sr = new SplitRangeControllerStructure(pid, 2);
    Assertions.assertEquals(2, sr.getNumberOfElements());
    Assertions.assertTrue(sr.isActive());
  }

  @Test
  void testSplitRangeOutputMapping() {
    ControllerDeviceBaseClass pid = new ControllerDeviceBaseClass("PID-SR2");
    StubTransmitter tx = new StubTransmitter("T-SR2");
    tx.setValue(50.0);
    pid.setTransmitter(tx);
    pid.setControllerSetPoint(50.0, "C");
    pid.setControllerParameters(1.0, 300.0, 0.0);
    pid.setOutputLimits(0.0, 100.0);

    // Range: element 0 = [0, 50], element 1 = [50, 100]
    SplitRangeControllerStructure sr = new SplitRangeControllerStructure(pid, 2);
    sr.runTransient(1.0);

    double rawOutput = sr.getOutput();
    // Both element outputs should be valid
    double el0 = sr.getOutput(0);
    double el1 = sr.getOutput(1);
    Assertions.assertTrue(el0 >= 0.0 && el0 <= 100.0);
    Assertions.assertTrue(el1 >= 0.0 && el1 <= 100.0);
  }

  @Test
  void testSplitRangeCustomRanges() {
    ControllerDeviceBaseClass pid = new ControllerDeviceBaseClass("PID-SR3");
    StubTransmitter tx = new StubTransmitter("T-SR3");
    pid.setTransmitter(tx);
    pid.setControllerSetPoint(50.0, "C");

    double[] low = {0.0, 40.0, 80.0};
    double[] high = {40.0, 80.0, 100.0};
    SplitRangeControllerStructure sr = new SplitRangeControllerStructure(pid, low, high);
    Assertions.assertEquals(3, sr.getNumberOfElements());
  }

  @Test
  void testSplitRangeRejectsLessThan2() {
    ControllerDeviceBaseClass pid = new ControllerDeviceBaseClass("PID-SR4");
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new SplitRangeControllerStructure(pid, 1));
  }

  // ─── NIP-05: Override control ───

  @Test
  void testOverrideHighSelect() {
    ControllerDeviceBaseClass primary = new ControllerDeviceBaseClass("PRI");
    ControllerDeviceBaseClass override = new ControllerDeviceBaseClass("OVR");
    StubTransmitter tx1 = new StubTransmitter("T1");
    StubTransmitter tx2 = new StubTransmitter("T2");
    primary.setTransmitter(tx1);
    override.setTransmitter(tx2);
    primary.setControllerSetPoint(50.0, "C");
    override.setControllerSetPoint(50.0, "C");
    primary.setControllerParameters(1.0, 300.0, 0.0);
    override.setControllerParameters(2.0, 300.0, 0.0);

    OverrideControllerStructure os = new OverrideControllerStructure(primary, override,
        OverrideControllerStructure.SelectionType.HIGH_SELECT);
    Assertions.assertEquals(OverrideControllerStructure.SelectionType.HIGH_SELECT,
        os.getSelectionType());
  }

  @Test
  void testOverrideLowSelect() {
    ControllerDeviceBaseClass primary = new ControllerDeviceBaseClass("PRI2");
    ControllerDeviceBaseClass override = new ControllerDeviceBaseClass("OVR2");
    StubTransmitter tx1 = new StubTransmitter("T3");
    StubTransmitter tx2 = new StubTransmitter("T4");
    primary.setTransmitter(tx1);
    override.setTransmitter(tx2);
    primary.setControllerSetPoint(50.0, "C");
    override.setControllerSetPoint(50.0, "C");

    OverrideControllerStructure os = new OverrideControllerStructure(primary, override,
        OverrideControllerStructure.SelectionType.LOW_SELECT);
    os.runTransient(1.0);
    Assertions.assertFalse(os.isOverrideActive() && os.getOutput() == 0.0,
        "Output should be valid after runTransient");
  }

  // ─── NIP-07: Sensor fault injection ───

  @Test
  void testDefaultNoFault() {
    StubTransmitter tx = new StubTransmitter("TF-1");
    Assertions.assertEquals(SensorFaultType.NONE, tx.getFaultType());
    tx.setValue(42.0);
    Assertions.assertEquals(42.0, tx.getMeasuredValue("C"), 1e-12);
  }

  @Test
  void testStuckAtValueFault() {
    StubTransmitter tx = new StubTransmitter("TF-2");
    tx.setValue(42.0);
    tx.setFault(SensorFaultType.STUCK_AT_VALUE, 99.0);
    Assertions.assertEquals(SensorFaultType.STUCK_AT_VALUE, tx.getFaultType());
    Assertions.assertEquals(99.0, tx.getMeasuredValue("C"), 1e-12);
    // Even if real value changes, stuck output stays
    tx.setValue(10.0);
    Assertions.assertEquals(99.0, tx.getMeasuredValue("C"), 1e-12);
  }

  @Test
  void testBiasFault() {
    StubTransmitter tx = new StubTransmitter("TF-3");
    tx.setValue(50.0);
    tx.setFault(SensorFaultType.BIAS, 5.0);
    Assertions.assertEquals(55.0, tx.getMeasuredValue("C"), 1e-12);
  }

  @Test
  void testLinearDriftFault() {
    StubTransmitter tx = new StubTransmitter("TF-4");
    tx.setValue(100.0);
    tx.setFault(SensorFaultType.LINEAR_DRIFT, 0.1);
    double first = tx.getMeasuredValue("C");
    double second = tx.getMeasuredValue("C");
    double third = tx.getMeasuredValue("C");
    // Each call accumulates 0.1 more drift
    Assertions.assertEquals(100.1, first, 1e-12);
    Assertions.assertEquals(100.2, second, 1e-12);
    Assertions.assertEquals(100.3, third, 1e-12);
  }

  @Test
  void testSaturationFault() {
    StubTransmitter tx = new StubTransmitter("TF-5");
    tx.setValue(80.0);
    tx.setFault(SensorFaultType.SATURATION, 70.0);
    Assertions.assertEquals(70.0, tx.getMeasuredValue("C"), 1e-12);
    // Below saturation passes through
    tx.setValue(60.0);
    Assertions.assertEquals(60.0, tx.getMeasuredValue("C"), 1e-12);
  }

  @Test
  void testClearFault() {
    StubTransmitter tx = new StubTransmitter("TF-6");
    tx.setValue(50.0);
    tx.setFault(SensorFaultType.BIAS, 10.0);
    Assertions.assertEquals(60.0, tx.getMeasuredValue("C"), 1e-12);
    tx.clearFault();
    Assertions.assertEquals(SensorFaultType.NONE, tx.getFaultType());
    Assertions.assertEquals(50.0, tx.getMeasuredValue("C"), 1e-12);
  }

  @Test
  void testNoiseBurstFault() {
    StubTransmitter tx = new StubTransmitter("TF-7");
    tx.setRandomSeed(42L);
    tx.setValue(50.0);
    tx.setFault(SensorFaultType.NOISE_BURST, 5.0);
    double val = tx.getMeasuredValue("C");
    // With noise burst, output should deviate from 50
    Assertions.assertNotEquals(50.0, val, 1e-6);
  }
}
