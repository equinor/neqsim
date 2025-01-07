/*
 * Density.java
 *
 * Created on 24. januar 2001, 19:49
 */

package neqsim.physicalproperties.methods.solidphysicalproperties.density;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.methods.methodinterface.DensityInterface;
import neqsim.physicalproperties.methods.solidphysicalproperties.SolidPhysicalPropertyMethod;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * Density class for solids.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Density extends SolidPhysicalPropertyMethod implements DensityInterface {
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
    if (solidPhase.getPhase().useVolumeCorrection()) {
      for (int i = 0; i < solidPhase.getPhase().getNumberOfComponents(); i++) {
        tempVar += solidPhase.getPhase().getComponent(i).getx()
            * (solidPhase.getPhase().getComponent(i).getVolumeCorrection()
                + solidPhase.getPhase().getComponent(i).getVolumeCorrectionT()
                    * (solidPhase.getPhase().getTemperature() - 288.15));
      }
    }
    // System.out.println("density correction tempvar " + tempVar);
    return 1.0 / (solidPhase.getPhase().getMolarVolume() - tempVar)
        * solidPhase.getPhase().getMolarMass() * 1e5;
  }
}
