/*
 * OperationInterafce.java
 *
 * Created on 2. oktober 2000, 22:14
 */

package neqsim.thermodynamicOperations.flashOps.saturationOps;

import neqsim.thermodynamicOperations.OperationInterface;

/**
 * <p>constantDutyFlashInterface interface.</p>
 *
 * @author Even Solbraa
 */
public interface constantDutyFlashInterface extends OperationInterface {
    /**
     * <p>setBeta.</p>
     *
     * @param beta a double
     */
    public void setBeta(double beta);

    /**
     * <p>isSuperCritical.</p>
     *
     * @return a boolean
     */
    public boolean isSuperCritical();
}
