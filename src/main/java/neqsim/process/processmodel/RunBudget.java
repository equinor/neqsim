package neqsim.process.processmodel;

import java.util.ArrayList;
import java.util.List;

/**
 * Opt-in wall-clock, cancellation and stagnation budget shared by a {@link ProcessSystem} run (and by all areas of a
 * {@link ProcessModel} run).
 *
 * <p>
 * The budget is cooperative: it is checked between unit operations and between recycle, adjuster and outer-model
 * iterations. A single unit operation that never returns cannot be interrupted. All budget features are off by default
 * (maximum run time 0, stall window 0), in which case a run behaves exactly as before.
 * </p>
 *
 * <p>
 * The owner of the outermost run calls {@link #enter()} and {@link #exit()}; nested runs (a model run that runs its
 * areas, or {@link ProcessSystem#runUntilConverged(int)} that runs the process several times) share one clock and one
 * trip state. {@link #requestStop()} and {@link #checkStop(String)} are thread-safe.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public final class RunBudget {
  /** Reason why a budget stopped a run. */
  public enum Outcome {
    /** The budget did not stop the run. */
    NONE,
    /** The wall-clock budget was exceeded. */
    TIMED_OUT,
    /** A stop was requested with {@link RunBudget#requestStop()}. */
    STOPPED,
    /** The residual did not improve for the configured number of iterations. */
    STALLED
  }

  private static final int HISTORY_SIZE = 20;

  private volatile double maxRunTimeSeconds = 0.0;
  private volatile int stallWindow = 0;
  private volatile double stallMinImprovement = 1.0e-3;
  private volatile boolean stopRequested = false;
  private volatile Outcome outcome = Outcome.NONE;
  private volatile String reason = "";
  private volatile String tripUnit = null;
  private volatile String lastStartedUnit = null;
  private volatile long startNanos = System.nanoTime();
  private volatile long endNanos = -1L;
  private int depth = 0;

  private final Object residualLock = new Object();
  private double bestResidual = Double.POSITIVE_INFINITY;
  private double lastResidual = Double.NaN;
  private int residualCount = 0;
  private final List<Double> history = new ArrayList<Double>();

  /**
   * Sets the wall-clock budget of one run.
   *
   * @param seconds maximum run time in seconds; zero or negative disables the time budget
   */
  public void setMaxRunTimeSeconds(double seconds) {
    this.maxRunTimeSeconds = seconds > 0.0 && !Double.isNaN(seconds) ? seconds : 0.0;
  }

  /**
   * Gets the wall-clock budget.
   *
   * @return maximum run time in seconds, zero if disabled
   */
  public double getMaxRunTimeSeconds() {
    return maxRunTimeSeconds;
  }

  /**
   * Enables stagnation detection: a run is stopped with {@link Outcome#STALLED} when the residual did not improve by
   * the minimum fraction for the given number of consecutive iterations.
   *
   * @param window number of consecutive iterations without improvement; zero or negative disables detection
   * @param minRelativeImprovement minimum relative improvement of the best residual that counts as progress (for
   * example 1.0e-3)
   */
  public void setStallDetection(int window, double minRelativeImprovement) {
    this.stallWindow = Math.max(window, 0);
    this.stallMinImprovement = Math.max(minRelativeImprovement, 0.0);
  }

  /**
   * Gets the stall window.
   *
   * @return number of iterations without improvement that trip the budget, zero if disabled
   */
  public int getStallWindow() {
    return stallWindow;
  }

  /**
   * Gets the minimum relative residual improvement that counts as progress.
   *
   * @return relative improvement
   */
  public double getStallMinImprovement() {
    return stallMinImprovement;
  }

  /**
   * Requests cooperative cancellation of the run in progress. A request made while no run is active is discarded when
   * the next run starts.
   */
  public void requestStop() {
    stopRequested = true;
  }

  /**
   * Returns whether a stop has been requested for the current run.
   *
   * @return true if a stop request is pending
   */
  public boolean isStopRequested() {
    return stopRequested;
  }

  /**
   * Enters a run scope. The outermost entry starts the clock and clears any earlier trip and stop request.
   *
   * @return true if this is the outermost entry (the caller owns the clock)
   */
  public synchronized boolean enter() {
    boolean outermost = depth == 0;
    depth++;
    if (outermost) {
      stopRequested = false;
      outcome = Outcome.NONE;
      reason = "";
      tripUnit = null;
      lastStartedUnit = null;
      startNanos = System.nanoTime();
      endNanos = -1L;
      resetResiduals();
    }
    return outermost;
  }

  /**
   * Leaves a run scope; the outermost exit stops the clock.
   */
  public synchronized void exit() {
    if (depth > 0) {
      depth--;
      if (depth == 0) {
        endNanos = System.nanoTime();
      }
    }
  }

  /**
   * Returns whether a run scope is currently open.
   *
   * @return true while a run is in progress
   */
  public synchronized boolean isInRun() {
    return depth > 0;
  }

  /**
   * Gets the elapsed wall-clock time of the current or last run.
   *
   * @return elapsed time in seconds
   */
  public double getElapsedSeconds() {
    long end = endNanos >= 0L ? endNanos : System.nanoTime();
    return (end - startNanos) * 1.0e-9;
  }

  /**
   * Checks the stop request and the wall-clock budget, and records the unit that is about to run.
   *
   * @param unitName name of the unit about to run, or null when called between iterations
   * @return true if the run has to stop (the budget has tripped)
   */
  public boolean checkStop(String unitName) {
    if (outcome != Outcome.NONE) {
      return true;
    }
    if (unitName != null) {
      lastStartedUnit = unitName;
    }
    if (stopRequested) {
      trip(Outcome.STOPPED, "stop requested by requestStop()");
      return true;
    }
    double limit = maxRunTimeSeconds;
    if (limit > 0.0 && getElapsedSeconds() > limit) {
      trip(Outcome.TIMED_OUT, String.format(java.util.Locale.US, "wall-clock budget of %.3f s exceeded (%.3f s)", limit,
          getElapsedSeconds()));
      return true;
    }
    return false;
  }

  /**
   * Trips the budget; the first trip wins.
   *
   * @param why outcome to report
   * @param text human-readable reason
   */
  public synchronized void trip(Outcome why, String text) {
    if (outcome == Outcome.NONE && why != Outcome.NONE) {
      outcome = why;
      reason = text;
      tripUnit = lastStartedUnit;
    }
  }

  /**
   * Returns whether the budget has stopped the run.
   *
   * @return true if tripped
   */
  public boolean isTripped() {
    return outcome != Outcome.NONE;
  }

  /**
   * Gets the outcome of the budget.
   *
   * @return outcome, {@link Outcome#NONE} if the budget did not stop the run
   */
  public Outcome getOutcome() {
    return outcome;
  }

  /**
   * Gets the reason text of the trip.
   *
   * @return reason, empty if not tripped
   */
  public String getReason() {
    return reason;
  }

  /**
   * Gets the unit that was running (the last unit started) when the budget tripped.
   *
   * @return unit name, or null if no unit had started
   */
  public String getTripUnit() {
    return tripUnit;
  }

  /**
   * Gets the last unit started in the run.
   *
   * @return unit name or null
   */
  public String getLastStartedUnit() {
    return lastStartedUnit;
  }

  /**
   * Creates a stall tracker for one iteration loop. Each loop needs its own tracker so that loops of different areas
   * running in parallel do not disturb each other.
   *
   * @return new tracker bound to this budget
   */
  public StallTracker newStallTracker() {
    return new StallTracker();
  }

  /**
   * Tracks the residual of one iteration loop and trips the budget when it stagnates.
   *
   * @author esol
   * @version 1.0
   */
  public final class StallTracker {
    private double best = Double.POSITIVE_INFINITY;
    private int noImprovement = 0;

    /**
     * Records the residual of an iteration and trips the budget with {@link Outcome#STALLED} when it did not improve
     * for the configured window. Non-finite residuals count as no progress.
     *
     * @param residual normalised residual (a value below one means converged)
     * @param iteration iteration number for the message
     * @return true if the budget has tripped
     */
    public boolean record(double residual, int iteration) {
      if (Double.isFinite(residual) && residual < best * (1.0 - stallMinImprovement)) {
        best = residual;
        noImprovement = 0;
      } else {
        noImprovement++;
      }
      synchronized (residualLock) {
        lastResidual = residual;
        residualCount++;
        if (best < bestResidual) {
          bestResidual = best;
        }
        history.add(Double.valueOf(residual));
        if (history.size() > HISTORY_SIZE) {
          history.remove(0);
        }
      }
      if (stallWindow > 0 && noImprovement >= stallWindow) {
        trip(Outcome.STALLED,
            String.format(java.util.Locale.US,
                "residual %.3e did not improve (best %.3e) for %d iterations, stopped at iteration %d", residual, best,
                noImprovement, iteration));
      }
      return outcome != Outcome.NONE;
    }
  }

  /**
   * Clears the residual bookkeeping.
   */
  private void resetResiduals() {
    synchronized (residualLock) {
      bestResidual = Double.POSITIVE_INFINITY;
      lastResidual = Double.NaN;
      residualCount = 0;
      history.clear();
    }
  }

  /**
   * Creates a diagnostics snapshot of the current or last run.
   *
   * @param passes number of passes or iterations of the run
   * @return diagnostics
   */
  public RunDiagnostics snapshot(int passes) {
    synchronized (residualLock) {
      return new RunDiagnostics(outcome, reason, tripUnit != null ? tripUnit : lastStartedUnit, getElapsedSeconds(),
          maxRunTimeSeconds, stallWindow, passes, lastResidual, bestResidual, residualCount,
          new ArrayList<Double>(history));
    }
  }
}
