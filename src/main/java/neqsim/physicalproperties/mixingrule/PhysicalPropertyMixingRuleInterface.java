/*
 * PhysicalPropertyMixingRuleInterface.java
 *
 * Created on 2. august 2001, 13:41
 */

package neqsim.physicalproperties.mixingrule;

import neqsim.thermo.phase.PhaseInterface;

/**
 * PhysicalPropertyMixingRuleInterface interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface PhysicalPropertyMixingRuleInterface extends Cloneable {
  /**
   * getViscosityGij.
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getViscosityGij(int i, int j);

  /**
   * setViscosityGij.
   *
   * @param val a double
   * @param i a int
   * @param j a int
   */
  public void setViscosityGij(double val, int i, int j);

  /**
   * initMixingRules.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void initMixingRules(PhaseInterface phase);

  /**
   * clone.
   *
   * @return a {@link neqsim.physicalproperties.mixingrule.PhysicalPropertyMixingRuleInterface} object
   */
  public PhysicalPropertyMixingRuleInterface clone();
}
