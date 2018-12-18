  /*
 * System_SRK_EOS.java
 *
 * Created on 8. april 2000, 23:14
 */
package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseCPAInterface;
import neqsim.thermo.phase.PhaseInterface;
import org.apache.log4j.Logger;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class ComponentElectrolyteCPAOld extends ComponentModifiedFurstElectrolyteEos implements ComponentCPAInterface {

    private static final long serialVersionUID = 1000;

    int cpaon = 1;

    private double[][] xsitedni = new double[0][0];
    double[] xsite = new double[0];
    double[] xsiteOld = new double[0];
    double[] xsitedV = new double[0];
    double[] xsitedT = new double[0];
    static Logger logger = Logger.getLogger(ComponentElectrolyteCPAOld.class);

    public ComponentElectrolyteCPAOld() {
    }

    public ComponentElectrolyteCPAOld(double moles) {
        super(moles);
    }

    public ComponentElectrolyteCPAOld(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
        xsite = new double[numberOfAssociationSites];
        xsitedV = new double[numberOfAssociationSites];
        xsiteOld = new double[numberOfAssociationSites];
        xsitedT = new double[numberOfAssociationSites];
        if (numberOfAssociationSites != 0 && cpaon == 1) {
            logger.info("ass sites: " + numberOfAssociationSites);
            logger.info("aSRK " + a + " aCPA " + aCPA);
            logger.info("bSRK " + b + " bCPA " + bCPA);
            for (int j = 0; j < getNumberOfAssociationSites(); j++) {
                setXsite(j, 1.0);
                setXsiteOld(j, 1.0);
                setXsitedV(j, 0.0);
                setXsitedT(j, 0.0);
            }
            if (Math.abs(aCPA) > 1e-6 && cpaon == 1) {
                a = aCPA;
                b = bCPA;
            }
            setAtractiveTerm(0);
        }
    }

    public ComponentElectrolyteCPAOld(int number, double TC, double PC, double M, double a, double moles) {
        super(number, TC, PC, M, a, moles);
        xsite = new double[numberOfAssociationSites];
        xsitedV = new double[numberOfAssociationSites];
        xsiteOld = new double[numberOfAssociationSites];
        xsitedT = new double[numberOfAssociationSites];
        if (numberOfAssociationSites != 0 && cpaon == 1) {
            for (int j = 0; j < getNumberOfAssociationSites(); j++) {
                setXsite(j, 1.0);
                setXsiteOld(j, 1.0);
                setXsitedV(j, 0.0);
                setXsitedT(j, 0.0);
            }
            if (Math.abs(aCPA) > 1e-6 && cpaon == 1) {
                a = aCPA;
                b = bCPA;
            }
            setAtractiveTerm(0);
        }
    }

    public Object clone() {

        ComponentElectrolyteCPAOld clonedComponent = null;
        try {
            clonedComponent = (ComponentElectrolyteCPAOld) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }
        clonedComponent.xsite = xsite.clone();
        System.arraycopy(this.xsite, 0, clonedComponent.xsite, 0, xsite.length);
        clonedComponent.xsiteOld = xsiteOld.clone();
        System.arraycopy(this.xsiteOld, 0, clonedComponent.xsiteOld, 0, xsiteOld.length);
        clonedComponent.xsitedV = xsitedV.clone();
        System.arraycopy(this.xsitedV, 0, clonedComponent.xsitedV, 0, xsitedV.length);
        clonedComponent.xsitedT = xsitedT.clone();
        System.arraycopy(this.xsitedT, 0, clonedComponent.xsitedT, 0, xsitedT.length);
        return clonedComponent;
    }

    public void init(double temperature, double pressure, double totalNumberOfMoles, double beta, int type) {
        super.init(temperature, pressure, totalNumberOfMoles, beta, type);
    }

    public double getVolumeCorrection() {
        if((aCPA>1.0e-10) && cpaon == 1) {
            return 0.0;
        } else {
            return super.getVolumeCorrection();
        }
    }
    
    public void setAtractiveTerm(int i) {
        super.setAtractiveTerm(i);
        if (Math.abs(aCPA) > 1e-6 && cpaon == 1) {
            getAtractiveTerm().setm(mCPA);
        }
    }

    public void seta(double a) {
        aCPA = a;
    }

    public void setb(double a) {
        bCPA = a;
    }

    public double calca() {
        if (Math.abs(aCPA) > 1e-6 && cpaon == 1) {
            return aCPA;
        } else {
            return super.calca();
        }
    }

    public double calcb() {
        if (Math.abs(aCPA) > 1e-6 && cpaon == 1) {
            return bCPA;
        } else {
            return super.calcb();
        }
    }

    public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
        double Fsup = super.dFdN(phase, numberOfComponents, temperature, pressure);
        double Fcpa = 0.0;
        //if(phase.getPhaseType()==1) cpaon=0;
        Fcpa = dFCPAdN(phase, numberOfComponents, temperature, pressure);
        //System.out.println("Fsup " + Fsup + "  fcpa " + Fcpa);
        return Fsup + cpaon * Fcpa;
    }

    public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
        return super.dFdNdT(phase, numberOfComponents, temperature, pressure) + dFCPAdNdT(phase, numberOfComponents, temperature, pressure);
    }

    public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
        return super.dFdNdV(phase, numberOfComponents, temperature, pressure) + dFCPAdNdV(phase, numberOfComponents, temperature, pressure);
    }

    public double dFdNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
        return super.dFdNdN(j, phase, numberOfComponents, temperature, pressure);
    }

    public double dFCPAdN(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
        double xi = 0.0;
        for (int i = 0; i < numberOfAssociationSites; i++) {
            xi += Math.log(xsite[i]);
        }
        return (xi - ((PhaseCPAInterface) phase).getHcpatot() / 2.0 * calc_lngi(phase));
    }

    public double dFCPAdNdV(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
        double xi = dFCPAdNdXidXdV(phase);
        double xi2 = -((PhaseCPAInterface) phase).getHcpatot() / 2.0 * calc_lngidV(phase);
        return xi + xi2;
    }

    public double dFCPAdNdT(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
        double xi = 0.0;
        for (int i = 0; i < numberOfAssociationSites; i++) {
            xi += (1.0 / xsite[i] - 1.0 / 2.0) * xsitedT[i];
        }
        return xi;
    }

    public double calc_lngidV(PhaseInterface phase) {
        return 2.0 * getBi() * (10.0) / ((8.0 * phase.getTotalVolume() - phase.getB()) * (4.0 * phase.getTotalVolume() - phase.getB())) -
                2.0 * getBi() * (10.0 * phase.getTotalVolume() - phase.getB()) * (32 * Math.pow(phase.getTotalVolume(), 2.0) - 12.0 * phase.getTotalVolume() * phase.getB() + Math.pow(phase.getB(), 2.0)) / Math.pow(((8.0 * phase.getTotalVolume() - phase.getB()) * (4.0 * phase.getTotalVolume() - phase.getB())), 2.0);

    }

    public double dFCPAdVdXi(int site, PhaseInterface phase) {
        return 1.0 / (2.0 * phase.getTotalVolume()) * (1.0 - phase.getTotalVolume() * ((PhaseCPAInterface) phase).getGcpav()) * getNumberOfMolesInPhase();
    }

    public double dFCPAdNdXi(int site, PhaseInterface phase) {
        double xi = 1.0 / xsite[site];

        //  return xi - tempp;
        return xi + getNumberOfMolesInPhase() / 2.0 * calc_lngi(phase);
    }

    public double dFCPAdXidXj(int sitei, int sitej, int compj, PhaseInterface phase) {
        return 0.0;
    }

    public double dFCPAdXi(int site, PhaseInterface phase) {
        return 0.0;
    }

    public double dFCPAdNdXidXdV(PhaseInterface phase) {
        double temp = 0.0;
        for (int i = 0; i < numberOfAssociationSites; i++) {
            temp += dFCPAdNdXi(i, phase) * getXsitedV()[i];
        }
        return temp;
    }

    public double calc_lngi(PhaseInterface phase) {
        return 0.475 / (1.0 - 0.475 * phase.getB() / phase.getTotalVolume()) * getBi() / phase.getTotalVolume();
    }

    /** Getter for property xsite.
     * @return Value of property xsite.
     */
    public double[] getXsite() {
        return this.xsite;
    }

    /** Setter for property xsite.
     * @param xsite New value of property xsite.
     */
    public void setXsite(double[] xsite) {
        this.xsite = xsite;
    }

    public void setXsite(int i, double xsite) {
        this.xsite[i] = xsite;
    }

    public double[] getXsitedV() {
        return this.xsitedV;
    }

    public void setXsitedV(int i, double xsitedV) {
        this.xsitedV[i] = xsitedV;
    }

    public double[] getXsiteOld() {
        return this.xsiteOld;
    }

    /** Setter for property xsite.
     * @param xsite New value of property xsite.
     */
    public void setXsiteOld(double[] xsiteOld) {
        this.xsiteOld = xsiteOld;
    }

    public void setXsiteOld(int i, double xsiteOld) {
    }

    public double[] getXsitedT() {
        return null;
    }

    public double[] getXsitedTdT() {
        return null;
    }

    public void setXsitedT(int i, double xsitedT) {
    }

    public void setXsitedTdT(int i, double xsitedT) {
    }

       public void setXsitedni(int xnumb, int compnumb, double val) {
        xsitedni[xnumb][compnumb] = val;
    }
}
