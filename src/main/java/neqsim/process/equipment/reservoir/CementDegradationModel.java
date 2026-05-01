package neqsim.process.equipment.reservoir;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;

/**
 * Cement degradation model for CO2 injection wells.
 *
 * <p>
 * Models the time-dependent degradation of wellbore cement exposed to CO2, which is critical for
 * assessing long-term out-of-zone leakage risk in CCS projects.
 * </p>
 *
 * <p>
 * The model tracks two key processes:
 * </p>
 * <ul>
 * <li><b>Carbonation front advance:</b> CO2 reacts with Ca(OH)2 and C-S-H in Portland cement,
 * creating CaCO3. The front advances as d(t) = A * sqrt(D_eff * t), where D_eff is the effective
 * CO2 diffusivity.</li>
 * <li><b>Permeability evolution:</b> Behind the carbonation front, the microstructure changes
 * (initially densification, then degradation at high CO2 exposure), modifying the cement's
 * permeability over time.</li>
 * </ul>
 *
 * <h2>Cement Types</h2>
 * <ul>
 * <li><b>PORTLAND:</b> Standard Class G/H cement. Initial permeability ~0.001 mD, but can degrade
 * to 0.01-1 mD under CO2 exposure over decades.</li>
 * <li><b>SILICA_PORTLAND:</b> Portland with silica flour, more CO2-resistant.</li>
 * <li><b>CO2_RESISTANT:</b> Specialty cements (calcium aluminate, geopolymer) designed for CCS
 * applications.</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * CementDegradationModel cement = new CementDegradationModel("Production Casing Cement");
 * cement.setCementType(CementDegradationModel.CementType.PORTLAND);
 * cement.setInitialPermeability(0.001, "mD");
 * cement.setCementThickness(0.05, "m"); // 50 mm annular cement
 * cement.setCO2Conditions(50.0, 353.15); // 50 bar CO2 partial pressure, 80°C
 *
 * // Get degradation after 30 years
 * double kDegraded = cement.getPermeabilityAtTime(30.0, "mD");
 * double carbonationDepth = cement.getDegradationDepth(30.0, "mm");
 * boolean compromised = cement.isCementCompromised(30.0);
 *
 * System.out.println("Permeability at 30 years: " + kDegraded + " mD");
 * System.out.println("Carbonation depth: " + carbonationDepth + " mm");
 * System.out.println("Cement compromised: " + compromised);
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class CementDegradationModel extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(CementDegradationModel.class);

  /**
   * Cement type classification.
   */
  public enum CementType {
    /** Standard Portland Class G/H cement. */
    PORTLAND,
    /** Portland cement with silica flour additive. */
    SILICA_PORTLAND,
    /** Specialty CO2-resistant cement (calcium aluminate or geopolymer). */
    CO2_RESISTANT
  }

  private CementType cementType = CementType.PORTLAND;
  private double initialPermeability = 0.001; // mD
  private double degradedPermeability = 1.0; // mD (fully carbonated cement)
  private double cementThickness = 0.05; // m (total cement sheath thickness)
  private double co2PartialPressure = 0.0; // bar
  private double temperature = 323.15; // K (default 50°C)
  private double effectiveDiffusivity = 1e-12; // m²/s (CO2 diffusivity in cement)
  private double carbonationRateFactor = 1.0; // dimensionless scaling factor

  /**
   * Creates a cement degradation model.
   *
   * @param name model name
   */
  public CementDegradationModel(String name) {
    super(name);
  }

  /**
   * Set the cement type. This adjusts default degradation parameters.
   *
   * @param type cement type
   */
  public void setCementType(CementType type) {
    this.cementType = type;
    applyDefaultsForCementType();
  }

  /**
   * Set initial cement permeability.
   *
   * @param permeability permeability value
   * @param unit unit ("mD", "D", "m2")
   */
  public void setInitialPermeability(double permeability, String unit) {
    if ("D".equalsIgnoreCase(unit)) {
      this.initialPermeability = permeability * 1000.0;
    } else if ("m2".equalsIgnoreCase(unit)) {
      this.initialPermeability = permeability / 9.869e-16;
    } else {
      this.initialPermeability = permeability;
    }
  }

  /**
   * Set the fully-degraded permeability (end-state when cement is fully carbonated).
   *
   * @param permeability degraded permeability value
   * @param unit unit ("mD", "D")
   */
  public void setDegradedPermeability(double permeability, String unit) {
    if ("D".equalsIgnoreCase(unit)) {
      this.degradedPermeability = permeability * 1000.0;
    } else {
      this.degradedPermeability = permeability;
    }
  }

  /**
   * Set cement sheath thickness.
   *
   * @param thickness cement thickness
   * @param unit thickness unit ("m", "mm", "in")
   */
  public void setCementThickness(double thickness, String unit) {
    if ("mm".equalsIgnoreCase(unit)) {
      this.cementThickness = thickness / 1000.0;
    } else if ("in".equalsIgnoreCase(unit)) {
      this.cementThickness = thickness * 0.0254;
    } else {
      this.cementThickness = thickness;
    }
  }

  /**
   * Set CO2 exposure conditions.
   *
   * @param co2PartialPressureBar CO2 partial pressure (bar)
   * @param temperatureK temperature (K)
   */
  public void setCO2Conditions(double co2PartialPressureBar, double temperatureK) {
    this.co2PartialPressure = co2PartialPressureBar;
    this.temperature = temperatureK;
    updateDiffusivity();
  }

  /**
   * Set effective CO2 diffusivity in cement.
   *
   * @param diffusivity diffusivity (m²/s), typically 1e-13 to 1e-11
   */
  public void setEffectiveDiffusivity(double diffusivity) {
    this.effectiveDiffusivity = diffusivity;
  }

  /**
   * Get the depth of the carbonation front at a given time.
   *
   * <p>
   * The carbonation front advances as: d(t) = A * sqrt(D_eff * t), where A depends on cement
   * chemistry, CO2 partial pressure, and temperature. The front cannot exceed total cement
   * thickness.
   * </p>
   *
   * @param years exposure time (years)
   * @param unit output unit ("m", "mm", "in")
   * @return carbonation front depth
   */
  public double getDegradationDepth(double years, String unit) {
    double timeSeconds = years * 365.25 * 86400.0;

    // d(t) = A * sqrt(D_eff * t)
    // A is a dimensionless factor that depends on CO2 conditions
    double aCarbonation = carbonationRateFactor * getPressureFactor() * getTemperatureFactor();
    double depthM = aCarbonation * Math.sqrt(effectiveDiffusivity * timeSeconds);

    // Cannot exceed total cement thickness
    depthM = Math.min(depthM, cementThickness);

    if ("mm".equalsIgnoreCase(unit)) {
      return depthM * 1000.0;
    } else if ("in".equalsIgnoreCase(unit)) {
      return depthM / 0.0254;
    }
    return depthM;
  }

  /**
   * Get the cement permeability at a given time, accounting for degradation.
   *
   * <p>
   * The permeability evolves as a weighted average between initial and degraded values based on the
   * fraction of cement thickness that has been carbonated.
   * </p>
   *
   * @param years exposure time (years)
   * @param unit output unit ("mD", "D")
   * @return permeability at the specified time
   */
  public double getPermeabilityAtTime(double years, String unit) {
    double degradedDepthM = getDegradationDepth(years, "m");
    double degradedFraction = cementThickness > 0 ? degradedDepthM / cementThickness : 0.0;

    // Linear interpolation between initial and degraded permeability
    double kMd =
        initialPermeability + (degradedPermeability - initialPermeability) * degradedFraction;

    if ("D".equalsIgnoreCase(unit)) {
      return kMd / 1000.0;
    }
    return kMd; // mD
  }

  /**
   * Check if the cement is considered compromised at the given time.
   *
   * <p>
   * Cement is compromised when the carbonation front has penetrated through more than 80% of the
   * sheath thickness, or when the effective permeability exceeds 0.1 mD.
   * </p>
   *
   * @param years exposure time (years)
   * @return true if cement is compromised
   */
  public boolean isCementCompromised(double years) {
    double degradedDepth = getDegradationDepth(years, "m");
    double kCurrent = getPermeabilityAtTime(years, "mD");

    boolean thicknessCompromised = degradedDepth > 0.8 * cementThickness;
    boolean permeabilityCompromised = kCurrent > 0.1;

    return thicknessCompromised || permeabilityCompromised;
  }

  /**
   * Get the estimated time until the cement is fully carbonated.
   *
   * @param unit time unit ("years", "days")
   * @return time to full carbonation
   */
  public double getTimeToFullCarbonation(String unit) {
    double aCarbonation = carbonationRateFactor * getPressureFactor() * getTemperatureFactor();
    if (aCarbonation <= 0 || effectiveDiffusivity <= 0) {
      return Double.MAX_VALUE;
    }

    // d = A * sqrt(D * t) => t = (d/A)^2 / D
    double timeSeconds = Math.pow(cementThickness / aCarbonation, 2.0) / effectiveDiffusivity;

    if ("years".equalsIgnoreCase(unit)) {
      return timeSeconds / (365.25 * 86400.0);
    } else if ("days".equalsIgnoreCase(unit)) {
      return timeSeconds / 86400.0;
    }
    return timeSeconds; // seconds
  }

  /**
   * Get the cement type.
   *
   * @return cement type
   */
  public CementType getCementType() {
    return cementType;
  }

  /**
   * Get the cement thickness.
   *
   * @return cement thickness (m)
   */
  public double getCementThickness() {
    return cementThickness;
  }

  /**
   * Apply default parameters based on cement type.
   */
  private void applyDefaultsForCementType() {
    switch (cementType) {
      case PORTLAND:
        initialPermeability = 0.001; // mD
        degradedPermeability = 1.0; // mD
        effectiveDiffusivity = 1e-12; // m²/s
        carbonationRateFactor = 1.0;
        break;
      case SILICA_PORTLAND:
        initialPermeability = 0.0005; // mD (lower initial)
        degradedPermeability = 0.1; // mD (more resistant)
        effectiveDiffusivity = 5e-13; // m²/s (slower diffusion)
        carbonationRateFactor = 0.5; // Half the rate
        break;
      case CO2_RESISTANT:
        initialPermeability = 0.001; // mD
        degradedPermeability = 0.01; // mD (very resistant)
        effectiveDiffusivity = 1e-13; // m²/s (much slower)
        carbonationRateFactor = 0.1; // 10x slower
        break;
      default:
        break;
    }
  }

  /**
   * CO2 partial pressure effect on carbonation rate foliowing a square root relationship.
   *
   * @return pressure factor (dimensionless)
   */
  private double getPressureFactor() {
    if (co2PartialPressure <= 0) {
      return 0.0;
    }
    // Rate increases with sqrt(pCO2) - simplified from Kutchko et al. (2007)
    return Math.sqrt(co2PartialPressure / 10.0); // Normalized to 10 bar reference
  }

  /**
   * Temperature effect on carbonation rate, following Arrhenius-type behavior.
   *
   * @return temperature factor (dimensionless)
   */
  private double getTemperatureFactor() {
    // Arrhenius-type: rate increases with temperature
    // Reference temperature: 323.15 K (50°C)
    double activationEnergy = 38000.0; // J/mol (typical for carbonation)
    double gasConstant = 8.314; // J/(mol K)
    double refTemp = 323.15; // K

    return Math.exp(-activationEnergy / gasConstant * (1.0 / temperature - 1.0 / refTemp));
  }

  /**
   * Update diffusivity based on current temperature.
   */
  private void updateDiffusivity() {
    // Temperature-dependent diffusivity using Arrhenius
    double baseDiffusivity;
    switch (cementType) {
      case SILICA_PORTLAND:
        baseDiffusivity = 5e-13;
        break;
      case CO2_RESISTANT:
        baseDiffusivity = 1e-13;
        break;
      case PORTLAND:
      default:
        baseDiffusivity = 1e-12;
        break;
    }
    double refTemp = 323.15;
    double activationEnergy = 20000.0; // J/mol for diffusion
    double gasConstant = 8.314;
    this.effectiveDiffusivity = baseDiffusivity
        * Math.exp(-activationEnergy / gasConstant * (1.0 / temperature - 1.0 / refTemp));
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // No-op for standalone model; call getPermeabilityAtTime() or getDegradationDepth() directly
  }
}
