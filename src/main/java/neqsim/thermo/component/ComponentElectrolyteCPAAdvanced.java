package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.util.constants.IonParametersAdvanced;

/**
 * Component class for the e-CPA-Advanced electrolyte equation of state.
 *
 * <p>
 * This class extends {@link ComponentElectrolyteCPAstatoil} to use ion-specific,
 * temperature-dependent Born radii from {@link IonParametersAdvanced} instead of the default
 * Lennard-Jones diameter for the Born solvation contribution. The key modification is in the
 * calculation of XBorni, which determines the Born contribution to the chemical potential.
 * </p>
 *
 * <p>
 * In the parent model, the Born parameter is:
 * </p>
 *
 * $$ X_{Born,i} = \frac{z_i^2}{\sigma_i} $$
 *
 * <p>
 * where sigma is the Lennard-Jones diameter. In this advanced model:
 * </p>
 *
 * $$ X_{Born,i} = \frac{z_i^2}{2 \cdot r_{Born,i}(T)} $$
 *
 * <p>
 * where r_Born is a temperature-dependent Born cavity radius fitted to experimental activity
 * coefficient data. The factor of 2 arises because the parent's Born energy prefactor uses
 * 1/(4*pi*eps0) with diameter, while the standard Born equation uses 1/(8*pi*eps0) with radius.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentElectrolyteCPAAdvanced extends ComponentElectrolyteCPAstatoil {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Whether this ion has advanced Born parameters in IonParametersAdvanced.
   */
  private boolean hasAdvancedParams = false;

  /**
   * Constructor for ComponentElectrolyteCPAAdvanced.
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentElectrolyteCPAAdvanced(String name, double moles, double molesInPhase,
      int compIndex) {
    super(name, moles, molesInPhase, compIndex);
    hasAdvancedParams = IonParametersAdvanced.hasIonData(name);
  }

  /**
   * Constructor for ComponentElectrolyteCPAAdvanced.
   *
   * @param number a int. Not used.
   * @param TC Critical temperature [K]
   * @param PC Critical pressure [bara]
   * @param M Molar mass
   * @param a Acentric factor
   * @param moles Total number of moles of component.
   */
  public ComponentElectrolyteCPAAdvanced(int number, double TC, double PC, double M, double a,
      double moles) {
    super(number, TC, PC, M, a, moles);
  }

  /** {@inheritDoc} */
  @Override
  public ComponentElectrolyteCPAAdvanced clone() {
    ComponentElectrolyteCPAAdvanced clonedComponent = null;
    try {
      clonedComponent = (ComponentElectrolyteCPAAdvanced) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedComponent;
  }

  /** {@inheritDoc} */
  @Override
  public void Finit(PhaseInterface phase, double temp, double pres, double totMoles, double beta,
      int numberOfComponents, int initType) {
    // Let parent compute everything (Wi, WiT, alphai, alphaiV, XLRi, XBorni, etc.)
    super.Finit(phase, temp, pres, totMoles, beta, numberOfComponents, initType);

    // Override XBorni with advanced Born radii for ions that have parameters
    if (hasAdvancedParams && ionicCharge != 0) {
      double rBorn = IonParametersAdvanced.calcBornRadius(getComponentName(), temp);
      if (rBorn > 0.0) {
        // XBorni = z^2 / (2 * rBorn_meters)
        // Factor 2 converts from 1/(8*pi*eps0*rBorn) convention to parent's
        // 1/(4*pi*eps0*sigma) convention
        XBorni = (double) (ionicCharge * ionicCharge) / (2.0 * rBorn * 1e-10);
      }
    }
  }
}
