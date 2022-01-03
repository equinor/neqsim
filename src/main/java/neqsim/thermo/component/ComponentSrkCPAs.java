/*
 * System_SRK_EOS.java
 *
 * Created on 8. april 2000, 23:14
 */
package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>ComponentSrkCPAs class.</p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentSrkCPAs extends ComponentSrkCPA {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new System_SRK_EOS Ev liten fil ja.
     */
    public ComponentSrkCPAs() {
    }

    /**
     * <p>Constructor for ComponentSrkCPAs.</p>
     *
     * @param moles a double
     */
    public ComponentSrkCPAs(double moles) {
        super(moles);
    }

    /**
     * <p>Constructor for ComponentSrkCPAs.</p>
     *
     * @param component_name a {@link java.lang.String} object
     * @param moles a double
     * @param molesInPhase a double
     * @param compnumber a int
     */
    public ComponentSrkCPAs(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
    }

    /**
     * <p>Constructor for ComponentSrkCPAs.</p>
     *
     * @param number a int
     * @param TC a double
     * @param PC a double
     * @param M a double
     * @param a a double
     * @param moles a double
     */
    public ComponentSrkCPAs(int number, double TC, double PC, double M, double a, double moles) {
        super(number, TC, PC, M, a, moles);
    }

    /** {@inheritDoc} */
    @Override
    public ComponentSrkCPAs clone() {

        ComponentSrkCPAs clonedComponent = null;
        try {
            clonedComponent = (ComponentSrkCPAs) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedComponent;
    }
    /*
     * public double calc_lngi2(PhaseInterface phase) { return 0.475 / (1.0 - 0.475
     * * phase.getB() / phase.getTotalVolume()) * getBi() / phase.getTotalVolume();
     * }
     */

	/** {@inheritDoc} */
    @Override
	public double calc_lngi(PhaseInterface phase) {
        return 0.475 * getBi() / (phase.getTotalVolume() - 0.475 * phase.getB());
    }
    /*
     * public double calc_lngi(PhaseInterface phase) { double nbet = phase.getB() /
     * 4.0 / phase.getVolume(); double dlngdb = 1.9 / (1.0 - 1.9 * nbet); double
     * nbeti = nbet / phase.getB() * getBi(); return dlngdb * nbeti; }
     */

	/** {@inheritDoc} */
    @Override
	public double calc_lngidV(PhaseInterface phase) {
        double temp = phase.getTotalVolume() - 0.475 * phase.getB();
        return -0.475 * getBi() / (temp * temp);
    }

	/** {@inheritDoc} */
    @Override
	public double calc_lngij(int j, PhaseInterface phase) {
        double temp = phase.getTotalVolume() - 0.475 * phase.getB();
        // System.out.println("B " + phase.getB() + " Bi " + getBi() + " bij " +
        // getBij(j));
        return 0.475 * getBij(j) * 0 / (phase.getTotalVolume() - 0.475 * phase.getB()) - 0.475 * getBi() * 1.0
                / (temp * temp) * (-0.475 * ((ComponentEosInterface) phase.getComponent(j)).getBi());
    }
}
