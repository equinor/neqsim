package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

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
   * 
   * <p>
   * Terrain-induced liquid accumulation occurs when:
   * <ul>
   * <li>Liquid flows downhill into the low point (gravity-driven)</li>
   * <li>Liquid velocity slows in uphill sections (slip accumulation)</li>
   * <li>Gas-liquid slip causes liquid to settle in stratified regions</li>
   * </ul>
   */
  private void updateZone(AccumulationZone zone, PipeSection[] sections, double dt) {
    if (!zone.isActive || zone.sectionIndices.isEmpty()) {
      return;
    }

    // Calculate liquid accumulation based on slip and gravity effects
    double accumulationRate = 0;

    // For each section in the zone, calculate the gravity-driven settling rate
    for (int idx : zone.sectionIndices) {
      PipeSection section = sections[idx];
      double A = section.getArea();
      double holdup = section.getLiquidHoldup();
      double gasHoldup = section.getGasHoldup();

      // Slip velocity (gas moves faster than liquid)
      double slipVelocity = section.getGasVelocity() - section.getLiquidVelocity();

      // Inclination effect: downhill sections drain into low point
      // uphill sections after low point resist liquid outflow
      double inclination = section.getInclination();
      double rho_L = section.getLiquidDensity();
      double rho_G = section.getGasDensity();
      double rho_diff = rho_L - rho_G;

      // Gravity-driven accumulation: liquid settles faster on downhill,
      // and slower on uphill (creating a dam effect)
      // Rate = (rho_L - rho_G) * g * sin(theta) * holdup * A * settling_factor
      double settlingFactor = 0.01; // Empirical factor for accumulation rate
      double gravitySettling = -rho_diff * GRAVITY * Math.sin(inclination) * holdup * gasHoldup * A
          * settlingFactor / Math.max(rho_L, 100.0);

      // Positive settling = liquid accumulates (downhill sections or stagnant uphill)
      if (inclination < 0) {
        // Downhill: liquid flows in faster
        accumulationRate += Math.abs(gravitySettling);
      } else if (inclination > 0 && slipVelocity > 0.1) {
        // Uphill with significant slip: liquid held back
        accumulationRate += Math.abs(gravitySettling) * 0.3;
      }
    }

    // Also add inflow-based accumulation (difference between liquid in and out)
    int firstIdx = zone.sectionIndices.get(0);
    int lastIdx = zone.sectionIndices.get(zone.sectionIndices.size() - 1);

    if (firstIdx > 0 && lastIdx < sections.length - 1) {
      PipeSection upstream = sections[firstIdx - 1];
      PipeSection downstream = sections[lastIdx + 1];
      double A = upstream.getArea();

      double liquidIn = upstream.getLiquidHoldup() * Math.max(upstream.getLiquidVelocity(), 0) * A;
      double liquidOut =
          downstream.getLiquidHoldup() * Math.max(downstream.getLiquidVelocity(), 0) * A;

      zone.netInflowRate = liquidIn - liquidOut;
      accumulationRate += Math.max(0, zone.netInflowRate); // Only accumulate, don't drain this way
    }

    // Update accumulated volume
    zone.liquidVolume += accumulationRate * dt;
    zone.liquidVolume = Math.max(0, Math.min(zone.liquidVolume, zone.maxVolume));

    // Also calculate actual liquid volume in zone sections based on current holdup
    double actualLiquidInZone = 0;
    for (int idx : zone.sectionIndices) {
      PipeSection section = sections[idx];
      actualLiquidInZone += section.getLiquidHoldup() * section.getArea() * section.getLength();
    }

    // Use the maximum of tracked accumulation and actual liquid in sections
    // This ensures slug release when sections are actually full
    zone.liquidVolume = Math.max(zone.liquidVolume, actualLiquidInZone * 0.8);

    // Check for overflow/slug-out based on liquid content
    // Zone overflows when holdup in the zone is high (>70% liquid)
    double avgHoldupInZone = actualLiquidInZone / Math.max(zone.maxVolume, 1e-10);
    zone.isOverflowing = zone.liquidVolume > 0.7 * zone.maxVolume || avgHoldupInZone > 0.7;

    // Calculate liquid level
    double avgArea = zone.maxVolume / (zone.endPosition - zone.startPosition);
    zone.liquidLevel = zone.liquidVolume / (avgArea + 1e-10);

    // Distribute accumulated liquid to actually increase section holdups
    distributeAccumulatedLiquid(zone, sections);

    zone.timeSinceSlug += dt;
  }

  /**
   * Distribute accumulated liquid among sections in the zone.
   * 
   * <p>
   * This method now ACTUALLY updates the section holdups, not just a separate tracking variable.
   * This is critical for terrain-induced slug formation.
   * </p>
   *
   * @param zone the accumulation zone to process
   * @param sections the pipe sections array
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

    // Calculate holdup boost from accumulation
    double accumulationHoldup = zone.liquidVolume / Math.max(totalZoneVolume, 1e-10);

    // Distribute with weighting toward lowest elevation
    double minElev = Double.MAX_VALUE;
    double maxElev = Double.MIN_VALUE;
    for (int idx : zone.sectionIndices) {
      minElev = Math.min(minElev, sections[idx].getElevation());
      maxElev = Math.max(maxElev, sections[idx].getElevation());
    }

    double elevRange = maxElev - minElev + 1e-6;

    for (int idx : zone.sectionIndices) {
      PipeSection section = sections[idx];
      double elev = section.getElevation();

      // Higher holdup at lower elevations
      double weight = 1.0 + 2.0 * (maxElev - elev) / elevRange;
      double additionalHoldup = accumulationHoldup * weight;

      // Get current holdup from drift-flux calculation
      double currentHoldup = section.getLiquidHoldup();

      // Add accumulated liquid to increase the holdup
      double newHoldup = currentHoldup + additionalHoldup;
      newHoldup = Math.min(newHoldup, 0.95); // Cap at 95%

      // ACTUALLY update the section holdup (this is the key fix!)
      section.setLiquidHoldup(newHoldup);
      section.setGasHoldup(1.0 - newHoldup);

      // Store for tracking
      section
          .setAccumulatedLiquidVolume(additionalHoldup * section.getArea() * section.getLength());

      // Reduce liquid velocity in accumulation zones (liquid is pooling)
      double velocityReduction = 1.0 - 0.5 * (additionalHoldup / 0.5);
      velocityReduction = Math.max(0.3, velocityReduction);
      section.setLiquidVelocity(section.getLiquidVelocity() * velocityReduction);

      section.updateDerivedQuantities();
    }
  }

  /**
   * Check if terrain-induced slug should be released.
   *
   * <p>
   * Slug release occurs when:
   * <ul>
   * <li>Accumulated liquid exceeds zone capacity (overflow condition)</li>
   * <li>Gas pressure is sufficient to push liquid out of the low point</li>
   * <li>Minimum time since last slug has elapsed (prevents rapid cycling)</li>
   * <li>Previous slug has cleared the zone exit (prevents immediate merging)</li>
   * </ul>
   *
   * @param zone Accumulation zone
   * @param sections Pipe sections
   * @return Slug characteristics if slug released, null otherwise
   */
  public SlugCharacteristics checkForSlugRelease(AccumulationZone zone, PipeSection[] sections) {
    if (!zone.isOverflowing) {
      return null;
    }

    // Minimum time between slug releases to prevent rapid cycling
    double minTimeBetweenSlugs = 30.0; // seconds - longer interval for distinct slugs
    if (zone.timeSinceSlug < minTimeBetweenSlugs) {
      return null;
    }

    // Get downstream section to check if gas can push liquid out
    int lastIdx = zone.sectionIndices.get(zone.sectionIndices.size() - 1);
    if (lastIdx >= sections.length - 1) {
      return null;
    }

    // BUG FIX: Don't release a new slug if the downstream section is still in a slug body
    // This prevents immediate merging when a previous slug hasn't cleared the zone
    PipeSection downstreamSection = sections[lastIdx + 1];
    if (downstreamSection.isInSlugBody()) {
      return null;
    }

    PipeSection downstream = sections[lastIdx + 1];
    PipeSection zoneSection = sections[lastIdx];

    // Calculate driving pressure from gas compression behind liquid
    double gasPressure = zoneSection.getPressure();
    double downstreamPressure = downstream.getPressure();
    double pressureDiff = gasPressure - downstreamPressure;

    // Calculate hydrostatic resistance for uphill downstream section
    double elevDiff = downstream.getElevation() - zoneSection.getElevation();
    double hydrostaticHead = zoneSection.getLiquidDensity() * GRAVITY * Math.max(elevDiff, 0);

    // Slug releases when zone is overflowing AND one of:
    // 1. Pressure difference exceeds hydrostatic resistance
    // 2. Downstream section is going downhill (elevDiff < 0)
    // 3. Mixture velocity is sufficient
    // 4. Zone is critically full (>85% capacity)
    boolean pressureDriven = pressureDiff > hydrostaticHead * 0.3;
    boolean downhillRelease = elevDiff < -0.5; // Downhill by >0.5m
    boolean velocityDriven = downstream.getMixtureVelocity() > 0.3; // m/s
    boolean criticallyFull = zone.liquidVolume > 0.85 * zone.maxVolume;

    if (pressureDriven || downhillRelease || velocityDriven || criticallyFull) {
      SlugCharacteristics slug = new SlugCharacteristics();

      // BUG FIX: Slug should be compact when released, not span the entire zone
      // Calculate initial slug length based on accumulated volume and pipe area
      double pipeArea = zoneSection.getArea();
      // Slug body holdup should be high (nearly all liquid) - typically 0.95-0.98
      // This must be higher than the normal stratified flow holdup to show slug passage
      double slugHoldup = 0.98; // Near-100% liquid in slug body
      // V = L * A * holdup => L = V / (A * holdup)
      double initialLength = zone.liquidVolume / (pipeArea * slugHoldup);
      // Cap to reasonable range (5m to zone length)
      initialLength = Math.max(5.0, Math.min(initialLength, zone.endPosition - zone.startPosition));

      // Slug front starts at zone end, tail is behind by the slug length
      slug.frontPosition = zone.endPosition;
      slug.tailPosition = zone.endPosition - initialLength;
      slug.length = initialLength;
      slug.holdup = slugHoldup;
      slug.velocity = Math.max(downstream.getMixtureVelocity() * 1.2, 0.5); // At least 0.5 m/s
      slug.volume = zone.liquidVolume;
      slug.isTerrainInduced = true;

      // Reset zone after slug release - retain some liquid in the low point
      zone.liquidVolume *= 0.2; // 20% remains as film
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
   * Get critical holdup for slug initiation.
   *
   * @return Critical holdup value (0-1)
   */
  public double getCriticalHoldup() {
    return this.criticalHoldup;
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
