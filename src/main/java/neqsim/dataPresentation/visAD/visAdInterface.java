/*
 * visAdInterface.java
 *
 * Created on 24. mai 2001, 19:50
 */

package neqsim.dataPresentation.visAD;

import java.rmi.RemoteException;
import visad.*;

/**
 * <p>visAdInterface interface.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface visAdInterface {
    /**
     * <p>init.</p>
     *
     * @throws java.rmi.RemoteException if any.
     * @throws visad.VisADException if any.
     */
    public void init() throws RemoteException, VisADException;
}
