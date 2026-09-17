package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.system.SystemInterface;

/**
 * Pressure-entropy flash with a safeguarded temperature solve.
 *
 * <p>
 * Normal return requires finite state variables, normalized phase fractions and a total entropy residual within
 * {@code max(1e-8 * n, 1e-10 * abs(Sspec))} J/K, where n is the total amount in moles. Non-convergence is reported with
 * an {@link IllegalStateException}.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class PSFlash extends QfuncFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Absolute molar entropy tolerance in J/(mol K). */
  private static final double MOLAR_ENTROPY_TOLERANCE = 1.0e-8;
  /** Relative total-entropy tolerance for the temperature iteration. */
  private static final double RELATIVE_ENTROPY_TOLERANCE = 1.0e-10;
  /** Number of non-improving Newton iterations before a cold bracket recovery. */
  private static final int STAGNATION_LIMIT = 8;
  /** Maximum number of safeguarded temperature iterations. */
  private static final int MAX_ITERATIONS = 200;
  /** Maximum number of temperature expansions used to find a cold entropy bracket. */
  private static final int MAX_BRACKET_EXPANSIONS = 40;
  /** Lowest temperature considered by the generic cold-bracket recovery in K. */
  private static final double MIN_BRACKET_TEMPERATURE = 1.0;
  /** Highest temperature considered by the generic cold-bracket recovery in K. */
  private static final double MAX_BRACKET_TEMPERATURE = 5000.0;

  double Sspec = 0;
  Flash tpFlash;
  int type = 0;

  /**
   * Constructor for PSFlash.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Sspec a double
   * @param type a int
   */
  public PSFlash(SystemInterface system, double Sspec, int type) {
    this.system = system;
    this.tpFlash = new TPflash(system);
    this.Sspec = Sspec;
    this.type = type;
  }

  /** {@inheritDoc} */
  @Override
  public double calcdQdTT() {
    if (system.getNumberOfPhases() == 1) {
      return -system.getPhase(0).getCp() / system.getTemperature();
    }

    double dQdTT = 0.0;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      dQdTT -= system.getPhase(i).getCp() / system.getPhase(i).getTemperature();
    }
    return dQdTT;
  }

  /** {@inheritDoc} */
  @Override
  public double calcdQdT() {
    double dQ = -system.getEntropy() + Sspec;
    return dQ;
  }

  /** {@inheritDoc} */
  @Override
  public double solveQ() {
    double lowerTemperature = Double.NaN;
    double upperTemperature = Double.NaN;
    double tolerance = entropyTolerance(system, Sspec);
    double bestResidual = Double.POSITIVE_INFINITY;
    int stagnantIterations = 0;
    system.init(2);

    for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
      double temperature = system.getTemperature();
      double residual = system.getEntropy() - Sspec;
      if (!Double.isFinite(residual)) {
        throw convergenceFailure(system, Sspec, "non-finite entropy");
      }
      double absoluteResidual = Math.abs(residual);
      if (absoluteResidual <= tolerance) {
        return temperature;
      }
      if (absoluteResidual < bestResidual * (1.0 - 1.0e-8)) {
        bestResidual = absoluteResidual;
        stagnantIterations = 0;
      } else {
        stagnantIterations++;
      }
      if (stagnantIterations >= STAGNATION_LIMIT) {
        return solveWithColdBracket(temperature, tolerance);
      }

      // Equilibrium entropy increases with temperature at fixed pressure. Phase Cp/T does not
      // include phase-transfer contributions, so Newton steps need a sign-changing bracket.
      if (residual < 0.0) {
        lowerTemperature = temperature;
      } else {
        upperTemperature = temperature;
      }
      double derivative = -calcdQdTT();
      double step = Double.isFinite(derivative) && derivative > 0.0 ? -residual / derivative
          : -Math.copySign(10.0, residual);
      step = Math.max(-10.0, Math.min(10.0, step));
      double nextTemperature = Math.max(0.5 * temperature, temperature + step);
      if (Double.isFinite(lowerTemperature) && Double.isFinite(upperTemperature)) {
        double margin = 0.05 * (upperTemperature - lowerTemperature);
        if (nextTemperature <= lowerTemperature + margin || nextTemperature >= upperTemperature - margin) {
          nextTemperature = 0.5 * (lowerTemperature + upperTemperature);
        }
      }
      // Reduce a failed EOS step toward the last finite state. Never accept a failed trial.
      for (int backoff = 0;; backoff++) {
        system.setTemperature(nextTemperature);
        try {
          tpFlash.run();
          system.init(2);
          if (!Double.isFinite(system.getEntropy())) {
            throw convergenceFailure(system, Sspec, "non-finite trial entropy");
          }
          break;
        } catch (RuntimeException ex) {
          if (backoff == 15) {
            return solveWithColdBracket(temperature, tolerance);
          }
          nextTemperature = 0.5 * (temperature + nextTemperature);
        }
      }
    }
    return solveWithColdBracket(system.getTemperature(), tolerance);
  }

  /**
   * Recover from a stalled warm-start iteration with cold TP flashes and a temperature bracket.
   *
   * @param initialTemperature temperature at which the safeguarded Newton iteration stalled
   * @param tolerance accepted total-entropy residual in J/K
   * @return converged temperature in K
   */
  private double solveWithColdBracket(double initialTemperature, double tolerance) {
    neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(false);
    double trialTemperature = Math.max(MIN_BRACKET_TEMPERATURE, Math.min(MAX_BRACKET_TEMPERATURE, initialTemperature));
    double residual = evaluateColdResidual(trialTemperature);
    if (Math.abs(residual) <= tolerance) {
      return trialTemperature;
    }

    double lowerTemperature = residual < 0.0 ? trialTemperature : Double.NaN;
    double upperTemperature = residual > 0.0 ? trialTemperature : Double.NaN;
    for (int expansion = 0; expansion < MAX_BRACKET_EXPANSIONS
        && (!Double.isFinite(lowerTemperature) || !Double.isFinite(upperTemperature)); expansion++) {
      double previousTemperature = trialTemperature;
      if (residual < 0.0) {
        trialTemperature = Math.min(MAX_BRACKET_TEMPERATURE, trialTemperature * 1.25 + 5.0);
      } else {
        trialTemperature = Math.max(MIN_BRACKET_TEMPERATURE, trialTemperature / 1.25 - 5.0);
      }
      if (trialTemperature == previousTemperature) {
        break;
      }
      residual = evaluateColdResidual(trialTemperature);
      if (Math.abs(residual) <= tolerance) {
        return trialTemperature;
      }
      if (residual < 0.0) {
        lowerTemperature = trialTemperature;
      } else {
        upperTemperature = trialTemperature;
      }
    }
    if (!Double.isFinite(lowerTemperature) || !Double.isFinite(upperTemperature)) {
      throw convergenceFailure(system, Sspec, "unable to bracket entropy root");
    }

    for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
      trialTemperature = 0.5 * (lowerTemperature + upperTemperature);
      residual = evaluateColdResidual(trialTemperature);
      if (Math.abs(residual) <= tolerance) {
        return trialTemperature;
      }
      if (residual < 0.0) {
        lowerTemperature = trialTemperature;
      } else {
        upperTemperature = trialTemperature;
      }
    }
    throw convergenceFailure(system, Sspec, "cold bracket iteration limit reached");
  }

  /**
   * Evaluate total entropy at a temperature using a cold TP flash.
   *
   * @param temperature trial temperature in K
   * @return total entropy residual in J/K
   */
  private double evaluateColdResidual(double temperature) {
    system.setTemperature(temperature);
    tpFlash.run();
    system.init(2);
    double residual = system.getEntropy() - Sspec;
    if (!Double.isFinite(residual)) {
      throw convergenceFailure(system, Sspec, "non-finite cold-bracket entropy");
    }
    return residual;
  }

  /**
   * Get an amount-scaled tolerance for total entropy.
   *
   * @param fluid fluid being solved
   * @param entropy specified total entropy in J/K
   * @return entropy tolerance in J/K
   */
  static double entropyTolerance(SystemInterface fluid, double entropy) {
    return Math.max(MOLAR_ENTROPY_TOLERANCE * fluid.getTotalNumberOfMoles(),
        Math.abs(entropy) * RELATIVE_ENTROPY_TOLERANCE);
  }

  /**
   * Validate the input before either pure-component or mixture PS calculations.
   *
   * @param fluid fluid being solved
   * @param entropy specified total entropy in J/K
   * @throws IllegalArgumentException if the target or initial state is non-finite or non-physical
   */
  static void validateInput(SystemInterface fluid, double entropy) {
    if (!Double.isFinite(entropy) || !Double.isFinite(fluid.getTemperature()) || fluid.getTemperature() <= 0.0
        || !Double.isFinite(fluid.getPressure()) || fluid.getPressure() <= 0.0
        || !Double.isFinite(fluid.getTotalNumberOfMoles()) || fluid.getTotalNumberOfMoles() <= 0.0) {
      throw new IllegalArgumentException("PSflash requires finite entropy, positive temperature, pressure and amount");
    }
  }

  /**
   * Enforce the common pure-component and mixture PS postcondition.
   *
   * @param fluid solved fluid
   * @param entropy specified total entropy in J/K
   * @param pressure specified pressure in bara
   * @throws IllegalStateException if the flash has not satisfied its specification
   */
  static void validateResult(SystemInterface fluid, double entropy, double pressure) {
    fluid.init(2);
    double residual = fluid.getEntropy() - entropy;
    if (!Double.isFinite(residual) || Math.abs(residual) > entropyTolerance(fluid, entropy)
        || !Double.isFinite(fluid.getTemperature()) || fluid.getTemperature() <= 0.0
        || !Double.isFinite(fluid.getPressure()) || fluid.getPressure() <= 0.0
        || Math.abs(fluid.getPressure() - pressure) > 1.0e-10 * Math.max(1.0, pressure)) {
      throw convergenceFailure(fluid, entropy, "entropy or state postcondition failed");
    }
    double betaSum = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      double beta = fluid.getBeta(phase);
      if (!Double.isFinite(beta) || beta < 0.0 || beta > 1.0) {
        throw convergenceFailure(fluid, entropy, "invalid phase fraction");
      }
      betaSum += beta;
    }
    if (Math.abs(betaSum - 1.0) > 1.0e-8) {
      throw convergenceFailure(fluid, entropy, "unnormalized phase fractions");
    }
  }

  /**
   * Build a diagnostic exception without hiding the actual entropy mismatch.
   *
   * @param fluid current fluid state
   * @param entropy specified total entropy in J/K
   * @param reason reason for failure
   * @return convergence exception
   */
  private static IllegalStateException convergenceFailure(SystemInterface fluid, double entropy, String reason) {
    return new IllegalStateException("PSflash did not converge: " + reason + "; target entropy=" + entropy
        + " J/K, actual entropy=" + fluid.getEntropy() + " J/K, residual=" + (fluid.getEntropy() - entropy)
        + " J/K, temperature=" + fluid.getTemperature() + " K, pressure=" + fluid.getPressure() + " bara");
  }

  /**
   * onPhaseSolve.
   */
  public void onPhaseSolve() {
  }

  /**
   * Check whether K-value warm starts are suitable for the inner TP flashes.
   *
   * <p>
   * Delegates to {@link neqsim.thermo.ThermodynamicModelSettings#isInnerFlashWarmStartSafe} so that every iterative
   * flash shares one policy. CPA association-site fractions can change strongly with temperature, so reusing K-values
   * while the PS solver moves through temperature space may increase TP-flash work or bias the iteration path. Cubic
   * EOS models retain the established warm-start acceleration.
   * </p>
   *
   * @return {@code true} when inner TP flashes may reuse K-values
   */
  protected boolean isInnerTpFlashWarmStartSafe() {
    return neqsim.thermo.ThermodynamicModelSettings.isInnerFlashWarmStartSafe(system);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    validateInput(system, Sspec);
    double specifiedPressure = system.getPressure();
    // First TPflash runs COLD (Wilson initial K) so that stale K from a
    // previous unrelated flash (at different P/T) does not bias the solution.
    // Then enable K-value warm-start for subsequent TPflash iterations only when
    // the thermodynamic model is suitable. CPA association terms can make K-values
    // stale as temperature changes, increasing work in glycol/water calculations.
    boolean prevWarm = neqsim.thermo.ThermodynamicModelSettings.isUseWarmStartKValues();
    boolean useInnerWarmStart = isInnerTpFlashWarmStartSafe();
    try {
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(false);
      tpFlash.run();
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(useInnerWarmStart);

      if (type == 0) {
        solveQ();
      } else {
        SysNewtonRhapsonPHflash secondOrderSolver = new SysNewtonRhapsonPHflash(system, 2,
            system.getPhases()[0].getNumberOfComponents(), 1);
        secondOrderSolver.setSpec(Sspec);
        secondOrderSolver.solve(1);
      }
      // Verify the endpoint with cold K-values before accepting a warm-start solution.
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(false);
      tpFlash.run();
      system.init(2);
      if (Math.abs(system.getEntropy() - Sspec) > entropyTolerance(system, Sspec)) {
        solveQ();
      }
      validateResult(system, Sspec, specifiedPressure);
    } finally {
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(prevWarm);
    }
  }
}
