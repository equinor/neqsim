/*
 * System_SRK_EOS.java
 *
 * Created on 8. april 2000, 23:14
 */
package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseCPAInterface;
import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
abstract class ComponentPrCPA extends ComponentPR implements ComponentCPAInterface {
    private static final long serialVersionUID = 1000;

    /**
     * Creates new System_SRK_EOS Ev liten fil ja.
     */
    int cpaon = 1;
    double[] xsite;

    public ComponentPrCPA() {}

    public ComponentPrCPA(double moles) {
        super(moles);
    }

    public ComponentPrCPA(String component_name, double moles, double molesInPhase,
            int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
        xsite = new double[numberOfAssociationSites];
        if ((numberOfAssociationSites != 0 || Math.abs(aCPA) > 1e-6) && cpaon == 1) {
            // System.out.println("ass sites: " + numberOfAssociationSites);
            // System.out.println("aSRK " + a + " aCPA " + aCPA);
            // System.out.println("bSRK " + b + " bCPA " + bCPA);
            for (int j = 0; j < getNumberOfAssociationSites(); j++) {
                setXsite(j, 0.0);
            }
            a = aCPA;
            b = bCPA;
            setAtractiveTerm(1);
        }
    }

    public ComponentPrCPA(int number, double TC, double PC, double M, double a, double moles) {
        super(number, TC, PC, M, a, moles);
        xsite = new double[numberOfAssociationSites];
        for (int j = 0; j < getNumberOfAssociationSites(); j++) {
            setXsite(j, 1.0);
        }
        if ((numberOfAssociationSites != 0 || Math.abs(aCPA) > 1e-6) && cpaon == 1) {
            a = aCPA;
            b = bCPA;
        }
        setAtractiveTerm(1);
    }

    @Override
    public double getVolumeCorrection() {
        if ((aCPA > 1.0e-10) && cpaon == 1) {
            return 0.0;
        } else {
            return super.getVolumeCorrection();
        }
    }

    @Override
    public ComponentPrCPA clone() {

        ComponentPrCPA clonedComponent = null;
        try {
            clonedComponent = (ComponentPrCPA) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedComponent;
    }

    @Override
    public void init(double temperature, double pressure, double totalNumberOfMoles, double beta,
            int type) {
        super.init(temperature, pressure, totalNumberOfMoles, beta, type);
    }

    @Override
    public double calca() {
        if ((numberOfAssociationSites != 0 || Math.abs(aCPA) > 1e-6) && cpaon == 1) {
            return aCPA;
        }
        return a;
    }

    @Override
    public double calcb() {
        if ((numberOfAssociationSites != 0 || Math.abs(aCPA) > 1e-6) && cpaon == 1) {
            return bCPA;
        }
        return b;
    }

    @Override
    public void setAtractiveTerm(int i) {
        super.setAtractiveTerm(i);
        if ((getNumberOfAssociationSites() > 0 || Math.abs(aCPA) > 1e-6) && cpaon == 1) {
            getAtractiveTerm().setm(mCPA);
        }
    }

    @Override
    public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature,
            double pressure) {
        double Fsup = super.dFdN(phase, numberOfComponents, temperature, pressure);
        double Fcpa = 0.0;
        // if(phase.getPhaseType()==1) cpaon=0;
        Fcpa = dFCPAdN(phase, numberOfComponents, temperature, pressure);
        // System.out.println("Fsup " + Fsup + " fcpa " + Fcpa);
        return Fsup + cpaon * Fcpa;
    }

    @Override
    public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature,
            double pressure) {
        return super.dFdNdT(phase, numberOfComponents, temperature, pressure);
    }

    @Override
    public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature,
            double pressure) {
        return super.dFdNdV(phase, numberOfComponents, temperature, pressure);
    }

    @Override
    public double dFdNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature,
            double pressure) {
        return super.dFdNdN(j, phase, numberOfComponents, temperature, pressure);
    }

    public double dFCPAdN(PhaseInterface phase, int numberOfComponents, double temperature,
            double pressure) {
        double xi = 0.0;
        for (int i = 0; i < numberOfAssociationSites; i++) {
            xi += Math.log(xsite[i]);
        }
        return (xi - ((PhaseCPAInterface) phase).getHcpatot() / 2.0 * calc_lngi(phase));
    }

    public double calc_lngi(PhaseInterface phase) {
        return 0.475 / (1.0 - 0.475 * phase.getB() / phase.getTotalVolume()) * getBi()
                / phase.getTotalVolume();
    }

    public double calc_lngi2(PhaseInterface phase) {
        return 2.0 * getBi() * (10.0 * phase.getTotalVolume() - phase.getB())
                / ((8.0 * phase.getTotalVolume() - phase.getB())
                        * (4.0 * phase.getTotalVolume() - phase.getB()));
    }

    /**
     * Getter for property xsite.
     *
     * @return Value of property xsite.
     */
    @Override
    public double[] getXsite() {
        return this.xsite;
    }

    /**
     * Setter for property xsite.
     *
     * @param xsite New value of property xsite.
     */
    public void setXsite(double[] xsite) {
        this.xsite = xsite;
    }

    @Override
    public void setXsite(int i, double xsite) {
        this.xsite[i] = xsite;
    }
}
