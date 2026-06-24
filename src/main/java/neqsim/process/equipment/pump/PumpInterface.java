package neqsim.process.equipment.pump;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortInterface;

/**
 * PumpInterface interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface PumpInterface extends ProcessEquipmentInterface, TwoPortInterface {
  /**
   * getEnergy.
   *
   * @return a double
   */
  public double getEnergy();

  /**
   * getPower.
   *
   * @return a double
   */
  public double getPower();

  /**
   * setPumpChartType.
   *
   * @param type a {@link java.lang.String} object
   */
  public void setPumpChartType(String type);

  /**
   * Calculate the Net Positive Suction Head Available.
   *
   * @return NPSHa in meters
   */
  public double getNPSHAvailable();

  /**
   * Get the required Net Positive Suction Head.
   *
   * @return NPSHr in meters
   */
  public double getNPSHRequired();

  /**
   * Check if pump is at risk of cavitation.
   *
   * @return true if cavitation risk exists
   */
  public boolean isCavitating();

  /**
   * Enable or disable NPSH checking.
   *
   * @param checkNPSH true to enable NPSH checking
   */
  public void setCheckNPSH(boolean checkNPSH);

  /**
   * Get minimum flow rate.
   *
   * @return minimum flow in kg/sec
   */
  @Override
  public double getMinimumFlow();

  /**
   * Set minimum flow rate.
   *
   * @param minimumFlow minimum flow in kg/sec
   */
  @Override
  public void setMinimumFlow(double minimumFlow);
}
