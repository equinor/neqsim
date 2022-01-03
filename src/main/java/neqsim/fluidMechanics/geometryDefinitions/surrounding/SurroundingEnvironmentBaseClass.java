package neqsim.fluidMechanics.geometryDefinitions.surrounding;

/**
 * <p>SurroundingEnvironmentBaseClass class.</p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class SurroundingEnvironmentBaseClass implements SurroundingEnvironment /**
                                                                                * @return the heatTransferCoefficient
                                                                                */
{
    private static final long serialVersionUID = 1000;

	/** {@inheritDoc} */
    @Override
	public double getTemperature() {
        return temperature;
    }

	/** {@inheritDoc} */
    @Override
	public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    private double heatTransferCoefficient = 20.0;
    private double temperature = 298.15;

    /**
     * <p>Constructor for SurroundingEnvironmentBaseClass.</p>
     */
    public SurroundingEnvironmentBaseClass() {

    }

    /** {@inheritDoc} */
    @Override
	public double getHeatTransferCoefficient() {
        return heatTransferCoefficient;
    }

	/** {@inheritDoc} */
    @Override
	public void setHeatTransferCoefficient(double heatTransferCoefficient) {
        this.heatTransferCoefficient = heatTransferCoefficient;
    }

}
