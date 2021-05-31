/*
 * PhaseSrkEos.java
 *
 * Created on 3. juni 2000, 14:38
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentPR;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class PhasePrEos extends PhaseEos {

    private static final long serialVersionUID = 1000;

    /** Creates new PhaseSrkEos */
    public PhasePrEos() {
        super();
        thermoPropertyModelName = "PR-EoS";
        uEOS = 2;
        wEOS = -1;
        delta1 = 1.0 + Math.sqrt(2.0);
        delta2 = 1.0 - Math.sqrt(2.0);
    }

    @Override
	public Object clone() {
        PhasePrEos clonedPhase = null;
        try {
            clonedPhase = (PhasePrEos) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedPhase;
    }

    @Override
	public void addcomponent(String componentName, double moles, double molesInPhase, int compNumber) {
        super.addcomponent(molesInPhase);
        componentArray[compNumber] = new ComponentPR(componentName, moles, molesInPhase, compNumber);
    }

}