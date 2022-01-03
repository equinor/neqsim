package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentGEInterface;
import neqsim.thermo.component.ComponentGENRTLmodifiedWS;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class PhaseGENRTLmodifiedWS extends PhaseGENRTLmodifiedHV {

    private static final long serialVersionUID = 1000;

    public PhaseGENRTLmodifiedWS() {
        super();
    }

    public PhaseGENRTLmodifiedWS(PhaseInterface phase, double[][] alpha, double[][] Dij,
            String[][] mixRule, double[][] intparam) {
        super(phase, alpha, Dij, mixRule, intparam);
        componentArray = new ComponentGENRTLmodifiedWS[alpha[0].length];
        for (int i = 0; i < alpha[0].length; i++) {
            numberOfComponents++;
            componentArray[i] = new ComponentGENRTLmodifiedWS(phase.getComponents()[i].getName(),
                    phase.getComponents()[i].getNumberOfmoles(),
                    phase.getComponents()[i].getNumberOfMolesInPhase(),
                    phase.getComponents()[i].getComponentNumber());
        }
    }

    public PhaseGENRTLmodifiedWS(PhaseInterface phase, double[][] alpha, double[][] Dij,
            double[][] DijT, String[][] mixRule, double[][] intparam) {
        super(phase, alpha, Dij, DijT, mixRule, intparam);
        componentArray = new ComponentGENRTLmodifiedWS[alpha[0].length];
        for (int i = 0; i < alpha[0].length; i++) {
            componentArray[i] = new ComponentGENRTLmodifiedWS(phase.getComponents()[i].getName(),
                    phase.getComponents()[i].getNumberOfmoles(),
                    phase.getComponents()[i].getNumberOfMolesInPhase(),
                    phase.getComponents()[i].getComponentNumber());
        }
    }

    @Override
    public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase,
            double beta) {
        super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
    }

    @Override
    public void setMixingRule(int type) {
        super.setMixingRule(type);
        this.intparam = mixSelect.getWSintparam();
        this.alpha = mixSelect.getNRTLalpha();
        this.mixRule = mixSelect.getClassicOrHV();
        this.Dij = mixSelect.getNRTLDij();
    }

    @Override
    public void addcomponent(String componentName, double moles, double molesInPhase,
            int compNumber) {
        super.addcomponent(molesInPhase);
        componentArray[compNumber] =
                new ComponentGENRTLmodifiedWS(componentName, moles, molesInPhase, compNumber);
    }

    @Override
    public double getExessGibbsEnergy(PhaseInterface phase, int numberOfComponents,
            double temperature, double pressure, int phasetype) {
        double GE = 0;
        for (int i = 0; i < numberOfComponents; i++) {
            if (type == 0) {
                GE += phase.getComponents()[i].getx()
                        * Math.log(((ComponentGEInterface) componentArray[i]).getGamma(phase,
                                numberOfComponents, temperature, pressure, phasetype, alpha, Dij,
                                intparam, mixRule));
            }
            if (type == 1) {
                GE += phase.getComponents()[i].getx()
                        * Math.log(((ComponentGENRTLmodifiedWS) componentArray[i]).getGamma(phase,
                                numberOfComponents, temperature, pressure, phasetype, alpha, Dij,
                                DijT, intparam, mixRule));
            }
        }
        return R * temperature * GE * numberOfMolesInPhase;
    }

}
