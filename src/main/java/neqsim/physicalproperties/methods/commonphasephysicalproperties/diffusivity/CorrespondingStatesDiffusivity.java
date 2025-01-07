package neqsim.physicalproperties.methods.commonphasephysicalproperties.diffusivity;

import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.phase.PhaseType;

/**
 * <p>
 * CorrespondingStatesDiffusivity class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class CorrespondingStatesDiffusivity extends Diffusivity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double[][] binaryDiffusionCoefficients;
  double[][] binaryLennardJonesOmega;

  /**
   * <p>
   * Constructor for CorrespondingStatesDiffusivity.
   * </p>
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public CorrespondingStatesDiffusivity(PhysicalProperties phase) {
    super(phase);
    binaryDiffusionCoefficients = new double[phase.getPhase().getNumberOfComponents()][phase
        .getPhase().getNumberOfComponents()];
  }

  /** {@inheritDoc} */
  @Override
  public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
    if (phase.getPhase().getType() == PhaseType.LIQUID) {
      binaryDiffusionCoefficients[i][j] =
          1.0e-4 * 9.89e-8 * Math.pow(phase.getViscosity() * 1e3, -0.907)
              * Math.pow(1.0 / phase.getPhase().getComponent(i).getNormalLiquidDensity()
                  * phase.getPhase().getComponent(i).getMolarMass() * 1000, -0.45)
              * Math.pow(1.0 / phase.getPhase().getComponent(j).getNormalLiquidDensity()
                  * phase.getPhase().getComponent(j).getMolarMass() * 1000, 0.265)
              * phase.getPhase().getTemperature();
      // System.out.println("liq diffusivity " +binaryDiffusionCoefficients[i][j]
      // );
      return binaryDiffusionCoefficients[i][j];
    } else {
      binaryDiffusionCoefficients[i][j] = 1.8e-5 / phase.getPhase().getPressure();
      // System.out.println("gas diffusivity " +binaryDiffusionCoefficients[i][j]
      // );
      return binaryDiffusionCoefficients[i][j];
    }
  }
}
