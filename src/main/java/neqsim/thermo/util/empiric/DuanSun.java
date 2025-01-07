package neqsim.thermo.util.empiric;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * DuanSun class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class DuanSun {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(DuanSun.class);

  double[] c = new double[] {0.1, 0.2, 0.3, 0.4, 0.5};
  double[] d = new double[] {0.1, 0.2, 0.3, 0.4, 0.5};

  /**
   * <p>
   * Constructor for DuanSun.
   * </p>
   */
  public DuanSun() {}

  /**
   * <p>
   * bublePointPressure.
   * </p>
   *
   * @param temperature a double
   * @param x_CO2 a double
   * @param salinity a double
   * @return a double
   */
  public double bublePointPressure(double temperature, double x_CO2, double salinity) {
    // Type manually the pressure limits according to the expected pressure
    double P = 9.0;
    double Pold = 9.0;
    double Poldold = 9.0;
    double[] y = {0.9, 0.1};
    double[] x = {x_CO2, 1.0 - x_CO2};
    double error = 1e10;
    double errorOld = 1e10;
    int iter = 1;
    do {
      // while (P < 15.0) {
      iter++;
      double[] Tc = {304.2, 647.3};
      double[] Pc = {72.8, 217.6};
      double[] w = {0.225, 0.344};
      double[][] K12 = {{0.0, 0.2}, {0.2, 0.0}};
      double T = temperature;
      double S = salinity;
      // double[] x = {0.000554093, 1.0-0.000554093};

      // Normalize Y
      double SUMY = 0.0;
      for (int i = 0; i < 2; i++) {
        SUMY = SUMY + y[i];
      }
      for (int i = 0; i < 2; i++) {
        y[i] = y[i] / SUMY;
      }
      // System.out.println(SUMY);
      // System.out.println(y[0]);

      double R = ThermodynamicConstantsInterface.R * Math.pow(10.0, -2.0);

      // Calculate A and B of pure compound
      double[] Tr = {0.0, 0.0};
      double ac = 0.45724;
      double bc = 0.07780;
      double[] d = {0.384401, 1.52276, -0.213808, 0.034616, -0.001976};
      double[] dm = {0.0, 0.0};
      double[] ag = {0.0, 0.0};
      double[] asmal = {0.0, 0.0};
      double[] bsmal = {0.0, 0.0};
      double[] a = {0.0, 0.0};
      double[] b = {0.0, 0.0};

      for (int i = 0; i < 2; i++) {
        Tr[i] = T / Tc[i];
        dm[i] = d[0] + w[i] * (d[1] + w[i] * (d[2] + w[i] * (d[3] + w[i] * d[4])));
        ag[i] = Math.pow((1.0 + dm[i] * (1.0 - Math.pow(Tr[i], 0.5))), 2.0);
        asmal[i] = ag[i] * ac * ((Math.pow(R * Tc[i], 2.0)) / Pc[i]);
        bsmal[i] = bc * (R * Tc[i]) / Pc[i];
        a[i] = asmal[i] * P / (Math.pow(R * T, 2.0));
        b[i] = bsmal[i] * P / (R * T);
      }

      // System.out.println("asmal0 = " + asmal[0]);
      // System.out.println("asmal1 = " +asmal[1]);
      // System.out.println("bsmal0 = " +bsmal[0]);
      // System.out.println("bsmal1 = " +bsmal[1]);
      // System.out.println("a0 = " +a[0]);
      // System.out.println("a1 = " +a[1]);
      // System.out.println("b0 = " +b[0]);
      // System.out.println("b1 = " +b[1]);

      // Calculate A and B of mixture

      double av = 0.0;
      double bv = 0.0;
      double[] sumav = {0.0, 0.0};
      double aij = 0.0;

      for (int i = 0; i < 2; i++) {
        sumav[i] = 0.0;
        for (int j = 0; j < 2; j++) {
          aij = Math.sqrt(a[i] * a[j]) * (1.0 - K12[i][j]);
          av = av + y[i] * y[j] * aij;
          sumav[i] = sumav[i] + y[j] * aij;
        }
        bv = bv + y[i] * b[i];
      }
      // System.out.println("av = " + av);
      // System.out.println("bv = " + bv);

      // ZCUBIC
      double c0 = 0.0;
      double c1 = 0.0;
      double c2 = 0.0;
      double c3 = 0.0;

      c0 = -(av * bv - Math.pow(bv, 2.0) - Math.pow(bv, 3.0));
      c1 = av - 3.0 * Math.pow(bv, 2.0) - 2.0 * bv;
      c2 = -(1.0 - bv);
      c3 = 1.0;

      // for(int i=0;i<1;i++) {
      // c0=-(a[i]*b[i]-Math.pow(b[i],2)-Math.pow(b[i],3));
      // c1=a[i]-3.0*Math.pow(b[i],2)-2.0*b[i];
      // c2=-(1.0-b[i]);
      // c3=1.0;
      // }

      // System.out.println("c0 = " +c0);
      // System.out.println("c1 = " +c1);
      // System.out.println("c2 = " +c2);
      // System.out.println("c3 = " +c3);

      // PZEROS
      double OMEGA = 0.0;
      double[] ROOT = {0.0, 0.0, 0.0};
      double[] W = {0.0, 0.0, 0.0};
      double k = 0.0;
      double NRR = 0.0;
      double PHI = 0.0;
      double A0 = c0 / c3;
      double A1 = c1 / c3;
      double A2 = c2 / c3;
      double p = (3 * A1 - Math.pow(A2, 2)) / 3.0;
      double Q = (27.0 * A0 - 9.0 * A1 * A2 + 2.0 * Math.pow(A2, 3.0)) / 27.0;
      double D = (Math.pow(Q, 2)) / 4.0 + (Math.pow(p, 3.0) / 27.0);
      double r = Math.sqrt(Math.pow(Math.abs(p), 3) / 27.0);
      double PI = Math.acos(-1.0);

      if (D < Math.pow(10.0, -16.0)) {
        NRR = 3.0;
        PHI = Math.acos(-Q / (2.0 * r));
        for (int i = 0; i < 3; i++) {
          k = i - 1.0;
          W[i] = 2.0 * Math.pow(r, (1.0 / 3.0)) * Math.cos((PHI + 2.0 * PI * k) / 3.0);
          ROOT[i] = W[i] - A2 / 3.0;
        }
      } else {
        NRR = 1.0;
        if (p < 0.0) {
          OMEGA = Math.asin(2.0 * r / Q);
          PHI = Math.atan(Math.pow((Math.tan(Math.abs(OMEGA) / 2.0)), 1.0 / 3.0));
          if (OMEGA < 0.0) {
            PHI = -PHI;
            W[0] = -2.0 * Math.sqrt(-p / 3.0) / Math.sin(2.0 * PHI);
          }
        } else {
          OMEGA = Math.atan(2.0 * r / Q);
          PHI = Math.atan(Math.pow((Math.tan(Math.abs(OMEGA) / 2.0)), 1.0 / 3.0));
          if (OMEGA < 0.0) {
            PHI = -PHI;
          }
          W[0] = -2.0 * Math.sqrt(p / 3.0) / Math.tan(2.0 * PHI);
        }
        ROOT[0] = W[0] - A2 / 3.0;
        ROOT[1] = 0.0;
        ROOT[2] = 0.0;
      }

      double zv = 0.0;
      // double IERR = 0.0;

      if (NRR == 1.0) {
        zv = ROOT[0];
      } else {
        zv = Math.max(Math.max(ROOT[0], ROOT[1]), ROOT[2]);
      }

      if (zv < Math.pow(10.0, -19.0)) {
        // IERR = 1;
        // System.out.println(IERR);
      }

      // System.out.println("zv = " + zv);

      double VV = 0.0;

      VV = zv * R * T / P;
      // System.out.println("VV = " + VV);

      // Correction of volume
      double[] trans = {0.0, 0.0};
      double[] bh = {0.0, 0.0};
      double[] t0 = {0.0, 0.0};
      double[] ti = {0.0, 0.0};
      double[] zc = {0.0, 0.0};
      double TEV = 0.0;
      double zceos = 0.3074;
      double dk0 = -0.014471;
      double dk1 = 0.067498;
      double dk2 = -0.084852;
      double dk3 = 0.067298;
      double dk4 = -0.017366;
      double dl0 = -10.24470;
      double dl1 = -28.63120;
      double dl2 = 0.0;
      for (int i = 0; i < 2; i++) {
        zc[i] = 0.2890 + w[i] * (-0.0701 - 0.0207 * w[i]);
        ti[i] = (R * Tc[i] / Pc[i]) * (zceos - zc[i]);
        t0[i] =
            (R * Tc[i] / Pc[i]) * (dk0 + w[i] * (dk1 + w[i] * (dk2 + w[i] * (dk3 + w[i] * dk4))));
        bh[i] = dl0 + w[i] * (dl1 + w[i] * dl2);
        trans[i] = t0[i] + (ti[i] - t0[i]) * Math.exp(bh[i] * Math.abs(1.0 - Tr[i]));
        TEV = TEV + y[i] * trans[i];
      }
      VV = VV - TEV;
      // System.out.println("VV = " + VV);
      // System.out.println("TEV = " + TEV);

      // Calculate fugacity coefficient (FC) for all components and phases

      double[] dlnfc = {0.0, 0.0};
      double[] fcv = {0.0, 0.0};

      for (int i = 0; i < 2; i++) {
        dlnfc[i] = (b[i] / bv) * (zv - 1.0) - Math.log(zv - bv) - (av / (2.0 * Math.sqrt(2.0) * bv))
            * (2.0 * sumav[i] / av - b[i] / bv) * Math.log((zv + 2.414 * bv) / (zv - 0.414 * bv));
        fcv[i] = Math.exp(dlnfc[i]);
      }

      // System.out.println("fcv0 = " + fcv[0]);
      // System.out.println("fcv1 = " + fcv[1]);

      double[] fv = {0.0, 0.0};

      for (int i = 0; i < 2; i++) {
        fv[i] = fcv[i] * y[i] * P;
      }

      // System.out.println("fv0 = " + fv[0]);
      // System.out.println("fv1 = " + fv[1]);

      // VCO2INF

      double VCO2INF = 0.0;

      VCO2INF =
          (-159751499.972988 * Math.pow(10.0, -10.0)) + (101831855.926854 * Math.pow(10.0, -10)) * S
              + (18075168.978622 * Math.pow(10.0, -11.0)) * T
              - (787538191.939352 * Math.pow(10.0, -13.0)) * S * T
              - (192886808.345857 * Math.pow(10.0, -11.0)) * (Math.pow(S, 2.0))
              + 142830810.095592 * Math.pow(10.0, -15.0) * S * (Math.pow(T, 2.0))
              + (123450785.102997 * Math.pow(10.0, -13.0)) * T * (Math.pow(S, 2.0))
              - (220053285.910771 * Math.pow(10.0, -16.0)) * (Math.pow(S, 2.0)) * (Math.pow(T, 2.0))
              - 35351000.350961 * Math.pow(10.0, -17.0) * (Math.pow(T, 3.0));

      // System.out.println("VCO2INF = " + VCO2INF);

      double HCO2AST = 0.0;

      HCO2AST =
          (547703618.010975 * Math.pow(10.0, -3.0)) - (237824440.424155 * Math.pow(10.0, -4.0)) * T
              - (108668654.561957 * Math.pow(10.0, -7.0)) * (Math.pow(T, 2.0))
              + (10492428.477532 * Math.pow(10.0, -8.0)) * S * (Math.pow(T, 2.0))
              + (426241410.644264 * Math.pow(10.0, -11.0)) * Math.pow(T, 3.0)
              - (124268021.223715 * Math.pow(10.0, -12.0)) * S * (Math.pow(T, 3.0))
              + (435491737.902085 * Math.pow(10.0, -5.0)) * T * Math.log(T)
              + (19224323.617885 * Math.pow(10.0, -8.0)) * Math.pow(S, 3.0) * T
              - (486711042.327079 * Math.pow(10.0, -9.0)) * T * Math.exp(S)
              + (593013871.824553 * Math.pow(10.0, -10.0)) * S * T * Math.exp(S)
              - (186057141.990893 * Math.pow(10.0, -7.0)) * S * T
              + (13867353.798785 * Math.pow(10.0, -7.0)) * Math.exp(S) * Math.log(T);

      // System.out.println("HCO2AST = " + HCO2AST);

      double HCO2 = 0.0;

      HCO2 = Math.exp(Math.log(HCO2AST) + (VCO2INF * P) / (R * T));

      // System.out.println("HCO2 = " + HCO2);

      double[] fl = {0.0, 0.0};
      fl[0] = x[0] * HCO2;

      // System.out.println("fl0 = " + fl[0]);

      // PHIWSAT

      double PHIWSAT = 0.0;
      if (((9.0 / 5.0) * T - 459.67) > 90.0) {
        PHIWSAT = 0.9958 + 9.68330 * Math.pow(10.0, -5.0) * ((9.0 / 5.0) * T - 459.67)
            - 6.17050 * Math.pow(10.0, -7.0) * Math.pow((9.0 / 5.0) * T - 459.67, 2.0)
            - 3.08333 * Math.pow(10.0, -10.0) * Math.pow((9.0 / 5.0) * T - 459.67, 3.0);
      } else {
        PHIWSAT = 1.0;
      }

      // VWATER

      double Densliq = 0.0;
      double VW = 0.0;
      Densliq = 4.6137 / (Math.pow(0.26214, 1.0 + Math.pow(1.0 - T / 647.29, 0.23072)));
      VW = 1.0 / Densliq;

      // PWSAT

      double PWSAT = 0.0;
      PWSAT = Math.exp(73.649 - 7258.2 / T - 7.3037 * Math.log(T) + 0.0000041653 * Math.pow(T, 2.0))
          / Math.pow(10.0, 5.0);

      // Calculation of H2O fugacity in the aqueous phase

      double Poyntef = 0.0;
      Poyntef = Math.exp(VW * (P - PWSAT) / (R * T));
      fl[1] = x[1] * PHIWSAT * PWSAT * Poyntef;

      // System.out.println("fl1 = " + fl[1]);

      double SUMY1 = 0.0;
      for (int i = 0; i < 2; i++) {
        y[i] = y[i] * fl[i] / fv[i];
        SUMY1 = SUMY1 + y[i];
      }

      double G = 0.0;
      G = SUMY1 - 1.0;

      if (Math.abs((fl[0] - fv[0])) < Math.pow(10.0, -5.0)
          && Math.abs((fl[1] - fv[1])) < Math.pow(10.0, -5.0)
          && Math.abs(G) < Math.pow(10.0, -5.0)) {
        System.out.println("fl0 = " + fl[0]);
        System.out.println("fl1 = " + fl[1]);
        System.out.println("fv0 = " + fv[0]);
        System.out.println("fv1 = " + fv[1]);
        System.out.println("y0 = " + y[0]);
        System.out.println("y1 = " + y[1]);
        System.out.println("P = " + P + " bar ");
        break;
      }
      errorOld = error;
      error = Math.abs((fl[0] - fv[0])) + Math.abs((fl[1] - fv[1])) + Math.abs(G);
      Poldold = Pold;
      Pold = P;

      if (iter < 5) {
        P = P + Math.pow(10.0, -7.0) * P;
      } else {
        P = P - 0.1 * (error - errorOld) / (Pold - Poldold);
      }
      System.out.println("P = " + P + " bar " + " error " + error);
    } while (Math.abs(error) > 1e-6);
    return P;
  }

  /**
   * <p>
   * calcCO2solubility.
   * </p>
   *
   * @param temperature a double
   * @param pressure a double
   * @param salinity a double
   * @return a double
   */
  public double calcCO2solubility(double temperature, double pressure, double salinity) {
    double T = temperature;
    double P = pressure;
    double S = salinity;
    // double Tc1 = 304.2;
    double Tc2 = 647.29;
    // double Pc1 = 73.825;
    double Pc2 = 220.85;
    double c1 = 0;
    double c2 = 0;
    double c3 = 0;
    double c4 = 0;
    double c5 = 0;
    double c6 = 0;
    double c7 = 0;
    double c8 = 0;
    double c9 = 0;
    double c10 = 0;
    double c11 = 0;
    double c12 = 0;
    double c13 = 0;
    double c14 = 0;
    double c15 = 0;
    // double c[]= {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
    double[] parcdpsat = {85.530, -3481.3, -11.336, 0.021505, 1.0};
    // for (int i=0;i<parcdpsat.length;i++)
    // {
    // System.out.println(parcdpsat[i]);
    // }
    double PCO2sat = 0.0;
    PCO2sat = Math.exp(parcdpsat[0] + (parcdpsat[1] / T) + (parcdpsat[2] * Math.log(T))
        + (parcdpsat[3] * (Math.pow(T, parcdpsat[4])))) / 100000;
    // System.out.println(PCO2sat);
    if (T >= 273.0 && T < 305.0 && P <= PCO2sat) {
      c1 = 1.0;
      c2 = 4.7586835 * Math.pow(10.0, -3.0);
      c3 = -3.3569963 * Math.pow(10.0, -6.0);
      c4 = 0.0;
      c5 = -1.3179396;
      c6 = -3.8389101 * Math.pow(10.0, -6.0);
      c7 = 0.0;
      c8 = 2.2815104 * Math.pow(10.0, -3.0);
      c9 = 0.0;
      c10 = 0.0;
      c11 = 0.0;
      c12 = 0.0;
      c13 = 0.0;
      c14 = 0.0;
      c15 = 0.0;
    } else if ((T >= 305.0 && T <= 405.0 && P <= (75.0 + (T - 305.0) * 1.25))) {
      c1 = 1.0;
      c2 = 4.7586835 * Math.pow(10.0, -3.0);
      c3 = -3.3569963 * Math.pow(10.0, -6.0);
      c4 = 0.0;
      c5 = -1.3179396;
      c6 = -3.8389101 * Math.pow(10.0, -6.0);
      c7 = 0.0;
      c8 = 2.2815104 * Math.pow(10.0, -3.0);
      c9 = 0.0;
      c10 = 0.0;
      c11 = 0.0;
      c12 = 0.0;
      c13 = 0.0;
      c14 = 0.0;
      c15 = 0.0;
    } else if (T > 405.0 && P <= 200.0) {
      c1 = 1.0;
      c2 = 4.7586835 * Math.pow(10.0, -3.0);
      c3 = -3.3569963 * Math.pow(10.0, -6.0);
      c4 = 0.0;
      c5 = -1.3179396;
      c6 = -3.8389101 * Math.pow(10.0, -6.0);
      c7 = 0.0;
      c8 = 2.2815104 * Math.pow(10.0, -3.0);
      c9 = 0.0;
      c10 = 0.0;
      c11 = 0.0;
      c12 = 0.0;
      c13 = 0.0;
      c14 = 0.0;
      c15 = 0.0;
    } else if (T >= 273.0 && T < 305.0 && P <= 1000 && P > PCO2sat) {
      c1 = -7.1734882 * Math.pow(10.0, -1.0);
      c2 = 1.5985379 * Math.pow(10.0, -4.0);
      c3 = -4.9286471 * Math.pow(10.0, -7.0);
      c4 = 0.0;
      c5 = 0.0;
      c6 = -2.7855285 * Math.pow(10.0, -7.0);
      c7 = 1.1877015 * Math.pow(10.0, -9.0);
      c8 = 0.0;
      c9 = 0.0;
      c10 = 0.0;
      c11 = 0.0;
      c12 = -96.539512;
      c13 = 4.4774938 * Math.pow(10.0, -1.0);
      c14 = 101.81078;
      c15 = 5.3783879 * Math.pow(10, -6.0);
    } else if (T >= 305.0 && T <= 340.0 && P <= 1000.0 && P > (75.0 + (T - 305.0)) * 1.25) {
      c1 = -7.1734882 * Math.pow(10.0, -1.0);
      c2 = 1.5985379 * Math.pow(10.0, -4.0);
      c3 = -4.9286471 * Math.pow(10.0, -7.0);
      c4 = 0.0;
      c5 = 0.0;
      c6 = -2.7855285 * Math.pow(10.0, -7.0);
      c7 = 1.1877015 * Math.pow(10.0, -9.0);
      c8 = 0.0;
      c9 = 0.0;
      c10 = 0.0;
      c11 = 0.0;
      c12 = -96.539512;
      c13 = 4.4774938 * Math.pow(10.0, -1.0);
      c14 = 101.81078;
      c15 = 5.3783879 * Math.pow(10, -6.0);
    } else if (T >= 273.0 && T <= 340.0 && P > 1000.0) {
      c1 = -6.5129019 * Math.pow(10.0, -2.0);
      c2 = -2.1429977 * Math.pow(10.0, -4.0);
      c3 = -1.144493 * Math.pow(10.0, -6.0);
      c4 = 0.0;
      c5 = 0.0;
      c6 = -1.1558081 * Math.pow(10.0, -7.0);
      c7 = 1.195237 * Math.pow(10.0, -9.0);
      c8 = 0.0;
      c9 = 0.0;
      c10 = 0.0;
      c11 = 0.0;
      c12 = -221.34306;
      c13 = 0.0;
      c14 = 71.820393;
      c15 = 6.6089246 * Math.pow(10, -6.0);
    } else if (T > 340 && T < 405 && P <= 1000.0 && P > (75.0 + (T - 305.0) * 1.25)) {
      c1 = 5.0383896;
      c2 = -4.4257744 * Math.pow(10.0, -3);
      c3 = 0.0;
      c4 = 1.9572733;
      c5 = 0.0;
      c6 = 2.4223436 * Math.pow(10.0, -6.0);
      c7 = 0.0;
      c8 = -9.3796135 * Math.pow(10.0, -4.0);
      c9 = -1.5026030;
      c10 = 3.027224 * Math.pow(10.0, -3.0);
      c11 = -31.377342;
      c12 = -12.847063;
      c13 = 0.0;
      c14 = 0.0;
      c15 = -1.5056648 * Math.pow(10, -5.0);
    } else if (T >= 405.0 && T <= 435.0 && P <= 1000.0 && P > 200.0) {
      c1 = 5.0383896;
      c2 = -4.4257744 * Math.pow(10.0, -3);
      c3 = 0.0;
      c4 = 1.9572733;
      c5 = 0.0;
      c6 = 2.4223436 * Math.pow(10.0, -6.0);
      c7 = 0.0;
      c8 = -9.3796135 * Math.pow(10.0, -4.0);
      c9 = -1.5026030;
      c10 = 3.027224 * Math.pow(10.0, -3.0);
      c11 = -31.377342;
      c12 = -12.847063;
      c13 = 0.0;
      c14 = 0.0;
      c15 = -1.5056648 * Math.pow(10, -5.0);
    } else if (T > 340 && T <= 435.0 && P > 1000.0) {
      c1 = -16.063152;
      c2 = -2.705799 * Math.pow(10.0, -3);
      c3 = 0.0;
      c4 = 1.4119239 * Math.pow(10.0, -1.0);
      c5 = 0.0;
      c6 = 8.1132965 * Math.pow(10.0, -7.0);
      c7 = 0.0;
      c8 = -1.1453082 * Math.pow(10.0, -4.0);
      c9 = 2.3895671;
      c10 = 5.0527457 * Math.pow(10.0, -4.0);
      c11 = -17.76346;
      c12 = 985.92232;
      c13 = 0.0;
      c14 = 0.0;
      c15 = -5.4965256 * Math.pow(10, -7.0);
    } else if (T > 435.0 && P > 200.0) {
      c1 = -1.569349 * Math.pow(10.0, -1.0);
      c2 = 4.4621407 * Math.pow(10.0, -4);
      c3 = -9.1080591 * Math.pow(10.0, -7.0);
      c4 = 0.0;
      c5 = 0.0;
      c6 = 1.0647399 * Math.pow(10.0, -7.0);
      c7 = 2.4273357 * Math.pow(10.0, -10.0);
      c8 = 0.0;
      c9 = 3.5874255 * Math.pow(10.0, -1.0);
      c10 = 6.331971 * Math.pow(10.0, -5.0);
      c11 = -249.89661;
      c12 = 0.0;
      c13 = 0.0;
      c14 = 888.768;
      c15 = -6.6348003 * Math.pow(10, -7.0);
    }

    // System.out.println(c1);
    // System.out.println("PCO2sat = " + PCO2sat);

    double fCO2 = 0.0;
    fCO2 = c1 + (c2 + c3 * T + c4 / T + c5 / (T - 150.0)) * P
        + (c6 + c7 * T + c8 / T) * Math.pow(P, 2) + (c9 + c10 * T + c11 / T) * Math.log(P)
        + (c12 + c13 * T) / P + c14 / T + c15 * Math.pow(T, 2);
    // System.out.println("fCO2 = " + fCO2);

    double chempotliqCO2RT = 0.0;
    chempotliqCO2RT = 28.9447706 - 0.0354581768 * T - 4770.67077 / T
        + 1.02782768 * Math.pow(10.0, -5.0) * Math.pow(T, 2.0) + 33.8126098 / (630.0 - T)
        + 0.009040371 * P - 0.00114934 * P * Math.log(T) - 0.307405726 * P / T
        - 0.090730149 * P / (630.0 - T) + 0.000932713 * Math.pow(P, 2) / (Math.pow((630.0 - T), 2));
    // System.out.println("chempotliqCO2RT = " + chempotliqCO2RT);

    double lamdaCO2Na = 0.0;
    lamdaCO2Na = -0.411370585 + 0.000607632 * T + 97.5347708 / T - 0.023762247 * P / T
        + 0.017065624 * P / (630.0 - T) + 1.41335834 * Math.pow(10.0, -5.0) * T * Math.log(P);
    // System.out.println("lamdaCO2Na = " + lamdaCO2Na);

    double zetaCO2NaCl = 0.0;
    zetaCO2NaCl = 0.00033639 - 1.9829898 * Math.pow(10.0, -5.0) * T + 0.002122208 * P / T
        - 0.005248733 * P / (630. - T);
    // System.out.println("zetaCO2NaCl = " + zetaCO2NaCl);

    double tH2O = 0.0;
    tH2O = (T - Tc2) / Tc2;

    double PH2O = 0.0;
    PH2O = (Pc2 * T / Tc2) * (1.0 - 38.640844 * Math.pow(-tH2O, 1.9) + 5.8948420 * tH2O
        + 59.876516 * Math.pow(tH2O, 2.0) + 26.654627 * Math.pow(tH2O, 3.0)
        + 10.637097 * Math.pow(tH2O, 4.0));

    double yCO2 = 0.0;
    yCO2 = (P - PH2O) / P;

    double mCO2 = 0.0;
    mCO2 = (yCO2 * P) / Math.exp((-Math.log(fCO2) + chempotliqCO2RT + (2.0) * lamdaCO2Na * S
        + zetaCO2NaCl * Math.pow(S, 2.0)));
    // System.out.println("mCO2 = " + mCO2 + "b mol/kg solvent ");

    double xCO2 = 0.0;
    xCO2 = mCO2 / (1000. / 18. + mCO2);
    // System.out.println("xCO2 = " + xCO2 + " mole fraction ");

    return xCO2;
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
    DuanSun testDuanSun = new DuanSun();

    double CO2solubility = testDuanSun.calcCO2solubility(273.15 + 30.0, 100.0, 3.00);

    System.out.println("CO2solubility " + CO2solubility / 0.01802 + " mol/kg");

    // double CO2solubility2 = testDuanSun.bublePointPressure(298.15, CO2solubility,
    // 2.0);

    // System.out.println("Total pressure " + CO2solubility2 + " bara");

    SystemInterface fluid1 = new SystemElectrolyteCPAstatoil(298.15, 10.0);
    fluid1.addComponent("CO2", 0.05, "kg/sec");
    // fluid1.addComponent("oxygen", 1.0, "mol/sec");
    // fluid1.addComponent("methane", 1.0, "mol/sec");
    fluid1.addComponent("water", 0.5, "kg/sec");
    fluid1.addComponent("Na+", 0.1, "mol/sec");
    fluid1.addComponent("Cl-", 0.1, "mol/sec");
    fluid1.setMixingRule(10);
    ThermodynamicOperations thermoOPs = new ThermodynamicOperations(fluid1);
    try {
      thermoOPs.TPflash();
      // fluid1.init(0);
      fluid1.display();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    // fluid1.setMolarComposition(new double[] {0.5, 0.5, 0.0, 0.0});
    fluid1.init(1);
    try {
      thermoOPs.TPflash();
      // fluid1.init(0);
      fluid1.display();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    String fluidname = "" + "";
    fluid1.saveObjectToFile(fluidname, fluidname);

    fluid1.setPhaseIndex(0, 1);
    // fluid1.save
    System.out
        .println("CO2 in liquid " + fluid1.getPhase(0).getComponent(0).getLogFugacityCoefficient());
    System.out
        .println("CO2 in liquid " + fluid1.getPhase(0).getComponent(0).getFugacityCoefficient());
    System.out.println("CO2 in liquid " + fluid1.getPhase("aqueous").getComponent("CO2").getx());
  }
}
