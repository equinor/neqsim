package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseBWRSEos;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;

/**
 * This class defines a thermodynamic system using the BWRS equation of state
 *
 * @author Even Solbraa
 */
public class SystemBWRSEos extends SystemEos {
    private static final long serialVersionUID = 1000;
    double[][] TBPfractionCoefs = {{163.12, 86.052, 0.43475, -1877.4, 0.0},
            {-0.13408, 2.5019, 208.46, -3987.2, 1.0}, {0.7431, 0.004812, 0.009671, -3.7e-6, 0.0}};

    /**
     * <p>
     * Constructor for SystemBWRSEos.
     * </p>
     */
    public SystemBWRSEos() {
        super();
        modelName = "BWRS-EOS";
        attractiveTermNumber = 0;
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseBWRSEos();
            phaseArray[i].setTemperature(298.15);
            phaseArray[i].setPressure(1.0);
        }
    }

    /**
     * <p>
     * Constructor for SystemBWRSEos.
     * </p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemBWRSEos(double T, double P) {
        super(T, P);
        modelName = "BWRS-EOS";
        attractiveTermNumber = 0;
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseBWRSEos();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
    }

    /**
     * <p>
     * Constructor for SystemBWRSEos.
     * </p>
     *
     * @param T a double
     * @param P a double
     * @param solidCheck a boolean
     */
    public SystemBWRSEos(double T, double P, boolean solidCheck) {
        this(T, P);
        modelName = "BWRS-EOS";
        attractiveTermNumber = 0;
        setNumberOfPhases(5);
        solidPhaseCheck = solidCheck;

        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseBWRSEos();
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

        if (hydrateCheck) {
            // System.out.println("here first");
            phaseArray[numberOfPhases - 1] = new PhaseHydrate();
            phaseArray[numberOfPhases - 1].setTemperature(T);
            phaseArray[numberOfPhases - 1].setPressure(P);
            phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
        }
    }

    /** {@inheritDoc} */
    @Override
    public SystemBWRSEos clone() {
        SystemBWRSEos clonedSystem = null;
        try {
            clonedSystem = (SystemBWRSEos) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        // clonedSystem.phaseArray = (PhaseInterface[]) phaseArray.clone();
        // for(int i = 0; i < numberOfPhases; i++) {
        // clonedSystem.phaseArray[i] = (PhaseInterface) phaseArray[i].clone();
        // }

        return clonedSystem;
    }
}
