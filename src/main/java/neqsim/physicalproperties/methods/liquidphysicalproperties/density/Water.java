/*
 * Costald.java
 *
 * Created on 13. July 2022
 */

package neqsim.physicalproperties.methods.liquidphysicalproperties.density;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.methods.liquidphysicalproperties.LiquidPhysicalPropertyMethod;
import neqsim.physicalproperties.methods.methodinterface.DensityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * Costald Density Calculation class for liquids.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Water extends LiquidPhysicalPropertyMethod implements DensityInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Water.class);

  /**
   * <p>
   * Constructor for Water.
   * </p>
   *
   * @param liquidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public Water(PhysicalProperties liquidPhase) {
    super(liquidPhase);
  }

  /** {@inheritDoc} */
  @Override
  public Costald clone() {
    Costald properties = null;

    try {
      properties = (Costald) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return properties;
  }

  /**
   * {@inheritDoc} Densities of compressed liquid mixtures. Unit: kg/m^3
   */
  @Override
  public double calcDensity() {

    return 1000.0;
  }
}
