package neqsim.process.equipment.expander;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortInterface;

/**
 * ExpanderInterface interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface ExpanderInterface extends ProcessEquipmentInterface, TwoPortInterface {
  /**
   * getEnergy.
   *
   * @return a double
   */
  public double getEnergy();
}
