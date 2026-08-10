package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentBaseClass;

/** Baseline physical-step failure semantics that parallel execution must preserve. */
class ProcessSystemTransientFailureContractTest {
  @Test
  void sequentialEquipmentFailureStopsDownstreamExecutionAndStepCommit() {
    ProcessSystem process = new ProcessSystem("sequential transient failure");
    FailingTransientEquipment failing = new FailingTransientEquipment("failing unit");
    RecordingTransientEquipment downstream = new RecordingTransientEquipment("downstream unit");
    process.add(failing);
    process.add(downstream);

    UUID stepId = UUID.randomUUID();
    IllegalStateException failure = assertThrows(IllegalStateException.class, () -> process.runTransient(1.0, stepId));

    assertEquals("intentional transient failure", failure.getMessage());
    assertEquals(1, failing.getTransientCalls());
    assertEquals(0, downstream.getTransientCalls(), "downstream equipment must not run after a failed unit");
    assertNotEquals(stepId, process.getCalculationIdentifier(),
        "a failed physical step must not commit the ProcessSystem calculation identifier");
    assertEquals(0, process.getHistorySize(), "a failed physical step must not append measurement history");
    assertEquals(1.0, process.getTime(), 0.0,
        "the current engine advances time before equipment; whole-step rollback remains a separate Phase-0 dependency");
  }

  private static final class FailingTransientEquipment extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000L;
    private int transientCalls;

    private FailingTransientEquipment(String name) {
      super(name);
    }

    @Override
    public void run(UUID id) {
      setCalculationIdentifier(id);
    }

    @Override
    public void runTransient(double dt, UUID id) {
      transientCalls++;
      throw new IllegalStateException("intentional transient failure");
    }

    private int getTransientCalls() {
      return transientCalls;
    }
  }

  private static final class RecordingTransientEquipment extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000L;
    private int transientCalls;

    private RecordingTransientEquipment(String name) {
      super(name);
    }

    @Override
    public void run(UUID id) {
      setCalculationIdentifier(id);
    }

    @Override
    public void runTransient(double dt, UUID id) {
      transientCalls++;
      setCalculationIdentifier(id);
    }

    private int getTransientCalls() {
      return transientCalls;
    }
  }
}
