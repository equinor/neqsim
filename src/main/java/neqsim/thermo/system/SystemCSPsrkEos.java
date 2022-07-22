package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseCSPsrkEos;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;

/**
 * This class defines a thermodynamic system using the CSP SRK equation of state
 *
 * @author Even Solbraa
 */
public class SystemCSPsrkEos extends SystemSrkEos {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for SystemCSPsrkEos.
     * </p>
     */
    public SystemCSPsrkEos() {
        super();
        modelName = "CSPsrk-EOS";
        attractiveTermNumber = 0;
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseCSPsrkEos();
            phaseArray[i].setTemperature(298.15);
            phaseArray[i].setPressure(1.0);
        }
    }

    /**
     * <p>
     * Constructor for SystemCSPsrkEos.
     * </p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemCSPsrkEos(double T, double P) {
        super(T, P);
        modelName = "CSPsrk-EOS";
        attractiveTermNumber = 0;
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseCSPsrkEos();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
    }

    /**
     * <p>
     * Constructor for SystemCSPsrkEos.
     * </p>
     *
     * @param T a double
     * @param P a double
     * @param solidCheck a boolean
     */
    public SystemCSPsrkEos(double T, double P, boolean solidCheck) {
        this(T, P);
        modelName = "CSPsrk-EOS";
        attractiveTermNumber = 0;
        setNumberOfPhases(5);
        solidPhaseCheck = solidCheck;

        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseCSPsrkEos();
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
    public SystemCSPsrkEos clone() {
        SystemCSPsrkEos clonedSystem = null;
        try {
            clonedSystem = (SystemCSPsrkEos) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedSystem;
    }
}
