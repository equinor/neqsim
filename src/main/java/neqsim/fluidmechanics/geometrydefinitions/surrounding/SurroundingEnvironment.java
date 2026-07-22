package neqsim.fluidmechanics.geometrydefinitions.surrounding;

/**
 * SurroundingEnvironment interface.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface SurroundingEnvironment {
  /**
   * getTemperature.
   *
   * @return a double
   */
  public double getTemperature();

  /**
   * setTemperature.
   *
   * @param temperature a double
   */
  public void setTemperature(double temperature);

  /**
   * getHeatTransferCoefficient.
   *
   * @return a double
   */
  public double getHeatTransferCoefficient();

  /**
   * setHeatTransferCoefficient.
   *
   * @param heatTransferCoefficient a double
   */
  public void setHeatTransferCoefficient(double heatTransferCoefficient);
}
