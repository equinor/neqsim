package neqsim.thermo.util.steam;

/**
 * Utility class implementing a compact version of the full IAPWS IF97 steam tables.
 * <p>
 * The implementation is intentionally compact and only provides a few thermodynamic properties in
 * the SI unit system. Input pressure is in MPa and temperature in Kelvin.
 * </p>
 *
 * @author esol
 */
public final class Iapws_if97 {

  /** Specific gas constant for water [kJ/(kg*K)]. */
  private static final double R = 0.461526;

  // Coefficients for Region 1 from IAPWS IF97
  private static final int[] I1 = {0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3,
      4, 4, 4, 5, 8, 8, 21, 23, 29, 30, 31, 32};
  private static final int[] J1 = {-2, -1, 0, 1, 2, 3, 4, 5, -9, -7, -1, 0, 1, 3, -3, 0, 1, 3, 17,
      -4, 0, 6, -5, -2, 10, -8, -11, -6, -29, -31, -38, -39, -40, -41};
  private static final double[] N1 = {0.14632971213167, -0.84548187169114, -3.756360367204,
      3.3855169168385, -0.95791963387872, 0.15772038513228, -0.016616417199501, 8.1214629983568e-4,
      2.8319080123804e-4, -6.0706301565874e-4, -0.018990068218419, -0.032529748770505,
      -0.021841717175414, -5.283835796993e-5, -4.7184321073267e-4, -3.0001780793026e-4,
      4.7661393906987e-5, -4.4141845330846e-6, -7.2694996297594e-16, -3.1679644845054e-5,
      -2.8270797985312e-6, -8.5205128120103e-10, -2.2425281908e-6, -6.5171222895601e-7,
      -1.4341729937924e-13, -4.0516996860117e-7, -1.2734301741641e-9, -1.7424871230634e-10,
      -6.8762131295531e-19, 1.4478307828521e-20, 2.6335781662795e-23, -1.1947622640071e-23,
      1.8228094581404e-24, -9.3537087292458e-26};

  // Coefficients for Region 2 (ideal and residual parts)
  private static final int[] J0 = {0, 1, -5, -4, -3, -2, -1, 2, 3};
  private static final double[] N0 =
      {-9.6927686500217, 10.086655968018, -0.005608791128302, 0.071452738081455, -0.40710498223928,
          1.4240819171444, -4.383951131945, -0.28408632460772, 0.021268463753307};
  private static final int[] IR = {1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 4, 4, 4, 5, 6, 6, 6,
      7, 7, 7, 8, 8, 9, 10, 10, 10, 16, 16, 18, 20, 20, 20, 21, 22, 23, 24, 24, 24};
  private static final int[] JR = {0, 1, 2, 3, 6, 1, 2, 4, 7, 36, 0, 1, 3, 6, 35, 1, 2, 3, 7, 3, 16,
      35, 0, 11, 25, 8, 36, 13, 4, 10, 14, 29, 50, 57, 20, 35, 48, 21, 53, 39, 26, 40, 58};
  private static final double[] NR = {-1.7731742473213e-3, -0.017834862292358, -0.045996013696365,
      -0.057581259083432, -0.05032527872793, -3.3032641670203e-5, -1.8948987516315e-4,
      -3.9392777243355e-3, -0.043797295650573, -2.6674547914087e-5, 2.0481737692309e-8,
      4.3870667284435e-7, -3.227767723857e-5, -1.5033924542148e-3, -0.040668253562649,
      -7.8847309559367e-10, 1.2790717852285e-8, 4.8225372718507e-7, 2.2922076337661e-6,
      -1.6714766451061e-11, -2.1171472321355e-3, -23.895741934104, -5.905956432427e-18,
      -1.2621808899101e-6, -0.038946842435739, 1.1256211360459e-11, -8.2311340897998,
      1.9809712802088e-8, 1.0406965210174e-19, -1.0234747095929e-13, -1.0018179379511e-9,
      -8.0882908646985e-11, 0.10693031879409, -0.33662250574171, 8.9185845355421e-25,
      3.0629316876232e-13, -4.2002467698208e-6, -5.9056029685639e-26, 3.7826947613457e-6,
      -1.2768608934681e-15, 7.3087610595061e-29, 5.5414715350778e-17, -9.436970724121e-7};

  private Iapws_if97() {}

  // *************** Region 4 saturation equations *******************

  /** Saturation pressure as function of temperature (K) in MPa. */
  /**
   * Calculates the pressure at the boundary between region 4 and region 3 as a function of
   * temperature.
   *
   * @param T temperature in K
   * @return pressure in MPa
   */
  public static double p4_T(double T) {
    double theta = T - 0.23855557567849 / (T - 650.17534844798);
    double a = theta * theta + 1167.0521452767 * theta - 724213.16703206;
    double b = -17.073846940092 * theta * theta + 12020.82470247 * theta - 3232555.0322333;
    double c = 14.91510861353 * theta * theta - 4823.2657361591 * theta + 405113.40542057;
    return Math.pow(2 * c / (-b + Math.sqrt(b * b - 4 * a * c)), 4);
  }

  /** Saturation temperature as function of pressure (MPa) in Kelvin. */
  /**
   * Calculates the temperature at the boundary between region 4 and region 3 as a function of
   * pressure.
   *
   * @param p pressure in MPa
   * @return temperature in K
   */
  public static double T4_p(double p) {
    double beta = Math.pow(p, 0.25);
    double e = beta * beta - 17.073846940092 * beta + 14.91510861353;
    double f = 1167.0521452767 * beta * beta + 12020.82470247 * beta - 4823.2657361591;
    double g = -724213.16703206 * beta * beta - 3232555.0322333 * beta + 405113.40542057;
    double d = 2 * g / (-f - Math.sqrt(f * f - 4 * e * g));
    double temp = (650.17534844798 + d
        - Math
            .sqrt(Math.pow(650.17534844798 + d, 2) - 4 * (-0.23855557567849 + 650.17534844798 * d)))
        / 2.0;
    return temp;
  }

  // *************** Region 1 dimensionless Gibbs functions **********
  private static double gamma1(double pi, double tau) {
    double sum = 0.0;
    for (int i = 0; i < N1.length; i++) {
      sum += N1[i] * Math.pow(7.1 - pi, I1[i]) * Math.pow(tau - 1.222, J1[i]);
    }
    return sum;
  }

  private static double gamma1_pi(double pi, double tau) {
    double sum = 0.0;
    for (int i = 0; i < N1.length; i++) {
      sum -= N1[i] * I1[i] * Math.pow(7.1 - pi, I1[i] - 1) * Math.pow(tau - 1.222, J1[i]);
    }
    return sum;
  }

  private static double gamma1_tau(double pi, double tau) {
    double sum = 0.0;
    for (int i = 0; i < N1.length; i++) {
      sum += N1[i] * Math.pow(7.1 - pi, I1[i]) * J1[i] * Math.pow(tau - 1.222, J1[i] - 1);
    }
    return sum;
  }

  // *************** Region 2 dimensionless Gibbs functions **********
  private static double gamma0(double pi, double tau) {
    double sum = Math.log(pi);
    for (int i = 0; i < N0.length; i++) {
      sum += N0[i] * Math.pow(tau, J0[i]);
    }
    return sum;
  }

  private static double gamma0_pi(double pi) {
    return 1.0 / pi;
  }

  private static double gamma0_tau(double pi, double tau) {
    double sum = 0.0;
    for (int i = 0; i < N0.length; i++) {
      sum += N0[i] * J0[i] * Math.pow(tau, J0[i] - 1);
    }
    return sum;
  }

  private static double gammar(double pi, double tau) {
    double sum = 0.0;
    for (int i = 0; i < NR.length; i++) {
      sum += NR[i] * Math.pow(pi, IR[i]) * Math.pow(tau - 0.5, JR[i]);
    }
    return sum;
  }

  private static double gammar_pi(double pi, double tau) {
    double sum = 0.0;
    for (int i = 0; i < NR.length; i++) {
      sum += NR[i] * IR[i] * Math.pow(pi, IR[i] - 1) * Math.pow(tau - 0.5, JR[i]);
    }
    return sum;
  }

  private static double gammar_tau(double pi, double tau) {
    double sum = 0.0;
    for (int i = 0; i < NR.length; i++) {
      sum += NR[i] * Math.pow(pi, IR[i]) * JR[i] * Math.pow(tau - 0.5, JR[i] - 1);
    }
    return sum;
  }

  // second derivatives needed for cp and sound speed
  private static double gamma1_pipi(double pi, double tau) {
    double sum = 0.0;
    for (int i = 0; i < N1.length; i++) {
      sum += N1[i] * I1[i] * (I1[i] - 1.0) * Math.pow(7.1 - pi, I1[i] - 2.0)
          * Math.pow(tau - 1.222, J1[i]);
    }
    return sum;
  }

  private static double gamma1_pitau(double pi, double tau) {
    double sum = 0.0;
    for (int i = 0; i < N1.length; i++) {
      sum -= N1[i] * I1[i] * Math.pow(7.1 - pi, I1[i] - 1.0) * J1[i]
          * Math.pow(tau - 1.222, J1[i] - 1.0);
    }
    return sum;
  }

  private static double gamma1_tautau(double pi, double tau) {
    double sum = 0.0;
    for (int i = 0; i < N1.length; i++) {
      sum += N1[i] * Math.pow(7.1 - pi, I1[i]) * J1[i] * (J1[i] - 1.0)
          * Math.pow(tau - 1.222, J1[i] - 2.0);
    }
    return sum;
  }

  private static double gamma0_tautau(double tau) {
    double sum = 0.0;
    for (int i = 0; i < N0.length; i++) {
      sum += N0[i] * J0[i] * (J0[i] - 1.0) * Math.pow(tau, J0[i] - 2.0);
    }
    return sum;
  }

  private static double gammar_pipi(double pi, double tau) {
    double sum = 0.0;
    for (int i = 0; i < NR.length; i++) {
      sum += NR[i] * IR[i] * (IR[i] - 1.0) * Math.pow(pi, IR[i] - 2.0) * Math.pow(tau - 0.5, JR[i]);
    }
    return sum;
  }

  private static double gammar_pitau(double pi, double tau) {
    double sum = 0.0;
    for (int i = 0; i < NR.length; i++) {
      sum += NR[i] * IR[i] * Math.pow(pi, IR[i] - 1.0) * JR[i] * Math.pow(tau - 0.5, JR[i] - 1.0);
    }
    return sum;
  }

  private static double gammar_tautau(double pi, double tau) {
    double sum = 0.0;
    for (int i = 0; i < NR.length; i++) {
      sum += NR[i] * Math.pow(pi, IR[i]) * JR[i] * (JR[i] - 1.0) * Math.pow(tau - 0.5, JR[i] - 2.0);
    }
    return sum;
  }

  // *************** Public property methods *************************

  /**
   * Specific volume in m^3/kg for given pressure (MPa) and temperature (K).
   */
  /**
   * Calculates the specific volume of steam at given pressure and temperature.
   *
   * @param p pressure in MPa
   * @param T temperature in K
   * @return specific volume in m^3/kg
   */
  public static double v_pt(double p, double T) {
    double ts = T4_p(p);
    if (T <= ts) { // Region 1
      double pi = p / 16.53;
      double tau = 1386.0 / T;
      return R * T / p * pi * gamma1_pi(pi, tau) / 1000.0;
    }
    // Region 2
    double pi = p;
    double tau = 540.0 / T;
    return R * T / p * pi * (gamma0_pi(pi) + gammar_pi(pi, tau)) / 1000.0;
  }

  /**
   * Specific enthalpy in kJ/kg for given pressure (MPa) and temperature (K).
   */
  /**
   * Calculates the enthalpy of steam at given pressure and temperature.
   *
   * @param p pressure in MPa
   * @param T temperature in K
   * @return enthalpy in kJ/kg
   */
  public static double h_pt(double p, double T) {
    double ts = T4_p(p);
    if (T <= ts) { // Region 1
      double pi = p / 16.53;
      double tau = 1386.0 / T;
      return R * T * tau * gamma1_tau(pi, tau);
    }
    // Region 2
    double pi = p;
    double tau = 540.0 / T;
    return R * T * tau * (gamma0_tau(pi, tau) + gammar_tau(pi, tau));
  }

  /**
   * Specific entropy in kJ/(kg*K) for given pressure (MPa) and temperature (K).
   */
  /**
   * Calculates the entropy of steam at given pressure and temperature.
   *
   * @param p pressure in MPa
   * @param T temperature in K
   * @return entropy in kJ/kg/K
   */
  public static double s_pt(double p, double T) {
    double ts = T4_p(p);
    if (T <= ts) { // Region 1
      double pi = p / 16.53;
      double tau = 1386.0 / T;
      double g = gamma1(pi, tau);
      double g_tau = gamma1_tau(pi, tau);
      return R * (tau * g_tau - g);
    }
    // Region 2
    double pi = p;
    double tau = 540.0 / T;
    double g = gamma0(pi, tau) + gammar(pi, tau);
    double g_tau = gamma0_tau(pi, tau) + gammar_tau(pi, tau);
    return R * (tau * g_tau - g);
  }

  /**
   * Heat capacity at constant pressure in kJ/(kg*K).
   */
  /**
   * Calculates the specific heat capacity of steam at given pressure and temperature.
   *
   * @param p pressure in MPa
   * @param T temperature in K
   * @return specific heat capacity in kJ/kg/K
   */
  public static double cp_pt(double p, double T) {
    double ts = T4_p(p);
    if (T <= ts) {
      double pi = p / 16.53;
      double tau = 1386.0 / T;
      return -R * tau * tau * gamma1_tautau(pi, tau);
    }
    double pi = p;
    double tau = 540.0 / T;
    return -R * tau * tau * (gamma0_tautau(tau) + gammar_tautau(pi, tau));
  }

  /**
   * Speed of sound in m/s.
   */
  /**
   * Calculates the speed of sound in steam at given pressure and temperature.
   *
   * @param p pressure in MPa
   * @param T temperature in K
   * @return speed of sound in m/s
   */
  public static double w_pt(double p, double T) {
    double ts = T4_p(p);
    if (T <= ts) {
      double pi = p / 16.53;
      double tau = 1386.0 / T;
      double gpi = gamma1_pi(pi, tau);
      double gpipi = gamma1_pipi(pi, tau);
      double gpitau = gamma1_pitau(pi, tau);
      double gtautau = gamma1_tautau(pi, tau);
      double num = 1000.0 * R * T * gpi * gpi;
      double denom = (gpi - tau * gpitau) * (gpi - tau * gpitau) / (tau * tau * gtautau) - gpipi;
      return Math.sqrt(num / denom);
    }
    double pi = p;
    double tau = 540.0 / T;
    double grPi = gammar_pi(pi, tau);
    double grPipi = gammar_pipi(pi, tau);
    double grPitau = gammar_pitau(pi, tau);
    double grTautau = gammar_tautau(pi, tau);
    double g0tt = gamma0_tautau(tau);
    double num = 1000.0 * R * T * (1.0 + 2.0 * pi * grPi + pi * pi * grPi * grPi);
    double denom = (1.0 - pi * pi * grPipi)
        + Math.pow(1.0 + pi * grPi - tau * pi * grPitau, 2.0) / (tau * tau * (g0tt + grTautau));
    return Math.sqrt(num / denom);
  }

  /** Saturation temperature at given pressure in Kelvin. */
  /**
   * Calculates the saturation temperature at a given pressure.
   *
   * @param p pressure in MPa
   * @return saturation temperature in K
   */
  public static double tsat_p(double p) {
    return T4_p(p);
  }

  /** Saturation pressure at given temperature in MPa. */
  /**
   * Calculates the saturation pressure at a given temperature.
   *
   * @param T temperature in K
   * @return saturation pressure in MPa
   */
  public static double psat_t(double T) {
    return p4_T(T);
  }
}
