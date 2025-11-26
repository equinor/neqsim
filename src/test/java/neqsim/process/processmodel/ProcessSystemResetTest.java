package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

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
}
