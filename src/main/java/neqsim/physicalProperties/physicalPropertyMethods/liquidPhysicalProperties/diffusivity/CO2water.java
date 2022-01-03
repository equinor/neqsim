package neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.diffusivity;

/**
 * <p>CO2water class.</p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class CO2water extends Diffusivity {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new Conductivity
     */
    public CO2water() {}

    /**
     * <p>Constructor for CO2water.</p>
     *
     * @param liquidPhase a {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface} object
     */
    public CO2water(
            neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface liquidPhase) {
        super(liquidPhase);
    }

    // aqueous correlation
    /** {@inheritDoc} */
    @Override
    public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
        binaryDiffusionCoeffisients[i][j] =
                0.03389 * Math.exp(-2213.7 / liquidPhase.getPhase().getTemperature()) * 1e-4; // Tammi
                                                                                              // (1994)
                                                                                              // -
                                                                                              // Pcheco
        return binaryDiffusionCoeffisients[i][j];
    }
}
