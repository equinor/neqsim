package neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver;

import java.io.Serializable;
import java.util.Arrays;
import com.google.gson.GsonBuilder;

/**
 * Immutable convergence and total-mass diagnostics from a one-phase pipe-flow solve.
 *
 * <p>
 * Nonlinear iterate changes, EOS-to-finite-volume density consistency, and transient total-mass
 * closure are reported separately. A small update does not establish convergence when the
 * thermodynamic density is inconsistent with the conservative density solution.
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
  private final String message;

  /**
   * Create a convergence report.
   *
   * @param reason reason why the solve stopped
   * @param dynamic true for a transient solve
   * @param solverType selected solver type
   * @param nonlinearIterations number of coupled nonlinear iterations
   * @param nonlinearUpdateTolerance relative nonlinear update tolerance
   * @param densityRelativeTolerance relative EOS-density tolerance
   * @param massBalanceRelativeTolerance relative total-mass tolerance
   * @param maximumRelativeNonlinearUpdate final nonlinear update residual
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
   * @param nonlinearUpdateHistory nonlinear update residual history
   * @param densityResidualHistory EOS/FV density residual history
   * @param message diagnostic summary
   */
  public OnePhaseFlowConvergenceReport(ConvergenceReason reason, boolean dynamic, int solverType,
      int nonlinearIterations, double nonlinearUpdateTolerance, double densityRelativeTolerance,
      double massBalanceRelativeTolerance, double maximumRelativeNonlinearUpdate,
      double maximumRelativeDensityResidual, double initialFiniteVolumeMassKg,
      double finalFiniteVolumeMassKg, double finalThermodynamicMassKg,
      double inletBoundaryMassKg, double outletBoundaryMassKg, double netBoundaryMassKg,
      double finiteVolumeMassResidualKg, double thermodynamicMassResidualKg,
      double relativeFiniteVolumeMassResidual, double relativeThermodynamicMassResidual,
      double[] nonlinearUpdateHistory, double[] densityResidualHistory, String message) {
    this.reason = reason;
    this.dynamic = dynamic;
    this.solverType = solverType;
    this.nonlinearIterations = nonlinearIterations;
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
    this.message = message;
  }

  /**
   * Create a report for a solver that has not run.
   *
   * @return not-run report
   */
  public static OnePhaseFlowConvergenceReport notRun() {
    return new OnePhaseFlowConvergenceReport(ConvergenceReason.NOT_RUN, false, -1, 0,
        Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
        Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
        Double.NaN, Double.NaN, Double.NaN, new double[0], new double[0],
        "The solver has not run.");
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

  /** @return number of coupled nonlinear iterations */
  public int getNonlinearIterations() {
    return nonlinearIterations;
  }

  /** @return relative nonlinear update tolerance */
  public double getNonlinearUpdateTolerance() {
    return nonlinearUpdateTolerance;
  }

  /** @return relative EOS-density consistency tolerance */
  public double getDensityRelativeTolerance() {
    return densityRelativeTolerance;
  }

  /** @return relative total-mass tolerance */
  public double getMassBalanceRelativeTolerance() {
    return massBalanceRelativeTolerance;
  }

  /** @return final nonlinear update residual */
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

  /** @return defensive copy of nonlinear update history */
  public double[] getNonlinearUpdateHistory() {
    return copy(nonlinearUpdateHistory);
  }

  /** @return defensive copy of EOS/FV density residual history */
  public double[] getDensityResidualHistory() {
    return copy(densityResidualHistory);
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
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }

  private static double[] copy(double[] values) {
    return values == null ? new double[0] : Arrays.copyOf(values, values.length);
  }
}
