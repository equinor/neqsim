package neqsim.physicalproperties.methods.liquidphysicalproperties.diffusivity;

import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * CO2water class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class CO2water extends Diffusivity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for CO2water.
   *
   * @param liquidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public CO2water(PhysicalProperties liquidPhase) {
    super(liquidPhase);
  }

  // aqueous correlation
  /** {@inheritDoc} */
  @Override
  public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
    // Tammi (1994) - Pcheco
    binaryDiffusionCoefficients[i][j] = 0.03389 * Math.exp(-2213.7 / liquidPhase.getPhase().getTemperature()) * 1e-4;
    return binaryDiffusionCoefficients[i][j];
  }
}
