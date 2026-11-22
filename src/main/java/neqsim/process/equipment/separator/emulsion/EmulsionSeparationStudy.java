package neqsim.process.equipment.separator.emulsion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.thermo.system.SystemInterface;

/**
 * Runs an optional constant-area water-in-oil emulsion-layer diagnostic study.
 *
 * <p>
 * The study owns its state and never changes a supplied separator, its outlets, or its transient inventory. It applies
 * the dense-packed-zone model of Zlobin et al. (2026), DOI 10.1002/aic.70429. The supplied water rate is the
 * dispersed-water rate entering the modeled layer, not necessarily the separator's total water rate. Effective
 * horizontal interface area must be supplied explicitly. A horizontal drum requires a justified constant-area
 * approximation.
 * </p>
 *
 * <p>
 * Without calibrated kinetics, the default radius (1.35 mm) and contact-coalescence time (60 s) are disclosed screening
 * guesses. The conversion {@code omega = 1/(pi * radius^2 * contactTime)} is an assumed effective contact area, not a
 * published predictive chemistry law. Sensitivity scenarios are not confidence intervals or field validation.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class EmulsionSeparationStudy {
  /** Maximum number of integration steps per scenario. */
  private static final int MAXIMUM_STEPS = 100000;
  /** Default representative droplet radius, m. */
  private static final double DEFAULT_RADIUS_M = 1.35e-3;
  /** Default assumed mean effective contact-coalescence time, s. */
  private static final double DEFAULT_CONTACT_TIME_S = 60.0;
  /** Constant horizontal interface area, m2. */
  private final double effectiveAreaM2;
  /** Oil-water interfacial tension, N/m. */
  private final double interfacialTensionNPerM;
  /** Water-minus-oil density difference, kg/m3. */
  private final double densityDifferenceKgPerM3;
  /** Representative radius in the dense layer, m. */
  private final double dropletRadiusM;
  /** Assumed contact-coalescence time, s, or NaN for supplied omega. */
  private final double contactTimeS;
  /** Supplied or estimated coalescence rate per contact area, 1/(m2 s). */
  private final double omegaPerM2Second;
  /** Provenance of the kinetic input. */
  private final KineticsProvenance provenance;

  /**
   * Indicates whether kinetics are screening estimates or caller-supplied calibrated input.
   *
   * @author ESOL
   * @version 1.0
   */
  public enum KineticsProvenance {
    /** Guessed kinetics; no experimental calibration is implied. */
    ESTIMATED_UNCALIBRATED,
    /** Caller-supplied omega; the caller must provide calibration provenance and applicability. */
    CALIBRATED_INPUT
  }

  /**
   * Create a study from explicit physical properties, using disclosed screening kinetics.
   *
   * @param effectiveAreaM2 constant horizontal interface area in m2
   * @param interfacialTensionNPerM oil-water interfacial tension in N/m
   * @param oilDensityKgPerM3 continuous oil density in kg/m3
   * @param waterDensityKgPerM3 dispersed water density in kg/m3, greater than oil density
   */
  public EmulsionSeparationStudy(double effectiveAreaM2, double interfacialTensionNPerM, double oilDensityKgPerM3,
      double waterDensityKgPerM3) {
    this(effectiveAreaM2, interfacialTensionNPerM, densityDifference(oilDensityKgPerM3, waterDensityKgPerM3),
        DEFAULT_RADIUS_M, DEFAULT_CONTACT_TIME_S, estimatedOmega(DEFAULT_RADIUS_M, DEFAULT_CONTACT_TIME_S),
        KineticsProvenance.ESTIMATED_UNCALIBRATED);
  }

  /**
   * Copy properties from an already calculated separator without performing a new flash.
   *
   * @param separator separator containing distinct oil and aqueous phases
   * @param effectiveAreaM2 justified constant horizontal interface area in m2
   */
  public EmulsionSeparationStudy(ThreePhaseSeparator separator, double effectiveAreaM2) {
    this(snapshot(separator), effectiveAreaM2);
  }

  /**
   * Initialize a study from a detached property snapshot.
   *
   * @param properties detached snapshot [oil-water tension, oil density, water density]
   * @param effectiveAreaM2 constant interface area in m2
   */
  private EmulsionSeparationStudy(double[] properties, double effectiveAreaM2) {
    this(effectiveAreaM2, properties[0], properties[1], properties[2]);
  }

  /**
   * Initialize immutable study settings.
   *
   * @param area constant horizontal interface area in m2
   * @param tension oil-water interfacial tension in N/m
   * @param densityDifference water-minus-oil density difference in kg/m3
   * @param radius representative droplet radius in m
   * @param contactTime assumed coalescence time in s, or NaN for calibrated kinetics
   * @param omega effective coalescence rate in 1/(m2 s)
   * @param kineticsProvenance input provenance
   */
  private EmulsionSeparationStudy(double area, double tension, double densityDifference, double radius,
      double contactTime, double omega, KineticsProvenance kineticsProvenance) {
    requirePositive("effective area", area);
    requirePositive("interfacial tension", tension);
    requirePositive("density difference", densityDifference);
    requirePositive("droplet radius", radius);
    requireNonnegative("coalescence rate", omega);
    if (kineticsProvenance == KineticsProvenance.ESTIMATED_UNCALIBRATED) {
      requirePositive("contact-coalescence time", contactTime);
    }
    effectiveAreaM2 = area;
    interfacialTensionNPerM = tension;
    densityDifferenceKgPerM3 = densityDifference;
    dropletRadiusM = radius;
    contactTimeS = contactTime;
    omegaPerM2Second = omega;
    provenance = kineticsProvenance;
  }

  /**
   * Return an independent study with guessed representative droplet radius and contact time.
   *
   * @param radiusM representative dense-layer droplet radius in m
   * @param contactTimeSeconds assumed mean contact-coalescence time in s
   * @return new study marked estimated and uncalibrated
   */
  public EmulsionSeparationStudy guessKinetics(double radiusM, double contactTimeSeconds) {
    return new EmulsionSeparationStudy(effectiveAreaM2, interfacialTensionNPerM, densityDifferenceKgPerM3, radiusM,
        contactTimeSeconds, estimatedOmega(radiusM, contactTimeSeconds), KineticsProvenance.ESTIMATED_UNCALIBRATED);
  }

  /**
   * Return an independent study using caller-supplied coalescence kinetics.
   *
   * @param radiusM representative dense-layer droplet radius in m
   * @param coalescenceRatePerM2Second omega in 1/(m2 s), zero for fully inhibited coalescence
   * @return new study with calibrated-input provenance, without guessed sensitivity replacements
   */
  public EmulsionSeparationStudy calibratedKinetics(double radiusM, double coalescenceRatePerM2Second) {
    return new EmulsionSeparationStudy(effectiveAreaM2, interfacialTensionNPerM, densityDifferenceKgPerM3, radiusM,
        Double.NaN, coalescenceRatePerM2Second, KineticsProvenance.CALIBRATED_INPUT);
  }

  /**
   * Run a new study starting from an empty layer.
   *
   * @param incomingDispersedWaterFlowM3PerS dispersed water entering the layer in m3/s
   * @param durationS duration in s, zero allowed
   * @param timeStepS maximum integration timestep in s
   * @return immutable nominal result and declared sensitivity scenarios
   */
  public Result run(double incomingDispersedWaterFlowM3PerS, double durationS, double timeStepS) {
    return run(0.0, incomingDispersedWaterFlowM3PerS, durationS, timeStepS);
  }

  /**
   * Run a new study from an explicitly supplied dispersed-water inventory per interface area.
   *
   * @param initialDispersedWaterDepthM initial dispersed-water volume divided by area in m
   * @param incomingDispersedWaterFlowM3PerS dispersed water entering the layer in m3/s
   * @param durationS duration in s, zero allowed
   * @param timeStepS maximum integration timestep in s
   * @return immutable nominal result and declared sensitivity scenarios
   */
  public Result run(double initialDispersedWaterDepthM, double incomingDispersedWaterFlowM3PerS, double durationS,
      double timeStepS) {
    requireNonnegative("initial dispersed-water depth", initialDispersedWaterDepthM);
    requireNonnegative("incoming dispersed-water flow", incomingDispersedWaterFlowM3PerS);
    requireNonnegative("duration", durationS);
    requirePositive("time step", timeStepS);
    double requiredSteps = durationS == 0.0 ? 0.0 : Math.max(1.0, Math.ceil(durationS / timeStepS));
    if (!Double.isFinite(requiredSteps) || requiredSteps > MAXIMUM_STEPS) {
      throw new IllegalArgumentException("study exceeds 100000 steps per scenario");
    }
    double initialVolume = initialDispersedWaterDepthM * effectiveAreaM2;
    double incomingVolume = incomingDispersedWaterFlowM3PerS * durationS;
    requireNonnegative("initial water volume", initialVolume);
    requireNonnegative("incoming water volume", incomingVolume);
    requireNonnegative("total supplied water volume", initialVolume + incomingVolume);
    double flux = incomingDispersedWaterFlowM3PerS / effectiveAreaM2;
    requireNonnegative("incoming dispersed-water flux", flux);
    if (incomingDispersedWaterFlowM3PerS > 0.0 && flux == 0.0) {
      throw new IllegalArgumentException("incoming dispersed-water flux underflowed");
    }
    DenseEmulsionLayerModel model = model(dropletRadiusM, omegaPerM2Second);
    Trajectory nominal = integrate(model, initialDispersedWaterDepthM, flux, durationS, timeStepS, (int) requiredSteps,
        true);
    List<Scenario> scenarios = new ArrayList<Scenario>();
    if (provenance == KineticsProvenance.ESTIMATED_UNCALIBRATED) {
      double[] factors = new double[] {1.0 / 3.0, 1.0, 3.0};
      for (double radiusFactor : factors) {
        for (double timeFactor : factors) {
          double radius = dropletRadiusM * radiusFactor;
          double contactTime = contactTimeS * timeFactor;
          double omega = estimatedOmega(radius, contactTime);
          DenseEmulsionLayerModel sensitivityModel = model(radius, omega);
          Trajectory trajectory = radiusFactor == 1.0 && timeFactor == 1.0 ? nominal
              : integrate(sensitivityModel, initialDispersedWaterDepthM, flux, durationS, timeStepS,
                  (int) requiredSteps, false);
          scenarios.add(scenario(sensitivityModel, radius, contactTime, omega, trajectory.finalDepthM, flux));
        }
      }
    } else {
      scenarios.add(scenario(model, dropletRadiusM, Double.NaN, omegaPerM2Second, nominal.finalDepthM, flux));
    }
    double accumulatedVolume = nominal.finalDepthM * effectiveAreaM2;
    requireNonnegative("accumulated dispersed-water volume", accumulatedVolume);
    double coalescedVolume = initialVolume + incomingVolume - accumulatedVolume;
    if (!Double.isFinite(coalescedVolume) || coalescedVolume < -1e-12 * Math.max(1.0, initialVolume + incomingVolume)) {
      throw new IllegalStateException("dispersed-water volume ledger did not close");
    }
    List<String> warnings = new ArrayList<String>();
    warnings.add("Diagnostic constant-area water-in-oil layer; live vessel inventories and outlets are unchanged.");
    warnings.add(
        "Initial monolayers and wall effects are outside the paper model; an empty initial layer is an extrapolation.");
    warnings.add("Model-emulsion evidence does not establish crude-oil or field applicability.");
    if (provenance == KineticsProvenance.ESTIMATED_UNCALIBRATED) {
      warnings.add(
          "ESTIMATED_UNCALIBRATED: radius and contact time are guesses; omega = 1/(pi R^2 tc) is an assumed contact-area mapping.");
      warnings.add(
          "The nine factor-three sensitivity scenarios are not a probability distribution or confidence interval.");
    } else {
      warnings
          .add("CALIBRATED_INPUT labels supplied kinetics only; preserve the calibration source and validity range.");
    }
    return new Result(nominal.profile, initialVolume, incomingVolume, accumulatedVolume, Math.max(0.0, coalescedVolume),
        criticalFlow(model), model.getSteadyLayerHeightM(flux), provenance, warnings, scenarios);
  }

  /**
   * Construct a physical kernel with study-owned property values.
   *
   * @param radius representative radius in m
   * @param omega coalescence rate in 1/(m2 s)
   * @return independent immutable kernel
   */
  private DenseEmulsionLayerModel model(double radius, double omega) {
    return new DenseEmulsionLayerModel(interfacialTensionNPerM, densityDifferenceKgPerM3, radius, omega);
  }

  /**
   * Convert the limiting flux to a finite flow on the explicitly declared area.
   *
   * @param model physical kernel
   * @return critical dispersed-water flow in m3/s
   */
  private double criticalFlow(DenseEmulsionLayerModel model) {
    double flow = model.getCriticalFluxMPerS() * effectiveAreaM2;
    requireNonnegative("critical dispersed-water flow", flow);
    return flow;
  }

  /**
   * Integrate one scenario without retaining sensitivity profiles.
   *
   * @param model physical kernel
   * @param initialDepth initial dispersed-water depth in m
   * @param flux incoming dispersed-water flux in m/s
   * @param duration total duration in s
   * @param timeStep maximum timestep in s
   * @param steps bounded number of steps
   * @param retainProfile whether to retain every nominal point
   * @return final depth and optional profile
   */
  private Trajectory integrate(DenseEmulsionLayerModel model, double initialDepth, double flux, double duration,
      double timeStep, int steps, boolean retainProfile) {
    List<ProfilePoint> profile = new ArrayList<ProfilePoint>();
    double depth = initialDepth;
    double previousTime = 0.0;
    if (retainProfile) {
      profile.add(new ProfilePoint(0.0, model.getLayerHeightM(depth), depth * effectiveAreaM2));
    }
    for (int step = 1; step <= steps; step++) {
      if (previousTime >= duration) {
        break;
      }
      double time = Math.min(duration, step * timeStep);
      double dt = time - previousTime;
      if (!(dt > 0.0)) {
        throw new IllegalArgumentException("time step cannot advance the supplied duration");
      }
      depth = model.advance(depth, flux, dt);
      if (retainProfile) {
        profile.add(new ProfilePoint(time, model.getLayerHeightM(depth), depth * effectiveAreaM2));
      }
      previousTime = time;
    }
    return new Trajectory(depth, profile);
  }

  /**
   * Assemble a scenario summary with explicit kinetic assumptions.
   *
   * @param model physical kernel
   * @param radius representative radius in m
   * @param contactTime assumed contact-coalescence time in s, or NaN
   * @param omega coalescence rate in 1/(m2 s)
   * @param finalDepth final dispersed-water depth in m
   * @param flux incoming flux in m/s
   * @return immutable scenario summary
   */
  private Scenario scenario(DenseEmulsionLayerModel model, double radius, double contactTime, double omega,
      double finalDepth, double flux) {
    return new Scenario(radius, contactTime, omega, model.getLayerHeightM(finalDepth), criticalFlow(model),
        model.getSteadyLayerHeightM(flux));
  }

  /**
   * Copy thermodynamic state before initializing transport properties and evaluating oil-water IFT.
   *
   * @param separator calculated separator with distinct oil and aqueous phases
   * @return detached property values [tension, oil density, water density]
   */
  private static double[] snapshot(ThreePhaseSeparator separator) {
    if (separator == null || separator.getThermoSystem() == null) {
      throw new IllegalArgumentException("a calculated three-phase separator is required");
    }
    SystemInterface fluid = separator.getThermoSystem().clone();
    if (!fluid.hasPhaseType("oil") || !fluid.hasPhaseType("aqueous")) {
      throw new IllegalArgumentException("the separator must contain distinct oil and aqueous phases");
    }
    fluid.initPhysicalProperties();
    int oilIndex = fluid.getPhaseNumberOfPhase("oil");
    int waterIndex = fluid.getPhaseNumberOfPhase("aqueous");
    double tension = fluid.getInterphaseProperties().getSurfaceTension(oilIndex, waterIndex);
    return new double[] {tension, fluid.getPhase(oilIndex).getPhysicalProperties().getDensity(),
        fluid.getPhase(waterIndex).getPhysicalProperties().getDensity()};
  }

  /**
   * Validate phase densities and compute the settling density difference.
   *
   * @param oilDensity continuous oil density in kg/m3
   * @param waterDensity dispersed water density in kg/m3
   * @return positive water-minus-oil density difference in kg/m3
   */
  private static double densityDifference(double oilDensity, double waterDensity) {
    requirePositive("oil density", oilDensity);
    requirePositive("water density", waterDensity);
    double difference = waterDensity - oilDensity;
    requirePositive("water-minus-oil density difference", difference);
    return difference;
  }

  /**
   * Map two disclosed guesses onto an effective rate per assumed droplet contact area.
   *
   * @param radius representative droplet radius in m
   * @param contactTime mean effective contact-coalescence time in s
   * @return estimated omega in 1/(m2 s)
   */
  private static double estimatedOmega(double radius, double contactTime) {
    requirePositive("droplet radius", radius);
    requirePositive("contact-coalescence time", contactTime);
    double contactAreaTime = Math.PI * radius * radius * contactTime;
    requirePositive("effective contact area times time", contactAreaTime);
    double omega = 1.0 / contactAreaTime;
    requirePositive("estimated coalescence rate", omega);
    return omega;
  }

  /**
   * Reject invalid positive input values.
   *
   * @param name input name
   * @param value input value
   */
  private static void requirePositive(String name, double value) {
    if (!Double.isFinite(value) || !(value > 0.0)) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  /**
   * Reject invalid nonnegative input values.
   *
   * @param name input name
   * @param value input value
   */
  private static void requireNonnegative(String name, double value) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and nonnegative");
    }
  }

  /**
   * Private integration outcome; never shared with caller-owned state.
   *
   * @author ESOL
   * @version 1.0
   */
  private static final class Trajectory {
    private final double finalDepthM;
    private final List<ProfilePoint> profile;

    /**
     * Store an internally owned integration outcome.
     *
     * @param depth final dispersed-water depth in m
     * @param points nominal profile, or an empty list
     */
    private Trajectory(double depth, List<ProfilePoint> points) {
      finalDepthM = depth;
      profile = points;
    }
  }

  /**
   * Immutable nominal profile point.
   *
   * @author ESOL
   * @version 1.0
   */
  public static final class ProfilePoint {
    private final double timeS;
    private final double layerHeightM;
    private final double dispersedWaterVolumeM3;

    /**
     * Store one nominal state.
     *
     * @param time elapsed time in s
     * @param height total emulsion-layer height in m
     * @param volume dispersed-water volume in m3
     */
    private ProfilePoint(double time, double height, double volume) {
      timeS = time;
      layerHeightM = height;
      dispersedWaterVolumeM3 = volume;
    }

    /**
     * Return elapsed time.
     *
     * @return time in s
     */
    public double getTimeS() {
      return timeS;
    }

    /**
     * Return total emulsion-layer height.
     *
     * @return height in m
     */
    public double getLayerHeightM() {
      return layerHeightM;
    }

    /**
     * Return accumulated dispersed-water inventory.
     *
     * @return volume in m3
     */
    public double getDispersedWaterVolumeM3() {
      return dispersedWaterVolumeM3;
    }
  }

  /**
   * Immutable kinetic-sensitivity scenario, not a statistical quantile.
   *
   * @author ESOL
   * @version 1.0
   */
  public static final class Scenario {
    private final double dropletRadiusM;
    private final double contactTimeS;
    private final double omegaPerM2Second;
    private final double finalLayerHeightM;
    private final double criticalWaterFlowM3PerS;
    private final double steadyLayerHeightM;

    /**
     * Store scenario assumptions and predictions.
     *
     * @param radius representative radius in m
     * @param contactTime assumed coalescence time in s, or NaN for supplied omega
     * @param omega effective coalescence rate in 1/(m2 s)
     * @param finalHeight final total layer height in m
     * @param criticalFlow maximum asymptotic coalescence flow in m3/s
     * @param steadyHeight steady layer height in m, or positive infinity
     */
    private Scenario(double radius, double contactTime, double omega, double finalHeight, double criticalFlow,
        double steadyHeight) {
      dropletRadiusM = radius;
      contactTimeS = contactTime;
      omegaPerM2Second = omega;
      finalLayerHeightM = finalHeight;
      criticalWaterFlowM3PerS = criticalFlow;
      steadyLayerHeightM = steadyHeight;
    }

    /**
     * Return representative dense-layer droplet radius.
     *
     * @return radius in m
     */
    public double getDropletRadiusM() {
      return dropletRadiusM;
    }

    /**
     * Return guessed contact time.
     *
     * @return time in s, or NaN for supplied omega
     */
    public double getContactTimeS() {
      return contactTimeS;
    }

    /**
     * Return effective coalescence rate.
     *
     * @return omega in 1/(m2 s)
     */
    public double getOmegaPerM2Second() {
      return omegaPerM2Second;
    }

    /**
     * Return final total emulsion-layer height.
     *
     * @return height in m
     */
    public double getFinalLayerHeightM() {
      return finalLayerHeightM;
    }

    /**
     * Return asymptotic critical dispersed-water flow.
     *
     * @return flow in m3/s
     */
    public double getCriticalWaterFlowM3PerS() {
      return criticalWaterFlowM3PerS;
    }

    /**
     * Return steady height.
     *
     * @return height in m, or positive infinity if no finite steady state
     */
    public double getSteadyLayerHeightM() {
      return steadyLayerHeightM;
    }
  }

  /**
   * Immutable study result with a dispersed-water ledger and declared kinetic provenance.
   *
   * @author ESOL
   * @version 1.0
   */
  public static final class Result {
    private final List<ProfilePoint> profile;
    private final double initialWaterVolumeM3;
    private final double incomingWaterVolumeM3;
    private final double accumulatedWaterVolumeM3;
    private final double coalescedWaterVolumeM3;
    private final double criticalWaterFlowM3PerS;
    private final double steadyLayerHeightM;
    private final KineticsProvenance kineticsProvenance;
    private final List<String> warnings;
    private final List<Scenario> scenarios;

    /**
     * Store a detached nominal result and declared scenarios.
     *
     * @param points nominal time profile
     * @param initialVolume initial dispersed-water volume in m3
     * @param incomingVolume integrated incoming dispersed-water volume in m3
     * @param accumulatedVolume final dispersed-water volume in m3
     * @param coalescedVolume integrated coalesced-water volume in m3
     * @param criticalFlow critical incoming dispersed-water flow in m3/s
     * @param steadyHeight steady layer height in m, or positive infinity
     * @param provenance kinetic-input provenance
     * @param findings applicability and uncertainty findings
     * @param sensitivityScenarios nominal and optional assumed sensitivity scenarios
     */
    private Result(List<ProfilePoint> points, double initialVolume, double incomingVolume, double accumulatedVolume,
        double coalescedVolume, double criticalFlow, double steadyHeight, KineticsProvenance provenance,
        List<String> findings, List<Scenario> sensitivityScenarios) {
      profile = Collections.unmodifiableList(new ArrayList<ProfilePoint>(points));
      initialWaterVolumeM3 = initialVolume;
      incomingWaterVolumeM3 = incomingVolume;
      accumulatedWaterVolumeM3 = accumulatedVolume;
      coalescedWaterVolumeM3 = coalescedVolume;
      criticalWaterFlowM3PerS = criticalFlow;
      steadyLayerHeightM = steadyHeight;
      kineticsProvenance = provenance;
      warnings = Collections.unmodifiableList(new ArrayList<String>(findings));
      scenarios = Collections.unmodifiableList(new ArrayList<Scenario>(sensitivityScenarios));
    }

    /**
     * Return the immutable nominal time profile.
     *
     * @return profile points
     */
    public List<ProfilePoint> getProfile() {
      return profile;
    }

    /**
     * Return final total layer height.
     *
     * @return emulsion-layer height in m
     */
    public double getFinalLayerHeightM() {
      return profile.get(profile.size() - 1).getLayerHeightM();
    }

    /**
     * Return initial dispersed-water inventory.
     *
     * @return volume in m3
     */
    public double getInitialWaterVolumeM3() {
      return initialWaterVolumeM3;
    }

    /**
     * Return integrated supplied dispersed water.
     *
     * @return volume in m3
     */
    public double getIncomingWaterVolumeM3() {
      return incomingWaterVolumeM3;
    }

    /**
     * Return final dispersed-water inventory.
     *
     * @return volume in m3
     */
    public double getAccumulatedWaterVolumeM3() {
      return accumulatedWaterVolumeM3;
    }

    /**
     * Return integrated coalesced water.
     *
     * @return volume in m3
     */
    public double getCoalescedWaterVolumeM3() {
      return coalescedWaterVolumeM3;
    }

    /**
     * Return asymptotic coalescence-limited flow.
     *
     * @return critical flow in m3/s
     */
    public double getCriticalWaterFlowM3PerS() {
      return criticalWaterFlowM3PerS;
    }

    /**
     * Return steady height.
     *
     * @return height in m, or positive infinity if no finite steady state
     */
    public double getSteadyLayerHeightM() {
      return steadyLayerHeightM;
    }

    /**
     * Return kinetic-input provenance.
     *
     * @return estimated or supplied-calibration marker
     */
    public KineticsProvenance getKineticsProvenance() {
      return kineticsProvenance;
    }

    /**
     * Return immutable applicability findings.
     *
     * @return warnings requiring interpretation
     */
    public List<String> getWarnings() {
      return warnings;
    }

    /**
     * Return declared kinetic sensitivity cases.
     *
     * @return immutable scenarios, not probability quantiles
     */
    public List<Scenario> getScenarios() {
      return scenarios;
    }
  }
}
