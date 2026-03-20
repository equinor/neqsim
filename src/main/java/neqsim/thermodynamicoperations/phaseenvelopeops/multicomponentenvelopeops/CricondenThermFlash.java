package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import Jama.Matrix;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * Direct cricondentherm calculation using Newton's method.
 *
 * <p>
 * Finds the maximum temperature on the phase envelope by solving two coupled conditions:
 * </p>
 * <ul>
 * <li>Q = 0 (phase equilibrium on the boundary)</li>
 * <li>dQ/dP = 0 (extremum condition: at cricondentherm, the temperature is at a maximum so the
 * pressure derivative of the tangent-plane distance must vanish)</li>
 * </ul>
 * <p>
 * The algorithm uses initial estimates from the phase envelope tracing. It refines using
 * alternating Newton updates: inner loop 1 solves dQ/dP = 0 for P (using numerical second
 * derivatives d2Q/dP2), and inner loop 2 solves Q = 0 for T with successive substitution K-value
 * updates.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class CricondenThermFlash extends PTphaseEnvelope {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(CricondenThermFlash.class);
  /** Maximum outer iterations. */
  private static final int MAX_OUTER_ITER = 200;
  /** Maximum inner iterations for the extremum condition (dQ/dP = 0). */
  private static final int MAX_INNER_EXTREMUM_ITER = 100;
  /** Maximum inner iterations for the equilibrium condition (Q = 0). */
  private static final int MAX_INNER_EQUIL_ITER = 200;
  /** Convergence tolerance. */
  private static final double TOLERANCE = 1.0e-7;

  int neq = 0;
  Matrix u;
  Matrix uold;
  SystemInterface system;
  int numberOfComponents;
  double funcT;
  double dfuncdT;
  double funcP;
  double dfuncdP;
  double T;
  double P;

  /**
   * Constructor for CricondenThermFlash.
   *
   * @param system the thermodynamic system
   * @param name name identifier
   * @param phaseFraction vapor phase fraction (typically 1 - 1e-10 for dew point)
   * @param cricondenTherm array of length 3: [T_K, P_bar, ...] initial estimate and output
   * @param cricondenThermX liquid phase mole fractions at the cricondentherm estimate
   * @param cricondenThermY vapor phase mole fractions at the cricondentherm estimate
   */
  public CricondenThermFlash(SystemInterface system, String name, double phaseFraction,
      double[] cricondenTherm, double[] cricondenThermX, double[] cricondenThermY) {
    this.system = system;
    this.numberOfComponents = system.getPhase(0).getNumberOfComponents();
    u = new Matrix(numberOfComponents, 1);
    this.cricondenTherm = cricondenTherm;
    this.cricondenThermX = cricondenThermX;
    this.cricondenThermY = cricondenThermY;
    bubblePointFirst = false;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    T = cricondenTherm[0];
    P = cricondenTherm[1];
    double Tini = T;
    double Pini = P;

    for (int ii = 0; ii < numberOfComponents; ii++) {
      double kval = cricondenThermY[ii] / cricondenThermX[ii];
      u.set(ii, 0, kval);
      system.getPhase(0).getComponent(ii).setK(kval);
      system.getPhase(0).getComponent(ii).setx(cricondenThermX[ii]);
      system.getPhase(1).getComponent(ii).setx(cricondenThermY[ii]);
    }
    setNewX();

    for (int iter = 0; iter < MAX_OUTER_ITER; iter++) {
      // Inner loop 1: solve dQ/dP = 0 for P (extremum condition)
      for (int iterP = 0; iterP < MAX_INNER_EXTREMUM_ITER; iterP++) {
        system.setTemperature(T);
        system.setPressure(P);
        init();
        funcP();
        double dfdpBase = dfuncdP;

        if (Math.abs(dfdpBase) < TOLERANCE) {
          break;
        }

        // Numerical second derivative d2Q/dP2 using relative perturbation
        double perturbation = Math.max(Math.abs(P) * 1.0e-6, 1.0e-6);
        system.setTemperature(T);
        system.setPressure(P + perturbation);
        init();
        funcP();
        double dfdpPerturbed = dfuncdP;

        double d2QdP2 = (dfdpPerturbed - dfdpBase) / perturbation;
        if (Math.abs(d2QdP2) < 1.0e-15) {
          break;
        }

        double step = -dfdpBase / d2QdP2;
        // Limit step to prevent divergence (max 50% of current P per iteration)
        if (Math.abs(step) > 0.5 * P) {
          step = Math.signum(step) * 0.5 * P;
        }

        P = P + step;
        if (P < 0.1) {
          P = Pini;
          break;
        }
      }

      // Inner loop 2: solve Q = 0 for T with K-value successive substitution
      for (int iterT = 0; iterT < MAX_INNER_EQUIL_ITER; iterT++) {
        system.setTemperature(T);
        system.setPressure(P);

        uold = u.copy();
        init();
        setNewK();

        double sumK = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
          double dk = uold.get(i, 0) - u.get(i, 0);
          sumK += dk * dk;
        }

        setNewX();
        init();
        funcT();

        if (Math.abs(dfuncdT) < 1.0e-15) {
          break;
        }
        if ((Math.abs(funcT / dfuncdT) < TOLERANCE) || (sumK <= 1.0e-10)) {
          break;
        }

        double stepT = -funcT / dfuncdT;
        // Limit temperature step (max 20 K per iteration)
        double maxStep = 20.0;
        if (Math.abs(stepT) > maxStep) {
          stepT = Math.signum(stepT) * maxStep;
        }

        T = T + stepT;
        if (T <= 50.0) {
          T = T - stepT;
          break;
        }
      }

      // Check overall convergence
      system.setTemperature(T);
      system.setPressure(P);
      init();
      funcT();
      funcP();

      if (Math.abs(dfuncdP) <= TOLERANCE && Math.abs(funcT) <= TOLERANCE
          && Math.abs(dfuncdT) >= TOLERANCE) {
        cricondenTherm[0] = T;
        cricondenTherm[1] = P;
        logger.debug("CricondenThermFlash converged in {} outer iterations: T={} K, P={} bar", iter,
            T, P);
        return;
      }
      if (Math.abs(dfuncdP) <= TOLERANCE && Math.abs(funcT) <= TOLERANCE
          && Math.abs(dfuncdT) <= TOLERANCE) {
        // Both derivatives vanish - likely at the critical point, not cricondentherm
        logger.warn("CricondenThermFlash: both dQ/dP and dQ/dT vanish - possible critical point");
        cricondenTherm[0] = T;
        cricondenTherm[1] = P;
        return;
      }
    }

    // Did not converge - keep the best estimate from envelope tracing
    logger.warn("CricondenThermFlash did not converge after {} iterations."
        + " Keeping envelope estimate T={} K, P={} bar", MAX_OUTER_ITER, Tini, Pini);
    cricondenTherm[0] = Tini;
    cricondenTherm[1] = Pini;
  }

  /**
   * Update K-values from fugacity coefficients and synchronize with the u vector.
   */
  public void setNewK() {
    for (int j = 0; j < numberOfComponents; j++) {
      double kval = system.getPhase(0).getComponent(j).getFugacityCoefficient()
          / system.getPhase(1).getComponent(j).getFugacityCoefficient();
      system.getPhase(0).getComponent(j).setK(kval);
      u.set(j, 0, kval);
    }
  }

  /**
   * Compute phase compositions from K-values using the Rachford-Rice formulation and normalize.
   */
  public void setNewX() {
    double sumx = 0.0;
    double sumy = 0.0;
    double[] xx = new double[numberOfComponents];
    double[] yy = new double[numberOfComponents];
    double betaVal = system.getBeta();

    for (int j = 0; j < numberOfComponents; j++) {
      double kj = system.getPhase(0).getComponent(j).getK();
      double zj = system.getPhase(0).getComponent(j).getz();
      double denom = 1.0 - betaVal + betaVal * kj;
      xx[j] = zj / denom;
      yy[j] = zj * kj / denom;
      sumx += xx[j];
      sumy += yy[j];
    }

    for (int j = 0; j < numberOfComponents; j++) {
      system.getPhase(0).getComponent(j).setx(xx[j] / sumx);
      system.getPhase(1).getComponent(j).setx(yy[j] / sumy);
    }
  }

  /**
   * Initialize the thermodynamic system (calls system.init(3) to compute fugacities and
   * derivatives).
   */
  public void init() {
    system.init(3);
  }

  /**
   * Compute the tangent-plane objective Q and its temperature derivative dQ/dT.
   *
   * <p>
   * Q = -1 + sum_i x_i * (1 + ln(y_i/x_i) + ln(phi_v_i) - ln(phi_l_i)). At phase equilibrium Q = 0.
   * dQ/dT = sum_i x_i * (d ln(phi_v_i)/dT - d ln(phi_l_i)/dT).
   * </p>
   */
  public void funcT() {
    funcT = -1.0;
    dfuncdT = 0.0;

    for (int j = 0; j < numberOfComponents; j++) {
      double xj = system.getPhase(0).getComponent(j).getx();
      double yj = system.getPhase(1).getComponent(j).getx();
      double fugl = system.getPhase(0).getComponent(j).getLogFugacityCoefficient();
      double fugv = system.getPhase(1).getComponent(j).getLogFugacityCoefficient();
      double fugTl = system.getPhase(0).getComponent(j).getdfugdt();
      double fugTv = system.getPhase(1).getComponent(j).getdfugdt();

      funcT += xj + xj * (Math.log(yj) - Math.log(xj) + fugv - fugl);
      dfuncdT += xj * (fugTv - fugTl);
    }
  }

  /**
   * Compute the tangent-plane objective Q and its pressure derivative dQ/dP.
   *
   * <p>
   * Same Q formula as funcT, but derivative is with respect to pressure: dQ/dP = sum_i x_i * (d
   * ln(phi_v_i)/dP - d ln(phi_l_i)/dP).
   * </p>
   */
  public void funcP() {
    funcP = -1.0;
    dfuncdP = 0.0;

    for (int j = 0; j < numberOfComponents; j++) {
      double xj = system.getPhase(0).getComponent(j).getx();
      double yj = system.getPhase(1).getComponent(j).getx();
      double fugl = system.getPhase(0).getComponent(j).getLogFugacityCoefficient();
      double fugv = system.getPhase(1).getComponent(j).getLogFugacityCoefficient();
      double fugPl = system.getPhase(0).getComponent(j).getdfugdp();
      double fugPv = system.getPhase(1).getComponent(j).getdfugdp();

      funcP += xj + xj * (Math.log(yj) - Math.log(xj) + fugv - fugl);
      dfuncdP += xj * (fugPv - fugPl);
    }
  }
}
