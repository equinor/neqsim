package neqsim.thermo.util.Vega;

import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;
import neqsim.util.ExcludeFromJacocoGeneratedReport;


//Fundamental EOS for Helium based on Helmholz free energy, used by NIST
//https://nvlpubs.nist.gov/nistpubs/ir/2023/NIST.IR.8474.pdf

public class Vega {

  double R;
  double M;
  double Tc;
  double Dc;


  double dPdDsave;

  double a1, a2;

  int N = 23;
  int I_pol = 6;
  int I_Exp = 6;
  int I_GBS = 11;
  double n_i[] = new double[N+1];
  double t_i[] = new double[N+1];
  double d_i[] = new double[N+1];
  double l_i[] = new double[N+1];
  double eta_i[] = new double[N+1];
  double beta_i[] = new double[N+1];
  double gamma_i[] = new double[N+1];
  double epsilon_i[] = new double[N+1];

  double epsilon = 1e-15;

  public void DensityVega(int iFlag, double T, double P, doubleW D, intW ierr,
  StringW herr) {
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
    tolr = 1e-12;
    PseudoCriticalPointVega(Tcx, Dcx);

    if (D.val > -epsilon) {
    D.val = P / R / T; // Ideal gas estimate for vapor phase
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
     herr.val = "Calculation failed to converge in Vega method, ideal gas density returned.";
     D.val = P / R / T;
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
 PressureVega(T, D.val, P2, Z);
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
         propertiesVega(T, D.val, PP, Z, dPdD, d2PdD2, d2PdTD, dPdT, U, H, S, Cv, Cp, W, G,
             JT, Kappa, A);
         if ((PP.val <= 0 || dPdD.val <= 0 || d2PdTD.val <= 0)
             || (Cv.val <= 0 || Cp.val <= 0 || W.val <= 0)) {
           // Iteration failed (above loop did find a solution or checks made
           // below
           // indicate possible 2-phase state)
           ierr.val = 1;
           herr.val =
               "Calculation failed to converge in Vega method, ideal gas density returned.";
           D.val = P / R / T;
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
herr.val = "Calculation failed to converge in Vega method, ideal gas density returned.";
D.val = P / R / T;
}

public void PressureVega(double T, double D, doubleW P, doubleW Z) {
  doubleW[][] ar = new doubleW[4][4];
  for (int i = 0; i < 4; i++) {
    for (int j = 0; j < 4; j++) {
      ar[i][j] = new doubleW(0.0d);
    }
  }
  AlpharVega(0, 0, T, D, ar);

  Z.val = 1 + ar[0][1].val;
  P.val = D * R * T * Z.val;
  dPdDsave = R * T * (1 + 2 * ar[0][1].val + ar[0][2].val);
}

void AlpharVega(int itau, int idelta, double T, double D, doubleW[][] ar) {

  // Outputs:
  // ar(0,0) - Residual Helmholtz energy (dimensionless, =a/RT)
  // ar(0,1) - delta*partial (ar)/partial(delta)
  // ar(0,2) - delta^2*partial^2(ar)/partial(delta)^2
  // ar(0,3) - delta^3*partial^3(ar)/partial(delta)^3
  // ar(1,0) - tau*partial (ar)/partial(tau)
  // ar(1,1) - tau*delta*partial^2(ar)/partial(tau)/partial(delta)
  // ar(2,0) - tau^2*partial^2(ar)/partial(tau)^2


  // Select parameters based on hydrogen type
  //double[] N_i, t_i, d_i, p_i, phi_i, beta_i, gamma_i, D_i; 
  //double Tc, Dc;


  // Clear previous ar values
  for (int i = 0; i <= 3; ++i) {
      for (int j = 0; j <= 3; ++j) {
          ar[i][j].val = 0;
      }
  }

  // Define reduced variables
  double tau = Tc / T;  // Reduced temperature
  double delta = D / Dc; // Reduced density

  //first sum
  for (int k = 0; k < I_pol; k++) {
    double B = n_i[k] * Math.pow(delta, d_i[k]) * Math.pow(tau, t_i[k]);
    
    double dBddelta = n_i[k] * d_i[k] * Math.pow(delta, d_i[k] - 1) * Math.pow(tau, t_i[k]);
    double d2Bddelta2 = n_i[k] * d_i[k] * (d_i[k] - 1) * Math.pow(delta, d_i[k] - 2) * Math.pow(tau, t_i[k]);
    double d3Bddelta3 = n_i[k] * d_i[k] * (d_i[k] - 1) * (d_i[k] - 2) * Math.pow(delta, d_i[k] - 3) * Math.pow(tau, t_i[k]);

    double dBdtau = n_i[k] * Math.pow(delta, d_i[k]) * t_i[k] * Math.pow(tau, t_i[k] - 1);
    double d2Bdtau2 = n_i[k] * Math.pow(delta, d_i[k]) * t_i[k] * (t_i[k] - 1) * Math.pow(tau, t_i[k] - 2);

    double d2Bddeltadtau = n_i[k] * d_i[k] * Math.pow(delta, d_i[k] - 1) * t_i[k] * Math.pow(tau, t_i[k] - 1);

    ar[0][0].val += B;
    ar[0][1].val += dBddelta;
    ar[0][2].val += d2Bddelta2;
    ar[0][3].val += d3Bddelta3;

    ar[1][0].val += dBdtau;
    ar[2][0].val += d2Bdtau2;

    ar[1][1].val += d2Bddeltadtau;
  }
  //second sum
  for (int k = I_pol; k < I_pol + I_Exp; k++) {
    double B = n_i[k] * Math.pow(delta, d_i[k]) * Math.pow(tau, t_i[k]);
    double e = -Math.pow(delta, l_i[k]);
    double E = Math.exp(e);

    //derivates of delta
    double dBddelta = B * d_i[k] / delta;
    double d2Bddelta2 = B * d_i[k] * (d_i[k] - 1) / Math.pow(delta, 2);
    double d3Bddelta3 = B * d_i[k] * (d_i[k] - 1) * (d_i[k] - 2) / Math.pow(delta, 3);

    double deddelta = -l_i[k] * Math.pow(delta, l_i[k] - 1);
    double d2eddelta2 = l_i[k] * (l_i[k] - 1) * Math.pow(delta, l_i[k] - 2);
    double d3eddelta3 = l_i[k] * (l_i[k] - 1) * (l_i[k] - 2) * Math.pow(delta, l_i[k] - 3);

    //derivates of tau
    double dBdtau = B * t_i[k] / tau;
    double d2Bdtau2 = B * t_i[k] * (t_i[k] - 1) / Math.pow(tau, 2);

    double dedtau = 0;
    double d2edtau2 = 0;

    //derivates of delta and tau
    double d2Bddeltadtau = B * d_i[k] * t_i[k] / (delta * tau);
    double d2edeltadtau = 0;

    ar[0][0].val += B * E;
    //d(alpha^r)/d(delta)
    ar[0][1].val += dBddelta * E + B * deddelta * E;
    //d^2(alpha^r)/d(delta)^2
    double G = d2Bddelta2 + 2 * dBddelta * deddelta + B * d2eddelta2 + B * deddelta * deddelta;
    ar[0][2].val += G*E;
    //d^3(alpha^r)/d(delta)^3
    double dGddelta = d3Bddelta3 + 2 * (d2Bddelta2 * deddelta + dBddelta * d2eddelta2) + dBddelta*d2Bddelta2 + B * d3eddelta3 + dBddelta * deddelta * deddelta + 2 * B * deddelta * d2eddelta2;
    ar[0][3].val += dGddelta*E;

    //d(alpha^r)/d(tau)
    ar[1][0].val += dBdtau * E + B * E * dedtau;
    //d^2(alpha^r)/d(tau)^2
    ar[2][0].val += d2Bdtau2 * E + 2 * dBdtau * dedtau * E + B * E * d2edtau2;

    //d^2(alpha^r)/d(delta)d(tau)
    ar[1][1].val += d2Bddeltadtau * E + dBddelta * dedtau * E + dBdtau * deddelta * E + B * d2edeltadtau * E;
  }
  //thirdsum
  for (int k = I_pol + I_Exp; k < I_pol + I_Exp + I_GBS; k++) {
    double B = n_i[k] * Math.pow(delta, d_i[k]) * Math.pow(tau, t_i[k]);
    double e = - eta_i[k] * Math.pow(delta - epsilon_i[k], 2) - beta_i[k] * Math.pow(tau - gamma_i[k], 2);
    double E = Math.exp(e);

    //derivates of delta
    double dBddelta = B * d_i[k] / delta;
    double d2Bddelta2 = B * d_i[k] * (d_i[k] - 1) / Math.pow(delta, 2);
    double d3Bddelta3 = B * d_i[k] * (d_i[k] - 1) * (d_i[k] - 2) / Math.pow(delta, 3);

    double deddelta = -2 * eta_i[k] * (delta - epsilon_i[k]);
    double d2eddelta2 = -2 * eta_i[k];
    double d3eddelta3 = 0;

    //derivates of tau
    double dBdtau = B * t_i[k] / tau;
    double d2Bdtau2 = B * t_i[k] * (t_i[k] - 1) / Math.pow(tau, 2);
    
    double dedtau = -2 * beta_i[k] * (tau - gamma_i[k]);
    double d2edtau2 = -2 * beta_i[k];

    //derivates of delta and tau
    double d2Bddeltadtau = B * d_i[k] * t_i[k] / (delta * tau);
    double d2edeltadtau = 0;

    ar[0][0].val += B * E;
    //d(alpha^r)/d(delta)
    ar[0][1].val += dBddelta * E + B * deddelta * E;
    //d^2(alpha^r)/d(delta)^2
    double G = d2Bddelta2 + 2 * dBddelta * deddelta + B * d2eddelta2 + B * deddelta * deddelta;
    ar[0][2].val += G*E;
    //d^3(alpha^r)/d(delta)^3
    double dGddelta = d3Bddelta3 + 2*(d2Bddelta2*deddelta + dBddelta*d2eddelta2) + dBddelta*d2eddelta2 + B*d3eddelta3 + dBddelta*deddelta*deddelta + 2*B*deddelta*d2eddelta2;
    ar[0][3].val += E * (dGddelta + G*deddelta);

    //d(alpha^r)/d(tau)
    ar[1][0].val += dBdtau * E + B * E * dedtau;
    //d^2(alpha^r)/d(tau)^2
    ar[2][0].val += d2Bdtau2 * E + 2 * dBdtau * dedtau * E + B * E * d2edtau2;

    //d^2(alpha^r)/d(delta)d(tau)
    ar[1][1].val += d2Bddeltadtau * E + dBddelta * dedtau * E + dBdtau * deddelta * E + B * d2edeltadtau * E + B * deddelta * dedtau * E;
  }
  ar[0][1].val = delta*ar[0][1].val;
  ar[0][2].val = delta*delta*ar[0][2].val;
  ar[0][3].val = delta*delta*delta*ar[0][3].val;
  ar[1][0].val = tau*ar[1][0].val;
  ar[2][0].val = tau*tau*ar[2][0].val;
  ar[1][1].val = tau*delta*ar[1][1].val;
}

void Alpha0Vega(double T, double D, doubleW[] a0) {
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
  double tau = Tc / T;  // Reduced temperature
  double delta = D / Dc; // Reduced density
  double lntau = Math.log(tau);

  a0[0].val = a1 + a2 * tau + Math.log(delta) + 1.5 * Math.log(tau); 
  a0[1].val = a2 + 1.5 / tau;
  a0[2].val = -1.5 / (tau * tau);


  a0[1].val = tau*a0[1].val;
  a0[2].val = tau*tau*a0[2].val;
}



  /**
   * <p>
   * PropertiesVega.
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
  public void propertiesVega(double T, double D, doubleW P, doubleW Z, doubleW dPdD,
      doubleW d2PdD2, doubleW d2PdTD, doubleW dPdT, doubleW U, doubleW H, doubleW S, doubleW Cv,
      doubleW Cp, doubleW W, doubleW G, doubleW JT, doubleW Kappa, doubleW A) {
    // Sub PropertiesGERG(T, D, x, P, Z, dPdD, d2PdD2, d2PdTD, dPdT, U, H, S, Cv,
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

    double RT;
    // Calculate molar mass

    // Calculate the ideal gas Helmholtz energy, and its first and second
    // derivatives with respect to temperature.
    Alpha0Vega(T, D, a0);

    // Calculate the real gas Helmholtz energy, and its derivatives with respect to
    // temperature and/or density.
    AlpharVega(1, 0, T, D, ar);

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
    W.val = 1000 * Cp.val / Cv.val * dPdD.val / M;
    if (W.val < 0) {
      W.val = 0;
    }
    W.val = Math.sqrt(W.val);
    Kappa.val = Math.pow(W.val, 2) * M / (RT * 1000 * Z.val);
  }


  /**
   * @param Tcx temperature in Kelvin
   * @param Dcx density
   */
  void PseudoCriticalPointVega(doubleW Tcx, doubleW Dcx) {
    // Use the pre-initialized class-level values for Tc and Dc
    if (Tc == 0 || Dc == 0) {
        throw new IllegalStateException("Critical point parameters not set. Please call SetupVega() first.");
    }

    Tcx.val = Tc;    // Assign the pre-initialized critical temperature
    Dcx.val = Dc;    // Assign the pre-initialized critical density

    // Optionally calculate Vcx if needed
    double Vcx = 1 / Dcx.val;
  }

// The following routine must be called once before any other routine.
  /**
   * <p>
   * SetupVega.
   * </p>
   */
  public void SetupVega() {
    // Initialize all the constants and parameters in the GERG-2008 model.
    // Some values are modified for calculations that do not depend on T, D, and x in order to
    // speed up the program.
    R = 8.314472; // Universal gas constant [J/(mol-K)]
    M = 4.002602;

    Tc = 5.1953;
    Dc = 17.3837; //mol/l

    a1 = 0.1733487932835764;
    a2 = 0.4674522201550815;

    n_i = new double[] {0.015559018, 3.0638932, -4.2420844, 0.054418088, -0.18971904,
      0.087856262, 2.2833566, -0.53331595, -0.53296502, 0.99444915,
      -0.30078896, -1.6432563, 0.8029102, 0.026838669, 0.04687678,
      -0.14832766, 0.03016211, -0.019986041, 0.14283514, 0.007418269,
      -0.22989793, 0.79224829, -0.049386338
    };
    t_i = new double[] {1.0, 0.425, 0.63, 0.69, 1.83, 0.575, 0.925, 1.585, 1.69, 1.51, 2.9, 0.8, 
      1.26, 3.51, 2.785, 1.0, 4.22, 0.83, 1.575, 3.447, 0.73, 1.634, 6.13
    };
    d_i = new double[] {4, 1, 1, 2, 2, 3, 1, 1, 3, 2, 2, 1, 
      2, 1, 2, 1, 1, 3, 2, 2, 3, 2, 2
    };
    l_i = new double[] {0, 0, 0, 0, 0, 0, 1, 2, 2, 1, 2, 1, 
      1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };
    eta_i = new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 
       1.5497, 9.245, 4.76323, 6.3826, 8.7023, 0.255, 0.3523, 0.1492, 0.05, 0.1668, 42.2358
    };
    beta_i = new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 
      0.2471, 0.0983, 0.1556, 2.6782, 2.7077, 0.6621, 0.1775, 0.4821, 0.3069, 0.1758, 1357.6577
    };
    gamma_i = new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
      3.15, 2.54505, 1.2513, 1.9416, 0.5984, 2.2282, 1.606, 3.815, 1.61958, 0.6407, 1.076
    };
    epsilon_i = new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 
      0.596, 0.3423, 0.761, 0.9747, 0.5868, 0.5627, 2.5346, 3.6763, 4.5245, 5.039, 0.959
    };
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
    Vega test = new Vega();
    test.SetupVega();

    double T = 273.15;
    doubleW D = new doubleW(30);
    doubleW P = new doubleW(10000.0d);
    intW ierr = new intW(0);
    //doubleW M_L = new doubleW(0.0d);
    doubleW Z = new doubleW(0.0d);
    int iFlag = 0;
    StringW herr = new StringW("");


    

    // System.out.println("mol mass " + Mm.val);

    //test.PressureVega(T, D.val, P, Z);

    //System.out.println("pressure " + P.val);
    //System.out.println("Z " + Z.val);

    test.DensityVega(iFlag, T, P.val, D, ierr, herr);
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
    test.propertiesVega(T, D.val, P, Z, dPdD, d2PdD2, d2PdTD, dPdT, U, H, S, Cv, Cp, W, G, JT,
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
    System.out.println("Molar mass [g/mol]:                 " + test.M);
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


