package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseGEUnifacPSRK;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSrkEos;

/**
 * This class defines a thermodynamic system using the SRK equation of state
 * 
 * @author Even Solbraa
 */
public class SystemUNIFACpsrk extends SystemEos {
    private static final long serialVersionUID = 1000;

    public SystemUNIFACpsrk() {
        super();
        modelName = "UNIFAC-GE-model";
        attractiveTermNumber = 0;
        phaseArray[0] = new PhaseSrkEos();
        for (int i = 1; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseGEUnifacPSRK();
        }
    }

    /**
     * <p>
     * Constructor for SystemUNIFACpsrk.
     * </p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemUNIFACpsrk(double T, double P) {
        super(T, P);
        attractiveTermNumber = 0;
        modelName = "UNIFAC-GE-model";
        phaseArray[0] = new PhaseSrkEos();
        phaseArray[0].setTemperature(T);
        phaseArray[0].setPressure(P);
        for (int i = 1; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseGEUnifacPSRK();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
    }

    /**
     * <p>
     * Constructor for SystemUNIFACpsrk.
     * </p>
     *
     * @param T a double
     * @param P a double
     * @param solidCheck a boolean
     */
    public SystemUNIFACpsrk(double T, double P, boolean solidCheck) {
        this(T, P);
        attractiveTermNumber = 0;
        setNumberOfPhases(4);
        modelName = "UNIFAC-GE-model";
        solidPhaseCheck = solidCheck;

        phaseArray[0] = new PhaseSrkEos();
        phaseArray[0].setTemperature(T);
        phaseArray[0].setPressure(P);
        for (int i = 1; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseGEUnifacPSRK();
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
    public SystemUNIFACpsrk clone() {
        SystemUNIFACpsrk clonedSystem = null;
        try {
            clonedSystem = (SystemUNIFACpsrk) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        // for(int i = 0; i < numberOfPhases; i++) {
        // clonedSystem.phaseArray[i] = (PhaseInterface) phaseArray[i].clone();
        // }

        return clonedSystem;
    }
}
