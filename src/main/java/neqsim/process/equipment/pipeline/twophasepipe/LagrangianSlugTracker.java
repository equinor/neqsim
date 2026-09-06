package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;

/**
 * Correlation-based Lagrangian slug tracking overlay for the Eulerian pipeline model.
 *
 * <p>
 * Tracks individual slugs through the pipeline, modeling:
 * </p>
 * <ul>
 * <li>Hydrodynamic slug initiation from flow instabilities</li>
 * <li>Terrain-induced slug initiation at low points</li>
 * <li>Slug growth by liquid pickup from stratified film</li>
 * <li>Slug decay by shedding to trailing Taylor bubble</li>
 * <li>Slug-bubble unit dynamics (front/tail velocities)</li>
 * <li>Slug merging when front catches preceding tail</li>
 * <li>Wake effects between consecutive slugs</li>
 * <li>Statistical output (length distribution, frequency, volumes)</li>
 * </ul>
 *
 * <p>
 * <b>Coupling modes:</b> The default is a one-way closure overlay whose empirical front/tail velocities determine
 * geometry. Opt-in conservative film coupling uses liquid continuity to determine interface motion and supplies
 * {@link SlugFilmCoupling} subcell states to an owning finite-volume solver. In either mode the Eulerian solution owns
 * all physical inventory; marker creation and dissolution do not independently deposit or withdraw mass. Geometric
 * borrowed/returned/exited statistics remain compatibility diagnostics. Section positions denote cell centers. These
 * implementations do not establish OLGA equivalence or replace independent experimental qualification.
 * </p>
 * <ul>
 * <li>Slugs are tracked as discrete entities with position, length, velocity</li>
 * <li>Film region between slugs is modeled as stratified flow</li>
 * <li>Mass exchange between slug body and film is computed from pickup/shedding</li>
 * <li>Slug frequency from mechanistic or empirical correlations</li>
 * </ul>
 *
 * <p>
 * <b>References:</b>
 * </p>
 * <ul>
 * <li>Bendiksen, K.H. et al. (1991) - The Dynamic Two-Fluid Model OLGA</li>
 * <li>Nydal, O.J. and Banerjee, S. (1996) - Dynamic Slug Tracking Simulations</li>
 * <li>Kjølaas, J. et al. (2013) - Lagrangian slug flow modeling and sensitivity</li>
 * <li>Issa, R.I. and Kempf, M.H.W. (2003) - Simulation of slug flow in horizontal and nearly horizontal pipes</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 2.0
 */
public class LagrangianSlugTracker implements Serializable {

  private static final long serialVersionUID = 2L;
  private static final double GRAVITY = 9.81;

  // ============== Slug Unit Definition ==============

  /**
   * Represents a single slug-bubble unit in Lagrangian tracking.
   *
   * <p>
   * Each unit consists of:
   * </p>
   * <ul>
   * <li><b>Slug body:</b> High-holdup liquid region (H_LS ~ 0.7-1.0)</li>
   * <li><b>Taylor bubble:</b> Elongated gas pocket above film</li>
   * <li><b>Film region:</b> Stratified liquid below Taylor bubble</li>
   * </ul>
   */
  public static class SlugBubbleUnit implements Serializable {
    private static final long serialVersionUID = 1L;

    // === Identification ===
    /** Unique slug identifier. */
    public int id;
    /** Generation source: INLET, TERRAIN, INSTABILITY. */
    public SlugSource source;

    // === Position and Geometry ===
    /** Position of slug front (m from inlet). */
    public double frontPosition;
    /** Position of slug tail (m from inlet). */
    public double tailPosition;
    /** Length of liquid slug body (m). */
    public double slugLength;
    /** Length of Taylor bubble / film region behind tail (m). */
    public double bubbleLength;
    /** Pipe diameter at current location (m). */
    public double localDiameter;
    /** Pipe area at current location (m²). */
    public double localArea;
    /** Local pipe inclination (radians). */
    public double localInclination;

    // === Velocities ===
    /** Slug front (nose) velocity (m/s). */
    public double frontVelocity;
    /** Slug tail velocity (m/s). */
    public double tailVelocity;
    /** Taylor bubble nose velocity (m/s). */
    public double bubbleNoseVelocity;
    /** Liquid velocity in slug body (m/s). */
    public double slugLiquidVelocity;
    /** Film velocity behind slug (m/s). */
    public double filmVelocity;

    // === Holdup and Volume ===
    /** Liquid holdup in slug body (0.5-1.0). */
    public double slugHoldup;
    /** Liquid holdup in film region (0.01-0.5). */
    public double filmHoldup;
    /** Liquid volume in slug body (m³). */
    public double slugLiquidVolume;
    /** Liquid volume in film region (m³). */
    public double filmLiquidVolume;

    // === Mass Conservation ===
    /** Liquid mass in slug body (kg). */
    public double slugLiquidMass;
    /** Rate of liquid pickup at front (kg/s). */
    public double pickupRate;
    /** Rate of liquid shedding at tail (kg/s). */
    public double sheddingRate;
    /** Net mass change rate (kg/s). */
    public double netMassRate;
    /** Whether this marker uses conservative Eulerian slug/film reconstruction. */
    public boolean usesConservativeFilmCoupling;
    /** Whether an accepted continuity calculation has supplied the body-velocity reconstruction target. */
    public boolean hasConservativeVelocity;
    /** In-domain gas, oil, and water masses assigned by Eulerian subcell reconstruction (kg). */
    public double[] phaseMassKg = new double[3];
    /** In-domain phase momenta assigned by Eulerian subcell reconstruction (kg m/s). */
    public double[] phaseMomentumKgMPerSecond = new double[3];
    /** In-domain phase volumes assigned by Eulerian subcell reconstruction (m3). */
    public double[] phaseVolumeM3 = new double[3];
    /** In-domain total energy assigned by Eulerian subcell reconstruction (J). */
    public double totalEnergyJ;

    // === State Flags ===
    /** Is slug currently growing. */
    public boolean isGrowing;
    /** Is slug decaying/dissipating. */
    public boolean isDecaying;
    /** Is this a terrain-induced slug. */
    public boolean isTerrainInduced;
    /** Has this slug completely left the pipe through either boundary. */
    public boolean hasExited;
    /** Age since creation (s). */
    public double age;
    /** Distance traveled since creation (m). */
    public double distanceTraveled;

    // === Wake Effects ===
    /** Distance to preceding slug's tail (m). */
    public double distanceToPrecedingSlug;
    /** Is this slug in wake of preceding slug. */
    public boolean inWakeRegion;
    /** Wake coefficient (1.0 = no wake, &gt;1.0 = accelerated). */
    public double wakeCoefficient;

    /**
     * Get total slug-bubble unit length.
     *
     * @return total length (m)
     */
    public double getTotalUnitLength() {
      return slugLength + bubbleLength;
    }

    /**
     * Get total liquid volume in unit (slug + film).
     *
     * @return liquid volume (m³)
     */
    public double getTotalLiquidVolume() {
      return slugLiquidVolume + filmLiquidVolume;
    }

    @Override
    public String toString() {
      return String.format("Slug#%d[pos=%.1fm, Ls=%.1fm, Lb=%.1fm, vf=%.2fm/s, H=%.2f, %s]", id, frontPosition,
          slugLength, bubbleLength, frontVelocity, slugHoldup,
          isGrowing ? "GROWING" : (isDecaying ? "DECAYING" : "STABLE"));
    }
  }

  /**
   * Source of slug generation.
   */
  public enum SlugSource {
    /** Generated at inlet from slug flow regime. */
    INLET,
    /** Generated from terrain feature (low point). */
    TERRAIN,
    /** Generated from Kelvin-Helmholtz instability. */
    INSTABILITY,
    /** Generated from random perturbation (stochastic). */
    RANDOM
  }

  // ============== Tracking State ==============

  /** List of active slugs in the pipeline. */
  private List<SlugBubbleUnit> slugs;

  /** Observational probes, lazily initialized for compatibility with older serialized trackers. */
  private List<SlugProbe> probes;

  /** Counter for slug IDs. */
  private int slugIdCounter = 0;

  /** Random number generator for stochastic initiation. */
  private Random random;

  // ============== Model Parameters ==============

  // --- Slug Initiation ---
  /** Enable hydrodynamic slug generation at inlet. */
  private boolean enableInletSlugGeneration = true;

  /** Enable terrain-induced slug generation. */
  private boolean enableTerrainSlugGeneration = true;

  /** Enable random/stochastic slug initiation. */
  private boolean enableStochasticInitiation = false;

  /** Minimum liquid holdup for slug initiation. */
  private double initiationHoldupThreshold = 0.25;

  /** Time since last inlet slug generation (s). */
  private double timeSinceLastInletSlug = 0;

  // --- Slug Geometry ---
  /** Minimum stable slug length (diameters). */
  private double minSlugLengthDiameters = 12.0;

  /** Maximum slug length (diameters). */
  private double maxSlugLengthDiameters = 300.0;

  /** Initial slug length at generation (diameters). */
  private double initialSlugLengthDiameters = 20.0;

  // --- Holdup ---
  /** Base holdup in slug body. */
  private double baseSlugHoldup = 0.90;

  /** Minimum holdup in film region. */
  private double minFilmHoldup = 0.02;

  // --- Merging ---
  /** Distance threshold for slug merging (m). */
  private double mergeDistanceThreshold = 1.0;

  // --- Wake Effects ---
  /** Enable wake interaction between slugs. */
  private boolean enableWakeEffects = true;

  /** Wake length behind slug (diameters). */
  private double wakeLengthDiameters = 30.0;

  /** Maximum wake acceleration factor. */
  private double maxWakeAcceleration = 1.3;

  // ============== Statistics ==============

  /** Total slugs generated. */
  private int totalSlugsGenerated = 0;

  /** Total slugs merged. */
  private int totalSlugsMerged = 0;

  /** Total slugs dissipated. */
  private int totalSlugsDissipated = 0;

  /** Total slugs exited at outlet. */
  private int totalSlugsExited = 0;

  /** Total slugs that completely exited upstream through an open inlet. */
  private int totalSlugsExitedAtInlet = 0;

  /** Slug frequency at inlet (1/s). */
  private double inletSlugFrequency = 0;

  /** Slug frequency at outlet (1/s). */
  private double outletSlugFrequency = 0;

  /** Average slug length (m). */
  private double averageSlugLength = 0;

  /** Maximum slug length observed (m). */
  private double maxObservedSlugLength = 0;

  /** Maximum slug volume at outlet (m³). */
  private double maxSlugVolumeAtOutlet = 0;

  /** Simulation time for statistics (s). */
  private double simulationTime = 0;

  /** List of slug lengths at outlet for distribution. */
  private List<Double> outletSlugLengths;

  /** List of slug volumes at outlet. */
  private List<Double> outletSlugVolumes;

  /** List of inter-arrival times at outlet (s). */
  private List<Double> outletInterArrivalTimes;

  /** Time of last slug arrival at outlet (s). */
  private double lastOutletArrivalTime = -1;

  // ============== Mass Conservation ==============

  /** Total mass borrowed from Eulerian field (kg). */
  private double totalMassBorrowed = 0;

  /** Total mass returned to Eulerian field (kg). */
  private double totalMassReturned = 0;

  /** Total tracked slug mass that exited through the pipe outlet (kg). */
  private double totalMassExitedAtOutlet = 0;

  /** Tracked overlay mass removed by complete upstream exits (kg), separate from physical Eulerian transport. */
  private double totalMassExitedAtInlet = 0;

  /** Reference mixture velocity (m/s). */
  private double referenceMixtureVelocity = 1.0;

  /** Opt-in conservative Eulerian reconstruction with phase-continuity interface velocities. */
  private boolean conservativeFilmCouplingEnabled;
  /** Whether conservative tracking must respect an impermeable inlet. */
  private boolean closedInlet;
  /** Whether conservative tracking must respect an impermeable outlet. */
  private boolean closedOutlet;

  // ============== Constructor ==============

  /**
   * Default constructor.
   */
  public LagrangianSlugTracker() {
    this.slugs = new ArrayList<>();
    this.probes = new ArrayList<>();
    this.outletSlugLengths = new ArrayList<>();
    this.outletSlugVolumes = new ArrayList<>();
    this.outletInterArrivalTimes = new ArrayList<>();
    this.random = new Random();
  }

  /**
   * Constructor with random seed for reproducibility.
   *
   * @param seed random seed
   */
  public LagrangianSlugTracker(long seed) {
    this();
    this.random = new Random(seed);
  }

  // ============== Main Tracking Methods ==============

  /**
   * Advance all slugs by one time step.
   *
   * <p>
   * This is the main entry point for the Lagrangian tracking algorithm. Called each time step to:
   * </p>
   * <ol>
   * <li>Check for new slug initiation (inlet, terrain, instability)</li>
   * <li>Update velocity of each slug (Bendiksen correlation)</li>
   * <li>Calculate mass exchange (pickup/shedding)</li>
   * <li>Advance positions</li>
   * <li>Update slug lengths</li>
   * <li>Handle slug merging</li>
   * <li>Remove exited/dissipated slugs</li>
   * <li>Update Eulerian section properties</li>
   * <li>Update statistics</li>
   * </ol>
   *
   * @param sections pipe sections array
   * @param dt time step (s)
   */
  public void advanceTimeStep(PipeSection[] sections, double dt) {
    if (sections == null || sections.length == 0 || !Double.isFinite(dt) || dt <= 0) {
      return;
    }

    simulationTime += dt;
    timeSinceLastInletSlug += dt;

    // 1. Check for new slug initiation
    checkSlugInitiation(sections, dt);

    // 2. Clear slug flags on all sections
    clearSlugFlags(sections);

    // 3. Sort slugs by position (front to back)
    sortSlugsByPosition();

    // 4. Update wake effects
    updateWakeEffects(sections);

    // 5. Evaluate every conservative interface before changing any marker
    // geometry or body-velocity target during this accepted step.
    double[][] conservativeVelocities = null;
    if (conservativeFilmCouplingEnabled) {
      conservativeVelocities = new double[slugs.size()][];
      for (int index = 0; index < slugs.size(); index++) {
        conservativeVelocities[index] = conservativeKinematics(slugs.get(index), sections);
      }
    }
    if (probes != null) {
      for (SlugProbe probe : probes) {
        probe.beginStep();
      }
    }
    for (int index = 0; index < slugs.size(); index++) {
      advanceSlug(slugs.get(index), sections, dt,
          conservativeVelocities == null ? null : conservativeVelocities[index]);
    }
    if (probes != null) {
      for (SlugProbe probe : probes) {
        probe.endStep();
      }
    }

    // 6. Handle slug merging
    handleSlugMerging(sections);

    // 7. Remove exited/dissipated slugs
    removeInactiveSlugs(sections);

    // 8. Mark sections that are in slugs
    markSlugSections(sections);

    // 9. Update statistics
    if (conservativeFilmCouplingEnabled) {
      refreshConservativeInventories(sections);
    }
    updateStatistics();
  }

  /**
   * Check conditions for slug initiation.
   *
   * @param sections pipe sections
   * @param dt time step (s)
   */
  private void checkSlugInitiation(PipeSection[] sections, double dt) {
    // Inlet slug generation
    if (enableInletSlugGeneration) {
      checkInletSlugGeneration(sections, dt);
    }

    // Terrain-induced slugs checked externally via initializeTerrainSlug()

    // Stochastic initiation
    if (enableStochasticInitiation) {
      checkStochasticInitiation(sections, dt);
    }
  }

  /**
   * Check for inlet slug generation based on frequency correlation.
   *
   * <p>
   * Uses the Zabaras (2000) frequency correlation modified for inclination. Slugs are generated probabilistically based
   * on expected frequency.
   * </p>
   *
   * @param sections pipe sections
   * @param dt time step (s)
   */
  private void checkInletSlugGeneration(PipeSection[] sections, double dt) {
    if (sections.length == 0) {
      return;
    }

    PipeSection inlet = sections[0];

    // Only generate slugs if inlet is in slug flow regime
    FlowRegime regime = inlet.getFlowRegime();
    if (regime != FlowRegime.SLUG && regime != FlowRegime.CHURN) {
      inletSlugFrequency = 0.0;
      return;
    }

    // Calculate expected slug frequency
    double U_SL = inlet.getSuperficialLiquidVelocity();
    double U_SG = inlet.getSuperficialGasVelocity();
    double U_M = U_SL + U_SG;
    double D = inlet.getDiameter();
    double theta = inlet.getInclination();

    // The inlet frequency correlation requires co-current, positive flow of both phases.
    if (U_SL <= 0.0 || U_SG <= 0.0 || !Double.isFinite(U_M)) {
      inletSlugFrequency = 0.0;
      return;
    }

    // Zabaras (2000) correlation
    double Fr = U_M / Math.sqrt(GRAVITY * D);
    double lambdaL = U_SL / (U_M + 1e-10);

    // Gregory-Scott base frequency
    double freqBase = 0.0226 * Math.pow(lambdaL, 1.2) * Math.pow(Fr, 2.0) / D;

    // Inclination correction
    double inclCorrection = 1.0 + Math.sin(Math.abs(theta));

    inletSlugFrequency = freqBase * inclCorrection;

    // Expected time between slugs
    double expectedPeriod = 1.0 / (inletSlugFrequency + 1e-10);

    // Generate slug if enough time has passed (with some randomness)
    double randomFactor = 0.8 + 0.4 * random.nextDouble(); // 80-120%
    if (timeSinceLastInletSlug >= expectedPeriod * randomFactor) {
      generateInletSlug(inlet);
      timeSinceLastInletSlug = 0;
    }
  }

  /**
   * Generate a new slug at the inlet.
   *
   * @param inlet inlet pipe section
   * @return new slug unit
   */
  private SlugBubbleUnit generateInletSlug(PipeSection inlet) {
    SlugBubbleUnit slug = new SlugBubbleUnit();
    slug.id = ++slugIdCounter;
    slug.source = SlugSource.INLET;
    slug.usesConservativeFilmCoupling = conservativeFilmCouplingEnabled;

    double D = inlet.getDiameter();
    double A = inlet.getArea();

    // Position: start at inlet
    slug.slugLength = initialSlugLengthDiameters * D;
    slug.tailPosition = inlet.getPosition() - inlet.getLength() / 2.0;
    slug.frontPosition = slug.tailPosition + slug.slugLength;

    // Bubble length from unit model
    double U_M = inlet.getMixtureVelocity();
    if (Math.abs(U_M) < 0.1) {
      U_M = referenceMixtureVelocity;
    }
    double unitLength = (inletSlugFrequency > 0) ? U_M / inletSlugFrequency : 2 * slug.slugLength;
    slug.bubbleLength = Math.max(slug.slugLength, unitLength - slug.slugLength);

    // Geometry
    slug.localDiameter = D;
    slug.localArea = A;
    slug.localInclination = inlet.getInclination();

    // Velocities
    calculateSlugVelocities(slug, inlet);

    // Holdup
    slug.slugHoldup = calculateSlugBodyHoldup(U_M);
    slug.filmHoldup = calculateFilmHoldup(inlet);

    // Volumes
    slug.slugLiquidVolume = slug.slugLength * A * slug.slugHoldup;
    slug.filmLiquidVolume = slug.bubbleLength * A * slug.filmHoldup;

    // Mass (borrow from Eulerian)
    double rhoL = inlet.getLiquidDensity();
    slug.slugLiquidMass = rhoL * slug.slugLiquidVolume;
    totalMassBorrowed += slug.slugLiquidMass;

    // State
    slug.isGrowing = true;
    slug.isDecaying = false;
    slug.isTerrainInduced = false;
    slug.age = 0;
    slug.distanceTraveled = 0;

    // Wake
    slug.wakeCoefficient = 1.0;
    slug.inWakeRegion = false;

    slugs.add(slug);
    totalSlugsGenerated++;

    return slug;
  }

  /**
   * Initialize slug from terrain-induced accumulation.
   *
   * <p>
   * Called externally when liquid accumulation tracker detects slug-out conditions at a terrain low point.
   * </p>
   *
   * @param characteristics slug characteristics from accumulation tracker
   * @param sections pipe sections
   * @return new slug unit
   */
  public SlugBubbleUnit initializeTerrainSlug(LiquidAccumulationTracker.SlugCharacteristics characteristics,
      PipeSection[] sections) {

    if (!enableTerrainSlugGeneration) {
      return null;
    }

    SlugBubbleUnit slug = new SlugBubbleUnit();
    slug.id = ++slugIdCounter;
    slug.source = SlugSource.TERRAIN;
    slug.usesConservativeFilmCoupling = conservativeFilmCouplingEnabled;

    // Position from characteristics
    slug.frontPosition = characteristics.frontPosition;
    slug.tailPosition = characteristics.tailPosition;
    slug.slugLength = characteristics.length;
    slug.bubbleLength = 0; // Terrain slugs initially have no bubble

    // Find section at slug position
    int idx = findSectionIndex(slug.frontPosition, sections);
    if (idx < 0) {
      idx = 0;
    }
    PipeSection section = sections[idx];

    // Geometry
    slug.localDiameter = section.getDiameter();
    slug.localArea = section.getArea();
    slug.localInclination = section.getInclination();

    // Velocities from characteristics
    slug.frontVelocity = characteristics.velocity;
    slug.tailVelocity = characteristics.velocity;

    // Holdup
    slug.slugHoldup = characteristics.holdup;
    slug.filmHoldup = calculateFilmHoldup(section);

    // Volume from characteristics
    slug.slugLiquidVolume = characteristics.volume;
    slug.filmLiquidVolume = 0;

    // Mass
    double rhoL = section.getLiquidDensity();
    slug.slugLiquidMass = rhoL * slug.slugLiquidVolume;
    totalMassBorrowed += slug.slugLiquidMass;

    // State
    slug.isGrowing = true;
    slug.isDecaying = false;
    slug.isTerrainInduced = true;
    slug.age = 0;
    slug.distanceTraveled = 0;

    // Wake
    slug.wakeCoefficient = 1.0;
    slug.inWakeRegion = false;

    slugs.add(slug);
    totalSlugsGenerated++;

    return slug;
  }

  /**
   * Check for stochastic slug initiation from instabilities.
   *
   * @param sections pipe sections
   * @param dt time step (s)
   */
  private void checkStochasticInitiation(PipeSection[] sections, double dt) {
    // Check each section for potential instability-driven slug initiation
    for (int i = 1; i < sections.length - 1; i++) {
      PipeSection section = sections[i];

      // Only in stratified wavy regime
      if (section.getFlowRegime() != FlowRegime.STRATIFIED_WAVY) {
        continue;
      }

      // Check if holdup is above threshold
      if (section.getLiquidHoldup() < initiationHoldupThreshold) {
        continue;
      }

      // Kelvin-Helmholtz instability criterion
      double U_G = section.getGasVelocity();
      double U_L = section.getLiquidVelocity();
      double rhoG = section.getGasDensity();
      double rhoL = section.getLiquidDensity();
      double D = section.getDiameter();
      double h_L = section.getLiquidHoldup() * D; // Approximate liquid height

      // Critical velocity difference for instability
      double sigma = 0.03; // Surface tension N/m (approximate)
      double deltaU_crit = Math
          .sqrt((rhoL - rhoG) * GRAVITY * h_L / (rhoG * rhoL / (rhoG + rhoL)) + sigma / (rhoG * h_L));

      double deltaU = Math.abs(U_G - U_L);

      // Probability of initiation based on excess velocity
      if (deltaU > deltaU_crit) {
        double excess = (deltaU - deltaU_crit) / deltaU_crit;
        double probability = 0.01 * excess * dt; // Small probability per time step

        if (random.nextDouble() < probability) {
          generateInstabilitySlug(section, i, sections);
        }
      }
    }
  }

  /**
   * Generate slug from flow instability.
   *
   * @param section section where instability occurs
   * @param sectionIndex index of the section
   * @param sections all pipe sections
   * @return new slug unit
   */
  private SlugBubbleUnit generateInstabilitySlug(PipeSection section, int sectionIndex, PipeSection[] sections) {

    SlugBubbleUnit slug = new SlugBubbleUnit();
    slug.id = ++slugIdCounter;
    slug.source = SlugSource.INSTABILITY;
    slug.usesConservativeFilmCoupling = conservativeFilmCouplingEnabled;

    double D = section.getDiameter();
    double A = section.getArea();
    double position = section.getPosition();

    // Initial slug is small
    slug.slugLength = minSlugLengthDiameters * D;
    slug.frontPosition = position + slug.slugLength / 2;
    slug.tailPosition = position - slug.slugLength / 2;
    slug.bubbleLength = slug.slugLength; // Equal bubble length initially

    // Geometry
    slug.localDiameter = D;
    slug.localArea = A;
    slug.localInclination = section.getInclination();

    // Velocities
    calculateSlugVelocities(slug, section);

    // Holdup
    double U_M = section.getMixtureVelocity();
    slug.slugHoldup = calculateSlugBodyHoldup(U_M);
    slug.filmHoldup = calculateFilmHoldup(section);

    // Volumes
    slug.slugLiquidVolume = slug.slugLength * A * slug.slugHoldup;
    slug.filmLiquidVolume = slug.bubbleLength * A * slug.filmHoldup;

    // Mass
    double rhoL = section.getLiquidDensity();
    slug.slugLiquidMass = rhoL * slug.slugLiquidVolume;
    totalMassBorrowed += slug.slugLiquidMass;

    // State
    slug.isGrowing = true;
    slug.isDecaying = false;
    slug.isTerrainInduced = false;
    slug.age = 0;
    slug.distanceTraveled = 0;

    // Wake
    slug.wakeCoefficient = 1.0;

    slugs.add(slug);
    totalSlugsGenerated++;

    return slug;
  }

  // ============== Slug Dynamics ==============

  /**
   * Advance a single slug by one time step.
   *
   * @param slug the slug to advance
   * @param sections pipe sections
   * @param dt time step (s)
   * @param conservativeVelocities frozen conservative kinematics, or null for legacy tracking
   */
  private void advanceSlug(SlugBubbleUnit slug, PipeSection[] sections, double dt, double[] conservativeVelocities) {
    if (conservativeFilmCouplingEnabled) {
      advanceConservativeSlug(slug, sections, dt, conservativeVelocities);
      return;
    }
    double previousSlugLiquidMass = slug.slugLiquidMass;
    slug.age += dt;

    // Find section at slug front
    int frontIdx = findSectionIndex(slug.frontPosition, sections);
    if (frontIdx < 0) {
      frontIdx = slug.frontPosition < sections[0].getPosition() ? 0 : sections.length - 1;
    }
    PipeSection frontSection = sections[frontIdx];

    // Update local properties
    slug.localDiameter = frontSection.getDiameter();
    slug.localArea = frontSection.getArea();
    slug.localInclination = frontSection.getInclination();

    // Calculate velocities
    calculateSlugVelocities(slug, frontSection);

    // Apply wake effects
    if (slug.inWakeRegion) {
      slug.frontVelocity *= slug.wakeCoefficient;
    }

    // Calculate mass exchange (pickup/shedding)
    calculateMassExchange(slug, frontSection, dt);

    // Update positions
    double frontDisplacement = slug.frontVelocity * dt;
    double tailDisplacement = slug.tailVelocity * dt;

    double previousFront = slug.frontPosition;
    double previousTail = slug.tailPosition;
    slug.frontPosition += frontDisplacement;
    slug.tailPosition += tailDisplacement;
    recordProbeMotion(slug, dt, previousFront, previousTail);
    slug.distanceTraveled += frontDisplacement;

    // Update slug length
    double newLength = slug.frontPosition - slug.tailPosition;
    double maxLength = maxSlugLengthDiameters * slug.localDiameter;

    // The minimum is a dissipation threshold, not an enforced length: clamping to it makes
    // the removal condition unreachable and manufactures liquid in short slugs.
    slug.slugLength = Math.max(0.0, Math.min(maxLength, newLength));

    // Enforce position consistency
    if (slug.slugLength != newLength) {
      // Adjust tail position to match constrained length
      slug.tailPosition = slug.frontPosition - slug.slugLength;
    }

    // Update bubble length (from unit cell model)
    updateBubbleLength(slug, frontSection);

    // Update holdup
    double U_M = frontSection.getMixtureVelocity();
    if (Math.abs(U_M) < 0.1) {
      U_M = referenceMixtureVelocity;
    }
    slug.slugHoldup = calculateSlugBodyHoldup(U_M);
    slug.filmHoldup = calculateFilmHoldup(frontSection);

    // Update volumes
    slug.slugLiquidVolume = slug.slugLength * slug.localArea * slug.slugHoldup;
    slug.filmLiquidVolume = slug.bubbleLength * slug.localArea * slug.filmHoldup;

    // Update mass
    double rhoL = frontSection.getLiquidDensity();
    slug.slugLiquidMass = rhoL * slug.slugLiquidVolume;
    accountSlugMassChange(previousSlugLiquidMass, slug.slugLiquidMass);

    // Determine growth/decay state
    double equilibriumLength = calculateEquilibriumLength(frontSection);
    if (slug.slugLength < 0.8 * equilibriumLength) {
      slug.isGrowing = true;
      slug.isDecaying = false;
    } else if (slug.slugLength > 1.5 * equilibriumLength) {
      slug.isGrowing = false;
      slug.isDecaying = true;
    } else {
      slug.isGrowing = false;
      slug.isDecaying = false;
    }
  }

  /**
   * Calculate slug front and tail velocities using Bendiksen correlation.
   *
   * <p>
   * The slug front moves at the Taylor bubble velocity: V_front = C0 * U_m + U_drift
   * </p>
   * <p>
   * The tail velocity uses an empirical length-dependent shedding factor. Pickup/shedding rates are calculated
   * separately as diagnostics; they do not determine the tail velocity or conservatively exchange Eulerian mass.
   * </p>
   *
   * @param slug the slug unit
   * @param section pipe section at slug location
   */
  private void calculateSlugVelocities(SlugBubbleUnit slug, PipeSection section) {
    double U_M = section.getMixtureVelocity();
    if (Math.abs(U_M) < 0.1) {
      U_M = referenceMixtureVelocity;
    }

    double D = section.getDiameter();
    double theta = section.getInclination();
    double rhoL = section.getLiquidDensity();
    double rhoG = section.getGasDensity();
    double deltaRho = rhoL - rhoG;

    // Froude number
    double Fr = Math.abs(U_M) / Math.sqrt(GRAVITY * D);

    // Distribution coefficient C0 (Bendiksen 1984)
    double C0;
    if (Fr > 3.5) {
      C0 = 1.2; // High Froude - more uniform distribution
    } else {
      C0 = 1.05 + 0.15 * Math.sin(theta); // Low Froude - inclination dependent
    }

    // Drift velocity
    double U_drift = calculateDriftVelocity(D, theta, deltaRho, rhoL);

    // Front velocity = Taylor bubble nose velocity
    slug.frontVelocity = C0 * U_M + U_drift;
    slug.bubbleNoseVelocity = slug.frontVelocity;

    // Liquid velocity in slug body (approximately equal to mixture)
    slug.slugLiquidVelocity = U_M;

    // Film velocity (from stratified flow)
    double H_film = slug.filmHoldup;
    if (H_film > 0.01 && H_film < 0.99) {
      double U_SL = section.getSuperficialLiquidVelocity();
      slug.filmVelocity = U_SL / H_film;
    } else {
      slug.filmVelocity = U_M * 0.5;
    }

    // Tail velocity: determines slug growth/decay
    // For stable slug: V_tail ≈ V_front
    // Growing slug: V_tail < V_front (net pickup)
    // Decaying slug: V_tail > V_front (net shedding)

    // Tail velocity from continuity at slug-film interface
    // Adjusted based on slug length relative to equilibrium
    double L_eq = calculateEquilibriumLength(section);
    double lengthRatio = slug.slugLength / L_eq;

    // Shedding increases as slug exceeds equilibrium length
    double sheddingFactor;
    if (lengthRatio < 0.9) {
      // Short slug - grows by pickup
      sheddingFactor = 0.95;
    } else if (lengthRatio > 1.2) {
      // Long slug - decays by shedding
      sheddingFactor = 1.0 + 0.1 * (lengthRatio - 1.2);
    } else {
      // Near equilibrium
      sheddingFactor = 0.98;
    }

    slug.tailVelocity = slug.frontVelocity * Math.min(1.05, sheddingFactor);
  }

  /**
   * Calculate drift velocity using Bendiksen (1984) correlation.
   *
   * @param D pipe diameter (m)
   * @param theta pipe inclination (radians)
   * @param deltaRho density difference (kg/m³)
   * @param rhoL liquid density (kg/m³)
   * @return drift velocity (m/s)
   */
  private double calculateDriftVelocity(double D, double theta, double deltaRho, double rhoL) {
    if (deltaRho <= 0 || rhoL <= 0) {
      return 0;
    }

    double absTheta = Math.abs(theta);

    // Horizontal drift (Zukoski 1966)
    double U_dH = 0.54 * Math.sqrt(GRAVITY * D * deltaRho / rhoL);

    // Vertical drift (Dumitrescu 1943)
    double U_dV = 0.35 * Math.sqrt(GRAVITY * D * deltaRho / rhoL);

    // Interpolation based on inclination
    double U_drift;
    if (absTheta < Math.PI / 6) {
      // Near horizontal
      U_drift = U_dH * Math.cos(theta) + U_dV * Math.sin(theta);
    } else if (absTheta > Math.PI / 3) {
      // Near vertical
      U_drift = U_dV * Math.sin(theta);
    } else {
      // Transition region
      double w = (absTheta - Math.PI / 6) / (Math.PI / 6);
      U_drift = (1 - w) * U_dH * Math.cos(theta) + U_dV * Math.sin(theta);
    }

    // The signed sine term already gives the buoyancy direction in downward sections.
    return U_drift;
  }

  /**
   * Calculate mass exchange at slug front and tail.
   *
   * <p>
   * Pickup rate at front: liquid is scooped from stratified film Shedding rate at tail: liquid is shed into film behind
   * slug
   * </p>
   *
   * @param slug the slug unit
   * @param section pipe section
   * @param dt time step (s)
   */
  private void calculateMassExchange(SlugBubbleUnit slug, PipeSection section, double dt) {
    double rhoL = section.getLiquidDensity();
    double A = section.getArea();

    // Pickup at front: liquid from stratified film enters slug
    // Rate = rhoL * A * H_film * (V_front - V_film)
    double V_front = slug.frontVelocity;
    double V_film = slug.filmVelocity;
    double H_film = slug.filmHoldup;

    double relativeVelocity = V_front - V_film;
    if (relativeVelocity > 0) {
      slug.pickupRate = rhoL * A * H_film * relativeVelocity;
    } else {
      slug.pickupRate = 0;
    }

    // Shedding at tail: liquid leaves slug into forming film
    // Rate = rhoL * A * (H_slug - H_film) * (V_tail - V_slug_liquid)
    double H_slug = slug.slugHoldup;
    double V_tail = slug.tailVelocity;
    double V_slug = slug.slugLiquidVelocity;

    double tailRelVelocity = V_tail - V_slug;
    if (tailRelVelocity > 0) {
      slug.sheddingRate = rhoL * A * (H_slug - H_film) * tailRelVelocity;
    } else {
      slug.sheddingRate = 0;
    }

    // Net mass rate
    slug.netMassRate = slug.pickupRate - slug.sheddingRate;
  }

  /**
   * Account for the actual liquid-mass change implied by the updated slug geometry.
   *
   * <p>
   * The geometric slug state is authoritative because length, area, holdup, and local liquid density can all change in
   * one time step. Positive and negative changes close the borrowed/returned overlay account; neither changes Eulerian
   * inventory. The pickup and shedding rates remain diagnostic instantaneous rates.
   * </p>
   *
   * @param previousMass liquid mass before the update (kg)
   * @param currentMass liquid mass after the update (kg)
   */
  private void accountSlugMassChange(double previousMass, double currentMass) {
    double massChange = currentMass - previousMass;
    if (massChange > 0.0) {
      totalMassBorrowed += massChange;
    } else if (massChange < 0.0) {
      totalMassReturned -= massChange;
    }
  }

  /**
   * Advance interfaces using liquid continuity, with Eulerian inventories remaining authoritative.
   *
   * @param slug marker to advance after the hydrodynamic step is accepted
   * @param sections accepted Eulerian sections
   * @param dt accepted step duration (s)
   * @param kinematics interface velocities and exchange diagnostics frozen before any marker moves
   */
  private void advanceConservativeSlug(SlugBubbleUnit slug, PipeSection[] sections, double dt, double[] kinematics) {
    double previousMass = slug.slugLiquidMass;
    slug.frontVelocity = kinematics[0];
    slug.tailVelocity = kinematics[1];
    slug.bubbleNoseVelocity = slug.tailVelocity;
    slug.pickupRate = kinematics[2];
    slug.sheddingRate = kinematics[3];
    slug.netMassRate = slug.pickupRate - slug.sheddingRate;
    slug.slugLiquidVelocity = kinematics[4];
    slug.hasConservativeVelocity = true;
    slug.filmVelocity = kinematics[5];
    double previousFront = slug.frontPosition;
    double previousTail = slug.tailPosition;
    slug.frontPosition += dt * slug.frontVelocity;
    slug.tailPosition += dt * slug.tailVelocity;
    double inlet = sections[0].getPosition() - sections[0].getLength() / 2.0;
    double outlet = calculatePipeLength(sections);
    if (closedInlet) {
      slug.frontPosition = Math.max(inlet, slug.frontPosition);
      slug.tailPosition = Math.max(inlet, slug.tailPosition);
    }
    if (closedOutlet) {
      slug.frontPosition = Math.min(outlet, slug.frontPosition);
      slug.tailPosition = Math.min(outlet, slug.tailPosition);
    }
    recordProbeMotion(slug, dt, previousFront, previousTail);
    slug.frontVelocity = (slug.frontPosition - previousFront) / dt;
    slug.tailVelocity = (slug.tailPosition - previousTail) / dt;
    slug.slugLength = Math.max(0.0, slug.frontPosition - slug.tailPosition);
    slug.age += dt;
    slug.distanceTraveled += Math.abs(dt * slug.frontVelocity);
    slug.isGrowing = slug.frontVelocity > slug.tailVelocity;
    slug.isDecaying = slug.frontVelocity < slug.tailVelocity;
    int index = boundedSectionIndex(slug.frontPosition, sections);
    PipeSection section = sections[index];
    slug.localArea = section.getArea();
    slug.localDiameter = section.getDiameter();
    slug.localInclination = section.getInclination();
    slug.filmHoldup = kinematics[6];
    // Geometric compatibility statistics are not additional physical inventories. Actual
    // in-domain phase masses/momenta/energy are reconstructed separately below.
    slug.slugLiquidVolume = slug.slugLength * slug.localArea * slug.slugHoldup;
    slug.slugLiquidMass = section.getLiquidDensity() * slug.slugLiquidVolume;
    slug.filmLiquidVolume = slug.bubbleLength * slug.localArea * slug.filmHoldup;
    accountSlugMassChange(previousMass, slug.slugLiquidMass);
  }

  /**
   * Calculate bubble-tail and liquid-front velocities from the two film interfaces.
   *
   * <p>
   * The Taylor-bubble correlation specifies the tail speed. Continuity at the trailing film determines the body liquid
   * flux, and the liquid Rankine-Hugoniot condition then determines the front speed. At a uniform-density interface,
   * pickup minus shedding equals body mass per length times front-minus-tail speed. A vanishing holdup contrast has no
   * identifiable discontinuity and is transported at the bubble speed.
   * </p>
   *
   * @param slug marker whose positions are queried, without mutation
   * @param sections frozen Eulerian sections
   * @return front speed, tail speed, pickup, shedding, body velocity, ahead film velocity, and ahead film holdup
   */
  private double[] conservativeKinematics(SlugBubbleUnit slug, PipeSection[] sections) {
    double frontProbe = Math.max(1.0e-9, Math.ulp(slug.frontPosition) * 4.0);
    double tailProbe = Math.max(1.0e-9, Math.ulp(slug.tailPosition) * 4.0);
    PipeSection ahead = sections[boundedSectionIndex(slug.frontPosition + frontProbe, sections)];
    PipeSection behind = sections[boundedSectionIndex(slug.tailPosition - tailProbe, sections)];
    PipeSection bodyFront = sections[boundedSectionIndex(slug.frontPosition - frontProbe, sections)];
    PipeSection bodyTail = sections[boundedSectionIndex(slug.tailPosition + tailProbe, sections)];
    if (ahead instanceof TwoFluidSection) {
      ahead = SlugFilmCoupling.reconstruct((TwoFluidSection) ahead, slugs).getFilmState();
    }
    if (behind instanceof TwoFluidSection) {
      behind = SlugFilmCoupling.reconstruct((TwoFluidSection) behind, slugs).getFilmState();
    }
    if (bodyFront instanceof TwoFluidSection) {
      bodyFront = SlugFilmCoupling.reconstruct((TwoFluidSection) bodyFront, slugs).getBodyState();
    }
    if (bodyTail instanceof TwoFluidSection) {
      bodyTail = SlugFilmCoupling.reconstruct((TwoFluidSection) bodyTail, slugs).getBodyState();
    }
    double diameter = bodyTail.getDiameter();
    double inclination = bodyTail.getInclination();
    // Use the actual accepted mixture flow, without the legacy reference-speed fallback.
    PipeSection tailCell = sections[boundedSectionIndex(slug.tailPosition, sections)];
    double mixtureVelocity = tailCell.getMixtureVelocity();
    double froude = Math.abs(mixtureVelocity) / Math.sqrt(GRAVITY * diameter);
    double distribution = froude > 3.5 ? 1.2 : 1.05 + 0.15 * Math.sin(inclination);
    double tailSpeed = distribution * mixtureVelocity + calculateDriftVelocity(diameter, inclination,
        bodyTail.getLiquidDensity() - bodyTail.getGasDensity(), bodyTail.getLiquidDensity());
    double frontBodyMass = liquidMassPerLength(bodyFront);
    double tailBodyMass = liquidMassPerLength(bodyTail);
    double aheadMass = liquidMassPerLength(ahead);
    double behindMass = liquidMassPerLength(behind);
    double aheadFlux = liquidMassFlux(ahead);
    double behindFlux = liquidMassFlux(behind);
    double bodyFluxAtTail = tailSpeed * (tailBodyMass - behindMass) + behindFlux;
    double bodyVelocity = tailBodyMass > 0.0 ? bodyFluxAtTail / tailBodyMass : mixtureVelocity;
    double contrast = frontBodyMass - aheadMass;
    double frontSpeed = tailSpeed;
    if (contrast > 1.0e-9 * Math.max(frontBodyMass, 1.0)) {
      frontSpeed = (frontBodyMass * bodyVelocity - aheadFlux) / contrast;
    }
    double inlet = sections[0].getPosition() - sections[0].getLength() / 2.0;
    double outlet = calculatePipeLength(sections);
    if (closedInlet) {
      if (slug.frontPosition <= inlet && frontSpeed < 0.0) {
        frontSpeed = 0.0;
      }
      if (slug.tailPosition <= inlet && tailSpeed < 0.0) {
        tailSpeed = 0.0;
      }
    }
    if (closedOutlet) {
      if (slug.frontPosition >= outlet && frontSpeed > 0.0) {
        frontSpeed = 0.0;
      }
      if (slug.tailPosition >= outlet && tailSpeed > 0.0) {
        tailSpeed = 0.0;
      }
    }
    double pickup = frontSpeed * aheadMass - aheadFlux;
    double shedding = tailSpeed * behindMass - behindFlux;
    double filmVelocity = aheadMass > 0.0 ? aheadFlux / aheadMass : 0.0;
    return new double[] { frontSpeed, tailSpeed, pickup, shedding, bodyVelocity, filmVelocity,
        ahead.getLiquidHoldup() };
  }

  /**
   * Obtain actual liquid mass per length, including both independently transported liquids.
   *
   * @param section local body or film state
   * @return liquid mass per length (kg/m)
   */
  private double liquidMassPerLength(PipeSection section) {
    if (section instanceof TwoFluidSection) {
      TwoFluidSection state = (TwoFluidSection) section;
      return state.getOilMassPerLength() + state.getWaterMassPerLength();
    }
    return section.getLiquidHoldup() * section.getLiquidDensity() * section.getArea();
  }

  /**
   * Obtain the phase-resolved total liquid mass flow.
   *
   * @param section local body or film state
   * @return oil plus water mass flow (kg/s)
   */
  private double liquidMassFlux(PipeSection section) {
    if (section instanceof TwoFluidSection) {
      double[] state = ((TwoFluidSection) section).getStateVector();
      return state[4] + state[5];
    }
    return liquidMassPerLength(section) * section.getLiquidVelocity();
  }

  /**
   * Locate a position and use the nearest end cell outside the physical pipe.
   *
   * @param position axial position (m)
   * @param sections midpoint-positioned cells
   * @return valid section index
   */
  private int boundedSectionIndex(double position, PipeSection[] sections) {
    int index = findSectionIndex(position, sections);
    return index >= 0 ? index : (position < sections[0].getPosition() ? 0 : sections.length - 1);
  }

  /**
   * Refresh diagnostic body inventories by partitioning the sole Eulerian inventory.
   *
   * @param sections accepted Eulerian cells
   */
  private void refreshConservativeInventories(PipeSection[] sections) {
    for (SlugBubbleUnit slug : slugs) {
      slug.phaseMassKg = new double[3];
      slug.phaseMomentumKgMPerSecond = new double[3];
      slug.phaseVolumeM3 = new double[3];
      slug.totalEnergyJ = 0.0;
    }
    for (PipeSection section : sections) {
      if (!(section instanceof TwoFluidSection)) {
        continue;
      }
      TwoFluidSection cell = (TwoFluidSection) section;
      SlugFilmCoupling.Reconstruction reconstruction = SlugFilmCoupling.reconstruct(cell, slugs);
      double[] body = reconstruction.getBodyConservativeState();
      double left = section.getPosition() - section.getLength() / 2.0;
      double right = left + section.getLength();
      double[] overlap = new double[slugs.size()];
      double totalOverlap = 0.0;
      for (int i = 0; i < slugs.size(); i++) {
        overlap[i] = Math.max(0.0,
            Math.min(right, slugs.get(i).frontPosition) - Math.max(left, slugs.get(i).tailPosition));
        totalOverlap += overlap[i];
      }
      if (totalOverlap <= 0.0) {
        continue;
      }
      double[] densities = { cell.getGasDensity(),
          cell.getOilDensity() > 0.0 ? cell.getOilDensity() : cell.getLiquidDensity(),
          cell.getWaterDensity() > 0.0 ? cell.getWaterDensity() : 1000.0 };
      for (int i = 0; i < slugs.size(); i++) {
        SlugBubbleUnit slug = slugs.get(i);
        double assignedLength = section.getLength() * reconstruction.getBodyFraction() * overlap[i] / totalOverlap;
        for (int phase = 0; phase < 3; phase++) {
          slug.phaseMassKg[phase] += assignedLength * body[phase];
          slug.phaseMomentumKgMPerSecond[phase] += assignedLength * body[phase + 3];
          if (densities[phase] > 0.0) {
            slug.phaseVolumeM3[phase] += assignedLength * body[phase] / densities[phase];
          }
        }
        slug.totalEnergyJ += assignedLength * body[6];
      }
    }
  }

  /**
   * Update Taylor bubble length.
   *
   * @param slug the slug unit
   * @param section pipe section
   */
  private void updateBubbleLength(SlugBubbleUnit slug, PipeSection section) {
    double U_M = section.getMixtureVelocity();
    if (Math.abs(U_M) < 0.1) {
      U_M = referenceMixtureVelocity;
    }

    // Bubble length from slug unit model
    // L_bubble = L_unit - L_slug
    // L_unit = U_M / frequency
    if (inletSlugFrequency > 0) {
      double unitLength = Math.abs(U_M) / inletSlugFrequency;
      slug.bubbleLength = Math.max(0, unitLength - slug.slugLength);
    } else {
      // Default: bubble length proportional to slug length
      slug.bubbleLength = slug.slugLength;
    }
  }

  /**
   * Calculate slug body holdup using Gregory et al. (1978).
   *
   * @param U_M mixture velocity (m/s)
   * @return slug body holdup (0.5-1.0)
   */
  private double calculateSlugBodyHoldup(double U_M) {
    // Gregory, Nicholson, Aziz (1978)
    // H_LS = 1 / (1 + (U_M / 8.66)^1.39)
    double ratio = Math.abs(U_M) / 8.66;
    double H_LS = 1.0 / (1.0 + Math.pow(ratio, 1.39));
    return Math.max(0.5, Math.min(0.98, H_LS));
  }

  /**
   * Calculate film holdup from section properties.
   *
   * @param section pipe section
   * @return film holdup
   */
  private double calculateFilmHoldup(PipeSection section) {
    // Film holdup is the stratified holdup in the section
    double H_film = section.getLiquidHoldup();

    // Bound to reasonable range for film
    return Math.max(minFilmHoldup, Math.min(0.5, H_film));
  }

  /**
   * Calculate equilibrium slug length using Barnea-Taitel model.
   *
   * @param section pipe section
   * @return equilibrium slug length (m)
   */
  private double calculateEquilibriumLength(PipeSection section) {
    double D = section.getDiameter();
    double U_M = section.getMixtureVelocity();
    double theta = section.getInclination();

    // Base length in diameters
    double Fr = Math.abs(U_M) / Math.sqrt(GRAVITY * D);

    // Barnea-Taitel (1993): L_s/D = 15-40 depending on conditions
    double L_s_D = 25.0 + 10.0 * Math.min(Fr, 2.0);

    // Inclination effect
    if (theta > 0) {
      L_s_D *= (1.0 + 0.3 * Math.sin(theta));
    }

    double L_eq = L_s_D * D;
    return Math.max(minSlugLengthDiameters * D, L_eq);
  }

  // ============== Wake Effects ==============

  /**
   * Update wake effects between consecutive slugs.
   *
   * <p>
   * OLGA wake model: A slug following closely behind another experiences accelerated motion due to reduced liquid
   * hold-up in the wake region.
   * </p>
   *
   * @param sections pipe sections
   */
  private void updateWakeEffects(PipeSection[] sections) {
    for (SlugBubbleUnit slug : slugs) {
      slug.inWakeRegion = false;
      slug.wakeCoefficient = 1.0;
      slug.distanceToPrecedingSlug = Double.POSITIVE_INFINITY;
    }
    if (!enableWakeEffects || slugs.size() < 2) {
      return;
    }

    // Slugs should be sorted by position (front to back)
    for (int i = 1; i < slugs.size(); i++) {
      SlugBubbleUnit current = slugs.get(i);
      SlugBubbleUnit preceding = slugs.get(i - 1);

      // Distance from current slug front to preceding slug tail
      double distance = preceding.tailPosition - current.frontPosition;
      current.distanceToPrecedingSlug = distance;

      // Wake length scales with pipe diameter
      double D = current.localDiameter;
      double wakeLength = wakeLengthDiameters * D;

      if (distance > 0 && distance < wakeLength) {
        current.inWakeRegion = true;

        // Wake coefficient increases as slug gets closer
        // Linear interpolation: 1.0 at wake edge, maxWakeAcceleration at zero distance
        double normalizedDistance = distance / wakeLength;
        current.wakeCoefficient = maxWakeAcceleration - (maxWakeAcceleration - 1.0) * normalizedDistance;
      } else {
        current.inWakeRegion = false;
        current.wakeCoefficient = 1.0;
      }
    }
  }

  // ============== Slug Merging ==============

  /**
   * Handle merging of slugs that have caught up to each other.
   *
   * @param sections pipe sections
   */
  private void handleSlugMerging(PipeSection[] sections) {
    if (slugs.size() < 2) {
      return;
    }

    List<SlugBubbleUnit> toRemove = new ArrayList<>();

    // Check consecutive slug pairs
    for (int i = 1; i < slugs.size(); i++) {
      SlugBubbleUnit following = slugs.get(i);
      SlugBubbleUnit preceding = slugs.get(i - 1);

      // Check if following slug front has caught preceding slug tail
      double gap = preceding.tailPosition - following.frontPosition;

      if (gap <= mergeDistanceThreshold) {
        // Merge: following slug absorbs preceding slug
        mergeSlugPair(following, preceding, sections);
        toRemove.add(preceding);
        totalSlugsMerged++;
      }
    }

    slugs.removeAll(toRemove);
  }

  /**
   * Merge two slugs into one.
   *
   * @param survivor the surviving slug (following)
   * @param absorbed the absorbed slug (preceding)
   * @param sections pipe sections
   */
  private void mergeSlugPair(SlugBubbleUnit survivor, SlugBubbleUnit absorbed, PipeSection[] sections) {

    // Extend survivor to include absorbed slug
    survivor.frontPosition = Math.max(survivor.frontPosition, absorbed.frontPosition);
    survivor.tailPosition = Math.min(survivor.tailPosition, absorbed.tailPosition);
    survivor.slugLength = survivor.frontPosition - survivor.tailPosition;

    if (conservativeFilmCouplingEnabled) {
      // A union of markers changes no Eulerian inventory. In particular, overlapping
      // geometric volume estimates must not push the merged tail farther upstream.
      double previousMass = survivor.slugLiquidMass + absorbed.slugLiquidMass;
      PipeSection frontSection = sections[boundedSectionIndex(survivor.frontPosition, sections)];
      survivor.localArea = frontSection.getArea();
      survivor.localDiameter = frontSection.getDiameter();
      survivor.localInclination = frontSection.getInclination();
      survivor.slugHoldup = Math.max(survivor.slugHoldup, absorbed.slugHoldup);
      survivor.slugLiquidVolume = survivor.slugLength * survivor.localArea * survivor.slugHoldup;
      survivor.slugLiquidMass = survivor.slugLiquidVolume * frontSection.getLiquidDensity();
      survivor.filmLiquidVolume = survivor.bubbleLength * survivor.localArea * survivor.filmHoldup;
      survivor.frontVelocity = absorbed.frontVelocity;
      survivor.hasConservativeVelocity = false;
      accountSlugMassChange(previousMass, survivor.slugLiquidMass);
      survivor.isGrowing = true;
      survivor.isDecaying = false;
      return;
    }

    // Combine liquid volumes
    survivor.slugLiquidVolume += absorbed.slugLiquidVolume;
    survivor.filmLiquidVolume += absorbed.filmLiquidVolume;

    // Combine masses
    survivor.slugLiquidMass += absorbed.slugLiquidMass;

    // Take front velocity from absorbed slug
    survivor.frontVelocity = absorbed.frontVelocity;

    // Derive holdup from the conserved liquid volume and the merged body geometry. If the
    // pre-merge bodies overlap, extend the tail only as needed to fit their combined liquid.
    survivor.slugLength = Math.max(survivor.slugLength, survivor.slugLiquidVolume / survivor.localArea);
    survivor.tailPosition = survivor.frontPosition - survivor.slugLength;
    survivor.slugHoldup = survivor.slugLiquidVolume / (survivor.localArea * survivor.slugLength);

    // Bubble length from absorbed slug
    survivor.bubbleLength = absorbed.bubbleLength;

    // Reset growth state
    survivor.isGrowing = true;
    survivor.isDecaying = false;
  }

  // ============== Slug Removal ==============

  /**
   * Remove slugs that have exited the pipe or dissipated.
   *
   * @param sections pipe sections
   */
  private void removeInactiveSlugs(PipeSection[] sections) {
    double pipeLength = calculatePipeLength(sections);
    double inletPosition = sections[0].getPosition() - sections[0].getLength() / 2.0;

    Iterator<SlugBubbleUnit> iter = slugs.iterator();
    while (iter.hasNext()) {
      SlugBubbleUnit slug = iter.next();

      // Check if slug has exited
      if (slug.tailPosition >= pipeLength && !(conservativeFilmCouplingEnabled && closedOutlet)) {
        slug.hasExited = true;
        recordSlugAtOutlet(slug);
        totalMassExitedAtOutlet += slug.slugLiquidMass;
        totalSlugsExited++;
        iter.remove();
        continue;
      }

      if (slug.frontPosition <= inletPosition && !(conservativeFilmCouplingEnabled && closedInlet)) {
        // Upstream departure is a boundary exit, not a downstream arrival or dissipation.
        // Probe trajectories were already recorded before removing this overlay.
        slug.hasExited = true;
        totalMassExitedAtInlet += slug.slugLiquidMass;
        totalSlugsExitedAtInlet++;
        iter.remove();
        continue;
      }

      // Check if slug has dissipated (too short after sufficient time)
      double minLength = minSlugLengthDiameters * slug.localDiameter;
      if (slug.slugLength <= 0.0 || (slug.slugLength < minLength && slug.age > 10.0)) {
        // No Eulerian mass was removed at initiation. Remove only the overlay.
        totalMassReturned += slug.slugLiquidMass;
        totalSlugsDissipated++;
        iter.remove();
      }
    }
  }

  /**
   * Record slug statistics as it exits at outlet.
   *
   * @param slug the exiting slug
   */
  private void recordSlugAtOutlet(SlugBubbleUnit slug) {
    outletSlugLengths.add(slug.slugLength);
    outletSlugVolumes.add(slug.slugLiquidVolume);

    if (slug.slugLiquidVolume > maxSlugVolumeAtOutlet) {
      maxSlugVolumeAtOutlet = slug.slugLiquidVolume;
    }

    // Inter-arrival time
    if (lastOutletArrivalTime >= 0) {
      double interArrival = simulationTime - lastOutletArrivalTime;
      outletInterArrivalTimes.add(interArrival);
    }
    lastOutletArrivalTime = simulationTime;
  }

  // ============== Section Marking ==============

  /**
   * Clear slug flags on all sections.
   *
   * @param sections pipe sections
   */
  private void clearSlugFlags(PipeSection[] sections) {
    for (PipeSection section : sections) {
      section.setInSlugBody(false);
      section.setInSlugBubble(false);
      section.setSlugHoldup(section.getLiquidHoldup());
    }
  }

  /**
   * Mark sections that are within slug bodies or bubbles.
   *
   * @param sections pipe sections
   */
  private void markSlugSections(PipeSection[] sections) {
    for (SlugBubbleUnit slug : slugs) {
      for (PipeSection section : sections) {
        double secStart = section.getPosition() - section.getLength() / 2.0;
        double secEnd = secStart + section.getLength();

        // Check overlap with slug body
        if (secStart < slug.frontPosition && secEnd > slug.tailPosition) {
          section.setInSlugBody(true);
          section.setInSlugBubble(false);
          section.setSlugHoldup(slug.slugHoldup);
        }

        // Check overlap with bubble region (behind tail)
        double bubbleStart = slug.tailPosition - slug.bubbleLength;
        double bubbleEnd = slug.tailPosition;
        if (secStart < bubbleEnd && secEnd > bubbleStart && !section.isInSlugBody()) {
          section.setInSlugBubble(true);
          section.setSlugHoldup(slug.filmHoldup);
        }
      }
    }
  }

  // ============== Utility Methods ==============

  /**
   * Sort slugs by front position (downstream to upstream).
   */
  private void sortSlugsByPosition() {
    Collections.sort(slugs, new Comparator<SlugBubbleUnit>() {
      @Override
      public int compare(SlugBubbleUnit a, SlugBubbleUnit b) {
        return Double.compare(b.frontPosition, a.frontPosition);
      }
    });
  }

  /**
   * Find section index containing a position.
   *
   * @param position position along pipe (m)
   * @param sections pipe sections
   * @return section index, or -1 if not found
   */
  private int findSectionIndex(double position, PipeSection[] sections) {
    for (int i = 0; i < sections.length; i++) {
      double start = sections[i].getPosition() - sections[i].getLength() / 2.0;
      double end = start + sections[i].getLength();
      if (position >= start && position <= end) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Calculate total pipe length.
   *
   * @param sections pipe sections
   * @return pipe length (m)
   */
  private double calculatePipeLength(PipeSection[] sections) {
    if (sections.length == 0) {
      return 0;
    }
    PipeSection last = sections[sections.length - 1];
    return last.getPosition() + last.getLength() / 2.0;
  }

  // ============== Statistics ==============

  /**
   * Update slug statistics.
   */
  private void updateStatistics() {
    double sumLength = 0;
    maxObservedSlugLength = 0;

    for (SlugBubbleUnit slug : slugs) {
      sumLength += slug.slugLength;
      if (slug.slugLength > maxObservedSlugLength) {
        maxObservedSlugLength = slug.slugLength;
      }
    }

    averageSlugLength = slugs.isEmpty() ? 0.0 : sumLength / slugs.size();

    // Outlet frequency from inter-arrival times
    if (outletInterArrivalTimes.size() > 0) {
      double sumTime = 0;
      for (Double t : outletInterArrivalTimes) {
        sumTime += t;
      }
      double avgPeriod = sumTime / outletInterArrivalTimes.size();
      outletSlugFrequency = 1.0 / (avgPeriod + 1e-10);
    }
  }

  // ============== Getters and Setters ==============

  /**
   * Get all active slugs.
   *
   * @return list of slug units (copy)
   */
  public List<SlugBubbleUnit> getSlugs() {
    return new ArrayList<>(slugs);
  }

  /**
   * Get number of active slugs.
   *
   * @return slug count
   */
  public int getSlugCount() {
    return slugs.size();
  }

  /**
   * Get total slugs generated.
   *
   * @return count
   */
  public int getTotalSlugsGenerated() {
    return totalSlugsGenerated;
  }

  /**
   * Get total slugs merged.
   *
   * @return count
   */
  public int getTotalSlugsMerged() {
    return totalSlugsMerged;
  }

  /**
   * Get total slugs dissipated.
   *
   * @return count
   */
  public int getTotalSlugsDissipated() {
    return totalSlugsDissipated;
  }

  /**
   * Get total slugs exited at outlet.
   *
   * @return count
   */
  public int getTotalSlugsExited() {
    return totalSlugsExited;
  }

  /** @return number of complete marker exits through the open upstream inlet */
  public int getTotalSlugsExitedAtInlet() {
    return totalSlugsExitedAtInlet;
  }

  /**
   * Get inlet slug frequency.
   *
   * @return frequency (1/s)
   */
  public double getInletSlugFrequency() {
    return inletSlugFrequency;
  }

  /**
   * Get outlet slug frequency.
   *
   * @return frequency (1/s)
   */
  public double getOutletSlugFrequency() {
    return outletSlugFrequency;
  }

  /**
   * Get average slug length.
   *
   * @return length (m)
   */
  public double getAverageSlugLength() {
    return averageSlugLength;
  }

  /**
   * Get maximum observed slug length.
   *
   * @return length (m)
   */
  public double getMaxSlugLength() {
    return maxObservedSlugLength;
  }

  /**
   * Get maximum slug volume at outlet.
   *
   * @return volume (m³)
   */
  public double getMaxSlugVolumeAtOutlet() {
    return maxSlugVolumeAtOutlet;
  }

  /**
   * Get slug frequency (deprecated, use getInletSlugFrequency).
   *
   * @return inlet slug frequency (1/s)
   */
  public double getSlugFrequency() {
    return inletSlugFrequency;
  }

  /**
   * Set reference mixture velocity.
   *
   * @param velocity velocity (m/s)
   */
  public void setReferenceVelocity(double velocity) {
    this.referenceMixtureVelocity = Math.max(0.1, velocity);
  }

  /**
   * Enable the conservative Eulerian subcell reconstruction and phase-continuity interface model.
   *
   * <p>
   * This controls tracker kinematics. The owning finite-volume solver must also use {@link SlugFilmCoupling} face
   * states in its shared fluxes; enabling this flag alone does not advance an Eulerian solution. Existing geometric
   * liquid-volume statistics remain compatibility estimates, while phase inventory arrays are in-domain Eulerian
   * partition diagnostics.
   * </p>
   *
   * @param enabled true to use the conservative coupling kinematics
   */
  public void setConservativeFilmCouplingEnabled(boolean enabled) {
    conservativeFilmCouplingEnabled = enabled;
    for (SlugBubbleUnit slug : slugs) {
      slug.usesConservativeFilmCoupling = enabled;
      slug.hasConservativeVelocity = false;
    }
  }

  /** @return whether conservative Eulerian slug/film kinematics are selected */
  public boolean isConservativeFilmCouplingEnabled() {
    return conservativeFilmCouplingEnabled;
  }

  /**
   * Configure impermeable boundaries for conservative marker motion.
   *
   * @param inletClosed true when no material can leave through the inlet
   * @param outletClosed true when no material can leave through the outlet
   */
  public void setClosedBoundaries(boolean inletClosed, boolean outletClosed) {
    closedInlet = inletClosed;
    closedOutlet = outletClosed;
  }

  /**
   * Calculate the greatest current interface speed without advancing any tracker state.
   *
   * @param sections frozen Eulerian sections used for the next trial step
   * @return maximum absolute front or tail velocity (m/s)
   */
  public double getMaximumInterfaceSpeed(PipeSection[] sections) {
    if (sections == null || sections.length == 0) {
      return 0.0;
    }
    double speed = 0.0;
    for (SlugBubbleUnit slug : slugs) {
      double[] velocities = conservativeFilmCouplingEnabled ? conservativeKinematics(slug, sections)
          : new double[] { slug.frontVelocity, slug.tailVelocity };
      speed = Math.max(speed, Math.max(Math.abs(velocities[0]), Math.abs(velocities[1])));
    }
    return speed;
  }

  /**
   * Set the random seed used by stochastic slug initiation.
   *
   * <p>
   * The default constructor intentionally uses a non-deterministic seed. Set an explicit seed before advancing the
   * tracker when reproducible validation or regression results are required.
   * </p>
   *
   * @param seed random seed
   */
  public void setRandomSeed(long seed) {
    random.setSeed(seed);
  }

  /**
   * Set minimum slug length in diameters.
   *
   * @param diameters minimum length (diameters)
   */
  public void setMinSlugLengthDiameters(double diameters) {
    this.minSlugLengthDiameters = Math.max(5.0, diameters);
  }

  /**
   * Set maximum slug length in diameters.
   *
   * @param diameters maximum length (diameters)
   */
  public void setMaxSlugLengthDiameters(double diameters) {
    this.maxSlugLengthDiameters = Math.max(50.0, diameters);
  }

  /**
   * Set initial slug length in diameters.
   *
   * @param diameters initial length (diameters)
   */
  public void setInitialSlugLengthDiameters(double diameters) {
    this.initialSlugLengthDiameters = Math.max(10.0, diameters);
  }

  /**
   * Enable or disable inlet slug generation.
   *
   * @param enable true to enable
   */
  public void setEnableInletSlugGeneration(boolean enable) {
    this.enableInletSlugGeneration = enable;
  }

  /**
   * Enable or disable terrain slug generation.
   *
   * @param enable true to enable
   */
  public void setEnableTerrainSlugGeneration(boolean enable) {
    this.enableTerrainSlugGeneration = enable;
  }

  /**
   * Enable or disable wake effects.
   *
   * @param enable true to enable
   */
  public void setEnableWakeEffects(boolean enable) {
    this.enableWakeEffects = enable;
  }

  /**
   * Enable or disable stochastic slug initiation.
   *
   * @param enable true to enable
   */
  public void setEnableStochasticInitiation(boolean enable) {
    this.enableStochasticInitiation = enable;
  }

  /**
   * Set initiation holdup threshold.
   *
   * @param threshold holdup threshold (0-1)
   */
  public void setInitiationHoldupThreshold(double threshold) {
    this.initiationHoldupThreshold = Math.max(0.1, Math.min(0.5, threshold));
  }

  /**
   * Set wake length in diameters.
   *
   * @param diameters wake length (diameters)
   */
  public void setWakeLengthDiameters(double diameters) {
    this.wakeLengthDiameters = Math.max(10.0, diameters);
  }

  /**
   * Set maximum wake acceleration factor.
   *
   * @param factor acceleration factor (&gt;= 1.0)
   */
  public void setMaxWakeAcceleration(double factor) {
    this.maxWakeAcceleration = Math.max(1.0, Math.min(2.0, factor));
  }

  /**
   * Get mass conservation error.
   *
   * <p>
   * The tracker audit is {@code borrowed - returned - exited - active}. It is separate from the conservative
   * TwoFluidPipe mass-balance report and should remain near zero throughout tracking.
   * </p>
   *
   * @return tracker accounting error (kg), should be near zero
   */
  public double getMassConservationError() {
    double massInActive = 0;
    for (SlugBubbleUnit slug : slugs) {
      massInActive += slug.slugLiquidMass;
    }
    return totalMassBorrowed - totalMassReturned - totalMassExitedAtOutlet - totalMassExitedAtInlet - massInActive;
  }

  /**
   * Get cumulative mass added to the slug overlay account. No mass is withdrawn from Eulerian cells.
   *
   * @return mass (kg)
   */
  public double getTotalMassBorrowedFromEulerian() {
    return totalMassBorrowed;
  }

  /**
   * Get cumulative mass removed from the overlay by shrinkage or dissipation. No mass is deposited into Eulerian cells.
   *
   * @return mass (kg)
   */
  public double getTotalMassReturnedToEulerian() {
    return totalMassReturned;
  }

  /**
   * Get total tracked slug liquid mass that exited through the pipe outlet.
   *
   * @return cumulative outlet mass (kg)
   */
  public double getTotalMassExitedAtOutlet() {
    return totalMassExitedAtOutlet;
  }

  /**
   * Get geometric overlay mass removed by complete upstream exits.
   *
   * @return cumulative inlet-exit overlay mass in kg; no Eulerian mass is withdrawn
   */
  public double getTotalMassExitedAtInlet() {
    return totalMassExitedAtInlet;
  }

  /**
   * Get outlet slug lengths for distribution analysis.
   *
   * @return list of slug lengths (m)
   */
  public List<Double> getOutletSlugLengths() {
    return new ArrayList<>(outletSlugLengths);
  }

  /**
   * Get outlet slug volumes.
   *
   * @return list of slug volumes (m³)
   */
  public List<Double> getOutletSlugVolumes() {
    return new ArrayList<>(outletSlugVolumes);
  }

  /**
   * Get inter-arrival times at outlet.
   *
   * @return list of inter-arrival times (s)
   */
  public List<Double> getOutletInterArrivalTimes() {
    return new ArrayList<>(outletInterArrivalTimes);
  }

  /**
   * Get the tracker time at the most recent completed slug exit.
   *
   * <p>
   * An outlet event is recorded when the slug tail crosses the outlet, not when its front first arrives. The time is
   * the end of the tracking step that contains the crossing.
   * </p>
   *
   * @return last completed exit time (s), or -1 before an exit has been recorded
   */
  public double getLastOutletArrivalTime() {
    return lastOutletArrivalTime;
  }

  /**
   * Register a fixed-position observation without changing the slug model.
   *
   * @param position axial position in metres
   * @return newly registered probe
   */
  public SlugProbe addProbe(double position) {
    return addProbe(new SlugProbe(position));
  }

  /**
   * Register an independent probe, including one made with {@link SlugProbe#copyConfiguration()}.
   *
   * @param probe non-null probe to register
   * @return registered probe
   */
  public SlugProbe addProbe(SlugProbe probe) {
    if (probe == null) {
      throw new IllegalArgumentException("Slug probe cannot be null");
    }
    if (probes == null) {
      probes = new ArrayList<>();
    }
    if (!probes.contains(probe)) {
      probes.add(probe);
    }
    return probe;
  }

  /** @return immutable snapshot of registered probes; their event histories remain independently drainable */
  public List<SlugProbe> getProbes() {
    return probes == null ? Collections.<SlugProbe>emptyList() : Collections.unmodifiableList(new ArrayList<>(probes));
  }

  /**
   * Stop observing a probe without modifying its retained events.
   *
   * @param probe probe to unregister
   * @return true if it was registered
   */
  public boolean removeProbe(SlugProbe probe) {
    return probes != null && probes.remove(probe);
  }

  /** Record displacement before merge/removal or other instantaneous geometry changes. */
  private void recordProbeMotion(SlugBubbleUnit slug, double dt, double previousFront, double previousTail) {
    if (probes != null) {
      for (SlugProbe probe : probes) {
        probe.recordMotion(slug.id, simulationTime - dt, dt, previousFront, previousTail, slug.frontPosition,
            slug.tailPosition);
      }
    }
  }

  /**
   * Reset tracker state.
   */
  public void reset() {
    slugs.clear();
    slugIdCounter = 0;
    totalSlugsGenerated = 0;
    totalSlugsMerged = 0;
    totalSlugsDissipated = 0;
    totalSlugsExited = 0;
    totalSlugsExitedAtInlet = 0;
    inletSlugFrequency = 0;
    outletSlugFrequency = 0;
    averageSlugLength = 0;
    maxObservedSlugLength = 0;
    maxSlugVolumeAtOutlet = 0;
    simulationTime = 0;
    timeSinceLastInletSlug = 0;
    totalMassBorrowed = 0;
    totalMassReturned = 0;
    totalMassExitedAtOutlet = 0;
    totalMassExitedAtInlet = 0;
    lastOutletArrivalTime = -1;
    outletSlugLengths.clear();
    outletSlugVolumes.clear();
    outletInterArrivalTimes.clear();
    if (probes != null) {
      for (SlugProbe probe : probes) {
        probe.clearEvents();
      }
    }
  }

  /**
   * Get detailed statistics string.
   *
   * @return statistics summary
   */
  public String getStatisticsString() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Lagrangian Slug Tracking Statistics ===\n");
    sb.append(String.format("Simulation time: %.1f s\n", simulationTime));
    sb.append(String.format("Active slugs: %d\n", slugs.size()));
    sb.append(String.format("Total generated: %d\n", totalSlugsGenerated));
    sb.append(String.format("  - Exited at outlet: %d\n", totalSlugsExited));
    sb.append(String.format("  - Exited at inlet: %d\n", totalSlugsExitedAtInlet));
    sb.append(String.format("  - Merged: %d\n", totalSlugsMerged));
    sb.append(String.format("  - Dissipated: %d\n", totalSlugsDissipated));
    sb.append(String.format("Inlet frequency: %.4f Hz (period: %.1f s)\n", inletSlugFrequency,
        1.0 / (inletSlugFrequency + 1e-10)));
    sb.append(String.format("Outlet frequency: %.4f Hz\n", outletSlugFrequency));
    sb.append(String.format("Average slug length: %.2f m\n", averageSlugLength));
    sb.append(String.format("Max slug length: %.2f m\n", maxObservedSlugLength));
    sb.append(String.format("Max slug volume at outlet: %.4f m³\n", maxSlugVolumeAtOutlet));
    sb.append(String.format("Mass borrowed: %.3f kg\n", totalMassBorrowed));
    sb.append(String.format("Mass returned: %.3f kg\n", totalMassReturned));
    sb.append(String.format("Mass exited at outlet: %.3f kg\n", totalMassExitedAtOutlet));
    sb.append(String.format("Mass conservation error: %.6f kg\n", getMassConservationError()));

    if (!outletSlugLengths.isEmpty()) {
      double minLen = Collections.min(outletSlugLengths);
      double maxLen = Collections.max(outletSlugLengths);
      sb.append(String.format("Outlet slug length range: %.2f - %.2f m\n", minLen, maxLen));
    }

    return sb.toString();
  }

  /**
   * Get JSON representation of current state.
   *
   * @return JSON string
   */
  public String toJson() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append(String.format("  \"simulationTime\": %.2f,\n", simulationTime));
    sb.append(String.format("  \"activeSlugCount\": %d,\n", slugs.size()));
    sb.append(String.format("  \"totalGenerated\": %d,\n", totalSlugsGenerated));
    sb.append(String.format("  \"totalExited\": %d,\n", totalSlugsExited));
    sb.append(String.format("  \"totalExitedAtInlet\": %d,\n", totalSlugsExitedAtInlet));
    sb.append(String.format("  \"totalMerged\": %d,\n", totalSlugsMerged));
    sb.append(String.format("  \"totalDissipated\": %d,\n", totalSlugsDissipated));
    sb.append(String.format("  \"inletFrequency\": %.6f,\n", inletSlugFrequency));
    sb.append(String.format("  \"outletFrequency\": %.6f,\n", outletSlugFrequency));
    sb.append(String.format("  \"averageSlugLength\": %.4f,\n", averageSlugLength));
    sb.append(String.format("  \"maxSlugLength\": %.4f,\n", maxObservedSlugLength));
    sb.append(String.format("  \"maxSlugVolumeAtOutlet\": %.6f,\n", maxSlugVolumeAtOutlet));
    sb.append(String.format("  \"massBorrowed\": %.9f,\n", totalMassBorrowed));
    sb.append(String.format("  \"massReturned\": %.9f,\n", totalMassReturned));
    sb.append(String.format("  \"massExitedAtOutlet\": %.9f,\n", totalMassExitedAtOutlet));
    sb.append(String.format("  \"massConservationError\": %.9f,\n", getMassConservationError()));
    sb.append("  \"activeSlugs\": [\n");

    for (int i = 0; i < slugs.size(); i++) {
      SlugBubbleUnit slug = slugs.get(i);
      sb.append("    {\n");
      sb.append(String.format("      \"id\": %d,\n", slug.id));
      sb.append(String.format("      \"source\": \"%s\",\n", slug.source));
      sb.append(String.format("      \"frontPosition\": %.2f,\n", slug.frontPosition));
      sb.append(String.format("      \"tailPosition\": %.2f,\n", slug.tailPosition));
      sb.append(String.format("      \"slugLength\": %.2f,\n", slug.slugLength));
      sb.append(String.format("      \"bubbleLength\": %.2f,\n", slug.bubbleLength));
      sb.append(String.format("      \"frontVelocity\": %.3f,\n", slug.frontVelocity));
      sb.append(String.format("      \"slugHoldup\": %.3f,\n", slug.slugHoldup));
      sb.append(String.format("      \"liquidVolume\": %.6f,\n", slug.slugLiquidVolume));
      sb.append(String.format("      \"age\": %.2f,\n", slug.age));
      sb.append(String.format("      \"isGrowing\": %b,\n", slug.isGrowing));
      sb.append(String.format("      \"inWakeRegion\": %b\n", slug.inWakeRegion));
      sb.append("    }");
      if (i < slugs.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }

    sb.append("  ]\n");
    sb.append("}");
    return sb.toString();
  }
}
