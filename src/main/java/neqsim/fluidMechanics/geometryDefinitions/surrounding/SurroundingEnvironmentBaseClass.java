package neqsim.fluidMechanics.geometryDefinitions.surrounding;

/**
 *
 * @author ESOL
 */
public class SurroundingEnvironmentBaseClass
        implements SurroundingEnvironment /**
                                           * @return the heatTransferCoefficient
                                           */
{
    private static final long serialVersionUID = 1000;

    /**
     * @return the temperature
     */
    @Override
    public double getTemperature() {
        return temperature;
    }

    /**
     * @param temperature the temperature to set
     */
    @Override
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    private double heatTransferCoefficient = 20.0;
    private double temperature = 298.15;

    public SurroundingEnvironmentBaseClass() {}

    @Override
    public double getHeatTransferCoefficient() {
        return heatTransferCoefficient;
    }

    /**
     * @param heatTransferCoefficient the heatTransferCoefficient to set
     */
    @Override
    public void setHeatTransferCoefficient(double heatTransferCoefficient) {
        this.heatTransferCoefficient = heatTransferCoefficient;
    }
}
