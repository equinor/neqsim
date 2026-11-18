package neqsim.process.mpc;

import java.io.Serializable;

/**
 * Immutable result of one constrained process-linked MPC scan. Each row corresponds to one future sample. A failed scan
 * contains no applicable moves or predictions.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public final class HorizonPlan implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Outcome of the constrained solve. */
  public enum Status {
    /** A feasible plan passed the first-order convergence checks. */
    OPTIMAL,
    /** No sequence of MV values satisfies the absolute and rate limits. */
    INFEASIBLE,
    /** The solver could not certify convergence or finite results. */
    NO_CONVERGENCE
  }

  private final Status status;
  private final double[][] plannedMvs;
  private final double[][] predictedCvs;
  private final double[][] lowerSlack;
  private final double[][] upperSlack;

  /**
   * Snapshot a completed scan.
   *
   * @param status solver outcome
   * @param plannedMvs absolute MV sequence, with the last move held past the control horizon
   * @param predictedCvs future CV measurements
   * @param lowerSlack shortfalls below the CV soft lower bounds
   * @param upperSlack excesses above the CV soft upper bounds
   */
  HorizonPlan(Status status, double[][] plannedMvs, double[][] predictedCvs, double[][] lowerSlack,
      double[][] upperSlack) {
    this.status = status;
    this.plannedMvs = copy(plannedMvs);
    this.predictedCvs = copy(predictedCvs);
    this.lowerSlack = copy(lowerSlack);
    this.upperSlack = copy(upperSlack);
  }

  /**
   * Build a result with no applicable move.
   *
   * @param status failure category
   * @return empty result
   */
  static HorizonPlan failed(Status status) {
    double[][] empty = new double[0][0];
    return new HorizonPlan(status, empty, empty, empty, empty);
  }

  /**
   * Copy a sample-by-variable matrix.
   *
   * @param source matrix to snapshot
   * @return independent matrix
   */
  private static double[][] copy(double[][] source) {
    double[][] result = new double[source.length][];
    for (int i = 0; i < source.length; i++) {
      result[i] = source[i].clone();
    }
    return result;
  }

  /**
   * Get the solver outcome.
   *
   * @return solver outcome for the scan
   */
  public Status getStatus() {
    return status;
  }

  /**
   * Check whether the scan produced an applicable first move.
   *
   * @return true if the first planned move is available for application
   */
  public boolean isSuccessful() {
    return status == Status.OPTIMAL;
  }

  /**
   * Get the absolute MV settings for the interval starting now.
   *
   * @return first planned move, or an empty array for an unsuccessful solve
   */
  public double[] getFirstMove() {
    return isSuccessful() ? plannedMvs[0].clone() : new double[0];
  }

  /**
   * Get a snapshot of the planned manipulated variables.
   *
   * @return absolute MV values by future sample and registration order
   */
  public double[][] getPlannedMvs() {
    return copy(plannedMvs);
  }

  /**
   * Get a snapshot of the predicted controlled variables.
   *
   * @return predicted CV values by future sample and registration order
   */
  public double[][] getPredictedCvs() {
    return copy(predictedCvs);
  }

  /**
   * Get the predicted violations of the lower soft limits.
   *
   * @return nonnegative CV lower soft-limit violations in the units of each measurement
   */
  public double[][] getLowerSlack() {
    return copy(lowerSlack);
  }

  /**
   * Get the predicted violations of the upper soft limits.
   *
   * @return nonnegative CV upper soft-limit violations in the units of each measurement
   */
  public double[][] getUpperSlack() {
    return copy(upperSlack);
  }
}
