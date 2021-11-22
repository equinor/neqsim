/*
 * ComponentGEUniquacmodifiedHV.java
 *
 * Created on 18. juli 2000, 20:24
 */

package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
abstract class ComponentGEUniquacmodifiedHV extends ComponentGEUniquac {
    private static final long serialVersionUID = 1000;

    /** Creates new ComponentGEUniquacmodifiedHV */
    public ComponentGEUniquacmodifiedHV() {}

    public ComponentGEUniquacmodifiedHV(String component_name, double moles, double molesInPhase,
            int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
    }

    @Override
    public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature,
            double pressure, int phasetype) {
        double V = 0, F = 0, a, gammaC = 0, gammaR = 0, temp1 = 0, temp2 = 0, temp3 = 0, temp4 = 0,
                temp5 = 0, gamma;
        int j, k;

        // PhaseGEInterface phaseny = (PhaseGEInterface) phase.getPhase();
        // PhaseGEInterface GEPhase = phaseny.getGEphase();

        return 1;// super.getGamma(GEPhase, numberOfComponents, temperature, pressure,
                 // phasetype);
    }
}
