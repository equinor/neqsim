package neqsim.process.equipment.lng;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Models sloshing-induced mixing enhancement in LNG cargo tanks.
 *
 * <p>
 * Ship motion causes the LNG liquid to slosh within the tank. The resulting turbulence enhances
 * mixing between stratified layers and increases the surface renewal rate (BOG enhancement).
 * Sloshing intensity depends on:
 * </p>
 * <ul>
 * <li><b>Significant wave height (Hs):</b> Primary driver of ship motion amplitude</li>
 * <li><b>Fill level:</b> Sloshing is most severe at 10-70% fill; minimal near full or empty</li>
 * <li><b>Tank geometry:</b> Membrane tanks allow more free-surface movement than Moss</li>
 * <li><b>Ship heading:</b> Beam seas produce more sloshing than head seas</li>
 * </ul>
 *
 * <p>
 * The model produces two outputs:
 * </p>
 * <ul>
 * <li><b>Mixing factor:</b> Enhancement to inter-layer mass transfer (diffusion coefficient
 * multiplier)</li>
 * <li><b>BOG enhancement factor:</b> Enhancement to surface evaporation rate</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class LNGSloshingModel implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1024L;

  /** Logger object. */
  private static final Logger logger = LogManager.getLogger(LNGSloshingModel.class);

  /** Reference wave height for normalisation (m). Typical North Sea conditions. */
  private double referenceWaveHeight = 3.0;

  /** Sloshing intensity coefficient. Empirical scaling factor. */
  private double sloshingCoefficient = 1.0;

  /** Tank containment type (affects sloshing severity). */
  private TankGeometry.ContainmentType containmentType = TankGeometry.ContainmentType.MEMBRANE;

  /** Maximum mixing enhancement factor (physical limit). */
  private double maxMixingFactor = 10.0;

  /** Maximum BOG enhancement factor. */
  private double maxBOGEnhancement = 2.0;

  /**
   * Default constructor.
   */
  public LNGSloshingModel() {}

  /**
   * Constructor with containment type.
   *
   * @param containmentType tank containment type
   */
  public LNGSloshingModel(TankGeometry.ContainmentType containmentType) {
    this.containmentType = containmentType;
    setContainmentDefaults(containmentType);
  }

  /**
   * Set default parameters based on containment type.
   *
   * @param type containment type
   */
  private void setContainmentDefaults(TankGeometry.ContainmentType type) {
    switch (type) {
      case MEMBRANE:
        // Membrane tanks have more free-surface movement
        sloshingCoefficient = 1.0;
        maxMixingFactor = 10.0;
        break;
      case MOSS:
        // Spherical tanks have less sloshing due to shape
        sloshingCoefficient = 0.6;
        maxMixingFactor = 6.0;
        break;
      case TYPE_C:
        // Cylindrical tanks — moderate sloshing
        sloshingCoefficient = 0.8;
        maxMixingFactor = 8.0;
        break;
      case SPB:
        // SPB similar to membrane
        sloshingCoefficient = 0.9;
        maxMixingFactor = 9.0;
        break;
      default:
        sloshingCoefficient = 1.0;
        break;
    }
  }

  /**
   * Calculate the mixing enhancement factor due to sloshing.
   *
   * <p>
   * The mixing factor enhances inter-layer diffusion and is applied as a multiplier to the
   * effective diffusion coefficient. The factor depends on wave height and fill level:
   * </p>
   *
   * <pre>
   * factor = 1 + k * (Hs / Hs_ref) ^ 2 * f(fill)
   * </pre>
   * <p>
   * where f(fill) peaks at ~30-50% fill and drops to near zero at high fill levels.
   * </p>
   *
   * @param significantWaveHeight significant wave height Hs (m)
   * @param fillFraction tank fill fraction (0.0 to 1.0)
   * @return mixing enhancement factor (1.0 = no enhancement)
   */
  public double calculateMixingFactor(double significantWaveHeight, double fillFraction) {
    if (significantWaveHeight <= 0 || fillFraction <= 0 || fillFraction >= 1.0) {
      return 1.0;
    }

    // Fill-level dependency: sloshing most severe at 30-50% fill
    // Bell-shaped function peaking at 0.4 fill
    double fillEffect = calculateFillLevelEffect(fillFraction);

    // Wave height scaling: quadratic with reference wave height
    double waveRatio = significantWaveHeight / referenceWaveHeight;
    double waveEffect = waveRatio * waveRatio;

    // Combined mixing factor
    double factor = 1.0 + sloshingCoefficient * waveEffect * fillEffect;

    return Math.min(factor, maxMixingFactor);
  }

  /**
   * Calculate the BOG enhancement factor due to surface renewal from sloshing.
   *
   * <p>
   * Sloshing increases the liquid surface renewal rate, which enhances evaporation. The effect is
   * smaller than the mixing enhancement and primarily affects the surface layer.
   * </p>
   *
   * @param significantWaveHeight significant wave height Hs (m)
   * @param fillFraction tank fill fraction (0.0 to 1.0)
   * @return BOG enhancement factor (1.0 = no enhancement)
   */
  public double calculateBOGEnhancement(double significantWaveHeight, double fillFraction) {
    if (significantWaveHeight <= 0 || fillFraction <= 0 || fillFraction >= 1.0) {
      return 1.0;
    }

    double fillEffect = calculateFillLevelEffect(fillFraction);
    double waveRatio = significantWaveHeight / referenceWaveHeight;

    // BOG enhancement is less than mixing enhancement (square root scaling)
    double factor = 1.0 + 0.3 * sloshingCoefficient * Math.sqrt(waveRatio) * fillEffect;

    return Math.min(factor, maxBOGEnhancement);
  }

  /**
   * Calculate the fill-level effect on sloshing intensity.
   *
   * <p>
   * Sloshing severity depends strongly on fill level:
   * </p>
   * <ul>
   * <li>&lt; 10% fill: minimal liquid, low sloshing</li>
   * <li>10-30% fill: increasing sloshing, free surface grows</li>
   * <li>30-50% fill: maximum sloshing — resonance with tank natural frequency</li>
   * <li>50-80% fill: decreasing sloshing, less room for motion</li>
   * <li>&gt; 80% fill: minimal sloshing, liquid nearly static (deck cargo limit typically 98%)</li>
   * </ul>
   *
   * @param fillFraction fill level (0-1)
   * @return fill-level effect factor (0-1, peaks at ~0.4)
   */
  private double calculateFillLevelEffect(double fillFraction) {
    // Bell-shaped function centered at 0.4 fill with spread of 0.2
    double center = 0.4;
    double spread = 0.2;
    double deviation = (fillFraction - center) / spread;
    double bellFactor = Math.exp(-0.5 * deviation * deviation);

    // Reduce to zero at very high fills (>90%)
    if (fillFraction > 0.90) {
      double dampingFactor = (1.0 - fillFraction) / 0.10;
      bellFactor *= dampingFactor;
    }

    // Also reduce at very low fills (<10%)
    if (fillFraction < 0.10) {
      bellFactor *= fillFraction / 0.10;
    }

    return bellFactor;
  }

  // ─── Getters and setters ───

  /**
   * Get reference wave height.
   *
   * @return reference wave height (m)
   */
  public double getReferenceWaveHeight() {
    return referenceWaveHeight;
  }

  /**
   * Set reference wave height.
   *
   * @param referenceWaveHeight reference wave height (m)
   */
  public void setReferenceWaveHeight(double referenceWaveHeight) {
    this.referenceWaveHeight = referenceWaveHeight;
  }

  /**
   * Get sloshing coefficient.
   *
   * @return sloshing coefficient
   */
  public double getSloshingCoefficient() {
    return sloshingCoefficient;
  }

  /**
   * Set sloshing coefficient.
   *
   * @param sloshingCoefficient sloshing coefficient
   */
  public void setSloshingCoefficient(double sloshingCoefficient) {
    this.sloshingCoefficient = sloshingCoefficient;
  }

  /**
   * Get containment type.
   *
   * @return containment type
   */
  public TankGeometry.ContainmentType getContainmentType() {
    return containmentType;
  }

  /**
   * Set containment type (also updates defaults).
   *
   * @param containmentType containment type
   */
  public void setContainmentType(TankGeometry.ContainmentType containmentType) {
    this.containmentType = containmentType;
    setContainmentDefaults(containmentType);
  }

  /**
   * Get max mixing factor.
   *
   * @return max mixing factor
   */
  public double getMaxMixingFactor() {
    return maxMixingFactor;
  }

  /**
   * Set max mixing factor.
   *
   * @param maxMixingFactor max mixing factor
   */
  public void setMaxMixingFactor(double maxMixingFactor) {
    this.maxMixingFactor = maxMixingFactor;
  }

  /**
   * Get max BOG enhancement factor.
   *
   * @return max BOG enhancement
   */
  public double getMaxBOGEnhancement() {
    return maxBOGEnhancement;
  }

  /**
   * Set max BOG enhancement factor.
   *
   * @param maxBOGEnhancement max BOG enhancement
   */
  public void setMaxBOGEnhancement(double maxBOGEnhancement) {
    this.maxBOGEnhancement = maxBOGEnhancement;
  }
}
