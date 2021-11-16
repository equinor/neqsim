/*
 * PhaseGENRTL.java
 *
 * Created on 17. juli 2000, 20:51
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentKentEisenberg;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class PhaseKentEisenberg extends PhaseGENRTL {
    private static final long serialVersionUID = 1000;

    public PhaseKentEisenberg() {
        super();
    }

    @Override
    public void addcomponent(String componentName, double moles, double molesInPhase,
            int compNumber) {
        super.addcomponent(molesInPhase);
        componentArray[compNumber] =
                new ComponentKentEisenberg(componentName, moles, molesInPhase, compNumber);
    }

    @Override
    public double getActivityCoefficient(int k, int p) {
        return 1.0;
    }
}
