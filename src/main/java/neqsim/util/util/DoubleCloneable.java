/*
 * DoubleCloneable.java
 *
 * Created on 3. juni 2001, 20:19
 */

package neqsim.util.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * DoubleCloneable class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class DoubleCloneable implements Cloneable {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(DoubleCloneable.class);
  double doubleValue;

  /**
   * Constructor for DoubleCloneable.
   */
  public DoubleCloneable() {
  }

  /**
   * Constructor for DoubleCloneable.
   *
   * @param val a double
   */
  public DoubleCloneable(double val) {
    this.doubleValue = val;
  }

  /** {@inheritDoc} */
  @Override
  public DoubleCloneable clone() {
    DoubleCloneable clonedSystem = null;
    try {
      clonedSystem = (DoubleCloneable) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }
    return clonedSystem;
  }

  /**
   * doubleValue.
   *
   * @return a double
   */
  public double doubleValue() {
    return doubleValue;
  }

  /**
   * set.
   *
   * @param val a double
   */
  public void set(double val) {
    doubleValue = val;
  }
}
