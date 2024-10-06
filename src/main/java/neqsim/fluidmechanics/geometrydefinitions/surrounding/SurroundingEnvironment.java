package neqsim.fluidmechanics.geometrydefinitions.surrounding;

/**
 * <p>
 * SurroundingEnvironment interface.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface SurroundingEnvironment {
  /**
   * <p>
   * getTemperature.
   * </p>
   *
   * @return a double
   */
  public double getTemperature();

  /**
   * <p>
   * setTemperature.
   * </p>
   *
   * @param temperature a double
   */
  public void setTemperature(double temperature);

  /**
   * <p>
   * getHeatTransferCoefficient.
   * </p>
   *
   * @return a double
   */
  public double getHeatTransferCoefficient();

  /**
   * <p>
   * setHeatTransferCoefficient.
   * </p>
   *
   * @param heatTransferCoefficient a double
   */
  public void setHeatTransferCoefficient(double heatTransferCoefficient);
}
