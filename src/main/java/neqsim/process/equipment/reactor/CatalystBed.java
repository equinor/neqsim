package neqsim.process.equipment.reactor;

import java.io.Serializable;

/**
 * Catalyst bed properties for packed bed and plug flow reactors.
 *
 * <p>
 * Models the physical properties of a heterogeneous catalyst bed including particle geometry, void
 * fraction, bulk density, and transport properties. Provides calculations for bed pressure drop
 * (Ergun equation), catalyst effectiveness factor (Thiele modulus), and effective diffusivity.
 * </p>
 *
 * <p>
 * The Ergun equation for pressure drop through a packed bed:
 * </p>
 *
 * <p>
 * dP/dz = -(150 * mu * (1-eps)^2 * u) / (eps^3 * dp^2) - (1.75 * rho * (1-eps) * u^2) / (eps^3 *
 * dp)
 * </p>
 *
 * <p>
 * The effectiveness factor for a spherical catalyst pellet using the Thiele modulus:
 * </p>
 *
 * <p>
 * eta = (1/phi) * (1/tanh(3*phi) - 1/(3*phi))
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * CatalystBed catalyst = new CatalystBed();
 * catalyst.setParticleDiameter(3.0, "mm");
 * catalyst.setVoidFraction(0.40);
 * catalyst.setBulkDensity(800.0);
 * double dPdz = catalyst.calculatePressureDrop(1.5, 25.0, 1.8e-5);
 * </pre>
 *
 * @author esol
 * @version 1.0
 */
public class CatalystBed implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Catalyst particle diameter [m]. */
  private double particleDiameter = 0.003;

  /** Bed void fraction (porosity) [-]. Typical 0.35-0.50 for random packing. */
  private double voidFraction = 0.40;

  /** Catalyst bulk density [kg/m3]. Mass of catalyst per unit bed volume. */
  private double bulkDensity = 800.0;

  /** Catalyst particle density [kg/m3]. Density of individual particles. */
  private double particleDensity = 1200.0;

  /** Intra-particle porosity [-]. Fraction of particle volume that is pore. */
  private double particlePorosity = 0.50;

  /** Tortuosity factor [-]. Ratio of actual pore path length to straight-line distance. */
  private double tortuosity = 3.0;

  /** BET specific surface area [m2/kg]. */
  private double specificSurfaceArea = 150000.0;

  /** Catalyst activity factor [-]. 1.0 = fresh, decreases with deactivation. */
  private double activityFactor = 1.0;

  /**
   * Default constructor for CatalystBed.
   */
  public CatalystBed() {}

  /**
   * Constructor with particle diameter and void fraction.
   *
   * @param particleDiameterMm particle diameter in mm
   * @param voidFraction bed void fraction [-]
   * @param bulkDensityKgM3 bulk density in kg/m3
   */
  public CatalystBed(double particleDiameterMm, double voidFraction, double bulkDensityKgM3) {
    this.particleDiameter = particleDiameterMm / 1000.0;
    this.voidFraction = voidFraction;
    this.bulkDensity = bulkDensityKgM3;
  }

  /**
   * Calculate pressure drop per unit length using the Ergun equation.
   *
   * <p>
   * dP/dz = -(150 * mu * (1-eps)^2 * u) / (eps^3 * dp^2) - (1.75 * rho * (1-eps) * u^2) / (eps^3 *
   * dp)
   * </p>
   *
   * @param superficialVelocity gas superficial velocity [m/s]
   * @param gasDensity gas density [kg/m3]
   * @param gasViscosity gas dynamic viscosity [Pa*s]
   * @return pressure drop per unit length [Pa/m] (positive value representing pressure loss)
   */
  public double calculatePressureDrop(double superficialVelocity, double gasDensity,
      double gasViscosity) {
    double eps = voidFraction;
    double dp = particleDiameter;
    double u = Math.abs(superficialVelocity);

    double oneMinusEps = 1.0 - eps;
    double epsCubed = eps * eps * eps;

    // Viscous (Blake-Kozeny) term
    double viscousTerm =
        150.0 * gasViscosity * oneMinusEps * oneMinusEps * u / (dp * dp * epsCubed);

    // Inertial (Burke-Plummer) term
    double inertialTerm = 1.75 * gasDensity * oneMinusEps * u * u / (dp * epsCubed);

    return viscousTerm + inertialTerm;
  }

  /**
   * Calculate pressure drop across the full catalyst bed.
   *
   * @param superficialVelocity gas superficial velocity [m/s]
   * @param gasDensity gas density [kg/m3]
   * @param gasViscosity gas dynamic viscosity [Pa*s]
   * @param bedLength bed length [m]
   * @return total pressure drop [bar]
   */
  public double calculateTotalPressureDrop(double superficialVelocity, double gasDensity,
      double gasViscosity, double bedLength) {
    double dPdz = calculatePressureDrop(superficialVelocity, gasDensity, gasViscosity);
    return dPdz * bedLength / 1.0e5;
  }

  /**
   * Calculate the generalized Thiele modulus for a first-order reaction in a spherical pellet.
   *
   * <p>
   * phi = (R/3) * sqrt(k_v / D_eff) where R = particle radius, k_v = volumetric rate constant,
   * D_eff = effective diffusivity. The factor of 3 converts from radius-based to generalized Thiele
   * modulus for sphere.
   * </p>
   *
   * @param volumetricRateConstant first-order rate constant [1/s]
   * @param effectiveDiffusivity effective diffusivity [m2/s]
   * @return generalized Thiele modulus phi [-]
   */
  public double calculateThieleModulus(double volumetricRateConstant, double effectiveDiffusivity) {
    double radius = particleDiameter / 2.0;
    if (effectiveDiffusivity <= 0.0) {
      return 1.0e6;
    }
    return (radius / 3.0) * Math.sqrt(Math.abs(volumetricRateConstant) / effectiveDiffusivity);
  }

  /**
   * Calculate the internal effectiveness factor for a spherical catalyst pellet.
   *
   * <p>
   * eta = (1/phi) * [1/tanh(3*phi) - 1/(3*phi)]
   * </p>
   *
   * <p>
   * For small phi (phi &lt; 0.1): eta approaches 1.0 (no diffusion limitation). For large phi (phi
   * &gt; 10): eta approaches 1/(3*phi) (severe diffusion limitation).
   * </p>
   *
   * @param thieleModulus generalized Thiele modulus phi
   * @return effectiveness factor eta [0-1]
   */
  public double calculateEffectivenessFactor(double thieleModulus) {
    if (thieleModulus < 0.01) {
      return 1.0; // No diffusion limitation
    }
    if (thieleModulus > 500.0) {
      return 1.0 / (3.0 * thieleModulus); // Asymptotic limit
    }

    double threePhi = 3.0 * thieleModulus;
    double cothTerm = 1.0 / Math.tanh(threePhi);
    return (1.0 / thieleModulus) * (cothTerm - 1.0 / threePhi);
  }

  /**
   * Calculate the effective diffusivity inside catalyst pores.
   *
   * <p>
   * D_eff = D_molecular * epsilon_particle / tau
   * </p>
   *
   * @param molecularDiffusivity molecular diffusivity in free phase [m2/s]
   * @return effective diffusivity [m2/s]
   */
  public double getEffectiveDiffusivity(double molecularDiffusivity) {
    return molecularDiffusivity * particlePorosity / tortuosity;
  }

  /**
   * Calculate bed Reynolds number.
   *
   * <p>
   * Re_p = rho * u * dp / (mu * (1 - eps))
   * </p>
   *
   * @param superficialVelocity gas superficial velocity [m/s]
   * @param gasDensity gas density [kg/m3]
   * @param gasViscosity gas dynamic viscosity [Pa*s]
   * @return particle Reynolds number [-]
   */
  public double calculateReynoldsNumber(double superficialVelocity, double gasDensity,
      double gasViscosity) {
    return gasDensity * Math.abs(superficialVelocity) * particleDiameter
        / (gasViscosity * (1.0 - voidFraction));
  }

  /**
   * Set particle diameter.
   *
   * @param diameter particle diameter value
   * @param unit unit: "m", "mm", "cm", "in"
   */
  public void setParticleDiameter(double diameter, String unit) {
    if ("mm".equals(unit)) {
      this.particleDiameter = diameter / 1000.0;
    } else if ("cm".equals(unit)) {
      this.particleDiameter = diameter / 100.0;
    } else if ("in".equals(unit)) {
      this.particleDiameter = diameter * 0.0254;
    } else {
      this.particleDiameter = diameter;
    }
  }

  /**
   * Get particle diameter [m].
   *
   * @return particle diameter in meters
   */
  public double getParticleDiameter() {
    return particleDiameter;
  }

  /**
   * Set bed void fraction.
   *
   * @param voidFraction void fraction [-] (0 to 1)
   */
  public void setVoidFraction(double voidFraction) {
    this.voidFraction = voidFraction;
  }

  /**
   * Get bed void fraction.
   *
   * @return void fraction [-]
   */
  public double getVoidFraction() {
    return voidFraction;
  }

  /**
   * Set catalyst bulk density.
   *
   * @param bulkDensity bulk density [kg/m3]
   */
  public void setBulkDensity(double bulkDensity) {
    this.bulkDensity = bulkDensity;
  }

  /**
   * Get catalyst bulk density.
   *
   * @return bulk density [kg/m3]
   */
  public double getBulkDensity() {
    return bulkDensity;
  }

  /**
   * Set catalyst particle density.
   *
   * @param particleDensity particle density [kg/m3]
   */
  public void setParticleDensity(double particleDensity) {
    this.particleDensity = particleDensity;
  }

  /**
   * Get catalyst particle density.
   *
   * @return particle density [kg/m3]
   */
  public double getParticleDensity() {
    return particleDensity;
  }

  /**
   * Set intra-particle porosity.
   *
   * @param particlePorosity particle porosity [-] (0 to 1)
   */
  public void setParticlePorosity(double particlePorosity) {
    this.particlePorosity = particlePorosity;
  }

  /**
   * Get intra-particle porosity.
   *
   * @return particle porosity [-]
   */
  public double getParticlePorosity() {
    return particlePorosity;
  }

  /**
   * Set tortuosity factor.
   *
   * @param tortuosity tortuosity [-] (typically 2-6)
   */
  public void setTortuosity(double tortuosity) {
    this.tortuosity = tortuosity;
  }

  /**
   * Get tortuosity factor.
   *
   * @return tortuosity [-]
   */
  public double getTortuosity() {
    return tortuosity;
  }

  /**
   * Set BET specific surface area.
   *
   * @param area surface area value
   * @param unit unit: "m2/kg" or "m2/g"
   */
  public void setSpecificSurfaceArea(double area, String unit) {
    if ("m2/g".equals(unit)) {
      this.specificSurfaceArea = area * 1000.0;
    } else {
      this.specificSurfaceArea = area;
    }
  }

  /**
   * Get BET specific surface area [m2/kg].
   *
   * @return specific surface area [m2/kg]
   */
  public double getSpecificSurfaceArea() {
    return specificSurfaceArea;
  }

  /**
   * Set catalyst activity factor.
   *
   * @param activityFactor activity factor [-] (0 to 1, where 1 = fresh)
   */
  public void setActivityFactor(double activityFactor) {
    this.activityFactor = Math.max(0.0, Math.min(1.0, activityFactor));
  }

  /**
   * Get catalyst activity factor.
   *
   * @return activity factor [-]
   */
  public double getActivityFactor() {
    return activityFactor;
  }
}
