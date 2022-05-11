/*
* System_SRK_EOS.java
*
* Created on 8. april 2000, 23:14
*/
package neqsim.thermo.component;

import neqsim.thermo.component.attractiveEosTerm.AttractiveTermPr;
import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class ComponentPRvolcor extends ComponentPR {

    private static final long serialVersionUID = 1000;

    public ComponentPRvolcor(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
    }

    public ComponentPRvolcor(int number, double TC, double PC, double M, double a, double moles) {
        super(number, TC, PC, M, a, moles);
    }
    @Override
    public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
        return super.dFdN(phase,numberOfComponents,temperature,pressure);
    }

    @Override
    public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
        return super.dFdNdT(phase,numberOfComponents,temperature,pressure);
    }
    @Override
    public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
        return super.dFdNdV(phase,numberOfComponents,temperature,pressure);
    }

}