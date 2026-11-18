/*
 * PhysicalPropertyMethod.java
 *
 * Created on 3. august 2001, 22:49
 */

package neqsim.physicalproperties.methods;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * Abstract PhysicalPropertyMethod class.
 *
 * @author esol
 * @version $Id: $Id
 */
public abstract class PhysicalPropertyMethod implements PhysicalPropertyMethodInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhysicalPropertyMethod.class);

  /**
   * Constructor for PhysicalPropertyMethod.
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public PhysicalPropertyMethod(PhysicalProperties phase) {
    setPhase(phase);
  }

  /**
   * Copy mutable coefficient rows while preserving absent arrays and rows.
   *
   * @param source source matrix, possibly null
   * @return independently owned matrix, or null when absent
   */
  protected static double[][] copyCoefficientMatrix(double[][] source) {
    if (source == null) {
      return null;
    }
    double[][] copy = source.clone();
    for (int row = 0; row < source.length; row++) {
      copy[row] = source[row] == null ? null : source[row].clone();
    }
    return copy;
  }

  /** {@inheritDoc} */
  @Override
  public PhysicalPropertyMethod clone() {
    PhysicalPropertyMethod properties = null;

    try {
      properties = (PhysicalPropertyMethod) super.clone();
    } catch (CloneNotSupportedException ex) {
      throw new AssertionError("Clone failed for PhysicalPropertyMethod", ex);
    }

    return properties;
  }

  /** {@inheritDoc} */
  @Override
  public void tuneModel(double val, double temperature, double pressure) {
    throw new UnsupportedOperationException("Unimplemented method 'tuneModel'");
  }
}
