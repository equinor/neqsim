package neqsim.process.equipment.pump;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortInterface;

/**
 * <p>
 * PumpInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface PumpInterface extends ProcessEquipmentInterface, TwoPortInterface {
  /**
   * <p>
   * getEnergy.
   * </p>
   *
   * @return a double
   */
  public double getEnergy();

  /**
   * <p>
   * getPower.
   * </p>
   *
   * @return a double
   */
  public double getPower();

  /**
   * <p>
   * setPumpChartType.
   * </p>
   *
   * @param type a {@link java.lang.String} object
   */
  public void setPumpChartType(String type);

  /**
   * <p>
   * Calculate the Net Positive Suction Head Available.
   * </p>
   *
   * @return NPSHa in meters
   */
  public double getNPSHAvailable();

  /**
   * <p>
   * Get the required Net Positive Suction Head.
   * </p>
   *
   * @return NPSHr in meters
   */
  public double getNPSHRequired();

  /**
   * <p>
   * Check if pump is at risk of cavitation.
   * </p>
   *
   * @return true if cavitation risk exists
   */
  public boolean isCavitating();

  /**
   * <p>
   * Enable or disable NPSH checking.
   * </p>
   *
   * @param checkNPSH true to enable NPSH checking
   */
  public void setCheckNPSH(boolean checkNPSH);

  /**
   * <p>
   * Get minimum flow rate.
   * </p>
   *
   * @return minimum flow in kg/sec
   */
  public double getMinimumFlow();

  /**
   * <p>
   * Set minimum flow rate.
   * </p>
   *
   * @param minimumFlow minimum flow in kg/sec
   */
  public void setMinimumFlow(double minimumFlow);
}
