package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * ComponentElectrolyteCPAstatoil class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentElectrolyteCPAstatoil extends ComponentElectrolyteCPA {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for ComponentElectrolyteCPAstatoil.
     * </p>
     */
    public ComponentElectrolyteCPAstatoil() {}

    /**
     * <p>
     * Constructor for ComponentElectrolyteCPAstatoil.
     * </p>
     *
     * @param moles a double
     */
    public ComponentElectrolyteCPAstatoil(double moles) {
        super(moles);
    }

    /**
     * <p>
     * Constructor for ComponentElectrolyteCPAstatoil.
     * </p>
     *
     * @param component_name a {@link java.lang.String} object
     * @param moles a double
     * @param molesInPhase a double
     * @param compnumber a int
     */
    public ComponentElectrolyteCPAstatoil(String component_name, double moles, double molesInPhase,
            int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
    }

    /**
     * <p>
     * Constructor for ComponentElectrolyteCPAstatoil.
     * </p>
     *
     * @param number a int
     * @param TC a double
     * @param PC a double
     * @param M a double
     * @param a a double
     * @param moles a double
     */
    public ComponentElectrolyteCPAstatoil(int number, double TC, double PC, double M, double a,
            double moles) {
        super(number, TC, PC, M, a, moles);
    }

    /** {@inheritDoc} */
    @Override
    public ComponentElectrolyteCPAstatoil clone() {
        ComponentElectrolyteCPAstatoil clonedComponent = null;
        try {
            clonedComponent = (ComponentElectrolyteCPAstatoil) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedComponent;
    }

    /** {@inheritDoc} */
    @Override
    public double calc_lngi(PhaseInterface phase) {
        // System.out.println("val "
        // +0.475/(1.0-0.475*phase.getB()/phase.getTotalVolume())*getBi()/phase.getTotalVolume());
        return 0.475 / (1.0 - 0.475 * phase.getB() / phase.getTotalVolume()) * getBi()
                / phase.getTotalVolume();
    }

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
        return 0.475 * getBij(j) * 0 / (phase.getTotalVolume() - 0.475 * phase.getB())
                - 0.475 * getBi() * 1.0 / (temp * temp)
                        * (-0.475 * ((ComponentEosInterface) phase.getComponent(j)).getBi());
    }
}
