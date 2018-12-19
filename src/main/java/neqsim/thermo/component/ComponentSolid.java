/*
 * SolidComponent.java
 *
 * Created on 18. august 2001, 12:45
 */
package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author esol
 * @version
 */
public class ComponentSolid extends ComponentSrk {

    private static final long serialVersionUID = 1000;

    double dpdt = 1.0;
    double SolidFug = 0.0, PvapSolid = 0.0, PvapSoliddT = 0.0;
    double solvol = 0.0, soldens = 0.0;
    boolean CCequation = true;
    boolean AntoineSolidequation = true;
    PhaseInterface refPhase = null;
    double pureCompFug = 0.0;

    /**
     * Creates new SolidComponent
     */
    public ComponentSolid() {
    }

    public ComponentSolid(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
    }

    /**
     * Uses Claperyons equation to calculate the solid fugacity
     */
    public double fugcoef(PhaseInterface phase1) {
        // dfugdt(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure());
//        return fugcoef(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure());
        if (!solidCheck) {
//            return 1.0e20;
        }
        if (componentName.equals("methane")) {
            return 1e30;
        }
        return fugcoef2(phase1);
         // return fugcoef(phase1.getTemperature(), phase1.getPressure());
    }

    public double fugcoef2(PhaseInterface phase1) {
        refPhase.setTemperature(phase1.getTemperature());
        refPhase.setPressure(phase1.getPressure());
        refPhase.init(refPhase.getNumberOfMolesInPhase(), 1, 1, 0, 1.0);
        refPhase.getComponent(0).fugcoef(refPhase);

        double liquidPhaseFugacity = refPhase.getComponent(0).getFugasityCoefficient() * refPhase.getPressure();
        
        // Calculates delta Cp solid-liquid
        double deltaCpSL = -(getPureComponentCpSolid(getTriplePointTemperature()) - getPureComponentCpLiquid(getTriplePointTemperature()));
        if (componentName.equals("water")) {
            deltaCpSL = 37.12;
        }
        
        // System.out.println("deltaCp Sol-liq " + deltaCpSL);
        // Calculates solid-liquid volume change
        double liqMolVol = 0.0, solMolVol = 0.0;
        double temp = getPureComponentLiquidDensity(getTriplePointTemperature());
        if (temp > 1e-20) {
            liqMolVol = 1.0 / temp * getMolarMass();
        } else {
            liqMolVol = 1.0/refPhase.getDensity()*getMolarMass();
        }
        temp = getPureComponentSolidDensity(getTriplePointTemperature());
        if (temp > 1e-20) {
            solMolVol = 1.0 / temp * getMolarMass();
        } else {
            solMolVol = liqMolVol;
        }
        double deltaSolVol = (solMolVol - liqMolVol);

        // System.out.println("liquid density " + 1.0/liqMolVol*getMolarMass() + " solid density " + 1.0/solMolVol*getMolarMass());

        SolidFug = getx() * liquidPhaseFugacity * Math.exp(-getHeatOfFusion() / (R * phase1.getTemperature()) * (1.0 - phase1.getTemperature() / getTriplePointTemperature())
                + deltaCpSL / (R * phase1.getTemperature()) * (getTriplePointTemperature() - phase1.getTemperature()) - deltaCpSL / R * Math.log(getTriplePointTemperature() / phase1.getTemperature())
                - deltaSolVol * (1.0 - phase1.getPressure()) / (R * phase1.getTemperature()));


        //System.out.println("solidfug " + SolidFug);
        fugasityCoeffisient = SolidFug / (phase1.getPressure() * getx());
        logFugasityCoeffisient = Math.log(fugasityCoeffisient);
        return fugasityCoeffisient;
    }

    public double fugcoef(double temp, double pres) {

        if (Math.abs(Hsub) < 0.000001) {
            CCequation = false;
        }
        if (Math.abs(AntoineASolid) < 0.000001) {
            AntoineSolidequation = false;
        }
        if (CCequation || AntoineSolidequation) {
            if (temp > getTriplePointTemperature() + 0.1) {
                temp = getTriplePointTemperature();
            }
            if (CCequation) {
                PvapSolid = getCCsolidVaporPressure(temp);
                PvapSoliddT = getCCsolidVaporPressuredT(temp);
                //System.out.println("pvap solid CC " + PvapSolid);
            } else {
                PvapSolid = getSolidVaporPressure(temp);
                PvapSoliddT = getSolidVaporPressuredT(temp);
                //   System.out.println("pvap solid Antonie " + PvapSolid);
            }
        }

        soldens = getPureComponentSolidDensity(temp) * 1000;
        //System.out.println("solid density " + soldens);
//            if(soldens>2000)
        soldens = 1000.0;
        solvol = 1.0 / soldens * getMolarMass();
        //System.out.println("molmass " + getMolarMass());

        refPhase.setTemperature(temp);
        refPhase.setPressure(PvapSolid);
        refPhase.init(refPhase.getNumberOfMolesInPhase(), 1, 1, 1, 1.0);
        refPhase.getComponent(0).fugcoef(refPhase);

        //System.out.println("ref co2 fugcoef  " + refPhase.getComponent(0).getFugasityCoefficient());
        SolidFug = PvapSolid * Math.exp(solvol / (R * temp) * (pres - PvapSolid) * 1e5) * refPhase.getComponent(0).getFugasityCoefficient();
        //System.out.println("Pvap solid " + SolidFug);
        dfugdt = Math.log(PvapSoliddT * Math.exp(solvol / (R * temp) * (pres - PvapSolid))) / pres;
        fugasityCoeffisient = SolidFug / pres;
//        } else{
//            fugasityCoeffisient = 1e5;
//            dfugdt=0;
//        }
        logFugasityCoeffisient = Math.log(fugasityCoeffisient);
        return fugasityCoeffisient;
    }

//    public double dfugdt(PhaseInterface phase, int numberOfComps, double temp, double pres){
//        if(componentName.equals("water")){
//            // double solvol = 1.0/getPureComponentSolidDensity(getMeltingPointTemperature())*molarMass;
//            double solvol = 1.0/(780*0.92)*getMolarMass();
//
//            dfugdt = Math.log((getSolidVaporPressuredT(temp)*Math.exp(solvol/(R*temp)*(pres-getSolidVaporPressure(temp))))/pres);
//        }
//        else if(componentName.equals("MEG")){
//            double solvol = 1.0/getPureComponentSolidDensity(getMeltingPointTemperature())*molarMass;
//            dfugdt = Math.log((getSolidVaporPressuredT(temp))/pres);
//        }
//        else if(componentName.equals("S8")){
//            double solvol = 1.0/(1800.0)*getMolarMass();
//            dfugdt = Math.log((getSolidVaporPressuredT(temp)*10*Math.exp(solvol/(R*temp)*(pres-getSolidVaporPressure(temp)*10)))/pres);
//        }
//
//        else dfugdt=0;
//        return dfugdt;
//    }
//    public double getdpdt() {
//        return dpdt;
//    }
    public double getMolarVolumeSolid() {
        return getPureComponentSolidDensity(getMeltingPointTemperature()) / molarMass;
    }

    public void setSolidRefFluidPhase(PhaseInterface phase) {
        try {
            refPhase = phase.getClass().newInstance();
            refPhase.setTemperature(273.0);
            refPhase.setPressure(1.0);
            refPhase.addcomponent(componentName, 10.0, 10.0, 0);
            refPhase.getComponent(componentName).setAtractiveTerm(phase.getComponent(componentName).getAtractiveTermNumber());
            refPhase.init(refPhase.getNumberOfMolesInPhase(), 1, 0, 1, 1.0);
        } catch (Exception e) {
            logger.error("error occured", e);
        }
    }
}
