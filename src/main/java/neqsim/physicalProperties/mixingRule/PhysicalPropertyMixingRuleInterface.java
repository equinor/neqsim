/*
 * PhysicalPropertyMixingRuleInterface.java
 *
 * Created on 2. august 2001, 13:41
 */
package neqsim.physicalProperties.mixingRule;

import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author esol
 * @version
 */
public interface PhysicalPropertyMixingRuleInterface extends Cloneable {

    public double getViscosityGij(int i, int j);

    public void setViscosityGij(double val, int i, int j);

    public void initMixingRules(PhaseInterface phase);

    public Object clone();
}
