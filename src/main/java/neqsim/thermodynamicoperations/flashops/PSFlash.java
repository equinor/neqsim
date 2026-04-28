package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * PSFlash class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class PSFlash extends QfuncFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double Sspec = 0;
  Flash tpFlash;
  int type = 0;

  /**
   * <p>
   * Constructor for PSFlash.
   * </p>
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
    double oldTemp = system.getTemperature();
    double nyTemp = system.getTemperature();
    int iterations = 1;
    double error = 1.0;
    double errorOld = 10.0e10;
    double factor = 0.8;

    boolean correctFactor = true;
    double newCorr = 1.0;
    system.init(2);

    do {
      if (error > errorOld && factor > 0.1 && correctFactor) {
        factor *= 0.5;
      } else if (error < errorOld && correctFactor) {
        factor = 1.0;
      }

      iterations++;
      oldTemp = system.getTemperature();

      newCorr = factor * calcdQdT() / calcdQdTT();
      nyTemp = oldTemp - newCorr;
      if (Math.abs(system.getTemperature() - nyTemp) > 10.0) {
        nyTemp = system.getTemperature() - Math.signum(system.getTemperature() - nyTemp) * 10.0;
        correctFactor = false;
      } else if (nyTemp < 0) {
        nyTemp = Math.abs(system.getTemperature() - 10.0);
        correctFactor = false;
      } else if (Double.isNaN(nyTemp)) {
        nyTemp = oldTemp + 1.0;
        correctFactor = false;
      } else {
        correctFactor = true;
      }

      system.setTemperature(nyTemp);
      try {
        tpFlash.run();
        system.init(2);
      } catch (Exception ex) {
        // EOS solver failed at this temperature, revert and reduce step
        nyTemp = oldTemp;
        system.setTemperature(oldTemp);
        tpFlash.run();
        system.init(2);
        factor *= 0.5;
        correctFactor = false;
      }
      errorOld = error;
      error = Math.abs(calcdQdT()); // Math.abs((nyTemp - oldTemp) / (nyTemp));
      // if(error>errorOld) factor *= -1.0;
      // System.out.println("temp " + system.getTemperature() + " iter "+ iterations +
      // " error "+ error + " correction " + newCorr + " factor "+ factor);
      // newCorr = Math.abs(factor * calcdQdT() / calcdQdTT());
    } while (((error + errorOld) > 1e-8 || iterations < 3) && iterations < 200);
    return nyTemp;
  }

  /**
   * Bisection fallback for solveQ. Used when Newton on T fails to drive the entropy residual below
   * the convergence tolerance — typically because the EOS gives an ill-conditioned Cp (wrong cubic
   * root, near-saturation, or a pure associating fluid on a non-associating EOS).
   *
   * <p>
   * Brackets the target T by probing outward from the current temperature, then bisects until the
   * residual is below tolerance or the bracket collapses. Always converges if a solution exists in
   * the probed range, because S(T,P) is monotonically increasing in T for a stable single phase at
   * fixed pressure (Cp &gt; 0). Slower than Newton but guaranteed bounded.
   *
   * @return final temperature after bisection
   */
  private double bisectSolveQ() {
    final double tMinAbs = 50.0;
    final double tMaxAbs = 5000.0;
    double tStart = system.getTemperature();
    if (!Double.isFinite(tStart) || tStart < tMinAbs) {
      tStart = 300.0;
    }

    double tLo = Math.max(tMinAbs, tStart * 0.5);
    double tHi = Math.min(tMaxAbs, tStart * 2.0);

    double rLo = evalResidualAt(tLo);
    double rHi = evalResidualAt(tHi);
    if (Double.isNaN(rLo) || Double.isNaN(rHi)) {
      // EOS solver outright failed at probe points; restore start and give up.
      system.setTemperature(tStart);
      tpFlash.run();
      system.init(2);
      return tStart;
    }

    // Expand outward until residuals have opposite sign (or limits reached).
    int expand = 0;
    while (rLo * rHi > 0 && expand < 8) {
      if (Math.abs(rLo) < Math.abs(rHi)) {
        tLo = Math.max(tMinAbs, tLo * 0.5);
        rLo = evalResidualAt(tLo);
      } else {
        tHi = Math.min(tMaxAbs, tHi * 1.5);
        rHi = evalResidualAt(tHi);
      }
      if (Double.isNaN(rLo) || Double.isNaN(rHi)) {
        break;
      }
      expand++;
    }

    if (rLo * rHi > 0 || Double.isNaN(rLo) || Double.isNaN(rHi)) {
      // Could not bracket — leave at the better of the two endpoints.
      double tBest = Math.abs(rLo) < Math.abs(rHi) ? tLo : tHi;
      system.setTemperature(tBest);
      tpFlash.run();
      system.init(2);
      return tBest;
    }

    // Bisect.
    for (int i = 0; i < 80; i++) {
      double tMid = 0.5 * (tLo + tHi);
      double rMid = evalResidualAt(tMid);
      if (Double.isNaN(rMid)) {
        // EOS hiccup at midpoint — pull bracket toward the better side.
        tMid = 0.5 * (tLo + tMid);
        rMid = evalResidualAt(tMid);
        if (Double.isNaN(rMid)) {
          break;
        }
      }
      if (Math.abs(rMid) < 1e-6 || (tHi - tLo) < 1e-4) {
        return tMid;
      }
      if (rMid * rLo < 0) {
        tHi = tMid;
        rHi = rMid;
      } else {
        tLo = tMid;
        rLo = rMid;
      }
    }
    return system.getTemperature();
  }

  /**
   * Sets temperature, runs TP-flash, and returns the entropy residual (Sspec - S). Returns
   * Double.NaN if the EOS solver fails at this temperature.
   *
   * @param t temperature in Kelvin
   * @return entropy residual or NaN
   */
  private double evalResidualAt(double t) {
    system.setTemperature(t);
    try {
      tpFlash.run();
      system.init(2);
    } catch (Exception ex) {
      return Double.NaN;
    }
    double r = calcdQdT();
    return Double.isFinite(r) ? r : Double.NaN;
  }

  /**
   * <p>
   * onPhaseSolve.
   * </p>
   */
  public void onPhaseSolve() {}

  /** {@inheritDoc} */
  @Override
  public void run() {
    // First TPflash runs COLD (Wilson initial K) so that stale K from a
    // previous unrelated flash (at different P/T) does not bias the solution.
    // Then enable K-value warm-start only for subsequent TPflash iterations
    // within this outer PS-flash loop — safe because the outer loop converges
    // on T, absorbing inner SS-path differences. Typical speedup: 3-5x.
    boolean prevWarm = neqsim.thermo.ThermodynamicModelSettings.isUseWarmStartKValues();
    try {
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(false);
      tpFlash.run();
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(true);

      if (type == 0) {
        solveQ();
        // Bisection fallback: if Newton on T failed to drive the entropy residual below a
        // reasonable tolerance, re-solve with a guaranteed-convergent bracket-and-bisect.
        // This catches cases where the EOS returns a wrong cubic root (e.g., a strongly-
        // associating pure fluid on a non-associating cubic EOS like PR/SRK), where Cp can
        // be wrong-signed or unrealistically small and the Newton step blows up. The
        // tolerance is generous to avoid invoking bisection on healthy normal cases.
        double residual = Math.abs(calcdQdT());
        double tol = Math.max(1.0e-3, 1.0e-4 * Math.abs(Sspec));
        if (!Double.isFinite(residual) || residual > tol) {
          bisectSolveQ();
        }
      } else {
        SysNewtonRhapsonPHflash secondOrderSolver = new SysNewtonRhapsonPHflash(system, 2,
            system.getPhases()[0].getNumberOfComponents(), 1);
        secondOrderSolver.setSpec(Sspec);
        secondOrderSolver.solve(1);
      }
    } finally {
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(prevWarm);
    }
    // System.out.println("Entropy: " + system.getEntropy());
    // System.out.println("Temperature: " + system.getTemperature());
  }
}
