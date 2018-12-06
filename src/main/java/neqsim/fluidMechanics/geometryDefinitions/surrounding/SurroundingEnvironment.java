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
public interface SurroundingEnvironment {

    public double getTemperature();

    public void setTemperature(double temperature);

    public double getHeatTransferCoefficient();

    public void setHeatTransferCoefficient(double heatTransferCoefficient);
}
