/*
 * Density.java
 *
 * Created on 24. januar 2001, 19:49
 */

package neqsim.physicalproperties.physicalpropertymethods.liquidphysicalproperties.density;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.physicalpropertymethods.liquidphysicalproperties.LiquidPhysicalPropertyMethod;
import neqsim.physicalproperties.physicalpropertymethods.methodinterface.DensityInterface;
import neqsim.physicalproperties.physicalpropertysystem.PhysicalProperties;

/**
 * <p>
 * Density class foir liquids.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Density extends LiquidPhysicalPropertyMethod implements DensityInterface {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(Density.class);

  /**
   * <p>
   * Constructor for Density.
   * </p>
   *
   * @param liquidPhase a
   *        {@link neqsim.physicalproperties.physicalpropertysystem.PhysicalProperties} object
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
        tempVar += liquidPhase.getPhase().getComponents()[i].getx()
            * (liquidPhase.getPhase().getComponents()[i].getVolumeCorrection()
                + liquidPhase.getPhase().getComponents()[i].getVolumeCorrectionT()
                    * (liquidPhase.getPhase().getTemperature() - 288.15));
      }
    }
    // System.out.println("density correction tempvar " + tempVar);
    return 1.0 / (liquidPhase.getPhase().getMolarVolume() - tempVar)
        * liquidPhase.getPhase().getMolarMass() * 1.0e5;
  }
}
