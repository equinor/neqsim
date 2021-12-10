/*
 * PhaseSrkEos.java
 *
 * Created on 3. juni 2000, 14:38
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentTST;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class PhaseTSTEos extends PhaseEos {

    private static final long serialVersionUID = 1000;

    /** Creates new PhaseSrkEos */
    public PhaseTSTEos() {
        super();
        uEOS = 2.5;
        wEOS = -1.5;
        delta1 = 1.0 + Math.sqrt(2.0);
        delta2 = 1.0 - Math.sqrt(2.0);
    }

    @Override
    public PhaseTSTEos clone() {
        PhaseTSTEos clonedPhase = null;
        try {
            clonedPhase = (PhaseTSTEos) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedPhase;
    }

    @Override
	public void addcomponent(String componentName, double moles, double molesInPhase, int compNumber) {
        super.addcomponent(molesInPhase);
        componentArray[compNumber] = new ComponentTST(componentName, moles, molesInPhase, compNumber);
    }

}