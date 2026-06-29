package neqsim.process.mechanicaldesign.pipeline;

import java.io.Serializable;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Flow-induced vibration (FIV) likelihood-of-failure screening for process main-line piping, following the screening
 * methodology of the Energy Institute "Guidelines for the Avoidance of Vibration Induced Fatigue Failure in Process
 * Pipework" (often referred to as the AVIFF guidelines).
 *
 * <p>
 * The main-line FIV screening compares the kinetic energy of the flow (&rho;v&sup2;) against a Fatigue Vibration Factor
 * (FVF) that depends on the pipe size and a Fatigue Correction Factor (FCF) that depends on the small-bore connection
 * and support arrangement. A Likelihood Of Failure (LOF) is then formed:
 * </p>
 *
 * <p>
 * LOF = (&rho; v&sup2; &times; GVF<sub>corr</sub>) / (FVF &times; FCF)
 * </p>
 *
 * <p>
 * The Gas Void Fraction (GVF) correction increases the screening value in the multiphase region where intermittent
 * (slug/churn) flow can drive higher dynamic excitation. The acceptance bands are:
 * </p>
 * <ul>
 * <li>LOF &lt; 0.5 &ndash; LOW (acceptable)</li>
 * <li>0.5 &le; LOF &lt; 1.0 &ndash; MEDIUM (further assessment required)</li>
 * <li>LOF &ge; 1.0 &ndash; HIGH (redesign / detailed dynamic analysis)</li>
 * </ul>
 *
 * <p>
 * The FVF correlation here approximates the Energy Institute main-line graph and is intended for screening only; a
 * detailed assessment is required for any line that does not screen out as LOW.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class AviffScreeningCalculator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(AviffScreeningCalculator.class);

  /** Support-arrangement stiffness category for the Fatigue Correction Factor. */
  public enum SupportArrangement {
    /** Stiff, well-supported pipework with stiff small-bore connections. */
    STIFF,
    /** Medium stiffness pipework / connection arrangement. */
    MEDIUM,
    /** Flexible pipework or poorly braced small-bore connections. */
    FLEXIBLE
  }

  // ====================== Inputs ======================
  /** Mixture density in kg/m3. */
  private double mixtureDensity = 100.0;

  /** Mixture velocity in m/s. */
  private double mixtureVelocity = 10.0;

  /** Pipe nominal bore (internal diameter) in meters. */
  private double pipeInternalDiameter = 0.1;

  /** Gas void fraction (0-1). */
  private double gasVoidFraction = 1.0;

  /** Support arrangement category. */
  private SupportArrangement supportArrangement = SupportArrangement.MEDIUM;

  // ====================== Results ======================
  private double kineticEnergy = 0.0;
  private double fatigueVibrationFactor = 0.0;
  private double fatigueCorrectionFactor = 1.0;
  private double gvfCorrection = 1.0;
  private double likelihoodOfFailure = 0.0;
  private String likelihoodBand = "UNKNOWN";

  /**
   * Default constructor for AviffScreeningCalculator.
   */
  public AviffScreeningCalculator() {
  }

  /**
   * Sets the flow conditions.
   *
   * @param density mixture density in kg/m3 (must be &gt; 0)
   * @param velocity mixture velocity in m/s (must be &ge; 0)
   * @param gvf gas void fraction (0-1)
   */
  public void setFlowConditions(double density, double velocity, double gvf) {
    this.mixtureDensity = density;
    this.mixtureVelocity = velocity;
    this.gasVoidFraction = gvf;
  }

  /**
   * Sets the pipe internal diameter.
   *
   * @param idMeters internal diameter in meters (must be &gt; 0)
   */
  public void setPipeInternalDiameter(double idMeters) {
    this.pipeInternalDiameter = idMeters;
  }

  /**
   * Sets the support-arrangement category that drives the Fatigue Correction Factor.
   *
   * @param arrangement the support arrangement (must not be null)
   */
  public void setSupportArrangement(SupportArrangement arrangement) {
    this.supportArrangement = arrangement;
  }

  /**
   * Computes the Fatigue Vibration Factor (FVF) as a function of pipe bore. This is a screening approximation of the
   * Energy Institute main-line FVF graph: larger bores tolerate a higher kinetic energy before reaching a given
   * likelihood of failure.
   *
   * @param idMeters pipe internal diameter in meters (must be &gt; 0)
   * @return Fatigue Vibration Factor in Pa
   */
  private double computeFvf(double idMeters) {
    double idMillimeters = idMeters * 1000.0;
    // Screening correlation: FVF rises with bore. Calibrated to give ~LOF 1.0
    // near rho*v^2 ~ 12000 Pa for a 100 mm bore stiff line.
    return 6000.0 * Math.pow(idMillimeters / 100.0, 0.6);
  }

  /**
   * Returns the Fatigue Correction Factor for the configured support arrangement.
   *
   * @return Fatigue Correction Factor (dimensionless)
   */
  private double computeFcf() {
    switch (supportArrangement) {
    case STIFF:
      return 2.0;
    case FLEXIBLE:
      return 0.5;
    case MEDIUM:
    default:
      return 1.0;
    }
  }

  /**
   * Computes the gas void fraction correction. The largest amplification is in the intermittent multiphase region
   * (roughly GVF 0.5-0.95) where slug and churn flow drive high dynamic loads; single-phase gas and single-phase liquid
   * receive no amplification.
   *
   * @param gvf gas void fraction (0-1)
   * @return GVF correction factor (&ge; 1)
   */
  private double computeGvfCorrection(double gvf) {
    if (gvf <= 0.0 || gvf >= 1.0) {
      return 1.0;
    }
    // Peaked amplification centred on GVF ~ 0.75, up to a factor of 2.
    double peak = 0.75;
    double width = 0.25;
    double x = (gvf - peak) / width;
    return 1.0 + 1.0 * Math.exp(-x * x);
  }

  /**
   * Runs the AVIFF main-line FIV screening calculation.
   */
  public void calcScreening() {
    kineticEnergy = mixtureDensity * mixtureVelocity * mixtureVelocity;
    fatigueVibrationFactor = computeFvf(pipeInternalDiameter);
    fatigueCorrectionFactor = computeFcf();
    gvfCorrection = computeGvfCorrection(gasVoidFraction);

    double denom = fatigueVibrationFactor * fatigueCorrectionFactor;
    likelihoodOfFailure = denom > 0.0 ? (kineticEnergy * gvfCorrection) / denom : 0.0;

    if (likelihoodOfFailure < 0.5) {
      likelihoodBand = "LOW";
    } else if (likelihoodOfFailure < 1.0) {
      likelihoodBand = "MEDIUM";
    } else {
      likelihoodBand = "HIGH";
    }

    logger.debug("AVIFF screening: rho*v2={} Pa, FVF={}, FCF={}, GVFcorr={}, LOF={} ({})", kineticEnergy,
        fatigueVibrationFactor, fatigueCorrectionFactor, gvfCorrection, likelihoodOfFailure, likelihoodBand);
  }

  /**
   * Returns the flow kinetic energy (&rho;v&sup2;).
   *
   * @return kinetic energy in Pa
   */
  public double getKineticEnergy() {
    return kineticEnergy;
  }

  /**
   * Returns the Fatigue Vibration Factor.
   *
   * @return Fatigue Vibration Factor in Pa
   */
  public double getFatigueVibrationFactor() {
    return fatigueVibrationFactor;
  }

  /**
   * Returns the Fatigue Correction Factor.
   *
   * @return Fatigue Correction Factor (dimensionless)
   */
  public double getFatigueCorrectionFactor() {
    return fatigueCorrectionFactor;
  }

  /**
   * Returns the gas void fraction correction factor.
   *
   * @return GVF correction factor (dimensionless)
   */
  public double getGvfCorrection() {
    return gvfCorrection;
  }

  /**
   * Returns the calculated Likelihood Of Failure.
   *
   * @return Likelihood Of Failure (dimensionless)
   */
  public double getLikelihoodOfFailure() {
    return likelihoodOfFailure;
  }

  /**
   * Returns the Likelihood Of Failure band ("LOW", "MEDIUM" or "HIGH").
   *
   * @return likelihood band string
   */
  public String getLikelihoodBand() {
    return likelihoodBand;
  }

  /**
   * Serializes the screening results to a pretty-printed JSON string.
   *
   * @return JSON representation of the screening results
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
