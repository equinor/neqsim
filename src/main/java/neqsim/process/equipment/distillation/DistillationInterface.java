package neqsim.process.equipment.distillation;

import neqsim.process.equipment.ProcessEquipmentInterface;

/**
 * <p>
 * DistillationInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface DistillationInterface extends ProcessEquipmentInterface {
  /**
   * <p>
   * setNumberOfTrays.
   * </p>
   *
   * @param number a int
   */
  public void setNumberOfTrays(int number);
}
