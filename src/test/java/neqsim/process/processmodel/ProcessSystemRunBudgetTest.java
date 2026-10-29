package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests of the opt-in run budget (wall-clock limit, cooperative stop and stall detection) of {@link ProcessSystem} and
 * {@link ProcessModel}.
 *
 * @author esol
 * @version 1.0
 */
class ProcessSystemRunBudgetTest {
  /**
   * Deliberately non-converging recycle that needs a fixed time for every run, i.e. a slow dummy unit.
   *
   * @author esol
   * @version 1.0
   */
  private static class SlowOpenLoop extends Recycle {
    private static final long serialVersionUID = 1L;
    private final long sleepMillis;
    private final AtomicInteger runs = new AtomicInteger();
    /** Optional callback after the loop starts, before its first simulated delay. */
    private transient Runnable onFirstRun;

    /**
     * Creates the loop.
     *
     * @param name unit name
     * @param sleepMillis time each run takes in milliseconds
     */
    SlowOpenLoop(String name, long sleepMillis) {
      super(name);
      this.sleepMillis = sleepMillis;
    }

    /** {@inheritDoc} */
    @Override
    public void run(UUID id) {
      if (runs.incrementAndGet() == 1 && onFirstRun != null) {
        onFirstRun.run();
      }
      try {
        Thread.sleep(sleepMillis);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      setCalculationIdentifier(id);
    }

    /** {@inheritDoc} */
    @Override
    public boolean solved() {
      return false;
    }
  }

  /**
   * Builds a feed, a mixer and a slow non-converging loop.
   *
   * @param name process name
   * @param loopName name of the slow loop
   * @param sleepMillis run time of the loop in milliseconds
   * @param optimized whether to use graph-optimized execution
   * @return process and loop in one object array
   */
  private static Object[] slowProcess(String name, String loopName, long sleepMillis, boolean optimized) {
    SystemSrkEos fluid = new SystemSrkEos(303.15, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed " + name, fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Stream tear = feed.clone("tear " + name);
    Mixer mixer = new Mixer("mixer " + name);
    mixer.addStream(feed);
    mixer.addStream(tear);
    SlowOpenLoop loop = new SlowOpenLoop(loopName, sleepMillis);
    loop.addStream(mixer.getOutletStream());
    loop.setOutletStream(tear);
    ProcessSystem process = new ProcessSystem(name);
    process.setUseOptimizedExecution(optimized);
    process.setAutoConvergenceTuning(false);
    process.add(feed);
    process.add(mixer);
    process.add(loop);
    return new Object[] {process, loop};
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void defaultsAreOffAndRunIsUnchanged(boolean optimized) {
    Object[] parts = slowProcess("p", "slow loop", 1L, optimized);
    ProcessSystem process = (ProcessSystem) parts[0];
    assertEquals(0.0, process.getMaxRunTimeSeconds(), 0.0);
    process.run();
    RunStatus status = process.getRunStatus();
    assertFalse(status.isTimedOut());
    assertFalse(status.isStalled());
    assertFalse(status.isStopped());
    assertEquals("NONE", process.getLastRunDiagnostics().getOutcome());
    assertTrue(status.isSuccess());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void timeBudgetStopsRunAndNamesTheUnit(boolean optimized) {
    Object[] parts = slowProcess("p", "slow loop", 300L, optimized);
    ProcessSystem process = (ProcessSystem) parts[0];
    SlowOpenLoop loop = (SlowOpenLoop) parts[1];
    // Arm only after the intended unit starts: feed/mixer initialization may exceed
    // 250 ms on a loaded runner. Its first delay then crosses the deadline before
    // another unit can run, so the exact diagnostic name is independent of timing.
    loop.onFirstRun = () -> process.setMaxRunTimeSeconds(0.25);
    long t0 = System.nanoTime();
    process.run();
    double elapsed = (System.nanoTime() - t0) * 1.0e-9;
    RunStatus status = process.getRunStatus();
    assertTrue(status.isTimedOut(), status.getTerminationReason());
    assertEquals(RunStatus.TIMED_OUT, status.getTerminationReason());
    assertFalse(status.isConverged());
    assertEquals("slow loop", status.getBudgetUnitName());
    assertTrue(status.getBudgetReason().contains("wall-clock"));
    assertTrue(elapsed < 1.2, "run must stop near the budget, took " + elapsed);
    assertEquals(1, loop.runs.get(), "the budget must stop the run after the first slow unit invocation");
    RunDiagnostics diag = process.getLastRunDiagnostics();
    assertTrue(diag.isTimedOut());
    assertEquals("slow loop", diag.getUnitName());
    assertTrue(diag.getElapsedSeconds() >= 0.25);
    assertEquals(0.25, diag.getMaxRunTimeSeconds(), 1.0e-12);
    assertTrue(diag.toJson().contains("TIMED_OUT"));
    assertTrue(status.toJson().contains("budgetUnitName"));
  }

  @Test
  void budgetCoversAllPassesOfRunUntilConverged() {
    Object[] parts = slowProcess("p", "slow loop", 60L, false);
    ProcessSystem process = (ProcessSystem) parts[0];
    process.setMaxRunTimeSeconds(0.3);
    long t0 = System.nanoTime();
    boolean converged = process.runUntilConverged(50);
    double elapsed = (System.nanoTime() - t0) * 1.0e-9;
    assertFalse(converged);
    assertTrue(process.getRunStatus().isTimedOut());
    assertTrue(elapsed < 1.5, "took " + elapsed);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void requestStopFromAnotherThreadStopsTheRun(boolean optimized) throws Exception {
    Object[] parts = slowProcess("p", "slow loop", 100L, optimized);
    ProcessSystem process = (ProcessSystem) parts[0];
    SlowOpenLoop loop = (SlowOpenLoop) parts[1];
    Thread stopper = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          Thread.sleep(250L);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        process.requestStop();
      }
    });
    long t0 = System.nanoTime();
    stopper.start();
    process.run();
    double elapsed = (System.nanoTime() - t0) * 1.0e-9;
    stopper.join();
    RunStatus status = process.getRunStatus();
    assertTrue(status.isStopped(), status.getTerminationReason());
    assertEquals("slow loop", status.getBudgetUnitName());
    assertTrue(elapsed < 1.1, "took " + elapsed);
    assertTrue(loop.runs.get() < 14, "loop ran " + loop.runs.get());
    assertTrue(process.getLastRunDiagnostics().isStopped());
    // a stop request does not leak into the next run
    process.setMaxRunTimeSeconds(0.0);
    process.run();
    assertFalse(process.getRunStatus().isStopped());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void stallDetectionStopsNonImprovingRecycle(boolean optimized) {
    Object[] parts = slowProcess("p", "slow loop", 1L, optimized);
    ProcessSystem process = (ProcessSystem) parts[0];
    SlowOpenLoop loop = (SlowOpenLoop) parts[1];
    process.setStallDetection(3, 1.0e-3);
    process.run();
    RunStatus status = process.getRunStatus();
    assertTrue(status.isStalled(), status.getTerminationReason());
    assertEquals(RunStatus.STALLED, status.getTerminationReason());
    assertFalse(status.isConverged());
    assertNotNull(status.getBudgetReason());
    assertTrue(status.getBudgetReason().contains("did not improve"));
    assertEquals("slow loop", status.getBudgetUnitName());
    assertTrue(loop.runs.get() <= 6, "loop ran " + loop.runs.get());
    RunDiagnostics diag = process.getLastRunDiagnostics();
    assertTrue(diag.isStalled());
    assertTrue(diag.getResidualCount() >= 3);
    assertEquals(3, diag.getStallWindow());
  }

  @Test
  void docSnippetWatchdogOnConvergingProcessLeavesStatusClean() {
    SystemSrkEos fluid = new SystemSrkEos(303.15, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("doc feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    ProcessSystem process = new ProcessSystem("doc");
    process.add(feed);
    process.setMaxRunTimeSeconds(30.0);
    process.setStallDetection(8, 1.0e-3);
    process.run();
    RunStatus status = process.getRunStatus();
    assertFalse(status.isTimedOut() || status.isStalled() || status.isStopped());
    RunDiagnostics diag = process.getLastRunDiagnostics();
    assertEquals("NONE", diag.getOutcome());
    assertTrue(diag.getElapsedSeconds() < 30.0);
    assertEquals(30.0, process.getMaxRunTimeSeconds(), 1.0e-12);
  }

  @Test
  void modelRespectsTimeBudgetAcrossAreas() {
    ProcessModel model = new ProcessModel();
    Object[] a = slowProcess("a", "slow loop A", 80L, true);
    Object[] b = slowProcess("b", "slow loop B", 80L, true);
    model.add("area a", (ProcessSystem) a[0]);
    model.add("area b", (ProcessSystem) b[0]);
    model.setMaxRunTimeSeconds(0.3);
    long t0 = System.nanoTime();
    model.run();
    double elapsed = (System.nanoTime() - t0) * 1.0e-9;
    assertTrue(elapsed < 2.0, "took " + elapsed);
    RunStatus status = model.getRunStatus();
    assertTrue(status.isTimedOut(), status.getTerminationReason());
    assertTrue(status.getBudgetUnitName().startsWith("slow loop"));
    assertTrue(model.getLastRunDiagnostics().isTimedOut());
    assertFalse(model.isModelConverged());
    // areas detached from the model budget: stand-alone run has no budget again
    assertEquals(0.0, ((ProcessSystem) a[0]).getMaxRunTimeSeconds(), 0.0);
  }

  @Test
  void modelRequestStopFromAnotherThread() throws Exception {
    ProcessModel model = new ProcessModel();
    Object[] a = slowProcess("a", "slow loop A", 100L, true);
    model.add("area a", (ProcessSystem) a[0]);
    Thread stopper = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          Thread.sleep(250L);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        model.requestStop();
      }
    });
    long t0 = System.nanoTime();
    stopper.start();
    model.run();
    double elapsed = (System.nanoTime() - t0) * 1.0e-9;
    stopper.join();
    assertTrue(model.getRunStatus().isStopped(), model.getRunStatus().getTerminationReason());
    assertTrue(elapsed < 1.2, "took " + elapsed);
  }
}
