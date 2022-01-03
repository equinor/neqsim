/*
 * ChungViscosityMethod.java
 *
 * Created on 1. august 2001, 12:44
 */

package neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * <p>PFCTViscosityMethod class.</p>
 *
 * @author esol
 * @version Method was checked on 2.8.2001 - seems to be correct - Even Solbraa
 */
public class PFCTViscosityMethod extends Viscosity {

    private static final long serialVersionUID = 1000;

    int phaseTypeNumb = 1;
    SystemInterface referenceSystem = new SystemSrkEos(273.0, 1.01325);
    double[] GVcoef = { -2.090975e5, 2.647269e5, -1.472818e5, 4.716740e4, -9.491872e3, 1.219979e3, -9.627993e1,
            4.274152, -8.141531e-2 };
    double visRefA = 1.696985927, visRefB = -0.133372346, visRefC = 1.4, visRefF = 168.0, visRefE = 1.0;
    double viscRefJ[] = { -1.035060586e1, 1.7571599671e1, -3.0193918656e3, 1.8873011594e2, 4.2903609488e-2,
            1.4529023444e2, 6.1276818706e3 };

    /**
     * Creates new ChungViscosityMethod
     */
    public PFCTViscosityMethod() {
    }

    /**
     * <p>Constructor for PFCTViscosityMethod.</p>
     *
     * @param phase a {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface} object
     */
    public PFCTViscosityMethod(neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface phase) {
        super(phase);
        referenceSystem.addComponent("methane", 10.0);
        referenceSystem.init(0);
    }

	/** {@inheritDoc} */
    @Override
	public double calcViscosity() {

        int phaseTypeNumb = 0;
        // if(phase.getPhase().getPhaseType()==0) phaseTypeNumb=1;

        double Pc0 = referenceSystem.getPhase(0).getComponent(0).getPC(),
                Tc0 = referenceSystem.getPhase(0).getComponent(0).getTC(),
                M0 = referenceSystem.getPhase(0).getComponent(0).getMolarMass() * 1e3;

        double PCmix = 0.0, TCmix = 0.0, Mmix = 0.0;
        double alfa0 = 1.0, alfaMix = 1.0;
        double par1 = 0.0, par2 = 0.0, par3 = 0.0, par4 = 0.0;
        double tempTC1 = 0.0, tempTC2 = 0.0;
        double tempPC1 = 0.0, tempPC2 = 0.0;
        double Mwtemp = 0.0, Mmtemp = 0.0;

        for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
            for (int j = 0; j < phase.getPhase().getNumberOfComponents(); j++) {
                double tempVar = phase.getPhase().getComponent(i).getx() * phase.getPhase().getComponent(j).getx()
                        * Math.pow(Math
                                .pow(phase.getPhase().getComponent(i).getTC()
                                        / phase.getPhase().getComponent(i).getPC(), 1.0 / 3.0)
                                + Math.pow(phase.getPhase().getComponent(j).getTC()
                                        / phase.getPhase().getComponent(j).getPC(), 1.0 / 3.0),
                                3.0);
                tempTC1 += tempVar * Math
                        .sqrt(phase.getPhase().getComponent(i).getTC() * phase.getPhase().getComponent(j).getTC());
                tempTC2 += tempVar;
                tempPC1 += tempVar * Math
                        .sqrt(phase.getPhase().getComponent(i).getTC() * phase.getPhase().getComponent(j).getTC());
                tempPC2 += tempVar;
            }
            Mwtemp += phase.getPhase().getComponent(i).getx()
                    * Math.pow(phase.getPhase().getComponent(i).getMolarMass(), 2.0);
            Mmtemp += phase.getPhase().getComponent(i).getx() * phase.getPhase().getComponent(i).getMolarMass();
        }

        PCmix = 8.0 * tempPC1 / (tempPC2 * tempPC2);
        TCmix = tempTC1 / tempTC2;
        Mmix = (Mmtemp + 0.291 * (Mwtemp / Mmtemp - Mmtemp)) * 1e3; // phase.getPhase().getMolarMass();

        referenceSystem.setTemperature(
                phase.getPhase().getTemperature() * referenceSystem.getPhase(0).getComponent(0).getTC() / TCmix);
        referenceSystem.setPressure(
                phase.getPhase().getPressure() * referenceSystem.getPhase(0).getComponent(0).getPC() / PCmix);
        referenceSystem.init(1);
        double molDens = 1.0 / referenceSystem.getPhase(phaseTypeNumb).getMolarVolume() * 100.0;
        double critMolDens = 10.15;// 1.0/referenceSystem.getPhase(0).getComponent(0).getCriticalVolume();
        double redDens = molDens / critMolDens;

        alfaMix = 1.0 + 7.475e-5 * Math.pow(redDens, 4.265) * Math.pow(Mmix, 0.8579);
        alfa0 = 1.0 + 8.374e-4 * Math.pow(redDens, 4.265);
        // System.out.println("func " + 7.475e-5*Math.pow(16.043, 0.8579));
        double T0 = phase.getPhase().getTemperature() * referenceSystem.getPhase(0).getComponent(0).getTC() / TCmix
                * alfa0 / alfaMix;
        double P0 = phase.getPhase().getPressure() * referenceSystem.getPhase(0).getComponent(0).getPC() / PCmix * alfa0
                / alfaMix;

        double refVisosity = getRefComponentViscosity(T0, P0);
        // System.out.println("m/mix " + Mmix/M0);
        // System.out.println("a/amix " + alfaMix/alfa0);
        double viscosity = refVisosity * Math.pow(TCmix / Tc0, -1.0 / 6.0) * Math.pow(PCmix / Pc0, 2.0 / 3.0)
                * Math.pow(Mmix / M0, 0.5) * alfaMix / alfa0;
        return viscosity;
    }

	/** {@inheritDoc} */
    @Override
	public double getPureComponentViscosity(int i) {
        return 0;
    }

    /**
     * <p>getRefComponentViscosity.</p>
     *
     * @param temp a double
     * @param pres a double
     * @return a double
     */
    public double getRefComponentViscosity(double temp, double pres) {

        referenceSystem.setTemperature(temp);
        // System.out.println("ref temp " + temp);
        referenceSystem.setPressure(pres);
        // System.out.println("ref pres " + pres);
        referenceSystem.init(1);
        double molDens = 1.0 / referenceSystem.getPhase(phaseTypeNumb).getMolarVolume() * 100.0;
        // System.out.println("mol dens " + molDens);
        double critMolDens = 10.15;// 1.0/referenceSystem.getPhase(0).getComponent(0).getCriticalVolume();
        double redMolDens = (molDens - critMolDens) / critMolDens;
        // System.out.println("gv1 " +GVcoef[0]);

        molDens = referenceSystem.getPhase(phaseTypeNumb).getDensity() * 1e-3;

        double viscRefO = GVcoef[0] * Math.pow(temp, -1.0) + GVcoef[1] * Math.pow(temp, -2.0 / 3.0)
                + GVcoef[2] * Math.pow(temp, -1.0 / 3.0) + GVcoef[3] + GVcoef[4] * Math.pow(temp, 1.0 / 3.0)
                + GVcoef[5] * Math.pow(temp, 2.0 / 3.0) + GVcoef[6] * temp + GVcoef[7] * Math.pow(temp, 4.0 / 3.0)
                + GVcoef[8] * Math.pow(temp, 5.0 / 3.0);

        // System.out.println("ref visc0 " + viscRefO);

        double viscRef1 = (visRefA + visRefB * Math.pow(visRefC - Math.log(temp / visRefF), 2.0)) * molDens;
        // System.out.println("ref visc1 " + viscRef1);

        double temp1 = Math.pow(molDens, 0.1) * (viscRefJ[1] + viscRefJ[2] / Math.pow(temp, 3.0 / 2.0));
        double temp2 = redMolDens * Math.pow(molDens, 0.5)
                * (viscRefJ[4] + viscRefJ[5] / temp + viscRefJ[6] / Math.pow(temp, 2.0));
        double temp3 = Math.exp(temp1 + temp2);
        double viscRef2 = visRefE * Math.exp(viscRefJ[0] + viscRefJ[3] / temp) * (temp3 - 1.0);
        // System.out.println("ref visc2 " + viscRef2);
        double refVisc = (viscRefO + viscRef1 + viscRef2) / 1.0e7;
        // System.out.println("ref visc " + refVisc);
        return refVisc;
    }

}
