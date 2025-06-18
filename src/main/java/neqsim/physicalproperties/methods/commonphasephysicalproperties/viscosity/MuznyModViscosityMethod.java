package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * MuznyModViscosityMethod class.
 * </p>
 *
 * @author esol
 */
public class MuznyModViscosityMethod extends Viscosity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for MuznyModViscosityMethod.
   * </p>
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public MuznyModViscosityMethod(PhysicalProperties phase) {
    super(phase);
  }

  /** {@inheritDoc} */
  @Override
  public double calcViscosity() {
    // Check if there are other components than helium
    if (phase.getPhase().getNumberOfComponents() > 1
        || !phase.getPhase().getComponent(0).getName().equalsIgnoreCase("hydrogen")) {
      throw new Error("Muzny viscosity model only supports PURE HYDROGEN.");
    }

    double T = phase.getPhase().getTemperature();
    double P = phase.getPhase().getPressure() / 10; // [MPa]
    double rho = phase.getPhase().getDensity_Leachman();

    double[] a = {2.09630e-1, -4.55274e-1, 1.43602e-1, -3.35325e-2, 2.76981e-3};
    double[] b = {-0.1870, 2.4871, 3.7151, -11.0972, 9.0965, -3.8292, 0.5166};
    double[] c =
        {0, 6.43449673, 4.56334068e-2, 2.32797868e-1, 9.58326120e-1, 1.27941189e-1, 3.63576595e-1};
    double Tc = 33.145; // [K] (Source: NIST)
    double rho_sc = 90.909090909; // [kg/m^3]
    double M = 2.01588; // [g/mol] molar mass
    double sigma = 0.297; // [nm] scaling parameter
    double epsilon_kb = 30.41; // [K] scaling parameter

    double Tr = T / Tc;
    double rho_r = rho / rho_sc;
    double T_star = T * 1 / epsilon_kb;

    double sstar = 0.0; // creating sstar object temporary
    for (int i = 0; i < a.length; i++) {
      sstar += a[i] * Math.pow(Math.log(T_star), i);
    }
    double Sstar = Math.exp(sstar);

    double Bstar_eta = 0.0; // creating Bstar_eta object
    for (int i = 0; i < b.length; i++) {
      Bstar_eta += b[i] * Math.pow(T_star, -i);
    }

    double B_eta = Bstar_eta * Math.pow(sigma, 3);

    double eta_0 = (0.021357 * Math.pow(M * T, 0.5)) / (Math.pow(sigma, 2) * Sstar);
    double eta_1 = B_eta * eta_0;

    double eta = eta_0 + eta_1 * rho + c[1] * Math.pow(rho_r, 2) * Math.exp(c[2] * Tr + c[3] / Tr
        + (c[4] * Math.pow(rho_r, 2)) / (c[5] + Tr) + c[6] * Math.pow(rho_r, 6));

    double A =
        0.002 * P * Math.pow(405.0 / T, 4.6) + 0.173 * (1 + 0.05 / (1 + Math.exp(-0.5 * (P - 20))));

    double B = 0.13 * (1 / (1 + Math.exp(0.5 * (T - 405))))
        + 0.1 * (1 / (1 + Math.exp(-0.05 * (T - 390)))) * (1 + 2 / (1 + Math.exp(0.9 * (P - 17))));

    double C = 0.5 * (1 - 1 / (1 + Math.exp(1.0 * (T - 355))))
        * (1 - 1 / (1 + Math.exp(-0.3 * (P - 10)))) * Math.pow(T / 600.0, 2.5)
        - 0.15 * (1 - 1 / (1 + Math.exp(0.8 * (P - 19)))) * (420.0 / T)
            * (1 - 1 / (1 + Math.exp(1.0 * (T - 415))));

    return (eta - A + B - C) * Math.pow(10, -6); // [Pa*s]
  }
}
