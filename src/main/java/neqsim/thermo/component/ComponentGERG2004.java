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
public class ComponentGERG2004 extends ComponentEos {

    private static final long serialVersionUID = 1000;

    /** Creates new System_SRK_EOS
     * Ev liten fil ja.
     */
    public ComponentGERG2004() {
    }

    public ComponentGERG2004(double moles) {
        numberOfMoles = moles;
    }

    public ComponentGERG2004(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);

    }

    public ComponentGERG2004(int number, double TC, double PC, double M, double a, double moles) {
        super(number, TC, PC, M, a, moles);
    }

    public Object clone() {

        ComponentGERG2004 clonedComponent = null;
        try {
            clonedComponent = (ComponentGERG2004) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedComponent;
    }

    public void init(double temperature, double pressure, double totalNumberOfMoles, double beta, int type) {
        super.init(temperature, pressure, totalNumberOfMoles, beta, type);
    }

    public double getVolumeCorrection() {
        return 0.0;
    }

    public double calca() {
        return 0;
    }

    public double calcb() {
        return 0;
    }

    public double fugcoef(PhaseInterface phase) {

        return fugasityCoeffisient;
    }

    public double alpha(double temperature) {
        return 1;
    }

    public double diffaT(double temperature) {
        return 1;
    }

    public double diffdiffaT(double temperature) {
        return 1;
    }
}
