package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import neqsim.process.measurementdevice.MeasurementDeviceBaseClass;

/** Tests for the ProcessSystem history buffer and reset behaviour. */
public class ProcessSystemResetTest extends neqsim.NeqSimTest {
  @Test
  public void historyGrowsBeyondOriginalCapacity() {
    ProcessSystem process = new ProcessSystem();
    int steps = 12_500;
    process.setTimeStep(1.0);

    for (int i = 0; i < steps; i++) {
      process.runTransient();
    }

    assertEquals(steps, process.getHistorySize());
  }

  @Test
  public void resetRestoresInitialState() {
    ProcessSystem process = new ProcessSystem();
    process.setTimeStep(5.0);
    process.setSurroundingTemperature(300.0);
    process.storeInitialState();

    process.runTransient();
    process.setSurroundingTemperature(310.0);
    process.runTransient();

    process.reset();

    assertEquals(300.0, process.getSurroundingTemperature(), 1e-9);
    assertEquals(0.0, process.getTime(), 1e-9);
    assertEquals(0, process.getHistorySize());

    process.runTransient();
    assertEquals(1, process.getHistorySize());
  }

  @Test
  public void resetWithoutStoredStateThrows() {
    ProcessSystem process = new ProcessSystem();
    assertThrows(IllegalStateException.class, process::reset);
  }

  @Test
  public void historyRecordingCanBeDisabledWithoutSkippingMeasurements() {
    ProcessSystem process = new ProcessSystem();
    AtomicInteger measurements = new AtomicInteger();
    process.add(new CountingMeasurement(measurements));

    assertTrue(process.isMeasurementHistoryRecordingEnabled());
    process.setMeasurementHistoryRecordingEnabled(false);
    process.runTransient();
    process.runTransient();

    assertFalse(process.isMeasurementHistoryRecordingEnabled());
    assertEquals(0, process.getHistorySize());
    assertEquals(2, measurements.get());

    process.setMeasurementHistoryRecordingEnabled(true);
    process.runTransient();
    assertEquals(1, process.getHistorySize());
    assertEquals(3, measurements.get());
  }

  @Test
  public void resetRestoresHistoryRecordingSetting() {
    ProcessSystem process = new ProcessSystem();
    process.setMeasurementHistoryRecordingEnabled(false);
    process.storeInitialState();

    process.setMeasurementHistoryRecordingEnabled(true);
    process.runTransient();
    process.reset();

    assertFalse(process.isMeasurementHistoryRecordingEnabled());
    assertEquals(0, process.getHistorySize());
  }

  private static final class CountingMeasurement extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1000L;
    private final AtomicInteger measurements;

    private CountingMeasurement(AtomicInteger measurements) {
      super("counter", "-");
      this.measurements = measurements;
    }

    @Override
    public double getMeasuredValue(String unit) {
      measurements.incrementAndGet();
      return 1.0;
    }
  }
}
