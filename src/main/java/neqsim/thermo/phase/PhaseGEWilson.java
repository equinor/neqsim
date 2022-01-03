/*
 * PhaseGEUniquac.java
 *
 * Created on 11. juli 2000, 21:01
 */
package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentGEWilson;

/**
 * <p>PhaseGEWilson class.</p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseGEWilson extends PhaseGE {

    private static final long serialVersionUID = 1000;

    double GE = 0;

    /**
     * Creates new PhaseGEUniquac
     */
    public PhaseGEWilson() {
        super();
        componentArray = new ComponentGEWilson[MAX_NUMBER_OF_COMPONENTS];
    }

    /**
     * <p>Constructor for PhaseGEWilson.</p>
     *
     * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
     * @param alpha an array of {@link double} objects
     * @param Dij an array of {@link double} objects
     * @param mixRule an array of {@link java.lang.String} objects
     * @param intparam an array of {@link double} objects
     */
    public PhaseGEWilson(PhaseInterface phase, double[][] alpha, double[][] Dij, String[][] mixRule,
            double[][] intparam) {
        super();
        componentArray = new ComponentGEWilson[alpha[0].length];
        for (int i = 0; i < alpha[0].length; i++) {
            numberOfComponents++;
            componentArray[i] = new ComponentGEWilson(phase.getComponents()[i].getName(),
                    phase.getComponents()[i].getNumberOfmoles(), phase.getComponents()[i].getNumberOfMolesInPhase(),
                    phase.getComponents()[i].getComponentNumber());
        }
    }

	/** {@inheritDoc} */
    @Override
	public void addcomponent(String componentName, double moles, double molesInPhase, int compNumber) {
        super.addcomponent(molesInPhase);
        componentArray[compNumber] = new ComponentGEWilson(componentName, moles, molesInPhase, compNumber);
    }

	/** {@inheritDoc} */
    @Override
	public void setMixingRule(int type) {
        super.setMixingRule(type);
    }

	/** {@inheritDoc} */
    @Override
	public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase, double beta) {
        super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
    }

	/** {@inheritDoc} */
    @Override
	public double getExessGibbsEnergy(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
            int phasetype) {
        GE = 0;
        for (int i = 0; i < numberOfComponents; i++) {
            GE += phase.getComponents()[i].getx()
                    * Math.log(((ComponentGEWilson) componentArray[i]).getWilsonActivityCoeffisient(phase));
        }

        return R * temperature * numberOfMolesInPhase * GE;
    }

	/** {@inheritDoc} */
    @Override
	public double getGibbsEnergy() {
        return R * temperature * numberOfMolesInPhase * (GE + Math.log(pressure));
    }

	/** {@inheritDoc} */
    @Override
	public double getExessGibbsEnergy() {
        // GE = getExessGibbsEnergy(this, numberOfComponents, temperature, pressure,
        // phaseType);
        return GE;
    }
}
