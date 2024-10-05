package neqsim.physicalproperties.physicalpropertymethods.liquidphysicalproperties.diffusivity;

/**
 * <p>
 * CO2water class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class CO2water extends Diffusivity {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for CO2water.
   * </p>
   *
   * @param liquidPhase a
   *        {@link neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface}
   *        object
   */
  public CO2water(
      neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface liquidPhase) {
    super(liquidPhase);
  }

  // aqueous correlation
  /** {@inheritDoc} */
  @Override
  public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
    // Tammi (1994) - Pcheco
    binaryDiffusionCoefficients[i][j] =
        0.03389 * Math.exp(-2213.7 / liquidPhase.getPhase().getTemperature()) * 1e-4;
    return binaryDiffusionCoefficients[i][j];
  }
}
