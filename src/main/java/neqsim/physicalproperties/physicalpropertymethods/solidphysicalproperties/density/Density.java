/*
 * Density.java
 *
 * Created on 24. januar 2001, 19:49
 */

package neqsim.physicalproperties.physicalpropertymethods.solidphysicalproperties.density;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * Density class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Density extends
    neqsim.physicalproperties.physicalpropertymethods.solidphysicalproperties.SolidPhysicalPropertyMethod
    implements neqsim.physicalproperties.physicalpropertymethods.methodinterface.DensityInterface {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(Density.class);

  /**
   * <p>
   * Constructor for Density.
   * </p>
   *
   * @param liquidPhase a
   *        {@link neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface}
   *        object
   */
  public Density(
      neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface liquidPhase) {
    this.solidPhase = liquidPhase;
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
        tempVar += solidPhase.getPhase().getComponents()[i].getx()
            * (solidPhase.getPhase().getComponents()[i].getVolumeCorrection()
                + solidPhase.getPhase().getComponents()[i].getVolumeCorrectionT()
                    * (solidPhase.getPhase().getTemperature() - 288.15));
      }
    }
    // System.out.println("density correction tempvar " + tempVar);
    return 1.0 / (solidPhase.getPhase().getMolarVolume() - tempVar)
        * solidPhase.getPhase().getMolarMass() * 1e5;
  }
}
