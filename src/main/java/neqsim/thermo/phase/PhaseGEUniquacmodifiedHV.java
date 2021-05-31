/*
 * PhaseGEUniquacmodifiedHV.java
 *
 * Created on 18. juli 2000, 20:30
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentGEInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class PhaseGEUniquacmodifiedHV extends PhaseGEUniquac {

    private static final long serialVersionUID = 1000;

    /** Creates new PhaseGEUniquacmodifiedHV */
    public PhaseGEUniquacmodifiedHV() {
        super();
    }

    @Override
	public void addcomponent(String componentName, double moles, double molesInPhase, int compNumber) {
        super.addcomponent(molesInPhase);
        // componentArray[compNumber] = new ComponentGEUniquacmodifiedHV(componentName,
        // moles, molesInPhase, compNumber);
    }

    @Override
	public double getExessGibbsEnergy(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
            int phasetype) {
        double GE = 0;

        ComponentGEInterface[] comp_Array = (ComponentGEInterface[]) this.getcomponentArray();

        for (int i = 0; i < numberOfComponents; i++) {
            // GE = GE + comp_Array[i].getx()*Math.log(comp_Array[i].getGamma(phase,
            // numberOfComponents, temperature, pressure, phasetype));
        }
        return R * temperature * GE * numberOfMolesInPhase;
    }

}