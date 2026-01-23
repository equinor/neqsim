package neqsim.physicalproperties.methods.solidphysicalproperties.viscosity;

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

  /**
   * {@inheritDoc}
   *
   * <p>
   * For solids, viscosity is not well-defined in the traditional sense. However, for modeling
   * purposes (e.g., flow of slurries or deposits), we return a very high effective viscosity. For
   * asphaltene and similar organic solids near their softening point, viscosity can be on the order
   * of 1e3 to 1e12 Pa·s depending on temperature.
   * </p>
   */
  @Override
  public double calcViscosity() {
    // Solids don't have viscosity in the fluid sense, but for modeling
    // purposes we return a very high value (similar to bitumen/asphalt)
    // Use temperature-dependent correlation for semi-solid behavior
    double temperature = solidPhase.getPhase().getTemperature(); // K

    // Arrhenius-type temperature dependence for viscosity
    // Reference: bitumen/asphaltene viscosity ~1e4 Pa·s at 350K, ~1e2 Pa·s at 450K
    double Tref = 350.0; // K
    double viscRef = 1.0e4; // Pa·s at Tref
    double activationEnergy = 50000.0; // J/mol (typical for heavy organics)
    double R = 8.314; // J/(mol·K)

    double viscosity = viscRef * Math.exp(activationEnergy / R * (1.0 / temperature - 1.0 / Tref));

    // Clamp to reasonable range: 1e2 to 1e10 Pa·s
    viscosity = Math.max(1.0e2, Math.min(1.0e10, viscosity));

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
