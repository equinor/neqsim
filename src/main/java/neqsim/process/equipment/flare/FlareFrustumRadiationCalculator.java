package neqsim.process.equipment.flare;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Flare thermal-radiation calculator using the API 521 solid-flame (frustum) model.
 *
 * <p>
 * Unlike a single point-source model, the solid-flame approach locates the radiant centroid of a wind-distorted flame
 * and computes radiant flux at a ground-level receptor by inverse-square spreading with atmospheric transmissivity. The
 * flame is tilted by the crosswind so that the centroid moves downwind, which is the physical mechanism behind a
 * "relevant wind" radiation case.
 * </p>
 *
 * <p>
 * The wind speed at the flame tip can be supplied directly or taken from
 * {@link RelevantWindCalculator#getRelevantWindSpeed()}. The flame length is supplied from a flare datasheet or a
 * separate flame-length correlation.
 * </p>
 *
 * <p>
 * Computation steps:
 * </p>
 *
 * <ol>
 * <li>total heat release Q = mass flow &middot; lower heating value;</li>
 * <li>radiated power Qrad = F &middot; Q;</li>
 * <li>flame tilt from the wind/jet velocity ratio (sin&theta; = Rv / sqrt(1 + Rv^2));</li>
 * <li>flame centroid located half the flame length along the tilted axis from the tip;</li>
 * <li>radiant flux q = &tau; &middot; Qrad / (4&pi; &middot; d^2) at the receptor.</li>
 * </ol>
 *
 * @author NeqSim
 * @version 1.0
 */
public class FlareFrustumRadiationCalculator implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Logger for this class. */
  private static final Logger logger = LogManager.getLogger(FlareFrustumRadiationCalculator.class);

  // ===== Inputs =====
  /** Relieved mass flow rate to the flare in kg/s. */
  private double massFlow = 50.0;
  /** Lower heating value of the flared gas in J/kg. */
  private double lowerHeatingValue = 45.0e6;
  /** Fraction of total heat release radiated (dimensionless). */
  private double fractionRadiated = 0.20;
  /** Flame length in m. */
  private double flameLength = 60.0;
  /** Gas jet exit velocity at the flare tip in m/s. */
  private double jetExitVelocity = 120.0;
  /** Wind speed at the flame tip in m/s. */
  private double windSpeed = 10.0;
  /** Flare tip elevation above grade in m. */
  private double flareTipElevation = 100.0;
  /** Horizontal (downwind) distance from the flare stack to the receptor in m. */
  private double receptorHorizontalDistance = 150.0;
  /** Receptor elevation above grade in m. */
  private double receptorElevation = 1.5;
  /** Atmospheric transmissivity (dimensionless, 0-1). */
  private double atmosphericTransmissivity = 1.0;
  /** Allowable radiant heat flux in W/m2 (e.g. API 521 limits). */
  private double allowableRadiantFlux = 6300.0;

  // ===== Results =====
  /** Total heat release in W. */
  private double totalHeatRelease;
  /** Radiated power in W. */
  private double radiatedPower;
  /** Flame tilt angle from vertical in degrees. */
  private double flameTiltDegrees;
  /** Horizontal coordinate of the flame centroid (downwind) in m. */
  private double centroidHorizontal;
  /** Vertical coordinate of the flame centroid above grade in m. */
  private double centroidElevation;
  /** Distance from the flame centroid to the receptor in m. */
  private double distanceToReceptor;
  /** Radiant heat flux at the receptor in W/m2. */
  private double radiantHeatFlux;
  /** True when the radiant flux is within the allowable limit. */
  private boolean withinAllowable;

  /**
   * Default constructor for FlareFrustumRadiationCalculator.
   */
  public FlareFrustumRadiationCalculator() {
  }

  /**
   * Sets the flared-gas duty.
   *
   * @param massFlowKgS relieved mass flow rate in kg/s (must be &gt; 0)
   * @param lowerHeatingValueJKg lower heating value in J/kg (must be &gt; 0)
   * @param fractionRadiatedDimensionless radiated fraction F (0-1, must be &gt; 0)
   */
  public void setDuty(double massFlowKgS, double lowerHeatingValueJKg, double fractionRadiatedDimensionless) {
    this.massFlow = massFlowKgS;
    this.lowerHeatingValue = lowerHeatingValueJKg;
    this.fractionRadiated = fractionRadiatedDimensionless;
  }

  /**
   * Sets the flame geometry and wind conditions.
   *
   * @param flameLengthM flame length in m (must be &gt; 0)
   * @param jetExitVelocityMS gas jet exit velocity in m/s (must be &gt; 0)
   * @param windSpeedMS wind speed at the flame tip in m/s (must be &ge; 0)
   * @param flareTipElevationM flare tip elevation above grade in m (must be &gt; 0)
   */
  public void setFlameGeometry(double flameLengthM, double jetExitVelocityMS, double windSpeedMS,
      double flareTipElevationM) {
    this.flameLength = flameLengthM;
    this.jetExitVelocity = jetExitVelocityMS;
    this.windSpeed = windSpeedMS;
    this.flareTipElevation = flareTipElevationM;
  }

  /**
   * Sets the receptor location and acceptance criteria.
   *
   * @param horizontalDistanceM downwind distance from the stack to the receptor in m (must be &gt; 0)
   * @param receptorElevationM receptor elevation above grade in m (must be &ge; 0)
   * @param transmissivity atmospheric transmissivity (0-1, must be &gt; 0)
   * @param allowableFluxWM2 allowable radiant heat flux in W/m2 (must be &gt; 0)
   */
  public void setReceptor(double horizontalDistanceM, double receptorElevationM, double transmissivity,
      double allowableFluxWM2) {
    this.receptorHorizontalDistance = horizontalDistanceM;
    this.receptorElevation = receptorElevationM;
    this.atmosphericTransmissivity = transmissivity;
    this.allowableRadiantFlux = allowableFluxWM2;
  }

  /**
   * Populates the flare duty directly from a NeqSim process {@link Flare}.
   *
   * <p>
   * Reads the relieving mass flow from the flare inlet stream and the radiant scenario heat duty already computed by
   * the flare, deriving the lower heating value as heat duty divided by mass flow. The radiated fraction and
   * flame/receptor geometry are left unchanged so they can be configured separately via {@link #setDuty},
   * {@link #setFlameGeometry} and {@link #setReceptor}.
   * </p>
   *
   * @param flare the process flare supplying the relieving duty (must not be null and must have an inlet stream)
   */
  public void fromFlare(Flare flare) {
    if (flare == null) {
      throw new IllegalArgumentException("flare cannot be null");
    }
    StreamInterface inlet = flare.getInletStream();
    if (inlet == null) {
      throw new IllegalArgumentException("flare has no inlet stream");
    }
    double massFlowKgPerS = inlet.getFlowRate("kg/sec");
    double heatDutyW = flare.getHeatDuty();
    this.massFlow = massFlowKgPerS;
    if (massFlowKgPerS > 0.0) {
      this.lowerHeatingValue = heatDutyW / massFlowKgPerS;
    }
    logger.debug("Populated flare radiation from flare: m={} kg/s, LHV={} J/kg", this.massFlow, this.lowerHeatingValue);
  }

  /**
   * Runs the API 521 solid-flame frustum radiation calculation.
   */
  public void calcRadiation() {
    totalHeatRelease = massFlow * lowerHeatingValue;
    radiatedPower = fractionRadiated * totalHeatRelease;

    double velocityRatio = windSpeed / Math.max(jetExitVelocity, 1.0e-9);
    double sinTilt = velocityRatio / Math.sqrt(1.0 + velocityRatio * velocityRatio);
    double cosTilt = 1.0 / Math.sqrt(1.0 + velocityRatio * velocityRatio);
    flameTiltDegrees = Math.toDegrees(Math.asin(sinTilt));

    centroidHorizontal = 0.5 * flameLength * sinTilt;
    centroidElevation = flareTipElevation + 0.5 * flameLength * cosTilt;

    double dx = receptorHorizontalDistance - centroidHorizontal;
    double dz = centroidElevation - receptorElevation;
    distanceToReceptor = Math.sqrt(dx * dx + dz * dz);

    radiantHeatFlux = atmosphericTransmissivity * radiatedPower
        / (4.0 * Math.PI * distanceToReceptor * distanceToReceptor);
    withinAllowable = radiantHeatFlux <= allowableRadiantFlux;

    logger.debug("API 521 frustum: Qrad={} W, tilt={} deg, d={} m, q={} W/m2, ok={}", radiatedPower, flameTiltDegrees,
        distanceToReceptor, radiantHeatFlux, withinAllowable);
  }

  /**
   * Returns the total heat release.
   *
   * @return total heat release in W
   */
  public double getTotalHeatRelease() {
    return totalHeatRelease;
  }

  /**
   * Returns the radiated power.
   *
   * @return radiated power in W
   */
  public double getRadiatedPower() {
    return radiatedPower;
  }

  /**
   * Returns the flame tilt angle from vertical.
   *
   * @return flame tilt in degrees
   */
  public double getFlameTiltDegrees() {
    return flameTiltDegrees;
  }

  /**
   * Returns the flame centroid downwind horizontal coordinate.
   *
   * @return horizontal centroid coordinate in m
   */
  public double getCentroidHorizontal() {
    return centroidHorizontal;
  }

  /**
   * Returns the flame centroid elevation above grade.
   *
   * @return centroid elevation in m
   */
  public double getCentroidElevation() {
    return centroidElevation;
  }

  /**
   * Returns the distance from the flame centroid to the receptor.
   *
   * @return distance in m
   */
  public double getDistanceToReceptor() {
    return distanceToReceptor;
  }

  /**
   * Returns the radiant heat flux at the receptor.
   *
   * @return radiant heat flux in W/m2
   */
  public double getRadiantHeatFlux() {
    return radiantHeatFlux;
  }

  /**
   * Returns whether the radiant flux is within the allowable limit.
   *
   * @return true when within the allowable radiant flux
   */
  public boolean isWithinAllowable() {
    return withinAllowable;
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
