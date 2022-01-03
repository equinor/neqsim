/*
 * visAdBaseClass.java
 *
 * Created on 24. mai 2001, 19:46
 */

package neqsim.dataPresentation.visAD;

import java.rmi.RemoteException;
import visad.*;

/**
 * <p>visAdBaseClass class.</p>
 *
 * @author esol
 */
public class visAdBaseClass implements visAdInterface, java.io.Serializable {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new visAdBaseClass
     */
    public visAdBaseClass() {
    }

    /** {@inheritDoc} */
    @Override
	public void init() throws RemoteException, VisADException {
    }
}
