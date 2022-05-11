/*
 * SolidComponent.java
 *
 * Created on 18. august 2001, 12:45
 */
package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * ComponentSolid class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
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
     * <p>
     * Constructor for ComponentSolid.
     * </p>
     *
     * @param component_name a {@link java.lang.String} object
     * @param moles a double
     * @param molesInPhase a double
     * @param compnumber a int
     */
    public ComponentSolid(String component_name, double moles, double molesInPhase,
            int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
    }

    /**
     * {@inheritDoc}
     *
     * Uses Claperyons equation to calculate the solid fugacity
     */
    @Override
    public double fugcoef(PhaseInterface phase1) {
        // dfugdt(phase, phase.getNumberOfComponents(), phase.getTemperature(),
        // phase.getPressure());
        // return fugcoef(phase, phase.getNumberOfComponents(), phase.getTemperature(),
        // phase.getPressure());
        if (!solidCheck) {
            // return 1.0e20;
        }
        if (componentName.equals("methane")) {
            return 1e30;
        }
        return fugcoef2(phase1);
        // return fugcoef(phase1.getTemperature(), phase1.getPressure());
    }

    /**
     * <p>
     * fugcoef2.
     * </p>
     *
     * @param phase1 a {@link neqsim.thermo.phase.PhaseInterface} object
     * @return a double
     */
    public double fugcoef2(PhaseInterface phase1) {
        refPhase.setTemperature(phase1.getTemperature());
        refPhase.setPressure(phase1.getPressure());
        refPhase.init(refPhase.getNumberOfMolesInPhase(), 1, 1, 0, 1.0);
        refPhase.getComponent(0).fugcoef(refPhase);

        double liquidPhaseFugacity =
                refPhase.getComponent(0).getFugacityCoefficient() * refPhase.getPressure();

        // Calculates delta Cp solid-liquid
        double deltaCpSL = -(getPureComponentCpSolid(getTriplePointTemperature())
                - getPureComponentCpLiquid(getTriplePointTemperature()));
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
            liqMolVol = 1.0 / refPhase.getDensity() * getMolarMass();
        }
        temp = getPureComponentSolidDensity(getTriplePointTemperature());
        if (temp > 1e-20) {
            solMolVol = 1.0 / temp * getMolarMass();
        } else {
            solMolVol = liqMolVol;
        }
        double deltaSolVol = (solMolVol - liqMolVol);

        // System.out.println("liquid density " + 1.0/liqMolVol*getMolarMass() + " solid
        // density " + 1.0/solMolVol*getMolarMass());

        SolidFug = getx() * liquidPhaseFugacity
                * Math.exp(-getHeatOfFusion() / (R * phase1.getTemperature())
                        * (1.0 - phase1.getTemperature() / getTriplePointTemperature())
                        + deltaCpSL / (R * phase1.getTemperature())
                                * (getTriplePointTemperature() - phase1.getTemperature())
                        - deltaCpSL / R
                                * Math.log(getTriplePointTemperature() / phase1.getTemperature())
                        - deltaSolVol * (1.0 - phase1.getPressure())
                                / (R * phase1.getTemperature()));

        // System.out.println("solidfug " + SolidFug);
        fugacityCoefficient = SolidFug / (phase1.getPressure() * getx());
        logFugacityCoefficient = Math.log(fugacityCoefficient);
        return fugacityCoefficient;
    }

    /**
     * <p>
     * fugcoef.
     * </p>
     *
     * @param temp a double
     * @param pres a double
     * @return a double
     */
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
                // System.out.println("pvap solid CC " + PvapSolid);
            } else {
                PvapSolid = getSolidVaporPressure(temp);
                PvapSoliddT = getSolidVaporPressuredT(temp);
                // System.out.println("pvap solid Antonie " + PvapSolid);
            }
        }

        soldens = getPureComponentSolidDensity(temp) * 1000;
        // System.out.println("solid density " + soldens);
        // if(soldens>2000)
        soldens = 1000.0;
        solvol = 1.0 / soldens * getMolarMass();
        // System.out.println("molmass " + getMolarMass());

        refPhase.setTemperature(temp);
        refPhase.setPressure(PvapSolid);
        refPhase.init(refPhase.getNumberOfMolesInPhase(), 1, 1, 1, 1.0);
        refPhase.getComponent(0).fugcoef(refPhase);

        // System.out.println("ref co2 fugcoef " +
        // refPhase.getComponent(0).getFugacityCoefficient());
        SolidFug = PvapSolid * Math.exp(solvol / (R * temp) * (pres - PvapSolid) * 1e5)
                * refPhase.getComponent(0).getFugacityCoefficient();
        // System.out.println("Pvap solid " + SolidFug);
        dfugdt = Math.log(PvapSoliddT * Math.exp(solvol / (R * temp) * (pres - PvapSolid))) / pres;
        fugacityCoefficient = SolidFug / pres;
        // } else{
        // fugacityCoefficient = 1e5;
        // dfugdt=0;
        // }
        logFugacityCoefficient = Math.log(fugacityCoefficient);
        return fugacityCoefficient;
    }

    // public double dfugdt(PhaseInterface phase, int numberOfComps, double temp, double pres){
    // if(componentName.equals("water")){
    // // double solvol = 1.0/getPureComponentSolidDensity(getMeltingPointTemperature())*molarMass;
    // double solvol = 1.0/(780*0.92)*getMolarMass();

    // dfugdt =
    // Math.log((getSolidVaporPressuredT(temp)*Math.exp(solvol/(R*temp)*(pres-getSolidVaporPressure(temp))))/pres);
    // }
    // else if(componentName.equals("MEG")){
    // double solvol = 1.0/getPureComponentSolidDensity(getMeltingPointTemperature())*molarMass;
    // dfugdt = Math.log((getSolidVaporPressuredT(temp))/pres);
    // }
    // else if(componentName.equals("S8")){
    // double solvol = 1.0/(1800.0)*getMolarMass();
    // dfugdt =
    // Math.log((getSolidVaporPressuredT(temp)*10*Math.exp(solvol/(R*temp)*(pres-getSolidVaporPressure(temp)*10)))/pres);
    // }

    // else dfugdt=0;
    // return dfugdt;
    // }
    // public double getdpdt() {
    // return dpdt;
    // }
    /**
     * <p>
     * getMolarVolumeSolid.
     * </p>
     *
     * @return a double
     */
    public double getMolarVolumeSolid() {
        return getPureComponentSolidDensity(getMeltingPointTemperature()) / molarMass;
    }

    /**
     * <p>
     * setSolidRefFluidPhase.
     * </p>
     *
     * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
     */
    public void setSolidRefFluidPhase(PhaseInterface phase) {
        try {
            if ((!isTBPfraction && !isPlusFraction)) {
                refPhase = phase.getClass().getDeclaredConstructor().newInstance();
                refPhase.setTemperature(273.0);
                refPhase.setPressure(1.0);
                try {
                    refPhase.addcomponent(componentName, 10.0, 10.0, 0);
                } catch (Exception e) {
                    logger.error("error occured", e);
                    refPhase.addcomponent("methane", 10.0, 10.0, 0);
                    refPhase.getComponent("methane").setComponentName(componentName);
                }
                refPhase.getComponent(componentName).setAttractiveTerm(
                        phase.getComponent(componentName).getAttractiveTermNumber());
                refPhase.init(refPhase.getNumberOfMolesInPhase(), 1, 0, 1, 1.0);
            }
        } catch (Exception e) {
            logger.error("error occured", e);
        }
    }

    /**
     * <p>
     * getVolumeCorrection2.
     * </p>
     *
     * @return a double
     */
    public double getVolumeCorrection2() {
        return 0.0;
    }
}
