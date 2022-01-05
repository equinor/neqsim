package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentKentEisenberg;

/**
 * <p>
 * PhaseKentEisenberg class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseKentEisenberg extends PhaseGENRTL {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for PhaseKentEisenberg.
     * </p>
     */
    public PhaseKentEisenberg() {
        super();
    }

    /** {@inheritDoc} */
    @Override
    public void addcomponent(String componentName, double moles, double molesInPhase,
            int compNumber) {
        super.addcomponent(molesInPhase);
        componentArray[compNumber] =
                new ComponentKentEisenberg(componentName, moles, molesInPhase, compNumber);
    }

    /** {@inheritDoc} */
    @Override
    public double getActivityCoefficient(int k, int p) {
        return 1.0;
    }
}
