/*
 * PhaseSrkEos.java
 *
 * Created on 3. juni 2000, 14:38
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentSrk;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class PhaseSrkEos extends PhaseEos {
    private static final long serialVersionUID = 1000;

    /** Creates new PhaseSrkEos */
    public PhaseSrkEos() {
        super();
        // mixRule = mixSelect.getMixingRule(2);
        thermoPropertyModelName = "SRK-EoS";
        uEOS = 1;
        wEOS = 0;
        delta1 = 1;
        delta2 = 0;
    }

    @Override
    public Object clone() {
        PhaseSrkEos clonedPhase = null;
        try {
            clonedPhase = (PhaseSrkEos) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedPhase;
    }

    @Override
    public void addcomponent(String componentName, double moles, double molesInPhase,
            int compNumber) {
        super.addcomponent(molesInPhase);
        componentArray[compNumber] =
                new ComponentSrk(componentName, moles, molesInPhase, compNumber);
    }
}
