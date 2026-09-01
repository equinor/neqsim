package neqsim.process.corrosion;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Screening model for flow-accelerated corrosion (FAC) of carbon and low-alloy steel in single-phase aqueous service.
 *
 * <p>
 * FAC is the dissolution of the protective magnetite film into flowing water, followed by transport of the dissolved
 * iron away from the surface. It is an <b>electrochemical</b> process rate-limited by mass transfer, and it is distinct
 * from erosion-corrosion, which requires mechanical damage by particle impingement or cavitation. The two occur at the
 * same locations and are routinely confused; they need different mitigation, so the distinction matters. FAC leaves
 * smooth, scalloped or wavy surfaces, while erosion-corrosion leaves directional grooves and impingement craters.
 * </p>
 *
 * <p>
 * The susceptibility is modelled as the product of a rigorously computed mass-transfer coefficient and four
 * dimensionless factors:
 * </p>
 *
 * <p>
 * {@code index = k_m * F_T * F_pH * F_geometry * F_Cr}
 * </p>
 *
 * <ul>
 * <li><b>Mass transfer</b> from the Berger-Hau correlation {@code Sh = 0.0165 * Re^0.86 * Sc^0.33}, the standard
 * turbulent-pipe relation used in FAC work.</li>
 * <li><b>Temperature</b>, a bell centred near 150 &deg;C. Magnetite solubility passes through a maximum there, which is
 * why API RP 571 identifies about 150 &deg;C as the most severe temperature for FAC in water systems.</li>
 * <li><b>pH at temperature</b>. Magnetite solubility falls steeply as the fluid becomes more alkaline. The pH used here
 * must be the in-situ value at operating temperature, not a laboratory value measured on a cooled sample — see
 * {@link AmineBufferedPH}, which typically shows a shift of one to two pH units.</li>
 * <li><b>Geometry</b>, the local turbulence enhancement at bends, welds and restrictions ({@link FacGeometry}).</li>
 * <li><b>Chromium content</b>. Around 1 % Cr reduces FAC by roughly an order of magnitude, which is the basis for
 * specifying low-alloy Cr-Mo steels such as ASTM A335 P11 in place of plain carbon steel.</li>
 * </ul>
 *
 * <p>
 * <b>The index is for comparison, not for absolute wall-loss prediction.</b> Only ratios between cases are meaningful:
 * this operating point versus that one, this material versus that one, this location versus that one. Use
 * {@link FlowAcceleratedCorrosionResult#getSusceptibilityIndex()} to rank alternatives, and use
 * {@link FlowAcceleratedCorrosionResult#getDominantFactor()} to identify which lever actually controls the outcome.
 * Absolute rates require plant-specific calibration against measured wall loss.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * FlowAcceleratedCorrosion fac = new FlowAcceleratedCorrosion();
 * fac.setFlow(2.66, 0.025);
 * fac.setFluidProperties(931.0, 0.487);
 * fac.setTemperature(150.0);
 * fac.setInSituPH(7.1);
 * fac.setGeometry(FacGeometry.WELD_AT_BEND);
 * fac.setChromiumContent(0.02);
 * FlowAcceleratedCorrosionResult r = fac.calculate();
 * </pre>
 *
 * <p>
 * References: API RP 571 &sect;3.9; API RP 669; Berger and Hau, <i>Int. J. Heat Mass Transfer</i> 20 (1977);
 * Sanchez-Caldera (1984); Moreira et al., <i>Materials Research</i> (2022).
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see AmineBufferedPH
 * @see FacGeometry
 */
public class FlowAcceleratedCorrosion {
  private static final Logger logger = LogManager.getLogger(FlowAcceleratedCorrosion.class);

  /** Temperature at which magnetite solubility and FAC severity peak [C]. */
  private static final double PEAK_TEMPERATURE_C = 150.0;

  /** Half-width of the temperature bell [C]. */
  private static final double TEMPERATURE_BELL_WIDTH_C = 55.0;

  /** Reference in-situ pH at which the pH factor is unity. */
  private static final double REFERENCE_PH = 7.0;

  /** Decades of magnetite solubility reduction per unit rise in in-situ pH. */
  private static final double DEFAULT_PH_DECADES = 1.0;

  /** Chromium coefficient giving an order-of-magnitude reduction at about 1.25 wt% Cr. */
  private static final double CHROMIUM_COEFFICIENT = 1.872;

  /** Diffusivity of ferrous species in water at 25 C [m2/s]. */
  private static final double DIFFUSIVITY_REF = 7.2e-10;

  /** Reference temperature for the diffusivity scaling [K]. */
  private static final double DIFFUSIVITY_REF_T = 298.15;

  /** Reference viscosity of water at 25 C [Pa s]. */
  private static final double DIFFUSIVITY_REF_MU = 0.89e-3;

  private double velocityMs = Double.NaN;
  private double hydraulicDiameterM = Double.NaN;
  private double densityKgPerM3 = Double.NaN;
  private double viscosityPaS = Double.NaN;
  private double temperatureC = Double.NaN;
  private double inSituPH = Double.NaN;
  private FacGeometry geometry = FacGeometry.STRAIGHT_PIPE;
  private double chromiumMassPercent = 0.02;
  private double phDecades = DEFAULT_PH_DECADES;

  /**
   * Create a FAC screening model with no inputs set.
   */
  public FlowAcceleratedCorrosion() {
  }

  /**
   * Set the bulk flow condition.
   *
   * @param velocityMs bulk velocity [m/s]; must be positive
   * @param hydraulicDiameterM hydraulic (internal) diameter [m]; must be positive
   * @return this model for chaining
   * @throws IllegalArgumentException if either argument is not positive
   */
  public FlowAcceleratedCorrosion setFlow(double velocityMs, double hydraulicDiameterM) {
    if (!(velocityMs > 0.0)) {
      throw new IllegalArgumentException("Velocity must be positive");
    }
    if (!(hydraulicDiameterM > 0.0)) {
      throw new IllegalArgumentException("Hydraulic diameter must be positive");
    }
    this.velocityMs = velocityMs;
    this.hydraulicDiameterM = hydraulicDiameterM;
    return this;
  }

  /**
   * Set the fluid properties at the local condition. These are normally taken from a NeqSim flash of the actual fluid
   * rather than from water tables, because glycol content changes both values substantially.
   *
   * @param densityKgPerM3 density [kg/m3]; must be positive
   * @param viscosityCP dynamic viscosity [cP]; must be positive
   * @return this model for chaining
   * @throws IllegalArgumentException if either argument is not positive
   */
  public FlowAcceleratedCorrosion setFluidProperties(double densityKgPerM3, double viscosityCP) {
    if (!(densityKgPerM3 > 0.0)) {
      throw new IllegalArgumentException("Density must be positive");
    }
    if (!(viscosityCP > 0.0)) {
      throw new IllegalArgumentException("Viscosity must be positive");
    }
    this.densityKgPerM3 = densityKgPerM3;
    this.viscosityPaS = viscosityCP * 1.0e-3;
    return this;
  }

  /**
   * Set the local metal-surface temperature.
   *
   * @param temperatureC temperature [C]
   * @return this model for chaining
   */
  public FlowAcceleratedCorrosion setTemperature(double temperatureC) {
    this.temperatureC = temperatureC;
    return this;
  }

  /**
   * Set the in-situ pH at operating temperature. This must not be a laboratory pH measured on a cooled sample; use
   * {@link AmineBufferedPH} to convert one into the other.
   *
   * @param inSituPH in-situ pH at operating temperature; must lie between 0 and 14
   * @return this model for chaining
   * @throws IllegalArgumentException if the pH is outside 0 to 14
   */
  public FlowAcceleratedCorrosion setInSituPH(double inSituPH) {
    if (!(inSituPH >= 0.0) || !(inSituPH <= 14.0)) {
      throw new IllegalArgumentException("In-situ pH must be between 0 and 14");
    }
    this.inSituPH = inSituPH;
    return this;
  }

  /**
   * Set the local geometry class.
   *
   * @param geometry the geometry; must not be null
   * @return this model for chaining
   * @throws IllegalArgumentException if the geometry is null
   */
  public FlowAcceleratedCorrosion setGeometry(FacGeometry geometry) {
    if (geometry == null) {
      throw new IllegalArgumentException("Geometry must not be null");
    }
    this.geometry = geometry;
    return this;
  }

  /**
   * Set the chromium content of the steel.
   *
   * @param massPercent chromium content [wt%]; must not be negative. Plain carbon steel is about 0.02, ASTM A335 P11 is
   * about 1.25, and P22 is about 2.25
   * @return this model for chaining
   * @throws IllegalArgumentException if the value is negative
   */
  public FlowAcceleratedCorrosion setChromiumContent(double massPercent) {
    if (!(massPercent >= 0.0)) {
      throw new IllegalArgumentException("Chromium content must not be negative");
    }
    this.chromiumMassPercent = massPercent;
    return this;
  }

  /**
   * Set the number of decades of magnetite solubility reduction per unit rise in in-situ pH.
   *
   * @param decades solubility decades per pH unit; must be positive. The default of 1.0 is representative of the
   * alkaline region
   * @return this model for chaining
   * @throws IllegalArgumentException if the value is not positive
   */
  public FlowAcceleratedCorrosion setPhSensitivity(double decades) {
    if (!(decades > 0.0)) {
      throw new IllegalArgumentException("pH sensitivity must be positive");
    }
    this.phDecades = decades;
    return this;
  }

  /**
   * Run the FAC screening calculation.
   *
   * @return an immutable screening result
   * @throws IllegalStateException if required inputs are missing
   */
  public FlowAcceleratedCorrosionResult calculate() {
    validateInputs();
    List<String> warnings = new ArrayList<String>();

    double temperatureK = temperatureC + 273.15;
    double diffusivity = DIFFUSIVITY_REF * (temperatureK / DIFFUSIVITY_REF_T) * (DIFFUSIVITY_REF_MU / viscosityPaS);
    double reynolds = densityKgPerM3 * velocityMs * hydraulicDiameterM / viscosityPaS;
    double schmidt = viscosityPaS / (densityKgPerM3 * diffusivity);
    double sherwood = 0.0165 * Math.pow(reynolds, 0.86) * Math.pow(schmidt, 0.33);
    double massTransferCoefficient = sherwood * diffusivity / hydraulicDiameterM;

    double frictionFactor = frictionFactor(reynolds);
    double wallShearStress = frictionFactor / 8.0 * densityKgPerM3 * velocityMs * velocityMs;

    double temperatureFactor = temperatureFactor(temperatureC);
    double phFactor = Math.pow(10.0, -phDecades * (inSituPH - REFERENCE_PH));
    double geometryFactor = geometry.getEnhancementFactor();
    double chromiumFactor = Math.exp(-CHROMIUM_COEFFICIENT * chromiumMassPercent);

    double index = massTransferCoefficient * temperatureFactor * phFactor * geometryFactor * chromiumFactor;

    if (reynolds < 10000.0) {
      warnings.add("Reynolds number " + reynolds + " is below the turbulent range for which the Berger-Hau "
          + "correlation was developed; the mass-transfer coefficient is extrapolated");
    }
    if (inSituPH > 9.5) {
      warnings.add("In-situ pH above 9.5 is outside the range where the single-slope solubility factor is reliable; "
          + "at high alkalinity magnetite solubility passes through a minimum and can rise again");
    }
    warnings.add("Screening index for ranking cases only; ratios between cases are meaningful but the absolute value "
        + "is not a wall-loss rate and requires calibration against measured thinning");
    warnings.add("Model assumes single-phase flow and a mass-transfer-controlled dissolution mechanism; it does not "
        + "represent erosion-corrosion by particle impingement or cavitation, which needs different mitigation");

    logger.info("FAC screening: Re={}, k_m={} m/s, tau={} Pa, F_T={}, F_pH={}, F_geom={}, F_Cr={}, index={}", reynolds,
        massTransferCoefficient, wallShearStress, temperatureFactor, phFactor, geometryFactor, chromiumFactor, index);

    return new FlowAcceleratedCorrosionResult(velocityMs, hydraulicDiameterM, temperatureC, inSituPH, geometry,
        chromiumMassPercent, reynolds, schmidt, sherwood, massTransferCoefficient, wallShearStress, temperatureFactor,
        phFactor, geometryFactor, chromiumFactor, index, warnings);
  }

  /**
   * Evaluate the temperature factor, a bell centred on the temperature at which magnetite solubility peaks.
   *
   * @param temperature local temperature [C]
   * @return the temperature factor, dimensionless, with a maximum of 1 at the peak temperature
   */
  public static double temperatureFactor(double temperature) {
    double z = (temperature - PEAK_TEMPERATURE_C) / TEMPERATURE_BELL_WIDTH_C;
    return Math.exp(-z * z);
  }

  /**
   * Evaluate the Darcy friction factor for a smooth pipe.
   *
   * @param reynolds Reynolds number; must be positive
   * @return the Darcy friction factor, dimensionless
   */
  static double frictionFactor(double reynolds) {
    if (reynolds < 2300.0) {
      return 64.0 / reynolds;
    }
    return 0.3164 / Math.pow(reynolds, 0.25);
  }

  /**
   * Validate that all mandatory inputs are present.
   *
   * @throws IllegalStateException if any mandatory input is missing
   */
  private void validateInputs() {
    if (Double.isNaN(velocityMs) || Double.isNaN(hydraulicDiameterM)) {
      throw new IllegalStateException("Flow velocity and hydraulic diameter must be set");
    }
    if (Double.isNaN(densityKgPerM3) || Double.isNaN(viscosityPaS)) {
      throw new IllegalStateException("Fluid density and viscosity must be set");
    }
    if (Double.isNaN(temperatureC)) {
      throw new IllegalStateException("Temperature must be set");
    }
    if (Double.isNaN(inSituPH)) {
      throw new IllegalStateException("In-situ pH must be set");
    }
  }
}
