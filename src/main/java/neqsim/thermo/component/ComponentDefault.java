/*
 * ComponentEos.java
 *
 * Created on 14. mai 2000, 21:27
 */

package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class ComponentDefault extends Component {
    private static final long serialVersionUID = 1000;

    public ComponentDefault() {}

    @Override
    public double fugcoef(PhaseInterface phase) {
        return 0.0;
    }
}
