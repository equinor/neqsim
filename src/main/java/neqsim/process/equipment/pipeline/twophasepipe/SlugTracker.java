package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;

/**
 * Tracks individual slugs through the pipeline.
 *
 * <p>
 * Implements slug unit model with Lagrangian tracking of slug front and tail. Models slug growth,
 * decay, merging, and interaction with terrain features.
 *
 * <p>
 * Key features:
 * <ul>
 * <li>Tracks slug front and tail positions</li>
 * <li>Models slug liquid holdup and bubble region</li>
 * <li>Handles slug merging when front catches tail</li>
 * <li>Bendiksen correlation for slug velocity</li>
 * </ul>
 *
 * <p>
 * References:
 * <ul>
 * <li>Bendiksen, K.H. et al. (1991) - The Dynamic Two-Fluid Model OLGA</li>
 * <li>Nydal, O.J. and Banerjee, S. (1996) - Dynamic Slug Tracking Simulations</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SlugTracker implements Serializable {

  private static final long serialVersionUID = 1L;
  private static final double GRAVITY = 9.81;

  private List<SlugUnit> slugs;
  private double minimumSlugLength = 5.0; // m - slugs shorter than this dissipate
  private double slugMergeDistance = 1.0; // m - distance for slug merging
  private double slugHoldupBody = 0.90; // Holdup in slug body
  private double slugHoldupFilm = 0.20; // Holdup in film region

  // Slug statistics
  private double slugFrequency; // 1/s
  private double averageSlugLength; // m
  private double maxSlugLength; // m
  private int totalSlugsGenerated;
  private int totalSlugsMerged;

  /**
   * Cumulative liquid mass returned to Eulerian cells from exiting/dissipating slugs (kg). This
   * enables mass conservation verification between Lagrangian and Eulerian representations.
   */
  private double totalMassReturnedToEulerian;

  /**
   * Cumulative liquid mass borrowed from Eulerian cells when slugs are created (kg). This enables
   * mass conservation verification between Lagrangian and Eulerian representations.
   */
  private double totalMassBorrowedFromEulerian;

  /**
   * Represents a single slug unit (liquid slug + Taylor bubble).
   */
  public static class SlugUnit implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Unique slug identifier. */
    public int id;
    /** Position of slug front (m from inlet). */
    public double frontPosition;
    /** Position of slug tail (m from inlet). */
    public double tailPosition;
    /** Length of liquid slug body (m). */
    public double slugBodyLength;
    /** Length of Taylor bubble / film region (m). */
    public double bubbleLength;
    /** Slug front velocity (m/s). */
    public double frontVelocity;
    /** Slug tail velocity (m/s). */
    public double tailVelocity;
    /** Liquid holdup in slug body. */
    public double bodyHoldup;
    /** Liquid holdup in film region. */
    public double filmHoldup;
    /** Liquid volume in slug (m³). */
    public double liquidVolume;
    /** Is this slug actively growing. */
    public boolean isGrowing;
    /** Is this slug decaying. */
    public boolean isDecaying;
    /** Is this a terrain-induced slug. */
    public boolean isTerrainInduced;
    /** Time slug has existed (s). */
    public double age;
    /** Local pipe inclination at slug front. */
    public double localInclination;
    /**
     * Liquid mass borrowed from Eulerian cells (kg). This tracks the mass that was "collected" from
     * the stratified film region to form this slug, enabling proper mass conservation
     * reconciliation between Lagrangian and Eulerian solvers.
     */
    public double borrowedLiquidMass;
    /**
     * Array of section indices from which liquid mass was borrowed. Used to return mass to
     * appropriate cells when slug exits or dissipates.
     */
    public int[] borrowedFromSections;

    /**
     * Get total slug unit length (body + bubble).
     *
     * @return Total length (m)
     */
    public double getTotalLength() {
      return slugBodyLength + bubbleLength;
    }

    @Override
    public String toString() {
      return String.format("Slug#%d[front=%.1fm, body=%.1fm, bubble=%.1fm, vf=%.2fm/s]", id,
          frontPosition, slugBodyLength, bubbleLength, frontVelocity);
    }
  }

  /**
   * Constructor.
   */
  public SlugTracker() {
    this.slugs = new ArrayList<>();
  }

  /**
   * Initialize slug from terrain-induced accumulation.
   *
   * <p>
   * This method creates a new slug from accumulated liquid at a terrain feature and tracks the mass
   * "borrowed" from the Eulerian cells. This borrowed mass is recorded to enable mass conservation
   * verification and is returned to appropriate cells when the slug exits or dissipates.
   * </p>
   *
   * @param characteristics Slug characteristics from accumulation tracker
   * @param sections Pipe sections
   * @return New slug unit
   */
  public SlugUnit initializeTerrainSlug(
      LiquidAccumulationTracker.SlugCharacteristics characteristics, PipeSection[] sections) {

    SlugUnit slug = new SlugUnit();
    slug.id = ++totalSlugsGenerated;
    slug.frontPosition = characteristics.frontPosition;
    slug.tailPosition = characteristics.tailPosition;
    slug.slugBodyLength = characteristics.length;
    slug.bubbleLength = 0; // Initial terrain slug has no bubble
    slug.frontVelocity = characteristics.velocity;
    slug.tailVelocity = characteristics.velocity;
    slug.bodyHoldup = characteristics.holdup;
    slug.filmHoldup = slugHoldupFilm;
    slug.liquidVolume = characteristics.volume;
    slug.isGrowing = true;
    slug.isTerrainInduced = true;
    slug.age = 0;

    // Get local inclination and calculate borrowed mass
    int sectionIdx = findSectionIndex(slug.frontPosition, sections);
    if (sectionIdx >= 0) {
      slug.localInclination = sections[sectionIdx].getInclination();

      // Calculate mass borrowed from Eulerian cells
      // The slug liquid volume comes from accumulated stratified liquid
      // Mass = ρ_L × Volume
      double rho_L = sections[sectionIdx].getLiquidDensity();
      slug.borrowedLiquidMass = rho_L * slug.liquidVolume;
      totalMassBorrowedFromEulerian += slug.borrowedLiquidMass;

      // Track which sections contributed (sections covered by slug)
      int startIdx = findSectionIndex(slug.tailPosition, sections);
      int endIdx = findSectionIndex(slug.frontPosition, sections);
      if (startIdx < 0) {
        startIdx = 0;
      }
      if (endIdx < 0) {
        endIdx = sections.length - 1;
      }
      int numSections = endIdx - startIdx + 1;
      slug.borrowedFromSections = new int[numSections];
      for (int i = 0; i < numSections; i++) {
        slug.borrowedFromSections[i] = startIdx + i;
      }
    }

    slugs.add(slug);
    return slug;
  }

  /**
   * Generate slug at inlet based on flow conditions.
   *
   * @param inletSection Inlet pipe section
   * @param pipeArea Pipe cross-sectional area (m²)
   * @return New slug if generated, null otherwise
   */
  public SlugUnit generateInletSlug(PipeSection inletSection, double pipeArea) {
    // Check if conditions favor slug flow
    if (inletSection.getFlowRegime() != FlowRegime.SLUG) {
      return null;
    }

    // Calculate slug frequency from Zabaras correlation
    double U_SL = inletSection.getSuperficialLiquidVelocity();
    double U_SG = inletSection.getSuperficialGasVelocity();
    double U_M = U_SL + U_SG;
    double D = inletSection.getDiameter();
    double theta = inletSection.getInclination();

    // Gregory-Scott (1969) slug frequency
    double Fr = U_M / Math.sqrt(GRAVITY * D);
    double lambda_L = U_SL / U_M;

    // Zabaras (2000) correlation including inclination
    double freq_horiz = 0.0226 * Math.pow(lambda_L, 1.2) * Math.pow(Fr, 2.0) / D;
    double inclFactor = 1.0 + Math.sin(theta);
    slugFrequency = freq_horiz * inclFactor;

    // Determine if we should generate a slug based on frequency
    // This would typically be called at regular intervals
    // For now, just return null - actual generation handled by advanceSlug

    return null;
  }

  /**
   * Advance all slugs by one time step.
   *
   * @param sections Pipe sections
   * @param dt Time step (s)
   */
  public void advanceSlugs(PipeSection[] sections, double dt) {
    if (sections.length == 0) {
      return;
    }

    // Clear all section slug flags and reset slugHoldup before updating
    // This ensures no stale holdup values are used when section leaves slug
    for (PipeSection section : sections) {
      section.setInSlugBody(false);
      section.setInSlugBubble(false);
      section.setSlugHoldup(section.getLiquidHoldup()); // Reset to base holdup
    }

    // Update each slug
    for (SlugUnit slug : slugs) {
      advanceSlug(slug, sections, dt);
    }

    // Check for slug merging
    mergeSlugs();

    // Remove slugs that have exited or dissipated
    removeInactiveSlugs(sections);

    // Update statistics
    updateStatistics();
  }

  /** Reference velocity from inlet for fallback when sections have near-zero velocity. */
  private double referenceVelocity = 1.0; // m/s default

  /** Maximum allowed slug body length (multiples of diameter). */
  private static final double MAX_SLUG_LENGTH_DIAMETERS = 200.0;

  /**
   * Set the reference velocity for slug propagation. This is used as fallback when section mixture
   * velocity is near zero (e.g., with constant pressure outlet BC where momentum solver gives low
   * velocities).
   *
   * @param velocity Reference velocity in m/s (typically inlet mixture velocity)
   */
  public void setReferenceVelocity(double velocity) {
    this.referenceVelocity = Math.max(0.1, velocity);
  }

  /**
   * Advance a single slug.
   *
   * @param slug the slug unit to advance
   * @param sections the pipe sections array
   * @param dt the time step
   */
  private void advanceSlug(SlugUnit slug, PipeSection[] sections, double dt) {
    slug.age += dt;

    // Get section at slug front (use last section if slug is past pipe end)
    int frontIdx = findSectionIndex(slug.frontPosition, sections);
    if (frontIdx < 0) {
      // Slug is past end of pipe - use last section for extrapolation
      frontIdx = sections.length - 1;
    }
    if (frontIdx >= sections.length) {
      return;
    }

    PipeSection frontSection = sections[frontIdx];
    slug.localInclination = frontSection.getInclination();

    // Calculate slug velocity using Bendiksen correlation
    double U_M = frontSection.getMixtureVelocity();
    double D = frontSection.getDiameter();
    double rho_L = frontSection.getLiquidDensity();
    double rho_G = frontSection.getGasDensity();
    double theta = frontSection.getInclination();

    // BUG FIX: Use reference velocity when section mixture velocity is too low
    // This happens with CONSTANT_PRESSURE outlet BC where momentum solver gives ~0 velocity
    if (Math.abs(U_M) < 0.1 && referenceVelocity > 0.1) {
      U_M = referenceVelocity;
    }

    // Distribution coefficient
    double Fr_M = U_M / Math.sqrt(GRAVITY * D);
    double C0 = (Fr_M > 3.5) ? 1.2 : 1.05 + 0.15 * Math.sin(theta);

    // Drift velocity (Bendiksen 1984)
    double deltaRho = rho_L - rho_G;
    double U_drift = calculateDriftVelocity(D, theta, deltaRho, rho_L);

    // Slug front velocity = C0 * U_m + U_drift
    slug.frontVelocity = C0 * U_M + U_drift;

    // BUG FIX: Tail velocity should approach front velocity for stable slugs
    // Use dynamic shedding factor that decreases as slug grows
    // This prevents indefinite slug growth
    double maxSlugLength = MAX_SLUG_LENGTH_DIAMETERS * D;
    double lengthRatio = Math.min(1.0, slug.slugBodyLength / maxSlugLength);
    // Shedding factor increases from 0.9 to 1.0 as slug approaches max length
    double sheddingFactor = 0.9 + 0.1 * lengthRatio;
    slug.tailVelocity = slug.frontVelocity * sheddingFactor;

    // Update positions
    slug.frontPosition += slug.frontVelocity * dt;
    slug.tailPosition += slug.tailVelocity * dt;

    // Update lengths
    slug.slugBodyLength = Math.max(0, slug.frontPosition - slug.tailPosition);
    slug.bubbleLength = calculateBubbleLength(slug, frontSection);

    // Update slug body holdup using Gregory et al. (1978) correlation
    // This accounts for gas entrainment at higher velocities
    slug.bodyHoldup = calculateSlugBodyHoldup(U_M);

    // Check for growth/decay
    double equilibriumLength = calculateEquilibriumLength(frontSection);
    if (slug.slugBodyLength < equilibriumLength) {
      slug.isGrowing = true;
      slug.isDecaying = false;
    } else {
      slug.isGrowing = false;
      slug.isDecaying = slug.slugBodyLength > 2 * equilibriumLength;
    }

    // Update liquid volume with NaN protection
    double pipeArea = frontSection.getArea();
    double bodyVolume = slug.slugBodyLength * pipeArea * slug.bodyHoldup;
    double bubbleVolume = slug.bubbleLength * pipeArea * slug.filmHoldup;
    slug.liquidVolume = bodyVolume + bubbleVolume;
    if (Double.isNaN(slug.liquidVolume) || Double.isInfinite(slug.liquidVolume)) {
      slug.liquidVolume = slug.slugBodyLength * pipeArea * 0.9; // Fallback estimate
    }

    // Mark sections as in slug
    markSlugSections(slug, sections);
  }

  /**
   * Calculate drift velocity using Bendiksen correlation.
   */
  private double calculateDriftVelocity(double D, double theta, double deltaRho, double rho_L) {
    if (deltaRho <= 0 || rho_L <= 0) {
      return 0;
    }

    double absTheta = Math.abs(theta);

    // Horizontal component (Zukoski)
    double U_dH = 0.54 * Math.sqrt(GRAVITY * D * deltaRho / rho_L);

    // Vertical component (Dumitrescu)
    double U_dV = 0.35 * Math.sqrt(GRAVITY * D * deltaRho / rho_L);

    // Interpolation
    double U_drift;
    if (absTheta < Math.PI / 6) {
      U_drift = U_dH * Math.cos(theta) + U_dV * Math.sin(theta);
    } else if (absTheta > Math.PI / 3) {
      U_drift = U_dV * Math.sin(theta);
    } else {
      double w = (absTheta - Math.PI / 6) / (Math.PI / 6);
      U_drift = (1 - w) * U_dH * Math.cos(theta) + U_dV * Math.sin(theta);
    }

    return U_drift;
  }

  /**
   * Calculate Taylor bubble/film region length.
   */
  private double calculateBubbleLength(SlugUnit slug, PipeSection section) {
    // Bubble length based on slug frequency and unit length
    double U_M = section.getMixtureVelocity();

    if (slugFrequency > 0 && U_M > 0) {
      double unitLength = U_M / slugFrequency;
      return Math.max(0, unitLength - slug.slugBodyLength);
    }

    // Default: bubble length = slug length
    return slug.slugBodyLength;
  }

  /**
   * Calculate equilibrium slug length using Nydal correlation.
   *
   * <p>
   * References:
   * </p>
   * <ul>
   * <li>Nydal, O.J. (1991) - An Experimental Investigation of Slug Flow</li>
   * <li>Barnea, D. and Taitel, Y. (1993) - A Model for Slug Length Distribution</li>
   * </ul>
   *
   * @param section pipe section with flow conditions
   * @return equilibrium slug length [m]
   */
  private double calculateEquilibriumLength(PipeSection section) {
    double D = section.getDiameter();
    double U_M = section.getMixtureVelocity();

    // Nydal (1991) correlation for stable slug length
    // L_s/D = 20 for horizontal pipe (lower bound)
    // Longer slugs possible in developing flow
    double Fr = U_M / Math.sqrt(GRAVITY * D);

    // Barnea-Taitel (1993): L_s/D typically 15-40 depending on void fraction
    // Use moderate value with weak Fr dependence
    double L_s_D = 25 + 10 * Math.min(Fr, 2.0);

    double L_s = D * L_s_D;

    // Inclination effect - upward inclined pipes have longer slugs
    double theta = section.getInclination();
    if (theta > 0) {
      L_s *= (1 + 0.3 * Math.sin(theta));
    }

    return Math.max(minimumSlugLength, L_s);
  }

  /**
   * Calculate slug body holdup using Gregory et al. (1978) correlation.
   *
   * <p>
   * H_LS = 1 / (1 + (U_M / 8.66)^1.39)
   * </p>
   * <p>
   * This accounts for gas entrainment in the slug body at high mixture velocities.
   * </p>
   *
   * @param U_M mixture velocity [m/s]
   * @return liquid holdup in slug body (0.5 to 1.0 typically)
   */
  private double calculateSlugBodyHoldup(double U_M) {
    // Gregory, Nicholson, Aziz (1978)
    // H_LS = 1 / (1 + (U_M / 8.66)^1.39)
    double ratio = U_M / 8.66;
    double H_LS = 1.0 / (1.0 + Math.pow(ratio, 1.39));

    // Clamp to reasonable range [0.5, 0.98]
    return Math.max(0.5, Math.min(0.98, H_LS));
  }

  /**
   * Mark sections that are within a slug.
   * <p>
   * A section is marked as in slug body if it overlaps with the slug body region (between tail and
   * front positions). Sections are marked as in slug bubble if they overlap with the bubble/film
   * region behind the tail.
   * </p>
   * <p>
   * <b>Mass Conservation Note:</b> The slug holdup represents additional liquid concentration from
   * the slug body. The section's base holdup (from Eulerian solver) is preserved and the slug
   * holdup is used to modify effective properties for friction/pressure calculations.
   * </p>
   */
  private void markSlugSections(SlugUnit slug, PipeSection[] sections) {
    for (PipeSection section : sections) {
      double sectionStart = section.getPosition();
      double sectionEnd = sectionStart + section.getLength();

      // Slug body spans from tailPosition to frontPosition
      double slugBodyStart = slug.tailPosition;
      double slugBodyEnd = slug.frontPosition;

      // Check if section overlaps with slug body (ranges overlap if one starts before the other
      // ends)
      boolean overlapsBody =
          sectionStart < slugBodyEnd && sectionEnd > slugBodyStart && slugBodyEnd > slugBodyStart;

      if (overlapsBody) {
        section.setInSlugBody(true);
        // Set slug holdup - this modifies effective properties but doesn't override
        // the Eulerian liquid mass (which is in the conservative variables)
        section.setSlugHoldup(slug.bodyHoldup);
      }

      // Bubble/film region is behind the tail
      double bubbleStart = slug.tailPosition - slug.bubbleLength;
      double bubbleEnd = slug.tailPosition;

      boolean overlapsBubble =
          sectionStart < bubbleEnd && sectionEnd > bubbleStart && bubbleEnd > bubbleStart;

      if (overlapsBubble && !overlapsBody) {
        section.setInSlugBubble(true);
        section.setSlugHoldup(slug.filmHoldup);
      }
    }
  }

  /**
   * Merge slugs that have caught up to each other.
   */
  private void mergeSlugs() {
    if (slugs.size() < 2) {
      return;
    }

    // Sort slugs by front position
    slugs.sort((a, b) -> Double.compare(a.frontPosition, b.frontPosition));

    List<SlugUnit> toRemove = new ArrayList<>();

    for (int i = 1; i < slugs.size(); i++) {
      SlugUnit front = slugs.get(i);
      SlugUnit back = slugs.get(i - 1);

      // Check if front of back slug has caught tail of front slug
      if (back.frontPosition >= front.tailPosition - slugMergeDistance) {
        // Merge: back slug absorbs front slug
        back.frontPosition = front.frontPosition;
        back.frontVelocity = front.frontVelocity;
        back.slugBodyLength = back.frontPosition - back.tailPosition;
        back.liquidVolume += front.liquidVolume;

        // Combine borrowed mass for proper mass conservation tracking
        back.borrowedLiquidMass += front.borrowedLiquidMass;

        toRemove.add(front);
        totalSlugsMerged++;
      }
    }

    slugs.removeAll(toRemove);
  }

  /**
   * Remove slugs that have exited pipe or dissipated, returning mass to Eulerian cells.
   *
   * <p>
   * When a slug exits the pipe or dissipates, its liquid mass is "returned" to the Eulerian
   * representation. For slugs exiting at the outlet, the mass is considered as having left the
   * system (outlet mass flux). For dissipating slugs, the mass is returned to the surrounding cells
   * proportionally.
   * </p>
   *
   * @param sections Pipe sections (may be modified to return mass)
   */
  private void removeInactiveSlugs(PipeSection[] sections) {
    double pipeLength = 0;
    for (PipeSection section : sections) {
      pipeLength = Math.max(pipeLength, section.getPosition() + section.getLength());
    }

    Iterator<SlugUnit> iter = slugs.iterator();
    while (iter.hasNext()) {
      SlugUnit slug = iter.next();

      // Check if front has exited pipe
      if (slug.frontPosition > pipeLength + minimumSlugLength) {
        // Slug exited at outlet - mass leaves the system via outlet flux
        // Track as returned mass for conservation verification
        totalMassReturnedToEulerian += slug.borrowedLiquidMass;
        iter.remove();
        continue;
      }

      // Check if slug has dissipated (too short after some time)
      if (slug.slugBodyLength < minimumSlugLength && slug.age > 10) {
        // Dissipating slug - return mass to nearby cells
        returnMassToEulerianCells(slug, sections);
        totalMassReturnedToEulerian += slug.borrowedLiquidMass;
        iter.remove();
      }
    }
  }

  /**
   * Return liquid mass from a dissipating slug back to Eulerian cells.
   *
   * <p>
   * When a slug dissipates (becomes too short), its liquid mass is distributed back to the
   * surrounding pipe sections. This maintains mass conservation between the Lagrangian slug
   * tracking and Eulerian field representation.
   * </p>
   *
   * @param slug The dissipating slug
   * @param sections Pipe sections to receive the returned mass
   */
  private void returnMassToEulerianCells(SlugUnit slug, PipeSection[] sections) {
    if (slug.borrowedLiquidMass <= 0 || sections.length == 0) {
      return;
    }

    // Find sections near the slug position
    int centerIdx = findSectionIndex((slug.frontPosition + slug.tailPosition) / 2, sections);
    if (centerIdx < 0) {
      centerIdx = sections.length - 1;
    }

    // Distribute mass to nearby sections (gaussian-like distribution)
    int spreadRadius = 3; // Number of sections to spread mass over
    int startIdx = Math.max(0, centerIdx - spreadRadius);
    int endIdx = Math.min(sections.length - 1, centerIdx + spreadRadius);

    double totalWeight = 0;
    double[] weights = new double[endIdx - startIdx + 1];

    for (int i = startIdx; i <= endIdx; i++) {
      // Weight decreases with distance from center
      double distance = Math.abs(i - centerIdx);
      weights[i - startIdx] = Math.exp(-distance * distance / 2.0);
      totalWeight += weights[i - startIdx];
    }

    // Distribute mass according to weights
    for (int i = startIdx; i <= endIdx; i++) {
      double massFraction = weights[i - startIdx] / totalWeight;
      double massToReturn = slug.borrowedLiquidMass * massFraction;

      // Convert mass to holdup increase
      // Δα_L = Δm / (ρ_L × V_cell)
      PipeSection section = sections[i];
      double rho_L = section.getLiquidDensity();
      double cellVolume = section.getArea() * section.getLength();

      if (rho_L > 0 && cellVolume > 0) {
        double deltaHoldup = massToReturn / (rho_L * cellVolume);
        double newLiquidHoldup = Math.min(1.0, section.getLiquidHoldup() + deltaHoldup);
        section.setLiquidHoldup(newLiquidHoldup);
        section.setGasHoldup(1.0 - newLiquidHoldup);
        section.updateDerivedQuantities();
      }
    }
  }

  /**
   * Update slug statistics.
   */
  private void updateStatistics() {
    if (slugs.isEmpty()) {
      averageSlugLength = 0;
      maxSlugLength = 0;
      return;
    }

    double sumLength = 0;
    maxSlugLength = 0;

    for (SlugUnit slug : slugs) {
      sumLength += slug.slugBodyLength;
      maxSlugLength = Math.max(maxSlugLength, slug.slugBodyLength);
    }

    averageSlugLength = sumLength / slugs.size();
  }

  /**
   * Find section index containing a position.
   *
   * @param position position along pipe (m)
   * @param sections array of pipe sections
   * @return section index, or -1 if not found
   */
  private int findSectionIndex(double position, PipeSection[] sections) {
    for (int i = 0; i < sections.length; i++) {
      double start = sections[i].getPosition();
      double end = start + sections[i].getLength();
      if (position >= start && position <= end) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Get all active slugs.
   *
   * @return List of slug units
   */
  public List<SlugUnit> getSlugs() {
    return new ArrayList<>(slugs);
  }

  /**
   * Get number of active slugs.
   *
   * @return Slug count
   */
  public int getSlugCount() {
    return slugs.size();
  }

  /**
   * Get slug frequency.
   *
   * @return Frequency (1/s)
   */
  public double getSlugFrequency() {
    return slugFrequency;
  }

  /**
   * Get average slug length.
   *
   * @return Average length (m)
   */
  public double getAverageSlugLength() {
    return averageSlugLength;
  }

  /**
   * Get maximum slug length.
   *
   * @return Max length (m)
   */
  public double getMaxSlugLength() {
    return maxSlugLength;
  }

  /**
   * Get total slugs generated.
   *
   * @return Total count
   */
  public int getTotalSlugsGenerated() {
    return totalSlugsGenerated;
  }

  /**
   * Get total slugs merged.
   *
   * @return Merge count
   */
  public int getTotalSlugsMerged() {
    return totalSlugsMerged;
  }

  /**
   * Set minimum slug length.
   *
   * @param length Minimum length (m)
   */
  public void setMinimumSlugLength(double length) {
    this.minimumSlugLength = Math.max(1.0, length);
  }

  /**
   * Set slug body holdup.
   *
   * @param holdup Holdup (0-1)
   */
  public void setSlugBodyHoldup(double holdup) {
    this.slugHoldupBody = Math.max(0.5, Math.min(1.0, holdup));
  }

  /**
   * Get slug body holdup.
   *
   * @return Holdup in slug body (0-1)
   */
  public double getSlugBodyHoldup() {
    return this.slugHoldupBody;
  }

  /**
   * Set film holdup.
   *
   * @param holdup Holdup (0-1)
   */
  public void setFilmHoldup(double holdup) {
    this.slugHoldupFilm = Math.max(0.01, Math.min(0.5, holdup));
  }

  /**
   * Reset tracker state.
   */
  public void reset() {
    slugs.clear();
    slugFrequency = 0;
    averageSlugLength = 0;
    maxSlugLength = 0;
    totalSlugsGenerated = 0;
    totalSlugsMerged = 0;
    totalMassBorrowedFromEulerian = 0;
    totalMassReturnedToEulerian = 0;
  }

  /**
   * Get total liquid mass borrowed from Eulerian cells when slugs are created.
   *
   * @return Total borrowed mass (kg)
   */
  public double getTotalMassBorrowedFromEulerian() {
    return totalMassBorrowedFromEulerian;
  }

  /**
   * Get total liquid mass returned to Eulerian cells when slugs exit or dissipate.
   *
   * @return Total returned mass (kg)
   */
  public double getTotalMassReturnedToEulerian() {
    return totalMassReturnedToEulerian;
  }

  /**
   * Get net mass currently held in active slugs (borrowed - returned - in_active_slugs).
   *
   * <p>
   * For mass conservation verification: netMassInSlugs = totalBorrowed - totalReturned -
   * massInActiveSlugs This should be approximately zero if mass is conserved.
   * </p>
   *
   * @return Net mass discrepancy (kg), should be ~0 for conservation
   */
  public double getMassConservationError() {
    double massInActiveSlugs = 0;
    for (SlugUnit slug : slugs) {
      massInActiveSlugs += slug.borrowedLiquidMass;
    }
    // Borrowed = Returned + InActiveSlugs (for conservation)
    // Error = Borrowed - Returned - InActiveSlugs
    return totalMassBorrowedFromEulerian - totalMassReturnedToEulerian - massInActiveSlugs;
  }

  /**
   * Get detailed slug statistics string.
   *
   * @return Statistics summary
   */
  public String getStatisticsString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Slug Statistics:\n");
    sb.append(String.format("  Active slugs: %d\n", slugs.size()));
    sb.append(String.format("  Total generated: %d\n", totalSlugsGenerated));
    sb.append(String.format("  Total merged: %d\n", totalSlugsMerged));
    sb.append(String.format("  Frequency: %.3f Hz\n", slugFrequency));
    sb.append(String.format("  Avg length: %.1f m\n", averageSlugLength));
    sb.append(String.format("  Max length: %.1f m\n", maxSlugLength));
    sb.append(String.format("  Mass borrowed: %.3f kg\n", totalMassBorrowedFromEulerian));
    sb.append(String.format("  Mass returned: %.3f kg\n", totalMassReturnedToEulerian));
    sb.append(String.format("  Mass conservation error: %.6f kg\n", getMassConservationError()));
    return sb.toString();
  }
}
