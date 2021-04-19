/*
 * PhaseGEUniquac.java
 *
 * Created on 11. juli 2000, 21:01
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentGEInterface;
import neqsim.thermo.component.ComponentGEUniquac;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class PhaseGEUniquac extends PhaseGE {

    private static final long serialVersionUID = 1000;

    double[][] alpha;
    String[][] mixRule;
    double[][] intparam;
    double[][] Dij;
    double GE = 0.0;

    /** Creates new PhaseGEUniquac */
    public PhaseGEUniquac() {
        super();
        componentArray = new ComponentGEInterface[MAX_NUMBER_OF_COMPONENTS];
    }

    public PhaseGEUniquac(PhaseInterface phase, double[][] alpha, double[][] Dij, String[][] mixRule,
            double[][] intparam) {
        super();
        componentArray = new ComponentGEUniquac[alpha[0].length];
        this.mixRule = mixRule;
        this.alpha = alpha;
        this.Dij = Dij;
        this.intparam = intparam;
        for (int i = 0; i < alpha[0].length; i++) {
            numberOfComponents++;
            componentArray[i] = new ComponentGEUniquac(phase.getComponents()[i].getName(),
                    phase.getComponents()[i].getNumberOfmoles(), phase.getComponents()[i].getNumberOfMolesInPhase(),
                    phase.getComponents()[i].getComponentNumber());
        }
    }

    @Override
	public void addcomponent(String componentName, double moles, double molesInPhase, int compNumber) {
        super.addcomponent(molesInPhase);
        componentArray[compNumber] = new ComponentGEUniquac(componentName, moles, molesInPhase, compNumber);
    }

    @Override
	public void setMixingRule(int type) {
        super.setMixingRule(type);
    }

    @Override
	public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase, double beta) {
        super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
    }

    @Override
	public double getExessGibbsEnergy(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
            int phasetype) {
        GE = 0;
        for (int i = 0; i < numberOfComponents; i++) {
            GE += phase.getComponents()[i].getx() * Math.log(((ComponentGEInterface) componentArray[i]).getGamma(phase,
                    numberOfComponents, temperature, pressure, phasetype, alpha, Dij, intparam, mixRule));
        }

        return R * temperature * numberOfMolesInPhase * GE;
    }

    @Override
	public double getGibbsEnergy() {
        return R * temperature * numberOfMolesInPhase * (GE + Math.log(pressure));
    }

    @Override
	public double getExessGibbsEnergy() {
        // GE = getExessGibbsEnergy(this, numberOfComponents, temperature, pressure,
        // phaseType);
        return GE;
    }

}