package neqsim.processSimulation.processEquipment.expander;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.TwoPortInterface;

/**
 * <p>
 * ExpanderInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface ExpanderInterface extends ProcessEquipmentInterface, TwoPortInterface {
  /**
   * <p>
   * getEnergy.
   * </p>
   *
   * @return a double
   */
  public double getEnergy();
}
