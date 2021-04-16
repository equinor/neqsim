/*
 * PhaseSolid.java
 *
 * Created on 18. august 2001, 12:38
 */
package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentSolid;

/**
 *
 * @author esol
 * @version
 */
public abstract class PhaseSolid extends PhaseSrkEos {

    private static final long serialVersionUID = 1000;

    /** Creates new PhaseSolid */
    public PhaseSolid() {
        super();
        phaseTypeName = "solid";
    }

    public Object clone() {
        PhaseSolid clonedPhase = null;
        try {
            clonedPhase = (PhaseSolid) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }
        return clonedPhase;
    }

    public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase, double beta) { // type = 0
                                                                                                            // start
                                                                                                            // init type
                                                                                                            // =1 gi nye
                                                                                                            // betingelser
        super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
        phaseTypeName = "solid";
    }

    public void addcomponent(String componentName, double molesInPhase, double moles, int compNumber) {
        super.addcomponent(molesInPhase);
        componentArray[compNumber] = new ComponentSolid(componentName, moles, molesInPhase, compNumber);
    }

    public double getEnthalpy() {
        double fusionHeat = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            fusionHeat += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getHeatOfFusion();
        }
        return super.getEnthalpy() - fusionHeat;
    }

    public void setSolidRefFluidPhase(PhaseInterface refPhase) {
        for (int i = 0; i < numberOfComponents; i++) {
            ((ComponentSolid) componentArray[i]).setSolidRefFluidPhase(refPhase);
        }
    }

    /**
     * method to get density of a phase note: at the moment return density of water
     * (997 kg/m3)
     *
     * @return density with unit kg/m3
     */
    public double getDensityTemp() {
        double density = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            density += getWtFrac(i)
                    * ((ComponentSolid) componentArray[i]).getPureComponentSolidDensity(getTemperature()) * 1000.0;
        }
        molarVolume = density / getMolarMass() * 1e-5;
        return density;
    }

}
