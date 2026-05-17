package neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity;

import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * Full Chung et al. (1988) thermal conductivity method including both dilute-gas and dense-fluid
 * contributions. Works for gas and liquid phases across all pressures.
 *
 * <p>
 * The dilute-gas contribution uses a modified Eucken correction with the Mason-Saxena mixing rule.
 * The dense-fluid contribution accounts for density effects via the parameter y = rho * Vc / 6.
 * </p>
 *
 * <p>
 * Reference: Chung, T.-H., Ajlan, M., Lee, L. L., Starling, K. E. (1988). Generalized
 * multiparameter correlation for nonpolar and polar fluid transport properties. Ind. Eng. Chem.
 * Res. 27(4), 671-679.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ChungDenseConductivityMethod extends Conductivity
    implements ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Conversion factor from Chung conductivity units (1e-6 cal/(cm*s*K)) to W/(m*K). */
  private static final double CHUNG_TO_SI = 4.184e-4;

  /**
   * Constructor for ChungDenseConductivityMethod.
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public ChungDenseConductivityMethod(PhysicalProperties phase) {
    super(phase);
  }

  /** {@inheritDoc} */
  @Override
  public double calcConductivity() {
    int ncomp = phase.getPhase().getNumberOfComponents();
    double temp = phase.getPhase().getTemperature();

    // Mixing rules for Chung parameters (mole-fraction weighted)
    double sigMix3 = 0.0;
    double epskMix = 0.0;
    double omegaMix = 0.0;
    double muMix = 0.0;
    double kappaMix = 0.0;
    double mwMixG = phase.getPhase().getMolarMass() * 1000.0; // g/mol
    double mwMixKg = phase.getPhase().getMolarMass(); // kg/mol
    double vcMix = 0.0; // cm3/mol
    double tcMix = 0.0; // K
    double cpMix = phase.getPhase().getCp0() / phase.getPhase().getNumberOfMolesInPhase(); // J/(mol*K)

    for (int i = 0; i < ncomp; i++) {
      double xi = phase.getPhase().getComponent(i).getx();
      double tci = phase.getPhase().getComponent(i).getTC();
      double vci = phase.getPhase().getComponent(i).getCriticalVolume(); // cm3/mol
      double omi = phase.getPhase().getComponent(i).getAcentricFactor();
      double sigI = Math.pow(vci, 1.0 / 3.0);

      for (int j = 0; j < ncomp; j++) {
        double xj = phase.getPhase().getComponent(j).getx();
        double tcj = phase.getPhase().getComponent(j).getTC();
        double vcj = phase.getPhase().getComponent(j).getCriticalVolume();
        double sigJ = Math.pow(vcj, 1.0 / 3.0);

        double sigIJ = Math.sqrt(sigI * sigJ);
        double epsKij = Math.sqrt(tci / 1.2593 * tcj / 1.2593);
        double sigIJ3 = sigIJ * sigIJ * sigIJ;

        sigMix3 += xi * xj * sigIJ3;
        epskMix += xi * xj * epsKij * sigIJ3;
      }
      omegaMix += xi * omi;
      muMix += xi * phase.getPhase().getComponent(i).getDebyeDipoleMoment();
      kappaMix += xi * phase.getPhase().getComponent(i).getAssociationVolume();
      vcMix += xi * phase.getPhase().getComponent(i).getCriticalVolume();
      tcMix += xi * tci;
    }

    if (sigMix3 > 0.0) {
      epskMix = epskMix / sigMix3;
    }

    // Reduced dipole moment (Chung 1988 Eq 9-4.12)
    double muR4 = 0.0;
    if (vcMix > 0.0 && tcMix > 0.0) {
      double muR = 131.3 * muMix / Math.sqrt(vcMix * tcMix);
      muR4 = muR * muR * muR * muR;
    }

    double tStar = temp / (epskMix * 1.2593);
    if (tStar < 0.01) {
      tStar = 0.01;
    }

    // Neufeld collision integral
    double omegaV = 1.16145 * Math.pow(tStar, -0.14874) + 0.52487 * Math.exp(-0.77320 * tStar)
        + 2.16178 * Math.exp(-2.43787 * tStar);

    // Fc correction for polarity and association (Chung 1988 Eq 9-4.11)
    double fc = 1.0 - 0.2756 * omegaMix + 0.059035 * muR4 + kappaMix;

    // ---- Dilute-gas contribution in W/(m*K) ----
    // Cv0 in J/(mol*K)
    double cv0 = cpMix - R;
    double psi = calcAlpha(cv0, omegaMix, temp, epskMix * 1.2593);

    // Dilute viscosity in Pa*s (Chung Eq 9-4.10 from Poling 5th ed)
    double eta0PaS =
        40.785e-7 * fc * Math.sqrt(mwMixG * temp) / (Math.pow(vcMix, 2.0 / 3.0) * omegaV);

    // Dilute conductivity in W/(m*K): lambda0 = (eta0/M) * 3.75 * R * Psi
    double lambda0 = (eta0PaS / mwMixKg) * 3.75 * R * psi;

    // ---- Dense-fluid contribution (Chung 1988) ----
    // All internal calculations in Chung CGS units, converted at end.
    // Chung unit for conductivity: 1e-6 cal/(cm*s*K) = 4.184e-4 W/(m*K)
    double rhoMolar = phase.getPhase().getDensity() / mwMixKg; // mol/m3
    double rhoMolPerCm3 = rhoMolar * 1e-6; // mol/cm3
    double y = rhoMolPerCm3 * vcMix / 6.0;

    if (y < 0.0) {
      y = 0.0;
    }
    if (y > 0.99) {
      y = 0.99;
    }

    double lambdaDenseSI = 0.0;
    if (y > 1e-10) {
      // G coefficients from Chung (1988) Table V
      double[] aC = {2.4166e0, -5.0924e-1, 6.6107e0, 1.4543e1, 7.9274e-1, -5.8634e0, 9.1089e1};
      double[] bC = {7.4824e-1, -1.5094e0, 5.6207e0, -8.9139e0, 8.2019e-1, 1.2801e1, 1.2811e2};
      double[] cC = {-9.1858e-1, -4.9991e1, 6.4760e1, -5.6379e0, -6.9369e-1, 9.5893e0, -5.4217e1};
      double[] dC = {1.2172e2, 6.9983e1, 2.7039e1, 7.4344e1, 6.3173e0, 6.5529e1, 5.2381e2};

      double[] G = new double[7];
      for (int k = 0; k < 7; k++) {
        G[k] = aC[k] + bC[k] * omegaMix + cC[k] * muR4 + dC[k] * kappaMix;
      }

      // Hard-sphere radial distribution function at contact
      double g1 = (1.0 - 0.5 * y) / Math.pow(1.0 - y, 3.0);

      // H2 function (Chung 1988 Eq 10.5-8): correction to dilute conductivity
      double h2Num =
          G[0] * (1.0 - Math.exp(-G[3] * y)) / y + G[1] * g1 * Math.exp(G[4] * y) + G[2] * g1;
      double h2Den = G[0] * G[3] + G[1] + G[2];
      double h2 = (Math.abs(h2Den) > 1e-30) ? h2Num / h2Den : 1.0;
      if (h2 < 0.01) {
        h2 = 0.01;
      }

      // Dilute conductivity in Chung units: 7.452 * (eta0_uP / M_g) * Psi
      double eta0MicroP = eta0PaS * 1.0e7;
      double lambda0Chung = 7.452 * (eta0MicroP / mwMixG) * psi;

      // Dense pre-factor q (Chung 1988 Eq 10.5-6)
      double qChung = 3.586e-3 * Math.sqrt(tcMix / mwMixG) / Math.pow(vcMix, 2.0 / 3.0);

      // Total in Chung units: lambda = lambda0/H2 + G6*q*y
      double lambdaTotalChung = lambda0Chung / h2 + G[5] * qChung * y;

      // Convert to SI: multiply by 4.184e-4 (Chung unit -> W/(m*K))
      double lambdaTotalSI = lambdaTotalChung * CHUNG_TO_SI;

      // Dense contribution = total - dilute
      lambdaDenseSI = lambdaTotalSI - lambda0;
      if (lambdaDenseSI < 0.0) {
        lambdaDenseSI = 0.0;
      }
    }

    conductivity = lambda0 + lambdaDenseSI;
    if (conductivity < 1e-10) {
      conductivity = 1e-10;
    }
    return conductivity;
  }

  /**
   * Calculates the alpha correction factor for thermal conductivity.
   *
   * @param cv0 ideal gas Cv in J/(mol*K)
   * @param omega acentric factor
   * @param temp temperature in K
   * @param tc critical temperature in K
   * @return alpha correction factor
   */
  private double calcAlpha(double cv0, double omega, double temp, double tc) {
    double cvR = cv0 / R - 1.5;
    double beta = 0.7862 - 0.7109 * omega + 1.3168 * omega * omega;
    double z = 2.0 + 10.5 * (temp / tc) * (temp / tc);
    double psi = 1.0 + cvR * ((0.215 + 0.28288 * cvR - 1.061 * beta + 0.26665 * z)
        / (0.6366 + beta * z + 1.061 * cvR * beta));
    return psi;
  }
}
