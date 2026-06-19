/*
 * PhysicalPropertyMethodInterface.java
 *
 * Created on 21. august 2001, 13:20
 */

package neqsim.physicalproperties.methods;

import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * PhysicalPropertyMethodInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface PhysicalPropertyMethodInterface extends Cloneable, java.io.Serializable {
  /**
   * <p>
   * clone.
   * </p>
   *
   * @return a {@link neqsim.physicalproperties.methods.PhysicalPropertyMethodInterface} object
   */
  public PhysicalPropertyMethodInterface clone();

  /**
   * <p>
   * setPhase.
   * </p>
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public void setPhase(PhysicalProperties phase);

  /**
   * <p>
   * tuneModel.
   * </p>
   *
   * @param val a double
   * @param temperature a double
   * @param pressure a double
   */
  public void tuneModel(double val, double temperature, double pressure);
}
