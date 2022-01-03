/*
 * PhysicalPropertyMixingRuleInterface.java
 *
 * Created on 2. august 2001, 13:41
 */
package neqsim.physicalProperties.mixingRule;

import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>PhysicalPropertyMixingRuleInterface interface.</p>
 *
 * @author  esol
 */
public interface PhysicalPropertyMixingRuleInterface extends Cloneable {

    /**
     * <p>getViscosityGij.</p>
     *
     * @param i a int
     * @param j a int
     * @return a double
     */
    public double getViscosityGij(int i, int j);

    /**
     * <p>setViscosityGij.</p>
     *
     * @param val a double
     * @param i a int
     * @param j a int
     */
    public void setViscosityGij(double val, int i, int j);

    /**
     * <p>initMixingRules.</p>
     *
     * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
     */
    public void initMixingRules(PhaseInterface phase);

    /**
     * <p>clone.</p>
     *
     * @return a {@link neqsim.physicalProperties.mixingRule.PhysicalPropertyMixingRuleInterface} object
     */
    public PhysicalPropertyMixingRuleInterface clone();
}
