/*
 * Density.java
 *
 * Created on 24. januar 2001, 19:49
 */

package neqsim.physicalproperties.physicalpropertymethods.gasphysicalproperties.density;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.physicalpropertymethods.gasphysicalproperties.GasPhysicalPropertyMethod;
import neqsim.physicalproperties.physicalpropertymethods.methodinterface.DensityInterface;
import neqsim.physicalproperties.physicalpropertysystem.PhysicalProperties;

/**
 * <p>
 * Density class for gases.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Density extends GasPhysicalPropertyMethod implements DensityInterface {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(Density.class);

  /**
   * <p>
   * Constructor for Density.
   * </p>
   *
   * @param gasPhase a {@link neqsim.physicalproperties.physicalpropertysystem.PhysicalProperties}
   *        object
   */
  public Density(PhysicalProperties gasPhase) {
    super(gasPhase);
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
    if (gasPhase.getPhase().useVolumeCorrection()) {
      for (int i = 0; i < gasPhase.getPhase().getNumberOfComponents(); i++) {
        tempVar += gasPhase.getPhase().getComponents()[i].getx()
            * (gasPhase.getPhase().getComponents()[i].getVolumeCorrection()
                + gasPhase.getPhase().getComponents()[i].getVolumeCorrectionT()
                    * (gasPhase.getPhase().getTemperature() - 288.15));
      }
    }
    // System.out.println("density correction tempvar " + tempVar);
    return 1.0 / (gasPhase.getPhase().getMolarVolume() - tempVar)
        * gasPhase.getPhase().getMolarMass() * 1.0e5;
  }
}
