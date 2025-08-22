package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * Reference viscosity correlation for pure carbon dioxide.
 * Based on correlations by Laesecke et al. (JPCRD 2017).
 */
public class CO2ViscosityMethod extends Viscosity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>Constructor for CO2ViscosityMethod.</p>
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public CO2ViscosityMethod(PhysicalProperties phase) {
    super(phase);
  }

  /** {@inheritDoc} */
  @Override
  public double calcViscosity() {
    if (phase.getPhase().getNumberOfComponents() > 1
        || !phase.getPhase().getComponent(0).getName().equalsIgnoreCase("CO2")) {
      throw new Error("CO2 viscosity model only supports PURE CO2.");
    }

    double T = phase.getPhase().getTemperature();
    double rho = phase.getDensity();

    // Dilute-gas term (Laesecke JPCRD 2017 Eq. 4)
    double[] a = {1749.354893188350, -369.069300007128, 5423856.34887691,
        -2.21283852168356, -269503.247933569, 73145.021531826, 5.34368649509278};
    double T13 = Math.pow(T, 1.0 / 3.0);
    double T16 = Math.pow(T, 1.0 / 6.0);
    double den = a[0] + a[1] * T16 + a[2] * Math.exp(a[3] * T13)
        + (a[4] + a[5] * T13) / Math.exp(T13) + a[6] * Math.sqrt(T);
    double eta0 = 0.0010055 * Math.sqrt(T) / den; // [Pa*s]

    // Residual term (Laesecke JPCRD 2017 Eq. 8-9)
    double c1 = 0.360603235428487;
    double c2 = 0.121550806591497;
    double gamma = 8.06282737481277;
    double Tt = 216.592; // Triple point temperature [K]
    double rho_tL = 1178.53; // Triple point liquid density [kg/m3]
    double Tr = T / Tt;
    double rhor = rho / rho_tL;
    double R = 8.314462618; // Gas constant [J/mol/K]
    double M = phase.getPhase().getComponent(0).getMolarMass(); // [kg/mol]
    double eta_tL = Math.pow(rho_tL, 2.0 / 3.0) * Math.sqrt(R * Tt)
        / (Math.pow(M, 1.0 / 6.0) * 84446887.43579945);
    double residual = eta_tL
        * (c1 * Tr * Math.pow(rhor, 3.0) + (Math.pow(rhor, 2.0) + Math.pow(rhor, gamma)) / (Tr - c2));

    return eta0 + residual;
  }
}
