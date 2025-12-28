/*
 * Conductivity.java
 *
 * Created on 1. november 2000, 19:00
 */

package neqsim.physicalproperties.methods.solidphysicalproperties.conductivity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.methods.methodinterface.ConductivityInterface;
import neqsim.physicalproperties.methods.solidphysicalproperties.SolidPhysicalPropertyMethod;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.phase.PhaseType;

/**
 * <p>
 * Conductivity class for solids.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Conductivity extends SolidPhysicalPropertyMethod implements ConductivityInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Conductivity.class);

  double conductivity = 0;

  /**
   * <p>
   * Constructor for Conductivity.
   * </p>
   *
   * @param solidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public Conductivity(PhysicalProperties solidPhase) {
    super(solidPhase);
  }

  /** {@inheritDoc} */
  @Override
  public Conductivity clone() {
    Conductivity properties = null;

    try {
      properties = (Conductivity) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return properties;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Thermal conductivity of organic solids is typically 0.15-0.35 W/mK. Values based on: -
   * Asphaltene: ~0.17-0.22 W/mK (similar to bitumen) - Wax/Paraffin: ~0.20-0.25 W/mK - Hydrate:
   * ~0.50-0.60 W/mK (ice-like structure)
   * </p>
   */
  @Override
  public double calcConductivity() {
    PhaseType phaseType = solidPhase.getPhase().getType();

    if (phaseType == PhaseType.WAX) {
      // Paraffin wax thermal conductivity
      conductivity = 0.25;
    } else if (phaseType == PhaseType.HYDRATE) {
      // Gas hydrate thermal conductivity (ice-like)
      conductivity = 0.55;
    } else if (phaseType == PhaseType.ASPHALTENE) {
      // Asphaltene thermal conductivity (similar to bitumen)
      conductivity = 0.20;
    } else {
      // Default for generic solids
      conductivity = 0.25;
    }

    return conductivity;
  }
}
