package neqsim.physicalproperties.methods.solidphysicalproperties.viscosity;

import static java.lang.Double.NaN;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.methods.methodinterface.ViscosityInterface;
import neqsim.physicalproperties.methods.solidphysicalproperties.SolidPhysicalPropertyMethod;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * Viscosity class.
 * </p>
 *
 * @author Even Solbraa
 * @version Method was checked on 2.8.2001 - seems to be correct - Even Solbraa
 */
public class Viscosity extends SolidPhysicalPropertyMethod implements ViscosityInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Viscosity.class);

  public double[] pureComponentViscosity;

  /**
   * <p>
   * Constructor for Viscosity.
   * </p>
   *
   * @param solidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public Viscosity(PhysicalProperties solidPhase) {
    super(solidPhase);
  }

  /** {@inheritDoc} */
  @Override
  public Viscosity clone() {
    Viscosity properties = null;

    try {
      properties = (Viscosity) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return properties;
  }

  /** {@inheritDoc} */
  @Override
  public double calcViscosity() {
    double viscosity = NaN;
    return viscosity;
  }

  /**
   * <p>
   * calcPureComponentViscosity.
   * </p>
   */
  public void calcPureComponentViscosity() {}

  /** {@inheritDoc} */
  @Override
  public double getPureComponentViscosity(int i) {
    return pureComponentViscosity[i];
  }

  /**
   * <p>
   * getViscosityPressureCorrection.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getViscosityPressureCorrection(int i) {
    return 0.0;
  }
}
