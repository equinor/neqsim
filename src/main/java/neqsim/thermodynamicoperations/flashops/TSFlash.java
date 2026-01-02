/*
 * TSFlash.java
 *
 * Created on 8. mars 2001, 10:56
 */

package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TSFlash class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class TSFlash extends QfuncFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double Sspec = 0;
  Flash tpFlash;

  /**
   * <p>
   * Constructor for TSFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Sspec a double
   */
  public TSFlash(SystemInterface system, double Sspec) {
    this.system = system;
    this.tpFlash = new TPflash(system);
    this.Sspec = Sspec;
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

  /**
   * Calculate the gradient of Q with respect to pressure for TS-flash. For isentropic flash at
   * fixed T, we solve for P such that S(T,P) = Sspec. Q = Sspec - S, so dQ/dP = -dS/dP. By Maxwell
   * relation: (dS/dP)_T = -(dV/dT)_P Therefore: dQ/dP = -(-dV/dT) = dV/dT
   *
   * @return gradient dQ/dP
   */
  public double calcdQdP() {
    // By Maxwell relation: (dS/dP)_T = -(dV/dT)_P
    // Q = Sspec - S, so dQ/dP = -dS/dP = dV/dT
    return system.getdVdTpn();
  }

  /**
   * Calculate the second derivative of Q with respect to pressure for TS-flash. d²Q/dP² =
   * d(dV/dT)/dP This approximation uses finite differencing concept for stability.
   *
   * @return second derivative d²Q/dP²
   */
  public double calcdQdPP() {
    // Use a numerical approximation for the second derivative
    // d²Q/dP² ≈ d(dV/dT)/dP
    // For an ideal gas: dV/dT = nR/P, so d²(dV/dT)/dP² = -nR/P²
    // For real gases, use scaled volume derivative
    double dVdT = system.getdVdTpn();
    double P = system.getPressure();

    // Approximate second derivative based on typical pressure scaling
    // The key insight is that dV/dT scales roughly as 1/P for gases
    if (Math.abs(dVdT) > 1e-20) {
      return -dVdT / P;
    }
    // Fallback using volume derivative for stability
    double dVdP = system.getdVdPtn();
    double T = system.getTemperature();
    return dVdP / T;
  }

  /** {@inheritDoc} */
  @Override
  public double solveQ() {
    double oldPres = system.getPressure();
    double nyPres = system.getPressure();
    int iterations = 0;
    double residual = 1.0;
    double factor = 1.0;

    do {
      iterations++;
      oldPres = nyPres;
      system.init(3);

      // Residual: r = S(T,P) - Sspec (we want r = 0)
      residual = system.getEntropy() - Sspec;

      // By Maxwell relation: dS/dP at constant T = -(dV/dT) at constant P
      double dSdP = -system.getdVdTpn();

      // Avoid division by zero
      if (Math.abs(dSdP) < 1e-20) {
        dSdP = (dSdP >= 0) ? 1e-20 : -1e-20;
      }

      // Newton step: P_new = P_old - residual / (d residual / dP)
      double deltaP = -residual / dSdP;

      // Limit step size to avoid divergence
      double maxDeltaP = 0.3 * oldPres;
      if (Math.abs(deltaP) > maxDeltaP) {
        deltaP = Math.signum(deltaP) * maxDeltaP;
      }

      // Apply damping factor that increases with iterations
      factor = (double) iterations / (iterations + 5.0);
      nyPres = oldPres + factor * deltaP;

      // Ensure pressure stays positive and physical
      if (nyPres <= 0.01) {
        nyPres = 0.01;
      }
      if (nyPres > 1000) {
        nyPres = oldPres * 0.9;
      }

      system.setPressure(nyPres);
      tpFlash.run();

    } while (Math.abs(residual) > 1e-8 && iterations < 200);

    return nyPres;
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
    tpFlash.run();
    solveQ();
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    SystemInterface testSystem = new SystemSrkEos(373.15, 45.551793);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.addComponent("methane", 9.4935);
    testSystem.addComponent("ethane", 5.06499);
    testSystem.addComponent("n-heptane", 0.2);
    testSystem.init(0);
    try {
      testOps.TPflash();
      testSystem.display();

      double Sspec = testSystem.getEntropy("kJ/kgK");
      logger.info("S spec " + Sspec);
      testSystem.setTemperature(293.15);
      testOps.TSflash(Sspec, "kJ/kgK");
      testSystem.display();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }
}
