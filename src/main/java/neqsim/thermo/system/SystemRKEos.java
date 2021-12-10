/*
 * System_SRK_EOS.java
 *
 * Created on 8. april 2000, 23:05
 */

package neqsim.thermo.system;

import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseRK;

/**
 *
 * @author  Even Solbraa
 * @version
 */

/**
 * This class defines a thermodynamic system using the RK equation of state
 */
public class SystemRKEos extends SystemEos {

    private static final long serialVersionUID = 1000;

    /** Creates a thermodynamic system using the SRK equation of state. */
    // SystemSrkEos clonedSystem;

    public SystemRKEos() {
        super();
        modelName = "RK-EOS";
        attractiveTermNumber = 5;
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseRK();
            phaseArray[i].setTemperature(298.15);
            phaseArray[i].setPressure(1.0);
        }
    }

    public SystemRKEos(double T, double P) {
        super(T, P);
        attractiveTermNumber = 5;
        modelName = "RK-EOS";
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseRK();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
    }

    public SystemRKEos(double T, double P, boolean solidCheck) {
        this(T, P);
        attractiveTermNumber = 5;
        numberOfPhases = 4;
        modelName = "RK-EOS";
        maxNumberOfPhases = 4;
        solidPhaseCheck = solidCheck;

        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseRK();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }

        if (solidPhaseCheck) {
            // System.out.println("here first");
            phaseArray[numberOfPhases - 1] = new PhasePureComponentSolid();
            phaseArray[numberOfPhases - 1].setTemperature(T);
            phaseArray[numberOfPhases - 1].setPressure(P);
            phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
        }
    }

    @Override
    public SystemRKEos clone() {
        SystemRKEos clonedSystem = null;
        try {
            clonedSystem = (SystemRKEos) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedSystem;
    }

}