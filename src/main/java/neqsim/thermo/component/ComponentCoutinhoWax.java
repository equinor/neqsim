package neqsim.thermo.component;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * Wax component model based on the Coutinho predictive UNIQUAC approach.
 *
 * <p>
 * Implements the solid-liquid equilibrium model of Coutinho (1998, 2001) for wax precipitation in
 * petroleum fluids. The model uses the UNIQUAC local composition framework for the solid-phase
 * activity coefficient, with predictive interaction parameters derived from the
 * Hildebrand-Scatchard regular solution theory and the Wilson equation for the liquid phase.
 * </p>
 *
 * <p>
 * The solid-liquid equilibrium condition for each component i is:
 * </p>
 *
 * <pre>
 * ln(x_i ^ s * gamma_i ^ s) = ln(x_i ^ l * gamma_i ^ l) - (DeltaH_f_i / (R * T)) * (1 - T / T_f_i)
 *     + (DeltaCp_SL_i / R) * (T_f_i / T - 1 - ln(T_f_i / T))
 * </pre>
 *
 * <p>
 * The solid-phase activity coefficient is calculated using the predictive UNIQUAC equation with
 * combinatorial and residual contributions. The UNIQUAC interaction parameters lambda_ij for the
 * solid phase are estimated from the sublimation enthalpies of pure components.
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Coutinho, J.A.P., "Predictive UNIQUAC: A New Model for the Description of Multiphase
 * Solid-Liquid Equilibria in Complex Hydrocarbon Mixtures," Ind. Eng. Chem. Res., 37, 4870-4875,
 * 1998.</li>
 * <li>Coutinho, J.A.P. and Daridon, J.-L., "Low-Pressure Modeling of Wax Formation in Crude Oils,"
 * Energy &amp; Fuels, 15, 1454-1460, 2001.</li>
 * <li>Coutinho, J.A.P. and Stenby, E.H., "Predictive Local Composition Models for Solid/Liquid
 * Equilibrium in n-Alkane Systems," Ind. Eng. Chem. Res., 35, 918-925, 1996.</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class ComponentCoutinhoWax extends ComponentSolid {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ComponentCoutinhoWax.class);

  /**
   * Coordination number for UNIQUAC model. Standard value is 10 as recommended by Abrams and
   * Prausnitz (1975).
   */
  private static final double Z_COORD = 10.0;

  /**
   * Constructor for ComponentCoutinhoWax.
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentCoutinhoWax(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase1) {
    if (!isWaxFormer()) {
      fugacityCoefficient = 1.0e50;
      return fugacityCoefficient;
    }
    return fugcoef2(phase1);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Calculates the solid fugacity coefficient using the Coutinho predictive UNIQUAC model. The
   * solid-phase fugacity is computed from the liquid reference fugacity, heat of fusion, heat
   * capacity difference, and the UNIQUAC solid-phase activity coefficient.
   * </p>
   */
  @Override
  public double fugcoef2(PhaseInterface phase1) {
    try {
      refPhase.setTemperature(phase1.getTemperature());
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    refPhase.setPressure(phase1.getPressure());
    refPhase.init(refPhase.getNumberOfMolesInPhase(), 1, 1, PhaseType.LIQUID, 1.0);
    refPhase.getComponent(0).fugcoef(refPhase);

    double liquidPhaseFugacity =
        refPhase.getComponent(0).getFugacityCoefficient() * refPhase.getPressure();

    double tempK = phase1.getTemperature();
    double tfus = getTriplePointTemperature();
    double deltaHf = getHeatOfFusion();

    // Heat capacity difference solid-liquid (Coutinho, 2001, Eq. 4)
    // DeltaCp_SL = 0.3033 * MW - 4.635e-4 * MW * T [cal/mol/K]
    // converted to J/mol/K by multiplying by 4.184
    double mw = getMolarMass() * 1000.0; // g/mol
    double deltaCpSL = (0.3033 * mw - 4.635e-4 * mw * tempK) * 4.184; // J/(mol*K)

    // Solid-liquid volume change (Poynting correction)
    // At moderate pressures this is negligible - included for completeness
    double liquidDensity = refPhase.getMolarVolume();
    double solidDensity = liquidDensity * 0.9;
    double deltaVSL = solidDensity - liquidDensity; // m3/mol
    double refPressure = 1.0; // bara

    // UNIQUAC solid-phase activity coefficient
    double lnGammaSolid = calcLnGammaUNIQUAC(phase1);

    // Solid fugacity: Eq. (1) from Coutinho (1998)
    // ln(f_s) = ln(x * f_liq) - DH/(RT)*(1 - T/Tf) + DCp/R*(Tf/T - 1 - ln(Tf/T))
    // - DV*(P - Pref)/(RT) + ln(gamma_s)
    double thermTerm = -deltaHf / (R * tempK) * (1.0 - tempK / tfus);
    double cpTerm = deltaCpSL / R * (tfus / tempK - 1.0 - Math.log(tfus / tempK));
    double pvTerm = -deltaVSL * (phase1.getPressure() - refPressure) * 1e5 / (R * tempK);

    SolidFug = getx() * liquidPhaseFugacity * Math.exp(thermTerm + cpTerm + pvTerm)
        * Math.exp(lnGammaSolid);

    fugacityCoefficient = SolidFug / (phase1.getPressure() * getx());
    return fugacityCoefficient;
  }

  /**
   * Calculates the natural logarithm of the UNIQUAC activity coefficient for this component in the
   * solid (wax) phase.
   *
   * <p>
   * The UNIQUAC model splits the activity coefficient into a combinatorial (entropic) term and a
   * residual (enthalpic) term:
   * </p>
   *
   * <pre>
   * ln(gamma_i) = ln(gamma_i ^ comb) + ln(gamma_i ^ res)
   * </pre>
   *
   * <p>
   * The combinatorial term accounts for differences in molecular size and shape, while the residual
   * term accounts for energetic interactions between unlike molecules in the solid solution.
   * </p>
   *
   * @param phase1 The phase for which to calculate the activity coefficient.
   * @return The natural logarithm of the UNIQUAC activity coefficient.
   */
  public double calcLnGammaUNIQUAC(PhaseInterface phase1) {
    int ncomp = phase1.getNumberOfComponents();
    double tempK = phase1.getTemperature();

    // Calculate UNIQUAC r and q parameters for all components
    // r_i ~ V_i / 15.17 (van der Waals volume ratio)
    // q_i ~ A_i / 2.5e9 (van der Waals area ratio)
    // Using Bondi group contribution approach for n-alkanes:
    // r = 0.6744 * CN + 0.4534, q = 0.5400 * CN + 0.6160 (Fredenslund et al., 1975)
    double[] ri = new double[ncomp];
    double[] qi = new double[ncomp];

    for (int i = 0; i < ncomp; i++) {
      double cn = getCarbonNumber(phase1.getComponent(i));
      ri[i] = 0.6744 * cn + 0.4534;
      qi[i] = 0.5400 * cn + 0.6160;
    }

    // Volume fractions (Phi) and area fractions (Theta)
    double sumXR = 0.0;
    double sumXQ = 0.0;
    for (int i = 0; i < ncomp; i++) {
      double xi = phase1.getComponent(i).getx();
      sumXR += xi * ri[i];
      sumXQ += xi * qi[i];
    }

    int iThis = getComponentNumber();
    double phiI = ri[iThis] / (sumXR > 0 ? sumXR : 1.0);
    double thetaI = qi[iThis] / (sumXQ > 0 ? sumXQ : 1.0);
    double xI = phase1.getComponent(iThis).getx();

    // Combinatorial contribution (Staverman-Guggenheim)
    // ln(gamma_comb) = ln(Phi_i/x_i) + z/2 * q_i * ln(Theta_i/Phi_i)
    // + l_i - Phi_i/x_i * sum(x_j * l_j)
    double[] li = new double[ncomp];
    for (int i = 0; i < ncomp; i++) {
      li[i] = Z_COORD / 2.0 * (ri[i] - qi[i]) - (ri[i] - 1.0);
    }

    double lnGammaComb = 0.0;
    if (xI > 1e-100 && phiI > 0 && thetaI > 0) {
      double sumXL = 0.0;
      for (int j = 0; j < ncomp; j++) {
        sumXL += phase1.getComponent(j).getx() * li[j];
      }
      lnGammaComb = Math.log(phiI / xI) + Z_COORD / 2.0 * qi[iThis] * Math.log(thetaI / phiI)
          + li[iThis] - phiI / xI * sumXL;
    }

    // Residual contribution
    // ln(gamma_res) = q_i * [1 - ln(sum_j theta_j * tau_ji)
    // - sum_j (theta_j * tau_ij / sum_k theta_k * tau_kj)]
    // tau_ij = exp(-lambda_ij / (R*T))
    // lambda_ij : interaction energy parameters from sublimation enthalpies

    // Calculate tau matrix
    double[][] tau = new double[ncomp][ncomp];
    for (int i = 0; i < ncomp; i++) {
      for (int j = 0; j < ncomp; j++) {
        double lambdaIJ = calcLambdaIJ(phase1, i, j);
        tau[i][j] = Math.exp(-lambdaIJ / (R * tempK));
      }
    }

    // Theta array
    double[] theta = new double[ncomp];
    for (int i = 0; i < ncomp; i++) {
      theta[i] = phase1.getComponent(i).getx() * qi[i] / (sumXQ > 0 ? sumXQ : 1.0);
    }

    // Residual term
    double sum1 = 0.0;
    for (int j = 0; j < ncomp; j++) {
      sum1 += theta[j] * tau[j][iThis];
    }

    double sum2 = 0.0;
    for (int j = 0; j < ncomp; j++) {
      double denomJ = 0.0;
      for (int k = 0; k < ncomp; k++) {
        denomJ += theta[k] * tau[k][j];
      }
      if (denomJ > 0) {
        sum2 += theta[j] * tau[iThis][j] / denomJ;
      }
    }

    double lnGammaRes = 0.0;
    if (sum1 > 0) {
      lnGammaRes = qi[iThis] * (1.0 - Math.log(sum1) - sum2);
    }

    return lnGammaComb + lnGammaRes;
  }

  /**
   * Calculates the UNIQUAC binary interaction parameter lambda_ij for the solid phase.
   *
   * <p>
   * Following Coutinho (1998), the interaction parameters are estimated from the sublimation
   * enthalpies using the geometric mean combining rule. For the solid phase, the interaction energy
   * between molecules i and j is related to the geometric mean of their sublimation enthalpies:
   * </p>
   *
   * <pre>
   * lambda_ij = lambda_ji = -(2 / Z) * sqrt((DH_sub_i - RT) * (DH_sub_j - RT))
   *     + (1 - alpha_ij) * (1 / Z) * ((DH_sub_i - RT) + (DH_sub_j - RT))
   * </pre>
   *
   * <p>
   * where alpha_ij is a non-randomness correction parameter. For equal chain molecules, lambda_ii =
   * -(2/Z) * (DH_sub_i - RT). The deviation from the geometric mean rule is captured by the excess
   * parameter alpha_ij which is zero for same-size molecules and increases with size difference.
   * </p>
   *
   * @param phase1 Current phase
   * @param comp1 Index of component 1
   * @param comp2 Index of component 2
   * @return The UNIQUAC interaction parameter lambda_ij [J/mol]
   */
  public double calcLambdaIJ(PhaseInterface phase1, int comp1, int comp2) {
    double tempK = phase1.getTemperature();

    double dhSub1 = calcSublimationEnthalpy(phase1, comp1);
    double dhSub2 = calcSublimationEnthalpy(phase1, comp2);

    // Self-interaction
    if (comp1 == comp2) {
      return -2.0 / Z_COORD * (dhSub1 - R * tempK);
    }

    // Cross-interaction (modified Berthelot combining rule)
    // Coutinho (1998) Eq. 8-9
    double eps1 = dhSub1 - R * tempK;
    double eps2 = dhSub2 - R * tempK;

    if (eps1 < 0) {
      eps1 = 0;
    }
    if (eps2 < 0) {
      eps2 = 0;
    }

    // Geometric mean for unlike interactions
    double lambdaIJ = -2.0 / Z_COORD * Math.sqrt(eps1 * eps2);

    return lambdaIJ;
  }

  /**
   * Calculates the sublimation enthalpy of a pure n-alkane at the given temperature.
   *
   * <p>
   * DH_sub = DH_vap(T) + DH_fus + DH_trans
   * </p>
   *
   * <p>
   * where DH_vap is the enthalpy of vaporization, DH_fus is the enthalpy of fusion, and DH_trans is
   * the solid-solid transition enthalpy (relevant for odd-numbered n-alkanes).
   * </p>
   *
   * @param phase1 Current phase
   * @param compIndex Component index
   * @return Sublimation enthalpy [J/mol]
   */
  public double calcSublimationEnthalpy(PhaseInterface phase1, int compIndex) {
    ComponentInterface comp = phase1.getComponent(compIndex);
    double mw = comp.getMolarMass() * 1000.0; // g/mol
    double tempK = phase1.getTemperature();

    // Carbon number estimate
    double cn = getCarbonNumber(comp);

    // Melting temperature (Pedersen correlation)
    double tfus = 374.5 + 0.02617 * mw - 20172.0 / mw;
    if (comp.getTriplePointTemperature() > 0) {
      tfus = comp.getTriplePointTemperature();
    }

    // Heat of fusion (Pedersen, converted to J/mol)
    double deltaHf = 0.1426 * mw * tfus / 0.238845; // J/mol
    if (comp.getHeatOfFusion() > 0) {
      deltaHf = comp.getHeatOfFusion();
    }

    // Solid-solid transition enthalpy (Won, 1989)
    // For odd n-alkanes with n >= 9 and even n-alkanes
    double deltaHtrans = 0.0;
    double totalTransH = (3.7791 * cn - 12.654) * 1000.0; // J/mol
    if (totalTransH > deltaHf) {
      deltaHtrans = totalTransH - deltaHf;
    }

    // Vaporization enthalpy (Morgan-Kobayashi, 1994)
    // Using Pitzer 3-parameter correlation
    double tc = comp.getTC();
    if (tc < tempK + 1) {
      tc = tempK + 100;
    }
    double x = 1.0 - tempK / tc;
    double deltaHvap0 = 5.2804 * Math.pow(x, 0.3333) + 12.865 * Math.pow(x, 0.8333)
        + 1.171 * Math.pow(x, 1.2083) - 13.166 * x + 0.4858 * x * x - 1.088 * x * x * x;
    double omega = 0.0520750 + 0.0448946 * cn - 0.000185397 * cn * cn;
    double deltaHvap1 = 0.80022 * Math.pow(x, 0.3333) + 273.23 * Math.pow(x, 0.8333)
        + 465.08 * Math.pow(x, 1.2083) - 638.51 * x - 145.12 * x * x - 74.049 * x * x * x;
    double deltaHvap2 = 7.2543 * Math.pow(x, 0.3333) - 346.45 * Math.pow(x, 0.8333)
        - 610.48 * Math.pow(x, 1.2083) + 839.89 * x + 160.05 * x * x - 50.711 * x * x * x;

    double deltaHvap = R * tc * (deltaHvap0 + omega * deltaHvap1 + omega * omega * deltaHvap2);

    // Total sublimation enthalpy
    return deltaHvap + deltaHf + deltaHtrans;
  }

  /**
   * Estimates the effective carbon number for a component.
   *
   * <p>
   * Uses the molecular weight to estimate carbon number, assuming the component is approximately an
   * n-alkane (CnH2n+2): MW = 14.027 * n + 2.016.
   * </p>
   *
   * @param comp Component interface
   * @return Estimated carbon number
   */
  private double getCarbonNumber(ComponentInterface comp) {
    double mw = comp.getMolarMass() * 1000.0; // g/mol
    double cn = (mw - 2.016) / 14.027;
    if (cn < 1.0) {
      cn = 1.0;
    }
    return cn;
  }
}
