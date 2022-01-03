/*
 * ComponentEos.java
 *
 * Created on 14. mai 2000, 21:27
 */

package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>ComponentDefault class.</p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentDefault extends Component {

    private static final long serialVersionUID = 1000;

    /**
     * <p>Constructor for ComponentDefault.</p>
     */
    public ComponentDefault() {
    }

	/** {@inheritDoc} */
    @Override
	public double fugcoef(PhaseInterface phase) {
        return 0.0;
    }

}
