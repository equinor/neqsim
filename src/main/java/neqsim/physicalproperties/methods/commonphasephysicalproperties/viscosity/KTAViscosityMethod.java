package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * KTAViscosityMethod class.
 * </p>
 */

public class KTAViscosityMethod extends Viscosity {
  /**
   * <p>
   * Constructor for KTAViscosityMethod.
   * </p>
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public KTAViscosityMethod(PhysicalProperties phase) {
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
    //Source of KTA-model https://www.sciencedirect.com/science/article/pii/S0149197024004670#bib28
    double visc = 3.674 * Math.pow(10, -7) * Math.pow(T, 0.7);    //[Pa*s]
    return visc;
  }
}
