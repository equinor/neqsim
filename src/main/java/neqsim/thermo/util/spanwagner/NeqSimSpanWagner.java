package neqsim.thermo.util.spanwagner;

import neqsim.thermo.phase.PhaseType;

/**
 * Utility class implementing the Span–Wagner reference equation of state for
 * carbon dioxide. All properties are returned on a molar basis in SI units.
 */
public final class NeqSimSpanWagner {

  private NeqSimSpanWagner() {}

  // Gas constant and critical parameters for CO2
  private static final double R = 8.31451; // J/mol/K
  private static final double TC = 304.1282; // K
  private static final double RHOC = 10624.9063; // mol/m3

  // Ideal-gas coefficients
  private static final double LEAD_A1 = 8.37304456;
  private static final double LEAD_A2 = -3.70454304;
  private static final double LOGTAU_A = 2.5;
  private static final double OFFSET_A1 = -14.4979156224319;
  private static final double OFFSET_A2 = 8.82013935801453;
  private static final double[] N0 = {
      1.99427042, 0.62105248, 0.41195293, 1.04028922, 0.08327678};
  private static final double[] T0 = {
      3.15163, 6.1119, 6.77708, 11.32384, 27.08792};

  // Residual power-series terms (Span–Wagner n, d, t, c=l)
  private static final double[] N = {
      0.388568232032, 2.93854759427, -5.5867188535, -0.767531995925,
      0.317290055804, 0.548033158978, 0.122794112203, 2.16589615432,
      1.58417351097, -0.231327054055, 0.0581169164314, -0.553691372054,
      0.489466159094, -0.0242757398435, 0.0624947905017,
      -0.121758602252, -0.370556852701, -0.0167758797004,
      -0.11960736638, -0.0456193625088, 0.0356127892703,
      -0.00744277271321, -0.00173957049024, -0.0218101212895,
      0.0243321665592, -0.0374401334235, 0.143387157569,
      -0.134919690833, -0.0231512250535, 0.0123631254929,
      0.00210583219729, -0.000339585190264, 0.00559936517716,
      -0.000303351180556};
  private static final double[] D = {
      1, 1, 1, 1, 2, 2, 3, 1, 2, 4, 5, 5, 5, 6, 6, 6,
      1, 1, 4, 4, 4, 7, 8, 2, 3, 3, 5, 5, 6, 7, 8, 10, 4, 8};
  private static final double[] T = {
      0, 0.75, 1, 2, 0.75, 2, 0.75, 1.5, 1.5, 2.5, 0, 1.5, 2,
      0, 1, 2, 3, 6, 3, 6, 8, 6, 0, 7, 12, 16, 22, 24, 16, 24,
      8, 2, 28, 14};
  private static final double[] L = {
      0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1,
      2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 4, 4, 4, 5, 6};

  // Gaussian terms
  private static final double[] GN = {
      -213.654886883, 26641.5691493, -24027.2122046,
      -283.41603424, 212.472844002};
  private static final double[] GD = {2, 2, 2, 3, 3};
  private static final double[] GT = {1, 0, 1, 3, 3};
  private static final double[] GBETA = {325, 300, 300, 275, 275};
  private static final double[] GGAMMA = {1.16, 1.19, 1.19, 1.25, 1.22};
  private static final double[] GEPS = {1, 1, 1, 1, 1};
  private static final double[] GETA = {25, 25, 25, 15, 20};

  /** Holder for Helmholtz energy derivatives. */
  private static class Derivs {
    double a0; // ideal part
    double a0_t;
    double a0_tt;
    double ar; // residual part
    double ar_d;
    double ar_dd;
    double ar_t;
    double ar_tt;
    double ar_dt;
  }

  /** Evaluate ideal-gas contribution and derivatives. */
  private static void alpha0(double delta, double tau, Derivs d) {
    d.a0 = Math.log(delta) + LEAD_A1 + LEAD_A2 * tau
        + OFFSET_A1 + OFFSET_A2 * tau + LOGTAU_A * Math.log(tau);
    d.a0_t = LEAD_A2 + OFFSET_A2 + LOGTAU_A / tau;
    d.a0_tt = -LOGTAU_A / (tau * tau);
    for (int i = 0; i < N0.length; i++) {
      double ei = Math.exp(-T0[i] * tau);
      double denom = 1.0 - ei;
      d.a0 += N0[i] * Math.log(denom);
      d.a0_t += N0[i] * T0[i] * ei / denom;
      d.a0_tt -= N0[i] * T0[i] * T0[i] * ei / (denom * denom);
    }
  }

  /** Evaluate residual contribution and derivatives (power + gaussian terms). */
  private static void alphar(double delta, double tau, Derivs d) {
    for (int i = 0; i < N.length; i++) {
      double expc = L[i] == 0 ? 1.0 : Math.exp(-Math.pow(delta, L[i]));
      double deltaPow = Math.pow(delta, D[i]);
      double tauPow = Math.pow(tau, T[i]);
      double common = N[i] * deltaPow * tauPow * expc;
      d.ar += common;
      double dterm = D[i] / delta - (L[i] == 0 ? 0.0 : L[i] * Math.pow(delta, L[i] - 1));
      d.ar_d += common * dterm;
      d.ar_dd += common * (dterm * dterm + (-D[i] / (delta * delta)
          - (L[i] == 0 ? 0.0 : L[i] * (L[i] - 1) * Math.pow(delta, L[i] - 2))));
      double tterm = T[i] / tau;
      d.ar_t += common * tterm;
      d.ar_tt += common * tterm * (tterm - 1.0 / tau);
      d.ar_dt += common * dterm * tterm;
    }
    for (int i = 0; i < GN.length; i++) {
      double dr = delta - GEPS[i];
      double tr = tau - GGAMMA[i];
      double exp = Math.exp(-GETA[i] * dr * dr - GBETA[i] * tr * tr);
      double deltaPow = Math.pow(delta, GD[i]);
      double tauPow = Math.pow(tau, GT[i]);
      double common = GN[i] * deltaPow * tauPow * exp;
      d.ar += common;
      double dterm = GD[i] / delta - 2 * GETA[i] * dr;
      d.ar_d += common * dterm;
      d.ar_dd += common * (dterm * dterm + (-GD[i] / (delta * delta) - 2 * GETA[i]));
      double tterm = GT[i] / tau - 2 * GBETA[i] * tr;
      d.ar_t += common * tterm;
      d.ar_tt += common * (tterm * tterm + (-GT[i] / (tau * tau) - 2 * GBETA[i]));
      d.ar_dt += common * dterm * tterm;
    }
  }

  /** Solve for reduced density delta given temperature and pressure. */
  private static double density(double tau, double pressure, PhaseType type) {
    double delta;
    if (type == PhaseType.LIQUID) {
      delta = 1.0; // high density initial guess
    } else {
      delta = pressure / (R * (TC / tau)) / RHOC; // gas-like guess
    }
    for (int iter = 0; iter < 100; iter++) {
      Derivs d = new Derivs();
      alphar(delta, tau, d);
      double f = R * (TC / tau) * RHOC * delta * (1 + delta * d.ar_d) - pressure;
      double df = R * (TC / tau) * RHOC * (1 + 2 * delta * d.ar_d + delta * delta * d.ar_dd);
      double deltaNew = delta - f / df;
      if (Math.abs(deltaNew - delta) < 1e-12) {
        return deltaNew;
      }
      delta = deltaNew;
    }
    return delta;
  }

  /**
   * Compute thermodynamic properties for given temperature and pressure.
   *
   * @param temperature Kelvin
   * @param pressure Pascal
   * @return array [rho, Z, h, s, cp, cv, u, g, w]
   */
  public static double[] getProperties(double temperature, double pressure, PhaseType type) {
    double tau = TC / temperature;
    double delta = density(tau, pressure, type);
    double rho = delta * RHOC;

    Derivs d = new Derivs();
    alpha0(delta, tau, d);
    alphar(delta, tau, d);

    double Z = 1 + delta * d.ar_d;
    double at = d.a0_t + d.ar_t;
    double att = d.a0_tt + d.ar_tt;
    double h = R * temperature * (1 + tau * at + delta * d.ar_d);
    double s = R * (tau * at - (d.a0 + d.ar));
    double cv = R * (-tau * tau * att);
    double cp = cv + R * Math.pow(1 + delta * d.ar_d - delta * tau * d.ar_dt, 2)
        / (1 + 2 * delta * d.ar_d + delta * delta * d.ar_dd);
    double u = R * temperature * tau * at;
    double g = R * temperature * (d.a0 + d.ar + 1 + delta * d.ar_d);
    double dPdrho = R * temperature * (1 + 2 * delta * d.ar_d + delta * delta * d.ar_dd);
    double w2 = cp / cv * dPdrho / 0.0440098; // molar mass kg/mol
    double w = Math.sqrt(w2);
    double lnPhi = d.ar + delta * d.ar_d - Math.log(Z);
    double phi = Math.exp(lnPhi);

    return new double[] {rho, Z, h, s, cp, cv, u, g, w, phi};
  }
}

