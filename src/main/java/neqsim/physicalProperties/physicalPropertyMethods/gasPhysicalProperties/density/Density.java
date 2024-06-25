/*
 * Density.java
 *
 * Created on 24. januar 2001, 19:49
 */

package neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.density;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.GasPhysicalPropertyMethod;

/**
 * <p>
 * Density class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Density extends GasPhysicalPropertyMethod
    implements neqsim.physicalProperties.physicalPropertyMethods.methodInterface.DensityInterface {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(Density.class);

  /**
   * <p>
   * Constructor for Density.
   * </p>
   */
  public Density() {}

  /**
   * <p>
   * Constructor for Density.
   * </p>
   *
   * @param gasPhase a
   *        {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface}
   *        object
   */
  public Density(
      neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface gasPhase) {
    this.gasPhase = gasPhase;
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

  /**
   * {@inheritDoc}
   *
   * Returns the density of the phase. Unit: kg/m^3
   */
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
