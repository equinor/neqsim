package neqsim.physicalproperties.methods.gasphysicalproperties.conductivity;

import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * ChungConductivityMethod class for dilute-gas thermal conductivity using the Chung et al.
 * correlation (1984, 1988).
 * </p>
 *
 * <p>
 * The method computes its own Chung dilute-gas viscosity internally so that it is independent of
 * whatever viscosity model is active on the phase. This avoids the unit-mismatch bug where the PFCT
 * viscosity model returns values in Pa·s while the original code assumed Chung μP units.
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Chung, T.H., Lee, L.L., Starling, K.E. (1984). Ind. Eng. Chem. Fundam. 23, 8-13.</li>
 * <li>Chung, T.H., Ajlan, M., Lee, L.L., Starling, K.E. (1988). Ind. Eng. Chem. Res. 27,
 * 671-679.</li>
 * <li>Poling, B.E., Prausnitz, J.M., O'Connell, J.P. (2001). The Properties of Gases and Liquids,
 * 5th ed, Chap. 10.</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ChungConductivityMethod extends Conductivity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  double conductivity = 0;
  public double[] pureComponentConductivity;

  // Neufeld collision integral constants (eq. 9-4.3 TPoLG)
  private static final double NF_A = 1.16145;
  private static final double NF_B = 0.14874;
  private static final double NF_C = 0.52487;
  private static final double NF_D = 0.77320;
  private static final double NF_E = 2.16178;
  private static final double NF_F = 2.43787;

  /**
   * <p>
   * Constructor for ChungConductivityMethod.
   * </p>
   *
   * @param gasPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public ChungConductivityMethod(PhysicalProperties gasPhase) {
    super(gasPhase);
    pureComponentConductivity = new double[gasPhase.getPhase().getNumberOfComponents()];
  }

  /** {@inheritDoc} */
  @Override
  public double calcConductivity() {
    calcPureComponentConductivity();
    double tempVar = 0;
    conductivity = 0;

    for (int i = 0; i < gasPhase.getPhase().getNumberOfComponents(); i++) {
      tempVar = 0;
      for (int j = 0; j < gasPhase.getPhase().getNumberOfComponents(); j++) {
        // Aij from Mason-Saxena method (Wassiljewa equation)
        double Aij = 1.0
            * Math.pow(1.0 + Math.sqrt(pureComponentConductivity[i] / pureComponentConductivity[j])
                * Math.pow(gasPhase.getPhase().getComponent(i).getMolarMass()
                    / gasPhase.getPhase().getComponent(j).getMolarMass(), 1.0 / 4.0),
                2.0)
            / Math.sqrt(8.0 * (1.0 + gasPhase.getPhase().getComponent(i).getMolarMass()
                / gasPhase.getPhase().getComponent(j).getMolarMass()));
        tempVar += gasPhase.getPhase().getComponent(j).getx() * Aij;
      }
      conductivity +=
          gasPhase.getPhase().getComponent(i).getx() * pureComponentConductivity[i] / tempVar;
    }
    return conductivity;
  }

  /**
   * Computes the Chung dilute-gas viscosity for component i using eq. 9-4.7 of TPoLG 5th ed.
   *
   * <p>
   * η₀ = 40.785 · Fc · √(M·T) / (Vc^(2/3) · Ω) [μP]
   * </p>
   *
   * @param i component index
   * @return dilute-gas viscosity in μP (micropoise)
   */
  private double calcChungDiluteGasViscosity(int i) {
    double T = gasPhase.getPhase().getTemperature();
    double Tc = gasPhase.getPhase().getComponent(i).getTC();
    double Vc = gasPhase.getPhase().getComponent(i).getCriticalVolume(); // cm3/mol
    double omega = gasPhase.getPhase().getComponent(i).getAcentricFactor();
    double mu_r =
        131.3 * gasPhase.getPhase().getComponent(i).getDebyeDipoleMoment() / Math.sqrt(Vc * Tc);
    double kappa = gasPhase.getPhase().getComponent(i).getViscosityCorrectionFactor();
    double Mw = gasPhase.getPhase().getComponent(i).getMolarMass() * 1000.0; // g/mol

    // Fc factor (eq. 9-4.10 TPoLG)
    double Fc = 1.0 - 0.2756 * omega + 0.059035 * Math.pow(mu_r, 4) + kappa;

    // Reduced temperature for collision integral (eq. 9-4.8)
    double Tstar = 1.2593 * T / Tc;

    // Neufeld collision integral (eq. 9-4.3)
    double omegaV = NF_A * Math.pow(Tstar, -NF_B) + NF_C * Math.exp(-NF_D * Tstar)
        + NF_E * Math.exp(-NF_F * Tstar);

    // Chung dilute gas viscosity (eq. 9-4.7) in μP
    double eta0 = 40.785 * Fc * Math.sqrt(Mw * T) / (Math.pow(Vc, 2.0 / 3.0) * omegaV);
    return eta0;
  }

  /**
   * <p>
   * Calculates pure-component dilute-gas thermal conductivity using the Chung et al. method (eq.
   * 10-3.14 through 10-3.17 in TPoLG 5th ed).
   * </p>
   *
   * <p>
   * λ = 3.75 · (R/M) · η₀ · Ψ
   * </p>
   *
   * <p>
   * where η₀ is the Chung dilute-gas viscosity computed internally (not from the phase viscosity
   * model) and Ψ is the Eucken correction factor.
   * </p>
   */
  public void calcPureComponentConductivity() {
    for (int i = 0; i < gasPhase.getPhase().getNumberOfComponents(); i++) {
      double Cv0 = gasPhase.getPhase().getComponent(i).getCv0(gasPhase.getPhase().getTemperature());
      double Mw_kg = gasPhase.getPhase().getComponent(i).getMolarMass(); // kg/mol
      double omega = gasPhase.getPhase().getComponent(i).getAcentricFactor();
      double Tc = gasPhase.getPhase().getComponent(i).getTC();
      double T = gasPhase.getPhase().getTemperature();

      // Eucken correction parameters (eq. 10-3.15, 10-3.16, 10-3.17 TPoLG)
      double alpha = Cv0 / R - 1.5;
      double beta = 0.7862 - 0.7109 * omega + 1.3168 * omega * omega;
      double Z = 2.0 + 10.5 * Math.pow(T / Tc, 2.0);
      double psi = 1.0 + alpha * ((0.215 + 0.28288 * alpha - 1.061 * beta + 0.26665 * Z)
          / (0.6366 + beta * Z + 1.061 * alpha * beta));

      // Chung dilute-gas viscosity in μP, convert to Pa·s (1 μP = 1e-7 Pa·s)
      double eta0_muP = calcChungDiluteGasViscosity(i);
      double eta0_Pas = eta0_muP * 1e-7;

      // λ = 3.75 * R * η₀ * Ψ / M [W/(m·K)]
      pureComponentConductivity[i] = 3.75 * R * psi * eta0_Pas / Mw_kg;

      if (pureComponentConductivity[i] < 1e-50) {
        pureComponentConductivity[i] = 1e-50;
      }
    }
  }
}
