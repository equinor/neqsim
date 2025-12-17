package neqsim.fluidmechanics.flownode;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class for detecting flow patterns in two-phase pipe flow.
 *
 * <p>
 * Implements various flow pattern prediction models including Taitel-Dukler (1976), Baker chart,
 * and Barnea (1987).
 * </p>
 *
 * <p>
 * Reference: Taitel, Y., & Dukler, A.E. (1976). "A model for predicting flow regime transitions in
 * horizontal and near horizontal gas-liquid flow." AIChE Journal, 22(1), 47-55.
 * </p>
 *
 * @author ASMF
 * @version 1.0
 */
public class FlowPatternDetector {
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(FlowPatternDetector.class);

  /** Gravitational acceleration (m/s²). */
  private static final double G = 9.81;

  /**
   * Private constructor to prevent instantiation.
   */
  private FlowPatternDetector() {}

  /**
   * Detects the flow pattern using the specified model.
   *
   * @param model the flow pattern prediction model to use
   * @param usg superficial gas velocity (m/s)
   * @param usl superficial liquid velocity (m/s)
   * @param rhoG gas density (kg/m³)
   * @param rhoL liquid density (kg/m³)
   * @param muG gas viscosity (Pa·s)
   * @param muL liquid viscosity (Pa·s)
   * @param sigma surface tension (N/m)
   * @param diameter pipe diameter (m)
   * @param inclination pipe inclination from horizontal (radians, positive = upward)
   * @return the predicted flow pattern
   */
  public static FlowPattern detectFlowPattern(FlowPatternModel model, double usg, double usl,
      double rhoG, double rhoL, double muG, double muL, double sigma, double diameter,
      double inclination) {

    switch (model) {
      case TAITEL_DUKLER:
        return detectTaitelDukler(usg, usl, rhoG, rhoL, muG, muL, sigma, diameter, inclination);
      case BAKER_CHART:
        return detectBakerChart(usg, usl, rhoG, rhoL, muL, sigma);
      case BARNEA:
        return detectBarnea(usg, usl, rhoG, rhoL, muG, muL, sigma, diameter, inclination);
      case BEGGS_BRILL:
        return detectBeggsBrill(usg, usl, rhoG, rhoL, diameter, inclination);
      case MANUAL:
      default:
        return FlowPattern.STRATIFIED; // Default
    }
  }

  /**
   * Detects flow pattern using the Taitel-Dukler (1976) mechanistic model.
   *
   * <p>
   * This model is applicable to horizontal and near-horizontal pipes. It uses physical transition
   * criteria:
   * </p>
   * <ul>
   * <li>Stratified → Slug: Kelvin-Helmholtz instability</li>
   * <li>Slug → Annular: Liquid film stability</li>
   * <li>Stratified → Annular: Minimum gas velocity for film suspension</li>
   * <li>Bubble → Slug: Maximum void fraction (~0.25)</li>
   * </ul>
   */
  private static FlowPattern detectTaitelDukler(double usg, double usl, double rhoG, double rhoL,
      double muG, double muL, double sigma, double diameter, double inclination) {

    // Calculate mixture velocity and input liquid fraction
    double um = usg + usl;
    double lambdaL = usl / um;

    // Calculate superficial Reynolds numbers
    double resl = rhoL * usl * diameter / muL;
    double resg = rhoG * usg * diameter / muG;

    // Calculate Froude number
    double frm = um / Math.sqrt(G * diameter);

    // Lockhart-Martinelli parameter
    double X = Math.sqrt((rhoL / rhoG) * (muL / muG)) * (usl / usg);

    // Dimensionless groups for Taitel-Dukler
    double F = Math.sqrt(rhoG / (rhoL - rhoG)) * usg
        / Math.sqrt(G * diameter * Math.cos(inclination + 1e-10));
    double K = F * Math.sqrt(resl);
    double T = Math.pow(Math.abs(Math.sin(inclination)) / Math.cos(inclination + 1e-10), 0.5)
        * Math.pow(resl, 0.5);

    // Flow pattern determination based on Taitel-Dukler criteria

    // Check for dispersed bubble flow (high liquid velocity, low gas)
    if (lambdaL > 0.75 && frm > 3.0) {
      return FlowPattern.DISPERSED_BUBBLE;
    }

    // Check for bubble flow
    // Transition criterion: void fraction < 0.25
    double voidFraction = usg / um;
    if (voidFraction < 0.25 && usl > 0.5) {
      return FlowPattern.BUBBLE;
    }

    // Check for annular/mist flow
    // Criterion: F > 1 (gas velocity high enough to suspend liquid film)
    // or very high void fraction with significant gas velocity
    if ((F > 1.0 && usg > 5.0) || (voidFraction > 0.95 && usg > 10.0)) {
      if (voidFraction > 0.9) {
        return FlowPattern.DROPLET;
      }
      return FlowPattern.ANNULAR;
    }

    // Check for slug flow
    // Criterion: Kelvin-Helmholtz instability
    // K > 1.6 for horizontal pipes
    if (K > 1.6 && X > 0.1 && X < 100) {
      return FlowPattern.SLUG;
    }

    // Check for stratified wavy flow
    // Criterion: K > 0.5 but not reaching slug
    if (K > 0.5 && F < 1.0) {
      return FlowPattern.STRATIFIED_WAVY;
    }

    // Default: stratified smooth
    return FlowPattern.STRATIFIED;
  }

  /**
   * Detects flow pattern using the Baker (1954) empirical chart.
   *
   * <p>
   * Uses dimensionless groups based on superficial mass fluxes.
   * </p>
   */
  private static FlowPattern detectBakerChart(double usg, double usl, double rhoG, double rhoL,
      double muL, double sigma) {

    // Reference properties (air-water at atmospheric conditions)
    double rhoG_ref = 1.23; // kg/m³
    double rhoL_ref = 1000.0; // kg/m³
    double muL_ref = 0.001; // Pa·s
    double sigma_ref = 0.072; // N/m

    // Baker parameters
    double lambda = Math.sqrt((rhoG / rhoG_ref) * (rhoL / rhoL_ref));
    double psi =
        (sigma_ref / sigma) * Math.pow(muL / muL_ref, 0.33333) * Math.pow(rhoL_ref / rhoL, 2);

    // Mass fluxes
    double Gg = rhoG * usg;
    double Gl = rhoL * usl;

    // Baker coordinates
    double x = Gl * psi / lambda;
    double y = Gg / lambda;

    // Simplified Baker chart boundaries
    if (y > 30 * Math.pow(x, 0.4)) {
      return FlowPattern.DROPLET; // Dispersed/spray
    }
    if (y > 6 * Math.pow(x, 0.5) && y < 30 * Math.pow(x, 0.4)) {
      return FlowPattern.ANNULAR;
    }
    if (x > 100 && y < 6 * Math.pow(x, 0.5)) {
      return FlowPattern.BUBBLE;
    }
    if (x > 10 && x < 100 && y > 0.3 * Math.pow(x, 0.8)) {
      return FlowPattern.SLUG;
    }
    if (y < 0.3 * Math.pow(x, 0.8)) {
      return FlowPattern.STRATIFIED;
    }

    return FlowPattern.SLUG; // Default to slug for intermediate conditions
  }

  /**
   * Detects flow pattern using the Barnea (1987) unified model.
   *
   * <p>
   * Extension of Taitel-Dukler for all pipe inclinations.
   * </p>
   */
  private static FlowPattern detectBarnea(double usg, double usl, double rhoG, double rhoL,
      double muG, double muL, double sigma, double diameter, double inclination) {

    // For near-horizontal pipes, use Taitel-Dukler
    if (Math.abs(inclination) < Math.PI / 18) { // Less than 10 degrees
      return detectTaitelDukler(usg, usl, rhoG, rhoL, muG, muL, sigma, diameter, inclination);
    }

    // For vertical/inclined pipes
    double um = usg + usl;
    double voidFraction = usg / um;

    // Drift velocity for bubble rise
    double vd = 1.53 * Math.pow(G * sigma * (rhoL - rhoG) / (rhoL * rhoL), 0.25);

    if (inclination > 0) {
      // Upward flow
      if (voidFraction < 0.25) {
        return FlowPattern.BUBBLE;
      }
      if (voidFraction > 0.75) {
        if (usg > 15.0) {
          return FlowPattern.DROPLET;
        }
        return FlowPattern.ANNULAR;
      }
      // Check for churn flow in vertical
      if (Math.abs(inclination) > Math.PI / 4) { // More than 45 degrees
        double frg = usg / Math.sqrt(G * diameter);
        if (frg > 0.5 && frg < 3.0 && voidFraction > 0.4 && voidFraction < 0.7) {
          return FlowPattern.CHURN;
        }
      }
      return FlowPattern.SLUG;
    } else {
      // Downward flow - simplified
      if (voidFraction > 0.8) {
        return FlowPattern.ANNULAR;
      }
      if (voidFraction < 0.2) {
        return FlowPattern.BUBBLE;
      }
      return FlowPattern.SLUG;
    }
  }

  /**
   * Detects flow pattern using the Beggs-Brill (1973) correlation.
   *
   * <p>
   * Empirical correlation for all pipe inclinations.
   * </p>
   */
  private static FlowPattern detectBeggsBrill(double usg, double usl, double rhoG, double rhoL,
      double diameter, double inclination) {

    double um = usg + usl;
    double lambdaL = usl / um;
    double nFr = um * um / (G * diameter);

    // Beggs-Brill transition boundaries
    double L1 = 316 * Math.pow(lambdaL, 0.302);
    double L2 = 0.0009252 * Math.pow(lambdaL, -2.4684);
    double L3 = 0.10 * Math.pow(lambdaL, -1.4516);
    double L4 = 0.5 * Math.pow(lambdaL, -6.738);

    // Determine horizontal flow pattern
    if ((lambdaL < 0.01 && nFr < L1) || (lambdaL >= 0.01 && nFr < L2)) {
      return FlowPattern.STRATIFIED; // Segregated
    }
    if ((lambdaL >= 0.01 && lambdaL < 0.4 && nFr >= L3 && nFr <= L1)
        || (lambdaL >= 0.4 && nFr >= L3 && nFr <= L4)) {
      return FlowPattern.SLUG; // Intermittent
    }
    if ((lambdaL < 0.4 && nFr >= L1) || (lambdaL >= 0.4 && nFr > L4)) {
      return FlowPattern.DROPLET; // Distributed
    }

    // Transition zone
    return FlowPattern.SLUG;
  }

  /**
   * Calculates the liquid holdup using the Beggs-Brill correlation.
   *
   * @param pattern the flow pattern
   * @param lambdaL no-slip liquid holdup (input liquid fraction)
   * @param nFr Froude number
   * @param inclination pipe inclination (radians)
   * @return the actual liquid holdup
   */
  public static double calculateLiquidHoldup(FlowPattern pattern, double lambdaL, double nFr,
      double inclination) {
    // Horizontal liquid holdup coefficients
    double a, b, c;
    switch (pattern) {
      case STRATIFIED:
      case STRATIFIED_WAVY:
        a = 0.980;
        b = 0.4846;
        c = 0.0868;
        break;
      case SLUG:
      case CHURN:
        a = 0.845;
        b = 0.5351;
        c = 0.0173;
        break;
      case ANNULAR:
      case DROPLET:
      case DISPERSED_BUBBLE:
        a = 1.065;
        b = 0.5824;
        c = 0.0609;
        break;
      case BUBBLE:
      default:
        a = 0.98;
        b = 0.4846;
        c = 0.0868;
        break;
    }

    // Horizontal holdup
    double hL0 = a * Math.pow(lambdaL, b) / Math.pow(nFr, c);
    hL0 = Math.min(hL0, 1.0);
    hL0 = Math.max(hL0, lambdaL); // Holdup >= no-slip holdup for upflow

    // Inclination correction (simplified)
    double theta = inclination;
    if (Math.abs(theta) > 0.001) {
      double psi = 1.0 + 0.3 * Math.sin(1.8 * theta)
          - 0.1 * Math.sin(1.8 * theta) * (1.0 - Math.pow(lambdaL, 2));
      return hL0 * psi;
    }

    return hL0;
  }
}
