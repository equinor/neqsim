package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Baseline physical-step failure semantics that parallel execution must preserve. */
class ProcessSystemTransientFailureContractTest {
  @Test
  void sequentialEquipmentFailureStopsDownstreamExecutionAndStepCommit() {
    FailureScenario scenario = createFailureScenario("sequential transient failure", false);

    assertFailureBoundary(scenario);
  }

  @Test
  void parallelEquipmentFailureMatchesSequentialFailLoudStepBoundary() {
    FailureScenario scenario = createFailureScenario("parallel transient failure", true);

    assertFailureBoundary(scenario);
  }

  @Test
  void processModelStopsLaterAreasAfterParallelAreaFailure() {
    FailureScenario failingArea = createFailureScenario("failing area", true);
    ProcessSystem laterArea = new ProcessSystem("later area");
    RecordingTransientEquipment laterEquipment = new RecordingTransientEquipment("later equipment");
    laterArea.add(laterEquipment);

    ProcessModel model = new ProcessModel();
    model.add("failing", failingArea.process);
    model.add("later", laterArea);

    UUID stepId = UUID.randomUUID();
    IllegalStateException failure = assertThrows(IllegalStateException.class, () -> model.runTransient(1.0, stepId));

    assertEquals("intentional transient failure", failure.getMessage());
    assertEquals(0, laterEquipment.getTransientCalls(),
        "a later ProcessModel area must not run after an earlier area fails");
    assertEquals(0.0, laterArea.getTime(), 0.0,
        "a later ProcessModel area must not advance its clock after an earlier area fails");
  }

  private static FailureScenario createFailureScenario(String name, boolean parallel) {
    Stream dependency = new Stream(name + " dependency", new SystemSrkEos(298.15, 1.0));
    FailingTransientEquipment failing = new FailingTransientEquipment("failing unit", dependency);
    RecordingTransientEquipment downstream = new RecordingTransientEquipment("downstream unit", dependency);
    RecordingController controller = new RecordingController("standalone controller");

    ProcessSystem process = new ProcessSystem(name);
    process.add(failing);
    process.add(downstream);
    process.add(controller);
    process.setParallelTransientEnabled(parallel);
    process.setTransientThreadPoolSize(1);
    return new FailureScenario(process, failing, downstream, controller);
  }

  private static void assertFailureBoundary(FailureScenario scenario) {
    UUID stepId = UUID.randomUUID();
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> scenario.process.runTransient(1.0, stepId));

    assertEquals("intentional transient failure", failure.getMessage());
    assertEquals(1, scenario.failing.getTransientCalls());
    assertEquals(0, scenario.downstream.getTransientCalls(), "downstream equipment must not run after a failed unit");
    assertEquals(0, scenario.controller.getTransientCalls(),
        "standalone controllers must not run after equipment failure");
    assertNotEquals(stepId, scenario.process.getCalculationIdentifier(),
        "a failed physical step must not commit the ProcessSystem calculation identifier");
    assertEquals(0, scenario.process.getHistorySize(), "a failed physical step must not append measurement history");
    assertEquals(1.0, scenario.process.getTime(), 0.0,
        "time advances before equipment; whole-step rollback remains a separate Phase-0 dependency");
  }

  private static final class FailureScenario {
    private final ProcessSystem process;
    private final FailingTransientEquipment failing;
    private final RecordingTransientEquipment downstream;
    private final RecordingController controller;

    private FailureScenario(ProcessSystem process, FailingTransientEquipment failing,
        RecordingTransientEquipment downstream, RecordingController controller) {
      this.process = process;
      this.failing = failing;
      this.downstream = downstream;
      this.controller = controller;
    }
  }

  private static final class FailingTransientEquipment extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000L;
    private final StreamInterface outlet;
    private int transientCalls;

    private FailingTransientEquipment(String name, StreamInterface outlet) {
      super(name);
      this.outlet = outlet;
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

    @Override
    public List<StreamInterface> getOutletStreams() {
      return Collections.singletonList(outlet);
    }

    private int getTransientCalls() {
      return transientCalls;
    }
  }

  private static final class RecordingTransientEquipment extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000L;
    private final StreamInterface inlet;
    private int transientCalls;

    private RecordingTransientEquipment(String name) {
      this(name, null);
    }

    private RecordingTransientEquipment(String name, StreamInterface inlet) {
      super(name);
      this.inlet = inlet;
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

    @Override
    public List<StreamInterface> getInletStreams() {
      return inlet == null ? Collections.<StreamInterface>emptyList() : Collections.singletonList(inlet);
    }

    private int getTransientCalls() {
      return transientCalls;
    }
  }

  private static final class RecordingController extends ControllerDeviceBaseClass {
    private static final long serialVersionUID = 1000L;
    private int transientCalls;

    private RecordingController(String name) {
      super(name);
    }

    @Override
    public void runTransient(double initResponse, double dt, UUID id) {
      transientCalls++;
    }

    private int getTransientCalls() {
      return transientCalls;
    }
  }
}
