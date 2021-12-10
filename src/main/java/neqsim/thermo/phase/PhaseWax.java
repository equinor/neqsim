/*
 * PhasePureComponentSolid.java
 *
 * Created on 18. august 2001, 12:39
 */
package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentWax;

/**
 *
 * @author esol
 * @version
 */
public class PhaseWax extends PhaseSolid {

    private static final long serialVersionUID = 1000;

    /** Creates new PhasePureComponentSolid */
    public PhaseWax() {
        super();
        phaseTypeName = "wax";
    }

    @Override
    public PhaseWax clone() {
        PhaseWax clonedPhase = null;
        try {
            clonedPhase = (PhaseWax) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedPhase;
    }

    @Override
	public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase, double beta) { // type = 0
                                                                                                            // start
                                                                                                            // init type
                                                                                                            // =1 gi nye
                                                                                                            // betingelser
        super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
        phaseTypeName = "wax";

    }

    @Override
	public void addcomponent(String componentName, double molesInPhase, double moles, int compNumber) {
        super.addcomponent(molesInPhase);
        componentArray[compNumber] = new ComponentWax(componentName, moles, molesInPhase, compNumber);
        // componentArray[compNumber] = new ComponentWaxWilson(componentName, moles,
        // molesInPhase, compNumber);
        //// componentArray[compNumber] = new ComponentWonWax(componentName, moles,
        // molesInPhase, compNumber);
    }

}
