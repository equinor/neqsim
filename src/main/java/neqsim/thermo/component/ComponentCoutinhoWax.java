package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 * Wax component model based on the Coutinho predictive UNIQUAC approach.
 *
 * <p>
 * Implements the solid-liquid equilibrium model of Coutinho (1998, 2001) for wax precipitation in petroleum fluids. The
 * model uses the UNIQUAC local composition framework for the solid-phase activity coefficient, with predictive
 * interaction parameters derived from pure-component sublimation energies and the shorter-chain interaction rule. The
 * liquid reference is supplied by the selected equation of state.
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
 * The solid-phase activity coefficient is calculated using the predictive UNIQUAC equation with combinatorial and
 * residual contributions. The UNIQUAC interaction parameters lambda_ij for the solid phase are estimated from the
 * sublimation enthalpies of pure components.
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Coutinho, J.A.P., "Predictive UNIQUAC: A New Model for the Description of Multiphase Solid-Liquid Equilibria in
 * Complex Hydrocarbon Mixtures," Ind. Eng. Chem. Res., 37, 4870-4875, 1998.</li>
 * <li>Coutinho, J.A.P. and Daridon, J.-L., "Low-Pressure Modeling of Wax Formation in Crude Oils," Energy &amp; Fuels,
 * 15, 1454-1460, 2001.</li>
 * <li>Coutinho, J.A.P. and Stenby, E.H., "Predictive Local Composition Models for Solid/Liquid Equilibrium in n-Alkane
 * Systems," Ind. Eng. Chem. Res., 35, 918-925, 1996.</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class ComponentCoutinhoWax extends ComponentSolid {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Coordination number for UNIQUAC model. Standard value is 10 as recommended by Abrams and Prausnitz (1975).
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
   * Calculates the solid fugacity coefficient using the Coutinho predictive UNIQUAC model. The solid-phase fugacity is
   * computed from the liquid reference fugacity, heat of fusion, heat capacity difference, and the UNIQUAC solid-phase
   * activity coefficient.
   * </p>
   */
  @Override
  public double fugcoef2(PhaseInterface phase1) {
    if (!isWaxFormer()) {
      return WaxModelCorrelations.solidFugacityCoefficient(this, phase1, 0.0);
    }
    return WaxModelCorrelations.solidFugacityCoefficient(this, phase1, calcLnGammaUNIQUAC(phase1));
  }

  /**
   * Calculates the natural logarithm of the UNIQUAC activity coefficient for this component in the solid (wax) phase.
   *
   * <p>
   * The UNIQUAC model splits the activity coefficient into a combinatorial (entropic) term and a residual (enthalpic)
   * term:
   * </p>
   *
   * <pre>
   * ln(gamma_i) = ln(gamma_i ^ comb) + ln(gamma_i ^ res)
   * </pre>
   *
   * <p>
   * The combinatorial term accounts for differences in molecular size and shape, while the residual term accounts for
   * energetic interactions between unlike molecules in the solid solution.
   * </p>
   *
   * @param phase1 The phase for which to calculate the activity coefficient.
   * @return The natural logarithm of the UNIQUAC activity coefficient.
   */
  public double calcLnGammaUNIQUAC(PhaseInterface phase1) {
    if (!isWaxFormer()) {
      return 0.0;
    }
    double[] fractions = WaxModelCorrelations.composition(phase1);
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
      double xi = fractions[i];
      sumXR += xi * ri[i];
      sumXQ += xi * qi[i];
    }

    int iThis = getComponentNumber();
    double phiI = ri[iThis] / (sumXR > 0 ? sumXR : 1.0);
    double thetaI = qi[iThis] / (sumXQ > 0 ? sumXQ : 1.0);

    // Combinatorial contribution (Staverman-Guggenheim)
    // ln(gamma_comb) = ln(Phi_i/x_i) + z/2 * q_i * ln(Theta_i/Phi_i)
    // + l_i - Phi_i/x_i * sum(x_j * l_j)
    double[] li = new double[ncomp];
    for (int i = 0; i < ncomp; i++) {
      li[i] = Z_COORD / 2.0 * (ri[i] - qi[i]) - (ri[i] - 1.0);
    }

    double lnGammaComb = 0.0;
    if (phiI > 0 && thetaI > 0) {
      double sumXL = 0.0;
      for (int j = 0; j < ncomp; j++) {
        sumXL += fractions[j] * li[j];
      }
      lnGammaComb = Math.log(phiI) + Z_COORD / 2.0 * qi[iThis] * Math.log(thetaI / phiI) + li[iThis] - phiI * sumXL;
    }

    // Residual contribution
    // ln(gamma_res) = q_i * [1 - ln(sum_j theta_j * tau_ji)
    // - sum_j (theta_j * tau_ij / sum_k theta_k * tau_kj)]
    // tau_ij = exp(-(lambda_ij - lambda_jj) / (R*T)); tau_ii = 1
    // lambda_ij : interaction energy parameters from sublimation enthalpies

    // Calculate tau matrix
    double[][] tau = new double[ncomp][ncomp];
    for (int i = 0; i < ncomp; i++) {
      for (int j = 0; j < ncomp; j++) {
        if (!phase1.getComponent(i).isWaxFormer() || !phase1.getComponent(j).isWaxFormer()) {
          tau[i][j] = 1.0;
          continue;
        }
        double lambdaIJ = calcLambdaIJ(phase1, i, j);
        double lambdaJJ = calcLambdaIJ(phase1, j, j);
        tau[i][j] = WaxModelCorrelations.coefficient(-(lambdaIJ - lambdaJJ) / (R * tempK), this, phase1);
      }
    }

    // Theta array
    double[] theta = new double[ncomp];
    for (int i = 0; i < ncomp; i++) {
      theta[i] = fractions[i] * qi[i] / (sumXQ > 0 ? sumXQ : 1.0);
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
   * The pair energy is the self-interaction energy of the shorter-chain n-alkane: lambda_ij = lambda_ji =
   * lambda_short,short. The self energy is -2/Z * (DH_sub - RT). See Coutinho et al., Fluid Phase Equilibria 233
   * (2005), 28-33, Eq. 13, DOI 10.1016/j.fluid.2005.04.007.
   * </p>
   *
   * @param phase1 Current phase
   * @param comp1 Index of component 1
   * @param comp2 Index of component 2
   * @return The UNIQUAC interaction parameter lambda_ij [J/mol]
   */
  public double calcLambdaIJ(PhaseInterface phase1, int comp1, int comp2) {
    int shorter = phase1.getComponent(comp1).getMolarMass() <= phase1.getComponent(comp2).getMolarMass() ? comp1
        : comp2;
    double cohesiveEnergy = calcSublimationEnthalpy(phase1, shorter) - R * phase1.getTemperature();
    if (!Double.isFinite(cohesiveEnergy) || cohesiveEnergy <= 0.0) {
      throw new IllegalStateException("Invalid UNIQUAC solid cohesive energy");
    }
    return -2.0 / Z_COORD * cohesiveEnergy;
  }

  /**
   * Calculates the sublimation enthalpy of a pure n-alkane at the given temperature.
   *
   * <p>
   * DH_sub = DH_vap(T) + DH_fus + DH_trans
   * </p>
   *
   * <p>
   * where DH_vap is the enthalpy of vaporization, DH_fus is the enthalpy of fusion, and DH_trans is the solid-solid
   * transition enthalpy (relevant for odd-numbered n-alkanes).
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

    double omega = 0.0520750 + 0.0448946 * cn - 0.000185397 * cn * cn;
    double deltaHvap = WaxModelCorrelations.vaporizationEnthalpy(tempK, comp.getTC(), omega);

    // Total sublimation enthalpy
    return deltaHvap + deltaHf + deltaHtrans;
  }

  /**
   * Estimates the effective carbon number for a component.
   *
   * <p>
   * Uses the molecular weight to estimate carbon number, assuming the component is approximately an n-alkane (CnH2n+2):
   * MW = 14.027 * n + 2.016.
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
