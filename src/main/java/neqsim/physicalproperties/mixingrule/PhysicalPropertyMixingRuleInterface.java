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
   * Gets the Grunberg-Nissan interaction parameter at a given temperature.
   *
   * <p>
   * The parameter is {@code Gij(298.15 K) * exp(-k (T - 298.15))}, where k is the decay coefficient stored with the
   * pair in the INTER table (column GIJVISCT, 1/K). Pairs without a coefficient give the constant {@code Gij}.
   * </p>
   *
   * @param i first component index
   * @param j second component index
   * @param temperature temperature in K
   * @return interaction parameter at the given temperature
   */
  public default double getViscosityGij(int i, int j, double temperature) {
    return getViscosityGij(i, j);
  }

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
