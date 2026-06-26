package neqsim.process.safety.vibration;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Acoustic-induced vibration (AIV) screening calculator for piping downstream of pressure-reducing devices (relief
 * valves, control valves, restriction orifices, blowdown valves).
 *
 * <p>
 * Implements the Carucci &amp; Mueller (1982) sound power level (PWL) correlation together with the Carucci &amp;
 * Mueller "design line" allowable PWL as a function of pipe diameter-to-thickness ratio (D/t). The screening margin
 * between the computed PWL and the allowable PWL is mapped to a Likelihood Of Failure (LOF) band, consistent with the
 * Energy Institute "Guidelines for the Avoidance of Vibration Induced Fatigue Failure in Process Pipework" (AVIFF).
 * </p>
 *
 * <p>
 * The sound power level is computed from:
 * </p>
 *
 * <pre>
 * PWL = 10 log10[ (dP/P1)^3.6 * W^2 * (T/MW)^1.2 ] + 126.1
 * </pre>
 *
 * <p>
 * where W is mass flow in lb/s and T is in degrees Rankine (the original imperial form). SI inputs are converted
 * internally. The allowable design-line PWL is:
 * </p>
 *
 * <pre>
 * PWL_allowable = 173.6 - 0.125 * (D / t)
 * </pre>
 *
 * <p>
 * Results are intended for screening - a {@link PipingFivLikelihood#HIGH} or {@link PipingFivLikelihood#VERY_HIGH}
 * outcome triggers a detailed acoustic-fatigue assessment (small-bore connection management, wall-thickness increase,
 * source treatment).
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class AcousticInducedVibrationScreening implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Mass-flow conversion factor, kg/s to lb/s. */
  private static final double KG_PER_S_TO_LB_PER_S = 2.2046226218;
  /** Temperature conversion factor, Kelvin to degrees Rankine. */
  private static final double KELVIN_TO_RANKINE = 1.8;
  /** Additive constant in the Carucci &amp; Mueller PWL correlation, dB. */
  private static final double PWL_CONSTANT = 126.1;
  /** Intercept of the Carucci &amp; Mueller design-line allowable PWL, dB. */
  private static final double DESIGN_LINE_INTERCEPT = 173.6;
  /** Slope of the Carucci &amp; Mueller design-line allowable PWL, dB per unit D/t. */
  private static final double DESIGN_LINE_SLOPE = 0.125;

  private AcousticInducedVibrationScreening() {
    // utility class
  }

  /**
   * Performs AIV screening for a gas service circuit downstream of a pressure-reducing device.
   *
   * @param circuitName circuit identifier
   * @param massFlow gas mass flow, kg/s (must be &gt; 0)
   * @param upstreamPressure upstream absolute pressure P1, bara (must be &gt; 0)
   * @param downstreamPressure downstream absolute pressure P2, bara (must be &ge; 0 and &lt; P1)
   * @param temperature downstream temperature, K (must be &gt; 0)
   * @param molecularWeight gas molecular weight, g/mol (must be &gt; 0)
   * @param outsideDiameter pipe OD, m (must be &gt; 0)
   * @param wallThickness pipe wall thickness, m (must be &gt; 0)
   * @return AIV screening result
   */
  public static AcousticInducedVibrationResult screen(String circuitName, double massFlow, double upstreamPressure,
      double downstreamPressure, double temperature, double molecularWeight, double outsideDiameter,
      double wallThickness) {
    if (massFlow <= 0.0) {
      throw new IllegalArgumentException("massFlow must be positive");
    }
    if (upstreamPressure <= 0.0) {
      throw new IllegalArgumentException("upstreamPressure must be positive");
    }
    if (downstreamPressure < 0.0 || downstreamPressure >= upstreamPressure) {
      throw new IllegalArgumentException("downstreamPressure must be in [0, upstreamPressure)");
    }
    if (temperature <= 0.0) {
      throw new IllegalArgumentException("temperature must be positive");
    }
    if (molecularWeight <= 0.0) {
      throw new IllegalArgumentException("molecularWeight must be positive");
    }
    if (outsideDiameter <= 0.0 || wallThickness <= 0.0) {
      throw new IllegalArgumentException("outsideDiameter and wallThickness must be positive");
    }

    double pressureDropRatio = (upstreamPressure - downstreamPressure) / upstreamPressure;
    double flowLbPerSec = massFlow * KG_PER_S_TO_LB_PER_S;
    double tempRankine = temperature * KELVIN_TO_RANKINE;
    double dOverT = outsideDiameter / wallThickness;

    double argument = Math.pow(pressureDropRatio, 3.6) * flowLbPerSec * flowLbPerSec
        * Math.pow(tempRankine / molecularWeight, 1.2);
    double pwlDb = 10.0 * Math.log10(argument) + PWL_CONSTANT;
    double allowablePwlDb = DESIGN_LINE_INTERCEPT - DESIGN_LINE_SLOPE * dOverT;
    double marginDb = pwlDb - allowablePwlDb;

    Map<String, Double> factors = new LinkedHashMap<String, Double>();
    factors.put("pressureDropRatio", pressureDropRatio);
    factors.put("massFlow_lbPerSec", flowLbPerSec);
    factors.put("temperature_rankine", tempRankine);
    factors.put("DoverT", dOverT);
    factors.put("pwl_dB", pwlDb);
    factors.put("allowablePwl_dB", allowablePwlDb);
    factors.put("margin_dB", marginDb);

    PipingFivLikelihood band = bandFor(marginDb);
    return new AcousticInducedVibrationResult(circuitName, pwlDb, allowablePwlDb, marginDb, band, factors,
        recommendation(band));
  }

  /**
   * Maps a screening margin (computed minus allowable PWL) to a likelihood band.
   *
   * @param marginDb margin, dB
   * @return likelihood band
   */
  public static PipingFivLikelihood bandFor(double marginDb) {
    if (marginDb < -10.0) {
      return PipingFivLikelihood.LOW;
    }
    if (marginDb < 0.0) {
      return PipingFivLikelihood.MEDIUM;
    }
    if (marginDb < 10.0) {
      return PipingFivLikelihood.HIGH;
    }
    return PipingFivLikelihood.VERY_HIGH;
  }

  /**
   * Returns the recommendation for a likelihood band.
   *
   * @param band likelihood band
   * @return recommendation text
   */
  public static String recommendation(PipingFivLikelihood band) {
    switch (band) {
    case LOW:
      return "Acceptable - no further AIV assessment.";
    case MEDIUM:
      return "Manage small-bore connections; verify branch and weld quality.";
    case HIGH:
      return "Detailed acoustic-fatigue assessment required (increase wall thickness, "
          + "remove unbraced branches, or treat the source).";
    case VERY_HIGH:
      return "Redesign required - reduce pressure-drop energy, stage the let-down, or "
          + "increase downstream pipe schedule.";
    default:
      return "Unknown";
    }
  }
}
