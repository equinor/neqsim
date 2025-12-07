package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;

/**
 * Tracks liquid accumulation in low points and riser bases.
 *
 * <p>
 * Models liquid pooling at low points, terrain-induced slugging, and riser-base accumulation
 * phenomena. Handles drainage, filling, and surge-out events.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class LiquidAccumulationTracker implements Serializable {

  private static final long serialVersionUID = 1L;
  private static final double GRAVITY = 9.81;

  /**
   * Represents a liquid accumulation zone.
   */
  public static class AccumulationZone implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Start position (m). */
    public double startPosition;
    /** End position (m). */
    public double endPosition;
    /** Volume of accumulated liquid (m³). */
    public double liquidVolume;
    /** Maximum volume capacity (m³). */
    public double maxVolume;
    /** Liquid level in the low point (m). */
    public double liquidLevel;
    /** Is zone actively accumulating. */
    public boolean isActive;
    /** Is zone at capacity (will slug out). */
    public boolean isOverflowing;
    /** Net inflow rate (m³/s). */
    public double netInflowRate;
    /** Outflow rate when slugging (m³/s). */
    public double outflowRate;
    /** Time since last slug-out (s). */
    public double timeSinceSlug;
    /** Associated pipe sections. */
    public List<Integer> sectionIndices;

    /**
     * Constructor.
     */
    public AccumulationZone() {
      this.sectionIndices = new ArrayList<>();
    }
  }

  private List<AccumulationZone> accumulationZones;
  private double criticalHoldup = 0.25; // Holdup at which slug initiates
  private double drainageCoefficient = 0.6; // Orifice-like drainage factor

  /**
   * Constructor.
   */
  public LiquidAccumulationTracker() {
    this.accumulationZones = new ArrayList<>();
  }

  /**
   * Identify low points and potential accumulation zones in the pipeline.
   *
   * @param sections Array of pipe sections
   */
  public void identifyAccumulationZones(PipeSection[] sections) {
    accumulationZones.clear();

    for (int i = 1; i < sections.length - 1; i++) {
      double elev_prev = sections[i - 1].getElevation();
      double elev_curr = sections[i].getElevation();
      double elev_next = sections[i + 1].getElevation();

      // Check for local minimum (low point)
      if (elev_curr < elev_prev && elev_curr <= elev_next) {
        sections[i].setLowPoint(true);

        // Create accumulation zone centered on low point
        AccumulationZone zone = new AccumulationZone();
        zone.sectionIndices.add(i);

        // Extend zone to adjacent downward sections
        int startIdx = i;
        while (startIdx > 0 && sections[startIdx - 1].getInclination() < 0) {
          startIdx--;
          zone.sectionIndices.add(0, startIdx);
        }

        int endIdx = i;
        while (endIdx < sections.length - 1 && sections[endIdx + 1].getInclination() > 0) {
          endIdx++;
          zone.sectionIndices.add(endIdx);
        }

        zone.startPosition = sections[startIdx].getPosition();
        zone.endPosition = sections[endIdx].getPosition() + sections[endIdx].getLength();

        // Calculate max volume (pipe volume in the zone)
        zone.maxVolume = 0;
        for (int idx : zone.sectionIndices) {
          zone.maxVolume += sections[idx].getArea() * sections[idx].getLength();
        }

        zone.isActive = true;
        accumulationZones.add(zone);

      } else if (elev_curr > elev_prev && elev_curr >= elev_next) {
        // High point
        sections[i].setHighPoint(true);
      }
    }

    // Special case: riser base (transition from horizontal/downward to upward)
    for (int i = 1; i < sections.length; i++) {
      double incl_prev = sections[i - 1].getInclination();
      double incl_curr = sections[i].getInclination();

      // Transition to significant upward inclination
      if (incl_prev < Math.toRadians(5) && incl_curr > Math.toRadians(30)) {
        // This is a riser base - mark for accumulation tracking
        AccumulationZone riserBase = new AccumulationZone();
        riserBase.sectionIndices.add(i - 1);
        riserBase.sectionIndices.add(i);
        riserBase.startPosition = sections[i - 1].getPosition();
        riserBase.endPosition = sections[i].getPosition() + sections[i].getLength();
        riserBase.maxVolume = (sections[i - 1].getArea() * sections[i - 1].getLength()
            + sections[i].getArea() * sections[i].getLength()) * 0.5;
        riserBase.isActive = true;
        accumulationZones.add(riserBase);
      }
    }
  }

  /**
   * Update liquid accumulation for all zones.
   *
   * @param sections Pipe sections
   * @param dt Time step (s)
   */
  public void updateAccumulation(PipeSection[] sections, double dt) {
    for (AccumulationZone zone : accumulationZones) {
      updateZone(zone, sections, dt);
    }
  }

  /**
   * Update a single accumulation zone.
   */
  private void updateZone(AccumulationZone zone, PipeSection[] sections, double dt) {
    if (!zone.isActive || zone.sectionIndices.isEmpty()) {
      return;
    }

    // Calculate net liquid inflow to zone
    double liquidInflowRate = 0;
    double gasFlowRate = 0;

    // Inflow from upstream sections
    int firstIdx = zone.sectionIndices.get(0);
    if (firstIdx > 0) {
      PipeSection upstream = sections[firstIdx - 1];
      double A = upstream.getArea();
      liquidInflowRate += upstream.getLiquidHoldup() * upstream.getLiquidVelocity() * A;
      gasFlowRate += upstream.getGasHoldup() * upstream.getGasVelocity() * A;
    }

    // Outflow from downstream sections
    int lastIdx = zone.sectionIndices.get(zone.sectionIndices.size() - 1);
    if (lastIdx < sections.length - 1) {
      PipeSection downstream = sections[lastIdx + 1];
      double A = downstream.getArea();
      double liquidOutflow = downstream.getLiquidHoldup() * downstream.getLiquidVelocity() * A;

      // Outflow limited by accumulated volume
      zone.outflowRate = liquidOutflow;
      zone.netInflowRate = liquidInflowRate - liquidOutflow;
    } else {
      zone.netInflowRate = liquidInflowRate;
      zone.outflowRate = 0;
    }

    // Update accumulated volume
    zone.liquidVolume += zone.netInflowRate * dt;
    zone.liquidVolume = Math.max(0, zone.liquidVolume);

    // Check for overflow/slug-out
    zone.isOverflowing = zone.liquidVolume > 0.9 * zone.maxVolume;

    // Calculate liquid level and update sections
    double avgArea = zone.maxVolume / (zone.endPosition - zone.startPosition);
    zone.liquidLevel = zone.liquidVolume / (avgArea + 1e-10);

    // Distribute liquid among zone sections
    distributeAccumulatedLiquid(zone, sections);

    zone.timeSinceSlug += dt;
  }

  /**
   * Distribute accumulated liquid among sections in the zone.
   */
  private void distributeAccumulatedLiquid(AccumulationZone zone, PipeSection[] sections) {
    if (zone.sectionIndices.isEmpty() || zone.liquidVolume <= 0) {
      return;
    }

    // Calculate required holdup to store accumulated liquid
    double totalZoneVolume = 0;
    for (int idx : zone.sectionIndices) {
      totalZoneVolume += sections[idx].getArea() * sections[idx].getLength();
    }

    double avgHoldup = zone.liquidVolume / Math.max(totalZoneVolume, 1e-10);
    avgHoldup = Math.min(avgHoldup, 0.95);

    // Distribute with weighting toward lowest elevation
    double minElev = Double.MAX_VALUE;
    double maxElev = Double.MIN_VALUE;
    for (int idx : zone.sectionIndices) {
      minElev = Math.min(minElev, sections[idx].getElevation());
      maxElev = Math.max(maxElev, sections[idx].getElevation());
    }

    double elevRange = maxElev - minElev + 1e-6;

    for (int idx : zone.sectionIndices) {
      double elev = sections[idx].getElevation();
      // Higher holdup at lower elevations
      double weight = 1.0 + (maxElev - elev) / elevRange;
      double localHoldup = avgHoldup * weight;
      localHoldup = Math.min(localHoldup, 0.98);

      sections[idx].setAccumulatedLiquidVolume(
          localHoldup * sections[idx].getArea() * sections[idx].getLength());
    }
  }

  /**
   * Check if terrain-induced slug should be released.
   *
   * @param zone Accumulation zone
   * @param sections Pipe sections
   * @return Slug characteristics if slug released, null otherwise
   */
  public SlugCharacteristics checkForSlugRelease(AccumulationZone zone, PipeSection[] sections) {
    if (!zone.isOverflowing) {
      return null;
    }

    // Get downstream section to check if gas can push liquid out
    int lastIdx = zone.sectionIndices.get(zone.sectionIndices.size() - 1);
    if (lastIdx >= sections.length - 1) {
      return null;
    }

    PipeSection downstream = sections[lastIdx + 1];

    // Check if downstream inclination allows slug propagation
    if (downstream.getInclination() > Math.toRadians(5)) {
      // Uphill - slug can form when liquid level exceeds pipe capacity
      SlugCharacteristics slug = new SlugCharacteristics();
      slug.frontPosition = zone.endPosition;
      slug.tailPosition = zone.startPosition;
      slug.length = zone.endPosition - zone.startPosition;
      slug.holdup = 0.85; // Typical slug holdup
      slug.velocity = downstream.getMixtureVelocity() * 1.2; // Slug moves faster
      slug.volume = zone.liquidVolume;
      slug.isTerrainInduced = true;

      // Reset zone after slug release
      zone.liquidVolume *= 0.3; // Some liquid remains
      zone.timeSinceSlug = 0;
      zone.isOverflowing = false;

      return slug;
    }

    return null;
  }

  /**
   * Calculate drainage rate from a zone.
   *
   * @param zone Accumulation zone
   * @param sections Pipe sections
   * @param pressureDrop Pressure drop across zone (Pa)
   * @return Drainage rate (m³/s)
   */
  public double calculateDrainageRate(AccumulationZone zone, PipeSection[] sections,
      double pressureDrop) {

    if (zone.liquidVolume <= 0 || zone.sectionIndices.isEmpty()) {
      return 0;
    }

    int firstIdx = zone.sectionIndices.get(0);
    double D = sections[firstIdx].getDiameter();
    double rho_L = sections[firstIdx].getLiquidDensity();

    // Effective drainage area (bottom of pipe)
    double A_drain = 0.25 * Math.PI * D * D * zone.liquidLevel / D;

    // Orifice-type drainage equation
    double dP_total = pressureDrop + rho_L * GRAVITY * zone.liquidLevel;
    if (dP_total <= 0) {
      return 0;
    }

    double Q_drain = drainageCoefficient * A_drain * Math.sqrt(2.0 * dP_total / rho_L);

    return Q_drain;
  }

  /**
   * Get total accumulated liquid volume in all zones.
   *
   * @return Total volume (m³)
   */
  public double getTotalAccumulatedVolume() {
    double total = 0;
    for (AccumulationZone zone : accumulationZones) {
      total += zone.liquidVolume;
    }
    return total;
  }

  /**
   * Get list of accumulation zones.
   *
   * @return List of zones
   */
  public List<AccumulationZone> getAccumulationZones() {
    return accumulationZones;
  }

  /**
   * Get zones that are currently overflowing (about to slug).
   *
   * @return List of overflowing zones
   */
  public List<AccumulationZone> getOverflowingZones() {
    List<AccumulationZone> overflowing = new ArrayList<>();
    for (AccumulationZone zone : accumulationZones) {
      if (zone.isOverflowing) {
        overflowing.add(zone);
      }
    }
    return overflowing;
  }

  /**
   * Set critical holdup for slug initiation.
   *
   * @param holdup Critical holdup (0-1)
   */
  public void setCriticalHoldup(double holdup) {
    this.criticalHoldup = Math.max(0.1, Math.min(0.9, holdup));
  }

  /**
   * Set drainage coefficient.
   *
   * @param coeff Drainage coefficient (0-1)
   */
  public void setDrainageCoefficient(double coeff) {
    this.drainageCoefficient = Math.max(0.1, Math.min(1.0, coeff));
  }

  /**
   * Slug characteristics for terrain-induced slugs.
   */
  public static class SlugCharacteristics implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Front position (m from inlet). */
    public double frontPosition;
    /** Tail position (m from inlet). */
    public double tailPosition;
    /** Slug length (m). */
    public double length;
    /** Liquid holdup in slug (typically 0.8-1.0). */
    public double holdup;
    /** Slug translational velocity (m/s). */
    public double velocity;
    /** Slug liquid volume (m³). */
    public double volume;
    /** Is this a terrain-induced slug. */
    public boolean isTerrainInduced;

    @Override
    public String toString() {
      return String.format("Slug[front=%.1fm, length=%.1fm, vel=%.2fm/s, vol=%.3fm³]",
          frontPosition, length, velocity, volume);
    }
  }
}
