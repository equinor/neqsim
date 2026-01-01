/*
 * PVflash.java
 *
 * Pressure-Volume flash calculation using Q-function methodology. Given pressure P and volume V,
 * solves for temperature T.
 */

package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.system.SystemInterface;

/**
 * Pressure-Volume (PV) flash calculation.
 *
 * <p>
 * Solves for temperature T given specified pressure P and volume V. Uses Newton iteration with the
 * thermodynamic derivative (∂V/∂T)_P.
 * </p>
 *
 * <p>
 * Applications include fixed volume vessels at constant pressure and process simulation where
 * pressure and volume are specified independently.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class PVflash extends QfuncFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Specified volume in m³. */
  private double Vspec = 0;

  /** TP flash calculator. */
  private Flash tpFlash;

  /**
   * Constructor for PVflash.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Vspec specified volume in m³
   */
  public PVflash(SystemInterface system, double Vspec) {
    this.system = system;
    this.tpFlash = new TPflash(system);
    this.Vspec = Vspec;
  }

  /**
   * Calculates the residual dQ = V - Vspec.
   *
   * @return volume residual in m³
   */
  public double calcdQdT() {
    return system.getVolume() - Vspec;
  }

  /**
   * Calculates the derivative d²Q/dT² = (∂V/∂T)_P.
   *
   * <p>
   * With NeqSim sign convention: getdVdTpn returns -dV/dT, so we negate it.
   * </p>
   *
   * @return derivative of volume with respect to temperature at constant P
   */
  public double calcdQdTT() {
    // Get dV/dT at constant P using system-level method
    // Note: getdVdTpn returns -dV/dT, so dV/dT = -getdVdTpn()
    return -system.getdVdTpn();
  }

  /**
   * Solves for temperature using Newton iteration with numerical derivatives.
   *
   * @return converged temperature in K
   */
  public double solveQ() {
    double oldTemp = system.getTemperature();
    double nyTemp = system.getTemperature();
    int iterations = 0;
    double error = 100.0;
    double dV = 0.0;
    double olddV = 0.0;
    double tempStep = 1.0;

    do {
      iterations++;
      oldTemp = nyTemp;
      system.init(3);

      olddV = dV;
      dV = system.getVolume() - Vspec;

      // Calculate derivative (dV/dT)_P
      // NeqSim convention: getdVdTpn returns -(dV/dT)_P
      double dVdT_analytical = -system.getdVdTpn();

      // Numerical derivative from previous iteration
      double dVdT_numerical = 0.0;
      if (iterations > 1 && Math.abs(tempStep) > 1e-10) {
        dVdT_numerical = (dV - olddV) / tempStep;
      }

      // Use analytical for first iterations, switch to numerical for stability
      double dVdT;
      if (iterations < 5 || Math.abs(dVdT_numerical) < 1e-12) {
        dVdT = dVdT_analytical;
      } else {
        dVdT = dVdT_numerical;
      }

      // Avoid division by zero
      if (Math.abs(dVdT) < 1e-10) {
        dVdT = Math.signum(dVdT) * 1e-10;
        if (dVdT == 0) {
          dVdT = 1e-10;
        }
      }

      // Newton step with damping
      double factor = (double) iterations / (iterations + 5.0);
      double deltaT = -factor * dV / dVdT;

      // Limit step size to 20% of current temperature
      if (Math.abs(deltaT) > 0.2 * oldTemp) {
        deltaT = Math.signum(deltaT) * 0.2 * oldTemp;
      }

      nyTemp = oldTemp + deltaT;

      // Ensure temperature stays within bounds
      if (nyTemp <= 10.0) {
        nyTemp = 10.0;
      }
      if (nyTemp > 2000.0) {
        nyTemp = 2000.0;
      }

      tempStep = nyTemp - oldTemp;

      system.setTemperature(nyTemp);
      tpFlash.run();

      error = Math.abs(system.getVolume() - Vspec) / Math.max(Vspec, 1e-10);

    } while ((error > 1e-9 && iterations < 200) || iterations < 3);

    return nyTemp;
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
