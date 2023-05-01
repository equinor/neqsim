package neqsim.processSimulation.processEquipment.absorber;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;

/**
 * <p>
 * AbsorberInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface AbsorberInterface extends ProcessEquipmentInterface {
  /**
   * <p>
   * setAproachToEquilibrium.
   * </p>
   *
   * @param eff a double
   */
  public void setAproachToEquilibrium(double eff);
}
