/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author rahmat
 */
public class ComponentWonWax extends ComponentSolid {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new SolidComponent
     */
    public ComponentWonWax() {
    }

    public ComponentWonWax(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
    }

    //Uses Claperyons equation to calculate the solid fugacity
    public double fugcoef(PhaseInterface phase1) {
        if (!isWaxFormer()) {
            return 1.0e30;
        }
        return fugcoef2(phase1);
    }

    public double fugcoef2(PhaseInterface phase1) {
        refPhase.setTemperature(phase1.getTemperature());
        refPhase.setPressure(phase1.getPressure());
        refPhase.init(refPhase.getNumberOfMolesInPhase(), 1, 1, 0, 1.0);
        refPhase.getComponent(0).fugcoef(refPhase);

        double liquidPhaseFugacity = refPhase.getComponent(0).getFugasityCoefficient() * refPhase.getPressure();


        double solidActivityCoefficient = getWonActivityCoeficient(phase1);
        System.out.println("activity coef Won " + solidActivityCoefficient);
        SolidFug = getx() * liquidPhaseFugacity * Math.exp(-getHeatOfFusion() / (R * phase1.getTemperature()) * (1.0 - phase1.getTemperature() / getTriplePointTemperature()));

        fugasityCoeffisient = solidActivityCoefficient * SolidFug / (phase1.getPressure() * getx());
        logFugasityCoeffisient = Math.log(fugasityCoeffisient);
        return fugasityCoeffisient;
    }

    //public double fugcoef(PhaseInterface phase, int numberOfComps, double temp, double pres){
    public double getWonActivityCoeficient(PhaseInterface phase1) {
        double TetaAvg = 0.0;
        double SolidActivity = 0.0;
        double gamma = 0.0;
        for (int i = 0; i < phase1.getNumberOfComponents(); i++) {
            double tempSum = 0.0;
            for (int j = 0; j < phase1.getNumberOfComponents(); j++) {
                tempSum += phase1.getComponent(j).getx() * (((ComponentWonWax) phase1.getComponent(j)).getWonVolume(phase1));
            }
            TetaAvg += phase1.getComponent(i).getx() * (((ComponentWonWax) phase1.getComponent(i)).getWonVolume(phase1)) / tempSum * ((ComponentWonWax) phase1.getComponent(i)).getWonParam(phase1);
        }
        gamma = Math.exp(getWonVolume(phase1) * Math.pow((TetaAvg - getWonParam(phase1)), 2) / (1.9858775 * phase1.getTemperature()));

        return gamma;
    }

    public double getWonVolume(PhaseInterface phase1) {
        double d25 = 0.8155 + 0.6273e-4 * getMolarMass() - 13.06 / getMolarMass();

        return getMolarMass() / d25;
    }

    public double getWonParam(PhaseInterface phase1) {
        double WonParam = 0.0;
        //calculation of Heat of fusion
        double Tf = 374.5 * 0.02617 * getMolarMass() - 20172 / getMolarMass();
        double Hf = 0.1426 * getMolarMass() * Tf;

        //calculation of Enthalpy of evaporation
        double x = 1.0 - phase1.getTemperature() / getTC();
        double deltaHvap0 = 5.2804 * Math.pow(x, 0.3333) + 12.865 * Math.pow(x, 0.8333) + 1.171 * Math.pow(x, 1.2083) - 13.166 * x + 0.4858 * Math.pow(x, 2.0) - 1.088 * Math.pow(x, 3.0);
        double deltaHvap1 = 0.80022 * Math.pow(x, 0.3333) + 273.23 * Math.pow(x, 0.8333) + 465.08 * Math.pow(x, 1.2083) - 638.51 * x - 145.12 * Math.pow(x, 2.0) - 74.049 * Math.pow(x, 3.0);
        double deltaHvap2 = 7.2543 * Math.pow(x, 0.3333) - 346.45 * Math.pow(x, 0.8333) - 610.48 * Math.pow(x, 1.2083) + 839.89 * x + 160.05 * Math.pow(x, 2.0) - 50.711 * Math.pow(x, 3.0);
        double carbonnumber = getMolarMass() / 0.014;
        double omega = 0.0520750 + 0.0448946 * carbonnumber - 0.000185397 * carbonnumber * carbonnumber;
        double Hvap = 1.9858775 * getTC() * (deltaHvap0 + omega * deltaHvap1 + omega * omega * deltaHvap2);
        return Math.sqrt((Hvap - Hf - 1.9858775 * phase1.getTemperature()) / (getMolarMass() / (0.8155 + 0.6273e-4 * getMolarMass() - 13.06 / getMolarMass())));
    }
}