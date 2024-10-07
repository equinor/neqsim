package neqsim.processsimulation.processequipment.heatExchanger;

import neqsim.processsimulation.SimulationInterface;

/**
 * <p>
 * HeaterInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface HeaterInterface extends SimulationInterface {
  /**
   * <p>
   * setdT.
   * </p>
   *
   * @param dT a double
   */
  public void setdT(double dT);

  /**
   * <p>
   * setOutTP.
   * </p>
   *
   * @param temperature a double
   * @param pressure a double
   */
  public void setOutTP(double temperature, double pressure);

  /**
   * <p>
   * setOutTemperature.
   * </p>
   *
   * @param temperature a double
   * @param unit a {@link java.lang.String} object
   */
  public void setOutTemperature(double temperature, String unit);

  /**
   * <p>
   * setOutPressure.
   * </p>
   *
   * @param pressure a double
   * @param unit a {@link java.lang.String} object
   */
  public void setOutPressure(double pressure, String unit);
}
