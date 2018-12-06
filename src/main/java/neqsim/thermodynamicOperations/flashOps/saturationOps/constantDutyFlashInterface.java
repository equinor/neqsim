/*
 * OperationInterafce.java
 *
 * Created on 2. oktober 2000, 22:14
 */

package neqsim.thermodynamicOperations.flashOps.saturationOps;

import neqsim.thermodynamicOperations.OperationInterface;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public interface constantDutyFlashInterface extends OperationInterface {
    public void setBeta(double beta);
     public boolean isSuperCritical();
}

