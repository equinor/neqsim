package neqsim.process.controllerdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Tests for {@link LogicBlock}.
 */
class LogicBlockTest {

  /**
   * Simple stub measurement device that returns a configurable value.
   */
  static class StubMeasurement implements MeasurementDeviceInterface {
    private double value;
    private String name;

    StubMeasurement(String name, double value) {
      this.name = name;
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
  void testAndOperator_allTrue() {
    LogicBlock block = new LogicBlock("AND-test", LogicBlock.Operator.AND);
    block.addInput(new StubMeasurement("P1", 130.0), 100.0, LogicBlock.Comparator.GREATER_THAN);
    block.addInput(new StubMeasurement("T1", 90.0), 80.0, LogicBlock.Comparator.GREATER_THAN);

    block.runTransient(0.0, 1.0, UUID.randomUUID());

    assertTrue(block.getOutputBoolean());
    assertEquals(1.0, block.getOutput(), 1e-10);
  }

  @Test
  void testAndOperator_oneFalse() {
    LogicBlock block = new LogicBlock("AND-test", LogicBlock.Operator.AND);
    block.addInput(new StubMeasurement("P1", 130.0), 100.0, LogicBlock.Comparator.GREATER_THAN);
    block.addInput(new StubMeasurement("T1", 70.0), 80.0, LogicBlock.Comparator.GREATER_THAN);

    block.runTransient(0.0, 1.0, UUID.randomUUID());

    assertFalse(block.getOutputBoolean());
    assertEquals(0.0, block.getOutput(), 1e-10);
  }

  @Test
  void testOrOperator() {
    LogicBlock block = new LogicBlock("OR-test", LogicBlock.Operator.OR);
    block.addInput(new StubMeasurement("P1", 50.0), 100.0, LogicBlock.Comparator.GREATER_THAN);
    block.addInput(new StubMeasurement("T1", 90.0), 80.0, LogicBlock.Comparator.GREATER_THAN);

    block.runTransient(0.0, 1.0, UUID.randomUUID());

    assertTrue(block.getOutputBoolean());
  }

  @Test
  void testOrOperator_allFalse() {
    LogicBlock block = new LogicBlock("OR-test", LogicBlock.Operator.OR);
    block.addInput(new StubMeasurement("P1", 50.0), 100.0, LogicBlock.Comparator.GREATER_THAN);
    block.addInput(new StubMeasurement("T1", 70.0), 80.0, LogicBlock.Comparator.GREATER_THAN);

    block.runTransient(0.0, 1.0, UUID.randomUUID());

    assertFalse(block.getOutputBoolean());
  }

  @Test
  void testNotOperator() {
    LogicBlock block = new LogicBlock("NOT-test", LogicBlock.Operator.NOT);
    block.addInput(new StubMeasurement("P1", 50.0), 100.0, LogicBlock.Comparator.GREATER_THAN);

    block.runTransient(0.0, 1.0, UUID.randomUUID());

    // 50 > 100 is false, NOT false = true
    assertTrue(block.getOutputBoolean());
  }

  @Test
  void testNandOperator() {
    LogicBlock block = new LogicBlock("NAND-test", LogicBlock.Operator.NAND);
    block.addInput(new StubMeasurement("P1", 130.0), 100.0, LogicBlock.Comparator.GREATER_THAN);
    block.addInput(new StubMeasurement("T1", 90.0), 80.0, LogicBlock.Comparator.GREATER_THAN);

    block.runTransient(0.0, 1.0, UUID.randomUUID());

    // Both true, NAND = false
    assertFalse(block.getOutputBoolean());
  }

  @Test
  void testXorOperator() {
    LogicBlock block = new LogicBlock("XOR-test", LogicBlock.Operator.XOR);
    block.addInput(new StubMeasurement("P1", 130.0), 100.0, LogicBlock.Comparator.GREATER_THAN); // true
    block.addInput(new StubMeasurement("T1", 70.0), 80.0, LogicBlock.Comparator.GREATER_THAN); // false

    block.runTransient(0.0, 1.0, UUID.randomUUID());

    // One true, one false -> XOR = true
    assertTrue(block.getOutputBoolean());
  }

  @Test
  void testLessThanComparator() {
    LogicBlock block = new LogicBlock("LT-test", LogicBlock.Operator.AND);
    block.addInput(new StubMeasurement("level", 30.0), 50.0, LogicBlock.Comparator.LESS_THAN);

    block.runTransient(0.0, 1.0, UUID.randomUUID());

    assertTrue(block.getOutputBoolean());
  }

  @Test
  void testEqualComparator() {
    LogicBlock block = new LogicBlock("EQ-test", LogicBlock.Operator.AND);
    block.setEqualityTolerance(0.5);
    block.addInput(new StubMeasurement("setpoint", 50.1), 50.0, LogicBlock.Comparator.EQUAL);

    block.runTransient(0.0, 1.0, UUID.randomUUID());

    assertTrue(block.getOutputBoolean());
  }

  @Test
  void testFixedInput() {
    LogicBlock block = new LogicBlock("fixed-test", LogicBlock.Operator.AND);
    block.addFixedInput(true);
    block.addFixedInput(true);

    block.runTransient(0.0, 1.0, UUID.randomUUID());

    assertTrue(block.getOutputBoolean());
  }

  @Test
  void testChainedLogicBlocks() {
    // First block: pressure high
    LogicBlock pressureHigh = new LogicBlock("P-high", LogicBlock.Operator.AND);
    pressureHigh.addInput(new StubMeasurement("P1", 150.0), 100.0,
        LogicBlock.Comparator.GREATER_THAN);

    // Second block: temperature high
    LogicBlock tempHigh = new LogicBlock("T-high", LogicBlock.Operator.AND);
    tempHigh.addInput(new StubMeasurement("T1", 90.0), 80.0, LogicBlock.Comparator.GREATER_THAN);

    // Composite: both must be true
    LogicBlock esdTrigger = new LogicBlock("ESD", LogicBlock.Operator.AND);
    esdTrigger.addInput(pressureHigh);
    esdTrigger.addInput(tempHigh);

    // Run upstream blocks first
    pressureHigh.runTransient(0.0, 1.0, UUID.randomUUID());
    tempHigh.runTransient(0.0, 1.0, UUID.randomUUID());
    esdTrigger.runTransient(0.0, 1.0, UUID.randomUUID());

    assertTrue(esdTrigger.getOutputBoolean());
  }

  @Test
  void testInactiveBlock() {
    LogicBlock block = new LogicBlock("inactive", LogicBlock.Operator.AND);
    block.addFixedInput(true);
    block.setActive(false);

    block.runTransient(0.0, 1.0, UUID.randomUUID());

    // Inactive block should not change output (stays at default 0.0)
    assertEquals(0.0, block.getOutput(), 1e-10);
  }

  @Test
  void testDynamicSignalChange() {
    StubMeasurement pressure = new StubMeasurement("P1", 80.0);
    LogicBlock block = new LogicBlock("dynamic", LogicBlock.Operator.AND);
    block.addInput(pressure, 100.0, LogicBlock.Comparator.GREATER_THAN);

    // Initially below threshold
    block.runTransient(0.0, 1.0, UUID.randomUUID());
    assertFalse(block.getOutputBoolean());

    // Signal rises above threshold
    pressure.setValue(120.0);
    block.runTransient(0.0, 1.0, UUID.randomUUID());
    assertTrue(block.getOutputBoolean());

    // Signal drops back
    pressure.setValue(90.0);
    block.runTransient(0.0, 1.0, UUID.randomUUID());
    assertFalse(block.getOutputBoolean());
  }
}
