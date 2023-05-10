package neqsim.thermodynamicOperations.flashOps.saturationOps;

import neqsim.thermodynamicOperations.OperationInterface;

/**
 * <p>
 * ConstantDutyFlashInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ConstantDutyFlashInterface extends OperationInterface {
  /**
   * <p>
   * isSuperCritical.
   * </p>
   *
   * @return a boolean
   */
  public boolean isSuperCritical();
}
