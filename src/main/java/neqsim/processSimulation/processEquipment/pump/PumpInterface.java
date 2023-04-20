package neqsim.processSimulation.processEquipment.pump;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.TwoPortInterface;

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
}
