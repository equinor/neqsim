package neqsim.processsimulation.processequipment.absorber;

import neqsim.processsimulation.processequipment.ProcessEquipmentInterface;

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
