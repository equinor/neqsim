/*
 * System_SRK_EOS.java
 *
 * Created on 8. april 2000, 23:05
 */

package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseGENRTL;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSrkEos;

/**
 *
 * @author Even Solbraa
 * @version
 */

/**
 * This class defines a thermodynamic system using the SRK EoS amd NRTL for liquids
 */
public class SystemNRTL extends SystemEos {
    private static final long serialVersionUID = 1000;

    /**
     * Creates a thermodynamic system using the SRK equation of state.
     */
    // SystemSrkEos clonedSystem;
    public SystemNRTL() {
        super();
        modelName = "NRTL-GE-model";
        attractiveTermNumber = 0;
        phaseArray[0] = new PhaseSrkEos();
        for (int i = 1; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseGENRTL();// modifiedWS();
        }
    }

    /**
     * <p>
     * Constructor for SystemNRTL.
     * </p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemNRTL(double T, double P) {
        super(T, P);
        attractiveTermNumber = 0;
        modelName = "NRTL-GE-model";
        phaseArray[0] = new PhaseSrkEos();
        phaseArray[0].setTemperature(T);
        phaseArray[0].setPressure(P);
        for (int i = 1; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseGENRTL();// new PhaseGENRTLmodifiedWS();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
    }

    /**
     * <p>
     * Constructor for SystemNRTL.
     * </p>
     *
     * @param T a double
     * @param P a double
     * @param solidCheck a boolean
     */
    public SystemNRTL(double T, double P, boolean solidCheck) {
        this(T, P);
        attractiveTermNumber = 0;
        numberOfPhases = 4;
        maxNumberOfPhases = 4;
        modelName = "NRTL-GE-model";
        solidPhaseCheck = solidCheck;

        phaseArray[0] = new PhaseSrkEos();
        phaseArray[0].setTemperature(T);
        phaseArray[0].setPressure(P);
        for (int i = 1; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseGENRTL();// new PhaseGENRTLmodifiedWS();
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

    /** {@inheritDoc} */
    @Override
    public SystemNRTL clone() {
        SystemNRTL clonedSystem = null;
        try {
            clonedSystem = (SystemNRTL) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        // for(int i = 0; i < numberOfPhases; i++) {
        // clonedSystem.phaseArray[i] = (PhaseInterface) phaseArray[i].clone();
        // }

        return clonedSystem;
    }
}
