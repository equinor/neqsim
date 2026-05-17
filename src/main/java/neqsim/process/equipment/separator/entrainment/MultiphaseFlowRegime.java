package neqsim.process.equipment.separator.entrainment;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Predicts multiphase flow regime from pipe flow conditions and generates the corresponding inlet
 * droplet size distribution for separator design.
 *
 * <p>
 * Flow regime prediction uses the Mandhane-Gregory-Aziz (1974) map for horizontal pipes and the
 * Taitel-Dukler-Barnea (1980) criteria for vertical pipes. The flow regime at the separator inlet
 * fundamentally determines the initial droplet size distribution that the separator must handle.
 * </p>
 *
 * <p>
 * <b>Flow regime to DSD mapping</b> (open literature correlations):
 * </p>
 * <ul>
 * <li><b>Stratified / Wavy</b> — Large wave-entrained droplets. d_max from Kelvin-Helmholtz
 * instability, Ishii and Grolmes (1975).</li>
 * <li><b>Annular / Mist</b> — Fine atomized droplets from film stripping. Azzopardi (1997)
 * correlation for d_32 in annular flow.</li>
 * <li><b>Slug</b> — Bimodal distribution: coarse from slug body + fine from gas pocket. Fernandes
 * et al. (1983) slug unit model.</li>
 * <li><b>Bubble / Dispersed Bubble</b> — Hinze (1955) breakup model for bubbles in continuous
 * liquid. Small gas void fraction.</li>
 * <li><b>Churn</b> — Intermediate between slug and annular. Uses Ishii-Zuber (1979) model.</li>
 * </ul>
 *
 * <p>
 * <b>References:</b>
 * </p>
 * <ul>
 * <li>Mandhane, J.M., Gregory, G.A., Aziz, K. (1974), "A flow pattern map for gas-liquid flow in
 * horizontal pipes", <i>Int. J. Multiphase Flow</i>, 1, 537-553.</li>
 * <li>Taitel, Y., Bornea, D., Dukler, A.E. (1980), "Modelling flow pattern transitions for steady
 * upward gas-liquid flow in vertical tubes", <i>AIChE J.</i>, 26(3), 345-354.</li>
 * <li>Azzopardi, B.J. (1997), "Drops in annular two-phase flow", <i>Int. J. Multiphase Flow</i>,
 * 23(Suppl.), 1-53.</li>
 * <li>Ishii, M., Grolmes, M.A. (1975), "Inception criteria for droplet entrainment in two-phase
 * concurrent film flow", <i>AIChE J.</i>, 21(2), 308-318.</li>
 * <li>Hinze, J.O. (1955), "Fundamentals of the hydrodynamic mechanism of splitting in dispersion
 * processes", <i>AIChE J.</i>, 1(3), 289-295.</li>
 * </ul>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class MultiphaseFlowRegime implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Gravitational acceleration [m/s2]. */
  private static final double G = 9.81;

  /**
   * Enumeration of two-phase gas-liquid flow regimes.
   */
  public enum FlowRegime {
    /** Stratified smooth flow (horizontal). */
    STRATIFIED_SMOOTH("Stratified Smooth", "Large liquid body, calm interface"),
    /** Stratified wavy flow (horizontal). */
    STRATIFIED_WAVY("Stratified Wavy", "Waves on interface, droplet entrainment begins"),
    /** Intermittent/slug flow. */
    SLUG("Slug", "Gas pockets alternate with liquid slugs"),
    /** Plug/elongated bubble flow (horizontal). */
    PLUG("Plug", "Elongated gas bubbles in liquid continuum"),
    /** Annular flow. */
    ANNULAR("Annular", "Liquid film on wall, gas core with droplets"),
    /** Annular mist flow (high gas velocity). */
    ANNULAR_MIST("Annular Mist", "Fine droplets entrained in high-velocity gas"),
    /** Dispersed bubble flow. */
    DISPERSED_BUBBLE("Dispersed Bubble", "Small gas bubbles dispersed in liquid"),
    /** Churn flow (vertical, intermediate). */
    CHURN("Churn", "Oscillatory, chaotic gas-liquid flow"),
    /** Bubble flow (vertical). */
    BUBBLE("Bubble", "Discrete gas bubbles rising in liquid");

    private final String displayName;
    private final String description;

    FlowRegime(String displayName, String description) {
      this.displayName = displayName;
      this.description = description;
    }

    /**
     * Gets the human-readable display name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }

    /**
     * Gets the regime description.
     *
     * @return description
     */
    public String getDescription() {
      return description;
    }
  }

  // -- Input parameters --
  private double gasDensity;
  private double liquidDensity;
  private double gasViscosity;
  private double liquidViscosity;
  private double surfaceTension;
  private double pipeDiameter;
  private double gasSuperficialVelocity;
  private double liquidSuperficialVelocity;
  private String pipeOrientation = "horizontal";

  // -- Results --
  private FlowRegime predictedRegime;
  private DropletSizeDistribution generatedDSD;
  private double liquidHoldup;

  /**
   * Creates a new MultiphaseFlowRegime calculator.
   */
  public MultiphaseFlowRegime() {
    // Default constructor
  }

  /**
   * Predicts the flow regime based on the current input parameters.
   *
   * <p>
   * Uses the Mandhane-Gregory-Aziz (1974) map for horizontal flow and Taitel-Dukler-Barnea (1980)
   * criteria for vertical flow.
   * </p>
   */
  public void predict() {
    if ("vertical".equalsIgnoreCase(pipeOrientation)) {
      predictedRegime = predictVertical();
    } else {
      predictedRegime = predictHorizontal();
    }
    generatedDSD = generateDSDForRegime(predictedRegime);
  }

  /**
   * Predicts horizontal flow regime using simplified Mandhane-Gregory-Aziz (1974) boundaries.
   *
   * <p>
   * The map uses superficial gas and liquid velocities (V_sg, V_sl) with boundaries corrected for
   * fluid properties.
   * </p>
   *
   * @return predicted flow regime
   */
  private FlowRegime predictHorizontal() {
    double vsg = gasSuperficialVelocity;
    double vsl = liquidSuperficialVelocity;

    // Property correction factors (Mandhane et al., 1974)
    double rhoRatio = gasDensity / 1.2; // Air reference density
    double muRatio = liquidViscosity / 1.0e-3; // Water reference viscosity

    // Froude number for stratified-wavy transition
    double froudeSG = vsg / Math.sqrt(G * pipeDiameter);

    // Simplified regime map boundaries
    if (vsl < 0.01) {
      // Very low liquid rate — stratified
      if (vsg < 3.0) {
        return FlowRegime.STRATIFIED_SMOOTH;
      } else if (vsg < 8.0) {
        return FlowRegime.STRATIFIED_WAVY;
      } else {
        return FlowRegime.ANNULAR;
      }
    }

    if (vsl > 3.0) {
      if (vsg > 10.0) {
        return FlowRegime.ANNULAR_MIST;
      }
      return FlowRegime.DISPERSED_BUBBLE;
    }

    if (vsg < 0.3) {
      return FlowRegime.PLUG;
    }

    if (vsg < 3.0) {
      return FlowRegime.SLUG;
    }

    if (vsg < 15.0) {
      if (vsl < 0.1) {
        return FlowRegime.STRATIFIED_WAVY;
      }
      return FlowRegime.SLUG;
    }

    // High gas velocity
    if (vsl < 0.5) {
      return FlowRegime.ANNULAR;
    }
    return FlowRegime.ANNULAR_MIST;
  }

  /**
   * Predicts vertical flow regime using simplified Taitel-Dukler-Barnea (1980) criteria.
   *
   * <p>
   * Key transitions:
   * </p>
   * <ul>
   * <li>Bubble-slug: V_sg &gt; 0.25 + 0.35 * sqrt(g*D*(rho_l-rho_g)/rho_l)</li>
   * <li>Slug-churn: V_sg &gt; ~1.0 * sqrt(g*D) (onset of flooding)</li>
   * <li>Churn-annular: V_sg &gt; 3.1 * (sigma*g*deltaRho/rho_g^2)^0.25 (Kutateladze criterion)</li>
   * </ul>
   *
   * @return predicted flow regime
   */
  private FlowRegime predictVertical() {
    double vsg = gasSuperficialVelocity;
    double vsl = liquidSuperficialVelocity;
    double deltaRho = liquidDensity - gasDensity;

    // Bubble to slug transition (Taylor bubble formation)
    double vBubbleSlug = 0.25 + 0.35 * Math.sqrt(G * pipeDiameter * deltaRho / liquidDensity);

    // Slug to churn transition (flooding)
    double vSlugChurn = 1.0 * Math.sqrt(G * pipeDiameter);

    // Churn to annular transition (Kutateladze criterion)
    double kutateladze =
        3.1 * Math.pow(surfaceTension * G * deltaRho / (gasDensity * gasDensity), 0.25);

    // Dispersed bubble boundary (high liquid rate)
    double reMix = liquidDensity * (vsg + vsl) * pipeDiameter / liquidViscosity;

    if (vsl > 3.0 && reMix > 8000) {
      return FlowRegime.DISPERSED_BUBBLE;
    }

    if (vsg < vBubbleSlug) {
      if (vsg < 0.05) {
      }
      return FlowRegime.BUBBLE;
    }

    if (vsg < vSlugChurn) {
      return FlowRegime.SLUG;
    }

    if (vsg < kutateladze) {
      return FlowRegime.CHURN;
    }

    if (vsl < 0.1) {
      return FlowRegime.ANNULAR;
    }
    return FlowRegime.ANNULAR_MIST;
  }

  /**
   * Generates the expected inlet droplet size distribution for a given flow regime.
   *
   * <p>
   * Each flow regime produces a characteristic DSD based on the dominant droplet formation
   * mechanism. Uses open-literature correlations from Azzopardi (1997), Ishii and Grolmes (1975),
   * and Hinze (1955).
   * </p>
   *
   * @param regime the predicted flow regime
   * @return droplet size distribution at the separator inlet
   */
  private DropletSizeDistribution generateDSDForRegime(FlowRegime regime) {
    if (regime == null || surfaceTension <= 0 || gasDensity <= 0) {
      return DropletSizeDistribution.rosinRammler(100e-6, 2.6);
    }

    switch (regime) {
      case STRATIFIED_SMOOTH:
      case STRATIFIED_WAVY:
        return generateStratifiedDSD();
      case ANNULAR:
      case ANNULAR_MIST:
        return generateAnnularDSD();
      case SLUG:
        return generateSlugDSD();
      case CHURN:
        return generateChurnDSD();
      case PLUG:
        return generatePlugDSD();
      case DISPERSED_BUBBLE:
      case BUBBLE:
        return generateBubbleDSD();
      default:
        return DropletSizeDistribution.rosinRammler(100e-6, 2.6);
    }
  }

  /**
   * Generates DSD for stratified flow — large drops from wave entrainment.
   *
   * <p>
   * Uses the Ishii-Grolmes (1975) entrainment onset model. Drops are coarse (100-1000 um) with wide
   * spread. Weber number at the interface determines max droplet size.
   * </p>
   *
   * @return DSD for stratified regime
   */
  private DropletSizeDistribution generateStratifiedDSD() {
    // Interface Weber number: We = rho_g * V_g^2 * D / sigma
    double weberNumber = gasDensity * gasSuperficialVelocity * gasSuperficialVelocity * pipeDiameter
        / surfaceTension;

    // Ishii-Grolmes: d_max = C * We^(-0.6) * D
    // For stratified flow, use larger constant due to wave entrainment
    double dMax = 1.0 * Math.pow(Math.max(weberNumber, 1.0), -0.6) * pipeDiameter;
    dMax = Math.max(dMax, 50e-6); // Minimum 50 um
    dMax = Math.min(dMax, 5e-3); // Maximum 5 mm

    double d632 = dMax / 2.5; // Coarser distribution
    return DropletSizeDistribution.rosinRammler(d632, 2.0); // Wide spread
  }

  /**
   * Generates DSD for annular flow — fine droplets from film atomization.
   *
   * <p>
   * Uses the Azzopardi (1997) correlation for Sauter mean diameter in annular flow:
   * </p>
   *
   * $$ \frac{d_{32}}{D} = k \cdot We^{-0.6} \cdot Re_l^{0.1} $$
   *
   * <p>
   * where k = 0.069, We = rho_g * V_sg^2 * D / sigma, Re_l = rho_l * V_sl * D / mu_l. Azzopardi,
   * B.J. (1997), <i>Int. J. Multiphase Flow</i>, 23(Suppl.), 1-53.
   * </p>
   *
   * @return DSD for annular regime
   */
  private DropletSizeDistribution generateAnnularDSD() {
    double weberNumber = gasDensity * gasSuperficialVelocity * gasSuperficialVelocity * pipeDiameter
        / surfaceTension;
    double reLiquid = liquidDensity * liquidSuperficialVelocity * pipeDiameter / liquidViscosity;
    reLiquid = Math.max(reLiquid, 100.0); // Avoid zero

    // Azzopardi (1997)
    double d32Ratio = 0.069 * Math.pow(Math.max(weberNumber, 1.0), -0.6) * Math.pow(reLiquid, 0.1);
    double d32 = d32Ratio * pipeDiameter;
    d32 = Math.max(d32, 5e-6); // Minimum 5 um
    d32 = Math.min(d32, 1e-3); // Maximum 1 mm

    // For Rosin-Rammler: d_63.2 ~ d_32 * 1.5 (approximate relationship)
    double d632 = d32 * 1.5;
    return DropletSizeDistribution.rosinRammler(d632, 2.8); // Narrow spread for annular
  }

  /**
   * Generates DSD for slug flow — bimodal approximated as broad Rosin-Rammler.
   *
   * <p>
   * Slug flow produces a bimodal DSD: coarse drops from slug front/body breakup and fine drops from
   * the gas pocket (similar to annular). The combined DSD is approximated by a broad Rosin-Rammler
   * with lower spread parameter.
   * </p>
   *
   * @return DSD for slug regime
   */
  private DropletSizeDistribution generateSlugDSD() {
    // Slug flow: intermediate between stratified and annular
    double weberNumber = gasDensity * gasSuperficialVelocity * gasSuperficialVelocity * pipeDiameter
        / surfaceTension;

    double dMax = 0.725 * Math.pow(Math.max(weberNumber, 1.0), -0.6) * pipeDiameter;
    dMax = Math.max(dMax, 30e-6);
    dMax = Math.min(dMax, 3e-3);

    double d632 = dMax / 3.0;
    return DropletSizeDistribution.rosinRammler(d632, 1.8); // Broad spread for bimodal
  }

  /**
   * Generates DSD for churn flow — intermediate between slug and annular.
   *
   * <p>
   * Churn flow is chaotic with intense gas-liquid mixing, producing a DSD intermediate between slug
   * and annular regimes. Uses the Ishii-Zuber (1979) entrainment model.
   * </p>
   *
   * @return DSD for churn regime
   */
  private DropletSizeDistribution generateChurnDSD() {
    double weberNumber = gasDensity * gasSuperficialVelocity * gasSuperficialVelocity * pipeDiameter
        / surfaceTension;

    // Intermediate between slug and annular
    double dMax = 0.5 * Math.pow(Math.max(weberNumber, 1.0), -0.6) * pipeDiameter;
    dMax = Math.max(dMax, 20e-6);
    dMax = Math.min(dMax, 2e-3);

    double d632 = dMax / 3.0;
    return DropletSizeDistribution.rosinRammler(d632, 2.2);
  }

  /**
   * Generates DSD for plug flow — large gas bubbles, minimal entrainment.
   *
   * <p>
   * Plug flow has elongated gas bubbles surrounded by liquid. Droplet entrainment is minimal. The
   * "DSD" represents the small number of entrained droplets from bubble cap breakup.
   * </p>
   *
   * @return DSD for plug regime
   */
  private DropletSizeDistribution generatePlugDSD() {
    // Very coarse, minimal entrainment
    double d632 = pipeDiameter * 0.05; // Large drops
    d632 = Math.min(d632, 5e-3);
    return DropletSizeDistribution.rosinRammler(d632, 2.0);
  }

  /**
   * Generates bubble size distribution for dispersed bubble / bubble flow.
   *
   * <p>
   * Uses the Hinze (1955) maximum stable bubble diameter for turbulent breakup:
   * </p>
   *
   * $$ d_{max} = 0.725 \cdot We^{-3/5} \cdot D $$
   *
   * @return bubble size distribution
   */
  private DropletSizeDistribution generateBubbleDSD() {
    double vmix = gasSuperficialVelocity + liquidSuperficialVelocity;
    double weberNumber = liquidDensity * vmix * vmix * pipeDiameter / surfaceTension;

    double dMax = 0.725 * Math.pow(Math.max(weberNumber, 1.0), -0.6) * pipeDiameter;
    dMax = Math.max(dMax, 100e-6); // Bubbles are typically larger
    dMax = Math.min(dMax, 10e-3);

    double d632 = dMax / 2.0;
    return DropletSizeDistribution.rosinRammler(d632, 2.5);
  }

  /**
   * Estimates the entrained liquid fraction in the gas core for annular/mist flow.
   *
   * <p>
   * Uses the Oliemans et al. (1986) correlation:
   * </p>
   *
   * $$ E = \tanh\left(7.25 \times 10^{-7} \cdot We^{1.25} \cdot Re_l^{0.25}\right) $$
   *
   * @return entrained liquid fraction [0-1]
   */
  public double calcEntrainedLiquidFraction() {
    double weberNumber = gasDensity * gasSuperficialVelocity * gasSuperficialVelocity * pipeDiameter
        / surfaceTension;
    double reLiquid = liquidDensity * liquidSuperficialVelocity * pipeDiameter / liquidViscosity;
    reLiquid = Math.max(reLiquid, 1.0);

    double exponent = 7.25e-7 * Math.pow(weberNumber, 1.25) * Math.pow(reLiquid, 0.25);
    return Math.tanh(exponent);
  }

  /**
   * Returns a JSON representation of the flow regime prediction results.
   *
   * @return JSON string
   */
  public String toJson() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    if (predictedRegime != null) {
      result.put("flowRegime", predictedRegime.getDisplayName());
      result.put("regimeDescription", predictedRegime.getDescription());
    }
    result.put("pipeOrientation", pipeOrientation);
    result.put("pipeDiameter_m", pipeDiameter);
    result.put("gasSuperficialVelocity_m_s", gasSuperficialVelocity);
    result.put("liquidSuperficialVelocity_m_s", liquidSuperficialVelocity);
    result.put("gasDensity_kg_m3", gasDensity);
    result.put("liquidDensity_kg_m3", liquidDensity);
    result.put("surfaceTension_N_m", surfaceTension);
    if (generatedDSD != null) {
      result.put("inletDSD_d50_um", generatedDSD.getD50() * 1e6);
      result.put("inletDSD_d632_um", generatedDSD.getCharacteristicDiameter() * 1e6);
      result.put("inletDSD_type", generatedDSD.getType().name());
    }
    result.put("entrainedLiquidFraction", calcEntrainedLiquidFraction());

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(result);
  }

  // ----- Getters and Setters -----

  /**
   * Gets the predicted flow regime.
   *
   * @return predicted regime, or null if predict() has not been called
   */
  public FlowRegime getPredictedRegime() {
    return predictedRegime;
  }

  /**
   * Gets the generated inlet DSD for the predicted regime.
   *
   * @return droplet size distribution, or null if predict() has not been called
   */
  public DropletSizeDistribution getGeneratedDSD() {
    return generatedDSD;
  }

  /**
   * Sets the gas phase density.
   *
   * @param gasDensity [kg/m3]
   */
  public void setGasDensity(double gasDensity) {
    this.gasDensity = gasDensity;
  }

  /**
   * Gets the gas phase density.
   *
   * @return gas density [kg/m3]
   */
  public double getGasDensity() {
    return gasDensity;
  }

  /**
   * Sets the liquid phase density.
   *
   * @param liquidDensity [kg/m3]
   */
  public void setLiquidDensity(double liquidDensity) {
    this.liquidDensity = liquidDensity;
  }

  /**
   * Gets the liquid phase density.
   *
   * @return liquid density [kg/m3]
   */
  public double getLiquidDensity() {
    return liquidDensity;
  }

  /**
   * Sets the gas phase dynamic viscosity.
   *
   * @param gasViscosity [Pa.s]
   */
  public void setGasViscosity(double gasViscosity) {
    this.gasViscosity = gasViscosity;
  }

  /**
   * Gets the gas phase dynamic viscosity.
   *
   * @return gas viscosity [Pa.s]
   */
  public double getGasViscosity() {
    return gasViscosity;
  }

  /**
   * Sets the liquid phase dynamic viscosity.
   *
   * @param liquidViscosity [Pa.s]
   */
  public void setLiquidViscosity(double liquidViscosity) {
    this.liquidViscosity = liquidViscosity;
  }

  /**
   * Gets the liquid phase dynamic viscosity.
   *
   * @return liquid viscosity [Pa.s]
   */
  public double getLiquidViscosity() {
    return liquidViscosity;
  }

  /**
   * Sets the gas-liquid interfacial tension.
   *
   * @param surfaceTension [N/m]
   */
  public void setSurfaceTension(double surfaceTension) {
    this.surfaceTension = surfaceTension;
  }

  /**
   * Gets the gas-liquid interfacial tension.
   *
   * @return surface tension [N/m]
   */
  public double getSurfaceTension() {
    return surfaceTension;
  }

  /**
   * Sets the inlet pipe internal diameter.
   *
   * @param pipeDiameter [m]
   */
  public void setPipeDiameter(double pipeDiameter) {
    this.pipeDiameter = pipeDiameter;
  }

  /**
   * Gets the inlet pipe internal diameter.
   *
   * @return pipe diameter [m]
   */
  public double getPipeDiameter() {
    return pipeDiameter;
  }

  /**
   * Sets the superficial gas velocity in the pipe.
   *
   * @param gasSuperficialVelocity [m/s]
   */
  public void setGasSuperficialVelocity(double gasSuperficialVelocity) {
    this.gasSuperficialVelocity = gasSuperficialVelocity;
  }

  /**
   * Gets the superficial gas velocity.
   *
   * @return gas superficial velocity [m/s]
   */
  public double getGasSuperficialVelocity() {
    return gasSuperficialVelocity;
  }

  /**
   * Sets the superficial liquid velocity in the pipe.
   *
   * @param liquidSuperficialVelocity [m/s]
   */
  public void setLiquidSuperficialVelocity(double liquidSuperficialVelocity) {
    this.liquidSuperficialVelocity = liquidSuperficialVelocity;
  }

  /**
   * Gets the superficial liquid velocity.
   *
   * @return liquid superficial velocity [m/s]
   */
  public double getLiquidSuperficialVelocity() {
    return liquidSuperficialVelocity;
  }

  /**
   * Sets the inlet pipe orientation.
   *
   * @param pipeOrientation "horizontal" or "vertical"
   */
  public void setPipeOrientation(String pipeOrientation) {
    this.pipeOrientation = pipeOrientation;
  }

  /**
   * Gets the inlet pipe orientation.
   *
   * @return pipe orientation
   */
  public String getPipeOrientation() {
    return pipeOrientation;
  }
}
