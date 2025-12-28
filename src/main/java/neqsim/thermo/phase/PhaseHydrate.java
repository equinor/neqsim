/*
 * PhaseHydrate.java
 *
 * Created on 18. august 2001, 12:50
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentHydrate;
import neqsim.thermo.component.ComponentHydrateGF;
import neqsim.thermo.component.ComponentHydratePVTsim;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;
import neqsim.thermo.mixingrule.MixingRulesInterface;

/**
 * <p>
 * PhaseHydrate class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class PhaseHydrate extends Phase {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  String hydrateModel = "PVTsimHydrateModel";

  /**
   * <p>
   * Constructor for PhaseHydrate.
   * </p>
   */
  public PhaseHydrate() {
    setType(PhaseType.HYDRATE);
  }

  /**
   * <p>
   * Constructor for PhaseHydrate.
   * </p>
   *
   * @param fluidModel a {@link java.lang.String} object
   */
  public PhaseHydrate(String fluidModel) {
    if (fluidModel.isEmpty()) {
      hydrateModel = "PVTsimHydrateModel";
    } else if (fluidModel.equals("CPAs-SRK-EOS-statoil") || fluidModel.equals("CPAs-SRK-EOS")
        || fluidModel.equals("CPA-SRK-EOS")) {
      hydrateModel = "CPAHydrateModel";
    } else {
      hydrateModel = "PVTsimHydrateModel";
    }
  }

  /** {@inheritDoc} */
  @Override
  public PhaseHydrate clone() {
    PhaseHydrate clonedPhase = null;
    try {
      clonedPhase = (PhaseHydrate) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    double sum = 1.0;
    int hydrateStructure = ((ComponentHydrate) getComponent(0)).getHydrateStructure();
    for (int j = 0; j < 2; j++) {
      for (int i = 0; i < numberOfComponents; i++) {
        sum += ((ComponentHydrate) getComponent(i)).getCavprwat(hydrateStructure, j)
            * ((ComponentHydrate) getComponent(i)).calcYKI(hydrateStructure, j, this);
      }
    }
    return sum / (((ComponentHydrate) getComponent(0)).getMolarVolumeHydrate(hydrateStructure,
        temperature));
    // return 1.0;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    // componentArray[compNumber] = new ComponentHydrateStatoil(name, moles, molesInPhase,
    // compNumber);
    if (hydrateModel.equals("CPAHydrateModel")) {
      componentArray[compNumber] = new ComponentHydrateGF(name, moles, molesInPhase, compNumber);
      // System.out.println("hydrate model: CPA-EoS hydrate model selected");
    } else {
      componentArray[compNumber] =
          new ComponentHydratePVTsim(name, moles, molesInPhase, compNumber);
      // System.out.println("hydrate model: standard PVTsim hydrate model selected");
    }
    // componentArray[compNumber] = new ComponentHydrateBallard(name, moles, molesInPhase,
    // compNumber);
    // componentArray[compNumber] = new ComponentHydratePVTsim(name, moles, molesInPhase,
    // compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    setType(PhaseType.HYDRATE);
  }

  /** {@inheritDoc} */
  @Override
  public MixingRulesInterface getMixingRule() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRuleGEModel(String name) {}

  /**
   * {@inheritDoc}
   *
   * <p>
   * Not relevant for PhaseHydrate
   * </p>
   */
  @Override
  public void resetMixingRule(MixingRuleTypeInterface mr) {}

  /**
   * {@inheritDoc}
   *
   * <p>
   * Not relevant for PhaseHydrate
   * </p>
   */
  @Override
  public void setMixingRule(MixingRuleTypeInterface mr) {}

  /**
   * <p>
   * setSolidRefFluidPhase.
   * </p>
   *
   * @param refPhase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void setSolidRefFluidPhase(PhaseInterface refPhase) {
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getName().equals("water")) {
        ((ComponentHydrate) componentArray[i]).setSolidRefFluidPhase(refPhase);
      }
    }
  }

  /**
   * Get the stable hydrate structure type.
   *
   * <p>
   * Returns the hydrate structure (1 for Structure I, 2 for Structure II) that is thermodynamically
   * stable at the current conditions. Structure I has small (5^12) and large (5^12 6^2) cavities,
   * while Structure II has small (5^12) and large (5^12 6^4) cavities.
   * </p>
   *
   * @return 1 for Structure I, 2 for Structure II
   */
  public int getStableHydrateStructure() {
    if (hasComponent("water")) {
      ComponentHydrate waterComp = (ComponentHydrate) getComponent("water");
      return waterComp.getHydrateStructure() + 1; // Structure is 0-indexed internally
    }
    return 1; // Default to Structure I
  }

  /**
   * Get the cavity occupancy for a specific component in a specific cavity type.
   *
   * <p>
   * Cavity occupancy (θ) represents the fraction of cavities of a given type that are occupied by a
   * specific guest molecule. Values range from 0 (empty) to 1 (fully occupied).
   * </p>
   *
   * @param componentName the name of the guest component
   * @param structure the hydrate structure (1 for Structure I, 2 for Structure II)
   * @param cavityType the cavity type (0 for small cavity, 1 for large cavity)
   * @return the cavity occupancy fraction (0 to 1), or 0 if component not found
   */
  public double getCavityOccupancy(String componentName, int structure, int cavityType) {
    if (hasComponent(componentName)) {
      ComponentHydrate comp = (ComponentHydrate) getComponent(componentName);
      return comp.calcYKI(structure - 1, cavityType, this);
    }
    return 0.0;
  }

  /**
   * Get the total cavity occupancy for small cavities.
   *
   * @param structure the hydrate structure (1 for Structure I, 2 for Structure II)
   * @return the total small cavity occupancy (sum of all guest occupancies)
   */
  public double getSmallCavityOccupancy(int structure) {
    double totalOccupancy = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].isHydrateFormer()) {
        totalOccupancy += ((ComponentHydrate) componentArray[i]).calcYKI(structure - 1, 0, this);
      }
    }
    return totalOccupancy;
  }

  /**
   * Get the total cavity occupancy for large cavities.
   *
   * @param structure the hydrate structure (1 for Structure I, 2 for Structure II)
   * @return the total large cavity occupancy (sum of all guest occupancies)
   */
  public double getLargeCavityOccupancy(int structure) {
    double totalOccupancy = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].isHydrateFormer()) {
        totalOccupancy += ((ComponentHydrate) componentArray[i]).calcYKI(structure - 1, 1, this);
      }
    }
    return totalOccupancy;
  }

  /**
   * Get the hydration number for the hydrate at current conditions.
   *
   * <p>
   * The hydration number is the ratio of water molecules to guest molecules in the hydrate. It
   * depends on the cavity occupancy and hydrate structure.
   * </p>
   *
   * @return the hydration number (water molecules per guest molecule)
   */
  public double getHydrationNumber() {
    int structure = getStableHydrateStructure();
    double smallOcc = getSmallCavityOccupancy(structure);
    double largeOcc = getLargeCavityOccupancy(structure);

    if (structure == 1) {
      // Structure I: 46 water, 2 small cavities, 6 large cavities per unit cell
      double totalGuests = 2.0 * smallOcc + 6.0 * largeOcc;
      return totalGuests > 0 ? 46.0 / totalGuests : Double.POSITIVE_INFINITY;
    } else {
      // Structure II: 136 water, 16 small cavities, 8 large cavities per unit cell
      double totalGuests = 16.0 * smallOcc + 8.0 * largeOcc;
      return totalGuests > 0 ? 136.0 / totalGuests : Double.POSITIVE_INFINITY;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getSoundSpeed() {
    // Gas hydrates have ice-like acoustic properties
    // Typical sound speed in gas hydrate: 3600-3900 m/s
    // Using bulk modulus of ~9 GPa and density ~910 kg/m3
    double density = getDensity("kg/m3");
    if (density <= 0 || Double.isNaN(density)) {
      density = 910.0; // Default methane hydrate density
    }
    double bulkModulus = 9.0e9; // Pa (9 GPa for gas hydrate)
    return Math.sqrt(bulkModulus / density);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For gas hydrates, returns a realistic density based on hydrate structure. Structure I methane
   * hydrate has a density of approximately 910 kg/m³, while Structure II hydrates are slightly
   * denser at approximately 940 kg/m³.
   * </p>
   */
  @Override
  public double getDensity() {
    int structure = getStableHydrateStructure();
    if (structure == 1) {
      // Structure I hydrate density: ~910 kg/m³
      return 910.0;
    } else {
      // Structure II hydrate density: ~940 kg/m³
      return 940.0;
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For gas hydrates, returns a realistic density based on hydrate structure with unit conversion.
   * </p>
   */
  @Override
  public double getDensity(String unit) {
    double refDensity = getDensity(); // density in kg/m3
    double conversionFactor = 1.0;
    switch (unit) {
      case "kg/m3":
        conversionFactor = 1.0;
        break;
      case "mol/m3":
        conversionFactor = 1.0 / getMolarMass();
        break;
      case "lb/ft3":
        conversionFactor = 0.0624279606;
        break;
      default:
        throw new RuntimeException("unit not supported " + unit);
    }
    return refDensity * conversionFactor;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For gas hydrates, viscosity is not a meaningful property since hydrates are crystalline solids.
   * Returns a very high value to indicate solid behavior (effectively infinite for flow
   * calculations).
   * </p>
   */
  @Override
  public double getViscosity() {
    // Hydrates are crystalline solids - return high viscosity to indicate no flow
    // Using 1e6 Pa.s (= 1e6 kg/m.s = 1e3 kg/(m.ms)) as typical for glacial ice
    return 1.0e6; // kg/(m.s) - very high indicating solid
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For gas hydrates, thermal conductivity is approximately 0.5-0.6 W/(m·K), similar to ice but
   * slightly lower due to the clathrate structure.
   * </p>
   */
  @Override
  public double getThermalConductivity() {
    // Gas hydrate thermal conductivity: ~0.5 W/(m·K)
    // Lower than ice (~2.2 W/m·K) due to cage structure scattering phonons
    return 0.5;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For gas hydrates, the residual enthalpy is approximated as zero since the PVT contribution for
   * incompressible solids is negligible.
   * </p>
   */
  @Override
  public double getHresTP() {
    return 0.0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For gas hydrates, the residual entropy is approximated as zero since the PVT contribution for
   * incompressible solids is negligible.
   * </p>
   */
  @Override
  public double getSresTP() {
    return 0.0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For gas hydrates, the residual heat capacity at constant pressure is approximated as zero.
   * </p>
   */
  @Override
  public double getCpres() {
    return 0.0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For gas hydrates, the residual heat capacity at constant volume is approximated as zero.
   * </p>
   */
  @Override
  public double getCvres() {
    return 0.0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For gas hydrates, the Joule-Thomson coefficient is approximated as zero since hydrates are
   * nearly incompressible solids.
   * </p>
   */
  @Override
  public double getJouleThomsonCoefficient() {
    return 0.0;
  }
}
