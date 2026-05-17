package neqsim.process.controllerdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.alarm.AlarmConfig;
import neqsim.process.alarm.AlarmEvent;
import neqsim.process.alarm.AlarmState;
import neqsim.process.measurementdevice.InstrumentTagRole;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.measurementdevice.online.OnlineSignal;

/**
 * Tests for {@link TransferFunctionBlock}.
 */
class TransferFunctionBlockTest {

  /**
   * Simple stub that returns a configurable value.
   */
  static class StubTransmitter implements MeasurementDeviceInterface {
    private double value;
    private String name = "stub";

    StubTransmitter(double value) {
      this.value = value;
    }

    void setValue(double value) {
      this.value = value;
    }

    @Override
    public void displayResult() {}

    @Override
    public double getMeasuredValue(String unit) {
      return value;
    }

    @Override
    public OnlineSignal getOnlineSignal() {
      return null;
    }

    @Override
    public double getMeasuredPercentValue() {
      return value;
    }

    @Override
    public String getUnit() {
      return "bar";
    }

    @Override
    public void setUnit(String unit) {}

    @Override
    public double getMaximumValue() {
      return 200.0;
    }

    @Override
    public double getMinimumValue() {
      return 0.0;
    }

    @Override
    public void setMaximumValue(double maxValue) {}

    @Override
    public void setMinimumValue(double minValue) {}

    @Override
    public boolean isLogging() {
      return false;
    }

    @Override
    public void setLogging(boolean logging) {}

    @Override
    public boolean isOnlineSignal() {
      return false;
    }

    @Override
    public void setAlarmConfig(AlarmConfig config) {}

    @Override
    public AlarmConfig getAlarmConfig() {
      return null;
    }

    @Override
    public AlarmState getAlarmState() {
      return null;
    }

    @Override
    public List<AlarmEvent> evaluateAlarm(double measuredValue, double dt, double time) {
      return new ArrayList<>();
    }

    @Override
    public AlarmEvent acknowledgeAlarm(double time) {
      return null;
    }

    @Override
    public String getTag() {
      return "";
    }

    @Override
    public void setTag(String tag) {}

    @Override
    public InstrumentTagRole getTagRole() {
      return InstrumentTagRole.VIRTUAL;
    }

    @Override
    public void setTagRole(InstrumentTagRole role) {}

    @Override
    public double getFieldValue() {
      return Double.NaN;
    }

    @Override
    public void setFieldValue(double value) {}

    @Override
    public boolean hasFieldValue() {
      return false;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public void setName(String name) {
      this.name = name;
    }

    @Override
    public void setTagNumber(String tagNumber) {}

    @Override
    public String getTagNumber() {
      return "";
    }
  }

  @Test
  void testFirstOrderLagSteadyState() {
    // After many time steps, first-order lag should converge to K * u
    TransferFunctionBlock block =
        new TransferFunctionBlock("lag", TransferFunctionBlock.Type.FIRST_ORDER_LAG);
    block.setGain(2.0);
    block.setLagTime(10.0);
    block.setInputBias(0.0);
    block.setOutputBias(0.0);

    StubTransmitter transmitter = new StubTransmitter(5.0);
    block.setTransmitter(transmitter);

    // Run many steps to reach steady state
    UUID id = UUID.randomUUID();
    for (int i = 0; i < 1000; i++) {
      block.runTransient(0.0, 1.0, id);
    }

    // Should converge to K * u = 2.0 * 5.0 = 10.0
    assertEquals(10.0, block.getOutput(), 0.01);
  }

  @Test
  void testFirstOrderLagTransientBehavior() {
    TransferFunctionBlock block =
        new TransferFunctionBlock("lag", TransferFunctionBlock.Type.FIRST_ORDER_LAG);
    block.setGain(1.0);
    block.setLagTime(10.0);

    StubTransmitter transmitter = new StubTransmitter(0.0);
    block.setTransmitter(transmitter);

    UUID id = UUID.randomUUID();
    // Initial step to initialize
    block.runTransient(0.0, 1.0, id);

    // Step change in input
    transmitter.setValue(10.0);

    // After one time constant (tau=10s, dt=1s), should reach ~63.2% of final value
    for (int i = 0; i < 10; i++) {
      block.runTransient(0.0, 1.0, id);
    }

    double expectedApprox = 10.0 * (1.0 - Math.exp(-1.0)); // ~6.32
    assertEquals(expectedApprox, block.getOutput(), 1.0);
  }

  @Test
  void testLeadLagSteadyState() {
    // At steady state, lead-lag should pass through as K * u
    TransferFunctionBlock block =
        new TransferFunctionBlock("leadlag", TransferFunctionBlock.Type.LEAD_LAG);
    block.setGain(1.5);
    block.setLeadTime(5.0);
    block.setLagTime(20.0);

    StubTransmitter transmitter = new StubTransmitter(10.0);
    block.setTransmitter(transmitter);

    UUID id = UUID.randomUUID();
    for (int i = 0; i < 2000; i++) {
      block.runTransient(0.0, 1.0, id);
    }

    // Steady state: K * u = 1.5 * 10.0 = 15.0
    assertEquals(15.0, block.getOutput(), 0.1);
  }

  @Test
  void testDeadTime() {
    TransferFunctionBlock block =
        new TransferFunctionBlock("delay", TransferFunctionBlock.Type.DEAD_TIME);
    block.setGain(1.0);
    block.setDeadTime(5.0); // 5 second delay

    StubTransmitter transmitter = new StubTransmitter(0.0);
    block.setTransmitter(transmitter);

    UUID id = UUID.randomUUID();

    // Initialize with zero
    block.runTransient(0.0, 1.0, id);

    // Step change at t=1
    transmitter.setValue(10.0);

    // Run for less than dead time - output should still be ~0
    for (int i = 0; i < 3; i++) {
      block.runTransient(0.0, 1.0, id);
    }
    assertEquals(0.0, block.getOutput(), 0.1);

    // Run past dead time - output should reach the step value
    for (int i = 0; i < 5; i++) {
      block.runTransient(0.0, 1.0, id);
    }
    assertEquals(10.0, block.getOutput(), 0.1);
  }

  @Test
  void testSecondOrderSteadyState() {
    TransferFunctionBlock block =
        new TransferFunctionBlock("2nd", TransferFunctionBlock.Type.SECOND_ORDER);
    block.setGain(3.0);
    block.setLagTime(5.0);
    block.setLagTime2(10.0);

    StubTransmitter transmitter = new StubTransmitter(4.0);
    block.setTransmitter(transmitter);

    UUID id = UUID.randomUUID();
    for (int i = 0; i < 2000; i++) {
      block.runTransient(0.0, 1.0, id);
    }

    // Steady state: K * u = 3.0 * 4.0 = 12.0
    assertEquals(12.0, block.getOutput(), 0.1);
  }

  @Test
  void testWithBias() {
    TransferFunctionBlock block =
        new TransferFunctionBlock("bias", TransferFunctionBlock.Type.FIRST_ORDER_LAG);
    block.setGain(2.0);
    block.setLagTime(5.0);
    block.setInputBias(10.0);
    block.setOutputBias(50.0);

    // Input = 15.0, deviation u = 15 - 10 = 5
    // Steady state output = K*u + bias = 2.0*5.0 + 50.0 = 60.0
    StubTransmitter transmitter = new StubTransmitter(15.0);
    block.setTransmitter(transmitter);

    UUID id = UUID.randomUUID();
    for (int i = 0; i < 1000; i++) {
      block.runTransient(0.0, 1.0, id);
    }

    assertEquals(60.0, block.getOutput(), 0.1);
  }

  @Test
  void testReset() {
    TransferFunctionBlock block =
        new TransferFunctionBlock("reset", TransferFunctionBlock.Type.FIRST_ORDER_LAG);
    block.setGain(1.0);
    block.setLagTime(5.0);
    block.setOutputBias(10.0);

    StubTransmitter transmitter = new StubTransmitter(100.0);
    block.setTransmitter(transmitter);

    UUID id = UUID.randomUUID();
    for (int i = 0; i < 100; i++) {
      block.runTransient(0.0, 1.0, id);
    }
    assertTrue(block.getOutput() > 50.0);

    block.reset();
    assertEquals(10.0, block.getOutput(), 1e-10); // output bias
  }

  @Test
  void testInactiveBlock() {
    TransferFunctionBlock block =
        new TransferFunctionBlock("inactive", TransferFunctionBlock.Type.FIRST_ORDER_LAG);
    block.setGain(10.0);
    block.setActive(false);

    StubTransmitter transmitter = new StubTransmitter(100.0);
    block.setTransmitter(transmitter);

    block.runTransient(42.0, 1.0, UUID.randomUUID());

    // Inactive block should pass through initResponse
    assertEquals(42.0, block.getOutput(), 1e-10);
  }

  @Test
  void testWithoutTransmitter() {
    // When no transmitter is attached, use initResponse as input
    TransferFunctionBlock block =
        new TransferFunctionBlock("noTx", TransferFunctionBlock.Type.FIRST_ORDER_LAG);
    block.setGain(1.0);
    block.setLagTime(1.0);

    UUID id = UUID.randomUUID();
    for (int i = 0; i < 100; i++) {
      block.runTransient(5.0, 1.0, id);
    }

    // Steady state should converge to K * u = 1.0 * 5.0 = 5.0
    assertEquals(5.0, block.getOutput(), 0.1);
  }

  @Test
  void testFirstOrderLagWithDeadTime() {
    TransferFunctionBlock block =
        new TransferFunctionBlock("lag+dt", TransferFunctionBlock.Type.FIRST_ORDER_LAG);
    block.setGain(1.0);
    block.setLagTime(5.0);
    block.setDeadTime(3.0);

    StubTransmitter transmitter = new StubTransmitter(0.0);
    block.setTransmitter(transmitter);

    UUID id = UUID.randomUUID();
    block.runTransient(0.0, 1.0, id);

    // Step change
    transmitter.setValue(10.0);

    // During dead time, output should still be close to zero
    block.runTransient(0.0, 1.0, id);
    block.runTransient(0.0, 1.0, id);
    assertEquals(0.0, block.getOutput(), 0.5);

    // After dead time + long enough for lag, should approach steady state
    for (int i = 0; i < 200; i++) {
      block.runTransient(0.0, 1.0, id);
    }
    assertEquals(10.0, block.getOutput(), 0.5);
  }

  @Test
  void testGettersAndSetters() {
    TransferFunctionBlock block =
        new TransferFunctionBlock("props", TransferFunctionBlock.Type.LEAD_LAG);
    block.setGain(2.5);
    block.setLagTime(30.0);
    block.setLeadTime(10.0);
    block.setLagTime2(15.0);
    block.setDeadTime(4.0);
    block.setInputBias(5.0);
    block.setOutputBias(50.0);

    assertEquals(TransferFunctionBlock.Type.LEAD_LAG, block.getType());
    assertEquals(2.5, block.getGain(), 1e-10);
    assertEquals(30.0, block.getLagTime(), 1e-10);
    assertEquals(10.0, block.getLeadTime(), 1e-10);
    assertEquals(15.0, block.getLagTime2(), 1e-10);
    assertEquals(4.0, block.getDeadTime(), 1e-10);
    assertEquals(5.0, block.getInputBias(), 1e-10);
    assertEquals(50.0, block.getOutputBias(), 1e-10);
  }
}
