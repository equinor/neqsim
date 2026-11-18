package neqsim.process.mpc;

import java.util.ArrayList;
import java.util.List;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolverDense;

/**
 * Convex step-response planner for a single MPC scan. The independent decisions are MV increments. Each row of the
 * convolution, the absolute MV trajectory and its slew has a scalar convex cost or interval constraint. ADMM splits
 * those rows from the linear trajectory and factors the least-squares matrix once per scan.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
final class HorizonSolver {
  private static final int MAX_ITERATIONS = 5000;
  private static final double TOLERANCE = 1.0e-8;

  /** Utility class. */
  private HorizonSolver() {
  }

  /**
   * Plan future moves around the predicted trajectory with all future increments held at zero.
   *
   * @param mvs manipulated variables in registration order
   * @param cvs controlled variables in registration order
   * @param currentMv measured MV values at the scan
   * @param freeCv free CV response by future sample and CV
   * @param response normalized step coefficients by CV, MV and lag, held at the terminal value
   * @param controlHorizon number of independently chosen moves
   * @return certified plan or failure status
   */
  static HorizonPlan solve(List<ManipulatedVariable> mvs, List<ControlledVariable> cvs, double[] currentMv,
      double[][] freeCv, double[][][] response, int controlHorizon) {
    if (controlHorizon < 1 || controlHorizon > freeCv.length || mvs.isEmpty() || cvs.isEmpty()
        || currentMv.length != mvs.size() || response.length != cvs.size()) {
      throw new IllegalArgumentException("Invalid process-linked MPC dimensions or horizons");
    }
    List<Row> rows = new ArrayList<>();
    double[][] viableLow = new double[controlHorizon][mvs.size()];
    double[][] viableHigh = new double[controlHorizon][mvs.size()];
    for (int i = 0; i < mvs.size(); i++) {
      ManipulatedVariable mv = mvs.get(i);
      requireFinite(currentMv[i]);
      requireBounds(mv.getMinValue(), mv.getMaxValue());
      requireBounds(mv.getMinRateOfChange(), mv.getMaxRateOfChange());
      requireWeight(mv.getPreferredWeight());
      requireWeight(mv.getMoveWeight());
      requireWeight(mv.getCost());
      if (mv.getPreferredWeight() > 0.0) {
        requireFinite(mv.getPreferredValue());
      }
      if (!viable(mv, i, currentMv[i], freeCv.length, viableLow, viableHigh)) {
        return HorizonPlan.failed(HorizonPlan.Status.INFEASIBLE);
      }
      addMvRows(rows, mv, i, mvs.size(), currentMv[i], freeCv.length, controlHorizon);
    }
    for (double[] free : freeCv) {
      if (free.length != cvs.size()) {
        throw new IllegalArgumentException("Free response dimensions must agree with the CV list");
      }
      for (double value : free) {
        requireFinite(value);
      }
    }
    for (int j = 0; j < cvs.size(); j++) {
      addCvRows(rows, cvs.get(j), j, mvs.size(), freeCv, response[j], controlHorizon);
    }
    double[] increments = optimize(rows);
    if (increments == null) {
      return HorizonPlan.failed(HorizonPlan.Status.NO_CONVERGENCE);
    }
    return plan(mvs, cvs, currentMv, freeCv, response, increments, viableLow, viableHigh);
  }

  /**
   * Compute backward reachable MV intervals for all stages, including the held tail.
   *
   * @param mv manipulated variable
   * @param i MV index
   * @param current measured MV value
   * @param prediction number of prediction samples
   * @param low reachable lower bounds by control step and MV
   * @param high reachable upper bounds by control step and MV
   * @return true when the measured value can reach a feasible trajectory
   */
  private static boolean viable(ManipulatedVariable mv, int i, double current, int prediction, double[][] low,
      double[][] high) {
    double down = mv.getMinRateOfChange();
    double up = mv.getMaxRateOfChange();
    if (prediction > low.length && (down > 0.0 || up < 0.0)) {
      return false;
    }
    double min = mv.getMinValue();
    double max = mv.getMaxValue();
    for (int k = low.length - 1; k >= 0; k--) {
      low[k][i] = min;
      high[k][i] = max;
      if (min > max) {
        return false;
      }
      min = Math.max(mv.getMinValue(), min - up);
      max = Math.min(mv.getMaxValue(), max - down);
    }
    return Math.max(low[0][i], current + down) <= Math.min(high[0][i], current + up);
  }

  /**
   * Add absolute and incremental MV rows. The terminal economic cost repeats during the held tail.
   *
   * @param rows cost and constraint rows
   * @param mv manipulated variable
   * @param i MV index
   * @param numMv number of MVs
   * @param current measured MV value
   * @param prediction total future samples
   * @param control number of independent moves
   */
  private static void addMvRows(List<Row> rows, ManipulatedVariable mv, int i, int numMv, double current,
      int prediction, int control) {
    double[] cumulative = new double[control * numMv];
    double preferred = mv.getPreferredWeight() > 0.0 ? mv.getPreferredValue() - current : 0.0;
    for (int k = 0; k < control; k++) {
      double[] move = new double[cumulative.length];
      move[k * numMv + i] = 1.0;
      cumulative[k * numMv + i] = 1.0;
      rows.add(new Row(move, mv.getMinRateOfChange(), mv.getMaxRateOfChange(), 0.0, 0.0, mv.getMoveWeight(), 0.0));
      int count = k == control - 1 ? prediction - k : 1;
      rows.add(new Row(cumulative.clone(), mv.getMinValue() - current, mv.getMaxValue() - current, preferred, preferred,
          count * mv.getPreferredWeight(), count * mv.getCost()));
    }
  }

  /**
   * Add weighted tracking and soft-violation costs for a CV across all predicted samples.
   *
   * @param rows cost and constraint rows
   * @param cv controlled variable
   * @param j CV index
   * @param numMv number of MVs
   * @param free predicted free response
   * @param kernels step-response coefficients by MV and lag
   * @param control number of independent moves
   */
  private static void addCvRows(List<Row> rows, ControlledVariable cv, int j, int numMv, double[][] free,
      double[][] kernels, int control) {
    if (cv.getHardMin() != Double.NEGATIVE_INFINITY || cv.getHardMax() != Double.POSITIVE_INFINITY
        || cv.getMinValue() != Double.NEGATIVE_INFINITY || cv.getMaxValue() != Double.POSITIVE_INFINITY) {
      throw new IllegalArgumentException("Horizon planning supports soft CV limits and hard MV limits");
    }
    requireWeight(cv.getWeight());
    requireWeight(cv.getSoftConstraintPenalty());
    requireBounds(cv.getSoftMin(), cv.getSoftMax());
    double low = cv.isZoneControl() ? cv.getZoneLower() : cv.getSetpoint();
    double high = cv.isZoneControl() ? cv.getZoneUpper() : cv.getSetpoint();
    if (cv.getWeight() > 0.0) {
      requireBounds(low, high);
    }
    if (kernels.length != numMv) {
      throw new IllegalArgumentException("The response model must cover each MV");
    }
    for (double[] kernel : kernels) {
      if (kernel.length == 0) {
        throw new IllegalArgumentException("A step response needs at least one future coefficient");
      }
      for (double value : kernel) {
        requireFinite(value);
      }
    }
    for (int k = 0; k < free.length; k++) {
      double[] coefficient = convolution(kernels, k, control);
      addPenalty(rows, coefficient, low - free[k][j], high - free[k][j], cv.getWeight());
      addPenalty(rows, coefficient, cv.getSoftMin() - free[k][j], cv.getSoftMax() - free[k][j],
          cv.getSoftConstraintPenalty());
    }
  }

  /**
   * Add a squared distance to an interval unless it has zero effect.
   *
   * @param rows cost and constraint rows
   * @param coefficient response coefficients
   * @param low lower edge
   * @param high upper edge
   * @param weight quadratic penalty weight
   */
  private static void addPenalty(List<Row> rows, double[] coefficient, double low, double high, double weight) {
    if (weight > 0.0 && (Double.isFinite(low) || Double.isFinite(high))) {
      rows.add(new Row(coefficient, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, low, high, weight, 0.0));
    }
  }

  /**
   * Construct one row mapping present and future MV increments to the CV change at a specified sample.
   *
   * @param kernels unit step responses for this CV by MV
   * @param sample zero-based future sample
   * @param control number of independent moves
   * @return coefficients in move-major order
   */
  private static double[] convolution(double[][] kernels, int sample, int control) {
    double[] row = new double[control * kernels.length];
    for (int k = 0; k <= sample && k < control; k++) {
      for (int i = 0; i < kernels.length; i++) {
        double[] kernel = kernels[i];
        row[k * kernels.length + i] = kernel[Math.min(sample - k, kernel.length - 1)];
      }
    }
    return row;
  }

  /**
   * Solve via consensus ADMM, checking the primal consistency residual and the unscaled stationarity residual.
   *
   * @param rows linear trajectories and their convex scalar costs
   * @return increments or null if convergence could not be established
   */
  private static double[] optimize(List<Row> rows) {
    int n = rows.get(0).coefficients.length;
    int m = rows.size();
    DMatrixRMaj a = new DMatrixRMaj(m, n);
    for (int i = 0; i < m; i++) {
      System.arraycopy(rows.get(i).coefficients, 0, a.data, i * n, n);
    }
    DMatrixRMaj gram = new DMatrixRMaj(n, n);
    CommonOps_DDRM.multTransA(a, a, gram);
    LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.symmPosDef(n);
    if (!solver.setA(gram)) {
      return null;
    }
    DMatrixRMaj increments = new DMatrixRMaj(n, 1);
    DMatrixRMaj rhs = new DMatrixRMaj(n, 1);
    DMatrixRMaj values = new DMatrixRMaj(m, 1);
    DMatrixRMaj dual = new DMatrixRMaj(m, 1);
    DMatrixRMaj split = new DMatrixRMaj(m, 1);
    DMatrixRMaj work = new DMatrixRMaj(m, 1);
    double rho = 1.0;
    for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
      CommonOps_DDRM.subtract(split, dual, work);
      CommonOps_DDRM.multTransA(a, work, rhs);
      solver.solve(rhs, increments);
      CommonOps_DDRM.mult(a, increments, values);
      double primalResidual = 0.0;
      double relativePrimalResidual = 0.0;
      for (int i = 0; i < m; i++) {
        double previous = split.data[i];
        double value = values.data[i];
        double updated = rows.get(i).prox(value + dual.data[i], rho);
        split.data[i] = updated;
        dual.data[i] += value - updated;
        work.data[i] = rho * (updated - previous);
        double residual = Math.abs(value - updated);
        primalResidual = Math.max(primalResidual, residual);
        double rowScale = Math.max(1.0, Math.max(Math.abs(value), Math.abs(updated)));
        relativePrimalResidual = Math.max(relativePrimalResidual, residual / rowScale);
      }
      CommonOps_DDRM.multTransA(a, work, rhs);
      double dualResidual = CommonOps_DDRM.elementMaxAbs(rhs);
      if (!Double.isFinite(primalResidual) || !Double.isFinite(dualResidual)
          || !Double.isFinite(relativePrimalResidual)) {
        return null;
      }
      if (relativePrimalResidual <= TOLERANCE && dualResidual <= TOLERANCE) {
        return increments.data.clone();
      }
      if ((iteration + 1) % 25 == 0) {
        double oldRho = rho;
        if (primalResidual > 10.0 * dualResidual && rho < 1e8) {
          rho *= 2.0;
        } else if (dualResidual > 10.0 * primalResidual && rho > 1e-8) {
          rho *= 0.5;
        }
        CommonOps_DDRM.scale(oldRho / rho, dual);
      }
    }
    return null;
  }

  /**
   * Construct a feasible sequence from the converged increments and recompute physical-unit predictions. Reject
   * projection changes larger than numerical roundoff in the converged trajectory.
   *
   * @param mvs manipulated variables
   * @param cvs controlled variables
   * @param current measured MV values
   * @param free free CV response
   * @param response step kernels
   * @param increments converged MV increments
   * @param low backward reachable lower limits
   * @param high backward reachable upper limits
   * @return immutable scan result
   */
  private static HorizonPlan plan(List<ManipulatedVariable> mvs, List<ControlledVariable> cvs, double[] current,
      double[][] free, double[][][] response, double[] increments, double[][] low, double[][] high) {
    int n = mvs.size();
    double[][] moves = new double[free.length][n];
    double[][] predicted = new double[free.length][cvs.size()];
    double[][] lower = new double[free.length][cvs.size()];
    double[][] upper = new double[free.length][cvs.size()];
    for (int k = 0; k < free.length; k++) {
      for (int i = 0; i < n; i++) {
        double previous = k == 0 ? current[i] : moves[k - 1][i];
        moves[k][i] = previous;
        if (k < low.length) {
          ManipulatedVariable mv = mvs.get(i);
          double min = Math.max(low[k][i], previous + mv.getMinRateOfChange());
          double max = Math.min(high[k][i], previous + mv.getMaxRateOfChange());
          double delta = increments[k * n + i];
          double value = Math.max(min, Math.min(max, previous + delta));
          if (min > max || !Double.isFinite(value)
              || Math.abs(value - (previous + delta)) > 1e-6 * Math.max(1.0, Math.abs(delta))) {
            return HorizonPlan.failed(HorizonPlan.Status.NO_CONVERGENCE);
          }
          moves[k][i] = value;
          increments[k * n + i] = value - previous;
        }
      }
      for (int j = 0; j < cvs.size(); j++) {
        double[] row = convolution(response[j], k, low.length);
        double value = free[k][j];
        for (int index = 0; index < row.length; index++) {
          value += row[index] * increments[index];
        }
        if (!Double.isFinite(value)) {
          return HorizonPlan.failed(HorizonPlan.Status.NO_CONVERGENCE);
        }
        predicted[k][j] = value;
        lower[k][j] = Math.max(0.0, cvs.get(j).getSoftMin() - value);
        upper[k][j] = Math.max(0.0, value - cvs.get(j).getSoftMax());
      }
    }
    return new HorizonPlan(HorizonPlan.Status.OPTIMAL, moves, predicted, lower, upper);
  }

  /**
   * Require a finite model or scan input.
   *
   * @param value input to check
   */
  private static void requireFinite(double value) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("Expected finite MPC measurement, model or reference");
    }
  }

  /**
   * Require an ordered interval, allowing unbounded ends.
   *
   * @param low lower limit
   * @param high upper limit
   */
  private static void requireBounds(double low, double high) {
    if (!(low <= high) || low == Double.POSITIVE_INFINITY || high == Double.NEGATIVE_INFINITY) {
      throw new IllegalArgumentException("Invalid MPC interval");
    }
  }

  /**
   * Require a finite nonnegative cost weight.
   *
   * @param weight weight to check
   */
  private static void requireWeight(double weight) {
    if (!(weight >= 0.0) || !Double.isFinite(weight)) {
      throw new IllegalArgumentException("MPC weights must be finite and nonnegative");
    }
  }

  /** One linear measurement with an absolute interval, squared distance to a target interval and linear cost. */
  private static final class Row {
    private final double[] coefficients;
    private final double min;
    private final double max;
    private final double low;
    private final double high;
    private final double weight;
    private final double cost;

    /**
     * Define a separable term on the trajectory.
     *
     * @param coefficients coefficients on the independent MV increments
     * @param min hard lower bound
     * @param max hard upper bound
     * @param low lower zero-penalty target
     * @param high upper zero-penalty target
     * @param weight squared violation penalty
     * @param cost linear economic cost
     */
    Row(double[] coefficients, double min, double max, double low, double high, double weight, double cost) {
      this.coefficients = coefficients;
      this.min = min;
      this.max = max;
      this.low = low;
      this.high = high;
      this.weight = weight;
      this.cost = cost;
    }

    /**
     * Compute the exact bounded minimizer of the scalar augmented objective.
     *
     * @param consensus unconstrained argument
     * @param rho augmented-Lagrangian penalty
     * @return new proximal variable
     */
    double prox(double consensus, double rho) {
      double centre = consensus - cost / rho;
      double target = Math.max(low, Math.min(high, centre));
      double candidate = centre + (2.0 * weight / (rho + 2.0 * weight)) * (target - centre);
      return Math.max(min, Math.min(max, candidate));
    }
  }
}
