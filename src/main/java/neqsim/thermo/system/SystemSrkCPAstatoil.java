/*
 * System_SRK_EOS.java
 *
 * Created on 8. april 2000, 23:05
 */

package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSrkCPAs;

/**
 *
 * @author Even Solbraa
 * @version
 */

/**
 * This class defines a thermodynamic system using the CPA-EOS of Equinor equation of state
 */
public class SystemSrkCPAstatoil extends SystemSrkCPAs {
    private static final long serialVersionUID = 1000;
    /** Creates a thermodynamic system using the SRK equation of state. */
    // SystemSrkEos clonedSystem;

    /**
     * Constructor of a fluid object using the CPA-EoS version of Equinor
     */
    public SystemSrkCPAstatoil() {
        super();
        attractiveTermNumber = 15;
        modelName = "CPAs-SRK-EOS-statoil";
    }

    /**
     * Constructor of a fluid object using the CPA-EoS version of Equinor
     *
     * @param T The temperature in unit Kelvin
     * @param P The pressure in unit bara (absolute pressure)
     */
    public SystemSrkCPAstatoil(double T, double P) {
        super(T, P);
        modelName = "CPAs-SRK-EOS-statoil";
        attractiveTermNumber = 15;
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseSrkCPAs();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
        this.useVolumeCorrection(true);
    }

    /**
     * Constructor of a fluid object using the CPA-EoS version of Equinor
     *
     * @param T The temperature in unit Kelvin
     * @param P The pressure in unit bara (absolute pressure)
     * @param solidCheck a boolean variable specifying if solid phase check and calculation should
     *        be done
     */
    public SystemSrkCPAstatoil(double T, double P, boolean solidCheck) {
        super(T, P, solidCheck);
        modelName = "CPAs-SRK-EOS-statoil";
        attractiveTermNumber = 15;
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseSrkCPAs();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
        this.useVolumeCorrection(true);

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

    @Override
    public SystemSrkCPAstatoil clone() {
        SystemSrkCPAstatoil clonedSystem = null;
        try {
            clonedSystem = (SystemSrkCPAstatoil) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        // for(int i = 0; i < numberOfPhases; i++) {
        // clonedSystem.phaseArray[i] = (PhaseInterface) phaseArray[i].clone();
        // }
        //
        return clonedSystem;
    }
}