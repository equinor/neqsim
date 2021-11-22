/*
 * PhaseGENRTLmodifiedHV.java
 *
 * Created on 18. juli 2000, 18:32
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentGEInterface;
import neqsim.thermo.component.ComponentGENRTLmodifiedHV;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class PhaseGENRTLmodifiedHV extends PhaseGENRTL {
    private static final long serialVersionUID = 1000;

    double[][] DijT;
    int type = 0;

    /** Creates new PhaseGENRTLmodifiedHV */

    public PhaseGENRTLmodifiedHV() {
        super();
        mixRuleEos = mixSelect.getMixingRule(1);
    }

    public PhaseGENRTLmodifiedHV(PhaseInterface phase, double[][] alpha, double[][] Dij,
            String[][] mixRule, double[][] intparam) {
        super(phase, alpha, Dij, mixRule, intparam);
        componentArray = new ComponentGENRTLmodifiedHV[alpha[0].length];
        type = 0;
        for (int i = 0; i < alpha[0].length; i++) {
            componentArray[i] = new ComponentGENRTLmodifiedHV(phase.getComponents()[i].getName(),
                    phase.getComponents()[i].getNumberOfmoles(),
                    phase.getComponents()[i].getNumberOfMolesInPhase(),
                    phase.getComponents()[i].getComponentNumber());
        }
    }

    public PhaseGENRTLmodifiedHV(PhaseInterface phase, double[][] alpha, double[][] Dij,
            double[][] DijT, String[][] mixRule, double[][] intparam) {
        super(phase, alpha, Dij, mixRule, intparam);
        componentArray = new ComponentGENRTLmodifiedHV[alpha[0].length];
        type = 1;
        this.DijT = DijT;
        for (int i = 0; i < alpha[0].length; i++) {
            componentArray[i] = new ComponentGENRTLmodifiedHV(phase.getComponents()[i].getName(),
                    phase.getComponents()[i].getNumberOfmoles(),
                    phase.getComponents()[i].getNumberOfMolesInPhase(),
                    phase.getComponents()[i].getComponentNumber());
        }
    }

    @Override
    public void addcomponent(String componentName, double moles, double molesInPhase,
            int compNumber) {
        super.addcomponent(molesInPhase);
        componentArray[compNumber] =
                new ComponentGENRTLmodifiedHV(componentName, moles, molesInPhase, compNumber);
    }

    @Override
    public void setMixingRule(int type) {
        super.setMixingRule(type);
        this.DijT = mixSelect.getHVDijT();
        this.intparam = mixSelect.getSRKbinaryInteractionParameters();
        this.alpha = mixSelect.getHValpha();
        this.mixRule = mixSelect.getClassicOrHV();
        this.Dij = mixSelect.getHVDij();
    }

    @Override
    public void setParams(PhaseInterface phase, double[][] alpha, double[][] Dij, double[][] DijT,
            String[][] mixRule, double[][] intparam) {
        this.mixRule = mixRule;
        this.alpha = alpha;
        this.Dij = Dij;
        type = 1;
        this.DijT = DijT;
        this.intparam = intparam;
    }

    @Override
    public void setDijT(double[][] DijT) {
        for (int i = 0; i < DijT.length; i++) {
            System.arraycopy(DijT[i], 0, this.DijT[i], 0, DijT[0].length);
        }
    }

    @Override
    public double getExessGibbsEnergy(PhaseInterface phase, int numberOfComponents,
            double temperature, double pressure, int phasetype) {
        GE = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            if (type == 0) {
                GE += phase.getComponents()[i].getx()
                        * Math.log(((ComponentGEInterface) componentArray[i]).getGamma(phase,
                                numberOfComponents, temperature, pressure, phasetype, alpha, Dij,
                                intparam, mixRule));
            } else if (type == 1) {
                GE += phase.getComponents()[i].getx()
                        * Math.log(((ComponentGENRTLmodifiedHV) componentArray[i]).getGamma(phase,
                                numberOfComponents, temperature, pressure, phasetype, alpha, Dij,
                                DijT, intparam, mixRule));
            }
        }
        return (R * phase.getTemperature() * GE) * phase.getNumberOfMolesInPhase();
    }

    @Override
    public double getGibbsEnergy() {
        double val = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            val += getComponent(i).getNumberOfMolesInPhase()
                    * (getComponent(i).getLogFugasityCoeffisient());// +Math.log(getComponent(i).getx()*getComponent(i).getAntoineVaporPressure(temperature)));
        }
        return R * temperature * ((val) + Math.log(pressure) * numberOfMolesInPhase);
    }

    @Override
    public double getHresTP() {
        double val = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            val -= getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getdfugdt();
        }
        return R * temperature * temperature * val;
    }
}
