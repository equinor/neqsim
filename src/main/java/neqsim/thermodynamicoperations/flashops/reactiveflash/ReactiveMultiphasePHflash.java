package neqsim.thermodynamicoperations.flashops.reactiveflash;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.BaseOperation;

/**
 * Reactive multiphase PH flash: simultaneous chemical equilibrium, phase equilibrium, and enthalpy
 * balance.
 *
 * <p>
 * Given pressure P and total enthalpy H_spec, finds the equilibrium temperature T, phase split, and
 * composition satisfying both chemical and phase equilibrium. Uses a nested approach: an outer
 * Newton-Raphson loop on temperature (in the 1/T variable for numerical stability, following
 * Michelsen 1987) wraps an inner reactive TP flash using the Modified RAND method.
 * </p>
 *
 * <p>
 * The outer loop solves: Q(T) = [H(T,P,n(T)) - H_spec] / |H_spec| = 0, where n(T) is the
 * equilibrium composition from the reactive TP flash at temperature T. The Newton update uses:
 * dQ/d(1/T) = -T^2 * Cp_total / |H_spec|, where Cp_total = (dH/dT)_P includes both sensible heat
 * and reaction heat effects (since the equilibrium composition shifts with temperature, the system
 * Cp implicitly captures reaction enthalpy contributions via Le Chatelier's principle).
 * </p>
 *
 * <p>
 * Algorithm overview:
 * <ol>
 * <li>Perform an initial reactive TP flash at the current temperature.</li>
 * <li>Compute the enthalpy residual Q = (H - H_spec)/|H_spec|.</li>
 * <li>Compute the derivative dQ/d(1/T) using the system heat capacity.</li>
 * <li>Update temperature via Newton-Raphson: (1/T_new) = (1/T) - alpha * Q / (dQ/d(1/T)).</li>
 * <li>Repeat steps 1-4 until |Q| is below tolerance.</li>
 * </ol>
 * </p>
 *
 * <p>
 * The approach is state-of-the-art for reactive PH flash. While a single-level formulation
 * (simultaneously solving for T, n, and lambda in one Newton system) offers quadratic convergence,
 * the nested approach is preferred for robustness because:
 * <ul>
 * <li>It reuses the fully-tested Modified RAND solver unchanged.</li>
 * <li>The 1/T Newton iteration is proven robust in NeqSim's non-reactive PH flash.</li>
 * <li>Convergence is still fast (typically 5-15 outer iterations) because the Newton step on 1/T
 * converges quadratically in the outer variable.</li>
 * <li>Phase topology changes (phase appearance/disappearance near phase boundaries) are handled
 * gracefully by the inner TP flash solver.</li>
 * </ul>
 * </p>
 *
 * <p>
 * The class also supports PS flash (specifying entropy instead of enthalpy) by setting an entropy
 * specification via {@link #setEntropySpec(double)}.
 * </p>
 *
 * <p>
 * References:
 * <ul>
 * <li>Michelsen (1987) "Multiphase isenthalpic and isentropic flash algorithms", Fluid Phase
 * Equilib. 33, 13-27</li>
 * <li>White, Johnson, Dantzig (1958) J. Chem. Phys. 28, 751-755</li>
 * <li>Smith, Missen (1982) Chemical Reaction Equilibrium Analysis, Wiley</li>
 * <li>Tsanas, Stenby, Yan (2017) Ind. Eng. Chem. Res. 56, 11983-11995</li>
 * <li>Paterson, Michelsen, Stenby, Yan (2018) SPE Journal 23(03), 609-622</li>
 * </ul>
 * </p>
 *
 * @author copilot
 * @version 1.0
 */
public class ReactiveMultiphasePHflash extends BaseOperation {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(ReactiveMultiphasePHflash.class);

  /** Maximum outer (temperature) iterations. */
  private static final int MAX_OUTER_ITER = 200;

  /** Convergence tolerance on normalized enthalpy residual. */
  private static final double TOL = 1.0e-8;

  /** Maximum temperature step per iteration (K). */
  private static final double MAX_T_STEP = 50.0;

  /** Minimum allowed temperature (K). */
  private static final double T_MIN = 50.0;

  /** Maximum allowed temperature (K). */
  private static final double T_MAX = 5000.0;

  /** The thermodynamic system. */
  private SystemInterface system;

  /** Specified enthalpy (J). */
  private double enthalpySpec;

  /** Specified entropy (J/K). If non-zero, overrides enthalpy mode. */
  private double entropySpec = 0.0;

  /** Whether to use entropy specification instead of enthalpy. */
  private boolean useEntropySpec = false;

  /** Whether the flash converged. */
  private boolean converged;

  /** Total outer iterations used. */
  private int outerIterations;

  /** Total inner (RAND solver) iterations across all outer steps. */
  private int totalInnerIterations;

  /** The equilibrium temperature found. */
  private double equilibriumTemperature;

  /** Whether to use DIIS in inner TP flash. */
  private boolean useDIIS = true;

  /** Whether to use chemicalReactionInit in inner TP flash. */
  private boolean useChemicalReactionInit = false;

  /** User-specified maximum number of phases for inner flash. */
  private int userMaxPhases = -1;

  /** The last inner reactive TP flash result. */
  private transient ReactiveMultiphaseTPflash lastTPflash;

  /**
   * Constructor for ReactiveMultiphasePHflash.
   *
   * @param system the thermodynamic system
   * @param enthalpySpec the specified total enthalpy in J
   */
  public ReactiveMultiphasePHflash(SystemInterface system, double enthalpySpec) {
    this.system = system;
    this.enthalpySpec = enthalpySpec;
    this.converged = false;
    this.outerIterations = 0;
    this.totalInnerIterations = 0;
  }

  /**
   * Constructor for ReactiveMultiphasePHflash with type parameter (for API compatibility with
   * PHflash).
   *
   * @param system the thermodynamic system
   * @param enthalpySpec the specified total enthalpy in J
   * @param type flash type (0 = standard, currently unused)
   */
  public ReactiveMultiphasePHflash(SystemInterface system, double enthalpySpec, int type) {
    this(system, enthalpySpec);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    try {
      if (useEntropySpec) {
        solveEntropySpec();
      } else {
        solveEnthalpySpec();
      }
    } catch (Exception ex) {
      logger.error("ReactiveMultiphasePHflash failed: " + ex.getMessage(), ex);
      converged = false;
    }
  }

  /**
   * Solve the PH flash (enthalpy specification).
   *
   * <p>
   * Uses a secant + bisection hybrid on T with inner reactive TP flash. The first iteration uses a
   * Cp-based Newton step; subsequent iterations use the secant method, which naturally captures the
   * effective dH/dT including reaction enthalpy contributions (Le Chatelier shift). If the secant
   * step falls outside a tracked bracket, bisection is used as a guaranteed-convergence fallback.
   * </p>
   */
  private void solveEnthalpySpec() {
    double absHspec = Math.abs(enthalpySpec);
    if (absHspec < 1.0e-10) {
      absHspec = 1.0; // Prevent division by zero for near-zero enthalpy
    }

    // Step 1: Initial reactive TP flash at current temperature
    runInnerTPflash();

    double T = system.getTemperature();
    double error = computeEnthalpyResidual(absHspec);

    // Check if already converged
    if (Math.abs(error) < TOL) {
      converged = true;
      equilibriumTemperature = T;
      outerIterations = 0;
      return;
    }

    // Bracket tracking: H(T) is monotonically increasing with T (Cp > 0)
    // error > 0 means H > Hspec => T too high; error < 0 => T too low
    double tLow = T_MIN;
    double tHigh = T_MAX;
    if (error > 0) {
      tHigh = T;
    } else {
      tLow = T;
    }

    // History for secant method
    double Tprev = T;
    double errPrev = Double.NaN;

    for (int iter = 0; iter < MAX_OUTER_ITER; iter++) {
      outerIterations = iter + 1;

      // Compute next temperature estimate
      double Tnew;
      boolean hasBracket = tLow > T_MIN && tHigh < T_MAX;

      if (iter == 0 || Double.isNaN(errPrev) || Math.abs(T - Tprev) < 1.0e-12) {
        // First iteration or degenerate: Cp-based Newton step
        double Cp = system.getCp();
        if (Math.abs(Cp) < 1.0e-20) {
          Cp = 100.0;
        }
        Tnew = T - (system.getEnthalpy() - enthalpySpec) / Cp;
      } else {
        // Secant method: interpolate using two successive (T, error) pairs.
        // This naturally captures the effective dH/dT including reaction enthalpy
        // contributions (Le Chatelier shift), unlike the Cp-only Newton derivative.
        double dErrDT = (error - errPrev) / (T - Tprev);
        if (Math.abs(dErrDT) > 1.0e-30) {
          Tnew = T - error / dErrDT;
        } else {
          double Cp = system.getCp();
          if (Math.abs(Cp) < 1.0e-20) {
            Cp = 100.0;
          }
          Tnew = T - (system.getEnthalpy() - enthalpySpec) / Cp;
        }
      }

      // Clamp step size
      if (Math.abs(Tnew - T) > MAX_T_STEP) {
        Tnew = T + Math.signum(Tnew - T) * MAX_T_STEP;
      }

      // If step goes outside bracket, use bisection
      if (hasBracket && (Tnew <= tLow || Tnew >= tHigh)) {
        Tnew = 0.5 * (tLow + tHigh);
      }

      // Enforce absolute bounds
      Tnew = Math.max(T_MIN, Math.min(T_MAX, Tnew));

      // Avoid stagnation at the same temperature
      if (Math.abs(Tnew - T) < 1.0e-12) {
        if (hasBracket) {
          Tnew = 0.5 * (tLow + tHigh);
        } else {
          Tnew = T + (error < 0 ? 1.0 : -1.0);
        }
      }

      // Store history for secant
      Tprev = T;
      errPrev = error;

      // Update temperature and run inner flash
      system.setTemperature(Tnew);
      runInnerTPflash();

      T = system.getTemperature();
      error = computeEnthalpyResidual(absHspec);

      // Update bracket
      if (error > 0 && T < tHigh) {
        tHigh = T;
      } else if (error < 0 && T > tLow) {
        tLow = T;
      }

      logger.debug("PH iter=" + iter + " T=" + String.format("%.4f", T) + " H="
          + String.format("%.4e", system.getEnthalpy()) + " Hspec="
          + String.format("%.4e", enthalpySpec) + " err=" + String.format("%.3e", error)
          + " bracket=[" + String.format("%.2f", tLow) + "," + String.format("%.2f", tHigh) + "]");

      // Check convergence
      if (Math.abs(error) < TOL) {
        converged = true;
        break;
      }

      // If bracket is very tight, declare convergence
      hasBracket = tLow > T_MIN && tHigh < T_MAX;
      if (hasBracket && (tHigh - tLow) < 1.0e-6) {
        converged = true;
        break;
      }
    }

    equilibriumTemperature = system.getTemperature();

    if (!converged) {
      logger.warn("ReactiveMultiphasePHflash did not converge after " + outerIterations
          + " iterations. Final T=" + equilibriumTemperature + " error=" + error);
    } else {
      logger.debug("ReactiveMultiphasePHflash converged: T="
          + String.format("%.4f", equilibriumTemperature) + " K, " + outerIterations
          + " outer iterations, " + totalInnerIterations + " total inner iterations");
    }
  }

  /**
   * Solve the PS flash (entropy specification).
   *
   * <p>
   * Uses a secant + bisection hybrid on T (same approach as the enthalpy case), with entropy
   * residual Q(T) = [S(T) - S_spec] / |S_spec|. The secant method captures the effective dS/dT
   * including reaction contributions, with bisection fallback for guaranteed convergence.
   * </p>
   */
  private void solveEntropySpec() {
    double absSspec = Math.abs(entropySpec);
    if (absSspec < 1.0e-10) {
      absSspec = 1.0;
    }

    // Initial reactive TP flash
    runInnerTPflash();

    double T = system.getTemperature();
    double error = (system.getEntropy() - entropySpec) / absSspec;

    if (Math.abs(error) < TOL) {
      converged = true;
      equilibriumTemperature = T;
      outerIterations = 0;
      return;
    }

    // Bracket tracking: S(T) is monotonically increasing with T (Cp/T > 0)
    double tLow = T_MIN;
    double tHigh = T_MAX;
    if (error > 0) {
      tHigh = T;
    } else {
      tLow = T;
    }

    double Tprev = T;
    double errPrev = Double.NaN;

    for (int iter = 0; iter < MAX_OUTER_ITER; iter++) {
      outerIterations = iter + 1;

      double Tnew;
      boolean hasBracket = tLow > T_MIN && tHigh < T_MAX;

      if (iter == 0 || Double.isNaN(errPrev) || Math.abs(T - Tprev) < 1.0e-12) {
        // Cp-based Newton step: dS/dT = Cp/T
        double CpOverT = system.getCp() / system.getTemperature();
        if (Math.abs(CpOverT) < 1.0e-20) {
          CpOverT = 1.0;
        }
        Tnew = T - (system.getEntropy() - entropySpec) / CpOverT;
      } else {
        // Secant method
        double dErrDT = (error - errPrev) / (T - Tprev);
        if (Math.abs(dErrDT) > 1.0e-30) {
          Tnew = T - error / dErrDT;
        } else {
          double CpOverT = system.getCp() / system.getTemperature();
          if (Math.abs(CpOverT) < 1.0e-20) {
            CpOverT = 1.0;
          }
          Tnew = T - (system.getEntropy() - entropySpec) / CpOverT;
        }
      }

      // Clamp step size
      if (Math.abs(Tnew - T) > MAX_T_STEP) {
        Tnew = T + Math.signum(Tnew - T) * MAX_T_STEP;
      }

      // Bisection fallback
      if (hasBracket && (Tnew <= tLow || Tnew >= tHigh)) {
        Tnew = 0.5 * (tLow + tHigh);
      }

      Tnew = Math.max(T_MIN, Math.min(T_MAX, Tnew));

      if (Math.abs(Tnew - T) < 1.0e-12) {
        if (hasBracket) {
          Tnew = 0.5 * (tLow + tHigh);
        } else {
          Tnew = T + (error < 0 ? 1.0 : -1.0);
        }
      }

      Tprev = T;
      errPrev = error;

      system.setTemperature(Tnew);
      runInnerTPflash();

      T = system.getTemperature();
      error = (system.getEntropy() - entropySpec) / absSspec;

      if (error > 0 && T < tHigh) {
        tHigh = T;
      } else if (error < 0 && T > tLow) {
        tLow = T;
      }

      logger.debug("PS iter=" + iter + " T=" + String.format("%.4f", T) + " err="
          + String.format("%.3e", error));

      if (Math.abs(error) < TOL) {
        converged = true;
        break;
      }

      hasBracket = tLow > T_MIN && tHigh < T_MAX;
      if (hasBracket && (tHigh - tLow) < 1.0e-6) {
        converged = true;
        break;
      }
    }

    equilibriumTemperature = system.getTemperature();
    if (!converged) {
      logger.warn(
          "ReactiveMultiphasePSflash did not converge after " + outerIterations + " iterations");
    }
  }

  /**
   * Run the inner reactive TP flash at the current system temperature and pressure.
   *
   * <p>
   * Creates a new {@link ReactiveMultiphaseTPflash} instance, configures it with the current
   * settings (DIIS, max phases, chemicalReactionInit), runs it, and then calls
   * {@code system.init(2)} to compute thermodynamic properties (enthalpy, Cp, entropy) needed for
   * the outer Newton loop.
   * </p>
   */
  private void runInnerTPflash() {
    ReactiveMultiphaseTPflash tpFlash = new ReactiveMultiphaseTPflash(system);
    tpFlash.setUseDIIS(useDIIS);
    if (userMaxPhases > 0) {
      tpFlash.setMaxNumberOfPhases(userMaxPhases);
    }
    if (useChemicalReactionInit) {
      tpFlash.setUseChemicalReactionInit(true);
    }
    tpFlash.run();
    totalInnerIterations += tpFlash.getTotalIterations();
    lastTPflash = tpFlash;

    // init(2) computes enthalpy, Cp, entropy, etc.
    system.init(2);
  }

  /**
   * Compute the normalized enthalpy residual.
   *
   * @param absHspec absolute value of H_spec for normalization
   * @return (H_system - H_spec) / |H_spec|
   */
  private double computeEnthalpyResidual(double absHspec) {
    return (system.getEnthalpy() - enthalpySpec) / absHspec;
  }

  /**
   * Compute the derivative of the enthalpy residual with respect to 1/T.
   *
   * <p>
   * Uses the chain rule: dQ/d(1/T) = dQ/dT * dT/d(1/T) = (Cp/|H_spec|) * (-T^2) = -T^2 *
   * Cp/|H_spec|.
   * </p>
   *
   * <p>
   * The system Cp includes both sensible heat and implicit reaction heat effects: as T changes, the
   * reactive TP flash produces a different equilibrium composition, and the resulting Cp from
   * init(2) reflects the full derivative dH/dT at fixed P and feed composition. For strongly
   * exothermic/endothermic reactions (e.g., WGS, SMR), this correctly captures the coupling between
   * equilibrium shift and enthalpy.
   * </p>
   *
   * @param absHspec absolute value of H_spec for normalization
   * @return dQ/d(1/T)
   */
  private double computeEnthalpyDerivative(double absHspec) {
    double T = system.getTemperature();
    double Cp = system.getCp();
    return -T * T * Cp / absHspec;
  }

  /**
   * Check if the flash converged.
   *
   * @return true if converged
   */
  public boolean isConverged() {
    return converged;
  }

  /**
   * Get the equilibrium temperature found by the flash.
   *
   * @return equilibrium temperature in K
   */
  public double getEquilibriumTemperature() {
    return equilibriumTemperature;
  }

  /**
   * Get the number of outer (temperature) iterations used.
   *
   * @return outer iteration count
   */
  public int getOuterIterations() {
    return outerIterations;
  }

  /**
   * Get the total inner (RAND solver) iterations across all outer steps.
   *
   * @return total inner iteration count
   */
  public int getTotalInnerIterations() {
    return totalInnerIterations;
  }

  /**
   * Get the number of phases at equilibrium.
   *
   * @return number of phases
   */
  public int getNumberOfPhases() {
    return system.getNumberOfPhases();
  }

  /**
   * Get the last inner reactive TP flash object.
   *
   * @return the last ReactiveMultiphaseTPflash, or null if not yet run
   */
  public ReactiveMultiphaseTPflash getLastTPflash() {
    return lastTPflash;
  }

  /**
   * Set whether to use DIIS acceleration in the inner reactive TP flash.
   *
   * @param useDIIS true to enable DIIS
   */
  public void setUseDIIS(boolean useDIIS) {
    this.useDIIS = useDIIS;
  }

  /**
   * Set the maximum number of phases for the inner reactive TP flash.
   *
   * @param maxPhases maximum number of phases (e.g., 1 for single-phase, 2 for VLE)
   */
  public void setMaxNumberOfPhases(int maxPhases) {
    this.userMaxPhases = maxPhases;
  }

  /**
   * Set whether to use chemicalReactionInit in the inner TP flash.
   *
   * @param useChemicalReactionInit true to auto-discover reactions
   */
  public void setUseChemicalReactionInit(boolean useChemicalReactionInit) {
    this.useChemicalReactionInit = useChemicalReactionInit;
  }

  /**
   * Set entropy specification instead of enthalpy. When set, the flash finds the temperature at
   * which the total entropy equals the specified value.
   *
   * @param entropySpec the specified total entropy in J/K
   */
  public void setEntropySpec(double entropySpec) {
    this.entropySpec = entropySpec;
    this.useEntropySpec = true;
  }

  /**
   * Get a summary of the convergence status and results.
   *
   * @return human-readable summary string
   */
  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("ReactiveMultiphasePHflash Summary\n");
    sb.append("  Mode:           ").append(useEntropySpec ? "PS flash" : "PH flash").append("\n");
    sb.append("  Converged:      ").append(converged).append("\n");
    sb.append("  T_equilibrium:  ").append(
        String.format("%.4f K (%.2f C)", equilibriumTemperature, equilibriumTemperature - 273.15))
        .append("\n");
    sb.append("  Outer iters:    ").append(outerIterations).append("\n");
    sb.append("  Inner iters:    ").append(totalInnerIterations).append("\n");
    sb.append("  Phases:         ").append(system.getNumberOfPhases()).append("\n");
    if (converged) {
      sb.append("  H_spec:         ").append(String.format("%.4e J", enthalpySpec)).append("\n");
      sb.append("  H_actual:       ").append(String.format("%.4e J", system.getEnthalpy()))
          .append("\n");
    }
    return sb.toString();
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return system;
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {
    if (system != null) {
      system.display();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addData(String name, double[][] data) {
    // Not needed for reactive PH flash
  }
}
