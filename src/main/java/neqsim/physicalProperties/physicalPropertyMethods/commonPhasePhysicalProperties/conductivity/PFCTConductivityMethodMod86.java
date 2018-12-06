/*
 * ChungViscosityMethod.java
 *
 * Created on 1. august 2001, 12:44
 */
package neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.conductivity;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 *
 * @author esol
 * @version Method was checked on 2.8.2001 - seems to be correct - Even Solbraa
 */
public class PFCTConductivityMethodMod86 extends Conductivity {

    private static final long serialVersionUID = 1000;

    public static SystemInterface referenceSystem = new SystemSrkEos(273.0, 1.01325);
    double[] GVcoef = {-2.147621e5, 2.190461e5, -8.618097e4, 1.496099e4, -4.730660e2, -2.331178e2, 3.778439e1, -2.320481, 5.311764e-2};
    double visRefA = -0.25276292, visRefB = 0.33432859, visRefC = 1.12, visRefF = 168.0, visRefE = 1.0, visRefG = 0.0;
    double viscRefJ[] = {-7.04036339907, 12.319512908, -8.8525979933e2, 72.835897919, 0.74421462902, -2.9706914540, 2.2209758501e3};
    double viscRefK[] = {-8.55109, 12.5539, -1020.85, 238.394, 1.31563, -72.5759, 1411.6};
    int phaseTypeNumb = 1;

    /**
     * Creates new ChungViscosityMethod
     */
    public PFCTConductivityMethodMod86() {
    }

    public PFCTConductivityMethodMod86(neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface phase) {
        super(phase);
        if (referenceSystem.getNumberOfMoles() < 1e-10) {
            //System.out.println("here..");
            referenceSystem.addComponent("methane", 10.0);
            referenceSystem.init(0);
        }
    }

    public double calcConductivity() {

        int phaseTypeNumb = 0;
        //if(phase.getPhase().getPhaseType()==0) phaseTypeNumb=1;

        double Pc0 = referenceSystem.getPhase(0).getComponent(0).getPC(), Tc0 = referenceSystem.getPhase(0).getComponent(0).getTC(), M0 = referenceSystem.getPhase(0).getComponent(0).getMolarMass() * 1e3;

        double PCmix = 0.0, TCmix = 0.0, Mmix = 0.0;
        double alfa0 = 1.0, alfaMix = 1.0;
        double par1 = 0.0, par2 = 0.0, par3 = 0.0, par4 = 0.0;
        double tempTC1 = 0.0, tempTC2 = 0.0;
        double tempPC1 = 0.0, tempPC2 = 0.0;
        double Mwtemp = 0.0, Mmtemp = 0.0;

        for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
            for (int j = 0; j < phase.getPhase().getNumberOfComponents(); j++) {
                double tempVar = phase.getPhase().getComponent(i).getx() * phase.getPhase().getComponent(j).getx() * Math.pow(Math.pow(phase.getPhase().getComponent(i).getTC() / phase.getPhase().getComponent(i).getPC(), 1.0 / 3.0) + Math.pow(phase.getPhase().getComponent(j).getTC() / phase.getPhase().getComponent(j).getPC(), 1.0 / 3.0), 3.0);
                tempTC1 += tempVar * Math.sqrt(phase.getPhase().getComponent(i).getTC() * phase.getPhase().getComponent(j).getTC());
                tempTC2 += tempVar;
                tempPC1 += tempVar * Math.sqrt(phase.getPhase().getComponent(i).getTC() * phase.getPhase().getComponent(j).getTC());
                tempPC2 += tempVar;
            }
            Mwtemp += phase.getPhase().getComponent(i).getx() * Math.pow(phase.getPhase().getComponent(i).getMolarMass(), 2.0);
            Mmtemp += phase.getPhase().getComponent(i).getx() * phase.getPhase().getComponent(i).getMolarMass();
        }

        PCmix = 8.0 * tempPC1 / (tempPC2 * tempPC2);
        TCmix = tempTC1 / tempTC2;
        Mmix = (Mmtemp + 1.304e-4 * (Math.pow(Mwtemp / Mmtemp, 2.303) - Math.pow(Mmtemp, 2.303))) * 1e3; //phase.getPhase().getMolarMass();

        if(Double.isNaN(PCmix) || Double.isNaN(TCmix)){
            PCmix = 1.0;
            TCmix = 273.15;
        }
        referenceSystem.setTemperature(phase.getPhase().getTemperature() * referenceSystem.getPhase(0).getComponent(0).getTC() / TCmix);
        referenceSystem.setPressure(phase.getPhase().getPressure() * referenceSystem.getPhase(0).getComponent(0).getPC() / PCmix);
        try {
            referenceSystem.init(1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        //  double molDens = 1.0 / referenceSystem.getPhase(phaseTypeNumb).getMolarVolume() * 1.0/10.0;
        double molDens = 1.0 / referenceSystem.getLowestGibbsEnergyPhase().getMolarVolume() * referenceSystem.getLowestGibbsEnergyPhase().getMolarMass() * 1e2;
        double critMolDens = 0.16284;//1.0/referenceSystem.getPhase(0).getComponent(0).getCriticalVolume();
        double redDens = molDens / critMolDens;

        alfaMix = 1.0 + 6.004e-4 * Math.pow(redDens, 2.043) * Math.pow(Mmix, 1.086);
        alfa0 = 1.0 + 8.374e-4 * Math.pow(redDens, 4.265);
        //System.out.println("func " + 7.475e-5*Math.pow(16.043, 0.8579));
        double T0 = phase.getPhase().getTemperature() * referenceSystem.getPhase(0).getComponent(0).getTC() / TCmix * alfa0 / alfaMix;
        double P0 = phase.getPhase().getPressure() * referenceSystem.getPhase(0).getComponent(0).getPC() / PCmix * alfa0 / alfaMix;

         if(Double.isNaN(T0) || Double.isNaN(P0)){
            P0 = 1.0;
            T0 = 273.15;
        }
         
        double nstarRef = getRefComponentConductivity(T0, 1.01325);
        double CpID = referenceSystem.getLowestGibbsEnergyPhase().getComponent(0).getCp0(referenceSystem.getTemperature()) * referenceSystem.getLowestGibbsEnergyPhase().getMolarMass();
        double rhorel = redDens;
        double Ffunc = 1.0 + 0.053432 * rhorel - 0.030182 * rhorel * rhorel - 0.029725 * rhorel * rhorel * rhorel;
        double condIntRef = 1.18653 * nstarRef * (CpID - 2.5 * neqsim.thermo.ThermodynamicConstantsInterface.R) * Ffunc;

        double nstarMix = getRefComponentConductivity(phase.getPhase().getTemperature(), 1.01325);
        double CpIDMix = phase.getPhase().getCp0() * phase.getPhase().getMolarMass();
        double critMolDensMix = 0.9;
        double rhorelMix = 1.0 / phase.getPhase().getMolarVolume() * phase.getPhase().getMolarMass() * 100.0 / critMolDensMix;
        double FfuncMix = 1.0 + 0.053432 * rhorelMix - 0.030182 * rhorelMix * rhorelMix - 0.029725 * rhorelMix * rhorelMix * rhorelMix;
        double condIntMix = 1.18653 * nstarMix * (CpIDMix - 2.5 * neqsim.thermo.ThermodynamicConstantsInterface.R) * FfuncMix;

        double refConductivity = getRefComponentConductivity(T0, P0);
        //        System.out.println("m/mix " + Mmix/M0);
        //        System.out.println("a/amix " + alfaMix/alfa0);
        double conduct = -condIntRef + condIntMix;
        // System.out.println("new condt " + conduct);
        // System.out.println("old condt " + refConductivity);
        // System.out.println("condt intMix " + condIntMix);
        double conductivity = refConductivity * Math.pow(TCmix / Tc0, -1.0 / 6.0) * Math.pow(PCmix / Pc0, 2.0 / 3.0) * Math.pow(Mmix / M0, -0.5) * alfaMix / alfa0;
        // System.out.println("condictivity  " + conductivity);
        return conductivity;
    }

    public double getRefComponentConductivity(double temp, double pres) {

        referenceSystem.setTemperature(temp);
        //System.out.println("ref temp " + temp);
        referenceSystem.setPressure(pres);
        //System.out.println("ref pres " + pres);
        
        referenceSystem.init(1);

        double molDens = 1.0 / referenceSystem.getPhase(phaseTypeNumb).getMolarVolume() * 100.0;
        //System.out.println("mol dens " + molDens);
        double critMolDens = 10.15;//1.0/referenceSystem.getPhase(0).getComponent(0).getCriticalVolume();
        double redMolDens = (molDens - critMolDens) / critMolDens;
        //System.out.println("gv1 " +GVcoef[0]);

        molDens = referenceSystem.getLowestGibbsEnergyPhase().getDensity() * 1e-3;

        double viscRefO = GVcoef[0] * Math.pow(temp, -1.0) + GVcoef[1] * Math.pow(temp, -2.0 / 3.0) + GVcoef[2] * Math.pow(temp, -1.0 / 3.0)
                + GVcoef[3] + GVcoef[4] * Math.pow(temp, 1.0 / 3.0) + GVcoef[5] * Math.pow(temp, 2.0 / 3.0) + GVcoef[6] * temp
                + GVcoef[7] * Math.pow(temp, 4.0 / 3.0) + GVcoef[8] * Math.pow(temp, 5.0 / 3.0);

        //System.out.println("ref visc0 " + viscRefO);
        double viscRef1 = (visRefA + visRefB * Math.pow(visRefC - Math.log(temp / visRefF), 2.0)) * molDens;
        //System.out.println("ref visc1 " + viscRef1);

        double temp1 = Math.pow(molDens, 0.1) * (viscRefJ[1] + viscRefJ[2] / Math.pow(temp, 3.0 / 2.0));
        double temp2 = redMolDens * Math.pow(molDens, 0.5) * (viscRefJ[4] + viscRefJ[5] / temp + viscRefJ[6] / Math.pow(temp, 2.0));
        double temp3 = Math.exp(temp1 + temp2);

        double dTfreeze = temp - 90.69;
        double HTAN = (Math.exp(dTfreeze) - Math.exp(-dTfreeze)) / (Math.exp(dTfreeze) + Math.exp(-dTfreeze));
        visRefE = (HTAN + 1.0) / 2.0;

        double viscRef2 = visRefE * Math.exp(viscRefJ[0] + viscRefJ[3] / temp) * (temp3 - 1.0);

        double temp4 = Math.pow(molDens, 0.1) * (viscRefK[1] + viscRefK[2] / Math.pow(temp, 3.0 / 2.0));
        double temp5 = redMolDens * Math.pow(molDens, 0.5) * (viscRefK[4] + viscRefK[5] / temp + viscRefK[6] / Math.pow(temp, 2.0));
        double temp6 = Math.exp(temp4 + temp5);
        visRefG = (1.0 - HTAN) / 2.0;
        double viscRef3 = visRefG * Math.exp(viscRefK[0] + viscRefK[3] / temp) * (temp6 - 1.0);

        //       System.out.println("ref visc2 " + viscRef2);
        //        System.out.println("ref visc3 " + viscRef3);
        double refCond = (viscRefO + viscRef1 + viscRef2 + viscRef3) * 1e-3;
        //System.out.println("ref cond " + refCond);
        return refCond;
    }

    public double getCondInt0() {
        double nstar = 1.0;
        double CpID = referenceSystem.getPhase(0).getComponent(0).getCp0(referenceSystem.getTemperature());
        double rhorel = getRhoRelRef();//phase.get;
        double Ffunc = 1.0 + 0.053432 * rhorel - 0.030182 * rhorel * rhorel - 0.029725 * rhorel * rhorel * rhorel;
        double var1 = 1.18653 * nstar * (CpID - 2.5 * neqsim.thermo.ThermodynamicConstantsInterface.R) * Ffunc;
        return var1;

    }

    public double getCondIntmix() {
        double nstar = 1.0;
        double CpID = phase.getPhase().getCp0();
        double rhorel = getRhoRelMix();
        double Ffunc = 1.0 + 0.053432 * rhorel - 0.030182 * rhorel * rhorel - 0.029725 * rhorel * rhorel * rhorel;
        double var1 = 1.18653 * nstar * (CpID - 2.5 * neqsim.thermo.ThermodynamicConstantsInterface.R) * Ffunc;
        return var1;

    }

    public double getRhoRelRef() {

        double molDens = 1.0 / referenceSystem.getPhase(phaseTypeNumb).getMolarVolume() * referenceSystem.getPhase(phaseTypeNumb).getMolarMass() * 100.0;
        //System.out.println("mol dens " + molDens);
        double critMolDens = 0.16284;//1.0/referenceSystem.getPhase(0).getComponent(0).getCriticalVolume();
        return molDens / critMolDens;
    }

    public double getRhoRelMix() {

        double molDens = 1.0 / phase.getPhase().getMolarVolume() * phase.getPhase().getMolarMass() * 100.0;
        //System.out.println("mol dens " + molDens);
        double critMolDens = 0.16284;//1.0/referenceSystem.getPhase(0).getComponent(0).getCriticalVolume();
        return molDens / critMolDens;
    }
}
