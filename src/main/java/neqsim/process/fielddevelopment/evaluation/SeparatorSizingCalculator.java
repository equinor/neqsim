package neqsim.process.fielddevelopment.evaluation;

import java.io.Serializable;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign;
import neqsim.thermo.system.SystemInterface;

/**
 * Separator sizing calculator aligned with TPG4230 course material.
 *
 * <p>
 * Implements industry-standard separator sizing methods including:
 * </p>
 * <ul>
 * <li><b>API 12J</b> - Liquid retention time based on oil density</li>
 * <li><b>Stokes Law</b> - Droplet/bubble settling velocity</li>
 * <li><b>Souders-Brown</b> - Maximum gas velocity (K-factor method)</li>
 * <li><b>GPSA</b> - General sizing guidelines</li>
 * </ul>
 *
 * <h2>Key Equations</h2>
 *
 * <p>
 * <b>Stokes Settling Velocity:</b>
 * </p>
 * 
 * <pre>
 * v_s = g × d² × (ρ_L - ρ_G) / (18 × μ)
 * </pre>
 *
 * <p>
 * <b>Souders-Brown Gas Velocity:</b>
 * </p>
 * 
 * <pre>
 * V_max = K × sqrt((ρ_L - ρ_G) / ρ_G)
 * </pre>
 *
 * <p>
 * <b>Residence Time Criterion:</b>
 * </p>
 * 
 * <pre>
 * t_residence &gt; t_separation
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 * @see SeparatorMechanicalDesign
 */
public class SeparatorSizingCalculator implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Gravitational acceleration (m/s²). */
  private static final double GRAVITY = 9.81;

  // ============================================================================
  // ENUMS
  // ============================================================================

  /**
   * Separator orientation type.
   */
  public enum SeparatorType {
    /** Horizontal separator - preferred for high liquid loading. */
    HORIZONTAL,
    /** Vertical separator - preferred for high GOR, limited footprint. */
    VERTICAL,
    /** Spherical separator - compact, high-pressure applications. */
    SPHERICAL
  }

  /**
   * Design standard to apply.
   */
  public enum DesignStandard {
    /** API 12J - Specification for Oil and Gas Separators. */
    API_12J,
    /** GPSA Engineering Data Book guidelines. */
    GPSA,
    /** Shell DEP standards. */
    SHELL_DEP
  }

  // ============================================================================
  // API 12J RETENTION TIME
  // ============================================================================

  /**
   * Returns minimum liquid retention time per API 12J.
   *
   * <p>
   * API 12J recommends retention times based on oil API gravity/density:
   * </p>
   * <table border="1">
   * <caption>API 12J Retention Times</caption>
   * <tr>
   * <th>Oil Density (kg/m³)</th>
   * <th>API Gravity</th>
   * <th>Retention Time (s)</th>
   * </tr>
   * <tr>
   * <td>&lt; 850</td>
   * <td>&gt; 35°</td>
   * <td>60</td>
   * </tr>
   * <tr>
   * <td>850-930</td>
   * <td>20-35°</td>
   * <td>120</td>
   * </tr>
   * <tr>
   * <td>&gt; 930</td>
   * <td>&lt; 20°</td>
   * <td>180</td>
   * </tr>
   * </table>
   *
   * @param oilDensityKgM3 oil density in kg/m³
   * @return minimum retention time in seconds
   */
  public double getAPI12JRetentionTime(double oilDensityKgM3) {
    if (oilDensityKgM3 < 850.0) {
      return 60.0; // Light oil (API > 35)
    } else if (oilDensityKgM3 <= 930.0) {
      return 120.0; // Medium oil (API 20-35)
    } else {
      return 180.0; // Heavy oil (API < 20)
    }
  }

  /**
   * Returns retention time based on API gravity.
   *
   * @param apiGravity API gravity (dimensionless)
   * @return minimum retention time in seconds
   */
  public double getAPI12JRetentionTimeFromAPI(double apiGravity) {
    // Convert API to density: ρ = 141.5 / (API + 131.5) * 1000
    double densityKgM3 = 141.5 / (apiGravity + 131.5) * 1000.0;
    return getAPI12JRetentionTime(densityKgM3);
  }

  // ============================================================================
  // STOKES SETTLING
  // ============================================================================

  /**
   * Calculates droplet/bubble settling velocity using Stokes Law.
   *
   * <p>
   * Stokes Law applies to laminar flow around spherical particles (Re &lt; 1):
   * </p>
   * 
   * <pre>
   * v_s = g × d² × (ρ_heavy - ρ_light) / (18 × μ)
   * </pre>
   *
   * <p>
   * where:
   * </p>
   * <ul>
   * <li>g = gravitational acceleration (9.81 m/s²)</li>
   * <li>d = droplet/bubble diameter (m)</li>
   * <li>ρ_heavy = density of heavier phase (kg/m³)</li>
   * <li>ρ_light = density of lighter phase (kg/m³)</li>
   * <li>μ = viscosity of continuous phase (Pa·s)</li>
   * </ul>
   *
   * @param dropletDiameterM droplet or bubble diameter (m)
   * @param heavyPhaseDensity density of heavier phase (kg/m³)
   * @param lightPhaseDensity density of lighter phase (kg/m³)
   * @param continuousPhaseViscosity viscosity of continuous phase (Pa·s)
   * @return settling velocity (m/s), positive = downward for droplets
   */
  public double stokesSettlingVelocity(double dropletDiameterM, double heavyPhaseDensity,
      double lightPhaseDensity, double continuousPhaseViscosity) {
    if (continuousPhaseViscosity <= 0) {
      throw new IllegalArgumentException("Viscosity must be positive");
    }
    if (dropletDiameterM <= 0) {
      return 0.0;
    }

    double densityDiff = heavyPhaseDensity - lightPhaseDensity;
    double d2 = dropletDiameterM * dropletDiameterM;
    return GRAVITY * d2 * densityDiff / (18.0 * continuousPhaseViscosity);
  }

  /**
   * Calculates oil droplet settling velocity in gas phase.
   *
   * @param dropletDiameterMicrons droplet diameter in microns
   * @param oilDensity oil density (kg/m³)
   * @param gasDensity gas density (kg/m³)
   * @param gasViscosity gas viscosity (Pa·s)
   * @return settling velocity (m/s)
   */
  public double oilDropletSettlingInGas(double dropletDiameterMicrons, double oilDensity,
      double gasDensity, double gasViscosity) {
    double diameterM = dropletDiameterMicrons * 1e-6;
    return stokesSettlingVelocity(diameterM, oilDensity, gasDensity, gasViscosity);
  }

  /**
   * Calculates gas bubble rise velocity in liquid phase.
   *
   * @param bubbleDiameterMm bubble diameter in mm
   * @param liquidDensity liquid density (kg/m³)
   * @param gasDensity gas density (kg/m³)
   * @param liquidViscosity liquid viscosity (Pa·s)
   * @return rise velocity (m/s)
   */
  public double gasBubbleRiseInLiquid(double bubbleDiameterMm, double liquidDensity,
      double gasDensity, double liquidViscosity) {
    double diameterM = bubbleDiameterMm * 1e-3;
    return stokesSettlingVelocity(diameterM, liquidDensity, gasDensity, liquidViscosity);
  }

  /**
   * Calculates separation time for a droplet to settle a given distance.
   *
   * @param settlingDistance vertical distance to settle (m)
   * @param settlingVelocity settling velocity from Stokes (m/s)
   * @return separation time (s)
   */
  public double separationTime(double settlingDistance, double settlingVelocity) {
    if (settlingVelocity <= 0) {
      return Double.POSITIVE_INFINITY;
    }
    return settlingDistance / settlingVelocity;
  }

  // ============================================================================
  // SOUDERS-BROWN GAS VELOCITY
  // ============================================================================

  /**
   * Calculates maximum gas velocity using Souders-Brown equation.
   *
   * <p>
   * The Souders-Brown equation determines the maximum allowable gas velocity to prevent liquid
   * entrainment:
   * </p>
   * 
   * <pre>
   * V_max = K × sqrt((ρ_L - ρ_G) / ρ_G)
   * </pre>
   *
   * <p>
   * where K is the Souders-Brown coefficient (m/s), typically:
   * </p>
   * <ul>
   * <li>0.04-0.05 m/s for vertical separators without demister</li>
   * <li>0.06-0.12 m/s for horizontal separators</li>
   * <li>0.10-0.15 m/s for separators with wire mesh demister</li>
   * </ul>
   *
   * @param kFactor Souders-Brown K factor (m/s)
   * @param liquidDensity liquid density (kg/m³)
   * @param gasDensity gas density (kg/m³)
   * @return maximum gas velocity (m/s)
   */
  public double soudersbrownGasVelocity(double kFactor, double liquidDensity, double gasDensity) {
    if (gasDensity <= 0) {
      throw new IllegalArgumentException("Gas density must be positive");
    }
    double densityRatio = (liquidDensity - gasDensity) / gasDensity;
    if (densityRatio <= 0) {
      return 0.0;
    }
    return kFactor * Math.sqrt(densityRatio);
  }

  /**
   * Returns recommended K-factor based on separator configuration.
   *
   * @param type separator type
   * @param hasDemister true if wire mesh demister is installed
   * @return recommended K-factor (m/s)
   */
  public double getRecommendedKFactor(SeparatorType type, boolean hasDemister) {
    if (hasDemister) {
      return (type == SeparatorType.VERTICAL) ? 0.107 : 0.122;
    } else {
      return (type == SeparatorType.VERTICAL) ? 0.046 : 0.076;
    }
  }

  // ============================================================================
  // SEPARATOR SIZING
  // ============================================================================

  /**
   * Sizes a separator for the given feed stream.
   *
   * <p>
   * This method calculates the required separator dimensions based on:
   * </p>
   * <ul>
   * <li>Gas capacity constraint (Souders-Brown)</li>
   * <li>Liquid retention time (API 12J)</li>
   * <li>Slenderness ratio limits (L/D)</li>
   * </ul>
   *
   * @param feed inlet stream to separator
   * @param type separator orientation
   * @param standard design standard to apply
   * @return sizing result with dimensions and constraints
   */
  public SeparatorSizingResult sizeSeparator(StreamInterface feed, SeparatorType type,
      DesignStandard standard) {
    // Run the feed to get properties
    if (feed.getFluid() == null) {
      throw new IllegalArgumentException("Feed stream must have a fluid");
    }

    SystemInterface fluid = feed.getFluid();

    // Get phase properties
    double gasDensity = 0.0;
    double liquidDensity = 0.0;
    double gasViscosity = 0.0;
    double liquidViscosity = 0.0;
    double gasVolumeFlow = 0.0; // m³/s
    double liquidVolumeFlow = 0.0; // m³/s

    if (fluid.hasPhaseType("gas")) {
      gasDensity = fluid.getPhase("gas").getDensity("kg/m3");
      gasViscosity = fluid.getPhase("gas").getViscosity("kg/msec");
      gasVolumeFlow = fluid.getPhase("gas").getVolume("m3") / 1.0; // per second at conditions
    }

    if (fluid.hasPhaseType("oil")) {
      liquidDensity = fluid.getPhase("oil").getDensity("kg/m3");
      liquidViscosity = fluid.getPhase("oil").getViscosity("kg/msec");
      liquidVolumeFlow = fluid.getPhase("oil").getVolume("m3") / 1.0;
    } else if (fluid.hasPhaseType("aqueous")) {
      liquidDensity = fluid.getPhase("aqueous").getDensity("kg/m3");
      liquidViscosity = fluid.getPhase("aqueous").getViscosity("kg/msec");
      liquidVolumeFlow = fluid.getPhase("aqueous").getVolume("m3") / 1.0;
    }

    // If no gas, use default
    if (gasDensity <= 0) {
      gasDensity = 50.0; // Typical for medium pressure
    }
    if (liquidDensity <= 0) {
      liquidDensity = 800.0; // Light oil default
    }

    // Gas capacity sizing
    double kFactor = getRecommendedKFactor(type, true);
    double maxGasVelocity = soudersbrownGasVelocity(kFactor, liquidDensity, gasDensity);

    // Liquid retention sizing
    double retentionTime = getAPI12JRetentionTime(liquidDensity);

    // Calculate dimensions
    double diameter;
    double length;
    double gasAreaRequired = gasVolumeFlow / maxGasVelocity;
    double liquidVolumeRequired = liquidVolumeFlow * retentionTime;

    if (type == SeparatorType.VERTICAL) {
      // Vertical: diameter from gas, length from liquid
      diameter = Math.sqrt(4.0 * gasAreaRequired / Math.PI);
      double gasHeight = 1.5; // meters above liquid
      double liquidHeight = liquidVolumeRequired / (Math.PI * diameter * diameter / 4.0);
      length = liquidHeight + gasHeight + 0.5; // +0.5 for inlet zone
    } else {
      // Horizontal: more complex - iterate for L/D
      // Start with L/D = 3
      double ld = 3.0;
      diameter = Math.pow(4.0 * liquidVolumeRequired / (Math.PI * 0.5 * ld), 1.0 / 3.0);
      length = ld * diameter;

      // Check gas capacity
      double gasArea = 0.5 * Math.PI * diameter * diameter / 4.0; // Upper half
      double actualGasVelocity = gasVolumeFlow / gasArea;

      if (actualGasVelocity > maxGasVelocity) {
        // Gas constrained - increase diameter
        diameter = Math.sqrt(4.0 * gasVolumeFlow / (maxGasVelocity * 0.5 * Math.PI));
        length = ld * diameter;
      }
    }

    // Apply slenderness limits
    double ldRatio = length / diameter;
    if (ldRatio < 2.0) {
      length = 2.0 * diameter;
    } else if (ldRatio > 6.0) {
      length = 6.0 * diameter;
    }

    // Build result
    SeparatorSizingResult result = new SeparatorSizingResult();
    result.separatorType = type;
    result.designStandard = standard;
    result.internalDiameter = diameter;
    result.tanTanLength = length;
    result.slendernessRatio = length / diameter;
    result.maxGasVelocity = maxGasVelocity;
    result.requiredRetentionTime = retentionTime;
    result.actualRetentionTime = liquidVolumeRequired / liquidVolumeFlow;
    result.kFactor = kFactor;
    result.liquidDensity = liquidDensity;
    result.gasDensity = gasDensity;

    return result;
  }

  /**
   * Creates a sized Separator equipment from a sizing result.
   *
   * @param name equipment name
   * @param feed inlet stream
   * @param result sizing result
   * @return configured Separator
   */
  public Separator createSeparator(String name, StreamInterface feed,
      SeparatorSizingResult result) {
    Separator sep = new Separator(name, feed);
    sep.setInternalDiameter(result.internalDiameter);
    sep.setSeparatorLength(result.tanTanLength);
    if (result.separatorType == SeparatorType.VERTICAL) {
      sep.setOrientation("vertical");
    } else {
      sep.setOrientation("horizontal");
    }
    return sep;
  }

  /**
   * Uses existing NeqSim SeparatorMechanicalDesign for sizing.
   *
   * <p>
   * This method leverages the existing NeqSim separator design infrastructure.
   * </p>
   *
   * @param separator existing separator with inlet stream
   * @return sizing result from mechanical design
   */
  public SeparatorSizingResult sizeUsingNeqSimDesign(Separator separator) {
    SeparatorMechanicalDesign design = new SeparatorMechanicalDesign(separator);
    design.calcDesign();

    SeparatorSizingResult result = new SeparatorSizingResult();
    result.internalDiameter = design.getInnerDiameter();
    result.tanTanLength = design.getTantanLength();
    result.slendernessRatio = result.tanTanLength / result.internalDiameter;
    result.kFactor = design.getGasLoadFactor();
    result.maxGasVelocity = design.getMaxDesignVolumeFlow()
        / (Math.PI * result.internalDiameter * result.internalDiameter / 4.0);
    result.requiredRetentionTime = design.getRetentionTime();
    result.designStandard = DesignStandard.API_12J;

    return result;
  }

  // ============================================================================
  // RESULT CLASS
  // ============================================================================

  /**
   * Result container for separator sizing calculations.
   */
  public static class SeparatorSizingResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Separator orientation type. */
    public SeparatorType separatorType = SeparatorType.HORIZONTAL;

    /** Design standard used. */
    public DesignStandard designStandard = DesignStandard.API_12J;

    /** Internal diameter (m). */
    public double internalDiameter;

    /** Tan-to-tan length (m). */
    public double tanTanLength;

    /** Slenderness ratio (L/D). */
    public double slendernessRatio;

    /** Souders-Brown K-factor used (m/s). */
    public double kFactor;

    /** Maximum allowable gas velocity (m/s). */
    public double maxGasVelocity;

    /** Required liquid retention time (s). */
    public double requiredRetentionTime;

    /** Actual liquid retention time (s). */
    public double actualRetentionTime;

    /** Liquid density used (kg/m³). */
    public double liquidDensity;

    /** Gas density used (kg/m³). */
    public double gasDensity;

    /** Gas capacity utilization (0-1). */
    public double gasCapacityUtilization;

    /** Liquid capacity utilization (0-1). */
    public double liquidCapacityUtilization;

    /**
     * Returns separator volume in m³.
     *
     * @return total separator volume
     */
    public double getVolume() {
      return Math.PI * internalDiameter * internalDiameter / 4.0 * tanTanLength;
    }

    /**
     * Returns liquid volume capacity in m³.
     *
     * @return liquid volume (assumes 50% fill for horizontal)
     */
    public double getLiquidVolume() {
      if (separatorType == SeparatorType.VERTICAL) {
        return getVolume() * 0.7; // 70% for liquid
      } else {
        return getVolume() * 0.5; // 50% for horizontal
      }
    }

    @Override
    public String toString() {
      return String.format(
          "SeparatorSizingResult[type=%s, D=%.2fm, L=%.2fm, L/D=%.1f, K=%.3f, t_ret=%.0fs]",
          separatorType, internalDiameter, tanTanLength, slendernessRatio, kFactor,
          requiredRetentionTime);
    }
  }
}
