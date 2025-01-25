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
}
