package neqsim.thermo.util.gerg;

import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * GERG2008 class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class EOSCGModel {
  // Variables containing the common parameters in the GERG-2008 equations
  double RGERG;
  int NcGERG = 21;
  int MaxFlds = 21;
  int MaxMdl = 10;
  int MaxTrmM = 12;
  int MaxTrmP = 24;
  double epsilon = 1e-15;
  int[][] intcoik = new int[MaxFlds + 1][MaxTrmP + 1];
  int[][] coik = new int[MaxFlds + 1][MaxTrmP + 1];
  int[][] doik = new int[MaxFlds + 1][MaxTrmP + 1];
  int[][] dijk = new int[MaxMdl + 1][MaxTrmM + 1];

  double Drold;
  double Trold;
  double Told;
  double Trold2;
  double[] xold = new double[MaxFlds + 1];
  int[][] mNumb = new int[MaxFlds + 1][MaxFlds + 1];
  int[] kpol = new int[MaxFlds + 1];
  int[] kexp = new int[MaxFlds + 1];
  int[] kpolij = new int[MaxMdl + 1];
  int[] kexpij = new int[MaxMdl + 1];

  double[] Dc = new double[MaxFlds + 1];
  double[] Tc = new double[MaxFlds + 1];
  double[] MMiGERG = new double[MaxFlds + 1];
  double[] Vc3 = new double[MaxFlds + 1];
  double[] Tc2 = new double[MaxFlds + 1];

  double[][] noik = new double[MaxFlds + 1][MaxTrmP + 1];
  double[][] toik = new double[MaxFlds + 1][MaxTrmP + 1];

  double[][] cijk = new double[MaxMdl + 1][MaxTrmM + 1];

  double[][] eijk = new double[MaxMdl + 1][MaxTrmM + 1];
  double[][] gijk = new double[MaxMdl + 1][MaxTrmM + 1];
  double[][] nijk = new double[MaxMdl + 1][MaxTrmM + 1];
  double[][] tijk = new double[MaxMdl + 1][MaxTrmM + 1];

  double[][] btij = new double[MaxFlds + 1][MaxFlds + 1];
  double[][] bvij = new double[MaxFlds + 1][MaxFlds + 1];
  double[][] gtij = new double[MaxFlds + 1][MaxFlds + 1];
  double[][] gvij = new double[MaxFlds + 1][MaxFlds + 1];

  double[][] fij = new double[MaxFlds + 1][MaxFlds + 1];
  double[][] th0i = new double[MaxFlds + 1][7 + 1];
  double[][] n0i = new double[MaxFlds + 1][7 + 1];

  double[][] taup = new double[MaxFlds + 1][MaxTrmP + 1];
  double[][] taupijk = new double[MaxFlds + 1][MaxTrmM + 1];
  double dPdDsave;

  /**
   * <p>
   * MolarMassGERG.
   * </p>
   *
   * @param x an array of type double
   * @param Mm a {@link org.netlib.util.doubleW} object
   */
  public void MolarMassEOSCG(double[] x, doubleW Mm) {
    // Sub MolarMassEOSCG(x, Mm)

    // Calculate molar mass of the mixture with the compositions contained in the
    // x() input array

    // Inputs:
    // x() - Composition (mole fraction)
    // Do not send mole percents or mass fractions in the x() array, otherwise the
    // output will be incorrect.
    // The sum of the compositions in the x() array must be equal to one.
    // The order of the fluids in this array is given at the top of this module.

    // Outputs:
    // Mm - Molar mass (g/mol)

    Mm.val = 0;
    for (int i = 1; i <= NcGERG; ++i) {
      Mm.val += x[i] * MMiGERG[i];
    }
  }

  /**
   * <p>
   * PressureGERG.
   * </p>
   *
   * @param T a double
   * @param D a double
   * @param x an array of type double
   * @param P a {@link org.netlib.util.doubleW} object
   * @param Z a {@link org.netlib.util.doubleW} object
   */
  public void PressureEOSCG(double T, double D, double[] x, doubleW P, doubleW Z) {
    // Sub PressureEOSCG(T, D, x, P, Z)

    // Calculate pressure as a function of temperature and density. The derivative
    // d(P)/d(D) is also calculated
    // for use in the iterative DensityGERG subroutine (and is only returned as a
    // common variable).

    // Inputs:
    // T - Temperature (K)
    // D - Density (mol/l)
    // x() - Composition (mole fraction)
    // Do not send mole percents or mass fractions in the x() array, otherwise the
    // output will be incorrect.
    // The sum of the compositions in the x() array must be equal to one.

    // Outputs:
    // P - Pressure (kPa)
    // Z - Compressibility factor
    // dPdDsave - d(P)/d(D) [kPa/(mol/l)] (at constant temperature)
    // - This variable is cached in the common variables for use in the iterative
    // density solver, but not returned as an argument.

    doubleW[][] ar = new doubleW[4][4];
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 4; j++) {
        ar[i][j] = new doubleW(0.0d);
      }
    }
    AlpharEOSCG(0, 0, T, D, x, ar);

    Z.val = 1 + ar[0][1].val;
    P.val = D * RGERG * T * Z.val;
    dPdDsave = RGERG * T * (1 + 2 * ar[0][1].val + ar[0][2].val);
  }

  /**
   * <p>
   * DensityGERG.
   * </p>
   *
   * @param iFlag a int
   * @param T a double
   * @param P a double
   * @param x an array of type double
   * @param D a {@link org.netlib.util.doubleW} object
   * @param ierr a {@link org.netlib.util.intW} object
   * @param herr a {@link org.netlib.util.StringW} object
   */
  public void DensityEOSCG(int iFlag, double T, double P, double[] x, doubleW D, intW ierr,
      StringW herr) {
    // Sub DensityEOSCG(iFlag, T, P, x, D, ierr, herr)

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
    PseudoCriticalPointGERG(x, Tcx, Dcx);

    if (D.val > -epsilon) {
      D.val = P / RGERG / T; // Ideal gas estimate for vapor phase
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
          herr.val = "Calculation failed to converge in GERG method, ideal gas density returned.";
          D.val = P / RGERG / T;
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
      PressureEOSCG(T, D.val, x, P2, Z);
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
              PropertiesEOSCG(T, D.val, x, PP, Z, dPdD, d2PdD2, d2PdTD, dPdT, U, H, S, Cv, Cp, W, G,
                  JT, Kappa, A);
              if ((PP.val <= 0 || dPdD.val <= 0 || d2PdTD.val <= 0)
                  || (Cv.val <= 0 || Cp.val <= 0 || W.val <= 0)) {
                // Iteration failed (above loop did find a solution or checks made
                // below
                // indicate possible 2-phase state)
                ierr.val = 1;
                herr.val =
                    "Calculation failed to converge in GERG method, ideal gas density returned.";
                D.val = P / RGERG / T;
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
    herr.val = "Calculation failed to converge in GERG method, ideal gas density returned.";
    D.val = P / RGERG / T;
  }

  /**
   * <p>
   * PropertiesGERG.
   * </p>
   *
   * @param T a double
   * @param D a double
   * @param x an array of type double
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
  public void PropertiesEOSCG(double T, double D, double[] x, doubleW P, doubleW Z, doubleW dPdD,
      doubleW d2PdD2, doubleW d2PdTD, doubleW dPdT, doubleW U, doubleW H, doubleW S, doubleW Cv,
      doubleW Cp, doubleW W, doubleW G, doubleW JT, doubleW Kappa, doubleW A) {
    // Sub PropertiesEOSCG(T, D, x, P, Z, dPdD, d2PdD2, d2PdTD, dPdT, U, H, S, Cv,
    // Cp, W, G, JT, Kappa, A)

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

    doubleW Mm = new doubleW(0.0d); // , R, RT;
    double R;
    double RT;
    // Calculate molar mass
    MolarMassEOSCG(x, Mm);

    // Calculate the ideal gas Helmholtz energy, and its first and second
    // derivatives with respect to temperature.
    Alpha0EOSCG(T, D, x, a0);

    // Calculate the real gas Helmholtz energy, and its derivatives with respect to
    // temperature and/or density.
    AlpharEOSCG(1, 0, T, D, x, ar);

    R = RGERG;
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
    W.val = 1000 * Cp.val / Cv.val * dPdD.val / Mm.val;
    if (W.val < 0) {
      W.val = 0;
    }
    W.val = Math.sqrt(W.val);
    Kappa.val = Math.pow(W.val, 2) * Mm.val / (RT * 1000 * Z.val);
  }

  /**
   * @param x ...
   * @param Tr ...
   * @param Dr ...
   */
  // The following routines are low-level routines that should not be called outside of this code.
  void ReducingParametersGERG(double[] x, doubleW Tr, doubleW Dr) {
    // Private Sub ReducingParametersGERG(x, Tr, Dr)

    // Calculate reducing variables. Only need to call this if the composition has
    // changed.

    // Inputs:
    // x() - Composition (mole fraction)

    // Outputs:
    // Tr - Reducing temperature (K)
    // Dr - Reducing density (mol/l)

    double Vr;
    double xij;
    double F;
    int icheck;

    // Check to see if a component fraction has changed. If x is the same as the previous call,
    // then exit.
    icheck = 0;
    for (int i = 1; i <= NcGERG; ++i) {
      if (Math.abs(x[i] - xold[i]) > 0.0000001) {
        icheck = 1;
      }
      xold[i] = x[i];
    }
    if (icheck == 0) {
      Dr.val = Drold;
      Tr.val = Trold;
      return;
    }
    Told = 0;
    Trold2 = 0;

    // Calculate reducing variables for T and D
    Dr.val = 0;
    Vr = 0;
    Tr.val = 0;
    for (int i = 1; i <= NcGERG; ++i) {
      if (x[i] > epsilon) {
        F = 1;
        for (int j = i; j <= NcGERG; ++j) {
          if (x[j] > epsilon) {
            xij = F * (x[i] * x[j]) * (x[i] + x[j]);
            Vr = Vr + xij * gvij[i][j] / (bvij[i][j] * x[i] + x[j]);
            Tr.val = Tr.val + xij * gtij[i][j] / (btij[i][j] * x[i] + x[j]);
            F = 2;
          }
        }
      }
    }
    if (Vr > epsilon) {
      Dr.val = 1 / Vr;
    }
    Drold = Dr.val;
    Trold = Tr.val;
  }

  /**
   * @param T ...
   * @param D ...
   * @param x ...
   * @param a0 ...
   */
  void Alpha0EOSCG(double T, double D, double[] x, doubleW[] a0) {
    // Private Sub Alpha0EOSCG(T, D, x, a0)

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

    double LogT;
    double LogD;
    double LogHyp;
    double th0T;
    double LogxD;
    double SumHyp0;
    double SumHyp1;
    double SumHyp2;
    double em;

    double ep;
    double hcn;
    double hsn;
    a0[0].val = 0;
    a0[1].val = 0;
    a0[2].val = 0;
    if (D > epsilon) {
      LogD = Math.log(D);
    } else {
      LogD = Math.log(epsilon);
    }
    LogT = Math.log(T);
    for (int i = 1; i <= NcGERG; ++i) {
      if (x[i] > epsilon) {
        LogxD = LogD + Math.log(x[i]);
        SumHyp0 = 0;
        SumHyp1 = 0;
        SumHyp2 = 0;
        for (int j = 4; j <= 7; ++j) {
          if (th0i[i][j] > epsilon) {
            th0T = th0i[i][j] / T;
            ep = Math.exp(th0T);
            em = 1 / ep;
            hsn = (ep - em) / 2;
            hcn = (ep + em) / 2;
            if (j == 4 || j == 6) {
              LogHyp = Math.log(Math.abs(hsn));
              SumHyp0 = SumHyp0 + n0i[i][j] * LogHyp;
              SumHyp1 = SumHyp1 + n0i[i][j] * th0T * hcn / hsn;
              SumHyp2 = SumHyp2 + n0i[i][j] * (th0T / hsn) * (th0T / hsn);
            } else {
              LogHyp = Math.log(Math.abs(hcn));
              SumHyp0 = SumHyp0 - n0i[i][j] * LogHyp;
              SumHyp1 = SumHyp1 - n0i[i][j] * th0T * hsn / hcn;
              SumHyp2 = SumHyp2 + n0i[i][j] * (th0T / hcn) * (th0T / hcn);
            }
          }
        }
        a0[0].val += +x[i] * (LogxD + n0i[i][1] + n0i[i][2] / T - n0i[i][3] * LogT + SumHyp0);
        a0[1].val += +x[i] * (n0i[i][3] + n0i[i][2] / T + SumHyp1);
        a0[2].val += -x[i] * (n0i[i][3] + SumHyp2);
      }
    }
  }

  /**
   * @param itau ...
   * @param idelta ...
   * @param T ...
   * @param D ....
   * @param x ....
   * @param ar ...
   */
  void AlpharEOSCG(int itau, int idelta, double T, double D, double[] x, doubleW[][] ar) {
    // Private Sub AlpharEOSCG(itau, idelta, T, D, x, ar)

    // Calculate dimensionless residual Helmholtz energy and its derivatives with
    // respect to tau and delta.

    // Inputs:
    // itau - Set this to 1 to calculate "ar" derivatives with respect to tau [i.e.,
    // ar(1,0), ar(1,1), and ar(2,0)], otherwise set it to 0.
    // idelta - Currently not used, but kept as an input for future use in specifing
    // the highest density derivative needed.
    // T - Temperature (K)
    // D - Density (mol/l)
    // x() - Composition (mole fraction)

    // Outputs:
    // ar(0,0) - Residual Helmholtz energy (dimensionless, =a/RT)
    // ar(0,1) - delta*partial (ar)/partial(delta)
    // ar(0,2) - delta^2*partial^2(ar)/partial(delta)^2
    // ar(0,3) - delta^3*partial^3(ar)/partial(delta)^3
    // ar(1,0) - tau*partial (ar)/partial(tau)
    // ar(1,1) - tau*delta*partial^2(ar)/partial(tau)/partial(delta)
    // ar(2,0) - tau^2*partial^2(ar)/partial(tau)^2

    int mn;
    doubleW Tr = new doubleW(0.0d);
    doubleW Dr = new doubleW(0.0d);
    double del;
    double tau;
    double lntau;
    double ex;
    double ex2;
    double ex3;
    double cij0;
    double eij0;
    double ndt;
    double ndtd;
    double ndtt;
    double xijf;
    double[] delp = new double[7 + 1];
    double[] Expd = new double[7 + 1];

    for (int i = 0; i <= 3; ++i) {
      for (int j = 0; j <= 3; ++j) {
        ar[i][j].val = 0;
      }
    }

    // Set up del, tau, log(tau), and the first 7 calculations for del^i
    ReducingParametersGERG(x, Tr, Dr);
    del = D / Dr.val;
    tau = Tr.val / T;
    lntau = Math.log(tau);
    delp[1] = del;
    Expd[1] = Math.exp(-delp[1]);
    for (int i = 2; i <= 7; ++i) {
      delp[i] = delp[i - 1] * del;
      Expd[i] = Math.exp(-delp[i]);
    }

    // If temperature has changed, calculate temperature dependent parts
    if (Math.abs(T - Told) > 0.0000001 || Math.abs(Tr.val - Trold2) > 0.0000001) {
      tTermsGERG(lntau, x);
    }
    Told = T;
    Trold2 = Tr.val;

    // Calculate pure fluid contributions
    for (int i = 1; i <= NcGERG; ++i) {
      if (x[i] > epsilon) {
        for (int k = 1; k <= kpol[i]; ++k) {
          ndt = x[i] * delp[doik[i][k]] * taup[i][k];
          ndtd = ndt * doik[i][k];
          ar[0][1].val += ndtd;
          ar[0][2].val += ndtd * (doik[i][k] - 1);
          if (itau > 0) {
            ndtt = ndt * toik[i][k];
            ar[0][0].val += ndt;
            ar[1][0].val += ndtt;
            ar[2][0].val += ndtt * (toik[i][k] - 1);
            ar[1][1].val += ndtt * doik[i][k];
            ar[1][2].val += ndtt * doik[i][k] * (doik[i][k] - 1);
            ar[0][3].val += ndtd * (doik[i][k] - 1) * (doik[i][k] - 2);
          }
        }
        for (int k = 1 + kpol[i]; k <= kpol[i] + kexp[i]; ++k) {
          ndt = x[i] * delp[doik[i][k]] * taup[i][k] * Expd[coik[i][k]];
          ex = coik[i][k] * delp[coik[i][k]];
          ex2 = doik[i][k] - ex;
          ex3 = ex2 * (ex2 - 1);
          ar[0][1].val += ndt * ex2;
          ar[0][2].val += ndt * (ex3 - coik[i][k] * ex);
          if (itau > 0) {
            ndtt = ndt * toik[i][k];
            ar[0][0].val += ndt;
            ar[1][0].val += ndtt;
            ar[2][0].val += ndtt * (toik[i][k] - 1);
            ar[1][1].val += ndtt * ex2;
            ar[1][2].val += ndtt * (ex3 - coik[i][k] * ex);
            ar[0][3].val += ndt * (ex3 * (ex2 - 2) - ex * (3 * ex2 - 3 + coik[i][k]) * coik[i][k]);
          }
        }
      }
    }

    // Calculate mixture contributions
    for (int i = 1; i <= NcGERG - 1; ++i) {
      if (x[i] > epsilon) {
        for (int j = i + 1; j <= NcGERG; ++j) {
          if (x[j] > epsilon) {
            mn = mNumb[i][j];
            if (mn >= 0) {
              xijf = x[i] * x[j] * fij[i][j];
              for (int k = 1; k <= kpolij[mn]; ++k) {
                ndt = xijf * delp[dijk[mn][k]] * taupijk[mn][k];
                ndtd = ndt * dijk[mn][k];
                ar[0][1].val += ndtd;
                ar[0][2].val += ndtd * (dijk[mn][k] - 1);
                if (itau > 0) {
                  ndtt = ndt * tijk[mn][k];
                  ar[0][0].val += ndt;
                  ar[1][0].val += ndtt;
                  ar[2][0].val += ndtt * (tijk[mn][k] - 1);
                  ar[1][1].val += ndtt * dijk[mn][k];
                  ar[1][2].val += ndtt * dijk[mn][k] * (dijk[mn][k] - 1);
                  ar[0][3].val += ndtd * (dijk[mn][k] - 1) * (dijk[mn][k] - 2);
                }
              }
              for (int k = 1 + kpolij[mn]; k <= kpolij[mn] + kexpij[mn]; ++k) {
                cij0 = cijk[mn][k] * delp[2];
                eij0 = eijk[mn][k] * del;
                ndt = xijf * nijk[mn][k] * delp[dijk[mn][k]]
                    * Math.exp(cij0 + eij0 + gijk[mn][k] + tijk[mn][k] * lntau);
                ex = dijk[mn][k] + 2 * cij0 + eij0;
                ex2 = (ex * ex - dijk[mn][k] + 2 * cij0);
                ar[0][1].val += ndt * ex;
                ar[0][2].val += ndt * ex2;
                if (itau > 0) {
                  ndtt = ndt * tijk[mn][k];
                  ar[0][0].val += ndt;
                  ar[1][0].val += ndtt;
                  ar[2][0].val += ndtt * (tijk[mn][k] - 1);
                  ar[1][1].val += ndtt * ex;
                  ar[1][2].val += ndtt * ex2;
                  ar[0][3].val +=
                      ndt * (ex * (ex2 - 2 * (dijk[mn][k] - 2 * cij0)) + 2 * dijk[mn][k]);
                }
              }
            }
          }
        }
      }
    }
  }

  /**
   * @param lntau ...
   * @param x ....
   */
  void tTermsGERG(double lntau, double[] x) {
    // Private Sub tTermsGERG(lntau, x)

    // Calculate temperature dependent parts of the GERG-2008 equation of state

    int i;
    int mn;
    double[] taup0 = new double[12 + 1];

    i = 5; // Use propane to get exponents for short form of EOS
    for (int k = 1; k <= kpol[i] + kexp[i]; ++k) {
      taup0[k] = Math.exp(toik[i][k] * lntau);
    }
    for (i = 1; i <= NcGERG; ++i) {
      if (x[i] > epsilon) {
        if (i > 4 && i != 15 && i != 18 && i != 20) {
          for (int k = 1; k <= kpol[i] + kexp[i]; ++k) {
            taup[i][k] = noik[i][k] * taup0[k];
          }
        } else {
          for (int k = 1; k <= kpol[i] + kexp[i]; ++k) {
            taup[i][k] = noik[i][k] * Math.exp(toik[i][k] * lntau);
          }
        }
      }
    }

    for (i = 1; i <= NcGERG - 1; ++i) {
      if (x[i] > epsilon) {
        for (int j = i + 1; j <= NcGERG; ++j) {
          if (x[j] > epsilon) {
            mn = mNumb[i][j];
            if (mn >= 0) {
              for (int k = 1; k <= kpolij[mn]; ++k) {
                taupijk[mn][k] = nijk[mn][k] * Math.exp(tijk[mn][k] * lntau);
              }
            }
          }
        }
      }
    }
  }

  /**
   * @param x composition
   * @param Tcx temperature in Kelvin
   * @param Dcx density
   */
  void PseudoCriticalPointGERG(double[] x, doubleW Tcx, doubleW Dcx) {
    // PseudoCriticalPointGERG(x, Tcx, Dcx)

    // Calculate a pseudo critical point as the mole fraction average of the
    // critical temperatures and critical volumes

    double Vcx;
    Tcx.val = 0;
    Vcx = 0;
    Dcx.val = 0;
    for (int i = 1; i <= NcGERG; ++i) {
      Tcx.val = Tcx.val + x[i] * Tc[i];
      Vcx = Vcx + x[i] / Dc[i];
    }
    if (Vcx > epsilon) {
      Dcx.val = 1 / Vcx;
    }
  }

  // The following routine must be called once before any other routine.
  /**
   * <p>
   * SetupGERG.
   * </p>
   */
  public void SetupEOSCG() {
    // Initialize all the constants and parameters in the GERG-2008 model.
    // Some values are modified for calculations that do not depend on T, D, and x in order to
    // speed up the program.

    double o13;
    double Rs;
    double Rsr;
    double[][] bijk = new double[MaxMdl + 1][MaxTrmM + 1];
    double T0;

    double d0;
    // ThermodynamicConstantsInterface.R
    RGERG = 8.314472;
    Rs = 8.31451;
    Rsr = Rs / RGERG;
    o13 = 1.0 / 3.0;

    for (int i = 1; i <= MaxFlds; ++i) {
      xold[i] = 0;
    }
    Told = 0;

    // Molar masses [g/mol]
    MMiGERG[1] = 16.04246; // Methane
    MMiGERG[2] = 28.0134; // Nitrogen
    MMiGERG[3] = 44.0095; // Carbon dioxide
    MMiGERG[4] = 30.06904; // Ethane
    MMiGERG[5] = 44.09562; // Propane
    MMiGERG[6] = 58.1222; // Isobutane
    MMiGERG[7] = 58.1222; // n-Butane
    MMiGERG[8] = 72.14878; // Isopentane
    MMiGERG[9] = 72.14878; // n-Pentane
    MMiGERG[10] = 86.17536; // Hexane
    MMiGERG[11] = 100.20194; // Heptane
    MMiGERG[12] = 114.22852; // Octane
    MMiGERG[13] = 128.2551; // Nonane
    MMiGERG[14] = 142.28168; // Decane
    MMiGERG[15] = 2.01588; // Hydrogen
    MMiGERG[16] = 31.9988; // Oxygen
    MMiGERG[17] = 28.0101; // Carbon monoxide
    MMiGERG[18] = 18.01528; // Water
    MMiGERG[19] = 34.08088; // Hydrogen sulfide
    MMiGERG[20] = 4.002602; // Helium
    MMiGERG[21] = 39.948; // Argon

    // Number of polynomial and exponential terms
    for (int i = 1; i <= MaxFlds; ++i) {
      kpol[i] = 6;
      kexp[i] = 6;
    }
    kexp[1] = 18;
    kexp[2] = 18;
    kexp[4] = 18;
    kpol[3] = 4;
    kexp[3] = 18;
    kpol[15] = 5;
    kexp[15] = 9;
    kpol[18] = 7;
    kexp[18] = 9;
    kpol[20] = 4;
    kexp[20] = 8;
    kpolij[1] = 2;
    kexpij[1] = 10;
    kpolij[2] = 5;
    kexpij[2] = 4;
    kpolij[3] = 2;
    kexpij[3] = 7;
    kpolij[4] = 3;
    kexpij[4] = 3;
    kpolij[5] = 2;
    kexpij[5] = 4;
    kpolij[6] = 3;
    kexpij[6] = 3;
    kpolij[7] = 4;
    kexpij[7] = 0;
    kpolij[10] = 10;
    kexpij[10] = 0;

    // Critical densities [mol/l]
    Dc[1] = 10.139342719;
    Dc[2] = 11.1839;
    Dc[3] = 10.624978698;
    Dc[4] = 6.87085454;
    Dc[5] = 5.000043088;
    Dc[6] = 3.86014294;
    Dc[7] = 3.920016792;
    Dc[8] = 3.271;
    Dc[9] = 3.215577588;
    Dc[10] = 2.705877875;
    Dc[11] = 2.315324434;
    Dc[12] = 2.056404127;
    Dc[13] = 1.81;
    Dc[14] = 1.64;
    Dc[15] = 14.94;
    Dc[16] = 13.63;
    Dc[17] = 10.85;
    Dc[18] = 17.87371609;
    Dc[19] = 10.19;
    Dc[20] = 17.399;
    Dc[21] = 13.407429659;

    // Critical temperatures [K]
    Tc[1] = 190.564;
    Tc[2] = 126.192;
    Tc[3] = 304.1282;
    Tc[4] = 305.322;
    Tc[5] = 369.825;
    Tc[6] = 407.817;
    Tc[7] = 425.125;
    Tc[8] = 460.35;
    Tc[9] = 469.7;
    Tc[10] = 507.82;
    Tc[11] = 540.13;
    Tc[12] = 569.32;
    Tc[13] = 594.55;
    Tc[14] = 617.7;
    Tc[15] = 33.19;
    Tc[16] = 154.595;
    Tc[17] = 132.86;
    Tc[18] = 647.096;
    Tc[19] = 373.1;
    Tc[20] = 5.1953;
    Tc[21] = 150.687;

    // Exponents in pure fluid equations
    for (int i = 1; i <= MaxFlds; ++i) {
      Vc3[i] = 1 / Math.pow(Dc[i], o13) / 2;
      Tc2[i] = Math.sqrt(Tc[i]);
      coik[i][1] = 0;
      doik[i][1] = 1;
      toik[i][1] = 0.25;
      coik[i][2] = 0;
      doik[i][2] = 1;
      toik[i][2] = 1.125;
      coik[i][3] = 0;
      doik[i][3] = 1;
      toik[i][3] = 1.5;
      coik[i][4] = 0;
      doik[i][4] = 2;
      toik[i][4] = 1.375;
      coik[i][5] = 0;
      doik[i][5] = 3;
      toik[i][5] = 0.25;
      coik[i][6] = 0;
      doik[i][6] = 7;
      toik[i][6] = 0.875;
      coik[i][7] = 1;
      doik[i][7] = 2;
      toik[i][7] = 0.625;
      coik[i][8] = 1;
      doik[i][8] = 5;
      toik[i][8] = 1.75;
      coik[i][9] = 2;
      doik[i][9] = 1;
      toik[i][9] = 3.625;
      coik[i][10] = 2;
      doik[i][10] = 4;
      toik[i][10] = 3.625;
      coik[i][11] = 3;
      doik[i][11] = 3;
      toik[i][11] = 14.5;
      coik[i][12] = 3;
      doik[i][12] = 4;
      toik[i][12] = 12;
    }
    for (int i = 1; i <= 4; ++i) {
      if (i != 3) {
        coik[i][1] = 0;
        doik[i][1] = 1;
        toik[i][1] = 0.125;
        coik[i][2] = 0;
        doik[i][2] = 1;
        toik[i][2] = 1.125;
        coik[i][3] = 0;
        doik[i][3] = 2;
        toik[i][3] = 0.375;
        coik[i][4] = 0;
        doik[i][4] = 2;
        toik[i][4] = 1.125;
        coik[i][5] = 0;
        doik[i][5] = 4;
        toik[i][5] = 0.625;
        coik[i][6] = 0;
        doik[i][6] = 4;
        toik[i][6] = 1.5;
        coik[i][7] = 1;
        doik[i][7] = 1;
        toik[i][7] = 0.625;
        coik[i][8] = 1;
        doik[i][8] = 1;
        toik[i][8] = 2.625;
        coik[i][9] = 1;
        doik[i][9] = 1;
        toik[i][9] = 2.75;
        coik[i][10] = 1;
        doik[i][10] = 2;
        toik[i][10] = 2.125;
        coik[i][11] = 1;
        doik[i][11] = 3;
        toik[i][11] = 2;
        coik[i][12] = 1;
        doik[i][12] = 6;
        toik[i][12] = 1.75;
        coik[i][13] = 2;
        doik[i][13] = 2;
        toik[i][13] = 4.5;
        coik[i][14] = 2;
        doik[i][14] = 3;
        toik[i][14] = 4.75;
        coik[i][15] = 2;
        doik[i][15] = 3;
        toik[i][15] = 5;
        coik[i][16] = 2;
        doik[i][16] = 4;
        toik[i][16] = 4;
        coik[i][17] = 2;
        doik[i][17] = 4;
        toik[i][17] = 4.5;
        coik[i][18] = 3;
        doik[i][18] = 2;
        toik[i][18] = 7.5;
        coik[i][19] = 3;
        doik[i][19] = 3;
        toik[i][19] = 14;
        coik[i][20] = 3;
        doik[i][20] = 4;
        toik[i][20] = 11.5;
        coik[i][21] = 6;
        doik[i][21] = 5;
        toik[i][21] = 26;
        coik[i][22] = 6;
        doik[i][22] = 6;
        toik[i][22] = 28;
        coik[i][23] = 6;
        doik[i][23] = 6;
        toik[i][23] = 30;
        coik[i][24] = 6;
        doik[i][24] = 7;
        toik[i][24] = 16;
      }
    }

    // Coefficients of pure fluid equations
    // Methane
    noik[1][1] = 0.57335704239162;
    noik[1][2] = -1.676068752373;
    noik[1][3] = 0.23405291834916;
    noik[1][4] = -0.21947376343441;
    noik[1][5] = 0.016369201404128;
    noik[1][6] = 0.01500440638928;
    noik[1][7] = 0.098990489492918;
    noik[1][8] = 0.58382770929055;
    noik[1][9] = -0.7478686756039;
    noik[1][10] = 0.30033302857974;
    noik[1][11] = 0.20985543806568;
    noik[1][12] = -0.018590151133061;
    noik[1][13] = -0.15782558339049;
    noik[1][14] = 0.12716735220791;
    noik[1][15] = -0.032019743894346;
    noik[1][16] = -0.068049729364536;
    noik[1][17] = 0.024291412853736;
    noik[1][18] = 5.1440451639444E-03;
    noik[1][19] = -0.019084949733532;
    noik[1][20] = 5.5229677241291E-03;
    noik[1][21] = -4.4197392976085E-03;
    noik[1][22] = 0.040061416708429;
    noik[1][23] = -0.033752085907575;
    noik[1][24] = -2.5127658213357E-03;
    // Nitrogen
    noik[2][1] = 0.59889711801201;
    noik[2][2] = -1.6941557480731;
    noik[2][3] = 0.24579736191718;
    noik[2][4] = -0.23722456755175;
    noik[2][5] = 0.017954918715141;
    noik[2][6] = 0.014592875720215;
    noik[2][7] = 0.10008065936206;
    noik[2][8] = 0.73157115385532;
    noik[2][9] = -0.88372272336366;
    noik[2][10] = 0.31887660246708;
    noik[2][11] = 0.20766491728799;
    noik[2][12] = -0.019379315454158;
    noik[2][13] = -0.16936641554983;
    noik[2][14] = 0.13546846041701;
    noik[2][15] = -0.033066712095307;
    noik[2][16] = -0.060690817018557;
    noik[2][17] = 0.012797548292871;
    noik[2][18] = 5.8743664107299E-03;
    noik[2][19] = -0.018451951971969;
    noik[2][20] = 4.7226622042472E-03;
    noik[2][21] = -5.2024079680599E-03;
    noik[2][22] = 0.043563505956635;
    noik[2][23] = -0.036251690750939;
    noik[2][24] = -2.8974026866543E-03;
    // Ethane
    noik[4][1] = 0.63596780450714;
    noik[4][2] = -1.7377981785459;
    noik[4][3] = 0.28914060926272;
    noik[4][4] = -0.33714276845694;
    noik[4][5] = 0.022405964699561;
    noik[4][6] = 0.015715424886913;
    noik[4][7] = 0.11450634253745;
    noik[4][8] = 1.0612049379745;
    noik[4][9] = -1.2855224439423;
    noik[4][10] = 0.39414630777652;
    noik[4][11] = 0.31390924682041;
    noik[4][12] = -0.021592277117247;
    noik[4][13] = -0.21723666564905;
    noik[4][14] = -0.28999574439489;
    noik[4][15] = 0.42321173025732;
    noik[4][16] = 0.04643410025926;
    noik[4][17] = -0.13138398329741;
    noik[4][18] = 0.011492850364368;
    noik[4][19] = -0.033387688429909;
    noik[4][20] = 0.015183171583644;
    noik[4][21] = -4.7610805647657E-03;
    noik[4][22] = 0.046917166277885;
    noik[4][23] = -0.039401755804649;
    noik[4][24] = -3.2569956247611E-03;
    // Propane
    noik[5][1] = 1.0403973107358;
    noik[5][2] = -2.8318404081403;
    noik[5][3] = 0.84393809606294;
    noik[5][4] = -0.076559591850023;
    noik[5][5] = 0.09469737305728;
    noik[5][6] = 2.4796475497006E-04;
    noik[5][7] = 0.2774376042287;
    noik[5][8] = -0.043846000648377;
    noik[5][9] = -0.2699106478435;
    noik[5][10] = -0.06931341308986;
    noik[5][11] = -0.029632145981653;
    noik[5][12] = 0.01404012675138;
    // Isobutane
    noik[6][1] = 1.04293315891;
    noik[6][2] = -2.8184272548892;
    noik[6][3] = 0.8617623239785;
    noik[6][4] = -0.10613619452487;
    noik[6][5] = 0.098615749302134;
    noik[6][6] = 2.3948208682322E-04;
    noik[6][7] = 0.3033000485695;
    noik[6][8] = -0.041598156135099;
    noik[6][9] = -0.29991937470058;
    noik[6][10] = -0.080369342764109;
    noik[6][11] = -0.029761373251151;
    noik[6][12] = 0.01305963030314;
    // n-Butane
    noik[7][1] = 1.0626277411455;
    noik[7][2] = -2.862095182835;
    noik[7][3] = 0.88738233403777;
    noik[7][4] = -0.12570581155345;
    noik[7][5] = 0.10286308708106;
    noik[7][6] = 2.5358040602654E-04;
    noik[7][7] = 0.32325200233982;
    noik[7][8] = -0.037950761057432;
    noik[7][9] = -0.32534802014452;
    noik[7][10] = -0.079050969051011;
    noik[7][11] = -0.020636720547775;
    noik[7][12] = 0.005705380933475;
    // Isopentane
    noik[8][1] = 1.0963;
    noik[8][2] = -3.0402;
    noik[8][3] = 1.0317;
    noik[8][4] = -0.1541;
    noik[8][5] = 0.11535;
    noik[8][6] = 0.00029809;
    noik[8][7] = 0.39571;
    noik[8][8] = -0.045881;
    noik[8][9] = -0.35804;
    noik[8][10] = -0.10107;
    noik[8][11] = -0.035484;
    noik[8][12] = 0.018156;
    // n-Pentane
    noik[9][1] = 1.0968643098001;
    noik[9][2] = -2.9988888298061;
    noik[9][3] = 0.99516886799212;
    noik[9][4] = -0.16170708558539;
    noik[9][5] = 0.11334460072775;
    noik[9][6] = 2.6760595150748E-04;
    noik[9][7] = 0.40979881986931;
    noik[9][8] = -0.040876423083075;
    noik[9][9] = -0.38169482469447;
    noik[9][10] = -0.10931956843993;
    noik[9][11] = -0.03207322332799;
    noik[9][12] = 0.016877016216975;
    // Hexane
    noik[10][1] = 1.0553238013661;
    noik[10][2] = -2.6120615890629;
    noik[10][3] = 0.7661388296726;
    noik[10][4] = -0.29770320622459;
    noik[10][5] = 0.11879907733358;
    noik[10][6] = 2.7922861062617E-04;
    noik[10][7] = 0.46347589844105;
    noik[10][8] = 0.011433196980297;
    noik[10][9] = -0.48256968738131;
    noik[10][10] = -0.093750558924659;
    noik[10][11] = -6.7273247155994E-03;
    noik[10][12] = -5.1141583585428E-03;
    // Heptane
    noik[11][1] = 1.0543747645262;
    noik[11][2] = -2.6500681506144;
    noik[11][3] = 0.81730047827543;
    noik[11][4] = -0.30451391253428;
    noik[11][5] = 0.122538687108;
    noik[11][6] = 2.7266472743928E-04;
    noik[11][7] = 0.4986582568167;
    noik[11][8] = -7.1432815084176E-04;
    noik[11][9] = -0.5423689552545;
    noik[11][10] = -0.13801821610756;
    noik[11][11] = -6.1595287380011E-03;
    noik[11][12] = 4.8602510393022E-04;
    // Octane
    noik[12][1] = 1.0722544875633;
    noik[12][2] = -2.4632951172003;
    noik[12][3] = 0.65386674054928;
    noik[12][4] = -0.36324974085628;
    noik[12][5] = 0.12713269626764;
    noik[12][6] = 3.071357277793E-04;
    noik[12][7] = 0.5265685698754;
    noik[12][8] = 0.019362862857653;
    noik[12][9] = -0.58939426849155;
    noik[12][10] = -0.14069963991934;
    noik[12][11] = -7.8966330500036E-03;
    noik[12][12] = 3.3036597968109E-03;
    // Nonane
    noik[13][1] = 1.1151;
    noik[13][2] = -2.702;
    noik[13][3] = 0.83416;
    noik[13][4] = -0.38828;
    noik[13][5] = 0.1376;
    noik[13][6] = 0.00028185;
    noik[13][7] = 0.62037;
    noik[13][8] = 0.015847;
    noik[13][9] = -0.61726;
    noik[13][10] = -0.15043;
    noik[13][11] = -0.012982;
    noik[13][12] = 0.0044325;
    // Decane
    noik[14][1] = 1.0461;
    noik[14][2] = -2.4807;
    noik[14][3] = 0.74372;
    noik[14][4] = -0.52579;
    noik[14][5] = 0.15315;
    noik[14][6] = 0.00032865;
    noik[14][7] = 0.84178;
    noik[14][8] = 0.055424;
    noik[14][9] = -0.73555;
    noik[14][10] = -0.18507;
    noik[14][11] = -0.020775;
    noik[14][12] = 0.012335;
    // Oxygen
    noik[16][1] = 0.88878286369701;
    noik[16][2] = -2.4879433312148;
    noik[16][3] = 0.59750190775886;
    noik[16][4] = 9.6501817061881E-03;
    noik[16][5] = 0.07197042871277;
    noik[16][6] = 2.2337443000195E-04;
    noik[16][7] = 0.18558686391474;
    noik[16][8] = -0.03812936803576;
    noik[16][9] = -0.15352245383006;
    noik[16][10] = -0.026726814910919;
    noik[16][11] = -0.025675298677127;
    noik[16][12] = 9.5714302123668E-03;
    // Carbon monoxide
    noik[17][1] = 0.90554;
    noik[17][2] = -2.4515;
    noik[17][3] = 0.53149;
    noik[17][4] = 0.024173;
    noik[17][5] = 0.072156;
    noik[17][6] = 0.00018818;
    noik[17][7] = 0.19405;
    noik[17][8] = -0.043268;
    noik[17][9] = -0.12778;
    noik[17][10] = -0.027896;
    noik[17][11] = -0.034154;
    noik[17][12] = 0.016329;
    // Hydrogen sulfide
    noik[19][1] = 0.87641;
    noik[19][2] = -2.0367;
    noik[19][3] = 0.21634;
    noik[19][4] = -0.050199;
    noik[19][5] = 0.066994;
    noik[19][6] = 0.00019076;
    noik[19][7] = 0.20227;
    noik[19][8] = -0.0045348;
    noik[19][9] = -0.2223;
    noik[19][10] = -0.034714;
    noik[19][11] = -0.014885;
    noik[19][12] = 0.0074154;
    // Argon
    noik[21][1] = 0.85095714803969;
    noik[21][2] = -2.400322294348;
    noik[21][3] = 0.54127841476466;
    noik[21][4] = 0.016919770692538;
    noik[21][5] = 0.068825965019035;
    noik[21][6] = 2.1428032815338E-04;
    noik[21][7] = 0.17429895321992;
    noik[21][8] = -0.033654495604194;
    noik[21][9] = -0.13526799857691;
    noik[21][10] = -0.016387350791552;
    noik[21][11] = -0.024987666851475;
    noik[21][12] = 8.8769204815709E-03;
    // Carbon dioxide
    coik[3][1] = 0;
    doik[3][1] = 1;
    toik[3][1] = 0;
    noik[3][1] = 0.52646564804653;
    coik[3][2] = 0;
    doik[3][2] = 1;
    toik[3][2] = 1.25;
    noik[3][2] = -1.4995725042592;
    coik[3][3] = 0;
    doik[3][3] = 2;
    toik[3][3] = 1.625;
    noik[3][3] = 0.27329786733782;
    coik[3][4] = 0;
    doik[3][4] = 3;
    toik[3][4] = 0.375;
    noik[3][4] = 0.12949500022786;
    coik[3][5] = 1;
    doik[3][5] = 3;
    toik[3][5] = 0.375;
    noik[3][5] = 0.15404088341841;
    coik[3][6] = 1;
    doik[3][6] = 3;
    toik[3][6] = 1.375;
    noik[3][6] = -0.58186950946814;
    coik[3][7] = 1;
    doik[3][7] = 4;
    toik[3][7] = 1.125;
    noik[3][7] = -0.18022494838296;
    coik[3][8] = 1;
    doik[3][8] = 5;
    toik[3][8] = 1.375;
    noik[3][8] = -0.095389904072812;
    coik[3][9] = 1;
    doik[3][9] = 6;
    toik[3][9] = 0.125;
    noik[3][9] = -8.0486819317679E-03;
    coik[3][10] = 1;
    doik[3][10] = 6;
    toik[3][10] = 1.625;
    noik[3][10] = -0.03554775127309;
    coik[3][11] = 2;
    doik[3][11] = 1;
    toik[3][11] = 3.75;
    noik[3][11] = -0.28079014882405;
    coik[3][12] = 2;
    doik[3][12] = 4;
    toik[3][12] = 3.5;
    noik[3][12] = -0.082435890081677;
    coik[3][13] = 3;
    doik[3][13] = 1;
    toik[3][13] = 7.5;
    noik[3][13] = 0.010832427979006;
    coik[3][14] = 3;
    doik[3][14] = 1;
    toik[3][14] = 8;
    noik[3][14] = -6.7073993161097E-03;
    coik[3][15] = 3;
    doik[3][15] = 3;
    toik[3][15] = 6;
    noik[3][15] = -4.6827907600524E-03;
    coik[3][16] = 3;
    doik[3][16] = 3;
    toik[3][16] = 16;
    noik[3][16] = -0.028359911832177;
    coik[3][17] = 3;
    doik[3][17] = 4;
    toik[3][17] = 11;
    noik[3][17] = 0.019500174744098;
    coik[3][18] = 5;
    doik[3][18] = 5;
    toik[3][18] = 24;
    noik[3][18] = -0.21609137507166;
    coik[3][19] = 5;
    doik[3][19] = 5;
    toik[3][19] = 26;
    noik[3][19] = 0.43772794926972;
    coik[3][20] = 5;
    doik[3][20] = 5;
    toik[3][20] = 28;
    noik[3][20] = -0.22130790113593;
    coik[3][21] = 6;
    doik[3][21] = 5;
    toik[3][21] = 24;
    noik[3][21] = 0.015190189957331;
    coik[3][22] = 6;
    doik[3][22] = 5;
    toik[3][22] = 26;
    noik[3][22] = -0.0153809489533;
    // Hydrogen
    coik[15][1] = 0;
    doik[15][1] = 1;
    toik[15][1] = 0.5;
    noik[15][1] = 5.3579928451252;
    coik[15][2] = 0;
    doik[15][2] = 1;
    toik[15][2] = 0.625;
    noik[15][2] = -6.2050252530595;
    coik[15][3] = 0;
    doik[15][3] = 2;
    toik[15][3] = 0.375;
    noik[15][3] = 0.13830241327086;
    coik[15][4] = 0;
    doik[15][4] = 2;
    toik[15][4] = 0.625;
    noik[15][4] = -0.071397954896129;
    coik[15][5] = 0;
    doik[15][5] = 4;
    toik[15][5] = 1.125;
    noik[15][5] = 0.015474053959733;
    coik[15][6] = 1;
    doik[15][6] = 1;
    toik[15][6] = 2.625;
    noik[15][6] = -0.14976806405771;
    coik[15][7] = 1;
    doik[15][7] = 5;
    toik[15][7] = 0;
    noik[15][7] = -0.026368723988451;
    coik[15][8] = 1;
    doik[15][8] = 5;
    toik[15][8] = 0.25;
    noik[15][8] = 0.056681303156066;
    coik[15][9] = 1;
    doik[15][9] = 5;
    toik[15][9] = 1.375;
    noik[15][9] = -0.060063958030436;
    coik[15][10] = 2;
    doik[15][10] = 1;
    toik[15][10] = 4;
    noik[15][10] = -0.45043942027132;
    coik[15][11] = 2;
    doik[15][11] = 1;
    toik[15][11] = 4.25;
    noik[15][11] = 0.424788402445;
    coik[15][12] = 3;
    doik[15][12] = 2;
    toik[15][12] = 5;
    noik[15][12] = -0.021997640827139;
    coik[15][13] = 3;
    doik[15][13] = 5;
    toik[15][13] = 8;
    noik[15][13] = -0.01049952137453;
    coik[15][14] = 5;
    doik[15][14] = 1;
    toik[15][14] = 8;
    noik[15][14] = -2.8955902866816E-03;
    // Water
    coik[18][1] = 0;
    doik[18][1] = 1;
    toik[18][1] = 0.5;
    noik[18][1] = 0.82728408749586;
    coik[18][2] = 0;
    doik[18][2] = 1;
    toik[18][2] = 1.25;
    noik[18][2] = -1.8602220416584;
    coik[18][3] = 0;
    doik[18][3] = 1;
    toik[18][3] = 1.875;
    noik[18][3] = -1.1199009613744;
    coik[18][4] = 0;
    doik[18][4] = 2;
    toik[18][4] = 0.125;
    noik[18][4] = 0.15635753976056;
    coik[18][5] = 0;
    doik[18][5] = 2;
    toik[18][5] = 1.5;
    noik[18][5] = 0.87375844859025;
    coik[18][6] = 0;
    doik[18][6] = 3;
    toik[18][6] = 1;
    noik[18][6] = -0.36674403715731;
    coik[18][7] = 0;
    doik[18][7] = 4;
    toik[18][7] = 0.75;
    noik[18][7] = 0.053987893432436;
    coik[18][8] = 1;
    doik[18][8] = 1;
    toik[18][8] = 1.5;
    noik[18][8] = 1.0957690214499;
    coik[18][9] = 1;
    doik[18][9] = 5;
    toik[18][9] = 0.625;
    noik[18][9] = 0.053213037828563;
    coik[18][10] = 1;
    doik[18][10] = 5;
    toik[18][10] = 2.625;
    noik[18][10] = 0.013050533930825;
    coik[18][11] = 2;
    doik[18][11] = 1;
    toik[18][11] = 5;
    noik[18][11] = -0.41079520434476;
    coik[18][12] = 2;
    doik[18][12] = 2;
    toik[18][12] = 4;
    noik[18][12] = 0.1463744334412;
    coik[18][13] = 2;
    doik[18][13] = 4;
    toik[18][13] = 4.5;
    noik[18][13] = -0.055726838623719;
    coik[18][14] = 3;
    doik[18][14] = 4;
    toik[18][14] = 3;
    noik[18][14] = -0.0112017741438;
    coik[18][15] = 5;
    doik[18][15] = 1;
    toik[18][15] = 4;
    noik[18][15] = -6.6062758068099E-03;
    coik[18][16] = 5;
    doik[18][16] = 1;
    toik[18][16] = 6;
    noik[18][16] = 4.6918522004538E-03;
    // Helium
    coik[20][1] = 0;
    doik[20][1] = 1;
    toik[20][1] = 0;
    noik[20][1] = -0.45579024006737;
    coik[20][2] = 0;
    doik[20][2] = 1;
    toik[20][2] = 0.125;
    noik[20][2] = 1.2516390754925;
    coik[20][3] = 0;
    doik[20][3] = 1;
    toik[20][3] = 0.75;
    noik[20][3] = -1.5438231650621;
    coik[20][4] = 0;
    doik[20][4] = 4;
    toik[20][4] = 1;
    noik[20][4] = 0.020467489707221;
    coik[20][5] = 1;
    doik[20][5] = 1;
    toik[20][5] = 0.75;
    noik[20][5] = -0.34476212380781;
    coik[20][6] = 1;
    doik[20][6] = 3;
    toik[20][6] = 2.625;
    noik[20][6] = -0.020858459512787;
    coik[20][7] = 1;
    doik[20][7] = 5;
    toik[20][7] = 0.125;
    noik[20][7] = 0.016227414711778;
    coik[20][8] = 1;
    doik[20][8] = 5;
    toik[20][8] = 1.25;
    noik[20][8] = -0.057471818200892;
    coik[20][9] = 1;
    doik[20][9] = 5;
    toik[20][9] = 2;
    noik[20][9] = 0.019462416430715;
    coik[20][10] = 2;
    doik[20][10] = 2;
    toik[20][10] = 1;
    noik[20][10] = -0.03329568012302;
    coik[20][11] = 3;
    doik[20][11] = 1;
    toik[20][11] = 4.5;
    noik[20][11] = -0.010863577372367;
    coik[20][12] = 3;
    doik[20][12] = 2;
    toik[20][12] = 5;
    noik[20][12] = -0.022173365245954;

    // Exponents in mixture equations
    // Methane-Nitrogen
    dijk[3][1] = 1;
    tijk[3][1] = 0;
    cijk[3][1] = 0;
    eijk[3][1] = 0;
    bijk[3][1] = 0;
    gijk[3][1] = 0;
    nijk[3][1] = -9.8038985517335E-03;
    dijk[3][2] = 4;
    tijk[3][2] = 1.85;
    cijk[3][2] = 0;
    eijk[3][2] = 0;
    bijk[3][2] = 0;
    gijk[3][2] = 0;
    nijk[3][2] = 4.2487270143005E-04;
    dijk[3][3] = 1;
    tijk[3][3] = 7.85;
    cijk[3][3] = 1;
    eijk[3][3] = 0.5;
    bijk[3][3] = 1;
    gijk[3][3] = 0.5;
    nijk[3][3] = -0.034800214576142;
    dijk[3][4] = 2;
    tijk[3][4] = 5.4;
    cijk[3][4] = 1;
    eijk[3][4] = 0.5;
    bijk[3][4] = 1;
    gijk[3][4] = 0.5;
    nijk[3][4] = -0.13333813013896;
    dijk[3][5] = 2;
    tijk[3][5] = 0;
    cijk[3][5] = 0.25;
    eijk[3][5] = 0.5;
    bijk[3][5] = 2.5;
    gijk[3][5] = 0.5;
    nijk[3][5] = -0.011993694974627;
    dijk[3][6] = 2;
    tijk[3][6] = 0.75;
    cijk[3][6] = 0;
    eijk[3][6] = 0.5;
    bijk[3][6] = 3;
    gijk[3][6] = 0.5;
    nijk[3][6] = 0.069243379775168;
    dijk[3][7] = 2;
    tijk[3][7] = 2.8;
    cijk[3][7] = 0;
    eijk[3][7] = 0.5;
    bijk[3][7] = 3;
    gijk[3][7] = 0.5;
    nijk[3][7] = -0.31022508148249;
    dijk[3][8] = 2;
    tijk[3][8] = 4.45;
    cijk[3][8] = 0;
    eijk[3][8] = 0.5;
    bijk[3][8] = 3;
    gijk[3][8] = 0.5;
    nijk[3][8] = 0.24495491753226;
    dijk[3][9] = 3;
    tijk[3][9] = 4.25;
    cijk[3][9] = 0;
    eijk[3][9] = 0.5;
    bijk[3][9] = 3;
    gijk[3][9] = 0.5;
    nijk[3][9] = 0.22369816716981;
    // Methane-Carbon dioxide
    dijk[4][1] = 1;
    tijk[4][1] = 2.6;
    cijk[4][1] = 0;
    eijk[4][1] = 0;
    bijk[4][1] = 0;
    gijk[4][1] = 0;
    nijk[4][1] = -0.10859387354942;
    dijk[4][2] = 2;
    tijk[4][2] = 1.95;
    cijk[4][2] = 0;
    eijk[4][2] = 0;
    bijk[4][2] = 0;
    gijk[4][2] = 0;
    nijk[4][2] = 0.080228576727389;
    dijk[4][3] = 3;
    tijk[4][3] = 0;
    cijk[4][3] = 0;
    eijk[4][3] = 0;
    bijk[4][3] = 0;
    gijk[4][3] = 0;
    nijk[4][3] = -9.3303985115717E-03;
    dijk[4][4] = 1;
    tijk[4][4] = 3.95;
    cijk[4][4] = 1;
    eijk[4][4] = 0.5;
    bijk[4][4] = 1;
    gijk[4][4] = 0.5;
    nijk[4][4] = 0.040989274005848;
    dijk[4][5] = 2;
    tijk[4][5] = 7.95;
    cijk[4][5] = 0.5;
    eijk[4][5] = 0.5;
    bijk[4][5] = 2;
    gijk[4][5] = 0.5;
    nijk[4][5] = -0.24338019772494;
    dijk[4][6] = 3;
    tijk[4][6] = 8;
    cijk[4][6] = 0;
    eijk[4][6] = 0.5;
    bijk[4][6] = 3;
    gijk[4][6] = 0.5;
    nijk[4][6] = 0.23855347281124;
    // Methane-Ethane
    dijk[1][1] = 3;
    tijk[1][1] = 0.65;
    cijk[1][1] = 0;
    eijk[1][1] = 0;
    bijk[1][1] = 0;
    gijk[1][1] = 0;
    nijk[1][1] = -8.0926050298746E-04;
    dijk[1][2] = 4;
    tijk[1][2] = 1.55;
    cijk[1][2] = 0;
    eijk[1][2] = 0;
    bijk[1][2] = 0;
    gijk[1][2] = 0;
    nijk[1][2] = -7.5381925080059E-04;
    dijk[1][3] = 1;
    tijk[1][3] = 3.1;
    cijk[1][3] = 1;
    eijk[1][3] = 0.5;
    bijk[1][3] = 1;
    gijk[1][3] = 0.5;
    nijk[1][3] = -0.041618768891219;
    dijk[1][4] = 2;
    tijk[1][4] = 5.9;
    cijk[1][4] = 1;
    eijk[1][4] = 0.5;
    bijk[1][4] = 1;
    gijk[1][4] = 0.5;
    nijk[1][4] = -0.23452173681569;
    dijk[1][5] = 2;
    tijk[1][5] = 7.05;
    cijk[1][5] = 1;
    eijk[1][5] = 0.5;
    bijk[1][5] = 1;
    gijk[1][5] = 0.5;
    nijk[1][5] = 0.14003840584586;
    dijk[1][6] = 2;
    tijk[1][6] = 3.35;
    cijk[1][6] = 0.875;
    eijk[1][6] = 0.5;
    bijk[1][6] = 1.25;
    gijk[1][6] = 0.5;
    nijk[1][6] = 0.063281744807738;
    dijk[1][7] = 2;
    tijk[1][7] = 1.2;
    cijk[1][7] = 0.75;
    eijk[1][7] = 0.5;
    bijk[1][7] = 1.5;
    gijk[1][7] = 0.5;
    nijk[1][7] = -0.034660425848809;
    dijk[1][8] = 2;
    tijk[1][8] = 5.8;
    cijk[1][8] = 0.5;
    eijk[1][8] = 0.5;
    bijk[1][8] = 2;
    gijk[1][8] = 0.5;
    nijk[1][8] = -0.23918747334251;
    dijk[1][9] = 2;
    tijk[1][9] = 2.7;
    cijk[1][9] = 0;
    eijk[1][9] = 0.5;
    bijk[1][9] = 3;
    gijk[1][9] = 0.5;
    nijk[1][9] = 1.9855255066891E-03;
    dijk[1][10] = 3;
    tijk[1][10] = 0.45;
    cijk[1][10] = 0;
    eijk[1][10] = 0.5;
    bijk[1][10] = 3;
    gijk[1][10] = 0.5;
    nijk[1][10] = 6.1777746171555;
    dijk[1][11] = 3;
    tijk[1][11] = 0.55;
    cijk[1][11] = 0;
    eijk[1][11] = 0.5;
    bijk[1][11] = 3;
    gijk[1][11] = 0.5;
    nijk[1][11] = -6.9575358271105;
    dijk[1][12] = 3;
    tijk[1][12] = 1.95;
    cijk[1][12] = 0;
    eijk[1][12] = 0.5;
    bijk[1][12] = 3;
    gijk[1][12] = 0.5;
    nijk[1][12] = 1.0630185306388;
    // Methane-Propane
    dijk[2][1] = 3;
    tijk[2][1] = 1.85;
    cijk[2][1] = 0;
    eijk[2][1] = 0;
    bijk[2][1] = 0;
    gijk[2][1] = 0;
    nijk[2][1] = 0.013746429958576;
    dijk[2][2] = 3;
    tijk[2][2] = 3.95;
    cijk[2][2] = 0;
    eijk[2][2] = 0;
    bijk[2][2] = 0;
    gijk[2][2] = 0;
    nijk[2][2] = -7.4425012129552E-03;
    dijk[2][3] = 4;
    tijk[2][3] = 0;
    cijk[2][3] = 0;
    eijk[2][3] = 0;
    bijk[2][3] = 0;
    gijk[2][3] = 0;
    nijk[2][3] = -4.5516600213685E-03;
    dijk[2][4] = 4;
    tijk[2][4] = 1.85;
    cijk[2][4] = 0;
    eijk[2][4] = 0;
    bijk[2][4] = 0;
    gijk[2][4] = 0;
    nijk[2][4] = -5.4546603350237E-03;
    dijk[2][5] = 4;
    tijk[2][5] = 3.85;
    cijk[2][5] = 0;
    eijk[2][5] = 0;
    bijk[2][5] = 0;
    gijk[2][5] = 0;
    nijk[2][5] = 2.3682016824471E-03;
    dijk[2][6] = 1;
    tijk[2][6] = 5.25;
    cijk[2][6] = 0.25;
    eijk[2][6] = 0.5;
    bijk[2][6] = 0.75;
    gijk[2][6] = 0.5;
    nijk[2][6] = 0.18007763721438;
    dijk[2][7] = 1;
    tijk[2][7] = 3.85;
    cijk[2][7] = 0.25;
    eijk[2][7] = 0.5;
    bijk[2][7] = 1;
    gijk[2][7] = 0.5;
    nijk[2][7] = -0.44773942932486;
    dijk[2][8] = 1;
    tijk[2][8] = 0.2;
    cijk[2][8] = 0;
    eijk[2][8] = 0.5;
    bijk[2][8] = 2;
    gijk[2][8] = 0.5;
    nijk[2][8] = 0.0193273748882;
    dijk[2][9] = 2;
    tijk[2][9] = 6.5;
    cijk[2][9] = 0;
    eijk[2][9] = 0.5;
    bijk[2][9] = 3;
    gijk[2][9] = 0.5;
    nijk[2][9] = -0.30632197804624;
    // Nitrogen-Carbon dioxide
    dijk[5][1] = 2;
    tijk[5][1] = 1.85;
    cijk[5][1] = 0;
    eijk[5][1] = 0;
    bijk[5][1] = 0;
    gijk[5][1] = 0;
    nijk[5][1] = 0.28661625028399;
    dijk[5][2] = 3;
    tijk[5][2] = 1.4;
    cijk[5][2] = 0;
    eijk[5][2] = 0;
    bijk[5][2] = 0;
    gijk[5][2] = 0;
    nijk[5][2] = -0.10919833861247;
    dijk[5][3] = 1;
    tijk[5][3] = 3.2;
    cijk[5][3] = 0.25;
    eijk[5][3] = 0.5;
    bijk[5][3] = 0.75;
    gijk[5][3] = 0.5;
    nijk[5][3] = -1.137403208227;
    dijk[5][4] = 1;
    tijk[5][4] = 2.5;
    cijk[5][4] = 0.25;
    eijk[5][4] = 0.5;
    bijk[5][4] = 1;
    gijk[5][4] = 0.5;
    nijk[5][4] = 0.76580544237358;
    dijk[5][5] = 1;
    tijk[5][5] = 8;
    cijk[5][5] = 0;
    eijk[5][5] = 0.5;
    bijk[5][5] = 2;
    gijk[5][5] = 0.5;
    nijk[5][5] = 4.2638000926819E-03;
    dijk[5][6] = 2;
    tijk[5][6] = 3.75;
    cijk[5][6] = 0;
    eijk[5][6] = 0.5;
    bijk[5][6] = 3;
    gijk[5][6] = 0.5;
    nijk[5][6] = 0.17673538204534;
    // Nitrogen-Ethane
    dijk[6][1] = 2;
    tijk[6][1] = 0;
    cijk[6][1] = 0;
    eijk[6][1] = 0;
    bijk[6][1] = 0;
    gijk[6][1] = 0;
    nijk[6][1] = -0.47376518126608;
    dijk[6][2] = 2;
    tijk[6][2] = 0.05;
    cijk[6][2] = 0;
    eijk[6][2] = 0;
    bijk[6][2] = 0;
    gijk[6][2] = 0;
    nijk[6][2] = 0.48961193461001;
    dijk[6][3] = 3;
    tijk[6][3] = 0;
    cijk[6][3] = 0;
    eijk[6][3] = 0;
    bijk[6][3] = 0;
    gijk[6][3] = 0;
    nijk[6][3] = -5.7011062090535E-03;
    dijk[6][4] = 1;
    tijk[6][4] = 3.65;
    cijk[6][4] = 1;
    eijk[6][4] = 0.5;
    bijk[6][4] = 1;
    gijk[6][4] = 0.5;
    nijk[6][4] = -0.1996682004132;
    dijk[6][5] = 2;
    tijk[6][5] = 4.9;
    cijk[6][5] = 1;
    eijk[6][5] = 0.5;
    bijk[6][5] = 1;
    gijk[6][5] = 0.5;
    nijk[6][5] = -0.69411103101723;
    dijk[6][6] = 2;
    tijk[6][6] = 4.45;
    cijk[6][6] = 0.875;
    eijk[6][6] = 0.5;
    bijk[6][6] = 1.25;
    gijk[6][6] = 0.5;
    nijk[6][6] = 0.69226192739021;
    // Methane-Hydrogen
    dijk[7][1] = 1;
    tijk[7][1] = 2;
    cijk[7][1] = 0;
    eijk[7][1] = 0;
    bijk[7][1] = 0;
    gijk[7][1] = 0;
    nijk[7][1] = -0.25157134971934;
    dijk[7][2] = 3;
    tijk[7][2] = -1;
    cijk[7][2] = 0;
    eijk[7][2] = 0;
    bijk[7][2] = 0;
    gijk[7][2] = 0;
    nijk[7][2] = -6.2203841111983E-03;
    dijk[7][3] = 3;
    tijk[7][3] = 1.75;
    cijk[7][3] = 0;
    eijk[7][3] = 0;
    bijk[7][3] = 0;
    gijk[7][3] = 0;
    nijk[7][3] = 0.088850315184396;
    dijk[7][4] = 4;
    tijk[7][4] = 1.4;
    cijk[7][4] = 0;
    eijk[7][4] = 0;
    bijk[7][4] = 0;
    gijk[7][4] = 0;
    nijk[7][4] = -0.035592212573239;
    // Methane-n-Butane, Methane-Isobutane, Ethane-Propane, Ethane-n-Butane,
    // Ethane-Isobutane, Propane-n-Butane, Propane-Isobutane, and n-Butane-Isobutane
    dijk[10][1] = 1;
    tijk[10][1] = 1;
    cijk[10][1] = 0;
    eijk[10][1] = 0;
    bijk[10][1] = 0;
    gijk[10][1] = 0;
    nijk[10][1] = 2.5574776844118;
    dijk[10][2] = 1;
    tijk[10][2] = 1.55;
    cijk[10][2] = 0;
    eijk[10][2] = 0;
    bijk[10][2] = 0;
    gijk[10][2] = 0;
    nijk[10][2] = -7.9846357136353;
    dijk[10][3] = 1;
    tijk[10][3] = 1.7;
    cijk[10][3] = 0;
    eijk[10][3] = 0;
    bijk[10][3] = 0;
    gijk[10][3] = 0;
    nijk[10][3] = 4.7859131465806;
    dijk[10][4] = 2;
    tijk[10][4] = 0.25;
    cijk[10][4] = 0;
    eijk[10][4] = 0;
    bijk[10][4] = 0;
    gijk[10][4] = 0;
    nijk[10][4] = -0.73265392369587;
    dijk[10][5] = 2;
    tijk[10][5] = 1.35;
    cijk[10][5] = 0;
    eijk[10][5] = 0;
    bijk[10][5] = 0;
    gijk[10][5] = 0;
    nijk[10][5] = 1.3805471345312;
    dijk[10][6] = 3;
    tijk[10][6] = 0;
    cijk[10][6] = 0;
    eijk[10][6] = 0;
    bijk[10][6] = 0;
    gijk[10][6] = 0;
    nijk[10][6] = 0.28349603476365;
    dijk[10][7] = 3;
    tijk[10][7] = 1.25;
    cijk[10][7] = 0;
    eijk[10][7] = 0;
    bijk[10][7] = 0;
    gijk[10][7] = 0;
    nijk[10][7] = -0.49087385940425;
    dijk[10][8] = 4;
    tijk[10][8] = 0;
    cijk[10][8] = 0;
    eijk[10][8] = 0;
    bijk[10][8] = 0;
    gijk[10][8] = 0;
    nijk[10][8] = -0.10291888921447;
    dijk[10][9] = 4;
    tijk[10][9] = 0.7;
    cijk[10][9] = 0;
    eijk[10][9] = 0;
    bijk[10][9] = 0;
    gijk[10][9] = 0;
    nijk[10][9] = 0.11836314681968;
    dijk[10][10] = 4;
    tijk[10][10] = 5.4;
    cijk[10][10] = 0;
    eijk[10][10] = 0;
    bijk[10][10] = 0;
    gijk[10][10] = 0;
    nijk[10][10] = 5.5527385721943E-05;

    // Generalized parameters
    fij[1][2] = 1; // Methane-Nitrogen
    fij[1][3] = 1; // Methane-CO2
    fij[1][4] = 1; // Methane-Ethane
    fij[1][5] = 1; // Methane-Propane
    fij[2][3] = 1; // Nitrogen-CO2
    fij[2][4] = 1; // Nitrogen-Ethane
    fij[1][15] = 1; // Methane-Hydrogen
    fij[1][6] = 0.771035405688; // Methane-Isobutane
    fij[1][7] = 1; // Methane-n-Butane
    fij[4][5] = 0.13042476515; // Ethane-Propane
    fij[4][6] = 0.260632376098; // Ethane-Isobutane
    fij[4][7] = 0.281570073085; // Ethane-n-Butane
    fij[5][6] = -0.0551609771024; // Propane-Isobutane
    fij[5][7] = 0.0312572600489; // Propane-n-Butane
    fij[6][7] = -0.0551240293009; // Isobutane-n-Butane

    // Model numbers for binary mixtures with no excess functions (mn=-1)
    for (int i = 1; i <= MaxFlds; ++i) {
      mNumb[i][i] = -1;
      for (int j = i + 1; j <= MaxFlds; ++j) {
        fij[j][i] = fij[i][j];
        mNumb[i][j] = -1;
        mNumb[j][i] = -1;
      }
    }

    // Model numbers for excess functions, 10 is for generalized equation
    mNumb[1][2] = 3;
    mNumb[1][3] = 4;
    mNumb[1][4] = 1;
    mNumb[1][5] = 2;
    mNumb[1][6] = 10;
    mNumb[1][7] = 10;
    mNumb[1][15] = 7;
    mNumb[2][3] = 5;
    mNumb[2][4] = 6;
    mNumb[4][5] = 10;
    mNumb[4][6] = 10;
    mNumb[4][7] = 10;
    mNumb[5][6] = 10;
    mNumb[5][7] = 10;
    mNumb[6][7] = 10;

    // Ideal gas parameters
    n0i[1][3] = 4.00088;
    n0i[1][4] = 0.76315;
    n0i[1][5] = 0.0046;
    n0i[1][6] = 8.74432;
    n0i[1][7] = -4.46921;
    n0i[1][1] = 29.83843397;
    n0i[1][2] = -15999.69151;
    n0i[2][3] = 3.50031;
    n0i[2][4] = 0.13732;
    n0i[2][5] = -0.1466;
    n0i[2][6] = 0.90066;
    n0i[2][7] = 0;
    n0i[2][1] = 17.56770785;
    n0i[2][2] = -2801.729072;
    n0i[3][3] = 3.50002;
    n0i[3][4] = 2.04452;
    n0i[3][5] = -1.06044;
    n0i[3][6] = 2.03366;
    n0i[3][7] = 0.01393;
    n0i[3][1] = 20.65844696;
    n0i[3][2] = -4902.171516;
    n0i[4][3] = 4.00263;
    n0i[4][4] = 4.33939;
    n0i[4][5] = 1.23722;
    n0i[4][6] = 13.1974;
    n0i[4][7] = -6.01989;
    n0i[4][1] = 36.73005938;
    n0i[4][2] = -23639.65301;
    n0i[5][3] = 4.02939;
    n0i[5][4] = 6.60569;
    n0i[5][5] = 3.197;
    n0i[5][6] = 19.1921;
    n0i[5][7] = -8.37267;
    n0i[5][1] = 44.70909619;
    n0i[5][2] = -31236.63551;
    n0i[6][3] = 4.06714;
    n0i[6][4] = 8.97575;
    n0i[6][5] = 5.25156;
    n0i[6][6] = 25.1423;
    n0i[6][7] = 16.1388;
    n0i[6][1] = 34.30180349;
    n0i[6][2] = -38525.50276;
    n0i[7][3] = 4.33944;
    n0i[7][4] = 9.44893;
    n0i[7][5] = 6.89406;
    n0i[7][6] = 24.4618;
    n0i[7][7] = 14.7824;
    n0i[7][1] = 36.53237783;
    n0i[7][2] = -38957.80933;
    n0i[8][3] = 4;
    n0i[8][4] = 11.7618;
    n0i[8][5] = 20.1101;
    n0i[8][6] = 33.1688;
    n0i[8][7] = 0;
    n0i[8][1] = 43.17218626;
    n0i[8][2] = -51198.30946;
    n0i[9][3] = 4;
    n0i[9][4] = 8.95043;
    n0i[9][5] = 21.836;
    n0i[9][6] = 33.4032;
    n0i[9][7] = 0;
    n0i[9][1] = 42.67837089;
    n0i[9][2] = -45215.83;
    n0i[10][3] = 4;
    n0i[10][4] = 11.6977;
    n0i[10][5] = 26.8142;
    n0i[10][6] = 38.6164;
    n0i[10][7] = 0;
    n0i[10][1] = 46.99717188;
    n0i[10][2] = -52746.83318;
    n0i[11][3] = 4;
    n0i[11][4] = 13.7266;
    n0i[11][5] = 30.4707;
    n0i[11][6] = 43.5561;
    n0i[11][7] = 0;
    n0i[11][1] = 52.07631631;
    n0i[11][2] = -57104.81056;
    n0i[12][3] = 4;
    n0i[12][4] = 15.6865;
    n0i[12][5] = 33.8029;
    n0i[12][6] = 48.1731;
    n0i[12][7] = 0;
    n0i[12][1] = 57.25830934;
    n0i[12][2] = -60546.76385;
    n0i[13][3] = 4;
    n0i[13][4] = 18.0241;
    n0i[13][5] = 38.1235;
    n0i[13][6] = 53.3415;
    n0i[13][7] = 0;
    n0i[13][1] = 62.09646901;
    n0i[13][2] = -66600.12837;
    n0i[14][3] = 4;
    n0i[14][4] = 21.0069;
    n0i[14][5] = 43.4931;
    n0i[14][6] = 58.3657;
    n0i[14][7] = 0;
    n0i[14][1] = 65.93909154;
    n0i[14][2] = -74131.45483;
    n0i[15][3] = 2.47906;
    n0i[15][4] = 0.95806;
    n0i[15][5] = 0.45444;
    n0i[15][6] = 1.56039;
    n0i[15][7] = -1.3756;
    n0i[15][1] = 13.07520288;
    n0i[15][2] = -5836.943696;
    n0i[16][3] = 3.50146;
    n0i[16][4] = 1.07558;
    n0i[16][5] = 1.01334;
    n0i[16][6] = 0;
    n0i[16][7] = 0;
    n0i[16][1] = 16.8017173;
    n0i[16][2] = -2318.32269;
    n0i[17][3] = 3.50055;
    n0i[17][4] = 1.02865;
    n0i[17][5] = 0.00493;
    n0i[17][6] = 0;
    n0i[17][7] = 0;
    n0i[17][1] = 17.45786899;
    n0i[17][2] = -2635.244116;
    n0i[18][3] = 4.00392;
    n0i[18][4] = 0.01059;
    n0i[18][5] = 0.98763;
    n0i[18][6] = 3.06904;
    n0i[18][7] = 0;
    n0i[18][1] = 21.57882705;
    n0i[18][2] = -7766.733078;
    n0i[19][3] = 4;
    n0i[19][4] = 3.11942;
    n0i[19][5] = 1.00243;
    n0i[19][6] = 0;
    n0i[19][7] = 0;
    n0i[19][1] = 21.5830944;
    n0i[19][2] = -6069.035869;
    n0i[20][3] = 2.5;
    n0i[20][4] = 0;
    n0i[20][5] = 0;
    n0i[20][6] = 0;
    n0i[20][7] = 0;
    n0i[20][1] = 10.04639507;
    n0i[20][2] = -745.375;
    n0i[21][3] = 2.5;
    n0i[21][4] = 0;
    n0i[21][5] = 0;
    n0i[21][6] = 0;
    n0i[21][7] = 0;
    n0i[21][1] = 10.04639507;
    n0i[21][2] = -745.375;
    th0i[1][4] = 820.659;
    th0i[1][5] = 178.41;
    th0i[1][6] = 1062.82;
    th0i[1][7] = 1090.53;
    th0i[2][4] = 662.738;
    th0i[2][5] = 680.562;
    th0i[2][6] = 1740.06;
    th0i[2][7] = 0;
    th0i[3][4] = 919.306;
    th0i[3][5] = 865.07;
    th0i[3][6] = 483.553;
    th0i[3][7] = 341.109;
    th0i[4][4] = 559.314;
    th0i[4][5] = 223.284;
    th0i[4][6] = 1031.38;
    th0i[4][7] = 1071.29;
    th0i[5][4] = 479.856;
    th0i[5][5] = 200.893;
    th0i[5][6] = 955.312;
    th0i[5][7] = 1027.29;
    th0i[6][4] = 438.27;
    th0i[6][5] = 198.018;
    th0i[6][6] = 1905.02;
    th0i[6][7] = 893.765;
    th0i[7][4] = 468.27;
    th0i[7][5] = 183.636;
    th0i[7][6] = 1914.1;
    th0i[7][7] = 903.185;
    th0i[8][4] = 292.503;
    th0i[8][5] = 910.237;
    th0i[8][6] = 1919.37;
    th0i[8][7] = 0;
    th0i[9][4] = 178.67;
    th0i[9][5] = 840.538;
    th0i[9][6] = 1774.25;
    th0i[9][7] = 0;
    th0i[10][4] = 182.326;
    th0i[10][5] = 859.207;
    th0i[10][6] = 1826.59;
    th0i[10][7] = 0;
    th0i[11][4] = 169.789;
    th0i[11][5] = 836.195;
    th0i[11][6] = 1760.46;
    th0i[11][7] = 0;
    th0i[12][4] = 158.922;
    th0i[12][5] = 815.064;
    th0i[12][6] = 1693.07;
    th0i[12][7] = 0;
    th0i[13][4] = 156.854;
    th0i[13][5] = 814.882;
    th0i[13][6] = 1693.79;
    th0i[13][7] = 0;
    th0i[14][4] = 164.947;
    th0i[14][5] = 836.264;
    th0i[14][6] = 1750.24;
    th0i[14][7] = 0;
    th0i[15][4] = 228.734;
    th0i[15][5] = 326.843;
    th0i[15][6] = 1651.71;
    th0i[15][7] = 1671.69;
    th0i[16][4] = 2235.71;
    th0i[16][5] = 1116.69;
    th0i[16][6] = 0;
    th0i[16][7] = 0;
    th0i[17][4] = 1550.45;
    th0i[17][5] = 704.525;
    th0i[17][6] = 0;
    th0i[17][7] = 0;
    th0i[18][4] = 268.795;
    th0i[18][5] = 1141.41;
    th0i[18][6] = 2507.37;
    th0i[18][7] = 0;
    th0i[19][4] = 1833.63;
    th0i[19][5] = 847.181;
    th0i[19][6] = 0;
    th0i[19][7] = 0;
    th0i[20][4] = 0;
    th0i[20][5] = 0;
    th0i[20][6] = 0;
    th0i[20][7] = 0;
    th0i[21][4] = 0;
    th0i[21][5] = 0;
    th0i[21][6] = 0;
    th0i[21][7] = 0;

    // Mixture parameters for reducing variables
    bvij[1][2] = 0.998721377;
    gvij[1][2] = 1.013950311;
    btij[1][2] = 0.99809883;
    gtij[1][2] = 0.979273013; // CH4-N2
    bvij[1][3] = 0.999518072;
    gvij[1][3] = 1.002806594;
    btij[1][3] = 1.02262449;
    gtij[1][3] = 0.975665369; // CH4-CO2
    bvij[1][4] = 0.997547866;
    gvij[1][4] = 1.006617867;
    btij[1][4] = 0.996336508;
    gtij[1][4] = 1.049707697; // CH4-C2H6
    bvij[1][5] = 1.00482707;
    gvij[1][5] = 1.038470657;
    btij[1][5] = 0.989680305;
    gtij[1][5] = 1.098655531; // CH4-C3H8
    // TODO: Update EOS-CG parameters for Methane + i-Butane
    bvij[1][6] = 1.011240388;
    gvij[1][6] = 1.054319053;
    btij[1][6] = 0.980315756;
    gtij[1][6] = 1.161117729; // CH4-i-C4H10
    // TODO: Update EOS-CG parameters for Methane + n-Butane
    bvij[1][7] = 0.979105972;
    gvij[1][7] = 1.045375122;
    btij[1][7] = 0.99417491;
    gtij[1][7] = 1.171607691; // CH4-C4H10
    // TODO: Update EOS-CG parameters for Methane + i-Pentane
    bvij[1][8] = 1;
    gvij[1][8] = 1.343685343;
    btij[1][8] = 1;
    gtij[1][8] = 1.188899743; // CH4-i-C5H12
    // TODO: Update EOS-CG parameters for Methane + n-Pentane
    bvij[1][9] = 0.94833012;
    gvij[1][9] = 1.124508039;
    btij[1][9] = 0.992127525;
    gtij[1][9] = 1.249173968; // CH4-C5H12
    bvij[1][10] = 0.958015294;
    gvij[1][10] = 1.052643846;
    btij[1][10] = 0.981844797;
    gtij[1][10] = 1.330570181; // CH4-C6H14
    bvij[1][11] = 0.962050831;
    gvij[1][11] = 1.156655935;
    btij[1][11] = 0.977431529;
    gtij[1][11] = 1.379850328; // CH4-C7H16
    bvij[1][12] = 0.994740603;
    gvij[1][12] = 1.116549372;
    btij[1][12] = 0.957473785;
    gtij[1][12] = 1.449245409; // CH4-C8H18
    bvij[1][13] = 1.002852287;
    gvij[1][13] = 1.141895355;
    btij[1][13] = 0.947716769;
    gtij[1][13] = 1.528532478; // CH4-C9H20
    bvij[1][14] = 1.033086292;
    gvij[1][14] = 1.146089637;
    btij[1][14] = 0.937777823;
    gtij[1][14] = 1.568231489; // CH4-C10H22
    bvij[1][15] = 1;
    gvij[1][15] = 1.018702573;
    btij[1][15] = 1;
    gtij[1][15] = 1.352643115; // CH4-H2
    bvij[1][16] = 1;
    gvij[1][16] = 1;
    btij[1][16] = 1;
    gtij[1][16] = 0.95; // CH4-O2
    bvij[1][17] = 0.997340772;
    gvij[1][17] = 1.006102927;
    btij[1][17] = 0.987411732;
    gtij[1][17] = 0.987473033; // CH4-CO
    bvij[1][18] = 1.012783169;
    gvij[1][18] = 1.585018334;
    btij[1][18] = 1.063333913;
    gtij[1][18] = 0.775810513; // CH4-H2O
    bvij[1][19] = 1.012599087;
    gvij[1][19] = 1.040161207;
    btij[1][19] = 1.011090031;
    gtij[1][19] = 0.961155729; // CH4-H2S
    bvij[1][20] = 1;
    gvij[1][20] = 0.881405683;
    btij[1][20] = 1;
    gtij[1][20] = 3.159776855; // CH4-He
    bvij[1][21] = 1.034630259;
    gvij[1][21] = 1.014678542;
    btij[1][21] = 0.990954281;
    gtij[1][21] = 0.989843388; // CH4-Ar
    bvij[2][3] = 0.977794634;
    gvij[2][3] = 1.047578256;
    btij[2][3] = 1.005894529;
    gtij[2][3] = 1.107654104; // N2-CO2
    bvij[2][4] = 0.978880168;
    gvij[2][4] = 1.042352891;
    btij[2][4] = 1.007671428;
    gtij[2][4] = 1.098650964; // N2-C2H6
    bvij[2][5] = 0.974424681;
    gvij[2][5] = 1.081025408;
    btij[2][5] = 1.002677329;
    gtij[2][5] = 1.201264026; // N2-C3H8
    bvij[2][6] = 0.98641583;
    gvij[2][6] = 1.100576129;
    btij[2][6] = 0.99286813;
    gtij[2][6] = 1.284462634; // N2-i-C4H10
    bvij[2][7] = 0.99608261;
    gvij[2][7] = 1.146949309;
    btij[2][7] = 0.994515234;
    gtij[2][7] = 1.304886838; // N2-C4H10
    bvij[2][8] = 1;
    gvij[2][8] = 1.154135439;
    btij[2][8] = 1;
    gtij[2][8] = 1.38177077; // N2-i-C5H12
    bvij[2][9] = 1;
    gvij[2][9] = 1.078877166;
    btij[2][9] = 1;
    gtij[2][9] = 1.419029041; // N2-C5H12
    bvij[2][10] = 1;
    gvij[2][10] = 1.195952177;
    btij[2][10] = 1;
    gtij[2][10] = 1.472607971; // N2-C6H14
    bvij[2][11] = 1;
    gvij[2][11] = 1.40455409;
    btij[2][11] = 1;
    gtij[2][11] = 1.520975334; // N2-C7H16
    bvij[2][12] = 1;
    gvij[2][12] = 1.186067025;
    btij[2][12] = 1;
    gtij[2][12] = 1.733280051; // N2-C8H18
    bvij[2][13] = 1;
    gvij[2][13] = 1.100405929;
    btij[2][13] = 0.95637945;
    gtij[2][13] = 1.749119996; // N2-C9H20
    bvij[2][14] = 1;
    gvij[2][14] = 1;
    btij[2][14] = 0.957934447;
    gtij[2][14] = 1.822157123; // N2-C10H22
    bvij[2][15] = 0.972532065;
    gvij[2][15] = 0.970115357;
    btij[2][15] = 0.946134337;
    gtij[2][15] = 1.175696583; // N2-H2
    bvij[2][16] = 0.99952177;
    gvij[2][16] = 0.997082328;
    btij[2][16] = 0.997190589;
    gtij[2][16] = 0.995157044; // N2-O2
    bvij[2][17] = 1;
    gvij[2][17] = 1.008690943;
    btij[2][17] = 1;
    gtij[2][17] = 0.993425388; // N2-CO
    bvij[2][18] = 1;
    gvij[2][18] = 1.094749685;
    btij[2][18] = 1;
    gtij[2][18] = 0.968808467; // N2-H2O
    bvij[2][19] = 0.910394249;
    gvij[2][19] = 1.256844157;
    btij[2][19] = 1.004692366;
    gtij[2][19] = 0.9601742; // N2-H2S
    bvij[2][20] = 0.969501055;
    gvij[2][20] = 0.932629867;
    btij[2][20] = 0.692868765;
    gtij[2][20] = 1.47183158; // N2-He
    bvij[2][21] = 1.004166412;
    gvij[2][21] = 1.002212182;
    btij[2][21] = 0.999069843;
    gtij[2][21] = 0.990034831; // N2-Ar
    bvij[3][4] = 1.002525718;
    gvij[3][4] = 1.032876701;
    btij[3][4] = 1.013871147;
    gtij[3][4] = 0.90094953; // CO2-C2H6
    bvij[3][5] = 0.996898004;
    gvij[3][5] = 1.047596298;
    btij[3][5] = 1.033620538;
    gtij[3][5] = 0.908772477; // CO2-C3H8
    bvij[3][6] = 1.076551882;
    gvij[3][6] = 1.081909003;
    btij[3][6] = 1.023339824;
    gtij[3][6] = 0.929982936; // CO2-i-C4H10
    bvij[3][7] = 1.174760923;
    gvij[3][7] = 1.222437324;
    btij[3][7] = 1.018171004;
    gtij[3][7] = 0.911498231; // CO2-C4H10
    bvij[3][8] = 1.060793104;
    gvij[3][8] = 1.116793198;
    btij[3][8] = 1.019180957;
    gtij[3][8] = 0.961218039; // CO2-i-C5H12
    bvij[3][9] = 1.024311498;
    gvij[3][9] = 1.068406078;
    btij[3][9] = 1.027000795;
    gtij[3][9] = 0.979217302; // CO2-C5H12
    bvij[3][10] = 1;
    gvij[3][10] = 0.851343711;
    btij[3][10] = 1;
    gtij[3][10] = 1.038675574; // CO2-C6H14
    bvij[3][11] = 1.205469976;
    gvij[3][11] = 1.164585914;
    btij[3][11] = 1.011806317;
    gtij[3][11] = 1.046169823; // CO2-C7H16
    bvij[3][12] = 1.026169373;
    gvij[3][12] = 1.104043935;
    btij[3][12] = 1.02969078;
    gtij[3][12] = 1.074455386; // CO2-C8H18
    bvij[3][13] = 1;
    gvij[3][13] = 0.973386152;
    btij[3][13] = 1.00768862;
    gtij[3][13] = 1.140671202; // CO2-C9H20
    bvij[3][14] = 1.000151132;
    gvij[3][14] = 1.183394668;
    btij[3][14] = 1.02002879;
    gtij[3][14] = 1.145512213; // CO2-C10H22
    bvij[3][15] = 0.904142159;
    gvij[3][15] = 1.15279255;
    btij[3][15] = 0.942320195;
    gtij[3][15] = 1.782924792; // CO2-H2
    bvij[3][16] = 1;
    gvij[3][16] = 1;
    btij[3][16] = 1;
    gtij[3][16] = 1; // CO2-O2
    bvij[3][17] = 1;
    gvij[3][17] = 1;
    btij[3][17] = 1;
    gtij[3][17] = 1; // CO2-CO
    bvij[3][18] = 0.949055959;
    gvij[3][18] = 1.542328793;
    btij[3][18] = 0.997372205;
    gtij[3][18] = 0.775453996; // CO2-H2O
    bvij[3][19] = 0.906630564;
    gvij[3][19] = 1.024085837;
    btij[3][19] = 1.016034583;
    gtij[3][19] = 0.92601888; // CO2-H2S
    bvij[3][20] = 0.846647561;
    gvij[3][20] = 0.864141549;
    btij[3][20] = 0.76837763;
    gtij[3][20] = 3.207456948; // CO2-He
    bvij[3][21] = 1.008392428;
    gvij[3][21] = 1.029205465;
    btij[3][21] = 0.996512863;
    gtij[3][21] = 1.050971635; // CO2-Ar
    bvij[4][5] = 0.997607277;
    gvij[4][5] = 1.00303472;
    btij[4][5] = 0.996199694;
    gtij[4][5] = 1.01473019; // C2H6-C3H8
    bvij[4][6] = 1;
    gvij[4][6] = 1.006616886;
    btij[4][6] = 1;
    gtij[4][6] = 1.033283811; // C2H6-i-C4H10
    bvij[4][7] = 0.999157205;
    gvij[4][7] = 1.006179146;
    btij[4][7] = 0.999130554;
    gtij[4][7] = 1.034832749; // C2H6-C4H10
    bvij[4][8] = 1;
    gvij[4][8] = 1.045439935;
    btij[4][8] = 1;
    gtij[4][8] = 1.021150247; // C2H6-i-C5H12
    bvij[4][9] = 0.993851009;
    gvij[4][9] = 1.026085655;
    btij[4][9] = 0.998688946;
    gtij[4][9] = 1.066665676; // C2H6-C5H12
    bvij[4][10] = 1;
    gvij[4][10] = 1.169701102;
    btij[4][10] = 1;
    gtij[4][10] = 1.092177796; // C2H6-C6H14
    bvij[4][11] = 1;
    gvij[4][11] = 1.057666085;
    btij[4][11] = 1;
    gtij[4][11] = 1.134532014; // C2H6-C7H16
    bvij[4][12] = 1.007469726;
    gvij[4][12] = 1.071917985;
    btij[4][12] = 0.984068272;
    gtij[4][12] = 1.168636194; // C2H6-C8H18
    bvij[4][13] = 1;
    gvij[4][13] = 1.14353473;
    btij[4][13] = 1;
    gtij[4][13] = 1.05603303; // C2H6-C9H20
    bvij[4][14] = 0.995676258;
    gvij[4][14] = 1.098361281;
    btij[4][14] = 0.970918061;
    gtij[4][14] = 1.237191558; // C2H6-C10H22
    bvij[4][15] = 0.925367171;
    gvij[4][15] = 1.10607204;
    btij[4][15] = 0.932969831;
    gtij[4][15] = 1.902008495; // C2H6-H2
    bvij[4][16] = 1;
    gvij[4][16] = 1;
    btij[4][16] = 1;
    gtij[4][16] = 1; // C2H6-O2
    bvij[4][17] = 1;
    gvij[4][17] = 1.201417898;
    btij[4][17] = 1;
    gtij[4][17] = 1.069224728; // C2H6-CO
    bvij[4][18] = 1;
    gvij[4][18] = 1;
    btij[4][18] = 1;
    gtij[4][18] = 1; // C2H6-H2O
    bvij[4][19] = 1.010817909;
    gvij[4][19] = 1.030988277;
    btij[4][19] = 0.990197354;
    gtij[4][19] = 0.90273666; // C2H6-H2S
    bvij[4][20] = 1;
    gvij[4][20] = 1;
    btij[4][20] = 1;
    gtij[4][20] = 1; // C2H6-He
    bvij[4][21] = 1;
    gvij[4][21] = 1;
    btij[4][21] = 1;
    gtij[4][21] = 1; // C2H6-Ar
    bvij[5][6] = 0.999243146;
    gvij[5][6] = 1.001156119;
    btij[5][6] = 0.998012298;
    gtij[5][6] = 1.005250774; // C3H8-i-C4H10
    bvij[5][7] = 0.999795868;
    gvij[5][7] = 1.003264179;
    btij[5][7] = 1.000310289;
    gtij[5][7] = 1.007392782; // C3H8-C4H10
    bvij[5][8] = 1.040459289;
    gvij[5][8] = 0.999432118;
    btij[5][8] = 0.994364425;
    gtij[5][8] = 1.0032695; // C3H8-i-C5H12
    bvij[5][9] = 1.044919431;
    gvij[5][9] = 1.019921513;
    btij[5][9] = 0.996484021;
    gtij[5][9] = 1.008344412; // C3H8-C5H12
    bvij[5][10] = 1;
    gvij[5][10] = 1.057872566;
    btij[5][10] = 1;
    gtij[5][10] = 1.025657518; // C3H8-C6H14
    bvij[5][11] = 1;
    gvij[5][11] = 1.079648053;
    btij[5][11] = 1;
    gtij[5][11] = 1.050044169; // C3H8-C7H16
    bvij[5][12] = 1;
    gvij[5][12] = 1.102764612;
    btij[5][12] = 1;
    gtij[5][12] = 1.063694129; // C3H8-C8H18
    bvij[5][13] = 1;
    gvij[5][13] = 1.199769134;
    btij[5][13] = 1;
    gtij[5][13] = 1.109973833; // C3H8-C9H20
    bvij[5][14] = 0.984104227;
    gvij[5][14] = 1.053040574;
    btij[5][14] = 0.985331233;
    gtij[5][14] = 1.140905252; // C3H8-C10H22
    bvij[5][15] = 1;
    gvij[5][15] = 1.07400611;
    btij[5][15] = 1;
    gtij[5][15] = 2.308215191; // C3H8-H2
    bvij[5][16] = 1;
    gvij[5][16] = 1;
    btij[5][16] = 1;
    gtij[5][16] = 1; // C3H8-O2
    bvij[5][17] = 1;
    gvij[5][17] = 1.108143673;
    btij[5][17] = 1;
    gtij[5][17] = 1.197564208; // C3H8-CO
    bvij[5][18] = 1;
    gvij[5][18] = 1.011759763;
    btij[5][18] = 1;
    gtij[5][18] = 0.600340961; // C3H8-H2O
    bvij[5][19] = 0.936811219;
    gvij[5][19] = 1.010593999;
    btij[5][19] = 0.992573556;
    gtij[5][19] = 0.905829247; // C3H8-H2S
    bvij[5][20] = 1;
    gvij[5][20] = 1;
    btij[5][20] = 1;
    gtij[5][20] = 1; // C3H8-He
    bvij[5][21] = 1;
    gvij[5][21] = 1;
    btij[5][21] = 1;
    gtij[5][21] = 1; // C3H8-Ar

    // The beta values for isobutane+butane are the reciprocal values of those in the GERG-2008
    // publication because the order was reversed in this work.
    bvij[6][7] = 0.999120311;
    gvij[6][7] = 1.00041444;
    btij[6][7] = 0.999922459;
    gtij[6][7] = 1.001432824; // C4H10-i-C4H10

    bvij[6][8] = 1;
    gvij[6][8] = 1.002284353;
    btij[6][8] = 1;
    gtij[6][8] = 1.001835788; // i-C4H10-i-C5H1
    bvij[6][9] = 1;
    gvij[6][9] = 1.002779804;
    btij[6][9] = 1;
    gtij[6][9] = 1.002495889; // i-C4H10-C5H12
    bvij[6][10] = 1;
    gvij[6][10] = 1.010493989;
    btij[6][10] = 1;
    gtij[6][10] = 1.006018054; // i-C4H10-C6H14
    bvij[6][11] = 1;
    gvij[6][11] = 1.021668316;
    btij[6][11] = 1;
    gtij[6][11] = 1.00988576; // i-C4H10-C7H16
    bvij[6][12] = 1;
    gvij[6][12] = 1.032807063;
    btij[6][12] = 1;
    gtij[6][12] = 1.013945424; // i-C4H10-C8H18
    bvij[6][13] = 1;
    gvij[6][13] = 1.047298475;
    btij[6][13] = 1;
    gtij[6][13] = 1.017817492; // i-C4H10-C9H20
    bvij[6][14] = 1;
    gvij[6][14] = 1.060243344;
    btij[6][14] = 1;
    gtij[6][14] = 1.021624748; // i-C4H10-C10H22
    bvij[6][15] = 1;
    gvij[6][15] = 1.147595688;
    btij[6][15] = 1;
    gtij[6][15] = 1.895305393; // i-C4H10-H2
    bvij[6][16] = 1;
    gvij[6][16] = 1;
    btij[6][16] = 1;
    gtij[6][16] = 1; // i-C4H10-O2
    bvij[6][17] = 1;
    gvij[6][17] = 1.087272232;
    btij[6][17] = 1;
    gtij[6][17] = 1.161390082; // i-C4H10-CO
    bvij[6][18] = 1;
    gvij[6][18] = 1;
    btij[6][18] = 1;
    gtij[6][18] = 1; // i-C4H10-H2O
    bvij[6][19] = 1.012994431;
    gvij[6][19] = 0.988591117;
    btij[6][19] = 0.974550548;
    gtij[6][19] = 0.937130844; // i-C4H10-H2S
    bvij[6][20] = 1;
    gvij[6][20] = 1;
    btij[6][20] = 1;
    gtij[6][20] = 1; // i-C4H10-He
    bvij[6][21] = 1;
    gvij[6][21] = 1;
    btij[6][21] = 1;
    gtij[6][21] = 1; // i-C4H10-Ar
    bvij[7][8] = 1;
    gvij[7][8] = 1.002728434;
    btij[7][8] = 1;
    gtij[7][8] = 1.000792201; // C4H10-i-C5H12
    bvij[7][9] = 1;
    gvij[7][9] = 1.01815965;
    btij[7][9] = 1;
    gtij[7][9] = 1.00214364; // C4H10-C5H12
    bvij[7][10] = 1;
    gvij[7][10] = 1.034995284;
    btij[7][10] = 1;
    gtij[7][10] = 1.00915706; // C4H10-C6H14
    bvij[7][11] = 1;
    gvij[7][11] = 1.019174227;
    btij[7][11] = 1;
    gtij[7][11] = 1.021283378; // C4H10-C7H16
    bvij[7][12] = 1;
    gvij[7][12] = 1.046905515;
    btij[7][12] = 1;
    gtij[7][12] = 1.033180106; // C4H10-C8H18
    bvij[7][13] = 1;
    gvij[7][13] = 1.049219137;
    btij[7][13] = 1;
    gtij[7][13] = 1.014096448; // C4H10-C9H20
    bvij[7][14] = 0.976951968;
    gvij[7][14] = 1.027845529;
    btij[7][14] = 0.993688386;
    gtij[7][14] = 1.076466918; // C4H10-C10H22
    bvij[7][15] = 1;
    gvij[7][15] = 1.232939523;
    btij[7][15] = 1;
    gtij[7][15] = 2.509259945; // C4H10-H2
    bvij[7][16] = 1;
    gvij[7][16] = 1;
    btij[7][16] = 1;
    gtij[7][16] = 1; // C4H10-O2
    bvij[7][17] = 1;
    gvij[7][17] = 1.084740904;
    btij[7][17] = 1;
    gtij[7][17] = 1.173916162; // C4H10-CO
    bvij[7][18] = 1;
    gvij[7][18] = 1.223638763;
    btij[7][18] = 1;
    gtij[7][18] = 0.615512682; // C4H10-H2O
    bvij[7][19] = 0.908113163;
    gvij[7][19] = 1.033366041;
    btij[7][19] = 0.985962886;
    gtij[7][19] = 0.926156602; // C4H10-H2S
    bvij[7][20] = 1;
    gvij[7][20] = 1;
    btij[7][20] = 1;
    gtij[7][20] = 1; // C4H10-He
    bvij[7][21] = 1;
    gvij[7][21] = 1.214638734;
    btij[7][21] = 1;
    gtij[7][21] = 1.245039498; // C4H10-Ar
    bvij[8][9] = 1;
    gvij[8][9] = 1.000024335;
    btij[8][9] = 1;
    gtij[8][9] = 1.000050537; // C5H12-i-C5H12
    bvij[8][10] = 1;
    gvij[8][10] = 1.002995876;
    btij[8][10] = 1;
    gtij[8][10] = 1.001204174; // i-C5H12-C6H14
    bvij[8][11] = 1;
    gvij[8][11] = 1.009928206;
    btij[8][11] = 1;
    gtij[8][11] = 1.003194615; // i-C5H12-C7H16
    bvij[8][12] = 1;
    gvij[8][12] = 1.017880545;
    btij[8][12] = 1;
    gtij[8][12] = 1.00564748; // i-C5H12-C8H18
    bvij[8][13] = 1;
    gvij[8][13] = 1.028994325;
    btij[8][13] = 1;
    gtij[8][13] = 1.008191499; // i-C5H12-C9H20
    bvij[8][14] = 1;
    gvij[8][14] = 1.039372957;
    btij[8][14] = 1;
    gtij[8][14] = 1.010825138; // i-C5H12-C10H22
    bvij[8][15] = 1;
    gvij[8][15] = 1.184340443;
    btij[8][15] = 1;
    gtij[8][15] = 1.996386669; // i-C5H12-H2
    bvij[8][16] = 1;
    gvij[8][16] = 1;
    btij[8][16] = 1;
    gtij[8][16] = 1; // i-C5H12-O2
    bvij[8][17] = 1;
    gvij[8][17] = 1.116694577;
    btij[8][17] = 1;
    gtij[8][17] = 1.199326059; // i-C5H12-CO
    bvij[8][18] = 1;
    gvij[8][18] = 1;
    btij[8][18] = 1;
    gtij[8][18] = 1; // i-C5H12-H2O
    bvij[8][19] = 1;
    gvij[8][19] = 0.835763343;
    btij[8][19] = 1;
    gtij[8][19] = 0.982651529; // i-C5H12-H2S
    bvij[8][20] = 1;
    gvij[8][20] = 1;
    btij[8][20] = 1;
    gtij[8][20] = 1; // i-C5H12-He
    bvij[8][21] = 1;
    gvij[8][21] = 1;
    btij[8][21] = 1;
    gtij[8][21] = 1; // i-C5H12-Ar
    bvij[9][10] = 1;
    gvij[9][10] = 1.002480637;
    btij[9][10] = 1;
    gtij[9][10] = 1.000761237; // C5H12-C6H14
    bvij[9][11] = 1;
    gvij[9][11] = 1.008972412;
    btij[9][11] = 1;
    gtij[9][11] = 1.002441051; // C5H12-C7H16
    bvij[9][12] = 1;
    gvij[9][12] = 1.069223964;
    btij[9][12] = 1;
    gtij[9][12] = 1.016422347; // C5H12-C8H18
    bvij[9][13] = 1;
    gvij[9][13] = 1.034910633;
    btij[9][13] = 1;
    gtij[9][13] = 1.103421755; // C5H12-C9H20
    bvij[9][14] = 1;
    gvij[9][14] = 1.016370338;
    btij[9][14] = 1;
    gtij[9][14] = 1.049035838; // C5H12-C10H22
    bvij[9][15] = 1;
    gvij[9][15] = 1.188334783;
    btij[9][15] = 1;
    gtij[9][15] = 2.013859174; // C5H12-H2
    bvij[9][16] = 1;
    gvij[9][16] = 1;
    btij[9][16] = 1;
    gtij[9][16] = 1; // C5H12-O2
    bvij[9][17] = 1;
    gvij[9][17] = 1.119954454;
    btij[9][17] = 1;
    gtij[9][17] = 1.206043295; // C5H12-CO
    bvij[9][18] = 1;
    gvij[9][18] = 0.95667731;
    btij[9][18] = 1;
    gtij[9][18] = 0.447666011; // C5H12-H2O
    bvij[9][19] = 0.984613203;
    gvij[9][19] = 1.076539234;
    btij[9][19] = 0.962006651;
    gtij[9][19] = 0.959065662; // C5H12-H2S
    bvij[9][20] = 1;
    gvij[9][20] = 1;
    btij[9][20] = 1;
    gtij[9][20] = 1; // C5H12-He
    bvij[9][21] = 1;
    gvij[9][21] = 1;
    btij[9][21] = 1;
    gtij[9][21] = 1; // C5H12-Ar
    bvij[10][11] = 1;
    gvij[10][11] = 1.001508227;
    btij[10][11] = 1;
    gtij[10][11] = 0.999762786; // C6H14-C7H16
    bvij[10][12] = 1;
    gvij[10][12] = 1.006268954;
    btij[10][12] = 1;
    gtij[10][12] = 1.001633952; // C6H14-C8H18
    bvij[10][13] = 1;
    gvij[10][13] = 1.02076168;
    btij[10][13] = 1;
    gtij[10][13] = 1.055369591; // C6H14-C9H20
    bvij[10][14] = 1.001516371;
    gvij[10][14] = 1.013511439;
    btij[10][14] = 0.99764101;
    gtij[10][14] = 1.028939539; // C6H14-C10H22
    bvij[10][15] = 1;
    gvij[10][15] = 1.243461678;
    btij[10][15] = 1;
    gtij[10][15] = 3.021197546; // C6H14-H2
    bvij[10][16] = 1;
    gvij[10][16] = 1;
    btij[10][16] = 1;
    gtij[10][16] = 1; // C6H14-O2
    bvij[10][17] = 1;
    gvij[10][17] = 1.155145836;
    btij[10][17] = 1;
    gtij[10][17] = 1.233272781; // C6H14-CO
    bvij[10][18] = 1;
    gvij[10][18] = 1.170217596;
    btij[10][18] = 1;
    gtij[10][18] = 0.569681333; // C6H14-H2O
    bvij[10][19] = 0.754473958;
    gvij[10][19] = 1.339283552;
    btij[10][19] = 0.985891113;
    gtij[10][19] = 0.956075596; // C6H14-H2S
    bvij[10][20] = 1;
    gvij[10][20] = 1;
    btij[10][20] = 1;
    gtij[10][20] = 1; // C6H14-He
    bvij[10][21] = 1;
    gvij[10][21] = 1;
    btij[10][21] = 1;
    gtij[10][21] = 1; // C6H14-Ar
    bvij[11][12] = 1;
    gvij[11][12] = 1.006767176;
    btij[11][12] = 1;
    gtij[11][12] = 0.998793111; // C7H16-C8H18
    bvij[11][13] = 1;
    gvij[11][13] = 1.001370076;
    btij[11][13] = 1;
    gtij[11][13] = 1.001150096; // C7H16-C9H20
    bvij[11][14] = 1;
    gvij[11][14] = 1.002972346;
    btij[11][14] = 1;
    gtij[11][14] = 1.002229938; // C7H16-C10H22
    bvij[11][15] = 1;
    gvij[11][15] = 1.159131722;
    btij[11][15] = 1;
    gtij[11][15] = 3.169143057; // C7H16-H2
    bvij[11][16] = 1;
    gvij[11][16] = 1;
    btij[11][16] = 1;
    gtij[11][16] = 1; // C7H16-O2
    bvij[11][17] = 1;
    gvij[11][17] = 1.190354273;
    btij[11][17] = 1;
    gtij[11][17] = 1.256123503; // C7H16-CO
    bvij[11][18] = 1;
    gvij[11][18] = 1;
    btij[11][18] = 1;
    gtij[11][18] = 1; // C7H16-H2O
    bvij[11][19] = 0.828967164;
    gvij[11][19] = 1.087956749;
    btij[11][19] = 0.988937417;
    gtij[11][19] = 1.013453092; // C7H16-H2S
    bvij[11][20] = 1;
    gvij[11][20] = 1;
    btij[11][20] = 1;
    gtij[11][20] = 1; // C7H16-He
    bvij[11][21] = 1;
    gvij[11][21] = 1;
    btij[11][21] = 1;
    gtij[11][21] = 1; // C7H16-Ar
    bvij[12][13] = 1;
    gvij[12][13] = 1.001357085;
    btij[12][13] = 1;
    gtij[12][13] = 1.000235044; // C8H18-C9H20
    bvij[12][14] = 1;
    gvij[12][14] = 1.002553544;
    btij[12][14] = 1;
    gtij[12][14] = 1.007186267; // C8H18-C10H22
    bvij[12][15] = 1;
    gvij[12][15] = 1.305249405;
    btij[12][15] = 1;
    gtij[12][15] = 2.191555216; // C8H18-H2
    bvij[12][16] = 1;
    gvij[12][16] = 1;
    btij[12][16] = 1;
    gtij[12][16] = 1; // C8H18-O2
    bvij[12][17] = 1;
    gvij[12][17] = 1.219206702;
    btij[12][17] = 1;
    gtij[12][17] = 1.276565536; // C8H18-CO
    bvij[12][18] = 1;
    gvij[12][18] = 0.599484191;
    btij[12][18] = 1;
    gtij[12][18] = 0.662072469; // C8H18-H2O
    bvij[12][19] = 1;
    gvij[12][19] = 1;
    btij[12][19] = 1;
    gtij[12][19] = 1; // C8H18-H2S
    bvij[12][20] = 1;
    gvij[12][20] = 1;
    btij[12][20] = 1;
    gtij[12][20] = 1; // C8H18-He
    bvij[12][21] = 1;
    gvij[12][21] = 1;
    btij[12][21] = 1;
    gtij[12][21] = 1; // C8H18-Ar
    bvij[13][14] = 1;
    gvij[13][14] = 1.00081052;
    btij[13][14] = 1;
    gtij[13][14] = 1.000182392; // C9H20-C10H22
    bvij[13][15] = 1;
    gvij[13][15] = 1.342647661;
    btij[13][15] = 1;
    gtij[13][15] = 2.23435404; // C9H20-H2
    bvij[13][16] = 1;
    gvij[13][16] = 1;
    btij[13][16] = 1;
    gtij[13][16] = 1; // C9H20-O2
    bvij[13][17] = 1;
    gvij[13][17] = 1.252151449;
    btij[13][17] = 1;
    gtij[13][17] = 1.294070556; // C9H20-CO
    bvij[13][18] = 1;
    gvij[13][18] = 1;
    btij[13][18] = 1;
    gtij[13][18] = 1; // C9H20-H2O
    bvij[13][19] = 1;
    gvij[13][19] = 1.082905109;
    btij[13][19] = 1;
    gtij[13][19] = 1.086557826; // C9H20-H2S
    bvij[13][20] = 1;
    gvij[13][20] = 1;
    btij[13][20] = 1;
    gtij[13][20] = 1; // C9H20-He
    bvij[13][21] = 1;
    gvij[13][21] = 1;
    btij[13][21] = 1;
    gtij[13][21] = 1; // C9H20-Ar
    bvij[14][15] = 1.695358382;
    gvij[14][15] = 1.120233729;
    btij[14][15] = 1.064818089;
    gtij[14][15] = 3.786003724; // C10H22-H2
    bvij[14][16] = 1;
    gvij[14][16] = 1;
    btij[14][16] = 1;
    gtij[14][16] = 1; // C10H22-O2
    bvij[14][17] = 1;
    gvij[14][17] = 0.87018496;
    btij[14][17] = 1.049594632;
    gtij[14][17] = 1.803567587; // C10H22-CO
    bvij[14][18] = 1;
    gvij[14][18] = 0.551405318;
    btij[14][18] = 0.897162268;
    gtij[14][18] = 0.740416402; // C10H22-H2O
    bvij[14][19] = 0.975187766;
    gvij[14][19] = 1.171714677;
    btij[14][19] = 0.973091413;
    gtij[14][19] = 1.103693489; // C10H22-H2S
    bvij[14][20] = 1;
    gvij[14][20] = 1;
    btij[14][20] = 1;
    gtij[14][20] = 1; // C10H22-He
    bvij[14][21] = 1;
    gvij[14][21] = 1;
    btij[14][21] = 1;
    gtij[14][21] = 1; // C10H22-Ar
    bvij[15][16] = 1;
    gvij[15][16] = 1;
    btij[15][16] = 1;
    gtij[15][16] = 1; // H2-O2
    bvij[15][17] = 1;
    gvij[15][17] = 1.121416201;
    btij[15][17] = 1;
    gtij[15][17] = 1.377504607; // H2-CO
    bvij[15][18] = 1;
    gvij[15][18] = 1;
    btij[15][18] = 1;
    gtij[15][18] = 1; // H2-H2O
    bvij[15][19] = 1;
    gvij[15][19] = 1;
    btij[15][19] = 1;
    gtij[15][19] = 1; // H2-H2S
    bvij[15][20] = 1;
    gvij[15][20] = 1;
    btij[15][20] = 1;
    gtij[15][20] = 1; // H2-He
    bvij[15][21] = 1;
    gvij[15][21] = 1;
    btij[15][21] = 1;
    gtij[15][21] = 1; // H2-Ar
    bvij[16][17] = 1;
    gvij[16][17] = 1;
    btij[16][17] = 1;
    gtij[16][17] = 1; // O2-CO
    bvij[16][18] = 1;
    gvij[16][18] = 1.143174289;
    btij[16][18] = 1;
    gtij[16][18] = 0.964767932; // O2-H2O
    bvij[16][19] = 1;
    gvij[16][19] = 1;
    btij[16][19] = 1;
    gtij[16][19] = 1; // O2-H2S
    bvij[16][20] = 1;
    gvij[16][20] = 1;
    btij[16][20] = 1;
    gtij[16][20] = 1; // O2-He
    bvij[16][21] = 0.999746847;
    gvij[16][21] = 0.993907223;
    btij[16][21] = 1.000023103;
    gtij[16][21] = 0.990430423; // O2-Ar
    bvij[17][18] = 1;
    gvij[17][18] = 1;
    btij[17][18] = 1;
    gtij[17][18] = 1; // CO-H2O
    bvij[17][19] = 0.795660392;
    gvij[17][19] = 1.101731308;
    btij[17][19] = 1.025536736;
    gtij[17][19] = 1.022749748; // CO-H2S
    bvij[17][20] = 1;
    gvij[17][20] = 1;
    btij[17][20] = 1;
    gtij[17][20] = 1; // CO-He
    bvij[17][21] = 1;
    gvij[17][21] = 1.159720623;
    btij[17][21] = 1;
    gtij[17][21] = 0.954215746; // CO-Ar
    bvij[18][19] = 1;
    gvij[18][19] = 1.014832832;
    btij[18][19] = 1;
    gtij[18][19] = 0.940587083; // H2O-H2S
    bvij[18][20] = 1;
    gvij[18][20] = 1;
    btij[18][20] = 1;
    gtij[18][20] = 1; // H2O-He
    bvij[18][21] = 1;
    gvij[18][21] = 1.038993495;
    btij[18][21] = 1;
    gtij[18][21] = 1.070941866; // H2O-Ar
    bvij[19][20] = 1;
    gvij[19][20] = 1;
    btij[19][20] = 1;
    gtij[19][20] = 1; // H2S-He
    bvij[19][21] = 1;
    gvij[19][21] = 1;
    btij[19][21] = 1;
    gtij[19][21] = 1; // H2S-Ar
    bvij[20][21] = 1;
    gvij[20][21] = 1;
    btij[20][21] = 1;
    gtij[20][21] = 1; // He-Ar

    for (int i = 1; i <= MaxFlds; ++i) {
      bvij[i][i] = 1;
      btij[i][i] = 1;
      gvij[i][i] = 1 / Dc[i];
      gtij[i][i] = Tc[i];
      for (int j = i + 1; j <= MaxFlds; ++j) {
        gvij[i][j] = gvij[i][j] * bvij[i][j] * Math.pow(Vc3[i] + Vc3[j], 3);
        gtij[i][j] = gtij[i][j] * btij[i][j] * Tc2[i] * Tc2[j];
        bvij[i][j] = Math.pow(bvij[i][j], 2);
        btij[i][j] = Math.pow(btij[i][j], 2);
      }
    }

    for (int i = 1; i <= MaxMdl; ++i) {
      for (int j = 1; j <= MaxTrmM; ++j) {
        gijk[i][j] = -cijk[i][j] * Math.pow(eijk[i][j], 2) + bijk[i][j] * gijk[i][j];
        eijk[i][j] = 2 * cijk[i][j] * eijk[i][j] - bijk[i][j];
        cijk[i][j] = -cijk[i][j];
      }
    }

    // Ideal gas terms
    T0 = 298.15;
    d0 = 101.325 / RGERG / T0;
    for (int i = 1; i <= MaxFlds; ++i) {
      n0i[i][3] = n0i[i][3] - 1;
      n0i[i][2] = n0i[i][2] + T0;
      for (int j = 1; j <= 7; ++j) {
        n0i[i][j] = Rsr * n0i[i][j];
      }
      n0i[i][2] = n0i[i][2] - T0;
      n0i[i][1] = n0i[i][1] - Math.log(d0);
    }

    // Code to produce nearly exact values for n0(1) and n0(2)
    // This is not called in the current code, but included below to show how the values were
    // calculated. The return above can be removed to call this code.
    // T0 = 298.15;
    // d0 = 101.325 / RGERG / T0;
    // for (int i = 1; i <= MaxFlds; ++i){
    // n1 = 0; n2 = 0;
    // if (th0i[i][4] > epsilon) { n2 += - n0i[i][4] * th0i[i][4] / Tanh(th0i[i][4] / T0); n1 +=
    // - n0i[i][4] * log(Sinh(th0i[i][4] / T0)); }
    // if (th0i[i][5] > epsilon) { n2 += + n0i[i][5] * th0i[i][5] * Tanh(th0i[i][5] / T0); n1 +=
    // + n0i[i][5] * log(Cosh(th0i[i][5] / T0)); }
    // if (th0i[i][6] > epsilon) { n2 += - n0i[i][6] * th0i[i][6] / Tanh(th0i[i][6] / T0); n1 +=
    // - n0i[i][6] * log(Sinh(th0i[i][6] / T0)); }
    // if (th0i[i][7] > epsilon) { n2 += + n0i[i][7] * th0i[i][7] * Tanh(th0i[i][7] / T0); n1 +=
    // + n0i[i][7] * log(Cosh(th0i[i][7] / T0)); }
    // n0i[i][3] = n0i[i][3] - 1;
    // n0i[i][1] = n1 - n2 / T0 + n0i[i][3] * (1 + log(T0));
    // n0i[i][2] = n2 - n0i[i][3] * T0;
    // for (int j = 1; j <= 7; ++j){
    // n0i[i][j] = Rsr * n0i[i][j];
    // }
    // n0i[i][2] = n0i[i][2] - T0;
    // n0i[i][1] = n0i[i][1] - log(d0);
    // }
  }

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
    EOSCGModel test = new EOSCGModel();
    test.SetupEOSCG();

    double T = 400;
    doubleW D = new doubleW(12.79828626082062);
    doubleW P = new doubleW(50000.0d);
    intW ierr = new intW(0);
    doubleW Mm = new doubleW(0.0d);
    doubleW Z = new doubleW(0.0d);
    int iFlag = 0;
    StringW herr = new StringW("");

    double[] x = {0.0, 0.77824, 0.02, 0.06, 0.08, 0.03, 0.0015, 0.003, 0.0005, 0.00165, 0.00215,
        0.00088, 0.00024, 0.00015, 0.00009, 0.004, 0.005, 0.002, 0.0001, 0.0025, 0.007, 0.001};

    test.MolarMassEOSCG(x, Mm);

    // System.out.println("mol mass " + Mm.val);

    test.PressureEOSCG(T, D.val, x, P, Z);

    System.out.println("pressure " + P.val);
    System.out.println("Z " + Z.val);

    test.DensityEOSCG(iFlag, T, P.val, x, D, ierr, herr);
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
    test.PropertiesEOSCG(T, D.val, x, P, Z, dPdD, d2PdD2, d2PdTD, dPdT, U, H, S, Cv, Cp, W, G, JT,
        Kappa, A);

    /*
     * // test.PressureEOSCG(400, 12.798286, x); String herr = ""; test.DensityEOSCG(0, T, P, x,
     * ierr, herr); double pres = test.P; double molarmass = test.Mm;
     *
     * // double dPdD=0.0, dPdD2=0.0, d2PdTD=0.0, dPdT=0.0, U=0.0, H=0.0, S=0.0, // Cv=0.0, Cp=0.0,
     * W=0.0, G=0.0, JT=0.0, Kappa=0.0, A=0.0;
     *
     * // void DensityEOSCG(const int iFlag, const double T, const double P, const //
     * std::vector<double> &x, double &D, int &ierr, std::string &herr) // test.DensityEOSCG(0, T,
     * P, x, ierr, herr);
     *
     * // Sub PropertiesEOSCG(T, D, x, P, Z, dPdD, dPdD2, d2PdTD, dPdT, U, H, S, Cv, Cp, // W, G,
     * JT, Kappa) // test.PropertiesEOSCG(T, test.D, x);
     */
    System.out.println("Outputs-----\n");
    System.out
        .println("Molar mass [g/mol]:                 20.54274450160000 != %0.16g\n" + Mm.val);
    System.out.println("Molar density [mol/l]:              12.79828626082062 != %0.16g\n" + D.val);
    System.out.println("Pressure [kPa]:                     50000.00000000001 != %0.16g\n" + P.val);
    System.out.println("Compressibility factor:             1.174690666383717 != %0.16g\n" + Z.val);
    System.out
        .println("d(P)/d(rho) [kPa/(mol/l)]:          7000.694030193327 != %0.16g\n" + dPdD.val);
    System.out
        .println("d^2(P)/d(rho)^2 [kPa/(mol/l)^2]:    1130.481239114938 != %0.16g\n" + d2PdD2.val);
    System.out
        .println("d(P)/d(T) [kPa/K]:                  235.9832292593096 != %0.16g\n" + dPdT.val);
    System.out
        .println("Energy [J/mol]:                     -2746.492901212530 != %0.16g\n" + U.val);
    System.out.println("Enthalpy [J/mol]:                   1160.280160510973 != %0.16g\n" + H.val);
    System.out
        .println("Entropy [J/mol-K]:                  -38.57590392409089 != %0.16g\n" + S.val);
    System.out
        .println("Isochoric heat capacity [J/mol-K]:  39.02948218156372 != %0.16g\n" + Cv.val);
    System.out
        .println("Isobaric heat capacity [J/mol-K]:   58.45522051000366 != %0.16g\n" + Cp.val);
    System.out.println("Speed of sound [m/s]:               714.4248840596024 != %0.16g\n" + W.val);
    System.out.println("Gibbs energy [J/mol]:               16590.64173014733 != %0.16g\n" + G.val);
    System.out
        .println("Joule-Thomson coefficient [K/kPa]:  7.155629581480913E-05 != %0.16g\n" + JT.val);
    System.out
        .println("Isentropic exponent:                2.683820255058032 != %0.16g\n" + Kappa.val);
  }
}
