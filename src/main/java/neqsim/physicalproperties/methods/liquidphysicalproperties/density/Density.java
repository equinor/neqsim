/*
 * Density.java
 *
 * Created on 24. januar 2001, 19:49
 */

package neqsim.physicalproperties.methods.liquidphysicalproperties.density;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.methods.liquidphysicalproperties.LiquidPhysicalPropertyMethod;
import neqsim.physicalproperties.methods.methodinterface.DensityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * Density class foir liquids.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Density extends LiquidPhysicalPropertyMethod implements DensityInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Density.class);

  /**
   * <p>
   * Constructor for Density.
   * </p>
   *
   * @param liquidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public Density(PhysicalProperties liquidPhase) {
    super(liquidPhase);
  }

  /** {@inheritDoc} */
  @Override
  public Density clone() {
    Density properties = null;

    try {
      properties = (Density) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return properties;
  }

  /** {@inheritDoc} */
  @Override
  public double calcDensity() {
    double tempVar = 0.0;
    if (liquidPhase.getPhase().useVolumeCorrection()) {
      for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
        tempVar += liquidPhase.getPhase().getComponent(i).getx()
            * (liquidPhase.getPhase().getComponent(i).getVolumeCorrection()
                + liquidPhase.getPhase().getComponent(i).getVolumeCorrectionT()
                    * (liquidPhase.getPhase().getTemperature() - 288.15));
      }
    }
    // System.out.println("density correction tempvar " + tempVar);
    return 1.0 / (liquidPhase.getPhase().getMolarVolume() - tempVar)
        * liquidPhase.getPhase().getMolarMass() * 1.0e5;
  }
}
