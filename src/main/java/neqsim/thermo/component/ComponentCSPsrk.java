/*
 * System_SRK_EOS.java
 *
 * Created on 8. april 2000, 23:14
 */

package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseCSPsrkEos;
import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class ComponentCSPsrk extends ComponentSrk {

    private static final long serialVersionUID = 1000;

    double f_scale_mix_i = 0;
    double h_scale_mix_i = 0;
    PhaseCSPsrkEos refPhaseBWRS = null;

    public ComponentCSPsrk() {
        super();
    }

    public ComponentCSPsrk(double moles) {
        super(moles);
    }

    public ComponentCSPsrk(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
    }

    public ComponentCSPsrk(int number, double TC, double PC, double M, double a, double moles) {
        super(number, TC, PC, M, a, moles);
    }

    @Override
    public ComponentCSPsrk clone() {

        ComponentCSPsrk clonedComponent = null;
        try {
            clonedComponent = (ComponentCSPsrk) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedComponent;
    }

    @Override
	public void init(double temperature, double pressure, double totalNumberOfMoles, double beta, int type) {
        super.init(temperature, pressure, totalNumberOfMoles, beta, type);
        h_scale_mix_i = Bi / (refPhaseBWRS.getRefBWRSPhase().getB()/refPhaseBWRS.getRefBWRSPhase().getNumberOfMolesInPhase());

        double termfi1 = Ai / refPhaseBWRS.getA();
        double termfi2 = h_scale_mix_i / refPhaseBWRS.getH_scale_mix();
        double termfi3 = ((ComponentEosInterface) refPhaseBWRS.getRefBWRSPhase().getComponent(0)).getaDiffT()
                / ((ComponentEosInterface) refPhaseBWRS.getRefBWRSPhase().getComponent(0)).getaT()
                * refPhaseBWRS.getRefBWRSPhase().getTemperature() / refPhaseBWRS.getNumberOfMolesInPhase();
        double termfi4 = 1.0 - ((ComponentEosInterface) refPhaseBWRS.getRefBWRSPhase().getComponent(0)).getaDiffT()
                / ((ComponentEosInterface) refPhaseBWRS.getRefBWRSPhase().getComponent(0)).getaT()
                * refPhaseBWRS.getRefBWRSPhase().getTemperature();

        f_scale_mix_i = (termfi1 - termfi2 - termfi3) / termfi4 * refPhaseBWRS.getF_scale_mix();
    }

    @Override
	public double dFdN(PhaseInterface phase, int numberOfComponentphases, double temperature, double pressure) {
        // System.out.println("dFdN super " + super.dFdN(phase,
        // numberOfComponentphases,temperature,pressure));
        double term1 = f_scale_mix_i * refPhaseBWRS.getRefBWRSPhase().getF()
                / refPhaseBWRS.getRefBWRSPhase().getNumberOfMolesInPhase()
                * refPhaseBWRS.getRefBWRSPhase().getTemperature() / temperature;
        double term2 = refPhaseBWRS.getF_scale_mix()
                * (refPhaseBWRS.getRefBWRSPhase().dFdT() / refPhaseBWRS.getRefBWRSPhase().getNumberOfMolesInPhase()
                        * (1.0 / refPhaseBWRS.getNumberOfMolesInPhase() - f_scale_mix_i / refPhaseBWRS.getF_scale_mix())
                        * refPhaseBWRS.getRefBWRSPhase().getTemperature()
                        + refPhaseBWRS.getRefBWRSPhase().dFdV()
                                / refPhaseBWRS.getRefBWRSPhase().getNumberOfMolesInPhase()
                                * (-1.0 * h_scale_mix_i / refPhaseBWRS.getH_scale_mix()
                                        * refPhaseBWRS.getRefBWRSPhase().getMolarVolume()))
                * refPhaseBWRS.getRefBWRSPhase().getTemperature() / temperature
                / refPhaseBWRS.getRefBWRSPhase().getNumberOfMolesInPhase();
        double term3 = refPhaseBWRS.getF_scale_mix() * refPhaseBWRS.getRefBWRSPhase().getF() * 1.0 / temperature * 1.0
                / refPhaseBWRS.getRefBWRSPhase().getNumberOfMolesInPhase()
                * (1.0 / refPhaseBWRS.getNumberOfMolesInPhase() - f_scale_mix_i / refPhaseBWRS.getF_scale_mix())
                * refPhaseBWRS.getRefBWRSPhase().getTemperature();
        // System.out.println("dFdN " + super.dFdN(phase,
        // numberOfComponentphases,temperature,pressure));
        return term1 + term2 + term3;
    }

    /**
     * Getter for property f_scale_mix_i.
     * 
     * @return Value of property f_scale_mix_i.
     *
     */
    public double getF_scale_mix_i() {
        return f_scale_mix_i;
    }

    /**
     * Setter for property f_scale_mix_i.
     * 
     * @param f_scale_mix_i New value of property f_scale_mix_i.
     *
     */
    public void setF_scale_mix_i(double f_scale_mix_i) {
        this.f_scale_mix_i = f_scale_mix_i;
    }

    /**
     * Getter for property h_scale_mix_i.
     * 
     * @return Value of property h_scale_mix_i.
     *
     */
    public double getH_scale_mix_i() {
        return h_scale_mix_i;
    }

    /**
     * Setter for property h_scale_mix_i.
     * 
     * @param h_scale_mix_i New value of property h_scale_mix_i.
     *
     */
    public void setH_scale_mix_i(double h_scale_mix_i) {
        this.h_scale_mix_i = h_scale_mix_i;
    }

    /**
     * Getter for property refPhaseBWRS.
     * 
     * @return Value of property refPhaseBWRS.
     *
     */
    public neqsim.thermo.phase.PhaseCSPsrkEos getRefPhaseBWRS() {
        return refPhaseBWRS;
    }

    /**
     * Setter for property refPhaseBWRS.
     * 
     * @param refPhaseBWRS New value of property refPhaseBWRS.
     *
     */
    public void setRefPhaseBWRS(neqsim.thermo.phase.PhaseCSPsrkEos refPhaseBWRS) {
        this.refPhaseBWRS = refPhaseBWRS;
    }

}
