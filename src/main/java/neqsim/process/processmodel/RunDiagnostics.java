package neqsim.process.processmodel;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Diagnostics of the last run of a {@link ProcessSystem} or {@link ProcessModel}: wall-clock time, budget outcome
 * (timed out, stopped, stalled), the unit that was running, and the recent residual history.
 *
 * @author esol
 * @version 1.0
 */
public final class RunDiagnostics implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private final String outcome;
  private final String reason;
  private final String unitName;
  private final double elapsedSeconds;
  private final double maxRunTimeSeconds;
  private final int stallWindow;
  private final int passCount;
  private final double lastResidual;
  private final double bestResidual;
  private final int residualCount;
  private final List<Double> residualHistory;

  /**
   * Creates a diagnostics record.
   *
   * @param outcome budget outcome
   * @param reason reason text
   * @param unitName unit that was running when the budget tripped, or the last unit started
   * @param elapsedSeconds elapsed wall-clock time in seconds
   * @param maxRunTimeSeconds configured time budget in seconds (zero if off)
   * @param stallWindow configured stall window (zero if off)
   * @param passCount number of passes or iterations
   * @param lastResidual last recorded normalised residual
   * @param bestResidual best recorded normalised residual
   * @param residualCount number of recorded residuals
   * @param residualHistory most recent residuals
   */
  RunDiagnostics(RunBudget.Outcome outcome, String reason, String unitName, double elapsedSeconds,
      double maxRunTimeSeconds, int stallWindow, int passCount, double lastResidual, double bestResidual,
      int residualCount, List<Double> residualHistory) {
    this.outcome = outcome.name();
    this.reason = reason == null ? "" : reason;
    this.unitName = unitName;
    this.elapsedSeconds = elapsedSeconds;
    this.maxRunTimeSeconds = maxRunTimeSeconds;
    this.stallWindow = stallWindow;
    this.passCount = passCount;
    this.lastResidual = lastResidual;
    this.bestResidual = bestResidual;
    this.residualCount = residualCount;
    this.residualHistory = Collections.unmodifiableList(new ArrayList<Double>(residualHistory));
  }

  /**
   * Gets the budget outcome: NONE, TIMED_OUT, STOPPED or STALLED.
   *
   * @return outcome name
   */
  public String getOutcome() {
    return outcome;
  }

  /**
   * Returns whether the run was stopped by the wall-clock budget.
   *
   * @return true if timed out
   */
  public boolean isTimedOut() {
    return RunBudget.Outcome.TIMED_OUT.name().equals(outcome);
  }

  /**
   * Returns whether the run was stopped by {@link ProcessSystem#requestStop()}.
   *
   * @return true if stopped on request
   */
  public boolean isStopped() {
    return RunBudget.Outcome.STOPPED.name().equals(outcome);
  }

  /**
   * Returns whether the run was stopped because the residual stagnated.
   *
   * @return true if stalled
   */
  public boolean isStalled() {
    return RunBudget.Outcome.STALLED.name().equals(outcome);
  }

  /**
   * Gets the reason text.
   *
   * @return reason, empty if the budget did not stop the run
   */
  public String getReason() {
    return reason;
  }

  /**
   * Gets the unit that was running when the budget tripped, or the last unit started if it did not trip.
   *
   * @return unit name, may be null
   */
  public String getUnitName() {
    return unitName;
  }

  /**
   * Gets the elapsed wall-clock time.
   *
   * @return elapsed time in seconds
   */
  public double getElapsedSeconds() {
    return elapsedSeconds;
  }

  /**
   * Gets the configured time budget.
   *
   * @return seconds, zero if off
   */
  public double getMaxRunTimeSeconds() {
    return maxRunTimeSeconds;
  }

  /**
   * Gets the configured stall window.
   *
   * @return iterations, zero if off
   */
  public int getStallWindow() {
    return stallWindow;
  }

  /**
   * Gets the number of passes or iterations.
   *
   * @return pass count
   */
  public int getPassCount() {
    return passCount;
  }

  /**
   * Gets the last recorded normalised residual.
   *
   * @return residual, NaN if none was recorded
   */
  public double getLastResidual() {
    return lastResidual;
  }

  /**
   * Gets the best recorded normalised residual.
   *
   * @return residual, infinity if none was recorded
   */
  public double getBestResidual() {
    return bestResidual;
  }

  /**
   * Gets the number of recorded residuals.
   *
   * @return count
   */
  public int getResidualCount() {
    return residualCount;
  }

  /**
   * Gets the most recent residuals (oldest first).
   *
   * @return unmodifiable residual history
   */
  public List<Double> getResidualHistory() {
    return residualHistory;
  }

  /**
   * Returns the diagnostics as an ordered map.
   *
   * @return map of names to values
   */
  public Map<String, Object> toMap() {
    Map<String, Object> m = new LinkedHashMap<String, Object>();
    m.put("outcome", outcome);
    m.put("reason", reason);
    m.put("unitName", unitName);
    m.put("elapsedSeconds", elapsedSeconds);
    m.put("maxRunTimeSeconds", maxRunTimeSeconds);
    m.put("stallWindow", stallWindow);
    m.put("passCount", passCount);
    m.put("lastResidual", lastResidual);
    m.put("bestResidual", bestResidual);
    m.put("residualCount", residualCount);
    m.put("residualHistory", residualHistory);
    return m;
  }

  /**
   * Returns the diagnostics as JSON.
   *
   * @return JSON text
   */
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(toMap());
  }
}
