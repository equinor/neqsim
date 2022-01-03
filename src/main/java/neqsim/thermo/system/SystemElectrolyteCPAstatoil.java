/*
 * SystemModifiedFurstElectrolyteEos.java
 *
 * Created on 26. februar 2001, 17:38
 */

package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseElectrolyteCPAstatoil;
import neqsim.thermo.util.constants.FurstElectrolyteConstants;

/**
 *
 * @author  Even Solbraa
 * @version
 */
/**
 * This class defines a thermodynamic system using the Electrolyte CPA EoS of
 * Equinor
 */
public class SystemElectrolyteCPAstatoil extends SystemFurstElectrolyteEos {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new SystemModifiedFurstElectrolyteEos
     */
    public SystemElectrolyteCPAstatoil() {
        super();
        modelName = "Electrolyte-CPA-EOS-statoil";
        attractiveTermNumber = 15;
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseElectrolyteCPAstatoil();
        }
        FurstElectrolyteConstants.setFurstParams("electrolyteCPA");
        this.useVolumeCorrection(true);
    }

    /**
     * <p>Constructor for SystemElectrolyteCPAstatoil.</p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemElectrolyteCPAstatoil(double T, double P) {
        super(T, P);
        attractiveTermNumber = 15;
        modelName = "Electrolyte-CPA-EOS-statoil";
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseElectrolyteCPAstatoil();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
        FurstElectrolyteConstants.setFurstParams("electrolyteCPA");
        this.useVolumeCorrection(true);
    }

    /** {@inheritDoc} */
    @Override
    public SystemElectrolyteCPAstatoil clone() {
        SystemElectrolyteCPAstatoil clonedSystem = null;
        try {
            clonedSystem = (SystemElectrolyteCPAstatoil) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        // for(int i = 0; i < numberOfPhases; i++) {
        // clonedSystem.phaseArray[i] =(PhaseElectrolyteCPA) phaseArray[i].clone();
        // }

        return clonedSystem;
    }

}
