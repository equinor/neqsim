package neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver;

import java.io.Serializable;
import java.util.Arrays;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

/**
 * Immutable convergence and total-mass diagnostics from a one-phase pipe-flow solve.
 *
 * <p>
 * The nonlinear convergence metric, EOS-to-finite-volume density consistency, and transient total-mass closure are
 * reported separately. Coupled solves report a scaled equation residual; staged legacy solves retain their relative
 * iterate-change metric. A small nonlinear metric does not establish convergence when the thermodynamic density is
 * inconsistent with the conservative density solution.
 * </p>
 */
public final class OnePhaseFlowConvergenceReport implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Reason why the solve stopped. */
  public enum ConvergenceReason {
    /** No solve has been run. */
    NOT_RUN(false),
    /** Every applicable convergence criterion was satisfied. */
    CONVERGED(true),
    /** The maximum nonlinear iteration count was reached. */
    MAX_ITERATIONS_REACHED(false),
    /** Backtracking could not find a residual-decreasing step. */
    LINE_SEARCH_FAILED(false),
    /** Residual evaluation, Jacobian construction, or the linear solve failed. */
    NUMERICAL_FAILURE(false),
    /** At least one diagnostic was not finite. */
    NON_FINITE_RESIDUAL(false),
    /** EOS density remained inconsistent with finite-volume density. */
    DENSITY_INCONSISTENT(false),
    /** Finite-volume or thermodynamic total mass did not close. */
    MASS_BALANCE_FAILED(false);

    private final boolean converged;

    ConvergenceReason(boolean converged) {
      this.converged = converged;
    }

    /**
     * Check whether this reason represents convergence.
     *
     * @return true only for {@link #CONVERGED}
     */
    public boolean isConverged() {
      return converged;
    }
  }

  private final ConvergenceReason reason;
  private final boolean dynamic;
  private final int solverType;
  private final int nonlinearIterations;
  private final boolean nonlinearMetricEquationResidual;
  private final double nonlinearUpdateTolerance;
  private final double densityRelativeTolerance;
  private final double massBalanceRelativeTolerance;
  private final double maximumRelativeNonlinearUpdate;
  private final double maximumRelativeDensityResidual;
  private final double initialFiniteVolumeMassKg;
  private final double finalFiniteVolumeMassKg;
  private final double finalThermodynamicMassKg;
  private final double inletBoundaryMassKg;
  private final double outletBoundaryMassKg;
  private final double netBoundaryMassKg;
  private final double finiteVolumeMassResidualKg;
  private final double thermodynamicMassResidualKg;
  private final double relativeFiniteVolumeMassResidual;
  private final double relativeThermodynamicMassResidual;
  private final double[] nonlinearUpdateHistory;
  private final double[] densityResidualHistory;
  private final Double maximumScaledMassEquationResidual;
  private final Double maximumScaledMomentumEquationResidual;
  private final double[] scaledMassEquationResidualHistory;
  private final double[] scaledMomentumEquationResidualHistory;
  private final String message;

  /**
   * Create a convergence report.
   *
   * @param reason reason why the solve stopped
   * @param dynamic true for a transient solve
   * @param solverType selected solver type
   * @param nonlinearIterations number of nonlinear iterations
   * @param nonlinearUpdateTolerance nonlinear convergence-metric tolerance
   * @param densityRelativeTolerance relative EOS-density tolerance
   * @param massBalanceRelativeTolerance relative total-mass tolerance
   * @param maximumRelativeNonlinearUpdate final nonlinear convergence metric
   * @param maximumRelativeDensityResidual final EOS/FV density residual
   * @param initialFiniteVolumeMassKg previous-time finite-volume mass in kg
   * @param finalFiniteVolumeMassKg final conservative mass in kg
   * @param finalThermodynamicMassKg final mass calculated from EOS densities in kg
   * @param inletBoundaryMassKg integrated inlet mass in kg
   * @param outletBoundaryMassKg integrated outlet mass in kg
   * @param netBoundaryMassKg integrated inlet-minus-outlet boundary mass in kg
   * @param finiteVolumeMassResidualKg conservative inventory residual in kg
   * @param thermodynamicMassResidualKg thermodynamic inventory residual in kg
   * @param relativeFiniteVolumeMassResidual relative conservative inventory residual
   * @param relativeThermodynamicMassResidual relative thermodynamic inventory residual
   * @param nonlinearUpdateHistory nonlinear convergence-metric history
   * @param densityResidualHistory EOS/FV density residual history
   * @param message diagnostic summary
   */
  public OnePhaseFlowConvergenceReport(ConvergenceReason reason, boolean dynamic, int solverType,
      int nonlinearIterations, double nonlinearUpdateTolerance, double densityRelativeTolerance,
      double massBalanceRelativeTolerance, double maximumRelativeNonlinearUpdate, double maximumRelativeDensityResidual,
      double initialFiniteVolumeMassKg, double finalFiniteVolumeMassKg, double finalThermodynamicMassKg,
      double inletBoundaryMassKg, double outletBoundaryMassKg, double netBoundaryMassKg,
      double finiteVolumeMassResidualKg, double thermodynamicMassResidualKg, double relativeFiniteVolumeMassResidual,
      double relativeThermodynamicMassResidual, double[] nonlinearUpdateHistory, double[] densityResidualHistory,
      String message) {
    this(reason, dynamic, solverType, nonlinearIterations, nonlinearUpdateTolerance, densityRelativeTolerance,
        massBalanceRelativeTolerance, maximumRelativeNonlinearUpdate, maximumRelativeDensityResidual,
        initialFiniteVolumeMassKg, finalFiniteVolumeMassKg, finalThermodynamicMassKg, inletBoundaryMassKg,
        outletBoundaryMassKg, netBoundaryMassKg, finiteVolumeMassResidualKg, thermodynamicMassResidualKg,
        relativeFiniteVolumeMassResidual, relativeThermodynamicMassResidual, nonlinearUpdateHistory,
        densityResidualHistory, message, false);
  }

  OnePhaseFlowConvergenceReport(ConvergenceReason reason, boolean dynamic, int solverType, int nonlinearIterations,
      double nonlinearUpdateTolerance, double densityRelativeTolerance, double massBalanceRelativeTolerance,
      double maximumRelativeNonlinearUpdate, double maximumRelativeDensityResidual, double initialFiniteVolumeMassKg,
      double finalFiniteVolumeMassKg, double finalThermodynamicMassKg, double inletBoundaryMassKg,
      double outletBoundaryMassKg, double netBoundaryMassKg, double finiteVolumeMassResidualKg,
      double thermodynamicMassResidualKg, double relativeFiniteVolumeMassResidual,
      double relativeThermodynamicMassResidual, double[] nonlinearUpdateHistory, double[] densityResidualHistory,
      String message, boolean nonlinearMetricEquationResidual) {
    this(reason, dynamic, solverType, nonlinearIterations, nonlinearUpdateTolerance, densityRelativeTolerance,
        massBalanceRelativeTolerance, maximumRelativeNonlinearUpdate, maximumRelativeDensityResidual,
        initialFiniteVolumeMassKg, finalFiniteVolumeMassKg, finalThermodynamicMassKg, inletBoundaryMassKg,
        outletBoundaryMassKg, netBoundaryMassKg, finiteVolumeMassResidualKg, thermodynamicMassResidualKg,
        relativeFiniteVolumeMassResidual, relativeThermodynamicMassResidual, nonlinearUpdateHistory,
        densityResidualHistory, Double.NaN, Double.NaN, new double[0], new double[0], message,
        nonlinearMetricEquationResidual);
  }

  OnePhaseFlowConvergenceReport(ConvergenceReason reason, boolean dynamic, int solverType, int nonlinearIterations,
      double nonlinearUpdateTolerance, double densityRelativeTolerance, double massBalanceRelativeTolerance,
      double maximumRelativeNonlinearUpdate, double maximumRelativeDensityResidual, double initialFiniteVolumeMassKg,
      double finalFiniteVolumeMassKg, double finalThermodynamicMassKg, double inletBoundaryMassKg,
      double outletBoundaryMassKg, double netBoundaryMassKg, double finiteVolumeMassResidualKg,
      double thermodynamicMassResidualKg, double relativeFiniteVolumeMassResidual,
      double relativeThermodynamicMassResidual, double[] nonlinearUpdateHistory, double[] densityResidualHistory,
      double maximumScaledMassEquationResidual, double maximumScaledMomentumEquationResidual,
      double[] scaledMassEquationResidualHistory, double[] scaledMomentumEquationResidualHistory, String message,
      boolean nonlinearMetricEquationResidual) {
    this.reason = reason;
    this.dynamic = dynamic;
    this.solverType = solverType;
    this.nonlinearIterations = nonlinearIterations;
    this.nonlinearMetricEquationResidual = nonlinearMetricEquationResidual;
    this.nonlinearUpdateTolerance = nonlinearUpdateTolerance;
    this.densityRelativeTolerance = densityRelativeTolerance;
    this.massBalanceRelativeTolerance = massBalanceRelativeTolerance;
    this.maximumRelativeNonlinearUpdate = maximumRelativeNonlinearUpdate;
    this.maximumRelativeDensityResidual = maximumRelativeDensityResidual;
    this.initialFiniteVolumeMassKg = initialFiniteVolumeMassKg;
    this.finalFiniteVolumeMassKg = finalFiniteVolumeMassKg;
    this.finalThermodynamicMassKg = finalThermodynamicMassKg;
    this.inletBoundaryMassKg = inletBoundaryMassKg;
    this.outletBoundaryMassKg = outletBoundaryMassKg;
    this.netBoundaryMassKg = netBoundaryMassKg;
    this.finiteVolumeMassResidualKg = finiteVolumeMassResidualKg;
    this.thermodynamicMassResidualKg = thermodynamicMassResidualKg;
    this.relativeFiniteVolumeMassResidual = relativeFiniteVolumeMassResidual;
    this.relativeThermodynamicMassResidual = relativeThermodynamicMassResidual;
    this.nonlinearUpdateHistory = copy(nonlinearUpdateHistory);
    this.densityResidualHistory = copy(densityResidualHistory);
    this.maximumScaledMassEquationResidual = maximumScaledMassEquationResidual;
    this.maximumScaledMomentumEquationResidual = maximumScaledMomentumEquationResidual;
    this.scaledMassEquationResidualHistory = copy(scaledMassEquationResidualHistory);
    this.scaledMomentumEquationResidualHistory = copy(scaledMomentumEquationResidualHistory);
    this.message = message;
  }

  /**
   * Create a report for a solver that has not run.
   *
   * @return not-run report
   */
  public static OnePhaseFlowConvergenceReport notRun() {
    return new OnePhaseFlowConvergenceReport(ConvergenceReason.NOT_RUN, false, -1, 0, Double.NaN, Double.NaN,
        Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
        Double.NaN, Double.NaN, Double.NaN, Double.NaN, new double[0], new double[0], "The solver has not run.");
  }

  /** @return reason why the solve stopped */
  public ConvergenceReason getReason() {
    return reason;
  }

  /** @return true when every applicable criterion passed */
  public boolean isConverged() {
    return reason.isConverged();
  }

  /** @return true for a transient solve */
  public boolean isDynamic() {
    return dynamic;
  }

  /** @return selected solver type */
  public int getSolverType() {
    return solverType;
  }

  /** @return number of nonlinear iterations */
  public int getNonlinearIterations() {
    return nonlinearIterations;
  }

  /** @return nonlinear convergence-metric tolerance */
  public double getNonlinearUpdateTolerance() {
    return nonlinearUpdateTolerance;
  }

  /** @return true when the nonlinear metric is a scaled equation residual, false for an iterate change */
  public boolean isNonlinearMetricEquationResidual() {
    return nonlinearMetricEquationResidual;
  }

  /** @return relative EOS-density consistency tolerance */
  public double getDensityRelativeTolerance() {
    return densityRelativeTolerance;
  }

  /** @return relative total-mass tolerance */
  public double getMassBalanceRelativeTolerance() {
    return massBalanceRelativeTolerance;
  }

  /** @return final nonlinear convergence metric */
  public double getMaximumRelativeNonlinearUpdate() {
    return maximumRelativeNonlinearUpdate;
  }

  /** @return final maximum EOS/FV density residual */
  public double getMaximumRelativeDensityResidual() {
    return maximumRelativeDensityResidual;
  }

  /** @return previous-time finite-volume mass in kg */
  public double getInitialFiniteVolumeMassKg() {
    return initialFiniteVolumeMassKg;
  }

  /** @return final conservative finite-volume mass in kg */
  public double getFinalFiniteVolumeMassKg() {
    return finalFiniteVolumeMassKg;
  }

  /** @return final mass calculated from EOS densities in kg */
  public double getFinalThermodynamicMassKg() {
    return finalThermodynamicMassKg;
  }

  /** @return integrated inlet mass in kg */
  public double getInletBoundaryMassKg() {
    return inletBoundaryMassKg;
  }

  /** @return integrated outlet mass in kg */
  public double getOutletBoundaryMassKg() {
    return outletBoundaryMassKg;
  }

  /** @return integrated inlet-minus-outlet boundary mass in kg */
  public double getNetBoundaryMassKg() {
    return netBoundaryMassKg;
  }

  /** @return conservative inventory residual in kg */
  public double getFiniteVolumeMassResidualKg() {
    return finiteVolumeMassResidualKg;
  }

  /** @return thermodynamic inventory residual in kg */
  public double getThermodynamicMassResidualKg() {
    return thermodynamicMassResidualKg;
  }

  /** @return relative conservative inventory residual */
  public double getRelativeFiniteVolumeMassResidual() {
    return relativeFiniteVolumeMassResidual;
  }

  /** @return relative thermodynamic inventory residual */
  public double getRelativeThermodynamicMassResidual() {
    return relativeThermodynamicMassResidual;
  }

  /**
   * @return defensive copy of nonlinear convergence-metric history; the coupled path includes the initial residual at
   * index zero followed by one entry per completed Newton iteration, while staged legacy paths retain one entry per
   * iteration
   */
  public double[] getNonlinearUpdateHistory() {
    return copy(nonlinearUpdateHistory);
  }

  /**
   * @return defensive copy of EOS/FV density residual history using the same indexing convention as
   * {@link #getNonlinearUpdateHistory()}
   */
  public double[] getDensityResidualHistory() {
    return copy(densityResidualHistory);
  }

  /**
   * @return final maximum absolute scaled continuity-equation residual, or NaN when equation-family diagnostics are
   * unavailable
   */
  public double getMaximumScaledMassEquationResidual() {
    return maximumScaledMassEquationResidual == null ? Double.NaN : maximumScaledMassEquationResidual;
  }

  /**
   * @return final maximum absolute scaled momentum-equation residual, or NaN when equation-family diagnostics are
   * unavailable
   */
  public double getMaximumScaledMomentumEquationResidual() {
    return maximumScaledMomentumEquationResidual == null ? Double.NaN : maximumScaledMomentumEquationResidual;
  }

  /**
   * @return defensive copy of the coupled continuity-equation residual history, indexed like
   * {@link #getNonlinearUpdateHistory()}; empty for staged legacy paths
   */
  public double[] getScaledMassEquationResidualHistory() {
    return copy(scaledMassEquationResidualHistory);
  }

  /**
   * @return defensive copy of the coupled momentum-equation residual history, indexed like
   * {@link #getNonlinearUpdateHistory()}; empty for staged legacy paths
   */
  public double[] getScaledMomentumEquationResidualHistory() {
    return copy(scaledMomentumEquationResidualHistory);
  }

  /** @return diagnostic summary */
  public String getMessage() {
    return message;
  }

  /**
   * Serialize this report as stable, pretty-printed JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    JsonSerializer<Double> finiteDoubleSerializer = (value, type,
        context) -> value != null && Double.isFinite(value) ? new JsonPrimitive(value) : JsonNull.INSTANCE;
    return new GsonBuilder().registerTypeAdapter(Double.class, finiteDoubleSerializer)
        .registerTypeAdapter(Double.TYPE, finiteDoubleSerializer).serializeNulls().setPrettyPrinting().create()
        .toJson(this);
  }

  private static double[] copy(double[] values) {
    return values == null ? new double[0] : Arrays.copyOf(values, values.length);
  }
}
