package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests timing diagnostics reported by {@link DistillationColumn} solver fallback paths.
 *
 * @author esol
 * @version 1.0
 */
public class DistillationColumnTimingTest {
  /** Minimum forced accelerator work in nanoseconds. */
  private static final long FORCED_ACCELERATOR_WORK_NANOS = 5_000_000L;

  /**
   * Verifies that public run timing includes a failed accelerator before damped fallback.
   */
  @Test
  public void runSolveTimeIncludesFailedAcceleratorBeforeFallback() {
    TimingProbeColumn column = new TimingProbeColumn();
    column.setSolverType(DistillationColumn.SolverType.INSIDE_OUT);

    column.run(UUID.randomUUID());

    assertEquals(DistillationColumn.SolverType.DAMPED_SUBSTITUTION, column.getLastSolverTypeUsed());
    assertTrue(column.wasFallbackInvoked(), "The fallback solve path should be invoked");
    assertTrue(column.getLastSolveTimeSeconds() >= FORCED_ACCELERATOR_WORK_NANOS / 1.0e9,
        "Reported solve time should include the failed accelerator before fallback");
  }

  /**
   * Verifies that automatic full-fractionator feed movement remains opt-in.
   */
  @Test
  public void optInFullFractionatorFastPathMovesEndFeedAndUsesMeshResidual() {
    FastPathProbeColumn column = new FastPathProbeColumn();
    column.setFullFractionatorFastPathEnabled(true);

    column.run(UUID.randomUUID());

    assertTrue(column.wasFullFractionatorFastPathApplied(), column.getConvergenceDiagnostics());
    assertEquals(5, column.getFeedTrayNumber(column.getFeed()),
        "Opt-in fast path should move the end feed to the mid-column tray");
    assertTrue(column.wasMeshResidualInvoked(), "Opt-in fast path should invoke MESH residual");
    assertEquals(DistillationColumn.SolverType.MESH_RESIDUAL, column.getLastSolverTypeUsed());
  }

  /**
   * Lightweight column that forces the accelerator-to-fallback timing path.
   *
   * @author esol
   * @version 1.0
   */
  private static final class TimingProbeColumn extends DistillationColumn {
    /** Serialization version for the test probe. */
    private static final long serialVersionUID = 1000L;
    /** Whether the fallback method was called. */
    private boolean fallbackInvoked;

    /**
     * Creates a timing probe column.
     */
    private TimingProbeColumn() {
      super("timing probe column", 1, true, true);
      SystemInterface fluid = new SystemSrkEos(300.0, 10.0);
      fluid.addComponent("methane", 0.5);
      fluid.addComponent("ethane", 0.5);
      fluid.setMixingRule("classic");
      Stream feed = new Stream("timing probe feed", fluid);
      feed.setFlowRate(1.0, "kg/hr");
      feed.run();
      addFeedStream(feed, 1);
    }

    /** {@inheritDoc} */
    @Override
    void solveInsideOut(UUID id) {
      long startTime = System.nanoTime();
      while (System.nanoTime() - startTime < FORCED_ACCELERATOR_WORK_NANOS) {
        // Keep the forced work local so the regression test stays deterministic and fast.
      }
      throw new RuntimeException("forced accelerator failure");
    }

    /** {@inheritDoc} */
    @Override
    void solveDampedSubstitution(UUID id) {
      fallbackInvoked = true;
      init();
    }

    /**
     * Checks whether damped fallback was invoked.
     *
     * @return {@code true} when the fallback solve method was called
     */
    private boolean wasFallbackInvoked() {
      return fallbackInvoked;
    }
  }

  /**
   * Lightweight column that observes the opt-in full-fractionator fast path.
   *
   * @author esol
   * @version 1.0
   */
  private static final class FastPathProbeColumn extends DistillationColumn {
    /** Serialization version for the test probe. */
    private static final long serialVersionUID = 1001L;
    /** Feed stream used by the probe column. */
    private final Stream feed;
    /** Whether the MESH residual solve method was invoked. */
    private boolean meshResidualInvoked;

    /**
     * Creates a fast-path probe column with a single feed near the condenser.
     */
    private FastPathProbeColumn() {
      super("fast path probe column", 10, true, true);
      SystemInterface fluid = new SystemSrkEos(300.0, 10.0);
      fluid.addComponent("methane", 0.2);
      fluid.addComponent("ethane", 0.2);
      fluid.addComponent("propane", 0.3);
      fluid.addComponent("n-butane", 0.3);
      fluid.setMixingRule("classic");
      feed = new Stream("fast path probe feed", fluid);
      feed.setFlowRate(1.0, "kg/hr");
      feed.run();
      addFeedStream(feed, 9);
      getCondenser().setRefluxRatio(0.1);
    }

    /** {@inheritDoc} */
    @Override
    void solveMeshResidual(UUID id) {
      meshResidualInvoked = true;
      init();
    }

    /** {@inheritDoc} */
    @Override
    public boolean solved() {
      return true;
    }

    /**
     * Gets the probe feed stream.
     *
     * @return probe feed stream
     */
    private Stream getFeed() {
      return feed;
    }

    /**
     * Checks whether the MESH residual method was invoked.
     *
     * @return {@code true} when the MESH residual method was called
     */
    private boolean wasMeshResidualInvoked() {
      return meshResidualInvoked;
    }
  }
}
