/*
 * PhaseSolid.java
 *
 * Created on 18. august 2001, 12:38
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentSolid;

/**
 * <p>
 * Abstract PhaseSolid class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public abstract class PhaseSolid extends PhaseSrkEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Flag to control whether EOS-based properties should be used instead of literature-based values.
   *
   * <p>
   * When true, thermodynamic properties (density, entropy, enthalpy, heat capacities) are
   * calculated using the underlying equation of state (SRK EOS). This is useful for Pedersen's
   * approach where asphaltene is modeled as a heavy liquid phase with EOS-calculable properties.
   * </p>
   *
   * <p>
   * When false (default), solid-specific literature values are used for properties like density
   * (e.g., 1150 kg/m³ for asphaltene), and residual properties return 0 as is typical for solids.
   * </p>
   */
  private boolean useEosProperties = false;

  /**
   * <p>
   * Constructor for PhaseSolid.
   * </p>
   */
  public PhaseSolid() {
    setType(PhaseType.SOLID);
    calcMolarVolume = false;
  }

  /** {@inheritDoc} */
  @Override
  public PhaseSolid clone() {
    PhaseSolid clonedPhase = null;
    try {
      clonedPhase = (PhaseSolid) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    try {
      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
      getDensityTemp();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }
    // Set phase type based on composition: ASPHALTENE if > 50% asphaltene, else SOLID
    if (isAsphaltenePhase()) {
      setType(PhaseType.ASPHALTENE);
    } else {
      setType(PhaseType.SOLID);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentSolid(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public double getEnthalpy() {
    double fusionHeat = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      fusionHeat += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getHeatOfFusion();
    }
    return super.getEnthalpy() - fusionHeat;
  }

  /**
   * <p>
   * setSolidRefFluidPhase.
   * </p>
   *
   * @param refPhase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void setSolidRefFluidPhase(PhaseInterface refPhase) {
    for (int i = 0; i < numberOfComponents; i++) {
      ((ComponentSolid) componentArray[i]).setSolidRefFluidPhase(refPhase);
    }
  }

  /**
   * Get density of a phase note: at the moment return density of water (997 kg/m3).
   *
   * @return density with unit kg/m3
   */
  public double getDensityTemp() {
    double density = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      density += getWtFrac(i)
          * ((ComponentSolid) componentArray[i]).getPureComponentSolidDensity(getTemperature())
          * 1000.0;
    }
    molarVolume = density / getMolarMass() * 1e-5;
    return density;
  }

  /**
   * Checks if this solid phase is predominantly asphaltene (&gt; 50 mol% asphaltene).
   *
   * @return true if phase contains majority asphaltene
   */
  public boolean isAsphaltenePhase() {
    double asphalteneTotal = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      String name = getComponent(i).getComponentName().toLowerCase();
      if (name.contains("asphaltene")) {
        asphalteneTotal += getComponent(i).getx();
      }
    }
    return asphalteneTotal > 0.5;
  }

  /**
   * Updates the phase type to ASPHALTENE if this phase is predominantly asphaltene. Call this after
   * flash calculations to properly identify asphaltene-rich solid phases.
   */
  public void updatePhaseTypeForAsphaltene() {
    if (isAsphaltenePhase()) {
      setType(PhaseType.ASPHALTENE);
    }
  }

  /**
   * Get asphaltene density based on literature values. Asphaltene density is typically 1100-1200
   * kg/m³.
   *
   * @return asphaltene density in kg/m3
   */
  private double getAsphaltenePhaseAltDensity() {
    double density = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      String compName = getComponent(i).getComponentName().toLowerCase();
      double compDensity;

      // For asphaltene, use literature values since database values are unreliable
      if (compName.contains("asphaltene")) {
        compDensity = 1150.0; // Typical asphaltene density kg/m3 (literature: 1100-1200)
      } else if (compName.contains("resin")) {
        compDensity = 1080.0; // Typical resin density kg/m3
      } else {
        // Try database value for other components
        compDensity =
            ((ComponentSolid) componentArray[i]).getPureComponentSolidDensity(getTemperature())
                * 1000.0;

        // Check for invalid values
        if (compDensity <= 0.0 || Double.isNaN(compDensity) || compDensity > 2000.0
            || compDensity < 500.0) {
          compDensity = 1100.0; // Default
        }
      }
      density += getWtFrac(i) * compDensity;
    }
    // Ensure positive and realistic density
    if (density <= 500.0 || density > 2000.0 || Double.isNaN(density)) {
      density = 1150.0; // Default asphaltene density
    }
    return density;
  }

  /**
   * Check if EOS-based properties are used for this solid phase.
   *
   * @return true if EOS properties are used, false if literature-based values are used
   */
  public boolean isUseEosProperties() {
    return useEosProperties;
  }

  /**
   * Set whether to use EOS-based properties for this solid phase.
   *
   * <p>
   * When enabled, thermodynamic properties (density, entropy, enthalpy, heat capacities) are
   * calculated using the underlying equation of state. This is useful for Pedersen's approach where
   * asphaltene behaves more like a heavy liquid phase.
   * </p>
   *
   * @param useEosProperties true to use EOS properties, false to use literature-based values
   */
  public void setUseEosProperties(boolean useEosProperties) {
    this.useEosProperties = useEosProperties;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For solids, the residual entropy is approximated as zero since there is no PVT contribution for
   * incompressible solids at their reference state. When {@link #isUseEosProperties()} is true,
   * EOS-based calculation is used instead.
   * </p>
   */
  @Override
  public double getSresTP() {
    if (useEosProperties) {
      return super.getSresTP();
    }
    return 0.0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For solids, the residual enthalpy is approximated as zero since the PVT contribution for
   * incompressible solids is negligible. When {@link #isUseEosProperties()} is true, EOS-based
   * calculation is used instead.
   * </p>
   */
  @Override
  public double getHresTP() {
    if (useEosProperties) {
      return super.getHresTP();
    }
    return 0.0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For solids, the residual heat capacity at constant pressure is approximated as zero. When
   * {@link #isUseEosProperties()} is true, EOS-based calculation is used instead.
   * </p>
   */
  @Override
  public double getCpres() {
    if (useEosProperties) {
      return super.getCpres();
    }
    return 0.0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For solids, the residual heat capacity at constant volume is approximated as zero. When
   * {@link #isUseEosProperties()} is true, EOS-based calculation is used instead.
   * </p>
   */
  @Override
  public double getCvres() {
    if (useEosProperties) {
      return super.getCvres();
    }
    return 0.0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For solids, the heat capacity is taken as the sum of component heat capacities.
   * </p>
   */
  @Override
  public double getCp() {
    return getCp0() * numberOfMolesInPhase;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For solids, Cv is approximately equal to Cp since solids are nearly incompressible.
   * </p>
   */
  @Override
  public double getCv() {
    return getCp();
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Speed of sound in solids is typically 1500-4000 m/s for organic solids.
   * </p>
   */
  @Override
  public double getSoundSpeed() {
    double density = getDensity("kg/m3");
    if (density <= 0 || Double.isNaN(density)) {
      density = 1100.0;
    }

    // Bulk modulus for organic solids: typically 2-6 GPa
    double bulkModulus;
    if (isAsphaltenePhase()) {
      bulkModulus = 3.5e9; // Pa (3.5 GPa for asphaltene)
    } else if (getType() == PhaseType.HYDRATE) {
      bulkModulus = 9.0e9; // Pa (9 GPa for gas hydrate, ice-like)
    } else if (getType() == PhaseType.WAX) {
      bulkModulus = 2.5e9; // Pa (2.5 GPa for paraffin wax)
    } else {
      bulkModulus = 3.0e9; // Pa (default for organic solids)
    }

    return Math.sqrt(bulkModulus / density);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For solids, the Joule-Thomson coefficient is typically very small since solids are nearly
   * incompressible. When {@link #isUseEosProperties()} is true, EOS-based calculation is used
   * instead.
   * </p>
   */
  @Override
  public double getJouleThomsonCoefficient() {
    if (useEosProperties) {
      return super.getJouleThomsonCoefficient();
    }
    return 0.0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For asphaltene phases, returns a realistic density based on literature values. For other solid
   * phases, uses the standard EOS-based calculation. When {@link #isUseEosProperties()} is true,
   * always uses EOS-based calculation regardless of phase type.
   * </p>
   */
  @Override
  public double getDensity() {
    // When EOS properties are enabled, always use EOS calculation
    if (useEosProperties) {
      return super.getDensity();
    }
    // For asphaltene phases, use literature-based density since EOS gives unrealistic values
    if (isAsphaltenePhase()) {
      return getAsphaltenePhaseAltDensity();
    }
    // For other solid phases (wax, hydrate), use the standard calculation
    return super.getDensity();
  }
}
