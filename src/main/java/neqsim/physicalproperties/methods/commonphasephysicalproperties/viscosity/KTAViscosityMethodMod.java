package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * KTAViscosityMethodMod class.
 * </p>
 *
 * @author akselrs
 */
public class KTAViscosityMethodMod extends Viscosity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for KTAViscosityMethodMod.
   * </p>
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public KTAViscosityMethodMod(PhysicalProperties phase) {
    super(phase);
  }

  /** {@inheritDoc} */
  @Override
  public double calcViscosity() {
    // Check if there are other components than helium
    if (phase.getPhase().getNumberOfComponents() > 1
        || !phase.getPhase().getComponent(0).getName().equalsIgnoreCase("helium")) {
      throw new Error("This method only supports PURE HELIUM.");
    }

    double T = phase.getPhase().getTemperature();
    double P = phase.getPhase().getPressure();
    double P_crit = 0.22832; // [MPa] (Source: NIST)
    double A = Math.pow((2 - T / 300), 5.05);
    double B = Math.pow((2 - 300 / T), 2) - 1;
    double C = (1 - 0.3 / (1 + Math.exp(-0.5 * (T - 450)))) / (1 + Math.exp(-0.5 * (T - 377)))
        - 1.5 / (1 + Math.exp(-0.5 * (T - 572)));
    double viscosity = 1e-7 * (3.817 * Math.pow(T, 0.6938) + Math.pow(P, A) / (T * P_crit)
        + Math.exp(-Math.pow(T - 325, 2) / 1000) * (Math.pow(P / 25, 2.7) - Math.pow(T, B)) - C);
    return viscosity; // [Pa*s]
  }
}
