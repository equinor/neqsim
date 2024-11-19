package neqsim.process.equipment.expander;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortInterface;

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
