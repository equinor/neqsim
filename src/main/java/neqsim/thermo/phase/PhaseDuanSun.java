/*
 * PhaseGENRTL.java
 *
 * Created on 17. juli 2000, 20:51
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentGeDuanSun;

/**
 * <p>
 * PhaseDuanSun class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseDuanSun extends PhaseGE {

    private static final long serialVersionUID = 1000;

    double[][] alpha;
    String[][] mixRule;
    double[][] intparam;
    double[][] Dij;
    double GE = 0.0;

    /**
     * <p>Constructor for PhaseDuanSun.</p>
     */
    public PhaseDuanSun() {
        super();
    }

    /** {@inheritDoc} */
    @Override
    public void addcomponent(String componentName, double moles, double molesInPhase,
            int compNumber) {
        super.addcomponent(molesInPhase);
        componentArray[compNumber] =
                new ComponentGeDuanSun(componentName, moles, molesInPhase, compNumber);
    }

    /** {@inheritDoc} */
    @Override
    public void setMixingRule(int type) {
        super.setMixingRule(type);
        this.alpha = mixSelect.getNRTLalpha();
        this.Dij = mixSelect.getNRTLDij();
    }

    /** {@inheritDoc} */
    @Override
    public void setAlpha(double[][] alpha) {
        for (int i = 0; i < alpha.length; i++) {
            System.arraycopy(alpha[i], 0, this.alpha[i], 0, alpha[0].length);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setDij(double[][] Dij) {
        for (int i = 0; i < Dij.length; i++) {
            System.arraycopy(Dij[i], 0, this.Dij[i], 0, Dij[0].length);
        }
    }

    /** {@inheritDoc} */
    @Override
    public double getExessGibbsEnergy(PhaseInterface phase, int numberOfComponents,
            double temperature, double pressure, int phasetype) {
        GE = 0;
        double salinity = 0.0;
        // double k=0.0;
        // salinity=salinity+phase.getComponent("Na+").getNumberOfMolesInPhase()/(phase.getComponent("water").getNumberOfmoles()*phase.getComponent("water").getMolarMass());

        // for (int i=2;i<numberOfComponents;i++) {
        // salinity=salinity+phase.getComponents()[i].getNumberOfMolesInPhase()/(phase.getComponent("water").getNumberOfmoles()*phase.getComponent("water").getMolarMass());
        // }
        for (int i = 0; i < numberOfComponents; i++) {
            if (phase.getComponents()[i].isIsIon()) {
                salinity = salinity + phase.getComponents()[i].getNumberOfMolesInPhase()
                        / (phase.getComponent("water").getNumberOfMolesInPhase()
                                * phase.getComponent("water").getMolarMass());
            }
        }
        // for (int i=0; i < numberOfComponents; i++) {
        // if(phase.getComponents()[i].isIsIon()) {
        // salinity=salinity+phase.getComponents()[i].getNumberOfMolesInPhase()/(phase.getComponent("water").getNumberOfmoles()*phase.getComponent("water").getMolarMass());
        // phase.getComponent("Na+").getNumberOfmoles()
        // }
        // }

        // salinity=salinity+phase.getComponent("Na+").getNumberOfmoles()/(phase.getComponent("water").getNumberOfmoles()*phase.getComponent("water").getMolarMass());

        for (int i = 0; i < numberOfComponents; i++) {
            // GE += phase.getComponents()[i].getx()*Math.log(((ComponentGeDuanSun)
            // componentArray[i]).getGammaNRTL(phase, numberOfComponents, temperature, pressure,
            // phasetype, alpha, Dij));
            GE += phase.getComponents()[i].getx()
                    * Math.log(((ComponentGeDuanSun) componentArray[i]).getGammaPitzer(phase,
                            numberOfComponents, temperature, pressure, phasetype, salinity));
        }

        return R * temperature * numberOfMolesInPhase * GE;// phase.getNumberOfMolesInPhase()*
    }

    /** {@inheritDoc} */
    @Override
    public double getGibbsEnergy() {
        return R * temperature * numberOfMolesInPhase * (GE + Math.log(pressure));
    }

    /** {@inheritDoc} */
    @Override
    public double getExessGibbsEnergy() {
        // double GE = getExessGibbsEnergy(this, numberOfComponents, temperature,
        // pressure, phaseType);
        return GE;
    }

}
