package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * Direct cricondentherm calculation using the Michelsen simultaneous Newton method.
 *
 * <p>
 * Finds the maximum temperature point on the phase envelope by solving an (n+2)-dimensional Newton
 * system simultaneously, following the formulation of Michelsen (1980) and Michelsen &amp; Mollerup
 * (2007), Chapter 12.
 * </p>
 *
 * <p>
 * <b>Unknowns</b> (n+2): ln K_1, ..., ln K_n, ln T, ln P
 * </p>
 * <ul>
 * <li>g_i = ln K_i + ln phi_i^V(y,T,P) - ln phi_i^L(x,T,P) = 0, i=1..n (equilibrium)</li>
 * <li>g_{n+1} = sum_i z_i*(K_i - 1)/(1 + beta*(K_i - 1)) = 0 (Rachford-Rice summation)</li>
 * <li>g_{n+2} = S_T = 0 (cricondentherm condition: dP/dT = 0 along envelope, which requires the
 * sensitivity S_T of the envelope equations w.r.t. ln T specification to vanish)</li>
 * </ul>
 *
 * <p>
 * The full (n+2)x(n+2) Jacobian is built analytically using fugacity coefficient derivatives (d ln
 * phi/dT, d ln phi/dP, d ln phi/dx_j) available from system.init(3). This gives quadratic
 * convergence, typically converging in 3-8 iterations from a good initial estimate.
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
  /** Maximum Newton iterations. */
  private static final int MAX_ITER = 50;
  /** Convergence tolerance on the norm of the residual vector. */
  private static final double TOLERANCE = 1.0e-10;
  /** Maximum step size in ln-space per iteration for damping. */
  private static final double MAX_STEP = 3.0;

  SystemInterface system;
  int nc;
  double betaVal;

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
    this.nc = system.getPhase(0).getNumberOfComponents();
    this.cricondenTherm = cricondenTherm;
    this.cricondenThermX = cricondenThermX;
    this.cricondenThermY = cricondenThermY;
    this.betaVal = phaseFraction;
    bubblePointFirst = false;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    double T = cricondenTherm[0];
    double P = cricondenTherm[1];
    double Tini = T;
    double Pini = P;

    // Initialize ln(K) from provided compositions
    double[] lnK = new double[nc];
    for (int i = 0; i < nc; i++) {
      if (cricondenThermX[i] > 1.0e-100 && cricondenThermY[i] > 1.0e-100) {
        lnK[i] = Math.log(cricondenThermY[i] / cricondenThermX[i]);
      } else {
        lnK[i] = 0.0;
      }
    }

    double lnT = Math.log(T);
    double lnP = Math.log(P);

    for (int iter = 0; iter < MAX_ITER; iter++) {
      T = Math.exp(lnT);
      P = Math.exp(lnP);

      // Update K-values, compositions, and thermodynamic state
      updateCompositions(lnK, T, P);
      system.setTemperature(T);
      system.setPressure(P);
      system.init(3);

      // Build the (n+2) residual vector g
      double[] g = buildResidual(lnK, T, P);

      // Check convergence on the norm of g
      double norm = 0.0;
      for (int i = 0; i < nc + 2; i++) {
        norm += g[i] * g[i];
      }
      norm = Math.sqrt(norm);

      if (norm < TOLERANCE) {
        cricondenTherm[0] = T;
        cricondenTherm[1] = P;
        // Update output compositions
        for (int i = 0; i < nc; i++) {
          cricondenThermX[i] = system.getPhase(0).getComponent(i).getx();
          cricondenThermY[i] = system.getPhase(1).getComponent(i).getx();
        }
        logger.debug("CricondenThermFlash converged in {} iterations: T={} K, P={} bar, norm={}",
            iter, T, P, norm);
        return;
      }

      // Build the (n+2)x(n+2) Jacobian
      double[][] jac = buildJacobian(lnK, T, P);

      // Solve J * delta = -g using Gaussian elimination
      double[] delta = solveLinearSystem(jac, g);
      if (delta == null) {
        logger.warn("CricondenThermFlash: singular Jacobian at iter {}. Keeping envelope estimate.",
            iter);
        break;
      }

      // Damp the step if too large
      double maxDelta = 0.0;
      for (int i = 0; i < nc + 2; i++) {
        if (Math.abs(delta[i]) > maxDelta) {
          maxDelta = Math.abs(delta[i]);
        }
      }
      double damping = 1.0;
      if (maxDelta > MAX_STEP) {
        damping = MAX_STEP / maxDelta;
      }

      // Apply updates
      for (int i = 0; i < nc; i++) {
        lnK[i] += damping * delta[i];
      }
      lnT += damping * delta[nc];
      lnP += damping * delta[nc + 1];

      // Safety checks
      if (Math.exp(lnT) < 20.0 || Math.exp(lnT) > 2000.0) {
        logger.warn("CricondenThermFlash: T out of range after update. Reverting.");
        break;
      }
      if (Math.exp(lnP) < 0.01 || Math.exp(lnP) > 5000.0) {
        logger.warn("CricondenThermFlash: P out of range after update. Reverting.");
        break;
      }
    }

    // Did not converge - keep the best estimate from envelope tracing
    logger.warn("CricondenThermFlash did not converge. Keeping envelope estimate T={} K, P={} bar",
        Tini, Pini);
    cricondenTherm[0] = Tini;
    cricondenTherm[1] = Pini;
  }

  /**
   * Update phase compositions from ln(K) values using the Rachford-Rice formulation.
   *
   * @param lnK array of ln(K_i) values
   * @param T temperature in K
   * @param P pressure in bar
   */
  private void updateCompositions(double[] lnK, double T, double P) {
    double sumx = 0.0;
    double sumy = 0.0;
    double[] xx = new double[nc];
    double[] yy = new double[nc];

    for (int i = 0; i < nc; i++) {
      double Ki = Math.exp(lnK[i]);
      double zi = system.getPhase(0).getComponent(i).getz();
      double denom = 1.0 - betaVal + betaVal * Ki;
      xx[i] = zi / denom;
      yy[i] = zi * Ki / denom;
      sumx += xx[i];
      sumy += yy[i];
      system.getPhase(0).getComponent(i).setK(Ki);
    }

    for (int i = 0; i < nc; i++) {
      system.getPhase(0).getComponent(i).setx(xx[i] / sumx);
      system.getPhase(1).getComponent(i).setx(yy[i] / sumy);
    }
  }

  /**
   * Build the (n+2) residual vector for the Michelsen cricondentherm formulation.
   *
   * <p>
   * g_i = ln K_i + ln phi_i^V - ln phi_i^L, i=0..n-1 (equilibrium) g_n = sum_i
   * z_i*(K_i-1)/(1+beta*(K_i-1)) (Rachford-Rice) g_{n+1} = S_T = sum_i s_i * dg_i/d(lnP)
   * (cricondentherm condition: dP/dT = 0) where s_i = (y_i - x_i) normalized
   * </p>
   *
   * @param lnK array of ln(K_i) values
   * @param T temperature in K
   * @param P pressure in bar
   * @return (n+2) residual vector
   */
  private double[] buildResidual(double[] lnK, double T, double P) {
    double[] g = new double[nc + 2];

    // Equilibrium equations: g_i = ln K_i + ln phi_V_i - ln phi_L_i
    for (int i = 0; i < nc; i++) {
      double lnPhiV = system.getPhase(1).getComponent(i).getLogFugacityCoefficient();
      double lnPhiL = system.getPhase(0).getComponent(i).getLogFugacityCoefficient();
      g[i] = lnK[i] + lnPhiV - lnPhiL;
    }

    // Rachford-Rice summation: g_{n} = sum z_i*(K_i - 1)/(1 + beta*(K_i - 1))
    g[nc] = 0.0;
    for (int i = 0; i < nc; i++) {
      double Ki = Math.exp(lnK[i]);
      double zi = system.getPhase(0).getComponent(i).getz();
      g[nc] += zi * (Ki - 1.0) / (1.0 + betaVal * (Ki - 1.0));
    }

    // Cricondentherm condition: S_T = 0 (dP/dT = 0 along envelope)
    // S_T = sum_i s_i * dg_i/d(lnP) where s_i = (y_i - x_i) normalized
    double[] si = new double[nc];
    double snorm = 0.0;
    for (int i = 0; i < nc; i++) {
      si[i] = system.getPhase(1).getComponent(i).getx() - system.getPhase(0).getComponent(i).getx();
      snorm += si[i] * si[i];
    }
    snorm = Math.sqrt(snorm);
    if (snorm > 1.0e-30) {
      for (int i = 0; i < nc; i++) {
        si[i] /= snorm;
      }
    }

    g[nc + 1] = 0.0;
    for (int i = 0; i < nc; i++) {
      double dlnPhiV_dP = system.getPhase(1).getComponent(i).getdfugdp();
      double dlnPhiL_dP = system.getPhase(0).getComponent(i).getdfugdp();
      double dgdlnP = P * (dlnPhiV_dP - dlnPhiL_dP);
      g[nc + 1] += si[i] * dgdlnP;
    }

    return g;
  }

  /**
   * Build the (n+2)x(n+2) Jacobian matrix for the simultaneous Newton system.
   *
   * @param lnK array of ln(K_i) values
   * @param T temperature in K
   * @param P pressure in bar
   * @return (n+2)x(n+2) Jacobian matrix
   */
  private double[][] buildJacobian(double[] lnK, double T, double P) {
    int neq = nc + 2;
    double[][] jac = new double[neq][neq];

    double[] xi = new double[nc];
    double[] yi = new double[nc];
    double[] Ki = new double[nc];
    double[] zi = new double[nc];

    for (int i = 0; i < nc; i++) {
      xi[i] = system.getPhase(0).getComponent(i).getx();
      yi[i] = system.getPhase(1).getComponent(i).getx();
      Ki[i] = Math.exp(lnK[i]);
      zi[i] = system.getPhase(0).getComponent(i).getz();
    }

    // Row 0..nc-1: equilibrium equations
    for (int i = 0; i < nc; i++) {
      double dlnPhiV_dT = system.getPhase(1).getComponent(i).getdfugdt();
      double dlnPhiL_dT = system.getPhase(0).getComponent(i).getdfugdt();
      double dlnPhiV_dP = system.getPhase(1).getComponent(i).getdfugdp();
      double dlnPhiL_dP = system.getPhase(0).getComponent(i).getdfugdp();

      jac[i][nc] = T * (dlnPhiV_dT - dlnPhiL_dT);
      jac[i][nc + 1] = P * (dlnPhiV_dP - dlnPhiL_dP);

      for (int j = 0; j < nc; j++) {
        double dlnPhiV_dyj = system.getPhase(1).getComponent(i).getdfugdx(j);
        double dlnPhiL_dxj = system.getPhase(0).getComponent(i).getdfugdx(j);

        double denomj = 1.0 - betaVal + betaVal * Ki[j];
        double dyjdlnKj = Ki[j] * zi[j] * (1.0 - betaVal) / (denomj * denomj);
        double dxjdlnKj = -zi[j] * betaVal * Ki[j] / (denomj * denomj);

        if (i == j) {
          jac[i][j] = 1.0 + dlnPhiV_dyj * dyjdlnKj - dlnPhiL_dxj * dxjdlnKj;
        } else {
          jac[i][j] = dlnPhiV_dyj * dyjdlnKj - dlnPhiL_dxj * dxjdlnKj;
        }
      }
    }

    // Row nc: Rachford-Rice equation
    for (int j = 0; j < nc; j++) {
      double denomj = 1.0 + betaVal * (Ki[j] - 1.0);
      jac[nc][j] = zi[j] * Ki[j] * (1.0 - betaVal) / (denomj * denomj);
    }
    jac[nc][nc] = 0.0;
    jac[nc][nc + 1] = 0.0;

    // Row nc+1: Cricondentherm specification S_T = sum_i s_i * dg_i/dlnP
    // Numerical perturbation for the last row
    double eps = 1.0e-6;
    for (int j = 0; j < nc; j++) {
      double[] lnKp = new double[nc];
      System.arraycopy(lnK, 0, lnKp, 0, nc);
      lnKp[j] += eps;

      updateCompositions(lnKp, T, P);
      system.setTemperature(T);
      system.setPressure(P);
      system.init(3);
      double stPlus = computeST(P);

      updateCompositions(lnK, T, P);
      system.setTemperature(T);
      system.setPressure(P);
      system.init(3);
      double stBase = computeST(P);
      jac[nc + 1][j] = (stPlus - stBase) / eps;
    }

    // dS_T/dlnT
    {
      double Tp = T * Math.exp(eps);
      updateCompositions(lnK, Tp, P);
      system.setTemperature(Tp);
      system.setPressure(P);
      system.init(3);
      double stPlus = computeST(P);

      updateCompositions(lnK, T, P);
      system.setTemperature(T);
      system.setPressure(P);
      system.init(3);
      double stBase = computeST(P);
      jac[nc + 1][nc] = (stPlus - stBase) / eps;
    }

    // dS_T/dlnP
    {
      double Pp = P * Math.exp(eps);
      updateCompositions(lnK, T, Pp);
      system.setTemperature(T);
      system.setPressure(Pp);
      system.init(3);
      double stPlus = computeST(Pp);

      updateCompositions(lnK, T, P);
      system.setTemperature(T);
      system.setPressure(P);
      system.init(3);
      double stBase = computeST(P);
      jac[nc + 1][nc + 1] = (stPlus - stBase) / eps;
    }

    return jac;
  }

  /**
   * Compute S_T = sum_i s_i * P * (dlnPhiV_i/dP - dlnPhiL_i/dP) where s_i = (y_i - x_i) normalized.
   * This is the cricondentherm specification function.
   *
   * @param P pressure in bar
   * @return the value of S_T
   */
  private double computeST(double P) {
    double[] si = new double[nc];
    double snorm = 0.0;
    for (int i = 0; i < nc; i++) {
      si[i] = system.getPhase(1).getComponent(i).getx() - system.getPhase(0).getComponent(i).getx();
      snorm += si[i] * si[i];
    }
    snorm = Math.sqrt(snorm);
    if (snorm > 1.0e-30) {
      for (int i = 0; i < nc; i++) {
        si[i] /= snorm;
      }
    }

    double st = 0.0;
    for (int i = 0; i < nc; i++) {
      double dlnPhiV_dP = system.getPhase(1).getComponent(i).getdfugdp();
      double dlnPhiL_dP = system.getPhase(0).getComponent(i).getdfugdp();
      st += si[i] * P * (dlnPhiV_dP - dlnPhiL_dP);
    }
    return st;
  }

  /**
   * Solve a linear system J*delta = -g using Gaussian elimination with partial pivoting.
   *
   * @param jac the Jacobian matrix (n x n), will be modified in-place
   * @param g the residual vector (n), used to form the RHS = -g
   * @return the solution vector delta, or null if the system is singular
   */
  private double[] solveLinearSystem(double[][] jac, double[] g) {
    int n = g.length;
    double[][] a = new double[n][n + 1];
    for (int i = 0; i < n; i++) {
      System.arraycopy(jac[i], 0, a[i], 0, n);
      a[i][n] = -g[i];
    }

    // Forward elimination with partial pivoting
    for (int col = 0; col < n; col++) {
      int maxRow = col;
      double maxVal = Math.abs(a[col][col]);
      for (int row = col + 1; row < n; row++) {
        if (Math.abs(a[row][col]) > maxVal) {
          maxVal = Math.abs(a[row][col]);
          maxRow = row;
        }
      }
      if (maxVal < 1.0e-30) {
        return null;
      }
      if (maxRow != col) {
        double[] tmp = a[col];
        a[col] = a[maxRow];
        a[maxRow] = tmp;
      }
      for (int row = col + 1; row < n; row++) {
        double factor = a[row][col] / a[col][col];
        for (int k = col; k <= n; k++) {
          a[row][k] -= factor * a[col][k];
        }
      }
    }

    // Back substitution
    double[] delta = new double[n];
    for (int i = n - 1; i >= 0; i--) {
      double sum = a[i][n];
      for (int j = i + 1; j < n; j++) {
        sum -= a[i][j] * delta[j];
      }
      delta[i] = sum / a[i][i];
    }
    return delta;
  }
}
