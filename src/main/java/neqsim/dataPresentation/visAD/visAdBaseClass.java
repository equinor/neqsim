/*
 * visAdBaseClass.java
 *
 * Created on 24. mai 2001, 19:46
 */
package neqsim.dataPresentation.visAD;

import java.rmi.RemoteException;
import visad.VisADException;

/**
 * <p>
 * visAdBaseClass class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class visAdBaseClass implements visAdInterface, java.io.Serializable {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for visAdBaseClass.
     * </p>
     */
    public visAdBaseClass() {}

    /** {@inheritDoc} */
    @Override
    public void init() throws RemoteException, VisADException {}
}
