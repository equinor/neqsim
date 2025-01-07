/*
 * Density.java
 *
 * Created on 24. januar 2001, 19:49
 */

package neqsim.physicalproperties.methods.gasphysicalproperties.density;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.methods.gasphysicalproperties.GasPhysicalPropertyMethod;
import neqsim.physicalproperties.methods.methodinterface.DensityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * Density class for gases.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Density extends GasPhysicalPropertyMethod implements DensityInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Density.class);

  /**
   * <p>
   * Constructor for Density.
   * </p>
   *
   * @param gasPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
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
        tempVar += gasPhase.getPhase().getComponent(i).getx()
            * (gasPhase.getPhase().getComponent(i).getVolumeCorrection()
                + gasPhase.getPhase().getComponent(i).getVolumeCorrectionT()
                    * (gasPhase.getPhase().getTemperature() - 288.15));
      }
    }
    // System.out.println("density correction tempvar " + tempVar);
    return 1.0 / (gasPhase.getPhase().getMolarVolume() - tempVar)
        * gasPhase.getPhase().getMolarMass() * 1.0e5;
  }
}
