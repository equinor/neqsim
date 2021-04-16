/*
 * PhaseGENRTL.java
 *
 * Created on 17. juli 2000, 20:51
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentGEInterface;
import neqsim.thermo.component.ComponentGeNRTL;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class PhaseGENRTL extends PhaseGE {

    private static final long serialVersionUID = 1000;

    double[][] alpha;
    String[][] mixRule;
    double[][] intparam;
    double[][] Dij;
    double GE = 0.0;

    /** Creates new PhaseGENRTLmodifiedHV */

    public PhaseGENRTL() {
        super();
    }

    public PhaseGENRTL(PhaseInterface phase, double[][] alpha, double[][] Dij, String[][] mixRule,
            double[][] intparam) {
        super();
        componentArray = new ComponentGeNRTL[alpha[0].length];
        this.mixRule = mixRule;
        this.alpha = alpha;
        this.Dij = Dij;
        this.intparam = intparam;
        for (int i = 0; i < alpha[0].length; i++) {
            numberOfComponents++;
            componentArray[i] = new ComponentGeNRTL(phase.getComponents()[i].getName(),
                    phase.getComponents()[i].getNumberOfmoles(), phase.getComponents()[i].getNumberOfMolesInPhase(),
                    phase.getComponents()[i].getComponentNumber());
        }
        setMixingRule(2);
    }

    public void addcomponent(String componentName, double moles, double molesInPhase, int compNumber) {
        super.addcomponent(molesInPhase);
        componentArray[compNumber] = new ComponentGeNRTL(componentName, moles, molesInPhase, compNumber);
    }

    public void setMixingRule(int type) {
        super.setMixingRule(type);
        this.intparam = mixSelect.getSRKbinaryInteractionParameters();
        this.alpha = mixSelect.getNRTLalpha();
        this.mixRule = mixSelect.getClassicOrHV();
        this.Dij = mixSelect.getNRTLDij();
    }

    public void setAlpha(double[][] alpha) {
        for (int i = 0; i < alpha.length; i++) {
            System.arraycopy(alpha[i], 0, this.alpha[i], 0, alpha[0].length);
        }
    }

    public void setDij(double[][] Dij) {
        for (int i = 0; i < Dij.length; i++) {
            System.arraycopy(Dij[i], 0, this.Dij[i], 0, Dij[0].length);
        }
    }

    public double getExessGibbsEnergy(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
            int phasetype) {
        GE = 0;
        for (int i = 0; i < numberOfComponents; i++) {
            GE += phase.getComponents()[i].getx() * Math.log(((ComponentGEInterface) componentArray[i]).getGamma(phase,
                    numberOfComponents, temperature, pressure, phasetype, alpha, Dij, intparam, mixRule));
        }

        return R * temperature * numberOfMolesInPhase * GE;// phase.getNumberOfMolesInPhase()*
    }

    public double getGibbsEnergy() {
        return R * temperature * numberOfMolesInPhase * (GE + Math.log(pressure));
    }

    public double getExessGibbsEnergy() {
        // double GE = getExessGibbsEnergy(this, numberOfComponents, temperature,
        // pressure, phaseType);
        return GE;
    }
}
