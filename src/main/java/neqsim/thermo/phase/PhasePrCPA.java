/*
 * PhaseSrkEos.java
 *
 * Created on 3. juni 2000, 14:38
 */
package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentCPAInterface;
import neqsim.thermo.component.ComponentSrkCPA;
import neqsim.thermo.mixingRule.CPAMixing;
import neqsim.thermo.mixingRule.CPAMixingInterface;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class PhasePrCPA extends PhasePrEos implements PhaseCPAInterface {

    private static final long serialVersionUID = 1000;

    int totalNumberOfAccociationSites = 0;
    public CPAMixing cpaSelect = new CPAMixing();
    public CPAMixingInterface cpamix;
    double hcpatot = 1.0, hcpatotdT = 0.0, hcpatotdTdT = 0.0, gcpav = 1.0, lngcpa = 0.0, lngcpav = 1.0, gcpavv = 1.0, gcpavvv = 1.0, gcpa = 1.0;
    int cpaon = 1;
    int[][][] selfAccociationScheme = null;
    int[][][][] crossAccociationScheme = null;

    /** Creates new PhaseSrkEos */
    public PhasePrCPA() {
        super();
        cpamix = cpaSelect.getMixingRule(1);
    }

    public Object clone() {
        PhasePrCPA clonedPhase = null;
        try {
            clonedPhase = (PhasePrCPA) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return clonedPhase;
    }

    public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase, double beta) { // type = 0 start init type =1 gi nye betingelser
        boolean Xsolved = true;
        int totiter = 0;
        do {
            super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
            //if(getPhaseType()==1) cpaon=0;
            totiter++;
            if (cpaon == 1) {
                Xsolved = solveX();
                hcpatot = calc_hCPA();
                gcpa = calc_g();
                lngcpa = Math.log(gcpa);

                gcpav = calc_lngV();
                gcpavv = calc_lngVV();
                gcpavvv = calc_lngVVV();
            }
        } while (Xsolved != true && totiter < 5);

        if (type > 1) {
            hcpatotdT = calc_hCPAdT();
            hcpatotdTdT = calc_hCPAdTdT();
        }
        //System.out.println("tot iter " + totiter);
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
    }

    public void addcomponent(String componentName, double moles, double molesInPhase, int compNumber) {
        super.addcomponent(componentName, moles, molesInPhase, compNumber);
        componentArray[compNumber] = new ComponentSrkCPA(componentName, moles, molesInPhase, compNumber);
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
                double xai = ((ComponentSrkCPA) getComponent(i)).getXsite()[j];
                tot += (Math.log(xai) - 1.0 / 2.0 * xai + 1.0 / 2.0);
            }
            ans += getComponent(i).getNumberOfMolesInPhase() * tot;
        }
        return ans;
    }

    public double dFCPAdV() {
        return 1.0 / (2.0 * getTotalVolume()) * (1.0 - getTotalVolume() * gcpav) * hcpatot;
    }

    public double dFCPAdVdV() {
        return -1.0 / getTotalVolume() * dFCPAdV() + hcpatot / (2.0 * getTotalVolume()) * (-gcpav - getTotalVolume() * gcpavv);
    }

    public double dFCPAdVdVdV() {
        return -1.0 / getTotalVolume() * dFCPAdVdV() + 1.0 / Math.pow(getTotalVolume(), 2.0) * dFCPAdV() - hcpatot / (2.0 * Math.pow(getTotalVolume(), 2.0)) * (-gcpav - getTotalVolume() * gcpavv) + hcpatot / (2.0 * getTotalVolume()) * (-2.0 * gcpavv - getTotalVolume() * gcpavvv);
    }

    public double dFCPAdT() {
        return -1.0 / 2.0 * hcpatotdT;
    }

    public double dFCPAdTdT() {
        return -1.0 / 2.0 * hcpatotdTdT;
    }

    public double calc_hCPA() {
        double htot = 0.0;
        double tot = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            htot = 0.0;
            for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
                htot += (1.0 - ((ComponentSrkCPA) getComponent(i)).getXsite()[j]);
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
                        htot += ((ComponentSrkCPA) getComponent(i)).getXsite()[j] * ((ComponentSrkCPA) getComponent(k)).getXsite()[l] * cpamix.calcDeltadT(j, l, i, k, this, temperature, pressure, numberOfComponents);
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
                        htot += ((ComponentSrkCPA) getComponent(i)).getXsite()[j] * ((ComponentSrkCPA) getComponent(k)).getXsite()[l] * cpamix.calcDeltadTdT(j, l, i, k, this, temperature, pressure, numberOfComponents);
                    }
                }

                tot += getComponent(i).getNumberOfMolesInPhase() * getComponent(k).getNumberOfMolesInPhase() * htot;
            }
        }
        //System.out.println("tot " +tot );
        return tot / getTotalVolume();
    }

    public double calc_g() {
        double g = (2.0 - getb() / 4.0 / getMolarVolume()) / (2.0 * Math.pow(1.0 - getb() / 4.0 / getMolarVolume(), 3.0));
        return g;
    }

    public double calc_lngni(int comp) {
        return 0;
    }

    public double calc_lngV() {
        double gv = 0.0, gv2 = 0.0;
        gv = -2.0 * getB() * (10.0 * getTotalVolume() - getB()) / getTotalVolume() / ((8.0 * getTotalVolume() - getB()) * (4.0 * getTotalVolume() - getB()));

//        gv2 = 1.0/(2.0-getB()/(4.0*getTotalVolume()))*getB()/(4.0*Math.pow(getTotalVolume() ,2.0))
//            - 3.0/(1.0-getB()/(4.0*getTotalVolume()))*getB()/(4.0*Math.pow(getTotalVolume() ,2.0));
//        
//        System.out.println("err gv " + (100.0-gv/gv2*100));
        //-2.0*getB()*(10.0*getTotalVolume()-getB())/getTotalVolume()/((8.0*getTotalVolume()-getB())*(4.0*getTotalVolume()-getB()));
//         System.out.println("gv " + gv);

        return gv;
    }

    public double calc_lngVV() {
        double gvv = 0.0;
        gvv = 2.0 * (640.0 * Math.pow(getTotalVolume(), 3.0) - 216.0 * getB() * getTotalVolume() * getTotalVolume() + 24.0 * Math.pow(getB(), 2.0) * getTotalVolume() - Math.pow(getB(), 3.0)) * getB() / (getTotalVolume() * getTotalVolume()) / Math.pow(8.0 * getTotalVolume() - getB(), 2.0) / Math.pow(4.0 * getTotalVolume() - getB(), 2.0);
        return gvv;
    }

    public double calc_lngVVV() {
        double gvvv = 0.0;
        gvvv = 4.0 * (Math.pow(getB(), 5.0) + 17664.0 * Math.pow(getTotalVolume(), 4.0) * getB() - 4192.0 * Math.pow(getTotalVolume(), 3.0) * Math.pow(getB(), 2.0) + 528.0 * Math.pow(getB(), 3.0) * getTotalVolume() * getTotalVolume() - 36.0 * getTotalVolume() * Math.pow(getB(), 4.0) - 30720.0 * Math.pow(getTotalVolume(), 5.0)) * getB() / (Math.pow(getTotalVolume(), 3.0)) / Math.pow(-8.0 * getTotalVolume() + getB(), 3.0) / Math.pow(-4.0 * getTotalVolume() + getB(), 3.0);
        return gvvv;
    }

    public boolean solveX() {
        double err = .0;
        int iter = 0;
        try {
            molarVolume(pressure, temperature, A, B, phaseType);
        } catch (Exception e) {
            e.printStackTrace();
        }
        do {
            iter++;
            err = 0.0;
            for (int i = 0; i < numberOfComponents; i++) {
                for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
                    double old = ((ComponentSrkCPA) getComponent(i)).getXsite()[j];
                    double neeval = cpamix.calcXi(selfAccociationScheme, crossAccociationScheme, j, i, this, temperature, pressure, numberOfComponents);

                    ((ComponentCPAInterface) getComponent(i)).setXsite(j, neeval);
                    err += Math.abs((old - neeval) / neeval);
                }
            }
            //System.out.println("err " + err);
        } while (Math.abs(err) > 1e-10 && iter < 100);
        //System.out.println("iter " +iter);
        return iter < 3;
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

    public double getGcpav() {
        return gcpav;
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
