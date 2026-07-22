package neqsim.process.mechanicaldesign.pipeline;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Line-sizing screening with a likelihood-of-failure (LOF) banding.
 *
 * <p>
 * Combines several public line-sizing criteria into a single screening verdict:
 * </p>
 *
 * <ul>
 * <li>erosional velocity per API RP 14E, Ve = C / sqrt(&rho;);</li>
 * <li>erosion utilization = actual velocity / erosional velocity;</li>
 * <li>fluid kinetic energy &rho;v^2 (relevant to flow-induced vibration and noise);</li>
 * <li>a recommended velocity limit check.</li>
 * </ul>
 *
 * <p>
 * The likelihood of failure is the maximum of the erosion utilization and the normalized kinetic energy. It is banded
 * as LOW (&lt; 0.5), MEDIUM (0.5-1.0), or HIGH (&ge; 1.0). This is a screening tool that complements detailed checks
 * such as {@code NorsokP002LineSizingValidator} and {@code AviffScreeningCalculator}.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class LineSizingLofCalculator implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Logger for this class. */
  private static final Logger logger = LogManager.getLogger(LineSizingLofCalculator.class);

  /** Lower bound of the MEDIUM likelihood band. */
  private static final double MEDIUM_BAND_THRESHOLD = 0.5;

  /** Lower bound of the HIGH likelihood band. */
  private static final double HIGH_BAND_THRESHOLD = 1.0;

  // ===== Inputs =====
  /** Mixture density in kg/m3. */
  private double mixtureDensity = 120.0;
  /** Actual flow velocity in m/s. */
  private double velocity = 10.0;
  /** API RP 14E erosional-velocity constant (SI form, m/s with density in kg/m3). */
  private double erosionConstant = 122.0;
  /** Recommended maximum velocity in m/s. */
  private double recommendedVelocityLimit = 20.0;
  /** Reference kinetic energy used to normalize rho*v^2 in Pa. */
  private double kineticEnergyReference = 15000.0;

  // ===== Results =====
  /** Erosional velocity in m/s. */
  private double erosionalVelocity;
  /** Erosion utilization = velocity / erosional velocity (dimensionless). */
  private double erosionUtilization;
  /** Fluid kinetic energy rho*v^2 in Pa. */
  private double kineticEnergy;
  /** Normalized kinetic energy = rho*v^2 / reference (dimensionless). */
  private double normalizedKineticEnergy;
  /** True when the velocity exceeds the recommended limit. */
  private boolean exceedsRecommendedVelocity;
  /** Likelihood of failure score (dimensionless). */
  private double likelihoodOfFailure;
  /** Likelihood band (LOW, MEDIUM, HIGH). */
  private String likelihoodBand;

  /**
   * Default constructor for LineSizingLofCalculator.
   */
  public LineSizingLofCalculator() {
  }

  /**
   * Sets the flow conditions.
   *
   * @param mixtureDensityKgM3 mixture density in kg/m3 (must be &gt; 0)
   * @param velocityMS actual flow velocity in m/s (must be &gt; 0)
   */
  public void setFlowConditions(double mixtureDensityKgM3, double velocityMS) {
    this.mixtureDensity = mixtureDensityKgM3;
    this.velocity = velocityMS;
  }

  /**
   * Sets the screening criteria.
   *
   * @param erosionConstantC API RP 14E erosional-velocity constant C (must be &gt; 0)
   * @param recommendedVelocityLimitMS recommended maximum velocity in m/s (must be &gt; 0)
   * @param kineticEnergyReferencePa reference kinetic energy in Pa (must be &gt; 0)
   */
  public void setCriteria(double erosionConstantC, double recommendedVelocityLimitMS, double kineticEnergyReferencePa) {
    this.erosionConstant = erosionConstantC;
    this.recommendedVelocityLimit = recommendedVelocityLimitMS;
    this.kineticEnergyReference = kineticEnergyReferencePa;
  }

  /**
   * Populates the flow conditions directly from a NeqSim process stream and a pipe internal diameter.
   *
   * <p>
   * Reads the mixture density and total volumetric flow from the stream's fluid (the stream must already have been
   * run/flashed) and converts the volumetric flow to a bulk velocity using the supplied pipe internal diameter. The
   * screening criteria are left unchanged so they can be configured separately via {@link #setCriteria}.
   * </p>
   *
   * @param stream the process stream supplying the fluid (must not be null and must carry a fluid)
   * @param pipeInternalDiameterM the pipe internal diameter in m (must be &gt; 0)
   */
  public void fromStream(StreamInterface stream, double pipeInternalDiameterM) {
    if (stream == null) {
      throw new IllegalArgumentException("stream cannot be null");
    }
    SystemInterface fluid = stream.getFluid();
    if (fluid == null) {
      throw new IllegalArgumentException("stream has no fluid");
    }
    double rho = fluid.getDensity("kg/m3");
    double volumetricFlowM3PerS = fluid.getFlowRate("m3/sec");
    double areaM2 = Math.PI * Math.pow(pipeInternalDiameterM / 2.0, 2.0);
    this.mixtureDensity = rho;
    this.velocity = areaM2 > 0.0 ? volumetricFlowM3PerS / areaM2 : 0.0;
    logger.debug("Populated line-sizing LOF from stream: rho={} kg/m3, v={} m/s", this.mixtureDensity, this.velocity);
  }

  /**
   * Runs the line-sizing LOF screening calculation.
   */
  public void calcScreening() {
    erosionalVelocity = erosionConstant / Math.sqrt(Math.max(mixtureDensity, 1.0e-9));
    erosionUtilization = velocity / Math.max(erosionalVelocity, 1.0e-9);
    kineticEnergy = mixtureDensity * velocity * velocity;
    normalizedKineticEnergy = kineticEnergy / Math.max(kineticEnergyReference, 1.0e-9);
    exceedsRecommendedVelocity = velocity > recommendedVelocityLimit;

    likelihoodOfFailure = Math.max(erosionUtilization, normalizedKineticEnergy);
    if (likelihoodOfFailure >= HIGH_BAND_THRESHOLD) {
      likelihoodBand = "HIGH";
    } else if (likelihoodOfFailure >= MEDIUM_BAND_THRESHOLD) {
      likelihoodBand = "MEDIUM";
    } else {
      likelihoodBand = "LOW";
    }

    logger.debug("Line sizing LOF: Ve={} m/s, util={}, rho*v2={} Pa, LOF={}, band={}", erosionalVelocity,
        erosionUtilization, kineticEnergy, likelihoodOfFailure, likelihoodBand);
  }

  /**
   * Returns the erosional velocity.
   *
   * @return erosional velocity in m/s
   */
  public double getErosionalVelocity() {
    return erosionalVelocity;
  }

  /**
   * Returns the erosion utilization.
   *
   * @return erosion utilization (dimensionless)
   */
  public double getErosionUtilization() {
    return erosionUtilization;
  }

  /**
   * Returns the fluid kinetic energy rho*v^2.
   *
   * @return kinetic energy in Pa
   */
  public double getKineticEnergy() {
    return kineticEnergy;
  }

  /**
   * Returns the normalized kinetic energy.
   *
   * @return normalized kinetic energy (dimensionless)
   */
  public double getNormalizedKineticEnergy() {
    return normalizedKineticEnergy;
  }

  /**
   * Returns whether the velocity exceeds the recommended limit.
   *
   * @return true when the recommended velocity is exceeded
   */
  public boolean isExceedsRecommendedVelocity() {
    return exceedsRecommendedVelocity;
  }

  /**
   * Returns the likelihood-of-failure score.
   *
   * @return likelihood of failure (dimensionless)
   */
  public double getLikelihoodOfFailure() {
    return likelihoodOfFailure;
  }

  /**
   * Returns the likelihood band.
   *
   * @return likelihood band (LOW, MEDIUM, HIGH)
   */
  public String getLikelihoodBand() {
    return likelihoodBand;
  }

  /**
   * Serializes the calculation results to a pretty-printed JSON string.
   *
   * @return JSON representation of the results
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
