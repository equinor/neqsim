package neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity;

import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * Reference thermal conductivity correlation for pure carbon dioxide.
 * Based on correlations by Huber (JPCRD 2016) and Scalabrin (JPCRD 2006).
 */
public class CO2ConductivityMethod extends Conductivity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>Constructor for CO2ConductivityMethod.</p>
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public CO2ConductivityMethod(PhysicalProperties phase) {
    super(phase);
  }

  /** {@inheritDoc} */
  @Override
  public double calcConductivity() {
    if (phase.getPhase().getNumberOfComponents() > 1
        || !phase.getPhase().getComponent(0).getName().equalsIgnoreCase("CO2")) {
      throw new Error("CO2 conductivity model only supports PURE CO2.");
    }

    double T = phase.getPhase().getTemperature();
    double rho = phase.getDensity();

    // Dilute gas term from Huber 2016 Eq. (3)
    double Tc = 304.1282;
    double tau = Tc / T;
    double[] l = {0.0151874307, 0.0280674040, 0.0228564190, -0.00741624210};
    double lambda0 = Math.pow(tau, -0.5)
        / (l[0] + l[1] * tau + l[2] * Math.pow(tau, 2.0) + l[3] * Math.pow(tau, 3.0));
    lambda0 /= 1000.0; // [W/mK]

    // Critical enhancement from Scalabrin 2006
    double nc = 0.775547504e-3 * 4.81384;
    double Tr = T / Tc;
    double rhor = rho / 467.6;
    double[] a = {0.0, 3.0, 6.70697, 0.94604, 0.30, 0.30, 0.39751, 0.33791, 0.77963, 0.79857, 0.90, 0.02, 0.20};
    double acoshArg = 1 + a[11] * Math.pow(Math.pow(1 - Tr, 2.0), a[12]);
    double alpha = 1 - a[10] * Math.log(acoshArg + Math.sqrt(acoshArg - 1.0) * Math.sqrt(acoshArg + 1.0));
    double numer = rhor * Math.exp(-Math.pow(rhor, a[1]) / a[1]
        - Math.pow(a[2] * (Tr - 1), 2.0) - Math.pow(a[3] * (rhor - 1), 2.0));
    double braced = (1 - 1 / Tr) + a[4] * Math.pow(Math.pow(rhor - 1, 2.0), 0.5 / a[5]);
    double denom = Math.pow(Math.pow(Math.pow(braced, 2.0), a[6])
        + Math.pow(Math.pow(a[7] * (rhor - alpha), 2.0), a[8]), a[9]);
    double lambdaC = nc * numer / denom;

    return lambda0 + lambdaC;
  }
}
