package neqsim.process.safety.vibration;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Energy Institute AVIFF flow-induced vibration screening calculator.
 *
 * <p>
 * Implements a Likelihood Of Failure (LOF) score from the contributory factors described in the EI "Guidelines for the
 * Avoidance of Vibration Induced Fatigue Failure in Process Pipework" (AVIFF). The score combines the dominant flow
 * energy term (ρv² for gas or v for liquid) with weighting factors for D/t ratio, branch geometry, pulsation source,
 * and support quality.
 * </p>
 *
 * <p>
 * The exact AVIFF expressions are proprietary; this implementation uses public open-form approximations consistent with
 * the LOF bands described in the standard. Results are intended for screening - a {@link PipingFivLikelihood#HIGH} or
 * {@link PipingFivLikelihood#VERY_HIGH} outcome triggers a detailed dynamic assessment (FEA, strain gauging).
 * </p>
 *
 * <p>
 * <b>Inputs (gas service):</b>
 * </p>
 * <ul>
 * <li>density [kg/m³]</li>
 * <li>velocity [m/s]</li>
 * <li>pipe outside diameter [m]</li>
 * <li>pipe wall thickness [m]</li>
 * <li>branch count (number of small bore connections)</li>
 * <li>pulsation source factor: 1.0 (steady) - 4.0 (reciprocating compressor near-field)</li>
 * <li>support quality factor: 1.0 (good) - 3.0 (poor)</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class PipingFivScreening implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Reference gas momentum flux (ρv²) for LOF normalisation (kg/(m·s²)). */
  public static final double REFERENCE_GAS_RHO_V2 = 50000.0;
  /** Reference liquid velocity for LOF normalisation (m/s). */
  public static final double REFERENCE_LIQUID_VELOCITY = 6.0;

  private PipingFivScreening() {
    // utility class
  }

  /**
   * Performs FIV screening for a gas service circuit.
   *
   * @param circuitName circuit identifier
   * @param density gas density at line conditions, kg/m³
   * @param velocity gas velocity at line conditions, m/s
   * @param outsideDiameter pipe OD, m (must be &gt; 0)
   * @param wallThickness pipe wall thickness, m (must be &gt; 0)
   * @param branchCount number of small-bore branches
   * @param pulsationFactor 1.0 (steady) to 4.0 (high-pulsation source)
   * @param supportQualityFactor 1.0 (good) to 3.0 (poor)
   * @return FIV LOF result
   */
  public static FivLikelihoodResult screenGas(String circuitName, double density, double velocity,
      double outsideDiameter, double wallThickness, int branchCount, double pulsationFactor,
      double supportQualityFactor) {
    if (outsideDiameter <= 0.0 || wallThickness <= 0.0) {
      throw new IllegalArgumentException("outsideDiameter and wallThickness must be positive");
    }
    if (density < 0.0 || velocity < 0.0) {
      throw new IllegalArgumentException("density and velocity must be non-negative");
    }
    if (branchCount < 0) {
      throw new IllegalArgumentException("branchCount must be non-negative");
    }
    double rhoV2 = density * velocity * velocity;
    double dOverT = outsideDiameter / wallThickness;
    double energyTerm = rhoV2 / REFERENCE_GAS_RHO_V2;
    double dOverTFactor = Math.max(1.0, dOverT / 40.0);
    double branchFactor = 1.0 + 0.15 * branchCount;
    double pulsation = clamp(pulsationFactor, 1.0, 4.0);
    double support = clamp(supportQualityFactor, 1.0, 3.0);

    double lof = energyTerm * dOverTFactor * branchFactor * pulsation * support;

    Map<String, Double> factors = new LinkedHashMap<String, Double>();
    factors.put("rhoV2_kgPerMs2", rhoV2);
    factors.put("DoverT", dOverT);
    factors.put("DoverTFactor", dOverTFactor);
    factors.put("branchFactor", branchFactor);
    factors.put("pulsationFactor", pulsation);
    factors.put("supportQualityFactor", support);
    factors.put("energyTerm", energyTerm);
    factors.put("lof", lof);

    PipingFivLikelihood band = bandFor(lof);
    return new FivLikelihoodResult(circuitName, lof, band, factors, recommendation(band));
  }

  /**
   * Performs FIV screening for a liquid service circuit.
   *
   * @param circuitName circuit identifier
   * @param velocity liquid velocity at line conditions, m/s
   * @param outsideDiameter pipe OD, m (must be &gt; 0)
   * @param wallThickness pipe wall thickness, m (must be &gt; 0)
   * @param branchCount number of small-bore branches
   * @param supportQualityFactor 1.0 (good) to 3.0 (poor)
   * @return FIV LOF result
   */
  public static FivLikelihoodResult screenLiquid(String circuitName, double velocity, double outsideDiameter,
      double wallThickness, int branchCount, double supportQualityFactor) {
    if (outsideDiameter <= 0.0 || wallThickness <= 0.0) {
      throw new IllegalArgumentException("outsideDiameter and wallThickness must be positive");
    }
    if (velocity < 0.0) {
      throw new IllegalArgumentException("velocity must be non-negative");
    }
    if (branchCount < 0) {
      throw new IllegalArgumentException("branchCount must be non-negative");
    }
    double dOverT = outsideDiameter / wallThickness;
    double energyTerm = velocity / REFERENCE_LIQUID_VELOCITY;
    double dOverTFactor = Math.max(1.0, dOverT / 40.0);
    double branchFactor = 1.0 + 0.10 * branchCount;
    double support = clamp(supportQualityFactor, 1.0, 3.0);

    double lof = energyTerm * dOverTFactor * branchFactor * support;

    Map<String, Double> factors = new LinkedHashMap<String, Double>();
    factors.put("velocity_mps", velocity);
    factors.put("DoverT", dOverT);
    factors.put("DoverTFactor", dOverTFactor);
    factors.put("branchFactor", branchFactor);
    factors.put("supportQualityFactor", support);
    factors.put("energyTerm", energyTerm);
    factors.put("lof", lof);

    PipingFivLikelihood band = bandFor(lof);
    return new FivLikelihoodResult(circuitName, lof, band, factors, recommendation(band));
  }

  /**
   * Maps a LOF score to the AVIFF likelihood band.
   *
   * @param lof normalised likelihood of failure
   * @return likelihood band
   */
  public static PipingFivLikelihood bandFor(double lof) {
    if (lof < 0.3) {
      return PipingFivLikelihood.LOW;
    }
    if (lof < 0.5) {
      return PipingFivLikelihood.MEDIUM;
    }
    if (lof < 1.0) {
      return PipingFivLikelihood.HIGH;
    }
    return PipingFivLikelihood.VERY_HIGH;
  }

  /**
   * Returns the AVIFF recommendation for a band.
   *
   * @param band likelihood band
   * @return recommendation text
   */
  public static String recommendation(PipingFivLikelihood band) {
    switch (band) {
    case LOW:
      return "Acceptable - no further assessment.";
    case MEDIUM:
      return "Basic mitigation: improve clamping / supports; inspect small-bore connections.";
    case HIGH:
      return "Detailed dynamic assessment required (FEA or strain-gauge survey).";
    case VERY_HIGH:
      return "Redesign required - reduce flow energy, increase wall thickness, or remove branches.";
    default:
      return "Unknown";
    }
  }

  private static double clamp(double v, double lo, double hi) {
    if (v < lo) {
      return lo;
    }
    if (v > hi) {
      return hi;
    }
    return v;
  }
}
