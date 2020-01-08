/*
 * PhaseSrkEos.java
 *
 * Created on 3. juni 2000, 14:38
 */
package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentCPAInterface;
import neqsim.thermo.component.ComponentElectrolyteCPA;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.mixingRule.CPAMixing;
import neqsim.thermo.mixingRule.CPAMixingInterface;
import org.apache.logging.log4j.*;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class PhaseElectrolyteCPAOld extends PhaseModifiedFurstElectrolyteEos implements PhaseCPAInterface {

    private static final long serialVersionUID = 1000;

    public CPAMixing cpaSelect = new CPAMixing();
    
    int totalNumberOfAccociationSites = 0;
    public CPAMixingInterface cpamix;
    double hcpatot = 1.0, hcpatotdT = 0.0, hcpatotdTdT = 0.0, gcpav = 0.0, lngcpa = 0.0, lngcpav = 0.0, gcpavv = 1.0, gcpavvv = 0.0, gcpa = 0.0;
    int cpaon = 1;
    int[][][] selfAccociationScheme = null;
    int[][][][] crossAccociationScheme = null;
    double dFdVdXdXdVtotal = 0.0;
    double dFCPAdXdXdTtotal = 0.0, dFCPAdTdT = 0.0;
    
    static Logger logger = LogManager.getLogger(PhaseElectrolyteCPAOld.class);

    /** Creates new PhaseSrkEos */
    public PhaseElectrolyteCPAOld() {
        super();
    }

    public Object clone() {
        PhaseElectrolyteCPAOld clonedPhase = null;
        try {
            clonedPhase = (PhaseElectrolyteCPAOld) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }
        // clonedPhase.cpaSelect = (CPAMixing) cpaSelect.clone();

        return clonedPhase;
    }

    public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase, double beta) { // type = 0 start init type =1 gi nye betingelser
        if (type == 0) {
            selfAccociationScheme = new int[numberOfComponents][0][0];
            crossAccociationScheme = new int[numberOfComponents][numberOfComponents][0][0];
            for (int i = 0; i < numberOfComponents; i++) {
                selfAccociationScheme[i] = cpaSelect.setAssociationScheme(i, this);
                for (int j = 0; j < numberOfComponents; j++) {
                    crossAccociationScheme[i][j] = cpaSelect.setCrossAssociationScheme(i, j, this);
                }
            }
        }
        do {
            super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
        } while (!solveX());

        //System.out.println("test1 " + dFCPAdT());
        if (type > 1) {
//            calcXsitedT();
            // System.out.println("test2 " + dFCPAdT());
            hcpatotdT = calc_hCPAdT();
            hcpatotdTdT = calc_hCPAdTdT();
        }


    //System.out.println("tot iter " + totiter);
    }

    public void setMixingRule(int type) {
        super.setMixingRule(type);
        cpamix = cpaSelect.getMixingRule(1, this);
    }

    public void addcomponent(String componentName, double moles, double molesInPhase, int compNumber) {
        super.addcomponent(componentName, moles, molesInPhase, compNumber);
        componentArray[compNumber] = new ComponentElectrolyteCPA(componentName, moles, molesInPhase, compNumber);
    }

    public double getF() {
        return super.getF() + cpaon * FCPA();
    }

    public double dFdT() {
        return super.dFdT() + cpaon * dFCPAdT();
    }

    public double dFdTdV() {
        return super.dFdTdV();
    }

    public double dFdV() {
        //double dv = super.dFdV();
        double dv2 = dFCPAdV();
        //System.out.println("dv " + dv + "  dvcpa " + dv2);
        return super.dFdV() + cpaon * dv2;
    }

    public double dFdVdV() {
        return super.dFdVdV() + cpaon * dFCPAdVdV();
    }

    public double dFdVdVdV() {
        return super.dFdVdVdV() + cpaon * dFCPAdVdVdV();
    }

    public double dFdTdT() {
        return super.dFdTdT() + cpaon * dFCPAdTdT();
    }

    public double FCPA() {
        double tot = 0.0;
        double ans = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            tot = 0.0;
            for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
                double xai = ((ComponentElectrolyteCPA) getComponent(i)).getXsite()[j];
                tot += (Math.log(xai) - 1.0 / 2.0 * xai + 1.0 / 2.0);
            }
            ans += getComponent(i).getNumberOfMolesInPhase() * tot;
        }
        return ans;
    }

    public double dFCPAdV() {
        return 1.0 / (2.0 * getTotalVolume()) * (1.0 - getTotalVolume() * getGcpav()) * hcpatot;
    }

    public double dFCPAdVdV() {
        return -1.0 / getTotalVolume() * dFCPAdV() + hcpatot / (2.0 * getTotalVolume()) * (-getGcpav() - getTotalVolume() * gcpavv) + getdFdVdXdXdVtotal();
    }

    public double dFCPAdVdVdV() {
        return -1.0 / getTotalVolume() * dFCPAdVdV() + 1.0 / Math.pow(getTotalVolume(), 2.0) * dFCPAdV() - hcpatot / (2.0 * Math.pow(getTotalVolume(), 2.0)) * (-getGcpav() - getTotalVolume() * gcpavv) + hcpatot / (2.0 * getTotalVolume()) * (-gcpavv - getTotalVolume() * gcpavvv - gcpavv);
    }

    public double dFCPAdT() {
        //  System.out.println("dFCPAdXdXdTtotal " + dFCPAdXdXdTtotal);
        return dFCPAdXdXdTtotal;
    //-1.0 / 2.0 * hcpatotdT;
    }

    public double dFCPAdTdT() {
        return dFCPAdTdT;//-1.0 / 2.0 * hcpatotdTdT;
    }

    public double calc_hCPA() {
        double htot = 0.0;
        double tot = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            htot = 0.0;
            for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
                htot += (1.0 - ((ComponentElectrolyteCPA) getComponent(i)).getXsite()[j]);
            }
            tot += getComponent(i).getNumberOfMolesInPhase() * htot;
        }
        //System.out.println("tot " +tot );
        return tot;
    }

    public double calc_hCPAdT() {
        double htot = 0.0;
        double tot = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            for (int k = 0; k < numberOfComponents; k++) {

                htot = 0.0;
                for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
                    for (int l = 0; l < getComponent(k).getNumberOfAssociationSites(); l++) {
                        htot += ((ComponentElectrolyteCPA) getComponent(i)).getXsite()[j] * ((ComponentElectrolyteCPA) getComponent(k)).getXsite()[l] * cpamix.calcDeltadT(j, l, i, k, this, temperature, pressure, numberOfComponents);
                    }
                }

                tot += getComponent(i).getNumberOfMolesInPhase() * getComponent(k).getNumberOfMolesInPhase() * htot;
            }
        }
        //System.out.println("tot " +tot );
        return tot / getTotalVolume();
    }

    public double calc_hCPAdTdT() {
        double htot = 0.0;
        double tot = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            for (int k = 0; k < numberOfComponents; k++) {

                htot = 0.0;
                for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
                    for (int l = 0; l < getComponent(k).getNumberOfAssociationSites(); l++) {
                        htot += ((ComponentElectrolyteCPA) getComponent(i)).getXsite()[j] * ((ComponentElectrolyteCPA) getComponent(k)).getXsite()[l] * cpamix.calcDeltadTdT(j, l, i, k, this, temperature, pressure, numberOfComponents);
                    }
                }

                tot += getComponent(i).getNumberOfMolesInPhase() * getComponent(k).getNumberOfMolesInPhase() * htot;
            }
        }
        //System.out.println("tot " +tot );
        return tot / getTotalVolume();
    }

    public double calc_g() {
        double x = 1.9 / 4.0 * getB() / getTotalVolume();
        double g = 1.0 / (1.0 - x);
        //System.out.println("ratio " + getMolarVolume()/getb());
        return g;
    }
    
    public double calc_lngni(int comp) {
        double nbet = getb() / 4.0 / getMolarVolume();
        double dlngdb = 1.9 / (1.0 - 1.9 * nbet);
        double nbeti = nbet / getb() * ((ComponentEosInterface) getComponent(comp)).getBi();
        return dlngdb * nbeti;
    }

    public double calc_lngV() {
        double x = 1.9 / 4.0 * getB() / getTotalVolume();
        double gv = (x / getTotalVolume()) / (1.0 - x);
        return -gv;
    }

    public double calc_lngVV() {
        double x = 1.9 / 4.0 * getB() / getTotalVolume();
        double xV = -1.9 / 4.0 * getB() / Math.pow(getTotalVolume(), 2.0);
        double u = 1.0 - x;

        double val = -x / (Math.pow(getTotalVolume(), 2.0) * u) + xV / (getTotalVolume() * u) - x / (getTotalVolume() * u * u) * (-1.0) * xV;
        return -val;
//
//        double gvv =0.225625/Math.pow(1.0-0.475*getB()/getTotalVolume(),2.0)*Math.pow(getB(),2.0)/(Math.pow(getTotalVolume(),4.0))+0.95/(1.0-0.475*getB()/getTotalVolume())*getB()/(Math.pow(getTotalVolume(),3.0));
//         System.out.println("val2 " + gvv);
//        return gvv;
    }

    public double calc_lngVVV() {
        double gvv = -0.21434375 / Math.pow(1.0 - 0.475 * getB() / getTotalVolume(), 3.0) * Math.pow(getB(), 3.0) / (Math.pow(getTotalVolume(), 6.0)) - 0.135375E1 / Math.pow(1.0 - 0.475 * getB() / getTotalVolume(), 2.0) * Math.pow(getB(), 2.0) / (Math.pow(getTotalVolume(), 5.0)) - 0.285E1 / (1.0 - 0.475 * getB() / getTotalVolume()) * getB() / (Math.pow(getTotalVolume(), 4.0));
        return gvv;
    }

    public void setXsiteOld() {
        for (int i = 0; i < numberOfComponents; i++) {
            for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
                ((ComponentCPAInterface) getComponent(i)).setXsiteOld(j, ((ComponentCPAInterface) getComponent(i)).getXsite()[j]);
            }
        }
    }

    public void setXsitedV(double dV) {
        dFdVdXdXdVtotal = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
                double XdV = (((ComponentCPAInterface) getComponent(i)).getXsite()[j] - ((ComponentCPAInterface) getComponent(i)).getXsiteOld()[j]) / dV;
                ((ComponentCPAInterface) getComponent(i)).setXsitedV(j, XdV);
                dFdVdXdXdVtotal += XdV * ((ComponentCPAInterface) getComponent(i)).dFCPAdVdXi(j, this);
            //System.out.println("xidv " + XdV);
            }
        }
    }

    public void calcXsitedT() {
        double dt = 0.01, XdT = 0.0;
        setXsiteOld();
        setTemperature(temperature + dt);
        solveX();
        dFCPAdXdXdTtotal = 0.0;
        dFCPAdTdT = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
                XdT = (((ComponentCPAInterface) getComponent(i)).getXsite()[j] - ((ComponentCPAInterface) getComponent(i)).getXsiteOld()[j]) / dt;
                ((ComponentCPAInterface) getComponent(i)).setXsitedT(j, XdT);
                dFCPAdXdXdTtotal += XdT * ((ComponentCPAInterface) getComponent(i)).dFCPAdXi(j, this);
            }
        }
        for (int i = 0; i < numberOfComponents; i++) {
            for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
                for (int k = 0; k < numberOfComponents; k++) {
                    for (int j2 = 0; j2 < getComponent(k).getNumberOfAssociationSites(); j2++) {
                        dFCPAdTdT += ((ComponentCPAInterface) getComponent(i)).dFCPAdXidXj(j, j2, k, this) * ((ComponentCPAInterface) getComponent(i)).getXsitedT()[j] * ((ComponentCPAInterface) getComponent(k)).getXsitedT()[j2];
                    }
                }
            }
        }
        setTemperature(temperature - dt);
        solveX();

    }

    public double getdFdVdXdXdVtotal() {
        return dFdVdXdXdVtotal;
    }

    public boolean solveX() {
        double err = .0;
        int iter = 0;


        do {
            iter++;
            err = 0.0;
            for (int i = 0; i < numberOfComponents; i++) {
                for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
                    double old = ((ComponentElectrolyteCPA) getComponent(i)).getXsite()[j];
                    double neeval = getCpamix().calcXi(selfAccociationScheme, crossAccociationScheme, j, i, this, temperature, pressure, numberOfComponents);
                    ((ComponentCPAInterface) getComponent(i)).setXsite(j, neeval);
                    err += Math.abs((old - neeval) / neeval);
                }
            }
        //System.out.println("err " + err);
        } while (Math.abs(err) > 1e-10 && iter < 100);
        //System.out.println("iter " +iter);
        return Math.abs(err) < 1e-10;
    }

    /** Getter for property hcpatot.
     * @return Value of property hcpatot.
     */
    public double getHcpatot() {
        return hcpatot;
    }

    /** Setter for property hcpatot.
     * @param hcpatot New value of property hcpatot.
     */
    public void setHcpatot(double hcpatot) {
        this.hcpatot = hcpatot;
    }

    public double getGcpa() {
        return gcpa;
    }

    public double molarVolume3(double pressure, double temperature, double A, double B, int phasetype) throws neqsim.util.exception.IsNaNException, neqsim.util.exception.TooManyIterationsException {
        double BonV = phasetype == 0 ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature()) : pressure * getB() / (numberOfMolesInPhase * temperature * R);

        if (BonV < 0) {
            BonV = 1.0e-8;
        }
        if (BonV > 1.0) {
            BonV = 1.0 - 1.0e-8;
        }
        double BonVold = BonV;
        double Btemp = 0, Dtemp = 0, h = 0, dh = 0, gvvv = 0, fvvv = 0, dhh = 0;
        double d1 = 0, d2 = 0;
        Btemp = getB();
        Dtemp = getA();
        if (Btemp < 0) {
            logger.info("b negative in volume calc");
        }
        setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
        int iterations = 0;

        do {
            this.volInit();
            gcpa = calc_g();
            lngcpa = Math.log(gcpa);
            gcpav = calc_lngV();
            gcpavv = calc_lngVV();
            gcpavvv = calc_lngVVV();
            solveX();
            hcpatot = calc_hCPA();

            iterations++;
            BonVold = BonV;
            h = BonV - Btemp / numberOfMolesInPhase * dFdV() - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
            dh = 1.0 + Btemp / Math.pow(BonV, 2.0) * (Btemp / numberOfMolesInPhase * dFdVdV());
            dhh = -2.0 * Btemp / Math.pow(BonV, 3.0) * (Btemp / numberOfMolesInPhase * dFdVdV()) - Math.pow(Btemp, 2.0) / Math.pow(BonV, 4.0) * (Btemp / numberOfMolesInPhase * dFdVdVdV());

            d1 = -h / dh;
            d2 = -dh / dhh;

            if (Math.abs(d1 / d2) <= 1.0) {
                BonV += d1 * (1.0 + 0.5 * d1 / d2);
            } else if (d1 / d2 < -1) {
                BonV += d1 * (1.0 + 0.5 * -1.0);
            } else if (d1 / d2 > 1) {
                BonV += d2;
                double hnew = h + d2 * -h / d1;
                if (Math.abs(hnew) > Math.abs(h)) {
                    BonV = phasetype == 1 ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature()) : pressure * getB() / (numberOfMolesInPhase * temperature * R);
                }
            }

            if (BonV > 1) {
                BonV = 1.0 - 1.0e-8;
                BonVold = 10;
            }
            if (BonV < 0) {
                BonV = 1.0e-8;
                BonVold = 10;
            }

            setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
            Z = pressure * getMolarVolume() / (R * temperature);
        //System.out.println("Z" + Z);
        } while (Math.abs((BonV - BonVold) / BonV) > 1.0e-10 && iterations < 101);
        //System.out.println("Z" + Z + " iterations " + iterations);
        //System.out.println("pressure " + Z*R*temperature/molarVolume);
        //if(iterations>=100) throw new util.exception.TooManyIterationsException();
        //System.out.println("error in volume " + (-pressure+R*temperature/molarVolume-R*temperature*dFdV()) + " firstterm " + (R*temperature/molarVolume) + " second " + R*temperature*dFdV());
        if (Double.isNaN(getMolarVolume())) {
            throw new neqsim.util.exception.IsNaNException();
        //System.out.println("BonV: " + BonV + " "+"  itert: " +   iterations +" " +h + " " +dh + " B " + Btemp + "  D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv" + fVV());
        }
        return getMolarVolume();
    }

    public double molarVolume(double pressure, double temperature, double A, double B, int phasetype) throws neqsim.util.exception.IsNaNException, neqsim.util.exception.TooManyIterationsException {

        double BonV = phasetype == 0 ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature()) : pressure * getB() / (numberOfMolesInPhase * temperature * R);

        if (BonV < 0) {
            BonV = 1.0e-8;
        }
        if (BonV > 1.0) {
            BonV = 1.0 - 1.0e-8;
        }
        double BonVold = BonV;
        double Btemp = 0, Dtemp = 0, h = 0, dh = 0, gvvv = 0, fvvv = 0, dhh = 0;
        double d1 = 0, d2 = 0;
        Btemp = getB();
        Dtemp = getA();
        if (Btemp < 0) {
            logger.info("b negative in volume calc");
        }
        setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
        int iterations = 0;
        double oldVolume = getVolume();
        do {
            this.volInit();
            gcpa = calc_g();
            lngcpa = Math.log(gcpa);
            setGcpav(calc_lngV());
            gcpavv = calc_lngVV();
            gcpavvv = calc_lngVVV();

            solveX();
            hcpatot = calc_hCPA();

            double dV = getVolume() - oldVolume;
            if (iterations > 0) {
                setXsitedV(dV);
            }
            oldVolume = getVolume();
            setXsiteOld();

            iterations++;
            BonVold = BonV;
            h = BonV - Btemp / numberOfMolesInPhase * dFdV() - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
            dh = 1.0 + Btemp / Math.pow(BonV, 2.0) * (Btemp / numberOfMolesInPhase * dFdVdV());
            dhh = -2.0 * Btemp / Math.pow(BonV, 3.0) * (Btemp / numberOfMolesInPhase * dFdVdV()) - Math.pow(Btemp, 2.0) / Math.pow(BonV, 4.0) * (Btemp / numberOfMolesInPhase * dFdVdVdV());

            d1 = -h / dh;
            d2 = -dh / dhh;

            if (Math.abs(d1 / d2) <= 1.0) {
                BonV += d1 * (1.0 + 0.5 * d1 / d2);
            } else if (d1 / d2 < -1) {
                BonV += d1 * (1.0 + 0.5 * -1.0);
            } else if (d1 / d2 > 1) {
                BonV += d2;
                double hnew = h + d2 * -h / d1;
                if (Math.abs(hnew) > Math.abs(h)) {
                    BonV = phasetype == 1 ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature()) : pressure * getB() / (numberOfMolesInPhase * temperature * R);
                }
            }

            if (BonV > 1) {
                BonV = 1.0 - 1.0e-8;
                BonVold = 10;
            }
            if (BonV < 0) {
                BonV = 1.0e-8;
                BonVold = 10;
            }

            setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
            Z = pressure * getMolarVolume() / (R * temperature);

        // System.out.println("Z" + Z);
        } while (Math.abs((BonV - BonVold) / BonV) > 1.0e-10 && iterations < 1001);
        //     System.out.println("Z" + Z + " iterations " + iterations + " h " + h);
        //System.out.println("pressure " + Z*R*temperature/getMolarVolume());
        //if(iterations>=100) throw new util.exception.TooManyIterationsException();
        //System.out.println("error in volume " + (-pressure+R*temperature/getMolarVolume()-R*temperature*dFdV()));// + " firstterm " + (R*temperature/molarVolume) + " second " + R*temperature*dFdV());
        if (Double.isNaN(getMolarVolume())) {
            throw new neqsim.util.exception.IsNaNException();
        //System.out.println("BonV: " + BonV + " "+"  itert: " +   iterations +" " +h + " " +dh + " B " + Btemp + "  D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv" + fVV());
        }
        return getMolarVolume();
    }

    public double molarVolume2(double pressure, double temperature, double A, double B, int phase) throws neqsim.util.exception.IsNaNException, neqsim.util.exception.TooManyIterationsException {

        Z = phase == 0 ? 1.0 : 1.0e-5;
        setMolarVolume(Z * R * temperature / pressure);
        // super.molarVolume(pressure,temperature, A, B, phase);
        int iterations = 0;
        double err = 0.0, dErrdV = 0.0;
        double deltaV = 0;

        do {
            A = calcA(this, temperature, pressure, numberOfComponents);
            B = calcB(this, temperature, pressure, numberOfComponents);
            double dFdV = dFdV(), dFdVdV = dFdVdV(), dFdVdVdV = dFdVdVdV();
            double factor1 = 1.0e0, factor2 = 1.0e0;
            err = -R * temperature * dFdV + R * temperature / getMolarVolume() - pressure;

            logger.info("pressure " + -R * temperature * dFdV + " " + R * temperature / getMolarVolume());
            //-pressure;
            dErrdV = -R * temperature * dFdVdV - R * temperature * numberOfMolesInPhase / Math.pow(getVolume(), 2.0);

            logger.info("errdV " + dErrdV);
            logger.info("err " + err);

            deltaV = -err / dErrdV;

            setMolarVolume(getMolarVolume() + deltaV / numberOfMolesInPhase);

            Z = pressure * getMolarVolume() / (R * temperature);
            if (Z < 0) {
                Z = 1e-6;
                setMolarVolume(Z * R * temperature / pressure);
            }
        //System.out.println("Z " + Z);
        } while (Math.abs(err) > 1.0e-8 || iterations < 100);
        //System.out.println("Z " + Z);
        return getMolarVolume();
    }

    public double getGcpav() {
        return gcpav;
    }

    public void setGcpav(double gcpav) {
        this.gcpav = gcpav;
    }

    public CPAMixingInterface getCpamix() {
        return cpamix;
    }

      public int getCrossAssosiationScheme(int comp1, int comp2, int site1, int site2) {
        if (comp1 == comp2) {
            return selfAccociationScheme[comp1][site1][site2];
        }
        return crossAccociationScheme[comp1][comp2][site1][site2];
    }
      
        /**
     * @return the totalNumberOfAccociationSites
     */
    public int getTotalNumberOfAccociationSites() {
        return totalNumberOfAccociationSites;
    }

    /**
     * @param totalNumberOfAccociationSites the totalNumberOfAccociationSites to set
     */
    public void setTotalNumberOfAccociationSites(int totalNumberOfAccociationSites) {
        this.totalNumberOfAccociationSites = totalNumberOfAccociationSites;
    }
}