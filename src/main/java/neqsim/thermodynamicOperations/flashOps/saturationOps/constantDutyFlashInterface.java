package neqsim.thermodynamicOperations.flashOps.saturationOps;

import neqsim.thermodynamicOperations.OperationInterface;

/**
 * <p>
 * constantDutyFlashInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface constantDutyFlashInterface extends OperationInterface {
  /**
   * <p>
   * setBeta.
   * </p>
   *
   * @param beta a double
   */
  public void setBeta(double beta);

  /**
   * <p>
   * isSuperCritical.
   * </p>
   *
   * @return a boolean
   */
  public boolean isSuperCritical();
}
