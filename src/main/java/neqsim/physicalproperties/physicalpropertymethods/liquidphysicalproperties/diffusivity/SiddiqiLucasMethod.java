package neqsim.physicalproperties.physicalpropertymethods.liquidphysicalproperties.diffusivity;

/**
 * <p>
 * SiddiqiLucasMethod class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SiddiqiLucasMethod extends Diffusivity {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SiddiqiLucasMethod.
   * </p>
   *
   * @param liquidPhase a
   *        {@link neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface}
   *        object
   */
  public SiddiqiLucasMethod(
      neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface liquidPhase) {
    super(liquidPhase);
  }

  // aqueous correlation
  /** {@inheritDoc} */
  @Override
  public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
    // method - estimation method
    // if(method==? then)
    // remember this is the Maxwell-Stefan diffusion coefficients
    binaryDiffusionCoefficients[i][j] =
        1.0e-4 * 2.98e-7 * Math.pow(liquidPhase.getPureComponentViscosity(j), -1.026)
            * Math.pow(1.0 / liquidPhase.getPhase().getComponents()[i].getNormalLiquidDensity()
                * liquidPhase.getPhase().getComponents()[i].getMolarMass() * 1000, -0.5473)
            * liquidPhase.getPhase().getTemperature();
    return binaryDiffusionCoefficients[i][j];
  }

  // non-aqueous correlation
  /**
   * <p>
   * calcBinaryDiffusionCoefficient2.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @param method a int
   * @return a double
   */
  public double calcBinaryDiffusionCoefficient2(int i, int j, int method) {
    // method - estimation method
    // if(method==? then)
    // remember this is the Maxwell-Stefan diffusion coefficients
    binaryDiffusionCoefficients[i][j] =
        1.0e-4 * 9.89e-8 * Math.pow(liquidPhase.getPureComponentViscosity(j), -0.907)
            * Math.pow(1.0 / liquidPhase.getPhase().getComponents()[i].getNormalLiquidDensity()
                * liquidPhase.getPhase().getComponents()[i].getMolarMass() * 1000, -0.45)
            * Math.pow(1.0 / liquidPhase.getPhase().getComponents()[j].getNormalLiquidDensity()
                * liquidPhase.getPhase().getComponents()[j].getMolarMass() * 1000, 0.265)
            * liquidPhase.getPhase().getTemperature();
    return binaryDiffusionCoefficients[i][j];
  }
}
