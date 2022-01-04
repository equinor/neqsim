/*
 * SolidComponent.java
 *
 * Created on 18. august 2001, 12:45
 */
package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * ComponentWax class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ComponentWax extends ComponentSolid {

    private static final long serialVersionUID = 1000;

    /**
     * <p>Constructor for ComponentWax.</p>
     */
    public ComponentWax() {}

    /**
     * <p>
     * Constructor for ComponentWax.
     * </p>
     *
     * @param component_name a {@link java.lang.String} object
     * @param moles a double
     * @param molesInPhase a double
     * @param compnumber a int
     */
    public ComponentWax(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
    }

    /**
     * {@inheritDoc}
     *
     * Uses Claperyons equation to calculate the solid fugacity
     */
    @Override
    public double fugcoef(PhaseInterface phase1) {
        if (!isWaxFormer()) {
            fugasityCoeffisient = 1.0e50;
            logFugasityCoeffisient = Math.log(fugasityCoeffisient);
            return 1.0e50;
        }
        return fugcoef2(phase1);
    }

    /** {@inheritDoc} */
    @Override
    public double fugcoef2(PhaseInterface phase1) {
        try {
            refPhase.setTemperature(phase1.getTemperature());
        } catch (Exception e) {
            // System.out.println("compname " + componentName);
            e.printStackTrace();
        }
        refPhase.setPressure(phase1.getPressure());
        refPhase.init(refPhase.getNumberOfMolesInPhase(), 1, 1, 0, 1.0);
        refPhase.getComponent(0).fugcoef(refPhase);

        double liquidPhaseFugacity =
                refPhase.getComponent(0).getFugasityCoefficient() * refPhase.getPressure();

        double liquidDenisty = refPhase.getMolarVolume();
        double solidDensity = liquidDenisty * 0.9;
        double refPressure = 1.0;
        double presTerm = -(liquidDenisty - solidDensity) * (phase1.getPressure() - refPressure) / R
                / phase1.getTemperature();
        // System.out.println("heat of fusion" +getHeatOfFusion());
        SolidFug =
                getx() * liquidPhaseFugacity
                        * Math.exp(-getHeatOfFusion() / (R * phase1.getTemperature())
                                * (1.0 - phase1.getTemperature() / getTriplePointTemperature())
                                + presTerm);
        double SolidFug2 =
                getx() * liquidPhaseFugacity
                        * Math.exp(-getHeatOfFusion() / (R * phase1.getTemperature())
                                * (1.0 - phase1.getTemperature() / getTriplePointTemperature())
                                + presTerm);

        fugasityCoeffisient = SolidFug / (phase1.getPressure() * getx());
        logFugasityCoeffisient = Math.log(fugasityCoeffisient);
        return fugasityCoeffisient;

        // getS
    }
    // public double fugcoef(PhaseInterface phase, int numberOfComps, double temp,
    // double pres){
}
