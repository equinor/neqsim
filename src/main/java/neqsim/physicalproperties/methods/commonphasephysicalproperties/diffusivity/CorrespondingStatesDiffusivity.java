package neqsim.physicalproperties.methods.commonphasephysicalproperties.diffusivity;

import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.phase.PhaseType;

/**
 * CorrespondingStatesDiffusivity class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class CorrespondingStatesDiffusivity extends Diffusivity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for CorrespondingStatesDiffusivity.
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public CorrespondingStatesDiffusivity(PhysicalProperties phase) {
    super(phase);
  }

  /** {@inheritDoc} */
  @Override
  public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
    if (phase.getPhase().getType() == PhaseType.LIQUID) {
      return 1.0e-4 * 9.89e-8 * Math.pow(phase.getViscosity() * 1e3, -0.907)
          * Math.pow(1.0 / phase.getPhase().getComponent(i).getNormalLiquidDensity()
              * phase.getPhase().getComponent(i).getMolarMass() * 1000, -0.45)
          * Math.pow(1.0 / phase.getPhase().getComponent(j).getNormalLiquidDensity()
              * phase.getPhase().getComponent(j).getMolarMass() * 1000, 0.265)
          * phase.getPhase().getTemperature();
    } else {
      return 1.8e-5 / phase.getPhase().getPressure();
    }
  }
}
