package neqsim.process.equipment.pipeline;

import java.io.Serializable;
import com.google.gson.Gson;

/**
 * Immutable evidence for a Beggs and Brill pressure-to-flow solve.
 *
 *
 * @author Even Solbraa
 *
 * @version 1.0
 */
public final class FlowSolveReport implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Distinct acceptance and failure outcomes. */
  public enum TerminationReason {
    CONVERGED, ITERATION_LIMIT, BRACKET_LIMIT, INNER_FAILURE, NON_FINITE_OUTPUT, INVALID_INPUT, REPLAY_FAILURE
  }

  private final TerminationReason terminationReason;
  private final int iterations;
  private final int bracketIterations;
  private final Double lowerFlowKgPerHour;
  private final Double upperFlowKgPerHour;
  private final Double candidateFlowKgPerHour;
  private final Double pressureResidualBar;
  private final Double pressureToleranceBar;

  /**
   * Creates solve evidence.
   *
   * @param terminationReason termination outcome
   * @param iterations number of bisection trials
   * @param bracketIterations number of upper-bound expansions
   * @param lowerFlowKgPerHour lower flow bound in kg/hr
   * @param upperFlowKgPerHour upper flow bound in kg/hr
   * @param candidateFlowKgPerHour last attempted flow in kg/hr; not a capacity on failure
   * @param pressureResidualBar signed outlet minus target pressure in bar, null if unavailable
   * @param pressureToleranceBar absolute acceptance tolerance in bar
   */
  FlowSolveReport(TerminationReason terminationReason, int iterations, int bracketIterations, double lowerFlowKgPerHour,
      double upperFlowKgPerHour, double candidateFlowKgPerHour, double pressureResidualBar,
      double pressureToleranceBar) {
    this.terminationReason = terminationReason;
    this.iterations = iterations;
    this.bracketIterations = bracketIterations;
    this.lowerFlowKgPerHour = Double.isFinite(lowerFlowKgPerHour) ? lowerFlowKgPerHour : null;
    this.upperFlowKgPerHour = Double.isFinite(upperFlowKgPerHour) ? upperFlowKgPerHour : null;
    this.candidateFlowKgPerHour = Double.isFinite(candidateFlowKgPerHour) ? candidateFlowKgPerHour : null;
    this.pressureResidualBar = Double.isFinite(pressureResidualBar) ? pressureResidualBar : null;
    this.pressureToleranceBar = Double.isFinite(pressureToleranceBar) ? pressureToleranceBar : null;
  }

  /**
   * Returns the termination outcome.
   *
   * @return termination reason
   */
  public TerminationReason getTerminationReason() {
    return terminationReason;
  }

  /**
   * Returns whether final replay met the pressure tolerance.
   *
   * @return true only on success
   */
  public boolean isConverged() {
    return terminationReason == TerminationReason.CONVERGED;
  }

  /**
   * Returns number of bisection trials.
   *
   * @return number of bisection trials
   */
  public int getIterations() {
    return iterations;
  }

  /**
   * Returns number of upper-bound expansions.
   *
   * @return number of upper-bound expansions
   */
  public int getBracketIterations() {
    return bracketIterations;
  }

  /**
   * Returns lower flow bound in kg/hr.
   *
   * @return lower flow bound in kg/hr
   */
  public Double getLowerFlowKgPerHour() {
    return lowerFlowKgPerHour;
  }

  /**
   * Returns upper flow bound in kg/hr.
   *
   * @return upper flow bound in kg/hr
   */
  public Double getUpperFlowKgPerHour() {
    return upperFlowKgPerHour;
  }

  /**
   * Returns last attempted flow in kg/hr; not a capacity on failure.
   *
   * @return last attempted flow in kg/hr; not a capacity on failure
   */
  public Double getCandidateFlowKgPerHour() {
    return candidateFlowKgPerHour;
  }

  /**
   * Returns signed outlet minus target pressure in bar, null if unavailable.
   *
   * @return signed outlet minus target pressure in bar, null if unavailable
   */
  public Double getPressureResidualBar() {
    return pressureResidualBar;
  }

  /**
   * Returns absolute acceptance tolerance in bar.
   *
   * @return absolute acceptance tolerance in bar
   */
  public Double getPressureToleranceBar() {
    return pressureToleranceBar;
  }

  /**
   * Serializes diagnostics without non-standard floating-point literals.
   *
   * @return JSON evidence
   */
  public String toJson() {
    return new Gson().toJson(this);
  }
}
