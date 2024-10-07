package neqsim.processsimulation.processequipment.expander;

import neqsim.processsimulation.processequipment.ProcessEquipmentInterface;
import neqsim.processsimulation.processequipment.TwoPortInterface;

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
