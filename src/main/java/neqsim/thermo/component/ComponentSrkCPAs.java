/*
 * System_SRK_EOS.java
 *
 * Created on 8. april 2000, 23:14
 */
package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class ComponentSrkCPAs extends ComponentSrkCPA {

    private static final long serialVersionUID = 1000;

    /** Creates new System_SRK_EOS
     * Ev liten fil ja.
     */
    public ComponentSrkCPAs() {
    }

    public ComponentSrkCPAs(double moles) {
        super(moles);
    }

    public ComponentSrkCPAs(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
    }

    public ComponentSrkCPAs(int number, double TC, double PC, double M, double a, double moles) {
        super(number, TC, PC, M, a, moles);
    }

    public Object clone() {

        ComponentSrkCPAs clonedComponent = null;
        try {
            clonedComponent = (ComponentSrkCPAs) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        return clonedComponent;
    }
    /*
    public double calc_lngi2(PhaseInterface phase) {
    return 0.475 / (1.0 - 0.475 * phase.getB() / phase.getTotalVolume()) * getBi() / phase.getTotalVolume();
    }
     */

    public double calc_lngi(PhaseInterface phase) {
        return 0.475 * getBi() / (phase.getTotalVolume() - 0.475 * phase.getB());
    }
    /*
    public double calc_lngi(PhaseInterface phase) {
    double nbet = phase.getB() / 4.0 / phase.getVolume();
    double dlngdb = 1.9 / (1.0 - 1.9 * nbet);
    double nbeti = nbet / phase.getB() * getBi();
    return dlngdb * nbeti;
    }
     */

    public double calc_lngidV(PhaseInterface phase) {
        double temp = phase.getTotalVolume() - 0.475 * phase.getB();
        return -0.475 * getBi() / (temp*temp);
    }

    public double calc_lngij(int j, PhaseInterface phase) {
        double temp = phase.getTotalVolume() - 0.475 * phase.getB();
       // System.out.println("B " + phase.getB() + " Bi " + getBi() + "  bij " + getBij(j));
        return 0.475 * getBij(j)*0 / (phase.getTotalVolume() - 0.475 * phase.getB()) -
                0.475 * getBi() * 1.0 / (temp*temp)* (-0.475 * ((ComponentEosInterface) phase.getComponent(j)).getBi());
    }
}
