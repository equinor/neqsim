package neqsim.process.safety.firewater;

import java.io.Serializable;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * Screening of fire-water monitor ("fire cannon") coverage as an alternative to a fixed deluge net.
 *
 * <p>
 * Monitors are often proposed when a fixed nozzle net is impractical, for example over equipment that must be opened
 * for maintenance. This class tests the proposal against the two things that usually defeat it on an open, wind-exposed
 * deck:
 * </p>
 * <ol>
 * <li><b>Density</b> — a monitor spreads its flow over a large footprint, so the achieved (l/min)/m<sup>2</sup> is
 * typically far below the fixed-system minimum that the governing standard carries over to monitor substitution.</li>
 * <li><b>Wind drift</b> — a monitor jet breaks into droplets that are displaced downwind in proportion to the fall
 * height and inversely to the droplet terminal velocity. The screening drift displacement is <em>&Delta;x = h &middot;
 * u<sub>wind</sub> / v<sub>t</sub></em>.</li>
 * </ol>
 *
 * <p>
 * Shadowing behind large equipment is reported as a flag, not modelled: a monitor is a directional, line-of-sight
 * device and cannot wet surfaces it cannot see.
 * </p>
 *
 * <p>
 * This is a screening model intended to decide whether a monitor concept is worth carrying into detailed design; it is
 * not a substitute for a monitor trajectory or CFD study.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class FireMonitorCoverage implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Representative terminal velocity of a coarse water-monitor droplet in m/s. */
  public static final double DEFAULT_DROPLET_TERMINAL_VELOCITY_M_PER_S = 8.0;

  private final double targetAreaM2;
  private final double requiredDensityLpmPerM2;
  private int monitorCount = 1;
  private double monitorFlowLpm = 2400.0;
  private double effectiveThrowM = 40.0;
  private double sweepAngleDeg = 90.0;
  private double windSpeedMPerS = 0.0;
  private double fallHeightM = 10.0;
  private double dropletTerminalVelocityMPerS = DEFAULT_DROPLET_TERMINAL_VELOCITY_M_PER_S;
  private double characteristicTargetDimensionM = Double.NaN;
  private boolean lineOfSightObstructed = false;

  /**
   * Create a monitor coverage screening.
   *
   * @param targetAreaM2 area the monitors are proposed to protect, in m2, must be greater than zero
   * @param requiredDensityLpmPerM2 density the governing standard requires, in (l/min)/m2, must be greater than zero
   */
  public FireMonitorCoverage(double targetAreaM2, double requiredDensityLpmPerM2) {
    if (targetAreaM2 <= 0.0) {
      throw new IllegalArgumentException("targetAreaM2 must be > 0, got " + targetAreaM2);
    }
    if (requiredDensityLpmPerM2 <= 0.0) {
      throw new IllegalArgumentException("requiredDensityLpmPerM2 must be > 0, got " + requiredDensityLpmPerM2);
    }
    this.targetAreaM2 = targetAreaM2;
    this.requiredDensityLpmPerM2 = requiredDensityLpmPerM2;
  }

  /**
   * Set the monitor arrangement.
   *
   * @param count number of monitors directed at the target area, must be one or greater
   * @param flowLpm discharge of one monitor in l/min, must be greater than zero
   * @return this screening, for chaining
   */
  public FireMonitorCoverage setMonitors(int count, double flowLpm) {
    if (count < 1) {
      throw new IllegalArgumentException("monitor count must be >= 1, got " + count);
    }
    if (flowLpm <= 0.0) {
      throw new IllegalArgumentException("monitor flow must be > 0, got " + flowLpm);
    }
    this.monitorCount = count;
    this.monitorFlowLpm = flowLpm;
    return this;
  }

  /**
   * Set the monitor spray geometry.
   *
   * @param effectiveThrowM effective wetted throw in m, must be greater than zero
   * @param sweepAngleDeg swept sector angle in degrees, must be greater than zero and at most 360
   * @return this screening, for chaining
   */
  public FireMonitorCoverage setGeometry(double effectiveThrowM, double sweepAngleDeg) {
    if (effectiveThrowM <= 0.0) {
      throw new IllegalArgumentException("effectiveThrowM must be > 0, got " + effectiveThrowM);
    }
    if (sweepAngleDeg <= 0.0 || sweepAngleDeg > 360.0) {
      throw new IllegalArgumentException("sweepAngleDeg must be in (0,360], got " + sweepAngleDeg);
    }
    this.effectiveThrowM = effectiveThrowM;
    this.sweepAngleDeg = sweepAngleDeg;
    return this;
  }

  /**
   * Set the wind exposure used for the drift screening.
   *
   * @param windSpeedMPerS wind speed at deck level in m/s, must be zero or greater
   * @param fallHeightM height the droplets fall from the top of the trajectory to the target in m, must be greater than
   * zero
   * @return this screening, for chaining
   */
  public FireMonitorCoverage setWind(double windSpeedMPerS, double fallHeightM) {
    if (windSpeedMPerS < 0.0) {
      throw new IllegalArgumentException("windSpeedMPerS must be >= 0, got " + windSpeedMPerS);
    }
    if (fallHeightM <= 0.0) {
      throw new IllegalArgumentException("fallHeightM must be > 0, got " + fallHeightM);
    }
    this.windSpeedMPerS = windSpeedMPerS;
    this.fallHeightM = fallHeightM;
    return this;
  }

  /**
   * Set the droplet terminal velocity used in the drift screening.
   *
   * @param vtMPerS terminal velocity in m/s, must be greater than zero
   * @return this screening, for chaining
   */
  public FireMonitorCoverage setDropletTerminalVelocity(double vtMPerS) {
    if (vtMPerS <= 0.0) {
      throw new IllegalArgumentException("terminal velocity must be > 0, got " + vtMPerS);
    }
    this.dropletTerminalVelocityMPerS = vtMPerS;
    return this;
  }

  /**
   * Set the characteristic plan dimension of the target used to convert drift into a coverage loss.
   *
   * @param dimensionM characteristic dimension in m; when not set, the square root of the target area is used
   * @return this screening, for chaining
   */
  public FireMonitorCoverage setCharacteristicTargetDimensionM(double dimensionM) {
    if (dimensionM <= 0.0) {
      throw new IllegalArgumentException("characteristic dimension must be > 0, got " + dimensionM);
    }
    this.characteristicTargetDimensionM = dimensionM;
    return this;
  }

  /**
   * Declare whether large equipment blocks the monitor line of sight to part of the target.
   *
   * @param obstructed true when the target is partly shadowed
   * @return this screening, for chaining
   */
  public FireMonitorCoverage setLineOfSightObstructed(boolean obstructed) {
    this.lineOfSightObstructed = obstructed;
    return this;
  }

  /**
   * Total monitor discharge.
   *
   * @return total flow in l/min
   */
  public double totalFlowLpm() {
    return monitorCount * monitorFlowLpm;
  }

  /**
   * Geometric footprint swept by the monitors.
   *
   * @return swept area in m2
   */
  public double sweptAreaM2() {
    double sector = Math.PI * effectiveThrowM * effectiveThrowM * sweepAngleDeg / 360.0;
    return monitorCount * sector;
  }

  /**
   * Density achieved if the discharge were spread evenly over the target area, ignoring wind.
   *
   * @return nominal density in (l/min)/m2
   */
  public double nominalDensityLpmPerM2() {
    return totalFlowLpm() / targetAreaM2;
  }

  /**
   * Downwind displacement of the falling droplets.
   *
   * @return drift displacement in m
   */
  public double driftDisplacementM() {
    return fallHeightM * windSpeedMPerS / dropletTerminalVelocityMPerS;
  }

  /**
   * Fraction of the target still wetted once the pattern has drifted downwind.
   *
   * @return coverage fraction between 0 and 1
   */
  public double windCoverageFraction() {
    double dim = Double.isNaN(characteristicTargetDimensionM) ? Math.sqrt(targetAreaM2)
        : characteristicTargetDimensionM;
    double frac = 1.0 - driftDisplacementM() / dim;
    if (frac < 0.0) {
      return 0.0;
    }
    if (frac > 1.0) {
      return 1.0;
    }
    return frac;
  }

  /**
   * Density achieved after the wind-drift coverage loss.
   *
   * @return effective density in (l/min)/m2
   */
  public double effectiveDensityLpmPerM2() {
    return nominalDensityLpmPerM2() * windCoverageFraction();
  }

  /**
   * Whether the monitor concept meets the required density in still air.
   *
   * @return true when the nominal density is at least the requirement
   */
  public boolean meetsDensityStillAir() {
    return nominalDensityLpmPerM2() >= requiredDensityLpmPerM2;
  }

  /**
   * Whether the monitor concept meets the required density at the stated wind speed.
   *
   * @return true when the effective density is at least the requirement
   */
  public boolean meetsDensityInWind() {
    return effectiveDensityLpmPerM2() >= requiredDensityLpmPerM2;
  }

  /**
   * Total monitor flow that would be needed to reach the required density at the stated wind speed.
   *
   * @return required total flow in l/min, or {@link Double#POSITIVE_INFINITY} when the drift removes all coverage
   */
  public double flowNeededForComplianceLpm() {
    double frac = windCoverageFraction();
    if (frac <= 0.0) {
      return Double.POSITIVE_INFINITY;
    }
    return requiredDensityLpmPerM2 * targetAreaM2 / frac;
  }

  /**
   * Screening verdict on the monitor concept.
   *
   * @return a short verdict string
   */
  public String verdict() {
    if (lineOfSightObstructed) {
      return "NOT SUITABLE - line of sight to part of the target is obstructed; a monitor cannot wet "
          + "surfaces it cannot see";
    }
    if (!meetsDensityStillAir()) {
      return "NOT SUITABLE - required density not achieved even in still air";
    }
    if (!meetsDensityInWind()) {
      return "MARGINAL - density achieved in still air only; wind drift takes it below the requirement";
    }
    return "FEASIBLE ON DENSITY - carry forward to a trajectory and shadowing study";
  }

  /**
   * Serialise the monitor screening to JSON.
   *
   * @return pretty-printed JSON document
   */
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("schemaVersion", "1.0");
    root.addProperty("targetAreaM2", targetAreaM2);
    root.addProperty("requiredDensityLpmPerM2", requiredDensityLpmPerM2);
    root.addProperty("monitorCount", monitorCount);
    root.addProperty("monitorFlowLpm", monitorFlowLpm);
    root.addProperty("totalFlowLpm", totalFlowLpm());
    root.addProperty("effectiveThrowM", effectiveThrowM);
    root.addProperty("sweepAngleDeg", sweepAngleDeg);
    root.addProperty("sweptAreaM2", sweptAreaM2());
    root.addProperty("nominalDensityLpmPerM2", nominalDensityLpmPerM2());
    root.addProperty("windSpeedMPerS", windSpeedMPerS);
    root.addProperty("fallHeightM", fallHeightM);
    root.addProperty("driftDisplacementM", driftDisplacementM());
    root.addProperty("windCoverageFraction", windCoverageFraction());
    root.addProperty("effectiveDensityLpmPerM2", effectiveDensityLpmPerM2());
    root.addProperty("meetsDensityStillAir", meetsDensityStillAir());
    root.addProperty("meetsDensityInWind", meetsDensityInWind());
    root.addProperty("flowNeededForComplianceLpm", flowNeededForComplianceLpm());
    root.addProperty("lineOfSightObstructed", lineOfSightObstructed);
    root.addProperty("verdict", verdict());
    return new GsonBuilder().setPrettyPrinting().create().toJson(root);
  }
}
