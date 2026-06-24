package neqsim.thermodynamicoperations.flashops.saturationops;

import neqsim.thermodynamicoperations.OperationInterface;

/**
 * ConstantDutyFlashInterface interface.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ConstantDutyFlashInterface extends OperationInterface {
  /**
   * isSuperCritical.
   *
   * @return a boolean
   */
  public boolean isSuperCritical();
}
