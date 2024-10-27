/*
 * Conductivity.java
 *
 * Created on 1. november 2000, 19:00
 */

package neqsim.physicalproperties.physicalpropertymethods.solidphysicalproperties.conductivity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.physicalpropertymethods.methodinterface.ConductivityInterface;
import neqsim.physicalproperties.physicalpropertymethods.solidphysicalproperties.SolidPhysicalPropertyMethod;
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
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(Conductivity.class);

  double conductivity = 0;

  /**
   * <p>
   * Constructor for Conductivity.
   * </p>
   *
   * @param solidPhase a
   *        {@link neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface}
   *        object
   */
  public Conductivity(
      neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface solidPhase) {
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

  /** {@inheritDoc} */
  @Override
  public double calcConductivity() {
    // using default value of parafin wax
    if (solidPhase.getPhase().getType() == PhaseType.WAX) {
      conductivity = 0.25;
    } else {
      conductivity = 2.18;
    }

    return conductivity;
  }
}
