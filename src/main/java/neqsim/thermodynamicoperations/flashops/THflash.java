/*
 * THflash.java
 *
 * Temperature-Enthalpy flash calculation using Q-function methodology. Given temperature T and
 * enthalpy H, solves for pressure P.
 */

package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.system.SystemInterface;

/**
 * Temperature-Enthalpy (TH) flash calculation.
 *
 * <p>
 * Solves for pressure P given specified temperature T and enthalpy H. Uses Newton iteration with
 * the thermodynamic derivative (∂H/∂P)_T = V - T(∂V/∂T)_P.
 * </p>
 *
 * <p>
 * Applications include heat exchanger design at fixed temperature and process simulation where
 * temperature and enthalpy are specified independently.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class THflash extends QfuncFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Specified enthalpy in J. */
  private double Hspec = 0;

  /** TP flash calculator. */
  private Flash tpFlash;

  /**
   * Constructor for THflash.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Hspec specified enthalpy in J
   */
  public THflash(SystemInterface system, double Hspec) {
    this.system = system;
    this.tpFlash = new TPflash(system);
    this.Hspec = Hspec;
  }

  /**
   * Calculates the residual dQ = H - Hspec.
   *
   * @return enthalpy residual in J
   */
  public double calcdQdP() {
    return system.getEnthalpy() - Hspec;
  }

  /**
   * Calculates the derivative d²Q/dP² = (∂H/∂P)_T.
   *
   * <p>
   * Using thermodynamic relation: (∂H/∂P)_T = V - T(∂V/∂T)_P = V + T * dVdT where dVdT is defined
   * as -dV/dT in NeqSim convention.
   * </p>
   *
   * @return derivative of enthalpy with respect to pressure at constant T
   */
  public double calcdQdPP() {
    double V = system.getVolume();
    double T = system.getTemperature();

    // Get dV/dT at constant P using system-level method
    // Note: getdVdTpn returns -dV/dT, so dV/dT = -getdVdTpn()
    double dVdT = -system.getdVdTpn();

    // (∂H/∂P)_T = V - T*(∂V/∂T)_P
    return V - T * dVdT;
  }

  /**
   * Solves for pressure using Newton iteration with numerical derivatives.
   *
   * @return converged pressure in bar
   */
  public double solveQ() {
    double oldPres = system.getPressure();
    double nyPres = system.getPressure();
    int iterations = 0;
    double error = 100.0;
    double dH = 0.0;
    double olddH = 0.0;
    double pressureStep = 1.0;

    do {
      iterations++;
      oldPres = nyPres;
      system.init(3);

      olddH = dH;
      dH = system.getEnthalpy() - Hspec;

      // Calculate derivative (dH/dP)_T
      // Analytical: (dH/dP)_T = V - T*(dV/dT)_P
      // NeqSim convention: getdVdTpn returns -(dV/dT)_P
      double V = system.getVolume();
      double T = system.getTemperature();
      double dVdT = -system.getdVdTpn();
      double dHdP_analytical = (V - T * dVdT) * 1e5; // Convert m³ to J/bar

      // Numerical derivative from previous iteration
      double dHdP_numerical = 0.0;
      if (iterations > 1 && Math.abs(pressureStep) > 1e-10) {
        dHdP_numerical = (dH - olddH) / pressureStep;
      }

      // Use analytical for first iterations, switch to numerical for stability
      double dHdP;
      if (iterations < 5 || Math.abs(dHdP_numerical) < 1e-10) {
        dHdP = dHdP_analytical;
      } else {
        dHdP = dHdP_numerical;
      }

      // Avoid division by zero
      if (Math.abs(dHdP) < 1.0) {
        dHdP = Math.signum(dHdP) * 1.0;
        if (dHdP == 0) {
          dHdP = 1.0;
        }
      }

      // Newton step with damping
      double factor = (double) iterations / (iterations + 5.0);
      double deltaP = -factor * dH / dHdP;

      // Limit step size
      if (Math.abs(deltaP) > 0.5 * oldPres) {
        deltaP = Math.signum(deltaP) * 0.5 * oldPres;
      }

      nyPres = oldPres + deltaP;

      // Ensure pressure stays positive
      if (nyPres <= 0.01) {
        nyPres = 0.01;
      }
      if (nyPres > 10000.0) {
        nyPres = 10000.0;
      }

      pressureStep = nyPres - oldPres;

      system.setPressure(nyPres);
      tpFlash.run();

      error = Math.abs(system.getEnthalpy() - Hspec) / Math.max(Math.abs(Hspec), 1.0);

    } while ((error > 1e-9 && iterations < 200) || iterations < 3);

    return nyPres;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    tpFlash.run();
    solveQ();
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }
}
