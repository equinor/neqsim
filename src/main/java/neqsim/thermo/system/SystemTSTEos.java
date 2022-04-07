package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseTSTEos;

/**
 * This class defines a thermodynamic system using the SRK equation of state
 * 
 * @author Even Solbraa
 */
public class SystemTSTEos extends SystemEos {
    private static final long serialVersionUID = 1000;
    double[][] TBPfractionCoefs = {{73.404, 97.356, 0.61874, -2059.3, 0.0},
            {0.072846, 2.1881, 163.91, -4043.4, 1.0 / 3.0},
            {0.37377, 0.005493, 0.011793, -4.9e-6, 0.0}};

    /**
     * <p>
     * Constructor for SystemTSTEos.
     * </p>
     */
    public SystemTSTEos() {
        super();
        modelName = "TST-EOS";
        attractiveTermNumber = 14;
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseTSTEos();
            phaseArray[i].setTemperature(298.15);
            phaseArray[i].setPressure(1.0);
        }
    }

    /**
     * <p>
     * Constructor for SystemTSTEos.
     * </p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemTSTEos(double T, double P) {
        super(T, P);
        modelName = "TST-EOS";
        attractiveTermNumber = 14;
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseTSTEos();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
    }

    /**
     * <p>
     * Constructor for SystemTSTEos.
     * </p>
     *
     * @param T a double
     * @param P a double
     * @param solidCheck a boolean
     */
    public SystemTSTEos(double T, double P, boolean solidCheck) {
        this(T, P);
        attractiveTermNumber = 14;
        setNumberOfPhases(5);
        modelName = "TST-EOS";
        solidPhaseCheck = solidCheck;

        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseTSTEos();
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
    public SystemTSTEos clone() {
        SystemTSTEos clonedSystem = null;
        try {
            clonedSystem = (SystemTSTEos) super.clone();
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
