package neqsim.fluidMechanics.geometryDefinitions.surrounding;

/**
 *
 * @author ESOL
 */
public interface SurroundingEnvironment {

    public double getTemperature();

    public void setTemperature(double temperature);

    public double getHeatTransferCoefficient();

    public void setHeatTransferCoefficient(double heatTransferCoefficient);
}
