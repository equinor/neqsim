package neqsim.process.equipment.absorber;

import neqsim.process.equipment.ProcessEquipmentInterface;

/**
 * AbsorberInterface interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface AbsorberInterface extends ProcessEquipmentInterface {
  /**
   * setAproachToEquilibrium.
   *
   * @param eff a double
   */
  public void setAproachToEquilibrium(double eff);
}
