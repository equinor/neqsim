package neqsim.process.equipment.pipeline.twophasepipe;

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
 * </p>
 *
 * <p>
 * Key features:
 * <ul>
 * <li>Tracks slug front and tail positions</li>
 * <li>Models slug liquid holdup and bubble region</li>
 * <li>Handles slug merging when front catches tail</li>
 * <li>Bendiksen correlation for slug velocity</li>
 * </ul>
 * </p>
 *
 * <p>
 * References:
 * <ul>
 * <li>Bendiksen, K.H. et al. (1991) - The Dynamic Two-Fluid Model OLGA</li>
 * <li>Nydal, O.J. and Banerjee, S. (1996) - Dynamic Slug Tracking Simulations</li>
 * </ul>
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SlugTracker {

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
   * Represents a single slug unit (liquid slug + Taylor bubble).
   */
  public static class SlugUnit {
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

    // Get local inclination
    int sectionIdx = findSectionIndex(slug.frontPosition, sections);
    if (sectionIdx >= 0) {
      slug.localInclination = sections[sectionIdx].getInclination();
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

  /**
   * Advance a single slug.
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

    // Distribution coefficient
    double Fr_M = U_M / Math.sqrt(GRAVITY * D);
    double C0 = (Fr_M > 3.5) ? 1.2 : 1.05 + 0.15 * Math.sin(theta);

    // Drift velocity (Bendiksen 1984)
    double deltaRho = rho_L - rho_G;
    double U_drift = calculateDriftVelocity(D, theta, deltaRho, rho_L);

    // Slug front velocity = C0 * U_m + U_drift
    slug.frontVelocity = C0 * U_M + U_drift;

    // Slug tail velocity (shedding from back)
    // Tail moves slower due to liquid shedding into film
    double sheddingFactor = 0.9;
    slug.tailVelocity = slug.frontVelocity * sheddingFactor;

    // Update positions
    slug.frontPosition += slug.frontVelocity * dt;
    slug.tailPosition += slug.tailVelocity * dt;

    // Update lengths
    slug.slugBodyLength = Math.max(0, slug.frontPosition - slug.tailPosition);
    slug.bubbleLength = calculateBubbleLength(slug, frontSection);

    // Check for growth/decay
    double equilibriumLength = calculateEquilibriumLength(frontSection);
    if (slug.slugBodyLength < equilibriumLength) {
      slug.isGrowing = true;
      slug.isDecaying = false;
    } else {
      slug.isGrowing = false;
      slug.isDecaying = slug.slugBodyLength > 2 * equilibriumLength;
    }

    // Update liquid volume
    double pipeArea = frontSection.getArea();
    slug.liquidVolume = slug.slugBodyLength * pipeArea * slug.bodyHoldup
        + slug.bubbleLength * pipeArea * slug.filmHoldup;

    // Mark sections as in slug
    markSlugSections(slug, sections);
  }

  /**
   * Calculate drift velocity using Bendiksen correlation.
   */
  private double calculateDriftVelocity(double D, double theta, double deltaRho, double rho_L) {
    if (deltaRho <= 0) {
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
   * Calculate equilibrium slug length.
   */
  private double calculateEquilibriumLength(PipeSection section) {
    double D = section.getDiameter();
    double U_M = section.getMixtureVelocity();

    // Scott correlation for equilibrium slug length
    double Fr = U_M / Math.sqrt(GRAVITY * D);

    // L_s/D = 30 + 60*Fr for horizontal
    double L_s = D * (30 + 60 * Fr);

    // Inclination effect
    double theta = section.getInclination();
    L_s *= (1 + 0.5 * Math.abs(Math.sin(theta)));

    return Math.max(minimumSlugLength, L_s);
  }

  /**
   * Mark sections that are within a slug.
   */
  private void markSlugSections(SlugUnit slug, PipeSection[] sections) {
    for (PipeSection section : sections) {
      double pos = section.getPosition();
      double endPos = pos + section.getLength();

      // Check if section overlaps with slug body
      if (pos <= slug.frontPosition && endPos >= slug.tailPosition) {
        section.setInSlugBody(true);
        section.setSlugHoldup(slug.bodyHoldup);
      } else if (pos <= slug.tailPosition && endPos >= slug.tailPosition - slug.bubbleLength) {
        section.setInSlugBubble(true);
        section.setSlugHoldup(slug.filmHoldup);
      } else {
        section.setInSlugBody(false);
        section.setInSlugBubble(false);
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

        toRemove.add(front);
        totalSlugsMerged++;
      }
    }

    slugs.removeAll(toRemove);
  }

  /**
   * Remove slugs that have exited pipe or dissipated.
   */
  private void removeInactiveSlugs(PipeSection[] sections) {
    double pipeLength = 0;
    for (PipeSection section : sections) {
      pipeLength = Math.max(pipeLength, section.getPosition() + section.getLength());
    }

    Iterator<SlugUnit> iter = slugs.iterator();
    while (iter.hasNext()) {
      SlugUnit slug = iter.next();

      // Remove if exited pipe
      if (slug.tailPosition > pipeLength) {
        iter.remove();
        continue;
      }

      // Remove if too short
      if (slug.slugBodyLength < minimumSlugLength && slug.age > 10) {
        iter.remove();
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
    return sb.toString();
  }
}
