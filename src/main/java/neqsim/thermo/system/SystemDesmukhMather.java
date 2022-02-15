package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseDesmukhMather;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSrkEos;

/**
 * This class defines a thermodynamic system using the SRK equation of state
 * 
 * @author Even Solbraa
 * @version
 */
public class SystemDesmukhMather extends SystemEos {
    private static final long serialVersionUID = 1000;

    public SystemDesmukhMather() {
        super();
        modelName = "Desmukh-Mather-model";
        attractiveTermNumber = 0;
        phaseArray[0] = new PhaseSrkEos();
        for (int i = 1; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseDesmukhMather();// modifiedWS();
        }
    }

    /**
     * <p>
     * Constructor for SystemDesmukhMather.
     * </p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemDesmukhMather(double T, double P) {
        super(T, P);
        attractiveTermNumber = 0;
        modelName = "Desmukh-Mather-model";
        phaseArray[0] = new PhaseSrkEos();
        phaseArray[0].setTemperature(T);
        phaseArray[0].setPressure(P);
        for (int i = 1; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseDesmukhMather();// new PhaseGENRTLmodifiedWS();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
    }

    /**
     * <p>
     * Constructor for SystemDesmukhMather.
     * </p>
     *
     * @param T a double
     * @param P a double
     * @param solidCheck a boolean
     */
    public SystemDesmukhMather(double T, double P, boolean solidCheck) {
        this(T, P);
        attractiveTermNumber = 0;
        numberOfPhases = 4;
        maxNumberOfPhases = 4;
        modelName = "Desmukh-Mather-model";
        solidPhaseCheck = solidCheck;

        phaseArray[0] = new PhaseSrkEos();
        phaseArray[0].setTemperature(T);
        phaseArray[0].setPressure(P);
        for (int i = 1; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseDesmukhMather();// new PhaseGENRTLmodifiedWS();
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
    public SystemDesmukhMather clone() {
        SystemDesmukhMather clonedSystem = null;
        try {
            clonedSystem = (SystemDesmukhMather) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedSystem;
    }
}
