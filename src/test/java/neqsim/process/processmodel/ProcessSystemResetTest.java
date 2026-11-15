package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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

  @Test
  public void resetRestoresStateIntoExistingObjects() {
    SeparatorControlFlowsheet sheet = new SeparatorControlFlowsheet();
    ProcessSystem process = sheet.process;
    process.setTimeStep(2.0);
    process.storeInitialState();
    assertTrue(process.isInPlaceResetAvailable());
    double initialLevel = sheet.separator.getLiquidLevel();
    double initialPressure = sheet.pressureBara();

    double[] firstRun = new double[30];
    for (int i = 0; i < firstRun.length; i++) {
      process.runTransient();
      firstRun[i] = sheet.separator.getLiquidLevel();
    }
    double inletOpeningAfterRun = sheet.inletValve.getPercentValveOpening();

    process.reset();

    // References held by the caller (e.g. a Python or Gymnasium environment) still drive the model.
    assertSame(sheet.separator, process.getUnit("V-001"));
    assertSame(sheet.inletValve, process.getUnit("LCV-00"));
    assertSame(sheet.levelController, sheet.inletValve.getController());
    assertEquals(initialLevel, sheet.separator.getLiquidLevel(), 1.0e-12);
    assertEquals(initialPressure, sheet.pressureBara(), 1.0e-9);
    assertEquals(0.0, process.getTime(), 0.0);
    assertEquals(0, process.getHistorySize());
    assertTrue(Math.abs(sheet.inletValve.getPercentValveOpening() - inletOpeningAfterRun) > 1.0e-6);

    for (int i = 0; i < firstRun.length; i++) {
      process.runTransient();
      assertEquals(firstRun[i], sheet.separator.getLiquidLevel(), 1.0e-9, "level differs at step " + i);
    }

    // Reset can be repeated; each reset restores a fresh copy of the stored state.
    process.reset();
    assertEquals(initialLevel, sheet.separator.getLiquidLevel(), 1.0e-12);
  }

  @Test
  public void resetAfterStructureChangeReplacesObjects() {
    SeparatorControlFlowsheet sheet = new SeparatorControlFlowsheet();
    ProcessSystem process = sheet.process;
    process.storeInitialState();
    process.add(new neqsim.process.equipment.stream.Stream("extra", sheet.separator.getGasOutStream()));
    assertFalse(process.isInPlaceResetAvailable());

    process.reset();

    // The stored copy is restored; the caller must fetch equipment again.
    assertTrue(process.getUnit("V-001") != sheet.separator);
    assertTrue(process.getUnit("extra") == null);
    assertTrue(process.isInPlaceResetAvailable());
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
