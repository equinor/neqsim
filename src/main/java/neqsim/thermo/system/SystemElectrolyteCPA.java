package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseElectrolyteCPA;
import neqsim.thermo.util.constants.FurstElectrolyteConstants;

/**
 *
 * @author Even Solbraa
 * @version
 */
/**
 * This class defines a thermodynamic system using the Electrolyte CPA EoS of Equinor
 */
public class SystemElectrolyteCPA extends SystemFurstElectrolyteEos {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for SystemElectrolyteCPA.
     * </p>
     */
    public SystemElectrolyteCPA() {
        super();
        modelName = "Electrolyte-CPA-EOS";
        attractiveTermNumber = 0;
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseElectrolyteCPA();
        }
        FurstElectrolyteConstants.setFurstParams("electrolyteCPA");
        this.useVolumeCorrection(false);
    }

    /**
     * <p>
     * Constructor for SystemElectrolyteCPA.
     * </p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemElectrolyteCPA(double T, double P) {
        super(T, P);
        attractiveTermNumber = 0;
        modelName = "Electrolyte-CPA-EOS";
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseElectrolyteCPA();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
        FurstElectrolyteConstants.setFurstParams("electrolyteCPA");
        this.useVolumeCorrection(false);
    }

    /** {@inheritDoc} */
    @Override
    public SystemElectrolyteCPA clone() {
        SystemElectrolyteCPA clonedSystem = null;
        try {
            clonedSystem = (SystemElectrolyteCPA) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        // for(int i = 0; i < numberOfPhases; i++) {
        // clonedSystem.phaseArray[i] =(PhaseElectrolyteCPA) phaseArray[i].clone();
        // }

        return clonedSystem;
    }
}
