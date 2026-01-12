/*
 * TUflash.java
 *
 * Temperature-Internal Energy flash calculation using Q-function methodology. Given temperature T
 * and internal energy U, solves for pressure P.
 */

package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.system.SystemInterface;

/**
 * Temperature-Internal Energy (TU) flash calculation.
 *
 * <p>
 * Solves for pressure P given specified temperature T and internal energy U. Uses Newton iteration
 * with the thermodynamic derivative (∂U/∂P)_T.
 * </p>
 *
 * <p>
 * Using the relation U = H - PV: (∂U/∂P)_T = (∂H/∂P)_T - V - P(∂V/∂P)_T = V - T(∂V/∂T)_P - V -
 * P(∂V/∂P)_T = -T(∂V/∂T)_P - P(∂V/∂P)_T
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class TUflash extends QfuncFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Specified internal energy in J. */
  private double Uspec = 0;

  /** TP flash calculator. */
  private Flash tpFlash;

  /**
   * Constructor for TUflash.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Uspec specified internal energy in J
   */
  public TUflash(SystemInterface system, double Uspec) {
    this.system = system;
    this.tpFlash = new TPflash(system);
    this.Uspec = Uspec;
  }

  /**
   * Calculates the residual dQ = U - Uspec.
   *
   * @return internal energy residual in J
   */
  public double calcdQdP() {
    return system.getInternalEnergy() - Uspec;
  }

  /**
   * Calculates the derivative d²Q/dP² = (∂U/∂P)_T.
   *
   * <p>
   * Using thermodynamic relation: (∂U/∂P)_T = -T(∂V/∂T)_P - P(∂V/∂P)_T With NeqSim sign
   * conventions: dVdT = -(∂V/∂T)_P and dVdP = (∂V/∂P)_T So: (∂U/∂P)_T = T*dVdT - P*dVdP
   * </p>
   *
   * @return derivative of internal energy with respect to pressure at constant T
   */
  public double calcdQdPP() {
    double T = system.getTemperature();

    // Get dV/dT at constant P using system-level method
    // Note: getdVdTpn returns -dV/dT, so dV/dT = -getdVdTpn()
    double dVdT = -system.getdVdTpn();

    // Get dV/dP at constant T (returns m³/bar)
    double dVdP = system.getdVdPtn();

    // (∂U/∂P)_T = -T*(∂V/∂T)_P - P*(∂V/∂P)_T
    // Using: U = H - PV, so dU/dP = dH/dP - V - P*dV/dP
    // where dH/dP = V - T*dV/dT
    // Thus: dU/dP = (V - T*dV/dT) - V - P*dV/dP = -T*dV/dT - P*dV/dP
    // Units: T[K] * dVdT[m³/K] gives m³, need to convert to J/bar
    // P is in bar, dVdP is in m³/bar
    // Result in m³ = J/bar (since 1 bar*m³ = 1e5 J)
    double P = system.getPressure(); // bar
    return -T * dVdT * 1e5 - P * dVdP * 1e5;
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
    double dU = 0.0;
    double olddU = 0.0;
    double pressureStep = 1.0;

    do {
      iterations++;
      oldPres = nyPres;
      system.init(3);

      olddU = dU;
      dU = system.getInternalEnergy() - Uspec;

      // Calculate derivative (dU/dP)_T
      // Analytical: (dU/dP)_T = -T*(dV/dT)_P - P*(dV/dP)_T
      // NeqSim convention: getdVdTpn returns -(dV/dT)_P
      double T = system.getTemperature();
      double P = system.getPressure();
      double dVdT = -system.getdVdTpn();
      double dVdP = system.getdVdPtn();
      double dUdP_analytical = (-T * dVdT - P * dVdP) * 1e5; // Convert m³ to J/bar

      // Numerical derivative from previous iteration
      double dUdP_numerical = 0.0;
      if (iterations > 1 && Math.abs(pressureStep) > 1e-10) {
        dUdP_numerical = (dU - olddU) / pressureStep;
      }

      // Use analytical for first iterations, switch to numerical for stability
      double dUdP;
      if (iterations < 5 || Math.abs(dUdP_numerical) < 1e-10) {
        dUdP = dUdP_analytical;
      } else {
        dUdP = dUdP_numerical;
      }

      // Avoid division by zero
      if (Math.abs(dUdP) < 1.0) {
        dUdP = Math.signum(dUdP) * 1.0;
        if (dUdP == 0) {
          dUdP = -1.0;
        }
      }

      // Newton step with damping
      double factor = (double) iterations / (iterations + 5.0);
      double deltaP = -factor * dU / dUdP;

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

      error = Math.abs(system.getInternalEnergy() - Uspec) / Math.max(Math.abs(Uspec), 1.0);

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
