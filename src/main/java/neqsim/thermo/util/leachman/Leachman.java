package neqsim.thermo.util.leachman;

import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * The Leachman class provides methods to calculate the density, pressure, and various thermodynamic
 * properties of hydrogen using the Leachman method. It supports different types of hydrogen:
 * normal, para, and ortho.
 *
 * <p>
 * The class includes methods for setting up the parameters for different hydrogen types,
 * calculating the density from temperature and pressure, and calculating various thermodynamic
 * properties.
 * </p>
 *
 * <p>
 * The main methods include:
 * </p>
 * <ul>
 * <li>{@link #SetupLeachman(String)}: Initializes the constants and parameters for the specified
 * hydrogen type.</li>
 * <li>{@link #DensityLeachman(int, double, double, doubleW, intW, StringW)}: Calculates the density
 * as a function of temperature and pressure.</li>
 * <li>{@link #PressureLeachman(double, double, doubleW, doubleW)}: Calculates the pressure and
 * compressibility factor.</li>
 * <li>{@link #propertiesLeachman(double, double, doubleW, doubleW, doubleW, doubleW, doubleW, doubleW, doubleW, doubleW, doubleW, doubleW, doubleW, doubleW, doubleW, doubleW, doubleW, doubleW)}:
 * Calculates various thermodynamic properties.</li>
 * </ul>
 *
 * <p>
 * The class also includes helper methods for calculating the ideal gas and real gas Helmholtz
 * energy and their derivatives.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * {@code
 * Leachman leachman = new Leachman();
 * leachman.SetupLeachman("normal");
 * doubleW density = new doubleW(30);
 * doubleW pressure = new doubleW(500.0);
 * intW ierr = new intW(0);
 * StringW herr = new StringW("");
 * leachman.DensityLeachman(0, 500, pressure.val, density, ierr, herr);
 * System.out.println("Density: " + density.val);
 * }
 * </pre>
 *
 * <p>
 * Note: The class uses the doubleW and intW classes from the org.netlib.util package to handle
 * output parameters.
 * </p>
 *
 * @see org.netlib.util.doubleW
 * @see org.netlib.util.intW
 * @see org.netlib.util.StringW
 * @author victor
 */
public class Leachman {
  private String hydrogenType;

  // Constants for hydrogen
  double R_L;
  double M_L;// = 2.01588; // Molar mass for hydrogen (g/mol)
  double Tc;
  double Dc;

  double dPdDsave;

  // a0 parameters
  int K;
  double[] a0k, b0k;

  int K_normal = 7;
  double[] a0k_normal = new double[K_normal + 1];
  double[] b0k_normal = new double[K_normal + 1];

  int K_para = 9;
  double[] a0k_para = new double[K_para + 1];
  double[] b0k_para = new double[K_para + 1];

  int K_ortho = 6;
  double[] a0k_ortho = new double[K_ortho + 1];
  double[] b0k_ortho = new double[K_ortho + 1];

  // ar parameters
  int l = 7;
  int m = 9;
  int n = 14;

  double[] N_i, t_i, d_i, p_i, phi_i, beta_i, gamma_i, D_i;

  double[] N_i_normal = new double[n + 1];
  double[] t_i_normal = new double[n + 1];
  double[] d_i_normal = new double[n + 1];
  double[] p_i_normal = new double[n + 1];
  double[] phi_i_normal = new double[n + 1];
  double[] beta_i_normal = new double[n + 1];
  double[] gamma_i_normal = new double[n + 1];
  double[] D_i_normal = new double[n + 1];

  double[] N_i_para = new double[n + 1];
  double[] t_i_para = new double[n + 1];
  double[] d_i_para = new double[n + 1];
  double[] p_i_para = new double[n + 1];
  double[] phi_i_para = new double[n + 1];
  double[] beta_i_para = new double[n + 1];
  double[] gamma_i_para = new double[n + 1];
  double[] D_i_para = new double[n + 1];

  double[] N_i_ortho = new double[n + 1];
  double[] t_i_ortho = new double[n + 1];
  double[] d_i_ortho = new double[n + 1];
  double[] p_i_ortho = new double[n + 1];
  double[] phi_i_ortho = new double[n + 1];
  double[] beta_i_ortho = new double[n + 1];
  double[] gamma_i_ortho = new double[n + 1];
  double[] D_i_ortho = new double[n + 1];

  double epsilon = 1e-15;

  /**
   * Calculate density as a function of temperature and pressure using the Leachman method. This is
   * an iterative routine that calls PressureLeachman to find the correct state point. Generally,
   * only 6 iterations at most are required. If the iteration fails to converge, the ideal gas
   * density and an error message are returned. No checks are made to determine the phase boundary,
   * which would have guaranteed that the output is in the gas phase (or liquid phase when iFlag=2).
   * It is up to the user to locate the phase boundary, and thus identify the phase of the T and P
   * inputs. If the state point is 2-phase, the output density will represent a metastable state.
   *
   * @param iFlag Set to 0 for strict pressure solver in the gas phase without checks (fastest mode,
   *        but output state may not be stable single phase). Set to 1 to make checks for possible
   *        2-phase states (result may still not be stable single phase, but many unstable states
   *        will be identified). Set to 2 to search for liquid phase (and make the same checks when
   *        iFlag=1).
   * @param T Temperature (K)
   * @param P Pressure (kPa)
   * @param D Density (mol/l). For the liquid phase, an initial value can be sent to the routine to
   *        avoid a solution in the metastable or gas phases. The initial value should be sent as a
   *        negative number.
   * @param ierr Error number (0 indicates no error)
   * @param herr Error message if ierr is not equal to zero
   */
  public void DensityLeachman(int iFlag, double T, double P, doubleW D, intW ierr, StringW herr) {
    // Sub DensityGERG(iFlag, T, P, x, D, ierr, herr)

    // Calculate density as a function of temperature and pressure. This is an
    // iterative routine that calls PressureGERG
    // to find the correct state point. Generally only 6 iterations at most are
    // required.
    // If the iteration fails to converge, the ideal gas density and an error
    // message are returned.
    // No checks are made to determine the phase boundary, which would have
    // guaranteed that the output is in the gas phase (or liquid phase when
    // iFlag=2).
    // It is up to the user to locate the phase boundary, and thus identify the
    // phase of the T and P inputs.
    // If the state point is 2-phase, the output density will represent a metastable
    // state.

    // Inputs:
    // iFlag - Set to 0 for strict pressure solver in the gas phase without checks
    // (fastest mode, but output state may not be stable single phase)
    // Set to 1 to make checks for possible 2-phase states (result may still not be
    // stable single phase, but many unstable states will be identified)
    // Set to 2 to search for liquid phase (and make the same checks when iFlag=1)
    // T - Temperature (K)
    // P - Pressure (kPa)
    // x() - Composition (mole fraction)
    // (An initial guess for the density can be sent in D as the negative of the
    // guess for roots that are in the liquid phase instead of using iFlag=2)

    // Outputs:
    // D - Density (mol/l)
    // For the liquid phase, an initial value can be sent to the routine to avoid
    // a solution in the metastable or gas phases.
    // The initial value should be sent as a negative number.
    // ierr - Error number (0 indicates no error)
    // herr - Error message if ierr is not equal to zero

    int nFail;
    int iFail;
    double plog;
    double vlog;
    double dpdlv;
    double vdiff;
    double tolr;
    double vinc;
    doubleW Tcx = new doubleW(0.0d);

    doubleW Dcx = new doubleW(0.0d);
    doubleW dPdD = new doubleW(0.0d);
    doubleW d2PdD2 = new doubleW(0.0d);
    doubleW d2PdTD = new doubleW(0.0d);
    doubleW dPdT = new doubleW(0.0d);
    doubleW U = new doubleW(0.0d);
    doubleW H = new doubleW(0.0d);
    doubleW S = new doubleW(0.0d);
    doubleW A = new doubleW(0.0d);
    doubleW P2 = new doubleW(0.0d);
    doubleW Z = new doubleW(0.0d);
    doubleW Cv = new doubleW(0.0d);

    doubleW Cp = new doubleW(0.0d);
    doubleW W = new doubleW(0.0d);
    doubleW G = new doubleW(0.0d);
    doubleW JT = new doubleW(0.0d);
    doubleW Kappa = new doubleW(0.0d);
    doubleW PP = new doubleW(0.0d);
    ierr.val = 0;
    herr.val = "";
    nFail = 0;
    iFail = 0;
    if (P < epsilon) {
      D.val = 0;
      return;
    }
    tolr = 0.0000001;
    PseudoCriticalPointLeachman(Tcx, Dcx);

    if (D.val > -epsilon) {
      D.val = P / R_L / T; // Ideal gas estimate for vapor phase
      if (iFlag == 2) {
        D.val = Dcx.val * 3;
      } // Initial estimate for liquid phase
    } else {
      D.val = Math.abs(D.val); // If D<0, then use as initial estimate
    }

    plog = Math.log(P);
    vlog = -Math.log(D.val);
    for (int it = 1; it <= 50; ++it) {
      if (vlog < -7 || vlog > 100 || it == 20 || it == 30 || it == 40 || iFail == 1) {
        // Current state is bad or iteration is taking too long. Restart with completely
        // different initial state
        iFail = 0;
        if (nFail > 2) {
          // Iteration failed (above loop did not find a solution or checks made below
          // indicate possible 2-phase state)
          ierr.val = 1;
          herr.val =
              "Calculation failed to converge in Leachman method, ideal gas density returned.";
          D.val = P / R_L / T;
        }
        nFail++;
        if (nFail == 1) {
          D.val = Dcx.val * 3; // If vapor phase search fails, look for root in liquid
                               // region
        } else if (nFail == 2) {
          D.val = Dcx.val * 2.5; // If liquid phase search fails, look for root between
                                 // liquid and critical
                                 // regions
        } else if (nFail == 3) {
          D.val = Dcx.val * 2; // If search fails, look for root in critical region
        }
        vlog = -Math.log(D.val);
      }
      D.val = Math.exp(-vlog);
      PressureLeachman(T, D.val, P2, Z);
      if (dPdDsave < epsilon || P2.val < epsilon) {
        // Current state is 2-phase, try locating a different state that is single phase
        vinc = 0.1;
        if (D.val > Dcx.val) {
          vinc = -0.1;
        }
        if (it > 5) {
          vinc = vinc / 2;
        }
        if (it > 10 && it < 20) {
          vinc = vinc / 5;
        }
        vlog += vinc;
      } else {
        // Find the next density with a first order Newton's type iterative scheme, with
        // log(P) as the known variable and log(v) as the unknown property.
        // See AGA 8 publication for further information.
        dpdlv = -D.val * dPdDsave; // d(p)/d[log(v)]
        vdiff = (Math.log(P2.val) - plog) * P2.val / dpdlv;
        vlog += -vdiff;
        if (Math.abs(vdiff) < tolr) {
          // Check to see if state is possibly 2-phase, and if so restart
          if (dPdDsave < 0) {
            iFail = 1;
          } else {
            D.val = Math.exp(-vlog);

            // If requested, check to see if point is possibly 2-phase
            if (iFlag > 0) {
              propertiesLeachman(T, D.val, PP, Z, dPdD, d2PdD2, d2PdTD, dPdT, U, H, S, Cv, Cp, W, G,
                  JT, Kappa, A);
              if ((PP.val <= 0 || dPdD.val <= 0 || d2PdTD.val <= 0)
                  || (Cv.val <= 0 || Cp.val <= 0 || W.val <= 0)) {
                // Iteration failed (above loop did find a solution or checks made
                // below
                // indicate possible 2-phase state)
                ierr.val = 1;
                herr.val =
                    "Calculation failed to converge in Leachman method, ideal gas density returned.";
                D.val = P / R_L / T;
              }
            }
            return; // Iteration converged
          }
        }
      }
    }
    // Iteration failed (above loop did not find a solution or checks made below
    // indicate possible 2-phase state)
    ierr.val = 1;
    herr.val = "Calculation failed to converge in Leachman method, ideal gas density returned.";
    D.val = P / R_L / T;
  }

  /**
   * Calculate the pressure and compressibility factor using the Leachman method.
   *
   * @param T Temperature (K)
   * @param D Density (mol/l)
   * @param P Output parameter for pressure (kPa)
   * @param Z Output parameter for compressibility factor
   */
  public void PressureLeachman(double T, double D, doubleW P, doubleW Z) {
    doubleW[][] ar = new doubleW[4][4];
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 4; j++) {
        ar[i][j] = new doubleW(0.0d);
      }
    }
    AlpharLeachman(0, 0, T, D, ar);

    Z.val = 1 + ar[0][1].val;
    P.val = D * R_L * T * Z.val;
    dPdDsave = R_L * T * (1 + 2 * ar[0][1].val + ar[0][2].val);
  }

  /**
   * Calculate the residual Helmholtz energy and its derivatives with respect to tau and delta.
   *
   * Outputs:
   * <ul>
   * <li>ar(0,0) - Residual Helmholtz energy (dimensionless, =a/RT)</li>
   * <li>ar(0,1) - delta*partial (ar)/partial(delta)</li>
   * <li>ar(0,2) - delta^2*partial^2(ar)/partial(delta)^2</li>
   * <li>ar(0,3) - delta^3*partial^3(ar)/partial(delta)^3</li>
   * <li>ar(1,0) - tau*partial (ar)/partial(tau)</li>
   * <li>ar(1,1) - tau*delta*partial^2(ar)/partial(tau)/partial(delta)</li>
   * <li>ar(2,0) - tau^2*partial^2(ar)/partial(tau)^2</li>
   * </ul>
   *
   * @param itau Order of derivative with respect to tau
   * @param idelta Order of derivative with respect to delta
   * @param T Temperature (K)
   * @param D Density (mol/l)
   * @param ar Output array for Helmholtz energy and its derivatives
   */
  void AlpharLeachman(int itau, int idelta, double T, double D, doubleW[][] ar) {
    // Select parameters based on hydrogen type
    // double[] N_i, t_i, d_i, p_i, phi_i, beta_i, gamma_i, D_i;
    // double Tc, Dc;

    // Clear previous ar values
    for (int i = 0; i <= 3; ++i) {
      for (int j = 0; j <= 3; ++j) {
        ar[i][j].val = 0;
      }
    }

    // Define reduced variables
    double tau = Tc / T; // Reduced temperature
    double delta = D / Dc; // Reduced density

    // first sum
    for (int k = 0; k < l; k++) {
      double Ni = N_i[k];
      double di = d_i[k];
      double ti = t_i[k];

      double B = Ni * Math.pow(delta, di) * Math.pow(tau, ti);
      double dBddelta = B * di * Math.pow(delta, -1);
      double d2Bddelta2 = B * di * (di - 1) * Math.pow(delta, -2);
      double d3Bddelta3 = B * di * (di - 1) * (di - 2) * Math.pow(delta, -3);

      double dBdtau = B * ti * Math.pow(tau, -1);
      double d2Bdtau2 = B * ti * (ti - 1) * Math.pow(tau, -2);

      double d2Bddeltadtau = B * di * ti * Math.pow(delta, -1) * Math.pow(tau, -1);

      ar[0][0].val += B;
      ar[0][1].val += dBddelta;
      ar[0][2].val += d2Bddelta2;
      ar[0][3].val += d3Bddelta3;

      ar[1][0].val += dBdtau;
      ar[2][0].val += d2Bdtau2;

      ar[1][1].val += d2Bddeltadtau;
    }
    // second sum
    for (int k = l; k < m; k++) {
      double Ni = N_i[k];
      double di = d_i[k];
      double ti = t_i[k];
      double pi = p_i[k];

      double B = Ni * Math.pow(delta, di) * Math.pow(tau, ti);
      double e = -Math.pow(delta, pi);
      double E = Math.exp(e);

      // derivatives of delta
      double dBddelta = B * di * Math.pow(delta, -1);
      double d2Bddelta2 = B * di * (di - 1) * Math.pow(delta, -2);
      double d3Bddelta3 = B * di * (di - 1) * (di - 2) * Math.pow(delta, -3);

      double deddelta = -pi * Math.pow(delta, pi - 1);
      double d2edDelta2 = -pi * (pi - 1) * Math.pow(delta, pi - 2);
      double d3edDelta3 = -pi * (pi - 1) * (pi - 2) * Math.pow(delta, pi - 3);

      // derivatives of tau
      double dBdTau = B * ti * Math.pow(tau, -1);
      double d2BdTau2 = B * ti * (ti - 1) * Math.pow(tau, -2);
      double d3BdTau3 = B * ti * (ti - 1) * (ti - 2) * Math.pow(tau, -3);

      double dedTau = 0;

      // derivatives of delta and tau
      double d2Bddeltadtau = B * di * ti * Math.pow(delta, -1) * Math.pow(tau, -1);
      double d2edDeltadTau = 0;

      ar[0][0].val += B * E;
      // d(alpha^r)/d(delta)
      ar[0][1].val += dBddelta * E + B * E * deddelta;
      // d^2(alpha^r)/d(delta)^2
      double G = d2Bddelta2 + 2 * dBddelta * deddelta + B * deddelta * deddelta + B * d2edDelta2;
      ar[0][2].val += G * E;
      // d^3(alpha^r)/d(delta)^3
      double dGddelta =
          d3Bddelta3 + 2 * (d2Bddelta2 * deddelta + dBddelta * d2edDelta2) + dBddelta * d2edDelta2
              + B * d3edDelta3 + dBddelta * deddelta * deddelta + 2 * B * deddelta * d2edDelta2;
      ar[0][3].val += E * (dGddelta + G * deddelta);
      // d(alpha^r)/d(tau)
      ar[1][0].val += dBdTau * E + B * E * dedTau;
      // d^2(alpha^r)/d(tau)^2
      ar[2][0].val += d2BdTau2 * E; // dedtau = 0
      // d^2(alpha^r)/(d(delta)d(tau))
      ar[1][1].val += d2Bddeltadtau * E + dBdTau * E * deddelta;
    }
    // thirdsum
    for (int k = m; k < n; k++) {
      double Ni = N_i[k];
      double di = d_i[k];
      double ti = t_i[k];
      double phi = phi_i[k];
      double Di = D_i[k];
      double betai = beta_i[k];
      double gammai = gamma_i[k];

      double B = Ni * Math.pow(delta, di) * Math.pow(tau, ti);
      double e = (phi * (delta - Di) * (delta - Di) + betai * (tau - gammai) * (tau - gammai));
      double E = Math.exp(e);

      ar[0][0].val += B * E;

      // derivatives of delta
      double dBddelta = B * di * Math.pow(delta, -1);
      double d2Bddelta2 = B * di * (di - 1) * Math.pow(delta, -2);
      double d3Bddelta3 = B * di * (di - 1) * (di - 2) * Math.pow(delta, -3);

      double dedDelta = 2 * phi * (delta - Di);
      double d2edDelta2 = 2 * phi;
      double d3edDelta3 = 0;

      // derivatives of tau
      double dBdTau = B * ti * Math.pow(tau, -1);
      double d2BdTau2 = B * ti * (ti - 1) * Math.pow(tau, -2);
      double d3BdTau3 = B * ti * (ti - 1) * (ti - 2) * Math.pow(tau, -3);

      double dedTau = 2 * betai * (tau - gammai);
      double d2edTau2 = 2 * betai;

      // derivatives of delta and tau
      double d2Bddeltadtau = B * di * ti * Math.pow(delta, -1) * Math.pow(tau, -1);
      double d2edDeltadTau = 0;

      // d(alpha^r)/d(delta)
      ar[0][1].val += dBddelta * E + B * E * dedDelta;

      // d^2(alpha^r)/d(delta)^2
      double G = d2Bddelta2 + 2 * dBddelta * dedDelta + B * dedDelta * dedDelta + B * d2edDelta2;
      ar[0][2].val += G * E;

      // d^3(alpha^r)/d(delta)^3
      double dGddelta =
          d3Bddelta3 + 2 * (d2Bddelta2 * dedDelta + dBddelta * d2edDelta2) + dBddelta * d2edDelta2
              + B * d3edDelta3 + dBddelta * dedDelta * dedDelta + 2 * B * dedDelta * d2edDelta2;
      ar[0][3].val += E * (dGddelta + G * dedDelta);

      // d(alpha^r)/d(tau)
      ar[1][0].val += dBdTau * E + B * E * dedTau;

      // d^2(alpha^r)/d(tau)^2
      ar[2][0].val +=
          d2BdTau2 * E + 2 * dBdTau * E * dedTau + B * E * dedTau * dedTau + B * E * d2edTau2;

      // d^2(alpha^r)/(d(delta)d(tau))
      ar[1][1].val += d2Bddeltadtau * E + dBdTau * E * dedDelta + dBddelta * E * dedTau
          + B * E * dedDelta * dedTau + B * E * d2edDeltadTau;
    }
    ar[0][1].val = delta * ar[0][1].val;
    ar[0][2].val = delta * delta * ar[0][2].val;
    ar[0][3].val = delta * delta * delta * ar[0][3].val;
    ar[1][0].val = tau * ar[1][0].val;
    ar[2][0].val = tau * tau * ar[2][0].val;
    ar[1][1].val = tau * delta * ar[1][1].val;
  }

  /**
   * Calculate the ideal gas Helmholtz energy and its derivatives with respect to tau and delta.
   *
   * @param T Temperature (K)
   * @param D Density (mol/l)
   * @param a0 Output array for ideal gas Helmholtz energy and its derivatives
   */
  void Alpha0Leachman(double T, double D, doubleW[] a0) {
    // Private Sub Alpha0GERG(T, D, x, a0)

    // Calculate the ideal gas Helmholtz energy and its derivatives with respect to
    // tau and delta.
    // This routine is not needed when only P (or Z) is calculated.

    // Inputs:
    // T - Temperature (K)
    // D - Density (mol/l)
    // x() - Composition (mole fraction)

    // Outputs:
    // a0(0) - Ideal gas Helmholtz energy (all dimensionless [i.e., divided by RT])
    // a0(1) - tau*partial(a0)/partial(tau)
    // a0(2) - tau^2*partial^2(a0)/partial(tau)^2

    // Define reduced variables
    double tau = Tc / T; // Reduced temperature
    double delta = D / Dc; // Reduced density
    double lntau = Math.log(tau);

    a0[0].val = Math.log(delta) + 1.5 * Math.log(tau) + a0k[0] + a0k[1] * tau;
    for (int k = 2; k < K; k++) {
      double d = a0k[k];
      a0[0].val += a0k[k] * Math.log(1 - Math.exp(b0k[k] * tau));
      // System.out.println("a0k:" + a0k[k]);
      // System.out.println("b0k:" + b0k[k]);

      // System.out.println(a0k[k]*Math.log(1-Math.exp(b0k[k]*tau)));
    }

    a0[1].val = 1.5 / tau + a0k[1];
    for (int k = 2; k < K; k++) {
      a0[1].val += -a0k[k] * b0k[k] * Math.exp(b0k[k] * tau) / (1 - Math.exp(b0k[k] * tau));
    }

    a0[2].val = -1.5 / (tau * tau);
    for (int k = 2; k < K; k++) {
      a0[2].val +=
          -a0k[k] * b0k[k] * b0k[k] * Math.exp(b0k[k] * tau) * (1 / (1 - Math.exp(b0k[k] * tau))
              + Math.exp(b0k[k] * tau) / Math.pow(1 - Math.exp(b0k[k] * tau), 2));
      // -a0k[k]*b0k[k]*b0k[k]*Math.exp(b0k[k]*tau)/Math.pow((-1+Math.exp(b0k[k]*tau)),2);
    }
    a0[1].val = tau * a0[1].val;
    a0[2].val = tau * tau * a0[2].val;
  }

  /**
   * <p>
   * PropertiesGERG.
   * </p>
   *
   * @param T a double
   * @param D a double
   * @param P a {@link org.netlib.util.doubleW} object
   * @param Z a {@link org.netlib.util.doubleW} object
   * @param dPdD a {@link org.netlib.util.doubleW} object
   * @param d2PdD2 a {@link org.netlib.util.doubleW} object
   * @param d2PdTD a {@link org.netlib.util.doubleW} object
   * @param dPdT a {@link org.netlib.util.doubleW} object
   * @param U a {@link org.netlib.util.doubleW} object
   * @param H a {@link org.netlib.util.doubleW} object
   * @param S a {@link org.netlib.util.doubleW} object
   * @param Cv a {@link org.netlib.util.doubleW} object
   * @param Cp a {@link org.netlib.util.doubleW} object
   * @param W a {@link org.netlib.util.doubleW} object
   * @param G a {@link org.netlib.util.doubleW} object
   * @param JT a {@link org.netlib.util.doubleW} object
   * @param Kappa a {@link org.netlib.util.doubleW} object
   * @param A a {@link org.netlib.util.doubleW} object
   */
  public void propertiesLeachman(double T, double D, doubleW P, doubleW Z, doubleW dPdD,
      doubleW d2PdD2, doubleW d2PdTD, doubleW dPdT, doubleW U, doubleW H, doubleW S, doubleW Cv,
      doubleW Cp, doubleW W, doubleW G, doubleW JT, doubleW Kappa, doubleW A) {
    // Sub PropertiesGERG(T, D, x, P, Z, dPdD, d2PdD2, d2PdTD, dPdT, U, H, S, Cv, Cp, W, G, JT,
    // Kappa, A)

    // Calculate thermodynamic properties as a function of temperature and density.
    // Calls are made to the subroutines
    // ReducingParametersGERG, IdealGERG, and ResidualGERG. If the density is not
    // known, call subroutine DENSITY first
    // with the known values of pressure and temperature.

    // Inputs:
    // T - Temperature (K)
    // D - Density (mol/l)
    // x() - Composition (mole fraction)

    // Outputs:
    // P - Pressure (kPa)
    // Z - Compressibility factor
    // dPdD - First derivative of pressure with respect to density at constant
    // temperature [kPa/(mol/l)]
    // d2PdD2 - Second derivative of pressure with respect to density at constant
    // temperature [kPa/(mol/l)^2]
    // d2PdTD - Second derivative of pressure with respect to temperature and
    // density [kPa/(mol/l)/K]
    // dPdT - First derivative of pressure with respect to temperature at constant
    // density (kPa/K)
    // U - Internal energy (J/mol)
    // H - Enthalpy (J/mol)
    // S - Entropy [J/(mol-K)]
    // Cv - Isochoric heat capacity [J/(mol-K)]
    // Cp - Isobaric heat capacity [J/(mol-K)]
    // W - Speed of sound (m/s)
    // G - Gibbs energy (J/mol)
    // JT - Joule-Thomson coefficient (K/kPa)
    // Kappa - Isentropic Exponent
    // A - Helmholtz energy (J/mol)

    doubleW[] a0 = new doubleW[2 + 1];
    for (int i = 0; i < 3; i++) {
      a0[i] = new doubleW(0.0d);
    }
    doubleW[][] ar = new doubleW[3 + 1][3 + 1];

    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 4; j++) {
        ar[i][j] = new doubleW(0.0d);
      }
    }

    double R;
    double RT;
    // Calculate molar mass

    // Calculate the ideal gas Helmholtz energy, and its first and second
    // derivatives with respect to temperature.
    Alpha0Leachman(T, D, a0);

    // Calculate the real gas Helmholtz energy, and its derivatives with respect to
    // temperature and/or density.
    AlpharLeachman(1, 0, T, D, ar);

    R = R_L;
    RT = R * T;
    Z.val = 1 + ar[0][1].val;
    P.val = D * RT * Z.val;
    dPdD.val = RT * (1 + 2 * ar[0][1].val + ar[0][2].val);
    dPdT.val = D * R * (1 + ar[0][1].val - ar[1][1].val);

    d2PdTD.val = R * (1 + 2 * ar[0][1].val + ar[0][2].val - 2 * ar[1][1].val - ar[1][2].val);
    A.val = RT * (a0[0].val + ar[0][0].val);
    G.val = RT * (1 + ar[0][1].val + a0[0].val + ar[0][0].val);
    U.val = RT * (a0[1].val + ar[1][0].val);
    H.val = RT * (1 + ar[0][1].val + a0[1].val + ar[1][0].val);
    S.val = R * (a0[1].val + ar[1][0].val - a0[0].val - ar[0][0].val);
    Cv.val = -R * (a0[2].val + ar[2][0].val);
    if (D > epsilon) {
      Cp.val = Cv.val + T * (dPdT.val / D) * (dPdT.val / D) / dPdD.val;
      d2PdD2.val = RT * (2 * ar[0][1].val + 4 * ar[0][2].val + ar[0][3].val) / D;
      JT.val = (T / D * dPdT.val / dPdD.val - 1) / Cp.val / D; // '=(dB/dT*T-B)/Cp for an
                                                               // ideal gas, but dB/dT is
                                                               // not known
    } else {
      Cp.val = Cv.val + R;
      d2PdD2.val = 0;
      JT.val = 1E+20;
    }
    W.val = 1000 * Cp.val / Cv.val * dPdD.val / M_L;
    if (W.val < 0) {
      W.val = 0;
    }
    W.val = Math.sqrt(W.val);
    Kappa.val = Math.pow(W.val, 2) * M_L / (RT * 1000 * Z.val);
  }

  /**
   * @param Tcx temperature in Kelvin
   * @param Dcx density
   */
  void PseudoCriticalPointLeachman(doubleW Tcx, doubleW Dcx) {
    // Use the pre-initialized class-level values for Tc and Dc
    if (Tc == 0 || Dc == 0) {
      throw new IllegalStateException(
          "Critical point parameters not set. Please call SetupLeachman() first.");
    }

    Tcx.val = Tc; // Assign the pre-initialized critical temperature
    Dcx.val = Dc; // Assign the pre-initialized critical density

    // Optionally calculate Vcx if needed
    double Vcx = 1 / Dcx.val;
  }

  // The following routine must be called once before any other routine.
  /**
   * <p>
   * SetupGERG.
   * </p>
   *
   * @param hydrogenType String
   */
  public void SetupLeachman(String hydrogenType) {
    // Initialize all the constants and parameters in the GERG-2008 model.
    // Some values are modified for calculations that do not depend on T, D, and x in order to
    // speed up the program.
    R_L = 8.31451;
    M_L = 2.01588;

    // Define the number of terms in the Helmholtz energy expressions
    switch (hydrogenType.toLowerCase()) {
      case "normal":
        // System.out.println("Hydrogen type used: Normal.");

        a0k = new double[] {-1.4579856475, 1.888076782, 1.616, -0.4117, -0.792, 0.758, 1.217};
        b0k = new double[] {0, 0, -16.0205159149, -22.6580178006, -60.0090511389, -74.9434303817,
            -206.9392065168};
        K = 7;
        N_i = new double[] {-6.93643, 0.01, 2.1101, 4.52059, 0.732564, -1.34086, 0.130985,
            -0.777414, 0.351944, -0.0211716, 0.0226312, 0.032187, -0.0231752, 0.0557346};
        t_i = new double[] {0.6844, 1, 0.989, 0.489, 0.803, 1.1444, 1.409, 1.754, 1.311, 4.187,
            5.646, 0.791, 7.249, 2.986};
        d_i = new double[] {1, 4, 1, 1, 2, 2, 3, 1, 3, 2, 1, 3, 1, 1};
        p_i = new double[] {0, 0, 0, 0, 0, 0, 0, 1, 1};
        phi_i = new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, -1.685, -0.489, -0.103, -2.506, -1.607};
        beta_i =
            new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, -0.171, -0.2245, -0.1304, -0.2785, -0.3967};
        gamma_i = new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0.7164, 1.3444, 1.4517, 0.7204, 1.5445};
        D_i = new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 1.506, 0.156, 1.736, 0.67, 1.662};

        Tc = 33.145;
        Dc = 15.508;
        break;

      case "para":
        // System.out.println("Hydrogen type used: Para.");

        a0k = new double[] {-1.4485891134, 1.884521239, 4.30256, 13.0289, -47.7365, 50.0013,
            -18.6261, 0.993973, 0.536078};
        b0k = new double[] {0, 0, -15.1496751472, -25.0925982148, -29.4735563787, -35.4059141417,
            -40.724998482, -163.7925799988, -309.2173173842};
        K = 9;
        N_i = new double[] {-7.33375, 0.01, 2.60375, 4.66279, 0.68239, -1.47078, 0.135801, -1.05327,
            0.328239, -0.057783, 0.044974, 0.070346, -0.040176, 0.11951};
        t_i = new double[] {0.6855, 1, 1, 0.489, 0.774, 1.133, 1.386, 1.619, 1.162, 3.96, 5.276,
            0.99, 6.791, 3.19};
        d_i = new double[] {1, 4, 1, 1, 2, 2, 3, 1, 3, 2, 1, 3, 1, 1};
        p_i = new double[] {0, 0, 0, 0, 0, 0, 0, 1, 1};
        phi_i =
            new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, -1.7437, -0.5516, -0.0634, -2.1341, -1.777};
        beta_i =
            new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, -0.194, -0.2019, -0.0301, -0.2383, -0.3253};
        gamma_i = new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0.8048, 1.5248, 0.6648, 0.6832, 1.493};
        D_i = new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 1.5487, 0.1785, 1.28, 0.6319, 1.7104};

        Tc = 32.938;
        Dc = 15.538;
        break;

      case "ortho":
        // System.out.println("Hydrogen type used: Ortho.");

        a0k = new double[] {-1.4675442336, 1.8845068862, 2.54151, -2.3661, 1.00365, 1.22447};
        b0k = new double[] {0, 0, -25.7676098736, -43.4677904877, -66.0445514750, -209.7531607465};
        K = 6;
        N_i = new double[] {-6.83148, 0.01, 2.11505, 4.38353, 0.211292, -1.00939, 0.142086,
            -0.87696, 0.804927, -0.710775, 0.0639688, 0.0710858, -0.087654, 0.647088};
        t_i = new double[] {0.7333, 1, 1.1372, 0.5136, 0.5638, 1.6248, 1.829, 2.404, 2.105, 4.1,
            7.658, 1.259, 7.589, 3.946};
        d_i = new double[] {1, 4, 1, 1, 2, 2, 3, 1, 3, 2, 1, 3, 1, 1};
        p_i = new double[] {0, 0, 0, 0, 0, 0, 0, 1, 1};
        phi_i = new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, -1.169, -0.894, -0.04, -2.072, -1.306};
        beta_i =
            new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, -0.4555, -0.4046, -0.0869, -0.4415, -0.5743};
        gamma_i = new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 1.5444, 0.6627, 0.763, 0.6587, 1.4327};
        D_i = new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0.6366, 0.3876, 0.9437, 0.3976, 0.9626};

        Tc = 33.22;
        Dc = 15.445;
        break;

      default:
        throw new IllegalArgumentException("Invalid hydrogen type: " + hydrogenType);
    }
  }

  /**
   * Initialize the constants and parameters for the default hydrogen type (normal) using the
   * Leachman method.
   */
  public void SetupLeachman() {
    System.out.println("No hydrogen type specified. Using default type: 'Normal'.");
    SetupLeachman("normal");
  }

  /*
   * //a0 parameters a0k_normal = new double[] {-1.4579856475, 1.888076782, 1.616, -0.4117, -0.792,
   * 0.758, 1.217}; b0k_normal = new double[] {0, 0, -16.0205159149, -22.6580178006, -60.0090511389,
   * -74.9434303817, -206.9392065168};
   *
   * a0k_para = new double[] {-1.4485891134, 1.884521239, 4.30256, 13.0289, -47.7365, 50.0013,
   * -18.6261, 0.993973, 0.536078}; b0k_para = new double[] {0, 0, -15.1496751472, -25.0925982148,
   * -29.4735563787, -35.4059141417, -40.724998482, -163.7925799988, -309.2173173842};
   *
   *
   * a0k_ortho = new double[] {-1.4675442336, 1.8845068862, 2.54151, -2.3661, 1.00365, 1.22447};
   * b0k_ortho = new double[] {0, 0, -25.7676098736, -43.4677904877, -66.0445514750,
   * -209.7531607465};
   *
   *
   * //ar paramaters // Initialize normal hydrogen parameters N_i_normal = new double[] {-6.93643,
   * 0.01, 2.1101, 4.52059, 0.732564, -1.34086, 0.130985, -0.777414, 0.351944, -0.0211716,
   * 0.0226312, 0.032187, -0.0231752, 0.0557346}; t_i_normal = new double[] {0.6844, 1, 0.989,
   * 0.489, 0.803, 1.1444, 1.409, 1.754, 1.311, 4.187, 5.646, 0.791, 7.249, 2.986}; d_i_normal = new
   * double[] {1, 4, 1, 1, 2, 2, 3, 1, 3, 2, 1, 3, 1, 1}; p_i_normal = new double[] {0, 0, 0, 0, 0,
   * 0, 0, 1, 1}; phi_i_normal = new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, -1.685, -0.489, -0.103,
   * -2.506, -1.607}; beta_i_normal = new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, -0.171, -0.2245,
   * -0.1304, -0.2785, -0.3967}; gamma_i_normal = new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0.7164,
   * 1.3444, 1.4517, 0.7204, 1.5445}; D_i_normal = new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 1.506,
   * 0.156, 1.736, 0.67, 1.662};
   *
   * // Initialize para hydrogen parameters N_i_para = new double[] {-7.33375, 0.01, 2.60375,
   * 4.66279, 0.68239, -1.47078, 0.135801, -1.05327, 0.328239, -0.057783, 0.044974, 0.070346,
   * -0.040176, 0.11951}; t_i_para = new double[] {0.6855, 1, 1, 0.489, 0.774, 1.133, 1.386, 1.619,
   * 1.162, 3.96, 5.276, 0.99, 6.791, 3.19}; d_i_para = new double[] {1, 4, 1, 1, 2, 2, 3, 1, 3, 2,
   * 1, 3, 1, 1}; p_i_para = new double[] {0, 0, 0, 0, 0, 0, 0, 1, 1}; phi_i_para = new double[] {0,
   * 0, 0, 0, 0, 0, 0, 0, 0, -1.7437, -0.5516, -0.0634, -2.1341, -1.777}; beta_i_para = new double[]
   * {0, 0, 0, 0, 0, 0, 0, 0, 0, -0.194, -0.2019, -0.0301, -0.2383, -0.3253}; gamma_i_para = new
   * double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0.8048, 1.5248, 0.6648, 0.6832, 1.493}; D_i_para = new
   * double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 1.5487, 0.1785, 1.28, 0.6319, 1.7104};
   *
   * // Initialize ortho hydrogen parameters N_i_ortho = new double[] {-6.83148, 0.01, 2.11505,
   * 4.38353, 0.211292, -1.00939, 0.142086, -0.87696, 0.804927, -0.710775, 0.0639688, 0.0710858,
   * -0.087654, 0.647088}; t_i_ortho = new double[] {0.7333, 1, 1.1372, 0.5136, 0.5638, 1.6248,
   * 1.829, 2.404, 2.105, 4.1, 7.658, 1.259, 7.589, 3.946}; d_i_ortho = new double[] {1, 4, 1, 1, 2,
   * 2, 3, 1, 3, 2, 1, 3, 1, 1}; p_i_ortho = new double[] {0, 0, 0, 0, 0, 0, 0, 1, 1}; phi_i_ortho =
   * new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, -1.169, -0.894, -0.04, -2.072, -1.306}; beta_i_ortho =
   * new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, -0.4555, -0.4046, -0.0869, -0.4415, -0.5743};
   * gamma_i_ortho = new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 1.5444, 0.6627, 0.763, 0.6587,
   * 1.4327}; D_i_ortho = new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0.6366, 0.3876, 0.9437, 0.3976,
   * 0.9626};
   */

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    Leachman test = new Leachman();
    test.SetupLeachman();

    double T = 500;
    doubleW D = new doubleW(30);
    doubleW P = new doubleW(500.0d);
    intW ierr = new intW(0);
    // doubleW M_L = new doubleW(0.0d);
    doubleW Z = new doubleW(0.0d);
    int iFlag = 0;
    StringW herr = new StringW("");

    // System.out.println("mol mass " + Mm.val);

    // test.PressureLeachman(T, D.val, P, Z);

    // System.out.println("pressure " + P.val);
    // System.out.println("Z " + Z.val);

    test.DensityLeachman(iFlag, T, P.val, D, ierr, herr);
    System.out.println("density " + D.val);

    doubleW dPdD = new doubleW(0.0d);
    doubleW d2PdD2 = new doubleW(0.0d);
    doubleW d2PdTD = new doubleW(0.0d);
    doubleW dPdT = new doubleW(0.0d);
    doubleW U = new doubleW(0.0d);
    doubleW H = new doubleW(0.0d);
    doubleW S = new doubleW(0.0d);
    doubleW A = new doubleW(0.0d);
    doubleW P2 = new doubleW(0.0d);
    doubleW Cv = new doubleW(0.0d);

    doubleW Cp = new doubleW(0.0d);
    doubleW W = new doubleW(0.0d);
    doubleW G = new doubleW(0.0d);
    doubleW JT = new doubleW(0.0d);
    doubleW Kappa = new doubleW(0.0d);
    doubleW PP = new doubleW(0.0d);
    test.propertiesLeachman(T, D.val, P, Z, dPdD, d2PdD2, d2PdTD, dPdT, U, H, S, Cv, Cp, W, G, JT,
        Kappa, A);

    /*
     * // test.PressureGERG(400, 12.798286, x); String herr = ""; test.DensityGERG(0, T, P, x, ierr,
     * herr); double pres = test.P; double molarmass = test.Mm;
     *
     * // double dPdD=0.0, dPdD2=0.0, d2PdTD=0.0, dPdT=0.0, U=0.0, H=0.0, S=0.0, // Cv=0.0, Cp=0.0,
     * W=0.0, G=0.0, JT=0.0, Kappa=0.0, A=0.0;
     *
     * // void DensityGERG(const int iFlag, const double T, const double P, const //
     * std::vector<double> &x, double &D, int &ierr, std::string &herr) // test.DensityGERG(0, T, P,
     * x, ierr, herr);
     *
     * // Sub PropertiesGERG(T, D, x, P, Z, dPdD, dPdD2, d2PdTD, dPdT, U, H, S, Cv, Cp, // W, G, JT,
     * Kappa) // test.PropertiesGERG(T, test.D, x);
     */
    System.out.println("Outputs-----\n");
    System.out.println("Outputs-----\n");
    System.out.println("Molar mass [g/mol]:                 " + test.M_L);
    System.out.println("Molar density [mol/l]:              " + D.val);
    System.out.println("Pressure [kPa]:                     " + P.val);
    System.out.println("Compressibility factor:             " + Z.val);
    System.out.println("d(P)/d(rho) [kPa/(mol/l)]:          " + dPdD.val);
    System.out.println("d^2(P)/d(rho)^2 [kPa/(mol/l)^2]:    " + d2PdD2.val);
    System.out.println("d(P)/d(T) [kPa/K]:                  " + dPdT.val);
    System.out.println("Energy [J/mol]:                     " + U.val);
    System.out.println("Enthalpy [J/mol]:                   " + H.val);
    System.out.println("Entropy [J/mol-K]:                  " + S.val);
    System.out.println("Isochoric heat capacity [J/mol-K]:  " + Cv.val);
    System.out.println("Isobaric heat capacity [J/mol-K]:   " + Cp.val);
    System.out.println("Speed of sound [m/s]:               " + W.val);
    System.out.println("Gibbs energy [J/mol]:               " + G.val);
    System.out.println("Joule-Thomson coefficient [K/kPa]:  " + JT.val);
    System.out.println("Isentropic exponent:                " + Kappa.val);
  }
}

