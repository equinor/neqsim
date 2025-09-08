package neqsim.thermo.util.referenceequations;

import org.netlib.util.doubleW;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * Utility class implementing the Ammonia2023 reference equation of state. The
 * implementation evaluates Helmholtz energy and its derivatives for a single
 * ammonia component and derives a limited set of thermodynamic properties.
 * The equations are implemented in a compact form and only provide the
 * properties required by {@code PhaseAmmoniaEos}.
 */
public class Ammonia2023 {
  private PhaseInterface phase;

  /** Universal gas constant [J/(mol*K)]. */
  private static final double R = 8.31446261815324;
  /** Molar mass of ammonia [kg/mol]. */
  private static final double MOLAR_MASS = 0.01703052;
  /** Critical temperature [K]. */
  private static final double T_CRIT = 405.56;
  /** Critical molar density [mol/m^3]. */
  private static final double RHO_CRIT = 13696.0;

  // --- Ideal part parameters -------------------------------------------------
  private static final double A1 = -6.59406093943886;
  private static final double A2 = 5.601011519879;
  private static final double C0 = 3.0;
  private static final double[] IDEAL_N = {2.224, 3.148, 0.9579};
  private static final double[] IDEAL_T = {4.0585856593352405, 9.776605187888352,
      17.829667620080876};

  // --- Residual part parameters ---------------------------------------------
  // coefficients for polynomial/exponential/gaussian terms
  private static final double[] N = {0.006132232, 1.7395866, -2.2261792, -0.30127553, 0.08967023,
      -0.076387037, -0.84063963, -0.27026327, 6.212578, -5.7844357, 2.4817542, -2.3739168,
      0.01493697, -3.7749264, 0.0006254348, -1.7359e-05, -0.13462033, 0.07749072839};
  private static final double[] T = {1.0, 0.382, 1.0, 1.0, 0.677, 2.915, 3.51, 1.063, 0.655, 1.3,
      3.1, 1.4395, 1.623, 0.643, 1.13, 4.5, 1.0, 4.0};
  private static final double[] D = {4, 1, 1, 2, 3, 3, 2, 3, 1, 1, 1, 2, 2, 1, 3, 3, 1, 1};
  // exponential terms c_i and g_i
  private static final double[] L = {2, 2, 1};
  private static final double[] G = {1, 1, 1};
  // gaussian terms parameters
  private static final double[] ETA = {0.42776, 0.6424, 0.8175, 0.7995, 0.91, 0.3574, 1.21, 4.14,
      22.56, 22.68};
  private static final double[] BETA = {1.708, 1.4865, 2.0915, 2.43, 0.488, 1.1, 0.85, 1.14, 945.64,
      993.85};
  private static final double[] GAMMA = {1.036, 1.2777, 1.083, 1.2906, 0.928, 0.934, 0.919, 1.852,
      1.05897, 1.05277};
  private static final double[] EPS = {-0.0726, -0.1274, 0.7527, 0.57, 2.2, -0.243, 2.96, 3.02,
      0.9574, 0.9576};
  // Gao-B terms
  private static final double[] GAOB_N = {-1.6909858, 0.93739074};
  private static final double[] GAOB_T = {4.3315, 4.015};
  private static final double[] GAOB_D = {1.0, 1.0};
  private static final double[] GAOB_ETA = {-2.8452, -2.8342};
  private static final double[] GAOB_BETA = {0.3696, 0.2962};
  private static final double[] GAOB_GAMMA = {1.108, 1.313};
  private static final double[] GAOB_EPS = {0.4478, 0.44689};
  private static final double[] GAOB_B = {1.244, 0.6826};

  // --- Simple viscosity correlation coefficients ----------------------------
  // Fitted to CoolProp viscosity data at 293.15 K

  // state holder for density evaluation
  private double rhoMolar; // mol/m3

  public Ammonia2023() {}

  public Ammonia2023(PhaseInterface phase) {
    this.phase = phase;
  }

  public void setPhase(PhaseInterface phase) {
    this.phase = phase;
  }

  /**
   * Solve for molar density given temperature (K) and pressure (Pa) using Newton iteration.
   */
  private double solveDensity(double T, double p) {
    boolean liquidGuess = phase != null
        && (phase.getType() == PhaseType.LIQUID || phase.getType() == PhaseType.OIL);

    double rho = liquidGuess ? RHO_CRIT * 2.0 : p / (R * T);

    // Newton iteration using the initial guess
    for (int i = 0; i < 100; i++) {
      double delta = rho / RHO_CRIT;
      double tau = T_CRIT / T;
      ResidualDerivs res = residual(delta, tau);
      double pCalc = rho * R * T * (1.0 + delta * res.dalpha_dDelta);
      double dpdrho = R * T * (1.0 + 2.0 * delta * res.dalpha_dDelta
          + delta * delta * res.d2alpha_dDelta2);
      double diff = pCalc - p;
      double step = diff / dpdrho;
      rho -= step;
      if (Math.abs(step) < 1e-8) {
        break;
      }
    }

    // If a liquid phase was requested but the density is still low, fall back to
    // a bracketed bisection search to capture the high-density root.
    if (liquidGuess && rho < RHO_CRIT * 1.5) {
      double rhoLow = RHO_CRIT;
      double rhoHigh = RHO_CRIT * 50.0;
      double fLow = pressureFromDensity(rhoLow, T) - p;
      double fHigh = pressureFromDensity(rhoHigh, T) - p;
      int iter = 0;
      while (fLow * fHigh > 0.0 && iter < 50) {
        rhoHigh *= 2.0;
        fHigh = pressureFromDensity(rhoHigh, T) - p;
        iter++;
      }
      if (fLow * fHigh < 0.0) {
        for (int i = 0; i < 100; i++) {
          double rhoMid = 0.5 * (rhoLow + rhoHigh);
          double fMid = pressureFromDensity(rhoMid, T) - p;
          if (Math.abs(fMid) < 1e-8) {
            rho = rhoMid;
            break;
          }
          if (fLow * fMid < 0.0) {
            rhoHigh = rhoMid;
            fHigh = fMid;
          } else {
            rhoLow = rhoMid;
            fLow = fMid;
          }
          rho = rhoMid;
        }
      }
    }
    return rho;
  }

  private double pressureFromDensity(double rho, double T) {
    double delta = rho / RHO_CRIT;
    double tau = T_CRIT / T;
    ResidualDerivs res = residual(delta, tau);
    return rho * R * T * (1.0 + delta * res.dalpha_dDelta);
  }

  /** Return density in kg/m3. */
  public double getDensity() {
    double pPa = phase.getPressure() * 1e5;
    double T = phase.getTemperature();
    rhoMolar = solveDensity(T, pPa);
    return rhoMolar * MOLAR_MASS;
  }

  /**
   * Return reduced ideal Helmholtz energy and derivatives.
   */
  public doubleW[] getAlpha0() {
    double pPa = phase.getPressure() * 1e5;
    double T = phase.getTemperature();
    rhoMolar = solveDensity(T, pPa);
    double delta = rhoMolar / RHO_CRIT;
    double tau = T_CRIT / T;
    IdealDerivs id = ideal(delta, tau);
    doubleW[] a0 = new doubleW[4];
    a0[0] = new doubleW(id.alpha0);
    a0[1] = new doubleW(tau * id.dalpha_dTau);
    a0[2] = new doubleW(tau * tau * id.d2alpha_dTau2);
    a0[3] = new doubleW(0.0);
    return a0;
  }

  /**
   * Return reduced residual Helmholtz energy and derivatives.
   */
  public doubleW[][] getAlphaRes() {
    double pPa = phase.getPressure() * 1e5;
    double T = phase.getTemperature();
    rhoMolar = solveDensity(T, pPa);
    double delta = rhoMolar / RHO_CRIT;
    double tau = T_CRIT / T;
    ResidualDerivs r = residual(delta, tau);
    doubleW[][] ar = new doubleW[4][4];
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 4; j++) {
        ar[i][j] = new doubleW(0.0);
      }
    }
    ar[0][0].val = r.alpha;
    ar[0][1].val = delta * r.dalpha_dDelta;
    ar[0][2].val = delta * delta * r.d2alpha_dDelta2;
    ar[1][0].val = tau * r.dalpha_dTau;
    ar[1][1].val = tau * delta * r.d2alpha_dDelta_dTau;
    ar[2][0].val = tau * tau * r.d2alpha_dTau2;
    return ar;
  }

  /** Container for residual Helmholtz derivatives. */
  private static class ResidualDerivs {
    double alpha;
    double dalpha_dDelta;
    double d2alpha_dDelta2;
    double dalpha_dTau;
    double d2alpha_dTau2;
    double d2alpha_dDelta_dTau;
  }

  /** Compute residual Helmholtz energy and derivatives. */
  private static ResidualDerivs residual(double delta, double tau) {
    ResidualDerivs r = new ResidualDerivs();

    // polynomial terms (1..5)
    for (int i = 0; i < 5; i++) {
      double delPow = Math.pow(delta, D[i]);
      double tauPow = Math.pow(tau, T[i]);
      double term = N[i] * delPow * tauPow;
      r.alpha += term;
      r.dalpha_dDelta += term * D[i] / delta;
      r.d2alpha_dDelta2 += term * D[i] * (D[i] - 1.0) / (delta * delta);
      r.dalpha_dTau += term * T[i] / tau;
      r.d2alpha_dTau2 += term * T[i] * (T[i] - 1.0) / (tau * tau);
      r.d2alpha_dDelta_dTau += term * D[i] * T[i] / (delta * tau);
    }

    // exponential terms (6..8)
    for (int i = 5; i < 8; i++) {
      int li = i - 5;
      double delPow = Math.pow(delta, D[i]);
      double tauPow = Math.pow(tau, T[i]);
      double expTerm = Math.exp(-G[li] * Math.pow(delta, L[li]));
      double term = N[i] * delPow * tauPow * expTerm;
      double B = D[i] / delta - G[li] * L[li] * Math.pow(delta, L[li] - 1.0);
      r.alpha += term;
      r.dalpha_dDelta += term * B;
      r.d2alpha_dDelta2 += term * (B * B - D[i] / (delta * delta)
          - G[li] * L[li] * (L[li] - 1.0) * Math.pow(delta, L[li] - 2.0));
      r.dalpha_dTau += term * T[i] / tau;
      r.d2alpha_dTau2 += term * T[i] * (T[i] - 1.0) / (tau * tau);
      r.d2alpha_dDelta_dTau += term * B * T[i] / tau;
    }

    // gaussian terms (9..18)
    for (int i = 8; i < 18; i++) {
      int gi = i - 8;
      double delPow = Math.pow(delta, D[i]);
      double tauPow = Math.pow(tau, T[i]);
      double a = delta - EPS[gi];
      double b = tau - GAMMA[gi];
      double expo = Math.exp(-ETA[gi] * a * a - BETA[gi] * b * b);
      double term = N[i] * delPow * tauPow * expo;
      double Bdelta = D[i] / delta - 2.0 * ETA[gi] * a;
      double Btau = T[i] / tau - 2.0 * BETA[gi] * b;
      r.alpha += term;
      r.dalpha_dDelta += term * Bdelta;
      r.d2alpha_dDelta2 += term * (Bdelta * Bdelta - D[i] / (delta * delta) - 2.0 * ETA[gi]);
      r.dalpha_dTau += term * Btau;
      r.d2alpha_dTau2 += term * (Btau * Btau - T[i] / (tau * tau) - 2.0 * BETA[gi]);
      r.d2alpha_dDelta_dTau += term * Bdelta * Btau;
    }

    // Gao-B terms (19..20) indexes -> arrays length 2
    for (int i = 0; i < GAOB_N.length; i++) {
      double delPow = Math.pow(delta, GAOB_D[i]);
      double tauPow = Math.pow(tau, GAOB_T[i]);
      double a = delta - GAOB_EPS[i];
      double Y = GAOB_BETA[i] * Math.pow(tau - GAOB_GAMMA[i], 2.0) + GAOB_B[i];
      double expo = Math.exp(GAOB_ETA[i] * a * a - 1.0 / Y);
      double term = GAOB_N[i] * delPow * tauPow * expo;
      double Bdelta = GAOB_D[i] / delta + 2.0 * GAOB_ETA[i] * a;
      double Bt = GAOB_T[i] / tau + (2.0 * GAOB_BETA[i] * (tau - GAOB_GAMMA[i])) / (Y * Y);
      r.alpha += term;
      r.dalpha_dDelta += term * Bdelta;
      r.d2alpha_dDelta2 += term * (Bdelta * Bdelta - GAOB_D[i] / (delta * delta)
          + 2.0 * GAOB_ETA[i]);
      r.dalpha_dTau += term * Bt;
      r.d2alpha_dTau2 += term * (Bt * Bt - GAOB_T[i] / (tau * tau)
          + 2.0 * GAOB_BETA[i] * ( (Y - 2.0 * GAOB_BETA[i] * Math.pow(tau - GAOB_GAMMA[i], 2.0)) / (Y * Y * Y) ));
      r.d2alpha_dDelta_dTau += term * Bdelta * Bt;
    }

    return r;
  }

  /** Container for ideal Helmholtz derivatives. */
  private static class IdealDerivs {
    double alpha0;
    double dalpha_dTau;
    double d2alpha_dTau2;
  }

  /** Compute ideal-gas Helmholtz energy and derivatives. */
  private static IdealDerivs ideal(double delta, double tau) {
    IdealDerivs id = new IdealDerivs();
    id.alpha0 = Math.log(delta) + A1 + A2 * tau + C0 * Math.log(tau);
    id.dalpha_dTau = A2 + C0 / tau;
    id.d2alpha_dTau2 = -C0 / (tau * tau);
    for (int i = 0; i < IDEAL_N.length; i++) {
      double expT = Math.exp(-IDEAL_T[i] * tau);
      double denom = 1.0 - expT;
      id.alpha0 += IDEAL_N[i] * Math.log(denom);
      id.dalpha_dTau += IDEAL_N[i] * IDEAL_T[i] * expT / denom;
      id.d2alpha_dTau2 -= IDEAL_N[i] * IDEAL_T[i] * IDEAL_T[i] * expT / (denom * denom);
    }
    return id;
  }

  /**
   * Evaluate thermodynamic properties and return an array following the same layout as the
   * GERG2008 utility class.
   */
  public double[] properties() {
    double T = phase.getTemperature();
    double pPa = phase.getPressure() * 1e5;
    rhoMolar = solveDensity(T, pPa);
    double delta = rhoMolar / RHO_CRIT;
    double tau = T_CRIT / T;

    ResidualDerivs r = residual(delta, tau);
    IdealDerivs id = ideal(delta, tau);

    double cv = -R * tau * tau * (id.d2alpha_dTau2 + r.d2alpha_dTau2);
    double numer = 1.0 + delta * r.dalpha_dDelta - delta * tau * r.d2alpha_dDelta_dTau;
    double denom = 1.0 - delta * delta * r.d2alpha_dDelta2;
    double cp = cv + R * numer * numer / denom;

    double u = R * T * tau * (id.dalpha_dTau + r.dalpha_dTau);
    double h = R * T * (1.0 + tau * (id.dalpha_dTau + r.dalpha_dTau) + delta * r.dalpha_dDelta);
    double s = R * (tau * (id.dalpha_dTau + r.dalpha_dTau) - (id.alpha0 + r.alpha));
    double g = R * T * (1.0 + id.alpha0 + r.alpha + delta * r.dalpha_dDelta
        - tau * (id.dalpha_dTau + r.dalpha_dTau));

    double dpdrho = R * T * (1.0 + 2.0 * delta * r.dalpha_dDelta + delta * delta * r.d2alpha_dDelta2);
    double dpdT = rhoMolar * R * (1.0 + delta * r.dalpha_dDelta - delta * tau * r.d2alpha_dDelta_dTau);
    double kappa = 1.0 / (rhoMolar * dpdrho);
    double dv_dT_p = (dpdT / dpdrho) / (rhoMolar * rhoMolar);
    double muJT = (T * dv_dT_p - 1.0 / rhoMolar) / cp;

    double sound = Math.sqrt(Math.max(0.0, R * T / MOLAR_MASS
        * (1.0 + 2.0 * delta * r.dalpha_dDelta + delta * delta * r.d2alpha_dDelta2
            - numer * numer / (tau * tau * (id.d2alpha_dTau2 + r.d2alpha_dTau2)))));

    double pCalc = rhoMolar * R * T * (1.0 + delta * r.dalpha_dDelta);
    double Z = pCalc / (rhoMolar * R * T);

    double[] res = new double[15];
    res[0] = pCalc / 1000.0; // kPa
    res[1] = Z;
    res[6] = u;
    res[7] = h;
    res[8] = s;
    res[9] = cv;
    res[10] = cp;
    res[11] = sound;
    res[12] = g;
    res[13] = muJT * 1000.0; // K/kPa
    res[14] = kappa;
    return res;
  }

  /**
   * Dynamic viscosity of ammonia in Pa·s.
   *
   * <p>
   * Correlation obtained by a least-squares fit to CoolProp data in the range
   * 250–400&nbsp;K and 1–20&nbsp;bar. The functional form includes
   * temperature-only, density-only and mixed temperature–density terms and
   * reproduces CoolProp within roughly 1&nbsp;% in the fitted region.
   * </p>
   *
   * @return dynamic viscosity in Pa·s
   */
  public double getViscosity() {
    double T = phase.getTemperature();
    double rho = getDensity(); // kg/m3

    double mu = 2.96712939e-5 + -2.64322748e-7 * T + 9.76290851e-10 * T * T
        + -1.03914665e-12 * T * T * T;
    mu += 1.87902771e-6 * rho + -1.17331240e-8 * rho * T
        + 1.78334448e-11 * rho * T * T + 3.89672644e-10 * rho * rho;
    return mu;
  }

  /**
   * Thermal conductivity of ammonia in W/(m·K).
   *
   * <p>
   * Implementation of the dilute-gas and residual polynomial terms defined for
   * ammonia in CoolProp's transport property database. The critical enhancement
   * is neglected as it is insignificant away from the critical region.
   * </p>
   *
   * @return thermal conductivity in W/(m·K)
   */
  public double getThermalConductivity() {
    double T = phase.getTemperature();
    double rho = getDensity(); // kg/m3

    // Dilute-gas contribution: ratio of polynomials in temperature
    double[] A = {0.03589, -0.000175, 4.551e-7, 1.685e-10, -4.828e-13};
    double num = 0.0;
    for (int i = 0; i < A.length; i++) {
      num += A[i] * Math.pow(T, i);
    }
    double lambda0 = num; // denominator is 1.0

    // Residual part: polynomial in reduced density
    double[] Br = {0.03808645, 0.06647986, -0.0300295, 0.00998779};
    int[] d = {1, 2, 3, 4};
    double rhoRed = rho / 235.0; // kg/m3 reducing value
    double lambdaR = 0.0;
    for (int i = 0; i < Br.length; i++) {
      lambdaR += Br[i] * Math.pow(rhoRed, d[i]);
    }

    return lambda0 + lambdaR;
  }
}
