package neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity;

import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.util.spanwagner.NeqSimSpanWagner;

/**
 * Reference thermal conductivity correlation for pure carbon dioxide. Based on correlations by
 * Huber (JPCRD 2016) and Scalabrin (JPCRD 2006).
 *
 * @author esol
 */
public class CO2ConductivityMethod extends Conductivity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for CO2ConductivityMethod.
   * </p>
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
    double rho = phase.getPhase().getDensity();
    if (rho <= 0.0 || rho > 1.0e6) {
      double[] props = NeqSimSpanWagner.getProperties(T,
          phase.getPhase().getPressure() * 1e5, phase.getPhase().getType());
      rho = props[0] * phase.getPhase().getComponent(0).getMolarMass();
    }

    // Polynomial fit to reference thermal conductivity (CoolProp data)
    double[] c = {-1.70653834e-06, -5.03800651e-05, 6.23697410e-04, 9.09038459e-07,
        -5.45907442e-06, 9.77177861e-07, -2.73923936e-09, 1.61311567e-08,
        -1.16449736e-09, -1.51624815e-09, 2.98898239e-12, -1.60039050e-11,
        -1.39252024e-12, 2.06650460e-12, 4.96560727e-13};
    double lambda = c[0] + c[1] * T + c[2] * rho + c[3] * T * T + c[4] * T * rho
        + c[5] * rho * rho + c[6] * T * T * T + c[7] * T * T * rho + c[8] * T * rho * rho
        + c[9] * rho * rho * rho + c[10] * Math.pow(T, 4.0) + c[11] * Math.pow(T, 3.0) * rho
        + c[12] * T * T * rho * rho + c[13] * T * Math.pow(rho, 3.0)
        + c[14] * Math.pow(rho, 4.0);
    return lambda;
  }
}
