/*
 * ComponentGEUniquac.java
 *
 * Created on 10. juli 2000, 21:06
 */

package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */

public class ComponentKentEisenberg extends ComponentGeNRTL {

    private static final long serialVersionUID = 1000;

    /** Creates new ComponentGENRTLmodifiedHV */
    public ComponentKentEisenberg() {
    }

    public ComponentKentEisenberg(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
    }

    @Override
	public double fugcoef(PhaseInterface phase) {
        double gamma = 1.0;
        if (referenceStateType.equals("solvent")) {
            fugasityCoeffisient = gamma * getAntoineVaporPressure(phase.getTemperature()) / phase.getPressure();
            gammaRefCor = gamma;
        } else {
            double activinf = 1.0;
            if (ionicCharge == 0) {
                fugasityCoeffisient = activinf * getHenryCoef(phase.getTemperature()) / phase.getPressure();
            } else {
                fugasityCoeffisient = 1e8;
            }
            gammaRefCor = activinf;
        }
        logFugasityCoeffisient = Math.log(fugasityCoeffisient);
        // System.out.println("gamma " + gamma);
        return fugasityCoeffisient;
    }

}