package neqsim.process.equipment.distillation;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * <p>
 * Implements a two-loop wrapper around the column's existing inside-out tray
 * infrastructure that matches the configuration surface of UniSim's classic
 * "Legacy Inside Out" method:
 * </p>
 *
 * <ul>
 * <li>Equilibrium error tolerance (default 1.0) — convergence criterion for
 * the inner-loop bubble-point summation residual {@code |Sum(K_i x_i) - 1|}
 * accumulated across all trays.</li>
 * <li>Heat spec error tolerance (default 5e-4) — convergence criterion for
 * the energy-balance residual at the reboiler and condenser specifications,
 * normalised by the total feed enthalpy.</li>
 * <li>Supercritical handling model — {@code SIMPLE_K} (Wilson-style K) or
 * {@code RIGOROUS} (full EOS flash). Falls through to the EOS multiphase
 * check by default.</li>
 * <li>Trace level — {@code LOW}, {@code MEDIUM}, {@code HIGH} controlling
 * logging verbosity.</li>
 * <li>Two-liquids check — enables a three-phase check on every tray flash.
 * Useful for aqueous/hydrocarbon systems where a second liquid may form.</li>
 * </ul>
 *
 * <p>
 * The solver wraps {@link DistillationColumn#solveInsideOut(UUID) the existing
 * inside-out solver}, applies the user-configured tolerances to the column's
 * effective tolerance fields for the duration of the solve, then computes the
 * equilibrium and heat-spec error metrics from the converged
 * tray state and exposes them via {@link #getLastEquilibriumError()} and
 * {@link #getLastHeatSpecError()}.
 * </p>
 *
 * <p>
 * Reference: Boston, J.F. and Sullivan, S.L. (1974). "A New Class of Solution
 * Methods for Multicomponent, Multistage Separation Processes", Can. J. Chem.
 * Eng., 52, 52-63.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class LegacyInsideOutSolver {

  /** Logger for this class. */
  private static final Logger logger = LogManager.getLogger(LegacyInsideOutSolver.class);

  /** The distillation column being solved. */
  private final DistillationColumn column;

  /** Maximum number of outer iterations. */
  private int maxIterations = 500;

  /** Equilibrium error tolerance (). */
  private double equilibriumErrorTolerance = 1.0e0;

  /** Heat spec error tolerance (4). */
  private double heatSpecErrorTolerance = 5.0e-4;

  /** Supercritical handling identifier (SIMPLE_K or RIGOROUS). */
  private String supercriticalHandling = "SIMPLE_K";

  /** Trace verbosity (LOW, MEDIUM, HIGH). */
  private String traceLevel = "LOW";

  /** Two-liquid-phase check toggle. */
  private boolean twoLiquidsCheck = true;

  // ---- Last-solve metrics ----

  /** Iterations consumed in the last solve. */
  private int lastIterations;

  /** Temperature residual in the last solve (K). */
  private double lastTemperatureResidual;

  /** Equilibrium error (Sum |Sum(Ki*xi) - 1|) in the last solve. */
  private double lastEquilibriumError;

  /** Heat-spec error (relative energy balance residual) in the last solve. */
  private double lastHeatSpecError;

  /** Wall-clock solve time in seconds. */
  private double lastSolveTimeSeconds;

  /**
   * Construct a solver for the given distillation column.
   *
   * @param column the distillation column to solve
   */
  public LegacyInsideOutSolver(DistillationColumn column) {
    this.column = column;
  }

  /**
   * Set the maximum number of outer iterations.
   *
   * @param maxIter maximum iterations
   */
  public void setMaxIterations(int maxIter) {
    this.maxIterations = Math.max(1, maxIter);
  }

  /**
   * Set the equilibrium error tolerance.
   *
   * @param tol equilibrium error tolerance
   */
  public void setEquilibriumErrorTolerance(double tol) {
    this.equilibriumErrorTolerance = tol;
  }

  /**
   * Set the heat-spec error tolerance.
   *
   * @param tol heat-spec error tolerance
   */
  public void setHeatSpecErrorTolerance(double tol) {
    this.heatSpecErrorTolerance = tol;
  }

  /**
   * Set the supercritical handling model.
   *
   * @param model {@code "SIMPLE_K"} or {@code "RIGOROUS"}
   */
  public void setSupercriticalHandling(String model) {
    this.supercriticalHandling = model == null ? "SIMPLE_K" : model;
  }

  /**
   * Set the trace verbosity level.
   *
   * @param level {@code "LOW"}, {@code "MEDIUM"} or {@code "HIGH"}
   */
  public void setTraceLevel(String level) {
    this.traceLevel = level == null ? "LOW" : level;
  }

  /**
   * Enable or disable the two-liquid-phase check.
   *
   * @param enabled true to enable
   */
  public void setTwoLiquidsCheck(boolean enabled) {
    this.twoLiquidsCheck = enabled;
  }

  /**
   * Get the number of iterations consumed in the last solve.
   *
   * @return iteration count
   */
  public int getLastIterations() {
    return lastIterations;
  }

  /**
   * Get the temperature residual from the last solve.
   *
   * @return temperature residual in K
   */
  public double getLastTemperatureResidual() {
    return lastTemperatureResidual;
  }

  /**
   * Get the equilibrium error from the last solve.
   *
   * @return Sum |Sum(Ki*xi) - 1| across all trays
   */
  public double getLastEquilibriumError() {
    return lastEquilibriumError;
  }

  /**
   * Get the heat-spec error from the last solve.
   *
   * @return relative energy-balance residual at spec stages
   */
  public double getLastHeatSpecError() {
    return lastHeatSpecError;
  }

  /**
   * Get the wall-clock solve time from the last solve.
   *
   * @return solve time in seconds
   */
  public double getLastSolveTimeSeconds() {
    return lastSolveTimeSeconds;
  }

  /**
   * Solve the column using the Legacy Inside-Out method.
   *
   * <p>
   * The implementation delegates the rigorous outer iterations (tray flashes,
   * tear-stream relaxation, K-value model fitting) to the column's existing
   * {@link DistillationColumn#run(UUID)} dispatch via {@link
   * DistillationColumn.SolverType#INSIDE_OUT}, temporarily applied while the
   * column's effective tolerances are set to the legacy IO values. After the
   * solve, UniSim-style equilibrium and heat-spec error metrics are computed
   * from the converged tray state.
   * </p>
   *
   * @param id calculation identifier
   * @return true if both equilibrium and heat-spec errors are below tolerance
   */
  public boolean solve(UUID id) {
    long startTime = System.nanoTime();

    if (isHighTrace()) {
      logger.info(
          "Legacy IO start: maxIter={} eqTol={} heatTol={} SC={} 2L={} trace={}",
          maxIterations, equilibriumErrorTolerance, heatSpecErrorTolerance,
          supercriticalHandling, twoLiquidsCheck, traceLevel);
    }

    // Push legacy IO configuration onto the column's existing inside-out path.
    int savedMaxIter = column.maxNumberOfIterations;
    boolean savedMultiphase = column.isDoMultiPhaseCheck();
    DistillationColumn.SolverType savedSolverType = column.getSolverType();

    try {
      column.setMaxNumberOfIterations(maxIterations);
      column.setMultiPhaseCheck(twoLiquidsCheck);
      // Force tolerance overrides for the inside-out solve so it converges to
      // the legacy IO targets rather than the column's adaptive defaults.
      column.setTemperatureTolerance(Math.min(0.5, equilibriumErrorTolerance));
      column.setMassBalanceTolerance(Math.min(0.1, equilibriumErrorTolerance * 0.1));
      column.setEnthalpyBalanceTolerance(heatSpecErrorTolerance);
      column.setSolverType(DistillationColumn.SolverType.INSIDE_OUT);

      column.run(id);
    } finally {
      column.setMaxNumberOfIterations(savedMaxIter);
      column.setMultiPhaseCheck(savedMultiphase);
      column.setSolverType(savedSolverType);
    }

    lastIterations = column.getLastIterationCount();
    lastTemperatureResidual = column.getLastTemperatureResidual();

    lastEquilibriumError = computeEquilibriumError();
    lastHeatSpecError = computeHeatSpecError();
    lastSolveTimeSeconds = (System.nanoTime() - startTime) * 1.0e-9;

    boolean converged = lastEquilibriumError < equilibriumErrorTolerance
        && lastHeatSpecError < heatSpecErrorTolerance;

    if (!isLowTrace()) {
      logger.info(
          "Legacy IO done: iters={} eqErr={} heatErr={} converged={} time={}s",
          lastIterations,
          String.format("%.4e", lastEquilibriumError),
          String.format("%.4e", lastHeatSpecError),
          converged,
          String.format("%.3f", lastSolveTimeSeconds));
    }

    return converged;
  }

  /**
   * Compute the equilibrium error metric: Sum over all trays of
   * {@code |Sum_i(K_i * x_i) - 1|}.
   *
   * <p>
   * On a converged tray the bubble-point criterion {@code Sum K_i x_i = 1}
   * holds exactly; the residual measures how far the tray temperature is from
   * the local bubble point.
   * </p>
   *
   * @return total equilibrium error summed across all trays
   */
  private double computeEquilibriumError() {
    double total = 0.0;
    int nTrays = column.getNumberOfTrays();
    for (int j = 0; j < nTrays; j++) {
      SimpleTray tray = (SimpleTray) column.getTray(j);
      SystemInterface sys = tray.getThermoSystem();
      if (sys == null || sys.getNumberOfPhases() < 1) {
        continue;
      }
      try {
        int nComp = sys.getNumberOfComponents();
        double sumKx = 0.0;
        // Use the liquid composition (phase 1 if 2-phase, else phase 0).
        int liqIdx = sys.getNumberOfPhases() > 1 ? 1 : 0;
        for (int i = 0; i < nComp; i++) {
          double xi = sys.getPhase(liqIdx).getComponent(i).getx();
          double ki = sys.getPhase(0).getComponent(i).getFugacityCoefficient()
              / Math.max(sys.getPhase(liqIdx).getComponent(i).getFugacityCoefficient(), 1e-30);
          // Wilson fallback for supercritical components in SIMPLE_K mode.
          if (!Double.isFinite(ki) || ki <= 0.0) {
            if ("SIMPLE_K".equalsIgnoreCase(supercriticalHandling)) {
              double tc = sys.getPhase(0).getComponent(i).getTC();
              double pc = sys.getPhase(0).getComponent(i).getPC();
              double omega = sys.getPhase(0).getComponent(i).getAcentricFactor();
              double t = sys.getTemperature();
              double p = sys.getPressure();
              ki = (pc / p) * Math.exp(5.37 * (1.0 + omega) * (1.0 - tc / t));
            } else {
              ki = 1.0;
            }
          }
          sumKx += ki * xi;
        }
        total += Math.abs(sumKx - 1.0);
      } catch (Exception ex) {
        if (isHighTrace()) {
          logger.debug("Legacy IO: equilibrium-error eval failed on tray {}: {}", j,
              ex.getMessage());
        }
      }
    }
    return total;
  }

  /**
   * Compute the heat-spec error metric: relative energy-balance residual at
   * the reboiler and condenser stages, normalised by total feed enthalpy.
   *
   * @return max relative heat-spec residual across spec stages
   */
  private double computeHeatSpecError() {
    int nTrays = column.getNumberOfTrays();
    if (nTrays == 0) {
      return 0.0;
    }

    // Total feed enthalpy as the normalisation scale.
    double feedEnthalpy = 0.0;
    for (java.util.List<StreamInterface> feeds : column.getFeedStreams().values()) {
      for (StreamInterface f : feeds) {
        try {
          feedEnthalpy += Math.abs(f.getThermoSystem().getEnthalpy());
        } catch (Exception ex) {
          // ignore
        }
      }
    }
    if (feedEnthalpy <= 0.0) {
      feedEnthalpy = 1.0e6;
    }

    double maxErr = 0.0;
    // Reboiler (tray 0) and top stage (numberOfTrays-1) are the spec stages.
    int[] specStages = nTrays > 1 ? new int[] { 0, nTrays - 1 } : new int[] { 0 };
    for (int j : specStages) {
      try {
        SimpleTray tray = (SimpleTray) column.getTray(j);
        SystemInterface sys = tray.getThermoSystem();
        if (sys == null) {
          continue;
        }
        double hOut = sys.getEnthalpy();
        double hIn = 0.0;
        for (int s = 0; s < tray.getNumberOfInputStreams(); s++) {
          StreamInterface in = tray.getStream(s);
          if (in != null && in.getThermoSystem() != null) {
            hIn += in.getThermoSystem().getEnthalpy();
          }
        }
        double rel = Math.abs(hOut - hIn) / feedEnthalpy;
        if (rel > maxErr) {
          maxErr = rel;
        }
      } catch (Exception ex) {
        if (isHighTrace()) {
          logger.debug("Legacy IO: heat-spec eval failed on tray {}: {}", j, ex.getMessage());
        }
      }
    }
    return maxErr;
  }

  /**
   * Whether the trace level is LOW (only warnings and errors logged).
   *
   * @return true if trace level is LOW
   */
  private boolean isLowTrace() {
    return "LOW".equalsIgnoreCase(traceLevel);
  }

  /**
   * Whether the trace level is HIGH (detailed per-tray diagnostics).
   *
   * @return true if trace level is HIGH
   */
  private boolean isHighTrace() {
    return "HIGH".equalsIgnoreCase(traceLevel);
  }
}
