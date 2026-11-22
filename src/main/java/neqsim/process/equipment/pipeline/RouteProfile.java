package neqsim.process.equipment.pipeline;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A surveyed pipeline route, resampled onto the mesh a pipe model needs.
 *
 * <p>
 * Bathymetry and survey data arrive as irregular (KP, depth) pairs, but the pipe models want different things:
 * {@link TwoFluidPipe} wants section lengths plus an elevation profile with one more entry than there are sections,
 * while {@link PipeBeggsAndBrills} wants a scalar length, elevation and angle. Every study that has needed both has
 * written its own resampler, and the off-by-one on the elevation array is easy to get wrong.
 * </p>
 *
 * <p>
 * Depths are positive downwards, as they come from a bathymetric survey. Elevations are negative downwards, which is
 * what the pipe models expect. The conversion happens here so it happens once.
 * </p>
 *
 * <pre>
 * {@code
 * RouteProfile route = RouteProfile.fromDepths(kpMetres, depthMetres).withRiser(216.0, 25.0).resample(60);
 * TwoFluidPipe pipe = new TwoFluidPipe("tie-back", feed);
 * pipe.setNumberOfSections(route.getNumberOfSections());
 * pipe.setSectionLengths(route.getSectionLengths());
 * pipe.setElevationProfile(route.getElevationProfile());
 * }
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class RouteProfile implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Class logger. */
  private static final Logger logger = LogManager.getLogger(RouteProfile.class);

  /** Cumulative distance along the route, in metres. */
  private final double[] kp;

  /** Elevation at each station, negative downwards, in metres. */
  private final double[] elevation;

  /**
   * Creates a route from cumulative distance and elevation arrays.
   *
   * @param kpMetres cumulative distance along the route in metres, strictly increasing
   * @param elevationMetres elevation at each station in metres, negative below sea level
   * @throws IllegalArgumentException if the arrays differ in length, are shorter than two entries, or the distance is
   * not strictly increasing
   */
  public RouteProfile(double[] kpMetres, double[] elevationMetres) {
    if (kpMetres == null || elevationMetres == null) {
      throw new IllegalArgumentException("route arrays cannot be null");
    }
    if (kpMetres.length != elevationMetres.length) {
      throw new IllegalArgumentException(
          "kp and elevation must have the same length, got " + kpMetres.length + " and " + elevationMetres.length);
    }
    if (kpMetres.length < 2) {
      throw new IllegalArgumentException("a route needs at least two stations");
    }
    for (int i = 1; i < kpMetres.length; i++) {
      if (kpMetres[i] <= kpMetres[i - 1]) {
        throw new IllegalArgumentException("kp must be strictly increasing; station " + i + " is " + kpMetres[i]);
      }
    }
    this.kp = kpMetres.clone();
    this.elevation = elevationMetres.clone();
  }

  /**
   * Creates a route from survey depths, which are positive downwards.
   *
   * @param kpMetres cumulative distance along the route in metres
   * @param depthMetres water depth at each station in metres, positive downwards
   * @return the route with elevations negated into the pipe-model convention
   */
  public static RouteProfile fromDepths(double[] kpMetres, double[] depthMetres) {
    double[] elevations = new double[depthMetres.length];
    for (int i = 0; i < depthMetres.length; i++) {
      elevations[i] = -depthMetres[i];
    }
    return new RouteProfile(kpMetres, elevations);
  }

  /**
   * Appends a near-vertical riser from the last station up to a topside elevation.
   *
   * @param waterDepthAtHostM water depth at the riser base in metres, positive downwards
   * @param topsideElevationM topside elevation above sea level in metres, positive upwards
   * @return a new route including the riser
   */
  public RouteProfile withRiser(double waterDepthAtHostM, double topsideElevationM) {
    double rise = waterDepthAtHostM + topsideElevationM;
    if (rise <= 0.0) {
      throw new IllegalArgumentException("riser rise must be positive, got " + rise);
    }
    int n = kp.length;
    double[] newKp = new double[n + 1];
    double[] newElevation = new double[n + 1];
    System.arraycopy(kp, 0, newKp, 0, n);
    System.arraycopy(elevation, 0, newElevation, 0, n);
    // 2% slant allowance so the riser is not exactly vertical, which some models reject.
    newKp[n] = kp[n - 1] + rise * 1.02;
    newElevation[n] = topsideElevationM;
    return new RouteProfile(newKp, newElevation);
  }

  /**
   * Resamples the route onto a uniform mesh.
   *
   * @param numberOfSections number of equal-length sections, must be positive
   * @return a resampled route with {@code numberOfSections + 1} stations
   */
  public RouteProfile resample(int numberOfSections) {
    if (numberOfSections < 1) {
      throw new IllegalArgumentException("numberOfSections must be positive");
    }
    double total = getTotalLength();
    double[] newKp = new double[numberOfSections + 1];
    double[] newElevation = new double[numberOfSections + 1];
    for (int i = 0; i <= numberOfSections; i++) {
      double target = kp[0] + total * i / numberOfSections;
      newKp[i] = target;
      newElevation[i] = elevationAt(target);
    }
    return new RouteProfile(newKp, newElevation);
  }

  /**
   * Linearly interpolates the elevation at a distance along the route.
   *
   * @param kpMetres distance along the route in metres
   * @return interpolated elevation in metres, clamped to the end stations
   */
  public double elevationAt(double kpMetres) {
    if (kpMetres <= kp[0]) {
      return elevation[0];
    }
    if (kpMetres >= kp[kp.length - 1]) {
      return elevation[elevation.length - 1];
    }
    for (int i = 0; i < kp.length - 1; i++) {
      if (kpMetres <= kp[i + 1]) {
        double span = kp[i + 1] - kp[i];
        double w = (kpMetres - kp[i]) / span;
        return elevation[i] + w * (elevation[i + 1] - elevation[i]);
      }
    }
    return elevation[elevation.length - 1];
  }

  /**
   * Returns the section lengths, one per section.
   *
   * @return section lengths in metres, length is {@link #getNumberOfSections()}
   */
  public double[] getSectionLengths() {
    double[] lengths = new double[kp.length - 1];
    for (int i = 0; i < lengths.length; i++) {
      lengths[i] = kp[i + 1] - kp[i];
    }
    return lengths;
  }

  /**
   * Returns the elevation profile, one entry per station.
   *
   * <p>
   * This has one more entry than {@link #getSectionLengths()}, which is what
   * {@link TwoFluidPipe#setElevationProfile(double[])} requires.
   * </p>
   *
   * @return elevations in metres, negative below sea level
   */
  public double[] getElevationProfile() {
    return elevation.clone();
  }

  /**
   * Returns the cumulative distance at each station.
   *
   * @return distances in metres
   */
  public double[] getKp() {
    return kp.clone();
  }

  /**
   * Returns the number of sections, which is one fewer than the number of stations.
   *
   * @return section count
   */
  public int getNumberOfSections() {
    return kp.length - 1;
  }

  /**
   * Returns the total developed length of the route.
   *
   * @return length in metres
   */
  public double getTotalLength() {
    return kp[kp.length - 1] - kp[0];
  }

  /**
   * Returns the net elevation change from inlet to outlet.
   *
   * @return elevation gain in metres, positive when the outlet is higher
   */
  public double getNetElevationChange() {
    return elevation[elevation.length - 1] - elevation[0];
  }

  /**
   * Returns the average inclination of the route.
   *
   * <p>
   * This is what {@link PipeBeggsAndBrills#setAngle(double)} wants. It discards the undulation, which matters for
   * hold-up and terrain slugging, so prefer a segmented model on a real seabed.
   * </p>
   *
   * @return angle in degrees, positive upwards
   */
  public double getAverageAngleDegrees() {
    double ratio = getNetElevationChange() / getTotalLength();
    return Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, ratio))));
  }

  /**
   * Returns the steepest section inclination along the route.
   *
   * @return maximum absolute angle in degrees
   */
  public double getMaximumAngleDegrees() {
    double worst = 0.0;
    for (int i = 0; i < kp.length - 1; i++) {
      double span = kp[i + 1] - kp[i];
      double rise = elevation[i + 1] - elevation[i];
      double angle = Math.toDegrees(Math.atan2(rise, span));
      worst = Math.max(worst, Math.abs(angle));
    }
    return worst;
  }

  /**
   * Returns the low points where liquid can accumulate.
   *
   * <p>
   * A local minimum in the elevation profile is a candidate terrain-slug trap and the first place to look when a line
   * is filling with liquid.
   * </p>
   *
   * @return distances along the route in metres, empty when the route is monotonic
   */
  public List<Double> getLowPointKp() {
    List<Double> lows = new ArrayList<Double>();
    for (int i = 1; i < elevation.length - 1; i++) {
      if (elevation[i] < elevation[i - 1] && elevation[i] < elevation[i + 1]) {
        lows.add(Double.valueOf(kp[i]));
      }
    }
    return lows;
  }

  /**
   * Applies this route to a two-fluid pipe model.
   *
   * @param pipe the pipe to configure
   */
  public void applyTo(TwoFluidPipe pipe) {
    pipe.setNumberOfSections(getNumberOfSections());
    pipe.setSectionLengths(getSectionLengths());
    pipe.setElevationProfile(getElevationProfile());
  }

  /**
   * Applies this route to a Beggs and Brill pipe model as a single averaged segment.
   *
   * @param pipe the pipe to configure
   */
  public void applyTo(PipeBeggsAndBrills pipe) {
    pipe.setLength(getTotalLength());
    pipe.setElevation(getNetElevationChange());
    if (getMaximumAngleDegrees() > 5.0) {
      logger.warn(
          "RouteProfile: collapsing a route with {} degree maximum inclination onto a single "
              + "averaged Beggs and Brill segment discards the undulation that drives hold-up and "
              + "terrain slugging; consider TwoFluidPipe with the full profile",
          String.format("%.1f", Double.valueOf(getMaximumAngleDegrees())));
    }
  }
}
