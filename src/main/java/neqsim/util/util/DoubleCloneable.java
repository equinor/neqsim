/*
 * DoubleCloneable.java
 *
 * Created on 3. juni 2001, 20:19
 */

package neqsim.util.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * DoubleCloneable class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class DoubleCloneable implements Cloneable {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(DoubleCloneable.class);
  double doubleValue;

  /**
   * <p>
   * Constructor for DoubleCloneable.
   * </p>
   */
  public DoubleCloneable() {}

  /**
   * <p>
   * Constructor for DoubleCloneable.
   * </p>
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
      logger.error(ex.getMessage());;
    }
    return clonedSystem;
  }

  /**
   * <p>
   * doubleValue.
   * </p>
   *
   * @return a double
   */
  public double doubleValue() {
    return doubleValue;
  }

  /**
   * <p>
   * set.
   * </p>
   *
   * @param val a double
   */
  public void set(double val) {
    doubleValue = val;
  }
}
