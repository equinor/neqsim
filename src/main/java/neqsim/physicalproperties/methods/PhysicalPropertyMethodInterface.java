/*
 * PhysicalPropertyMethodInterface.java
 *
 * Created on 21. august 2001, 13:20
 */

package neqsim.physicalproperties.methods;

import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * PhysicalPropertyMethodInterface interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface PhysicalPropertyMethodInterface extends Cloneable, java.io.Serializable {
  /**
   * clone.
   *
   * @return a {@link neqsim.physicalproperties.methods.PhysicalPropertyMethodInterface} object
   */
  public PhysicalPropertyMethodInterface clone();

  /**
   * setPhase.
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public void setPhase(PhysicalProperties phase);

  /**
   * tuneModel.
   *
   * @param val a double
   * @param temperature a double
   * @param pressure a double
   */
  public void tuneModel(double val, double temperature, double pressure);
}
