/*
 * System_SRK_EOS.java
 *
 * Created on 8. april 2000, 23:14
 */
package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class ComponentElectrolyteCPAstatoil extends ComponentElectrolyteCPA implements ComponentCPAInterface {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new System_SRK_EOS Ev liten fil ja.
     */
    public ComponentElectrolyteCPAstatoil() {
    }

    public ComponentElectrolyteCPAstatoil(double moles) {
        super(moles);
    }

    public ComponentElectrolyteCPAstatoil(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
    }

    public ComponentElectrolyteCPAstatoil(int number, double TC, double PC, double M, double a, double moles) {
        super(number, TC, PC, M, a, moles);
    }

    @Override
	public Object clone() {

        ComponentElectrolyteCPAstatoil clonedComponent = null;
        try {
            clonedComponent = (ComponentElectrolyteCPAstatoil) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedComponent;
    }

    @Override
	public double calc_lngi(PhaseInterface phase) {
        // System.out.println("val "
        // +0.475/(1.0-0.475*phase.getB()/phase.getTotalVolume())*getBi()/phase.getTotalVolume());
        return 0.475 / (1.0 - 0.475 * phase.getB() / phase.getTotalVolume()) * getBi() / phase.getTotalVolume();
    }

    @Override
	public double calc_lngidV(PhaseInterface phase) {
        double temp = phase.getTotalVolume() - 0.475 * phase.getB();
        return -0.475 * getBi() / (temp * temp);
    }

    @Override
	public double calc_lngij(int j, PhaseInterface phase) {
        double temp = phase.getTotalVolume() - 0.475 * phase.getB();
        // System.out.println("B " + phase.getB() + " Bi " + getBi() + " bij " +
        // getBij(j));
        return 0.475 * getBij(j) * 0 / (phase.getTotalVolume() - 0.475 * phase.getB()) - 0.475 * getBi() * 1.0
                / (temp * temp) * (-0.475 * ((ComponentEosInterface) phase.getComponent(j)).getBi());
    }

}
