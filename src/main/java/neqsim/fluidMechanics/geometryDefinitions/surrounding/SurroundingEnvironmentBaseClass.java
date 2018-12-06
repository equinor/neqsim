/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.fluidMechanics.geometryDefinitions.surrounding;

/**
 *
 * @author ESOL
 */
public class SurroundingEnvironmentBaseClass implements SurroundingEnvironment /**
 * @return the heatTransferCoefficient
 */
{

    /**
     * @return the temperature
     */
    public double getTemperature() {
        return temperature;
    }

    /**
     * @param temperature the temperature to set
     */
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    private double heatTransferCoefficient = 20.0;
    private double temperature = 298.15;

    public SurroundingEnvironmentBaseClass() {

    }

    public double getHeatTransferCoefficient() {
        return heatTransferCoefficient;
    }

    /**
     * @param heatTransferCoefficient the heatTransferCoefficient to set
     */
    public void setHeatTransferCoefficient(double heatTransferCoefficient) {
        this.heatTransferCoefficient = heatTransferCoefficient;
    }

}
