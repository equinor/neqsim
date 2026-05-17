package neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;

/**
 * Friction theory (f-theory) thermal conductivity method for both gas and liquid phases.
 *
 * <p>
 * The method decomposes thermal conductivity into a dilute-gas contribution (modified Eucken with
 * Mason-Saxena mixing) and a dense-fluid residual contribution. The residual conductivity is
 * obtained from the residual viscosity using the generalized Eucken relation:
 * </p>
 *
 * <p>
 * lambda = lambda_0 + (eta - eta_0) * f_Eucken / M
 * </p>
 *
 * <p>
 * where f_Eucken = (Cv0 + 1.25*R) is the Eucken correction factor, and eta/eta_0 are the total and
 * dilute-gas viscosities from the phase's existing viscosity model.
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Chung, T.-H., Ajlan, M., Lee, L. L., Starling, K. E. (1988). Ind. Eng. Chem. Res. 27(4),
 * 671-679.</li>
 * <li>Poling, B.E., Prausnitz, J.M., O'Connell, J.P. (2001). The Properties of Gases and Liquids,
 * 5th edition, Chapter 10.</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class FrictionTheoryConductivityMethod extends Conductivity
    implements ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(FrictionTheoryConductivityMethod.class);

  /** Pure component dilute-gas conductivities in W/(m*K). */
  public double[] pureComponentConductivity;

  /** Pure component dilute-gas viscosities in Pa*s. */
  private double[] pureComponentViscosity;

  /** Chung collision integral parameter. */
  public double[] omegaCond;

  /** Chung Fc correction factor. */
  public double[] fc;

  /** Chung collision integral constants. */
  private static final double CHUNG_A = 1.16145;
  private static final double CHUNG_B = 0.14874;
  private static final double CHUNG_C = 0.52487;
  private static final double CHUNG_D = 0.77320;
  private static final double CHUNG_E = 2.16178;
  private static final double CHUNG_F = 2.43787;

  /**
   * Constructor for FrictionTheoryConductivityMethod.
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public FrictionTheoryConductivityMethod(PhysicalProperties phase) {
    super(phase);
    int ncomp = phase.getPhase().getNumberOfComponents();
    pureComponentConductivity = new double[ncomp];
    pureComponentViscosity = new double[ncomp];
    omegaCond = new double[ncomp];
    fc = new double[ncomp];
  }

  /** {@inheritDoc} */
  @Override
  public double calcConductivity() {
    initPureComponentProperties();

    PhaseInterface localPhase = phase.getPhase();
    int numComp = localPhase.getNumberOfComponents();

    // ---- Dilute-gas mixture conductivity using Mason-Saxena mixing rule ----
    double lambda0 = 0.0;
    for (int i = 0; i < numComp; i++) {
      double denominator = 0.0;
      for (int j = 0; j < numComp; j++) {
        double aij = Math
            .pow(1.0 + Math.sqrt(pureComponentConductivity[i] / pureComponentConductivity[j])
                * Math.pow(localPhase.getComponent(i).getMolarMass()
                    / localPhase.getComponent(j).getMolarMass(), 0.25),
                2.0)
            / Math.sqrt(8.0 * (1.0 + localPhase.getComponent(i).getMolarMass()
                / localPhase.getComponent(j).getMolarMass()));
        denominator += localPhase.getComponent(j).getx() * aij;
      }
      lambda0 += localPhase.getComponent(i).getx() * pureComponentConductivity[i] / denominator;
    }

    // ---- Dense contribution via Eucken scaling of viscosity excess ----
    // Get total viscosity from phase (already computed by physical properties init)
    double viscTotal = phase.getViscosity(); // Pa*s

    // Dilute-gas mixture viscosity (Mason-Saxena mixing)
    double visc0 = 0.0;
    for (int i = 0; i < numComp; i++) {
      double denominator = 0.0;
      for (int j = 0; j < numComp; j++) {
        double phiIJ = Math
            .pow(1.0 + Math.sqrt(pureComponentViscosity[i] / pureComponentViscosity[j])
                * Math.pow(localPhase.getComponent(j).getMolarMass()
                    / localPhase.getComponent(i).getMolarMass(), 0.25),
                2.0)
            / Math.sqrt(8.0 * (1.0 + localPhase.getComponent(i).getMolarMass()
                / localPhase.getComponent(j).getMolarMass()));
        denominator += localPhase.getComponent(j).getx() * phiIJ;
      }
      visc0 += localPhase.getComponent(i).getx() * pureComponentViscosity[i] / denominator;
    }

    // Mixture Cv0 and Eucken correction factor
    double cv0Mix = 0.0;
    double mwMixKg = localPhase.getMolarMass(); // kg/mol
    for (int i = 0; i < numComp; i++) {
      cv0Mix += localPhase.getComponent(i).getx()
          * localPhase.getComponent(i).getCv0(localPhase.getTemperature());
    }

    // Eucken factor: f = (Cv0 + 1.25*R) for converting viscosity excess to conductivity
    double euckenFactor = cv0Mix + 1.25 * R;

    // Dense conductivity contribution: delta_lambda = (eta_total - eta_0) * f / M
    double viscExcess = viscTotal - visc0;
    double lambdaDense = 0.0;
    if (viscExcess > 0.0 && mwMixKg > 1e-30) {
      lambdaDense = viscExcess * euckenFactor / mwMixKg;
    }

    conductivity = lambda0 + lambdaDense;

    if (conductivity < 1e-10) {
      conductivity = 1e-10;
    }
    return conductivity;
  }

  /**
   * Initializes pure component dilute-gas conductivities and viscosities using the Chung et al.
   * (1988) method.
   */
  private void initPureComponentProperties() {
    PhaseInterface localPhase = phase.getPhase();
    double temperature = localPhase.getTemperature();

    for (int i = 0; i < localPhase.getNumberOfComponents(); i++) {
      ComponentInterface comp = localPhase.getComponent(i);

      // Fc correction
      double relativeDipole =
          131.3 * comp.getDebyeDipoleMoment() / Math.sqrt(comp.getCriticalVolume() * comp.getTC());
      fc[i] = 1.0 - 0.2756 * comp.getAcentricFactor() + 0.059035 * Math.pow(relativeDipole, 4.0);

      double tStar = 1.2593 * temperature / comp.getTC();
      omegaCond[i] = CHUNG_A / Math.pow(tStar, CHUNG_B) + CHUNG_C / Math.exp(CHUNG_D * tStar)
          + CHUNG_E / Math.exp(CHUNG_F * tStar);

      // Pure component viscosity in micropoise (Chung)
      double viscMicroP = 40.785 * Math.sqrt(comp.getMolarMass() * 1000.0 * temperature)
          / (Math.pow(comp.getCriticalVolume(), 2.0 / 3.0) * omegaCond[i]) * fc[i];

      // Store in Pa*s for dilute mixing
      pureComponentViscosity[i] = viscMicroP * 1e-7;

      // Pure component Cv0
      double cv0 = comp.getCv0(temperature);

      // Alpha correction (modified Eucken, Chung formulation)
      double cvR = cv0 / R - 1.5;
      double beta = 0.7862 - 0.7109 * comp.getAcentricFactor()
          + 1.3168 * comp.getAcentricFactor() * comp.getAcentricFactor();
      double z = 2.0 + 10.5 * (temperature / comp.getTC()) * (temperature / comp.getTC());
      double alpha = 1.0 + cvR * ((0.215 + 0.28288 * cvR - 1.061 * beta + 0.26665 * z)
          / (0.6366 + beta * z + 1.061 * cvR * beta));

      // Pure component conductivity in W/(m*K):
      // lambda = (eta_PaS / M_kg) * 3.75 * R * Psi
      pureComponentConductivity[i] =
          (pureComponentViscosity[i] / comp.getMolarMass()) * 3.75 * R * alpha;

      if (pureComponentConductivity[i] < 1e-50) {
        pureComponentConductivity[i] = 1e-50;
      }
    }
  }
}
