package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.google.gson.JsonObject;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Qualification of process-level open-recycle and convergence reporting.
 *
 * @author NeqSim
 * @version 1.0
 */
class ProcessSystemOpenRecycleTest {
  /**
   * Controlled nonconverging recycle, avoiding dependence on accidental numerical oscillation.
   *
   * @author NeqSim
   * @version 1.0
   */
  private static class OpenLoop extends Recycle {
    private static final long serialVersionUID = 1L;
    private boolean closed;

    /** Initializes a loop whose name does not contain the word recycle. */
    OpenLoop() {
      super("LP circulation");
    }

    /** {@inheritDoc} */
    @Override
    public void run(UUID id) {
      setCalculationIdentifier(id);
    }

    /** {@inheritDoc} */
    @Override
    public boolean solved() {
      return closed;
    }
  }

  /**
   * Verifies unsolved loops cannot be hidden by successful unit execution.
   *
   * @param optimized whether to use graph-optimized execution
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void openLoopIsNamedAndStatusIsNotConverged(boolean optimized) {
    SystemSrkEos fluid = new SystemSrkEos(303.15, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Stream tear = feed.clone("tear");
    Mixer mixer = new Mixer("suction mixer");
    mixer.addStream(feed);
    mixer.addStream(tear);
    OpenLoop loop = new OpenLoop();
    loop.addStream(mixer.getOutletStream());
    loop.setOutletStream(tear);
    ProcessSystem process = new ProcessSystem("process");
    process.setUseOptimizedExecution(optimized);
    process.setAutoConvergenceTuning(false);
    process.add(feed);
    process.add(mixer);
    process.add(loop);
    assertFalse(process.runUntilConverged(2));
    assertEquals("LP circulation", process.getOpenRecycles().get(0));
    assertThrows(UnsupportedOperationException.class, () -> process.getOpenRecycles().clear());
    RunStatus status = process.getRunStatus();
    assertTrue(status.isSuccess(), "Successful execution is distinct from convergence");
    assertFalse(status.isConverged());
    assertTrue(status.isStagnated());
    assertEquals("STAGNATED", status.getTerminationReason());
    assertEquals(2, status.getPassCount());
    assertEquals(1, status.getOpenRecycleCount());
    JsonObject json = status.toJsonObject();
    assertEquals(1, json.get("openRecycleCount").getAsInt());
    assertFalse(json.get("converged").getAsBoolean());
    java.util.List<String> previousNames = status.getOpenRecycles();
    loop.closed = true;
    assertTrue(process.runUntilConverged(2));
    assertTrue(process.getOpenRecycles().isEmpty());
    assertEquals(0, process.getRunStatus().getOpenRecycleCount());
    assertEquals(1, previousNames.size(), "Retained name lists are snapshots");
  }

  /** Verifies locked-inactive loops are excluded by type rather than name. */
  @Test
  void inactiveLoopsAreExcluded() {
    ProcessSystem process = new ProcessSystem("process");
    OpenLoop loop = new OpenLoop();
    process.add(loop);
    assertEquals(1, process.getOpenRecycles().size());
    loop.setLockedInactive(true);
    assertTrue(process.getOpenRecycles().isEmpty());
  }
}
