package neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.diffusivity;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class SiddiqiLucasMethod extends Diffusivity {
    private static final long serialVersionUID = 1000;

    /** Creates new Conductivity */

    public SiddiqiLucasMethod() {}

    public SiddiqiLucasMethod(
            neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface liquidPhase) {
        super(liquidPhase);
    }

    // aqueous correlation
    @Override
    public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
        // method - estimation method
        // if(method==? then)
        // remember this is the Maxwell-Stefan diffusion coefficients
        binaryDiffusionCoeffisients[i][j] = 1.0e-4 * 2.98e-7
                * Math.pow(liquidPhase.getPureComponentViscosity(j), -1.026)
                * Math.pow(
                        1.0 / liquidPhase.getPhase().getComponents()[i].getNormalLiquidDensity()
                                * liquidPhase.getPhase().getComponents()[i].getMolarMass() * 1000,
                        -0.5473)
                * liquidPhase.getPhase().getTemperature();
        return binaryDiffusionCoeffisients[i][j];
    }

    // non-aqueous correlation
    public double calcBinaryDiffusionCoefficient2(int i, int j, int method) {
        // method - estimation method
        // if(method==? then)
        // remember this is the Maxwell-Stefan diffusion coefficients
        binaryDiffusionCoeffisients[i][j] = 1.0e-4 * 9.89e-8
                * Math.pow(liquidPhase.getPureComponentViscosity(j), -0.907)
                * Math.pow(
                        1.0 / liquidPhase.getPhase().getComponents()[i].getNormalLiquidDensity()
                                * liquidPhase.getPhase().getComponents()[i].getMolarMass() * 1000,
                        -0.45)
                * Math.pow(
                        1.0 / liquidPhase.getPhase().getComponents()[j].getNormalLiquidDensity()
                                * liquidPhase.getPhase().getComponents()[j].getMolarMass() * 1000,
                        0.265)
                * liquidPhase.getPhase().getTemperature();
        return binaryDiffusionCoeffisients[i][j];
    }
}
